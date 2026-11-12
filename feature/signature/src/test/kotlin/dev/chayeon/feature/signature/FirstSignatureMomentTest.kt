package dev.chayeon.feature.signature

import androidx.compose.ui.test.*
import androidx.compose.runtime.mutableStateOf
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
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w1682dp-h1210dp-land-mdpi")
class FirstSignatureMomentTest {
    @get:Rule val compose = createComposeRule()
    private val f = SignatureScreenFixtures
    private val recap = f.recap.copy(firstSignature = true, changes = f.public.traits.map {
        TraitChange(it.trait, TraitLevel.RECORDING, it.level, it.trait.presentation.publicPhrase)
    })

    private fun show(scale: Float, visibility: () -> Unit = {}, signature: () -> Unit = {}) {
        compose.setContent {
            ChayeonTheme(DrivingPolicy.ALLOWED, scale) {
                TripRecapScreen(TripRecapUiState(f.driving, f.ready(recap)), previewChrome(showSourceLabels = false), {}, {}, signature, {}, visibility)
            }
        }
    }

    @Test fun knotMovesAloneBeforeTheStoryAppears() {
        compose.mainClock.autoAdvance = false
        show(1f)
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("이 주행이 매듭이 됐어요").assertDoesNotExist()
        compose.onNodeWithText("합성 주행").assertDoesNotExist()
        compose.onNodeWithTag("signature-screen").captureRoboImage("src/test/screenshots/moment_reveal_600ms.png")
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithText("이 주행이 매듭이 됐어요").assertIsDisplayed()
        compose.onNodeWithText(TraitId.EARLY_BRAKE.presentation.publicPhrase).assertDoesNotExist()
        compose.onNodeWithTag("signature-screen").captureRoboImage("src/test/screenshots/moment_title_1400ms.png")
        compose.mainClock.advanceTimeBy(1_600)
        compose.onNodeWithText(TraitId.EARLY_BRAKE.presentation.publicPhrase).assertIsDisplayed()
        compose.onNodeWithTag("recap-open-visibility").assertIsEnabled()
        compose.onNodeWithTag("signature-screen").captureRoboImage("src/test/screenshots/moment_settled.png")
    }

    @Test fun zeroAnimationScaleShowsCleanEndStateAndBothDestinations() {
        var visibilityCalls = 0
        var signatureCalls = 0
        show(0f, { visibilityCalls++ }, { signatureCalls++ })
        compose.onNodeWithText("이 주행이 매듭이 됐어요").assertIsDisplayed()
        compose.onNodeWithTag("recap-open-visibility").performClick()
        compose.onNodeWithTag("recap-open-signature").performClick()
        compose.onNodeWithTag("exclude-trip").assertDoesNotExist()
        assertEquals(1, visibilityCalls)
        assertEquals(1, signatureCalls)
        compose.onNodeWithTag("signature-screen").captureRoboImage("src/test/screenshots/moment_motion_off.png")
    }

    @Config(sdk = [35], qualifiers = "w840dp-h1210dp-port-mdpi")
    @Test fun narrowMomentKeepsEveryActionReachable() {
        show(0f)
        compose.onNodeWithTag("signature-screen").captureRoboImage("src/test/screenshots/moment_narrow.png")
        compose.onNodeWithTag("recap-open-visibility").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("recap-open-signature").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("exclude-trip").assertDoesNotExist()
    }

