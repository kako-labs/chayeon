package dev.chayeon.server

import dev.chayeon.core.engine.*
import dev.chayeon.core.model.*
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.util.UUID

class SessionIntegrityTest {
    private fun meta() = CommandMeta(UUID.randomUUID().toString())
    private fun <T> AppResult<T>.ok() = (this as AppResult.Success<T>).value
    private fun enter(registry: RoomRegistry, secret: String = "a".repeat(43), room: String? = null) =
        registry.enter(EnterSessionRequest(meta(), SessionMode.SERVER_DEMO, room, secret))
    private fun header(session: Session) = "Bearer ${session.accessToken}"

    @Test fun manyInstallationsOwnDistinctProfilesAndCapacityIsExplicit() {
        val registry = RoomRegistry(defaultRules(), policy = ServerPolicy(roomCapacity = 20), demoEnabled = false)
        val accounts = (1..20).map { enter(registry, "$it".padEnd(43, 'a')).ok() }
        assertEquals(20, accounts.map { it.accountId }.toSet().size)
        assertEquals(AppResult.Failure(AppError.Restricted), enter(registry, "z".repeat(43)))
        val recovered = enter(registry, "1".padEnd(43, 'a')).ok()
        assertEquals(accounts.first().accountId, recovered.accountId)
        assertNull(registry.authorize(header(accounts.first())))
        assertNotNull(registry.authorize(header(accounts.last())))
        val wire = EngineJson.encodeToString(EnterSessionResponse(AppResult.Success(recovered)))
        assertFalse(wire.contains("persona", ignoreCase = true))
        assertFalse(wire.contains("a".repeat(43)))
    }

    @Test fun defaultRoomIsSharedAndOtherRoomsRemainIsolated() {
        val registry = RoomRegistry(defaultRules(), demoEnabled = false)
        val a = enter(registry).ok()
        val b = enter(registry, room = "default").ok()
        assertEquals(a.accountId, b.accountId)
        assertNotEquals(a.accountId, enter(registry, room = "other").ok().accountId)
        assertEquals("", registry.snapshot(header(b))!!.ok().profile!!.publicProfile.nickname)
        for (value in listOf("", "a".repeat(31))) {
            assertEquals(AppResult.Failure(AppError.NotAuthorized), enter(registry, value))
        }
        assertEquals(AppResult.Failure(AppError.Restricted), enter(registry, room = "abc"))
    }

