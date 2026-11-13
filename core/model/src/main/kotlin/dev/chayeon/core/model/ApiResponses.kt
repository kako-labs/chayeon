package dev.chayeon.core.model

import kotlinx.serialization.Serializable

@Serializable data class EnterSessionResponse(val result: AppResult<Session>)
@Serializable data class SignOutResponse(val result: AppResult<CommandReceipt>)
@Serializable data class DeleteAccountResponse(val result: AppResult<CommandReceipt>)
@Serializable data class SaveProfileResponse(val result: AppResult<MyProfile>)
@Serializable data class RecordConsentResponse(val result: AppResult<ParticipationSettings>)
@Serializable data class UpdateSettingsResponse(val result: AppResult<ParticipationSettings>)
@Serializable data class PublishSignatureResponse(val result: AppResult<MyProfile>)
@Serializable data class SetDiscoveryResponse(val result: AppResult<DiscoveryState>)
@Serializable data class RefreshDiscoveryResponse(val result: AppResult<DiscoveryState>)
@Serializable data class UpdateLocationResponse(val result: AppResult<CommandReceipt>)
@Serializable data class SetSyntheticPresenceResponse(val result: AppResult<CommandReceipt>)
@Serializable data class RequestConnectionResponse(val result: AppResult<ConnectionRequest>)
@Serializable data class RespondConnectionResponse(val result: AppResult<ConnectionRequest>)
@Serializable data class CancelConnectionResponse(val result: AppResult<ConnectionRequest>)
@Serializable data class LeaveConnectionResponse(val result: AppResult<Connection>)
@Serializable data class DeleteEncounterResponse(val result: AppResult<CommandReceipt>)
@Serializable data class DeleteAllEncountersResponse(val result: AppResult<CommandReceipt>)
@Serializable data class RequestAfterResponse(val result: AppResult<AfterRequest>)
@Serializable data class RespondAfterResponse(val result: AppResult<AfterRequest>)
@Serializable data class WithdrawAfterResponse(val result: AppResult<AfterRequest>)
@Serializable data class EndAfterConnectionResponse(val result: AppResult<AfterConnection>)
@Serializable data class BlockPeerResponse(val result: AppResult<Block>)
@Serializable data class UnblockPeerResponse(val result: AppResult<CommandReceipt>)
@Serializable data class ReportPeerResponse(val result: AppResult<Report>)
@Serializable data class SyncResponse(val result: AppResult<SyncSnapshot>)

@Serializable
enum class EntityKind {
    PROFILE, SETTINGS, DISCOVERY, CONNECTION_REQUEST, CONNECTION,
    ENCOUNTER, AFTER_REQUEST, AFTER_CONNECTION, BLOCK,
}

@Serializable
data class Tombstone(
    override val id: String,
    override val version: Long,
    val kind: EntityKind,
) : VersionedEntity

/** Every successful poll is a complete authorized projection, including empty lists and nulls. */
@Serializable
data class SyncSnapshot(
    override val id: String,
    override val version: Long,
    val serverTimeEpochMillis: Long,
    val profile: MyProfile? = null,
    val settings: ParticipationSettings? = null,
    val discovery: DiscoveryState = DiscoveryState(),
    val connectionRequests: List<ConnectionRequest> = emptyList(),
    val connections: List<Connection> = emptyList(),
    val encounters: List<Encounter> = emptyList(),
    val afterRequests: List<AfterRequest> = emptyList(),
    val afterConnections: List<AfterConnection> = emptyList(),
    val blocks: List<Block> = emptyList(),
    val tombstones: List<Tombstone> = emptyList(),
) : VersionedEntity
