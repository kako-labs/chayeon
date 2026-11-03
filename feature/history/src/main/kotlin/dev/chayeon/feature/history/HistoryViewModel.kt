package dev.chayeon.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.chayeon.core.model.*
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

internal val historySharing = SharingStarted.WhileSubscribed(5_000, replayExpirationMillis = 0)

data class CommandState(val running: Boolean = false, val error: AppError? = null, val completed: Boolean = false)

data class HistoryUiState(
    val driving: DrivingState = DrivingState(),
    val session: SessionState = SessionState.Entering(),
    val modes: Set<SessionMode> = emptySet(),
    val personas: LoadState<List<Persona>> = LoadState.Loading(),
    val profile: LoadState<MyProfile?> = LoadState.Loading(),
    val settings: LoadState<ParticipationSettings> = LoadState.Loading(),
    val encounters: LoadState<List<Encounter>> = LoadState.Loading(),
    val encounter: LoadState<Encounter?> = LoadState.Loading(),
    val requests: LoadState<List<AfterRequest>> = LoadState.Loading(),
    val relationships: LoadState<List<AfterConnection>> = LoadState.Loading(),
    val messages: LoadState<List<AfterMessage>> = LoadState.Loading(),
    val blocks: LoadState<List<Block>> = LoadState.Loading(),
    val connection: LoadState<Connection?> = LoadState.Loading(),
    val discovery: DiscoveryState = DiscoveryState(),
    val commands: Map<String, CommandState> = emptyMap(),
    val sendCommands: Map<String, String> = emptyMap(),
    val draft: ProfileDraft = ProfileDraft("", AvatarId.DAWN, emptySet()),
    val draftLoaded: Boolean = false,
    val draftError: String? = null,
    val resumeHandled: Boolean = false,
    val selectedMode: SessionMode = SessionMode.SOLO,
    val selectedPersona: PersonaCode = PersonaCode.ACCOUNT_A,
    val roomCode: String = "",
    val selectedRelationshipId: String? = null,
    val targetEncounterId: String? = null,
    val messageDrafts: Map<String, String> = emptyMap(),
    val outbox: List<PendingAfterMessage> = emptyList(),
    val selectedReason: ReportReason = ReportReason.UNPLEASANT_MESSAGE,
    val confirmation: HistoryConfirmation? = null,
    val remainingMillis: Map<String, Long?> = emptyMap(),
) {
    val policy: ParticipationPolicy? get() = (session as? SessionState.Active)?.session?.policy
    fun command(key: String) = commands[sendCommands[key] ?: key] ?: CommandState()
    val myProfile: MyProfile? get() = profile.valueOrNull()
    val selectedRelationship: AfterConnection? get() = relationships.valueOrNull()?.firstOrNull { it.id == selectedRelationshipId }
}

data class PendingAfterMessage(val request: SendAfterMessageRequest, val delivery: DeliveryState = DeliveryState.SENDING)

enum class ConfirmationKind { DELETE_ENCOUNTER, WITHDRAW, END_RELATIONSHIP, BLOCK, UNBLOCK, DISCOVERY_OFF, LEAVE, DELETE_RECORDS, SIGN_OUT, DELETE_ACCOUNT }
data class HistoryConfirmation(val kind: ConfirmationKind, val targetId: String = "", val title: String, val body: String, val commandKey: String)

internal fun <T> LoadState<T>.valueOrNull(): T? = (this as? LoadState.Ready)?.value
internal fun newMeta(version: Long? = null) = CommandMeta(UUID.randomUUID().toString(), version)
internal fun String.characterCount() = codePointCount(0, length)

open class HistoryViewModel(protected val deps: HistoryDeps) : ViewModel() {
    protected val content = MutableStateFlow(HistoryUiState())
    private val commandStates = MutableStateFlow<Map<String, CommandState>>(emptyMap())
    private data class PendingCommand(val allowExit: Boolean, val run: suspend () -> AppResult<*>, val success: (Any?) -> Unit)
    private val pending = mutableMapOf<String, PendingCommand>()

