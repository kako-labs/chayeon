package dev.chayeon.server

import dev.chayeon.core.engine.*
import dev.chayeon.core.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class SessionIntegrityTest {
    private val registry = RoomRegistry(defaultRules(), demoEnabled = false)
    private var sequence = 0
    private fun meta() = CommandMeta("command-${++sequence}")
    private fun enter(secret: String?, room: String = "room", persona: PersonaCode = PersonaCode.ACCOUNT_A): AppResult<Session> {
        val base = EngineJson.encodeToJsonElement(EnterSessionRequest(meta(), SessionMode.SERVER_DEMO, persona, room)).jsonObject
        val body = JsonObject(base + ("clientSecret" to (secret?.let(::JsonPrimitive) ?: JsonNull)))
        return registry.enter(EngineJson.decodeFromJsonElement(body))
    }
    private fun <T> AppResult<T>.ok() = (this as AppResult.Success<T>).value
    private fun header(s: Session) = "Bearer ${s.accessToken}"
    private val secret = "a".repeat(43)

    @Test fun takeoverFailsWithoutRevokingVictimToken() {
        val victim = enter(secret).ok()
        assertEquals(AppResult.Failure(AppError.NotAuthorized), enter("b".repeat(43)))
        assertEquals(AppResult.Failure(AppError.NotAuthorized), enter(null))
        assertNotNull(registry.authorize(header(victim)))
    }

    @Test fun oldSignOutReplayCannotRevokeNewSession() {
        val first = enter(secret).ok()
        val body = EngineJson.encodeToString(SignOutRequest(meta()))
        val original = registry.execute(header(first), ApiRoutes.SIGN_OUT, body)
        val next = enter(secret).ok()
        assertEquals(first.accountId, next.accountId)
        assertEquals(original, registry.execute(header(next), ApiRoutes.SIGN_OUT, body))
        assertNotNull(registry.authorize(header(next)))
    }

    @Test fun stolenRoomCodeCannotForgeAcceptanceOrReadAfterMessages() {
        val a = enter(secret).ok()
        val b = enter("b".repeat(43), persona = PersonaCode.ACCOUNT_B).ok()
        val engine = registry.authorize(header(a))!!.second
        for (session in listOf(a, b)) {
            engine.saveProfile(session.accountId, SaveProfileRequest(meta(), ProfileDraft("가상상대", AvatarId.DAWN, emptySet()))).ok()
            ConsentScope.entries.forEach { engine.recordConsent(session.accountId,
                RecordConsentRequest(meta(), it, ConsentAction.GRANT, session.policy.consentDocumentVersion)).ok() }
            engine.setDiscovery(session.accountId, SetDiscoveryRequest(meta(), DiscoveryMode.ON)).ok()
        }
        val request = engine.requestConnection(a.accountId, RequestConnectionRequest(meta(),
            engine.snapshotFor(b.accountId).ok().profile!!.id)).ok()
        val takeover = enter("attacker".repeat(6), persona = PersonaCode.ACCOUNT_B)
        assertEquals(AppResult.Failure(AppError.NotAuthorized), takeover)
        val body = EngineJson.encodeToString(RespondConnectionRequest(meta(), request.id, RequestDecision.ACCEPT))
        assertNull(registry.execute("Bearer attacker", ApiRoutes.RESPOND_CONNECTION, body))
        assertEquals(ConnectionRequestStatus.PENDING, registry.snapshot(header(b))!!.ok().connectionRequests.single().status)
        val c = engine.respondConnection(b.accountId, RespondConnectionRequest(meta(), request.id, RequestDecision.ACCEPT)).ok().connectionId!!
        engine.leaveConnection(a.accountId, LeaveConnectionRequest(meta(), c)).ok()
        val ar = engine.requestAfter(a.accountId, RequestAfterRequest(meta(), engine.snapshotFor(a.accountId).ok().encounters.single().id)).ok()
        val ac = engine.respondAfter(b.accountId, RespondAfterRequest(meta(), ar.id, RequestDecision.ACCEPT)).ok().afterConnectionId!!
        engine.sendAfterMessage(a.accountId, SendAfterMessageRequest(meta(), ac, "private-message", AfterMessageContent.Text("다시 만나서 반가워요"))).ok()
        assertEquals(AppResult.Failure(AppError.NotAuthorized), enter(null, persona = PersonaCode.ACCOUNT_B))
        assertNull(registry.snapshot("Bearer attacker"))
        assertEquals(1, registry.snapshot(header(b))!!.ok().afterMessages.size)
    }

    @Test fun matchingDeviceRecoversAfterTokenLossAndSignOutWithRoomIsolation() {
        val first = enter(secret).ok()
        val recovered = enter(secret).ok()
        assertEquals(first.accountId, recovered.accountId)
        assertNotEquals(first.accessToken, recovered.accessToken)
        assertNull(registry.authorize(header(first)))
        registry.execute(header(recovered), ApiRoutes.SIGN_OUT, EngineJson.encodeToString(SignOutRequest(meta())))
        val returned = enter(secret).ok()
        assertEquals(first.accountId, returned.accountId)
        val other = enter("b".repeat(43), room = "other").ok()
        assertNotEquals(first.accountId, other.accountId)
        assertEquals(AppResult.Failure(AppError.NotAuthorized), enter("b".repeat(43)))
        assertNotNull(registry.authorize(header(returned)))
    }

    @Test fun newClaimsRequireLongSecretsAndRoomCodesAndNeverEchoSecrets() {
        for (value in listOf(null, "", "a".repeat(31))) {
            assertEquals(AppResult.Failure(AppError.NotAuthorized), enter(value))
        }
        assertEquals(AppResult.Failure(AppError.Restricted), enter(secret, room = "abc"))
        val request = EnterSessionRequest(meta(), SessionMode.SERVER_DEMO, PersonaCode.ACCOUNT_A, "room", secret)
        assertFalse(request.toString().contains(secret))
        assertFalse(EngineJson.encodeToString(EnterSessionResponse(registry.enter(request))).contains(secret))
        assertFalse(EngineJson.encodeToString(registry.authorize(header(enter(secret).ok()))!!.second.exportState()).contains(secret))
    }
}
