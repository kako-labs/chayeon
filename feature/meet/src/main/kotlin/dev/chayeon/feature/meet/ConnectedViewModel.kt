package dev.chayeon.feature.meet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.chayeon.core.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ConnectedViewModel(private val deps: MeetDeps) : ViewModel() {
    private var lastActiveConnectionId: String? = null
    private var revealedConnectionId: String? = null
    private var protectedWhileDriving = false
    private var observedConnectionId: String? = null
    private var revealedOwnProfile: PublicProfile? = null
    private val connections = deps.connectionRepository.connection.onEach { observation ->
        if (observation is LoadState.Ready) {
            lastActiveConnectionId = observation.value?.takeIf { it.status != ConnectionStatus.ENDED }?.id
        }
    }
    private val leave = MeetCommand<LeaveConnectionRequest>()
    private val content = combine(connections, deps.profileRepository.profile) { observation, profile ->
        ConnectedUiState(
            connection = observation.readyValue(),
            canLeave = lastActiveConnectionId != null,
            ownProfile = profile.publicProfile(),
            loading = observation is LoadState.Loading,
            observationError = (observation as? LoadState.Failed)?.error,
        )
    }
    val uiState: StateFlow<ConnectedUiState> = combine(content, deps.drivingStateProvider.drivingState, leave.state, deps.homeZoneRepository.state, deps.voiceCallRepository.state) { state, driving, leaving, home, call ->
        state.connection?.id?.let { id ->
            if (id != observedConnectionId && !home.suppressed) protectedWhileDriving = false
            observedConnectionId = id
        }
        if (driving.policy == DrivingPolicy.ALLOWED) {
            protectedWhileDriving = false
            revealedOwnProfile = state.ownProfile ?: revealedOwnProfile.takeIf { state.connection?.id == revealedConnectionId }
            revealedConnectionId = state.connection?.id
            state.copy(driving = driving, leave = leaving, call = call)
        } else {
            if (home.suppressed) protectedWhileDriving = true
            val awaitingPark = protectedWhileDriving
            state.copy(driving = driving, leave = leaving, call = call, awaitingPark = awaitingPark,
                connection = state.connection.takeUnless { awaitingPark },
                ownProfile = (state.ownProfile ?: revealedOwnProfile).takeUnless { awaitingPark })
        }
    }.stateIn(viewModelScope, meetSharing, ConnectedUiState())

    fun toggleMute() {
        viewModelScope.launch {
            if (deps.drivingStateProvider.drivingState.first().policy == DrivingPolicy.UNKNOWN) return@launch
            if (deps.homeZoneRepository.state.first().suppressed) return@launch
            val connection = deps.connectionRepository.connection.first().readyValue() ?: return@launch
            if (connection.status != ConnectionStatus.CONNECTED) return@launch
            deps.voiceCallRepository.setMuted(!deps.voiceCallRepository.state.value.muted)
        }
    }

    fun requestMicrophone() {
        viewModelScope.launch {
            if (deps.allowsInteraction()) deps.voiceCallRepository.requestMicrophone()
        }
    }

    fun leave() = leave.launch(viewModelScope,
        prepare = {
            val observation = connections.first()
            val current = observation.readyValue()
            val id = current?.takeIf { it.status != ConnectionStatus.ENDED }?.id ?: lastActiveConnectionId
            id?.let { LeaveConnectionRequest(freshMeta(current?.version), it) }
        },
        permitted = { true },
        isSameTarget = { request ->
            connections.first()
            lastActiveConnectionId == request.connectionId
        },
        execute = { request ->
            val observation = connections.first()
            val current = observation.readyValue()
            when {
                observation is LoadState.Ready && (current?.id != request.connectionId || current.status == ConnectionStatus.ENDED) -> AppResult.Failure(AppError.AlreadyClosed)
                lastActiveConnectionId != request.connectionId -> AppResult.Failure(AppError.AlreadyClosed)
                else -> deps.connectionRepository.leave(request)
            }
        },
    )
}
