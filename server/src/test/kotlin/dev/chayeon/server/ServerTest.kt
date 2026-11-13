package dev.chayeon.server

import dev.chayeon.core.engine.*
import dev.chayeon.core.model.*
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.atomic.AtomicLong

private val json = EngineJson
private fun meta() = CommandMeta(UUID.randomUUID().toString())
private fun <T> AppResult<T>.ok(): T = (this as? AppResult.Success<T>)?.value ?: error("Expected success")
private val fakeRules = object : SignatureRules {
    override fun title(traits: List<PublicTrait>) = "차분히 함께하는 드라이버"
    override fun chemistry(first: PublicSignature, second: PublicSignature) = Chemistry()
}

private class Api(val client: HttpClient) {
    val responses = mutableListOf<Pair<String?, String>>()
    suspend inline fun <reified R, reified S> post(session: Session?, route: String, request: R): S {
        val response = client.post(route) {
            contentType(ContentType.Application.Json)
            if (session != null) bearerAuth(session.accessToken!!)
            setBody(json.encodeToString(request))
        }
        val text = response.bodyAsText()
        synchronized(responses) { responses += session?.accountId to text }
        return json.decodeFromString(text)
    }
    suspend fun enter(persona: PersonaCode, room: String = "room"): Session =
        post<EnterSessionRequest, EnterSessionResponse>(null, ApiRoutes.ENTER_SESSION,
            EnterSessionRequest(meta(), SessionMode.SERVER_DEMO, persona, room, "test-install-secret".repeat(3))).result.ok()
    suspend fun ready(session: Session) {
        post<SaveProfileRequest, SaveProfileResponse>(session, ApiRoutes.SAVE_PROFILE,
            SaveProfileRequest(meta(), ProfileDraft("가상프로필", AvatarId.DAWN, setOf(InterestId.JAZZ)))).result.ok()
        ConsentScope.entries.forEach { scope -> post<RecordConsentRequest, RecordConsentResponse>(session, ApiRoutes.RECORD_CONSENT,
            RecordConsentRequest(meta(), scope, ConsentAction.GRANT, session.policy.consentDocumentVersion)).result.ok() }
        post<SetDiscoveryRequest, SetDiscoveryResponse>(session, ApiRoutes.SET_DISCOVERY, SetDiscoveryRequest(meta(), DiscoveryMode.ON)).result.ok()
    }
    suspend fun sync(session: Session, since: Long = 0): SyncSnapshot {
        val response = client.get(ApiRoutes.SYNC) { bearerAuth(session.accessToken!!); parameter("since", since) }.bodyAsText()
        synchronized(responses) { responses += session.accountId to response }
        return json.decodeFromString<SyncResponse>(response).result.ok()
    }
    suspend fun request(a: Session, b: Session) = post<RequestConnectionRequest, RequestConnectionResponse>(a, ApiRoutes.REQUEST_CONNECTION,
        RequestConnectionRequest(meta(), sync(b).profile!!.id)).result.ok()
    suspend fun accept(b: Session, id: String) = post<RespondConnectionRequest, RespondConnectionResponse>(b, ApiRoutes.RESPOND_CONNECTION,
        RespondConnectionRequest(meta(), id, RequestDecision.ACCEPT)).result
    suspend fun connect(a: Session, b: Session): String = accept(b, request(a, b).id).ok().connectionId!!
}

private suspend fun <T> race(first: suspend () -> T, second: suspend () -> T): List<T> = coroutineScope {
    val barrier = CyclicBarrier(2)
    listOf(first, second).map { action -> async(Dispatchers.IO) { barrier.await(); action() } }.awaitAll()
}

