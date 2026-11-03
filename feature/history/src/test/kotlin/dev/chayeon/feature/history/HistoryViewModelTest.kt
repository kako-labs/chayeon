package dev.chayeon.feature.history

import androidx.lifecycle.ViewModelStore
import dev.chayeon.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val deps = FakeHistoryDeps()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { store.clear(); Dispatchers.resetMain() }
    private fun <T : HistoryViewModel> TestScope.watch(model: T): T {
        store.put("model-${store.keys().size}", model)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect() }
        runCurrent()
        return model
    }

    private fun modelTest(block: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        try { block() } finally { store.clear(); runCurrent() }
    }

    @Test fun `save failure preserves draft and retry has identical payload and metadata`() = modelTest {
        val model = watch(ProfileSetupViewModel(deps))
        val draft = ProfileDraft("달빛산책", AvatarId.STAR, setOf(InterestId.COFFEE))
        model.updateDraft(draft); runCurrent()
        deps.error = AppError.Network
        model.save(); model.save(); runCurrent()
        assertEquals(1, deps.calls.size)
        assertEquals(draft, model.uiState.value.draft)
        assertEquals(AppError.Network, model.uiState.value.command("save").error)
        deps.error = null
        model.retry("save"); runCurrent()
        assertEquals(deps.calls[0], deps.calls[1])
        assertTrue(model.uiState.value.command("save").completed)
        model.save(); runCurrent()
        assertEquals(2, deps.calls.size)
    }

    @Test fun `editing after failed save creates a new command and policy limits are enforced`() = modelTest {
        val model = watch(ProfileSetupViewModel(deps))
        deps.error = AppError.Network
        model.save(); runCurrent()
        model.updateDraft(model.uiState.value.draft.copy(nickname = "별빛산책")); runCurrent()
        model.save(); runCurrent()
        val calls = deps.calls.filterIsInstance<SaveProfileRequest>()
        assertNotEquals(calls[0].meta.commandId, calls[1].meta.commandId)
        model.updateDraft(model.uiState.value.draft.copy(nickname = "가".repeat(13))); runCurrent()
        assertEquals("별빛산책", model.uiState.value.draft.nickname)
        model.updateDraft(model.uiState.value.draft.copy(nickname = "가")); runCurrent()
        model.save(); runCurrent()
        assertEquals(2, deps.calls.size)
        assertNotNull(model.uiState.value.draftError)
    }

    @Test fun `policy changes between render and tap block saving and keep draft`() = modelTest {
        val model = watch(ProfileSetupViewModel(deps))
        val draft = model.uiState.value.draft
        deps.driving.value = DrivingState(policy = DrivingPolicy.RESTRICTED)
        model.save(); model.updateDraft(draft.copy(nickname = "바뀐이름")); runCurrent()
        assertTrue(deps.calls.isEmpty())
        assertEquals(draft, model.uiState.value.draft)
        assertEquals(AppError.Restricted, model.uiState.value.command("save").error)
        deps.driving.value = DrivingState(policy = DrivingPolicy.ALLOWED)
        model.retry("save"); runCurrent()
        assertEquals(1, deps.calls.size)
    }

    @Test fun `unknown policy blocks navigation and all editing`() = modelTest {
        val model = watch(FirstRunViewModel(deps))
        var navigated = false
        deps.driving.value = DrivingState()
        model.navigate { navigated = true }; model.setRoomCode("room"); model.enter(); runCurrent()
        assertFalse(navigated)
        assertEquals("", model.uiState.value.roomCode)
        assertTrue(deps.calls.isEmpty())
    }

    @Test fun `resubscription expires cached allowed policy`() = modelTest {
        val model = ProfileSetupViewModel(deps)
        store.put("profile", model)
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect() }
        runCurrent()
        assertEquals(DrivingPolicy.ALLOWED, model.uiState.value.driving.policy)
        job.cancel(); advanceTimeBy(5_001); runCurrent()
        assertEquals(DrivingPolicy.UNKNOWN, model.uiState.value.driving.policy)
        deps.driving.value = DrivingState(policy = DrivingPolicy.RESTRICTED)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect() }
        runCurrent()
        assertEquals(DrivingPolicy.RESTRICTED, model.uiState.value.driving.policy)
    }

    @Test fun `server mode is checked again when entering`() = modelTest {
        val model = watch(FirstRunViewModel(deps))
        model.selectMode(SessionMode.SERVER_DEMO); model.setRoomCode("shared-room"); runCurrent()
        deps.modes.value = setOf(SessionMode.SOLO)
        model.enter(); runCurrent()
        assertTrue(deps.calls.isEmpty())
        assertEquals(AppError.PeerUnavailable, model.uiState.value.command("enter").error)
    }

    @Test fun `restart observes registration instead of resetting profile`() = modelTest {
        deps.profileState.value = LoadState.Ready(HistoryFixtures.profile.copy(registration = RegistrationState.CONSENT_REQUIRED), 2)
        val model = watch(FirstRunViewModel(deps))
        assertEquals(RegistrationState.CONSENT_REQUIRED, model.uiState.value.myProfile?.registration)
        assertTrue(deps.calls.isEmpty())
    }

    @Test fun `consent starts unselected and retries exact grant document`() = modelTest {
        val model = watch(ConsentViewModel(deps))
        assertTrue(model.uiState.value.settings.valueOrNull()!!.consents.isEmpty())
        assertTrue(deps.calls.isEmpty())
        deps.error = AppError.Network
        model.record(ConsentScope.ADULT_ATTESTATION, true); runCurrent()
        model.record(ConsentScope.ADULT_ATTESTATION, true); runCurrent()
        assertEquals(deps.calls[0], deps.calls[1])
        val request = deps.calls.first() as RecordConsentRequest
        assertEquals(ConsentAction.GRANT, request.action)
        assertEquals(HistoryFixtures.policy.consentDocumentVersion, request.documentVersion)
    }

    @Test fun `consent revocation is a separate command after success`() = modelTest {
        val model = watch(ConsentViewModel(deps))
        model.record(ConsentScope.VEHICLE_DATA, true); runCurrent()
        deps.settingsState.value = LoadState.Ready(ParticipationSettings("settings", 2, listOf(ConsentRecord("consent", 2, ConsentScope.VEHICLE_DATA, ConsentStatus.GRANTED, "1", 1))), 2)
        runCurrent()
        model.record(ConsentScope.VEHICLE_DATA, false); runCurrent()
        val requests = deps.calls.filterIsInstance<RecordConsentRequest>()
        assertEquals(ConsentAction.REVOKE, requests.last().action)
        assertNotEquals(requests.first().meta, requests.last().meta)
    }

    @Test fun `accept duplicates collapse and mid flow expiry never creates local relationship`() = modelTest {
        val model = watch(AfterRequestViewModel(deps, HistoryFixtures.encounter.id))
        deps.gate = CompletableDeferred()
        model.respond("request", RequestDecision.ACCEPT); model.respond("request", RequestDecision.ACCEPT); runCurrent()
        assertEquals(1, deps.calls.size)
        deps.requestsState.value = LoadState.Ready(listOf(HistoryFixtures.request.copy(status = AfterRequestStatus.EXPIRED, version = 2)), 2)
        deps.error = AppError.Expired
        deps.gate!!.complete(Unit); runCurrent()
        assertEquals(AfterRequestStatus.EXPIRED, model.uiState.value.requests.valueOrNull()!!.single().status)
        assertEquals(AppError.Expired, model.uiState.value.command("respond:request").error)
        assertNull(model.uiState.value.selectedRelationship)
    }

    @Test fun `incoming response is denied after driving change`() = modelTest {
        val model = watch(AfterRequestViewModel(deps, "encounter"))
        deps.driving.value = DrivingState()
        model.respond("request", RequestDecision.ACCEPT); runCurrent()
        assertTrue(deps.calls.isEmpty())
    }

    @Test fun `countdowns use monotonic server anchor and unknown anchor stays unknown`() = modelTest {
        val model = watch(AfterRequestViewModel(deps, "encounter"))
        val remaining = model.uiState.value.remainingMillis["request"]!!
        deps.monotonic += 5_000
        advanceTimeBy(1_001); runCurrent()
        assertEquals(remaining - 5_000, model.uiState.value.remainingMillis["request"])
        deps.clock.value = null; runCurrent()
        assertNull(model.uiState.value.remainingMillis["request"])
    }

    @Test fun `message retry preserves ids and draft survives interruption`() = modelTest {
        val model = watch(AfterConnectionsViewModel(deps))
        model.setText("다시 만나서 반가워요"); runCurrent()
        deps.error = AppError.Network
        model.sendText(); model.sendText(); runCurrent()
        assertEquals(1, deps.calls.size)
        assertEquals(DeliveryState.FAILED, model.uiState.value.outbox.single().delivery)
        val pending = model.uiState.value.outbox.single()
        deps.driving.value = DrivingState(policy = DrivingPolicy.RESTRICTED)
        model.setText("지워짐"); model.retryMessage(pending.request.clientMessageId); runCurrent()
        assertEquals("다시 만나서 반가워요", model.uiState.value.messageDrafts["after"])
        assertEquals(1, deps.calls.size)
        deps.driving.value = DrivingState(policy = DrivingPolicy.ALLOWED)
        deps.error = null
        model.retryMessage(pending.request.clientMessageId); runCurrent()
        assertEquals(deps.calls[0], deps.calls[1])
        assertEquals("", model.uiState.value.messageDrafts["after"].orEmpty())
    }

    @Test fun `multiple failed text messages retain separate retry commands`() = modelTest {
        val model = watch(AfterConnectionsViewModel(deps))
        deps.error = AppError.Network
        model.setText("첫 번째 인사"); runCurrent(); model.sendText(); runCurrent()
        val first = model.uiState.value.outbox.single().request
        model.setText("두 번째 인사"); runCurrent(); model.sendText(); runCurrent()
        val second = model.uiState.value.outbox.last().request
        deps.error = null
        model.retryMessage(first.clientMessageId); runCurrent()
        model.retryMessage(second.clientMessageId); runCurrent()
        assertEquals(first, deps.calls[2])
        assertEquals(second, deps.calls[3])
        assertNotEquals(first.meta, second.meta)
    }

    @Test fun `observed failed message can be retried after screen recreation`() = modelTest {
        val original = SendAfterMessageRequest(CommandMeta("client-retry"), "after", "client-retry", AfterMessageContent.Text("반가워요"))
        deps.messagesState.value = LoadState.Ready(listOf(HistoryFixtures.message.copy(author = MessageAuthor.SELF, clientMessageId = original.clientMessageId, content = original.content, delivery = DeliveryState.FAILED)), 2)
        val model = watch(AfterConnectionsViewModel(deps))
        model.retryMessage(original.clientMessageId); runCurrent()
        assertEquals(original, deps.calls.single())
    }

    @Test fun `relationship closing before tap blocks late message`() = modelTest {
        val model = watch(AfterConnectionsViewModel(deps))
        model.setText("안녕하세요"); runCurrent()
        deps.relationshipsState.value = LoadState.Ready(listOf(HistoryFixtures.relationship.copy(status = AfterConnectionStatus.CLOSED, version = 2)), 2)
        model.sendText(); runCurrent()
        assertTrue(deps.calls.isEmpty())
        assertEquals(AppError.AlreadyClosed, model.uiState.value.command("compose:after").error)
    }

    @Test fun `driving change during relationship refresh blocks message dispatch`() = modelTest {
        var pause = false
        val release = CompletableDeferred<Unit>()
        val guarded = object : HistoryDeps by deps {
            override val afterRepository = object : AfterRepository by deps.afterRepository {
                override val connections = flow {
                    if (pause) release.await()
                    emitAll(deps.relationshipsState)
                }
            }
        }
        val model = watch(AfterConnectionsViewModel(guarded))
        model.setText("반가워요"); runCurrent()
        pause = true
        model.sendText(); runCurrent()
        deps.driving.value = DrivingState(policy = DrivingPolicy.RESTRICTED)
        release.complete(Unit); runCurrent()
        assertTrue(deps.calls.isEmpty())
        assertEquals(AppError.Restricted, model.uiState.value.command("compose:after").error)
    }

    @Test fun `unblocking elsewhere allows blocking again with new metadata`() = modelTest {
        val model = watch(SafetyViewModel(deps, HistoryFixtures.block.peer.id))
        model.askBlock(); runCurrent(); model.confirmAction(); runCurrent()
        deps.blocksState.value = LoadState.Ready(listOf(HistoryFixtures.block), 2); runCurrent()
        deps.blocksState.value = LoadState.Ready(emptyList(), 3); runCurrent()
        model.askBlock(); runCurrent(); model.confirmAction(); runCurrent()
        val requests = deps.calls.filterIsInstance<BlockPeerRequest>()
        assertEquals(2, requests.size)
        assertNotEquals(requests[0].meta, requests[1].meta)
    }

    @Test fun `message limit uses code points and rejects overflow`() = modelTest {
        val model = watch(AfterConnectionsViewModel(deps))
        model.setText("가".repeat(500)); runCurrent()
        model.setText("나".repeat(501)); runCurrent()
        assertEquals("가".repeat(500), model.uiState.value.messageDrafts["after"])
    }

    @Test fun `safety uses peer id without needing an encounter and blocks only after confirmation`() = modelTest {
        val model = watch(SafetyViewModel(deps, "peer-from-profile"))
        model.confirmAction(); runCurrent()
        assertTrue(deps.calls.isEmpty())
        model.askBlock(); runCurrent(); model.confirmAction(); model.confirmAction(); runCurrent()
        assertEquals("peer-from-profile", (deps.calls.single() as BlockPeerRequest).peerProfileId)
        model.selectReason(ReportReason.POSSIBLE_MINOR); runCurrent(); model.report(); runCurrent()
        assertEquals(ReportReason.POSSIBLE_MINOR, (deps.calls.last() as ReportPeerRequest).reason)
    }

    @Test fun `cleanup commands have independent progress and cannot bypass confirmation`() = modelTest {
        val model = watch(CleanupViewModel(deps))
        model.execute(); runCurrent(); assertTrue(deps.calls.isEmpty())
        deps.gate = CompletableDeferred()
        model.ask(ConfirmationKind.DELETE_RECORDS); runCurrent(); model.execute(); runCurrent()
        assertTrue(model.uiState.value.command("cleanup:DELETE_RECORDS:").running)
        assertFalse(model.uiState.value.command("cleanup:SIGN_OUT:").running)
        model.dismissConfirmation(); runCurrent()
        assertNotNull(model.uiState.value.confirmation)
        deps.error = AppError.Network; deps.gate!!.complete(Unit); runCurrent()
        model.execute(); runCurrent()
        assertEquals(deps.calls[0], deps.calls[1])
    }

    @Test fun `restricted cleanup permits only leaving the current connection`() = modelTest {
        val model = watch(CleanupViewModel(deps))
        model.ask(ConfirmationKind.DELETE_ACCOUNT); runCurrent()
        deps.driving.value = DrivingState()
        model.execute(); model.leaveWhileLocked(); runCurrent()
        assertEquals(1, deps.calls.size)
        assertTrue(deps.calls.single() is LeaveConnectionRequest)
    }

    @Test fun `failed observation removes previously visible profiles`() = modelTest {
        val model = watch(EncountersViewModel(deps))
        assertNotNull(model.uiState.value.encounters.valueOrNull())
        deps.encountersState.value = LoadState.Failed(AppError.Network, 2); runCurrent()
        assertNull(model.uiState.value.encounters.valueOrNull())
    }
}
