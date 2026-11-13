package dev.chayeon.data.social

import dev.chayeon.core.model.AppError
import dev.chayeon.core.model.AppResult
import dev.chayeon.core.model.CommandReceipt
import dev.chayeon.core.model.DeleteAllEncountersRequest
import dev.chayeon.core.model.DeleteEncounterRequest
import dev.chayeon.core.model.Encounter
import dev.chayeon.core.model.EncounterRepository
import dev.chayeon.core.model.LoadState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class EmptyEncounterRepository : EncounterRepository {
    override val encounters: Flow<LoadState<List<Encounter>>> = flowOf(LoadState.Ready(emptyList(), 0))

    override fun observeEncounter(encounterId: String): Flow<LoadState<Encounter?>> = flowOf(LoadState.Ready(null, 0))

    override suspend fun deleteEncounter(request: DeleteEncounterRequest): AppResult<CommandReceipt> =
        AppResult.Failure(AppError.NotAuthorized)

    override suspend fun deleteAllEncounters(request: DeleteAllEncountersRequest): AppResult<CommandReceipt> =
        AppResult.Failure(AppError.NotAuthorized)
}
