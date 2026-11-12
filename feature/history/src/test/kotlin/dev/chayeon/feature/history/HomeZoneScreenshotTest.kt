package dev.chayeon.feature.history

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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
class HomeZoneScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private fun capture(name: String, status: HomeZoneStatus = HomeZoneStatus.NOT_SET, source: LocationSource = LocationSource.DEVICE,
        policy: DrivingPolicy = DrivingPolicy.ALLOWED, busy: Boolean = false, failure: Boolean = false, confirm: Boolean = false, consent: Boolean? = true) {
        val home = HomeZoneState(status, if (status == HomeZoneStatus.NOT_SET) null else HomeZoneRadius.MEDIUM,
            if (status == HomeZoneStatus.NOT_SET) null else source)
        val state = HomeZoneUiState(driving = DrivingState(policy = policy, gear = if (policy == DrivingPolicy.ALLOWED) Gear.PARK else Gear.DRIVE),
            home = home, syntheticEnabled = true, locationConsentGranted = consent, operation = if (busy) "set" else null, error = failure, confirmingClear = confirm)
        compose.setContent { ChayeonTheme(policy, animatorScaleOverride = 0f) {
            HomeZoneScreen(state, previewChrome(policy = policy, resting = home.suppressed, showSourceLabels = false), {}, {}, {}, {}, {}, {})
        } }
        if (status == HomeZoneStatus.LOCATION_UNKNOWN && source == LocationSource.SYNTHETIC)
            compose.onNodeWithText("시연 제어에서 ‘집에서 멀어지기’를 눌러 주세요. 그동안 발견을 쉬어요").assertIsDisplayed()
        if (consent != true) {
            compose.onNodeWithText("지금 위치를 집으로 정하기").assertIsNotEnabled()
            compose.onNodeWithText("집 보호 시작하기").assertIsEnabled()
        }
        if (name == "narrow") compose.onNodeWithText("집 보호 시작하기").performScrollTo()
        if (confirm) compose.onNode(isDialog()).captureRoboImage("src/test/screenshots/home_zone_$name.png")
        else compose.onNodeWithTag("screen").captureRoboImage("src/test/screenshots/home_zone_$name.png")
    }
    @Test fun consentOff() = capture("consent_off", HomeZoneStatus.PERMISSION_OFF, consent = false)
    @Test fun consentUnknown() = capture("consent_unknown", HomeZoneStatus.LOCATION_UNKNOWN, consent = null)
    @Test fun syntheticWithoutConsent() = capture("synthetic_without_consent", HomeZoneStatus.PERMISSION_OFF, LocationSource.SYNTHETIC, consent = false)
    @Test fun notSet() = capture("not_set")
    @Test fun inside() = capture("inside", HomeZoneStatus.INSIDE)
    @Test fun outside() = capture("outside", HomeZoneStatus.OUTSIDE)
    @Test fun unknownLocation() = capture("location_unknown", HomeZoneStatus.LOCATION_UNKNOWN)
    @Test fun permissionOff() = capture("permission_off", HomeZoneStatus.PERMISSION_OFF)
    @Test fun synthetic() = capture("synthetic", HomeZoneStatus.INSIDE, LocationSource.SYNTHETIC)
    @Test fun syntheticStartup() = capture("synthetic_startup", HomeZoneStatus.LOCATION_UNKNOWN, LocationSource.SYNTHETIC)
    @Test fun pending() = capture("pending", busy = true)
    @Test fun failure() = capture("failure", failure = true)
    @Test fun confirmation() = capture("confirm_clear", HomeZoneStatus.INSIDE, confirm = true)
    @Test fun restricted() = capture("restricted", HomeZoneStatus.INSIDE, policy = DrivingPolicy.RESTRICTED)
    @Test fun unknownDriving() = capture("driving_unknown", HomeZoneStatus.INSIDE, policy = DrivingPolicy.UNKNOWN)
    @Config(sdk = [35], qualifiers = "w840dp-h1210dp-port-mdpi")
    @Test fun narrow() = capture("narrow", HomeZoneStatus.INSIDE)
}
