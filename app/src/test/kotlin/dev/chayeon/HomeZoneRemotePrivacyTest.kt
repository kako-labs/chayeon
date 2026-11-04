package dev.chayeon

import dev.chayeon.core.engine.EngineApi
import dev.chayeon.core.engine.EngineJson
import dev.chayeon.core.model.*
import dev.chayeon.data.social.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeZoneRemotePrivacyTest {
    @Test fun fullProtectedSessionThroughRemoteBackendSendsOnlyExistingOrdinaryPresenceWithoutPrivateData() = runTest {
        val f = HomeEngineFixture()
        val session = f.join("a").copy(mode = SessionMode.SERVER_DEMO, accessToken = "test-credential")
        f.join("b")
        val api = EngineApi(f.engine)
        val captured = mutableListOf<String>()
        val routes = mutableListOf<String>()
        val storage = object : SessionStorage {
            override fun read() = session
            override fun write(session: Session) {}
            override fun clear() {}
        }
        val backend = RemoteBackend("https://example.test", storage, HttpClient(MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { request ->
            val path = request.url.encodedPath
            routes += path
            val body = (request.body as? TextContent)?.text.orEmpty()
            captured += body + request.headers.entries().joinToString()
            val response = if (path == ApiRoutes.SYNC) EngineJson.encodeToString(SyncResponse.serializer(), SyncResponse(f.engine.snapshotFor("a")))
                else api.execute("a", path, body)
            respond(response, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        })), secretStorage = object : ClientSecretStorage { override fun getOrCreate(server: String, room: String) = "test-secret-not-used" })
        val home = TestHomeZone()
        val coordinator = HomeZoneCoordinator(home, {}, backgroundScope)
        var acknowledgement = CompletableDeferred<Unit>()
        val start = async { coordinator.prepareSession(session) { request, canSend ->
            val response = backend.execute(session, ApiRoutes.SET_SYNTHETIC_PRESENCE, EngineJson.encodeToString(SetSyntheticPresenceRequest.serializer(), request), canExecute = canSend)
            EngineJson.decodeFromString(SetSyntheticPresenceResponse.serializer(), response).result.also { acknowledgement.complete(Unit) }
        } }
        runCurrent(); start.await(); acknowledgement.await(); runCurrent()
        coordinator.confirmPresence().ok()
        f.accept(f.request()).ok()
        acknowledgement = CompletableDeferred()
        home.setFromCurrentPosition(HomeZoneRadius.WIDE, LocationSource.DEVICE); runCurrent()
        acknowledgement.await(); runCurrent()
        coordinator.confirmPresence().ok()
        backend.sync(session)
        f.advance(f.policy.exitGraceMillis)
        assertEquals(ConnectionEndReason.DISTANCE, f.snapshot("b").connections.single().endReason)
        home.changeRadius(HomeZoneRadius.NEAR); runCurrent()
        home.state.value = home.state.value.copy(status = HomeZoneStatus.PERMISSION_OFF); runCurrent()
        home.state.value = home.state.value.copy(status = HomeZoneStatus.LOCATION_UNKNOWN); runCurrent()
        acknowledgement = CompletableDeferred()
        home.state.value = home.state.value.copy(status = HomeZoneStatus.OUTSIDE); runCurrent()
        acknowledgement.await(); runCurrent()
        coordinator.confirmPresence().ok()
        backend.sync(session)
        home.clear(); runCurrent()
        assertTrue(f.snapshot("b").discovery.candidates.isNotEmpty())
        assertFalse(routes.contains(ApiRoutes.UPDATE_LOCATION))
        assertEquals(3, routes.count { it == ApiRoutes.SET_SYNTHETIC_PRESENCE })
        val traffic = captured.joinToString().lowercase()
        listOf("latitude", "longitude", "radius", "home", "zone", "inside", "outside", "location_unknown", "permission_off", "medium", "wide").forEach {
            assertFalse("Private value in transport: $it", traffic.contains(it))
        }
        coordinator.endSession()
        backend.close()
    }
}
