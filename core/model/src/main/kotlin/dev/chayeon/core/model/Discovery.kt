package dev.chayeon.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class DiscoveryMode { OFF, ON }

@Serializable
enum class LocationSource { SYNTHETIC, DEVICE }

@Serializable
enum class LocationAvailability { NOT_REQUESTED, PERMISSION_DENIED, FRESH, STALE, UNAVAILABLE }

@Serializable
enum class DiscoveryStatus {
    OFF, LOADING, AVAILABLE, EMPTY, LOCATION_PERMISSION_OFF, LOCATION_STALE,
    NETWORK_FAILURE, DRIVING_RESTRICTED, DRIVING_UNKNOWN, ALREADY_CONNECTED,
}

@Serializable
data class Candidate(
    override val id: String,
    override val version: Long,
    val profile: PublicProfile,
    val chemistry: Chemistry,
    val sharedInterests: Set<InterestId>,
    val requestAvailableAtEpochMillis: Long? = null,
) : VersionedEntity

@Serializable
data class DiscoveryState(
    override val id: String = "discovery",
    override val version: Long = 0,
    val mode: DiscoveryMode = DiscoveryMode.OFF,
    val status: DiscoveryStatus = DiscoveryStatus.OFF,
    val locationSource: LocationSource = LocationSource.SYNTHETIC,
    val locationAvailability: LocationAvailability = LocationAvailability.NOT_REQUESTED,
    val candidates: List<Candidate> = emptyList(),
    val refreshedAtEpochMillis: Long? = null,
) : VersionedEntity
