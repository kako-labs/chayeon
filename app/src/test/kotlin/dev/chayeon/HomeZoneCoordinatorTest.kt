package dev.chayeon

import dev.chayeon.core.engine.*
import dev.chayeon.core.model.*
import dev.chayeon.data.social.EmptyDiscoveryRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

internal class TestHomeZone : HomeZoneRepository {
    override val state = MutableStateFlow(HomeZoneState())
    override suspend fun setFromCurrentPosition(radius: HomeZoneRadius, source: LocationSource): AppResult<Unit> {
        state.value = HomeZoneState(HomeZoneStatus.INSIDE, radius, source)
        return AppResult.Success(Unit)
    }
    override suspend fun changeRadius(radius: HomeZoneRadius): AppResult<Unit> {
        state.value = state.value.copy(radius = radius); return AppResult.Success(Unit)
    }
    override suspend fun clear(): AppResult<Unit> { state.value = HomeZoneState(); return AppResult.Success(Unit) }
}

internal class HomeEngineFixture {
    var now = 1_000_000L
    private var ids = 0
    private var commands = 0
    val policy = EnginePolicy()
    val engine = ChayeonEngine(object : SignatureRules {
        override fun title(traits: List<PublicTrait>) = "가상 시그니처"
        override fun chemistry(first: PublicSignature, second: PublicSignature) = Chemistry()
    }, EngineClock { now }, EngineIdGenerator { "id-${++ids}" }, MemoryEngineStore(), policy, demoEnabled = false)
    fun meta() = CommandMeta("command-${++commands}")
    fun join(actor: String): Session {
        val session = engine.enterSession(actor, EnterSessionRequest(meta(), SessionMode.SOLO)).ok()
        engine.saveProfile(actor, SaveProfileRequest(meta(), ProfileDraft("가상상대", AvatarId.DAWN, emptySet()))).ok()
        ConsentScope.entries.forEach { engine.recordConsent(actor, RecordConsentRequest(meta(), it, ConsentAction.GRANT, policy.consentDocumentVersion)).ok() }
        engine.setDiscovery(actor, SetDiscoveryRequest(meta(), DiscoveryMode.ON)).ok()
        return session
    }
    fun snapshot(actor: String) = engine.snapshotFor(actor).ok()
    fun request() = engine.requestConnection("a", RequestConnectionRequest(meta(), snapshot("b").profile!!.id)).ok()
    fun accept(request: ConnectionRequest) = engine.respondConnection("b", RespondConnectionRequest(meta(), request.id, RequestDecision.ACCEPT))
    fun advance(ms: Long) { now += ms; engine.tick(now) }
}
internal fun <T> AppResult<T>.ok(): T = (this as AppResult.Success).value

@OptIn(ExperimentalCoroutinesApi::class)
class HomeZoneCoordinatorTest {
    @Test fun restrictedPresenceStopsHeartbeatAndAwayRetriesUntilAnExplicitConfirmation() = runTest {
        for (suppressed in listOf(false, true)) {
            val home = TestHomeZone()
            if (suppressed) home.setFromCurrentPosition()
            val coordinator = HomeZoneCoordinator(home, {}, backgroundScope)
            var granted = true
            val calls = mutableListOf<SetSyntheticPresenceRequest>()
            coordinator.prepareSession(HomeEngineFixture().join("a").copy(mode = SessionMode.SERVER_DEMO)) { request, _ ->
                calls += request
                if (granted) AppResult.Success(CommandReceipt(request.meta.commandId, 1)) else AppResult.Failure(AppError.Restricted)
            }
            runCurrent()
            granted = false
            if (suppressed) {
                home.state.value = HomeZoneState(); runCurrent()
            } else { advanceTimeBy(20_000); runCurrent() }
            val rejected = calls.last()
            val rejectedCount = calls.size
            home.setFromCurrentPosition(); runCurrent()
            advanceTimeBy(120_000); runCurrent()
            assertEquals(rejectedCount, calls.size)
            assertEquals(AppResult.Failure(AppError.Restricted), coordinator.confirmPresence(retryRestricted = false))
            granted = true
            coordinator.confirmPresence(); runCurrent()
            assertEquals(rejectedCount + 1, calls.size)
            assertNotEquals(rejected.meta.commandId, calls.last().meta.commandId)
            assertEquals(SyntheticPresence.AWAY, calls.last().presence)
            assertTrue(coordinator.confirmPresence() is AppResult.Success)
            coordinator.endSession()
        }
    }