    @Test fun firstRealDriveUsesTheSameMomentAndLabelsItsSource() {
        compose.setContent {
            ChayeonTheme(DrivingPolicy.ALLOWED, 0f) {
                TripRecapScreen(TripRecapUiState(f.driving, f.ready(recap.copy(source = DataSource.VEHICLE))), previewChrome(showSourceLabels = true), {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("차량 주행").assertIsDisplayed()
        compose.onNodeWithTag("exclude-trip").assertIsDisplayed()
        compose.onNodeWithText("내 주행이 매듭이 됐어요").assertIsDisplayed()
        compose.onNodeWithTag("signature-screen").captureRoboImage("src/test/screenshots/moment_vehicle.png")
    }
    @Test fun disablingMotionDuringRevealImmediatelyShowsTheFinishedStory() {
        val scale = mutableStateOf(1f)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ChayeonTheme(DrivingPolicy.ALLOWED, scale.value) {
                TripRecapScreen(TripRecapUiState(f.driving, f.ready(recap)), previewChrome(showSourceLabels = false), {}, {}, {}, {})
            }
        }
        compose.mainClock.advanceTimeBy(400)
        compose.runOnIdle { scale.value = 0f }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("recap-open-visibility").assertIsEnabled()
        compose.onNodeWithText(TraitId.TIRE_CARE.presentation.publicPhrase).assertIsDisplayed()
        compose.onNodeWithTag("signature-screen").captureRoboImage("src/test/screenshots/moment_motion_cancelled.png")
    }

    @Test fun policyChangeDuringFirstRevealShowsTheFinishedMomentWithEveryActionLocked() {
        val policy = mutableStateOf(DrivingPolicy.ALLOWED)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ChayeonTheme(policy.value, 1f) {
                TripRecapScreen(TripRecapUiState(f.driving.copy(policy = policy.value), f.ready(recap)),
                    previewChrome(policy = policy.value), {}, {}, {}, {})
            }
        }
        compose.mainClock.advanceTimeBy(600)
        compose.runOnIdle { policy.value = DrivingPolicy.UNKNOWN }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("recap-knot", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("이 주행이 매듭이 됐어요", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText(TraitId.TIRE_CARE.presentation.publicPhrase, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("recap-open-visibility", useUnmergedTree = true).assertIsNotEnabled()
        compose.onNodeWithTag("recap-open-signature", useUnmergedTree = true).assertIsNotEnabled()
        compose.onNodeWithTag("exclude-trip", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("signature-screen").captureRoboImage("src/test/screenshots/moment_unknown.png")
        compose.mainClock.autoAdvance = true
        compose.runOnIdle { policy.value = DrivingPolicy.RESTRICTED }
        compose.onNodeWithText("주행 중", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("recap-open-visibility", useUnmergedTree = true).assertIsNotEnabled()
        compose.onNodeWithTag("exclude-trip", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("signature-screen").captureRoboImage("src/test/screenshots/moment_restricted.png")
    }

    @Config(sdk = [35], qualifiers = "w840dp-h1210dp-port-mdpi")
    @Test fun restrictedNarrowMomentCannotBeBrowsedByTouch() {
        compose.setContent {
            ChayeonTheme(DrivingPolicy.RESTRICTED, 1f) {
                TripRecapScreen(TripRecapUiState(f.driving.copy(policy = DrivingPolicy.RESTRICTED), f.ready(recap)),
                    previewChrome(policy = DrivingPolicy.RESTRICTED), {}, {}, {}, {})
            }
        }
        compose.onNodeWithContentDescription("주차하면 첫 시그니처를 이어서 볼 수 있어요.").assertIsNotEnabled()
        compose.onNodeWithTag("recap-open-visibility").assertDoesNotExist()
        val title = compose.onNodeWithTag("first-signature-title", useUnmergedTree = true)
        val before = title.getUnclippedBoundsInRoot()
        compose.onNodeWithTag("signature-screen").performTouchInput { swipeUp() }
        assertEquals(before, title.getUnclippedBoundsInRoot())
        compose.onNodeWithTag("recap-open-visibility", useUnmergedTree = true).assertIsNotEnabled()
        compose.onNodeWithTag("signature-screen").captureRoboImage("src/test/screenshots/moment_restricted_narrow.png")
    }

    @Test fun failedFirstMomentActionKeepsRetryAndExclusionReachable() {
        compose.setContent {
            ChayeonTheme(DrivingPolicy.ALLOWED, 0f) {
                TripRecapScreen(TripRecapUiState(f.driving, f.ready(recap), error = AppError.Network), previewChrome(showSourceLabels = false), {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("다시 시도").assertIsDisplayed()
        compose.onNodeWithTag("exclude-trip").assertDoesNotExist()
        compose.onNodeWithTag("signature-screen").captureRoboImage("src/test/screenshots/moment_command_failure.png")
    }

}
