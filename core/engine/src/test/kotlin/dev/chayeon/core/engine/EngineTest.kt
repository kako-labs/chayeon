package dev.chayeon.core.engine

import dev.chayeon.core.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

internal class Fixture(demo: Boolean = false, val policy: EnginePolicy = EnginePolicy(), val store: EngineStore = MemoryEngineStore()) {
    var now = 1_000_000L
    private var sequence = 0
    val rules = object : SignatureRules {
        override fun title(traits: List<PublicTrait>) = traits.joinToString(" ") { it.trait.name }.ifEmpty { "시그니처를 만드는 중이에요" }
        override fun chemistry(first: PublicSignature, second: PublicSignature) = Chemistry(first.traits.mapNotNull { a ->
            second.traits.firstOrNull { it.trait == a.trait }?.let { ChemistryLine(ChemistryKind.SHARED, a.trait, it.trait, "둘 다 같은 습관을 챙겨요") }
        })
    }
    val engine = ChayeonEngine(rules, EngineClock { now }, EngineIdGenerator { "id-${++sequence}" }, store, policy, demoEnabled = demo)
    fun meta() = CommandMeta("cmd-${++sequence}")
    fun join(actor: String) {
        engine.enterSession(actor, EnterSessionRequest(meta(), SessionMode.SOLO, PersonaCode.ACCOUNT_A)).ok()
        engine.saveProfile(actor, SaveProfileRequest(meta(), ProfileDraft("가상상대", AvatarId.DAWN, setOf(InterestId.JAZZ)))).ok()
        ConsentScope.entries.forEach { engine.recordConsent(actor, RecordConsentRequest(meta(), it, ConsentAction.GRANT, policy.consentDocumentVersion)).ok() }
        engine.setDiscovery(actor, SetDiscoveryRequest(meta(), DiscoveryMode.ON)).ok()
    }
    fun snap(actor: String) = engine.snapshotFor(actor).ok()
    fun request(a: String = "a", b: String = "b") = engine.requestConnection(a, RequestConnectionRequest(meta(), snap(b).profile!!.id)).ok()
    fun connect(a: String = "a", b: String = "b"): String {
        val r = request(a, b)
        return engine.respondConnection(b, RespondConnectionRequest(meta(), r.id, RequestDecision.ACCEPT)).ok().connectionId!!
    }
    fun advance(millis: Long) { now += millis; engine.tick(now) }
    fun signature(actor: String, trait: TraitId = TraitId.GENTLE_RIDE) = engine.publishSignature(actor,
        PublishSignatureRequest(meta(), PublicSignature("untrusted", 9999, "free text rejected", listOf(PublicTrait(trait, TraitLevel.SIGNATURE)),
            KnotSeed(1), DataSource.SYNTHETIC, EvidenceBucket.ESTABLISHED))).ok()
}

internal fun <T> AppResult<T>.ok(): T = (this as? AppResult.Success<T>)?.value ?: error("Expected success: $this")
internal fun AppResult<*>.error() = (this as AppResult.Failure).error

class EngineTest {
    @Test fun `after expiry and decline are terminal with a retry cooldown`() {
        val f = Fixture(); f.join("a"); f.join("b")
        val c = f.connect(); f.engine.leaveConnection("a", LeaveConnectionRequest(f.meta(), c)).ok()
        val encounter = f.snap("a").encounters.single().id
        val expired = f.engine.requestAfter("a", RequestAfterRequest(f.meta(), encounter, ContactIds(instagramId = java.util.UUID.randomUUID().toString().replace("-", "").take(24)))).ok()
        f.advance(f.policy.afterTtlMillis)
        assertEquals(AfterRequestStatus.EXPIRED, f.snap("a").afterRequests.single().status)
        assertEquals(AppError.Expired, f.engine.respondAfter("b", RespondAfterRequest(f.meta(), expired.id, RequestDecision.ACCEPT, ContactIds(instagramId = java.util.UUID.randomUUID().toString().replace("-", "").take(24)))).error())
        assertEquals(AfterEligibility.COOLDOWN, f.snap("a").encounters.single().afterEligibility)
        f.advance(f.policy.afterCooldownMillis)
        val declined = f.engine.requestAfter("a", RequestAfterRequest(f.meta(), encounter, ContactIds(instagramId = java.util.UUID.randomUUID().toString().replace("-", "").take(24)))).ok()
        f.engine.respondAfter("b", RespondAfterRequest(f.meta(), declined.id, RequestDecision.DECLINE)).ok()
        assertEquals(AppError.AlreadyClosed, f.engine.respondAfter("b", RespondAfterRequest(f.meta(), declined.id, RequestDecision.ACCEPT, ContactIds(instagramId = java.util.UUID.randomUUID().toString().replace("-", "").take(24)))).error())
        assertEquals(AfterEligibility.COOLDOWN, f.snap("a").encounters.single().afterEligibility)
        assertTrue(f.snap("b").afterConnections.isEmpty())
    }

