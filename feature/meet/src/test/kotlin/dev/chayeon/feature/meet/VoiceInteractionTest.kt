package dev.chayeon.feature.meet

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import dev.chayeon.core.model.*
import dev.chayeon.core.ui.*
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
class VoiceInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val driving = MeetFixtures.parked.copy(policy = DrivingPolicy.RESTRICTED, gear = Gear.DRIVE)

    @Test fun drivingCallHasOnlyLargeMuteAndEndActionsAndNoThread() {
        val state = mutableStateOf(ConnectedUiState(driving, MeetFixtures.connection, MeetFixtures.own,
            call = VoiceCallState("connection", CallStatus.ACTIVE)))
        var ended = false
        compose.setContent {
            ChayeonTheme(state.value.driving.policy, 0f) {
                ConnectedScreen(state.value, previewChrome(policy = state.value.driving.policy), { ended = true }, {},
                    onMute = { state.value = state.value.copy(call = state.value.call.copy(muted = !state.value.call.muted)) })
            }
        }
        compose.onAllNodes(hasClickAction() and isEnabled()).assertCountEquals(2)
        compose.onNodeWithText("주고받은 인사").assertDoesNotExist()
        compose.onNodeWithText("차단·신고").assertDoesNotExist()
        compose.onNodeWithContentDescription("음소거").assertHeightIsAtLeast(96.dp).performClick()
        compose.onNodeWithText("음소거 중").assertExists()
        compose.onNodeWithContentDescription("음소거 해제").performClick()
        compose.onNodeWithText("통화 중").assertExists()
        compose.onNodeWithContentDescription("통화 끊기").assertHeightIsAtLeast(96.dp).performClick()
        assertTrue(ended)
        compose.runOnIdle { state.value = state.value.copy(driving = DrivingState()) }
        compose.onNodeWithContentDescription("음소거").assertIsNotEnabled()
    }

    @Test fun incomingRequiresExplicitOptInAndKnownRestrictedPolicy() {
        val state = mutableStateOf(RequestUiState(driving, MeetFixtures.request, receiveRequestsWhileDriving = false))
        var accepted = 0
        var declined = 0
        compose.setContent {
            ChayeonTheme(state.value.driving.policy, 0f) {
                RequestScreen(state.value, previewChrome(policy = state.value.driving.policy), { accepted++ }, { declined++ }, {}, {}, {})
            }
        }
        compose.onNodeWithText("받기").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(receiveRequestsWhileDriving = true) }
        compose.onAllNodes(hasClickAction() and isEnabled()).assertCountEquals(2)
        compose.onNodeWithText("통화 요청").assertIsDisplayed()
        compose.onNodeWithText(MeetFixtures.peer.nickname).assertIsDisplayed()
        compose.onNodeWithText(MeetFixtures.peer.signature!!.title).assertIsDisplayed()
        compose.onNodeWithText("좋아하는 것").assertDoesNotExist()
        compose.onNodeWithContentDescription("받기").assertHeightIsAtLeast(96.dp).performClick()
        assertEquals(1, accepted)
        compose.runOnIdle { state.value = state.value.copy(homeSuppressed = true) }
        compose.onNodeWithText("집 근처에서 쉬는 동안에는 수락할 수 없어요").assertIsDisplayed()
        compose.onNodeWithContentDescription("받기").assertIsNotEnabled()
        compose.onNodeWithContentDescription("거절").performClick()
        assertEquals(1, declined)
        compose.runOnIdle { state.value = state.value.copy(driving = DrivingState()) }
        compose.onNodeWithText("받기").assertDoesNotExist()
    }

    @Test fun topicsShowPublicContentAndCollapseCompletelyWhenEmpty() {
        val state = mutableStateOf(ConnectedUiState(MeetFixtures.parked, MeetFixtures.connection, MeetFixtures.own))
        compose.setContent {
            ChayeonTheme(DrivingPolicy.ALLOWED, 0f) { ConnectedScreen(state.value, previewChrome(), {}, {}) }
        }
        val topicsHeight = compose.onNodeWithTag("call-topics").getUnclippedBoundsInRoot().let { it.bottom - it.top }
        compose.onNodeWithText("이야깃거리").assertExists()
        compose.onNodeWithText(InterestId.JAZZ.label).assertExists()
        compose.onNodeWithText(InterestId.EXHIBITIONS.label).assertDoesNotExist()
        compose.onNodeWithText(MeetFixtures.chemistry.lines.single().text).assertExists()
        compose.onNodeWithText("나 · ${MeetFixtures.own.signature!!.title}").assertExists()
        compose.runOnIdle {
            state.value = state.value.copy(ownProfile = MeetFixtures.own.copy(interests = emptySet(), signature = null),
                connection = MeetFixtures.connection.copy(peer = MeetFixtures.peer.copy(interests = emptySet(), signature = null), chemistry = Chemistry()))
        }
        compose.onNodeWithTag("call-topics").assertDoesNotExist()
        compose.onNodeWithText("이야깃거리").assertDoesNotExist()
        val safetyHeight = compose.onNodeWithTag("call-safety").getUnclippedBoundsInRoot().let { it.bottom - it.top }
        assertTrue(safetyHeight < topicsHeight)
        compose.onNodeWithText("차단·신고").assertIsDisplayed()
    }

    @Test fun bothParkedRequestDirectionsExplainAutomaticCall() {
        val state = mutableStateOf(RequestUiState(MeetFixtures.parked, MeetFixtures.request))
        compose.setContent {
            ChayeonTheme(DrivingPolicy.ALLOWED, 0f) { RequestScreen(state.value, previewChrome(), {}, {}, {}, {}, {}) }
        }
        compose.onNodeWithText("수락하면 바로 통화가 연결돼요").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(request = MeetFixtures.request.copy(direction = RequestDirection.OUTGOING)) }
        compose.onNodeWithText("수락하면 바로 통화가 연결돼요").assertIsDisplayed()
    }
}
