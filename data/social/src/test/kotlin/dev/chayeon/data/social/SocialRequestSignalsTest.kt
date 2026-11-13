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
class SocialRequestSignalsTest {
    private val rules = object : SignatureRules {
        override fun chemistry(first: PublicSignature, second: PublicSignature) = Chemistry()
        override fun title(traits: List<PublicTrait>) = "시그니처를 만드는 중이에요"
    }

    @Test fun incomingRequestsEmitOnlyOpaqueIdentifiersAndRespectNotificationSettings() = runTest {
        val seeded = seedConversation()
        val graph = restoredGraph(seeded.context)
        try {
            withContext(Dispatchers.Default) {
                withTimeout(5_000) { graph.recordingSettings.first { it is LoadState.Ready } }
                assertEquals(listOf(seeded.requestId), graph.requestSignals.first())
                success(graph.profileRepository.updateSettings(UpdateSettingsRequest(CommandMeta("mute-requests"),
                    notifyRequests = false)))
                withTimeout(5_000) { graph.requestSignals.first { it.isEmpty() } }
            }
        } finally { graph.close() }
    }

    @Test fun acceptedContactExchangeDoesNotEmitContactIdsOrNotificationSignals() = runTest {
        val seeded = seedConversation()
        val accepted = success(seeded.engine.respondConnection("account_a", RespondConnectionRequest(
            CommandMeta("accept"), seeded.requestId, RequestDecision.ACCEPT)))
        success(seeded.engine.leaveConnection("account_a", LeaveConnectionRequest(
            CommandMeta("leave"), checkNotNull(accepted.connectionId))))
        val request = success(seeded.engine.requestAfter("account_b", RequestAfterRequest(
            CommandMeta("after-request"), success(seeded.engine.snapshotFor("account_b")).encounters.single().id,
            ContactIds(instagramId = "private.peer"))))
        success(seeded.engine.respondAfter("account_a", RespondAfterRequest(CommandMeta("after-accept"),
            request.id, RequestDecision.ACCEPT, ContactIds(kakaoTalkId = "private-self"))))
        val graph = restoredGraph(seeded.context)
        try {
            withContext(Dispatchers.Default) {
                withTimeout(5_000) { graph.recordingSettings.first { it is LoadState.Ready } }
                assertTrue(graph.requestSignals.first().isEmpty())
            }
        } finally { graph.close() }
    }

    private fun restoredGraph(context: Context) = SocialGraph(context, null, rules, { AppResult.Success(Unit) },
        drivingStateProvider = object : DrivingStateProvider {
            override val drivingState = flowOf(DrivingState(policy = DrivingPolicy.ALLOWED, gear = Gear.PARK))
        })

    private data class Seeded(val context: Context, val engine: ChayeonEngine, val requestId: String)

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
        val candidate = success(engine.snapshotFor("account_b")).discovery.candidates.single()
        val request = success(engine.requestConnection("account_b", RequestConnectionRequest(CommandMeta("request"), candidate.id)))
        PrivateSessionStorage(context, "local-session.json").write(session)
        return Seeded(context, engine, request.id)
    }

    private fun <T> success(result: AppResult<T>): T {
        assertTrue("Expected success: $result", result is AppResult.Success)
        return (result as AppResult.Success).value
    }
}
