package dev.chayeon.core.engine

import dev.chayeon.core.model.*
import java.util.UUID
import kotlinx.serialization.encodeToString
import org.junit.Test
import kotlin.test.*

internal fun generatedContacts() = ContactIds(instagramId = UUID.randomUUID().toString().replace("-", "").take(24))

class ContactExchangeTest {
    private val mine = generatedContacts()
    private val theirs = generatedContacts()

    private fun pair(): Fixture = Fixture().also { f ->
        f.join("a"); f.join("b"); f.join("c")
        val connection = f.connect()
        f.engine.leaveConnection("a", LeaveConnectionRequest(f.meta(), connection)).ok()
    }

    private fun request(f: Fixture, save: Boolean = false) = RequestAfterRequest(
        f.meta(), f.snap("a").encounters.single().id, mine, save,
    )

    private fun assertHidden(value: Any, contacts: ContactIds) {
        val text = value.toString()
        contacts.instagramId?.let { assertFalse(text.contains(it)) }
        contacts.kakaoTalkId?.let { assertFalse(text.contains(it)) }
    }

    @Test fun `only mutual acceptance publishes both contact sets atomically`() {
        val f = pair()
        val command = request(f)
        val pending = f.engine.requestAfter("a", command).ok()
        assertHidden(EngineJson.encodeToString(pending), mine)
        for (actor in listOf("a", "b", "c")) {
            assertHidden(EngineJson.encodeToString(f.snap(actor)), mine)
            assertTrue(f.snap(actor).afterConnections.isEmpty())
        }
        assertEquals(AppError.Restricted, f.engine.respondAfter("b", RespondAfterRequest(f.meta(), pending.id, RequestDecision.ACCEPT)).error())
        assertEquals(AppError.PeerUnavailable, f.engine.respondAfter("c", RespondAfterRequest(f.meta(), pending.id, RequestDecision.ACCEPT, theirs)).error())
        assertEquals(AfterRequestStatus.PENDING, f.snap("b").afterRequests.single().status)
        val response = f.engine.respondAfter("b", RespondAfterRequest(f.meta(), pending.id, RequestDecision.ACCEPT, theirs)).ok()
        assertHidden(EngineJson.encodeToString(response), mine)
        assertHidden(EngineJson.encodeToString(response), theirs)
        val a = f.snap("a").afterConnections.single()
        val b = f.snap("b").afterConnections.single()
        assertEquals(mine, a.ownSharedContacts)
        assertEquals(theirs, a.sharedContacts)
        assertEquals(theirs, b.ownSharedContacts)
        assertEquals(mine, b.sharedContacts)
        assertEquals(a.id, b.id)
        assertEquals(a.connectedAtEpochMillis, b.connectedAtEpochMillis)
        assertTrue(f.snap("c").afterConnections.isEmpty())
        assertEquals(ContactIds(), f.snap("a").profile!!.contacts)
        assertEquals(ContactIds(), f.snap("b").profile!!.contacts)
        assertNull(f.engine.exportState().afterRequests.getValue(pending.id).submittedContacts)
    }

    @Test fun `explicit profile saving stays private and cached profile responses refresh`() {
        val f = pair()
        val originalProfile = f.snap("a").profile!!.publicProfile
        val save = SaveProfileRequest(f.meta(), ProfileDraft(originalProfile.nickname, originalProfile.avatar, originalProfile.interests, mine))
        f.engine.saveProfile("a", save).ok()
        assertEquals(mine, f.snap("a").profile!!.contacts)
        assertHidden(EngineJson.encodeToString(f.snap("b")), mine)
        val pending = f.engine.requestAfter("a", request(f, save = true)).ok()
        f.engine.respondAfter("b", RespondAfterRequest(f.meta(), pending.id, RequestDecision.ACCEPT, theirs, true)).ok()
        assertEquals(theirs, f.snap("b").profile!!.contacts)
        f.engine.saveProfile("a", save.copy(meta = f.meta(), draft = save.draft.copy(contacts = ContactIds()))).ok()
        assertEquals(ContactIds(), f.engine.saveProfile("a", save).ok().contacts)
    }

