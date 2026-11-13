package dev.chayeon.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class AvatarId { DAWN, SLOW, JAZZ, SEA, FOREST, STAR }

@Serializable
enum class InterestId {
    NIGHT_VIEW, JAZZ, DRIVE_MUSIC, COFFEE, EXHIBITIONS, FILM_PHOTOGRAPHY,
    CAMPING, RUNNING, WINE, MOVIES, DOGS, TRAVEL, SURFING, HIKING,
}

@Serializable
enum class PersonaCode { ACCOUNT_A, ACCOUNT_B }

@Serializable
enum class ProfileKind { VIRTUAL_PROFILE, DEMO_PEER }

@Serializable
enum class SessionMode { SOLO, SERVER_DEMO }

@Serializable
data class Persona(
    override val id: String,
    override val version: Long,
    val code: PersonaCode,
    val nickname: String,
    val avatar: AvatarId,
) : VersionedEntity

@Serializable
data class Session(
    override val id: String,
    override val version: Long,
    val accountId: String,
    val mode: SessionMode,
    val persona: PersonaCode,
    val roomCode: String?,
    val accessToken: String?,
    val expiresAtEpochMillis: Long?,
    val policy: ParticipationPolicy,
) : VersionedEntity {
    override fun toString(): String = "Session(id=$id, version=$version, mode=$mode)"
}

sealed interface SessionState {
    val version: Long
    data class SignedOut(override val version: Long = 0) : SessionState
    data class Entering(override val version: Long = 0) : SessionState
    data class Active(val session: Session) : SessionState {
        override val version: Long get() = session.version
    }
    data class Failed(val error: AppError, override val version: Long = 0) : SessionState
}

/** Product values are delivered by the implementation, not frozen from draft mockups. */
@Serializable
data class ParticipationPolicy(
    override val id: String,
    override val version: Long,
    val maximumInterests: Int,
    val nicknameMinLength: Int,
    val nicknameMaxLength: Int,
    val reportRetentionDays: Int?,
    val consentDocumentVersion: String,
) : VersionedEntity

@Serializable
data class PublicProfile(
    override val id: String,
    override val version: Long,
    val nickname: String,
    val avatar: AvatarId,
    val interests: Set<InterestId>,
    val kind: ProfileKind,
    val signature: PublicSignature?,
) : VersionedEntity

@Serializable
data class MyProfile(
    override val id: String,
    override val version: Long,
    val publicProfile: PublicProfile,
    val registration: RegistrationState,
) : VersionedEntity

@Serializable
enum class RegistrationState { PROFILE_REQUIRED, CONSENT_REQUIRED, READY }

@Serializable
data class ProfileDraft(
    val nickname: String,
    val avatar: AvatarId,
    val interests: Set<InterestId>,
)

@Serializable
enum class ConsentScope { ADULT_ATTESTATION, VEHICLE_DATA, LOCATION_USE, PUBLIC_PROFILE }

@Serializable
enum class ConsentAction { GRANT, REVOKE }

@Serializable
enum class ConsentStatus { NOT_REQUESTED, GRANTED, REVOKED }

@Serializable
data class ConsentRecord(
    override val id: String,
    override val version: Long,
    val scope: ConsentScope,
    val status: ConsentStatus,
    val documentVersion: String,
    val recordedAtEpochMillis: Long,
) : VersionedEntity

@Serializable
data class ParticipationSettings(
    override val id: String,
    override val version: Long,
    val consents: List<ConsentRecord> = emptyList(),
    val notifyRequests: Boolean = true,
    val notifyGreetings: Boolean = true,
    val receiveRequestsWhileDriving: Boolean = false,
) : VersionedEntity
