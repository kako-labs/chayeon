package dev.chayeon

import dev.chayeon.core.model.*
import org.junit.Assert.*
import org.junit.Test

class SessionConsentTest {
    private val policy = ParticipationPolicy("policy", 1, 5, 2, 12, null, "current")
    private val active = SessionState.Active(Session("session", 1, "account", SessionMode.SERVER_DEMO,
        null, null, null, policy))
    private val failed = LoadState.Failed(AppError.Network)
    private fun settings(status: ConsentStatus, document: String = "current") = LoadState.Ready(ParticipationSettings("settings", 1,
        listOf(ConsentScope.VEHICLE_DATA, ConsentScope.LOCATION_USE).map {
            ConsentRecord(it.name, 1, it, status, document, 0)
        }), 1)

    @Test fun readyGrantedSurvivesFailureAndLoadingInSameSession() {
        val cache = SessionConsent()
        val granted = cache.update(active, settings(ConsentStatus.GRANTED), false)
        assertEquals(SessionConsentValues("account", true, true), granted)
        assertEquals(granted, cache.update(active, failed, false))
        assertEquals(granted, cache.update(active, LoadState.Loading(), false))
    }

    @Test fun readyRevokedAfterFailureClosesBothGates() {
        val cache = SessionConsent()
        cache.update(active, settings(ConsentStatus.GRANTED), false)
        cache.update(active, failed, false)
        assertEquals(SessionConsentValues("account", false, false), cache.update(active, settings(ConsentStatus.REVOKED), false))
        assertEquals(SessionConsentValues("account", false, false), cache.update(active, failed, false))
    }

    @Test fun sessionEndClearsAndSameAccountReentryStartsClosed() {
        val cache = SessionConsent()
        cache.update(active, settings(ConsentStatus.GRANTED), false)
        assertEquals(SessionConsentValues(), cache.update(SessionState.SignedOut(), failed, false))
        assertEquals(SessionConsentValues("account"), cache.update(active, failed, false))
    }

    @Test fun accountSwitchNeverCarriesConsentOver() {
        val cache = SessionConsent()
        cache.update(active, settings(ConsentStatus.GRANTED), false)
        val next = SessionState.Active(active.session.copy(accountId = "other"))
        assertEquals(SessionConsentValues("other"), cache.update(next, failed, false))
    }

    @Test fun newSessionForSameAccountStartsClosed() {
        val cache = SessionConsent()
        cache.update(active, settings(ConsentStatus.GRANTED), false)
        val next = SessionState.Active(active.session.copy(id = "next"))
        assertEquals(SessionConsentValues("account"), cache.update(next, LoadState.Loading(), false))
    }

    @Test fun firstFailureStaysClosed() {
        assertEquals(SessionConsentValues("account"), SessionConsent().update(active, failed, false))
    }

    @Test fun sessionEndingClosesEvenWithReadyGranted() {
        val cache = SessionConsent()
        cache.update(active, settings(ConsentStatus.GRANTED), false)
        assertEquals(SessionConsentValues(), cache.update(active, settings(ConsentStatus.GRANTED), true))
        assertEquals(SessionConsentValues("account"), cache.update(active, failed, false))
    }

    @Test fun clearThenStaleReadyWhileEndingStaysEmpty() {
        val cache = SessionConsent()
        cache.update(active, settings(ConsentStatus.GRANTED), false)
        assertEquals(SessionConsentValues(), cache.clear())
        assertEquals(SessionConsentValues(), cache.update(active, settings(ConsentStatus.GRANTED), true))
    }

    @Test fun outdatedDocumentDoesNotGrantConsent() {
        assertEquals(SessionConsentValues("account", false, false), SessionConsent().update(active, settings(ConsentStatus.GRANTED, "old"), false))
    }
}
