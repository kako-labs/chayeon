package dev.chayeon.feature.signature

import androidx.compose.ui.test.junit4.createComposeRule
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
        val signature = if (variant == "empty" || variant == "recording") MySignature() else f.signature
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
                        }, signatureLoad, settings = f.ready(ParticipationSettings("settings", 1, receiveRequestsWhileDriving = variant == "incoming_on")), consoleAvailable = true), chrome, {}, {}, {}, {})
                    "signature" -> MySignatureScreen(SignatureUiState(driving, signatureLoad, true), chrome, {}, {}, {}, {}, {})
                    "trait" -> TraitDetailScreen(TraitDetailUiState(driving, f.evidence.trait,
                        when (variant) {
                            "loading" -> LoadState.Loading()
                            "failure" -> LoadState.Failed(AppError.Network)
                            "empty" -> f.ready(null)
                            "unsupported" -> f.ready(f.evidence.copy(availability = TraitAvailability.UNSUPPORTED, metrics = emptyList()))
                            "recording" -> f.ready(f.evidence.copy(availability = TraitAvailability.INSUFFICIENT_EVIDENCE,
                                level = TraitLevel.RECORDING, validTripCount = 1, metrics = emptyList()))
                            else -> f.ready(f.evidence)
                        }), chrome, {}, {})
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
        compose.onNodeWithTag("signature-screen").captureRoboImage("src/test/screenshots/${screen}_$variant.png")
    }
    @Test fun profile_incoming_on() = capture("profile", "incoming_on")
    @Test fun profile_incoming_off() = capture("profile", "incoming_off")
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