class ServerTest {
    @Test fun mutualContactExchangeKeepsEveryPendingResponsePrivateAndClosesWithoutReplay() = testApplication {
        val registry = RoomRegistry(fakeRules, demoEnabled = false)
        application { chayeonModule(registry) }
        val api = Api(client)
        val a = api.enter(PersonaCode.ACCOUNT_A)
        val b = api.enter(PersonaCode.ACCOUNT_B)
        api.ready(a); api.ready(b)
        val connection = api.connect(a, b)
        api.post<LeaveConnectionRequest, LeaveConnectionResponse>(a, ApiRoutes.LEAVE_CONNECTION, LeaveConnectionRequest(meta(), connection)).result.ok()
        val mine = ContactIds(instagramId = UUID.randomUUID().toString().replace("-", "").take(24))
        val theirs = ContactIds(kakaoTalkId = UUID.randomUUID().toString().replace("-", "").take(16))
        val request = RequestAfterRequest(meta(), api.sync(a).encounters.single().id, mine)
        val pending = api.post<RequestAfterRequest, RequestAfterResponse>(a, ApiRoutes.REQUEST_AFTER, request).result.ok()
        api.sync(a); api.sync(b)
        assertTrue(api.responses.none { it.second.contains(mine.instagramId!!) })
        val empty = api.post<RespondAfterRequest, RespondAfterResponse>(b, ApiRoutes.RESPOND_AFTER,
            RespondAfterRequest(meta(), pending.id, RequestDecision.ACCEPT)).result
        assertEquals(AppResult.Failure(AppError.Restricted), empty)
        val accepted = api.post<RespondAfterRequest, RespondAfterResponse>(b, ApiRoutes.RESPOND_AFTER,
            RespondAfterRequest(meta(), pending.id, RequestDecision.ACCEPT, theirs)).result.ok()
        assertTrue(api.responses.none { it.second.contains(mine.instagramId!!) || it.second.contains(theirs.kakaoTalkId!!) })
        assertEquals(theirs, api.sync(a).afterConnections.single().sharedContacts)
        assertEquals(mine, api.sync(b).afterConnections.single().sharedContacts)
        val end = EndAfterConnectionRequest(meta(), accepted.afterConnectionId!!)
        api.post<EndAfterConnectionRequest, EndAfterConnectionResponse>(a, ApiRoutes.END_AFTER_CONNECTION, end).result.ok()
        val cached = api.post<EndAfterConnectionRequest, EndAfterConnectionResponse>(a, ApiRoutes.END_AFTER_CONNECTION, end).result.ok()
        assertNull(cached.sharedContacts)
        assertNull(api.sync(b).afterConnections.single().sharedContacts)
        for (route in listOf("/v1/after/message", "/v1/connection/greeting")) {
            assertEquals(HttpStatusCode.NotFound, client.post(route) { bearerAuth(a.accessToken!!) }.status)
        }
    }

    @Test fun acceptingAndWithdrawingAnAfterRequestCannotPartiallyExchangeContacts() = testApplication {
        val registry = RoomRegistry(fakeRules, demoEnabled = false)
        application { chayeonModule(registry) }
        val api = Api(client)
        val a = api.enter(PersonaCode.ACCOUNT_A); val b = api.enter(PersonaCode.ACCOUNT_B)
        api.ready(a); api.ready(b)
        val connection = api.connect(a, b)
        api.post<LeaveConnectionRequest, LeaveConnectionResponse>(a, ApiRoutes.LEAVE_CONNECTION, LeaveConnectionRequest(meta(), connection)).result.ok()
        val mine = ContactIds(instagramId = UUID.randomUUID().toString().replace("-", "").take(24))
        val theirs = ContactIds(instagramId = UUID.randomUUID().toString().replace("-", "").take(24))
        val pending = api.post<RequestAfterRequest, RequestAfterResponse>(a, ApiRoutes.REQUEST_AFTER,
            RequestAfterRequest(meta(), api.sync(a).encounters.single().id, mine)).result.ok()
        val results = race(
            { api.post<RespondAfterRequest, RespondAfterResponse>(b, ApiRoutes.RESPOND_AFTER, RespondAfterRequest(meta(), pending.id, RequestDecision.ACCEPT, theirs)).result },
            { api.post<WithdrawAfterRequest, WithdrawAfterResponse>(a, ApiRoutes.WITHDRAW_AFTER, WithdrawAfterRequest(meta(), pending.id)).result },
        )
        assertEquals(1, results.count { it is AppResult.Success })
        val first = api.sync(a).afterConnections
        val second = api.sync(b).afterConnections
        assertEquals(first.isEmpty(), second.isEmpty())
        if (first.isNotEmpty()) {
            assertEquals(theirs, first.single().sharedContacts)
            assertEquals(mine, second.single().sharedContacts)
        }
    }

