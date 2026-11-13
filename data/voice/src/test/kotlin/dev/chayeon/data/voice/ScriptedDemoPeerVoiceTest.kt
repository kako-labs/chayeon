package dev.chayeon.data.voice

import dev.chayeon.core.model.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.*
import org.junit.Test
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ScriptedDemoPeerVoiceTest {
    private val peer = PublicProfile("demo-star", 1, "별보는밤", AvatarId.STAR,
        setOf(InterestId.NIGHT_VIEW, InterestId.FILM_PHOTOGRAPHY), ProfileKind.DEMO_PEER, null)

    @Test fun allFivePeersHaveThreeShortPublicProfileLines() {
        val names = listOf("별보는밤", "재즈한잔", "느린오후", "바다보러가요", "숲길산책")
        names.forEach { name ->
            val lines = DemoCallScripts.lines(peer.copy(nickname = name))
            assertEquals(3, lines.size)
            assertTrue(name in lines.first())
            assertTrue(lines.all { it.length <= 30 })
            assertTrue(lines.none { line -> listOf("시연", "가상", "합성", "AI", "테스트").any { it in line } })
        }
        assertTrue(DemoCallScripts.lines(peer.copy(nickname = "새 친구")).isEmpty())
    }

    @Test fun warmsImmediatelyThenPlaysScriptInOrderAtNaturalIntervalsAndFallsSilent() = runTest {
        val speech = FakeSpeech(this)
        val voice = ScriptedDemoPeerVoice(backgroundScope, speech) { testScheduler.currentTime }
        voice.setFocusAvailable(true)
        voice.start(peer)
        runCurrent()
        assertEquals(1, speech.prepares)
        assertEquals(listOf(0L), speech.times)
        advanceTimeBy(20_000); runCurrent()
        assertEquals(listOf(0L, 20_000L), speech.times)
        advanceTimeBy(20_000); runCurrent()
        assertEquals(DemoCallScripts.lines(peer), speech.lines)
        advanceTimeBy(120_000); runCurrent()
        assertEquals(3, speech.lines.size)
        voice.stop()
    }

    @Test fun everyEndAndProtectionStopsSpeechAndNoLaterLinesRun() = runTest {
        for (reason in ConnectionEndReason.entries.map { it.name } + listOf("ACCOUNT_DELETED", "HOME_PROTECTED", "EXPIRED", "CHECKING")) {
            val speech = FakeSpeech(this)
            val voice = ScriptedDemoPeerVoice(backgroundScope, speech) { testScheduler.currentTime }
            voice.setFocusAvailable(true)
            voice.start(peer); runCurrent()
            assertTrue(speech.playing, reason)
            voice.stop()
            assertFalse(speech.playing, reason)
            assertTrue(speech.releases > 0, reason)
            advanceTimeBy(90_000); runCurrent()
            assertEquals(1, speech.lines.size, reason)
        }
    }

    @Test fun unavailableInitializationAndUtteranceFailureStaySilentWithoutRetry() = runTest {
        for (stage in listOf("prepare", "speak", "exception")) {
            val speech = FakeSpeech(this, failure = stage)
            val voice = ScriptedDemoPeerVoice(backgroundScope, speech) { testScheduler.currentTime }
            voice.setFocusAvailable(true)
            voice.start(peer); runCurrent()
            advanceTimeBy(120_000); runCurrent()
            assertEquals(if (stage == "prepare") 0 else 1, speech.lines.size)
            assertFalse(speech.playing)
            voice.stop()
        }
    }

    @Test fun focusLossStopsSpeechAndRemainingLinesWaitForFocus() = runTest {
        val speech = FakeSpeech(this)
        val voice = ScriptedDemoPeerVoice(backgroundScope, speech) { testScheduler.currentTime }
        voice.start(peer); runCurrent()
        assertEquals(1, speech.prepares)
        assertTrue(speech.lines.isEmpty())
        voice.setFocusAvailable(true); runCurrent()
        voice.setFocusAvailable(false)
        assertFalse(speech.playing)
        advanceTimeBy(60_000); runCurrent()
        assertEquals(1, speech.lines.size)
        voice.setFocusAvailable(true); runCurrent()
        assertEquals(2, speech.lines.size)
        voice.stop()
    }

    @Test fun rapidFocusLossAndGainKeepsRemainingLines() = runTest {
        val speech = FakeSpeech(this)
        val voice = ScriptedDemoPeerVoice(backgroundScope, speech) { testScheduler.currentTime }
        voice.setFocusAvailable(true)
        voice.start(peer); runCurrent()
        voice.setFocusAvailable(false)
        voice.setFocusAvailable(true)
        runCurrent()
        advanceTimeBy(20_000); runCurrent()
        assertEquals(DemoCallScripts.lines(peer).take(2), speech.lines)
        voice.stop()
    }

    @Test fun slowSuccessfulSpeechStillKeepsTwentySecondStartCadence() = runTest {
        val speech = FakeSpeech(this, speakMillis = 8_000)
        val voice = ScriptedDemoPeerVoice(backgroundScope, speech) { testScheduler.currentTime }
        voice.setFocusAvailable(true)
        voice.start(peer); runCurrent()
        advanceTimeBy(40_000); runCurrent()
        assertEquals(listOf(0L, 20_000L, 40_000L), speech.times)
        voice.stop()
    }

    @Test fun endingWhileWarmingPreventsLateGreeting() = runTest {
        val speech = FakeSpeech(this, warmMillis = 4_500)
        val voice = ScriptedDemoPeerVoice(backgroundScope, speech) { testScheduler.currentTime }
        voice.setFocusAvailable(true)
        voice.start(peer); runCurrent()
        voice.stop()
        advanceTimeBy(60_000); runCurrent()
        assertTrue(speech.lines.isEmpty())
    }

    private class FakeSpeech(
        private val scope: TestScope,
        private val failure: String? = null,
        private val warmMillis: Long = 0,
        private val speakMillis: Long = 3_000,
    ) : DemoSpeechEngine {
        var prepares = 0
        var releases = 0
        var playing = false
        private var pending: CompletableDeferred<Boolean>? = null
        val lines = mutableListOf<String>()
        val times = mutableListOf<Long>()
        override suspend fun prepare(): Boolean {
            prepares++
            delay(warmMillis)
            return failure != "prepare"
        }
        override suspend fun speak(text: String): Boolean {
            lines += text
            times += scope.testScheduler.currentTime
            if (failure == "exception") throw IllegalStateException("Unavailable")
            if (failure == "speak") return false
            playing = true
            val result = CompletableDeferred<Boolean>().also { pending = it }
            return try { withTimeoutOrNull(speakMillis) { result.await() } ?: true }
            finally { playing = false; pending = null }
        }
        override fun stop() { pending?.complete(false); playing = false }
        override fun release() { releases++; playing = false }
    }
}
