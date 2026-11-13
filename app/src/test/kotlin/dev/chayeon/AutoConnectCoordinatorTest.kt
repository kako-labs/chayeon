package dev.chayeon

import dev.chayeon.core.model.*
import dev.chayeon.data.social.EmptyConnectionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.*
import java.time.ZoneId

class AutoConnectCoordinatorTest {
    private class TestDriving : DrivingStateProvider {
        override val drivingState = MutableStateFlow(DrivingState(policy = DrivingPolicy.RESTRICTED, gear = Gear.DRIVE))
    }
    private val peer = PublicProfile("peer", 1, "별보는밤", AvatarId.STAR, setOf(InterestId.JAZZ), ProfileKind.DEMO_PEER,
        PublicSignature("sig", 1, "깜빡이를 잊지 않는 드라이버", listOf(PublicTrait(TraitId.SIGNAL_MANNERS, TraitLevel.SIGNATURE, 94)),
            KnotSeed(1), DataSource.SYNTHETIC, EvidenceBucket.ESTABLISHED))
    private fun snapshot(mode: DrivingConnectMode = DrivingConnectMode.AUTO, conditions: AutoConnectConditions = AutoConnectConditions()) = AutoConnectSnapshot(
        "owner", ParticipationSettings("settings", 1, drivingConnectMode = mode, autoConnectConditions = conditions), setOf(InterestId.JAZZ),
        DiscoveryState(mode = DiscoveryMode.ON, status = DiscoveryStatus.AVAILABLE, candidates = listOf(Candidate(peer.id, 1, peer, Chemistry(), peer.interests))),
        emptyList(), emptyList(), emptySet())
    private fun incoming(now: Long, requester: PublicProfile = peer) = ConnectionRequest("incoming", 1, requester, RequestDirection.INCOMING,
        ConnectionRequestStatus.PENDING, now, now + 120_000, Chemistry(), automatic = true)
    private class Commands : ConnectionRepository by EmptyConnectionRepository() {
        val sent = mutableListOf<RequestConnectionRequest>()
        val accepted = mutableListOf<RespondConnectionRequest>()
        var failure: AppError? = null
        lateinit var response: ConnectionRequest
        override suspend fun requestConnection(request: RequestConnectionRequest): AppResult<ConnectionRequest> {
            sent += request
            return failure?.let { AppResult.Failure(it) } ?: AppResult.Success(response)
        }
        override suspend fun respond(request: RespondConnectionRequest): AppResult<ConnectionRequest> {
            accepted += request
            return failure?.let { AppResult.Failure(it) } ?: AppResult.Success(response.copy(status = ConnectionRequestStatus.ACCEPTED))
        }
    }

    @Test fun autoAutoConnectsWithoutTapsAndAnnouncementIsClaimedOnce() = runTest {
        val now = 1_800_000_000_000L
        val senderCommands = Commands().apply { response = incoming(now).copy(direction = RequestDirection.OUTGOING) }
        val receiverCommands = Commands().apply { response = incoming(now) }
        fun coordinator(commands: Commands) = AutoConnectCoordinator(MutableStateFlow(null), TestDriving(), TestHomeZone(), commands,
            backgroundScope, MemoryAutoConnectStore(), { now }, { ZoneId.of("Asia/Seoul") })
        val sender = coordinator(senderCommands)
        val receiver = coordinator(receiverCommands)
        sender.update(snapshot(), DrivingPolicy.RESTRICTED, false)
        assertEquals(1, senderCommands.sent.size)
        assertTrue(senderCommands.sent.single().automatic)
        receiver.update(snapshot().copy(accountId = "receiver", requests = listOf(incoming(now))), DrivingPolicy.RESTRICTED, false)
        assertEquals(1, receiverCommands.accepted.size)
        assertTrue(receiverCommands.accepted.single().automatic)
        val connection = Connection("call", 2, "incoming", peer, ConnectionStatus.CONNECTED, now, Chemistry())
        receiver.update(snapshot().copy(accountId = "receiver", connections = listOf(connection)), DrivingPolicy.RESTRICTED, false)
        assertTrue(receiver.shouldAnnounce(connection))
        assertTrue(receiver.markAnnounced(connection.id))
        assertFalse(receiver.shouldAnnounce(connection))
    }

