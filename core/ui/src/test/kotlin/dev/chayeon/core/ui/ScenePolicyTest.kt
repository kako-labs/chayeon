package dev.chayeon.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.chayeon.core.model.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1682dp-h1210dp-land-mdpi")
class ScenePolicyTest {
    @get:Rule val compose = createComposeRule()

    @Test fun callControlsRespectUnknownAndParkedOnlyActions() {
        val policy = mutableStateOf(DrivingPolicy.UNKNOWN)
        var sends = 0
        var calls = 0
        var exits = 0
        compose.setContent { ChayeonTheme(policy.value, 0f) { Column {
            PrimaryButton("요청", { sends++ })
            OutlineButton("음소거", { calls++ }, access = ButtonAccess.RESTRICTED_CALL)
            PrimaryButton("통화 끊기", { exits++ }, access = ButtonAccess.SAFE_EXIT)
        } } }
        compose.onNodeWithText("요청").assertIsNotEnabled().performClick()
        compose.onNodeWithText("음소거").assertIsNotEnabled().performClick()
        compose.onNodeWithText("통화 끊기").performClick()
        compose.runOnIdle { policy.value = DrivingPolicy.RESTRICTED }
        compose.onNodeWithText("요청").assertIsNotEnabled().performClick()
        compose.onNodeWithText("음소거").performClick()
        compose.runOnIdle { policy.value = DrivingPolicy.ALLOWED }
        compose.onNodeWithText("요청").performClick()
        assertEquals(1, sends); assertEquals(1, calls); assertEquals(1, exits)
    }

    @Test fun chromeBackPreservesScreenCallbackLockAndDisposal() {
        val registry = ChromeBackRegistry()
        val busy = mutableStateOf(true)
        val visible = mutableStateOf(true)
        val target = mutableStateOf(1)
        var opened = 0
        compose.setContent { ChayeonTheme(DrivingPolicy.ALLOWED, 0f) {
            CompositionLocalProvider(LocalChromeBackRegistry provides registry) {
                Column {
                    registry.action?.let { BackButton(it.onClick, enabled = it.enabled, inChrome = true) }
                    if (visible.value) {
                        val currentTarget = target.value
                        BackButton({ opened = currentTarget }, enabled = !busy.value)
                    }
                }
            }
        } }
        compose.onAllNodesWithContentDescription("뒤로").assertCountEquals(1)
        compose.onNodeWithContentDescription("뒤로").assertIsNotEnabled().performClick()
        assertEquals(0, opened)
        compose.runOnIdle { busy.value = false; target.value = 2 }
        compose.onNodeWithContentDescription("뒤로").performClick()
        assertEquals(2, opened)
        compose.runOnIdle { visible.value = false }
        compose.onNodeWithContentDescription("뒤로").assertDoesNotExist()
    }

    @Test fun drivingShowsOnlyTwoPublishedScoresAndNoNavigationCounts() {
        val policy = mutableStateOf(DrivingPolicy.ALLOWED)
        val signature = gallerySignature.copy(traits = listOf(
            PublicTrait(TraitId.EARLY_BRAKE, TraitLevel.SIGNATURE, 94),
            PublicTrait(TraitId.SIGNAL_MANNERS, TraitLevel.STEADY, 82),
            PublicTrait(TraitId.BELT_FIRST, TraitLevel.STEADY, 80),
            PublicTrait(TraitId.GENTLE_RIDE, TraitLevel.VISIBLE, 59),
            PublicTrait(TraitId.NIGHT_DRIVE, TraitLevel.SIGNATURE, 99),
        ))
        compose.setContent { ChayeonTheme(policy.value, 0f) { Column {
            PublishedScoreChips(signature)
            BottomNav(NavigationItem.RELATIONSHIPS, true, {}, {}, incomingAfterCount = 3)
        } } }
        compose.onNodeWithText("벨트 먼저 80").assertExists()
        compose.onNodeWithText("부드러운 주행 59").assertDoesNotExist()
        compose.onNodeWithContentDescription("새 애프터 요청 3개", useUnmergedTree = true).assertExists()
        compose.runOnIdle { policy.value = DrivingPolicy.RESTRICTED }
        compose.onNodeWithText("여유로운 제동 94").assertExists()
        compose.onNodeWithText("깜빡이 매너 82").assertExists()
        compose.onNodeWithText("벨트 먼저 80").assertDoesNotExist()
        compose.onNodeWithText("홈").assertIsNotEnabled()
        compose.onNodeWithContentDescription("새 애프터 요청 3개", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("발견 끄기").assertDoesNotExist()
    }
}
