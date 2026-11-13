package dev.chayeon.data.voice

import dev.chayeon.core.model.PublicProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Owns focus, communication mode and playback; capture is opened only by collecting capture(). */
interface VoiceAudio {
    val focusAvailable: StateFlow<Boolean>
    suspend fun start()
    fun capture(): Flow<ByteArray>
    fun stopCapture()
    suspend fun play(frame: ByteArray)
    suspend fun stop()
}

interface CallService {
    suspend fun start(microphone: Boolean): Boolean
    suspend fun stop()
}

object SilentDemoPeerVoice : DemoPeerVoice {
    override suspend fun start(peer: PublicProfile) = Unit
    override fun setFocusAvailable(available: Boolean) = Unit
    override suspend fun stop() = Unit
}

interface VoiceServiceOwner {
    fun endVoiceCall()
}
