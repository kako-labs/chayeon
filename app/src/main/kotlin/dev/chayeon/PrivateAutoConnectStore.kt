package dev.chayeon

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Device-only safety history survives process death; it contains opaque IDs and dates only. */
internal class PrivateAutoConnectStore(context: Context) : AutoConnectMemoryStore {
    private val directory = File(context.noBackupFilesDir, "automatic-connections")
    private val json = Json { ignoreUnknownKeys = true }
    private fun file(accountId: String): AtomicFile {
        check(directory.isDirectory || directory.mkdirs())
        val key = MessageDigest.getInstance("SHA-256").digest(accountId.toByteArray()).joinToString("") { "%02x".format(it) }
        return AtomicFile(File(directory, "$key.json"))
    }
    override fun read(accountId: String): AutoConnectMemory {
        val file = file(accountId)
        if (!file.baseFile.exists()) return AutoConnectMemory()
        return json.decodeFromString(file.openRead().bufferedReader().use { it.readText() })
    }
    override fun write(accountId: String, memory: AutoConnectMemory) {
        val file = file(accountId)
        val output = file.startWrite()
        try { output.write(json.encodeToString(memory).toByteArray()); file.finishWrite(output) }
        catch (failure: Exception) { file.failWrite(output); throw failure }
    }
}