    @Test fun `consent revocation invalidates pending acceptance and discovery off has its own end reason`() {
        val f = Fixture(); f.join("a"); f.join("b")
        val request = f.request()
        f.engine.recordConsent("a", RecordConsentRequest(f.meta(), ConsentScope.LOCATION_USE, ConsentAction.REVOKE, f.policy.consentDocumentVersion)).ok()
        assertEquals(AppError.PeerUnavailable, f.engine.respondConnection("b", RespondConnectionRequest(f.meta(), request.id, RequestDecision.ACCEPT)).error())
        assertEquals(RegistrationState.CONSENT_REQUIRED, f.snap("a").profile!!.registration)
        f.engine.recordConsent("a", RecordConsentRequest(f.meta(), ConsentScope.LOCATION_USE, ConsentAction.GRANT, f.policy.consentDocumentVersion)).ok()
        f.engine.setDiscovery("a", SetDiscoveryRequest(f.meta(), DiscoveryMode.ON)).ok()
        f.connect()
        f.engine.setDiscovery("a", SetDiscoveryRequest(f.meta(), DiscoveryMode.OFF)).ok()
        assertEquals(ConnectionEndReason.DISCOVERY_OFF, f.snap("b").connections.single().endReason)
    }

    @Test fun `recording traits are rejected by the actual command decoder`() {
        val f = Fixture(); f.join("a")
        val signature = PublicSignature("untrusted", 1, "free text", listOf(PublicTrait(TraitId.GENTLE_RIDE, TraitLevel.SIGNATURE)),
            KnotSeed(1), DataSource.SYNTHETIC, EvidenceBucket.GROWING)
        val body = EngineJson.encodeToString(PublishSignatureRequest(f.meta(), signature)).replace("\"level\":\"SIGNATURE\"", "\"level\":\"RECORDING\"")
        val response = EngineApi(f.engine).execute("a", ApiRoutes.PUBLISH_SIGNATURE, body)
        assertEquals(AppResult.Failure(AppError.Restricted), EngineJson.decodeFromString<PublishSignatureResponse>(response).result)
        assertNull(f.snap("a").profile!!.publicProfile.signature)
    }

    @Test fun `evicted command keys never apply an old profile over a newer edit`() {
        val f = Fixture(policy = EnginePolicy(maximumCachedCommands = 4)); f.join("a")
        val old = SaveProfileRequest(f.meta(), ProfileDraft("오래된프로필", AvatarId.DAWN, emptySet()))
        f.engine.saveProfile("a", old).ok()
        f.engine.saveProfile("a", old.copy(meta = f.meta(), draft = old.draft.copy(nickname = "현재프로필"))).ok()
        repeat(6) { f.engine.refreshDiscovery("a", RefreshDiscoveryRequest(f.meta())).ok() }
        assertEquals(AppError.AlreadyClosed, f.engine.saveProfile("a", old).error())
        assertEquals("현재프로필", f.snap("a").profile!!.publicProfile.nickname)
        assertEquals(AppError.Restricted, f.engine.saveProfile("a", old.copy(draft = old.draft.copy(nickname = "변경된본문"))).error())
    }

    @Test fun `deleting my encounter does not change the peers after eligibility or ability to request`() {
        val f = Fixture(); f.join("a"); f.join("b")
        val c = f.connect(); f.engine.leaveConnection("a", LeaveConnectionRequest(f.meta(), c)).ok()
        f.engine.deleteEncounter("a", DeleteEncounterRequest(f.meta(), f.snap("a").encounters.single().id)).ok()
        val theirs = f.snap("b").encounters.single()
        assertEquals(AfterEligibility.AVAILABLE, theirs.afterEligibility)
        val requested = f.engine.requestAfter("b", RequestAfterRequest(f.meta(), theirs.id, ContactIds(instagramId = java.util.UUID.randomUUID().toString().replace("-", "").take(24)))).ok()
        f.engine.respondAfter("a", RespondAfterRequest(f.meta(), requested.id, RequestDecision.ACCEPT, ContactIds(instagramId = java.util.UUID.randomUUID().toString().replace("-", "").take(24)))).ok()
        assertTrue(f.snap("a").encounters.isEmpty())
        assertEquals(1, f.snap("a").afterConnections.size)
    }