    @Test fun defaultModuleEnforcesSixtySecondPresenceLeaseWithoutExposingItsDeadline() = testApplication {
        lateinit var registry: RoomRegistry
        application { chayeonModule(); registry = attributes[roomRegistryKey] }
        val api = Api(client)
        val session = api.enter(PersonaCode.ACCOUNT_A)
        val engine = registry.authorize("Bearer ${session.accessToken}")!!.second
        assertEquals(60_000L, engine.policy.presenceLeaseMillis)
        val deadline = engine.exportState().accounts.getValue(session.accountId).presenceLeaseDeadline!!
        assertTrue(deadline - System.currentTimeMillis() in 55_000..60_000)
        val snapshotBody = client.get(ApiRoutes.SYNC) { bearerAuth(session.accessToken!!); parameter("since", 0) }.bodyAsText()
        assertTrue(json.decodeFromString<SyncResponse>(snapshotBody).result is AppResult.Success)
        assertFalse(snapshotBody.contains("lease", ignoreCase = true))
        assertTrue(api.responses.none { it.second.contains("lease", ignoreCase = true) })
    }

    @Test fun expiryWithoutHolderTrafficWakesAnAlreadyWaitingPeerLongPoll() = testApplication {
        val clock = AtomicLong(1_000_000)
        val registry = RoomRegistry(fakeRules, ServerPolicy(tickIntervalMillis = 10), EngineClock(clock::get),
            EnginePolicy(presenceLeaseMillis = 60_000), demoEnabled = false)
        application { chayeonModule(registry) }
        val api = Api(client); val a = api.enter(PersonaCode.ACCOUNT_A); val b = api.enter(PersonaCode.ACCOUNT_B)
        api.ready(a); api.ready(b); api.connect(a, b)
        clock.addAndGet(30_000)
        val before = api.sync(b)
        coroutineScope {
            val waiting = async(Dispatchers.IO) { api.sync(b, before.version) }
            delay(100)
            api.post<SetSyntheticPresenceRequest, SetSyntheticPresenceResponse>(b, ApiRoutes.SET_SYNTHETIC_PRESENCE,
                SetSyntheticPresenceRequest(meta(), SyntheticPresence.NEARBY)).result.ok()
            delay(100)
            assertFalse("A renewal must not wake the peer poll", waiting.isCompleted)
            clock.addAndGet(30_000)
            val checking = withTimeout(2_000) { waiting.await() }
            assertEquals(ConnectionStatus.CHECKING, checking.connections.single().status)
            val ending = async(Dispatchers.IO) { api.sync(b, checking.version) }
            delay(100)
            assertFalse(ending.isCompleted)
            clock.addAndGet(10_000)
            val ended = withTimeout(2_000) { ending.await() }
            assertEquals(ConnectionEndReason.DISTANCE, ended.connections.single().endReason)
        }
    }

