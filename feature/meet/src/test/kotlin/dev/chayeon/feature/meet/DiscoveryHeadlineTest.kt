package dev.chayeon.feature.meet

import dev.chayeon.core.model.*
import org.junit.Assert.assertEquals
import org.junit.Test

class DiscoveryHeadlineTest {
    @Test fun demoOnlyHeadlineCountsEveryCandidate() {
        assertEquals("근처에서 2명이 발견을 켰어요", discoveryHeadline(List(2) { MeetFixtures.candidate }))
    }

    @Test fun mixedHeadlineCountsEveryCandidate() {
        assertEquals("근처에서 2명이 발견을 켰어요", discoveryHeadline(listOf(MeetFixtures.candidate,
            MeetFixtures.candidate.copy(profile = MeetFixtures.own))))
    }

    @Test fun participantOnlyHeadlineKeepsItsCount() {
        assertEquals("근처에서 2명이 발견을 켰어요", discoveryHeadline(List(2) { MeetFixtures.candidate.copy(profile = MeetFixtures.own) }))
    }
}
