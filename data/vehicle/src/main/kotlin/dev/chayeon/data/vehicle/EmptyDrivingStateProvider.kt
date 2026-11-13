package dev.chayeon.data.vehicle

import dev.chayeon.core.model.DrivingState
import dev.chayeon.core.model.DrivingStateProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class EmptyDrivingStateProvider : DrivingStateProvider {
    override val drivingState: Flow<DrivingState> = flowOf(DrivingState())
}
