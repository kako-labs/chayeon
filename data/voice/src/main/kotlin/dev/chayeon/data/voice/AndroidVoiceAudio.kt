package dev.chayeon.data.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import dev.chayeon.core.model.VoiceFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

class AndroidVoiceAudio(context: Context) : VoiceAudio {
    private val context = context.applicationContext
    private val manager = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private val lock = Any()
    private val focusState = MutableStateFlow(false)
    override val focusAvailable: StateFlow<Boolean> = focusState.asStateFlow()
    private val focused: Boolean get() = focusState.value
    @Volatile private var recorder: AudioRecord? = null
    private var track: AudioTrack? = null
    private var effects = emptyList<AudioEffect>()
    private var previousMode: Int? = null
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener { change ->
            focusState.value = change == AudioManager.AUDIOFOCUS_GAIN ||
                change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK
            if (!focused) stopCapture()
        }.build()

    override suspend fun start() = withContext(Dispatchers.IO) {
        previousMode = manager.mode
        manager.mode = AudioManager.MODE_IN_COMMUNICATION
        focusState.value = manager.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        val bufferSize = AudioTrack.getMinBufferSize(VoiceFormat.SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (bufferSize <= 0) return@withContext
        val output = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(AudioFormat.Builder().setSampleRate(VoiceFormat.SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(maxOf(bufferSize, VoiceFormat.FRAME_BYTES * 3))
            .setTransferMode(AudioTrack.MODE_STREAM).build()
        if (output.state != AudioTrack.STATE_INITIALIZED) {
            output.release()
            return@withContext
        }
        synchronized(lock) { track = output }
        output.play()
    }

    override fun capture(): Flow<ByteArray> = flow {
        check(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
        check(focused && !microphonePrivate())
        val size = AudioRecord.getMinBufferSize(VoiceFormat.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(size > 0)
        val input = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            .setAudioFormat(AudioFormat.Builder().setSampleRate(VoiceFormat.SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(maxOf(size, VoiceFormat.FRAME_BYTES * 4)).build()
        // A mute or hang-up can cancel this flow while the recorder is being built, before stopCapture()
        // can see it. Re-checking cancellation under the lock keeps the microphone from opening late.
        val captureContext = currentCoroutineContext()
        synchronized(lock) { recorder = input }
        try {
            check(input.state == AudioRecord.STATE_INITIALIZED)
            synchronized(lock) {
                captureContext.ensureActive()
                if (recorder !== input) error("Capture stopped")
                effects = buildList {
                    if (AcousticEchoCanceler.isAvailable()) runCatching { AcousticEchoCanceler.create(input.audioSessionId) }.getOrNull()?.let(::add)
                    if (NoiseSuppressor.isAvailable()) runCatching { NoiseSuppressor.create(input.audioSessionId) }.getOrNull()?.let(::add)
                    if (AutomaticGainControl.isAvailable()) runCatching { AutomaticGainControl.create(input.audioSessionId) }.getOrNull()?.let(::add)
                }
                effects.forEach { runCatching { it.enabled = true } }
                input.startRecording()
            }
            check(input.recordingState == AudioRecord.RECORDSTATE_RECORDING)
            while (currentCoroutineContext().isActive && recorder === input) {
                check(focused && !microphonePrivate() &&
                    (Build.VERSION.SDK_INT < 29 || input.activeRecordingConfiguration?.isClientSilenced != true))
                val frame = ByteArray(VoiceFormat.FRAME_BYTES)
                var offset = 0
                while (offset < frame.size && currentCoroutineContext().isActive) {
                    val read = input.read(frame, offset, frame.size - offset, AudioRecord.READ_BLOCKING)
                    check(read >= 0)
                    if (read == 0) delay(10) else offset += read
                }
                check(!microphonePrivate() &&
                    (Build.VERSION.SDK_INT < 29 || input.activeRecordingConfiguration?.isClientSilenced != true))
                if (offset == frame.size && focused && recorder === input) emit(frame)
            }
            check(focused && recorder === input)
        } finally { stopCapture() }
    }.flowOn(Dispatchers.IO)

    private fun microphonePrivate(): Boolean = manager.isMicrophoneMute

    override fun stopCapture() {
        synchronized(lock) {
            val input = recorder
            recorder = null
            runCatching { input?.stop() }
            effects.forEach { runCatching { it.release() } }
            effects = emptyList()
            runCatching { input?.release() }
        }
    }

    override suspend fun play(frame: ByteArray) = withContext(Dispatchers.IO) {
        if (!focused || frame.size != VoiceFormat.FRAME_BYTES) return@withContext
        val output = synchronized(lock) { track } ?: return@withContext
        output.write(frame, 0, frame.size, AudioTrack.WRITE_BLOCKING)
        Unit
    }

    override suspend fun stop() = withContext(Dispatchers.IO) {
        stopCapture()
        val output = synchronized(lock) { track.also { track = null } }
        runCatching { output?.pause() }
        runCatching { output?.flush() }
        runCatching { output?.release() }
        runCatching { manager.abandonAudioFocusRequest(focus) }
        focusState.value = false
        previousMode?.let { saved -> runCatching { manager.mode = saved } }
        previousMode = null
    }
}
