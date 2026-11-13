package dev.chayeon.server

import dev.chayeon.core.engine.EngineClock
import dev.chayeon.core.engine.EngineJson
import dev.chayeon.core.model.*
import java.nio.file.Files
import java.util.UUID
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class RoomCapacityTest {
    private fun meta() = CommandMeta(UUID.randomUUID().toString())
    private fun secret(index: Int) = "install-$index".padEnd(43, 'x')
    private fun enter(registry: RoomRegistry, index: Int, room: String? = null) =
        registry.enter(EnterSessionRequest(meta(), SessionMode.SERVER_DEMO, room, secret(index)))
    private fun <T> AppResult<T>.ok() = (this as AppResult.Success<T>).value
    private fun header(session: Session) = "Bearer ${session.accessToken}"

    @Test fun fiftyActiveAccountsBlockEntryButFiftyIdleAccountsReleaseSeats() {
        var now = 0L
        val registry = RoomRegistry(defaultRules(), clock = EngineClock { now }, demoEnabled = false)
        val first = (0 until 50).map { enter(registry, it).ok() }.first()
        assertEquals(AppResult.Failure(AppError.Restricted), enter(registry, 50))
        registry.execute(header(first), ApiRoutes.SAVE_PROFILE,
            EngineJson.encodeToString(SaveProfileRequest(meta(), ProfileDraft("돌아온사람", AvatarId.STAR, emptySet()))))
        now = 1_800_001
        registry.tick()
        enter(registry, 50).ok()
        val returned = enter(registry, 0).ok()
        assertEquals(first.accountId, returned.accountId)
        assertEquals("돌아온사람", registry.snapshot(header(returned))!!.ok().profile!!.publicProfile.nickname)
    }

    @Test fun storedAccountLimitStopsEndlessNewAccountsEvenWhenSeatsAreFree() {
        var now = 1_800_000_000_000L
        val registry = RoomRegistry(defaultRules(), ServerPolicy(roomCapacity = 3, roomAccountLimit = 3), clock = EngineClock { now }, demoEnabled = false)
        (0 until 3).forEach { enter(registry, it).ok() }
        now += 1_800_001
        assertEquals(AppResult.Failure(AppError.Restricted), enter(registry, 3))
        enter(registry, 0).ok()
    }

    @Test fun signedOutAccountsReleaseSeatsButCannotReturnToAFullRoom() {
        val registry = RoomRegistry(defaultRules(), policy = ServerPolicy(roomCapacity = 1), demoEnabled = false)
        val first = enter(registry, 0).ok()
        val response = registry.execute(header(first), ApiRoutes.SIGN_OUT, EngineJson.encodeToString(SignOutRequest(meta())))!!
        EngineJson.decodeFromString<SignOutResponse>(response).result.ok()
        val second = enter(registry, 1).ok()
        assertEquals(AppResult.Failure(AppError.Restricted), enter(registry, 0))
        registry.execute(header(second), ApiRoutes.SIGN_OUT, EngineJson.encodeToString(SignOutRequest(meta())))
        assertEquals(first.accountId, enter(registry, 0).ok().accountId)
    }

    @Test fun authenticatedSyncAndHeartbeatRenewOnlyTheirOwnSeat() {
        var now = 0L
        val registry = RoomRegistry(defaultRules(), policy = ServerPolicy(roomCapacity = 2, idleRoomMillis = 100),
            clock = EngineClock { now }, demoEnabled = false)
        val first = enter(registry, 0).ok()
        val second = enter(registry, 1).ok()
        now = 80
        registry.snapshot(header(first))!!.ok()
        now = 101
        enter(registry, 2).ok()
        assertEquals(AppResult.Failure(AppError.Restricted), enter(registry, 1))
        assertNull(registry.authorize(header(second)))
        now = 170
        assertNotNull(registry.execute(header(first), ApiRoutes.SET_SYNTHETIC_PRESENCE,
            EngineJson.encodeToString(SetSyntheticPresenceRequest(meta(), SyntheticPresence.NEARBY))))
        now = 190
        assertEquals(AppResult.Failure(AppError.Restricted), enter(registry, 3))
        assertNotNull(registry.snapshot(header(first)))
    }

    @Test fun fileRestartPreservesActivityDeadlinesAndInactiveData() {
        var now = 0L
        val policy = ServerPolicy(dataDirectory = Files.createTempDirectory("seat-restart-test"), roomCapacity = 2, idleRoomMillis = 100)
        fun registry() = RoomRegistry(defaultRules(), policy = policy, clock = EngineClock { now }, demoEnabled = false)
        val first = registry()
        val a = enter(first, 0).ok()
        val b = enter(first, 1).ok()
        val engine = first.authorize(header(a))!!.second
        for (session in listOf(a, b)) {
            engine.saveProfile(session.accountId, SaveProfileRequest(meta(), ProfileDraft("인연", AvatarId.DAWN, emptySet()))).ok()
            ConsentScope.entries.forEach { engine.recordConsent(session.accountId,
                RecordConsentRequest(meta(), it, ConsentAction.GRANT, session.policy.consentDocumentVersion)).ok() }
            engine.setDiscovery(session.accountId, SetDiscoveryRequest(meta(), DiscoveryMode.ON)).ok()
        }
        val request = engine.requestConnection(a.accountId, RequestConnectionRequest(meta(), engine.exportState().accounts.getValue(b.accountId).profile.id)).ok()
        val connection = engine.respondConnection(b.accountId, RespondConnectionRequest(meta(), request.id, RequestDecision.ACCEPT)).ok().connectionId!!
        engine.leaveConnection(a.accountId, LeaveConnectionRequest(meta(), connection)).ok()
        now = 80
        first.snapshot(header(a))!!.ok()
        now = 90
        assertEquals(AppResult.Failure(AppError.Restricted), enter(registry(), 2))
        now = 101
        val second = registry()
        val newcomer = enter(second, 2).ok()
        assertEquals(AppResult.Failure(AppError.Restricted), enter(second, 1))
        second.execute(header(newcomer), ApiRoutes.SIGN_OUT, EngineJson.encodeToString(SignOutRequest(meta())))
        val third = registry()
        val returned = enter(third, 1).ok()
        assertEquals(b.accountId, returned.accountId)
        val snapshot = third.snapshot(header(returned))!!.ok()
        assertEquals("인연", snapshot.profile!!.publicProfile.nickname)
        val after = third.authorize(header(returned))!!.second.requestAfter(returned.accountId,
            RequestAfterRequest(meta(), snapshot.encounters.single().id, ContactIds(instagramId = "sample_peer")))
        after.ok()
        now = 300
        val fourth = registry()
        enter(fourth, 3).ok()
        assertEquals(b.accountId, enter(fourth, 1).ok().accountId)
    }

    @Test fun fiftyIdleAccountsReleaseSeatsAcrossFileStoreRestart() {
        var now = 0L
        val policy = ServerPolicy(dataDirectory = Files.createTempDirectory("full-room-restart-test"))
        fun registry() = RoomRegistry(defaultRules(), policy, clock = EngineClock { now }, demoEnabled = false)
        val first = registry()
        repeat(50) { enter(first, it).ok() }
        assertEquals(AppResult.Failure(AppError.Restricted), enter(registry(), 50))
        now = policy.idleRoomMillis + 1
        val restarted = registry()
        enter(restarted, 50).ok()
        enter(restarted, 0).ok()
    }

    @Test fun roomsRemovedFromAllowlistDoNotLoadOrConsumeRoomBudgetAfterRestart() {
        val directory = Files.createTempDirectory("allowed-room-restart-test")
        val policy = environmentPolicy(mapOf("CHAYEON_ALLOWED_ROOMS" to "*")).copy(dataDirectory = directory)
        val first = RoomRegistry(defaultRules(), policy, demoEnabled = false)
        val original = enter(first, 0, "other").ok()
        enter(first, 0).ok()
        val restricted = RoomRegistry(defaultRules(), policy.copy(allowedRooms = setOf("default"), maximumRooms = 1), demoEnabled = false)
        assertNull(restricted.authorize(header(original)))
        assertEquals(AppResult.Failure(AppError.Restricted), enter(restricted, 0, "other"))
        enter(restricted, 1).ok()
        val restored = RoomRegistry(defaultRules(), policy, demoEnabled = false)
        assertEquals(original.accountId, enter(restored, 0, "other").ok().accountId)
    }

    @Test fun legacyStoredTokensWithoutActivityCanReclaimAFreeSeat() {
        val directory = Files.createTempDirectory("legacy-seat-test")
        val policy = ServerPolicy(dataDirectory = directory, roomCapacity = 1)
        val first = RoomRegistry(defaultRules(), policy, demoEnabled = false)
        val original = enter(first, 0).ok()
        val store = FileEngineStore(directory.resolve("default.json"))
        store.saveTokens(store.tokens.mapValues { (_, identity) -> StoredAuthorization(identity.actor, identity.sessionId) })
        val restarted = RoomRegistry(defaultRules(), policy, demoEnabled = false)
        assertNotNull(restarted.authorize(header(original)))
        assertEquals(AppResult.Failure(AppError.Restricted), enter(restarted, 1))
    }

    @Test fun defaultRoomsAreRestrictedAndExplicitAllowlistOrWildcardAllowsRooms() {
        val defaults = RoomRegistry(defaultRules(), environmentPolicy(emptyMap()), demoEnabled = false)
        assertEquals(AppResult.Failure(AppError.Restricted), enter(defaults, 0, "other"))
        enter(defaults, 0).ok()
        val selected = RoomRegistry(defaultRules(), environmentPolicy(mapOf("CHAYEON_ALLOWED_ROOMS" to "default, event")), demoEnabled = false)
        enter(selected, 0, "event").ok()
        assertEquals(AppResult.Failure(AppError.Restricted), enter(selected, 0, "other"))
        val unrestricted = RoomRegistry(defaultRules(), environmentPolicy(mapOf("CHAYEON_ALLOWED_ROOMS" to "*")), demoEnabled = false)
        enter(unrestricted, 0, "other").ok()
        assertEquals(AppResult.Failure(AppError.Restricted), enter(unrestricted, 0, "../bad"))
    }
}
