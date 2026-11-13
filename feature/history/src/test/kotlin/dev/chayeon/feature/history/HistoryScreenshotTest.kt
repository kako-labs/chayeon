package dev.chayeon.feature.history

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModelStore
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
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
    private fun capture(screen: HistoryScreen, scenario: ScreenScenario, override: HistoryUiState? = null, suffix: String? = null, showSourceLabels: Boolean = false) {
        val state = override ?: screenState(screen, scenario)
        compose.setContent {
            ChayeonTheme(state.driving.policy, animatorScaleOverride = 0f) { RenderHistoryScreen(screen, state, showSourceLabels = showSourceLabels) }
        }
        if (scenario == ScreenScenario.FAILURE && screen in setOf(HistoryScreen.PROFILE_SETUP, HistoryScreen.PROFILE_EDIT))
            compose.onNodeWithText("프로필을 저장하지 못했어요").performScrollTo()
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
    @Test fun consent_populated() = capture(HistoryScreen.CONSENT, ScreenScenario.POPULATED)
    @Test fun consent_loading() = capture(HistoryScreen.CONSENT, ScreenScenario.LOADING)
    @Test fun consent_empty() = capture(HistoryScreen.CONSENT, ScreenScenario.EMPTY)
    @Test fun consent_failure() = capture(HistoryScreen.CONSENT, ScreenScenario.FAILURE)
    @Test fun consent_restricted() = capture(HistoryScreen.CONSENT, ScreenScenario.RESTRICTED)
    @Test fun consent_unknown() = capture(HistoryScreen.CONSENT, ScreenScenario.UNKNOWN)
    @Config(sdk = [35], qualifiers = "w840dp-h1210dp-port-mdpi")
    @Test fun consent_narrow() = capture(HistoryScreen.CONSENT, ScreenScenario.NARROW)
    @Test fun relationships_populated() = capture(HistoryScreen.RELATIONSHIPS, ScreenScenario.POPULATED)
    @Test fun relationships_loading() = capture(HistoryScreen.RELATIONSHIPS, ScreenScenario.LOADING)
    @Test fun relationships_empty() = capture(HistoryScreen.RELATIONSHIPS, ScreenScenario.EMPTY)
    @Test fun relationships_failure() = capture(HistoryScreen.RELATIONSHIPS, ScreenScenario.FAILURE)
    @Test fun relationships_restricted() = capture(HistoryScreen.RELATIONSHIPS, ScreenScenario.RESTRICTED)
    @Test fun relationships_unknown() = capture(HistoryScreen.RELATIONSHIPS, ScreenScenario.UNKNOWN)
    @Config(sdk = [35], qualifiers = "w840dp-h1210dp-port-mdpi")
    @Test fun relationships_narrow() = capture(HistoryScreen.RELATIONSHIPS, ScreenScenario.NARROW)
    @Test fun person_populated() = capture(HistoryScreen.PERSON, ScreenScenario.POPULATED)
    @Test fun person_loading() = capture(HistoryScreen.PERSON, ScreenScenario.LOADING)
    @Test fun person_empty() = capture(HistoryScreen.PERSON, ScreenScenario.EMPTY)
    @Test fun person_failure() = capture(HistoryScreen.PERSON, ScreenScenario.FAILURE)
    @Test fun person_restricted() = capture(HistoryScreen.PERSON, ScreenScenario.RESTRICTED)
    @Test fun person_unknown() = capture(HistoryScreen.PERSON, ScreenScenario.UNKNOWN)
    @Config(sdk = [35], qualifiers = "w840dp-h1210dp-port-mdpi")
    @Test fun person_narrow() = capture(HistoryScreen.PERSON, ScreenScenario.NARROW)
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
    @Test fun after_pending() = capture(HistoryScreen.AFTER_INCOMING, ScreenScenario.POPULATED,
        HistoryFixtures.state.copy(requests = LoadState.Ready(listOf(HistoryFixtures.request.copy(status = AfterRequestStatus.PENDING)), 2)), "pending")
    @Test fun after_accepted() = capture(HistoryScreen.AFTER_INCOMING, ScreenScenario.POPULATED,
        HistoryFixtures.state.copy(requests = LoadState.Ready(listOf(HistoryFixtures.request.copy(status = AfterRequestStatus.ACCEPTED)), 2)), "accepted")
    @Test fun after_declined() = capture(HistoryScreen.AFTER_INCOMING, ScreenScenario.POPULATED,
        HistoryFixtures.state.copy(requests = LoadState.Ready(listOf(HistoryFixtures.request.copy(status = AfterRequestStatus.DECLINED)), 2)), "declined")
    @Test fun after_expired() = capture(HistoryScreen.AFTER_INCOMING, ScreenScenario.POPULATED,
        HistoryFixtures.state.copy(requests = LoadState.Ready(listOf(HistoryFixtures.request.copy(status = AfterRequestStatus.EXPIRED)), 2)), "expired")
    @Test fun after_withdrawn() = capture(HistoryScreen.AFTER_INCOMING, ScreenScenario.POPULATED,
        HistoryFixtures.state.copy(requests = LoadState.Ready(listOf(HistoryFixtures.request.copy(status = AfterRequestStatus.WITHDRAWN)), 2)), "withdrawn")
    @Test fun after_unavailable() = capture(HistoryScreen.AFTER_INCOMING, ScreenScenario.POPULATED,
        HistoryFixtures.state.copy(requests = LoadState.Ready(listOf(HistoryFixtures.request.copy(status = AfterRequestStatus.UNAVAILABLE)), 2)), "unavailable")
    @Test fun personClosed() = capture(HistoryScreen.PERSON, ScreenScenario.POPULATED,
        HistoryFixtures.state.copy(relationships = LoadState.Ready(listOf(HistoryFixtures.relationship.copy(status = AfterConnectionStatus.CLOSED, sharedContacts = null, ownSharedContacts = null)), 2)), "closed")
    @Test fun personCrossed() = capture(HistoryScreen.PERSON, ScreenScenario.POPULATED,
        HistoryFixtures.state.copy(relationships = LoadState.Ready(emptyList(), 1), requests = LoadState.Ready(emptyList(), 1)), "crossed")
    @Test fun requestSavedBoth() = capture(HistoryScreen.AFTER_REQUEST, ScreenScenario.POPULATED,
        screenState(HistoryScreen.AFTER_REQUEST, ScreenScenario.POPULATED).copy(profile = LoadState.Ready(HistoryFixtures.profile.copy(contacts = ContactIds("demo-only-invalid!", "demo-contact")), 1)), "saved_both")
    @Test fun requestSavedOne() = capture(HistoryScreen.AFTER_REQUEST, ScreenScenario.POPULATED,
        screenState(HistoryScreen.AFTER_REQUEST, ScreenScenario.POPULATED).copy(profile = LoadState.Ready(HistoryFixtures.profile.copy(contacts = ContactIds("demo-only-invalid!")), 1), shareContacts = ContactIds("demo-only-invalid!")), "saved_one")
    @Test fun requestInvalid() = capture(HistoryScreen.AFTER_REQUEST, ScreenScenario.POPULATED,
        screenState(HistoryScreen.AFTER_REQUEST, ScreenScenario.POPULATED).copy(shareContacts = ContactIds("!", "!")), "invalid")
    @Test fun profileContactsInvalid() = capture(HistoryScreen.PROFILE_EDIT, ScreenScenario.POPULATED,
        HistoryFixtures.state.copy(draft = HistoryFixtures.state.draft.copy(contacts = ContactIds("!", "!"))), "invalid_contacts")
    @Test fun relationshipsServer() = capture(HistoryScreen.RELATIONSHIPS, ScreenScenario.POPULATED, suffix = "server", showSourceLabels = true)
    @Test fun personServer() = capture(HistoryScreen.PERSON, ScreenScenario.POPULATED, suffix = "server", showSourceLabels = true)
    private fun confirm(screen: HistoryScreen, kind: ConfirmationKind, title: String, body: String) {
        val state = HistoryFixtures.state.copy(confirmation = HistoryConfirmation(kind, "target", title, body, "confirm"))
        compose.setContent { ChayeonTheme(state.driving.policy, animatorScaleOverride = 0f) { RenderHistoryScreen(screen, state, showSourceLabels = false) } }
        compose.onNode(isDialog()).captureRoboImage("src/test/screenshots/${screen.name.lowercase()}_confirm_${kind.name.lowercase()}.png")
    }
    @Test fun personConfirmDelete() = confirm(HistoryScreen.PERSON, ConfirmationKind.DELETE_ENCOUNTER,
        "이 기록을 삭제할까요?", "내 기록만 사라져요. 상대의 기록과 차단 상태는 바뀌지 않아요. 삭제한 기록은 되돌릴 수 없어요.")
    @Test fun personConfirmEnd() = confirm(HistoryScreen.PERSON, ConfirmationKind.END_RELATIONSHIP,
        "관계를 끝낼까요?", "차연에서 공유한 아이디가 더 이상 보이지 않아요. 이미 전달된 아이디는 되돌릴 수 없어요. 처음 스친 기록은 남아요.")
    @Test fun afterConfirmWithdraw() = confirm(HistoryScreen.AFTER_REQUEST, ConfirmationKind.WITHDRAW,
        "애프터 신청을 철회할까요?", "이 신청은 닫히고 다시 수락할 수 없어요. 스친 기록은 남아요.")

    private fun modelConfirmation(screen: HistoryScreen, kind: ConfirmationKind) {
        val deps = FakeHistoryDeps()
        val model: HistoryViewModel = if (screen == HistoryScreen.CLEANUP) CleanupViewModel(deps) else SafetyViewModel(deps, HistoryFixtures.peer.id)
        store.put("confirmation", model)
        compose.setContent {
            val state by model.uiState.collectAsState()
            ChayeonTheme(state.driving.policy, 0f) { RenderHistoryScreen(screen, state, false) }
        }
        compose.runOnIdle {
            when (model) {
                is CleanupViewModel -> model.ask(kind, if (kind == ConfirmationKind.LEAVE) HistoryFixtures.connection.id else "")
                is SafetyViewModel -> if (kind == ConfirmationKind.BLOCK) model.askBlock() else model.askUnblock(HistoryFixtures.block)
            }
        }
        compose.onNode(isDialog()).captureRoboImage("src/test/screenshots/${screen.name.lowercase()}_confirm_${kind.name.lowercase()}.png")
    }
    @Test fun cleanupConfirmDeleteAccount() = modelConfirmation(HistoryScreen.CLEANUP, ConfirmationKind.DELETE_ACCOUNT)
    @Test fun cleanupConfirmSignOut() = modelConfirmation(HistoryScreen.CLEANUP, ConfirmationKind.SIGN_OUT)
    @Test fun cleanupConfirmRecords() = modelConfirmation(HistoryScreen.CLEANUP, ConfirmationKind.DELETE_RECORDS)
    @Test fun cleanupConfirmDiscovery() = modelConfirmation(HistoryScreen.CLEANUP, ConfirmationKind.DISCOVERY_OFF)
    @Test fun cleanupConfirmLeave() = modelConfirmation(HistoryScreen.CLEANUP, ConfirmationKind.LEAVE)
    @Test fun safetyConfirmBlock() = modelConfirmation(HistoryScreen.BLOCK_REPORT, ConfirmationKind.BLOCK)
    @Test fun safetyConfirmUnblock() = modelConfirmation(HistoryScreen.BLOCKS, ConfirmationKind.UNBLOCK)

    @Test fun incomingHomeProtected() = capture(HistoryScreen.AFTER_INCOMING, ScreenScenario.POPULATED,
        screenState(HistoryScreen.AFTER_INCOMING, ScreenScenario.POPULATED).copy(homeSuppressed = true), "home_protected")

    @Test fun profileContactInputsAndInlineErrors() {
        val state = HistoryFixtures.state.copy(draft = HistoryFixtures.state.draft.copy(contacts = ContactIds("!", "!")))
        compose.setContent { ChayeonTheme(state.driving.policy, 0f) { RenderHistoryScreen(HistoryScreen.PROFILE_EDIT, state, false) } }
        compose.onNodeWithText("영문·숫자·밑줄·마침표로 1~30자 입력해 주세요").performScrollTo()
        compose.onNodeWithTag("screen").captureRoboImage("src/test/screenshots/profile_edit_contact_inputs.png")
    }

    @Test fun firstRunSoloOnly() = capture(HistoryScreen.FIRST_RUN, ScreenScenario.POPULATED,
        HistoryFixtures.state.copy(modes = setOf(SessionMode.SOLO)), "solo")
    @Test fun firstRunServerMode() = capture(HistoryScreen.FIRST_RUN, ScreenScenario.POPULATED,
        HistoryFixtures.state.copy(selectedMode = SessionMode.SERVER_DEMO), "server")
    @Test fun firstRunRoomFull() = capture(HistoryScreen.FIRST_RUN, ScreenScenario.POPULATED,
        HistoryFixtures.state.copy(selectedMode = SessionMode.SERVER_DEMO,
            session = SessionState.Failed(AppError.Restricted),
            commands = mapOf("enter" to CommandState(error = AppError.Restricted))), "room_full")
    @Test fun profileSetupSoloEmpty() = capture(HistoryScreen.PROFILE_SETUP, ScreenScenario.EMPTY,
        screenState(HistoryScreen.PROFILE_SETUP, ScreenScenario.EMPTY).copy(modes = setOf(SessionMode.SOLO)), "solo_empty")
    private fun consentReadyState(discoveryOn: Boolean) = HistoryFixtures.state.copy(
        settings = LoadState.Ready(HistoryFixtures.settings.copy(consents = ConsentScope.entries.map {
            ConsentRecord(it.name, 1, it, ConsentStatus.GRANTED, HistoryFixtures.policy.consentDocumentVersion, 1)
        }), 1), discovery = DiscoveryState(mode = if (discoveryOn) DiscoveryMode.ON else DiscoveryMode.OFF))
    @Test fun consentGrantedOff() = capture(HistoryScreen.CONSENT, ScreenScenario.POPULATED, consentReadyState(false), "granted_off")
    @Test fun consentGrantedOn() = capture(HistoryScreen.CONSENT, ScreenScenario.POPULATED, consentReadyState(true), "granted_on")
    @Test fun consentMicrophoneGranted() = capture(HistoryScreen.CONSENT, ScreenScenario.POPULATED,
        HistoryFixtures.state.copy(microphoneGranted = true), "microphone_granted")
    @Test fun consentSavingLocation() = capture(HistoryScreen.CONSENT, ScreenScenario.POPULATED,
        HistoryFixtures.state.copy(commands = mapOf("consent:LOCATION_USE" to CommandState(running = true))), "saving_location")
    @Test fun blockReportAnonymized() = capture(HistoryScreen.BLOCK_REPORT, ScreenScenario.POPULATED,
        HistoryFixtures.state.copy(safetyPeer = HistoryFixtures.peer.copy(nickname = "가상 프로필", kind = ProfileKind.VIRTUAL_PROFILE, signature = null, interests = emptySet())), "anonymized")

    @Test fun personMixedRevisionContactsHidden() = capture(HistoryScreen.PERSON, ScreenScenario.POPULATED,
        HistoryFixtures.state.copy(blocks = LoadState.Ready(emptyList(), 2)), "contacts_hidden")

}
