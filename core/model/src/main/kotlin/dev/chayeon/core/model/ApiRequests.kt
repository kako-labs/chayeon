package dev.chayeon.core.model

import kotlinx.serialization.Serializable

/** Encoded as query parameters, never as a GET body. */
@Serializable
data class SyncRequest(val since: Long = 0)

/** Persona codes select demo credentials; the server issues the actor identity in a bearer token. */
@Serializable
data class EnterSessionRequest(
    val meta: CommandMeta,
    val mode: SessionMode,
    val persona: PersonaCode,
    val roomCode: String? = null,
    val clientSecret: String? = null,
) {
    override fun toString(): String = "EnterSessionRequest(redacted)"
}

@Serializable
data class SignOutRequest(val meta: CommandMeta)

@Serializable
data class DeleteAccountRequest(val meta: CommandMeta)

@Serializable
data class SaveProfileRequest(val meta: CommandMeta, val draft: ProfileDraft)

/** Recording consent is a command; request acceptance reads the stored consent record. */
@Serializable
data class RecordConsentRequest(
    val meta: CommandMeta,
    val scope: ConsentScope,
    val action: ConsentAction,
    val documentVersion: String,
)

@Serializable
data class UpdateSettingsRequest(
    val meta: CommandMeta,
    val notifyRequests: Boolean,
    val receiveRequestsWhileDriving: Boolean = false,
)

@Serializable
data class PublishSignatureRequest(val meta: CommandMeta, val signature: PublicSignature?)

@Serializable
data class SetDiscoveryRequest(val meta: CommandMeta, val mode: DiscoveryMode)

@Serializable
data class RefreshDiscoveryRequest(val meta: CommandMeta)

/** Request-only, ephemeral proximity input. Never echo this object in responses or diagnostics. */
@Serializable
class LocationInput(
    val latitude: Double,
    val longitude: Double,
    val observedAtEpochMillis: Long,
    val source: LocationSource,
) {
    override fun toString(): String = "LocationInput(redacted)"
}

@Serializable
data class UpdateLocationRequest(val meta: CommandMeta, val location: LocationInput) {
    override fun toString(): String = "UpdateLocationRequest(redacted)"
}

@Serializable
enum class SyntheticPresence { NEARBY, AWAY, STALE, OFFLINE }

@Serializable
data class SetSyntheticPresenceRequest(val meta: CommandMeta, val presence: SyntheticPresence)

@Serializable
data class RequestConnectionRequest(val meta: CommandMeta, val candidateId: String)

@Serializable
data class RespondConnectionRequest(
    val meta: CommandMeta,
    val requestId: String,
    val decision: RequestDecision,
)

@Serializable
data class CancelConnectionRequest(val meta: CommandMeta, val requestId: String)

@Serializable
data class LeaveConnectionRequest(val meta: CommandMeta, val connectionId: String)

@Serializable
data class DeleteEncounterRequest(val meta: CommandMeta, val encounterId: String)

@Serializable
data class DeleteAllEncountersRequest(val meta: CommandMeta)

@Serializable
data class RequestAfterRequest(val meta: CommandMeta, val encounterId: String, val contacts: ContactIds, val saveToProfile: Boolean = false)

@Serializable
data class RespondAfterRequest(val meta: CommandMeta, val requestId: String, val decision: RequestDecision, val contacts: ContactIds = ContactIds(), val saveToProfile: Boolean = false)

@Serializable
data class WithdrawAfterRequest(val meta: CommandMeta, val requestId: String)

@Serializable
data class EndAfterConnectionRequest(val meta: CommandMeta, val afterConnectionId: String)

@Serializable
data class BlockPeerRequest(val meta: CommandMeta, val peerProfileId: String)

@Serializable
data class UnblockPeerRequest(val meta: CommandMeta, val blockId: String)

@Serializable
data class ReportPeerRequest(
    val meta: CommandMeta,
    val peerProfileId: String,
    val reason: ReportReason,
    val context: ReportContext? = null,
)

@Serializable
data class ReportContext(val kind: ReportContextKind, val entityId: String)

@Serializable
enum class ReportContextKind { CONNECTION, ENCOUNTER, AFTER_CONNECTION }
