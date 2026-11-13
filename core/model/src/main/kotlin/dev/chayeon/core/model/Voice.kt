package dev.chayeon.core.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object VoiceFormat {
    const val SAMPLE_RATE = 16_000
    const val FRAME_MILLIS = 20
    const val FRAME_BYTES = 640
}

enum class CallStatus { IDLE, CONNECTING, ACTIVE, RECONNECTING }
enum class MicrophoneStatus { AVAILABLE, DENIED, UNAVAILABLE, NOT_USED }

data class VoiceCallState(
    val connectionId: String? = null,
    val status: CallStatus = CallStatus.IDLE,
    val muted: Boolean = false,
    val microphone: MicrophoneStatus = MicrophoneStatus.NOT_USED,
)

interface VoiceCallRepository {
    val state: StateFlow<VoiceCallState>
    val microphonePermission: StateFlow<Boolean> get() = ungrantedMicrophonePermission
    suspend fun setMuted(muted: Boolean)
    suspend fun requestMicrophone(): Boolean
}

private val ungrantedMicrophonePermission: StateFlow<Boolean> = MutableStateFlow(false)

object NoVoiceCall : VoiceCallRepository {
    override val state: StateFlow<VoiceCallState> = MutableStateFlow(VoiceCallState())
    override suspend fun setMuted(muted: Boolean) = Unit
    override suspend fun requestMicrophone(): Boolean = false
}

/** PCM exists only in bounded memory buffers during an active connection. */
interface VoiceChannel {
    val incoming: Flow<ByteArray>
    suspend fun send(frame: ByteArray)
    suspend fun close()
}

fun interface VoiceTransport {
    suspend fun open(connectionId: String): VoiceChannel
}

