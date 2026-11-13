package dev.chayeon.data.social

import dev.chayeon.core.model.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.bearerAuth
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flow
import java.util.concurrent.TimeUnit

/** Authentication travels in the header; PCM never enters the social snapshot or disk storage. */
class RemoteVoiceTransport internal constructor(
    serverUrl: String,
    private val session: suspend () -> Session?,
    private val client: HttpClient,
) : VoiceTransport, AutoCloseable {
    constructor(serverUrl: String, session: suspend () -> Session?) : this(serverUrl, session, createVoiceHttpClient())
    private val baseUrl = serverUrl.trimEnd('/')

    override suspend fun open(connectionId: String): VoiceChannel {
        val active = requireNotNull(session())
        require(active.mode == SessionMode.SERVER_DEMO)
        val token = requireNotNull(active.accessToken)
        require(connectionId.matches(Regex("[A-Za-z0-9_-]{1,160}")))
        val endpoint = URLBuilder("$baseUrl/v1/voice/$connectionId").apply {
            require(protocol == URLProtocol.HTTP || protocol == URLProtocol.HTTPS)
            require(user == null && password == null && parameters.isEmpty() && fragment.isEmpty())
            protocol = if (protocol == URLProtocol.HTTPS) URLProtocol.WSS else URLProtocol.WS
        }.buildString()
        val socket = client.webSocketSession(endpoint) { bearerAuth(token) }
        return object : VoiceChannel {
            override val incoming = flow {
                for (frame in socket.incoming) {
                    if (frame is Frame.Binary && frame.fin && frame.data.size == VoiceFormat.FRAME_BYTES) {
                        emit(frame.data)
                    }
                }
            }.buffer(5, BufferOverflow.DROP_OLDEST)

            override suspend fun send(frame: ByteArray) {
                require(frame.size == VoiceFormat.FRAME_BYTES)
                socket.send(Frame.Binary(true, frame))
            }

            override suspend fun close() {
                socket.close(CloseReason(CloseReason.Codes.NORMAL, "call_closed"))
            }
        }
    }

    override fun close() { client.close() }
}

fun createVoiceHttpClient(): HttpClient = HttpClient(OkHttp) {
    followRedirects = false
    engine { config { pingInterval(10, TimeUnit.SECONDS) } }
    // The OkHttp engine rejects a custom max frame size and closes the session right after the upgrade.
    // Frame sizes are enforced on `incoming` and `send` instead.
    install(WebSockets)
}
