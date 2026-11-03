package dev.chayeon.feature.history

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import dev.chayeon.core.model.*
import dev.chayeon.core.ui.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w1682dp-h1210dp-land-mdpi")
class HistoryInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val store = ViewModelStore()
    @After fun close() { store.clear() }

    @Test fun profileSaveFailureRetainsTextAndRetryUsesOriginalCommand() {
        val deps = FakeHistoryDeps().apply { error = AppError.Network }
        val model = ProfileSetupViewModel(deps).also { store.put("profile", it) }
        compose.setContent {
            val state by model.uiState.collectAsState()
            ChayeonTheme(state.driving.policy, 0f) {
                ProfileSetupScreen(state, previewChrome(), false, model::updateDraft, model::save, { model.retry("save") }, {})
            }
        }
        compose.onNodeWithContentDescription("닉네임").performTextReplacement("달빛산책")
        compose.onNodeWithText("저장하고 다음").performScrollTo().performClick()
        compose.onNodeWithText("프로필을 저장하지 못했어요").performScrollTo().assertExists()
        compose.runOnIdle { deps.error = null }
        compose.onNodeWithText("다시 저장").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(2, deps.calls.size)
            assertEquals(deps.calls[0], deps.calls[1])
            assertEquals("달빛산책", model.uiState.value.draft.nickname)
        }
    }

    @Test fun serverOptionIsHiddenWhenUnavailable() {
        val state = HistoryFixtures.state.copy(modes = setOf(SessionMode.SOLO))
        compose.setContent { ChayeonTheme(DrivingPolicy.ALLOWED, 0f) { FirstRunScreen(state, previewChrome(), {}, {}, {}, {}, {}) } }
        compose.onNodeWithText("두 대로 연결(서버)").assertDoesNotExist()
        compose.onNodeWithText("혼자 둘러보기(가상 상대)").assertExists()
    }

    @Test fun consentChoiceSendsExplicitCommandAndDoesNotGrantOtherScopes() {
        val deps = FakeHistoryDeps()
        val model = ConsentViewModel(deps).also { store.put("consent", it) }
        compose.setContent {
            val state by model.uiState.collectAsState()
            ChayeonTheme(state.driving.policy, 0f) {
                ConsentScreen(state, previewChrome(), model::record, model::setDiscovery, {}, {}, model::retry, {})
            }
        }
        compose.onNodeWithText("차량 데이터 사용에 동의해요").performScrollTo().performClick()
        compose.runOnIdle {
            val command = deps.calls.single() as RecordConsentRequest
            assertEquals(ConsentScope.VEHICLE_DATA, command.scope)
            assertEquals(ConsentAction.GRANT, command.action)
        }
    }

    @Test fun blockNeedsConfirmationAndFailureCanBeRetriedInSheet() {
        val deps = FakeHistoryDeps().apply { error = AppError.Network }
        val model = SafetyViewModel(deps, "peer-profile").also { store.put("safety", it) }
        compose.setContent {
            val state by model.uiState.collectAsState()
            ChayeonTheme(state.driving.policy, 0f) {
                SafetyScreen(state, previewChrome(), "peer-profile", model::selectReason, model::report, model::askBlock,
                    {}, model::retry, model::confirmAction, model::dismissConfirmation, {})
            }
        }
        compose.onNodeWithText("이 사람 차단").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(deps.calls.isEmpty()) }
        compose.onNodeWithText("확인하고 진행").performClick()
        compose.onNode(hasText("연결이 끊겨", substring = true) and hasAnyAncestor(isDialog())).assertExists()
        compose.runOnIdle { deps.error = null }
        compose.onNodeWithText("확인하고 진행").performClick()
        compose.runOnIdle { assertEquals(deps.calls[0], deps.calls[1]) }
    }

    @Test fun drivingInterruptionHidesEditingAndRestoresDraft() {
        val deps = FakeHistoryDeps()
        val model = ProfileSetupViewModel(deps).also { store.put("profile", it) }
        compose.setContent {
            val state by model.uiState.collectAsState()
            ChayeonTheme(state.driving.policy, 0f) {
                ProfileSetupScreen(state, previewChrome(policy = state.driving.policy), false, model::updateDraft, model::save, { model.retry("save") }, {})
            }
        }
        compose.onNodeWithContentDescription("닉네임").performTextReplacement("별빛산책")
        compose.runOnIdle { deps.driving.value = DrivingState(policy = DrivingPolicy.RESTRICTED) }
        compose.onNodeWithContentDescription("닉네임").assertDoesNotExist()
        compose.onNodeWithText("주차하면 볼 수 있어요").assertExists()
        compose.runOnIdle { deps.driving.value = DrivingState(policy = DrivingPolicy.ALLOWED) }
        compose.onNodeWithContentDescription("닉네임").assertTextContains("별빛산책")
    }

    @Config(sdk = [35], qualifiers = "w840dp-h1210dp-port-mdpi")
    @Test fun narrowContentScrollsToSaveAction() {
        compose.setContent {
            ChayeonTheme(DrivingPolicy.ALLOWED, 0f) { RenderHistoryScreen(HistoryScreen.PROFILE_SETUP, HistoryFixtures.state) }
        }
        compose.onNodeWithText("저장하고 다음").performScrollTo().assertIsDisplayed()
    }
    @Test fun restartResumesConsentAndBackDoesNotLoop() {
        val deps = FakeHistoryDeps().apply {
            profileState.value = LoadState.Ready(HistoryFixtures.profile.copy(registration = RegistrationState.CONSENT_REQUIRED), 2)
        }
        compose.setContent {
            ChayeonTheme(DrivingPolicy.ALLOWED, 0f) {
                val nav = rememberNavController()
                NavHost(nav, HistoryDestination.FIRST_RUN) { historyGraph(deps, previewChrome(), nav) {} }
            }
        }
        compose.onNodeWithText("차량 데이터·위치·공개 범위").assertExists()
        compose.onNodeWithContentDescription("뒤로").performClick()
        compose.onNodeWithText("이 계정으로 시작").performScrollTo().assertIsDisplayed()
    }

    @Test fun consentBackReturnsToSavedProfileWithoutNavigatingAgain() {
        val deps = FakeHistoryDeps().apply {
            profileState.value = LoadState.Ready(HistoryFixtures.profile.copy(registration = RegistrationState.PROFILE_REQUIRED), 1)
        }
        compose.setContent {
            ChayeonTheme(DrivingPolicy.ALLOWED, 0f) {
                val nav = rememberNavController()
                NavHost(nav, HistoryDestination.PROFILE_SETUP) { historyGraph(deps, previewChrome(), nav) {} }
            }
        }
        compose.onNodeWithText("저장하고 다음").performScrollTo().performClick()
        compose.runOnIdle {
            deps.profileState.value = LoadState.Ready(HistoryFixtures.profile.copy(registration = RegistrationState.CONSENT_REQUIRED), 2)
        }
        compose.onNodeWithText("차량 데이터·위치·공개 범위").assertExists()
        compose.onNodeWithContentDescription("뒤로").performClick()
        compose.onNodeWithContentDescription("닉네임").assertExists()
        compose.onNodeWithText("저장하고 다음").performScrollTo().assertIsDisplayed()
    }

    @Test fun messageMainActionAndFailedDeliveryRetry() {
        val deps = FakeHistoryDeps().apply { error = AppError.Network }
        val model = AfterConnectionsViewModel(deps).also { store.put("after", it) }
        compose.setContent {
            val state by model.uiState.collectAsState()
            ChayeonTheme(state.driving.policy, 0f) {
                AfterConnectionsScreen(state, previewChrome(), model::select, {}, model::setText, model::sendText,
                    model::sendPreset, model::retryMessage, model::retry, model::askEnd, {}, model::endRelationship, model::dismissConfirmation)
            }
        }
        compose.onNodeWithContentDescription("메시지").performScrollTo().performTextInput("반가워요")
        compose.onNodeWithText("메시지 보내기").performScrollTo().performClick()
        compose.onNodeWithText("이 메시지 다시 보내기").performScrollTo().assertExists()
        compose.runOnIdle { deps.error = null }
        compose.onNodeWithText("이 메시지 다시 보내기").performClick()
        compose.runOnIdle { assertEquals(deps.calls[0], deps.calls[1]) }
    }

    @Test fun incomingAcceptCallsRepository() {
        val deps = FakeHistoryDeps()
        val model = AfterRequestViewModel(deps, "encounter").also { store.put("request", it) }
        compose.setContent {
            val state by model.uiState.collectAsState()
            ChayeonTheme(state.driving.policy, 0f) {
                AfterRequestScreen(state, previewChrome(), model::request, model::respond, model::askWithdraw,
                    {}, {}, model::retry, model::withdraw, model::dismissConfirmation, {})
            }
        }
        compose.onNodeWithText("수락하고 이어가기").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(RequestDecision.ACCEPT, (deps.calls.single() as RespondAfterRequest).decision) }
    }
}