    @Test fun ringOffAndUnmetConditionsDoNotAcceptAutomaticallyAndParkedIsManual() = runTest {
        val now = 1_800_000_000_000L
        for (mode in DrivingConnectMode.entries) {
            val commands = Commands().apply { response = incoming(now) }
            val coordinator = AutoConnectCoordinator(MutableStateFlow(null), TestDriving(), TestHomeZone(), commands,
                backgroundScope, MemoryAutoConnectStore(), { now })
            val state = snapshot(mode, AutoConnectConditions(mapOf(TraitId.SIGNAL_MANNERS to 100))).copy(requests = listOf(incoming(now)))
            coordinator.update(state, DrivingPolicy.RESTRICTED, false)
            assertTrue(commands.accepted.isEmpty())
            assertTrue(commands.sent.isEmpty())
            coordinator.update(snapshot(mode).copy(requests = listOf(incoming(now))), DrivingPolicy.ALLOWED, false)
            coordinator.update(snapshot(mode).copy(requests = listOf(incoming(now))), DrivingPolicy.UNKNOWN, false)
            assertTrue(commands.accepted.isEmpty())
            assertTrue(commands.sent.isEmpty())
        }
    }

    @Test fun homeBlockDiscoveryOffActiveAndPendingPreventAutomaticActions() = runTest {
        val now = 1_800_000_000_000L
        val commands = Commands().apply { response = incoming(now) }
        val coordinator = AutoConnectCoordinator(MutableStateFlow(null), TestDriving(), TestHomeZone(), commands,
            backgroundScope, MemoryAutoConnectStore(), { now })
        val state = snapshot()
        coordinator.update(state, DrivingPolicy.RESTRICTED, true)
        coordinator.update(state.copy(blockedProfileIds = setOf(peer.id)), DrivingPolicy.RESTRICTED, false)
        coordinator.update(state.copy(discovery = state.discovery.copy(mode = DiscoveryMode.OFF)), DrivingPolicy.RESTRICTED, false)
        coordinator.update(state.copy(connections = listOf(Connection("call", 1, "request", peer, ConnectionStatus.CHECKING, now, Chemistry()))), DrivingPolicy.RESTRICTED, false)
        coordinator.update(state.copy(requests = listOf(incoming(now).copy(direction = RequestDirection.OUTGOING))), DrivingPolicy.RESTRICTED, false)
        assertTrue(commands.sent.isEmpty())
        coordinator.update(state.copy(requests = listOf(incoming(now)), blockedProfileIds = setOf(peer.id)), DrivingPolicy.RESTRICTED, false)
        coordinator.update(state.copy(requests = listOf(incoming(now))), DrivingPolicy.RESTRICTED, true)
        assertTrue(commands.accepted.isEmpty())
    }

    @Test fun cooldownAndSameDayGuardSurviveRecreationAndOtherPeerWaitsTenMinutes() = runTest {
        var now = 1_800_000_000_000L
        val store = MemoryAutoConnectStore()
        val commands = Commands().apply { response = incoming(now) }
        fun coordinator() = AutoConnectCoordinator(MutableStateFlow(null), TestDriving(), TestHomeZone(), commands,
            backgroundScope, store, { now }, { ZoneId.of("UTC") })
        val ended = Connection("call", 1, "request", peer, ConnectionStatus.ENDED, now - 60_000, Chemistry(), now, ConnectionEndReason.SELF_LEFT)
        coordinator().update(snapshot().copy(connections = listOf(ended)), DrivingPolicy.RESTRICTED, false)
        val restored = coordinator()
        val other = peer.copy(id = "other")
        val next = snapshot().copy(discovery = snapshot().discovery.copy(candidates = listOf(Candidate(other.id, 1, other, Chemistry(), other.interests))))
        now += 599_999
        restored.update(next, DrivingPolicy.RESTRICTED, false)
        assertTrue(commands.sent.isEmpty())
        now++
        restored.update(snapshot().copy(requests = listOf(incoming(now))), DrivingPolicy.RESTRICTED, false)
        assertTrue(commands.accepted.isEmpty())
        restored.update(next, DrivingPolicy.RESTRICTED, false)
        assertEquals("other", commands.sent.single().candidateId)
    }

