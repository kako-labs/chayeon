package dev.chayeon.server

import dev.chayeon.core.model.VoiceFormat
import org.junit.Assert.*
import org.junit.Test

class VoiceRelayTest {
    private val a = VoiceIdentity("room", "call", "a")
    private val b = VoiceIdentity("room", "call", "b")
    private val members = mutableMapOf("a" to a, "b" to b)
    private val relay = VoiceRelay { header, connection -> members[header]?.takeIf { it.connection == connection } }

    @Test fun onlyParticipantsJoinAndFramesOnlyReachTheOtherParticipant() {
        val first = relay.join("a", "call")!!
        val second = relay.join("b", "call")!!
        assertNull(relay.join("stranger", "call"))
        assertNull(relay.join("a", "missing"))
        val bytes = ByteArray(VoiceFormat.FRAME_BYTES) { 42 }
        assertTrue(relay.relay(first, bytes))
        assertArrayEquals(bytes, second.frames.tryReceive().getOrThrow())
        assertTrue(first.frames.tryReceive().isFailure)
    }

    @Test fun boundedQueueDropsOldestAndNeverRetainsMoreThanOneHundredMilliseconds() {
        val first = relay.join("a", "call")!!
        val second = relay.join("b", "call")!!
        val start = System.nanoTime()
        repeat(20) { index -> assertTrue(relay.relay(first, ByteArray(640) { index.toByte() }, start + index * 20_000_000L)) }
        assertEquals((15..19).toList(), generateSequence { second.frames.tryReceive().getOrNull()?.first()?.toInt() }.toList())
    }

    @Test fun wrongSizeFramesCloseImmediately() {
        for (size in listOf(0, 1, 639, 641, 64_000)) {
            val first = relay.join("a", "call")!!
            assertFalse(relay.relay(first, ByteArray(size)))
            assertTrue(first.ended.isCompleted)
        }
    }

    @Test fun stallBurstOfFiftyFramesIsForwardedAndOverrateDropsWithoutClosing() {
        val first = relay.join("a", "call")!!
        val second = relay.join("b", "call")!!
        val now = System.nanoTime() + 1_000_000_000L
        repeat(50) {
            assertTrue(relay.relay(first, ByteArray(640), now))
            assertNotNull(second.frames.tryReceive().getOrNull())
        }
        repeat(100) {
            assertTrue(relay.relay(first, ByteArray(640), now))
            assertTrue(second.frames.tryReceive().isFailure)
        }
        assertFalse(first.ended.isCompleted)
        assertTrue(relay.relay(first, ByteArray(640), now + 20_000_000L))
        assertNotNull(second.frames.tryReceive().getOrNull())
    }

    @Test fun reconnectRetainsBudgetAndDropsExcessWithoutClosing() {
        val first = relay.join("a", "call")!!
        val second = relay.join("b", "call")!!
        val now = System.nanoTime()
        repeat(50) {
            assertTrue(relay.relay(first, ByteArray(640), now))
            assertNotNull(second.frames.tryReceive().getOrNull())
        }
        relay.leave(first)
        val rejoined = relay.join("a", "call")!!
        assertTrue(relay.relay(rejoined, ByteArray(640), now))
        assertTrue(second.frames.tryReceive().isFailure)
        assertFalse(rejoined.ended.isCompleted)
    }

    @Test fun sustainedTrafficAboveTwiceTheRateClosesAfterFiveSeconds() {
        val first = relay.join("a", "call")!!
        val second = relay.join("b", "call")!!
        val start = System.nanoTime()
        var forwarded = 0
        var dropped = 0
        repeat(5) { secondIndex ->
            repeat(120) { index ->
                assertTrue("A five-second flood observation is required", relay.relay(first, ByteArray(640),
                    start + secondIndex * 1_000_000_000L + index * 1_000_000_000L / 120))
                if (second.frames.tryReceive().isSuccess) forwarded++ else dropped++
            }
        }
        assertTrue(forwarded > 0)
        assertTrue(dropped > 0)
        assertFalse(first.ended.isCompleted)
        assertFalse(relay.relay(first, ByteArray(640), start + 5_000_000_000L))
        assertTrue(first.ended.isCompleted)
    }

    @Test fun trafficAtTwiceTheRateAndInterruptedOverloadDoNotClose() {
        val exact = VoiceRateLimit(0)
        repeat(1_000) { index -> assertNotEquals(VoiceFrameDecision.CLOSE, exact.decide(640, index * 10_000_000L)) }
        val interrupted = VoiceRateLimit(0)
        repeat(4) { second -> repeat(120) { index ->
            assertNotEquals(VoiceFrameDecision.CLOSE, interrupted.decide(640, second * 1_000_000_000L + index * 1_000_000_000L / 120))
        } }
        assertNotEquals(VoiceFrameDecision.CLOSE, interrupted.decide(640, 6_000_000_000L))
        repeat(4) { second -> repeat(120) { index ->
            assertNotEquals(VoiceFrameDecision.CLOSE, interrupted.decide(640, (7L + second) * 1_000_000_000L + index * 1_000_000_000L / 120))
        } }
    }

    @Test fun revocationDropsQueuedAudioAndClosesBothWhileOrdinaryDisconnectAllowsRejoin() {
        val first = relay.join("a", "call")!!
        val second = relay.join("b", "call")!!
        relay.leave(first)
        assertFalse(second.ended.isCompleted)
        val replacement = relay.join("a", "call")!!
        assertTrue(relay.relay(replacement, ByteArray(640)))
        members.remove("a")
        relay.prune()
        assertTrue(replacement.ended.isCompleted)
        assertTrue(second.ended.isCompleted)
        assertTrue(second.frames.tryReceive().isFailure)
        assertNull(relay.join("a", "call"))
    }
}
