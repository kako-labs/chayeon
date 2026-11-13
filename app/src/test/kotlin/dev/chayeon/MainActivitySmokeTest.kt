package dev.chayeon

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
        compose.onNodeWithText("주행 상태 확인 중").assertIsDisplayed()
        assertFalse((compose.activity.application as ChayeonApplication).graph.serverModeAvailable)
        compose.onRoot().captureRoboImage("src/test/screenshots/application-unknown.png")
    }

    @Test fun microphonePermissionIsWiredWithoutAnyLocationPermissionResult() {
        val app = compose.activity.application as ChayeonApplication
        org.robolectric.Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.RECORD_AUDIO)
        app.graph.currentPolicy = { dev.chayeon.core.model.DrivingPolicy.ALLOWED }
        assertTrue(kotlinx.coroutines.runBlocking { app.graph.prepareMicrophone() })
        for (policy in listOf(dev.chayeon.core.model.DrivingPolicy.RESTRICTED, dev.chayeon.core.model.DrivingPolicy.UNKNOWN)) {
            app.graph.currentPolicy = { policy }
            assertFalse(kotlinx.coroutines.runBlocking { app.graph.prepareMicrophone() })
        }
    }
}
