package dev.chayeon.data.vehicle

import dev.chayeon.core.model.AppError
import dev.chayeon.core.model.AppResult
import dev.chayeon.core.model.ExcludeTripRequest
import dev.chayeon.core.model.LoadState
import dev.chayeon.core.model.MarkRecapSeenRequest
import dev.chayeon.core.model.MySignature
import dev.chayeon.core.model.RecordTripRequest
import dev.chayeon.core.model.SignatureRepository
import dev.chayeon.core.model.TraitEvidence
import dev.chayeon.core.model.TraitId
import dev.chayeon.core.model.TripRecap
import dev.chayeon.core.model.UpdateVisibilityRequest
import dev.chayeon.core.model.VisibilitySettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class EmptySignatureRepository : SignatureRepository {
    override suspend fun recordTrip(request: RecordTripRequest): AppResult<MySignature> =
        AppResult.Failure(AppError.NotAuthorized)

    override val signature: Flow<LoadState<MySignature>> = flowOf(LoadState.Ready(MySignature(), 0))

    override val recaps: Flow<LoadState<List<TripRecap>>> = flowOf(LoadState.Ready(emptyList(), 0))

    override fun observeEvidence(trait: TraitId): Flow<LoadState<TraitEvidence?>> = flowOf(LoadState.Ready(null, 0))

    override fun observeRecap(recapId: String): Flow<LoadState<TripRecap?>> = flowOf(LoadState.Ready(null, 0))

    override suspend fun updateVisibility(request: UpdateVisibilityRequest): AppResult<VisibilitySettings> =
        AppResult.Failure(AppError.NotAuthorized)

    override suspend fun excludeTrip(request: ExcludeTripRequest): AppResult<MySignature> =
        AppResult.Failure(AppError.NotAuthorized)

    override suspend fun markRecapSeen(request: MarkRecapSeenRequest): AppResult<TripRecap> =
        AppResult.Failure(AppError.NotAuthorized)

    override suspend fun clearLocalData(): AppResult<Unit> =
        AppResult.Success(Unit)
}
