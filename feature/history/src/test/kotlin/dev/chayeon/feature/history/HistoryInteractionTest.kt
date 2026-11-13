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
    private fun ephemeralId() = "a" + java.util.UUID.randomUUID().toString().replace("-", "").take(19)
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

    @Test fun discoveryIsOffAndExplainsDisabledStateBeforeConsent() {
        compose.setContent { ChayeonTheme(DrivingPolicy.ALLOWED, 0f) { RenderHistoryScreen(HistoryScreen.CONSENT, screenState(HistoryScreen.CONSENT, ScreenScenario.POPULATED)) } }
        compose.onNodeWithText("발견 켜기").performScrollTo().assertIsOff().assertIsNotEnabled()
        compose.onNodeWithText("위의 네 항목에 동의한 뒤 발견을 켤 수 있어요.").assertIsDisplayed()
    }

    @Test fun incomingSafetyDeclineAndAcceptAreVisibleWithoutScrolling() {
        compose.setContent { ChayeonTheme(DrivingPolicy.ALLOWED, 0f) { RenderHistoryScreen(HistoryScreen.AFTER_INCOMING, screenState(HistoryScreen.AFTER_INCOMING, ScreenScenario.POPULATED)) } }
        compose.onNodeWithText("수락하면 내가 고른 아이디도 상대에게 보여요. 거절 이유는 전달되지 않아요.").assertIsDisplayed()
        listOf("차단·신고", "거절", "수락하고 이어가기").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
    }

    @Test fun incomingAcceptCallsRepository() {
        val deps = FakeHistoryDeps()
        val model = AfterRequestViewModel(deps, "encounter").also { store.put("request", it) }
        model.setContacts(ContactIds(kakaoTalkId = ephemeralId()))
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
    @Test fun privateContactsAndQrDisappearImmediatelyWhenDrivingIsRestrictedOrUnknown() {
        var policy by mutableStateOf(DrivingPolicy.ALLOWED)
        compose.setContent {
            val state = HistoryFixtures.state.copy(driving = DrivingState(policy = policy))
            ChayeonTheme(policy, 0f) { RenderHistoryScreen(HistoryScreen.PERSON, state) }
        }
        compose.onNodeWithText("@demo-contact").assertExists()
        for (next in listOf(DrivingPolicy.RESTRICTED, DrivingPolicy.UNKNOWN)) {
            compose.runOnIdle { policy = next }
            compose.onNodeWithText("@demo-contact").assertDoesNotExist()
            compose.onNodeWithText("demo-contact").assertDoesNotExist()
            compose.onNodeWithText("관계 끝내기").assertDoesNotExist()
        }
    }

    @Test fun savedContactCheckboxRequiresExplicitSelection() {
        val savedId = ephemeralId()
        var state by mutableStateOf(screenState(HistoryScreen.AFTER_REQUEST, ScreenScenario.POPULATED).copy(
            profile = LoadState.Ready(HistoryFixtures.profile.copy(contacts = ContactIds(kakaoTalkId = savedId)), 1)))
        compose.setContent { ChayeonTheme(DrivingPolicy.ALLOWED, 0f) {
            AfterRequestScreen(state, previewChrome(), {}, { _, _ -> }, {}, {}, {}, {}, {}, {}, {},
                onContacts = { state = state.copy(shareContacts = it) })
        } }
        compose.onNodeWithText("애프터 신청 보내기").assertIsNotEnabled()
        compose.onNodeWithText("카카오톡 · $savedId").performScrollTo().performClick()
        compose.onNodeWithText("애프터 신청 보내기").assertIsEnabled()
        compose.onNodeWithText("카카오톡 · $savedId").performClick()
        compose.onNodeWithText("애프터 신청 보내기").assertIsNotEnabled()
    }

    @Test fun blockedPeerNeverAppearsInUnifiedListOrDetail() {
        val state = HistoryFixtures.state.copy(blocks = LoadState.Ready(listOf(HistoryFixtures.block), 2))
        compose.setContent { ChayeonTheme(DrivingPolicy.ALLOWED, 0f) { RenderHistoryScreen(HistoryScreen.PERSON, state) } }
        compose.onNodeWithText(HistoryFixtures.peer.nickname).assertDoesNotExist()
        compose.onNodeWithText("@demo-contact").assertDoesNotExist()
        compose.onNodeWithText("이 인연은 더 이상 보이지 않아요").assertExists()
    }

    @Test fun savedBothIdsCanBeSelectedTogetherOrIndividually() {
        val kakao = ephemeralId()
        val instagram = ephemeralId()
        var state by mutableStateOf(screenState(HistoryScreen.AFTER_REQUEST, ScreenScenario.POPULATED).copy(
            profile = LoadState.Ready(HistoryFixtures.profile.copy(contacts = ContactIds(kakao, instagram)), 1)))
        var sent: ContactIds? = null
        compose.setContent { ChayeonTheme(DrivingPolicy.ALLOWED, 0f) {
            AfterRequestScreen(state, previewChrome(), { sent = state.shareContacts }, { _, _ -> }, {}, {}, {}, {}, {}, {}, {},
                onContacts = { state = state.copy(shareContacts = it) })
        } }
        compose.onNodeWithText("카카오톡 · $kakao").performScrollTo().performClick()
        compose.onNodeWithText("인스타그램 · @$instagram").performScrollTo().performClick()
        compose.onNodeWithText("애프터 신청 보내기").performClick()
        assertEquals(ContactIds(kakao, instagram), sent)
        compose.onNodeWithText("카카오톡 · $kakao").performScrollTo().performClick()
        compose.onNodeWithText("애프터 신청 보내기").performClick()
        assertEquals(ContactIds(instagramId = instagram), sent)
    }

    @Test fun incomingPersonCanRespondAfterItsEncounterIsDeleted() {
        val state = screenState(HistoryScreen.AFTER_INCOMING, ScreenScenario.POPULATED).copy(encounters = LoadState.Ready(emptyList(), 2))
        var answered: String? = null
        compose.setContent { ChayeonTheme(DrivingPolicy.ALLOWED, 0f) {
            PersonScreen(state, previewChrome(), { answered = it }, {}, {}, {}, {}, {}, {}, {})
        } }
        compose.onNodeWithText("답하기").performScrollTo().performClick()
        assertEquals(HistoryFixtures.request.encounterId, answered)
    }

    @Test fun homeProtectionDisablesAcceptAndExplainsWhyWithoutBlockingDecline() {
        val state = screenState(HistoryScreen.AFTER_INCOMING, ScreenScenario.POPULATED).copy(homeSuppressed = true,
            shareContacts = ContactIds(kakaoTalkId = ephemeralId()))
        compose.setContent { ChayeonTheme(DrivingPolicy.ALLOWED, 0f) { RenderHistoryScreen(HistoryScreen.AFTER_INCOMING, state) } }
        compose.onNodeWithText("수락하고 이어가기").assertIsNotEnabled()
        compose.onNodeWithText("거절").assertIsEnabled()
        compose.onNodeWithText("집 근처에서 쉬는 동안에는 수락할 수 없어요").assertIsDisplayed()
    }

    @Test fun kakaoContactUsesLargeReadableText() {
        val id = ephemeralId()
        val relationship = HistoryFixtures.relationship.copy(sharedContacts = ContactIds(kakaoTalkId = id))
        val state = HistoryFixtures.state.copy(relationships = LoadState.Ready(listOf(relationship), 1))
        compose.setContent { ChayeonTheme(DrivingPolicy.ALLOWED, 0f) { RenderHistoryScreen(HistoryScreen.PERSON, state) } }
        compose.onNodeWithText(id).assertIsDisplayed()
        val bounds = compose.onNodeWithText(id).getUnclippedBoundsInRoot()
        assertTrue(bounds.bottom - bounds.top >= ChayeonSpacing.Touch / 2)
    }

    @Test fun mixedRevisionContactSnapshotsStayHiddenUntilEveryProjectionConverges() {
        var state by mutableStateOf(HistoryFixtures.state)
        compose.setContent { ChayeonTheme(DrivingPolicy.ALLOWED, 0f) { RenderHistoryScreen(HistoryScreen.PERSON, state) } }
        compose.onNodeWithText("@demo-contact").assertExists()
        compose.runOnIdle { state = state.copy(blocks = LoadState.Ready(emptyList(), 2)) }
        compose.onNodeWithText("@demo-contact").assertDoesNotExist()
        compose.onNodeWithText("@demo-own").assertDoesNotExist()
        compose.runOnIdle { state = state.copy(blocks = LoadState.Failed(AppError.Network, 2)) }
        compose.onNodeWithText("@demo-contact").assertDoesNotExist()
        compose.onNodeWithText("@demo-own").assertDoesNotExist()
        compose.runOnIdle { state = HistoryFixtures.state.copy(
            encounters = LoadState.Ready(listOf(HistoryFixtures.encounter), 2),
            requests = LoadState.Ready(listOf(HistoryFixtures.request.copy(status = AfterRequestStatus.ACCEPTED)), 2),
            relationships = LoadState.Ready(listOf(HistoryFixtures.relationship), 2), blocks = LoadState.Ready(emptyList(), 2)) }
        compose.onNodeWithText("@demo-contact").assertExists()
    }

    @Test fun wholeRelationshipRowOpensPersonWhileAnswerRemainsDirect() {
        val state = screenState(HistoryScreen.RELATIONSHIPS, ScreenScenario.POPULATED)
        var person: String? = null
        var encounter: String? = null
        compose.setContent { ChayeonTheme(DrivingPolicy.ALLOWED, 0f) {
            RelationshipsScreen(state, previewChrome(), { person = it }, { encounter = it }, {})
        } }
        compose.onNodeWithText("별보는밤").performClick()
        assertEquals("incoming", person)
        compose.onNodeWithText("답하기").performClick()
        assertEquals("incoming-encounter", encounter)
    }

}
