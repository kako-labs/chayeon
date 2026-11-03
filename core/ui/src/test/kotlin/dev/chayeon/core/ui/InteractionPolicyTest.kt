package dev.chayeon.core.ui

import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import dev.chayeon.core.model.DrivingPolicy
import dev.chayeon.core.model.KnotSeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1682dp-h1210dp-land-mdpi")
class InteractionPolicyTest {
    @get:Rule val compose = createComposeRule()

    @Test fun drivingBlocksActionsButAllowsExplicitExit() {
        var actions = 0
        var exits = 0
        val policy = mutableStateOf(DrivingPolicy.UNKNOWN)
        compose.setContent {
            ChayeonTheme(policy.value) {
                Column {
                    JellyButton("요청 보내기", { actions++ })
                    JellyButton("나가기", { exits++ }, allowWhenRestricted = true)
                }
            }
        }
        compose.onNodeWithText("요청 보내기").assertIsNotEnabled().performClick()
        compose.onNodeWithText("나가기").performClick()
        compose.runOnIdle { policy.value = DrivingPolicy.RESTRICTED }
        compose.onNodeWithText("요청 보내기").assertIsNotEnabled().performClick()
        compose.runOnIdle { policy.value = DrivingPolicy.ALLOWED }
        compose.onNodeWithText("요청 보내기").performClick()
        assertEquals(1, actions)
        assertEquals(1, exits)
    }

    @Test fun togglesExposeCheckedState() {
        val checked = mutableStateOf(false)
        compose.setContent { ChayeonTheme(DrivingPolicy.ALLOWED, 0f) { GelToggle(checked.value, { checked.value = it }, "발견 켜기") } }
        compose.onNodeWithText("발견 켜기").assertIsOff().performClick()
        compose.onNodeWithText("발견 켜기").assertIsOn()
    }

    @Test fun motionRequiresKnownParkedStateAndEnabledSystemAnimator() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val policy = mutableStateOf(DrivingPolicy.ALLOWED)
        var allowed = true
        compose.setContent { ChayeonTheme(policy.value) { allowed = LocalDecorativeMotion.current } }
        compose.waitForIdle()
        assertFalse(allowed)
        compose.runOnIdle { Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }
        compose.waitForIdle()
        assertTrue(allowed)
        compose.runOnIdle { policy.value = DrivingPolicy.UNKNOWN }
        compose.waitForIdle()
        assertFalse(allowed)
        compose.runOnIdle { policy.value = DrivingPolicy.RESTRICTED }
        compose.waitForIdle()
        assertFalse(allowed)
    }

    @Test fun knotsAreDeterministicClosedAndDistinct() {
        val a = knotGeometry(KnotSeed(2357933L))
        val b = knotGeometry(KnotSeed(8733462L))
        assertEquals(a, knotGeometry(KnotSeed(2357933L)))
        assertNotEquals(a, b)
        assertEquals(a.first().x, a.last().x, .00001f)
        assertEquals(a.first().y, a.last().y, .00001f)
        assertTrue(a.all { it.x.isFinite() && it.y.isFinite() && it.depth.isFinite() })
        assertTrue(a.any { it.depth > 0f } && a.any { it.depth < 0f })
    }
}
