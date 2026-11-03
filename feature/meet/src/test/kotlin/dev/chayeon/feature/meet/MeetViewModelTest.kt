package dev.chayeon.feature.meet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import dev.chayeon.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MeetViewModelTest {
    private fun scenario(block: suspend TestScope.(FakeMeetDeps, ViewModelStore) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try { block(FakeMeetDeps(), store) } finally { store.clear(); runCurrent(); Dispatchers.resetMain() }
    }
    private fun <T : ViewModel> ViewModelStore.keep(model: T): T = model.also { put(model.javaClass.name, it) }
    private fun TestScope.observe(flow: StateFlow<*>) = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect() }

    @Test fun `home retries exact discovery payload and rejects duplicate taps`() = scenario { deps, store ->
        val result = CompletableDeferred<AppResult<DiscoveryState>>()
        deps.setDiscoveryResult = { result.await() }
        val model = store.keep(HomeViewModel(deps)); observe(model.uiState); runCurrent()
        model.setDiscovery(false); model.setDiscovery(false); runCurrent()
        assertEquals(1, deps.discoveryCommands.size)
        assertTrue(model.uiState.value.toggle.inProgress)
        result.complete(AppResult.Failure(AppError.Network)); runCurrent()
        deps.discovery.value = deps.discovery.value.copy(version = 20)
        deps.setDiscoveryResult = { AppResult.Success(deps.discovery.value) }
        model.setDiscovery(false); runCurrent()
        assertEquals(deps.discoveryCommands[0], deps.discoveryCommands[1])
        model.setDiscovery(true); runCurrent()
        assertNotEquals(deps.discoveryCommands[1].meta.commandId, deps.discoveryCommands[2].meta.commandId)
    }

    @Test fun `discovery failures remove stale cards and unpublished signature remains nonblocking`() = scenario { deps, store ->
        val model = store.keep(HomeViewModel(deps)); observe(model.uiState); runCurrent()
        deps.discovery.value = deps.discovery.value.copy(status = DiscoveryStatus.NETWORK_FAILURE); runCurrent()
        assertTrue(model.uiState.value.discovery.candidates.isEmpty())
        deps.profile.value = LoadState.Ready(MyProfile("self", 2, MeetFixtures.own.copy(signature = null), RegistrationState.READY), 2)
        runCurrent(); assertNull(model.uiState.value.profile?.signature)
        model.refresh(); runCurrent(); assertEquals(1, deps.refreshCommands.size)
    }

    @Test fun `all command entry points recheck policy after rendered allowed state`() = scenario { deps, store ->
        deps.requests.value = LoadState.Ready(listOf(MeetFixtures.request), 2)
        deps.connection.value = LoadState.Ready(MeetFixtures.connection, 3)
        val home = store.keep(HomeViewModel(deps)); val candidate = store.keep(CandidateViewModel(deps, "candidate"))
        val request = store.keep(RequestViewModel(deps)); val connected = store.keep(ConnectedViewModel(deps))
        listOf(home.uiState, candidate.uiState, request.uiState, connected.uiState).forEach { observe(it) }; runCurrent()
        for (policy in listOf(DrivingPolicy.RESTRICTED, DrivingPolicy.UNKNOWN)) {
            deps.drivingState.value = DrivingState(policy = policy)
            home.setDiscovery(false); home.refresh(); candidate.requestConnection(); request.accept(); connected.sendGreeting(PresetCode.HELLO)
            runCurrent(); request.decline(); request.cancel(); runCurrent()
        }
        assertTrue(deps.discoveryCommands.isEmpty()); assertTrue(deps.refreshCommands.isEmpty())
        assertTrue(deps.requestCommands.isEmpty()); assertTrue(deps.respondCommands.isEmpty())
        assertTrue(deps.cancelCommands.isEmpty()); assertTrue(deps.greetingCommands.isEmpty())
        connected.leave(); runCurrent(); assertEquals(1, deps.leaveCommands.size)
    }

    @Test fun `candidate fresh lookup refuses removed peer between render and tap`() = scenario { deps, store ->
        val model = store.keep(CandidateViewModel(deps, "candidate")); observe(model.uiState); runCurrent()
        assertNotNull(model.uiState.value.candidate)
        deps.discovery.value = deps.discovery.value.copy(candidates = emptyList(), version = 2)
        model.requestConnection(); runCurrent()
        assertTrue(deps.requestCommands.isEmpty())
        assertEquals(AppError.PeerUnavailable, model.uiState.value.request.error)
    }

    @Test fun `candidate retry preserves key and success prevents duplicate request`() = scenario { deps, store ->
        deps.requestResult = { AppResult.Failure(AppError.Network) }
        val model = store.keep(CandidateViewModel(deps, "candidate")); observe(model.uiState); runCurrent()
        model.requestConnection(); runCurrent()
        deps.requestResult = { AppResult.Success(MeetFixtures.request) }
        model.requestConnection(); runCurrent(); model.requestConnection(); runCurrent()
        assertEquals(2, deps.requestCommands.size); assertEquals(deps.requestCommands[0], deps.requestCommands[1])
    }

    @Test fun `request countdown uses monotonic anchor and zero stays pending`() = scenario { deps, store ->
        deps.requests.value = LoadState.Ready(listOf(MeetFixtures.request), 2)
        val model = store.keep(RequestViewModel(deps)); observe(model.uiState); runCurrent()
        assertEquals(120_000L, model.uiState.value.remainingMillis)
        deps.monotonic += 121_000; advanceTimeBy(1_000); runCurrent()
        assertEquals(0L, model.uiState.value.remainingMillis)
        assertEquals(ConnectionRequestStatus.PENDING, model.uiState.value.request?.status)
        model.accept(); runCurrent(); assertEquals(1, deps.respondCommands.size)
        deps.serverTime.value = null; runCurrent(); assertNull(model.uiState.value.remainingMillis)
    }

    @Test fun `incoming accept retry preserves version and concurrent decline is suppressed`() = scenario { deps, store ->
        deps.requests.value = LoadState.Ready(listOf(MeetFixtures.request), 2)
        val result = CompletableDeferred<AppResult<ConnectionRequest>>()
        deps.respondResult = { result.await() }
        val model = store.keep(RequestViewModel(deps)); observe(model.uiState); runCurrent()
        model.accept(); model.decline(); model.accept(); runCurrent()
        assertEquals(1, deps.respondCommands.size)
        result.complete(AppResult.Failure(AppError.Network)); runCurrent()
        deps.requests.value = LoadState.Ready(listOf(MeetFixtures.request.copy(version = 5)), 5)
        deps.respondResult = { AppResult.Success(MeetFixtures.request.copy(status = ConnectionRequestStatus.ACCEPTED)) }
        model.accept(); runCurrent(); assertEquals(deps.respondCommands[0], deps.respondCommands[1])
    }

    @Test fun `cancelled request cannot be accepted from stale rendered screen`() = scenario { deps, store ->
        deps.requests.value = LoadState.Ready(listOf(MeetFixtures.request), 2)
        val model = store.keep(RequestViewModel(deps)); observe(model.uiState); runCurrent()
        deps.requests.value = LoadState.Ready(listOf(MeetFixtures.request.copy(status = ConnectionRequestStatus.CANCELLED, version = 4)), 4)
        model.accept(); runCurrent(); assertTrue(deps.respondCommands.isEmpty())
        assertEquals(ConnectionRequestStatus.CANCELLED, model.uiState.value.request?.status)
    }

    @Test fun `outgoing cancel failure retries identical command`() = scenario { deps, store ->
        deps.requests.value = LoadState.Ready(listOf(MeetFixtures.request.copy(direction = RequestDirection.OUTGOING)), 2)
        val model = store.keep(RequestViewModel(deps)); observe(model.uiState); runCurrent()
        deps.cancelResult = { AppResult.Failure(AppError.Network) }
        model.cancel(); runCurrent(); model.cancel(); runCurrent()
        assertEquals(2, deps.cancelCommands.size); assertEquals(deps.cancelCommands[0], deps.cancelCommands[1])
        model.accept(); runCurrent(); assertTrue(deps.respondCommands.isEmpty())
    }

    @Test fun `greeting retries keep body and client id and reconcile server acknowledgment`() = scenario { deps, store ->
        deps.connection.value = LoadState.Ready(MeetFixtures.connection, 3)
        val result = CompletableDeferred<AppResult<ConnectionGreeting>>()
        deps.greetingResult = { result.await() }
        val model = store.keep(ConnectedViewModel(deps)); observe(model.uiState); runCurrent()
        model.sendGreeting(PresetCode.HELLO); model.sendGreeting(PresetCode.HELLO); runCurrent()
        assertEquals(1, deps.greetingCommands.size)
        assertEquals(DeliveryState.SENDING, model.uiState.value.greetings.single().delivery)
        result.complete(AppResult.Failure(AppError.Network)); runCurrent()
        val id = deps.greetingCommands.single().clientMessageId
        assertEquals(DeliveryState.FAILED, model.uiState.value.greetings.single().delivery)
        val message = ConnectionGreeting("server-id", 4, "connection", id, MessageAuthor.SELF, PresetCode.HELLO, 120_000, DeliveryState.SENT)
        deps.greetingResult = { AppResult.Success(message) }
        model.retryGreeting(id); model.retryGreeting(id); runCurrent()
        assertEquals(2, deps.greetingCommands.size); assertEquals(deps.greetingCommands[0], deps.greetingCommands[1])
        deps.messages.value = LoadState.Ready(listOf(message), 4); runCurrent()
        assertEquals(1, model.uiState.value.greetings.size)
        assertEquals(DeliveryState.SENT, model.uiState.value.greetings.single().delivery)
        model.retryGreeting(id); runCurrent(); assertEquals(2, deps.greetingCommands.size)
    }

    @Test fun `checking ended and withdrawn presets refuse new greetings and failed retries`() = scenario { deps, store ->
        deps.connection.value = LoadState.Ready(MeetFixtures.connection, 3)
        deps.greetingResult = { AppResult.Failure(AppError.Network) }
        val model = store.keep(ConnectedViewModel(deps)); observe(model.uiState); runCurrent()
        model.sendGreeting(PresetCode.HELLO); runCurrent()
        val id = deps.greetingCommands.single().clientMessageId
        for (status in listOf(ConnectionStatus.CHECKING, ConnectionStatus.ENDED)) {
            deps.connection.value = LoadState.Ready(MeetFixtures.connection.copy(status = status, version = 8), 8)
            model.sendGreeting(PresetCode.HELLO); model.retryGreeting(id); runCurrent()
        }
        deps.connection.value = LoadState.Ready(MeetFixtures.connection.copy(availablePresets = emptySet()), 9)
        model.sendGreeting(PresetCode.HELLO); model.retryGreeting(id); runCurrent()
        assertEquals(1, deps.greetingCommands.size)
    }

    @Test fun `ending during send never revives greeting content`() = scenario { deps, store ->
        deps.connection.value = LoadState.Ready(MeetFixtures.connection, 3)
        val result = CompletableDeferred<AppResult<ConnectionGreeting>>()
        deps.greetingResult = { result.await() }
        val model = store.keep(ConnectedViewModel(deps)); observe(model.uiState); runCurrent()
        model.sendGreeting(PresetCode.HELLO); runCurrent()
        deps.connection.value = LoadState.Ready(MeetFixtures.ended, 4); runCurrent()
        result.complete(AppResult.Failure(AppError.Network)); runCurrent()
        assertTrue(model.uiState.value.greetings.isEmpty())
        assertEquals(ConnectionStatus.ENDED, model.uiState.value.connection?.status)
    }

    @Test fun `all screen state replays reset to unknown after unsubscribing`() = scenario { deps, store ->
        val states = listOf(store.keep(HomeViewModel(deps)).uiState, store.keep(CandidateViewModel(deps, "candidate")).uiState,
            store.keep(RequestViewModel(deps)).uiState, store.keep(ConnectedViewModel(deps)).uiState,
            store.keep(EndedViewModel(deps, "connection")).uiState, store.keep(MeetViewModel(deps)).uiState)
        val collectors = states.map { observe(it) }; runCurrent()
        collectors.forEach { it.cancel() }; advanceTimeBy(5_001); runCurrent()
        assertEquals(HomeUiState(), states[0].value); assertEquals(CandidateUiState(), states[1].value)
        assertEquals(RequestUiState(), states[2].value); assertEquals(ConnectedUiState(), states[3].value)
        assertEquals(EndedUiState(), states[4].value); assertEquals(MeetUiState(), states[5].value)
    }

    @Test fun `navigation is authoritative one-shot and preserves home on driving transition`() = scenario { deps, store ->
        val model = store.keep(MeetViewModel(deps))
        val events = mutableListOf<MeetNavigationEffect>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.effectsFor(MeetDestination.HOME).collect { events += it; model.handled(it) } }
        deps.drivingState.value = DrivingState(policy = DrivingPolicy.RESTRICTED); runCurrent()
        assertEquals(MeetNavigationEffect.LockHome, events.last())
        deps.drivingState.value = MeetFixtures.parked; runCurrent()
        deps.connection.value = LoadState.Ready(MeetFixtures.connection, 3); runCurrent()
        deps.connection.value = LoadState.Ready(MeetFixtures.connection.copy(version = 4, status = ConnectionStatus.CHECKING), 4); runCurrent()
        assertEquals(1, events.count { it is MeetNavigationEffect.Connected })
        deps.connection.value = LoadState.Ready(MeetFixtures.ended, 5); runCurrent()
        assertEquals(MeetNavigationEffect.Ended("connection"), events.last())
        job.cancel()
        assertEquals(MeetNavigationEffect.ResumeHome, model.effectsFor(MeetDestination.DRIVING).first())
    }
    @Test fun `request response before sync cannot select an old terminal request`() = scenario { deps, store ->
        deps.requests.value = LoadState.Ready(listOf(MeetFixtures.request.copy(id = "old", status = ConnectionRequestStatus.EXPIRED)), 2)
        val model = store.keep(RequestViewModel(deps, "new")); observe(model.uiState); runCurrent()
        assertNull(model.uiState.value.request); assertTrue(model.uiState.value.loading)
        deps.requests.value = LoadState.Ready(listOf(MeetFixtures.request.copy(id = "old", status = ConnectionRequestStatus.EXPIRED),
            MeetFixtures.request.copy(id = "new", direction = RequestDirection.OUTGOING, version = 4)), 4)
        runCurrent(); assertEquals("new", model.uiState.value.request?.id)
        model.cancel(); runCurrent(); assertEquals("new", deps.cancelCommands.single().requestId)
    }

    @Test fun `new connection never displays previous connection greetings while new observation stalls`() = scenario { deps, store ->
        deps.connection.value = LoadState.Ready(MeetFixtures.connection, 3)
        deps.messages.value = LoadState.Ready(listOf(ConnectionGreeting("old-message", 4, "connection", "old-client",
            MessageAuthor.PEER, PresetCode.HELLO, 120_000, DeliveryState.SENT)), 4)
        deps.greetingsSource = { id -> if (id == "new-connection") flow { awaitCancellation() } else deps.messages }
        val model = store.keep(ConnectedViewModel(deps)); observe(model.uiState); runCurrent()
        assertEquals(1, model.uiState.value.greetings.size)
        deps.connection.value = LoadState.Ready(MeetFixtures.connection.copy(id = "new-connection", version = 5), 5)
        runCurrent(); assertTrue(model.uiState.value.greetings.isEmpty()); assertTrue(model.uiState.value.greetingsLoading)
    }

    @Test fun `restored failed greeting reconstructs original command and message id`() = scenario { deps, store ->
        deps.connection.value = LoadState.Ready(MeetFixtures.connection, 3)
        deps.greetingResult = { AppResult.Failure(AppError.Network) }
        val original = store.keep(ConnectedViewModel(deps)); observe(original.uiState); runCurrent()
        original.sendGreeting(PresetCode.HELLO); runCurrent()
        val sent = deps.greetingCommands.single()
        deps.messages.value = LoadState.Ready(listOf(ConnectionGreeting("failed", 4, sent.connectionId, sent.clientMessageId,
            MessageAuthor.SELF, sent.code, 120_000, DeliveryState.FAILED)), 4)
        val restored = store.keep(ConnectedViewModel(deps)); observe(restored.uiState); runCurrent()
        restored.retryGreeting(sent.clientMessageId); runCurrent()
        assertEquals(sent, deps.greetingCommands.last())
    }

    @Test fun `connection failure clears public content while keeping restricted exit available`() = scenario { deps, store ->
        deps.connection.value = LoadState.Ready(MeetFixtures.connection, 3)
        val model = store.keep(ConnectedViewModel(deps)); observe(model.uiState); runCurrent()
        deps.connection.value = LoadState.Failed(AppError.Network, 4)
        deps.drivingState.value = DrivingState(policy = DrivingPolicy.UNKNOWN)
        runCurrent(); assertNull(model.uiState.value.connection); assertTrue(model.uiState.value.canLeave)
        model.leave(); runCurrent(); assertEquals("connection", deps.leaveCommands.single().connectionId)
    }

    @Test fun `candidate cooldown is rechecked and unknown server time fails closed`() = scenario { deps, store ->
        deps.discovery.value = deps.discovery.value.copy(candidates = listOf(MeetFixtures.candidate.copy(requestAvailableAtEpochMillis = 220_000)))
        deps.serverTime.value = null
        val model = store.keep(CandidateViewModel(deps, "candidate")); observe(model.uiState); runCurrent()
        assertFalse(model.uiState.value.canRequest)
        model.requestConnection(); runCurrent(); assertTrue(deps.requestCommands.isEmpty())
        deps.serverTime.value = ServerTimeAnchor(220_000, 10_000); runCurrent()
        assertTrue(model.uiState.value.canRequest)
        model.requestConnection(); runCurrent(); assertEquals(1, deps.requestCommands.size)
    }

    @Test fun `removed request does not return to initial loading`() = scenario { deps, store ->
        deps.requests.value = LoadState.Ready(listOf(MeetFixtures.request), 2)
        val model = store.keep(RequestViewModel(deps, "request")); observe(model.uiState); runCurrent()
        deps.requests.value = LoadState.Ready(emptyList(), 3); runCurrent()
        assertNull(model.uiState.value.request); assertFalse(model.uiState.value.loading)
    }

    @Test fun `leave failure for old connection cannot block leaving new connection`() = scenario { deps, store ->
        deps.connection.value = LoadState.Ready(MeetFixtures.connection, 3)
        val result = CompletableDeferred<AppResult<Connection>>()
        deps.leaveResult = { result.await() }
        val model = store.keep(ConnectedViewModel(deps)); observe(model.uiState); runCurrent()
        model.leave(); runCurrent()
        deps.connection.value = LoadState.Ready(MeetFixtures.connection.copy(id = "new-connection", version = 5), 5)
        runCurrent(); result.complete(AppResult.Failure(AppError.Network)); runCurrent()
        deps.leaveResult = { AppResult.Success(MeetFixtures.ended.copy(id = "new-connection")) }
        model.leave(); runCurrent()
        assertEquals(listOf("connection", "new-connection"), deps.leaveCommands.map { it.connectionId })
        assertNotEquals(deps.leaveCommands.first().meta.commandId, deps.leaveCommands.last().meta.commandId)
    }

}
