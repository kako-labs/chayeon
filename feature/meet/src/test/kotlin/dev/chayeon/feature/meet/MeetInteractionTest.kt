package dev.chayeon.feature.meet

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
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
class MeetInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val store = ViewModelStore()
    private fun <T : ViewModel> keep(model: T): T = model.also { store.put(model.javaClass.name, model) }

    @After fun clearModels() { compose.runOnIdle { store.clear() } }

    private fun showHome(model: HomeViewModel) {
        compose.setContent {
            val state by model.uiState.collectAsState()
            ChayeonTheme(state.driving.policy, 0f) {
                HomeScreen(state, previewChrome(policy = state.driving.policy), model::setDiscovery, model::refresh, {}, {}, {}, {}, {})
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) { model.uiState.value.profileLoaded }
    }

    private fun showCandidate(model: CandidateViewModel) {
        compose.setContent {
            val state by model.uiState.collectAsState()
            ChayeonTheme(state.driving.policy, 0f) {
                CandidateScreen(state, previewChrome(policy = state.driving.policy), model::requestConnection, {}, {})
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) { model.uiState.value.candidate != null }
    }

    private fun showRequest(model: RequestViewModel) {
        compose.setContent {
            val state by model.uiState.collectAsState()
            ChayeonTheme(state.driving.policy, 0f) {
                RequestScreen(state, previewChrome(policy = state.driving.policy), model::accept, model::decline, model::cancel, {}, {})
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) { model.uiState.value.request != null }
    }

    private fun showConnected(model: ConnectedViewModel) {
        compose.setContent {
            val state by model.uiState.collectAsState()
            ChayeonTheme(state.driving.policy, 0f) {
                ConnectedScreen(state, previewChrome(policy = state.driving.policy), model::sendGreeting, model::retryGreeting, model::leave, {})
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) { model.uiState.value.connection != null }
    }

    @Test fun unpublishedInvitationPrecedesCandidatesAndDisappearsWhenPublished() {
        val state = mutableStateOf(HomeUiState(MeetFixtures.parked, MeetFixtures.discovery,
            MeetFixtures.own.copy(signature = null), profileLoaded = true))
        var opened = false
        compose.setContent {
            ChayeonTheme(DrivingPolicy.ALLOWED, 0f) {
                HomeScreen(state.value, previewChrome(), {}, {}, {}, {}, {}, { opened = true }, {})
            }
        }
        val invitation = compose.onNodeWithText("내 시그니처 만들기").assertIsDisplayed().getUnclippedBoundsInRoot()
        val candidates = compose.onNodeWithText("지금 연결할 수 있는 사람").getUnclippedBoundsInRoot()
        assertTrue(invitation.bottom <= candidates.top)
        compose.onNodeWithText("시그니처 만들러 가기").performClick()
        assertTrue(opened)
        compose.runOnIdle { state.value = state.value.copy(profile = MeetFixtures.own) }
        compose.onNodeWithText("내 시그니처 만들기").assertDoesNotExist()
        compose.onNodeWithText("프로필 보기").assertExists()
    }

    @Test fun everyPublicTraitAndPhraseIsVisibleWithoutScrolling() {
        val state = CandidateUiState(MeetFixtures.parked, MeetFixtures.completeCandidate, MeetFixtures.own, canRequest = true)
        compose.setContent {
            ChayeonTheme(DrivingPolicy.ALLOWED, 0f) { CandidateScreen(state, previewChrome(), {}, {}, {}) }
        }
        TraitId.entries.forEach {
            compose.onNodeWithText(it.presentation.name).assertIsDisplayed()
            compose.onNodeWithText(it.presentation.publicPhrase).assertIsDisplayed()
        }
        MeetFixtures.completeCandidate.chemistry.lines.forEach { compose.onNodeWithText(it.text).assertIsDisplayed() }
        compose.onNodeWithText("좋아하는 것", substring = true).assertIsDisplayed()
        compose.onNodeWithText("연결 요청 보내기").assertIsDisplayed()
    }

    @Test fun homeToggleUpdatesDiscoveryAndStopsShowingCandidates() {
        val deps = FakeMeetDeps()
        deps.setDiscoveryResult = { payload ->
            val next = deps.discovery.value.copy(mode = payload.mode)
            deps.discovery.value = next
            AppResult.Success(next)
        }
        showHome(keep(HomeViewModel(deps)))
        compose.onNodeWithText("발견 켜짐").performClick()
        compose.waitUntil(timeoutMillis = 5_000) { deps.discoveryCommands.size == 1 }
        compose.onNode(hasText("발견 꺼짐") and hasClickAction()).assertExists()
        compose.onNodeWithText("내 카드를 직접 공개해요").assertExists()
        compose.onNodeWithText("프로필 보기").assertDoesNotExist()
        assertEquals(DiscoveryMode.OFF, deps.discoveryCommands.single().mode)
        compose.onNode(hasText("발견 꺼짐") and hasClickAction()).performClick()
        compose.waitUntil(timeoutMillis = 5_000) { deps.discoveryCommands.size == 2 }
        assertEquals(DiscoveryMode.ON, deps.discoveryCommands.last().mode)
    }

    @Test fun homeRefreshNetworkFailureOffersWorkingRetryAndRemovesOldCandidates() {
        val deps = FakeMeetDeps()
        deps.refreshResult = {
            deps.discovery.value = MeetFixtures.discovery.copy(status = DiscoveryStatus.NETWORK_FAILURE)
            AppResult.Failure(AppError.Network)
        }
        val model = keep(HomeViewModel(deps))
        showHome(model)
        compose.onNodeWithText("새로 보기").performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(AppError.Network, model.uiState.value.refresh.error) }
        compose.onNodeWithText("서버에 연결하지 못했어요").assertIsDisplayed()
        compose.onNodeWithText("프로필 보기").assertDoesNotExist()
        deps.refreshResult = {
            deps.discovery.value = MeetFixtures.discovery
            AppResult.Success(MeetFixtures.discovery)
        }
        compose.onNodeWithText("다시 시도").performClick()
        compose.waitUntil(timeoutMillis = 5_000) { deps.refreshCommands.size == 2 && model.uiState.value.refresh.error == null }
        compose.onNodeWithText("프로필 보기").assertExists()
        assertEquals(deps.refreshCommands.first(), deps.refreshCommands.last())
    }

    @Test fun candidateRequestFailureRetriesSamePayloadFromActualButton() {
        val deps = FakeMeetDeps()
        deps.requestResult = { AppResult.Failure(AppError.Network) }
        val model = keep(CandidateViewModel(deps, MeetFixtures.candidate.id))
        showCandidate(model)
        compose.onNodeWithText("연결 요청 보내기").performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(AppError.Network, model.uiState.value.request.error) }
        compose.onNodeWithText(AppError.Network.userMessage).assertIsDisplayed()
        deps.requestResult = { AppResult.Success(MeetFixtures.request.copy(direction = RequestDirection.OUTGOING)) }
        compose.onNodeWithText("연결 요청 보내기").performClick()
        compose.waitUntil(timeoutMillis = 5_000) { deps.requestCommands.size == 2 && model.uiState.value.request.error == null }
        assertEquals(2, deps.requestCommands.size)
        assertEquals(MeetFixtures.candidate.id, deps.requestCommands.first().candidateId)
        assertEquals(deps.requestCommands.first(), deps.requestCommands.last())
    }

    @Test fun incomingAcceptRetriesSameDecisionAfterFailure() {
        val deps = FakeMeetDeps()
        deps.requests.value = LoadState.Ready(listOf(MeetFixtures.request), 2)
        deps.respondResult = { AppResult.Failure(AppError.Network) }
        val model = keep(RequestViewModel(deps))
        showRequest(model)
        compose.onNodeWithText("수락하고 연결").performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(AppError.Network, model.uiState.value.accept.error) }
        compose.onNodeWithText(AppError.Network.userMessage).assertIsDisplayed()
        deps.respondResult = { AppResult.Success(MeetFixtures.request.copy(status = ConnectionRequestStatus.ACCEPTED)) }
        compose.onNodeWithText("수락하고 연결").performClick()
        compose.waitUntil(timeoutMillis = 5_000) { deps.respondCommands.size == 2 }
        assertEquals(RequestDecision.ACCEPT, deps.respondCommands.first().decision)
        assertEquals(MeetFixtures.request.id, deps.respondCommands.first().requestId)
        assertEquals(deps.respondCommands.first(), deps.respondCommands.last())
    }

    @Test fun incomingDeclineUsesDeclineDecision() {
        val deps = FakeMeetDeps()
        deps.requests.value = LoadState.Ready(listOf(MeetFixtures.request), 2)
        deps.respondResult = { payload ->
            val declined = MeetFixtures.request.copy(status = ConnectionRequestStatus.DECLINED)
            deps.requests.value = LoadState.Ready(listOf(declined), 3)
            assertEquals(RequestDecision.DECLINE, payload.decision)
            AppResult.Success(declined)
        }
        showRequest(keep(RequestViewModel(deps)))
        compose.onNodeWithText("거절").performClick()
        compose.waitUntil(timeoutMillis = 5_000) { deps.respondCommands.size == 1 }
        compose.onNodeWithText("수락하고 연결").assertDoesNotExist()
        compose.onNodeWithText("이번에는 연결되지 않았어요").assertExists()
        assertEquals(MeetFixtures.request.id, deps.respondCommands.single().requestId)
    }

    @Test fun outgoingCancelReportsOwnCancellationWithoutBlamingPeer() {
        val deps = FakeMeetDeps()
        val request = MeetFixtures.request.copy(direction = RequestDirection.OUTGOING)
        deps.requests.value = LoadState.Ready(listOf(request), 2)
        deps.cancelResult = {
            val cancelled = request.copy(status = ConnectionRequestStatus.CANCELLED)
            deps.requests.value = LoadState.Ready(listOf(cancelled), 3)
            AppResult.Success(cancelled)
        }
        showRequest(keep(RequestViewModel(deps)))
        compose.onNodeWithText("요청 취소").performClick()
        compose.waitUntil(timeoutMillis = 5_000) { deps.cancelCommands.size == 1 }
        compose.onNodeWithText("요청을 취소했어요").assertExists()
        compose.onNodeWithText("상대가 요청을 취소했어요").assertDoesNotExist()
        compose.onNodeWithText("수락하고 연결").assertDoesNotExist()
        assertEquals(request.id, deps.cancelCommands.single().requestId)
    }

    @Test fun zeroCountdownDoesNotExpirePendingRequestOrDisableAcceptance() {
        var accepts = 0
        val state = RequestUiState(driving = MeetFixtures.parked, request = MeetFixtures.request, ownProfile = MeetFixtures.own,
            loading = false, remainingMillis = 0)
        compose.setContent {
            ChayeonTheme(DrivingPolicy.ALLOWED, 0f) { RequestScreen(state, previewChrome(), { accepts++ }, {}, {}, {}, {}) }
        }
        compose.onNodeWithContentDescription("응답 결과를 확인하고 있어요").assertExists()
        compose.onNodeWithText("수락하고 연결").assertIsEnabled().performClick()
        compose.onNodeWithText("응답 시간이 지나 요청이 끝났어요").assertDoesNotExist()
        assertEquals(1, accepts)
    }

    @Test fun greetingMainActionAndFailedRetryPreserveClientMessageId() {
        val deps = FakeMeetDeps()
        deps.connection.value = LoadState.Ready(MeetFixtures.connection, 3)
        deps.greetingResult = { AppResult.Failure(AppError.Network) }
        val model = keep(ConnectedViewModel(deps))
        showConnected(model)
        compose.onNode(hasText(PresetCode.HELLO.label) and hasClickAction()).performScrollTo().performClick()
        compose.waitUntil(timeoutMillis = 5_000) { model.uiState.value.greetings.singleOrNull()?.delivery == DeliveryState.FAILED }
        compose.onNodeWithText("보내지 못했어요").performScrollTo().assertIsDisplayed()
        val first = deps.greetingCommands.single()
        deps.greetingResult = { payload ->
            AppResult.Success(ConnectionGreeting("ack", 4, payload.connectionId, payload.clientMessageId,
                MessageAuthor.SELF, payload.code, 120_000, DeliveryState.SENT))
        }
        compose.onNodeWithText("다시 보내기").performScrollTo().performClick()
        compose.waitUntil(timeoutMillis = 5_000) { model.uiState.value.greetings.singleOrNull()?.delivery == DeliveryState.SENT }
        compose.onNodeWithText("보냈어요").performScrollTo().assertIsDisplayed()
        assertEquals(2, deps.greetingCommands.size)
        assertEquals(first, deps.greetingCommands.last())
        assertEquals(PresetCode.HELLO, first.code)
    }

    @Test fun checkingDisablesPresetAndFailedGreetingRetry() {
        val retryIds = mutableListOf<String>()
        val sent = mutableListOf<PresetCode>()
        val state = ConnectedUiState(driving = MeetFixtures.parked,
            connection = MeetFixtures.connection.copy(status = ConnectionStatus.CHECKING), ownProfile = MeetFixtures.own,
            loading = false, greetingsLoading = false,
            greetings = listOf(GreetingUi("failed-id", PresetCode.HELLO, MessageAuthor.SELF, DeliveryState.FAILED)))
        compose.setContent {
            ChayeonTheme(DrivingPolicy.ALLOWED, 0f) { ConnectedScreen(state, previewChrome(), { sent += it }, { retryIds += it }, {}, {}) }
        }
        compose.onNodeWithText("연결 상태를 확인하고 있어요").assertExists()
        compose.onNode(hasText(PresetCode.HELLO.label) and hasClickAction()).performScrollTo().assertIsNotEnabled().performClick()
        compose.onNodeWithText("다시 보내기").performScrollTo().assertIsNotEnabled().performClick()
        assertTrue(sent.isEmpty())
        assertTrue(retryIds.isEmpty())
    }

    @Test fun policyTransitionHidesGreetingContentAndLeavesOnlyExitAction() {
        val deps = FakeMeetDeps()
        deps.connection.value = LoadState.Ready(MeetFixtures.connection, 3)
        deps.messages.value = LoadState.Ready(listOf(ConnectionGreeting("peer-message", 3, "connection", "peer-client",
            MessageAuthor.PEER, PresetCode.NICE_TO_MEET_YOU, 120_000, DeliveryState.SENT)), 3)
        val model = keep(ConnectedViewModel(deps))
        showConnected(model)
        compose.onNodeWithText("주고받은 인사").assertExists()
        for (policy in listOf(DrivingPolicy.RESTRICTED, DrivingPolicy.UNKNOWN)) {
            compose.runOnIdle { deps.drivingState.value = MeetFixtures.parked.copy(policy = policy, gear = Gear.DRIVE) }
            compose.waitUntil(timeoutMillis = 5_000) { model.uiState.value.driving.policy == policy }
            compose.onAllNodesWithText(PresetCode.NICE_TO_MEET_YOU.label).assertCountEquals(0)
            compose.onAllNodesWithText(PresetCode.HELLO.label).assertCountEquals(0)
            compose.onNodeWithText("주고받은 인사").assertDoesNotExist()
            compose.onNodeWithText("차단·신고").assertDoesNotExist()
            compose.onNodeWithText("나가기").assertIsEnabled()
        }
        compose.onNodeWithText("나가기").performClick()
        compose.waitUntil(timeoutMillis = 5_000) { deps.leaveCommands.size == 1 }
        assertEquals(MeetFixtures.connection.id, deps.leaveCommands.single().connectionId)
        assertTrue(deps.greetingCommands.isEmpty())
    }

    @Test fun restrictedFailedObservationKeepsExitWithoutStalePeerContent() {
        val deps = FakeMeetDeps()
        deps.connection.value = LoadState.Ready(MeetFixtures.connection, 3)
        val model = keep(ConnectedViewModel(deps))
        showConnected(model)
        compose.runOnIdle {
            deps.drivingState.value = MeetFixtures.parked.copy(policy = DrivingPolicy.RESTRICTED, gear = Gear.DRIVE)
            deps.connection.value = LoadState.Failed(AppError.Network, 4)
        }
        compose.waitUntil(timeoutMillis = 5_000) { model.uiState.value.connection == null && model.uiState.value.canLeave }
        compose.onNodeWithText(MeetFixtures.peer.nickname).assertDoesNotExist()
        compose.onNodeWithText("나가기").assertIsEnabled().performClick()
        compose.waitUntil(timeoutMillis = 5_000) { deps.leaveCommands.size == 1 }
        assertEquals(MeetFixtures.connection.id, deps.leaveCommands.single().connectionId)
    }

    @Test fun endedRecordActionPassesActualEncounterId() {
        val opened = mutableListOf<String>()
        var homes = 0
        val state = EndedUiState(driving = MeetFixtures.parked, connection = MeetFixtures.ended, ownProfile = MeetFixtures.own, loading = false)
        compose.setContent {
            ChayeonTheme(DrivingPolicy.ALLOWED, 0f) { EndedScreen(state, previewChrome(), { homes++ }, { opened += it }) }
        }
        compose.onNodeWithText("기록 보기").performClick()
        assertEquals(listOf(MeetFixtures.ended.encounterId), opened)
        compose.onNodeWithText("오늘의 인연으로").performClick()
        assertEquals(1, homes)
    }

    @Test @Config(sdk = [35], qualifiers = "w840dp-h1210dp-port-mdpi")
    fun narrowCandidateScrollReachesEveryPublicTraitAndRequestAction() {
        var requests = 0
        val signature = MeetFixtures.signature.copy(traits = TraitId.entries.map { PublicTrait(it, TraitLevel.SIGNATURE) })
        val state = CandidateUiState(driving = MeetFixtures.parked,
            candidate = MeetFixtures.candidate.copy(profile = MeetFixtures.peer.copy(signature = signature)),
            ownProfile = MeetFixtures.own, unavailableState = null, canRequest = true)
        compose.setContent {
            ChayeonTheme(DrivingPolicy.ALLOWED, 0f) { CandidateScreen(state, previewChrome(), { requests++ }, {}, {}) }
        }
        TraitId.entries.forEach { trait -> compose.onNodeWithText(trait.presentation.publicPhrase).performScrollTo().assertIsDisplayed() }
        compose.onNodeWithText("연결 요청 보내기").assertIsDisplayed().performClick()
        assertEquals(1, requests)
    }

    @Test @Config(sdk = [35], qualifiers = "w840dp-h1210dp-port-mdpi")
    fun narrowConnectedScrollReachesPresetAndFailedRetry() {
        val codes = mutableListOf<PresetCode>()
        val retries = mutableListOf<String>()
        val state = ConnectedUiState(driving = MeetFixtures.parked, connection = MeetFixtures.connection, ownProfile = MeetFixtures.own,
            loading = false, greetingsLoading = false,
            greetings = listOf(GreetingUi("narrow-failed", PresetCode.HELLO, MessageAuthor.SELF, DeliveryState.FAILED)))
        compose.setContent {
            ChayeonTheme(DrivingPolicy.ALLOWED, 0f) { ConnectedScreen(state, previewChrome(), { codes += it }, { retries += it }, {}, {}) }
        }
        compose.onNode(hasText(PresetCode.ADMIRE_SIGNAL_MANNERS.label) and hasClickAction()).performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("다시 보내기").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(listOf(PresetCode.ADMIRE_SIGNAL_MANNERS), codes)
        assertEquals(listOf("narrow-failed"), retries)
    }

    @Test fun directStateGuardBlocksDiscoveryEvenIfCallerThemeIsAllowed() {
        val state = mutableStateOf(HomeUiState(driving = MeetFixtures.parked, discovery = MeetFixtures.discovery,
            profile = MeetFixtures.own, profileLoaded = true))
        var toggles = 0
        compose.setContent {
            ChayeonTheme(DrivingPolicy.ALLOWED, 0f) { HomeScreen(state.value, previewChrome(), { toggles++ }, {}, {}, {}, {}, {}, {}) }
        }
        compose.onNodeWithText("발견 켜짐").assertExists()
        compose.runOnIdle { state.value = state.value.copy(driving = MeetFixtures.parked.copy(policy = DrivingPolicy.UNKNOWN)) }
        compose.onNodeWithText("발견 켜짐").assertDoesNotExist()
        compose.onNodeWithText("프로필 보기").assertDoesNotExist()
        compose.onNodeWithText("주행 상태를 확인하고 있어요").assertExists()
        assertEquals(0, toggles)
    }
}
