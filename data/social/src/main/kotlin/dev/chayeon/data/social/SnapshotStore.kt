package dev.chayeon.data.social

import dev.chayeon.core.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class SnapshotTicket(val generation: Long, val epoch: Long, val sequence: Long)

internal data class SnapshotState(
    val generation: Long = 0,
    val sessionId: String? = null,
    val cursor: Long = -1,
    val data: LoadState<SyncSnapshot> = LoadState.Loading(),
    val anchor: ServerTimeAnchor? = null,
)

/** All repository projections read one atomic value, never independently merged entity lists. */
internal class SnapshotStore(private val monotonicMillis: () -> Long) {
    private val mutable = MutableStateFlow(SnapshotState())
    val state: StateFlow<SnapshotState> = mutable.asStateFlow()
    private var lastSnapshot: SyncSnapshot? = null
    private var requestEpoch = 0L
    private var requestSequence = 0L
    private var completedSequence = 0L

    @Synchronized
    fun begin(sessionId: String?): Long {
        lastSnapshot = null
        requestEpoch = 0
        requestSequence = 0
        completedSequence = 0
        val next = SnapshotState(generation = mutable.value.generation + 1, sessionId = sessionId)
        mutable.value = next
        return next.generation
    }

    @Synchronized
    fun beginRequest(generation: Long = mutable.value.generation): SnapshotTicket? {
        if (generation != mutable.value.generation || mutable.value.sessionId == null) return null
        requestSequence++
        return SnapshotTicket(generation, requestEpoch, requestSequence)
    }

    @Synchronized
    fun confirm(ticket: SnapshotTicket): Boolean {
        if (ticket.generation != mutable.value.generation || ticket.epoch != requestEpoch) return false
        completedSequence = maxOf(completedSequence, ticket.sequence)
        return true
    }

    @Synchronized
    fun accept(ticket: SnapshotTicket, snapshot: SyncSnapshot): Boolean {
        val current = mutable.value
        if (ticket.generation != current.generation || ticket.epoch != requestEpoch || current.sessionId != snapshot.id) return false
        if (snapshot.version < current.cursor || snapshot.serverTimeEpochMillis < 0) return false
        if (ticket.sequence < completedSequence && snapshot.version <= current.cursor) return false
        val previous = lastSnapshot
        if (previous != null && snapshot.serverTimeEpochMillis < previous.serverTimeEpochMillis) return false
        val monotonic = monotonicMillis()
        if (monotonic < 0) return false
        val anchor = ServerTimeAnchor(snapshot.serverTimeEpochMillis, monotonic)
        if (snapshot.version == current.cursor) {
            // A heartbeat refreshes time. It can restore availability only after confirming identical entities.
            if (previous == null || previous.copy(serverTimeEpochMillis = snapshot.serverTimeEpochMillis) != snapshot) {
                return false
            }
            confirm(ticket)
            lastSnapshot = snapshot
            mutable.value = current.copy(
                anchor = anchor,
                data = if (current.data is LoadState.Failed) LoadState.Ready(previous, previous.version) else current.data,
            )
            return false
        }
        confirm(ticket)
        lastSnapshot = snapshot
        mutable.value = current.copy(
            cursor = snapshot.version,
            data = LoadState.Ready(snapshot, snapshot.version),
            anchor = anchor,
        )
        return true
    }

    @Synchronized
    fun fail(ticket: SnapshotTicket, error: AppError) {
        val current = mutable.value
        if (current.generation != ticket.generation || ticket.epoch != requestEpoch || ticket.sequence < completedSequence) return
        requestEpoch++
        completedSequence = ticket.sequence
        mutable.value = current.copy(data = LoadState.Failed(error, current.cursor.coerceAtLeast(0)), anchor = null)
    }
}
