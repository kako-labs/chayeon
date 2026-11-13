package dev.chayeon.feature.signature

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithTag
import com.github.takahirom.roborazzi.captureRoboImage
import dev.chayeon.core.model.*
import dev.chayeon.core.ui.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w1682dp-h1210dp-land-mdpi")
class SignatureScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun capture(screen: String, variant: String) {
        val f = SignatureScreenFixtures
        val policy = when (variant) {
            "restricted" -> DrivingPolicy.RESTRICTED
            "unknown" -> DrivingPolicy.UNKNOWN
            else -> DrivingPolicy.ALLOWED
        }
        val driving = f.driving.copy(policy = policy, gear = if (policy == DrivingPolicy.ALLOWED) Gear.PARK else Gear.UNKNOWN)
        val signature = when (variant) {
            "empty" -> MySignature()
            "recording", "recording_off" -> f.signature.copy(publicSignature = null, evidence = f.signature.evidence.map {
                it.copy(level = TraitLevel.RECORDING, validTripCount = 1, score = null, recentTrips = it.recentTrips.take(1))
            })
            else -> f.signature
        }
        val signatureLoad = when (variant) {
            "loading" -> LoadState.Loading()
            "failure" -> LoadState.Failed(AppError.Network)
            else -> f.ready(signature)
        }
        compose.setContent {
            ChayeonTheme(policy, animatorScaleOverride = 0f) {
                val chrome = previewChrome(policy = policy, gear = driving.gear, showSourceLabels = variant == "server")
                when (screen) {
                    "profile" -> MyProfileScreen(MyProfileUiState(driving,
                        when (variant) {
                            "loading" -> LoadState.Loading()
                            "failure" -> LoadState.Failed(AppError.Network)
                            "empty" -> f.ready(null)
                            else -> f.ready(f.profile)
                        }, signatureLoad, settings = f.ready(f.settings.copy(receiveRequestsWhileDriving = variant == "incoming_on",
                            consents = if (variant == "recording_off") emptyList() else f.settings.consents)), consoleAvailable = true), chrome, {}, {}, {}, {})
                    "driving_settings" -> DriveSettingsScreen(MyProfileUiState(driving = driving,
                        settings = when (variant) {
                            "loading" -> LoadState.Loading()
                            "failure" -> LoadState.Failed(AppError.Network)
                            "empty" -> f.ready(ParticipationSettings("settings", 1))
                            else -> f.ready(ParticipationSettings("settings", 1,
                            drivingConnectMode = if (variant == "off") DrivingConnectMode.OFF else if (variant == "ring") DrivingConnectMode.RING else DrivingConnectMode.AUTO,
                            autoConnectConditions = AutoConnectConditions(mapOf(TraitId.GENTLE_RIDE to 80, TraitId.SIGNAL_MANNERS to 90), true))) }),
                        chrome, {}, {}, { _, _ -> }, {}, {})
                    "signature" -> MySignatureScreen(SignatureUiState(driving, signatureLoad, true, recordingEnabled = true), chrome, {}, {}, {}, {}, {})
                    "trait" -> TraitDetailScreen(TraitDetailUiState(driving, if (variant == "preference") TraitId.LONG_ROAD else f.evidence.trait,
                        when (variant) {
                            "loading" -> LoadState.Loading()
                            "failure" -> LoadState.Failed(AppError.Network)
                            "empty" -> f.ready(null)
                            "unsupported" -> f.ready(f.evidence.copy(availability = TraitAvailability.UNSUPPORTED, metrics = emptyList(), score = null))
                            "recording" -> f.ready(f.evidence.copy(availability = TraitAvailability.INSUFFICIENT_EVIDENCE,
                                level = TraitLevel.RECORDING, validTripCount = 1, metrics = emptyList(), score = null,
                                sampleCount = 30, passingSampleCount = 24, recentTrips = f.evidence.recentTrips.take(1)))
                            "private_score" -> f.ready(f.evidence.copy(score = 54, level = TraitLevel.RECORDING,
                                availability = TraitAvailability.INSUFFICIENT_EVIDENCE, passingSampleCount = 130,
                                recentTrips = f.evidence.recentTrips.map { it.copy(passingSampleCount = if (it.tripId == "trip-0") 18 else 16) }))
                            "preference" -> f.ready(f.evidence.copy(trait = TraitId.LONG_ROAD, score = null, sampleCount = 8,
                                passingSampleCount = 6, signals = setOf(SignalKind.TRIP_LENGTH),
                                metrics = listOf(PrivateMetric(MetricId.LONG_TRIP_RATIO, .75, MetricUnit.RATIO)),
                                recentTrips = f.evidence.recentTrips.map { it.copy(supportedSampleCount = 1,
                                    passingSampleCount = if (it.tripId in setOf("trip-4", "trip-6")) 0 else 1) }))
                            else -> f.ready(f.evidence)
                        }, visible = variant == "public"), chrome, {}, {})
                    "visibility" -> SignatureVisibilityScreen(VisibilityUiState(driving, signatureLoad,
                        visibility = if (variant == "empty") VisibilitySettings() else f.visibility,
                        preview = if (variant in setOf("empty", "loading", "failure")) null else f.public,
                        error = if (variant == "publish_failure") AppError.Network else null,
                        published = variant != "empty", publicationKnown = true, pendingChanges = variant == "publish_failure" || variant == "pending"), chrome, {}, { _, _ -> }, {}, {})
                    "recap" -> TripRecapScreen(TripRecapUiState(driving,
                        when (variant) {
                            "loading" -> LoadState.Loading()
                            "failure" -> LoadState.Failed(AppError.Network)
                            "empty" -> f.ready(null)
                            "excluded" -> f.ready(f.recap.copy(included = false, changes = emptyList()))
                            "no_changes" -> f.ready(f.recap.copy(changes = emptyList()))
                            else -> f.ready(f.recap)
                        }, error = if (variant == "exclude_failure") AppError.Network else null, pendingChanges = variant == "exclude_failure"), chrome, {}, {}, {}, {})
                }
            }
        }
        if (screen == "profile" && variant == "privacy") {
            compose.onNodeWithText("사진과 차량 정보는 포함하지 않아요. 연락처 ID는 서로 애프터를 수락한 뒤에만 공유해요.").performScrollTo()
        }
        if (screen == "driving_settings" && variant == "interests") compose.onNodeWithText("관심사가 하나 이상 겹치는 사람").performScrollTo()
        if (screen == "profile" && variant == "scores") compose.onNodeWithTag("profile-trait-STEADY_PACE").performScrollTo()
        if (screen == "profile" && variant == "unsupported") compose.onNodeWithTag("profile-trait-BATTERY_KIND").performScrollTo()
        if (screen == "trait" && variant == "trips") compose.onNodeWithText("최근 주행").performScrollTo()
        compose.onNodeWithTag("signature-screen").captureRoboImage("src/test/screenshots/${screen}_$variant.png")
    }
    @Test fun settings_loading() = capture("driving_settings", "loading")
    @Test fun settings_failure() = capture("driving_settings", "failure")
    @Test fun settings_empty() = capture("driving_settings", "empty")
    @Test fun settings_interests() = capture("driving_settings", "interests")
    @Test fun settings_auto() = capture("driving_settings", "auto")
    @Test fun settings_ring() = capture("driving_settings", "ring")
    @Test fun settings_off() = capture("driving_settings", "off")
    @Test fun settings_restricted() = capture("driving_settings", "restricted")
    @Test fun settings_unknown() = capture("driving_settings", "unknown")
    @Config(sdk = [35], qualifiers = "w840dp-h1210dp-port-mdpi")
    @Test fun settings_narrow() = capture("driving_settings", "narrow")
    @Test fun profile_scores() = capture("profile", "scores")
    @Test fun profile_unsupported() = capture("profile", "unsupported")
    @Test fun trait_public() = capture("trait", "public")
    @Test fun trait_private_score() = capture("trait", "private_score")
    @Test fun trait_preference() = capture("trait", "preference")
    @Test fun trait_trips() = capture("trait", "trips")
    @Test fun profile_incoming_on() = capture("profile", "incoming_on")
    @Test fun profile_incoming_off() = capture("profile", "incoming_off")
    @Test fun profile_privacy() = capture("profile", "privacy")
    @Test fun profile_populated() = capture("profile", "populated")
    @Test fun profile_loading() = capture("profile", "loading")
    @Test fun profile_empty() = capture("profile", "empty")
    @Test fun profile_failure() = capture("profile", "failure")
    @Test fun profile_restricted() = capture("profile", "restricted")
    @Test fun profile_unknown() = capture("profile", "unknown")
    @Config(sdk = [35], qualifiers = "w840dp-h1210dp-port-mdpi")
    @Test fun profile_narrow() = capture("profile", "narrow")
    @Test fun signature_populated() = capture("signature", "populated")
    @Test fun signature_loading() = capture("signature", "loading")
    @Test fun signature_empty() = capture("signature", "empty")
    @Test fun signature_failure() = capture("signature", "failure")
    @Test fun signature_restricted() = capture("signature", "restricted")
    @Test fun signature_unknown() = capture("signature", "unknown")
    @Config(sdk = [35], qualifiers = "w840dp-h1210dp-port-mdpi")
    @Test fun signature_narrow() = capture("signature", "narrow")
    @Test fun trait_populated() = capture("trait", "populated")
    @Test fun trait_loading() = capture("trait", "loading")
    @Test fun trait_empty() = capture("trait", "empty")
    @Test fun trait_failure() = capture("trait", "failure")
    @Test fun trait_restricted() = capture("trait", "restricted")
    @Test fun trait_unknown() = capture("trait", "unknown")
    @Config(sdk = [35], qualifiers = "w840dp-h1210dp-port-mdpi")
    @Test fun trait_narrow() = capture("trait", "narrow")
    @Test fun visibility_populated() = capture("visibility", "populated")
    @Test fun visibility_loading() = capture("visibility", "loading")
    @Test fun visibility_empty() = capture("visibility", "empty")
    @Test fun visibility_failure() = capture("visibility", "failure")
    @Test fun visibility_restricted() = capture("visibility", "restricted")
    @Test fun visibility_unknown() = capture("visibility", "unknown")
    @Config(sdk = [35], qualifiers = "w840dp-h1210dp-port-mdpi")
    @Test fun visibility_narrow() = capture("visibility", "narrow")
    @Test fun recap_populated() = capture("recap", "populated")
    @Test fun recap_loading() = capture("recap", "loading")
    @Test fun recap_empty() = capture("recap", "empty")
    @Test fun recap_failure() = capture("recap", "failure")
    @Test fun recap_restricted() = capture("recap", "restricted")
    @Test fun recap_unknown() = capture("recap", "unknown")
    @Config(sdk = [35], qualifiers = "w840dp-h1210dp-port-mdpi")
    @Test fun recap_narrow() = capture("recap", "narrow")
    @Test fun profile_server() = capture("profile", "server")
    @Test fun signature_server() = capture("signature", "server")
    @Test fun trait_server() = capture("trait", "server")
    @Test fun visibility_server() = capture("visibility", "server")
    @Test fun recap_server() = capture("recap", "server")
    @Test fun profile_recording_off() = capture("profile", "recording_off")
    @Test fun profile_recording() = capture("profile", "recording")
    @Test fun signature_recording() = capture("signature", "recording")
    @Test fun trait_unsupported() = capture("trait", "unsupported")
    @Test fun trait_recording() = capture("trait", "recording")
    @Test fun visibility_pending() = capture("visibility", "pending")
    @Test fun visibility_publish_failure() = capture("visibility", "publish_failure")
    @Test fun recap_excluded() = capture("recap", "excluded")
    @Test fun recap_no_changes() = capture("recap", "no_changes")
    @Test fun recap_exclude_failure() = capture("recap", "exclude_failure")
}
