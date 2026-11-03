package dev.chayeon.feature.meet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.chayeon.core.model.*
import kotlinx.coroutines.flow.*

data class MeetUiState(val driving: DrivingState = DrivingState())

sealed interface MeetNavigationEffect {
    data class Connected(val connectionId: String) : MeetNavigationEffect
    data class Ended(val connectionId: String) : MeetNavigationEffect
    data class Requests(val requestId: String) : MeetNavigationEffect
    data object LockHome : MeetNavigationEffect
    data object ResumeHome : MeetNavigationEffect
}

class MeetViewModel(private val deps: MeetDeps) : ViewModel() {
    private val handled = mutableSetOf<String>()

    val uiState: StateFlow<MeetUiState> = deps.drivingStateProvider.drivingState
        .map { MeetUiState(driving = it) }
        .stateIn(viewModelScope, meetSharing, MeetUiState())

    fun effectsFor(route: String): Flow<MeetNavigationEffect> = combine(
        deps.drivingStateProvider.drivingState,
        deps.connectionRepository.connection,
        deps.connectionRepository.requests,
    ) { driving, connectionState, requestsState ->
        val connection = connectionState.readyValue()
        val pending = requestsState.readyValue().orEmpty()
            .filter { it.status == ConnectionRequestStatus.PENDING }.maxByOrNull { it.version }
        when {
            connection != null && connection.status != ConnectionStatus.ENDED &&
                route != MeetDestination.CONNECTED && "connected:${connection.id}" !in handled ->
                MeetNavigationEffect.Connected(connection.id)
            connection?.status == ConnectionStatus.ENDED && route != MeetDestination.ENDED &&
                "ended:${connection.id}" !in handled -> MeetNavigationEffect.Ended(connection.id)
            driving.policy != DrivingPolicy.ALLOWED && route == MeetDestination.HOME -> MeetNavigationEffect.LockHome
            driving.policy == DrivingPolicy.ALLOWED && route == MeetDestination.DRIVING -> MeetNavigationEffect.ResumeHome
            driving.policy == DrivingPolicy.ALLOWED && pending != null &&
                route in setOf(MeetDestination.HOME, MeetDestination.CANDIDATE) &&
                "request:${pending.id}" !in handled -> MeetNavigationEffect.Requests(pending.id)
            else -> null
        }
    }.filterNotNull().distinctUntilChanged()

    fun handled(effect: MeetNavigationEffect) {
        when (effect) {
            is MeetNavigationEffect.Connected -> handled += "connected:${effect.connectionId}"
            is MeetNavigationEffect.Ended -> {
                handled += "ended:${effect.connectionId}"
                handled += "connected:${effect.connectionId}"
            }
            is MeetNavigationEffect.Requests -> handled += "request:${effect.requestId}"
            MeetNavigationEffect.LockHome, MeetNavigationEffect.ResumeHome -> Unit
        }
    }

    suspend fun allowsNavigation(): Boolean = deps.allowsInteraction()
}