    @Test fun rejectedInitialAwayDoesNotBackOffUntilConsentAllowsExplicitConfirmation() = runTest {
        val home = TestHomeZone().apply { state.value = HomeZoneState(HomeZoneStatus.PERMISSION_OFF, HomeZoneRadius.MEDIUM) }
        val coordinator = HomeZoneCoordinator(home, {}, backgroundScope)
        var sends = 0
        var granted = false
        coordinator.prepareSession(HomeEngineFixture().join("a")) { request, _ ->
            sends++
            if (granted) AppResult.Success(CommandReceipt(request.meta.commandId, 1)) else AppResult.Failure(AppError.Restricted)
        }
        runCurrent(); advanceTimeBy(120_000); runCurrent(); assertEquals(1, sends)
        assertEquals(AppResult.Failure(AppError.Restricted), confirmPresenceForUi(coordinator))
        assertEquals(2, sends)
        granted = true
        assertTrue(confirmPresenceForUi(coordinator) is AppResult.Success)
        assertEquals(3, sends)
        coordinator.endSession()
    }

    @Test fun serverHeartbeatsUseFreshKeysEveryTwentySecondsAndStopForSuppressionAndSessionEnd() = runTest {
        val home = TestHomeZone()
        val coordinator = HomeZoneCoordinator(home, {}, backgroundScope)
        val session = HomeEngineFixture().join("a").copy(mode = SessionMode.SERVER_DEMO)
        val calls = mutableListOf<Pair<Long, SetSyntheticPresenceRequest>>()
        var failHeartbeat = false
        coordinator.prepareSession(session) { request, allowed ->
            assertTrue(allowed())
            calls += currentTime to request
            if (failHeartbeat) AppResult.Failure(AppError.Network) else AppResult.Success(CommandReceipt(request.meta.commandId, 1))
        }
        runCurrent()
        advanceTimeBy(19_999); runCurrent(); assertEquals(1, calls.size)
        advanceTimeBy(1); runCurrent(); assertEquals(listOf(0L, 20_000L), calls.map { it.first })
        assertNotEquals(calls[0].second.meta.commandId, calls[1].second.meta.commandId)
        failHeartbeat = true
        advanceTimeBy(20_000); runCurrent()
        assertTrue(coordinator.confirmPresence() is AppResult.Success)
        failHeartbeat = false
        home.setFromCurrentPosition(); runCurrent()
        assertEquals(SyntheticPresence.AWAY, calls.last().second.presence)
        val suppressedCount = calls.size
        advanceTimeBy(80_000); runCurrent(); assertEquals(suppressedCount, calls.size)
        home.state.value = home.state.value.copy(status = HomeZoneStatus.OUTSIDE); runCurrent()
        assertEquals(SyntheticPresence.NEARBY, calls.last().second.presence)
        coordinator.endSession()
        val endedCount = calls.size
        advanceTimeBy(80_000); runCurrent(); assertEquals(endedCount, calls.size)
        assertTrue(coordinator.confirmPresence() is AppResult.Failure)
    }

    @Test fun hungHeartbeatDoesNotInvalidateConfirmationAndSuppressionCancelsItWithoutWaiting() = runTest {
        val home = TestHomeZone()
        val coordinator = HomeZoneCoordinator(home, {}, backgroundScope)
        val session = HomeEngineFixture().join("a").copy(mode = SessionMode.SERVER_DEMO)
        val calls = mutableListOf<SyntheticPresence>()
        coordinator.prepareSession(session) { request, allowed ->
            assertTrue(allowed())
            calls += request.presence
            if (calls.size == 2) awaitCancellation()
            AppResult.Success(CommandReceipt(request.meta.commandId, 1))
        }
        runCurrent(); advanceTimeBy(20_000); runCurrent()
        assertEquals(listOf(SyntheticPresence.NEARBY, SyntheticPresence.NEARBY), calls)
        assertTrue(withTimeout(1) { coordinator.confirmPresence() } is AppResult.Success)
        home.setFromCurrentPosition(); runCurrent()
        assertEquals(SyntheticPresence.AWAY, calls.last())
        assertTrue(withTimeout(1) { coordinator.confirmPresence() } is AppResult.Success)
        withTimeout(1) { coordinator.endSession() }
        advanceTimeBy(80_000); runCurrent()
        assertEquals(3, calls.size)
    }

    @Test fun soloSessionsNeverRenewPresenceOnATimer() = runTest {
        val coordinator = HomeZoneCoordinator(TestHomeZone(), {}, backgroundScope)
        var sends = 0
        coordinator.prepareSession(HomeEngineFixture().join("a")) { request, _ ->
            sends++
            AppResult.Success(CommandReceipt(request.meta.commandId, 1))
        }
        runCurrent(); advanceTimeBy(200_000); runCurrent()
        assertEquals(1, sends)
        coordinator.endSession()
    }

