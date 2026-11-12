package dev.chayeon

import androidx.lifecycle.ViewModelStore
import dev.chayeon.core.model.*
import dev.chayeon.data.vehicle.EmptyVehicleConsole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DemoConsoleViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = ViewModelStore()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { models.clear(); Dispatchers.resetMain() }

    @Test fun leavingSyntheticHomeRestoresNearbyAfterTourSeparation() = runTest(dispatcher) {
        val fixture = TestGraph().apply { ready() }
        val sent = mutableListOf<SyntheticPresence>()
        fixture.homeCoordinator.prepareSession(fixture.active) { request, _ ->
            sent += request.presence
            AppResult.Success(CommandReceipt(request.meta.commandId, 1))
        }
        fixture.homeCoordinator.setDesiredPresence(SyntheticPresence.AWAY, fixture.graph.currentPolicy)
        fixture.homeZone.value = HomeZoneState(HomeZoneStatus.INSIDE, HomeZoneRadius.MEDIUM, LocationSource.SYNTHETIC)
        fixture.graph.syntheticHomePosition = { inside ->
            fixture.homeZone.value = fixture.homeZone.value.copy(status = if (inside) HomeZoneStatus.INSIDE else HomeZoneStatus.INSIDE)
            AppResult.Success(Unit)
        }
        val model = DemoConsoleViewModel(fixture.graph).also { models.put("home", it) }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect {} }
        runCurrent()
        model.homePosition(false); runCurrent()
        assertEquals(SyntheticPresence.NEARBY, fixture.homeCoordinator.desiredPresence.value)
        assertEquals(SyntheticPresence.AWAY, sent.last())
        fixture.homeZone.value = fixture.homeZone.value.copy(status = HomeZoneStatus.OUTSIDE)
        runCurrent()
        assertEquals(SyntheticPresence.NEARBY, sent.last())
        model.homePosition(true); runCurrent()
        assertEquals(SyntheticPresence.AWAY, sent.last())
        assertEquals(SyntheticPresence.NEARBY, fixture.homeCoordinator.desiredPresence.value)
        fixture.homeCoordinator.endSession()
    }

    @Test fun failedOrRestrictedHomeMoveNeverRestoresPresence() = runTest(dispatcher) {
        val fixture = TestGraph().apply { ready() }
        fixture.homeCoordinator.prepareSession(fixture.active) { request, _ -> AppResult.Success(CommandReceipt(request.meta.commandId, 1)) }
        fixture.homeCoordinator.setDesiredPresence(SyntheticPresence.AWAY, fixture.graph.currentPolicy)
        var moves = 0
        fixture.graph.syntheticHomePosition = { moves++; AppResult.Failure(AppError.Restricted) }
        val model = DemoConsoleViewModel(fixture.graph).also { models.put("home-failure", it) }
        model.homePosition(false); runCurrent()
        assertEquals(1, moves)
        assertEquals(SyntheticPresence.AWAY, fixture.homeCoordinator.desiredPresence.value)
        fixture.driving.value = DrivingState()
        model.homePosition(false); runCurrent()
        assertEquals(1, moves)
        assertEquals(SyntheticPresence.AWAY, fixture.homeCoordinator.desiredPresence.value)
        fixture.homeCoordinator.endSession()
    }

    @Test fun eachChoiceFillsThreeDrivesReportsProgressAndLeavesOnlyLastUnseen() = runTest(dispatcher) {
        for (scenario in signatureScenarios) {
            val consoleState = MutableStateFlow(ConsoleState(available = true))
            val choices = mutableListOf<DriveScenario>()
            lateinit var fixture: TestGraph
            val console = object : VehicleConsole by EmptyVehicleConsole() {
                override val consoleState = consoleState
                override suspend fun replay(request: ReplayDriveRequest): AppResult<ConsoleState> {
                    choices += request.scenario
                    consoleState.value = ConsoleState(available = true, status = ReplayStatus.PLAYING, scenario = request.scenario)
                    backgroundScope.launch {
                        delay(1_000)
                        val count = choices.size
                        val recap = TripRecap("new-$count", count.toLong(), "new-trip-$count", count.toLong(), DataSource.SYNTHETIC,
                            KnotSeed(1), KnotSeed(2), emptyList(), firstSignature = count == 3)
                        fixture.recaps.value = LoadState.Ready(listOf(recap) + (fixture.recaps.value as LoadState.Ready).value, count.toLong())
                        consoleState.value = consoleState.value.copy(status = ReplayStatus.FINISHED)
                    }
                    return AppResult.Success(consoleState.value)
                }
            }
            fixture = TestGraph(console).apply {
                ready()
                recaps.value = LoadState.Ready(listOf(
                    TripRecap("old-demo", 1, "old-synthetic", 0, DataSource.SYNTHETIC, KnotSeed(1), KnotSeed(1), emptyList()),
                    TripRecap("old-real", 1, "old-vehicle", 0, DataSource.VEHICLE, KnotSeed(1), KnotSeed(1), emptyList(), seen = true),
                ), 1)
            }
            val model = DemoConsoleViewModel(fixture.graph).also { models.put(scenario.name, it) }
            val observer = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect {} }
            model.fill(scenario)
            runCurrent()
            assertTrue(model.uiState.value.busy)
            assertEquals(1, model.uiState.value.fillProgress)
            assertNull(model.uiState.value.completedRecapId)
            advanceTimeBy(1_000); runCurrent()
            assertEquals(2, model.uiState.value.fillProgress)
            advanceTimeBy(1_000); runCurrent()
            assertEquals(3, model.uiState.value.fillProgress)
            advanceTimeBy(1_000); runCurrent()
            assertFalse(model.uiState.value.busy)
            assertEquals("new-3", model.uiState.value.completedRecapId)
            assertEquals(List(3) { scenario }, choices)
            assertEquals(listOf("old-synthetic"), fixture.excludedTrips)
            assertEquals(listOf("old-demo", "new-1", "new-2"), fixture.seenCalls.map { it.recapId })
            assertFalse((fixture.recaps.value as LoadState.Ready).value.single { it.id == "new-3" }.seen)
            assertTrue(fixture.publications.isEmpty())
            model.consumeRecap()
            runCurrent()
            assertNull(model.uiState.value.completedRecapId)
            observer.cancel()
        }
    }

    @Test fun unknownPolicyRejectsAStaleConsoleTap() = runTest(dispatcher) {
        val fixture = TestGraph().apply { ready() }
        val model = DemoConsoleViewModel(fixture.graph).also { models.put("restricted", it) }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect {} }
        runCurrent()
        fixture.driving.value = DrivingState()
        model.fill(DriveScenario.CARE)
        runCurrent()
        assertFalse(model.uiState.value.busy)
        assertTrue(fixture.seenCalls.isEmpty())
        assertNull(model.uiState.value.completedRecapId)
    }
}