    @Test fun declineCancelExpiryPreventSameDayRepeatButNextDayPermitsIt() = runTest {
        for (status in listOf(ConnectionRequestStatus.DECLINED, ConnectionRequestStatus.CANCELLED, ConnectionRequestStatus.EXPIRED)) {
            var now = 1_800_000_000_000L
            val commands = Commands().apply { response = incoming(now) }
            val coordinator = AutoConnectCoordinator(MutableStateFlow(null), TestDriving(), TestHomeZone(), commands,
                backgroundScope, MemoryAutoConnectStore(), { now }, { ZoneId.of("UTC") })
            val closed = incoming(now).copy(status = status)
            coordinator.update(snapshot().copy(requests = listOf(closed)), DrivingPolicy.RESTRICTED, false)
            coordinator.update(snapshot().copy(requests = listOf(incoming(now).copy(id = "new-request"))), DrivingPolicy.RESTRICTED, false)
            assertTrue(commands.sent.isEmpty())
            assertTrue(commands.accepted.isEmpty())
            now += 24 * 60 * 60_000
            coordinator.update(snapshot(), DrivingPolicy.RESTRICTED, false)
            assertEquals(1, commands.sent.size)
        }
    }

    @Test fun crossingRequestIsStillAcceptedAfterOurOwnAutomaticRequestIsRefused() = runTest {
        var now = 1_800_000_000_000L
        val commands = Commands().apply { response = incoming(now); failure = AppError.AlreadyClosed }
        val coordinator = AutoConnectCoordinator(MutableStateFlow(null), TestDriving(), TestHomeZone(), commands,
            backgroundScope, MemoryAutoConnectStore(), { now }, { ZoneId.of("UTC") })
        // The other person requested us at the same moment, so the server refuses our automatic request.
        coordinator.update(snapshot(), DrivingPolicy.RESTRICTED, false)
        assertEquals(1, commands.sent.size)
        assertTrue(coordinator.canAct(peer.id))
        commands.failure = null
        now += 5_000
        coordinator.update(snapshot().copy(requests = listOf(incoming(now))), DrivingPolicy.RESTRICTED, false)
        assertEquals("incoming", commands.accepted.single().requestId)
        // Our own request to them waits a minute instead of retrying at once.
        now += 5_000
        coordinator.update(snapshot(), DrivingPolicy.RESTRICTED, false)
        assertEquals(1, commands.sent.size)
    }

    @Test fun cooldownCountsFromWhenTheCarSawTheEndEvenIfItsClockIsAhead() = runTest {
        val serverEnd = 1_800_000_000_000L
        var now = serverEnd + 12 * 60_000
        val commands = Commands().apply { response = incoming(now) }
        val coordinator = AutoConnectCoordinator(MutableStateFlow(null), TestDriving(), TestHomeZone(), commands,
            backgroundScope, MemoryAutoConnectStore(), { now }, { ZoneId.of("UTC") })
        val ended = Connection("call", 1, "request", peer, ConnectionStatus.ENDED, serverEnd - 60_000, Chemistry(), serverEnd, ConnectionEndReason.SELF_LEFT)
        val other = peer.copy(id = "other")
        val next = snapshot().copy(connections = listOf(ended),
            discovery = snapshot().discovery.copy(candidates = listOf(Candidate(other.id, 1, other, Chemistry(), other.interests))))
        coordinator.update(next, DrivingPolicy.RESTRICTED, false)
        assertTrue(commands.sent.isEmpty())
        now += 599_999
        coordinator.update(next, DrivingPolicy.RESTRICTED, false)
        assertTrue(commands.sent.isEmpty())
        now++
        coordinator.update(next, DrivingPolicy.RESTRICTED, false)
        assertEquals("other", commands.sent.single().candidateId)
    }

    @Test fun historicalConnectionNeverOverwritesTodaysDeclineGuard() = runTest {
        val now = 1_800_000_000_000L
        val commands = Commands().apply { response = incoming(now) }
        val coordinator = AutoConnectCoordinator(MutableStateFlow(null), TestDriving(), TestHomeZone(), commands,
            backgroundScope, MemoryAutoConnectStore(), { now }, { ZoneId.of("UTC") })
        val past = Connection("yesterday", 1, "old", peer, ConnectionStatus.ENDED, now - 90_000_000, Chemistry(), now - 86_400_000, ConnectionEndReason.SELF_LEFT)
        val state = snapshot().copy(requests = listOf(incoming(now).copy(status = ConnectionRequestStatus.DECLINED)), connections = listOf(past))
        coordinator.update(state, DrivingPolicy.RESTRICTED, false)
        coordinator.update(snapshot(), DrivingPolicy.RESTRICTED, false)
        assertTrue(commands.sent.isEmpty())
        assertFalse(coordinator.canAct(peer.id))
    }

