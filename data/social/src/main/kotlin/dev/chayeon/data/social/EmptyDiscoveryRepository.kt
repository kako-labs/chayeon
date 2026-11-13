package dev.chayeon.data.social

import dev.chayeon.core.model.AppError
import dev.chayeon.core.model.AppResult
import dev.chayeon.core.model.CommandReceipt
import dev.chayeon.core.model.DiscoveryRepository
import dev.chayeon.core.model.DiscoveryState
import dev.chayeon.core.model.RefreshDiscoveryRequest
import dev.chayeon.core.model.SetDiscoveryRequest
import dev.chayeon.core.model.SetSyntheticPresenceRequest
import dev.chayeon.core.model.UpdateLocationRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class EmptyDiscoveryRepository : DiscoveryRepository {
    override val discovery: Flow<DiscoveryState> = flowOf(DiscoveryState())

    override suspend fun setDiscovery(request: SetDiscoveryRequest): AppResult<DiscoveryState> =
        AppResult.Failure(AppError.NotAuthorized)

    override suspend fun refresh(request: RefreshDiscoveryRequest): AppResult<DiscoveryState> =
        AppResult.Failure(AppError.NotAuthorized)

    override suspend fun updateLocation(request: UpdateLocationRequest): AppResult<CommandReceipt> =
        AppResult.Failure(AppError.NotAuthorized)

    override suspend fun setSyntheticPresence(request: SetSyntheticPresenceRequest): AppResult<CommandReceipt> =
        AppResult.Failure(AppError.NotAuthorized)
}
