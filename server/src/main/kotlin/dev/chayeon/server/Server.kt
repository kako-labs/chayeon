package dev.chayeon.server

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(val status: String)

fun Application.chayeonModule() {
    install(ContentNegotiation) { json() }
    routing {
        get("/health") { call.respond(HealthResponse("foundation")) }
    }
}

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(CIO, host = "0.0.0.0", port = port, module = Application::chayeonModule)
        .start(wait = true)
}
