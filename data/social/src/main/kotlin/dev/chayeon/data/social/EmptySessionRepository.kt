package dev.chayeon.data.social

import dev.chayeon.core.model.AppError
import dev.chayeon.core.model.AppResult
import dev.chayeon.core.model.CommandReceipt
import dev.chayeon.core.model.DeleteAccountRequest
import dev.chayeon.core.model.EnterSessionRequest
import dev.chayeon.core.model.Session
import dev.chayeon.core.model.SessionMode
import dev.chayeon.core.model.SessionRepository
import dev.chayeon.core.model.SessionState
import dev.chayeon.core.model.SignOutRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class EmptySessionRepository(serverAvailable: Boolean = false) : SessionRepository {
    override val availableModes: Flow<Set<SessionMode>> = flowOf(
        if (serverAvailable) setOf(SessionMode.SOLO, SessionMode.SERVER_DEMO) else setOf(SessionMode.SOLO),
    )

    override val session: Flow<SessionState> = flowOf(SessionState.SignedOut())

    override suspend fun enter(request: EnterSessionRequest): AppResult<Session> =
        AppResult.Failure(AppError.NotAuthorized)

    override suspend fun signOut(request: SignOutRequest): AppResult<CommandReceipt> =
        AppResult.Failure(AppError.NotAuthorized)

    override suspend fun deleteAccount(request: DeleteAccountRequest): AppResult<CommandReceipt> =
        AppResult.Failure(AppError.NotAuthorized)
}