    @Test fun atomicCallSnapshotCanAnnounceBeforeServiceCollectorSeesIt() = runTest {
        val now = 1_800_000_000_000L
        val commands = Commands().apply { response = incoming(now) }
        val coordinator = AutoConnectCoordinator(MutableStateFlow(null), TestDriving(), TestHomeZone(), commands,
            backgroundScope, MemoryAutoConnectStore(), { now })
        val request = incoming(now).copy(direction = RequestDirection.OUTGOING, status = ConnectionRequestStatus.ACCEPTED)
        val connection = Connection("call", 2, request.id, peer, ConnectionStatus.CONNECTED, now, Chemistry())
        val atomic = snapshot().copy(requests = listOf(request), connections = listOf(connection))
        assertTrue(coordinator.shouldAnnounce(connection, atomic))
        assertTrue(coordinator.markAnnounced(connection.id))
        assertFalse(coordinator.shouldAnnounce(connection, atomic))
    }

    @Test fun networkUncertaintyRetriesTheSameCommandAndCannotCreateSecondPendingRequest() = runTest {
        var now = 1_800_000_000_000L
        val commands = Commands().apply { failure = AppError.Network; response = incoming(now) }
        val coordinator = AutoConnectCoordinator(MutableStateFlow(null), TestDriving(), TestHomeZone(), commands,
            backgroundScope, MemoryAutoConnectStore(), { now })
        coordinator.update(snapshot(), DrivingPolicy.RESTRICTED, false)
        now += 5_000
        coordinator.update(snapshot(), DrivingPolicy.RESTRICTED, false)
        assertEquals(2, commands.sent.size)
        assertEquals(commands.sent[0], commands.sent[1])
        now += 5_000
        coordinator.update(snapshot().copy(requests = listOf(incoming(now).copy(direction = RequestDirection.OUTGOING))), DrivingPolicy.RESTRICTED, false)
        assertEquals(2, commands.sent.size)
    }

    @Test fun rankingUsesExceededConditionsThenInterestsThenStableId() = runTest {
        val now = 1_800_000_000_000L
        val commands = Commands().apply { response = incoming(now) }
        val coordinator = AutoConnectCoordinator(MutableStateFlow(null), TestDriving(), TestHomeZone(), commands,
            backgroundScope, MemoryAutoConnectStore(), { now })
        val peers = listOf(peer.copy(id = "z"), peer.copy(id = "b", interests = emptySet()), peer.copy(id = "a"))
        val state = snapshot(conditions = AutoConnectConditions(mapOf(TraitId.SIGNAL_MANNERS to 90)))
        coordinator.update(state.copy(discovery = state.discovery.copy(candidates = peers.map { Candidate(it.id, 1, it, Chemistry(), emptySet()) })), DrivingPolicy.RESTRICTED, false)
        assertEquals("a", commands.sent.single().candidateId)
    }

    @Test fun storageFailureFailsClosedAndExpiredIncomingDoesNotAccept() = runTest {
        val now = 1_800_000_000_000L
        val commands = Commands().apply { response = incoming(now) }
        val broken = object : AutoConnectMemoryStore {
            override fun read(accountId: String): AutoConnectMemory = error("unavailable")
            override fun write(accountId: String, memory: AutoConnectMemory) = Unit
        }
        AutoConnectCoordinator(MutableStateFlow(null), TestDriving(), TestHomeZone(), commands,
            backgroundScope, broken, { now }).update(snapshot(), DrivingPolicy.RESTRICTED, false)
        assertTrue(commands.sent.isEmpty())
        val state = snapshot().copy(requests = listOf(incoming(now - 120_000)), discovery = snapshot().discovery.copy(candidates = emptyList()))
        AutoConnectCoordinator(MutableStateFlow(null), TestDriving(), TestHomeZone(), commands,
            backgroundScope, MemoryAutoConnectStore(), { now }).update(state, DrivingPolicy.RESTRICTED, false)
        assertTrue(commands.accepted.isEmpty())
    }
}
