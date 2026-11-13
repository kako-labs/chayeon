package dev.chayeon.data.voice

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Only fixed public-profile scripts enter this on-device engine. */
class PleosSpeechEngine(context: Context) : DemoSpeechEngine {
    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null
    private var events: EventListener? = null
    private var generation = 0
    private var initialized: CompletableDeferred<Boolean>? = null
    private var utterance: CompletableDeferred<Boolean>? = null

    override suspend fun prepare(): Boolean = withContext(Dispatchers.Main.immediate) {
        release()
        val token = generation
        val ready = CompletableDeferred<Boolean>().also { initialized = it }
        try {
            val current = TextToSpeech(context, Mode.ON_DEVICE).also { engine = it }
            val listener = object : EventListener {
                override fun onReady() = Unit
                override fun onStart() = Unit
                override fun onUpdatedRms(rms: Double) = Unit
                override fun onStop() { main.post { if (token == generation) utterance?.complete(false) } }
                override fun onDone() { main.post { if (token == generation) utterance?.complete(true) } }
                override fun onError(errorType: ErrorType) {
                    main.post { if (token == generation) utterance?.complete(false) }
                }
            }.also { events = it }
            current.initialize(object : OnInitializeListener {
                override fun onSuccess() { main.post { if (token == generation) ready.complete(true) } }
                override fun onFailure(errorType: ErrorType) { main.post { if (token == generation) ready.complete(false) } }
            })
            current.addEventListener(listener)
            current.setLocale(Locale.KOREA)
            current.setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            withTimeoutOrNull(10_000) { ready.await() } ?: false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: RuntimeException) {
            false
        }
    }

    override suspend fun speak(text: String): Boolean = withContext(Dispatchers.Main.immediate) {
        val current = engine ?: return@withContext false
        val result = CompletableDeferred<Boolean>().also { utterance = it }
        try {
            current.speak(text, true)
            withTimeoutOrNull(10_000) { result.await() } ?: false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: RuntimeException) {
            false
        } finally {
            if (utterance === result) utterance = null
            runCatching { current.stop() }
        }
    }

    override fun stop() {
        utterance?.complete(false)
        utterance = null
        runCatching { engine?.stop() }
    }

    override fun release() {
        generation++
        initialized?.complete(false)
        initialized = null
        stop()
        val current = engine
        engine = null
        events?.let { runCatching { current?.removeEventListener(it) } }
        events = null
        runCatching { current?.release() }
    }
}
