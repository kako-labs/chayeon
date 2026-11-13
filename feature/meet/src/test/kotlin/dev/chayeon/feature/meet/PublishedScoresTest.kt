package dev.chayeon.feature.meet

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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
class PublishedScoresTest {
    @get:Rule val compose = createComposeRule()

    @Test fun publishedScoresUseScoreThenStableTraitOrderAndNeverPreferenceBadges() {
        val traits = listOf(
            PublicTrait(TraitId.NIGHT_DRIVE, TraitLevel.SIGNATURE),
            PublicTrait(TraitId.LONG_ROAD, TraitLevel.SIGNATURE, 100),
            PublicTrait(TraitId.GENTLE_RIDE, TraitLevel.VISIBLE, 59),
            PublicTrait(TraitId.BELT_FIRST, TraitLevel.SIGNATURE, 101),
            PublicTrait(TraitId.TIRE_CARE, TraitLevel.SIGNATURE, 94),
            PublicTrait(TraitId.SIGNAL_MANNERS, TraitLevel.SIGNATURE, 94),
            PublicTrait(TraitId.EARLY_BRAKE, TraitLevel.STEADY, 82),
            PublicTrait(TraitId.CABIN_CARE, TraitLevel.VISIBLE, 65),
        )
        val signature = MeetFixtures.signature.copy(traits = traits)
        val expected = listOf(TraitId.SIGNAL_MANNERS, TraitId.TIRE_CARE, TraitId.EARLY_BRAKE)
        assertEquals(expected, topPublishedScoredTraits(signature).map { it.trait })
        assertEquals(expected, topPublishedScoredTraits(signature.copy(traits = traits.reversed())).map { it.trait })
        assertEquals(expected.take(2), topPublishedScoredTraits(signature, 2).map { it.trait })
    }

    @Test fun homeCandidateShowsThreePublishedScores() {
        compose.setContent { ChayeonTheme(DrivingPolicy.ALLOWED, 0f) {
            HomeScreen(HomeUiState(MeetFixtures.parked, MeetFixtures.discovery, MeetFixtures.own, profileLoaded = true),
                previewChrome(), {}, {}, {}, {}, {}, {}, {})
        } }
        assertThreeScores()
    }

    @Test fun candidateShowsThreePublishedScores() {
        compose.setContent { ChayeonTheme(DrivingPolicy.ALLOWED, 0f) {
            CandidateScreen(CandidateUiState(MeetFixtures.parked, MeetFixtures.candidate, MeetFixtures.own), previewChrome(), {}, {}, {})
        } }
        assertThreeScores()
    }

    @Test @Config(sdk = [35], qualifiers = "w840dp-h1210dp-port-mdpi")
    fun incomingAndSentRequestsShowThreePublishedScores() {
        val state = androidx.compose.runtime.mutableStateOf(RequestUiState(MeetFixtures.parked, MeetFixtures.request))
        compose.setContent { ChayeonTheme(DrivingPolicy.ALLOWED, 0f) {
            RequestScreen(state.value, previewChrome(), {}, {}, {}, {}, {})
        } }
        assertThreeScores()
        compose.onNodeWithText("여유로운 제동 94").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(request = MeetFixtures.request.copy(direction = RequestDirection.OUTGOING)) }
        assertThreeScores()
    }

    @Test fun parkedCallShowsPartnerDrivingAboveTopics() {
        compose.setContent { ChayeonTheme(DrivingPolicy.ALLOWED, 0f) {
            ConnectedScreen(ConnectedUiState(MeetFixtures.parked, MeetFixtures.connection, MeetFixtures.own), previewChrome(), {}, {})
        } }
        compose.onNodeWithText("상대의 운전").assertExists()
        assertThreeScores()
        val scores = compose.onNodeWithText("상대의 운전").getUnclippedBoundsInRoot()
        val topics = compose.onNodeWithText("이야깃거리").getUnclippedBoundsInRoot()
        org.junit.Assert.assertTrue(scores.top < topics.top)
    }

    @Test @Config(sdk = [35], qualifiers = "w840dp-h1210dp-port-mdpi")
    fun drivingCallHasOnlyTwoStaticScoresAndTitle() {
        val driving = MeetFixtures.parked.copy(policy = DrivingPolicy.RESTRICTED, gear = Gear.DRIVE)
        compose.setContent { ChayeonTheme(DrivingPolicy.RESTRICTED, 0f) {
            ConnectedScreen(ConnectedUiState(driving, MeetFixtures.connection, MeetFixtures.own), previewChrome(policy = DrivingPolicy.RESTRICTED), {}, {})
        } }
        compose.onNodeWithText(MeetFixtures.signature.title).assertExists()
        compose.onNodeWithText("여유로운 제동 94").assertIsDisplayed()
        compose.onNodeWithText("깜빡이 매너 82").assertIsDisplayed()
        compose.onNodeWithText("타이어 관리 65").assertDoesNotExist()
        compose.onNodeWithText("이야깃거리").assertDoesNotExist()
        compose.onAllNodes(hasClickAction() and isEnabled()).assertCountEquals(2)
    }

    @Test fun drivingIncomingHasOnlyTwoScores() {
        val driving = MeetFixtures.parked.copy(policy = DrivingPolicy.RESTRICTED, gear = Gear.DRIVE)
        compose.setContent { ChayeonTheme(DrivingPolicy.RESTRICTED, 0f) {
            RequestScreen(RequestUiState(driving, MeetFixtures.request, receiveRequestsWhileDriving = true), previewChrome(policy = DrivingPolicy.RESTRICTED), {}, {}, {}, {}, {})
        } }
        compose.onNodeWithText("여유로운 제동 94").assertIsDisplayed()
        compose.onNodeWithText("깜빡이 매너 82").assertIsDisplayed()
        compose.onNodeWithText("타이어 관리 65").assertDoesNotExist()
        compose.onAllNodes(hasClickAction() and isEnabled()).assertCountEquals(2)
    }

    private fun assertThreeScores() {
        compose.onNodeWithText("여유로운 제동 94").assertExists()
        compose.onNodeWithText("깜빡이 매너 82").assertExists()
        compose.onNodeWithText("타이어 관리 65").assertExists()
    }
}