    @Test fun hungSendCannotBlockLifecycleAndAChangedZoneInvalidatesItsTransportRetry() = runTest {
        val home = TestHomeZone()
        val coordinator = HomeZoneCoordinator(home, {}, backgroundScope)
        val session = HomeEngineFixture().join("a").copy(mode = SessionMode.SERVER_DEMO)
        val release = CompletableDeferred<Unit>()
        val staleRetry = CompletableDeferred<Boolean>()
        val delivered = mutableListOf<SyntheticPresence>()
        coordinator.prepareSession(session) { request, allowed ->
            if (request.presence == SyntheticPresence.NEARBY) {
                // Simulate a transport that finishes after cancellation, but honors the send guard.
                withContext(NonCancellable) { release.await(); staleRetry.complete(allowed()) }
            } else if (allowed()) delivered += request.presence
            AppResult.Success(CommandReceipt(request.meta.commandId, 1))
        }
        runCurrent()
        assertTrue(withTimeout(1) { coordinator.confirmPresence() } is AppResult.Failure)
        home.setFromCurrentPosition(); runCurrent()
        assertEquals(listOf(SyntheticPresence.AWAY), delivered)
        assertTrue(coordinator.confirmPresence() is AppResult.Success)
        withTimeout(1) { coordinator.endSession() }
        withTimeout(1) { coordinator.prepareSession(session) { _, _ -> awaitCancellation() } }
        runCurrent()
        assertTrue(withTimeout(1) { coordinator.confirmPresence() } is AppResult.Failure)
        withTimeout(1) { coordinator.endSession() }
        release.complete(Unit); runCurrent()
        assertFalse(staleRetry.await())
        assertEquals(listOf(SyntheticPresence.AWAY), delivered)
    }

    @Test fun lostAcknowledgementCannotSkipTheCorrectivePresenceWhenProtectionChanges() = runTest {
        val f = HomeEngineFixture(); val session = f.join("a"); f.join("b")
        val home = TestHomeZone()
        val coordinator = HomeZoneCoordinator(home, {}, backgroundScope)
        val requests = mutableListOf<SetSyntheticPresenceRequest>()
        var loseResponse = false
        val start = async { coordinator.prepareSession(session) { request, _ ->
            requests += request
            val applied = f.engine.setSyntheticPresence("a", request)
            if (loseResponse) AppResult.Failure(AppError.Network) else applied
        } }
        runCurrent(); start.await()
        for (first in listOf(HomeZoneStatus.INSIDE, HomeZoneStatus.OUTSIDE)) {
            loseResponse = true
            home.state.value = HomeZoneState(first, HomeZoneRadius.MEDIUM, LocationSource.SYNTHETIC)
            runCurrent()
            val unacknowledged = requests.last()
            val count = requests.size
            loseResponse = false
            home.state.value = home.state.value.copy(status = if (first == HomeZoneStatus.INSIDE) HomeZoneStatus.OUTSIDE else HomeZoneStatus.INSIDE)
            runCurrent()
            assertEquals(count + 1, requests.size)
            assertNotEquals(unacknowledged.meta.commandId, requests.last().meta.commandId)
            val suppressed = home.state.value.suppressed
            assertEquals(if (suppressed) SyntheticPresence.AWAY else SyntheticPresence.NEARBY, requests.last().presence)
            assertEquals(suppressed, f.snapshot("b").discovery.candidates.isEmpty())
            home.state.value = HomeZoneState(HomeZoneStatus.INSIDE, HomeZoneRadius.MEDIUM, LocationSource.SYNTHETIC)
            runCurrent()
        }
    }

    @Test fun rejectedAuthenticationDoesNotDeadlockSessionPreparationOrConfirmPresence() = runTest {
        val home = TestHomeZone().apply {
            state.value = HomeZoneState(HomeZoneStatus.LOCATION_UNKNOWN, HomeZoneRadius.MEDIUM, LocationSource.DEVICE)
        }
        val session = HomeEngineFixture().join("a")
        val coordinator = HomeZoneCoordinator(home, {}, backgroundScope)
        val start = async { coordinator.prepareSession(session) { _, _ -> AppResult.Failure(AppError.NotAuthorized) } }
        runCurrent()
        assertTrue(start.isCompleted)
        start.await()
        assertTrue(coordinator.confirmPresence() is AppResult.Failure)
        val confirmation = async { coordinator.confirmPresence() }
        runCurrent()
        assertTrue(confirmation.await() is AppResult.Failure)
    }

