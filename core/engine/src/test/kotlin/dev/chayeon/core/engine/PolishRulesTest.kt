package dev.chayeon.core.engine

import dev.chayeon.core.model.*
import java.io.File
import kotlinx.serialization.json.*
import org.junit.Test
import kotlin.test.*

class PolishRulesTest {
    @Test fun `tour partners lead discovery and remain connected through the driving step`() {
        assertEquals(listOf("demo-star", "demo-jazz", "demo-slow", "demo-sea", "demo-forest"), DemoCast.peers.map { it.id })
        val f = Fixture(demo = true); f.join("a")
        assertEquals(listOf("별보는밤", "재즈한잔"), f.snap("a").discovery.candidates.take(2).map { it.profile.nickname })
        f.engine.requestConnection("a", RequestConnectionRequest(f.meta(), "profile-demo-star")).ok()
        f.advance(4_000)
        f.advance(90_000)
        assertEquals(ConnectionStatus.CONNECTED, f.snap("a").connections.single().status)
        assertEquals(listOf(DemoResponse.ACCEPT, DemoResponse.ACCEPT, DemoResponse.ACCEPT, DemoResponse.DECLINE, DemoResponse.IGNORE), DemoCast.peers.map { it.response })
        assertEquals(4, DemoCast.peers.single { it.id == "demo-slow" }.presenceScript.size)
    }

    @Test fun `persona default nicknames never collide with the demo cast`() {
        val f = Fixture()
        val names = PersonaCode.entries.map { persona ->
            val actor = persona.name
            f.engine.enterSession(actor, EnterSessionRequest(f.meta(), SessionMode.SOLO, persona)).ok()
            f.snap(actor).profile!!.publicProfile.nickname
        }
        assertEquals(listOf("새벽드라이브", "노을산책"), names)
        assertEquals(names.size, names.toSet().size)
        assertTrue(names.none { name -> DemoCast.peers.any { it.nickname == name } })
        assertEquals("느린오후", DemoCast.peers.single { it.id == "demo-slow" }.nickname)
    }

    @Test fun `shared presets require both public traits and commands recheck after withdrawal`() {
        for (trait in listOf(TraitId.LONG_ROAD, TraitId.NIGHT_DRIVE)) {
            val code = PresetCode.valueOf("SHARE_$trait")
            val f = Fixture(); f.join("a"); f.join("b"); f.signature("b", trait)
            val connection = f.connect()
            assertFalse(code in f.snap("a").connections.single().availablePresets)
            assertEquals(AppError.Restricted, f.engine.sendGreeting("a", SendGreetingRequest(f.meta(), connection, "missing", code)).error())
            f.signature("a", trait)
            assertTrue(code in f.snap("a").connections.single().availablePresets)
            f.engine.publishSignature("a", PublishSignatureRequest(f.meta(), null)).ok()
            assertEquals(AppError.Restricted, f.engine.sendGreeting("a", SendGreetingRequest(f.meta(), connection, "withdrawn", code)).error())
            f.signature("b", TraitId.TIRE_CARE)
            assertTrue(PresetCode.ADMIRE_TIRE_CARE in f.snap("a").connections.single().availablePresets)
            assertFalse(PresetCode.GOOD_EVENING in f.snap("a").connections.single().availablePresets)
            f.engine.leaveConnection("a", LeaveConnectionRequest(f.meta(), connection)).ok()
            val request = f.engine.requestAfter("a", RequestAfterRequest(f.meta(), f.snap("a").encounters.single().id)).ok()
            val after = f.engine.respondAfter("b", RespondAfterRequest(f.meta(), request.id, RequestDecision.ACCEPT)).ok().afterConnectionId!!
            f.signature("b", trait)
            assertFalse(code in f.snap("a").afterConnections.single().availablePresets)
            assertEquals(AppError.Restricted, f.engine.sendAfterMessage("a", SendAfterMessageRequest(f.meta(), after, "unearned", AfterMessageContent.Preset(code))).error())
        }
    }

