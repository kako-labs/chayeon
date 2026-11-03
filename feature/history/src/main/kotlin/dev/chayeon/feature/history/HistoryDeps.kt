package dev.chayeon.feature.history

import dev.chayeon.core.model.AfterRepository
import dev.chayeon.core.model.DrivingStateProvider
import dev.chayeon.core.model.EncounterRepository
import dev.chayeon.core.model.ProfileRepository
import dev.chayeon.core.model.SafetyRepository
import dev.chayeon.core.model.SessionRepository
import dev.chayeon.core.model.ServerTimeProvider

interface HistoryDeps {
    val sessionRepository: SessionRepository
    val profileRepository: ProfileRepository
    val encounterRepository: EncounterRepository
    val afterRepository: AfterRepository
    val safetyRepository: SafetyRepository
    val drivingStateProvider: DrivingStateProvider
    val serverTimeProvider: ServerTimeProvider
}