    @Test fun blockedPeerProfileEditsDoNotWakeLongPoll() = testApplication {
        application { chayeonModule(RoomRegistry(fakeRules, ServerPolicy(pollTimeoutMillis = 2_000), demoEnabled = false)) }
        val api = Api(client); val a = api.enter(PersonaCode.ACCOUNT_A); val b = api.enter(PersonaCode.ACCOUNT_B)
        api.ready(a); api.ready(b); api.connect(a, b)
        api.post<BlockPeerRequest, BlockPeerResponse>(a, ApiRoutes.BLOCK_PEER,
            BlockPeerRequest(meta(), api.sync(b).profile!!.id)).result.ok()
        val before = api.sync(b)
        coroutineScope {
            val poll = async(Dispatchers.IO) { api.sync(b, before.version) }
            delay(100)
            api.post<SaveProfileRequest, SaveProfileResponse>(a, ApiRoutes.SAVE_PROFILE,
                SaveProfileRequest(meta(), ProfileDraft("바뀐프로필", AvatarId.STAR, emptySet()))).result.ok()
            delay(200)
            assertFalse("Hidden activity woke the peer poll", poll.isCompleted)
            api.post<UpdateSettingsRequest, UpdateSettingsResponse>(b, ApiRoutes.UPDATE_SETTINGS,
                UpdateSettingsRequest(CommandMeta(UUID.randomUUID().toString(), before.version), false, false)).result.ok()
            val changed = withTimeout(1_000) { poll.await() }
            assertEquals(before.version + 1, changed.version)
            assertEquals(before.connections.single().peer.copy(version = changed.version), changed.connections.single().peer)
        }
    }

    @Test fun `idle rooms expire tokens and free bounded room capacity`() = testApplication {
        val clock = AtomicLong(1_000_000)
        val registry = RoomRegistry(fakeRules, ServerPolicy(maximumRooms = 1, idleRoomMillis = 1000), EngineClock(clock::get), demoEnabled = false)
        application { chayeonModule(registry) }
        val api = Api(client); val old = api.enter(PersonaCode.ACCOUNT_A)
        clock.addAndGet(1000); registry.tick()
        assertEquals(HttpStatusCode.Unauthorized, client.get(ApiRoutes.SYNC) { bearerAuth(old.accessToken!!) }.status)
        val next = api.enter(PersonaCode.ACCOUNT_A, "next")
        assertNotEquals(old.id, next.id)
    }

    @Test fun `lost lifecycle responses can retry only their exact receipt after token revocation`() = testApplication {
        application { chayeonModule(RoomRegistry(fakeRules, demoEnabled = false)) }
        val api = Api(client)
        for (delete in listOf(false, true)) {
            val a = api.enter(PersonaCode.ACCOUNT_A, if (delete) "delete" else "signout")
            val request = SignOutRequest(meta())
            val route = if (delete) ApiRoutes.DELETE_ACCOUNT else ApiRoutes.SIGN_OUT
            val first = api.post<SignOutRequest, SignOutResponse>(a, route, request)
            first.result.ok()
            assertEquals(first, api.post<SignOutRequest, SignOutResponse>(a, route, request))
            assertEquals(HttpStatusCode.Unauthorized, client.get(ApiRoutes.SYNC) { bearerAuth(a.accessToken!!) }.status)
            assertEquals(AppResult.Failure(AppError.NotAuthorized), api.post<SignOutRequest, SignOutResponse>(a, route, SignOutRequest(meta())).result)
        }
    }

    @Test fun `two recipients accepting the same sender cannot both connect`() = testApplication {
        val registry = RoomRegistry(fakeRules)
        application { chayeonModule(registry) }
        val api = Api(client); val a = api.enter(PersonaCode.ACCOUNT_A); val b = api.enter(PersonaCode.ACCOUNT_B)
        api.ready(a); api.ready(b)
        val engine = registry.authorize("Bearer ${a.accessToken}")!!.second
        val ra = engine.requestConnection("demo-slow", RequestConnectionRequest(meta(), api.sync(a).profile!!.id)).ok()
        val rb = engine.requestConnection("demo-slow", RequestConnectionRequest(meta(), api.sync(b).profile!!.id)).ok()
        assertEquals(1, race({ api.accept(a, ra.id) }, { api.accept(b, rb.id) }).count { it is AppResult.Success })
        assertEquals(1, engine.snapshotFor("demo-slow").ok().connections.count { it.status != ConnectionStatus.ENDED })
    }

