package dev.chayeon.feature.meet

import dev.chayeon.core.model.*

data class MeetCommandState(val inProgress: Boolean = false, val error: AppError? = null)

data class HomeUiState(
    val driving: DrivingState = DrivingState(),
    val discovery: DiscoveryState = DiscoveryState(),
    val profile: PublicProfile? = null,
    val profileLoaded: Boolean = false,
    val pendingRequest: ConnectionRequest? = null,
    val toggle: MeetCommandState = MeetCommandState(),
    val refresh: MeetCommandState = MeetCommandState(),
    val homeZone: HomeZoneState = HomeZoneState(),
    val demoPresence: SyntheticPresence? = null,
)

data class CandidateUiState(
    val driving: DrivingState = DrivingState(),
    val candidate: Candidate? = null,
    val ownProfile: PublicProfile? = null,
    val unavailableState: ExperienceState? = ExperienceState.DISCOVERY_LOADING,
    val request: MeetCommandState = MeetCommandState(),
    val canRequest: Boolean = false,
)

data class RequestUiState(
    val driving: DrivingState = DrivingState(),
    val request: ConnectionRequest? = null,
    val ownProfile: PublicProfile? = null,
    val loading: Boolean = true,
    val observationError: AppError? = null,
    val remainingMillis: Long? = null,
    val accept: MeetCommandState = MeetCommandState(),
    val decline: MeetCommandState = MeetCommandState(),
    val cancel: MeetCommandState = MeetCommandState(),
    val homeSuppressed: Boolean = false,
)

data class GreetingUi(
    val clientMessageId: String,
    val code: PresetCode,
    val author: MessageAuthor,
    val delivery: DeliveryState,
    val error: AppError? = null,
)

data class ConnectedUiState(
    val driving: DrivingState = DrivingState(),
    val connection: Connection? = null,
    val ownProfile: PublicProfile? = null,
    val loading: Boolean = true,
    val observationError: AppError? = null,
    val greetings: List<GreetingUi> = emptyList(),
    val greetingsLoading: Boolean = true,
    val greetingsError: AppError? = null,
    val sendingPresets: Set<PresetCode> = emptySet(),
    val leave: MeetCommandState = MeetCommandState(),
    val canLeave: Boolean = false,
    val awaitingPark: Boolean = false,
)

data class EndedUiState(
    val driving: DrivingState = DrivingState(),
    val connection: Connection? = null,
    val ownProfile: PublicProfile? = null,
    val loading: Boolean = true,
    val observationError: AppError? = null,
)

internal val DiscoveryStatus.experienceState: ExperienceState? get() = when (this) {
    DiscoveryStatus.OFF, DiscoveryStatus.AVAILABLE -> null
    DiscoveryStatus.LOADING -> ExperienceState.DISCOVERY_LOADING
    DiscoveryStatus.EMPTY -> ExperienceState.NO_CANDIDATES
    DiscoveryStatus.LOCATION_PERMISSION_OFF -> ExperienceState.LOCATION_PERMISSION_OFF
    DiscoveryStatus.LOCATION_STALE -> ExperienceState.LOCATION_STALE
    DiscoveryStatus.NETWORK_FAILURE -> ExperienceState.NETWORK_FAILURE
    DiscoveryStatus.DRIVING_RESTRICTED -> ExperienceState.DRIVING_RESTRICTED
    DiscoveryStatus.DRIVING_UNKNOWN -> ExperienceState.DRIVING_UNKNOWN
    DiscoveryStatus.ALREADY_CONNECTED -> ExperienceState.ALREADY_CONNECTED
}

internal val ConnectionRequestStatus.experienceState: ExperienceState? get() = when (this) {
    ConnectionRequestStatus.PENDING, ConnectionRequestStatus.ACCEPTED -> null
    ConnectionRequestStatus.DECLINED -> ExperienceState.REQUEST_DECLINED
    ConnectionRequestStatus.CANCELLED -> ExperienceState.REQUEST_CANCELLED
    ConnectionRequestStatus.EXPIRED -> ExperienceState.REQUEST_EXPIRED
    ConnectionRequestStatus.UNAVAILABLE -> ExperienceState.PEER_UNAVAILABLE
}

internal val AppError.userMessage: String get() = when (this) {
    AppError.NotAuthorized -> "참여 정보를 확인하지 못했어요. 내 프로필에서 참여 상태를 확인해요."
    AppError.AlreadyClosed -> "이미 끝난 요청이나 연결이에요. 오늘의 인연에서 다시 확인해요."
    AppError.Expired -> "응답 시간이 지나 요청이 끝났어요. 오늘의 인연에서 다른 인연을 만나봐요."
    AppError.PeerUnavailable -> "지금은 상대와 연결할 수 없어요. 다른 참여자를 확인해봐요."
    AppError.Restricted -> "주차하면 다시 할 수 있어요. 주행 상태가 확인될 때까지 기다려요."
    AppError.Network -> "연결하지 못했어요. 네트워크를 확인하고 다시 시도해요."
    AppError.Unknown -> "처리 결과를 확인하지 못했어요. 잠시 후 다시 시도해요."
}
