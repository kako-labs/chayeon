package dev.chayeon.feature.history

import dev.chayeon.core.model.AfterRepository
import dev.chayeon.core.model.ConnectionRepository
import dev.chayeon.core.model.DiscoveryRepository
import dev.chayeon.core.model.DrivingStateProvider
import dev.chayeon.core.model.EncounterRepository
import dev.chayeon.core.model.ProfileRepository
import dev.chayeon.core.model.SafetyRepository
import dev.chayeon.core.model.SessionRepository
import dev.chayeon.core.model.ServerTimeProvider
import dev.chayeon.core.model.SignatureRepository

interface HistoryDeps {
    val homeZoneRepository: dev.chayeon.core.model.HomeZoneRepository
    val syntheticHomeEnabled: Boolean get() = false
    suspend fun prepareHomeLocation(): Boolean = false
    val sessionRepository: SessionRepository
    val discoveryRepository: DiscoveryRepository
    val connectionRepository: ConnectionRepository
    val signatureRepository: SignatureRepository
    val profileRepository: ProfileRepository
    val encounterRepository: EncounterRepository
    val afterRepository: AfterRepository
    val safetyRepository: SafetyRepository
    val drivingStateProvider: DrivingStateProvider
    val serverTimeProvider: ServerTimeProvider
}
