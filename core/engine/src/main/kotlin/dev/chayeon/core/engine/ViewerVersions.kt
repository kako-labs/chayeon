package dev.chayeon.core.engine

import java.security.MessageDigest
import kotlinx.serialization.json.*

/** Internal room revisions must not expose changes outside a viewer's authorized projection. */
internal fun withVersion(value: JsonElement, version: Long): JsonElement = when (value) {
    is JsonObject -> JsonObject(value.mapValues { (key, child) ->
        if (key == "version") JsonPrimitive(version) else withVersion(child, version)
    })
    is JsonArray -> JsonArray(value.map { withVersion(it, version) })
    else -> value
}

internal fun Room.updateViews() {
    state.accounts.values.filter { it.active && !it.deleted }.forEach { account ->
        val projection = withVersion(EngineJson.encodeToJsonElement(snapshot(account.id).copy(serverTimeEpochMillis = 0)), 0)
        val digest = MessageDigest.getInstance("SHA-256").digest(projection.toString().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        if (state.viewDigests[account.id] != digest) {
            state = state.copy(viewDigests = state.viewDigests + (account.id to digest),
                viewRevisions = state.viewRevisions + (account.id to ((state.viewRevisions[account.id] ?: 0) + 1)))
        }
    }
}
