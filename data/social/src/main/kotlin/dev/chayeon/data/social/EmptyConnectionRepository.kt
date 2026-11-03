package dev.chayeon.data.social

import dev.chayeon.core.model.AppError
import dev.chayeon.core.model.AppResult
import dev.chayeon.core.model.CancelConnectionRequest
import dev.chayeon.core.model.Connection
import dev.chayeon.core.model.ConnectionGreeting
import dev.chayeon.core.model.ConnectionRepository
import dev.chayeon.core.model.ConnectionRequest
import dev.chayeon.core.model.LeaveConnectionRequest
import dev.chayeon.core.model.LoadState
import dev.chayeon.core.model.RequestConnectionRequest
import dev.chayeon.core.model.RespondConnectionRequest
import dev.chayeon.core.model.SendGreetingRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class EmptyConnectionRepository : ConnectionRepository {
    override val requests: Flow<LoadState<List<ConnectionRequest>>> = flowOf(LoadState.Ready(emptyList(), 0))

    override val connection: Flow<LoadState<Connection?>> = flowOf(LoadState.Ready(null, 0))

    override fun observeGreetings(connectionId: String): Flow<LoadState<List<ConnectionGreeting>>> = flowOf(LoadState.Ready(emptyList(), 0))

    override suspend fun requestConnection(request: RequestConnectionRequest): AppResult<ConnectionRequest> =
        AppResult.Failure(AppError.NotAuthorized)

    override suspend fun respond(request: RespondConnectionRequest): AppResult<ConnectionRequest> =
        AppResult.Failure(AppError.NotAuthorized)

    override suspend fun cancel(request: CancelConnectionRequest): AppResult<ConnectionRequest> =
        AppResult.Failure(AppError.NotAuthorized)

    override suspend fun sendGreeting(request: SendGreetingRequest): AppResult<ConnectionGreeting> =
        AppResult.Failure(AppError.NotAuthorized)

    override suspend fun leave(request: LeaveConnectionRequest): AppResult<Connection> =
        AppResult.Failure(AppError.NotAuthorized)
}
