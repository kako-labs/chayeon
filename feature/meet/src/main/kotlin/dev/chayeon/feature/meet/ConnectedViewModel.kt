package dev.chayeon.feature.meet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.chayeon.core.model.*
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectedViewModel(private val deps: MeetDeps) : ViewModel() {
    private data class Attempt(
        val request: SendGreetingRequest,
        val delivery: DeliveryState,
        val error: AppError? = null,
    )

    private var lastActiveConnectionId: String? = null
    private val connections = deps.connectionRepository.connection.onEach { observation ->
        if (observation is LoadState.Ready) {
            lastActiveConnectionId = observation.value?.takeIf { it.status != ConnectionStatus.ENDED }?.id
        }
    }
    private val attempts = MutableStateFlow<Map<String, Attempt>>(emptyMap())
    private val leave = MeetCommand<LeaveConnectionRequest>()
    private val greetings = connections.flatMapLatest { observation ->
        val connection = observation.readyValue()
        if (connection == null || connection.status == ConnectionStatus.ENDED) {
            flowOf(connection?.id to LoadState.Ready(emptyList<ConnectionGreeting>(), observation.version))
        } else deps.connectionRepository.observeGreetings(connection.id)
            .map { connection.id to it }
            .onStart { emit(connection.id to LoadState.Loading()) }
    }
    private val content = combine(
        connections, deps.profileRepository.profile, greetings, attempts,
    ) { observation, profile, messageObservation, local ->
        val connection = observation.readyValue()
        val messages: LoadState<List<ConnectionGreeting>> = if (messageObservation.first == connection?.id) messageObservation.second else LoadState.Loading()
        val currentAttempts = local.values.filter { it.request.connectionId == connection?.id }
        val serverMessages = messages.readyValue().orEmpty().filter { it.connectionId == connection?.id }
        val serverIds = serverMessages.map { it.clientMessageId }.toSet()
        val merged = serverMessages.map { message ->
            val attempt = local[message.clientMessageId]
            GreetingUi(message.clientMessageId, message.code, message.author,
                if (message.delivery == DeliveryState.SENT) DeliveryState.SENT else attempt?.delivery ?: message.delivery,
                if (message.delivery == DeliveryState.SENT) null else attempt?.error)
        } + currentAttempts.filter { it.request.clientMessageId !in serverIds }.map { attempt ->
            GreetingUi(attempt.request.clientMessageId, attempt.request.code, MessageAuthor.SELF, attempt.delivery, attempt.error)
        }
        ConnectedUiState(
            connection = connection,
            canLeave = lastActiveConnectionId != null,
            ownProfile = profile.publicProfile(),
            loading = observation is LoadState.Loading,
            observationError = (observation as? LoadState.Failed)?.error,
            greetings = if (connection != null && connection.status != ConnectionStatus.ENDED && messages !is LoadState.Failed) merged else emptyList(),
            greetingsLoading = messages is LoadState.Loading,
            greetingsError = (messages as? LoadState.Failed)?.error,
            sendingPresets = currentAttempts.filter { it.delivery == DeliveryState.SENDING }.map { it.request.code }.toSet(),
        )
    }
    val uiState: StateFlow<ConnectedUiState> = combine(content, deps.drivingStateProvider.drivingState, leave.state) { state, driving, leaving ->
        state.copy(driving = driving, leave = leaving)
    }.stateIn(viewModelScope, meetSharing, ConnectedUiState())

    private val startingPresets = mutableSetOf<PresetCode>()

    fun sendGreeting(code: PresetCode) {
        if (!startingPresets.add(code)) return
        viewModelScope.launch {
            try {
                if (!deps.allowsInteraction()) return@launch
                val connection = deps.connectionRepository.connection.first().readyValue() ?: return@launch
                if (connection.status != ConnectionStatus.CONNECTED || code !in connection.availablePresets) return@launch
                if (attempts.value.values.any { it.request.connectionId == connection.id && it.request.code == code && it.delivery == DeliveryState.SENDING }) return@launch
                val messageId = UUID.randomUUID().toString()
                val request = SendGreetingRequest(greetingMeta(messageId), connection.id, messageId, code)
                perform(request)
            } finally {
                startingPresets.remove(code)
            }
        }
    }

    private val retryingMessages = mutableSetOf<String>()

    fun retryGreeting(clientMessageId: String) {
        if (!retryingMessages.add(clientMessageId)) return
        viewModelScope.launch {
            try {
                if (!deps.allowsInteraction()) return@launch
                val connection = deps.connectionRepository.connection.first().readyValue() ?: return@launch
                if (connection.status != ConnectionStatus.CONNECTED) return@launch
                val observed = deps.connectionRepository.observeGreetings(connection.id).first().readyValue()
                    ?.firstOrNull { it.clientMessageId == clientMessageId }
                if (observed?.delivery == DeliveryState.SENT || observed?.author == MessageAuthor.PEER) return@launch
                val previous = attempts.value[clientMessageId]
                val request = if (previous != null) {
                    if (previous.delivery != DeliveryState.FAILED) return@launch
                    previous.request
                } else {
                    val failed = observed?.takeIf { it.delivery == DeliveryState.FAILED } ?: return@launch
                    SendGreetingRequest(greetingMeta(failed.clientMessageId), connection.id, failed.clientMessageId, failed.code)
                }
                if (request.connectionId != connection.id || request.code !in connection.availablePresets) return@launch
                perform(request)
            } finally {
                retryingMessages.remove(clientMessageId)
            }
        }
    }

    private suspend fun perform(request: SendGreetingRequest) {
        if (!deps.allowsInteraction()) return
        val connection = deps.connectionRepository.connection.first().readyValue()
        if (connection?.id != request.connectionId || connection.status != ConnectionStatus.CONNECTED || request.code !in connection.availablePresets) return
        if (!deps.allowsInteraction()) return
        attempts.update { it + (request.clientMessageId to Attempt(request, DeliveryState.SENDING)) }
        val result = try {
            deps.connectionRepository.sendGreeting(request)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            AppResult.Failure(AppError.Unknown)
        }
        attempts.update { current ->
            current + (request.clientMessageId to when (result) {
                is AppResult.Success -> Attempt(request, result.value.delivery)
                is AppResult.Failure -> Attempt(request, DeliveryState.FAILED, result.error)
            })
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

internal fun greetingMeta(clientMessageId: String) = CommandMeta("greeting:$clientMessageId")