    @Test fun `every pending terminal path destroys submitted IDs and retry cannot reveal them`() {
        for (action in listOf("decline", "withdraw", "expire", "block", "delete", "revoke")) {
            val f = pair()
            val command = request(f)
            val pending = f.engine.requestAfter("a", command).ok()
            when (action) {
                "decline" -> f.engine.respondAfter("b", RespondAfterRequest(f.meta(), pending.id, RequestDecision.DECLINE)).ok()
                "withdraw" -> f.engine.withdrawAfter("a", WithdrawAfterRequest(f.meta(), pending.id)).ok()
                "expire" -> f.advance(f.policy.afterTtlMillis)
                "block" -> f.engine.blockPeer("b", BlockPeerRequest(f.meta(), f.snap("a").profile!!.id)).ok()
                "delete" -> f.engine.deleteAccount("a", DeleteAccountRequest(f.meta())).ok()
                else -> f.engine.recordConsent("a", RecordConsentRequest(f.meta(), ConsentScope.PUBLIC_PROFILE, ConsentAction.REVOKE, f.policy.consentDocumentVersion)).ok()
            }
            assertNull(f.engine.exportState().afterRequests.getValue(pending.id).submittedContacts, action)
            assertHidden(EngineJson.encodeToString(f.engine.requestAfter("a", command)), mine)
            assertHidden(EngineJson.encodeToString(f.snap("b")), mine)
            assertTrue(f.engine.respondAfter("b", RespondAfterRequest(f.meta(), pending.id, RequestDecision.ACCEPT, theirs)) is AppResult.Failure)
        }
    }

    @Test fun `close block and deletion hide and purge exchanged IDs including cached copies`() {
        for (action in listOf("close", "block", "delete")) {
            val f = pair()
            if (action == "delete") {
                val profile = f.snap("a").profile!!.publicProfile
                f.engine.saveProfile("a", SaveProfileRequest(f.meta(), ProfileDraft(profile.nickname, profile.avatar, profile.interests, mine))).ok()
                f.engine.publishSignature("a", PublishSignatureRequest(f.meta(), null)).ok()
                assertTrue(EngineJson.encodeToString(f.engine.exportState().commands).contains(mine.instagramId!!))
            }
            val command = request(f, save = action == "delete")
            val pending = f.engine.requestAfter("a", command).ok()
            val peerCommand = UpdateSettingsRequest(f.meta(), false)
            f.engine.updateSettings("b", peerCommand).ok()
            val accept = RespondAfterRequest(f.meta(), pending.id, RequestDecision.ACCEPT, theirs)
            val id = f.engine.respondAfter("b", accept).ok().afterConnectionId!!
            when (action) {
                "close" -> f.engine.endAfterConnection("a", EndAfterConnectionRequest(f.meta(), id)).ok()
                "block" -> f.engine.blockPeer("b", BlockPeerRequest(f.meta(), f.snap("a").profile!!.id)).ok()
                else -> f.engine.deleteAccount("a", DeleteAccountRequest(f.meta())).ok()
            }
            val peer = f.snap("b").afterConnections.single()
            assertNull(peer.sharedContacts)
            assertNull(peer.ownSharedContacts)
            assertHidden(EngineJson.encodeToString(f.engine.respondAfter("b", accept)), mine)
            assertHidden(EngineJson.encodeToString(f.engine.requestAfter("a", command)), mine)
            if (action == "delete") assertEquals(AppError.AlreadyClosed, f.engine.updateSettings("b", peerCommand).error())
            val stored = EngineJson.encodeToString(f.engine.exportState())
            assertHidden(stored, mine)
            assertHidden(stored, theirs)
            if (action == "block") {
                f.engine.unblockPeer("b", UnblockPeerRequest(f.meta(), f.snap("b").blocks.single().id)).ok()
                assertNull(f.snap("b").afterConnections.single().sharedContacts)
            }
        }
    }

    @Test fun `demo exchange uses impossible Instagram IDs and real commands cannot forge them`() {
        val f = Fixture(demo = true); f.join("a")
        f.engine.requestConnection("a", RequestConnectionRequest(f.meta(), "profile-demo-star")).ok()
        f.advance(4_000)
        f.engine.leaveConnection("a", LeaveConnectionRequest(f.meta(), f.snap("a").connections.single().id)).ok()
        val encounter = f.snap("a").encounters.single().id
        val demo = DemoCast.peers.first().contacts
        assertFalse(demo.isValid(true))
        assertEquals(AppError.Restricted, f.engine.requestAfter("a", RequestAfterRequest(f.meta(), encounter, demo)).error())
        f.engine.requestAfter("a", RequestAfterRequest(f.meta(), encounter, mine)).ok()
        f.advance(4_000)
        assertEquals(demo, f.snap("a").afterConnections.single().sharedContacts)
    }
}