    @Test fun `every private route rejects absent and invalid tokens before parsing bodies`() = testApplication {
        application { chayeonModule(RoomRegistry(fakeRules, demoEnabled = false)) }
        COMMAND_ROUTES.forEach { route ->
            listOf<String?>(null, "invalid").forEach { token ->
                val response = client.post(route) { if (token != null) bearerAuth(token); setBody("unparseable") }
                assertEquals(route, HttpStatusCode.Unauthorized, response.status)
                assertEquals(AppResult.Failure(AppError.NotAuthorized), json.decodeFromString<SyncResponse>(response.bodyAsText()).result)
            }
        }
        assertEquals(HttpStatusCode.Unauthorized, client.get(ApiRoutes.SYNC).status)
    }

    @Test fun `rooms are isolated and forbidden records are indistinguishable from missing records`() = testApplication {
        application { chayeonModule(RoomRegistry(fakeRules, demoEnabled = false)) }
        val api = Api(client)
        val a = api.enter(PersonaCode.ACCOUNT_A); val b = api.enter(PersonaCode.ACCOUNT_B)
        val stranger = api.enter(PersonaCode.ACCOUNT_A, "other")
        listOf(a, b, stranger).forEach { api.ready(it) }
        assertTrue(api.sync(stranger).discovery.candidates.isEmpty())
        val request = api.request(a, b)
        assertEquals(api.accept(stranger, "unknown"), api.accept(stranger, request.id))
        val connection = api.accept(b, request.id).ok().connectionId!!
        api.post<LeaveConnectionRequest, LeaveConnectionResponse>(a, ApiRoutes.LEAVE_CONNECTION, LeaveConnectionRequest(meta(), connection)).result.ok()
        val mine = api.sync(a).encounters.single().id
        suspend fun delete(id: String) = api.post<DeleteEncounterRequest, DeleteEncounterResponse>(b, ApiRoutes.DELETE_ENCOUNTER, DeleteEncounterRequest(meta(), id)).result
        assertEquals(delete("unknown"), delete(mine))
        assertEquals(1, api.sync(a).encounters.size)
        assertTrue(api.sync(stranger).connections.isEmpty())
    }

    @Test fun `double accept with real concurrency establishes exactly one connection`() = testApplication {
        application { chayeonModule(RoomRegistry(fakeRules, demoEnabled = false)) }
        val api = Api(client); val a = api.enter(PersonaCode.ACCOUNT_A); val b = api.enter(PersonaCode.ACCOUNT_B)
        api.ready(a); api.ready(b)
        val r = api.request(a, b)
        val results = race({ api.accept(b, r.id) }, { api.accept(b, r.id) })
        assertEquals(1, results.count { it is AppResult.Success })
        assertEquals(1, api.sync(a).connections.size)
        assertEquals(ConnectionRequestStatus.ACCEPTED, api.sync(b).connectionRequests.single().status)
    }

    @Test fun `accept versus cancel has one terminal winner under real concurrency`() = testApplication {
        application { chayeonModule(RoomRegistry(fakeRules, demoEnabled = false)) }
        val api = Api(client); val a = api.enter(PersonaCode.ACCOUNT_A); val b = api.enter(PersonaCode.ACCOUNT_B)
        api.ready(a); api.ready(b)
        val r = api.request(a, b)
        val results = race({ api.accept(b, r.id) }, {
            api.post<CancelConnectionRequest, CancelConnectionResponse>(a, ApiRoutes.CANCEL_CONNECTION, CancelConnectionRequest(meta(), r.id)).result
        })
        assertEquals(1, results.count { it is AppResult.Success })
        val snapshot = api.sync(a)
        assertEquals(if (snapshot.connectionRequests.single().status == ConnectionRequestStatus.ACCEPTED) 1 else 0, snapshot.connections.size)
        assertTrue(snapshot.encounters.isEmpty())
    }

