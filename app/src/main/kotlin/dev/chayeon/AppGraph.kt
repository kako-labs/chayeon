package dev.chayeon

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
import dev.chayeon.core.model.VehicleConsole
import dev.chayeon.core.model.HomeZoneRepository
import dev.chayeon.feature.history.HistoryDeps
import dev.chayeon.feature.meet.MeetDeps
import dev.chayeon.feature.signature.SignatureDeps

class AppGraph(
    val sessionRepository: SessionRepository,
    val profileRepository: ProfileRepository,
    val discoveryRepository: DiscoveryRepository,
    val connectionRepository: ConnectionRepository,
    val encounterRepository: EncounterRepository,
    val afterRepository: AfterRepository,
    val safetyRepository: SafetyRepository,
    val drivingStateProvider: DrivingStateProvider,
    val signatureRepository: SignatureRepository,
    val vehicleConsole: VehicleConsole,
    val serverTimeProvider: ServerTimeProvider,
    val serverUrl: String,
    val homeZoneRepository: HomeZoneRepository,
    val homeZoneCoordinator: HomeZoneCoordinator,
    val voiceCallRepository: dev.chayeon.core.model.VoiceCallRepository = dev.chayeon.core.model.NoVoiceCall,
) {
    val serverModeAvailable: Boolean get() = serverUrl.isNotBlank()

    val meetDeps: MeetDeps = object : MeetDeps {
        override val voiceCallRepository = this@AppGraph.voiceCallRepository
        override val demoPresence = kotlinx.coroutines.flow.combine(sessionRepository.session, homeZoneCoordinator.desiredPresence) { session, presence ->
            presence.takeIf { demoEnabled && session is dev.chayeon.core.model.SessionState.Active }
        }
        override val discoveryRepository = this@AppGraph.discoveryRepository
        override val connectionRepository = this@AppGraph.connectionRepository
        override val profileRepository = this@AppGraph.profileRepository
        override val drivingStateProvider = this@AppGraph.drivingStateProvider
        override val serverTimeProvider = this@AppGraph.serverTimeProvider
        override val homeZoneRepository = this@AppGraph.homeZoneRepository
    }

    val historyDeps: HistoryDeps = object : HistoryDeps {
        override val voiceCallRepository = this@AppGraph.voiceCallRepository
        override val sessionRepository = this@AppGraph.sessionRepository
        override val discoveryRepository = this@AppGraph.discoveryRepository
        override val connectionRepository = this@AppGraph.connectionRepository
        override val signatureRepository = this@AppGraph.signatureRepository
        override val profileRepository = this@AppGraph.profileRepository
        override val encounterRepository = this@AppGraph.encounterRepository
        override val afterRepository = this@AppGraph.afterRepository
        override val safetyRepository = this@AppGraph.safetyRepository
        override val drivingStateProvider = this@AppGraph.drivingStateProvider
        override val serverTimeProvider = this@AppGraph.serverTimeProvider
        override val homeZoneRepository = this@AppGraph.homeZoneRepository
        override val syntheticHomeEnabled get() = this@AppGraph.demoEnabled
        override suspend fun prepareHomeLocation() = this@AppGraph.prepareHomeLocation()
    }

    val signatureDeps: SignatureDeps = object : SignatureDeps {
        override val profileRepository = this@AppGraph.profileRepository
        override val signatureRepository = this@AppGraph.signatureRepository
        override val drivingStateProvider = this@AppGraph.drivingStateProvider
        override val vehicleConsole = this@AppGraph.vehicleConsole
    }

    var currentPolicy: () -> dev.chayeon.core.model.DrivingPolicy = { dev.chayeon.core.model.DrivingPolicy.UNKNOWN }
    var isRealVehicle: Boolean = true
    var demoEnabled: Boolean = false
    var prepareHomeLocation: suspend () -> Boolean = { false }
    var prepareMicrophone: suspend () -> Boolean = { false }
    var syntheticHomePosition: suspend (Boolean) -> dev.chayeon.core.model.AppResult<Unit> = { dev.chayeon.core.model.AppResult.Failure(dev.chayeon.core.model.AppError.Restricted) }
}
