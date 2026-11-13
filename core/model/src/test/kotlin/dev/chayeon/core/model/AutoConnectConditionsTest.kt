package dev.chayeon.core.model

import kotlinx.serialization.json.Json
import org.junit.Test
import kotlin.test.*

class AutoConnectConditionsTest {
    private fun peer(score: Int? = 80, interests: Set<InterestId> = setOf(InterestId.JAZZ)) = PublicProfile("peer", 1, "별보는밤", AvatarId.STAR,
        interests, ProfileKind.VIRTUAL_PROFILE, PublicSignature("sig", 1, "칭호", listOf(PublicTrait(TraitId.SIGNAL_MANNERS, TraitLevel.STEADY, score)),
            KnotSeed(1), DataSource.SYNTHETIC, EvidenceBucket.ESTABLISHED))

    @Test fun thresholdsMissingPublishedTraitAndSharedInterestsAreAllRequired() {
        val conditions = AutoConnectConditions(mapOf(TraitId.SIGNAL_MANNERS to 80), true)
        assertTrue(conditions.matches(peer(), setOf(InterestId.JAZZ)))
        assertFalse(conditions.matches(peer(79), setOf(InterestId.JAZZ)))
        assertFalse(conditions.matches(peer(null), setOf(InterestId.JAZZ)))
        assertFalse(conditions.matches(peer().copy(signature = null), setOf(InterestId.JAZZ)))
        assertFalse(conditions.matches(peer(), emptySet()))
        assertFalse(conditions.matches(peer(101), setOf(InterestId.JAZZ)))
    }

    @Test fun onlyScoredTraitsAndFivePointThresholdsAreValid() {
        assertTrue(AutoConnectConditions(mapOf(TraitId.BELT_FIRST to 60, TraitId.CABIN_CARE to 100)).isValid())
        for (score in listOf(59, 61, 101)) assertFalse(AutoConnectConditions(mapOf(TraitId.BELT_FIRST to score)).isValid())
        assertFalse(AutoConnectConditions(mapOf(TraitId.NIGHT_DRIVE to 60)).isValid())
        assertFalse(AutoConnectConditions(mapOf(TraitId.LONG_ROAD to 60)).isValid())
    }

    @Test fun legacyOptInMigratesToRingAndNewInstallIsOff() {
        assertEquals(DrivingConnectMode.RING, Json.decodeFromString<ParticipationSettings>("""{"id":"settings","version":1,"receiveRequestsWhileDriving":true}""").drivingConnectMode)
        assertEquals(DrivingConnectMode.OFF, Json.decodeFromString<ParticipationSettings>("""{"id":"settings","version":1}""").drivingConnectMode)
        assertEquals(DrivingConnectMode.AUTO, Json.decodeFromString<ParticipationSettings>("""{"id":"settings","version":1,"receiveRequestsWhileDriving":true,"drivingConnectMode":"AUTO"}""").drivingConnectMode)
    }
}
