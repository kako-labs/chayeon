package dev.chayeon.data.voice

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import dev.chayeon.core.model.AvatarId
import dev.chayeon.core.model.ProfileKind
import dev.chayeon.core.model.PublicProfile
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class DemoCallClipsTest {
    private val names = listOf("별보는밤", "재즈한잔", "느린오후", "바다보러가요", "숲길산책")
    private val peer = PublicProfile("demo-star", 1, "별보는밤", AvatarId.STAR, emptySet(), ProfileKind.DEMO_PEER, null)

    @Test fun everyScriptedLineHasItsOwnBundledClip() {
        val resources = ApplicationProvider.getApplicationContext<Application>().resources
        val clips = names.flatMap { name ->
            DemoCallScripts.lines(peer.copy(nickname = name)).map { line ->
                assertNotNull(DemoCallScripts.clip(line), line)
            }
        }
        assertEquals(15, clips.size)
        assertEquals(15, clips.toSet().size)
        clips.forEach { clip -> assertTrue(resources.openRawResource(clip).use { it.readBytes().size } > 4_000) }
    }

    @Test fun otherTextHasNoClip() {
        assertNull(DemoCallScripts.clip("별보는밤님과 연결됐어요"))
        assertNull(DemoCallScripts.clip(""))
    }
}
