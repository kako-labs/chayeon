package dev.chayeon

import dev.chayeon.core.model.*
import kotlinx.coroutines.flow.first

/** The device closes new relationships before any asynchronous AWAY acknowledgement. */
class HomeProtectedConnectionRepository(
    private val delegate: ConnectionRepository,
    private val home: HomeZoneRepository,
) : ConnectionRepository by delegate {
    override suspend fun requestConnection(request: RequestConnectionRequest): AppResult<ConnectionRequest> =
        if (home.state.first().suppressed) AppResult.Failure(AppError.PeerUnavailable)
        else delegate.requestConnection(request)

    override suspend fun respond(request: RespondConnectionRequest): AppResult<ConnectionRequest> =
        if (request.decision == RequestDecision.ACCEPT && home.state.first().suppressed) AppResult.Failure(AppError.PeerUnavailable)
        else delegate.respond(request)
}