    val uiState: StateFlow<HistoryUiState> = combine(content, commandStates, deps.drivingStateProvider.drivingState) { state, commands, driving ->
        state.copy(commands = commands, driving = driving)
    }.stateIn(viewModelScope, historySharing, HistoryUiState())

    protected fun <T> observe(flow: Flow<T>, reduce: (HistoryUiState, T) -> HistoryUiState) {
        viewModelScope.launch { flow.collect { value -> content.update { reduce(it, value) } } }
    }

    protected fun observeProfile() = observe(deps.profileRepository.profile) { state, profile -> state.copy(profile = profile) }
    protected fun observeSession() = observe(deps.sessionRepository.session) { state, session -> state.copy(session = session) }
    protected fun observeRequests() = observe(deps.afterRepository.requests) { state, requests -> state.copy(requests = requests) }
    protected fun observeRelationships() = observe(deps.afterRepository.connections) { state, relationships -> state.copy(relationships = relationships) }

    protected fun edit(block: () -> Unit) {
        viewModelScope.launch { if (deps.drivingStateProvider.drivingState.first().policy == DrivingPolicy.ALLOWED) block() }
    }

    fun navigate(action: () -> Unit) = edit(action)

    protected fun resetCommand(key: String) {
        if (commandStates.value[key]?.running == true) return
        pending.remove(key)
        commandStates.update { it - key }
    }

    protected fun commandState(key: String) = commandStates.value[key] ?: CommandState()

    protected fun busy(key: String) = commandStates.value[content.value.sendCommands[key] ?: key]?.running == true

    protected fun command(key: String, allowExit: Boolean = false, run: suspend () -> AppResult<*>, success: (Any?) -> Unit = {}) {
        if (busy(key) || commandStates.value[key]?.completed == true) return
        if (pending[key] == null) pending[key] = PendingCommand(allowExit, run, success)
        retry(key)
    }

    fun retry(alias: String) {
        val key = content.value.sendCommands[alias] ?: alias
        val operation = pending[key] ?: return
        if (busy(key) || commandStates.value[key]?.completed == true) return
        commandStates.update { it + (key to CommandState(running = true)) }
        viewModelScope.launch {
            val result = try {
                if (!operation.allowExit && deps.drivingStateProvider.drivingState.first().policy != DrivingPolicy.ALLOWED) {
                    AppResult.Failure(AppError.Restricted)
                } else operation.run()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                AppResult.Failure(AppError.Unknown)
            }
            when (result) {
                is AppResult.Success -> {
                    commandStates.update { it + (key to CommandState(completed = true)) }
                    operation.success(result.value)
                    pending.remove(key)
                    content.update { if (it.confirmation?.commandKey == key) it.copy(confirmation = null) else it }
                }
                is AppResult.Failure -> commandStates.update { it + (key to CommandState(error = result.error)) }
            }
        }
    }

    fun dismissConfirmation() {
        val confirmation = content.value.confirmation ?: return
        if (!busy(confirmation.commandKey)) content.update { it.copy(confirmation = null) }
    }

    protected fun confirm(value: HistoryConfirmation) = edit { content.update { it.copy(confirmation = value) } }

    protected fun observeCountdowns() {
        viewModelScope.launch {
            combine(deps.serverTimeProvider.serverTime, content.map { state ->
                buildMap {
                    state.requests.valueOrNull().orEmpty().forEach { request ->
                        put(request.id, request.expiresAtEpochMillis)
                        request.retryAvailableAtEpochMillis?.let { put("retry:${request.id}", it) }
                    }
                    state.encounter.valueOrNull()?.let { encounter ->
                        encounter.afterAvailableAtEpochMillis?.let { put("retry:${encounter.id}", it) }
                    }
                }
            }.distinctUntilChanged(), flow { while (true) { emit(Unit); kotlinx.coroutines.delay(1_000) } }) { anchor, deadlines, _ ->
                deadlines.mapValues { (_, deadline) -> anchor?.remainingMillisUntil(deadline, deps.serverTimeProvider.monotonicTimeMillis()) }
            }.collect { times -> content.update { it.copy(remainingMillis = times) } }
        }
    }
}
