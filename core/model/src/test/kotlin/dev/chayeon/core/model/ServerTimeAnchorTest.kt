package dev.chayeon.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerTimeAnchorTest {
    @Test
    fun `five minute fast wall clock does not expire a two minute request`() {
        val serverNow = 1_800_000_000_000L
        val deviceWallClock = serverNow + 300_000L
        val deadline = serverNow + 120_000L
        val anchor = ServerTimeAnchor(serverNow, 50_000L)

        assertTrue(deadline < deviceWallClock)
        assertEquals(120_000L, anchor.remainingMillisUntil(deadline, 50_000L))
        assertEquals(90_000L, anchor.remainingMillisUntil(deadline, 80_000L))
    }

    @Test
    fun `five minute slow wall clock does not extend the countdown`() {
        val serverNow = 1_800_000_000_000L
        val deviceWallClock = serverNow - 300_000L
        val deadline = serverNow + 120_000L
        val anchor = ServerTimeAnchor(serverNow, 50_000L)

        assertEquals(420_000L, deadline - deviceWallClock)
        assertEquals(120_000L, anchor.remainingMillisUntil(deadline, 50_000L))
        assertEquals(0L, anchor.remainingMillisUntil(deadline, 170_000L))
    }

    @Test
    fun `missing anchor or restarted monotonic clock leaves the countdown unknown`() {
        val unavailable: ServerTimeAnchor? = null
        val anchor = ServerTimeAnchor(1_000L, 20_000L)

        assertNull(unavailable?.remainingMillisUntil(2_000L, 25_000L))
        assertNull(anchor.remainingMillisUntil(2_000L, 10L))
        assertNull(anchor.remainingMillisUntil(-1L, 20_000L))
    }

    @Test
    fun `past deadlines stay at zero and large timestamps do not overflow`() {
        val anchor = ServerTimeAnchor(Long.MAX_VALUE - 100L, 10L)

        assertEquals(0L, anchor.remainingMillisUntil(Long.MAX_VALUE - 101L, 10L))
        assertEquals(100L, anchor.remainingMillisUntil(Long.MAX_VALUE, 10L))
        assertEquals(0L, anchor.remainingMillisUntil(Long.MAX_VALUE, Long.MAX_VALUE))
    }
}
