package dev.chayeon.data.social

import android.content.Context
import dev.chayeon.core.model.AppResult
import dev.chayeon.core.model.CommandReceipt
import kotlinx.serialization.Serializable

/** No credentials are needed to finish local cleanup after the authenticated command has run. */
@Serializable
internal data class CleanupRecord(
    val route: String,
    val body: String,
    val result: AppResult<CommandReceipt>? = null,
)

internal interface CleanupStorage {
    fun read(): CleanupRecord?
    fun write(record: CleanupRecord)
    fun clear()
}

internal class MemoryCleanupStorage : CleanupStorage {
    private var record: CleanupRecord? = null
    override fun read(): CleanupRecord? = record
    override fun write(record: CleanupRecord) { this.record = record }
    override fun clear() { record = null }
}

internal class PrivateCleanupStorage(context: Context) : CleanupStorage {
    private val file = PrivateJsonFile(context, "cleanup.json")
    override fun read(): CleanupRecord? = file.read()?.let { socialJson.decodeFromString(CleanupRecord.serializer(), it) }
    override fun write(record: CleanupRecord) = file.write(socialJson.encodeToString(CleanupRecord.serializer(), record))
    override fun clear() = file.clear()
}