    @Test fun onboardingCanGrantConsentAndEnableDiscoveryAfterTheInitialPresenceWasRejected() = runTest {
        val f = HomeEngineFixture()
        val session = f.engine.enterSession("a", EnterSessionRequest(f.meta(), SessionMode.SOLO)).ok()
        val home = TestHomeZone()
        val coordinator = HomeZoneCoordinator(home, {}, backgroundScope)
        val sent = mutableListOf<SetSyntheticPresenceRequest>()
        val start = async { coordinator.prepareSession(session) { request, _ -> sent += request; f.engine.setSyntheticPresence("a", request) } }
        runCurrent(); start.await()
        advanceTimeBy(120_000); runCurrent(); assertEquals(1, sent.size)
        f.engine.saveProfile("a", SaveProfileRequest(f.meta(), ProfileDraft("가상상대", AvatarId.DAWN, emptySet()))).ok()
        ConsentScope.entries.forEach { f.engine.recordConsent("a", RecordConsentRequest(f.meta(), it, ConsentAction.GRANT, f.policy.consentDocumentVersion)).ok() }
        val discovery = HomeProtectedDiscoveryRepository(object : DiscoveryRepository by EmptyDiscoveryRepository() {
            override suspend fun setDiscovery(request: SetDiscoveryRequest) = f.engine.setDiscovery("a", request)
        }, home, coordinator)
        val enabled = async { discovery.setDiscovery(SetDiscoveryRequest(f.meta(), DiscoveryMode.ON)) }
        runCurrent(); advanceTimeBy(100); runCurrent()
        assertTrue(enabled.await() is AppResult.Success)
        assertNotEquals(sent.first().meta.commandId, sent.last().meta.commandId)
        assertEquals(DiscoveryMode.ON, f.snapshot("a").discovery.mode)
    }

    @Test fun coordinatorSuppressionMatchesAnExplicitAwayCommandWithoutAddingPeerFields() = runTest {
        suspend fun scenario(protected: Boolean): Pair<List<SyncSnapshot>, Long> {
            val f = HomeEngineFixture()
            val session = f.join("a"); f.join("b")
            val home = TestHomeZone()
            val coordinator = HomeZoneCoordinator(home, {}, backgroundScope)
            val start = async { coordinator.prepareSession(session) { request, _ -> f.engine.setSyntheticPresence("a", request) } }
            runCurrent(); start.await()
            f.accept(f.request()).ok()
            val sequence = mutableListOf(f.snapshot("b"))
            val began = f.now
            if (protected) home.state.value = HomeZoneState(HomeZoneStatus.INSIDE, HomeZoneRadius.MEDIUM, LocationSource.SYNTHETIC)
            else f.engine.setSyntheticPresence("a", SetSyntheticPresenceRequest(f.meta(), SyntheticPresence.AWAY)).ok()
            runCurrent()
            sequence += f.snapshot("b")
            assertEquals(ConnectionStatus.CHECKING, sequence.last().connections.single().status)
            assertTrue(f.snapshot("a").discovery.candidates.isEmpty())
            assertTrue(f.snapshot("b").discovery.candidates.isEmpty())
            f.advance(f.policy.exitGraceMillis - 1); sequence += f.snapshot("b")
            f.advance(1); sequence += f.snapshot("b")
            val ended = sequence.last().connections.single()
            assertEquals(ConnectionEndReason.DISTANCE, ended.endReason)
            if (protected) home.state.value = home.state.value.copy(status = HomeZoneStatus.OUTSIDE)
            else f.engine.setSyntheticPresence("a", SetSyntheticPresenceRequest(f.meta(), SyntheticPresence.NEARBY)).ok()
            runCurrent(); sequence += f.snapshot("b")
            assertTrue(f.snapshot("a").discovery.candidates.isNotEmpty())
            assertTrue(f.snapshot("b").discovery.candidates.isNotEmpty())
            coordinator.endSession()
            // Deterministic ids and an identical clock make normalization an identity operation.
            return sequence to (ended.endedAtEpochMillis!! - began)
        }
        val ordinary = scenario(false)
        val protected = scenario(true)
        assertEquals(ordinary.first, protected.first)
        assertEquals(10_000L, protected.second)
        assertEquals(ordinary.second, protected.second)
    }