    @Test fun `accept versus expiry and late accept use the committed terminal state`() = testApplication {
        val clock = AtomicLong(1_000_000)
        val registry = RoomRegistry(fakeRules, clock = EngineClock(clock::get), demoEnabled = false)
        application { chayeonModule(registry) }
        val api = Api(client); val a = api.enter(PersonaCode.ACCOUNT_A); val b = api.enter(PersonaCode.ACCOUNT_B)
        api.ready(a); api.ready(b)
        val r = api.request(a, b)
        race({ api.accept(b, r.id) }, { clock.set(r.expiresAtEpochMillis); registry.tick(); api.accept(b, r.id) })
        val snapshot = api.sync(a)
        val terminal = snapshot.connectionRequests.single().status
        assertTrue(terminal in setOf(ConnectionRequestStatus.ACCEPTED, ConnectionRequestStatus.EXPIRED))
        assertEquals(if (terminal == ConnectionRequestStatus.ACCEPTED) 1 else 0, snapshot.connections.size)
        assertEquals(AppResult.Failure(if (terminal == ConnectionRequestStatus.EXPIRED) AppError.Expired else AppError.AlreadyClosed), api.accept(b, r.id))
    }

    @Test fun `two accounts accepting different incoming requests concurrently do not overwrite each other`() = testApplication {
        val registry = RoomRegistry(fakeRules)
        application { chayeonModule(registry) }
        val api = Api(client); val a = api.enter(PersonaCode.ACCOUNT_A); val b = api.enter(PersonaCode.ACCOUNT_B)
        api.ready(a); api.ready(b)
        val engine = registry.authorize("Bearer ${a.accessToken}")!!.second
        val ra = engine.requestConnection("demo-slow", RequestConnectionRequest(meta(), api.sync(a).profile!!.id)).ok()
        val rb = engine.requestConnection("demo-sea", RequestConnectionRequest(meta(), api.sync(b).profile!!.id)).ok()
        assertEquals(2, race({ api.accept(a, ra.id) }, { api.accept(b, rb.id) }).count { it is AppResult.Success })
        assertEquals(1, api.sync(a).connections.count { it.status != ConnectionStatus.ENDED })
        assertEquals(1, api.sync(b).connections.count { it.status != ConnectionStatus.ENDED })
    }

    @Test fun `duplicate commands reordered versions and block revocation converge`() = testApplication {
        application { chayeonModule(RoomRegistry(fakeRules, demoEnabled = false)) }
        val api = Api(client); val a = api.enter(PersonaCode.ACCOUNT_A); val b = api.enter(PersonaCode.ACCOUNT_B)
        api.ready(a); api.ready(b)
        val command = RequestConnectionRequest(meta(), api.sync(b).profile!!.id)
        val first = api.post<RequestConnectionRequest, RequestConnectionResponse>(a, ApiRoutes.REQUEST_CONNECTION, command)
        assertEquals(first, api.post<RequestConnectionRequest, RequestConnectionResponse>(a, ApiRoutes.REQUEST_CONNECTION, command))
        val r = first.result.ok()
        api.accept(b, r.id).ok()
        val old = RespondConnectionRequest(CommandMeta(UUID.randomUUID().toString(), r.version), r.id, RequestDecision.DECLINE)
        assertEquals(AppResult.Failure(AppError.AlreadyClosed), api.post<RespondConnectionRequest, RespondConnectionResponse>(b, ApiRoutes.RESPOND_CONNECTION, old).result)
        api.post<BlockPeerRequest, BlockPeerResponse>(a, ApiRoutes.BLOCK_PEER, BlockPeerRequest(meta(), api.sync(b).profile!!.id)).result.ok()
        assertEquals(ConnectionEndReason.BLOCKED, api.sync(a).connections.single().endReason)
        val theirs = api.sync(b)
        assertEquals(ConnectionEndReason.PEER_LEFT, theirs.connections.single().endReason)
        assertTrue(theirs.blocks.isEmpty())
        assertTrue(theirs.discovery.candidates.isEmpty())
    }

