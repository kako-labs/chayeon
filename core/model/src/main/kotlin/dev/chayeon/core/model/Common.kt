package dev.chayeon.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Versions are server revisions, or account-scoped local revisions for device-only entities. */
interface VersionedEntity {
    val id: String
    val version: Long
}

@Serializable
sealed interface AppResult<out T> {
    @Serializable
    @SerialName("success")
    data class Success<T>(val value: T) : AppResult<T>

    @Serializable
    @SerialName("failure")
    data class Failure(val error: AppError) : AppResult<Nothing>
}

@Serializable
sealed interface AppError {
    @Serializable @SerialName("not_authorized") data object NotAuthorized : AppError
    @Serializable @SerialName("already_closed") data object AlreadyClosed : AppError
    @Serializable @SerialName("expired") data object Expired : AppError
    @Serializable @SerialName("peer_unavailable") data object PeerUnavailable : AppError
    @Serializable @SerialName("restricted") data object Restricted : AppError
    @Serializable @SerialName("network") data object Network : AppError
    @Serializable @SerialName("unknown") data object Unknown : AppError
}

/** Failed observations contain no cached public data that could outlive consent or freshness. */
sealed interface LoadState<out T> {
    val version: Long
    data class Loading(override val version: Long = 0) : LoadState<Nothing>
    data class Ready<T>(val value: T, override val version: Long) : LoadState<T>
    data class Failed(val error: AppError, override val version: Long = 0) : LoadState<Nothing>
}

@Serializable
data class CommandMeta(
    val commandId: String,
    val expectedVersion: Long? = null,
)

@Serializable
data class CommandReceipt(val commandId: String, val version: Long)

@Serializable
enum class DrivingPolicy { ALLOWED, RESTRICTED, UNKNOWN }

@Serializable
enum class Gear { PARK, REVERSE, NEUTRAL, DRIVE, UNKNOWN }

@Serializable
enum class DataSource { VEHICLE, SYNTHETIC }

data class DrivingState(
    override val id: String = "driving",
    override val version: Long = 0,
    val policy: DrivingPolicy = DrivingPolicy.UNKNOWN,
    val gear: Gear = Gear.UNKNOWN,
    val source: DataSource = DataSource.SYNTHETIC,
) : VersionedEntity

/** These names map one-to-one to the eighteen approved state cards. */
@Serializable
enum class ExperienceState {
    DISCOVERY_LOADING,
    LOCATION_PERMISSION_OFF,
    LOCATION_STALE,
    NO_CANDIDATES,
    NETWORK_FAILURE,
    DRIVING_RESTRICTED,
    DRIVING_UNKNOWN,
    ALREADY_CONNECTED,
    PROFILE_SAVE_FAILED,
    REQUEST_DECLINED,
    REQUEST_EXPIRED,
    REQUEST_CANCELLED,
    PEER_UNAVAILABLE,
    MESSAGE_SEND_FAILED,
    CONNECTION_CHECKING,
    AFTER_DECLINED,
    AFTER_EXPIRED,
    AFTER_WITHDRAWN,
}
