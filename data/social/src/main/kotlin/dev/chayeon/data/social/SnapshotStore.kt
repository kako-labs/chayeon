package dev.chayeon.data.social

import dev.chayeon.core.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class SnapshotTicket(val generation: Long, val epoch: Long, val sequence: Long)

internal data class PendingGreeting(val request: SendGreetingRequest, val message: ConnectionGreeting)
internal data class PendingAfterMessage(val request: SendAfterMessageRequest, val message: AfterMessage)

internal data class SnapshotState(
    val generation: Long = 0,
    val sessionId: String? = null,
    val cursor: Long = -1,
    val data: LoadState<SyncSnapshot> = LoadState.Loading(),
    val anchor: ServerTimeAnchor? = null,
    val pendingGreetings: List<PendingGreeting> = emptyList(),
    val pendingAfterMessages: List<PendingAfterMessage> = emptyList(),
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
            pendingGreetings = current.pendingGreetings.filterNot { pending ->
                snapshot.greetings.any { it.author == MessageAuthor.SELF && it.connectionId == pending.message.connectionId &&
                    it.clientMessageId == pending.message.clientMessageId } ||
                    (pending.message.delivery == DeliveryState.SENT && pending.message.version <= snapshot.version)
            },
            pendingAfterMessages = current.pendingAfterMessages.filterNot { pending ->
                snapshot.afterMessages.any { it.author == MessageAuthor.SELF && it.afterConnectionId == pending.message.afterConnectionId &&
                    it.clientMessageId == pending.message.clientMessageId } ||
                    (pending.message.delivery == DeliveryState.SENT && pending.message.version <= snapshot.version)
            },
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

    @Synchronized
    fun greeting(generation: Long, request: SendGreetingRequest, delivery: DeliveryState,
        received: ConnectionGreeting? = null,
    ): SendGreetingRequest? {
        val current = mutable.value
        if (current.generation != generation || current.sessionId == null) return null
        val original = current.pendingGreetings.find {
            it.request.connectionId == request.connectionId && it.request.clientMessageId == request.clientMessageId
        }
        if (original != null && original.request.code != request.code) return null
        val stable = original?.request ?: request
        val message = received ?: original?.message?.copy(delivery = delivery) ?: ConnectionGreeting(
            id = "pending:${request.clientMessageId}", version = current.cursor.coerceAtLeast(0),
            connectionId = request.connectionId, clientMessageId = request.clientMessageId,
            author = MessageAuthor.SELF, code = request.code,
            sentAtEpochMillis = current.anchor?.serverEpochMillis ?: 0,
            delivery = delivery,
        )
        mutable.value = current.copy(pendingGreetings = current.pendingGreetings.filterNot {
            it.request.connectionId == request.connectionId && it.request.clientMessageId == request.clientMessageId
        } + PendingGreeting(stable, message))
        return stable
    }

    @Synchronized
    fun afterMessage(generation: Long, request: SendAfterMessageRequest, delivery: DeliveryState,
        received: AfterMessage? = null,
    ): SendAfterMessageRequest? {
        val current = mutable.value
        if (current.generation != generation || current.sessionId == null) return null
        val original = current.pendingAfterMessages.find {
            it.request.afterConnectionId == request.afterConnectionId && it.request.clientMessageId == request.clientMessageId
        }
        if (original != null && original.request.content != request.content) return null
        val stable = original?.request ?: request
        val message = received ?: original?.message?.copy(delivery = delivery) ?: AfterMessage(
            id = "pending:${request.clientMessageId}", version = current.cursor.coerceAtLeast(0),
            afterConnectionId = request.afterConnectionId, clientMessageId = request.clientMessageId,
            author = MessageAuthor.SELF, content = request.content,
            sentAtEpochMillis = current.anchor?.serverEpochMillis ?: 0,
            delivery = delivery,
        )
        mutable.value = current.copy(pendingAfterMessages = current.pendingAfterMessages.filterNot {
            it.request.afterConnectionId == request.afterConnectionId && it.request.clientMessageId == request.clientMessageId
        } + PendingAfterMessage(stable, message))
        return stable
    }
}
