package dev.chayeon.feature.signature

import androidx.compose.runtime.mutableStateOf
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
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w1682dp-h1210dp-land-mdpi")
class SignatureMotionTest {
    @get:Rule val compose = createComposeRule()
    private val f = SignatureScreenFixtures

    private fun captureReveal(milliseconds: Long) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ChayeonTheme(DrivingPolicy.ALLOWED, 1f) {
                TripRecapScreen(TripRecapUiState(f.driving, f.ready(f.recap)), previewChrome(), {}, {}, {}, {})
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(milliseconds)
        compose.onNodeWithTag("signature-screen").captureRoboImage("src/test/screenshots/recap_reveal_${milliseconds}ms.png")
    }

    @Test fun revealHalfway() = captureReveal(600)
    @Test fun revealFinished() = captureReveal(1200)

    @Test fun drivingStopsRevealAndRemovesDetails() {
        val policy = mutableStateOf(DrivingPolicy.ALLOWED)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ChayeonTheme(policy.value, 1f) {
                TripRecapScreen(TripRecapUiState(f.driving.copy(policy = policy.value), f.ready(f.recap)),
                    previewChrome(policy = policy.value), {}, {}, {}, {})
            }
        }
        compose.mainClock.advanceTimeBy(600)
        compose.runOnIdle { policy.value = DrivingPolicy.UNKNOWN }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("recap-knot").assertDoesNotExist()
        compose.onNodeWithTag("exclude-trip").assertDoesNotExist()
        compose.onNodeWithText("주행 상태를 확인하고 있어요").assertIsDisplayed()
    }
}
