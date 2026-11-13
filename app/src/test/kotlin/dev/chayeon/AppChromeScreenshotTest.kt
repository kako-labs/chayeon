package dev.chayeon

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import dev.chayeon.core.model.*
import dev.chayeon.core.ui.*
import dev.chayeon.feature.history.HistoryDestination
import dev.chayeon.feature.meet.MeetDestination
import dev.chayeon.feature.signature.SignatureDestination
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
class AppChromeScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun capture(name: String, selected: NavigationItem = NavigationItem.MEET, policy: DrivingPolicy = DrivingPolicy.ALLOWED,
        detail: Boolean = false, synthetic: Boolean = false) {
        compose.setContent { ChayeonTheme(policy, 0f, showSourceLabels = synthetic) {
            ScreenScaffold(scene = Scene.PARKING,
                contentPadding = PaddingValues(start = ChayeonDimensions.SceneScreenHorizontal, end = ChayeonDimensions.SceneScreenHorizontal,
                    top = ChayeonDimensions.SceneScreenTop, bottom = ChayeonDimensions.SceneScreenBottom), topBar = {
                Row {
                    AppTopChrome(if (policy == DrivingPolicy.ALLOWED) Gear.PARK else if (policy == DrivingPolicy.RESTRICTED) Gear.DRIVE else Gear.UNKNOWN,
                        policy, rootTab = !detail, syntheticLocation = synthetic, modifier = Modifier.weight(1f), onDemo = if (synthetic) ({}) else null, backAction = if (detail) ChromeBackAction({}) else null)
                }
            }, bottomBar = { if (!detail && policy == DrivingPolicy.ALLOWED) BottomNav(selected, true, {}, {}, incomingAfterCount = 2) }, primary = {
                if (policy == DrivingPolicy.ALLOWED) Column(Modifier.fillMaxWidth().padding(top = 40.dp)) {
                    HeroHeadline(if (detail) "자세히 알아봐요" else selected.label, "길 위에서 시작되는 인연")
                }
            })
        } }
        compose.onRoot().captureRoboImage("src/test/screenshots/v2-chrome-$name.png")
    }

    @Test fun home() = capture("home")
    @Test fun bonds() = capture("bonds", NavigationItem.RELATIONSHIPS)
    @Test fun profile() = capture("profile", NavigationItem.PROFILE)
    @Test fun detail() = capture("detail", detail = true)
    @Test fun driving() = capture("driving", policy = DrivingPolicy.RESTRICTED)
    @Test fun unknown() = capture("unknown", policy = DrivingPolicy.UNKNOWN)
    @Test @Config(sdk = [35], qualifiers = "w840dp-h1210dp-port-mdpi")
    fun narrowServer() = capture("narrow-server", synthetic = true)

    @Test fun sceneRoutingUsesParkingForSignatureAndKeepsHomeProtectionLocal() {
        assertEquals(Scene.PARKING, appScene(MeetDestination.HOME, false))
        assertEquals(Scene.HOME, appScene(MeetDestination.HOME, true))
        assertEquals(Scene.HOME, appScene(HistoryDestination.HOME_ZONE, false))
        assertEquals(Scene.DAWN, appScene(HistoryDestination.PROFILE_SETUP, false))
        assertEquals(Scene.DAWN, appScene(HistoryDestination.RELATIONSHIPS, false))
        assertEquals(Scene.PARKING, appScene(SignatureDestination.TRAIT, false))
        assertEquals(Scene.ROAD, appScene(MeetDestination.ENDED, false))
    }
}