    @Test fun pendingAcceptanceFailsAndAfterRelationshipsAndBlocksKeepTheirOrdinaryRules() = runTest {
        val f = HomeEngineFixture(); val session = f.join("a"); f.join("b")
        val home = TestHomeZone()
        val coordinator = HomeZoneCoordinator(home, {}, backgroundScope)
        val start = async { coordinator.prepareSession(session) { request, _ -> f.engine.setSyntheticPresence("a", request) } }
        runCurrent(); start.await()
        val pending = f.request()
        home.setFromCurrentPosition(HomeZoneRadius.MEDIUM, LocationSource.SYNTHETIC); runCurrent()
        assertTrue(f.snapshot("a").discovery.candidates.isEmpty())
        assertTrue(f.snapshot("b").discovery.candidates.isEmpty())
        assertEquals(AppError.PeerUnavailable, (f.accept(pending) as AppResult.Failure).error)
        home.state.value = home.state.value.copy(status = HomeZoneStatus.OUTSIDE); runCurrent()
        f.advance(f.policy.requestCooldownMillis)
        f.accept(pending).ok()
        home.state.value = home.state.value.copy(status = HomeZoneStatus.INSIDE); runCurrent()
        f.advance(f.policy.exitGraceMillis)
        val encounter = f.snapshot("a").encounters.single()
        val after = f.engine.requestAfter("a", RequestAfterRequest(f.meta(), encounter.id, ContactIds(instagramId = java.util.UUID.randomUUID().toString().replace("-", "").take(24)))).ok()
        f.engine.respondAfter("b", RespondAfterRequest(f.meta(), after.id, RequestDecision.ACCEPT, ContactIds(instagramId = java.util.UUID.randomUUID().toString().replace("-", "").take(24)))).ok()
        val relationship = f.snapshot("b").afterConnections.single()
        home.state.value = home.state.value.copy(status = HomeZoneStatus.OUTSIDE); runCurrent()
        home.state.value = home.state.value.copy(status = HomeZoneStatus.INSIDE); runCurrent()
        assertEquals(AfterConnectionStatus.CONNECTED, f.snapshot("b").afterConnections.single().status)
        assertEquals(relationship.id, f.snapshot("b").afterConnections.single().id)
        f.engine.blockPeer("a", BlockPeerRequest(f.meta(), f.snapshot("b").profile!!.id)).ok()
        assertEquals(AfterCloseReason.PEER_LEFT, f.snapshot("b").afterConnections.single().closeReason)
        home.state.value = home.state.value.copy(status = HomeZoneStatus.OUTSIDE); runCurrent()
        assertTrue(f.snapshot("a").discovery.candidates.isEmpty())
        assertTrue(f.snapshot("b").discovery.candidates.isEmpty())
    }

    @Test fun startupBindsBeforePublicationAndRetriesAwayWithoutWaitingOrEnablingDiscovery() = runTest {
        val home = TestHomeZone()
        val f = HomeEngineFixture(); val session = f.join("a"); f.join("b")
        f.engine.setDiscovery("a", SetDiscoveryRequest(f.meta(), DiscoveryMode.OFF)).ok()
        val order = mutableListOf<String>()
        val requests = mutableListOf<SetSyntheticPresenceRequest>()
        var online = false
        val coordinator = HomeZoneCoordinator(home, {
            order += "bind"
            home.state.value = HomeZoneState(HomeZoneStatus.LOCATION_UNKNOWN, HomeZoneRadius.MEDIUM, LocationSource.DEVICE)
        }, backgroundScope)
        val start = async { coordinator.prepareSession(session) { request, _ ->
            order += "send"; requests += request
            if (online) f.engine.setSyntheticPresence("a", request) else AppResult.Failure(AppError.Network)
        }; order += "ready" }
        runCurrent()
        assertTrue(start.isCompleted)
        assertEquals(listOf("bind", "ready", "send"), order)
        advanceTimeBy(999); runCurrent(); assertEquals(1, requests.size)
        advanceTimeBy(1); runCurrent(); assertEquals(2, requests.size)
        online = true
        advanceTimeBy(2_000); runCurrent(); start.await()
        assertEquals(1, requests.toSet().size)
        assertTrue(requests.all { it.presence == SyntheticPresence.AWAY })
        assertEquals("ready", order[1])
        coordinator.setDesiredPresence(SyntheticPresence.NEARBY) { DrivingPolicy.ALLOWED }; runCurrent()
        assertEquals(3, requests.size)
        home.state.value = home.state.value.copy(status = HomeZoneStatus.OUTSIDE); runCurrent()
        assertEquals(SyntheticPresence.NEARBY, requests.last().presence)
        assertEquals(DiscoveryMode.OFF, f.snapshot("a").discovery.mode)
        home.state.value = home.state.value.copy(status = HomeZoneStatus.INSIDE); runCurrent()
        assertEquals(SyntheticPresence.AWAY, requests.last().presence)
        assertTrue(coordinator.setDesiredPresence(SyntheticPresence.NEARBY) { DrivingPolicy.RESTRICTED } is AppResult.Failure)
    }
}
