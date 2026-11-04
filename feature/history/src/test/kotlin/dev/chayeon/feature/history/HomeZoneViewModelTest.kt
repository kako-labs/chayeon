package dev.chayeon.feature.history

import androidx.lifecycle.ViewModelStore
import dev.chayeon.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeZoneViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { store.clear(); Dispatchers.resetMain() }

    private fun grantedSettings() = LoadState.Ready(HistoryFixtures.settings.copy(consents = listOf(
        ConsentRecord("location", 2, ConsentScope.LOCATION_USE, ConsentStatus.GRANTED, HistoryFixtures.policy.consentDocumentVersion, 0))), 2)

    @Test fun deviceSettingNeedsSavedConsentAndConsentScreenWaitsForPendingSave() = runTest(dispatcher) {
        val fake = FakeHistoryDeps()
        var permissions = 0
        val deps = object : HistoryDeps by fake {
            override val syntheticHomeEnabled = true
            override suspend fun prepareHomeLocation(): Boolean { permissions++; return true }
        }
        val home = HomeZoneViewModel(deps).also { store.put("home", it) }
        val consent = ConsentViewModel(deps).also { store.put("consent", it) }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { home.uiState.collect() }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { consent.uiState.collect() }
        runCurrent()
        assertEquals(false, home.uiState.value.locationConsentGranted)
        home.setHome(LocationSource.DEVICE); runCurrent()
        assertEquals(0, permissions); assertTrue(fake.calls.isEmpty())
        home.setHome(LocationSource.SYNTHETIC); runCurrent()
        assertEquals(listOf("home-set"), fake.calls); fake.calls.clear()
        fake.gate = CompletableDeferred()
        consent.record(ConsentScope.LOCATION_USE, true); runCurrent()
        var opened = false
        consent.openHomeZone { opened = true }; runCurrent()
        assertFalse(opened)
        assertEquals(false, home.uiState.value.locationConsentGranted)
        fake.settingsState.value = grantedSettings()
        fake.gate!!.complete(Unit); runCurrent(); fake.gate = null
        consent.openHomeZone { opened = true }; runCurrent(); assertTrue(opened)
        assertEquals(true, home.uiState.value.locationConsentGranted)
        home.setHome(LocationSource.DEVICE); runCurrent(); assertEquals(1, permissions)
        fake.settingsState.value = LoadState.Loading(); runCurrent()
        assertNull(home.uiState.value.locationConsentGranted)
        home.setHome(LocationSource.DEVICE); runCurrent(); assertEquals(1, permissions)
    }

    @Test fun commandRechecksPolicyAfterRuntimePermissionAndResubscribeNeverReplaysAllowed() = runTest(dispatcher) {
        val fake = FakeHistoryDeps()
        fake.settingsState.value = grantedSettings()
        val permission = CompletableDeferred<Boolean>()
        val deps = object : HistoryDeps by fake {
            override suspend fun prepareHomeLocation() = permission.await()
            override val syntheticHomeEnabled = true
        }
        val model = HomeZoneViewModel(deps).also { store.put("zone", it) }
        val observer = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect() }
        runCurrent()
        assertEquals(DrivingPolicy.ALLOWED, model.uiState.value.driving.policy)
        model.setHome(LocationSource.DEVICE); runCurrent()
        fake.driving.value = DrivingState(policy = DrivingPolicy.RESTRICTED)
        permission.complete(true); runCurrent()
        assertTrue(fake.calls.isEmpty())
        model.setHome(LocationSource.SYNTHETIC); model.selectRadius(HomeZoneRadius.WIDE); model.askClear(); model.clear()
        runCurrent(); assertTrue(fake.calls.isEmpty())
        observer.cancel(); advanceTimeBy(5_001); runCurrent()
        assertEquals(DrivingPolicy.UNKNOWN, model.uiState.value.driving.policy)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect() }
        runCurrent()
        assertEquals(DrivingPolicy.RESTRICTED, model.uiState.value.driving.policy)
    }

    @Test fun clearRequiresConfirmationAndDoesNotProceedIfDrivingChangesAfterConfirmation() = runTest(dispatcher) {
        val fake = FakeHistoryDeps()
        fake.homeZone.value = HomeZoneState(HomeZoneStatus.INSIDE, HomeZoneRadius.MEDIUM, LocationSource.SYNTHETIC)
        val model = HomeZoneViewModel(fake).also { store.put("zone", it) }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect() }
        runCurrent()
        model.clear(); runCurrent(); assertTrue(fake.calls.isEmpty())
        model.askClear(); runCurrent(); assertTrue(model.uiState.value.confirmingClear)
        fake.driving.value = DrivingState()
        model.clear(); runCurrent(); assertTrue(fake.calls.isEmpty())
        fake.driving.value = DrivingState(policy = DrivingPolicy.ALLOWED)
        model.clear(); runCurrent(); assertEquals(listOf("home-clear"), fake.calls)
        assertFalse(model.uiState.value.confirmingClear)
    }
}
