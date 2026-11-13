package dev.chayeon.feature.meet

import android.graphics.BitmapFactory
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import dev.chayeon.core.model.*
import dev.chayeon.core.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TemporaryFolder
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w1682dp-h1210dp-land-mdpi")
class MeetMotionTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val temporary = TemporaryFolder()
    @OptIn(ExperimentalRoborazziApi::class)
    private fun pixels(): IntArray {
        val frame = temporary.newFolder().resolve("frame.png")
        compose.onNodeWithTag("meet-screen").captureRoboImage(
            frame.absolutePath,
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
        )
        val image = BitmapFactory.decodeFile(frame.absolutePath)
        return IntArray(image.width * image.height).also {
            image.getPixels(it, 0, image.width, 0, 0, image.width, image.height)
            image.recycle()
        }
    }

    @Test fun connectionReachesAndThenLightFlows() {
        compose.mainClock.autoAdvance = false
        val state = ConnectedUiState(driving = MeetFixtures.parked, connection = MeetFixtures.connection,
            ownProfile = MeetFixtures.own, loading = false)
        compose.setContent {
            ChayeonTheme(DrivingPolicy.ALLOWED, 1f) { ConnectedScreen(state, previewChrome(), {}, {}) }
        }
        compose.mainClock.advanceTimeBy(350)
        val reaching = pixels()
        compose.onNodeWithTag("meet-screen").captureRoboImage("src/test/screenshots/connected_reaching_350ms.png")
        compose.mainClock.advanceTimeBy(400)
        val reached = pixels()
        assertFalse(reaching.contentEquals(reached))
        compose.mainClock.advanceTimeBy(1_000)
        assertFalse(reached.contentEquals(pixels()))
    }

    @Test fun endedThreadRetracts() {
        compose.mainClock.autoAdvance = false
        val state = EndedUiState(driving = MeetFixtures.parked, connection = MeetFixtures.ended, ownProfile = MeetFixtures.own, loading = false)
        compose.setContent {
            ChayeonTheme(DrivingPolicy.ALLOWED, 1f) { EndedScreen(state, previewChrome(), {}, {}) }
        }
        compose.mainClock.advanceTimeBy(100)
        val breaking = pixels()
        compose.onNodeWithTag("meet-screen").captureRoboImage("src/test/screenshots/ended_breaking_100ms.png")
        compose.mainClock.advanceTimeBy(400)
        assertFalse(breaking.contentEquals(pixels()))
    }

    @Test fun policyTransitionStopsAllDecorativeMotion() {
        compose.mainClock.autoAdvance = false
        val driving = mutableStateOf(MeetFixtures.parked)
        compose.setContent {
            ChayeonTheme(driving.value.policy, 1f) {
                ConnectedScreen(ConnectedUiState(driving = driving.value, connection = MeetFixtures.connection,
                    ownProfile = MeetFixtures.own, loading = false),
                    previewChrome(policy = driving.value.policy, gear = driving.value.gear), {}, {})
            }
        }
        compose.mainClock.advanceTimeBy(900)
        compose.runOnIdle { driving.value = DrivingState(policy = DrivingPolicy.RESTRICTED, gear = Gear.DRIVE) }
        compose.mainClock.advanceTimeBy(50)
        val stopped = pixels()
        compose.mainClock.advanceTimeBy(1_000)
        assertArrayEquals(stopped, pixels())
    }

    @Test fun disabledAnimatorScaleShowsStableFinalState() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ChayeonTheme(DrivingPolicy.ALLOWED, 0f) {
                ConnectedScreen(ConnectedUiState(driving = MeetFixtures.parked, connection = MeetFixtures.connection,
                    ownProfile = MeetFixtures.own, loading = false), previewChrome(), {}, {})
            }
        }
        compose.mainClock.advanceTimeBy(50)
        val still = pixels()
        compose.mainClock.advanceTimeBy(1_000)
        assertArrayEquals(still, pixels())
    }
}
