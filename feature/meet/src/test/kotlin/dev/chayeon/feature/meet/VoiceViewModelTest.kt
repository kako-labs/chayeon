package dev.chayeon.feature.meet

import androidx.lifecycle.ViewModelStore
import dev.chayeon.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceViewModelTest {
    @Test fun autoModeWithUnmetConditionsKeepsManualRingFallback() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val deps = FakeMeetDeps()
        val store = ViewModelStore()
        val model = RequestViewModel(deps).also { store.put("request", it) }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect() }
        try {
            deps.requests.value = LoadState.Ready(listOf(MeetFixtures.request), 2)
            deps.drivingState.value = DrivingState(policy = DrivingPolicy.RESTRICTED)
            deps.settings.value = LoadState.Ready(ParticipationSettings("settings", 2,
                drivingConnectMode = DrivingConnectMode.AUTO,
                autoConnectConditions = AutoConnectConditions(mapOf(TraitId.SIGNAL_MANNERS to 100))), 2)
            runCurrent()
            assertTrue(model.uiState.value.receiveRequestsWhileDriving)
            assertTrue(deps.respondCommands.isEmpty())
            model.accept(); runCurrent()
            assertEquals(RequestDecision.ACCEPT, deps.respondCommands.single().decision)
            assertFalse(deps.respondCommands.single().automatic)
        } finally { store.clear(); runCurrent(); Dispatchers.resetMain() }
    }

    @Test fun incomingResponseRechecksSettingPolicyDirectionAndHome() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val deps = FakeMeetDeps()
        val store = ViewModelStore()
        val model = RequestViewModel(deps).also { store.put("request", it) }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect() }
        try {
            assertFalse(ParticipationSettings("settings", 0).receiveRequestsWhileDriving)
            deps.requests.value = LoadState.Ready(listOf(MeetFixtures.request), 2)
            deps.drivingState.value = DrivingState(policy = DrivingPolicy.RESTRICTED)
            runCurrent(); model.accept(); runCurrent()
            assertTrue(deps.respondCommands.isEmpty())
            deps.settings.value = LoadState.Ready(ParticipationSettings("settings", 1, receiveRequestsWhileDriving = true), 1)
            runCurrent(); model.accept(); runCurrent()
            assertEquals(1, deps.respondCommands.size)
            deps.drivingState.value = DrivingState(); runCurrent(); model.accept(); runCurrent()
            assertEquals(1, deps.respondCommands.size)
            deps.drivingState.value = DrivingState(policy = DrivingPolicy.RESTRICTED)
            deps.homeZone.value = HomeZoneState(HomeZoneStatus.INSIDE, HomeZoneRadius.MEDIUM)
            runCurrent(); model.accept(); runCurrent()
            assertEquals(1, deps.respondCommands.size)
            model.decline(); runCurrent()
            assertEquals(RequestDecision.DECLINE, deps.respondCommands.last().decision)
        } finally { store.clear(); runCurrent(); Dispatchers.resetMain() }
    }

    @Test fun muteIsAllowedInDriveButPermissionPromptRequiresParkAndActiveCall() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val deps = FakeMeetDeps()
        val store = ViewModelStore()
        val model = ConnectedViewModel(deps).also { store.put("call", it) }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect() }
        try {
            deps.connection.value = LoadState.Ready(MeetFixtures.connection, 3)
            deps.callState.value = VoiceCallState("connection", CallStatus.ACTIVE)
            deps.drivingState.value = DrivingState(policy = DrivingPolicy.RESTRICTED)
            runCurrent(); model.toggleMute(); model.requestMicrophone(); runCurrent()
            assertEquals(listOf(true), deps.muteCommands)
            assertEquals(0, deps.microphoneRequests)
            deps.drivingState.value = DrivingState()
            runCurrent(); model.toggleMute(); model.requestMicrophone(); runCurrent()
            assertEquals(listOf(true), deps.muteCommands)
            assertEquals(0, deps.microphoneRequests)
            deps.drivingState.value = MeetFixtures.parked
            runCurrent(); model.requestMicrophone(); runCurrent()
            assertEquals(1, deps.microphoneRequests)
            deps.connection.value = LoadState.Ready(MeetFixtures.ended, 4)
            runCurrent(); model.toggleMute(); runCurrent()
            assertEquals(listOf(true), deps.muteCommands)
        } finally { store.clear(); runCurrent(); Dispatchers.resetMain() }
    }
}
