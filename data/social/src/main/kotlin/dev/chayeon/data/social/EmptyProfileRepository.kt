package dev.chayeon.data.social

import dev.chayeon.core.model.AppError
import dev.chayeon.core.model.AppResult
import dev.chayeon.core.model.LoadState
import dev.chayeon.core.model.MyProfile
import dev.chayeon.core.model.ParticipationSettings
import dev.chayeon.core.model.ProfileRepository
import dev.chayeon.core.model.PublishSignatureRequest
import dev.chayeon.core.model.RecordConsentRequest
import dev.chayeon.core.model.SaveProfileRequest
import dev.chayeon.core.model.UpdateSettingsRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class EmptyProfileRepository : ProfileRepository {
    override val profile: Flow<LoadState<MyProfile?>> = flowOf(LoadState.Ready(null, 0))

    override val settings: Flow<LoadState<ParticipationSettings>> = flowOf(LoadState.Ready(ParticipationSettings(id = "participation", version = 0), 0))

    override suspend fun saveProfile(request: SaveProfileRequest): AppResult<MyProfile> =
        AppResult.Failure(AppError.NotAuthorized)

    override suspend fun recordConsent(request: RecordConsentRequest): AppResult<ParticipationSettings> =
        AppResult.Failure(AppError.NotAuthorized)

    override suspend fun updateSettings(request: UpdateSettingsRequest): AppResult<ParticipationSettings> =
        AppResult.Failure(AppError.NotAuthorized)

    override suspend fun publishSignature(request: PublishSignatureRequest): AppResult<MyProfile> =
        AppResult.Failure(AppError.NotAuthorized)
}
