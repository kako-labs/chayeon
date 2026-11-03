package dev.chayeon.data.social

import dev.chayeon.core.engine.ChayeonEngine
import dev.chayeon.core.engine.EngineApi
import dev.chayeon.core.engine.SignatureRules
import dev.chayeon.core.model.AppError
import dev.chayeon.core.model.AppResult
import dev.chayeon.core.model.EnterSessionRequest
import dev.chayeon.core.model.Session
import dev.chayeon.core.model.SessionMode
import dev.chayeon.core.model.SyncSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class LocalBackend(
    private val engineFactory: () -> ChayeonEngine,
    private val sessionStorage: SessionStorage,
    private val clearEngineStorage: () -> Unit,
) : SocialBackend {
    constructor(context: android.content.Context, rules: SignatureRules) : this(
        engineFactory = { ChayeonEngine(rules = rules, store = PrivateEngineStore(context)) },
        sessionStorage = PrivateSessionStorage(context, "local-session.json"),
        clearEngineStorage = { PrivateEngineStore(context).clear() },
    )

    override val mode = SessionMode.SOLO
    private val mutex = Mutex()
    private var engine: ChayeonEngine? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            while (isActive) {
                delay(1_000)
                mutex.withLock {
                    try {
                        engine?.tick()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // The next snapshot reports the storage failure without exposing its details.
                    }
                }
            }
        }
    }

    private fun currentEngine(): ChayeonEngine = engine ?: engineFactory().also { engine = it }

    override suspend fun restoreSession(): AppResult<Session?> = localResult {
        mutex.withLock {
            val session = sessionStorage.read()
            if (session != null) {
                check(session.mode == mode)
                val snapshot = currentEngine().snapshotFor(session.accountId)
                check(snapshot is AppResult.Success && snapshot.value.id == session.id)
            }
            session
        }
    }

    override suspend fun enter(request: EnterSessionRequest): AppResult<Session> = withContext(Dispatchers.IO) {
        try {
            mutex.withLock {
                if (request.mode != mode) return@withLock AppResult.Failure(AppError.Unknown)
                val result = currentEngine().enterSession(request.persona.name.lowercase(), request)
                if (result is AppResult.Success) sessionStorage.write(result.value)
                result
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            AppResult.Failure(AppError.Unknown)
        }
    }

    override suspend fun execute(session: Session, route: String, body: String, canExecute: suspend () -> Boolean): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!canExecute()) throw SocialRestrictedException()
            checkSession(session)
            EngineApi(currentEngine()).execute(session.accountId, route, body)
        }
    }

    override suspend fun sync(session: Session, since: Long): AppResult<SyncSnapshot> = withContext(Dispatchers.IO) {
        try {
            mutex.withLock {
                checkSession(session)
                currentEngine().snapshotFor(session.accountId)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            AppResult.Failure(AppError.Unknown)
        }
    }

    override fun snapshots(session: Session, since: Long, onRequest: () -> Unit): Flow<AppResult<SyncSnapshot>> = flow {
        var cursor = since
        while (true) {
            onRequest()
            val result = sync(session, cursor)
            emit(result)
            if (result is AppResult.Success) {
                cursor = result.value.version
                val source = mutex.withLock { currentEngine() }
                withTimeoutOrNull(25_000) {
                    source.revision.first {
                        val visible = source.snapshotFor(session.accountId)
                        visible !is AppResult.Success || visible.value.version > cursor
                    }
                }
            } else {
                delay(1_000)
            }
        }
    }

    override suspend fun clearLocalData(): AppResult<Unit> = localResult {
        mutex.withLock {
            engine = null
            sessionStorage.clear()
            clearEngineStorage()
        }
    }

    private fun checkSession(session: Session) {
        check(session.mode == mode && sessionStorage.read()?.id == session.id) { "Session is unavailable" }
    }

    override fun close() { scope.cancel() }
}

internal suspend fun <T> localResult(action: suspend () -> T): AppResult<T> = withContext(Dispatchers.IO) {
    try {
        AppResult.Success(action())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        AppResult.Failure(AppError.Unknown)
    }
}
