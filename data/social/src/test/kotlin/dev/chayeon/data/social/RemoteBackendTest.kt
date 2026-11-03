package dev.chayeon.data.social

import dev.chayeon.core.model.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.content.TextContent
import java.io.IOException
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RemoteBackendTest {
    @Test fun `network command retry sends the exact same id and body with bearer authentication`() = runTest {
        val seenBodies = mutableListOf<String>()
        val seenTokens = mutableListOf<String?>()
        val engine = MockEngine { request ->
            seenBodies += (request.body as TextContent).text
            seenTokens += request.headers[HttpHeaders.Authorization]
            if (seenBodies.size == 1) throw IOException("Interrupted")
            respond(SocialCoordinatorTest.receipt(), HttpStatusCode.OK, jsonHeaders)
        }
        val backend = RemoteBackend("https://example.test", MemorySessionStorage(), HttpClient(engine), retryDelayMillis = 1)
        val session = SocialCoordinatorTest.testSession(SessionMode.SERVER_DEMO)
        val body = socialJson.encodeToString(SignOutRequest.serializer(), SignOutRequest(CommandMeta("stable-command")))
        backend.execute(session, ApiRoutes.SIGN_OUT, body)
        assertEquals(listOf(body, body), seenBodies)
        assertEquals(listOf("Bearer test-credential", "Bearer test-credential"), seenTokens)
        backend.close()
    }

    @Test fun `retry rechecks driving permission before sending again`() = runTest {
        var allowed = true
        var attempts = 0
        val backend = RemoteBackend("https://example.test", MemorySessionStorage(), HttpClient(MockEngine {
            attempts++
            allowed = false
            throw IOException("Interrupted")
        }), retryDelayMillis = 1)
        val result = runCatching {
            backend.execute(SocialCoordinatorTest.testSession(SessionMode.SERVER_DEMO), ApiRoutes.SEND_GREETING, "{}") { allowed }
        }
        assertTrue(result.exceptionOrNull() is SocialRestrictedException)
        assertEquals(1, attempts)
        backend.close()
    }

    @Test fun `long poll reconnects after network failure and advances cursor only forward`() = runTest {
        var count = 0
        val cursors = mutableListOf<String?>()
        val engine = MockEngine { request ->
            cursors += request.url.parameters[ApiRoutes.SINCE_QUERY]
            count++
            if (count == 1) throw IOException("Interrupted")
            val version = if (count == 2) 7L else 6L
            respond(socialJson.encodeToString(SyncResponse.serializer(), SyncResponse(AppResult.Success(
                SyncSnapshot("session", version, 1_000 + count.toLong())))), HttpStatusCode.OK, jsonHeaders)
        }
        val backend = RemoteBackend("https://example.test", MemorySessionStorage(), HttpClient(engine), retryDelayMillis = 1)
        val results = backend.snapshots(SocialCoordinatorTest.testSession(SessionMode.SERVER_DEMO), 5).take(3).toList()
        assertEquals(AppResult.Failure(AppError.Network), results.first())
        assertEquals(listOf("5", "5", "7"), cursors)
        backend.close()
    }

    @Test fun `authorization failure terminates reconnect instead of retrying invalid credentials`() = runTest {
        var count = 0
        val backend = RemoteBackend("https://example.test", MemorySessionStorage(), HttpClient(MockEngine {
            count++
            respond("{}", HttpStatusCode.Unauthorized, jsonHeaders)
        }), retryDelayMillis = 1)
        assertEquals(listOf(AppResult.Failure(AppError.NotAuthorized)),
            backend.snapshots(SocialCoordinatorTest.testSession(SessionMode.SERVER_DEMO), 0).toList())
        assertEquals(1, count)
        backend.close()
    }

    @Test fun `successful enter persists token and clear removes it`() = runTest {
        val storage = MemorySessionStorage()
        val session = SocialCoordinatorTest.testSession(SessionMode.SERVER_DEMO)
        val backend = RemoteBackend("https://example.test", storage, HttpClient(MockEngine {
            assertNull(it.headers[HttpHeaders.Authorization])
            respond(socialJson.encodeToString(EnterSessionResponse.serializer(), EnterSessionResponse(AppResult.Success(session))),
                HttpStatusCode.OK, jsonHeaders)
        }))
        val result = backend.enter(EnterSessionRequest(CommandMeta("enter"), SessionMode.SERVER_DEMO, PersonaCode.ACCOUNT_A))
        assertEquals(AppResult.Success(session), result)
        assertEquals(session, storage.value)
        assertEquals(AppResult.Success(session), backend.restoreSession())
        backend.clearLocalData()
        assertNull(storage.value)
        backend.close()
    }

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
}

internal class MemorySessionStorage : SessionStorage {
    var value: Session? = null
    override fun read(): Session? = value
    override fun write(session: Session) { value = session }
    override fun clear() { value = null }
}
