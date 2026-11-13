package dev.chayeon.server

import dev.chayeon.core.engine.*
import dev.chayeon.core.model.*
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.server.testing.testApplication
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

private fun voiceMeta() = CommandMeta(UUID.randomUUID().toString())
private fun <T> AppResult<T>.value() = (this as AppResult.Success).value

private class VoiceFixture(
    demoEnabled: Boolean = false,
    voiceStateReader: (ChayeonEngine) -> EngineState = ChayeonEngine::exportState,
) {
    val clock = AtomicLong(1_000_000)
    val registry = RoomRegistry(defaultRules(), clock = EngineClock(clock::get),
        enginePolicy = EnginePolicy(presenceLeaseMillis = 60_000), demoEnabled = demoEnabled, voiceStateReader = voiceStateReader)
    val a = enter(PersonaCode.ACCOUNT_A)
    val b = enter(PersonaCode.ACCOUNT_B)
    val stranger = enter(PersonaCode.ACCOUNT_A, "other")
    val engine = registry.authorize(header(a))!!.second
    lateinit var id: String
    init {
        for (session in listOf(a, b)) {
            engine.saveProfile(session.accountId, SaveProfileRequest(voiceMeta(), ProfileDraft("가상상대", AvatarId.DAWN, emptySet()))).value()
            ConsentScope.entries.forEach { engine.recordConsent(session.accountId,
                RecordConsentRequest(voiceMeta(), it, ConsentAction.GRANT, session.policy.consentDocumentVersion)).value() }
            engine.setDiscovery(session.accountId, SetDiscoveryRequest(voiceMeta(), DiscoveryMode.ON)).value()
        }
    }
    fun enter(persona: PersonaCode, room: String = "voice") = registry.enter(EnterSessionRequest(
        voiceMeta(), SessionMode.SERVER_DEMO, persona, room, "voice-test-secret".repeat(3))).value()
    fun connect(): String {
        val request = engine.requestConnection(a.accountId, RequestConnectionRequest(voiceMeta(), engine.exportState().accounts.getValue(b.accountId).profile.id)).value()
        id = engine.respondConnection(b.accountId, RespondConnectionRequest(voiceMeta(), request.id, RequestDecision.ACCEPT)).value().connectionId!!
        return id
    }
    fun header(s: Session) = "Bearer ${s.accessToken}"
    fun end(path: String) {
        when (path) {
            "leave" -> registry.execute(header(a), ApiRoutes.LEAVE_CONNECTION, EngineJson.encodeToString(LeaveConnectionRequest(voiceMeta(), id)))
            "distance" -> engine.setSyntheticPresence(a.accountId, SetSyntheticPresenceRequest(voiceMeta(), SyntheticPresence.AWAY)).value()
            "unverifiable" -> engine.setSyntheticPresence(a.accountId, SetSyntheticPresenceRequest(voiceMeta(), SyntheticPresence.STALE)).value()
            "offline" -> engine.setSyntheticPresence(a.accountId, SetSyntheticPresenceRequest(voiceMeta(), SyntheticPresence.OFFLINE)).value()
            "discovery" -> engine.setDiscovery(a.accountId, SetDiscoveryRequest(voiceMeta(), DiscoveryMode.OFF)).value()
            "block" -> engine.blockPeer(a.accountId, BlockPeerRequest(voiceMeta(), engine.exportState().accounts.getValue(b.accountId).profile.id)).value()
            "delete" -> registry.execute(header(a), ApiRoutes.DELETE_ACCOUNT, EngineJson.encodeToString(DeleteAccountRequest(voiceMeta())))
            "logout" -> registry.execute(header(a), ApiRoutes.SIGN_OUT, EngineJson.encodeToString(SignOutRequest(voiceMeta())))
            "lease" -> { clock.addAndGet(60_001); registry.tick() }
            "revoked" -> enter(PersonaCode.ACCOUNT_A)
            else -> error("Unknown test path")
        }
    }
}

