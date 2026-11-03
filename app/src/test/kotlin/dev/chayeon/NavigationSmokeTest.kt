package dev.chayeon

import android.provider.Settings
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import dev.chayeon.core.model.DataSource
import dev.chayeon.core.model.DrivingPolicy
import dev.chayeon.core.model.DrivingState
import dev.chayeon.core.model.DrivingStateProvider
import dev.chayeon.core.model.Gear
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
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

    private val driving = MutableStateFlow(
        DrivingState(version = 1, policy = DrivingPolicy.ALLOWED, gear = Gear.PARK, source = DataSource.SYNTHETIC),
    )
    private val graph = AppGraph.foundation(drivingStateProvider = object : DrivingStateProvider {
        override val drivingState = driving
    })

    @Before
    fun disableDecorativeMotion() {
        Settings.Global.putFloat(
            ApplicationProvider.getApplicationContext<ChayeonApplication>().contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f,
        )
    }

    @Test
    fun featureGraphsNavigateThroughCallbacks() {
        compose.setContent { ChayeonApp(graph) }
        compose.onNodeWithText("스친 인연").assertIsDisplayed()
        capture("history-placeholder")
        compose.onNodeWithText("오늘의 인연").performClick()
        compose.onNodeWithText("오늘의 인연").assertIsDisplayed()
        capture("meet-placeholder")
        compose.onNodeWithText("내 프로필").performClick()
        compose.onNodeWithText("내 프로필").assertIsDisplayed()
        capture("signature-placeholder")
        compose.onNodeWithText("스친 인연").performClick()
        compose.onNodeWithText("스친 인연").assertIsDisplayed()
    }

    @Test
    fun gearAndUnknownUpdatesRemoveBrowsingActions() {
        compose.setContent { ChayeonApp(graph) }
        compose.onNodeWithText("오늘의 인연").performClick()
        compose.runOnIdle {
            driving.value = DrivingState(version = 2, policy = DrivingPolicy.RESTRICTED, gear = Gear.DRIVE)
        }
        compose.onNodeWithText("주차하면 볼 수 있어요").assertIsDisplayed()
        compose.onNodeWithText("내 프로필").assertDoesNotExist()
        capture("meet-restricted")
        compose.runOnIdle { driving.value = DrivingState(version = 3) }
        compose.onNodeWithText("주행 상태를 확인하지 못했어요").assertIsDisplayed()
        compose.onNodeWithText("스친 인연").assertDoesNotExist()
        capture("meet-unknown")
    }

    @Test
    @Config(qualifiers = "w840dp-h1210dp-port-mdpi")
    fun narrowWindowStacksPlaceholderPanels() {
        compose.setContent { ChayeonApp(graph) }
        compose.onNodeWithText("오늘의 인연").assertIsDisplayed()
        capture("history-narrow")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
