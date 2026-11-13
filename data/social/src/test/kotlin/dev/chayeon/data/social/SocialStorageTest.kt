package dev.chayeon.data.social

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.chayeon.core.engine.ChayeonEngine
import dev.chayeon.core.engine.SignatureRules
import dev.chayeon.core.model.*
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SocialStorageTest {
    private lateinit var context: Context
    private val rules = object : SignatureRules {
        override fun chemistry(first: PublicSignature, second: PublicSignature) = Chemistry()
        override fun title(traits: List<PublicTrait>) = "시그니처를 만드는 중이에요"
    }

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        File(context.noBackupFilesDir, "chayeon-social").deleteRecursively()
    }

    @Test fun soloHasOneOwnerAndCleanupRestartsWithAnEmptyProfile() = runTest {
        val backend = LocalBackend(context, rules)
        try {
            val first = success(backend.enter(EnterSessionRequest(CommandMeta("first"), SessionMode.SOLO)))
            val again = success(backend.enter(EnterSessionRequest(CommandMeta("again"), SessionMode.SOLO)))
            assertEquals(first.accountId, again.accountId)
            val profile = success(backend.sync(again)).profile!!
            assertEquals("", profile.publicProfile.nickname)
            assertEquals(RegistrationState.PROFILE_REQUIRED, profile.registration)
            backend.execute(again, ApiRoutes.SAVE_PROFILE, socialJson.encodeToString(SaveProfileRequest.serializer(),
                SaveProfileRequest(CommandMeta("save"), ProfileDraft("나의별명", AvatarId.STAR, setOf(InterestId.JAZZ)))) )
            assertEquals("나의별명", success(backend.sync(again)).profile!!.publicProfile.nickname)
            success(backend.clearLocalData())
            val next = success(backend.enter(EnterSessionRequest(CommandMeta("next"), SessionMode.SOLO)))
            val fresh = success(backend.sync(next)).profile!!
            assertNotEquals(profile.id, fresh.id)
            assertEquals("", fresh.publicProfile.nickname)
            assertEquals(AvatarId.DAWN, fresh.publicProfile.avatar)
            assertEquals(emptySet<InterestId>(), fresh.publicProfile.interests)
            assertEquals(ContactIds(), fresh.contacts)
            assertEquals(RegistrationState.PROFILE_REQUIRED, fresh.registration)
        } finally { backend.close() }
    }

    @Test fun logoutAndDeletionBothReturnSoloToEmptyFirstRun() = runTest {
        for (delete in listOf(false, true)) {
            val driving = object : DrivingStateProvider {
                override val drivingState = kotlinx.coroutines.flow.flowOf(DrivingState(policy = DrivingPolicy.ALLOWED))
            }
            val graph = SocialGraph(context, null, rules, { AppResult.Success(Unit) }, driving)
            try {
                graph.sessionRepository.session.first { it is SessionState.SignedOut }
                success(graph.sessionRepository.enter(EnterSessionRequest(CommandMeta("enter-$delete"), SessionMode.SOLO)))
                success(graph.profileRepository.saveProfile(SaveProfileRequest(CommandMeta("save-$delete"),
                    ProfileDraft("나의별명", AvatarId.STAR, setOf(InterestId.JAZZ)))))
                if (delete) success(graph.sessionRepository.deleteAccount(DeleteAccountRequest(CommandMeta("delete"))))
                else success(graph.sessionRepository.signOut(SignOutRequest(CommandMeta("logout"))))
                assertTrue(graph.sessionRepository.session.first() is SessionState.SignedOut)
                success(graph.sessionRepository.enter(EnterSessionRequest(CommandMeta("fresh-$delete"), SessionMode.SOLO)))
                val fresh = (graph.profileRepository.profile.first { it is LoadState.Ready } as LoadState.Ready).value!!
                assertEquals("", fresh.publicProfile.nickname)
                assertEquals(AvatarId.DAWN, fresh.publicProfile.avatar)
                assertEquals(RegistrationState.PROFILE_REQUIRED, fresh.registration)
                success(graph.sessionRepository.signOut(SignOutRequest(CommandMeta("finish-$delete"))))
            } finally { graph.close() }
        }
    }

    @Test fun `sessions are backup excluded endpoint scoped and clearable`() {
        val session = SocialCoordinatorTest.testSession(SessionMode.SERVER_DEMO)
        PrivateSessionStorage(context, "remote-session.json", "https://one.example").write(session)
        val file = File(context.noBackupFilesDir, "chayeon-social/remote-session.json")
        assertTrue(file.isFile)
        assertEquals(session, PrivateSessionStorage(context, "remote-session.json", "https://one.example").read())
        assertNull(PrivateSessionStorage(context, "remote-session.json", "https://two.example").read())
        assertFalse(file.exists())
    }

    @Test fun installSecretSurvivesStorageRecreationAndSessionCleanup() {
        val original = PrivateClientSecretStorage(context).getOrCreate("https://one.example", "room")
        PrivateSessionStorage(context, "remote-session.json").clear()
        val restored = PrivateClientSecretStorage(context)
        assertEquals(original, restored.getOrCreate("https://one.example", "room"))
        assertNotEquals(original, restored.getOrCreate("https://one.example", "other"))
        assertNotEquals(original, restored.getOrCreate("https://two.example", "room"))
        assertTrue(File(context.noBackupFilesDir, "chayeon-social/client-secrets.json").isFile)
    }

    @Test fun storedLegacySessionAndEngineIgnoreRemovedPersonaWithoutChangingTheWireContract() {
        val engine = ChayeonEngine(rules, store = PrivateEngineStore(context), demoEnabled = false)
        val session = success(engine.enterSession("local-owner", EnterSessionRequest(CommandMeta("legacy"), SessionMode.SOLO)))
        PrivateSessionStorage(context, "local-session.json").write(session)
        for (name in listOf("local-session.json", "engine.json")) {
            val file = File(context.noBackupFilesDir, "chayeon-social/$name")
            file.writeText(file.readText().replace("\"mode\":\"SOLO\"", "\"mode\":\"SOLO\",\"persona\":\"ACCOUNT_A\""))
        }
        assertEquals(session, PrivateSessionStorage(context, "local-session.json").read())
        val restored = ChayeonEngine(rules, store = PrivateEngineStore(context), demoEnabled = false)
        assertEquals(session.id, success(restored.snapshotFor(session.accountId)).id)
        val wire = socialJson.encodeToString(EnterSessionRequest.serializer(), EnterSessionRequest(CommandMeta("wire"), SessionMode.SOLO))
            .replace("\"mode\":\"SOLO\"", "\"mode\":\"SOLO\",\"persona\":\"ACCOUNT_A\"")
        assertTrue(runCatching { socialJson.decodeFromString(EnterSessionRequest.serializer(), wire) }.isFailure)
    }

    @Test fun `cleanup marker survives a new storage instance and contains no credential`() {
        val record = CleanupRecord(ApiRoutes.SIGN_OUT,
            socialJson.encodeToString(SignOutRequest.serializer(), SignOutRequest(CommandMeta("out"))),
            AppResult.Success(CommandReceipt("out", 2)))
        PrivateCleanupStorage(context).write(record)
        assertEquals(record, PrivateCleanupStorage(context).read())
        val file = File(context.noBackupFilesDir, "chayeon-social/cleanup.json")
        val encoded = file.readText()
        assertFalse(encoded.contains("accessToken"))
        assertFalse(encoded.contains("latitude"))
        PrivateCleanupStorage(context).clear()
        assertFalse(file.exists())
    }

    @Test fun `restart cleanup clears remote credentials even when server mode is now disabled`() = runTest {
        PrivateSessionStorage(context, "remote-session.json", "https://example.test")
            .write(SocialCoordinatorTest.testSession(SessionMode.SERVER_DEMO))
        PrivateCleanupStorage(context).write(CleanupRecord(ApiRoutes.SIGN_OUT,
            socialJson.encodeToString(SignOutRequest.serializer(), SignOutRequest(CommandMeta("out"))),
            AppResult.Success(CommandReceipt("out", 2))))
        val graph = SocialGraph(context, null, rules, { AppResult.Success(Unit) })
        graph.sessionRepository.session.first { it is SessionState.SignedOut && it.version == 2L }
        assertFalse(File(context.noBackupFilesDir, "chayeon-social/remote-session.json").exists())
        assertNull(PrivateCleanupStorage(context).read())
        graph.close()
    }

    @Test fun `graph exposes server mode only when configured and starts with driving unknown`() = runTest {
        val graph = SocialGraph(context, null, rules, { AppResult.Success(Unit) })
        assertEquals(setOf(SessionMode.SOLO), graph.sessionRepository.availableModes.first())
        assertEquals(AppResult.Failure(AppError.Restricted), graph.sessionRepository.enter(
            EnterSessionRequest(CommandMeta("enter"), SessionMode.SOLO)))
        graph.close()
        val remoteGraph = SocialGraph(context, "https://example.test", rules, { AppResult.Success(Unit) })
        assertEquals(setOf(SessionMode.SOLO, SessionMode.SERVER_DEMO), remoteGraph.sessionRepository.availableModes.first())
        remoteGraph.close()
    }

    @Test fun `solo restart preserves profile and encounters and signout cleanup removes persisted data`() = runTest {
        val engine = ChayeonEngine(rules = rules, store = PrivateEngineStore(context), demoEnabled = false)
        val session = success(engine.enterSession("account_a", EnterSessionRequest(CommandMeta("enter-a"), SessionMode.SOLO)))
        success(engine.enterSession("account_b", EnterSessionRequest(CommandMeta("enter-b"), SessionMode.SOLO)))
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
        success(engine.leaveConnection("account_a", LeaveConnectionRequest(CommandMeta("leave"), accepted.connectionId!!)))
        PrivateSessionStorage(context, "local-session.json").write(session)
        val backend = LocalBackend(context, rules)
        assertEquals(AppResult.Success(session), backend.restoreSession())
        val snapshot = success(backend.sync(session))
        assertEquals("새벽드라이브", snapshot.profile?.publicProfile?.nickname)
        assertEquals(1, snapshot.encounters.size)
        val persisted = File(context.noBackupFilesDir, "chayeon-social/engine.json").readText()
        assertFalse(persisted.contains("latitude"))
        assertFalse(persisted.contains("longitude"))
        assertTrue(backend.clearLocalData() is AppResult.Success)
        assertFalse(File(context.noBackupFilesDir, "chayeon-social/engine.json").exists())
        assertFalse(File(context.noBackupFilesDir, "chayeon-social/local-session.json").exists())
        backend.close()
    }

    private fun <T> success(result: AppResult<T>): T {
        assertTrue("Expected success: $result", result is AppResult.Success)
        return (result as AppResult.Success).value
    }
}
