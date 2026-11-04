package dev.chayeon.core.model

import kotlinx.coroutines.flow.Flow

/** Device-only choices. No center, fix, or protection reason belongs in a wire type. */
enum class HomeZoneRadius(val meters: Int) {
    NEAR(300), MEDIUM(500), WIDE(1000);
    override fun toString() = "[redacted]"
}

enum class HomeZoneStatus {
    NOT_SET, OUTSIDE, INSIDE, LOCATION_UNKNOWN, PERMISSION_OFF;
    override fun toString() = "[redacted]"
}

data class HomeZoneState(
    val status: HomeZoneStatus = HomeZoneStatus.NOT_SET,
    val radius: HomeZoneRadius? = null,
    val source: LocationSource? = null,
) {
    val suppressed: Boolean get() = status != HomeZoneStatus.NOT_SET && status != HomeZoneStatus.OUTSIDE
    override fun toString() = "[redacted]"
}

interface HomeZoneRepository {
    val state: Flow<HomeZoneState>
    suspend fun setFromCurrentPosition(radius: HomeZoneRadius = HomeZoneRadius.MEDIUM, source: LocationSource = LocationSource.DEVICE): AppResult<Unit>
    suspend fun changeRadius(radius: HomeZoneRadius): AppResult<Unit>
    suspend fun clear(): AppResult<Unit>
}
