package dev.chayeon.server

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.install
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

internal fun Route.voiceRoutes(registry: RoomRegistry) {
    val admission = createRouteScopedPlugin("VoiceAdmission") {
        onCall { call ->
            if (!registry.voiceAllowed(call.request.header(HttpHeaders.Authorization), call.parameters["connectionId"].orEmpty())) {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }
    route("/v1/voice/{connectionId}") {
        install(admission)
        webSocket {
            val header = call.request.header(HttpHeaders.Authorization)
            val socket = registry.joinVoice(header, call.parameters["connectionId"].orEmpty())
            if (socket == null) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "call_ended"))
                return@webSocket
            }
            val engine = registry.authorize(header)?.second
            val revision = launch { engine?.revision?.collect { registry.pruneVoice() } }
            val writer = launch {
                for (frame in socket.frames) {
                    if (socket.ended.isCompleted) break
                    outgoing.send(Frame.Binary(true, frame))
                    flush()
                }
            }
            val ending = launch {
                val reason = socket.ended.await()
                writer.cancelAndJoin()
                close(CloseReason(CloseReason.Codes.NORMAL, reason))
            }
            try {
                for (frame in incoming) {
                    if (frame !is Frame.Binary || !frame.fin || !registry.relayVoice(socket, frame.data)) {
                        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "frame_limit"))
                        break
                    }
                }
            } finally {
                registry.leaveVoice(socket)
                writer.cancelAndJoin()
                revision.cancelAndJoin()
                ending.cancelAndJoin()
            }
        }
    }
}
