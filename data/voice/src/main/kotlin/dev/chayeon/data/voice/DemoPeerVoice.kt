package dev.chayeon.data.voice

import dev.chayeon.core.model.PublicProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

interface DemoPeerVoice {
    suspend fun start(peer: PublicProfile)
    fun setFocusAvailable(available: Boolean)
    suspend fun stop()
}

interface DemoSpeechEngine {
    suspend fun prepare(): Boolean
    suspend fun speak(text: String): Boolean
    fun stop()
    fun release()
}

/** Fixed public-profile lines of the demo partners; each has a bundled clip made by scripts/demo_voice.py. */
object DemoCallScripts {
    private val scripts: Map<String, List<Pair<String, Int>>> = mapOf(
        "별보는밤" to listOf(
            "안녕하세요, 별보는밤이에요." to R.raw.demo_voice_star_1,
            "밤 풍경을 사진에 담는 걸 좋아해요." to R.raw.demo_voice_star_2,
            "사진에 남기고 싶은 순간이 있나요?" to R.raw.demo_voice_star_3),
        "재즈한잔" to listOf(
            "안녕하세요, 재즈한잔이에요." to R.raw.demo_voice_jazz_1,
            "재즈를 들으면 마음이 편안해져요." to R.raw.demo_voice_jazz_2,
            "요즘 자주 듣는 곡이 있나요?" to R.raw.demo_voice_jazz_3),
        "느린오후" to listOf(
            "안녕하세요, 느린오후예요." to R.raw.demo_voice_slow_1,
            "잔잔한 음악과 커피를 좋아해요." to R.raw.demo_voice_slow_2,
            "어떤 커피를 즐겨 마시나요?" to R.raw.demo_voice_slow_3),
        "바다보러가요" to listOf(
            "안녕하세요, 바다보러가요라고 해요." to R.raw.demo_voice_sea_1,
            "여행 이야기를 듣는 게 좋아요." to R.raw.demo_voice_sea_2,
            "서핑을 해 본 적이 있나요?" to R.raw.demo_voice_sea_3),
        "숲길산책" to listOf(
            "안녕하세요, 숲길산책이에요." to R.raw.demo_voice_forest_1,
            "캠핑하며 쉬는 시간을 좋아해요." to R.raw.demo_voice_forest_2,
            "캠핑할 때 꼭 챙기는 게 있나요?" to R.raw.demo_voice_forest_3),
    )
    private val clips: Map<String, Int> = scripts.values.flatten().toMap()

    fun lines(peer: PublicProfile): List<String> = scripts[peer.nickname].orEmpty().map { it.first }

    /** The bundled clip for a scripted line, or null for any other text. */
    fun clip(text: String): Int? = clips[text]
}

/** The SDK's start callback precedes sound, so no speaking indicator is exposed. */
class ScriptedDemoPeerVoice(
    private val scope: CoroutineScope,
    private val speech: DemoSpeechEngine,
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 },
) : DemoPeerVoice {
    private val focus = MutableStateFlow(false)
    private var job: Job? = null
    private var focusInterruptions = 0L

    override suspend fun start(peer: PublicProfile) {
        stop()
        job = scope.launch {
            try {
                if (!speech.prepare()) return@launch
                val lines = DemoCallScripts.lines(peer)
                for ((index, line) in lines.withIndex()) {
                    focus.first { it }
                    val interruption = focusInterruptions
                    val startedAt = nowMillis()
                    val completed = speech.speak(line)
                    if (!completed && interruption == focusInterruptions) return@launch
                    if (index < lines.lastIndex) delay((20_000 - (nowMillis() - startedAt)).coerceAtLeast(0))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: RuntimeException) {
                speech.stop()
            }
        }
    }

    override fun setFocusAvailable(available: Boolean) {
        if (focus.value == available) return
        focus.value = available
        if (!available) {
            focusInterruptions++
            speech.stop()
        }
    }

    override suspend fun stop() {
        job?.cancelAndJoin()
        job = null
        speech.stop()
        speech.release()
    }
}