private suspend fun HttpClient.voice(session: Session, id: String) = webSocketSession("/v1/voice/$id") { bearerAuth(session.accessToken!!) }

class VoiceServerTest {
    @Test fun sameRoomAccountOutsideThePairCannotJoin() {
        val f = VoiceFixture(demoEnabled = true)
        val demo = f.engine.exportState().accounts.getValue("demo-slow")
        val request = f.engine.requestConnection(f.a.accountId, RequestConnectionRequest(voiceMeta(), demo.profile.id)).value()
        val id = f.engine.respondConnection(demo.id, RespondConnectionRequest(voiceMeta(), request.id, RequestDecision.ACCEPT)).value().connectionId!!
        assertNotNull(f.registry.joinVoice(f.header(f.a), id))
        assertNull(f.registry.joinVoice(f.header(f.b), id))
    }

    @Test fun rejectsStrangersMissingTokensUnknownAndEndedConnectionsBeforeUpgrade() = testApplication {
        val f = VoiceFixture()
        application { chayeonModule(f.registry) }
        f.connect()
        val ws = createClient { install(WebSockets) }
        suspend fun rejected(session: Session?, id: String) {
            var refusal: Throwable? = null
            try {
                val unexpected = ws.webSocketSession("/v1/voice/$id") {
                    if (session != null) bearerAuth(session.accessToken!!)
                }
                unexpected.close()
            } catch (failure: Exception) { refusal = failure }
            assertNotNull("Handshake must be refused", refusal)
            assertFalse(f.registry.voiceAllowed(session?.let(f::header), id))
        }
        for ((session, id) in listOf(f.stranger to f.id, f.a to "missing", null to f.id)) rejected(session, id)
        f.end("leave")
        rejected(f.b, f.id)
        ws.close()
    }

    @Test fun relaysBinaryOnlyToPeerAllowsRejoinAndDoesNotPersistAudio() = testApplication {
        val f = VoiceFixture()
        application { chayeonModule(f.registry) }
        f.connect()
        val ws = createClient { install(WebSockets) }
        val a = ws.voice(f.a, f.id)
        val b = ws.voice(f.b, f.id)
        try {
            val before = f.engine.exportState()
            val frame = ByteArray(640) { (it % 127).toByte() }
            a.send(Frame.Binary(true, frame))
            assertArrayEquals(frame, withTimeout(2_000) { (b.incoming.receive() as Frame.Binary).data })
            assertNull(withTimeoutOrNull(100) { a.incoming.receive() })
            assertEquals(before, f.engine.exportState())
            a.close()
            val replacement = ws.voice(f.a, f.id)
            replacement.send(Frame.Binary(true, frame))
            assertArrayEquals(frame, withTimeout(2_000) { (b.incoming.receive() as Frame.Binary).data })
            replacement.close()
        } finally { a.close(); b.close(); ws.close() }
    }

    @Test fun closesBothSocketsOnEveryEndOrRevocationAndRejectsLateJoin() {
        for (path in listOf("leave", "distance", "unverifiable", "offline", "discovery", "block", "delete", "logout", "lease", "revoked")) {
            testApplication {
                val f = VoiceFixture()
                application { chayeonModule(f.registry) }
                f.connect()
                val ws = createClient { install(WebSockets) }
                val a = ws.voice(f.a, f.id)
                val b = ws.voice(f.b, f.id)
                f.end(path)
                withTimeout(2_000) {
                    assertNotNull(path, a.closeReason.await())
                    assertNotNull(path, b.closeReason.await())
                }
                assertFalse(path, f.registry.voiceAllowed(f.header(f.a), f.id))
                if (path != "revoked") assertFalse(path, f.registry.voiceAllowed(f.header(f.b), f.id))
                ws.close()
            }
        }
    }

