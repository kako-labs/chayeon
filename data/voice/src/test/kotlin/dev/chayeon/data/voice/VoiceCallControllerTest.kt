package dev.chayeon.data.voice

import dev.chayeon.core.model.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceCallControllerTest {
    @Test fun permissionObservationReflectsRestoredGrantAndRevocationWithoutOpeningAudio() = runTest {
        val h = Harness(this)
        h.controller.updateTarget(null, SessionMode.SOLO, true)
        assertTrue(h.controller.microphonePermission.value)
        assertFalse(h.audio.capturing)
        assertFalse(h.service.running)
        h.controller.updateTarget(null, SessionMode.SOLO, false)
        assertFalse(h.controller.microphonePermission.value)
        assertFalse(h.audio.capturing)
    }

    @Test fun connectedStartsAudioServiceAndCapture() = runTest {
        val h = Harness(this)
        h.start()
        assertEquals(CallStatus.ACTIVE, h.controller.state.value.status)
        assertTrue(h.audio.focus)
        assertTrue(h.audio.communicationMode)
        assertTrue(h.service.running)
        assertEquals(1, h.audio.captureStarts)
        h.controller.stop()
        assertFalse(h.audio.focus)
        assertFalse(h.audio.communicationMode)
        assertFalse(h.service.running)
    }

    @Test fun everyConnectionEndAndProtectionStopsAllResources() = runTest {
        val h = Harness(this)
        for (end in ConnectionEndReason.entries.map { it.name } + listOf("ACCOUNT_DELETED", "EXPIRED", "CHECKING", "HOME_PROTECTED", "SIGNED_OUT")) {
            h.start(end)
            h.controller.updateTarget(null, SessionMode.SERVER_DEMO, true)
            assertEquals(VoiceCallState(), h.controller.state.value, end)
            assertFalse(h.audio.capturing, end)
            assertFalse(h.audio.focus, end)
            assertFalse(h.audio.communicationMode, end)
            assertFalse(h.service.running, end)
            assertTrue(h.transport.channels.last().closed, end)
        }
    }

    @Test fun mutedSendsZeroFramesAndClosesMicrophoneWhileReceiving() = runTest {
        val h = Harness(this)
        h.start()
        val socket = h.transport.channels.last()
        h.audio.input.send(frame(1)); runCurrent()
        assertEquals(1, socket.sent.size)
        h.controller.setMuted(true)
        assertFalse(h.audio.capturing)
        h.audio.input.trySend(frame(2)); runCurrent()
        assertEquals(1, socket.sent.size)
        repeat(4) { socket.input.send(frame(3)) }
        advanceTimeBy(100); runCurrent()
        assertEquals(listOf(3, 3, 3, 3), h.audio.played.map { it[0].toInt() })
        h.controller.setMuted(false); runCurrent()
        assertTrue(h.audio.capturing)
        h.controller.stop()
    }

    @Test fun soloNeverOpensMicrophoneOrNetwork() = runTest {
        val h = Harness(this)
        h.controller.updateTarget("solo", SessionMode.SOLO, true); runCurrent()
        assertEquals(CallStatus.ACTIVE, h.controller.state.value.status)
        assertEquals(MicrophoneStatus.NOT_USED, h.controller.state.value.microphone)
        assertEquals(0, h.audio.captureStarts)
        assertTrue(h.transport.channels.isEmpty())
        assertFalse(h.service.microphone)
        assertTrue(h.service.running)
        h.controller.stop()
    }

    @Test fun mutedChoiceSurvivesTemporaryTargetLossForTheSameConnection() = runTest {
        val h = Harness(this)
        h.start()
        h.controller.setMuted(true)
        h.controller.updateTarget(null, SessionMode.SERVER_DEMO, true)
        assertFalse(h.audio.capturing)
        h.controller.updateTarget("active", SessionMode.SERVER_DEMO, true); runCurrent()
        assertEquals(CallStatus.ACTIVE, h.controller.state.value.status)
        assertTrue(h.controller.state.value.muted)
        assertFalse(h.audio.capturing)
        assertEquals(1, h.audio.captureStarts)
        h.audio.input.send(frame(8)); runCurrent()
        assertTrue(h.transport.channels.last().sent.isEmpty())
        h.controller.stop()
    }

    @Test fun aDifferentConnectionStartsUnmutedAfterTemporaryTargetLoss() = runTest {
        val h = Harness(this)
        h.start()
        h.controller.setMuted(true)
        h.controller.updateTarget(null, SessionMode.SERVER_DEMO, true)
        h.controller.updateTarget("new-connection", SessionMode.SERVER_DEMO, true); runCurrent()
        assertFalse(h.controller.state.value.muted)
        assertTrue(h.audio.capturing)
        assertEquals(2, h.audio.captureStarts)
        h.controller.stop()
    }

    @Test fun deniedPermissionReceivesWithoutCapture() = runTest {
        val h = Harness(this)
        h.controller.updateTarget("denied", SessionMode.SERVER_DEMO, false); runCurrent()
        assertEquals(MicrophoneStatus.DENIED, h.controller.state.value.microphone)
        assertEquals(0, h.audio.captureStarts)
        repeat(4) { h.transport.channels.single().input.send(frame(4)) }
        advanceTimeBy(100); runCurrent()
        assertEquals(4, h.audio.played.size)
        h.controller.stop()
    }

    @Test fun noPermissionPromptWhileRestrictedOrUnknown() = runTest {
        val h = Harness(this)
        for (policy in listOf(DrivingPolicy.RESTRICTED, DrivingPolicy.UNKNOWN)) {
            h.policy = policy
            assertFalse(h.controller.requestMicrophone())
        }
        assertEquals(0, h.permissionRequests)
        h.policy = DrivingPolicy.ALLOWED
        assertTrue(h.controller.requestMicrophone())
        assertEquals(1, h.permissionRequests)
        assertEquals(MicrophoneStatus.AVAILABLE, h.controller.state.value.microphone)
    }

    @Test fun captureFailureIsCalmAndCallKeepsReceiving() = runTest {
        val h = Harness(this)
        h.audio.failCapture = true
        h.start()
        assertEquals(MicrophoneStatus.UNAVAILABLE, h.controller.state.value.microphone)
        assertEquals(CallStatus.ACTIVE, h.controller.state.value.status)
        repeat(4) { h.transport.channels.single().input.send(frame(5)) }
        advanceTimeBy(100); runCurrent()
        assertEquals(4, h.audio.played.size)
        assertTrue(h.service.running)
        h.controller.stop()
    }

    @Test fun foregroundMicrophoneRejectionDoesNotOpenCapture() = runTest {
        val h = Harness(this)
        h.service.allowMicrophone = false
        h.start()
        assertEquals(MicrophoneStatus.UNAVAILABLE, h.controller.state.value.microphone)
        assertEquals(CallStatus.ACTIVE, h.controller.state.value.status)
        assertEquals(0, h.audio.captureStarts)
        h.controller.stop()
    }

    @Test fun permissionRevocationImmediatelyClosesCapture() = runTest {
        val h = Harness(this)
        h.start()
        h.controller.updateTarget("active", SessionMode.SERVER_DEMO, false)
        assertFalse(h.audio.capturing)
        assertEquals(MicrophoneStatus.DENIED, h.controller.state.value.microphone)
        assertFalse(h.service.microphone)
        h.controller.stop()
    }

    @Test fun liveConnectionRejoinsAfterNetworkBlip() = runTest {
        val h = Harness(this)
        h.start()
        h.transport.channels.single().input.close(); runCurrent()
        assertEquals(CallStatus.RECONNECTING, h.controller.state.value.status)
        assertFalse(h.audio.capturing)
        advanceTimeBy(1_001); runCurrent()
        assertEquals(2, h.transport.channels.size)
        assertEquals(CallStatus.ACTIVE, h.controller.state.value.status)
        assertTrue(h.audio.capturing)
        h.controller.stop()
    }

    @Test fun stoppingDuringPendingTransportOpenDoesNotWaitForNetwork() = runTest {
        val h = Harness(this)
        h.transport.gate = CompletableDeferred()
        h.controller.updateTarget("waiting", SessionMode.SERVER_DEMO, true); runCurrent()
        assertEquals(CallStatus.CONNECTING, h.controller.state.value.status)
        h.controller.stop()
        assertEquals(CallStatus.IDLE, h.controller.state.value.status)
        assertFalse(h.service.running)
        assertFalse(h.audio.focus)
        assertEquals(0, h.audio.captureStarts)
    }

    @Test fun restrictedAllowsMuteUnknownBlocksIt() = runTest {
        val h = Harness(this)
        h.start()
        h.policy = DrivingPolicy.RESTRICTED
        h.controller.setMuted(true)
        assertTrue(h.controller.state.value.muted)
        h.policy = DrivingPolicy.UNKNOWN
        h.controller.setMuted(false)
        assertTrue(h.controller.state.value.muted)
        h.controller.stop()
    }

    @Test fun transientFocusLossAndGainResumesAnUnmutedCall() = runTest {
        val h = Harness(this)
        h.start()
        h.audio.focusAvailable.value = false; runCurrent()
        assertFalse(h.audio.capturing)
        assertEquals(CallStatus.ACTIVE, h.controller.state.value.status)
        h.audio.focusAvailable.value = true; runCurrent()
        assertTrue(h.audio.capturing)
        assertEquals(2, h.audio.captureStarts)
        h.audio.input.send(frame(9)); runCurrent()
        assertEquals(1, h.transport.channels.single().sent.size)
        h.controller.stop()
    }

    @Test fun focusGainDoesNotOpenMutedOrDeniedMicrophone() = runTest {
        val h = Harness(this)
        h.start()
        h.controller.setMuted(true)
        h.audio.focusAvailable.value = false; runCurrent()
        h.audio.focusAvailable.value = true; runCurrent()
        assertFalse(h.audio.capturing)
        assertEquals(1, h.audio.captureStarts)
        h.controller.updateTarget("active", SessionMode.SERVER_DEMO, false)
        h.controller.setMuted(false)
        h.audio.focusAvailable.value = false; runCurrent()
        h.audio.focusAvailable.value = true; runCurrent()
        assertFalse(h.audio.capturing)
        assertEquals(1, h.audio.captureStarts)
        h.controller.stop()
    }

    @Test fun malformedFramesAreNotPlayedOrSent() = runTest {
        val h = Harness(this)
        h.start()
        h.audio.input.send(ByteArray(1))
        h.transport.channels.single().input.send(ByteArray(1))
        advanceTimeBy(100); runCurrent()
        assertTrue(h.audio.played.isEmpty())
        assertTrue(h.transport.channels.single().sent.isEmpty())
        h.controller.stop()
    }

    @Test fun jitterBufferDropsOldestUnderBurst() = runTest {
        val h = Harness(this)
        h.start()
        val input = h.transport.channels.single().input
        input.send(frame(0)); runCurrent()
        repeat(20) { input.send(frame(it + 1)) }
        runCurrent()
        advanceTimeBy(250); runCurrent()
        assertEquals(listOf(16, 17, 18, 19, 20), h.audio.played.map { it[0].toInt() })
        h.controller.stop()
    }

    @Test fun tenSecondsAtFiftyFramesPerSecondHasNoDropOrPlaybackGap() = runTest {
        val h = Harness(this)
        h.audio.playMillis = 20
        h.start()
        val input = h.transport.channels.single().input
        backgroundScope.launch {
            repeat(500) { index ->
                input.send(indexedFrame(index))
                delay(20)
            }
        }
        advanceTimeBy(10_200); runCurrent()
        assertEquals((0 until 500).toList(), h.audio.played.map(::frameIndex))
        assertEquals(60L, h.audio.playTimes.first())
        assertTrue(h.audio.playTimes.zipWithNext().all { (left, right) -> right - left == 20L })
        h.controller.stop()
    }

    @Test fun underrunWaitsForFourFramesBeforePlaybackResumes() = runTest {
        val h = Harness(this)
        h.audio.playMillis = 20
        h.start()
        val input = h.transport.channels.single().input
        repeat(4) { input.send(indexedFrame(it)) }
        advanceTimeBy(200); runCurrent()
        assertEquals(4, h.audio.played.size)
        repeat(3) { input.send(indexedFrame(it + 4)); advanceTimeBy(20); runCurrent() }
        assertEquals(4, h.audio.played.size)
        input.send(indexedFrame(7)); runCurrent()
        assertEquals(5, h.audio.played.size)
        advanceTimeBy(80); runCurrent()
        assertEquals((0 until 8).toList(), h.audio.played.map(::frameIndex))
        assertEquals(listOf(260L, 280L, 300L, 320L), h.audio.playTimes.drop(4))
        h.controller.stop()
    }

    @Test fun serverDemoPeerUsesScriptWithoutNetworkOrMicrophone() = runTest {
        val h = Harness(this)
        val peer = peer(ProfileKind.DEMO_PEER)
        h.controller.updateTarget("demo-server", SessionMode.SERVER_DEMO, true, peer); runCurrent()
        assertEquals(listOf(peer), h.demo.started)
        assertTrue(h.transport.channels.isEmpty())
        assertEquals(0, h.audio.captureStarts)
        assertFalse(h.service.microphone)
        assertEquals(MicrophoneStatus.NOT_USED, h.controller.state.value.microphone)
        h.controller.stop()
        assertEquals(1, h.demo.stops)
        assertTrue(h.demo.stoppedWithFocus)
    }

    @Test fun realServerPeerStillUsesVoiceTransportAndCapture() = runTest {
        val h = Harness(this)
        h.controller.updateTarget("real-server", SessionMode.SERVER_DEMO, true, peer(ProfileKind.VIRTUAL_PROFILE)); runCurrent()
        assertTrue(h.demo.started.isEmpty())
        assertEquals(1, h.transport.channels.size)
        assertEquals(1, h.audio.captureStarts)
        h.controller.stop()
    }

    @Test fun demoWaitsForFocusAndMutesOnlyTheLocalSide() = runTest {
        val h = Harness(this)
        h.audio.focusAvailable.value = false
        h.controller.updateTarget("solo", SessionMode.SOLO, true, peer(ProfileKind.DEMO_PEER)); runCurrent()
        assertTrue(h.demo.started.isEmpty())
        h.audio.focusAvailable.value = true; runCurrent()
        assertEquals(1, h.demo.started.size)
        val focusEventsBeforeMute = h.demo.focusEvents.toList()
        h.controller.setMuted(true); runCurrent()
        assertEquals(0, h.demo.stops)
        assertEquals(focusEventsBeforeMute, h.demo.focusEvents)
        h.audio.focusAvailable.value = false; runCurrent()
        assertFalse(h.demo.focusEvents.last())
        h.audio.focusAvailable.value = true; runCurrent()
        assertTrue(h.demo.focusEvents.last())
        assertEquals(1, h.demo.started.size)
        h.controller.stop()
        assertTrue(h.demo.stoppedWithFocus)
    }

    @Test fun everyDemoEndStopsSpeechBeforeReleasingFocus() = runTest {
        val h = Harness(this)
        for ((index, end) in (ConnectionEndReason.entries.map { it.name } + listOf("CHECKING", "HOME_PROTECTED", "SIGNED_OUT")).withIndex()) {
            h.controller.updateTarget(end, SessionMode.SOLO, true, peer(ProfileKind.DEMO_PEER)); runCurrent()
            h.controller.updateTarget(null, SessionMode.SOLO, true)
            assertEquals(index + 1, h.demo.stops)
            assertTrue(h.demo.stoppedWithFocus)
            assertFalse(h.audio.focus)
        }
    }

    @Test fun automaticConnectionSpeaksOnlyItsAnnouncementOnceBeforeDemoAndStopsAtEnd() = runTest {
        val h = Harness(this)
        val text = "별보는밤님과 연결됐어요"
        h.controller.updateTarget("auto", SessionMode.SOLO, false, peer(ProfileKind.DEMO_PEER), text)
        runCurrent()
        h.controller.updateTarget("auto", SessionMode.SOLO, false, peer(ProfileKind.DEMO_PEER), text)
        runCurrent()
        assertEquals(listOf(text), h.announcement.spoken)
        assertEquals(1, h.demo.started.size)
        assertEquals(0, h.audio.captureStarts)
        h.controller.stop()
        assertTrue(h.announcement.releases > 0)
    }

    @Test fun automaticAnnouncementWaitsForFocusAndIsCancelledWithCall() = runTest {
        val h = Harness(this)
        h.audio.focusAvailable.value = false
        h.controller.updateTarget("auto", SessionMode.SOLO, false, peer(ProfileKind.DEMO_PEER), "별보는밤님과 연결됐어요")
        runCurrent()
        assertTrue(h.announcement.spoken.isEmpty())
        h.controller.stop()
        h.audio.focusAvailable.value = true
        runCurrent()
        assertTrue(h.announcement.spoken.isEmpty())
    }

    private class FakeAnnouncement : DemoSpeechEngine {
        val spoken = mutableListOf<String>()
        var releases = 0
        override suspend fun prepare() = true
        override suspend fun speak(text: String): Boolean { spoken += text; return true }
        override fun stop() = Unit
        override fun release() { releases++ }
    }

    private class Harness(val scope: TestScope) {
        val audio = FakeAudio { scope.testScheduler.currentTime }
        val demo = FakeDemoVoice(audio)
        val announcement = FakeAnnouncement()
        val service = FakeService()
        val transport = FakeTransport()
        var policy = DrivingPolicy.ALLOWED
        var permissionRequests = 0
        val controller = VoiceCallController(scope.backgroundScope, transport, audio, service, demo,
            currentPolicy = { policy }, permissionRequest = { permissionRequests++; true }, announcementSpeech = announcement)
        suspend fun start(id: String = "active") {
            controller.updateTarget(id, SessionMode.SERVER_DEMO, true)
            scope.runCurrent()
        }
    }

    private class FakeAudio(val now: () -> Long) : VoiceAudio {
        override val focusAvailable = MutableStateFlow(true)
        var focus = false
        var communicationMode = false
        var capturing = false
        var captureStarts = 0
        var failCapture = false
        val input = Channel<ByteArray>(Channel.UNLIMITED)
        val played = mutableListOf<ByteArray>()
        val playTimes = mutableListOf<Long>()
        var playMillis = 0L
        override suspend fun start() { focus = true; communicationMode = true }
        override fun capture(): Flow<ByteArray> = flow {
            captureStarts++
            if (failCapture) error("No microphone")
            capturing = true
            try { for (frame in input) emit(frame) } finally { capturing = false }
        }
        override fun stopCapture() { capturing = false }
        override suspend fun play(frame: ByteArray) { played += frame; playTimes += now(); delay(playMillis) }
        override suspend fun stop() { capturing = false; focus = false; communicationMode = false }
    }

    private class FakeDemoVoice(private val audio: FakeAudio) : DemoPeerVoice {
        val started = mutableListOf<PublicProfile>()
        val focusEvents = mutableListOf<Boolean>()
        var stops = 0
        var stoppedWithFocus = false
        override suspend fun start(peer: PublicProfile, connectionId: String) { started += peer }
        override fun setFocusAvailable(available: Boolean) { focusEvents += available }
        override suspend fun stop() { stops++; stoppedWithFocus = audio.focus }
    }

    private class FakeService : CallService {
        var running = false
        var microphone = false
        var allowMicrophone = true
        override suspend fun start(microphone: Boolean): Boolean {
            running = true
            this.microphone = microphone
            return allowMicrophone
        }
        override suspend fun stop() { running = false }
    }

    private class FakeTransport : VoiceTransport {
        val channels = mutableListOf<FakeChannel>()
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun open(connectionId: String): VoiceChannel {
            gate?.await()
            return FakeChannel().also { channels += it }
        }
    }

    private class FakeChannel : VoiceChannel {
        val input = Channel<ByteArray>(Channel.UNLIMITED)
        override val incoming = input.receiveAsFlow()
        val sent = mutableListOf<ByteArray>()
        var closed = false
        override suspend fun send(frame: ByteArray) { sent += frame }
        override suspend fun close() { closed = true; input.close() }
    }

    companion object {
        private fun peer(kind: ProfileKind) = PublicProfile("peer", 1, "새벽", AvatarId.DAWN, emptySet(), kind, null)
        private fun indexedFrame(index: Int) = ByteArray(VoiceFormat.FRAME_BYTES).also {
            it[0] = index.toByte(); it[1] = (index shr 8).toByte()
        }
        private fun frameIndex(frame: ByteArray): Int = (frame[0].toInt() and 255) or ((frame[1].toInt() and 255) shl 8)
        private fun frame(value: Int) = ByteArray(VoiceFormat.FRAME_BYTES) { value.toByte() }
    }
}
