package dev.chayeon.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import dev.chayeon.core.model.DrivingPolicy
import dev.chayeon.core.model.ExperienceState
import dev.chayeon.core.model.Gear
import dev.chayeon.core.model.KnotSeed
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w1682dp-h1210dp-land-mdpi")
class ComponentScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            ChayeonTheme(DrivingPolicy.ALLOWED, animatorScaleOverride = 0f) {
                ChayeonBackground(Modifier.requiredSize(900.dp, 580.dp).testTag("component")) {
                    Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) { content() }
                }
            }
        }
        compose.onNodeWithTag("component").captureRoboImage("src/test/screenshots/component_$name.png")
    }

    @Test fun background() = capture("background") {}
    @Test fun glassCard() = capture("glass_card") { GlassCard(Modifier.fillMaxWidth()) { ChayeonText("오늘의 인연", style = ChayeonType.Section); ChayeonText("천천히 서로를 알아가요", color = ChayeonColors.Secondary) } }
    @Test fun heroPanel() = capture("hero_panel") { HeroPanel(Modifier.fillMaxWidth()) { ChayeonText("길 위에서 시작되는 인연", style = ChayeonType.Title) } }
    @Test fun primaryButton() = capture("jelly_primary") { JellyButton("마음 전하기", {}, Modifier.fillMaxWidth()) }
    @Test fun secondaryButton() = capture("jelly_secondary") { JellyButton("자세히 보기", {}, Modifier.fillMaxWidth(), JellyVariant.SECONDARY) }
    @Test fun whiteButton() = capture("jelly_white") { JellyButton("수락하기", {}, Modifier.fillMaxWidth(), JellyVariant.WHITE) }
    @Test fun dangerButton() = capture("jelly_danger") { JellyButton("차단·신고", {}, Modifier.fillMaxWidth(), JellyVariant.DANGER) }
    @Test fun iconOrb() = capture("icon_orbs") { Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) { OrbVariant.entries.forEach { IconOrb(ChayeonIcon.THREAD, variant = it) } } }
    @Test fun avatarDish() = capture("avatar_dish") { AvatarDish(MochiAvatar.DAWN, Modifier.size(320.dp)) }
    @Test fun avatars() = capture("mochi_avatars") { Row { MochiAvatar.entries.forEach { MochiAvatar(it, Modifier.size(130.dp)) } } }
    @Test fun statusChips() = capture("status_chips") { Column(verticalArrangement = Arrangement.spacedBy(20.dp)) { StatusVariant.entries.forEach { StatusChip(if (it == StatusVariant.SYNTHETIC) "합성 위치" else "연결 상태", variant = it) } } }
    @Test fun interestTags() = capture("interest_tags") { Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) { InterestTag("겹친 관심사", matched = true); InterestTag("필름 사진") } }
    @Test fun gelToggle() = capture("gel_toggle") { Column { GelToggle(true, {}, "발견 켜짐"); GelToggle(false, {}, "발견 꺼짐") } }
    @Test fun gelRadio() = capture("gel_radio") { Column(verticalArrangement = Arrangement.spacedBy(20.dp)) { GelRadioRow("혼자 둘러보기", true, {}); GelRadioRow("두 대로 연결", false, {}) } }
    @Test fun gelCheckbox() = capture("gel_checkbox") { Column(verticalArrangement = Arrangement.spacedBy(20.dp)) { GelCheckbox("새로운 만남에 직접 참여해요", true, {}); GelCheckbox("차량 데이터 사용", false, {}) } }
    @Test fun stepIndicator() = capture("step_indicator") { StepIndicator(1) }
    @Test fun backButton() = capture("back_button") { BackButton({}) }
    @Test fun gearStrip() = capture("gear_strip") { Column(verticalArrangement = Arrangement.spacedBy(24.dp)) { GearStrip(Gear.PARK, DrivingPolicy.ALLOWED, true); GearStrip(Gear.DRIVE, DrivingPolicy.RESTRICTED, true); GearStrip(Gear.UNKNOWN, DrivingPolicy.UNKNOWN, false) } }
    @Test fun topBanner() = capture("top_banner") { TopBanner("새 요청이 왔어요. 주차한 뒤 확인하세요.") }
    @Test fun bottomNav() = capture("bottom_nav") { BottomNav(NavigationItem.MEET, true, {}, {}) }
    @Test fun chatBubbles() = capture("chat_bubble") { Column(verticalArrangement = Arrangement.spacedBy(20.dp)) { ChatBubble("반가워요", false); ChatBubble("깜빡이 매너가 멋져요", true) } }
    @Test fun presetButton() = capture("preset_button") { PresetButton("잠시 후 다시 이야기해요", {}) }
    @Test fun threadReach() = capture("thread_reach") { ThreadView(ThreadStage.REACH, animateTransition = false) }
    @Test fun threadConnected() = capture("thread_connected") { ThreadView(ThreadStage.CONNECTED, animateTransition = false) }
    @Test fun threadEnded() = capture("thread_ended") { ThreadView(ThreadStage.ENDED, animateTransition = false) }
    @Test fun threadAfterPending() = capture("thread_after_pending") { ThreadView(ThreadStage.AFTER_PENDING, animateTransition = false) }
    @Test fun threadAfterConnected() = capture("thread_after_connected") { ThreadView(ThreadStage.AFTER_CONNECTED, animateTransition = false) }
    @Test fun signatureKnot() = capture("signature_knot") { SignatureKnot(KnotSeed(2357933L), Modifier.size(480.dp), reveal = false) }
    @Test fun interlockedKnots() = capture("interlocked_knots") { SignatureKnot(KnotSeed(2357933L), interlockedWith = KnotSeed(1842720L), reveal = false) }
    @Test fun stateDiscoveryLoading() = capture("state_discovery_loading") { StateCard(ExperienceState.DISCOVERY_LOADING, Modifier.fillMaxWidth(), {}) }
    @Test fun stateLocationPermissionOff() = capture("state_location_permission_off") { StateCard(ExperienceState.LOCATION_PERMISSION_OFF, Modifier.fillMaxWidth(), {}) }
    @Test fun stateLocationStale() = capture("state_location_stale") { StateCard(ExperienceState.LOCATION_STALE, Modifier.fillMaxWidth(), {}) }
    @Test fun stateNoCandidates() = capture("state_no_candidates") { StateCard(ExperienceState.NO_CANDIDATES, Modifier.fillMaxWidth(), {}) }
    @Test fun stateNetworkFailure() = capture("state_network_failure") { StateCard(ExperienceState.NETWORK_FAILURE, Modifier.fillMaxWidth(), {}) }
    @Test fun stateDrivingRestricted() = capture("state_driving_restricted") { StateCard(ExperienceState.DRIVING_RESTRICTED, Modifier.fillMaxWidth(), {}) }
    @Test fun stateDrivingUnknown() = capture("state_driving_unknown") { StateCard(ExperienceState.DRIVING_UNKNOWN, Modifier.fillMaxWidth(), {}) }
    @Test fun stateAlreadyConnected() = capture("state_already_connected") { StateCard(ExperienceState.ALREADY_CONNECTED, Modifier.fillMaxWidth(), {}) }
    @Test fun stateProfileSaveFailed() = capture("state_profile_save_failed") { StateCard(ExperienceState.PROFILE_SAVE_FAILED, Modifier.fillMaxWidth(), {}) }
    @Test fun stateRequestDeclined() = capture("state_request_declined") { StateCard(ExperienceState.REQUEST_DECLINED, Modifier.fillMaxWidth(), {}) }
    @Test fun stateRequestExpired() = capture("state_request_expired") { StateCard(ExperienceState.REQUEST_EXPIRED, Modifier.fillMaxWidth(), {}) }
    @Test fun stateRequestCancelled() = capture("state_request_cancelled") { StateCard(ExperienceState.REQUEST_CANCELLED, Modifier.fillMaxWidth(), {}) }
    @Test fun statePeerUnavailable() = capture("state_peer_unavailable") { StateCard(ExperienceState.PEER_UNAVAILABLE, Modifier.fillMaxWidth(), {}) }
    @Test fun stateMessageSendFailed() = capture("state_message_send_failed") { StateCard(ExperienceState.MESSAGE_SEND_FAILED, Modifier.fillMaxWidth(), {}) }
    @Test fun stateConnectionChecking() = capture("state_connection_checking") { StateCard(ExperienceState.CONNECTION_CHECKING, Modifier.fillMaxWidth(), {}) }
    @Test fun stateAfterDeclined() = capture("state_after_declined") { StateCard(ExperienceState.AFTER_DECLINED, Modifier.fillMaxWidth(), {}) }
    @Test fun stateAfterExpired() = capture("state_after_expired") { StateCard(ExperienceState.AFTER_EXPIRED, Modifier.fillMaxWidth(), {}) }
    @Test fun stateAfterWithdrawn() = capture("state_after_withdrawn") { StateCard(ExperienceState.AFTER_WITHDRAWN, Modifier.fillMaxWidth(), {}) }
}