    @Test fun `old demo presence events cannot modify a replacement connection`() {
        val f = Fixture(demo = true); f.join("a")
        fun request() = f.engine.requestConnection("a", RequestConnectionRequest(f.meta(), "profile-demo-slow")).ok()
        request(); f.advance(4000)
        val old = f.snap("a").connections.single().id
        f.advance(1000)
        f.engine.leaveConnection("a", LeaveConnectionRequest(f.meta(), old)).ok()
        request(); f.advance(4000)
        val current = f.snap("a").connections.single { it.status == ConnectionStatus.CONNECTED }.id
        f.advance(7000)
        assertEquals(ConnectionStatus.CONNECTED, f.snap("a").connections.single { it.id == current }.status)
        f.advance(5000)
        assertEquals(ConnectionStatus.CHECKING, f.snap("a").connections.single { it.id == current }.status)
    }

    @Test fun `one large tick and frequent ticks produce identical demo state`() {
        fun setup() = Fixture(demo = true).also { f ->
            f.join("a"); f.signature("a")
            f.engine.requestConnection("a", RequestConnectionRequest(f.meta(), "profile-demo-slow")).ok()
        }
        val sparse = setup(); val frequent = setup()
        sparse.advance(50_000)
        repeat(500) { frequent.advance(100) }
        assertEquals(sparse.engine.exportState(), frequent.engine.exportState())
    }

    @Test fun `blocking closes pending requests and after relationships without leaking the block`() {
        val f = Fixture(); f.join("a"); f.join("b")
        val r = f.request()
        val blocked = f.engine.blockPeer("b", BlockPeerRequest(f.meta(), f.snap("a").profile!!.id)).ok()
        assertEquals(ConnectionRequestStatus.UNAVAILABLE, f.snap("a").connectionRequests.single().status)
        assertEquals(AppError.PeerUnavailable, f.engine.respondConnection("b", RespondConnectionRequest(f.meta(), r.id, RequestDecision.ACCEPT)).error())
        f.engine.unblockPeer("b", UnblockPeerRequest(f.meta(), blocked.id)).ok()
        val c = f.connect(); f.engine.leaveConnection("a", LeaveConnectionRequest(f.meta(), c)).ok()
        val after = f.engine.requestAfter("a", RequestAfterRequest(f.meta(), f.snap("a").encounters.single().id, ContactIds(instagramId = java.util.UUID.randomUUID().toString().replace("-", "").take(24)))).ok()
        val ac = f.engine.respondAfter("b", RespondAfterRequest(f.meta(), after.id, RequestDecision.ACCEPT, ContactIds(instagramId = java.util.UUID.randomUUID().toString().replace("-", "").take(24)))).ok().afterConnectionId!!
        f.engine.blockPeer("a", BlockPeerRequest(f.meta(), f.snap("b").profile!!.id)).ok()
        assertEquals(AfterCloseReason.BLOCKED, f.snap("a").afterConnections.single().closeReason)
        assertEquals(AfterCloseReason.PEER_LEFT, f.snap("b").afterConnections.single().closeReason)
    }

    @Test fun `cached discovery cannot replay retracted signatures or blocked candidates`() {
        val f = Fixture(); f.join("a"); f.join("b"); f.signature("b")
        val command = RefreshDiscoveryRequest(f.meta())
        assertNotNull(f.engine.refreshDiscovery("a", command).ok().candidates.single().profile.signature)
        f.engine.publishSignature("b", PublishSignatureRequest(f.meta(), null)).ok()
        assertNull(f.engine.refreshDiscovery("a", command).ok().candidates.single().profile.signature)
        f.engine.blockPeer("b", BlockPeerRequest(f.meta(), f.snap("a").profile!!.id)).ok()
        assertTrue(f.engine.refreshDiscovery("a", command).ok().candidates.isEmpty())
    }

