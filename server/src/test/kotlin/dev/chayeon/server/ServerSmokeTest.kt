package dev.chayeon.server

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.Assert.assertEquals
import org.junit.Test

class ServerSmokeTest {
    @Test
    fun healthStartsAndSocialRoutesRequireAuthorization() = testApplication {
        application { chayeonModule() }
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("{\"status\":\"ready\"}", response.bodyAsText())
        assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/sync?since=0").status)
    }
}
