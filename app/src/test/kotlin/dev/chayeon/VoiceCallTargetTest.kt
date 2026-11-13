package dev.chayeon

import dev.chayeon.core.model.*
import org.junit.Assert.*
import org.junit.Test

class VoiceCallTargetTest {
    private val profile = PublicProfile("peer", 1, "별보는밤", AvatarId.STAR, emptySet(), ProfileKind.DEMO_PEER, null)
    private val connection = Connection("call", 1, "request", profile, ConnectionStatus.CONNECTED, 0, Chemistry())
    private val session = SessionState.Active(Session("session", 1, "account", SessionMode.SERVER_DEMO,
        PersonaCode.ACCOUNT_A, "room", null, null, ParticipationPolicy("policy", 1, 5, 2, 12, null, "1")))

    @Test fun passesDemoIdentityOnlyWhileAudioTargetIsVisible() {
        assertEquals(profile, target(connection).peer)
        assertEquals(ProfileKind.DEMO_PEER, target(connection).peer?.kind)
        val real = profile.copy(kind = ProfileKind.VIRTUAL_PROFILE)
        assertEquals(real, target(connection.copy(peer = real)).peer)
        assertNull(target(connection, home = HomeZoneState(HomeZoneStatus.INSIDE)).peer)
        assertNull(target(connection.copy(status = ConnectionStatus.ENDED)).peer)
    }

    @Test fun everyEndReasonAndCheckingRemoveTheAudioTarget() {
        assertEquals("call", target(connection).connectionId)
        ConnectionEndReason.entries.forEach { reason ->
            assertNull(target(connection.copy(status = ConnectionStatus.ENDED, endReason = reason)).connectionId)
        }
        assertNull(target(connection.copy(status = ConnectionStatus.CHECKING)).connectionId)
        assertEquals("call", target(connection).connectionId)
    }

    @Test fun protectionAndSessionCleanupCloseBeforeNetworkAcknowledgement() {
        HomeZoneStatus.entries.filter { HomeZoneState(it).suppressed }.forEach { status ->
            assertNull(target(connection, home = HomeZoneState(status)).connectionId)
        }
        assertNull(target(connection, ending = true).connectionId)
        assertNull(target(connection, leaving = "call").connectionId)
        assertNull(voiceCallTarget(SessionState.SignedOut(), LoadState.Ready(connection, 1), HomeZoneState(), false, null).connectionId)
        assertNull(voiceCallTarget(session, LoadState.Failed(AppError.Network), HomeZoneState(), false, null).connectionId)
        assertNull(voiceCallTarget(session, LoadState.Loading(), HomeZoneState(), false, null).connectionId)
    }

    @Test fun briefHomeProtectionKeepsAudioClosedUntilParkingEvenIfConnectionSurvives() {
        val protection = VoiceHomeProtection()
        val inside = HomeZoneState(HomeZoneStatus.INSIDE)
        assertFalse(protection.update(session, HomeZoneState(), DrivingPolicy.RESTRICTED))
        assertTrue(protection.update(session, inside, DrivingPolicy.RESTRICTED))
        val hidden = protection.update(session, HomeZoneState(), DrivingPolicy.RESTRICTED)
        assertTrue(hidden)
        assertNull(voiceCallTarget(session, LoadState.Ready(connection, 1), HomeZoneState(), false, null, hidden).connectionId)
        assertTrue(protection.update(session, HomeZoneState(), DrivingPolicy.UNKNOWN))
        assertFalse(protection.update(session, HomeZoneState(), DrivingPolicy.ALLOWED))
        assertEquals("call", target(connection).connectionId)
        protection.update(session, inside, DrivingPolicy.RESTRICTED)
        assertFalse(protection.update(SessionState.SignedOut(), HomeZoneState(), DrivingPolicy.RESTRICTED))
    }

    @Test fun aNewAutomaticCallOutsideHomeDoesNotInheritTheEndedCallsProtectionLatch() {
        val protection = VoiceHomeProtection()
        assertFalse(protection.update(session, HomeZoneState(), DrivingPolicy.RESTRICTED, "old"))
        assertTrue(protection.update(session, HomeZoneState(HomeZoneStatus.INSIDE), DrivingPolicy.RESTRICTED, "old"))
        assertTrue(protection.update(session, HomeZoneState(), DrivingPolicy.RESTRICTED, "old"))
        assertTrue(protection.update(session, HomeZoneState(), DrivingPolicy.RESTRICTED, null))
        assertTrue(protection.update(session, HomeZoneState(), DrivingPolicy.RESTRICTED, "old"))
        assertFalse(protection.update(session, HomeZoneState(), DrivingPolicy.RESTRICTED, "new"))
        assertTrue(protection.update(session, HomeZoneState(HomeZoneStatus.INSIDE), DrivingPolicy.RESTRICTED, "another"))
    }

    private fun target(value: Connection, home: HomeZoneState = HomeZoneState(), ending: Boolean = false, leaving: String? = null) =
        voiceCallTarget(session, LoadState.Ready(value, 1), home, ending, leaving)
}
