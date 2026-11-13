package dev.chayeon.data.social

import dev.chayeon.core.model.*
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.KSerializer

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
internal class SocialCoordinator(
    private val backends: Map<SessionMode, SocialBackend>,
    private val scope: CoroutineScope,
    private val localCleanup: suspend () -> AppResult<Unit>,
    val monotonicMillis: () -> Long,
    drivingProvider: DrivingStateProvider? = null,
    private val cleanupStorage: CleanupStorage = MemoryCleanupStorage(),
    private val prepareSession: suspend (Session, suspend (SetSyntheticPresenceRequest, suspend () -> Boolean) -> AppResult<CommandReceipt>) -> Unit = { _, _ -> },
) {
    val store = SnapshotStore(monotonicMillis)
    val session = MutableStateFlow<SessionState>(SessionState.Entering())
    private val drivingBinding = MutableStateFlow(drivingProvider)
    val driving: Flow<DrivingState> = drivingBinding.flatMapLatest { provider ->
        (provider?.drivingState ?: flowOf(DrivingState())).catch { emit(DrivingState()) }
    }
    private val lifecycleMutex = Mutex()
    private var poll: Job? = null
    @Volatile private var active: Session? = null
    @Volatile private var closed = false
    private var pendingCleanup: CleanupRecord? = null
    private var restored = false
    private var restorationBlocked = false

    init { scope.launch { lifecycleMutex.withLock { restore() } } }

    fun bindDrivingState(provider: DrivingStateProvider) { drivingBinding.value = provider }

    suspend fun allowsIncomingResponse(requestId: String): Boolean {
        val policy = withTimeoutOrNull(1_000) { driving.first().policy } ?: return false
        if (policy == DrivingPolicy.ALLOWED) return true
        val snapshot = (store.state.value.data as? LoadState.Ready)?.value ?: return false
        return policy == DrivingPolicy.RESTRICTED && snapshot.settings?.drivingConnectMode in setOf(DrivingConnectMode.RING, DrivingConnectMode.AUTO) &&
            snapshot.connectionRequests.any { it.id == requestId && it.direction == RequestDirection.INCOMING &&
                it.status == ConnectionRequestStatus.PENDING }
    }

    fun incomingPeerId(requestId: String): String? =
        (store.state.value.data as? LoadState.Ready)?.value?.connectionRequests?.find { it.id == requestId }?.peer?.id

    private suspend fun automaticSnapshot(): SyncSnapshot? {
        if (withTimeoutOrNull(1_000) { driving.first().policy } != DrivingPolicy.RESTRICTED) return null
        val snapshot = (store.state.value.data as? LoadState.Ready)?.value ?: return null
        if (snapshot.settings?.drivingConnectMode != DrivingConnectMode.AUTO ||
            snapshot.discovery.mode != DiscoveryMode.ON || snapshot.connections.any { it.status != ConnectionStatus.ENDED }) return null
        return snapshot
    }

    suspend fun allowsAutomaticRequest(candidateId: String): Boolean {
        val snapshot = automaticSnapshot() ?: return false
        if (snapshot.connectionRequests.any { it.direction == RequestDirection.OUTGOING && it.status == ConnectionRequestStatus.PENDING }) return false
        val candidate = snapshot.discovery.candidates.find { it.id == candidateId } ?: return false
        return snapshot.blocks.none { it.peer.id == candidate.profile.id } &&
            snapshot.settings!!.autoConnectConditions.matches(candidate.profile, snapshot.profile?.publicProfile?.interests.orEmpty())
    }

    suspend fun allowsAutomaticResponse(requestId: String): Boolean {
        val snapshot = automaticSnapshot() ?: return false
        val request = snapshot.connectionRequests.find { it.id == requestId && it.direction == RequestDirection.INCOMING &&
            it.status == ConnectionRequestStatus.PENDING } ?: return false
        return snapshot.blocks.none { it.peer.id == request.peer.id } &&
            snapshot.settings!!.autoConnectConditions.matches(request.peer, snapshot.profile?.publicProfile?.interests.orEmpty())
    }

    suspend fun allowed(): Boolean = try {
        withTimeoutOrNull(1_000) { drivingBinding.value?.drivingState?.first()?.policy } == DrivingPolicy.ALLOWED
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
    }

    suspend fun enter(request: EnterSessionRequest): AppResult<Session> = lifecycleMutex.withLock {
        if (closed) return@withLock AppResult.Failure(AppError.NotAuthorized)
        if (!restored) restore()
        if (restorationBlocked) return@withLock AppResult.Failure(AppError.Unknown)
        pendingCleanup?.let { record ->
            val cleanup = completeLocalCleanup(record)
            if (pendingCleanup != null) return@withLock cleanup as? AppResult.Failure ?: AppResult.Failure(AppError.Unknown)
        }
        if (!allowed()) return@withLock AppResult.Failure(AppError.Restricted)
        if (active != null) return@withLock AppResult.Failure(AppError.AlreadyClosed)
        val backend = backends[request.mode] ?: return@withLock AppResult.Failure(AppError.NotAuthorized)
        session.value = SessionState.Entering()
        store.begin(null)
        val result = try {
            backend.enter(request)
        } catch (cancelled: CancellationException) {
            session.value = SessionState.SignedOut()
            throw cancelled
        } catch (_: Exception) {
            AppResult.Failure(AppError.Unknown)
        }
        when (result) {
            is AppResult.Success -> activate(result.value)
            is AppResult.Failure -> session.value = SessionState.Failed(result.error)
        }
        result
    }

    private suspend fun restore() {
        if (restored || active != null || closed) return
        val marker = localResult { cleanupStorage.read() }
        if (marker is AppResult.Failure) {
            restorationBlocked = true
            restored = true
            session.value = SessionState.Failed(marker.error)
            return
        }
        val pending = (marker as AppResult.Success).value
        if (pending != null) {
            pendingCleanup = pending
            store.begin(null)
            session.value = SessionState.Entering()
            completeLocalCleanup(pending)
            restored = true
            return
        }
        val restoredSessions = backends.values.map { backend -> backend.restoreSession() }
        val failure = restoredSessions.filterIsInstance<AppResult.Failure>().firstOrNull()
        if (failure != null) {
            restorationBlocked = true
            restored = true
            session.value = SessionState.Failed(failure.error)
            return
        }
        val sessions = restoredSessions.mapNotNull { (it as? AppResult.Success)?.value }
        when (sessions.size) {
            0 -> session.value = SessionState.SignedOut()
            1 -> activate(sessions.single())
            else -> {
                restorationBlocked = true
                session.value = SessionState.Failed(AppError.Unknown)
            }
        }
        restored = true
    }

    private suspend fun activate(value: Session) {
        if (closed) return
        active = value
        val generation = store.begin(value.id)
        prepareSession(value) { request, canSend ->
            command(ApiRoutes.SET_SYNTHETIC_PRESENCE, request, SetSyntheticPresenceRequest.serializer(),
                SetSyntheticPresenceResponse.serializer(), { it.result }, requiresAllowed = false, refresh = false, trackResult = false, canExecute = canSend)
        }
        session.value = SessionState.Active(value)
        poll?.cancel()
        poll = scope.launch {
            val backend = backends.getValue(value.mode)
            var ticket: SnapshotTicket? = null
            try {
                backend.snapshots(value, 0) { ticket = store.beginRequest(generation) }.collect { result ->
                    val responseTicket = ticket ?: return@collect
                    when (result) {
                        is AppResult.Success -> store.accept(responseTicket, result.value)
                        is AppResult.Failure -> store.fail(responseTicket, result.error)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ticket?.let { store.fail(it, AppError.Unknown) }
            }
        }
    }

    suspend fun <Request, Response, Value> command(
        route: String,
        request: Request,
        requestSerializer: KSerializer<Request>,
        responseSerializer: KSerializer<Response>,
        resultOf: (Response) -> AppResult<Value>,
        requiresAllowed: Boolean = true,
        refresh: Boolean = true,
        trackResult: Boolean = true,
        canExecute: suspend () -> Boolean = { true },
    ): AppResult<Value> {
        if (requiresAllowed && !allowed()) return AppResult.Failure(AppError.Restricted)
        val current = active ?: return AppResult.Failure(AppError.NotAuthorized)
        val generation = store.state.value.generation
        val ticket = if (trackResult) store.beginRequest(generation) ?: return AppResult.Failure(AppError.NotAuthorized) else null
        val backend = backends.getValue(current.mode)
        var validatedResponse = false
        val result = try {
            val body = socialJson.encodeToString(requestSerializer, request)
            val response = backend.execute(current, route, body) { (!requiresAllowed || allowed()) && canExecute() }
            val decoded = resultOf(socialJson.decodeFromString(responseSerializer, response))
            validatedResponse = true
            decoded
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SocialRestrictedException) {
            AppResult.Failure(AppError.Restricted)
        } catch (_: IOException) {
            AppResult.Failure(AppError.Network)
        } catch (_: Exception) {
            AppResult.Failure(AppError.Unknown)
        }
        if (store.state.value.generation != generation || active?.id != current.id) {
            return AppResult.Failure(AppError.NotAuthorized)
        }
        if (!trackResult) return result
        if (result is AppResult.Failure && result.error in setOf(AppError.Network, AppError.NotAuthorized)) {
            store.fail(ticket!!, result.error)
        } else {
            if (validatedResponse) store.confirm(ticket!!)
            if (refresh) refresh(current, generation)
        }
        return result
    }

    fun refresh(current: Session? = active, generation: Long = store.state.value.generation) {
        if (current == null) return
        scope.launch {
            val ticket = store.beginRequest(generation) ?: return@launch
            val result = try {
                backends.getValue(current.mode).sync(current, 0)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IOException) {
                AppResult.Failure(AppError.Network)
            } catch (_: Exception) {
                AppResult.Failure(AppError.Unknown)
            }
            when (result) {
                is AppResult.Success -> store.accept(ticket, result.value)
                is AppResult.Failure -> store.fail(ticket, result.error)
            }
        }
    }

    suspend fun signOut(request: SignOutRequest): AppResult<CommandReceipt> = finishSession(
        route = ApiRoutes.SIGN_OUT,
        body = socialJson.encodeToString(SignOutRequest.serializer(), request),
        decode = { socialJson.decodeFromString(SignOutResponse.serializer(), it).result },
    )

    suspend fun deleteAccount(request: DeleteAccountRequest): AppResult<CommandReceipt> = finishSession(
        route = ApiRoutes.DELETE_ACCOUNT,
        body = socialJson.encodeToString(DeleteAccountRequest.serializer(), request),
        decode = { socialJson.decodeFromString(DeleteAccountResponse.serializer(), it).result },
    )

    private suspend fun finishSession(
        route: String,
        body: String,
        decode: (String) -> AppResult<CommandReceipt>,
    ): AppResult<CommandReceipt> = lifecycleMutex.withLock {
        if (!restored) restore()
        val current = active
        val pending = pendingCleanup
        if (current == null && pending == null) return@withLock AppResult.Failure(AppError.NotAuthorized)
        if (pending != null && (pending.route != route || pending.body != body)) {
            return@withLock AppResult.Failure(AppError.AlreadyClosed)
        }
        if (pending == null) {
            val marker = CleanupRecord(route, body)
            val written = localResult { cleanupStorage.write(marker) }
            if (written is AppResult.Failure) return@withLock written
            pendingCleanup = marker
        }
        poll?.cancel()
        active = null
        store.begin(null)
        session.value = SessionState.Entering(current?.version ?: 0)
        withContext(NonCancellable) {
            val remoteResult = if (current != null) {
                try {
                    decode(backends.getValue(current.mode).execute(current, route, body))
                } catch (_: IOException) {
                    AppResult.Failure(AppError.Network)
                } catch (_: Exception) {
                    AppResult.Failure(AppError.Unknown)
                }
            } else {
                pending?.result ?: AppResult.Failure(AppError.Unknown)
            }
            val record = CleanupRecord(route, body, remoteResult)
            pendingCleanup = record
            val saved = localResult { cleanupStorage.write(record) }
            completeLocalCleanup(record, saved as? AppResult.Failure)
        }
    }

    private suspend fun completeLocalCleanup(
        record: CleanupRecord,
        storageFailure: AppResult.Failure? = null,
    ): AppResult<CommandReceipt> {
        val clearResults = backends.values.map { backend ->
            try { backend.clearLocalData() } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { AppResult.Failure(AppError.Unknown) }
        }
        val hookResult = try { localCleanup() } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { AppResult.Failure(AppError.Unknown) }
        val cleanupFailure = storageFailure ?: clearResults.filterIsInstance<AppResult.Failure>().firstOrNull()
            ?: (hookResult as? AppResult.Failure)
        if (cleanupFailure != null) {
            session.value = SessionState.Failed(cleanupFailure.error)
            return cleanupFailure
        }
        val markerCleared = localResult { cleanupStorage.clear() }
        if (markerCleared is AppResult.Failure) {
            session.value = SessionState.Failed(markerCleared.error)
            return markerCleared
        }
        pendingCleanup = null
        val result = record.result ?: AppResult.Failure(AppError.Unknown)
        session.value = when (result) {
            is AppResult.Success -> SessionState.SignedOut(result.value.version)
            is AppResult.Failure -> SessionState.Failed(result.error)
        }
        return result
    }

    fun close() {
        closed = true
        poll?.cancel()
        active = null
        store.begin(null)
        session.value = SessionState.SignedOut()
        backends.values.forEach(SocialBackend::close)
    }
}
