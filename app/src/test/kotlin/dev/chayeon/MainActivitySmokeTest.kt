package dev.chayeon

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1682dp-h1210dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainActivitySmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun applicationStartsWithUnknownDrivingPolicyAndNoSession() {
        compose.onNodeWithText("차연").assertIsDisplayed()
        compose.onNodeWithText("주행 상태를 확인하지 못했어요").assertIsDisplayed()
        compose.onNodeWithText("오늘의 인연").assertDoesNotExist()
        assertFalse(AppGraph.foundation().serverModeAvailable)
        compose.onRoot().captureRoboImage("src/test/screenshots/application-launch.png")
    }
}
