package dev.chayeon.feature.signature

import androidx.lifecycle.ViewModelStore
import dev.chayeon.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CallSettingsTest {
    @Test fun settingDefaultsOffAndPreservesOtherChoicesAndCannotChangeInDrive() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val deps = SignatureTestDeps()
        deps.profileRepository.settings.value = LoadState.Ready(ParticipationSettings("settings", 3, notifyRequests = false), 3)
        val store = ViewModelStore()
        val model = MyProfileViewModel(deps).also { store.put("profile", it) }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect() }
        try {
            runCurrent()
            assertFalse((model.uiState.value.settings as LoadState.Ready).value.receiveRequestsWhileDriving)
            model.setReceiveRequestsWhileDriving(true); runCurrent()
            val request = deps.profileRepository.settingsCalls.single()
            assertTrue(request.receiveRequestsWhileDriving)
            assertFalse(request.notifyRequests)
            for (policy in listOf(DrivingPolicy.RESTRICTED, DrivingPolicy.UNKNOWN)) {
                deps.drivingStateProvider.drivingState.value = DrivingState(policy = policy)
                runCurrent(); model.setReceiveRequestsWhileDriving(false); runCurrent()
                assertEquals(1, deps.profileRepository.settingsCalls.size)
            }
        } finally { store.clear(); runCurrent(); Dispatchers.resetMain() }
    }
    @Test fun autoConditionsUseOnlyScoredTraitsAndPreserveChoicesAcrossModeChanges() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val deps = SignatureTestDeps()
        val store = ViewModelStore()
        val model = MyProfileViewModel(deps).also { store.put("profile", it) }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect() }
        try {
            runCurrent()
            model.setMinimumScore(TraitId.SIGNAL_MANNERS, 90); runCurrent()
            model.setRequireSharedInterest(true); runCurrent()
            model.setDrivingConnectMode(DrivingConnectMode.AUTO); runCurrent()
            val selected = deps.profileRepository.settingsCalls.last()
            assertEquals(DrivingConnectMode.AUTO, selected.drivingConnectMode)
            assertEquals(mapOf(TraitId.SIGNAL_MANNERS to 90), selected.autoConnectConditions.minimumScores)
            assertTrue(selected.autoConnectConditions.requireSharedInterest)
            model.setMinimumScore(TraitId.NIGHT_DRIVE, 90)
            model.setMinimumScore(TraitId.SIGNAL_MANNERS, 59)
            model.setMinimumScore(TraitId.SIGNAL_MANNERS, 61)
            runCurrent()
            assertEquals(3, deps.profileRepository.settingsCalls.size)
            for (policy in listOf(DrivingPolicy.RESTRICTED, DrivingPolicy.UNKNOWN)) {
                deps.drivingStateProvider.drivingState.value = DrivingState(policy = policy)
                runCurrent()
                model.setMinimumScore(TraitId.SIGNAL_MANNERS, 60); runCurrent()
                model.setRequireSharedInterest(false); runCurrent()
                model.setDrivingConnectMode(DrivingConnectMode.OFF); runCurrent()
            }
            assertEquals(3, deps.profileRepository.settingsCalls.size)
        } finally { store.clear(); runCurrent(); Dispatchers.resetMain() }
    }

}
