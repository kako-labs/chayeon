package dev.chayeon.feature.meet

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.assertCountEquals
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import dev.chayeon.core.model.*
import dev.chayeon.core.ui.ChayeonTheme
import dev.chayeon.core.ui.previewChrome
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w1682dp-h1210dp-land-mdpi")
class MeetNavigationTest {
    @get:Rule val compose = createComposeRule()
    private val deps = FakeMeetDeps()
    private lateinit var controller: NavHostController
    private val exits = mutableListOf<MeetExit>()

    private fun launch() {
        compose.setContent {
            val driving by deps.drivingState.collectAsState()
            ChayeonTheme(driving.policy, animatorScaleOverride = 0f) {
                controller = rememberNavController()
                NavHost(controller, MeetDestination.HOME) {
                    meetGraph(deps, previewChrome(policy = driving.policy, gear = driving.gear), controller, exits::add)
                }
            }
        }
        compose.waitForIdle()
    }

    private fun awaitRoute(route: String) {
        compose.waitUntil(5_000) { controller.currentBackStackEntry?.destination?.route == route }
        compose.waitForIdle()
    }

    @Test fun drivingReturnsToHomeWithoutChangingDiscovery() {
        launch()
        compose.runOnIdle { deps.drivingState.value = DrivingState(policy = DrivingPolicy.RESTRICTED, gear = Gear.DRIVE) }
        awaitRoute(MeetDestination.DRIVING)
        compose.onNodeWithText("주행 중이에요").assertExists()
        compose.runOnIdle { deps.drivingState.value = MeetFixtures.parked }
        awaitRoute(MeetDestination.HOME)
        assertEquals(MeetFixtures.discovery, deps.discovery.value)
    }

    @Test fun newConnectionWhileDrivingHidesIdentityUntilParked() {
        launch()
        compose.runOnIdle { deps.drivingState.value = DrivingState(policy = DrivingPolicy.RESTRICTED, gear = Gear.DRIVE) }
        awaitRoute(MeetDestination.DRIVING)
        compose.runOnIdle { deps.connection.value = LoadState.Ready(MeetFixtures.connection, 3) }
        compose.waitForIdle()
        for (policy in listOf(DrivingPolicy.RESTRICTED, DrivingPolicy.UNKNOWN)) {
            compose.runOnIdle { deps.drivingState.value = DrivingState(policy = policy) }
            compose.waitForIdle()
            compose.runOnIdle { assertEquals(MeetDestination.DRIVING, controller.currentDestination?.route) }
            compose.onNodeWithText("새 연결이 있어요").assertExists()
            compose.onNodeWithText(MeetFixtures.peer.nickname).assertDoesNotExist()
            compose.onNodeWithText(MeetFixtures.own.nickname).assertDoesNotExist()
            compose.onAllNodes(hasClickAction()).assertCountEquals(1)
        }
        compose.runOnIdle { deps.drivingState.value = MeetFixtures.parked }
        awaitRoute(MeetDestination.CONNECTED)
        compose.onNodeWithText(MeetFixtures.peer.nickname).assertExists()
    }

    @Test fun newConnectionWhileUnknownCanBeLeftWithoutRevealingIdentity() {
        deps.drivingState.value = DrivingState()
        deps.leaveResult = {
            deps.connection.value = LoadState.Ready(MeetFixtures.ended, 4)
            AppResult.Success(MeetFixtures.ended)
        }
        launch()
        awaitRoute(MeetDestination.DRIVING)
        compose.runOnIdle { deps.connection.value = LoadState.Ready(MeetFixtures.connection, 3) }
        compose.waitForIdle()
        compose.onNodeWithText("나가기").performClick()
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(MeetFixtures.connection.id, deps.leaveCommands.single().connectionId)
            assertEquals(MeetDestination.DRIVING, controller.currentDestination?.route)
        }
        compose.onNodeWithText(MeetFixtures.peer.nickname).assertDoesNotExist()
        compose.runOnIdle { deps.drivingState.value = MeetFixtures.parked }
        awaitRoute(MeetDestination.ENDED)
        compose.onNodeWithText("기록 보기").assertExists()
    }

    @Test fun serverRequestConnectionAndEndDriveNavigationOnlyOnce() {
        launch()
        compose.runOnIdle { deps.requests.value = LoadState.Ready(listOf(MeetFixtures.request), 2) }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(MeetDestination.HOME, controller.currentDestination?.route) }
        compose.onNodeWithText("받은 연결 요청 보기").performClick()
        awaitRoute(MeetDestination.REQUESTS)
        compose.runOnIdle {
            assertEquals("request", controller.currentBackStackEntry?.savedStateHandle?.get<String>("meet.requestId"))
            deps.requests.value = LoadState.Ready(listOf(MeetFixtures.request.copy(status = ConnectionRequestStatus.ACCEPTED, connectionId = "connection")), 3)
            deps.connection.value = LoadState.Ready(MeetFixtures.connection, 3)
        }
        awaitRoute(MeetDestination.CONNECTED)
        compose.runOnIdle { deps.connection.value = LoadState.Ready(MeetFixtures.ended, 4) }
        awaitRoute(MeetDestination.ENDED)
        compose.onNodeWithText("기록 보기").performClick()
        compose.runOnIdle { assertEquals(listOf(MeetExit.OpenEncounter("encounter")), exits) }
        compose.onNodeWithText("오늘의 인연으로").performClick()
        awaitRoute(MeetDestination.HOME)
        compose.runOnIdle { deps.connection.value = LoadState.Ready(MeetFixtures.ended.copy(version = 5), 5) }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(MeetDestination.HOME, controller.currentDestination?.route) }
    }

    @Test fun newRequestResponseBeforeSyncKeepsExactTarget() {
        deps.requests.value = LoadState.Ready(listOf(MeetFixtures.request.copy(id = "old", status = ConnectionRequestStatus.EXPIRED)), 1)
        launch()
        compose.runOnIdle { controller.navigate(MeetDestination.candidate("candidate")) }
        awaitRoute(MeetDestination.CANDIDATE)
        compose.onNodeWithText("연결 요청 보내기").performClick()
        awaitRoute(MeetDestination.REQUESTS)
        compose.runOnIdle {
            assertEquals("request", controller.currentBackStackEntry?.savedStateHandle?.get<String>("meet.requestId"))
            deps.requests.value = LoadState.Ready(listOf(MeetFixtures.request.copy(direction = RequestDirection.OUTGOING)), 2)
        }
        compose.waitForIdle()
        compose.onNodeWithText("요청 취소").performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("request", deps.cancelCommands.single().requestId) }
    }
}
