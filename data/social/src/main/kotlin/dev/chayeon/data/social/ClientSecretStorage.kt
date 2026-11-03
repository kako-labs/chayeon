package dev.chayeon.data.social

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

interface ClientSecretStorage {
    fun getOrCreate(server: String, room: String): String
}

/** Account recovery credentials survive sign-out, separately from the disposable session token. */
class PrivateClientSecretStorage(context: Context) : ClientSecretStorage {
    private val file = PrivateJsonFile(context, "client-secrets.json")

    override fun getOrCreate(server: String, room: String): String = synchronized(lock) {
        val key = MessageDigest.getInstance("SHA-256").digest("${server.length}:$server$room".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val secrets = file.read()?.let { socialJson.decodeFromString(serializer, it) }.orEmpty()
        secrets[key] ?: newClientSecret().also { secret ->
            file.write(socialJson.encodeToString(serializer, secrets + (key to secret)))
        }
    }

    private companion object {
        val lock = Any()
        val serializer = MapSerializer(String.serializer(), String.serializer())
    }
}

internal class MemoryClientSecretStorage : ClientSecretStorage {
    private val secrets = mutableMapOf<Pair<String, String>, String>()
    @Synchronized override fun getOrCreate(server: String, room: String): String =
        secrets.getOrPut(server to room, ::newClientSecret)
}

private fun newClientSecret(): String = Base64.getUrlEncoder().withoutPadding()
    .encodeToString(ByteArray(32).also(SecureRandom()::nextBytes))
