package dev.chayeon.data.social

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.chayeon.core.engine.ChayeonEngine
import dev.chayeon.core.engine.SignatureRules
import dev.chayeon.core.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SocialMessageSignalsTest {
    private val rules = object : SignatureRules {
        override fun chemistry(first: PublicSignature, second: PublicSignature) = Chemistry()
        override fun title(traits: List<PublicTrait>) = "시그니처를 만드는 중이에요"
    }

    @Test fun temporaryPeerGreetingIsPresentInSnapshotButNeverEmitsANotificationSignal() = runTest {
        val seeded = seedConversation()
        val snapshot = success(seeded.engine.snapshotFor("account_a"))
        assertTrue(snapshot.greetings.any { it.author == MessageAuthor.PEER })
        val graph = restoredGraph(seeded.context)
        try {
            withContext(Dispatchers.Default) {
                withTimeout(5_000) { graph.recordingSettings.first { it is LoadState.Ready } }
                assertTrue(graph.messageSignals.first().isEmpty())
            }
        } finally { graph.close() }
    }

    @Test fun afterPeerMessageStillEmitsASignalAndRespectsGreetingNotificationSetting() = runTest {
        val seeded = seedConversation()
        val ended = success(seeded.engine.leaveConnection("account_a",
            LeaveConnectionRequest(CommandMeta("leave"), seeded.connectionId)))
        val request = success(seeded.engine.requestAfter("account_a",
            RequestAfterRequest(CommandMeta("after-request"), checkNotNull(ended.encounterId))))
        val accepted = success(seeded.engine.respondAfter("account_b",
            RespondAfterRequest(CommandMeta("after-accept"), request.id, RequestDecision.ACCEPT)))
        val afterId = checkNotNull(accepted.afterConnectionId)
        success(seeded.engine.sendAfterMessage("account_a", SendAfterMessageRequest(CommandMeta("self-after"),
            afterId, "self-after", AfterMessageContent.Text("반가워요"))))
        val incoming = success(seeded.engine.sendAfterMessage("account_b", SendAfterMessageRequest(CommandMeta("peer-after"),
            afterId, "peer-after", AfterMessageContent.Text("저도 반가워요"))))
        val snapshot = success(seeded.engine.snapshotFor("account_a"))
        assertTrue(snapshot.greetings.any { it.author == MessageAuthor.PEER })
        assertEquals(2, snapshot.afterMessages.size)
        val graph = restoredGraph(seeded.context)
        try {
            withContext(Dispatchers.Default) {
                withTimeout(5_000) { graph.recordingSettings.first { it is LoadState.Ready } }
                assertEquals(listOf(incoming.id), graph.messageSignals.first())
                success(graph.profileRepository.updateSettings(UpdateSettingsRequest(CommandMeta("mute-greetings"),
                    notifyRequests = true, notifyGreetings = false)))
                withTimeout(5_000) { graph.messageSignals.first { it.isEmpty() } }
            }
        } finally { graph.close() }
    }

    private fun restoredGraph(context: Context) = SocialGraph(context, null, rules, { AppResult.Success(Unit) },
        drivingStateProvider = object : DrivingStateProvider {
            override val drivingState = flowOf(DrivingState(policy = DrivingPolicy.ALLOWED, gear = Gear.PARK))
        })

    private data class Seeded(val context: Context, val engine: ChayeonEngine, val connectionId: String)

    private fun seedConversation(): Seeded {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = ChayeonEngine(rules = rules, store = PrivateEngineStore(context), demoEnabled = false)
        val session = success(engine.enterSession("account_a", EnterSessionRequest(CommandMeta("enter-a"), SessionMode.SOLO, PersonaCode.ACCOUNT_A)))
        success(engine.enterSession("account_b", EnterSessionRequest(CommandMeta("enter-b"), SessionMode.SOLO, PersonaCode.ACCOUNT_B)))
        for (actor in listOf("account_a", "account_b")) {
            success(engine.saveProfile(actor, SaveProfileRequest(CommandMeta("profile-$actor"),
                ProfileDraft(if (actor == "account_a") "새벽드라이브" else "노을산책", AvatarId.DAWN, setOf(InterestId.JAZZ)))))
            for (consent in ConsentScope.entries) success(engine.recordConsent(actor, RecordConsentRequest(
                CommandMeta("consent-$actor-$consent"), consent, ConsentAction.GRANT, session.policy.consentDocumentVersion)))
            success(engine.setSyntheticPresence(actor, SetSyntheticPresenceRequest(CommandMeta("near-$actor"), SyntheticPresence.NEARBY)))
            success(engine.setDiscovery(actor, SetDiscoveryRequest(CommandMeta("discover-$actor"), DiscoveryMode.ON)))
        }
        val candidate = success(engine.snapshotFor("account_a")).discovery.candidates.single()
        val request = success(engine.requestConnection("account_a", RequestConnectionRequest(CommandMeta("request"), candidate.id)))
        val accepted = success(engine.respondConnection("account_b", RespondConnectionRequest(CommandMeta("accept"), request.id, RequestDecision.ACCEPT)))
        val connectionId = checkNotNull(accepted.connectionId)
        success(engine.sendGreeting("account_b", SendGreetingRequest(CommandMeta("temporary-greeting"), connectionId,
            "temporary-client-message", PresetCode.HELLO)))
        PrivateSessionStorage(context, "local-session.json").write(session)
        return Seeded(context, engine, connectionId)
    }

    private fun <T> success(result: AppResult<T>): T {
        assertTrue("Expected success: $result", result is AppResult.Success)
        return (result as AppResult.Success).value
    }
}
