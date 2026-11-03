package dev.chayeon

import androidx.compose.ui.test.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import dev.chayeon.core.model.*
import dev.chayeon.core.ui.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1682dp-h1210dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationSmokeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun chromeTracksLiveGearAndDiscoveryCommand() {
        val fixture = TestGraph().apply { ready() }
        compose.setContent { ChayeonApp(fixture.graph) }
        compose.onNodeWithText("주차 중").assertIsDisplayed()
        compose.onNodeWithContentDescription("발견 켜기").performClick()
        compose.runOnIdle { assertEquals(1, fixture.discoveryCalls) }
        compose.onNodeWithContentDescription("발견 끄기").assertIsDisplayed()
        compose.onRoot().captureRoboImage("src/test/screenshots/app-ready.png")
        compose.runOnIdle { fixture.driving.value = DrivingState(policy = DrivingPolicy.RESTRICTED, gear = Gear.DRIVE) }
        compose.onNodeWithText("주행 중", useUnmergedTree = true).assertExists()
        compose.onRoot().captureRoboImage("src/test/screenshots/app-restricted.png")
    }

    @Test fun demoConsoleClearlyLabelsSyntheticDataAndDisablesRealGear() {
        val chrome = ScreenChrome({}, {})
        compose.setContent {
            ChayeonTheme(policy = DrivingPolicy.ALLOWED) {
                DemoConsoleScreen(DemoUiState(DrivingState(policy = DrivingPolicy.ALLOWED, gear = Gear.PARK)), chrome,
                    realVehicle = true, enabled = true, {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("시연 제어").assertIsDisplayed()
        compose.onNodeWithText("합성 주행 3회로 시그니처 채우기").assertIsDisplayed()
        compose.onNodeWithText("실제 차량 서비스가 있어요. 기어는 차량의 Virtual Console에서 바꿔 주세요.").assertIsDisplayed()
        compose.onRoot().captureRoboImage("src/test/screenshots/demo-console.png")
    }
    @Test fun unseenRecapWaitsUntilPark() {
        val fixture = TestGraph().apply {
            ready()
            driving.value = DrivingState(policy = DrivingPolicy.RESTRICTED, gear = Gear.DRIVE)
            recaps.value = LoadState.Ready(listOf(TripRecap("recap", 1, "trip", 1, DataSource.VEHICLE,
                KnotSeed(1), KnotSeed(2), emptyList())), 1)
        }
        compose.setContent { ChayeonApp(fixture.graph) }
        compose.onNodeWithText("주행 후 요약").assertDoesNotExist()
        compose.runOnIdle { fixture.driving.value = DrivingState(policy = DrivingPolicy.ALLOWED, gear = Gear.PARK) }
        compose.onNodeWithText("주행 후 요약").assertExists()
        compose.onRoot().captureRoboImage("src/test/screenshots/app-recap.png")
    }

    @Test fun demoLockedAndUnavailableStates() {
        var policy by androidx.compose.runtime.mutableStateOf(DrivingPolicy.UNKNOWN)
        compose.setContent {
            ChayeonTheme(policy = policy) {
                DemoConsoleScreen(DemoUiState(DrivingState(policy = policy)), ScreenChrome({}, {}), true, false, {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("합성 주행 3회로 시그니처 채우기").assertDoesNotExist()
        compose.onRoot().captureRoboImage("src/test/screenshots/demo-unknown.png")
        compose.runOnIdle { policy = DrivingPolicy.RESTRICTED }
        compose.onRoot().captureRoboImage("src/test/screenshots/demo-restricted.png")
        compose.runOnIdle { policy = DrivingPolicy.ALLOWED }
        compose.onNodeWithText("이 앱에서는 시연 제어를 제공하지 않아요").assertIsDisplayed()
        compose.onRoot().captureRoboImage("src/test/screenshots/demo-unavailable.png")
    }

}
