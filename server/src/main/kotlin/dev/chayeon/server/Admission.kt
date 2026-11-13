package dev.chayeon.server

import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header

/** Fixed windows with bounded, least-recently-used bucket eviction. */
internal class Admission(private val clock: () -> Long = System::currentTimeMillis, private val maximumKeys: Int = 4096) {
    private data class Bucket(val started: Long, var count: Int)
    private val buckets = java.util.LinkedHashMap<String, Bucket>(16, 0.75f, true)
    internal val bucketCount: Int get() = synchronized(this) { buckets.size }

    init { require(maximumKeys > 0) }

    @Synchronized fun allow(key: String, maximum: Int): Boolean {
        val now = clock()
        buckets.entries.removeAll { now - it.value.started >= 60_000 }
        val bucket = buckets[key] ?: run {
            if (buckets.size >= maximumKeys) buckets.remove(buckets.keys.first())
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
    if (!registry.ipAdmission.allow("$kind:$ip", ipMaximum)) return false
    val identity = registry.recognized(request.header(HttpHeaders.Authorization)) ?: return true
    return registry.actorAdmission.allow("$kind:${identity.roomCode}:${identity.actor}", if (kind == "voice") 20 else 300)
}
