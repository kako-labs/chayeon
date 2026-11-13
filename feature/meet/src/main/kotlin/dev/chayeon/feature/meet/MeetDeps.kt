package dev.chayeon.feature.meet

import dev.chayeon.core.model.ConnectionRepository
import dev.chayeon.core.model.DiscoveryRepository
import dev.chayeon.core.model.DrivingStateProvider
import dev.chayeon.core.model.ProfileRepository
import dev.chayeon.core.model.ServerTimeProvider

interface MeetDeps {
    val voiceCallRepository: dev.chayeon.core.model.VoiceCallRepository get() = dev.chayeon.core.model.NoVoiceCall
    val demoPresence: kotlinx.coroutines.flow.Flow<dev.chayeon.core.model.SyntheticPresence?>
        get() = kotlinx.coroutines.flow.flowOf(null)
    val homeZoneRepository: dev.chayeon.core.model.HomeZoneRepository
    val discoveryRepository: DiscoveryRepository
    val connectionRepository: ConnectionRepository
    val profileRepository: ProfileRepository
    val drivingStateProvider: DrivingStateProvider
    val serverTimeProvider: ServerTimeProvider
}
