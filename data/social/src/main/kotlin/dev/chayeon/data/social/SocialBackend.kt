package dev.chayeon.data.social

import dev.chayeon.core.model.AppResult
import dev.chayeon.core.model.EnterSessionRequest
import dev.chayeon.core.model.Session
import dev.chayeon.core.model.SessionMode
import dev.chayeon.core.model.SyncSnapshot
import kotlinx.coroutines.flow.Flow

/** Transport implementations never own relationship rules or projected repository state. */
interface SocialBackend : AutoCloseable {
    val mode: SessionMode
    suspend fun restoreSession(): AppResult<Session?>
    suspend fun enter(request: EnterSessionRequest): AppResult<Session>
    suspend fun execute(session: Session, route: String, body: String, canExecute: suspend () -> Boolean = { true }): String
    suspend fun sync(session: Session, since: Long = 0): AppResult<SyncSnapshot>
    /** Calls onRequest immediately before each sync and emits its result before starting another. */
    fun snapshots(session: Session, since: Long, onRequest: () -> Unit = {}): Flow<AppResult<SyncSnapshot>>
    suspend fun clearLocalData(): AppResult<Unit>
}

internal class SocialRestrictedException : Exception()
