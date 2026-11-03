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
        compose.onNodeWithText("저장하고 다음").performClick()
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
        compose.onNodeWithText("이 상대 차단").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(deps.calls.isEmpty()) }
        compose.onNode(hasText("이 상대 차단") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
        compose.onNode(hasText("연결이 끊겨", substring = true) and hasAnyAncestor(isDialog())).assertExists()
        compose.runOnIdle { deps.error = null }
        compose.onNode(hasText("이 상대 차단") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
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
    @Test fun narrowSaveActionIsFixedOutsideScrollContent() {
        compose.setContent {
            ChayeonTheme(DrivingPolicy.ALLOWED, 0f) { RenderHistoryScreen(HistoryScreen.PROFILE_SETUP, HistoryFixtures.state) }
        }
        compose.onNodeWithText("저장하고 다음").assertIsDisplayed()
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
        compose.onNodeWithText("이 계정으로 시작").assertIsDisplayed()
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
        compose.onNodeWithText("저장하고 다음").performClick()
        compose.runOnIdle {
            deps.profileState.value = LoadState.Ready(HistoryFixtures.profile.copy(registration = RegistrationState.CONSENT_REQUIRED), 2)
        }
        compose.onNodeWithText("차량 데이터·위치·공개 범위").assertExists()
        compose.onNodeWithContentDescription("뒤로").performClick()
        compose.onNodeWithContentDescription("닉네임").assertExists()
        compose.onNodeWithText("저장하고 다음").assertIsDisplayed()
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

    @Test fun systemBackKeepsDraftWhileRestrictedAndUnknown() {
        val deps = FakeHistoryDeps()
        val model = ProfileSetupViewModel(deps).also { store.put("profile", it) }
        var backCount = 0
        lateinit var dispatcher: androidx.activity.OnBackPressedDispatcher
        compose.setContent {
            dispatcher = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            androidx.activity.compose.BackHandler { backCount++ }
            val state by model.uiState.collectAsState()
            ChayeonTheme(state.driving.policy, 0f) {
                ProfileSetupScreen(state, previewChrome(), false, model::updateDraft, model::save, {}, {})
            }
        }
        compose.onNodeWithContentDescription("닉네임").performTextReplacement("별빛산책")
        listOf(DrivingPolicy.RESTRICTED, DrivingPolicy.UNKNOWN).forEach { policy ->
            compose.runOnIdle { deps.driving.value = DrivingState(policy = policy) }
            compose.waitForIdle()
            compose.runOnIdle { dispatcher.onBackPressed() }
            compose.runOnIdle { assertEquals(0, backCount) }
        }
        compose.runOnIdle { deps.driving.value = DrivingState(policy = DrivingPolicy.ALLOWED) }
        compose.onNodeWithContentDescription("닉네임").assertTextContains("별빛산책")
        compose.runOnIdle { dispatcher.onBackPressed(); assertEquals(1, backCount) }
    }

    @Test fun onboardingActionsStayAtSamePositionAcrossStepsAndScrolling() {
        var screen by mutableStateOf(HistoryScreen.FIRST_RUN)
        compose.setContent { ChayeonTheme(DrivingPolicy.ALLOWED, 0f) { RenderHistoryScreen(screen, screenState(screen, ScreenScenario.POPULATED)) } }
        val bounds = compose.onNodeWithTag("onboarding-primary").fetchSemanticsNode().boundsInRoot
        listOf(HistoryScreen.PROFILE_SETUP, HistoryScreen.CONSENT).forEach { next ->
            compose.runOnIdle { screen = next }
            compose.onNodeWithText("이전").assertIsDisplayed()
            compose.onNodeWithTag("onboarding-primary").assertIsDisplayed()
            assertEquals(bounds, compose.onNodeWithTag("onboarding-primary").fetchSemanticsNode().boundsInRoot)
        }
        compose.onNodeWithText("발견 켜기").performScrollTo()
        assertEquals(bounds, compose.onNodeWithTag("onboarding-primary").fetchSemanticsNode().boundsInRoot)
    }

    @Test fun firstRunSubtitleDoesNotOverlapVisualAndPanelsShareBottom() {
        compose.setContent { ChayeonTheme(DrivingPolicy.ALLOWED, 0f) { RenderHistoryScreen(HistoryScreen.FIRST_RUN, HistoryFixtures.state) } }
        val subtitle = compose.onNodeWithTag("first-run-subtitle").fetchSemanticsNode().boundsInRoot
        val visual = compose.onNodeWithTag("first-run-visual").fetchSemanticsNode().boundsInRoot
        assertTrue(subtitle.bottom <= visual.top)
        val left = compose.onNodeWithTag("onboarding-left").fetchSemanticsNode().boundsInRoot
        val right = compose.onNodeWithTag("onboarding-right").fetchSemanticsNode().boundsInRoot
        assertEquals(left.bottom, right.bottom, 1f)
    }

    @Test fun onboardingPanelsFitAboveActionsWithMatchingBottoms() {
        var screen by mutableStateOf(HistoryScreen.FIRST_RUN)
        compose.setContent { ChayeonTheme(DrivingPolicy.ALLOWED, 0f) { RenderHistoryScreen(screen, screenState(screen, ScreenScenario.POPULATED)) } }
        listOf(HistoryScreen.FIRST_RUN, HistoryScreen.PROFILE_SETUP, HistoryScreen.CONSENT).forEach { next ->
            compose.runOnIdle { screen = next }
            val left = compose.onNodeWithTag("onboarding-left").getUnclippedBoundsInRoot()
            val right = compose.onNodeWithTag("onboarding-right").getUnclippedBoundsInRoot()
            val actions = compose.onNodeWithTag("onboarding-actions").getUnclippedBoundsInRoot()
            assertEquals("$screen panel bottoms", left.bottom.value, right.bottom.value, 1f)
            assertTrue("$screen left panel clips", left.bottom <= actions.top)
            assertTrue("$screen right panel clips", right.bottom <= actions.top)
        }
    }

    @Test fun discoveryIsOffAndExplainsDisabledStateBeforeConsent() {
        compose.setContent { ChayeonTheme(DrivingPolicy.ALLOWED, 0f) { RenderHistoryScreen(HistoryScreen.CONSENT, screenState(HistoryScreen.CONSENT, ScreenScenario.POPULATED)) } }
        compose.onNodeWithText("발견 켜기").performScrollTo().assertIsOff().assertIsNotEnabled()
        compose.onNodeWithText("위의 네 항목에 동의한 뒤 발견을 켤 수 있어요.").assertIsDisplayed()
    }

    @Test fun incomingSafetyDeclineAndAcceptAreVisibleWithoutScrolling() {
        compose.setContent { ChayeonTheme(DrivingPolicy.ALLOWED, 0f) { RenderHistoryScreen(HistoryScreen.AFTER_INCOMING, HistoryFixtures.state) } }
        compose.onNodeWithText("수락하면 새 대화가 열려요. 거절해도 이유는 전달되지 않아요.").assertIsDisplayed()
        listOf("차단·신고", "거절", "수락하고 이어가기").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
    }

    @Test fun encounterChemistryIsVisibleAndEmptySharedInterestsHaveNoHeading() {
        val detailed = screenState(HistoryScreen.ENCOUNTER_DETAIL, ScreenScenario.POPULATED)
        val state = detailed.copy(encounter = LoadState.Ready(detailed.encounter.valueOrNull()!!.copy(sharedInterests = emptySet()), 1))
        compose.setContent { ChayeonTheme(DrivingPolicy.ALLOWED, 0f) { RenderHistoryScreen(HistoryScreen.ENCOUNTER_DETAIL, state) } }
        compose.onNodeWithText("함께하면 잘 맞는 점").assertIsDisplayed()
        HistoryFixtures.detailedChemistry.lines.forEach { compose.onNodeWithText(it.text).assertIsDisplayed() }
        compose.onNodeWithText("겹친 관심사").assertDoesNotExist()
    }

    @Test fun connectedSafetyAndEndActionsAreVisibleAboveNavigation() {
        val state = screenState(HistoryScreen.AFTER_CONNECTED, ScreenScenario.POPULATED)
        compose.setContent { ChayeonTheme(DrivingPolicy.ALLOWED, 0f) { RenderHistoryScreen(HistoryScreen.AFTER_CONNECTED, state) } }
        val actionTop = compose.onNodeWithText("관계 끝내기").getUnclippedBoundsInRoot().top
        state.selectedRelationship!!.availablePresets.forEach { code ->
            val preset = compose.onNode(hasText(code.label) and hasClickAction())
            preset.assertIsDisplayed()
            assertTrue("${code.name} clips", preset.getUnclippedBoundsInRoot().bottom <= actionTop)
        }
        compose.onNodeWithText("관계 끝내기").assertIsDisplayed()
        compose.onNodeWithText("차단·신고").assertIsDisplayed()
        compose.onNodeWithText("보고 있는 인연").assertDoesNotExist()
        compose.onNodeWithText("대화 중인 인연").assertIsDisplayed()
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
        compose.onNodeWithText("수락하고 이어가기").performClick()
        compose.runOnIdle { assertEquals(RequestDecision.ACCEPT, (deps.calls.single() as RespondAfterRequest).decision) }
    }
}
