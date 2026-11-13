package dev.chayeon.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
class DesignGalleryTest {
    @get:Rule val compose = createComposeRule()

    private fun capture(name: String, width: Int = 1682, height: Int = 1210, content: @Composable () -> Unit) {
        compose.setContent {
            ChayeonTheme(DrivingPolicy.ALLOWED, animatorScaleOverride = 0f) {
                ChayeonBackground(Modifier.requiredSize(width.dp, height.dp).testTag("gallery")) { content() }
            }
        }
        compose.onNodeWithTag("gallery").captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test fun designGallery() = capture("gallery_design_system") {
        Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            GearStrip(Gear.PARK, DrivingPolicy.ALLOWED, true)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    GradientText("차연의 감각", style = ChayeonType.Hero)
                    ChayeonText("붉은 실로 이어지는, 나만의 인연", style = ChayeonType.Body, color = ChayeonColors.Secondary)
                }
                StatusChip("가상 프로필", variant = StatusVariant.SYNTHETIC)
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                HeroPanel(Modifier.weight(1f).fillMaxSize()) {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        GradientText("말보다 오래 남는\n나의 드라이브 시그니처", style = ChayeonType.Title)
                        SignatureKnot(KnotSeed(2357933L), Modifier.weight(1f).fillMaxWidth(), reveal = false)
                        ChayeonText("여유롭게 멈추는 드라이버", style = ChayeonType.Section)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            InterestTag("여유로운 제동", matched = true)
                            InterestTag("깜빡이 매너")
                        }
                        StatusChip("합성 주행", variant = StatusVariant.SYNTHETIC)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    GlassCard(Modifier.fillMaxWidth()) {
                        ChayeonText("다정한 얼굴들", style = ChayeonType.Section)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            MochiAvatar.entries.forEach { MochiAvatar(it, Modifier.size(106.dp)) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            AvatarDish(MochiAvatar.DAWN, Modifier.size(128.dp))
                            Column(Modifier.padding(top = 20.dp)) {
                                ChayeonText("새벽드라이브", style = ChayeonType.Section)
                                ChayeonText("가상 프로필", style = ChayeonType.Secondary, color = ChayeonColors.Secondary)
                            }
                        }
                    }
                    GlassCard(Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            ChayeonText("손끝에 닿는 빛", style = ChayeonType.Section)
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                JellyButton("마음 전하기", {}, Modifier.weight(1f))
                                JellyButton("자세히 보기", {}, Modifier.weight(1f), JellyVariant.SECONDARY)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                JellyButton("수락하기", {}, Modifier.weight(1f), JellyVariant.WHITE)
                                JellyButton("차단·신고", {}, Modifier.weight(1f), JellyVariant.DANGER)
                            }
                            GelToggle(true, {}, "발견 켜짐")
                        }
                    }
                    TopBanner("새 요청이 왔어요. 주차한 뒤 확인하세요.")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatusChip("연결 중")
                        StatusChip("확인 중", variant = StatusVariant.WARNING)
                        StatusChip("합성 위치", variant = StatusVariant.SYNTHETIC)
                    }
                }
            }
            BottomNav(NavigationItem.MEET, true, {}, {})
        }
    }

    @Test fun formGallery() = capture("gallery_controls") {
        Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                BackButton({})
                ChayeonText("함께 시작할 준비", style = ChayeonType.Title)
                Spacer(Modifier.weight(1f))
                StepIndicator(1)
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                GlassCard(Modifier.weight(1f).fillMaxSize()) {
                    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        ChayeonText("나에게 맞게 골라요", style = ChayeonType.Section)
                        GelRadioRow("혼자 둘러보기", true, {}, description = "가상 프로필로 차연을 경험해요")
                        GelRadioRow("두 대로 연결", false, {}, description = "같은 시연 공간에 참여해요")
                        GelCheckbox("새로운 만남에 직접 참여해요", true, {}, description = "시연용 자기 확인이에요")
                        GelCheckbox("차량 데이터 사용", false, {})
                        GelToggle(false, {}, "밤 드라이브 공개")
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            IconOrb(ChayeonIcon.EYE)
                            IconOrb(ChayeonIcon.LOCK, variant = OrbVariant.GLASS)
                            IconOrb(ChayeonIcon.WARNING, variant = OrbVariant.WHITE)
                        }
                    }
                }
                HeroPanel(Modifier.weight(1f).fillMaxSize()) {
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        ChayeonText("상대에게 이렇게 보여요", style = ChayeonType.Section)
                        AvatarDish(MochiAvatar.SLOW, Modifier.size(280.dp))
                        ChayeonText("느린오후", style = ChayeonType.Title)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            InterestTag("재즈", matched = true)
                            InterestTag("야경", matched = true)
                            InterestTag("필름 사진")
                        }
                        StatusChip("가상 프로필", variant = StatusVariant.SYNTHETIC)
                        ChayeonText("당신의 일상에서\n천천히 이어지는 이야기", style = ChayeonType.Body)
                    }
                }
            }
            TopBanner("무엇을 공개할지 언제든 바꿀 수 있어요.", icon = ChayeonIcon.EYE)
        }
    }

    @Test fun threadGallery() = capture("gallery_threads") {
        Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            ChayeonText("실의 모양이 곧 마음의 상태", style = ChayeonType.Title)
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                GlassCard(Modifier.weight(1f).fillMaxSize()) {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                        ThreadStage.entries.forEach { stage ->
                            Column {
                                ChayeonText(stage.description, style = ChayeonType.Secondary)
                                ThreadView(stage, Modifier.fillMaxWidth(), animateTransition = false)
                            }
                        }
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    HeroPanel(Modifier.fillMaxWidth().weight(1f)) {
                        SignatureKnot(KnotSeed(2357933L), Modifier.fillMaxSize(), interlockedWith = KnotSeed(1842720L), reveal = false)
                    }

                }
            }
        }
    }

    private fun statesPage(name: String, states: List<ExperienceState>) = capture(name) {
        Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            states.chunked(3).forEach { row ->
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEach { StateCard(it, Modifier.weight(1f).fillMaxSize(), {}) }
                }
            }
        }
    }

    @Test fun discoveryStates() = statesPage("gallery_states_discover", ExperienceState.entries.take(6))
    @Test fun connectionStates() = statesPage("gallery_states_connect", ExperienceState.entries.drop(9).take(6))

    @Test fun remainingStates() = statesPage("gallery_states_other", ExperienceState.entries.drop(6).take(3) + ExperienceState.entries.drop(15))

    @Test fun knotGallery() = capture("gallery_signature_knots") {
        Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            ChayeonText("주행으로 짜인 하나뿐인 매듭", style = ChayeonType.Title)
            ChayeonText("합성 주행 · 서로 다른 시드의 결정적 형태", style = ChayeonType.Secondary, color = ChayeonColors.Secondary)
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                listOf(2357933L, 1842720L, 8733462L).forEachIndexed { index, seed ->
                    HeroPanel(Modifier.weight(1f).fillMaxSize()) {
                        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                            SignatureKnot(KnotSeed(seed), Modifier.weight(1f).fillMaxWidth(), reveal = false)
                            ChayeonText(listOf("여유로운 곡선", "차분한 흐름", "부드러운 여정")[index], style = ChayeonType.Section)
                        }
                    }
                }
            }
            HeroPanel(Modifier.fillMaxWidth().height(450.dp)) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    SignatureKnot(KnotSeed(2357933L), Modifier.weight(1f), interlockedWith = KnotSeed(8733462L), reveal = false)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        ChayeonText("함께하면 잘 맞는 점", style = ChayeonType.Section)
                        ChayeonText("둘 다 깜빡이를 챙겨요", style = ChayeonType.Body)
                        ChayeonText("둘 다 밤 드라이브를 좋아해요", style = ChayeonType.Body)
                    }
                }
            }
        }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w840dp-h1210dp-port-mdpi")
    fun narrowScaffold() = capture("gallery_narrow_scaffold", width = 840) {
        ScreenScaffold(
            topBar = { GearStrip(Gear.PARK, DrivingPolicy.ALLOWED, true) },
            primary = {
                HeroPanel(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        ChayeonText("나만의 드라이브\n시그니처", style = ChayeonType.Title)
                        SignatureKnot(KnotSeed(2357933L), Modifier.size(380.dp), reveal = false)
                        StatusChip("합성 주행", variant = StatusVariant.SYNTHETIC)
                    }
                }
            },
            secondary = { StateCard(ExperienceState.NO_CANDIDATES, Modifier.fillMaxWidth()) },
            bottomBar = { JellyButton("내 시그니처 보기", {}, Modifier.fillMaxWidth()) },
        )
    }
}
