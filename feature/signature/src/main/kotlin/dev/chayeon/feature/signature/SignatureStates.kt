package dev.chayeon.feature.signature

import dev.chayeon.core.model.AppError
import dev.chayeon.core.model.DrivingState
import dev.chayeon.core.model.LoadState
import dev.chayeon.core.model.MyProfile
import dev.chayeon.core.model.MySignature
import dev.chayeon.core.model.ParticipationSettings
import dev.chayeon.core.model.PublicSignature
import dev.chayeon.core.model.TraitEvidence
import dev.chayeon.core.model.TraitId
import dev.chayeon.core.model.TripRecap
import dev.chayeon.core.model.VisibilitySettings

data class SignatureUiState(
    val driving: DrivingState = DrivingState(),
    val signature: LoadState<MySignature> = LoadState.Loading(),
    val consoleAvailable: Boolean = false,
    val recordingEnabled: Boolean = false,
)

data class MyProfileUiState(
    val driving: DrivingState = DrivingState(),
    val profile: LoadState<MyProfile?> = LoadState.Loading(),
    val signature: LoadState<MySignature> = LoadState.Loading(),
    val settings: LoadState<ParticipationSettings> = LoadState.Loading(),
    val consoleAvailable: Boolean = false,
    val updatingCallSetting: Boolean = false,
    val callSettingError: AppError? = null,
)

data class TraitDetailUiState(
    val driving: DrivingState = DrivingState(),
    val trait: TraitId = TraitId.GENTLE_RIDE,
    val evidence: LoadState<TraitEvidence?> = LoadState.Loading(),
    val visible: Boolean = false,
    val updatingVisibility: Boolean = false,
    val visibilityError: AppError? = null,
)

data class VisibilityUiState(
    val driving: DrivingState = DrivingState(),
    val signature: LoadState<MySignature> = LoadState.Loading(),
    val visibility: VisibilitySettings? = null,
    val preview: PublicSignature? = null,
    val savingVisibility: Boolean = false,
    val publishing: Boolean = false,
    val error: AppError? = null,
    val published: Boolean = false,
    val pendingChanges: Boolean = false,
    val publicationKnown: Boolean = false,
)

data class TripRecapUiState(
    val driving: DrivingState = DrivingState(),
    val recap: LoadState<TripRecap?> = LoadState.Loading(),
    val excluding: Boolean = false,
    val markingSeen: Boolean = false,
    val error: AppError? = null,
    val openSignature: Boolean = false,
    val openVisibility: Boolean = false,
    val pendingChanges: Boolean = false,
)
