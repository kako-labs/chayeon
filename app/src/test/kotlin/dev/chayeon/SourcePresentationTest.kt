package dev.chayeon

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import dev.chayeon.core.model.*
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
class SourcePresentationTest {
    @get:Rule val compose = createComposeRule()

    private fun assertSoloSurface() {
        listOf("시연용 가상 상대", "합성 주행", "합성 위치", "가상 프로필", "가상 집").forEach {
            compose.onAllNodes(hasText(it, substring = true)).assertCountEquals(0)
            compose.onAllNodes(hasContentDescription(it, substring = true)).assertCountEquals(0)
        }
    }

    @Test fun activeSessionControlsHomeCandidateAndConnectionLabels() {
        val fixture = TestGraph().apply { ready() }
        val signature = PublicSignature("signature", 1, "밤길을 좋아하는 드라이버",
            listOf(PublicTrait(TraitId.NIGHT_DRIVE, TraitLevel.STEADY)), KnotSeed(42), DataSource.SYNTHETIC, EvidenceBucket.ESTABLISHED)
        val peer = fixture.publicProfile.copy(id = "peer", nickname = "별보는밤", avatar = AvatarId.STAR,
            kind = ProfileKind.DEMO_PEER, signature = signature)
        fixture.discovery.value = DiscoveryState(mode = DiscoveryMode.ON, status = DiscoveryStatus.AVAILABLE,
            candidates = listOf(Candidate("candidate", 1, peer, Chemistry(), emptySet())), locationSource = LocationSource.SYNTHETIC)
        compose.setContent { ChayeonApp(fixture.graph) }
        compose.onNodeWithText("근처에 인연이 있어요").assertExists()
        assertSoloSurface()
        val button = compose.onNodeWithContentDescription("시연 제어").assertHasClickAction().getUnclippedBoundsInRoot()
        assertTrue(button.right - button.left >= 76.dp && button.bottom - button.top >= 76.dp)
        compose.onNodeWithText("시연 제어").assertDoesNotExist()
        compose.onRoot().captureRoboImage("src/test/screenshots/app-solo-home.png")
        compose.runOnIdle { fixture.sessions.value = SessionState.Active(fixture.active.copy(mode = SessionMode.SERVER_DEMO)) }
        compose.onNodeWithText("시연용 가상 상대 · 합성 주행").assertExists()
        compose.onNodeWithText("합성 위치").assertExists()
        compose.onNodeWithText("근처에 인연이 있어요").assertExists()
        compose.onRoot().captureRoboImage("src/test/screenshots/app-server-home.png")
        compose.runOnIdle { fixture.sessions.value = SessionState.Active(fixture.active) }
        compose.onNodeWithContentDescription("프로필 보기").performScrollTo().performClick()
        compose.onAllNodesWithText("별보는밤").assertCountEquals(2)
        assertSoloSurface()
        compose.onRoot().captureRoboImage("src/test/screenshots/app-solo-candidate.png")
        compose.runOnIdle {
            fixture.connection.value = LoadState.Ready(Connection("connection", 1, "request", peer,
                ConnectionStatus.CONNECTED, 0, Chemistry()), 1)
        }
        compose.onNodeWithText("주고받은 인사").assertDoesNotExist()
        compose.onNodeWithText("통화 중").assertExists()
        compose.onNodeWithText("통화 끊기").assertExists()
        assertSoloSurface()
        compose.onRoot().captureRoboImage("src/test/screenshots/app-solo-connected.png")
        compose.runOnIdle { fixture.sessions.value = SessionState.Active(fixture.active.copy(mode = SessionMode.SERVER_DEMO)) }
        compose.onNodeWithText("시연용 가상 상대 · 합성 주행").assertExists()
        compose.onRoot().captureRoboImage("src/test/screenshots/app-server-connected.png")
    }
}
