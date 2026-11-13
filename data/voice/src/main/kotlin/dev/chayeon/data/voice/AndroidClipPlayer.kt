package dev.chayeon.data.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Plays bundled raw clips on the call stream; nothing is recorded or written. */
class AndroidClipPlayer(context: Context) : ClipPlayer {
    private val context = context.applicationContext
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private var player: MediaPlayer? = null
    private var result: CompletableDeferred<ClipResult>? = null

    override suspend fun play(clip: Int): ClipResult = withContext(Dispatchers.Main.immediate) {
        stop()
        val done = CompletableDeferred<ClipResult>().also { result = it }
        val current = MediaPlayer()
        player = current
        try {
            current.setAudioAttributes(attributes)
            context.resources.openRawResourceFd(clip).use { current.setDataSource(it) }
            current.setOnCompletionListener { done.complete(ClipResult.COMPLETED) }
            current.setOnErrorListener { _, _, _ -> done.complete(ClipResult.FAILED); true }
            current.prepare()
            if (done.isCompleted) return@withContext done.await()
            current.start()
            withTimeoutOrNull(MAX_CLIP_MILLIS) { done.await() } ?: ClipResult.STOPPED
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (done.isCompleted) done.await() else ClipResult.FAILED
        } finally {
            if (result === done) result = null
            if (player === current) player = null
            runCatching { current.release() }
        }
    }

    override fun stop() {
        result?.complete(ClipResult.STOPPED)
        result = null
        val current = player ?: return
        runCatching { if (current.isPlaying) current.stop() }
    }

    override fun release() = stop()

    private companion object {
        const val MAX_CLIP_MILLIS = 10_000L
    }
}
