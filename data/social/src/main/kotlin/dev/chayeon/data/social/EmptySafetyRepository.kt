package dev.chayeon.data.social

import dev.chayeon.core.model.AppError
import dev.chayeon.core.model.AppResult
import dev.chayeon.core.model.Block
import dev.chayeon.core.model.BlockPeerRequest
import dev.chayeon.core.model.CommandReceipt
import dev.chayeon.core.model.LoadState
import dev.chayeon.core.model.Report
import dev.chayeon.core.model.ReportPeerRequest
import dev.chayeon.core.model.SafetyRepository
import dev.chayeon.core.model.UnblockPeerRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class EmptySafetyRepository : SafetyRepository {
    override val blocks: Flow<LoadState<List<Block>>> = flowOf(LoadState.Ready(emptyList(), 0))

    override suspend fun block(request: BlockPeerRequest): AppResult<Block> =
        AppResult.Failure(AppError.NotAuthorized)

    override suspend fun unblock(request: UnblockPeerRequest): AppResult<CommandReceipt> =
        AppResult.Failure(AppError.NotAuthorized)

    override suspend fun report(request: ReportPeerRequest): AppResult<Report> =
        AppResult.Failure(AppError.NotAuthorized)
}