    @Test fun `heartbeat changes only server time without inventing a room revision`() {
        val f = Fixture(); f.join("a")
        val before = f.snap("a")
        f.advance(1_000)
        assertEquals(before.copy(serverTimeEpochMillis = f.now), f.snap("a"))
    }

    @Test fun `registration and stored consents gate discovery and publication`() {
        val f = Fixture()
        val e = f.engine
        e.enterSession("a", EnterSessionRequest(f.meta(), SessionMode.SOLO, PersonaCode.ACCOUNT_A)).ok()
        assertEquals(AppError.Restricted, e.setDiscovery("a", SetDiscoveryRequest(f.meta(), DiscoveryMode.ON)).error())
        e.saveProfile("a", SaveProfileRequest(f.meta(), ProfileDraft("가상상대", AvatarId.SEA, emptySet()))).ok()
        assertEquals(RegistrationState.CONSENT_REQUIRED, f.snap("a").profile!!.registration)
        assertEquals(AppError.Restricted, e.recordConsent("a", RecordConsentRequest(f.meta(), ConsentScope.PUBLIC_PROFILE, ConsentAction.GRANT, "old")).error())
        ConsentScope.entries.forEach { e.recordConsent("a", RecordConsentRequest(f.meta(), it, ConsentAction.GRANT, f.policy.consentDocumentVersion)).ok() }
        e.setDiscovery("a", SetDiscoveryRequest(f.meta(), DiscoveryMode.ON)).ok()
        assertEquals(RegistrationState.READY, f.snap("a").profile!!.registration)
    }

    @Test fun `idempotency preserves identity rejects changed body and bounds durable results`() {
        val f = Fixture(policy = EnginePolicy(maximumCachedCommands = 8))
        f.join("a"); f.join("b")
        val body = RequestConnectionRequest(f.meta(), f.snap("b").profile!!.id)
        val first = f.engine.requestConnection("a", body)
        assertEquals(first, f.engine.requestConnection("a", body))
        assertEquals(AppError.Restricted, f.engine.requestConnection("a", body.copy(candidateId = "missing")).error())
        assertEquals(1, f.snap("a").connectionRequests.size)
        repeat(20) { f.engine.refreshDiscovery("a", RefreshDiscoveryRequest(f.meta())).ok() }
        assertEquals(8, f.engine.exportState().commands.size)
        val restored = Json.decodeFromString<EngineState>(Json.encodeToString(f.engine.exportState()))
        assertEquals(f.engine.exportState(), restored)
    }

    @Test fun `cancel and expiry are final and return actual terminal error`() {
        val f = Fixture(); f.join("a"); f.join("b")
        val cancelled = f.request()
        f.engine.cancelConnection("a", CancelConnectionRequest(f.meta(), cancelled.id)).ok()
        assertEquals(AppError.AlreadyClosed, f.engine.respondConnection("b", RespondConnectionRequest(f.meta(), cancelled.id, RequestDecision.ACCEPT)).error())
        f.advance(f.policy.requestCooldownMillis)
        val expired = f.request()
        f.advance(f.policy.requestTtlMillis)
        assertEquals(AppError.Expired, f.engine.respondConnection("b", RespondConnectionRequest(f.meta(), expired.id, RequestDecision.ACCEPT)).error())
        assertTrue(f.snap("a").encounters.isEmpty())
        assertTrue(f.snap("b").connections.isEmpty())
    }

    @Test fun `third parties get the same response for unknown and forbidden ids`() {
        val f = Fixture(); listOf("a", "b", "c").forEach(f::join)
        val r = f.request()
        fun respond(id: String) = f.engine.respondConnection("c", RespondConnectionRequest(f.meta(), id, RequestDecision.ACCEPT)).error()
        assertEquals(respond("unknown"), respond(r.id))
        val c = f.engine.respondConnection("b", RespondConnectionRequest(f.meta(), r.id, RequestDecision.ACCEPT)).ok().connectionId!!
        assertEquals(AppError.PeerUnavailable, f.engine.leaveConnection("c", LeaveConnectionRequest(f.meta(), c)).error())
        assertTrue(f.snap("c").connections.isEmpty())
    }

