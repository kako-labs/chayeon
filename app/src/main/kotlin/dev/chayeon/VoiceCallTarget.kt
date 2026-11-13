package dev.chayeon

import dev.chayeon.core.model.*

internal data class VoiceCallTarget(val connectionId: String?, val mode: SessionMode, val peer: PublicProfile? = null)

/** Only a currently confirmed relationship may own device audio resources. */
internal fun voiceCallTarget(
    session: SessionState,
    observation: LoadState<Connection?>,
    home: HomeZoneState,
    sessionEnding: Boolean,
    leavingConnectionId: String?,
    protectedWhileDriving: Boolean = false,
): VoiceCallTarget {
    val active = (session as? SessionState.Active)?.session
    val connection = (observation as? LoadState.Ready)?.value
    val id = connection?.takeIf { active != null && !sessionEnding && !home.suppressed && !protectedWhileDriving &&
        it.status == ConnectionStatus.CONNECTED && it.id != leavingConnectionId }?.id
    return VoiceCallTarget(id, active?.mode ?: SessionMode.SOLO, connection?.peer.takeIf { id != null })
}

internal class VoiceHomeProtection {
    private var sessionId: String? = null
    private var hidden = false
    private var connectionId: String? = null

    fun update(session: SessionState, home: HomeZoneState, policy: DrivingPolicy, activeConnectionId: String? = null): Boolean {
        val next = (session as? SessionState.Active)?.session?.id
        if (sessionId != next || policy == DrivingPolicy.ALLOWED ||
            (activeConnectionId != null && activeConnectionId != connectionId && !home.suppressed)) hidden = false
        if (sessionId != next) connectionId = null
        if (activeConnectionId != null) connectionId = activeConnectionId
        sessionId = next
        if (next != null && policy != DrivingPolicy.ALLOWED && home.suppressed) hidden = true
        return hidden
    }
}
