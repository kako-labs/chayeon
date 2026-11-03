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
import dev.chayeon.data.social.EmptyAfterRepository
import dev.chayeon.data.social.EmptyConnectionRepository
import dev.chayeon.data.social.EmptyDiscoveryRepository
import dev.chayeon.data.social.EmptyEncounterRepository
import dev.chayeon.data.social.EmptyProfileRepository
import dev.chayeon.data.social.EmptySafetyRepository
import dev.chayeon.data.social.EmptySessionRepository
import dev.chayeon.data.social.EmptyServerTimeProvider
import dev.chayeon.data.vehicle.EmptyDrivingStateProvider
import dev.chayeon.data.vehicle.EmptySignatureRepository
import dev.chayeon.data.vehicle.EmptyVehicleConsole
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
) {
    val serverModeAvailable: Boolean get() = serverUrl.isNotBlank()

    val meetDeps: MeetDeps = object : MeetDeps {
        override val discoveryRepository = this@AppGraph.discoveryRepository
        override val connectionRepository = this@AppGraph.connectionRepository
        override val profileRepository = this@AppGraph.profileRepository
        override val drivingStateProvider = this@AppGraph.drivingStateProvider
        override val serverTimeProvider = this@AppGraph.serverTimeProvider
    }

    val historyDeps: HistoryDeps = object : HistoryDeps {
        override val sessionRepository = this@AppGraph.sessionRepository
        override val profileRepository = this@AppGraph.profileRepository
        override val encounterRepository = this@AppGraph.encounterRepository
        override val afterRepository = this@AppGraph.afterRepository
        override val safetyRepository = this@AppGraph.safetyRepository
        override val drivingStateProvider = this@AppGraph.drivingStateProvider
        override val serverTimeProvider = this@AppGraph.serverTimeProvider
    }

    val signatureDeps: SignatureDeps = object : SignatureDeps {
        override val profileRepository = this@AppGraph.profileRepository
        override val signatureRepository = this@AppGraph.signatureRepository
        override val drivingStateProvider = this@AppGraph.drivingStateProvider
    }

    companion object {
        fun foundation(
            serverUrl: String = "",
            drivingStateProvider: DrivingStateProvider = EmptyDrivingStateProvider(),
        ): AppGraph = AppGraph(
            sessionRepository = EmptySessionRepository(serverAvailable = serverUrl.isNotBlank()),
            profileRepository = EmptyProfileRepository(),
            discoveryRepository = EmptyDiscoveryRepository(),
            connectionRepository = EmptyConnectionRepository(),
            encounterRepository = EmptyEncounterRepository(),
            afterRepository = EmptyAfterRepository(),
            safetyRepository = EmptySafetyRepository(),
            drivingStateProvider = drivingStateProvider,
            signatureRepository = EmptySignatureRepository(),
            vehicleConsole = EmptyVehicleConsole(),
            serverTimeProvider = EmptyServerTimeProvider(),
            serverUrl = serverUrl,
        )
    }
}
