package dev.chayeon

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import dev.chayeon.core.model.*
import dev.chayeon.core.ui.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1682dp-h1210dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeZoneNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun offlineActiveSessionCanReachPrivateHomeSettingsWhileProfileSyncIsLoadingOrFailed() {
        val fixture = TestGraph().apply {
            sessions.value = SessionState.Active(active.copy(mode = SessionMode.SERVER_DEMO))
            profiles.value = LoadState.Loading()
            homeZone.value = HomeZoneState(HomeZoneStatus.LOCATION_UNKNOWN, HomeZoneRadius.MEDIUM, LocationSource.SYNTHETIC)
        }
        compose.setContent { ChayeonApp(fixture.graph) }
        compose.onNodeWithText("내 프로필").performClick()
        compose.runOnIdle { fixture.profiles.value = LoadState.Failed(AppError.Network, 0) }
        compose.onNodeWithText("집 보호 구역").performScrollTo().performClick()
        compose.onNodeWithText("위치를 확인할 때까지 쉬고 있어요").assertIsDisplayed()
        compose.onNodeWithText("집 지우기").assertIsEnabled()
        compose.onRoot().captureRoboImage("src/test/screenshots/home-zone-offline-settings.png")
    }

    private fun console(name: String) {
        compose.setContent { ChayeonTheme(DrivingPolicy.ALLOWED, 0f) {
            DemoConsoleScreen(DemoUiState(DrivingState(policy = DrivingPolicy.ALLOWED, gear = Gear.PARK)),
                previewChrome(resting = true), true, true, {}, {}, {}, {}, {}, syntheticHomeSet = true)
        } }
        compose.onNodeWithText("집에서 멀어지기").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("집 보호 구역 · 합성 위치").assertIsDisplayed()
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test fun consoleLabelsHomeControls() = console("home-zone-console")

    @Config(sdk = [35], qualifiers = "w840dp-h1210dp-port-mdpi")
    @Test fun narrowConsoleKeepsHomeControlsReachable() = console("home-zone-console-narrow")

    @Test fun drivingKeepsRestIndicatorButHidesAllHomeDetailsAndActions() {
        val fixture = TestGraph().apply {
            ready()
            homeZone.value = HomeZoneState(HomeZoneStatus.INSIDE, HomeZoneRadius.MEDIUM, LocationSource.SYNTHETIC)
            discovery.value = DiscoveryState(mode = DiscoveryMode.ON)
        }
        compose.setContent { ChayeonApp(fixture.graph) }
        compose.onNodeWithText("집 근처에서는 쉬고 있어요").assertIsDisplayed()
        for (policy in listOf(DrivingPolicy.RESTRICTED, DrivingPolicy.UNKNOWN)) {
            compose.runOnIdle { fixture.driving.value = DrivingState(policy = policy) }
            compose.onNodeWithText("쉬는 중").assertIsDisplayed()
            compose.onNodeWithText("집 근처에서는 쉬고 있어요").assertDoesNotExist()
            compose.onNodeWithText("집 보호 구역").assertDoesNotExist()
            compose.onNodeWithText("시연 제어").assertDoesNotExist()
            compose.onRoot().captureRoboImage("src/test/screenshots/home-zone-${policy.name.lowercase()}.png")
        }
    }

    @Test fun protectedConnectionKeepsOnlyRestIndicatorAndExitWhileDriving() {
        val fixture = TestGraph().apply {
            ready()
            discovery.value = DiscoveryState(mode = DiscoveryMode.ON)
            connection.value = LoadState.Ready(Connection("connection", 1, "request",
                publicProfile.copy(id = "peer", nickname = "가상상대"), ConnectionStatus.CONNECTED, 0, Chemistry()), 1)
        }
        compose.setContent { ChayeonApp(fixture.graph) }
        compose.onNodeWithText("가상상대 님과 이어져 있어요").assertIsDisplayed()
        for (policy in listOf(DrivingPolicy.RESTRICTED, DrivingPolicy.UNKNOWN)) {
            compose.runOnIdle {
                fixture.driving.value = DrivingState(policy = policy)
                fixture.homeZone.value = HomeZoneState(HomeZoneStatus.INSIDE, HomeZoneRadius.MEDIUM, LocationSource.SYNTHETIC)
            }
            compose.onNodeWithText("쉬는 중").assertIsDisplayed()
            compose.onNodeWithText("가상상대").assertDoesNotExist()
            compose.onNodeWithText("가상상대 님과 이어져 있어요").assertDoesNotExist()
            compose.onNodeWithText("나가기").assertIsEnabled()
            compose.onRoot().captureRoboImage("src/test/screenshots/home-zone-connection-${policy.name.lowercase()}.png")
        }
    }
}
