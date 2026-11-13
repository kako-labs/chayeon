package dev.chayeon.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class RequestDirection { INCOMING, OUTGOING }

@Serializable
enum class RequestDecision { ACCEPT, DECLINE }

@Serializable
enum class ConnectionRequestStatus { PENDING, ACCEPTED, DECLINED, CANCELLED, EXPIRED, UNAVAILABLE }

@Serializable
data class ConnectionRequest(
    override val id: String,
    override val version: Long,
    val peer: PublicProfile,
    val direction: RequestDirection,
    val status: ConnectionRequestStatus,
    val createdAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val chemistry: Chemistry,
    val connectionId: String? = null,
    val retryAvailableAtEpochMillis: Long? = null,
    val automatic: Boolean = false,
) : VersionedEntity

@Serializable
enum class ConnectionStatus { CONNECTED, CHECKING, ENDED }

@Serializable
enum class ConnectionEndReason { DISTANCE, PEER_LEFT, SELF_LEFT, STATUS_UNKNOWN, DISCOVERY_OFF, BLOCKED }

@Serializable
data class Connection(
    override val id: String,
    override val version: Long,
    val requestId: String,
    val peer: PublicProfile,
    val status: ConnectionStatus,
    val startedAtEpochMillis: Long,
    val chemistry: Chemistry,
    val endedAtEpochMillis: Long? = null,
    val endReason: ConnectionEndReason? = null,
    val encounterId: String? = null,
) : VersionedEntity

@Serializable
enum class AfterEligibility { AVAILABLE, PENDING, CONNECTED, COOLDOWN, UNAVAILABLE }

@Serializable
data class Encounter(
    override val id: String,
    override val version: Long,
    val connectionId: String,
    val peer: PublicProfile,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long,
    val endReason: ConnectionEndReason,
    val chemistry: Chemistry,
    val sharedInterests: Set<InterestId>,
    val afterEligibility: AfterEligibility,
    val afterAvailableAtEpochMillis: Long? = null,
    val afterRequestId: String? = null,
    val afterConnectionId: String? = null,
) : VersionedEntity

@Serializable
enum class AfterRequestStatus { PENDING, ACCEPTED, DECLINED, WITHDRAWN, EXPIRED, UNAVAILABLE }

@Serializable
data class AfterRequest(
    override val id: String,
    override val version: Long,
    val encounterId: String,
    val peer: PublicProfile,
    val direction: RequestDirection,
    val status: AfterRequestStatus,
    val createdAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val chemistry: Chemistry,
    val afterConnectionId: String? = null,
    val retryAvailableAtEpochMillis: Long? = null,
) : VersionedEntity

@Serializable
enum class AfterConnectionStatus { CONNECTED, CLOSED }

@Serializable
enum class AfterCloseReason { SELF_LEFT, PEER_LEFT, BLOCKED, ACCOUNT_DELETED }

@Serializable
data class AfterConnection(
    override val id: String,
    override val version: Long,
    val afterRequestId: String,
    val encounterId: String,
    val peer: PublicProfile,
    val status: AfterConnectionStatus,
    val connectedAtEpochMillis: Long,
    val chemistry: Chemistry,
    val closedAtEpochMillis: Long? = null,
    val closeReason: AfterCloseReason? = null,
    val sharedContacts: ContactIds? = null,
    val ownSharedContacts: ContactIds? = null,
) : VersionedEntity

@Serializable
data class Block(
    override val id: String,
    override val version: Long,
    val peer: PublicProfile,
    val createdAtEpochMillis: Long,
) : VersionedEntity

@Serializable
enum class ReportReason { UNPLEASANT_CONTACT, UNWANTED_APPROACH, IMPERSONATION, POSSIBLE_MINOR, OTHER }

@Serializable
enum class ReportStatus { RECEIVED }

@Serializable
data class Report(
    override val id: String,
    override val version: Long,
    val reason: ReportReason,
    val status: ReportStatus,
    val createdAtEpochMillis: Long,
) : VersionedEntity
