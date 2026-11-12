package dev.chayeon

import dev.chayeon.core.model.*

internal data class SessionConsentValues(
    val accountId: String? = null,
    val vehicleGranted: Boolean = false,
    val locationGranted: Boolean? = null,
)

internal class SessionConsent {
    private data class Key(val accountId: String, val sessionId: String, val documentVersion: String)
    private var key: Key? = null
    private var values = SessionConsentValues()

    @Synchronized
    fun clear(): SessionConsentValues {
        key = null
        values = SessionConsentValues()
        return values
    }

    @Synchronized
    fun update(session: SessionState, settings: LoadState<ParticipationSettings>, ending: Boolean): SessionConsentValues {
        val active = (session as? SessionState.Active)?.session?.takeUnless { ending } ?: return clear()
        val current = Key(active.accountId, active.id, active.policy.consentDocumentVersion)
        if (current != key) {
            key = current
            values = SessionConsentValues(accountId = active.accountId)
        }
        if (settings is LoadState.Ready) {
            fun granted(scope: ConsentScope) = settings.value.consents.any {
                it.scope == scope && it.status == ConsentStatus.GRANTED && it.documentVersion == current.documentVersion
            }
            values = SessionConsentValues(active.accountId, granted(ConsentScope.VEHICLE_DATA), granted(ConsentScope.LOCATION_USE))
        }
        return values
    }
}
