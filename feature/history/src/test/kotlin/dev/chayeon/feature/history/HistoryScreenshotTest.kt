package dev.chayeon.feature.history

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModelStore
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.isDialog
import com.github.takahirom.roborazzi.captureRoboImage
import dev.chayeon.core.model.*
import dev.chayeon.core.ui.ChayeonTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w1682dp-h1210dp-land-mdpi")
class HistoryScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val store = ViewModelStore()
    @After fun close() { store.clear() }
    private fun capture(screen: HistoryScreen, scenario: ScreenScenario, override: HistoryUiState? = null, suffix: String? = null) {
        val state = override ?: screenState(screen, scenario)
        compose.setContent {
            ChayeonTheme(state.driving.policy, animatorScaleOverride = 0f) { RenderHistoryScreen(screen, state) }
        }
        compose.onNodeWithTag("screen").captureRoboImage("src/test/screenshots/${screen.name.lowercase()}_${suffix ?: scenario.name.lowercase()}.png")
    }
    @Test fun first_run_populated() = capture(HistoryScreen.FIRST_RUN, ScreenScenario.POPULATED)
    @Test fun first_run_loading() = capture(HistoryScreen.FIRST_RUN, ScreenScenario.LOADING)
    @Test fun first_run_empty() = capture(HistoryScreen.FIRST_RUN, ScreenScenario.EMPTY)
    @Test fun first_run_failure() = capture(HistoryScreen.FIRST_RUN, ScreenScenario.FAILURE)
    @Test fun first_run_restricted() = capture(HistoryScreen.FIRST_RUN, ScreenScenario.RESTRICTED)
    @Test fun first_run_unknown() = capture(HistoryScreen.FIRST_RUN, ScreenScenario.UNKNOWN)
    @Config(sdk = [35], qualifiers = "w840dp-h1210dp-port-mdpi")
    @Test fun first_run_narrow() = capture(HistoryScreen.FIRST_RUN, ScreenScenario.NARROW)
    @Test fun profile_setup_populated() = capture(HistoryScreen.PROFILE_SETUP, ScreenScenario.POPULATED)
    @Test fun profile_setup_loading() = capture(HistoryScreen.PROFILE_SETUP, ScreenScenario.LOADING)
    @Test fun profile_setup_empty() = capture(HistoryScreen.PROFILE_SETUP, ScreenScenario.EMPTY)
    @Test fun profile_setup_failure() = capture(HistoryScreen.PROFILE_SETUP, ScreenScenario.FAILURE)
    @Test fun profile_setup_restricted() = capture(HistoryScreen.PROFILE_SETUP, ScreenScenario.RESTRICTED)
    @Test fun profile_setup_unknown() = capture(HistoryScreen.PROFILE_SETUP, ScreenScenario.UNKNOWN)
    @Config(sdk = [35], qualifiers = "w840dp-h1210dp-port-mdpi")
    @Test fun profile_setup_narrow() = capture(HistoryScreen.PROFILE_SETUP, ScreenScenario.NARROW)
    @Test fun profile_edit_populated() = capture(HistoryScreen.PROFILE_EDIT, ScreenScenario.POPULATED)
    @Test fun profile_edit_loading() = capture(HistoryScreen.PROFILE_EDIT, ScreenScenario.LOADING)
    @Test fun profile_edit_empty() = capture(HistoryScreen.PROFILE_EDIT, ScreenScenario.EMPTY)
    @Test fun profile_edit_failure() = capture(HistoryScreen.PROFILE_EDIT, ScreenScenario.FAILURE)
    @Test fun profile_edit_restricted() = capture(HistoryScreen.PROFILE_EDIT, ScreenScenario.RESTRICTED)
    @Test fun profile_edit_unknown() = capture(HistoryScreen.PROFILE_EDIT, ScreenScenario.UNKNOWN)
    @Config(sdk = [35], qualifiers = "w840dp-h1210dp-port-mdpi")
    @Test fun profile_edit_narrow() = capture(HistoryScreen.PROFILE_EDIT, ScreenScenario.NARROW)
    @Test fun consent_saving_location() = capture(HistoryScreen.CONSENT, ScreenScenario.POPULATED,
        HistoryFixtures.state.copy(commands = mapOf("consent:LOCATION_USE" to CommandState(running = true))), "saving_location")
    @Test fun consent_populated() = capture(HistoryScreen.CONSENT, ScreenScenario.POPULATED)
    @Test fun consent_loading() = capture(HistoryScreen.CONSENT, ScreenScenario.LOADING)
    @Test fun consent_empty() = capture(HistoryScreen.CONSENT, ScreenScenario.EMPTY)
    @Test fun consent_failure() = capture(HistoryScreen.CONSENT, ScreenScenario.FAILURE)
    @Test fun consent_restricted() = capture(HistoryScreen.CONSENT, ScreenScenario.RESTRICTED)
    @Test fun consent_unknown() = capture(HistoryScreen.CONSENT, ScreenScenario.UNKNOWN)
    @Config(sdk = [35], qualifiers = "w840dp-h1210dp-port-mdpi")
    @Test fun consent_narrow() = capture(HistoryScreen.CONSENT, ScreenScenario.NARROW)
    @Test fun encounters_populated() = capture(HistoryScreen.ENCOUNTERS, ScreenScenario.POPULATED)
    @Test fun encounters_loading() = capture(HistoryScreen.ENCOUNTERS, ScreenScenario.LOADING)
    @Test fun encounters_empty() = capture(HistoryScreen.ENCOUNTERS, ScreenScenario.EMPTY)
    @Test fun encounters_failure() = capture(HistoryScreen.ENCOUNTERS, ScreenScenario.FAILURE)
    @Test fun encounters_restricted() = capture(HistoryScreen.ENCOUNTERS, ScreenScenario.RESTRICTED)
    @Test fun encounters_unknown() = capture(HistoryScreen.ENCOUNTERS, ScreenScenario.UNKNOWN)
    @Config(sdk = [35], qualifiers = "w840dp-h1210dp-port-mdpi")
    @Test fun encounters_narrow() = capture(HistoryScreen.ENCOUNTERS, ScreenScenario.NARROW)
    @Test fun encounter_detail_populated() = capture(HistoryScreen.ENCOUNTER_DETAIL, ScreenScenario.POPULATED)
    @Test fun encounter_detail_loading() = capture(HistoryScreen.ENCOUNTER_DETAIL, ScreenScenario.LOADING)
    @Test fun encounter_detail_empty() = capture(HistoryScreen.ENCOUNTER_DETAIL, ScreenScenario.EMPTY)
    @Test fun encounter_detail_failure() = capture(HistoryScreen.ENCOUNTER_DETAIL, ScreenScenario.FAILURE)
    @Test fun encounter_detail_restricted() = capture(HistoryScreen.ENCOUNTER_DETAIL, ScreenScenario.RESTRICTED)
    @Test fun encounter_detail_unknown() = capture(HistoryScreen.ENCOUNTER_DETAIL, ScreenScenario.UNKNOWN)
    @Config(sdk = [35], qualifiers = "w840dp-h1210dp-port-mdpi")
    @Test fun encounter_detail_narrow() = capture(HistoryScreen.ENCOUNTER_DETAIL, ScreenScenario.NARROW)
    @Test fun after_request_populated() = capture(HistoryScreen.AFTER_REQUEST, ScreenScenario.POPULATED)
    @Test fun after_request_loading() = capture(HistoryScreen.AFTER_REQUEST, ScreenScenario.LOADING)
    @Test fun after_request_empty() = capture(HistoryScreen.AFTER_REQUEST, ScreenScenario.EMPTY)
    @Test fun after_request_failure() = capture(HistoryScreen.AFTER_REQUEST, ScreenScenario.FAILURE)
    @Test fun after_request_restricted() = capture(HistoryScreen.AFTER_REQUEST, ScreenScenario.RESTRICTED)
    @Test fun after_request_unknown() = capture(HistoryScreen.AFTER_REQUEST, ScreenScenario.UNKNOWN)
    @Config(sdk = [35], qualifiers = "w840dp-h1210dp-port-mdpi")
    @Test fun after_request_narrow() = capture(HistoryScreen.AFTER_REQUEST, ScreenScenario.NARROW)
    @Test fun after_incoming_populated() = capture(HistoryScreen.AFTER_INCOMING, ScreenScenario.POPULATED)
    @Test fun after_incoming_loading() = capture(HistoryScreen.AFTER_INCOMING, ScreenScenario.LOADING)
    @Test fun after_incoming_empty() = capture(HistoryScreen.AFTER_INCOMING, ScreenScenario.EMPTY)
    @Test fun after_incoming_failure() = capture(HistoryScreen.AFTER_INCOMING, ScreenScenario.FAILURE)
    @Test fun after_incoming_restricted() = capture(HistoryScreen.AFTER_INCOMING, ScreenScenario.RESTRICTED)
    @Test fun after_incoming_unknown() = capture(HistoryScreen.AFTER_INCOMING, ScreenScenario.UNKNOWN)
    @Config(sdk = [35], qualifiers = "w840dp-h1210dp-port-mdpi")
    @Test fun after_incoming_narrow() = capture(HistoryScreen.AFTER_INCOMING, ScreenScenario.NARROW)
    @Test fun after_connected_populated() = capture(HistoryScreen.AFTER_CONNECTED, ScreenScenario.POPULATED)
    @Test fun after_connected_loading() = capture(HistoryScreen.AFTER_CONNECTED, ScreenScenario.LOADING)
    @Test fun after_connected_empty() = capture(HistoryScreen.AFTER_CONNECTED, ScreenScenario.EMPTY)
    @Test fun after_connected_failure() = capture(HistoryScreen.AFTER_CONNECTED, ScreenScenario.FAILURE)
    @Test fun after_connected_restricted() = capture(HistoryScreen.AFTER_CONNECTED, ScreenScenario.RESTRICTED)
    @Test fun after_connected_unknown() = capture(HistoryScreen.AFTER_CONNECTED, ScreenScenario.UNKNOWN)
    @Config(sdk = [35], qualifiers = "w840dp-h1210dp-port-mdpi")
    @Test fun after_connected_narrow() = capture(HistoryScreen.AFTER_CONNECTED, ScreenScenario.NARROW)
    @Test fun block_report_populated() = capture(HistoryScreen.BLOCK_REPORT, ScreenScenario.POPULATED)
    @Test fun block_report_loading() = capture(HistoryScreen.BLOCK_REPORT, ScreenScenario.LOADING)
    @Test fun block_report_empty() = capture(HistoryScreen.BLOCK_REPORT, ScreenScenario.EMPTY)
    @Test fun block_report_failure() = capture(HistoryScreen.BLOCK_REPORT, ScreenScenario.FAILURE)
    @Test fun block_report_restricted() = capture(HistoryScreen.BLOCK_REPORT, ScreenScenario.RESTRICTED)
    @Test fun block_report_unknown() = capture(HistoryScreen.BLOCK_REPORT, ScreenScenario.UNKNOWN)
    @Config(sdk = [35], qualifiers = "w840dp-h1210dp-port-mdpi")
    @Test fun block_report_narrow() = capture(HistoryScreen.BLOCK_REPORT, ScreenScenario.NARROW)
    @Test fun blocks_populated() = capture(HistoryScreen.BLOCKS, ScreenScenario.POPULATED)
    @Test fun blocks_loading() = capture(HistoryScreen.BLOCKS, ScreenScenario.LOADING)
    @Test fun blocks_empty() = capture(HistoryScreen.BLOCKS, ScreenScenario.EMPTY)
    @Test fun blocks_failure() = capture(HistoryScreen.BLOCKS, ScreenScenario.FAILURE)
    @Test fun blocks_restricted() = capture(HistoryScreen.BLOCKS, ScreenScenario.RESTRICTED)
    @Test fun blocks_unknown() = capture(HistoryScreen.BLOCKS, ScreenScenario.UNKNOWN)
    @Config(sdk = [35], qualifiers = "w840dp-h1210dp-port-mdpi")
    @Test fun blocks_narrow() = capture(HistoryScreen.BLOCKS, ScreenScenario.NARROW)
    @Test fun cleanup_populated() = capture(HistoryScreen.CLEANUP, ScreenScenario.POPULATED)
    @Test fun cleanup_loading() = capture(HistoryScreen.CLEANUP, ScreenScenario.LOADING)
    @Test fun cleanup_empty() = capture(HistoryScreen.CLEANUP, ScreenScenario.EMPTY)
    @Test fun cleanup_failure() = capture(HistoryScreen.CLEANUP, ScreenScenario.FAILURE)
    @Test fun cleanup_restricted() = capture(HistoryScreen.CLEANUP, ScreenScenario.RESTRICTED)
    @Test fun cleanup_unknown() = capture(HistoryScreen.CLEANUP, ScreenScenario.UNKNOWN)
    @Config(sdk = [35], qualifiers = "w840dp-h1210dp-port-mdpi")
    @Test fun cleanup_narrow() = capture(HistoryScreen.CLEANUP, ScreenScenario.NARROW)
    @Test fun after_declined() = capture(HistoryScreen.AFTER_INCOMING, ScreenScenario.POPULATED,
        HistoryFixtures.state.copy(requests = LoadState.Ready(listOf(HistoryFixtures.request.copy(status = AfterRequestStatus.DECLINED)), 2)), "declined")
    @Test fun after_expired() = capture(HistoryScreen.AFTER_INCOMING, ScreenScenario.POPULATED,
        HistoryFixtures.state.copy(requests = LoadState.Ready(listOf(HistoryFixtures.request.copy(status = AfterRequestStatus.EXPIRED)), 2)), "expired")
    @Test fun after_withdrawn() = capture(HistoryScreen.AFTER_INCOMING, ScreenScenario.POPULATED,
        HistoryFixtures.state.copy(requests = LoadState.Ready(listOf(HistoryFixtures.request.copy(status = AfterRequestStatus.WITHDRAWN)), 2)), "withdrawn")
    @Test fun after_accepted() = capture(HistoryScreen.AFTER_INCOMING, ScreenScenario.POPULATED,
        HistoryFixtures.state.copy(requests = LoadState.Ready(listOf(HistoryFixtures.request.copy(status = AfterRequestStatus.ACCEPTED)), 2)), "accepted")
    @Test fun consentGrantedButDiscoveryOff() = capture(HistoryScreen.CONSENT, ScreenScenario.POPULATED,
        consentReadyState(false), "granted_off")
    @Test fun consentDiscoveryExplicitlyOn() = capture(HistoryScreen.CONSENT, ScreenScenario.POPULATED,
        consentReadyState(true), "granted_on")
    @Test fun blockReportAnonymizedPeer() = capture(HistoryScreen.BLOCK_REPORT, ScreenScenario.POPULATED,
        screenState(HistoryScreen.BLOCK_REPORT, ScreenScenario.POPULATED).copy(safetyPeer = HistoryFixtures.peer.copy(
            nickname = "가상 프로필", kind = ProfileKind.VIRTUAL_PROFILE, signature = null, interests = emptySet())), "anonymized")
    @Test fun encounterWithoutSharedInterests() = capture(HistoryScreen.ENCOUNTER_DETAIL, ScreenScenario.POPULATED,
        screenState(HistoryScreen.ENCOUNTER_DETAIL, ScreenScenario.POPULATED).let { state ->
            state.copy(encounter = LoadState.Ready(state.encounter.valueOrNull()!!.copy(sharedInterests = emptySet()), 1))
        }, "no_shared_interests")
    @Test fun encounterWithoutChemistry() = capture(HistoryScreen.ENCOUNTER_DETAIL, ScreenScenario.POPULATED,
        HistoryFixtures.state.copy(encounter = LoadState.Ready(HistoryFixtures.encounter.copy(chemistry = Chemistry()), 1)), "no_chemistry")
    @Test fun firstRunSoloOnly() = capture(HistoryScreen.FIRST_RUN, ScreenScenario.POPULATED,
        HistoryFixtures.state.copy(modes = setOf(SessionMode.SOLO)), "solo")
    @Test fun firstRunServerRoom() = capture(HistoryScreen.FIRST_RUN, ScreenScenario.POPULATED,
        HistoryFixtures.state.copy(selectedMode = SessionMode.SERVER_DEMO, roomCode = "DEMO-ROOM"), "server")

    private fun consentReadyState(discoveryOn: Boolean) = HistoryFixtures.state.copy(
        settings = LoadState.Ready(HistoryFixtures.settings.copy(consents = ConsentScope.entries.map {
            ConsentRecord(it.name, 1, it, ConsentStatus.GRANTED, HistoryFixtures.policy.consentDocumentVersion, 1)
        }), 1), discovery = DiscoveryState(mode = if (discoveryOn) DiscoveryMode.ON else DiscoveryMode.OFF),
    )

    private fun confirmation(screen: HistoryScreen, kind: ConfirmationKind) {
        val deps = FakeHistoryDeps()
        val model: HistoryViewModel = when (screen) {
            HistoryScreen.CLEANUP -> CleanupViewModel(deps)
            HistoryScreen.AFTER_CONNECTED -> AfterConnectionsViewModel(deps)
            HistoryScreen.AFTER_REQUEST -> AfterRequestViewModel(deps, HistoryFixtures.encounter.id)
            HistoryScreen.ENCOUNTER_DETAIL -> EncounterDetailViewModel(deps, HistoryFixtures.encounter.id)
            else -> SafetyViewModel(deps, HistoryFixtures.block.peer.id)
        }
        store.put("confirmation", model)
        compose.setContent {
            val state by model.uiState.collectAsState()
            ChayeonTheme(state.driving.policy, 0f) { RenderHistoryScreen(screen, state) }
        }
        compose.runOnIdle {
            when (model) {
                is CleanupViewModel -> model.ask(kind, if (kind == ConfirmationKind.LEAVE) HistoryFixtures.connection.id else "")
                is AfterConnectionsViewModel -> model.askEnd()
                is AfterRequestViewModel -> model.askWithdraw(HistoryFixtures.request.id)
                is EncounterDetailViewModel -> model.askDelete()
                is SafetyViewModel -> if (kind == ConfirmationKind.BLOCK) model.askBlock() else model.askUnblock(HistoryFixtures.block)
            }
        }
        compose.onNode(isDialog()).captureRoboImage("src/test/screenshots/${screen.name.lowercase()}_confirm_${kind.name.lowercase()}.png")
    }
    @Test fun cleanupConfirmDeleteAccount() = confirmation(HistoryScreen.CLEANUP, ConfirmationKind.DELETE_ACCOUNT)
    @Test fun cleanupConfirmSignOut() = confirmation(HistoryScreen.CLEANUP, ConfirmationKind.SIGN_OUT)
    @Test fun cleanupConfirmRecords() = confirmation(HistoryScreen.CLEANUP, ConfirmationKind.DELETE_RECORDS)
    @Test fun cleanupConfirmDiscovery() = confirmation(HistoryScreen.CLEANUP, ConfirmationKind.DISCOVERY_OFF)
    @Test fun cleanupConfirmLeave() = confirmation(HistoryScreen.CLEANUP, ConfirmationKind.LEAVE)
    @Test fun afterConfirmEnd() = confirmation(HistoryScreen.AFTER_CONNECTED, ConfirmationKind.END_RELATIONSHIP)
    @Test fun afterConfirmWithdraw() = confirmation(HistoryScreen.AFTER_REQUEST, ConfirmationKind.WITHDRAW)
    @Test fun encounterConfirmDelete() = confirmation(HistoryScreen.ENCOUNTER_DETAIL, ConfirmationKind.DELETE_ENCOUNTER)
    @Test fun safetyConfirmBlock() = confirmation(HistoryScreen.BLOCK_REPORT, ConfirmationKind.BLOCK)
    @Test fun safetyConfirmUnblock() = confirmation(HistoryScreen.BLOCKS, ConfirmationKind.UNBLOCK)
}