    @Test fun `blocking revokes both directions projects neutral reason and cannot revive`() {
        val f = Fixture(); f.join("a"); f.join("b"); f.signature("b")
        val c = f.connect()
        val b = f.engine.blockPeer("a", BlockPeerRequest(f.meta(), f.snap("b").profile!!.id)).ok()
        assertEquals(ConnectionEndReason.BLOCKED, f.snap("a").connections.single().endReason)
        assertEquals(ConnectionEndReason.PEER_LEFT, f.snap("b").connections.single().endReason)
        assertTrue(f.snap("a").discovery.candidates.isEmpty())
        assertTrue(f.snap("b").blocks.isEmpty())
        assertNull(f.snap("a").connections.single().peer.signature)
        f.engine.unblockPeer("a", UnblockPeerRequest(f.meta(), b.id)).ok()
        assertEquals(ConnectionStatus.ENDED, f.snap("a").connections.single().status)
        assertEquals(1, f.snap("a").encounters.size)
        assertEquals(1, f.snap("b").encounters.size)
    }

    @Test fun `encounter deletion is private and no tick duplicates records`() {
        val f = Fixture(); f.join("a"); f.join("b")
        val c = f.connect()
        f.engine.leaveConnection("a", LeaveConnectionRequest(f.meta(), c)).ok()
        repeat(5) { f.advance(1000) }
        assertEquals(ConnectionEndReason.SELF_LEFT, f.snap("a").connections.single().endReason)
        assertEquals(ConnectionEndReason.PEER_LEFT, f.snap("b").connections.single().endReason)
        f.engine.deleteEncounter("a", DeleteEncounterRequest(f.meta(), f.snap("a").encounters.single().id)).ok()
        assertTrue(f.snap("a").encounters.isEmpty())
        assertEquals(1, f.snap("b").encounters.size)
    }

    @Test fun `titles chemistry and all projections track current public visibility`() {
        val f = Fixture(); f.join("a"); f.join("b"); f.signature("a"); f.signature("b")
        assertEquals("GENTLE_RIDE", f.snap("a").discovery.candidates.single().profile.signature!!.title)
        assertEquals(1, f.snap("a").discovery.candidates.single().chemistry.lines.size)
        val body = RequestConnectionRequest(f.meta(), f.snap("b").profile!!.id)
        val request = f.engine.requestConnection("a", body).ok()
        val c = f.engine.respondConnection("b", RespondConnectionRequest(f.meta(), request.id, RequestDecision.ACCEPT)).ok().connectionId!!
        f.engine.publishSignature("b", PublishSignatureRequest(f.meta(), null)).ok()
        assertNull(f.engine.requestConnection("a", body).ok().peer.signature)
        assertTrue(f.snap("a").connections.single().chemistry.lines.isEmpty())
        assertNull(f.snap("a").connectionRequests.single().peer.signature)
        f.engine.leaveConnection("a", LeaveConnectionRequest(f.meta(), c)).ok()
        assertNull(f.snap("a").encounters.single().peer.signature)
    }

    @Test fun `synthetic exit grace distinguishes checking return distance and unknown`() {
        val f = Fixture(); f.join("a"); f.join("b"); f.connect()
        f.engine.setSyntheticPresence("b", SetSyntheticPresenceRequest(f.meta(), SyntheticPresence.AWAY)).ok()
        assertEquals(ConnectionStatus.CHECKING, f.snap("a").connections.single().status)
        f.advance(f.policy.exitGraceMillis - 1)
        f.engine.setSyntheticPresence("b", SetSyntheticPresenceRequest(f.meta(), SyntheticPresence.NEARBY)).ok()
        assertEquals(ConnectionStatus.CONNECTED, f.snap("a").connections.single().status)
        f.engine.setSyntheticPresence("b", SetSyntheticPresenceRequest(f.meta(), SyntheticPresence.STALE)).ok()
        f.advance(f.policy.statusUnknownGraceMillis)
        assertEquals(ConnectionEndReason.STATUS_UNKNOWN, f.snap("a").connections.single().endReason)
    }

