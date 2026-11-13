package dev.chayeon.feature.history

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import com.github.takahirom.roborazzi.captureRoboImage
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

    @Test fun latestWrappedReplyComposerAndSendFitWithoutPageScrolling() {
        val reply = "다시 이야기하게 되어 반가워요. 야경 보는 걸 좋아하세요? 함께 나누고 싶은 풍경 이야기가 많아요. " +
            "저는 해 질 무렵의 하늘과 조용한 산책길을 좋아해요. 다음에는 좋아하는 음악 이야기도 천천히 나눠 보고 싶어요."
        val older = (1..12).map { index -> AfterMessage("older-$index", 1, "after", "older-$index", MessageAuthor.PEER,
            AfterMessageContent.Text("지난 대화 $index · 함께 나누었던 이야기를 다시 읽어 봐요"), index.toLong(), DeliveryState.SENT) }
        val state = screenState(HistoryScreen.AFTER_CONNECTED, ScreenScenario.POPULATED).copy(
            messages = LoadState.Ready(older + AfterMessage("reply", 2, "after", "reply", MessageAuthor.PEER,
                AfterMessageContent.Text(reply), 20, DeliveryState.SENT), 2),
        )
        compose.setContent { ChayeonTheme(DrivingPolicy.ALLOWED, 0f) {
            val chrome = previewChrome()
            val appChrome = ScreenChrome(topBar = {
                Box(Modifier.height(ChayeonDimensions.Banner), contentAlignment = Alignment.Center) { chrome.topBar() }
            }, bottomBar = chrome.bottomBar)
            AfterConnectionsScreen(state, appChrome, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
        } }
        val message = compose.onNodeWithText(reply).assertIsDisplayed().getUnclippedBoundsInRoot()
        val viewport = compose.onNodeWithTag("after-message-list").getUnclippedBoundsInRoot()
        val composer = compose.onNodeWithContentDescription("메시지").assertIsDisplayed().getUnclippedBoundsInRoot()
        val send = compose.onNodeWithTag("after-send").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue("The peer reply must wrap onto multiple lines", (message.bottom - message.top).value >= ChayeonType.Body.lineHeight.value * 2)
        assertTrue(message.top >= viewport.top && message.bottom <= viewport.bottom)
        assertTrue("The whole latest reply must clear the older-message fade", message.top >= viewport.top + ChayeonDimensions.ScrollFade)
        assertTrue(viewport.bottom <= composer.top && composer.bottom <= send.top)
        assertTrue(send.bottom - send.top >= ChayeonSpacing.Touch)
        val scroll = compose.onNodeWithTag("after-message-list").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue("Older messages must scroll inside the conversation", scroll.maxValue() > 0f)
        assertEquals(scroll.maxValue(), scroll.value(), 1f)
        compose.onNodeWithText("지난 대화 1 · 함께 나누었던 이야기를 다시 읽어 봐요").assertIsNotDisplayed()
        compose.onRoot().captureRoboImage("src/test/screenshots/after_wrapped_reply.png")
        compose.onNodeWithTag("after-message-list").performScrollToNode(hasText("지난 대화 1 · 함께 나누었던 이야기를 다시 읽어 봐요"))
        compose.onNodeWithText("지난 대화 1 · 함께 나누었던 이야기를 다시 읽어 봐요").assertIsDisplayed()
        assertEquals(composer, compose.onNodeWithContentDescription("메시지").getUnclippedBoundsInRoot())
        assertEquals(send, compose.onNodeWithTag("after-send").getUnclippedBoundsInRoot())
    }

    @Test fun multipleRelationshipRowsAndPresetsRemainReachable() {
        val base = screenState(HistoryScreen.AFTER_CONNECTED, ScreenScenario.POPULATED)
        val selected = base.selectedRelationship!!
        val state = base.copy(relationships = LoadState.Ready(listOf(selected) + (1..6).map {
            selected.copy(id = "other-$it", peer = selected.peer.copy(id = "peer-$it", nickname = "산책길$it"))
        }, 2))
        var chosen: String? = null
        compose.setContent { ChayeonTheme(DrivingPolicy.ALLOWED, 0f) {
            AfterConnectionsScreen(state, previewChrome(), { chosen = it }, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
        } }
        compose.onNodeWithTag("relationship-${selected.id}").assertHasNoClickAction()
        compose.onNodeWithText("대화 중인 인연").assertIsDisplayed()
        val last = compose.onNodeWithTag("relationship-other-6").performScrollTo().assertIsDisplayed()
        val bounds = last.getUnclippedBoundsInRoot()
        assertTrue(bounds.bottom - bounds.top >= ChayeonSpacing.Touch)
        last.performClick()
        assertEquals("other-6", chosen)
        selected.availablePresets.forEach { code ->
            compose.onNode(hasText(code.label) and hasClickAction()).performScrollTo().assertIsDisplayed()
        }
        compose.onNode(hasText(PresetCode.ADMIRE_EARLY_BRAKE.label) and hasClickAction()).performScrollTo()
        compose.onRoot().captureRoboImage("src/test/screenshots/after_multiple_relationships.png")
        compose.onNodeWithText("관계 끝내기").assertIsDisplayed()
        compose.onNodeWithTag("after-send").assertIsDisplayed()
    }

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
        compose.onNodeWithText("이전").assertDoesNotExist()
        val left = compose.onNodeWithTag("onboarding-left").fetchSemanticsNode().boundsInRoot
        val right = compose.onNodeWithTag("onboarding-right").fetchSemanticsNode().boundsInRoot
        assertEquals(left.left, bounds.left, 1f)
        assertEquals(right.right, bounds.right, 1f)
        listOf(HistoryScreen.PROFILE_SETUP, HistoryScreen.CONSENT).forEach { next ->
            compose.runOnIdle { screen = next }
            compose.onNodeWithText("이전").assertDoesNotExist()
            compose.onNodeWithTag("onboarding-primary").assertIsDisplayed()
            assertEquals(bounds, compose.onNodeWithTag("onboarding-primary").fetchSemanticsNode().boundsInRoot)
        }
        compose.onNodeWithText("발견 켜기").performScrollTo()
        assertEquals(bounds, compose.onNodeWithTag("onboarding-primary").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithText("이전").assertDoesNotExist()
        compose.runOnIdle { screen = HistoryScreen.PROFILE_EDIT }
        compose.onNodeWithText("프로필 고치기").assertIsDisplayed()
        compose.onNodeWithText("변경 내용 저장").assertIsDisplayed()
        compose.onNodeWithText("이전").assertDoesNotExist()
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
