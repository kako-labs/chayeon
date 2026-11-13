package dev.chayeon

import dev.chayeon.core.model.*
import kotlinx.coroutines.flow.combine

/** Local visibility closes immediately, even when the network has not acknowledged AWAY yet. */
class HomeProtectedDiscoveryRepository(
    private val delegate: DiscoveryRepository,
    home: HomeZoneRepository,
    private val coordinator: HomeZoneCoordinator,
) : DiscoveryRepository by delegate {
    override val discovery = combine(delegate.discovery, home.state) { discovery, zone ->
        if (zone.suppressed) discovery.copy(candidates = emptyList()) else discovery
    }

    override suspend fun setDiscovery(request: SetDiscoveryRequest): AppResult<DiscoveryState> {
        if (request.mode == DiscoveryMode.ON) {
            val presence = confirmPresenceForUi(coordinator)
            if (presence is AppResult.Failure) return presence
        }
        return delegate.setDiscovery(request)
    }

    override suspend fun setSyntheticPresence(request: SetSyntheticPresenceRequest): AppResult<CommandReceipt> =
        AppResult.Failure(AppError.Restricted)

    override suspend fun updateLocation(request: UpdateLocationRequest): AppResult<CommandReceipt> =
        AppResult.Failure(AppError.Restricted)
}
