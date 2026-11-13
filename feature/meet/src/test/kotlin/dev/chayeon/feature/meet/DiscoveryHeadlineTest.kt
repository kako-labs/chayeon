package dev.chayeon.feature.meet

import dev.chayeon.core.model.*
import org.junit.Assert.assertEquals
import org.junit.Test

class DiscoveryCountTest {
    @Test fun demoOnlyCountCountsEveryCandidate() {
        assertEquals("2명", nearbyCountLabel(List(2) { MeetFixtures.candidate }))
    }

    @Test fun mixedCountCountsEveryCandidate() {
        assertEquals("2명", nearbyCountLabel(listOf(MeetFixtures.candidate,
            MeetFixtures.candidate.copy(profile = MeetFixtures.own))))
    }

    @Test fun participantOnlyCountKeepsItsCount() {
        assertEquals("2명", nearbyCountLabel(List(2) { MeetFixtures.candidate.copy(profile = MeetFixtures.own) }))
    }
}
