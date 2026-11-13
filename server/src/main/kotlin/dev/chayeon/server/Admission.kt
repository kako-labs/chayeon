package dev.chayeon.server

import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import java.security.MessageDigest

/** Fixed windows and bounded buckets fail closed when the live key budget is exhausted. */
internal class Admission(private val clock: () -> Long = System::currentTimeMillis, private val maximumKeys: Int = 4096) {
    private data class Bucket(val started: Long, var count: Int)
    private val buckets = mutableMapOf<String, Bucket>()

    @Synchronized fun allow(key: String, maximum: Int): Boolean {
        val now = clock()
        buckets.entries.removeAll { now - it.value.started >= 60_000 }
        val bucket = buckets[key] ?: run {
            if (buckets.size >= maximumKeys) return false
            Bucket(now, 0).also { buckets[key] = it }
        }
        if (bucket.count >= maximum) return false
        bucket.count++
        return true
    }
}

internal fun ApplicationCall.admitted(registry: RoomRegistry, kind: String): Boolean {
    val ip = if (registry.policy.trustProxy) request.header(registry.policy.clientIpHeader) ?: request.local.remoteAddress else request.local.remoteAddress
    val ipMaximum = when (kind) { "entry" -> 30; "voice" -> 60; else -> 600 }
    if (!registry.admission.allow("$kind:ip:$ip", ipMaximum)) return false
    val token = request.header(HttpHeaders.Authorization) ?: return true
    val hash = MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }
    return registry.admission.allow("$kind:token:$hash", if (kind == "voice") 20 else 300)
}