    @Test fun atomicStoreRoundTripsAndRestrictsPermissions() {
        val directory = Files.createTempDirectory("engine-store-test")
        val path = directory.resolve("room.json")
        val store = FileEngineStore(path)
        val first = EngineState(revision = 17, lastTick = 42)
        store.save(first)
        assertEquals(first, FileEngineStore(path).load())
        store.save(first.copy(revision = 18))
        assertEquals(18L, FileEngineStore(path).load()!!.revision)
        assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(path))
        Files.list(directory).use { assertEquals(1L, it.count()) }
        val before = Files.readString(path)
        assertFalse(before.contains("latitude"))
        assertFalse(before.contains("longitude"))
    }

    @Test fun atomicReplacementsNeverExposePartialJsonToReaders() {
        val path = Files.createTempDirectory("atomic-store-test").resolve("room.json")
        val store = FileEngineStore(path)
        store.save(EngineState(revision = 1))
        val failure = java.util.concurrent.atomic.AtomicReference<Throwable>()
        val running = java.util.concurrent.atomic.AtomicBoolean(true)
        val reader = Thread {
            try {
                while (running.get()) assertTrue(FileEngineStore(path).load()!!.revision in 1L..40L)
            } catch (error: Throwable) { failure.set(error) }
        }
        reader.start()
        try { for (revision in 2L..40L) store.save(EngineState(revision = revision)) }
        finally { running.set(false); reader.join(5_000) }
        assertFalse(reader.isAlive)
        failure.get()?.let { throw AssertionError("Atomic snapshot reader failed", it) }
    }

    @Test fun corruptStorageFailsClosedWithoutReplacingTheFile() {
        val path = Files.createTempDirectory("invalid-store-test").resolve("room.json")
        Files.writeString(path, "invalid-json")
        val error = assertThrows(IllegalStateException::class.java) { FileEngineStore(path) }
        assertEquals("Unable to load stored room", error.message)
        assertNull(error.cause)
        assertEquals("invalid-json", Files.readString(path))
    }

    @Test fun restartRestoresAuthProfilesAndRelationshipsButEndsLiveCalls() {
        val directory = Files.createTempDirectory("registry-restart-test")
        val policy = ServerPolicy(dataDirectory = directory)
        val first = RoomRegistry(defaultRules(), policy = policy, demoEnabled = false)
        val a = enter(first).ok()
        val b = enter(first, "b".repeat(43)).ok()
        val engine = first.authorize(header(a))!!.second
        for (session in listOf(a, b)) {
            engine.saveProfile(session.accountId, SaveProfileRequest(meta(), ProfileDraft("테스트", AvatarId.DAWN, emptySet()))).ok()
            ConsentScope.entries.forEach { engine.recordConsent(session.accountId,
                RecordConsentRequest(meta(), it, ConsentAction.GRANT, session.policy.consentDocumentVersion)).ok() }
            engine.setDiscovery(session.accountId, SetDiscoveryRequest(meta(), DiscoveryMode.ON)).ok()
        }
        val request = engine.requestConnection(a.accountId, RequestConnectionRequest(meta(), engine.exportState().accounts.getValue(b.accountId).profile.id)).ok()
        val connection = engine.respondConnection(b.accountId, RespondConnectionRequest(meta(), request.id, RequestDecision.ACCEPT)).ok().connectionId!!
        val second = RoomRegistry(defaultRules(), policy = policy, demoEnabled = false)
        assertNotNull(second.authorize(header(a)))
        val snapshot = second.snapshot(header(a))!!.ok()
        assertEquals(ConnectionStatus.ENDED, snapshot.connections.single().status)
        assertEquals(1, snapshot.encounters.size)
        assertEquals("테스트", snapshot.profile!!.publicProfile.nickname)
        val reentered = enter(second).ok()
        assertEquals(a.accountId, reentered.accountId)
        assertEquals(ConnectionStatus.ENDED, second.snapshot(header(reentered))!!.ok().connections.single { it.id == connection }.status)
        val restoredEngine = second.authorize(header(reentered))!!.second
        val afterRequest = restoredEngine.requestAfter(a.accountId, RequestAfterRequest(meta(), snapshot.encounters.single().id,
            ContactIds(instagramId = "drive_alpha"))).ok()
        val afterConnection = restoredEngine.respondAfter(b.accountId, RespondAfterRequest(meta(), afterRequest.id,
            RequestDecision.ACCEPT, ContactIds(instagramId = "drive_beta"))).ok().afterConnectionId!!
        val third = RoomRegistry(defaultRules(), policy = policy, demoEnabled = false)
        assertEquals(ConnectionStatus.ENDED, third.snapshot(header(reentered))!!.ok().connections.single().status)
        assertNotNull(third.snapshot(header(reentered))!!.ok().afterConnections.single().sharedContacts)
        third.authorize(header(reentered))!!.second.endAfterConnection(a.accountId,
            EndAfterConnectionRequest(meta(), afterConnection)).ok()
        val fourth = RoomRegistry(defaultRules(), policy = policy, demoEnabled = false)
        assertNull(fourth.snapshot(header(reentered))!!.ok().afterConnections.single().sharedContacts)
        val persisted = Files.readString(directory.resolve("default.json"))
        assertFalse(persisted.contains("drive_alpha"))
        assertFalse(persisted.contains("drive_beta"))
        assertFalse(persisted.contains(reentered.accessToken!!))
    }

    @Test fun deletionCreatesFreshIdentityAndSignOutReplayCannotRevokeNewSession() {
        val registry = RoomRegistry(defaultRules(), demoEnabled = false)
        val first = enter(registry).ok()
        val body = EngineJson.encodeToString(SignOutRequest(meta()))
        val response = registry.execute(header(first), ApiRoutes.SIGN_OUT, body)
        val next = enter(registry).ok()
        assertEquals(first.accountId, next.accountId)
        assertEquals(response, registry.execute(header(next), ApiRoutes.SIGN_OUT, body))
        assertNotNull(registry.authorize(header(next)))
        registry.execute(header(next), ApiRoutes.DELETE_ACCOUNT, EngineJson.encodeToString(DeleteAccountRequest(meta())))
        assertNotEquals(next.accountId, enter(registry).ok().accountId)
    }

    @Test fun retryOfSameEntryCommandKeepsTokenAndSession() {
        val registry = RoomRegistry(defaultRules(), demoEnabled = false)
        val request = EnterSessionRequest(meta(), SessionMode.SERVER_DEMO, clientSecret = "a".repeat(43))
        val first = registry.enter(request).ok()
        val retry = registry.enter(request).ok()
        assertEquals(first, retry)
        assertNotNull(registry.authorize(header(first)))
    }

    @Test fun persistentIdleRoomsKeepTheirCapacityReservation() {
        var now = 0L
        val directory = Files.createTempDirectory("room-capacity-test")
        val policy = ServerPolicy(dataDirectory = directory, maximumRooms = 1, idleRoomMillis = 100)
        val registry = RoomRegistry(defaultRules(), policy = policy, clock = EngineClock { now }, demoEnabled = false)
        val first = enter(registry).ok()
        now = 101
        registry.tick()
        assertNull(registry.authorize(header(first)))
        assertEquals(AppResult.Failure(AppError.Restricted), enter(registry, room = "other"))
        assertEquals(first.accountId, enter(registry).ok().accountId)
        assertNotNull(RoomRegistry(defaultRules(), policy = policy, clock = EngineClock { now }, demoEnabled = false))
    }

    @Test fun admissionBudgetsAreBoundedAndReset() {
        var now = 0L
        val admission = Admission({ now }, maximumKeys = 2)
        repeat(2) { assertTrue(admission.allow("a", 2)) }
        assertFalse(admission.allow("a", 2))
        assertTrue(admission.allow("b", 2))
        assertFalse(admission.allow("c", 2))
        now = 60_000
        assertTrue(admission.allow("c", 2))
    }
}
