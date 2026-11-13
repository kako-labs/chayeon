package dev.chayeon

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.chayeon.core.model.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1682dp-h1210dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VoiceNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun optedInIncomingInterruptsAnotherFeatureOnlyWhileRestricted() {
        val fixture = TestGraph().apply { ready() }
        compose.setContent { ChayeonApp(fixture.graph) }
        compose.onNodeWithText("내 프로필").performClick()
        compose.runOnIdle {
            fixture.driving.value = DrivingState(policy = DrivingPolicy.RESTRICTED, gear = Gear.DRIVE)
            fixture.requests.value = LoadState.Ready(listOf(ConnectionRequest("incoming", 1,
                fixture.publicProfile.copy(id = "peer", nickname = "별보는밤"), RequestDirection.INCOMING,
                ConnectionRequestStatus.PENDING, 0, 100_000, Chemistry())), 1)
        }
        compose.onNodeWithText("받기").assertDoesNotExist()
        compose.runOnIdle {
            fixture.settings.value = LoadState.Ready(ParticipationSettings("settings", 1, receiveRequestsWhileDriving = true), 1)
        }
        compose.onNodeWithText("받기").assertIsEnabled()
        compose.onNodeWithText("거절").assertIsEnabled()
        compose.onNodeWithText("별보는밤").assertIsDisplayed()
        compose.runOnIdle { fixture.driving.value = DrivingState(policy = DrivingPolicy.UNKNOWN) }
        compose.onNodeWithText("받기").assertDoesNotExist()
    }
}
