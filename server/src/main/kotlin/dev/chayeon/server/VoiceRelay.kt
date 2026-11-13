package dev.chayeon.server

import dev.chayeon.core.model.VoiceFormat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel

internal data class VoiceIdentity(val room: String, val connection: String, val actor: String)

internal enum class VoiceFrameDecision { FORWARD, DROP, CLOSE }

/** The participant's budget survives reconnects within the same active connection. */
internal class VoiceRateLimit(nowNanos: Long) {
    private var allowance = 50.0
    private var lastFrameNanos = nowNanos
    private var windowStartedNanos = nowNanos
    private var windowFrames = 0
    private var overloadedWindows = 0

    fun decide(size: Int, nowNanos: Long): VoiceFrameDecision {
        if (size != VoiceFormat.FRAME_BYTES) return VoiceFrameDecision.CLOSE
        val windowElapsed = (nowNanos - windowStartedNanos).coerceAtLeast(0)
        if (windowElapsed >= 1_000_000_000L) {
            overloadedWindows = if (windowElapsed < 2_000_000_000L && windowFrames > 100) overloadedWindows + 1 else 0
            windowStartedNanos += windowElapsed / 1_000_000_000L * 1_000_000_000L
            windowFrames = 0
        }
        if (overloadedWindows >= 5) return VoiceFrameDecision.CLOSE
        windowFrames++
        val elapsed = (nowNanos - lastFrameNanos).coerceAtLeast(0)
        allowance = (allowance + elapsed / 1_000_000_000.0 * 50).coerceAtMost(50.0)
        lastFrameNanos = nowNanos
        if (allowance < 1) return VoiceFrameDecision.DROP
        allowance -= 1
        return VoiceFrameDecision.FORWARD
    }
}

/** A socket owns at most 100 ms of PCM. No frame or frame metadata enters engine storage. */
internal class VoiceSocket(val identity: VoiceIdentity, val authorization: String, private val limit: VoiceRateLimit) {
    val frames = Channel<ByteArray>(5, BufferOverflow.DROP_OLDEST)
    val ended = CompletableDeferred<String>()

    fun decide(size: Int, nowNanos: Long) = limit.decide(size, nowNanos)

    fun end(reason: String) {
        ended.complete(reason)
        frames.cancel()
    }

    override fun toString() = "VoiceSocket(redacted)"
}

/** All operations run under RoomRegistry's monitor, including membership checks and enqueue. */
internal class VoiceRelay(private val identify: (String, String) -> VoiceIdentity?) {
    private val sockets = mutableMapOf<VoiceIdentity, VoiceSocket>()
    private val limits = mutableMapOf<VoiceIdentity, Pair<String, VoiceRateLimit>>()

    fun join(header: String, connectionId: String): VoiceSocket? {
        prune()
        val identity = identify(header, connectionId) ?: return null
        val limit = limits.getOrPut(identity) { header to VoiceRateLimit(System.nanoTime()) }.second
        return VoiceSocket(identity, header, limit).also { socket ->
            sockets.put(identity, socket)?.end("replaced")
        }
    }

    fun relay(sender: VoiceSocket, frame: ByteArray, nowNanos: Long = System.nanoTime()): Boolean {
        if (sockets[sender.identity] !== sender || sender.ended.isCompleted) return false
        when (sender.decide(frame.size, nowNanos)) {
            VoiceFrameDecision.CLOSE -> {
                sockets.remove(sender.identity)
                sender.end("frame_limit")
                return false
            }
            VoiceFrameDecision.DROP -> return true
            VoiceFrameDecision.FORWARD -> Unit
        }
        sockets.values.filter { it.identity.room == sender.identity.room &&
            it.identity.connection == sender.identity.connection && it.identity.actor != sender.identity.actor
        }.forEach { it.frames.trySend(frame.copyOf()) }
        return true
    }

    fun prune() {
        val invalid = sockets.values.filter {
            identify(it.authorization, it.identity.connection) != it.identity
        }.map { it.identity.room to it.identity.connection }.toSet()
        sockets.values.filter { it.identity.room to it.identity.connection in invalid }.toList().forEach {
            sockets.remove(it.identity)
            it.end("call_ended")
        }
        limits.entries.removeAll { (identity, value) -> identify(value.first, identity.connection) != identity }
    }

    fun leave(socket: VoiceSocket) {
        if (sockets[socket.identity] === socket) sockets.remove(socket.identity)
        socket.end("disconnected")
    }
}
