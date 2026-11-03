package dev.chayeon.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import dev.chayeon.core.model.DrivingPolicy
import dev.chayeon.core.model.KnotSeed
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w1682dp-h1210dp-land-mdpi")
class AnimationScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun captureFrame(name: String, time: Long, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ChayeonTheme(DrivingPolicy.ALLOWED, animatorScaleOverride = 1f) {
                ChayeonBackground(Modifier.requiredSize(900.dp, 580.dp).testTag("animation")) {
                    Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) { content() }
                }
            }
        }
        compose.mainClock.advanceTimeBy(time)
        compose.onNodeWithTag("animation").captureRoboImage("src/test/screenshots/animation_$name.png")
    }

    @Test fun connectionExtendsAt350ms() = captureFrame("connect_350ms", 350L) { ThreadView(ThreadStage.CONNECTED) }
    @Test fun severedEndsRetreatAt225ms() = captureFrame("end_225ms", 225L) { ThreadView(ThreadStage.ENDED) }
    @Test fun afterBeadsGatherAt400ms() = captureFrame("after_400ms", 400L) { ThreadView(ThreadStage.AFTER_CONNECTED) }
    @Test fun afterKnotTiesAt700ms() = captureFrame("after_700ms", 700L) { ThreadView(ThreadStage.AFTER_CONNECTED) }
    @Test fun signatureRevealsAt600ms() = captureFrame("signature_600ms", 600L) { SignatureKnot(KnotSeed(2357933L), Modifier.size(480.dp)) }
}
