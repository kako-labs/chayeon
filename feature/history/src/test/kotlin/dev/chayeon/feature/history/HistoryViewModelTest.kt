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

    private val ephemeralKakao = "a" + java.util.UUID.randomUUID().toString().replace("-", "").take(19)
    private fun validKakao() = ephemeralKakao

    @Test fun `optional microphone permission is requested only while parked`() = modelTest {
        val model = watch(ConsentViewModel(deps))
        val requiredBefore = model.uiState.value.requiredConsentsGranted
        for (policy in listOf(DrivingPolicy.RESTRICTED, DrivingPolicy.UNKNOWN)) {
            deps.driving.value = DrivingState(policy = policy)
            model.requestMicrophone(); runCurrent()
            assertEquals(0, deps.microphoneRequests)
        }
        deps.driving.value = DrivingState(policy = DrivingPolicy.ALLOWED)
        model.requestMicrophone(); runCurrent()
        assertEquals(1, deps.microphoneRequests)
        assertTrue(model.uiState.value.microphoneGranted)
        assertEquals(requiredBefore, model.uiState.value.requiredConsentsGranted)
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
        model.navigate { navigated = true }; model.enter(); runCurrent()
        assertFalse(navigated)
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
        model.selectMode(SessionMode.SERVER_DEMO); runCurrent()
        deps.modes.value = setOf(SessionMode.SOLO)
        model.enter(); runCurrent()
        assertTrue(deps.calls.isEmpty())
        assertEquals(AppError.PeerUnavailable, model.uiState.value.command("enter").error)
    }

    @Test fun `server entry uses default room without account selection`() = modelTest {
        val model = watch(FirstRunViewModel(deps))
        model.selectMode(SessionMode.SERVER_DEMO); runCurrent()
        model.enter(); runCurrent()
        assertNull((deps.calls.single() as EnterSessionRequest).roomCode)
    }

    @Test fun `solo only starts automatically after signout while configured server waits for choice`() = modelTest {
        deps.sessionState.value = SessionState.SignedOut()
        val model = watch(FirstRunViewModel(deps))
        model.enterSoloIfNeeded(); runCurrent()
        assertTrue(deps.calls.isEmpty())
        deps.modes.value = setOf(SessionMode.SOLO); runCurrent()
        model.enterSoloIfNeeded(); runCurrent()
        assertEquals(SessionMode.SOLO, (deps.calls.single() as EnterSessionRequest).mode)
    }

    @Test fun `solo automatic entry waits for session restoration and does not replace failure`() = modelTest {
        deps.modes.value = setOf(SessionMode.SOLO)
        deps.sessionState.value = SessionState.Entering()
        val model = watch(FirstRunViewModel(deps))
        model.enterSoloIfNeeded(); runCurrent()
        deps.sessionState.value = SessionState.Failed(AppError.Network); runCurrent()
        model.enterSoloIfNeeded(); runCurrent()
        assertTrue(deps.calls.isEmpty())
    }

    @Test fun `capacity rejection can retry without changing mode`() = modelTest {
        deps.sessionState.value = SessionState.SignedOut()
        val model = watch(FirstRunViewModel(deps))
        model.selectMode(SessionMode.SERVER_DEMO); runCurrent()
        deps.error = AppError.Restricted
        model.enter(); runCurrent()
        deps.error = null
        model.retry("enter"); runCurrent()
        assertEquals(2, deps.calls.size)
        assertTrue(model.uiState.value.command("enter").completed)
    }

    @Test fun `empty profile starts with empty nickname and cannot save until named`() = modelTest {
        deps.profileState.value = LoadState.Ready(HistoryFixtures.emptyProfile, 2)
        val model = watch(ProfileSetupViewModel(deps))
        assertEquals("", model.uiState.value.draft.nickname)
        model.save(); runCurrent()
        assertTrue(deps.calls.isEmpty())
        assertNotNull(model.uiState.value.draftError)
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

    @Test fun `discovery cannot start before all current consents are granted`() = modelTest {
        val model = watch(ConsentViewModel(deps))
        model.setDiscovery(true); runCurrent()
        assertTrue(deps.calls.isEmpty())
        assertEquals(AppError.Restricted, model.uiState.value.command("discovery").error)
    }

    @Test fun `block confirmation names peer found in encounter data`() = modelTest {
        val model = watch(SafetyViewModel(deps, HistoryFixtures.peer.id))
        model.askBlock(); runCurrent()
        assertEquals("느린오후 님 차단", model.uiState.value.confirmation?.title)
    }

    @Test fun `granting consent never opts into discovery and current consent is rechecked`() = modelTest {
        deps.discoveryState.value = DiscoveryState()
        val model = watch(ConsentViewModel(deps))
        val granted = HistoryFixtures.settings.copy(consents = ConsentScope.entries.map {
            ConsentRecord(it.name, 2, it, ConsentStatus.GRANTED, HistoryFixtures.policy.consentDocumentVersion, 1)
        })
        deps.settingsState.value = LoadState.Ready(granted, 2)
        runCurrent()
        assertEquals(DiscoveryMode.OFF, model.uiState.value.discovery.mode)
        assertTrue(deps.calls.isEmpty())
        model.setDiscovery(true); runCurrent()
        assertEquals(DiscoveryMode.ON, (deps.calls.single() as SetDiscoveryRequest).mode)
        deps.calls.clear()
        deps.settingsState.value = LoadState.Ready(granted.copy(consents = granted.consents.dropLast(1)), 3)
        model.setDiscovery(true); runCurrent()
        assertTrue(deps.calls.isEmpty())
    }

    @Test fun `safety follows newer anonymized peer and handles missing profile honestly`() = modelTest {
        val model = watch(SafetyViewModel(deps, HistoryFixtures.peer.id))
        assertEquals(HistoryFixtures.peer, model.uiState.value.safetyPeer)
        val anonymous = HistoryFixtures.peer.copy(version = 9, nickname = "가상 프로필", signature = null, interests = emptySet(), kind = ProfileKind.VIRTUAL_PROFILE)
        deps.encountersState.value = LoadState.Ready(listOf(HistoryFixtures.encounter.copy(peer = anonymous)), 9)
        runCurrent()
        model.askBlock(); runCurrent()
        assertEquals("이 상대 차단", model.uiState.value.confirmation?.title)
        val missing = watch(SafetyViewModel(deps, "unavailable-peer"))
        assertNull(missing.uiState.value.safetyPeer)
        missing.askBlock(); runCurrent()
        assertEquals("이 상대 차단", missing.uiState.value.confirmation?.actionLabel)
        missing.confirmAction(); runCurrent()
        assertEquals("unavailable-peer", (deps.calls.single() as BlockPeerRequest).peerProfileId)
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
        model.setContacts(ContactIds(kakaoTalkId = validKakao())); runCurrent()
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
        val model = watch(RelationshipsViewModel(deps))
        assertNotNull(model.uiState.value.encounters.valueOrNull())
        deps.encountersState.value = LoadState.Failed(AppError.Network, 2); runCurrent()
        assertNull(model.uiState.value.encounters.valueOrNull())
    }
    @Test fun `sharing needs explicit valid selection and failed request retries same snapshot`() = modelTest {
        deps.requestsState.value = LoadState.Ready(emptyList(), 1)
        val model = watch(AfterRequestViewModel(deps, "encounter"))
        model.request(); runCurrent()
        assertTrue(deps.calls.isEmpty())
        assertNotNull(model.uiState.value.contactError)
        model.setContacts(ContactIds(kakaoTalkId = validKakao())); model.setSaveToProfile(true); runCurrent()
        deps.error = AppError.Network
        model.request(); runCurrent()
        deps.error = null
        model.retry("request"); runCurrent()
        assertEquals(deps.calls[0], deps.calls[1])
        val request = deps.calls[0] as RequestAfterRequest
        assertEquals(ContactIds(kakaoTalkId = validKakao()), request.contacts)
        assertTrue(request.saveToProfile)
    }

    @Test fun `decline needs no contact but accept requires one`() = modelTest {
        val model = watch(AfterRequestViewModel(deps, "encounter"))
        model.respond("request", RequestDecision.ACCEPT); runCurrent()
        assertTrue(deps.calls.isEmpty())
        model.respond("request", RequestDecision.DECLINE); runCurrent()
        val request = deps.calls.single() as RespondAfterRequest
        assertEquals(ContactIds(), request.contacts)
        assertEquals(RequestDecision.DECLINE, request.decision)
    }

    @Test fun `saved private ids are loaded into draft but never selected for sharing`() = modelTest {
        val contacts = ContactIds(kakaoTalkId = validKakao())
        deps.profileState.value = LoadState.Ready(HistoryFixtures.profile.copy(contacts = contacts), 2)
        val profile = watch(ProfileSetupViewModel(deps))
        assertEquals(contacts, profile.uiState.value.draft.contacts)
        val request = watch(AfterRequestViewModel(deps, "encounter"))
        assertEquals(ContactIds(), request.uiState.value.shareContacts)
        request.respond("request", RequestDecision.ACCEPT); runCurrent()
        assertTrue(deps.calls.isEmpty())
    }

    @Test fun `private profile ids validate before save and remain private on public profile`() = modelTest {
        val model = watch(ProfileSetupViewModel(deps))
        model.updateDraft(model.uiState.value.draft.copy(contacts = ContactIds(instagramId = "!"))); runCurrent()
        model.save(); runCurrent()
        assertTrue(deps.calls.isEmpty())
        assertNotNull(model.uiState.value.draftError)
        model.updateDraft(model.uiState.value.draft.copy(contacts = ContactIds(kakaoTalkId = validKakao()))); runCurrent()
        model.save(); runCurrent()
        assertEquals(validKakao(), (deps.calls.single() as SaveProfileRequest).draft.contacts.kakaoTalkId)
    }

    @Test fun `person list deduplicates peers uses latest encounter and prioritizes incoming`() = modelTest {
        val other = HistoryFixtures.peer.copy(id = "other", nickname = "별보는밤")
        deps.encountersState.value = LoadState.Ready(listOf(HistoryFixtures.encounter,
            HistoryFixtures.encounter.copy(id = "newer", endedAtEpochMillis = 1_900_000_000_000),
            HistoryFixtures.encounter.copy(id = "other-encounter", peer = other, endedAtEpochMillis = 2_000_000_000_000)), 2)
        val model = watch(RelationshipsViewModel(deps, HistoryFixtures.peer.id))
        val people = model.uiState.value.people
        assertEquals(2, people.size)
        assertEquals(HistoryFixtures.peer.id, people.first().peer.id)
        assertEquals("newer", model.uiState.value.person?.encounter?.id)
        deps.blocksState.value = LoadState.Ready(listOf(HistoryFixtures.block), 3); runCurrent()
        assertEquals(listOf("other"), model.uiState.value.people.map { it.peer.id })
        assertNull(model.uiState.value.person)
    }

    @Test fun `relationship commands require confirmation and parked policy`() = modelTest {
        val model = watch(RelationshipsViewModel(deps, HistoryFixtures.peer.id))
        model.confirmAction(); runCurrent(); assertTrue(deps.calls.isEmpty())
        model.askEnd(); runCurrent()
        deps.driving.value = DrivingState(policy = DrivingPolicy.RESTRICTED)
        model.confirmAction(); runCurrent(); assertTrue(deps.calls.isEmpty())
        deps.driving.value = DrivingState(policy = DrivingPolicy.ALLOWED)
        model.retry("end:after"); runCurrent()
        assertTrue(deps.calls.single() is EndAfterConnectionRequest)
    }

    @Test fun `home protection is rechecked on every acceptance retry but allows decline`() = modelTest {
        val model = watch(AfterRequestViewModel(deps, "encounter"))
        model.setContacts(ContactIds(kakaoTalkId = validKakao())); runCurrent()
        deps.error = AppError.Network
        model.respond("request", RequestDecision.ACCEPT); runCurrent()
        assertEquals(1, deps.calls.size)
        deps.homeZone.value = HomeZoneState(status = HomeZoneStatus.INSIDE); runCurrent()
        deps.error = null
        model.retry("respond:request"); runCurrent()
        assertEquals(1, deps.calls.size)
        assertEquals(AppError.PeerUnavailable, model.uiState.value.command("respond:request").error)
        model.setContacts(ContactIds(kakaoTalkId = validKakao())); runCurrent()
        model.respond("request", RequestDecision.DECLINE); runCurrent()
        assertEquals(RequestDecision.DECLINE, (deps.calls.last() as RespondAfterRequest).decision)
    }

    @Test fun `mixed sync revisions fail closed for contact display`() = modelTest {
        val model = watch(RelationshipsViewModel(deps, HistoryFixtures.peer.id))
        assertTrue(model.uiState.value.contactsProjectionReady)
        deps.blocksState.value = LoadState.Ready(emptyList(), 2); runCurrent()
        assertFalse(model.uiState.value.contactsProjectionReady)
        deps.blocksState.value = LoadState.Failed(AppError.Network, 2); runCurrent()
        assertFalse(model.uiState.value.contactsProjectionReady)
    }

}
