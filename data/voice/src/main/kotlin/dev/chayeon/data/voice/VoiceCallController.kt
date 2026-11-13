package dev.chayeon.data.voice

import dev.chayeon.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One controller belongs to the Application, independent of the visible screen. */
class VoiceCallController(
    private val scope: CoroutineScope,
    private val transport: VoiceTransport,
    private val audio: VoiceAudio,
    private val service: CallService,
    private val demo: DemoPeerVoice,
    private val currentPolicy: () -> DrivingPolicy = { DrivingPolicy.UNKNOWN },
    private val permissionRequest: suspend () -> Boolean = { false },
) : VoiceCallRepository {
    private data class MuteChoice(val connectionId: String, val muted: Boolean)

    private val mutableState = MutableStateFlow(VoiceCallState())
    override val state: StateFlow<VoiceCallState> = mutableState.asStateFlow()
    private val permissionState = MutableStateFlow(false)
    override val microphonePermission: StateFlow<Boolean> = permissionState.asStateFlow()
    private val commands = Mutex()
    private var mode = SessionMode.SOLO
    private var demoCall = true
    private var peer: PublicProfile? = null
    private var permission: Boolean
        get() = permissionState.value
        set(value) { permissionState.value = value }
    private var serviceAllowsCapture = false
    private var callJob: Job? = null
    private var captureJob: Job? = null
    private var channel: VoiceChannel? = null
    private var muteChoice: MuteChoice? = null

    suspend fun updateTarget(connectionId: String?, mode: SessionMode, microphoneGranted: Boolean, peer: PublicProfile? = null) {
        commands.withLock {
            val nextDemoCall = mode == SessionMode.SOLO || peer?.kind == ProfileKind.DEMO_PEER
            if (connectionId == state.value.connectionId && mode == this.mode && demoCall == nextDemoCall) {
                if (permission != microphoneGranted) {
                    permission = microphoneGranted
                    stopCapture()
                    serviceAllowsCapture = if (connectionId != null) safeServiceStart() else false
                    updateMicrophone()
                    startCapture()
                }
                return
            }
            stopCurrent()
            this.mode = mode
            demoCall = nextDemoCall
            this.peer = peer
            permission = microphoneGranted
            if (connectionId == null) return
            val muted = muteChoice?.takeIf { it.connectionId == connectionId }?.muted ?: false
            muteChoice = MuteChoice(connectionId, muted)
            mutableState.value = VoiceCallState(connectionId, CallStatus.CONNECTING,
                muted = muted, microphone = microphoneStatus())
            callJob = scope.launch { runCall(connectionId) }
        }
    }

    override suspend fun setMuted(muted: Boolean) {
        commands.withLock {
            if (currentPolicy() == DrivingPolicy.UNKNOWN || state.value.connectionId == null) return
            if (state.value.muted == muted) return
            muteChoice = MuteChoice(checkNotNull(state.value.connectionId), muted)
            mutableState.update { it.copy(muted = muted) }
            stopCapture()
            if (!muted) {
                updateMicrophone()
                startCapture()
            }
        }
    }

    override suspend fun requestMicrophone(): Boolean {
        if (currentPolicy() != DrivingPolicy.ALLOWED) return false
        val granted = permissionRequest()
        commands.withLock {
            permission = granted
            if (state.value.connectionId == null) {
                mutableState.update { it.copy(microphone = if (granted) MicrophoneStatus.AVAILABLE else MicrophoneStatus.DENIED) }
            } else {
                stopCapture()
                serviceAllowsCapture = safeServiceStart()
                updateMicrophone()
                startCapture()
            }
        }
        return granted
    }

    suspend fun stop() = commands.withLock { stopCurrent() }

    private suspend fun stopCurrent() {
        mutableState.value = VoiceCallState()
        stopCapture()
        callJob?.cancelAndJoin()
        callJob = null
        channel = null
        audio.stopCapture()
        runCatching { audio.stop() }
        runCatching { service.stop() }
        serviceAllowsCapture = false
    }

    private suspend fun runCall(connectionId: String) {
        var focusObserver: Job? = null
        try {
            serviceAllowsCapture = safeServiceStart()
            try { audio.start() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { }
            focusObserver = scope.launch {
                audio.focusAvailable.collect { available ->
                    commands.withLock {
                        if (state.value.connectionId != connectionId) return@withLock
                        if (demoCall) demo.setFocusAvailable(available)
                        if (!available) stopCapture()
                        else {
                            updateMicrophone()
                            startCapture()
                        }
                    }
                }
            }
            if (demoCall) {
                mutableState.update { it.copy(status = CallStatus.ACTIVE, microphone = MicrophoneStatus.NOT_USED) }
                demo.setFocusAvailable(audio.focusAvailable.value)
                audio.focusAvailable.first { it }
                demo.start(peer ?: PublicProfile("demo", 0, "", AvatarId.DAWN, emptySet(), ProfileKind.DEMO_PEER, null))
                awaitCancellation()
            } else {
                while (currentCoroutineContext().isActive) {
                    var opened: VoiceChannel? = null
                    try {
                        opened = transport.open(connectionId)
                        commands.withLock {
                            if (state.value.connectionId != connectionId) return
                            channel = opened
                            mutableState.update { it.copy(status = CallStatus.ACTIVE) }
                            updateMicrophone()
                            startCapture()
                        }
                        playIncoming(opened.incoming)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // Only connection state is exposed; transport and audio payloads are never logged.
                    } finally {
                        stopCapture()
                        channel = null
                        withContext(NonCancellable) {
                            withTimeoutOrNull(500) { runCatching { opened?.close() } }
                        }
                    }
                    mutableState.update { current ->
                        if (current.connectionId == connectionId) current.copy(status = CallStatus.RECONNECTING) else current
                    }
                    delay(1_000)
                }
            }
        } finally {
            withContext(NonCancellable) {
                focusObserver?.cancelAndJoin()
                stopCapture()
                if (demoCall) runCatching { demo.stop() }
                runCatching { audio.stop() }
                runCatching { service.stop() }
            }
        }
    }

    private suspend fun safeServiceStart(): Boolean = try {
        service.start(!demoCall && permission)
    } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { false }

    private fun microphoneStatus(): MicrophoneStatus = when {
        demoCall -> MicrophoneStatus.NOT_USED
        !permission -> MicrophoneStatus.DENIED
        else -> MicrophoneStatus.AVAILABLE
    }

    private fun updateMicrophone() {
        if (state.value.connectionId == null) {
            mutableState.update { it.copy(microphone = if (permission) MicrophoneStatus.AVAILABLE else MicrophoneStatus.DENIED) }
            return
        }
        mutableState.update { it.copy(microphone = if (permission && !demoCall && !serviceAllowsCapture)
            MicrophoneStatus.UNAVAILABLE else microphoneStatus()) }
    }

    private fun startCapture() {
        val activeChannel = channel ?: return
        if (demoCall || !permission || !serviceAllowsCapture || state.value.muted ||
            !audio.focusAvailable.value || captureJob?.isActive == true) return
        captureJob = scope.launch {
            try {
                audio.capture().collect { frame ->
                    if (frame.size == VoiceFormat.FRAME_BYTES && !state.value.muted && channel === activeChannel) {
                        activeChannel.send(frame)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                mutableState.update { it.copy(microphone = MicrophoneStatus.UNAVAILABLE) }
            } finally {
                audio.stopCapture()
            }
        }
    }

    private suspend fun stopCapture() {
        val job = captureJob
        captureJob = null
        job?.cancel()
        audio.stopCapture()
        job?.join()
    }

    private suspend fun playIncoming(frames: Flow<ByteArray>) = coroutineScope {
        val buffer = PcmJitterBuffer()
        val producer = launch {
            try { frames.collect { if (it.size == VoiceFormat.FRAME_BYTES) buffer.offer(it) } }
            finally { buffer.close() }
        }
        try {
            while (true) {
                val frame = buffer.next() ?: break
                try { audio.play(frame) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { }
            }
        } finally {
            producer.cancelAndJoin()
            buffer.clear()
        }
    }
}

/** The blocking playback sink supplies the clock; refill after an underrun before resuming. */
private class PcmJitterBuffer {
    private val queue = ArrayDeque<ByteArray>()
    private val lock = Any()
    private val changes = MutableStateFlow(0L)
    private var closed = false
    private var buffering = true

    fun offer(frame: ByteArray) = synchronized(lock) {
        if (queue.size == 5) queue.removeFirst()
        queue.addLast(frame)
        changes.value++
    }

    fun close() = synchronized(lock) {
        closed = true
        changes.value++
    }

    fun clear() = synchronized(lock) { queue.clear() }

    suspend fun next(): ByteArray? {
        while (true) {
            val version = changes.value
            synchronized(lock) {
                if (queue.isEmpty()) {
                    buffering = true
                    if (closed) return null
                } else if (closed || queue.size >= if (buffering) 4 else 1) {
                    buffering = false
                    return queue.removeFirst()
                }
            }
            changes.first { it != version }
        }
    }
}
