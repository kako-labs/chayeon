package dev.chayeon.data.vehicle

import dev.chayeon.core.model.AppError
import dev.chayeon.core.model.AppResult
import dev.chayeon.core.model.ConsoleState
import dev.chayeon.core.model.DrivingState
import dev.chayeon.core.model.ReplayDriveRequest
import dev.chayeon.core.model.SetGearRequest
import dev.chayeon.core.model.StopReplayRequest
import dev.chayeon.core.model.VehicleConsole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class EmptyVehicleConsole : VehicleConsole {
    override val consoleState: Flow<ConsoleState> = flowOf(ConsoleState())

    override suspend fun setGear(request: SetGearRequest): AppResult<DrivingState> =
        AppResult.Failure(AppError.Restricted)

    override suspend fun replay(request: ReplayDriveRequest): AppResult<ConsoleState> =
        AppResult.Failure(AppError.Restricted)

    override suspend fun stopReplay(request: StopReplayRequest): AppResult<ConsoleState> =
        AppResult.Failure(AppError.Restricted)
}
