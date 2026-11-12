package dev.chayeon

import dev.chayeon.core.model.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The single presence writer. The lock protects bookkeeping, never a network operation. */
class HomeZoneCoordinator(
    private val home: HomeZoneRepository,
    private val bindAccount: suspend (String) -> Unit,
    private val scope: CoroutineScope,
    private val retryMillis: Long = 1_000,
    private val heartbeatMillis: Long = 20_000,
) {
    private class Binding(
        val serverMode: Boolean,
        val send: suspend (SetSyntheticPresenceRequest, suspend () -> Boolean) -> AppResult<CommandReceipt>,
    ) {
        var target: SyntheticPresence? = null
        var sent: SyntheticPresence? = null
        var pending: SetSyntheticPresenceRequest? = null
        var heartbeat = false
        var attempt: Job? = null
        var timer: Job? = null
        var failures = 0
        var restricted = false
        fun cancel() { attempt?.cancel(); timer?.cancel() }
    }
    private val mutex = Mutex()
    private var binding: Binding? = null
    private val desired = MutableStateFlow(SyntheticPresence.NEARBY)
    val desiredPresence = desired.asStateFlow()

    init { scope.launch { home.state.collect { mutex.withLock { reconcile() } } } }

    private suspend fun target() = if (home.state.first().suppressed) SyntheticPresence.AWAY else desired.value

    private fun schedule(current: Binding, delayMillis: Long, heartbeat: Boolean) {
        if (current.timer != null) return
        current.timer = scope.launch {
            delay(delayMillis)
            mutex.withLock {
                if (binding === current) {
                    current.timer = null
                    reconcile(renew = heartbeat)
                }
            }
        }
    }

    private fun heartbeat(current: Binding) {
        if (current.serverMode && current.target == SyntheticPresence.NEARBY && current.sent == SyntheticPresence.NEARBY)
            schedule(current, heartbeatMillis, heartbeat = true)
    }

    /** Caller owns mutex. Sends run in a separate job and validate intent at every transport attempt. */
    private suspend fun reconcile(renew: Boolean = false, explicit: Boolean = false) {
        val current = binding ?: return
        if (current.restricted) {
            if (!explicit) return
            current.restricted = false
        }
        val target = target()
        if (current.target != target) {
            current.target = target
            current.cancel()
            current.attempt = null
            current.timer = null
            current.failures = 0
            // Retain an unacknowledged request until a corrective command is acknowledged.
        }
        if (current.attempt != null) return
        if (current.sent == target && current.pending == null && !renew) {
            heartbeat(current)
            return
        }
        if (current.timer != null && !(explicit && current.pending == null)) return
        current.timer?.cancel()
        current.timer = null
        val request = current.pending?.takeIf { it.presence == target }
            ?: SetSyntheticPresenceRequest(CommandMeta(UUID.randomUUID().toString()), target)
        val isHeartbeat = (renew && current.sent == target && target == SyntheticPresence.NEARBY) ||
            (current.pending === request && current.heartbeat)
        current.pending = request
        current.heartbeat = isHeartbeat
        val attempt = scope.launch(start = CoroutineStart.LAZY) {
            val permitted: suspend () -> Boolean = {
                mutex.withLock { binding === current && current.pending === request && target() == request.presence }
            }
            val result = try {
                if (permitted()) current.send(request, permitted) else AppResult.Failure(AppError.Restricted)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { AppResult.Failure(AppError.Network) }
            mutex.withLock {
                if (binding !== current || current.pending !== request) return@withLock
                current.attempt = null
                if (result is AppResult.Success) {
                    current.sent = request.presence
                    current.pending = null
                    current.heartbeat = false
                    current.failures = 0
                    reconcile()
                } else if (target() != current.target) {
                    reconcile()
                } else if (result is AppResult.Failure && result.error == AppError.Restricted) {
                    current.sent = null
                    current.pending = null
                    current.heartbeat = false
                    current.restricted = true
                } else if (isHeartbeat) {
                    // A lease renewal is maintenance; it never changes UI command confirmation.
                    current.pending = null
                    current.heartbeat = false
                    heartbeat(current)
                } else {
                    if (result is AppResult.Failure && result.error == AppError.NotAuthorized)
                        current.pending = null
                    current.failures = (current.failures + 1).coerceAtMost(6)
                    schedule(current, (retryMillis * (1L shl (current.failures - 1))).coerceAtMost(30_000), heartbeat = false)
                }
            }
        }
        current.attempt = attempt
        attempt.start()
    }

    /** Bind private storage before session publication; network acknowledgement is asynchronous. */
    suspend fun prepareSession(session: Session,
        send: suspend (SetSyntheticPresenceRequest, suspend () -> Boolean) -> AppResult<CommandReceipt>) = mutex.withLock {
        binding?.cancel()
        binding = null
        desired.value = SyntheticPresence.NEARBY
        bindAccount(session.accountId)
        binding = Binding(session.mode == SessionMode.SERVER_DEMO, send)
        reconcile()
    }

    suspend fun setDesiredPresence(value: SyntheticPresence, currentPolicy: () -> DrivingPolicy): AppResult<Unit> = mutex.withLock {
        if (currentPolicy() != DrivingPolicy.ALLOWED) return@withLock AppResult.Failure(AppError.Restricted)
        if (binding == null) return@withLock AppResult.Failure(AppError.NotAuthorized)
        desired.value = value
        reconcile()
        AppResult.Success(Unit)
    }

    suspend fun endSession() = mutex.withLock {
        binding?.cancel()
        binding = null
        desired.value = SyntheticPresence.NEARBY
    }

    /** A current answer only: never join an in-flight send, including a hung heartbeat. */
    suspend fun confirmPresence(retryRestricted: Boolean = true): AppResult<Unit> = mutex.withLock {
        val current = binding ?: return@withLock AppResult.Failure(AppError.NotAuthorized)
        if (current.restricted && !retryRestricted) return@withLock AppResult.Failure(AppError.Restricted)
        reconcile(explicit = true)
        if (current.restricted) return@withLock AppResult.Failure(AppError.Restricted)
        if (current.sent == target() && (current.pending == null || current.heartbeat)) AppResult.Success(Unit)
        else AppResult.Failure(AppError.Network)
    }
}

/** Only explicit UI commands wait briefly for confirmation; session lifecycle never does. */
internal suspend fun confirmPresenceForUi(coordinator: HomeZoneCoordinator): AppResult<Unit> =
    withTimeoutOrNull(3_000) {
        var result = coordinator.confirmPresence()
        while (result == AppResult.Failure(AppError.Network)) {
            delay(100)
            result = coordinator.confirmPresence(retryRestricted = false)
        }
        result
    } ?: AppResult.Failure(AppError.Network)
