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

    @Test fun demoAwayExplainsEmptyListAndOpensConsole() {
        var opened = false
        compose.setContent {
            ChayeonTheme(DrivingPolicy.ALLOWED, 0f) {
                HomeScreen(HomeUiState(driving = MeetFixtures.parked, profile = MeetFixtures.own, profileLoaded = true,
                    discovery = MeetFixtures.discovery.copy(status = DiscoveryStatus.EMPTY, candidates = emptyList()), demoPresence = SyntheticPresence.AWAY),
                    previewChrome(), {}, {}, {}, {}, {}, {}, {}, onDemo = { opened = true })
            }
        }
        compose.onNodeWithText("합성 위치가 멀어진 상태예요").assertIsDisplayed()
        compose.onNodeWithText("시연 제어 열기").performClick()
        assertTrue(opened)
    }

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
                ConnectedScreen(state, previewChrome(policy = state.driving.policy), model::leave, {})
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

    @Test fun suppressedHomeExplainsAndBlocksAcceptIncludingDirectEntryButAllowsDecline() {
        val deps = FakeMeetDeps()
        deps.requests.value = LoadState.Ready(listOf(MeetFixtures.request), 2)
        deps.homeZone.value = HomeZoneState(HomeZoneStatus.LOCATION_UNKNOWN, HomeZoneRadius.MEDIUM, LocationSource.DEVICE)
        val model = keep(RequestViewModel(deps))
        showRequest(model)
        compose.onNodeWithText("집 근처에서 쉬는 동안에는 수락할 수 없어요").assertIsDisplayed()
        compose.onNodeWithText("수락하고 연결").assertIsNotEnabled()
        compose.runOnIdle { model.accept() }
        compose.waitForIdle()
        assertTrue(deps.respondCommands.isEmpty())
        compose.runOnIdle { model.decline() }
        compose.waitUntil(timeoutMillis = 5_000) { deps.respondCommands.size == 1 }
        assertEquals(RequestDecision.DECLINE, deps.respondCommands.single().decision)
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

    @Test fun parkedCallShowsVoiceAndConversationTopics() {
        val deps = FakeMeetDeps()
        deps.connection.value = LoadState.Ready(MeetFixtures.connection, 3)
        showConnected(keep(ConnectedViewModel(deps)))
        compose.onNodeWithText("통화 중").assertIsDisplayed()
        compose.onNodeWithText("이야깃거리").assertExists()
        listOf("주고받은 인사", "인사 보내기", "다시 보내기", "안녕하세요").forEach {
            compose.onAllNodesWithText(it).assertCountEquals(0)
        }
    }

    @Test fun checkingCallKeepsOnlyVoiceAndSafetyControls() {
        val state = ConnectedUiState(driving = MeetFixtures.parked,
            connection = MeetFixtures.connection.copy(status = ConnectionStatus.CHECKING), ownProfile = MeetFixtures.own,
            loading = false)
        compose.setContent {
            ChayeonTheme(DrivingPolicy.ALLOWED, 0f) { ConnectedScreen(state, previewChrome(), {}, {}) }
        }
        compose.onNodeWithText("연결 상태를 확인하고 있어요").assertExists()
        compose.onNodeWithContentDescription("음소거").assertIsNotEnabled()
        compose.onNodeWithContentDescription("통화 끊기").assertIsEnabled()
        compose.onNodeWithText("인사 보내기").assertDoesNotExist()
    }

    @Test fun policyTransitionHidesTopicsAndSafetyLeavingCallControls() {
        val deps = FakeMeetDeps()
        deps.connection.value = LoadState.Ready(MeetFixtures.connection, 3)
        val model = keep(ConnectedViewModel(deps))
        showConnected(model)
        compose.onNodeWithText("이야깃거리").assertExists()
        for (policy in listOf(DrivingPolicy.RESTRICTED, DrivingPolicy.UNKNOWN)) {
            compose.runOnIdle { deps.drivingState.value = MeetFixtures.parked.copy(policy = policy, gear = Gear.DRIVE) }
            compose.waitUntil(timeoutMillis = 5_000) { model.uiState.value.driving.policy == policy }
            compose.onNodeWithText("주고받은 인사").assertDoesNotExist()
            compose.onNodeWithText("차단·신고").assertDoesNotExist()
            compose.onNodeWithText("통화 끊기").assertIsEnabled()
        }
        compose.onNodeWithText("통화 끊기").performClick()
        compose.waitUntil(timeoutMillis = 5_000) { deps.leaveCommands.size == 1 }
        assertEquals(MeetFixtures.connection.id, deps.leaveCommands.single().connectionId)
    }

    @Test fun homeProtectionHidesAnAlreadySeenConnectionUntilParkingAgain() {
        val deps = FakeMeetDeps()
        deps.connection.value = LoadState.Ready(MeetFixtures.connection, 3)
        val model = keep(ConnectedViewModel(deps))
        showConnected(model)
        compose.onNodeWithText(MeetFixtures.peer.nickname).assertExists()
        compose.runOnIdle {
            deps.drivingState.value = MeetFixtures.parked.copy(policy = DrivingPolicy.RESTRICTED, gear = Gear.DRIVE)
            deps.homeZone.value = HomeZoneState(HomeZoneStatus.INSIDE, HomeZoneRadius.MEDIUM, LocationSource.SYNTHETIC)
        }
        compose.waitUntil { model.uiState.value.awaitingPark }
        compose.onNodeWithText(MeetFixtures.peer.nickname).assertDoesNotExist()
        compose.onNodeWithText("통화 끊기").assertIsEnabled()
        compose.runOnIdle { deps.homeZone.value = deps.homeZone.value.copy(status = HomeZoneStatus.OUTSIDE) }
        compose.onNodeWithText(MeetFixtures.peer.nickname).assertDoesNotExist()
        compose.runOnIdle { deps.drivingState.value = MeetFixtures.parked }
        compose.onNodeWithText(MeetFixtures.peer.nickname).assertExists()
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
        compose.onNodeWithText("통화 끊기").assertIsEnabled().performClick()
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
        val signature = MeetFixtures.signature.copy(traits = TraitId.entries.map { PublicTrait(it, TraitLevel.SIGNATURE, if (it == TraitId.LONG_ROAD || it == TraitId.NIGHT_DRIVE) null else 94) })
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
    fun narrowCallKeepsControlsVisibleAndTopicsReachable() {
        var muted = false
        var ended = false
        val state = ConnectedUiState(driving = MeetFixtures.parked, connection = MeetFixtures.connection, ownProfile = MeetFixtures.own,
            loading = false)
        compose.setContent {
            ChayeonTheme(DrivingPolicy.ALLOWED, 0f) { ConnectedScreen(state, previewChrome(), { ended = true }, {}, onMute = { muted = true }) }
        }
        compose.onNodeWithContentDescription("음소거").assertIsDisplayed().performClick()
        compose.onNodeWithText("이야깃거리").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("차단·신고").assertIsDisplayed()
        compose.onNodeWithText("주고받은 인사").assertDoesNotExist()
        compose.onNodeWithContentDescription("통화 끊기").assertIsDisplayed().performClick()
        assertTrue(muted)
        assertTrue(ended)
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