    @Test fun expiredRequestNeverGrantsVoiceMembershipAndSettingsDefaultOffPersistOptIn() {
        val f = VoiceFixture()
        val request = f.engine.requestConnection(f.a.accountId, RequestConnectionRequest(voiceMeta(),
            f.engine.exportState().accounts.getValue(f.b.accountId).profile.id)).value()
        assertFalse(f.engine.exportState().accounts.getValue(f.a.accountId).settings.receiveRequestsWhileDriving)
        f.engine.updateSettings(f.a.accountId, UpdateSettingsRequest(voiceMeta(), true, true)).value()
        assertTrue(f.engine.exportState().accounts.getValue(f.a.accountId).settings.receiveRequestsWhileDriving)
        f.clock.set(request.expiresAtEpochMillis)
        f.registry.tick()
        assertTrue(f.engine.respondConnection(f.b.accountId, RespondConnectionRequest(voiceMeta(), request.id, RequestDecision.ACCEPT)) is AppResult.Failure)
        assertNull(f.registry.joinVoice(f.header(f.a), request.id))
        assertNull(f.registry.joinVoice(f.header(f.b), "missing"))
    }

    @Test fun websocketRejectsOversizedAndTextFrames() {
        for (frame in listOf(Frame.Binary(true, ByteArray(641)), Frame.Text("not audio"))) testApplication {
            val f = VoiceFixture()
            application { chayeonModule(f.registry) }
            f.connect()
            val ws = createClient { install(WebSockets) }
            val a = ws.voice(f.a, f.id)
            a.send(frame)
            assertNotNull(withTimeout(2_000) { a.closeReason.await() })
            ws.close()
        }
    }

    @Test fun websocketDropsShortFloodWithoutClosing() = testApplication {
        val f = VoiceFixture()
        application { chayeonModule(f.registry) }
        f.connect()
        val ws = createClient { install(WebSockets) }
        val a = ws.voice(f.a, f.id)
        repeat(200) { a.send(Frame.Binary(true, ByteArray(640))) }
        delay(100)
        assertFalse(a.closeReason.isCompleted)
        a.close()
        ws.close()
    }

    @Test fun registryHotPathPerformsZeroEngineStateReadsForTenThousandFrames() {
        // Expected: exactly zero state reads during relay and queue consumption; tolerance: zero.
        val stateReads = AtomicLong()
        val f = VoiceFixture(voiceStateReader = { engine ->
            stateReads.incrementAndGet()
            engine.exportState()
        })
        f.connect()
        val a = f.registry.joinVoice(f.header(f.a), f.id)!!
        val b = f.registry.joinVoice(f.header(f.b), f.id)!!
        val beforeReads = stateReads.get()
        val beforeState = f.engine.exportState()
        var delivered = 0
        repeat(10_000) {
            assertTrue(f.registry.relayVoice(a, ByteArray(640)))
            if (b.frames.tryReceive().isSuccess) delivered++
        }
        assertTrue("The proof exercises forwarding as well as excess-frame drops", delivered >= 50)
        assertEquals(0L, stateReads.get() - beforeReads)
        assertEquals(beforeState, f.engine.exportState())
    }

    @Test fun leaveDistanceBlockAndDeletionCloseBothSocketsWithinThreeHundredMilliseconds() {
        // Expected end-to-end close propagation: at most 300 ms for each terminal command.
        for (path in listOf("leave", "distance", "block", "delete")) testApplication {
            val f = VoiceFixture()
            application { chayeonModule(f.registry) }
            f.connect()
            val ws = createClient { install(WebSockets) }
            val a = ws.voice(f.a, f.id)
            val b = ws.voice(f.b, f.id)
            val started = System.nanoTime()
            f.end(path)
            withTimeout(300) {
                assertNotNull(a.closeReason.await())
                assertNotNull(b.closeReason.await())
            }
            assertTrue(path, System.nanoTime() - started <= 300_000_000L)
            ws.close()
        }
    }
}
