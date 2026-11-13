package dev.chayeon.core.engine

import dev.chayeon.core.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class IntegrityTest {
    private fun pair() = Fixture().also { it.join("a"); it.join("b") }
    private fun after(f: Fixture): String {
        val c = f.connect()
        f.engine.leaveConnection("a", LeaveConnectionRequest(f.meta(), c)).ok()
        val r = f.engine.requestAfter("a", RequestAfterRequest(f.meta(), f.snap("a").encounters.single().id, ContactIds(instagramId = java.util.UUID.randomUUID().toString().replace("-", "").take(24)))).ok()
        return f.engine.respondAfter("b", RespondAfterRequest(f.meta(), r.id, RequestDecision.ACCEPT, ContactIds(instagramId = java.util.UUID.randomUUID().toString().replace("-", "").take(24)))).ok().afterConnectionId!!
    }

    @Test fun leaveReplayRemainsTerminalAfterBlock() {
        val f = pair(); val c = f.connect()
        val command = LeaveConnectionRequest(f.meta(), c)
        val original = f.engine.leaveConnection("b", command).ok()
        assertEquals(original, f.engine.leaveConnection("b", command).ok())
        f.engine.blockPeer("a", BlockPeerRequest(f.meta(), f.snap("b").profile!!.id)).ok()
    }

    @Test fun endAfterReplayRemainsTerminalAfterBlock() {
        val f = pair(); val ac = after(f)
        val command = EndAfterConnectionRequest(f.meta(), ac)
        val original = f.engine.endAfterConnection("b", command).ok()
        assertEquals(original, f.engine.endAfterConnection("b", command).ok())
        f.engine.blockPeer("a", BlockPeerRequest(f.meta(), f.snap("b").profile!!.id)).ok()
    }

    @Test fun blockedPeerCannotTrackProfileOrRevision() {
        val f = pair(); after(f)
        f.engine.blockPeer("a", BlockPeerRequest(f.meta(), f.snap("b").profile!!.id)).ok()
        val before = f.snap("b")
        f.engine.saveProfile("a", SaveProfileRequest(f.meta(), ProfileDraft("새로운닉네임", AvatarId.STAR, setOf(InterestId.TRAVEL)))).ok()
        assertEquals(before, f.snap("b"))
        assertEquals("가상 프로필", f.snap("b").connections.single().peer.nickname)
        assertEquals("가상상대", f.snap("a").blocks.single().peer.nickname)
    }

    @Test fun everyReachableProjectionMatchesConsentRevocation() {
        fun observe(block: Boolean): List<String> {
            val f = pair(); val ac = after(f)
            val peerId = f.snap("a").profile!!.id
            val discovery = RefreshDiscoveryRequest(f.meta())
            f.engine.refreshDiscovery("b", discovery).ok()
            val end = EndAfterConnectionRequest(f.meta(), ac)
            f.engine.endAfterConnection("b", end).ok()
            if (block) f.engine.blockPeer("a", BlockPeerRequest(f.meta(), f.snap("b").profile!!.id)).ok()
            else f.engine.recordConsent("a", RecordConsentRequest(f.meta(), ConsentScope.PUBLIC_PROFILE,
                ConsentAction.REVOKE, f.policy.consentDocumentVersion)).ok()
            return listOf(
                EngineJson.encodeToString(f.snap("b")),
                EngineJson.encodeToString(f.engine.endAfterConnection("b", end)),
                EngineJson.encodeToString(f.engine.refreshDiscovery("b", discovery)),
                EngineJson.encodeToString(f.engine.requestConnection("b", RequestConnectionRequest(f.meta(), peerId))),
                EngineJson.encodeToString(f.engine.requestAfter("b", RequestAfterRequest(f.meta(), f.snap("b").encounters.single().id, ContactIds(instagramId = java.util.UUID.randomUUID().toString().replace("-", "").take(24))))),
                EngineJson.encodeToString(f.engine.respondAfter("b", RespondAfterRequest(f.meta(), f.snap("b").afterRequests.single().id, RequestDecision.ACCEPT, ContactIds(instagramId = java.util.UUID.randomUUID().toString().replace("-", "").take(24))))),
                EngineJson.encodeToString(f.engine.respondConnection("b", RespondConnectionRequest(f.meta(), f.snap("b").connectionRequests.single().id, RequestDecision.ACCEPT))),
            )
        }
        assertEquals(observe(false), observe(true))
    }

    @Test fun pendingAndActiveRelationshipProjectionsMatchRevocation() {
        for (stage in 0..3) {
            fun observe(block: Boolean): SyncSnapshot {
                val f = pair()
                val request = f.request()
                if (stage > 0) {
                    val c = f.engine.respondConnection("b", RespondConnectionRequest(f.meta(), request.id, RequestDecision.ACCEPT)).ok().connectionId!!
                    if (stage > 1) {
                        f.engine.leaveConnection("b", LeaveConnectionRequest(f.meta(), c)).ok()
                        val ar = f.engine.requestAfter("a", RequestAfterRequest(f.meta(), f.snap("a").encounters.single().id, ContactIds(instagramId = java.util.UUID.randomUUID().toString().replace("-", "").take(24)))).ok()
                        if (stage > 2) f.engine.respondAfter("b", RespondAfterRequest(f.meta(), ar.id, RequestDecision.ACCEPT, ContactIds(instagramId = java.util.UUID.randomUUID().toString().replace("-", "").take(24)))).ok()
                    }
                }
                val command = f.meta()
                if (block) f.engine.blockPeer("a", BlockPeerRequest(command, f.snap("b").profile!!.id)).ok()
                else {
                    // Consume the opaque block identifier so both fixtures assign the same encounter identifiers.
                    f.meta()
                    f.engine.recordConsent("a", RecordConsentRequest(command, ConsentScope.PUBLIC_PROFILE,
                        ConsentAction.REVOKE, f.policy.consentDocumentVersion)).ok()
                }
                return f.snap("b")
            }
            assertEquals("stage $stage", observe(false), observe(true))
        }
    }

    @Test fun allCommandProjectionReplaysMatchRevocation() {
        fun observe(block: Boolean): List<String> {
            val f = pair()
            val api = EngineApi(f.engine)
            val commands = mutableListOf<Pair<String, String>>()
            fun execute(route: String, body: String): JsonObject {
                commands += route to body
                val result = EngineJson.parseToJsonElement(api.execute("b", route, body)).jsonObject.getValue("result").jsonObject
                assertEquals("success", result.getValue("type").jsonPrimitive.content)
                return result.getValue("value").jsonObject
            }
            val profileId = f.snap("a").profile!!.id
            execute(ApiRoutes.SAVE_PROFILE, EngineJson.encodeToString(SaveProfileRequest(f.meta(), ProfileDraft("가상상대", AvatarId.SLOW, setOf(InterestId.JAZZ)))))
            execute(ApiRoutes.UPDATE_SETTINGS, EngineJson.encodeToString(UpdateSettingsRequest(f.meta(), false, true)))
            execute(ApiRoutes.RECORD_CONSENT, EngineJson.encodeToString(RecordConsentRequest(f.meta(), ConsentScope.PUBLIC_PROFILE, ConsentAction.GRANT, f.policy.consentDocumentVersion)))
            execute(ApiRoutes.PUBLISH_SIGNATURE, EngineJson.encodeToString(PublishSignatureRequest(f.meta(), null)))
            execute(ApiRoutes.SET_DISCOVERY, EngineJson.encodeToString(SetDiscoveryRequest(f.meta(), DiscoveryMode.ON)))
            execute(ApiRoutes.REFRESH_DISCOVERY, EngineJson.encodeToString(RefreshDiscoveryRequest(f.meta())))
            execute(ApiRoutes.SET_SYNTHETIC_PRESENCE, EngineJson.encodeToString(SetSyntheticPresenceRequest(f.meta(), SyntheticPresence.NEARBY)))
            val request = execute(ApiRoutes.REQUEST_CONNECTION, EngineJson.encodeToString(RequestConnectionRequest(f.meta(), profileId)))
            val c = f.engine.respondConnection("a", RespondConnectionRequest(f.meta(), request.getValue("id").jsonPrimitive.content, RequestDecision.ACCEPT)).ok().connectionId!!
            execute(ApiRoutes.LEAVE_CONNECTION, EngineJson.encodeToString(LeaveConnectionRequest(f.meta(), c)))
            val ar = execute(ApiRoutes.REQUEST_AFTER, EngineJson.encodeToString(RequestAfterRequest(f.meta(), f.snap("b").encounters.single().id, ContactIds(instagramId = java.util.UUID.randomUUID().toString().replace("-", "").take(24)))))
            val ac = f.engine.respondAfter("a", RespondAfterRequest(f.meta(), ar.getValue("id").jsonPrimitive.content, RequestDecision.ACCEPT, ContactIds(instagramId = java.util.UUID.randomUUID().toString().replace("-", "").take(24)))).ok().afterConnectionId!!
            execute(ApiRoutes.END_AFTER_CONNECTION, EngineJson.encodeToString(EndAfterConnectionRequest(f.meta(), ac)))
            execute(ApiRoutes.REPORT_PEER, EngineJson.encodeToString(ReportPeerRequest(f.meta(), profileId, ReportReason.OTHER)))
            val command = f.meta()
            if (block) f.engine.blockPeer("a", BlockPeerRequest(command, f.snap("b").profile!!.id)).ok()
            else {
                f.meta()
                f.engine.recordConsent("a", RecordConsentRequest(command, ConsentScope.PUBLIC_PROFILE, ConsentAction.REVOKE, f.policy.consentDocumentVersion)).ok()
            }
            val replays = commands.map { (route, body) -> api.execute("b", route, body) }
            val ownBlock = execute(ApiRoutes.BLOCK_PEER, EngineJson.encodeToString(BlockPeerRequest(f.meta(), profileId)))
            return replays + ownBlock.toString() + EngineJson.encodeToString(f.snap("b"))
        }
        assertEquals(observe(false), observe(true))
    }

    @Test fun hiddenActivityCannotChangeReplaysOrOptimisticCommands() {
        val f = pair(); after(f)
        f.engine.blockPeer("a", BlockPeerRequest(f.meta(), f.snap("b").profile!!.id)).ok()
        val refresh = RefreshDiscoveryRequest(f.meta())
        val result = f.engine.refreshDiscovery("b", refresh)
        val before = f.snap("b")
        f.signature("a")
        f.engine.updateSettings("a", UpdateSettingsRequest(f.meta(), false, false)).ok()
        f.engine.setSyntheticPresence("a", SetSyntheticPresenceRequest(f.meta(), SyntheticPresence.AWAY)).ok()
        assertEquals(before, f.snap("b"))
        assertEquals(result, f.engine.refreshDiscovery("b", refresh))
        val changed = f.engine.updateSettings("b", UpdateSettingsRequest(CommandMeta("visible-change", before.version), false, false)).ok()
        assertEquals(before.version + 1, changed.version)
        val restored = ChayeonEngine(f.rules, EngineClock { f.now }, store = MemoryEngineStore(f.engine.exportState()), demoEnabled = false)
        assertEquals(f.snap("b"), restored.snapshotFor("b").ok())
    }

    @Test fun deletionAlsoMarksPreviouslyClosedConversations() {
        val f = pair(); val ac = after(f)
        f.engine.endAfterConnection("b", EndAfterConnectionRequest(f.meta(), ac)).ok()
        f.engine.deleteAccount("a", DeleteAccountRequest(f.meta())).ok()
        assertEquals(AfterCloseReason.ACCOUNT_DELETED, f.snap("b").afterConnections.single().closeReason)
    }


}
