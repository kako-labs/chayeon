package dev.chayeon.server

import dev.chayeon.core.engine.EngineApi
import dev.chayeon.core.engine.EngineJson
import dev.chayeon.core.engine.SignatureRules
import dev.chayeon.core.model.*
import dev.chayeon.core.signature.DefaultSignatureEngine
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.io.ByteArrayOutputStream

private val wire = EngineJson
private class BodyLimit : RuntimeException(null, null, false, false)

fun defaultRules(): SignatureRules {
    val signature = DefaultSignatureEngine()
    return object : SignatureRules {
        override fun title(traits: List<PublicTrait>) = signature.title(traits)
        override fun chemistry(first: PublicSignature, second: PublicSignature) = signature.chemistry(first, second)
    }
}

fun Application.chayeonModule(registry: RoomRegistry = RoomRegistry(defaultRules())) {
    install(StatusPages) {
        exception<BodyLimit> { call, _ -> call.failure(AppError.Restricted, HttpStatusCode.PayloadTooLarge) }
        exception<Throwable> { call, cause ->
            if (cause is CancellationException) throw cause
            call.failure(AppError.Unknown, HttpStatusCode.InternalServerError)
        }
        status(HttpStatusCode.NotFound, HttpStatusCode.MethodNotAllowed) { call, status ->
            call.failure(AppError.NotAuthorized, status)
        }
    }
    val ticker = launch {
        while (isActive) {
            delay(registry.policy.tickIntervalMillis)
            try { registry.tick() } catch (e: CancellationException) { throw e } catch (_: Exception) { /* The next tick retries without logging private state. */ }
        }
    }
    monitor.subscribe(ApplicationStopped) { ticker.cancel() }
    routing {
        get("/health") { call.respondText("{\"status\":\"ready\"}", ContentType.Application.Json) }
        post(ApiRoutes.ENTER_SESSION) {
            val body = call.readBody(registry.policy.maximumBodyBytes)
            val request = try { wire.decodeFromString<EnterSessionRequest>(body) } catch (_: Exception) {
                call.failure(AppError.Restricted, HttpStatusCode.BadRequest); return@post
            }
            call.respondText(wire.encodeToString(EnterSessionResponse(registry.enter(request))), ContentType.Application.Json)
        }
        COMMAND_ROUTES.forEach { route ->
            post(route) {
                val header = call.request.header(HttpHeaders.Authorization)
                if (!registry.canExecute(header, route)) { call.failure(AppError.NotAuthorized, HttpStatusCode.Unauthorized); return@post }
                val body = call.readBody(registry.policy.maximumBodyBytes)
                val response = registry.execute(header, route, body)
                if (response == null) { call.failure(AppError.NotAuthorized, HttpStatusCode.Unauthorized); return@post }
                call.respondText(response, ContentType.Application.Json)
            }
        }
        get(ApiRoutes.SYNC) {
            val header = call.request.header(HttpHeaders.Authorization)
            val auth = registry.authorize(header)
            if (auth == null) { call.failure(AppError.NotAuthorized, HttpStatusCode.Unauthorized); return@get }
            val (_, engine) = auth
            val rawSince = call.request.queryParameters[ApiRoutes.SINCE_QUERY]
            val since = rawSince?.toLongOrNull() ?: if (rawSince == null) 0 else -1
            if (since < 0) { call.failure(AppError.Restricted, HttpStatusCode.BadRequest); return@get }
            if (engine.revision.value <= since) withTimeoutOrNull(registry.policy.pollTimeoutMillis) {
                engine.revision.first { it > since }
            }
            val snapshot = registry.snapshot(header)
            if (snapshot == null) { call.failure(AppError.NotAuthorized, HttpStatusCode.Unauthorized); return@get }
            call.respondText(wire.encodeToString(SyncResponse(snapshot)), ContentType.Application.Json)
        }
    }
}

private suspend fun ApplicationCall.failure(error: AppError, status: HttpStatusCode) =
    respondText(wire.encodeToString(SyncResponse(AppResult.Failure(error))), ContentType.Application.Json, status)

private suspend fun ApplicationCall.readBody(limit: Int): String {
    val length = request.header(HttpHeaders.ContentLength)?.toLongOrNull()
    if (length != null && (length < 0 || length > limit)) throw BodyLimit()
    val channel = receiveChannel()
    val output = ByteArrayOutputStream(minOf(limit, 4096))
    val buffer = ByteArray(4096)
    while (!channel.isClosedForRead) {
        val count = channel.readAvailable(buffer, 0, buffer.size)
        if (count < 0) break
        if (output.size() + count > limit) throw BodyLimit()
        output.write(buffer, 0, count)
    }
    return output.toString(Charsets.UTF_8.name())
}

internal val COMMAND_ROUTES = listOf(
    ApiRoutes.SIGN_OUT, ApiRoutes.DELETE_ACCOUNT, ApiRoutes.SAVE_PROFILE, ApiRoutes.RECORD_CONSENT,
    ApiRoutes.UPDATE_SETTINGS, ApiRoutes.PUBLISH_SIGNATURE, ApiRoutes.SET_DISCOVERY, ApiRoutes.REFRESH_DISCOVERY,
    ApiRoutes.UPDATE_LOCATION, ApiRoutes.SET_SYNTHETIC_PRESENCE, ApiRoutes.REQUEST_CONNECTION,
    ApiRoutes.RESPOND_CONNECTION, ApiRoutes.CANCEL_CONNECTION, ApiRoutes.SEND_GREETING, ApiRoutes.LEAVE_CONNECTION,
    ApiRoutes.DELETE_ENCOUNTER, ApiRoutes.DELETE_ALL_ENCOUNTERS, ApiRoutes.REQUEST_AFTER, ApiRoutes.RESPOND_AFTER,
    ApiRoutes.WITHDRAW_AFTER, ApiRoutes.SEND_AFTER_MESSAGE, ApiRoutes.END_AFTER_CONNECTION,
    ApiRoutes.BLOCK_PEER, ApiRoutes.UNBLOCK_PEER, ApiRoutes.REPORT_PEER,
)

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val host = System.getenv("HOST") ?: "0.0.0.0"
    embeddedServer(CIO, host = host, port = port) { chayeonModule() }.start(wait = true)
}
