package dev.chayeon.feature.signature

import dev.chayeon.core.model.DrivingStateProvider
import dev.chayeon.core.model.ProfileRepository
import dev.chayeon.core.model.SignatureRepository
import dev.chayeon.core.model.VehicleConsole

interface SignatureDeps {
    val profileRepository: ProfileRepository
    val signatureRepository: SignatureRepository
    val drivingStateProvider: DrivingStateProvider
    val vehicleConsole: VehicleConsole
}