    @Test fun `device location validates freshness applies hysteresis and never persists coordinates`() {
        val f = Fixture(); f.join("a"); f.join("b")
        fun location(actor: String, lat: Double, observed: Long = f.now) = f.engine.updateLocation(actor,
            UpdateLocationRequest(f.meta(), LocationInput(lat, 127.123456, observed, LocationSource.DEVICE)))
        location("a", 37.123456).ok(); location("b", 37.124456).ok()
        f.connect()
        location("b", 37.125706).ok()
        assertEquals(ConnectionStatus.CONNECTED, f.snap("a").connections.single().status)
        location("b", 37.127456).ok()
        assertEquals(ConnectionStatus.CHECKING, f.snap("a").connections.single().status)
        f.advance(f.policy.exitGraceMillis)
        assertEquals(ConnectionEndReason.DISTANCE, f.snap("a").connections.single().endReason)
        val saved = Json.encodeToString(f.engine.exportState())
        listOf("latitude", "longitude", "37.123456", "127.123456").forEach { assertFalse(saved.contains(it)) }
        assertEquals(AppError.Restricted, location("b", Double.NaN).error())
        assertEquals(AppError.Restricted, location("b", 37.0, f.now + 1).error())
    }

    @Test fun `restore forgets device presence and cannot accept using persisted freshness`() {
        val f = Fixture(); f.join("a"); f.join("b")
        listOf("a", "b").forEach { f.engine.updateLocation(it, UpdateLocationRequest(f.meta(), LocationInput(37.0, 127.0, f.now, LocationSource.DEVICE))).ok() }
        val r = f.request()
        val restored = ChayeonEngine(f.rules, EngineClock { f.now }, store = f.store, demoEnabled = false)
        assertEquals(LocationAvailability.STALE, restored.snapshotFor("a").ok().discovery.locationAvailability)
        assertEquals(AppError.PeerUnavailable, restored.respondConnection("b", RespondConnectionRequest(f.meta(), r.id, RequestDecision.ACCEPT)).error())
    }



    @Test fun `store failure rolls back mutation and does not publish a revision`() {
        var fail = false
        val memory = MemoryEngineStore()
        val store = object : EngineStore {
            override fun load() = memory.load()
            override fun save(state: EngineState) { if (fail) error("storage unavailable"); memory.save(state) }
        }
        val f = Fixture(store = store); f.join("a"); f.join("b")
        val before = f.engine.exportState(); fail = true
        assertEquals(AppError.Unknown, f.engine.requestConnection("a", RequestConnectionRequest(f.meta(), f.snap("b").profile!!.id)).error())
        assertEquals(before, f.engine.exportState())
        assertEquals(before.revision, f.engine.revision.value)
    }

    @Test fun `demo cast script is deterministic across sparse ticks and covers all outcomes`() {
        val f = Fixture(demo = true); f.join("a"); f.signature("a")
        assertEquals(5, f.snap("a").discovery.candidates.size)
        assertTrue(f.snap("a").discovery.candidates.all { it.profile.kind == ProfileKind.DEMO_PEER && it.profile.signature!!.source == DataSource.SYNTHETIC })
        fun requestDemo(id: String) = f.engine.requestConnection("a", RequestConnectionRequest(f.meta(), "profile-$id")).ok()
        requestDemo("demo-sea"); f.advance(4000)
        assertEquals(ConnectionRequestStatus.DECLINED, f.snap("a").connectionRequests.single().status)
        f.advance(3000)
        val incoming = f.snap("a").connectionRequests.single { it.direction == RequestDirection.INCOMING }
        f.engine.respondConnection("a", RespondConnectionRequest(f.meta(), incoming.id, RequestDecision.DECLINE)).ok()
        requestDemo("demo-forest"); f.advance(f.policy.requestTtlMillis)
        assertTrue(f.snap("a").connectionRequests.any { it.status == ConnectionRequestStatus.EXPIRED })
        requestDemo("demo-slow"); f.advance(4000); f.advance(1000)
        f.advance(11_000)
        assertEquals(ConnectionStatus.CHECKING, f.snap("a").connections.single().status)
        f.advance(4_000)
        assertEquals(ConnectionStatus.CONNECTED, f.snap("a").connections.single().status)
        f.advance(24_000)
        assertEquals(ConnectionEndReason.DISTANCE, f.snap("a").connections.single().endReason)
        val encounter = f.snap("a").encounters.single()
        val after = f.engine.requestAfter("a", RequestAfterRequest(f.meta(), encounter.id, ContactIds(instagramId = java.util.UUID.randomUUID().toString().replace("-", "").take(24)))).ok()
        f.advance(4000)
        assertEquals(AfterRequestStatus.ACCEPTED, f.snap("a").afterRequests.single { it.id == after.id }.status)
    }
}