    @Test fun `long poll wakes on change and full scenario responses contain no private data`() = testApplication {
        val registry = RoomRegistry(fakeRules, demoEnabled = false)
        application { chayeonModule(registry) }
        val api = Api(client); val a = api.enter(PersonaCode.ACCOUNT_A); val b = api.enter(PersonaCode.ACCOUNT_B)
        api.ready(a); api.ready(b)
        listOf(a, b).forEach { s ->
            api.post<UpdateLocationRequest, UpdateLocationResponse>(s, ApiRoutes.UPDATE_LOCATION,
                UpdateLocationRequest(meta(), LocationInput(37.123456, 127.123456, System.currentTimeMillis(), LocationSource.DEVICE))).result.ok()
        }
        val version = api.sync(b).version
        val request = coroutineScope {
            val poll = async(Dispatchers.IO) { api.sync(b, version) }
            val requested = api.request(a, b)
            val changed = withTimeout(2_000) { poll.await() }
            assertTrue(changed.version > version)
            requested
        }
        val c = api.accept(b, request.id).ok().connectionId!!
        api.post<LeaveConnectionRequest, LeaveConnectionResponse>(a, ApiRoutes.LEAVE_CONNECTION, LeaveConnectionRequest(meta(), c)).result.ok()
        val encounter = api.sync(a).encounters.single().id
        val after = api.post<RequestAfterRequest, RequestAfterResponse>(a, ApiRoutes.REQUEST_AFTER, RequestAfterRequest(meta(), encounter, ContactIds(instagramId = java.util.UUID.randomUUID().toString().replace("-", "").take(24)))).result.ok()
        val ac = api.post<RespondAfterRequest, RespondAfterResponse>(b, ApiRoutes.RESPOND_AFTER, RespondAfterRequest(meta(), after.id, RequestDecision.ACCEPT, ContactIds(instagramId = java.util.UUID.randomUUID().toString().replace("-", "").take(24)))).result.ok().afterConnectionId!!
        api.sync(b)
        val forbidden = setOf("latitude", "longitude", "coordinates", "distance", "trajectory", "speed", "metrics", "sampleCount", "validTripCount", "rawSignal", "vin", "crn", "plate")
        fun inspect(value: JsonElement) {
            when (value) {
                is JsonObject -> { assertTrue(value.keys.intersect(forbidden).isEmpty()); value.values.forEach(::inspect) }
                is JsonArray -> value.forEach(::inspect)
                else -> Unit
            }
        }
        api.responses.forEach { (owner, response) ->
            inspect(json.parseToJsonElement(response))
            assertFalse(response.contains("37.123456")); assertFalse(response.contains("127.123456"))
            if (owner == a.accountId) assertFalse(response.contains(b.accessToken!!))
            if (owner == b.accountId) assertFalse(response.contains(a.accessToken!!))
        }
    }

    @Test fun `body and room limits are bounded and deleted account token is revoked`() = testApplication {
        val registry = RoomRegistry(fakeRules, ServerPolicy(maximumBodyBytes = 1024, maximumRooms = 1), demoEnabled = false)
        application { chayeonModule(registry) }
        val api = Api(client); val a = api.enter(PersonaCode.ACCOUNT_A)
        val response = client.post(ApiRoutes.SAVE_PROFILE) { bearerAuth(a.accessToken!!); setBody("x".repeat(1025)) }
        assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        assertEquals(AppResult.Failure(AppError.Restricted), api.post<EnterSessionRequest, EnterSessionResponse>(null, ApiRoutes.ENTER_SESSION,
            EnterSessionRequest(meta(), SessionMode.SERVER_DEMO, PersonaCode.ACCOUNT_A, "extra", "test-install-secret".repeat(3))).result)
        api.post<DeleteAccountRequest, DeleteAccountResponse>(a, ApiRoutes.DELETE_ACCOUNT, DeleteAccountRequest(meta())).result.ok()
        assertEquals(HttpStatusCode.Unauthorized, client.get(ApiRoutes.SYNC) { bearerAuth(a.accessToken!!) }.status)
        val fresh = api.enter(PersonaCode.ACCOUNT_A)
        assertNotEquals(a.accountId, fresh.accountId)
        assertTrue(api.sync(fresh).connections.isEmpty())
    }
}
