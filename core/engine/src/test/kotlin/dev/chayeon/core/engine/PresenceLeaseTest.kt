package dev.chayeon.core.engine

import dev.chayeon.core.model.*
import org.junit.Assert.*
import org.junit.Test

class PresenceLeaseTest {
    private val policy = EnginePolicy(presenceLeaseMillis = 60_000)

    @Test fun expiryAndExplicitAwayHaveIdenticalCompletePeerSnapshotsAndDistanceTiming() {
        fun scenario(expire: Boolean): List<SyncSnapshot> {
            val f = Fixture(policy = policy)
            f.join("a"); f.join("b"); f.connect()
            val snapshots = mutableListOf(f.snap("b"))
            f.advance(30_000)
            f.engine.setSyntheticPresence("b", SetSyntheticPresenceRequest(CommandMeta("observer-renew"), SyntheticPresence.NEARBY)).ok()
            if (!expire) {
                f.engine.setSyntheticPresence("a", SetSyntheticPresenceRequest(CommandMeta("holder-renew"), SyntheticPresence.NEARBY)).ok()
            }
            f.advance(29_999); snapshots += f.snap("b")
            f.advance(1)
            if (!expire) f.engine.setSyntheticPresence("a", SetSyntheticPresenceRequest(CommandMeta("ordinary-away"), SyntheticPresence.AWAY)).ok()
            snapshots += f.snap("b")
            assertEquals(ConnectionStatus.CHECKING, snapshots.last().connections.single().status)
            f.advance(policy.exitGraceMillis - 1); snapshots += f.snap("b")
            f.advance(1); snapshots += f.snap("b")
            assertEquals(ConnectionEndReason.DISTANCE, snapshots.last().connections.single().endReason)
            assertTrue(f.snap("a").discovery.candidates.isEmpty())
            assertTrue(f.snap("b").discovery.candidates.isEmpty())
            return snapshots
        }
        // Identical clocks and ids require no normalization; every response field is compared.
        assertEquals(scenario(false), scenario(true))
    }

    @Test fun renewalDoesNotStampOrChangeThePeerVersionAndKeepsAnUnexpiredLease() {
        val f = Fixture(policy = policy); f.join("a"); f.join("b")
        val peer = f.snap("b").version
        val revision = f.engine.revision.value
        f.advance(20_000)
        val receipt = f.engine.setSyntheticPresence("a", SetSyntheticPresenceRequest(f.meta(), SyntheticPresence.NEARBY)).ok()
        assertEquals(revision, f.engine.revision.value)
        assertEquals(peer, f.snap("b").version)
        assertEquals(f.snap("a").version, receipt.version)
        assertEquals(f.now + 60_000, f.engine.exportState().accounts.getValue("a").presenceLeaseDeadline)
        val wire = EngineJson.encodeToString(SyncSnapshot.serializer(), f.snap("b"))
        assertFalse(wire.contains("Lease", ignoreCase = true))
        f.engine.setSyntheticPresence("a", SetSyntheticPresenceRequest(f.meta(), SyntheticPresence.STALE)).ok()
        assertNull(f.engine.exportState().accounts.getValue("a").presenceLeaseDeadline)
    }

    @Test fun expiredAccountRejectsAcceptanceAndTheSameKeyKeepsThatRejection() {
        val f = Fixture(policy = policy); f.join("a"); f.join("b")
        val pending = f.request()
        f.advance(30_000)
        f.engine.setSyntheticPresence("b", SetSyntheticPresenceRequest(f.meta(), SyntheticPresence.NEARBY)).ok()
        f.advance(30_000)
        assertTrue(f.snap("b").discovery.candidates.isEmpty())
        assertEquals(AppError.PeerUnavailable, f.engine.requestConnection("b", RequestConnectionRequest(f.meta(), f.snap("a").profile!!.id)).error())
        val accept = RespondConnectionRequest(f.meta(), pending.id, RequestDecision.ACCEPT)
        assertEquals(AppError.PeerUnavailable, f.engine.respondConnection("b", accept).error())
        f.engine.setSyntheticPresence("a", SetSyntheticPresenceRequest(f.meta(), SyntheticPresence.NEARBY)).ok()
        assertEquals(AppError.PeerUnavailable, f.engine.respondConnection("b", accept).error())
        assertEquals(ConnectionRequestStatus.PENDING, f.snap("b").connectionRequests.single().status)
    }

    @Test fun aFreshAcceptKeyRecoversAfterExpiryAndReturnDuringGracePreservesConnection() {
        val f = Fixture(policy = policy); f.join("a"); f.join("b")
        val pending = f.request()
        f.advance(30_000)
        f.engine.setSyntheticPresence("b", SetSyntheticPresenceRequest(f.meta(), SyntheticPresence.NEARBY)).ok()
        f.advance(30_000)
        val rejected = RespondConnectionRequest(f.meta(), pending.id, RequestDecision.ACCEPT)
        assertEquals(AppError.PeerUnavailable, f.engine.respondConnection("b", rejected).error())
        f.engine.setSyntheticPresence("a", SetSyntheticPresenceRequest(f.meta(), SyntheticPresence.NEARBY)).ok()
        f.engine.respondConnection("b", rejected.copy(meta = f.meta())).ok()
        f.advance(20_000)
        f.engine.setSyntheticPresence("b", SetSyntheticPresenceRequest(f.meta(), SyntheticPresence.NEARBY)).ok()
        f.advance(40_000)
        assertEquals(ConnectionStatus.CHECKING, f.snap("b").connections.single().status)
        f.advance(policy.exitGraceMillis - 1)
        f.engine.setSyntheticPresence("a", SetSyntheticPresenceRequest(f.meta(), SyntheticPresence.NEARBY)).ok()
        assertEquals(ConnectionStatus.CONNECTED, f.snap("b").connections.single().status)
        assertTrue(f.snap("b").encounters.isEmpty())
    }

    @Test fun signingOutAndReenteringNearbyStartsALeaseWithoutAnotherPresenceCommand() {
        val f = Fixture(policy = policy); f.join("a")
        f.engine.signOut("a", SignOutRequest(f.meta())).ok()
        assertNull(f.engine.exportState().accounts.getValue("a").presenceLeaseDeadline)
        f.advance(10_000)
        f.engine.enterSession("a", EnterSessionRequest(f.meta(), SessionMode.SOLO)).ok()
        assertEquals(f.now + 60_000, f.engine.exportState().accounts.getValue("a").presenceLeaseDeadline)
        f.advance(59_999)
        assertEquals(SyntheticPresence.NEARBY, f.engine.exportState().accounts.getValue("a").syntheticPresence)
        f.advance(1)
        assertEquals(SyntheticPresence.AWAY, f.engine.exportState().accounts.getValue("a").syntheticPresence)
    }

    @Test fun nullLeaseAndDemoCastKeepTheirExistingPresence() {
        val local = Fixture(); local.join("a"); local.join("b"); local.connect()
        local.advance(600_000)
        assertEquals(ConnectionStatus.CONNECTED, local.snap("b").connections.single().status)
        assertNull(local.engine.exportState().accounts.getValue("a").presenceLeaseDeadline)
        val demo = Fixture(demo = true, policy = policy)
        demo.advance(600_000)
        DemoCast.peers.forEach {
            val account = demo.engine.exportState().accounts.getValue(it.id)
            assertNull(account.presenceLeaseDeadline)
            assertEquals(SyntheticPresence.NEARBY, account.syntheticPresence)
        }
    }
}
