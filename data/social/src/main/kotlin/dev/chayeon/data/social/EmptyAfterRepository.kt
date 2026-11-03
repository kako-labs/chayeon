package dev.chayeon.data.social

import dev.chayeon.core.model.AfterConnection
import dev.chayeon.core.model.AfterMessage
import dev.chayeon.core.model.AfterRepository
import dev.chayeon.core.model.AfterRequest
import dev.chayeon.core.model.AppError
import dev.chayeon.core.model.AppResult
import dev.chayeon.core.model.EndAfterConnectionRequest
import dev.chayeon.core.model.LoadState
import dev.chayeon.core.model.RequestAfterRequest
import dev.chayeon.core.model.RespondAfterRequest
import dev.chayeon.core.model.SendAfterMessageRequest
import dev.chayeon.core.model.WithdrawAfterRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class EmptyAfterRepository : AfterRepository {
    override val requests: Flow<LoadState<List<AfterRequest>>> = flowOf(LoadState.Ready(emptyList(), 0))

    override val connections: Flow<LoadState<List<AfterConnection>>> = flowOf(LoadState.Ready(emptyList(), 0))

    override fun observeMessages(afterConnectionId: String): Flow<LoadState<List<AfterMessage>>> = flowOf(LoadState.Ready(emptyList(), 0))

    override suspend fun requestAfter(request: RequestAfterRequest): AppResult<AfterRequest> =
        AppResult.Failure(AppError.NotAuthorized)

    override suspend fun respond(request: RespondAfterRequest): AppResult<AfterRequest> =
        AppResult.Failure(AppError.NotAuthorized)

    override suspend fun withdraw(request: WithdrawAfterRequest): AppResult<AfterRequest> =
        AppResult.Failure(AppError.NotAuthorized)

    override suspend fun sendMessage(request: SendAfterMessageRequest): AppResult<AfterMessage> =
        AppResult.Failure(AppError.NotAuthorized)

    override suspend fun endRelationship(request: EndAfterConnectionRequest): AppResult<AfterConnection> =
        AppResult.Failure(AppError.NotAuthorized)
}
