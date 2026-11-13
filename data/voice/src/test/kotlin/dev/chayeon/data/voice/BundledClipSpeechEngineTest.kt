package dev.chayeon.data.voice

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BundledClipSpeechEngineTest {
    private val clipLine = "안녕하세요, 별보는밤이에요."
    private val otherLine = "별보는밤님과 연결됐어요"
    private val clips = mapOf(clipLine to 7)

    @Test fun scriptedLinePlaysItsClipWithoutStartingTheFallback() = runTest {
        val player = FakeClipPlayer(ClipResult.COMPLETED)
        val tts = FakeTts()
        val engine = BundledClipSpeechEngine(player, tts, clips::get)
        assertTrue(engine.prepare())
        assertTrue(engine.speak(clipLine))
        assertEquals(listOf(7), player.played)
        assertEquals(0, tts.prepares)
        assertTrue(tts.spoken.isEmpty())
    }

    @Test fun lineWithoutClipUsesTheFallbackPreparedOnce() = runTest {
        val tts = FakeTts()
        val engine = BundledClipSpeechEngine(FakeClipPlayer(ClipResult.COMPLETED), tts, clips::get)
        assertTrue(engine.speak(otherLine))
        assertTrue(engine.speak(otherLine))
        assertEquals(1, tts.prepares)
        assertEquals(listOf(otherLine, otherLine), tts.spoken)
    }

    @Test fun clipThatCannotPlayFallsBackToTheSameLine() = runTest {
        val tts = FakeTts()
        val engine = BundledClipSpeechEngine(FakeClipPlayer(ClipResult.FAILED), tts, clips::get)
        assertTrue(engine.speak(clipLine))
        assertEquals(listOf(clipLine), tts.spoken)
    }

    @Test fun stoppedClipReportsNotCompletedAndNeverFallsBack() = runTest {
        val player = FakeClipPlayer(null)
        val tts = FakeTts()
        val engine = BundledClipSpeechEngine(player, tts, clips::get)
        val speaking = async { engine.speak(clipLine) }
        runCurrent()
        assertTrue(player.playing)
        engine.stop()
        assertFalse(speaking.await())
        assertFalse(player.playing)
        assertEquals(1, tts.stops)
        assertTrue(tts.spoken.isEmpty())
    }

    @Test fun stopWhileTheFallbackPreparesPreventsALateUtterance() = runTest {
        val gate = CompletableDeferred<Boolean>()
        val tts = FakeTts(prepareGate = gate)
        val engine = BundledClipSpeechEngine(FakeClipPlayer(ClipResult.COMPLETED), tts, clips::get)
        val speaking = async { engine.speak(otherLine) }
        runCurrent()
        engine.stop()
        gate.complete(true)
        assertFalse(speaking.await())
        assertTrue(tts.spoken.isEmpty())
    }

    @Test fun prepareSucceedsWithClipsEvenWhenTheFallbackCannotStart() = runTest {
        val tts = FakeTts(prepareResult = false)
        val engine = BundledClipSpeechEngine(FakeClipPlayer(ClipResult.COMPLETED), tts, clips::get)
        assertTrue(engine.prepare())
        assertTrue(engine.speak(clipLine))
        assertFalse(engine.speak(otherLine))
        assertTrue(tts.spoken.isEmpty())
    }

    @Test fun fallbackPrepareExceptionStaysSilent() = runTest {
        val tts = FakeTts(prepareThrows = true)
        val engine = BundledClipSpeechEngine(FakeClipPlayer(ClipResult.FAILED), tts, clips::get)
        assertFalse(engine.speak(clipLine))
        assertTrue(tts.spoken.isEmpty())
    }

    @Test fun releaseFreesThePlayerAndTheFallback() = runTest {
        val player = FakeClipPlayer(ClipResult.COMPLETED)
        val tts = FakeTts()
        val engine = BundledClipSpeechEngine(player, tts, clips::get)
        engine.speak(otherLine)
        engine.release()
        assertEquals(1, player.releases)
        assertEquals(1, tts.releases)
        engine.speak(otherLine)
        assertEquals(2, tts.prepares)
    }

    private class FakeClipPlayer(private val result: ClipResult?) : ClipPlayer {
        val played = mutableListOf<Int>()
        var playing = false
        var releases = 0
        private var pending: CompletableDeferred<ClipResult>? = null

        override suspend fun play(clip: Int): ClipResult {
            played += clip
            result?.let { return it }
            val done = CompletableDeferred<ClipResult>().also { pending = it }
            playing = true
            return done.await().also { playing = false }
        }

        override fun stop() {
            pending?.complete(ClipResult.STOPPED)
            pending = null
            playing = false
        }

        override fun release() { releases++ }
    }

    private class FakeTts(
        private val prepareResult: Boolean = true,
        private val prepareThrows: Boolean = false,
        private val prepareGate: CompletableDeferred<Boolean>? = null,
    ) : DemoSpeechEngine {
        var prepares = 0
        var stops = 0
        var releases = 0
        val spoken = mutableListOf<String>()

        override suspend fun prepare(): Boolean {
            prepares++
            if (prepareThrows) throw IllegalStateException("engine unavailable")
            return prepareGate?.await() ?: prepareResult
        }

        override suspend fun speak(text: String): Boolean {
            spoken += text
            return true
        }

        override fun stop() { stops++ }
        override fun release() { releases++ }
    }
}
