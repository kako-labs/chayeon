package dev.chayeon.feature.signature

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import dev.chayeon.core.model.*
import dev.chayeon.core.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w1682dp-h1210dp-land-mdpi")
class DriveProfileInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val f = SignatureScreenFixtures

    @Test fun privateScoreAndPreferencesRemainMineAndEachRowOpensEvidence() {
        var opened: TraitId? = null
        compose.setContent { ChayeonTheme(DrivingPolicy.ALLOWED, 0f) {
            MyProfileScreen(MyProfileUiState(f.driving, f.ready(f.profile), f.ready(f.signature)),
                previewChrome(), {}, {}, {}, {}, onTrait = { opened = it })
        } }
        compose.onNodeWithTag("profile-trait-STEADY_PACE").performScrollTo().performClick()
        assertEquals(TraitId.STEADY_PACE, opened)
        compose.onNodeWithText("54").assertIsDisplayed()
        compose.onNodeWithText("나만 보여요").assertIsDisplayed()
        compose.onNodeWithTag("profile-trait-LONG_ROAD").performScrollTo()
        compose.onAllNodes(hasText("취향은 점수 없이 보여요", substring = true)).assertCountEquals(2)
    }

    @Test fun buildingUsesActualTripCountsAndNeverDisplaysAScore() {
        val building = f.signature.copy(evidence = f.signature.evidence.map { it.copy(validTripCount = 1, score = null, recentTrips = it.recentTrips.take(1)) }, publicSignature = null)
        compose.setContent { ChayeonTheme(DrivingPolicy.ALLOWED, 0f) {
            MyProfileScreen(MyProfileUiState(f.driving, f.ready(f.profile), f.ready(building), settings = f.ready(f.settings)), previewChrome(), {}, {}, {}, {})
        } }
        compose.onNodeWithText("주행 기록 켜짐").assertIsDisplayed()
        compose.onNodeWithText("1 / 3 주행").assertIsDisplayed()
        compose.onAllNodes(hasText("주행 구간 30번 중 24번", substring = true)).assertCountEquals(1)
        compose.onNodeWithText("80").assertDoesNotExist()
    }

    @Test fun chartTapSelectsTripAndExcludedTripDoesNotBecomeABar() {
        compose.setContent { ChayeonTheme(DrivingPolicy.ALLOWED, 0f) {
            TraitDetailScreen(TraitDetailUiState(f.driving, f.evidence.trait, f.ready(f.evidence)), previewChrome(), {}, {})
        } }
        compose.onNodeWithTag("trip-bar-trip-2").assertDoesNotExist()
        compose.onNodeWithTag("trip-bar-trip-8").performClick()
        compose.onNodeWithText("선택한 주행 · ${evidenceDate(f.evidence.recentTrips.last().endedAtEpochMillis)}").assertIsDisplayed()
        compose.onNodeWithTag("signature-screen").captureRoboImage("src/test/screenshots/trait_selected_trip.png")
    }

    @Test fun allModesAndConditionsAreInteractiveOnlyWhileParked() {
        val state = mutableStateOf(MyProfileUiState(driving = f.driving,
            settings = f.ready(ParticipationSettings("settings", 1, drivingConnectMode = DrivingConnectMode.AUTO))))
        var changed: Int? = null
        var selected: DrivingConnectMode? = null
        compose.setContent { ChayeonTheme(state.value.driving.policy, 0f) {
            DriveSettingsScreen(state.value, previewChrome(policy = state.value.driving.policy), {}, { selected = it },
                { _, score -> changed = score }, {}, {})
        } }
        compose.onNodeWithText("전화처럼 받기").performClick()
        assertEquals(DrivingConnectMode.RING, selected)
        compose.onNodeWithText("부드러운 주행 조건 사용").performClick()
        assertEquals(60, changed)
        compose.runOnIdle { state.value = state.value.copy(driving = f.driving.copy(policy = DrivingPolicy.RESTRICTED)) }
        compose.onNodeWithText("주행 중이에요").assertIsDisplayed()
        compose.onNodeWithText("전화처럼 받기").assertDoesNotExist()
        compose.onNodeWithText("부드러운 주행 조건 사용").assertDoesNotExist()
    }
}
