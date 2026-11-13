package dev.chayeon.feature.meet

import dev.chayeon.core.model.*
import org.junit.Assert.*
import org.junit.Test

class CallTopicsTest {
    @Test fun topicsUseOnlySharedInterestsAndExistingPublicPhrasesAndTitles() {
        val state = ConnectedUiState(connection = MeetFixtures.connection, ownProfile = MeetFixtures.own)
        val topics = state.callTopics()
        assertEquals(setOf(InterestId.JAZZ, InterestId.NIGHT_VIEW), topics.sharedInterests)
        assertFalse(InterestId.EXHIBITIONS in topics.sharedInterests)
        assertEquals(MeetFixtures.connection.chemistry.lines.map { it.text }, topics.chemistryLines)
        assertEquals(MeetFixtures.own.signature?.title, topics.ownTitle)
        assertEquals(MeetFixtures.peer.signature?.title, topics.peerTitle)
    }

    @Test fun unpublishedSignaturesAndUnsharedInterestsDoNotInventTopics() {
        val state = ConnectedUiState(
            ownProfile = MeetFixtures.own.copy(interests = setOf(InterestId.JAZZ), signature = null),
            connection = MeetFixtures.connection.copy(peer = MeetFixtures.peer.copy(interests = setOf(InterestId.COFFEE), signature = null), chemistry = Chemistry()),
        )
        assertTrue(state.callTopics().isEmpty)
    }

    @Test fun missingOwnProfileCannotCreateSharedInterestsOrOwnTitle() {
        val topics = ConnectedUiState(connection = MeetFixtures.connection).callTopics()
        assertTrue(topics.sharedInterests.isEmpty())
        assertNull(topics.ownTitle)
        assertEquals(MeetFixtures.peer.signature?.title, topics.peerTitle)
    }
}
