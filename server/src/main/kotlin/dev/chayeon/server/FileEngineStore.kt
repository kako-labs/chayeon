package dev.chayeon.server

import dev.chayeon.core.engine.EngineJson
import dev.chayeon.core.engine.EngineState
import dev.chayeon.core.engine.EngineStore
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
internal data class StoredAuthorization(val actor: String, val sessionId: String)

@Serializable
private data class StoredRoom(val state: EngineState? = null, val tokens: Map<String, StoredAuthorization> = emptyMap())

/** Atomic replacement keeps the previous complete snapshot if encoding or writing fails. */
internal class FileEngineStore(private val path: Path) : EngineStore {
    private var room = try {
        if (Files.exists(path)) {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"))
            EngineJson.decodeFromString<StoredRoom>(Files.readString(path))
        } else StoredRoom()
    } catch (_: Exception) {
        throw IllegalStateException("Unable to load stored room")
    }
    val tokens: Map<String, StoredAuthorization> get() = room.tokens

    @Synchronized override fun load() = room.state
    @Synchronized override fun save(state: EngineState) = replace(room.copy(state = state))
    @Synchronized fun saveTokens(tokens: Map<String, StoredAuthorization>) = replace(room.copy(tokens = tokens))

    private fun replace(next: StoredRoom) {
        try { write(next) }
        catch (_: Exception) { throw IllegalStateException("Unable to save stored room") }
    }

    private fun write(next: StoredRoom) {
        val bytes = EngineJson.encodeToString(next).toByteArray(Charsets.UTF_8)
        Files.createDirectories(path.parent)
        val temporary = Files.createTempFile(path.parent, ".snapshot-", ".tmp",
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
        try {
            FileChannel.open(temporary, StandardOpenOption.WRITE).use { channel ->
                val buffer = java.nio.ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            room = next
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}
