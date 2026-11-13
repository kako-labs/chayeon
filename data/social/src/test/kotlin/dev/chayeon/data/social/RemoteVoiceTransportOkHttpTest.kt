package dev.chayeon.data.social

import dev.chayeon.core.model.SessionMode
import dev.chayeon.core.model.VoiceFormat
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertContentEquals

/**
 * Uses the production OkHttp engine against a real socket. The in-memory test client does not
 * reproduce engine-specific WebSocket limits, which once made every real call close right after
 * the upgrade.
 */
@RunWith(RobolectricTestRunner::class)
class RemoteVoiceTransportOkHttpTest {
    private lateinit var server: EmbeddedServer<*, *>
    private var port = 0

    @Before fun start() {
        server = embeddedServer(CIO, port = 0, host = "127.0.0.1") {
            install(WebSockets)
            routing {
                webSocket("/v1/voice/{connectionId}") {
                    for (frame in incoming) if (frame is Frame.Binary) send(Frame.Binary(true, frame.data))
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
    }

    @After fun stop() { server.stop(0, 0) }

    @Test fun productionClientKeepsTheSocketOpenAndExchangesFrames() = runBlocking {
        val session = SocialCoordinatorTest.testSession(SessionMode.SERVER_DEMO)
        val transport = RemoteVoiceTransport("http://127.0.0.1:$port", session = { session })
        try {
            val channel = transport.open("connection-1")
            val frame = ByteArray(VoiceFormat.FRAME_BYTES) { (it % 127).toByte() }
            channel.send(frame)
            val echoed = withTimeout(5_000) { channel.incoming.first() }
            assertContentEquals(frame, echoed)
            channel.close()
        } finally {
            transport.close()
        }
    }
}
