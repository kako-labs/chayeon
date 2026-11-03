package dev.chayeon.feature.signature

import dev.chayeon.core.model.DrivingStateProvider
import dev.chayeon.core.model.ProfileRepository
import dev.chayeon.core.model.SignatureRepository

interface SignatureDeps {
    val profileRepository: ProfileRepository
    val signatureRepository: SignatureRepository
    val drivingStateProvider: DrivingStateProvider
}
