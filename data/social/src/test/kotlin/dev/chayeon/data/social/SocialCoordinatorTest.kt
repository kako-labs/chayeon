package dev.chayeon.data.social

import dev.chayeon.core.model.*
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encoding.Encoder
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SocialCoordinatorTest {
    @Test fun `unknown and restricted policies reject commands using current provider rather than UI state`() = runTest {
        val backend = FakeBackend()
        val driving = MutableStateFlow(DrivingState(policy = DrivingPolicy.ALLOWED))
        val coordinator = SocialCoordinator(mapOf(SessionMode.SOLO to backend), backgroundScope,
            { AppResult.Success(Unit) }, { 10 }, provider(driving))
        runCurrent()
        coordinator.enter(enter())
        val repository = SocialProfileRepository(coordinator)
        driving.value = DrivingState(policy = DrivingPolicy.RESTRICTED)
        assertEquals(AppResult.Failure(AppError.Restricted), repository.saveProfile(save()))
        driving.value = DrivingState()
        assertEquals(AppResult.Failure(AppError.Restricted), repository.saveProfile(save()))
        assertEquals(0, backend.bodies.size)
        assertTrue(repository.profile.first() is LoadState.Failed)
    }

    @Test fun `network failure hides public cards while retrying greetings keep one stable message`() = runTest {
        val backend = FakeBackend()
        val coordinator = SocialCoordinator(mapOf(SessionMode.SOLO to backend), backgroundScope,
            { AppResult.Success(Unit) }, { 10 }, allowedProvider())
        runCurrent()
        coordinator.enter(enter())
        runCurrent()
        val repository = SocialConnectionRepository(coordinator)
        val request = SendGreetingRequest(CommandMeta("first"), "connection", "message", PresetCode.HELLO)
        val gate = CompletableDeferred<Unit>()
        backend.handler = { _, _ -> gate.await(); throw IOException("Unavailable") }
        val sending = async { repository.sendGreeting(request) }
        runCurrent()
        assertEquals(DeliveryState.SENDING, greetings(repository).single().delivery)
        gate.complete(Unit)
        assertEquals(AppResult.Failure(AppError.Network), sending.await())
        assertEquals(DeliveryState.FAILED, greetings(repository).single().delivery)
        backend.handler = { _, body ->
            val sent = socialJson.decodeFromString(SendGreetingRequest.serializer(), body)
            socialJson.encodeToString(SendGreetingResponse.serializer(), SendGreetingResponse(AppResult.Success(
                ConnectionGreeting("server-message", 2, sent.connectionId, sent.clientMessageId,
                    MessageAuthor.SELF, sent.code, 1_000, DeliveryState.SENT))))
        }
        assertTrue(repository.sendGreeting(request.copy(meta = CommandMeta("replacement"))) is AppResult.Success)
        assertEquals(DeliveryState.SENT, greetings(repository).single().delivery)
        assertEquals(backend.bodies[0], backend.bodies[1])
        assertEquals(1, greetings(repository).size)
    }

    @Test fun `cleanup failure clears observations but never reports signout success and can retry cleanup`() = runTest {
        val backend = FakeBackend()
        var cleanupFails = true
        var hookCalls = 0
        val coordinator = SocialCoordinator(mapOf(SessionMode.SOLO to backend), backgroundScope, {
            hookCalls++
            if (cleanupFails) AppResult.Failure(AppError.Unknown) else AppResult.Success(Unit)
        }, { 10 }, allowedProvider())
        runCurrent()
        coordinator.enter(enter())
        runCurrent()
        assertTrue(coordinator.signOut(SignOutRequest(CommandMeta("out"))) is AppResult.Failure)
        assertNull(coordinator.store.state.value.sessionId)
        assertTrue(coordinator.session.value is SessionState.Failed)
        assertEquals(1, backend.clearCalls)
        assertEquals(1, hookCalls)
        cleanupFails = false
        assertTrue(coordinator.signOut(SignOutRequest(CommandMeta("out"))) is AppResult.Success)
        assertEquals(2, hookCalls)
        assertEquals(1, backend.bodies.size)
    }

    @Test fun `pending signout cleanup cannot report success for account deletion`() = runTest {
        val backend = FakeBackend()
        var cleanupFails = true
        var hookCalls = 0
        val coordinator = SocialCoordinator(mapOf(SessionMode.SOLO to backend), backgroundScope, {
            hookCalls++
            if (cleanupFails) AppResult.Failure(AppError.Unknown) else AppResult.Success(Unit)
        }, { 10 }, allowedProvider())
        runCurrent()
        coordinator.enter(enter())
        val original = SignOutRequest(CommandMeta("out"))
        assertTrue(coordinator.signOut(original) is AppResult.Failure)
        cleanupFails = false
        assertEquals(AppResult.Failure(AppError.AlreadyClosed),
            coordinator.deleteAccount(DeleteAccountRequest(CommandMeta("delete"))))
        assertEquals(listOf(ApiRoutes.SIGN_OUT), backend.routes)
        assertEquals(1, hookCalls)
        assertTrue(coordinator.signOut(original) is AppResult.Success)
        assertEquals(2, hookCalls)
        assertEquals(listOf(ApiRoutes.SIGN_OUT), backend.routes)
    }

    @Test fun `pending account deletion cleanup cannot report success for signout`() = runTest {
        val backend = FakeBackend()
        var cleanupFails = true
        var hookCalls = 0
        val coordinator = SocialCoordinator(mapOf(SessionMode.SOLO to backend), backgroundScope, {
            hookCalls++
            if (cleanupFails) AppResult.Failure(AppError.Unknown) else AppResult.Success(Unit)
        }, { 10 }, allowedProvider())
        runCurrent()
        coordinator.enter(enter())
        val original = DeleteAccountRequest(CommandMeta("delete"))
        assertTrue(coordinator.deleteAccount(original) is AppResult.Failure)
        cleanupFails = false
        assertEquals(AppResult.Failure(AppError.AlreadyClosed), coordinator.signOut(SignOutRequest(CommandMeta("out"))))
        assertEquals(listOf(ApiRoutes.DELETE_ACCOUNT), backend.routes)
        assertEquals(1, hookCalls)
        assertTrue(coordinator.deleteAccount(original) is AppResult.Success)
        assertEquals(2, hookCalls)
        assertEquals(listOf(ApiRoutes.DELETE_ACCOUNT), backend.routes)
    }

    @Test fun `cleanup retries reject changed command id or body and keep original receipt`() = runTest {
        val backend = FakeBackend()
        var cleanupFails = true
        val coordinator = SocialCoordinator(mapOf(SessionMode.SOLO to backend), backgroundScope, {
            if (cleanupFails) AppResult.Failure(AppError.Unknown) else AppResult.Success(Unit)
        }, { 10 }, allowedProvider())
        runCurrent()
        coordinator.enter(enter())
        val original = SignOutRequest(CommandMeta("out", expectedVersion = 1))
        assertTrue(coordinator.signOut(original) is AppResult.Failure)
        cleanupFails = false
        assertEquals(AppResult.Failure(AppError.AlreadyClosed),
            coordinator.signOut(original.copy(meta = original.meta.copy(commandId = "different"))))
        assertEquals(AppResult.Failure(AppError.AlreadyClosed),
            coordinator.signOut(original.copy(meta = original.meta.copy(expectedVersion = 2))))
        assertEquals(AppResult.Success(CommandReceipt("out", 2)), coordinator.signOut(original))
        assertEquals(1, backend.bodies.size)
    }

    @Test fun `signout removes token from observations while cleanup is pending and does not announce completion`() = runTest {
        val backend = FakeBackend()
        val gate = CompletableDeferred<Unit>()
        val hookStarted = CompletableDeferred<Unit>()
        val coordinator = SocialCoordinator(mapOf(SessionMode.SOLO to backend), backgroundScope,
            { hookStarted.complete(Unit); gate.await(); AppResult.Success(Unit) }, { 10 }, allowedProvider())
        runCurrent()
        coordinator.enter(enter())
        val result = async { coordinator.signOut(SignOutRequest(CommandMeta("out"))) }
        hookStarted.await()
        assertNull(coordinator.store.state.value.sessionId)
        assertFalse(coordinator.session.value is SessionState.Active)
        assertFalse(coordinator.session.value is SessionState.SignedOut)
        assertFalse(result.isCompleted)
        gate.complete(Unit)
        assertTrue(result.await() is AppResult.Success)
        assertTrue(coordinator.session.value is SessionState.SignedOut)
    }

    @Test fun `account deletion clears local storage and cleanup before success`() = runTest {
        val backend = FakeBackend()
        var hookComplete = false
        val coordinator = SocialCoordinator(mapOf(SessionMode.SOLO to backend), backgroundScope,
            { hookComplete = true; AppResult.Success(Unit) }, { 10 }, allowedProvider())
        runCurrent()
        coordinator.enter(enter())
        val result = coordinator.deleteAccount(DeleteAccountRequest(CommandMeta("delete")))
        assertTrue(result is AppResult.Success)
        assertTrue(hookComplete)
        assertEquals(1, backend.clearCalls)
        assertNull(coordinator.store.state.value.sessionId)
        assertTrue(coordinator.session.value is SessionState.SignedOut)
    }

    @Test fun `poll begun before command failure cannot revive cards when its delayed response arrives`() = runTest {
        val backend = FakeBackend()
        val delayedPoll = CompletableDeferred<Unit>()
        backend.beforePollResponse = { delayedPoll.await() }
        val coordinator = SocialCoordinator(mapOf(SessionMode.SOLO to backend), backgroundScope,
            { AppResult.Success(Unit) }, { 10 }, allowedProvider())
        runCurrent()
        coordinator.enter(enter())
        runCurrent()
        coordinator.refresh()
        runCurrent()
        assertTrue(coordinator.store.state.value.data is LoadState.Ready)
        backend.handler = { _, _ -> throw IOException("Unavailable") }
        assertEquals(AppResult.Failure(AppError.Network), SocialProfileRepository(coordinator).saveProfile(save()))
        delayedPoll.complete(Unit)
        runCurrent()
        assertTrue(coordinator.store.state.value.data is LoadState.Failed)
        assertNull(coordinator.store.state.value.anchor)
        coordinator.refresh()
        runCurrent()
        assertTrue(coordinator.store.state.value.data is LoadState.Ready)
    }

    @Test fun `local guard and codec failures cannot suppress an older in flight network failure`() = runTest {
        for (failureKind in listOf("guard", "encode", "decode")) {
            val backend = FakeBackend()
            val driving = MutableStateFlow(DrivingState(policy = DrivingPolicy.ALLOWED))
            val coordinator = SocialCoordinator(mapOf(SessionMode.SOLO to backend), backgroundScope,
                { AppResult.Success(Unit) }, { 10 }, provider(driving))
            coordinator.enter(enter())
            runCurrent()
            assertTrue(coordinator.store.state.value.data is LoadState.Ready)
            val pollStarted = CompletableDeferred<Unit>()
            val pollRelease = CompletableDeferred<Unit>()
            backend.beforePollResponse = { pollStarted.complete(Unit); pollRelease.await() }
            backend.snapshot.value = AppResult.Failure(AppError.Network)
            pollStarted.await()
            backend.beforeExecute = {
                if (failureKind == "guard") driving.value = DrivingState(policy = DrivingPolicy.RESTRICTED)
            }
            backend.handler = { _, _ -> "not-json" }
            val requestSerializer = if (failureKind == "encode") {
                object : KSerializer<SaveProfileRequest> by SaveProfileRequest.serializer() {
                    override fun serialize(encoder: Encoder, value: SaveProfileRequest) {
                        throw SerializationException("Invalid local request")
                    }
                }
            } else SaveProfileRequest.serializer()
            val result = coordinator.command(ApiRoutes.SAVE_PROFILE, save(), requestSerializer,
                SaveProfileResponse.serializer(), { it.result }, refresh = false)
            assertEquals(AppResult.Failure(if (failureKind == "guard") AppError.Restricted else AppError.Unknown), result)
            if (failureKind != "decode") assertTrue(backend.bodies.isEmpty())
            driving.value = DrivingState(policy = DrivingPolicy.ALLOWED)
            pollRelease.complete(Unit)
            runCurrent()
            assertEquals("Failure was suppressed by $failureKind", LoadState.Failed(AppError.Network, 1),
                coordinator.store.state.value.data)
            assertNull(coordinator.store.state.value.anchor)
            coordinator.close()
        }
    }

    @Test fun `restart retries failed cleanup before restoring a session or allowing another account`() = runTest {
        val storage = MemoryCleanupStorage()
        val firstBackend = FakeBackend()
        val first = SocialCoordinator(mapOf(SessionMode.SOLO to firstBackend), backgroundScope,
            { AppResult.Failure(AppError.Unknown) }, { 10 }, allowedProvider(), storage)
        runCurrent()
        first.enter(enter())
        assertTrue(first.signOut(SignOutRequest(CommandMeta("out"))) is AppResult.Failure)
        assertNotNull(storage.read())
        first.close()
        val secondBackend = FakeBackend()
        var hookCalls = 0
        var hookFails = true
        val second = SocialCoordinator(mapOf(SessionMode.SOLO to secondBackend), backgroundScope, {
            hookCalls++
            if (hookFails) AppResult.Failure(AppError.Unknown) else AppResult.Success(Unit)
        }, { 10 }, allowedProvider(), storage)
        second.session.first { it is SessionState.Failed }
        assertTrue(second.enter(enter()) is AppResult.Failure)
        assertEquals(2, hookCalls)
        assertEquals(0, secondBackend.restoreCalls)
        assertNotNull(storage.read())
        hookFails = false
        val recovered = second.enter(enter().copy(meta = CommandMeta("new-account"), persona = PersonaCode.ACCOUNT_B))
        assertTrue(recovered is AppResult.Success)
        assertEquals(PersonaCode.ACCOUNT_B, (recovered as AppResult.Success).value.persona)
        assertNull(storage.read())
        assertEquals(0, secondBackend.bodies.size)
        assertEquals(3, hookCalls)
    }

    @Test fun `cleanup marker write failure prevents remote lifecycle effects`() = runTest {
        val backend = FakeBackend()
        val storage = object : CleanupStorage {
            override fun read(): CleanupRecord? = null
            override fun write(record: CleanupRecord) { throw IOException("Unavailable") }
            override fun clear() = Unit
        }
        var hookCalls = 0
        val coordinator = SocialCoordinator(mapOf(SessionMode.SOLO to backend), backgroundScope,
            { hookCalls++; AppResult.Success(Unit) }, { 10 }, allowedProvider(), storage)
        coordinator.enter(enter())
        assertTrue(coordinator.signOut(SignOutRequest(CommandMeta("out"))) is AppResult.Failure)
        assertTrue(coordinator.session.value is SessionState.Active)
        assertEquals(0, backend.bodies.size)
        assertEquals(0, backend.clearCalls)
        assertEquals(0, hookCalls)
    }

    @Test fun `restart with unknown remote outcome cleans local state without claiming deletion succeeded`() = runTest {
        val storage = MemoryCleanupStorage()
        storage.write(CleanupRecord(ApiRoutes.DELETE_ACCOUNT,
            socialJson.encodeToString(DeleteAccountRequest.serializer(), DeleteAccountRequest(CommandMeta("delete")))))
        val backend = FakeBackend()
        val coordinator = SocialCoordinator(mapOf(SessionMode.SOLO to backend), backgroundScope,
            { AppResult.Success(Unit) }, { 10 }, allowedProvider(), storage)
        coordinator.session.first { it is SessionState.Failed }
        assertNull(storage.read())
        assertEquals(SessionState.Failed(AppError.Unknown), coordinator.session.value)
        assertEquals(0, backend.restoreCalls)
        assertEquals(0, backend.bodies.size)
        assertTrue(coordinator.enter(enter()) is AppResult.Success)
    }

    @Test fun `response from a signed out session cannot reintroduce public state`() = runTest {
        val backend = FakeBackend()
        val coordinator = SocialCoordinator(mapOf(SessionMode.SOLO to backend), backgroundScope,
            { AppResult.Success(Unit) }, { 10 }, allowedProvider())
        runCurrent()
        coordinator.enter(enter())
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        backend.handler = { route, _ ->
            if (route == ApiRoutes.SAVE_PROFILE) {
                gate.await()
                socialJson.encodeToString(SaveProfileResponse.serializer(), SaveProfileResponse(AppResult.Success(profile())))
            } else receipt()
        }
        val pending = async { SocialProfileRepository(coordinator).saveProfile(save()) }
        runCurrent()
        coordinator.signOut(SignOutRequest(CommandMeta("out")))
        gate.complete(Unit)
        assertEquals(AppResult.Failure(AppError.NotAuthorized), pending.await())
        assertNull(coordinator.store.state.value.sessionId)
    }

    private suspend fun greetings(repository: ConnectionRepository) =
        (repository.observeGreetings("connection").first() as LoadState.Ready).value
    private fun provider(flow: Flow<DrivingState>) = object : DrivingStateProvider { override val drivingState = flow }
    private fun allowedProvider() = provider(flowOf(DrivingState(policy = DrivingPolicy.ALLOWED)))
    private fun enter() = EnterSessionRequest(CommandMeta("enter"), SessionMode.SOLO, PersonaCode.ACCOUNT_A)
    private fun save() = SaveProfileRequest(CommandMeta("save"), ProfileDraft("새벽드라이브", AvatarId.DAWN, emptySet()))

    private class FakeBackend : SocialBackend {
        override val mode = SessionMode.SOLO
        val bodies = mutableListOf<String>()
        val routes = mutableListOf<String>()
        var clearCalls = 0
        var beforePollResponse: suspend () -> Unit = {}
        var beforeExecute: suspend () -> Unit = {}
        var restoreCalls = 0
        val snapshot = MutableStateFlow<AppResult<SyncSnapshot>>(AppResult.Success(SyncSnapshot("session", 1, 1_000, profile())))
        var handler: suspend (String, String) -> String = { _, _ -> receipt() }
        override suspend fun restoreSession(): AppResult<Session?> { restoreCalls++; return AppResult.Success(null) }
        override suspend fun enter(request: EnterSessionRequest): AppResult<Session> =
            AppResult.Success(testSession().copy(persona = request.persona, accountId = request.persona.name.lowercase()))
        override suspend fun execute(session: Session, route: String, body: String, canExecute: suspend () -> Boolean): String {
            beforeExecute()
            if (!canExecute()) throw SocialRestrictedException()
            bodies += body
            routes += route
            return handler(route, body)
        }
        override suspend fun sync(session: Session, since: Long) = snapshot.value
        override fun snapshots(session: Session, since: Long, onRequest: () -> Unit): Flow<AppResult<SyncSnapshot>> = flow {
            snapshot.collect { onRequest(); beforePollResponse(); emit(it) }
        }
        override suspend fun clearLocalData(): AppResult<Unit> { clearCalls++; return AppResult.Success(Unit) }
        override fun close() = Unit
    }

    companion object {
        fun testSession(mode: SessionMode = SessionMode.SOLO) = Session("session", 1, "account_a", mode,
            PersonaCode.ACCOUNT_A, null, if (mode == SessionMode.SERVER_DEMO) "test-credential" else null, null,
            ParticipationPolicy("policy", 1, 5, 2, 12, null, "1"))
        fun profile() = MyProfile("profile", 1, PublicProfile("profile", 1, "새벽드라이브", AvatarId.DAWN,
            emptySet(), ProfileKind.VIRTUAL_PROFILE, null), RegistrationState.READY)
        fun receipt() = socialJson.encodeToString(SignOutResponse.serializer(),
            SignOutResponse(AppResult.Success(CommandReceipt("out", 2))))
    }
}
