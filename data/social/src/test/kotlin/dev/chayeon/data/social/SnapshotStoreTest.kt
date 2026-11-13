package dev.chayeon.data.social

import dev.chayeon.core.model.*
import org.junit.Assert.*
import org.junit.Test

class SnapshotStoreTest {
    private var monotonic = 100L
    private val store = SnapshotStore { monotonic }

    @Test fun `full snapshots atomically replace collections and reject duplicates and reordering`() {
        val generation = store.begin("session-a")
        assertTrue(store.accept(store.beginRequest(generation)!!, snapshot(4).copy(discovery = DiscoveryState(candidates = listOf(candidate())))))
        assertFalse(store.accept(store.beginRequest(generation)!!, snapshot(3)))
        assertFalse(store.accept(store.beginRequest(generation)!!, snapshot(4).copy(discovery = DiscoveryState())))
        assertEquals(1, ready().discovery.candidates.size)
        assertTrue(store.accept(store.beginRequest(generation)!!, snapshot(5)))
        assertTrue(ready().discovery.candidates.isEmpty())
    }

    @Test fun `old session responses cannot replace state even with a larger revision`() {
        val old = store.begin("session-a")
        val current = store.begin("session-b")
        assertFalse(store.accept(SnapshotTicket(old, 0, 1), snapshot(100)))
        assertFalse(store.accept(store.beginRequest(current)!!, snapshot(100)))
        assertTrue(store.accept(store.beginRequest(current)!!, snapshot(1).copy(id = "session-b")))
        assertEquals("session-b", ready().id)
    }

    @Test fun `only current nonreordered heartbeat updates monotonic time anchor`() {
        val generation = store.begin("session-a")
        store.accept(store.beginRequest(generation)!!, snapshot(1))
        monotonic = 150
        assertFalse(store.accept(store.beginRequest(generation)!!, snapshot(1).copy(serverTimeEpochMillis = 1_100)))
        assertEquals(ServerTimeAnchor(1_100, 150), store.state.value.anchor)
        monotonic = 200
        store.accept(store.beginRequest(generation)!!, snapshot(1).copy(serverTimeEpochMillis = 900))
        assertEquals(ServerTimeAnchor(1_100, 150), store.state.value.anchor)
        store.begin("session-b")
        assertNull(store.state.value.anchor)
    }

    @Test fun `network failure publishes no cached cards and identical fresh heartbeat recovers availability`() {
        val generation = store.begin("session-a")
        val snapshot = snapshot(1).copy(discovery = DiscoveryState(candidates = listOf(candidate())))
        store.accept(store.beginRequest(generation)!!, snapshot)
        store.fail(store.beginRequest(generation)!!, AppError.Network)
        assertTrue(store.state.value.data is LoadState.Failed)
        assertNull(store.state.value.anchor)
        store.accept(store.beginRequest(generation)!!, snapshot.copy(serverTimeEpochMillis = 1_001))
        assertEquals(snapshot.discovery, ready().discovery)
    }

    @Test fun `responses started before failure cannot restore cached cards even with newer versions`() {
        val generation = store.begin("session-a")
        store.accept(store.beginRequest(generation)!!, snapshot(10))
        val delayedHeartbeat = store.beginRequest(generation)!!
        val delayedNewer = store.beginRequest(generation)!!
        store.fail(store.beginRequest(generation)!!, AppError.Network)
        assertFalse(store.accept(delayedHeartbeat, snapshot(10)))
        assertFalse(store.accept(delayedNewer, snapshot(11).copy(serverTimeEpochMillis = 1_100)))
        assertTrue(store.state.value.data is LoadState.Failed)
        assertNull(store.state.value.anchor)
        store.accept(store.beginRequest(generation)!!, snapshot(10))
        assertTrue(store.state.value.data is LoadState.Ready)
    }

    @Test fun `older request failure cannot replace a later confirmed recovery`() {
        val generation = store.begin("session-a")
        store.accept(store.beginRequest(generation)!!, snapshot(10))
        val failedEpoch = store.beginRequest(generation)!!
        store.fail(store.beginRequest(generation)!!, AppError.Network)
        val delayedFailure = store.beginRequest(generation)!!
        store.accept(store.beginRequest(generation)!!, snapshot(11))
        store.fail(delayedFailure, AppError.Network)
        store.fail(failedEpoch, AppError.NotAuthorized)
        assertEquals(11L, ready().version)
        assertNotNull(store.state.value.anchor)
    }

    private fun ready(): SyncSnapshot = (store.state.value.data as LoadState.Ready).value
    private fun snapshot(version: Long) = SyncSnapshot("session-a", version, 1_000)
    private fun candidate() = Candidate("peer", 1, PublicProfile("peer", 1, "가상 상대", AvatarId.SEA,
        emptySet(), ProfileKind.DEMO_PEER, null), Chemistry(), emptySet())
}
