package dev.chayeon.server

import dev.chayeon.core.engine.EngineJson
import dev.chayeon.core.model.*
import io.ktor.client.request.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import io.ktor.server.application.*
import io.ktor.server.application.hooks.ResponseBodyReadyForSend
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class AdmissionServerTest {
    @Test fun entryIpBudgetIncludesInvalidBodiesAndReturnsExistingErrorShape() = testApplication {
        application { chayeonModule(RoomRegistry(defaultRules(), demoEnabled = false)) }
        repeat(30) { assertEquals(HttpStatusCode.BadRequest, client.post(ApiRoutes.ENTER_SESSION) { setBody("invalid") }.status) }
        val denied = client.post(ApiRoutes.ENTER_SESSION) { setBody("invalid") }
        assertEquals(HttpStatusCode.TooManyRequests, denied.status)
        assertEquals(AppResult.Failure(AppError.Restricted), EngineJson.decodeFromString<SyncResponse>(denied.bodyAsText()).result)
        assertEquals(HttpStatusCode.OK, client.get("/health").status)
        assertEquals(HttpStatusCode.OK, client.get("/ready").status)
    }

    @Test fun commandAndSyncShareTokenBudget() = testApplication {
        val registry = RoomRegistry(defaultRules(), demoEnabled = false)
        val session = (registry.enter(EnterSessionRequest(CommandMeta(UUID.randomUUID().toString()), SessionMode.SERVER_DEMO,
            clientSecret = "a".repeat(43))) as AppResult.Success).value
        application { chayeonModule(registry) }
        repeat(299) { assertEquals(HttpStatusCode.OK, client.get(ApiRoutes.SYNC) { bearerAuth(session.accessToken!!) }.status) }
        assertEquals(HttpStatusCode.OK, client.post(ApiRoutes.SAVE_PROFILE) {
            bearerAuth(session.accessToken!!)
            setBody(EngineJson.encodeToString(SaveProfileRequest(CommandMeta(UUID.randomUUID().toString()), ProfileDraft("테스트", AvatarId.DAWN, emptySet()))))
        }.status)
        assertEquals(HttpStatusCode.TooManyRequests, client.get(ApiRoutes.SYNC) { bearerAuth(session.accessToken!!) }.status)
    }

    @Test fun unauthenticatedCommandsHaveIpBudgetAndIgnoreSpoofedProxyHeaders() = testApplication {
        application { chayeonModule(RoomRegistry(defaultRules(), demoEnabled = false)) }
        repeat(600) { index ->
            assertEquals(HttpStatusCode.Unauthorized, client.post(ApiRoutes.SAVE_PROFILE) {
                header("X-Chayeon-Client-IP", "spoof-$index")
            }.status)
        }
        assertEquals(HttpStatusCode.TooManyRequests, client.get(ApiRoutes.SYNC).status)
    }

    @Test fun trustedTunnelHeaderSeparatesClientsBehindOneLocalAddress() = testApplication {
        val policy = ServerPolicy(trustProxy = true, clientIpHeader = "CF-Connecting-IP")
        application { chayeonModule(RoomRegistry(defaultRules(), policy, demoEnabled = false)) }
        repeat(600) {
            assertEquals(HttpStatusCode.Unauthorized, client.post(ApiRoutes.SAVE_PROFILE) { header("CF-Connecting-IP", "198.51.100.1") }.status)
        }
        assertEquals(HttpStatusCode.TooManyRequests, client.post(ApiRoutes.SAVE_PROFILE) { header("CF-Connecting-IP", "198.51.100.1") }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.post(ApiRoutes.SAVE_PROFILE) { header("CF-Connecting-IP", "198.51.100.2") }.status)
        assertEquals("CF-Connecting-IP", environmentPolicy(mapOf("CHAYEON_CLIENT_IP_HEADER" to "CF-Connecting-IP")).clientIpHeader)
    }

    @Test fun voiceAttemptsHaveIndependentIpAndTokenLimits() = testApplication {
        val statuses = Channel<HttpStatusCode>(Channel.UNLIMITED)
        val observer = createApplicationPlugin("ObserveVoiceAdmission") {
            on(ResponseBodyReadyForSend) { call, content ->
                statuses.trySend(content.status ?: call.response.status() ?: HttpStatusCode.InternalServerError)
            }
        }
        application {
            install(observer)
            chayeonModule(RoomRegistry(defaultRules(), demoEnabled = false))
        }
        val ws = createClient { install(WebSockets) }
        suspend fun attempt(expected: HttpStatusCode, token: String? = null) = coroutineScope {
            val handshake = launch {
                try { ws.webSocketSession("/v1/voice/missing") { if (token != null) bearerAuth(token) } }
                catch (_: Exception) { /* The test engine wraps refused upgrades in a generic failure. */ }
            }
            try { assertEquals(expected, withTimeout(2_000) { statuses.receive() }) }
            finally { handshake.cancelAndJoin() }
        }
        repeat(20) { attempt(HttpStatusCode.Forbidden, "invalid") }
        attempt(HttpStatusCode.TooManyRequests, "invalid")
        repeat(39) { attempt(HttpStatusCode.Forbidden) }
        attempt(HttpStatusCode.TooManyRequests)
        ws.close()
    }

    @Test fun environmentConfigurationHasBoundedExplicitDefaults() {
        val defaults = environmentPolicy(emptyMap())
        assertEquals(50, defaults.roomCapacity)
        assertNull(defaults.dataDirectory)
        assertFalse(defaults.trustProxy)
        val custom = environmentPolicy(mapOf("CHAYEON_ROOM_CAPACITY" to "12", "CHAYEON_MAX_ROOMS" to "3", "CHAYEON_IDLE_ROOM_MILLIS" to "6000"))
        assertEquals(12, custom.roomCapacity)
        assertEquals(3, custom.maximumRooms)
        assertEquals(6000L, custom.idleRoomMillis)
        assertThrows(IllegalArgumentException::class.java) { environmentPolicy(mapOf("CHAYEON_ROOM_CAPACITY" to "0")) }
    }
}
