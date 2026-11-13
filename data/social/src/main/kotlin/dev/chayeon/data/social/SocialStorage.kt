package dev.chayeon.data.social

import android.content.Context
import android.util.AtomicFile
import dev.chayeon.core.engine.EngineState
import dev.chayeon.core.engine.EngineStore
import dev.chayeon.core.model.Session
import java.io.File
import java.io.FileNotFoundException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import dev.chayeon.core.engine.EngineJson

internal val socialJson = EngineJson
private val storedJson = Json(EngineJson) { ignoreUnknownKeys = true }

interface SessionStorage {
    fun read(): Session?
    fun write(session: Session)
    fun clear()
}

/** Files stay in app-private, backup-excluded storage, including the bearer token. */
@Serializable
private data class StoredSession(val scope: String?, val session: Session)

class PrivateSessionStorage(context: Context, name: String, private val scope: String? = null) : SessionStorage {
    private val file = PrivateJsonFile(context, name)
    override fun read(): Session? = file.read()?.let { raw ->
        val stored = storedJson.decodeFromString(StoredSession.serializer(), raw)
        if (stored.scope == scope) stored.session else { file.clear(); null }
    }
    override fun write(session: Session) = file.write(socialJson.encodeToString(StoredSession.serializer(), StoredSession(scope, session)))
    override fun clear() = file.clear()
}

class PrivateEngineStore(context: Context) : EngineStore {
    private val file = PrivateJsonFile(context, "engine.json")
    override fun load(): EngineState? = file.read()?.let { storedJson.decodeFromString(EngineState.serializer(), it) }
    override fun save(state: EngineState) = file.write(socialJson.encodeToString(EngineState.serializer(), state))
    fun clear() = file.clear()
}

internal class PrivateJsonFile(context: Context, name: String) {
    private val directory = File(context.applicationContext.noBackupFilesDir, "chayeon-social")
    private val target = File(directory, name)
    private val file = AtomicFile(target)

    init {
        require(name.matches(Regex("[a-z-]+\\.json")))
        check(directory.isDirectory || directory.mkdirs()) { "Private storage is unavailable" }
    }

    @Synchronized
    fun read(): String? = try {
        file.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
    } catch (_: FileNotFoundException) {
        null
    }

    @Synchronized
    fun write(value: String) {
        val stream = file.startWrite()
        try {
            stream.write(value.toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (failure: Exception) {
            file.failWrite(stream)
            throw failure
        }
    }

    @Synchronized
    fun clear() {
        file.delete()
        check(!target.exists() && !File(target.path + ".bak").exists() && !File(target.path + ".new").exists()) {
            "Private storage could not be cleared"
        }
    }
}
