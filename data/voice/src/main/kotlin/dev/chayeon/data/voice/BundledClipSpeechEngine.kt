package dev.chayeon.data.voice

import androidx.annotation.RawRes
import kotlinx.coroutines.CancellationException

enum class ClipResult { COMPLETED, STOPPED, FAILED }

/** Plays one bundled clip to its end; FAILED means it could not be loaded or played. */
interface ClipPlayer {
    suspend fun play(@RawRes clip: Int): ClipResult
    fun stop()
    fun release()
}

/**
 * Speaks scripted demo lines from bundled clips and uses the fallback engine (on-device TTS) only
 * for lines without a clip or when a clip cannot be played. The fallback is prepared on first need.
 */
class BundledClipSpeechEngine(
    private val player: ClipPlayer,
    private val fallback: DemoSpeechEngine,
    private val clipFor: (String) -> Int? = DemoCallScripts::clip,
) : DemoSpeechEngine {
    private var fallbackReady = false
    private var stops = 0L

    override suspend fun prepare(): Boolean = true

    override suspend fun speak(text: String): Boolean {
        val stopsAtStart = stops
        val clip = clipFor(text)
        if (clip != null) {
            when (player.play(clip)) {
                ClipResult.COMPLETED -> return true
                ClipResult.STOPPED -> return false
                ClipResult.FAILED -> if (stops != stopsAtStart) return false
            }
        }
        if (!fallbackReady) fallbackReady = prepareFallback()
        if (!fallbackReady || stops != stopsAtStart) return false
        return fallback.speak(text)
    }

    override fun stop() {
        stops++
        player.stop()
        fallback.stop()
    }

    override fun release() {
        stop()
        player.release()
        fallback.release()
        fallbackReady = false
    }

    private suspend fun prepareFallback(): Boolean = try {
        fallback.prepare()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: RuntimeException) {
        false
    }
}
