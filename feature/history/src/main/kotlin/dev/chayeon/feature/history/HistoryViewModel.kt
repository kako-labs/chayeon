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
    val profile: LoadState<MyProfile?> = LoadState.Loading(),
    val settings: LoadState<ParticipationSettings> = LoadState.Loading(),
    val encounters: LoadState<List<Encounter>> = LoadState.Loading(),
    val encounter: LoadState<Encounter?> = LoadState.Loading(),
    val requests: LoadState<List<AfterRequest>> = LoadState.Loading(),
    val relationships: LoadState<List<AfterConnection>> = LoadState.Loading(),
    val blocks: LoadState<List<Block>> = LoadState.Loading(),
    val connection: LoadState<Connection?> = LoadState.Loading(),
    val discovery: DiscoveryState = DiscoveryState(),
    val commands: Map<String, CommandState> = emptyMap(),
    val draft: ProfileDraft = ProfileDraft("", AvatarId.DAWN, emptySet()),
    val draftLoaded: Boolean = false,
    val draftError: String? = null,
    val resumeHandled: Boolean = false,
    val selectedMode: SessionMode = SessionMode.SOLO,
    val selectedRelationshipId: String? = null,
    val targetEncounterId: String? = null,
    val shareContacts: ContactIds = ContactIds(),
    val contactsLoaded: Boolean = false,
    val saveContactsToProfile: Boolean = false,
    val contactError: String? = null,
    val targetPeerId: String? = null,
    val safetyPeer: PublicProfile? = null,
    val selectedReason: ReportReason = ReportReason.UNPLEASANT_CONTACT,
    val confirmation: HistoryConfirmation? = null,
    val remainingMillis: Map<String, Long?> = emptyMap(),
    val microphoneGranted: Boolean = false,
    val homeSuppressed: Boolean = false,
) {
    val requiredConsentsGranted: Boolean get() = policy?.let { settings.hasRequiredConsents(it.consentDocumentVersion) } == true
    val policy: ParticipationPolicy? get() = (session as? SessionState.Active)?.session?.policy
    fun command(key: String) = commands[key] ?: CommandState()
    val myProfile: MyProfile? get() = profile.valueOrNull()
    val selectedRelationship: AfterConnection? get() = relationships.valueOrNull()?.firstOrNull { it.id == selectedRelationshipId }
}

enum class ConfirmationKind { DELETE_ENCOUNTER, WITHDRAW, END_RELATIONSHIP, BLOCK, UNBLOCK, DISCOVERY_OFF, LEAVE, DELETE_RECORDS, SIGN_OUT, DELETE_ACCOUNT }
data class HistoryConfirmation(val kind: ConfirmationKind, val targetId: String = "", val title: String, val body: String, val commandKey: String, val actionLabel: String = kind.actionLabel)

internal val ConfirmationKind.actionLabel: String get() = when (this) {
    ConfirmationKind.DELETE_ENCOUNTER -> "이 기록 삭제"
    ConfirmationKind.WITHDRAW -> "신청 철회"
    ConfirmationKind.END_RELATIONSHIP -> "관계 끝내기"
    ConfirmationKind.BLOCK -> "상대 차단"
    ConfirmationKind.UNBLOCK -> "차단 해제"
    ConfirmationKind.DISCOVERY_OFF -> "발견 끄기"
    ConfirmationKind.LEAVE -> "일시 연결 나가기"
    ConfirmationKind.DELETE_RECORDS -> "내 기록 삭제"
    ConfirmationKind.SIGN_OUT -> "로그아웃"
    ConfirmationKind.DELETE_ACCOUNT -> "계정 삭제"
}

internal fun LoadState<ParticipationSettings>.hasRequiredConsents(documentVersion: String): Boolean =
    valueOrNull()?.consents?.let { records ->
        ConsentScope.entries.all { scope -> records.any {
            it.scope == scope && it.status == ConsentStatus.GRANTED && it.documentVersion == documentVersion
        } }
    } == true

internal val PublicProfile?.safetyName: String get() = when {
    this == null || nickname.isBlank() || nickname == "가상 프로필" -> "이 상대"
    else -> "$nickname 님"
}

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

    protected fun busy(key: String) = commandStates.value[key]?.running == true

    protected fun command(key: String, allowExit: Boolean = false, run: suspend () -> AppResult<*>, success: (Any?) -> Unit = {}) {
        if (busy(key) || commandStates.value[key]?.completed == true) return
        if (pending[key] == null) pending[key] = PendingCommand(allowExit, run, success)
        retry(key)
    }

    fun retry(alias: String) {
        val key = alias
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