    @Test fun `runtime cast exactly matches the fixture used by signature scenario tests`() {
        val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }.first { File(it, "fixtures/drive").isDirectory }
        val fixture = Json.parseToJsonElement(File(root, "core/signature/src/test/resources/demo-cast.json").readText()).jsonArray
        val expected = fixture.associate { it.jsonObject.getValue("id").jsonPrimitive.content to Json.decodeFromJsonElement<List<PublicTrait>>(it.jsonObject.getValue("traits")) }
        assertEquals(expected, DemoCast.peers.associate { it.id to it.traits })
    }

    @Test fun `restoring an older cast refreshes its traits without reopening relationships`() {
        val (f, connection) = connected()
        f.engine.leaveConnection("a", LeaveConnectionRequest(f.meta(), connection)).ok()
        val state = f.engine.exportState()
        val old = state.accounts.getValue("demo-star")
        val profile = old.profile.publicProfile
        f.store.save(state.copy(accounts = state.accounts + (old.id to old.copy(profile = old.profile.copy(
            publicProfile = profile.copy(signature = profile.signature!!.copy(title = "예전 칭호", traits = listOf(PublicTrait(TraitId.GENTLE_RIDE, TraitLevel.VISIBLE)))),
        )))))
        val restored = ChayeonEngine(f.rules, EngineClock { f.now }, store = f.store, demoEnabled = true)
        val current = restored.exportState().accounts.getValue("demo-star").profile.publicProfile.signature!!
        assertEquals(DemoCast.peers.single { it.id == "demo-star" }.traits, current.traits)
        assertEquals(f.rules.title(current.traits), current.title)
        assertEquals(ConnectionStatus.ENDED, restored.snapshotFor("a").ok().connections.single().status)
        assertEquals(state.greetings, restored.exportState().greetings)
        assertEquals(state.encounters.keys, restored.exportState().encounters.keys)
    }

    private fun connected(): Pair<Fixture, String> {
        val f = Fixture(demo = true); f.join("a")
        f.engine.requestConnection("a", RequestConnectionRequest(f.meta(), "profile-demo-star")).ok()
        f.advance(4_000)
        return f to f.snap("a").connections.single().id
    }

    @Test fun `greetings answer one turn after a delay including a burst before the opening greeting`() {
        val (f, connection) = connected()
        fun send(id: String) = f.engine.sendGreeting("a", SendGreetingRequest(f.meta(), connection, id, PresetCode.HELLO)).ok()
        send("one"); send("two"); send("two")
        f.advance(1_799)
        assertEquals(0, f.snap("a").greetings.count { it.author == MessageAuthor.PEER })
        f.advance(1)
        assertEquals(1, f.snap("a").greetings.count { it.author == MessageAuthor.PEER })
        f.advance(10_000)
        assertEquals(1, f.snap("a").greetings.count { it.author == MessageAuthor.PEER })
        send("three"); f.advance(1_800)
        assertEquals(2, f.snap("a").greetings.count { it.author == MessageAuthor.PEER })
        send("late")
        f.engine.leaveConnection("a", LeaveConnectionRequest(f.meta(), connection)).ok()
        f.advance(10_000)
        assertEquals(2, f.snap("a").greetings.count { it.author == MessageAuthor.PEER })
    }

    private fun after(): Pair<Fixture, String> {
        val (f, connection) = connected()
        f.engine.leaveConnection("a", LeaveConnectionRequest(f.meta(), connection)).ok()
        f.engine.requestAfter("a", RequestAfterRequest(f.meta(), f.snap("a").encounters.single().id)).ok()
        f.advance(4_000)
        return f to f.snap("a").afterConnections.single().id
    }

    @Test fun `after replies are delayed durable and identical for sparse or frequent ticks`() {
        val (sparse, sa) = after(); val (frequent, fa) = after()
        fun send(f: Fixture, id: String) = f.engine.sendAfterMessage("a", SendAfterMessageRequest(f.meta(), id, "first", AfterMessageContent.Text("반가워요"))).ok()
        send(sparse, sa); send(frequent, fa)
        sparse.advance(1_799)
        assertEquals(0, sparse.snap("a").afterMessages.count { it.author == MessageAuthor.PEER })
        sparse.advance(8_201)
        repeat(100) { frequent.advance(100) }
        assertEquals(sparse.engine.exportState(), frequent.engine.exportState())
        assertEquals(1, sparse.snap("a").afterMessages.count { it.author == MessageAuthor.PEER })
        val restored = ChayeonEngine(sparse.rules, EngineClock { sparse.now }, store = sparse.store, demoEnabled = true)
        restored.tick(sparse.now + 5_000)
        assertEquals(1, restored.snapshotFor("a").ok().afterMessages.count { it.author == MessageAuthor.PEER })
    }

    @Test fun `closed blocked and revoked after relationships never receive a scheduled reply`() {
        for (action in listOf("close", "block", "revoke")) {
            val (f, id) = after()
            f.engine.sendAfterMessage("a", SendAfterMessageRequest(f.meta(), id, "pending", AfterMessageContent.Text("다시 반가워요"))).ok()
            when (action) {
                "close" -> f.engine.endAfterConnection("a", EndAfterConnectionRequest(f.meta(), id)).ok()
                "block" -> f.engine.blockPeer("a", BlockPeerRequest(f.meta(), "profile-demo-star")).ok()
                else -> f.engine.recordConsent("a", RecordConsentRequest(f.meta(), ConsentScope.PUBLIC_PROFILE, ConsentAction.REVOKE, f.policy.consentDocumentVersion)).ok()
            }
            f.advance(10_000)
            assertTrue(f.snap("a").afterMessages.none { it.author == MessageAuthor.PEER })
        }
    }
}
