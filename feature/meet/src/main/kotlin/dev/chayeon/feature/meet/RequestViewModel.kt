package dev.chayeon.feature.meet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.chayeon.core.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*

class RequestViewModel(private val deps: MeetDeps, initialRequestId: String? = null) : ViewModel() {
    private val accept = MeetCommand<RespondConnectionRequest>()
    private val decline = MeetCommand<RespondConnectionRequest>()
    private val cancel = MeetCommand<CancelConnectionRequest>()
    private var selectedId: String? = initialRequestId
    private var hasObservedSelection = false

    private val content = combine(
        deps.connectionRepository.requests,
        deps.profileRepository.profile,
        deps.serverTimeProvider.serverTime,
        flow { while (true) { emit(Unit); delay(1_000) } },
    ) { requests, profile, anchor, _ ->
        val items = requests.readyValue().orEmpty()
        if (selectedId == null) selectedId = selectRequest(items)?.id
        val selected = items.firstOrNull { it.id == selectedId }
        if (selected != null) hasObservedSelection = true
        RequestUiState(
            request = selected,
            ownProfile = profile.publicProfile(),
            loading = requests is LoadState.Loading || (initialRequestId != null && !hasObservedSelection && selected == null && requests !is LoadState.Failed),
            observationError = (requests as? LoadState.Failed)?.error,
            remainingMillis = selected?.let {
                anchor?.remainingMillisUntil(it.expiresAtEpochMillis, deps.serverTimeProvider.monotonicTimeMillis())
            },
        )
    }

    val uiState: StateFlow<RequestUiState> = combine(
        content, deps.drivingStateProvider.drivingState, accept.state, decline.state, cancel.state,
    ) { state, driving, accepting, declining, cancelling ->
        state.copy(driving = driving, accept = accepting, decline = declining, cancel = cancelling)
    }.combine(deps.homeZoneRepository.state) { state, home ->
        state.copy(homeSuppressed = home.suppressed)
    }.combine(deps.profileRepository.settings) { state, settings ->
        state.copy(receiveRequestsWhileDriving = settings.readyValue()?.receiveRequestsWhileDriving == true)
    }.stateIn(viewModelScope, meetSharing, RequestUiState())

    private suspend fun currentRequest(): ConnectionRequest? {
        val items = deps.connectionRepository.requests.first().readyValue().orEmpty()
        val id = selectedId ?: selectRequest(items)?.id?.also { selectedId = it }
        return items.firstOrNull { it.id == id }
    }

    fun accept() = respond(RequestDecision.ACCEPT)
    fun decline() = respond(RequestDecision.DECLINE)

    private fun respond(decision: RequestDecision) {
        if (accept.state.value.inProgress || decline.state.value.inProgress || cancel.state.value.inProgress) return
        val command = if (decision == RequestDecision.ACCEPT) accept else decline
        command.launch(viewModelScope,
            prepare = { currentRequest()?.let { RespondConnectionRequest(freshMeta(), it.id, decision) } },
            permitted = { allowsResponse() },
            execute = { payload ->
                val current = currentRequest()
                when {
                    current?.id != payload.requestId || current.status != ConnectionRequestStatus.PENDING -> AppResult.Failure(AppError.AlreadyClosed)
                    current.direction != RequestDirection.INCOMING -> AppResult.Failure(AppError.NotAuthorized)
                    !allowsResponse() -> AppResult.Failure(AppError.Restricted)
                    decision == RequestDecision.ACCEPT && deps.homeZoneRepository.state.first().suppressed -> AppResult.Failure(AppError.PeerUnavailable)
                    else -> deps.connectionRepository.respond(payload)
                }
            },
        )
    }

    private suspend fun allowsResponse(): Boolean {
        val policy = deps.drivingStateProvider.drivingState.first().policy
        return policy == DrivingPolicy.ALLOWED || (policy == DrivingPolicy.RESTRICTED &&
            deps.profileRepository.settings.first().readyValue()?.receiveRequestsWhileDriving == true &&
            currentRequest()?.direction == RequestDirection.INCOMING)
    }

    fun cancel() {
        if (accept.state.value.inProgress || decline.state.value.inProgress) return
        cancel.launch(viewModelScope,
            prepare = { currentRequest()?.let { CancelConnectionRequest(freshMeta(it.version), it.id) } },
            permitted = { deps.allowsInteraction() },
            execute = { payload ->
                val current = currentRequest()
                when {
                    current?.id != payload.requestId || current.status != ConnectionRequestStatus.PENDING -> AppResult.Failure(AppError.AlreadyClosed)
                    current.direction != RequestDirection.OUTGOING -> AppResult.Failure(AppError.NotAuthorized)
                    !deps.allowsInteraction() -> AppResult.Failure(AppError.Restricted)
                    else -> deps.connectionRepository.cancel(payload)
                }
            },
        )
    }
}

internal fun selectRequest(requests: List<ConnectionRequest>): ConnectionRequest? =
    requests.filter { it.status == ConnectionRequestStatus.PENDING }.maxByOrNull { it.version }
        ?: requests.maxByOrNull { it.version }
