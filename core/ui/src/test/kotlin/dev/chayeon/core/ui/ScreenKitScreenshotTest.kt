package dev.chayeon.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import dev.chayeon.core.model.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w1682dp-h1210dp-land-mdpi")
class ScreenKitScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun capture(name: String, width: Int = 1000, height: Int = 820, policy: DrivingPolicy = DrivingPolicy.ALLOWED, content: @Composable () -> Unit) {
        compose.setContent {
            ChayeonTheme(policy, animatorScaleOverride = 0f) {
                ChayeonBackground(Modifier.requiredSize(width.dp, height.dp).testTag("kit")) {
                    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) { content() }
                }
            }
        }
        compose.onNodeWithTag("kit").captureRoboImage("src/test/screenshots/kit_$name.png")
    }

    @Test fun traitBadge() = capture("trait_badge") {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TraitLevel.entries.forEach { TraitBadge(TraitId.SIGNAL_MANNERS, it) }
            TraitBadge(TraitId.TIRE_CARE, TraitLevel.SIGNATURE, size = TraitBadgeSize.DETAIL)
        }
    }
    @Test fun signatureHero() = capture("signature_hero") { SignatureHero(gallerySignature, Modifier.fillMaxWidth()) }
    @Test fun signatureMini() = capture("signature_mini") { GlassCard { SignatureMini(gallerySignature) } }
    @Test fun chemistryCard() = capture("chemistry_card") { ChemistryCard(galleryChemistry, KnotSeed(2357933), KnotSeed(1842720), DataSource.SYNTHETIC, DataSource.SYNTHETIC, Modifier.fillMaxWidth()) }
    @Test fun emptyChemistry() = capture("chemistry_empty") { ChemistryCard(Chemistry(), KnotSeed(1), KnotSeed(2), DataSource.SYNTHETIC, DataSource.SYNTHETIC) }
    @Test fun candidateCard() = capture("candidate_card", width = 600, height = 1000) { CandidateCard(galleryCandidate, {}, Modifier.fillMaxWidth()) }
    @Test fun candidateRecording() = capture("candidate_recording", width = 600, height = 1000) {
        CandidateCard(galleryCandidate.copy(profile = galleryPeer.copy(signature = null), chemistry = Chemistry()), {}, Modifier.fillMaxWidth())
    }
    @Test fun peerHeader() = capture("peer_header") {
        Column(verticalArrangement = Arrangement.spacedBy(40.dp)) {
            PeerHeader(galleryPeer)
            PeerHeader(galleryPeer.copy(kind = ProfileKind.DEMO_PEER), size = PeerHeaderSize.SMALL)
        }
    }
    @Test fun responseCountdown() = capture("response_countdown") {
        Column(Modifier.width(600.dp), verticalArrangement = Arrangement.spacedBy(32.dp)) {
            ResponseCountdown(108_000, totalMillis = 120_000)
            ResponseCountdown(null)
            ResponseCountdown(0, totalMillis = 120_000)
            ResponseCountdown(82_800_000)
        }
    }
    @Test fun gelTextField() = capture("gel_text_field") {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            GelTextField("느린오후", {}, "닉네임", maxLength = 12)
            GelTextField("", {}, "인스타그램 아이디", placeholder = "아이디를 입력해 주세요", maxLength = 31)
            GelTextField("가", {}, "닉네임 확인", error = "두 글자 이상 적어주세요", maxLength = 12)
        }
    }
    @Test fun lockedForms() = capture("locked_forms", policy = DrivingPolicy.UNKNOWN) {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            GelTextField("느린오후", {}, "닉네임", maxLength = 12)
            AvatarPicker(AvatarId.SLOW, {})
            InterestPicker(setOf(InterestId.JAZZ), 5, {})
        }
    }
    @Test fun interestPicker() = capture("interest_picker") { InterestPicker(setOf(InterestId.JAZZ, InterestId.NIGHT_VIEW), 5, {}) }
    @Test fun avatarPicker() = capture("avatar_picker") { AvatarPicker(AvatarId.SLOW, {}) }
    @Test fun appHeader() = capture("app_header", width = 1600, height = 300) { AppHeader(profile = galleryPeer) }
    @Test fun sectionTitle() = capture("section_title") { SectionTitle("주행 스타일", subtitle = "주행으로 보여주는 나만의 매력이에요") }
    @Test fun gradientText() = capture("gradient_text") { GradientText("길 위에서\n시작되는 인연") }
    @Test fun progressDots() = capture("progress_dots") { ProgressDots(1, 3) }
    @Test fun infoRow() = capture("info_row") { InfoRow(ChayeonIcon.LOCK, "서로 수락해야 연결돼요", body = "수락 전에는 대화가 열리지 않아요") }
    @Test fun settingsRow() = capture("settings_row") { SettingsRow(ChayeonIcon.EYE, "위치와 공개 범위", "상대에게 보이는 항목을 확인해요", {}) }
    @Test fun confirmSheet() {
        compose.setContent {
            ChayeonTheme(DrivingPolicy.ALLOWED, 0f) {
                ChayeonBackground(Modifier.fillMaxSize()) {}
                ConfirmSheet("이 관계를 마칠까요?", "이어진 대화가 닫혀요. 처음 스친 기록은 남아요.", "관계 끝내기", {}, {}, Modifier.testTag("confirm"))
            }
        }
        compose.onNodeWithTag("confirm").captureRoboImage("src/test/screenshots/kit_confirm_sheet.png")
    }

    @Test fun signatureGallery() = capture("gallery_signature", 1682, 1210) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            AppHeader(profile = galleryPeer)
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    SignatureHero(gallerySignature, Modifier.fillMaxWidth())
                    SignatureMini(gallerySignature)
                    TraitBadge(TraitId.SIGNAL_MANNERS, TraitLevel.SIGNATURE, size = TraitBadgeSize.DETAIL)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    ChemistryCard(galleryChemistry, KnotSeed(2357933), KnotSeed(1842720), DataSource.SYNTHETIC, DataSource.SYNTHETIC, Modifier.fillMaxWidth())
                    ResponseCountdown(108_000, totalMillis = 120_000)
                    InfoRow(ChayeonIcon.EYE, "공개한 특성만 보여요", body = "무엇을 공개할지 언제든 바꿀 수 있어요")
                }
            }
            BottomNav(NavigationItem.PROFILE, true, {}, {})
        }
    }
    @Test fun formsGallery() = capture("gallery_forms", 1682, 1210) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            SectionTitle("나를 소개하는 작은 선택", subtitle = "가상 프로필로 시작해요")
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                GlassCard(Modifier.weight(1f)) {
                    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        GelTextField("느린오후", {}, "닉네임", maxLength = 12)
                        AvatarPicker(AvatarId.SLOW, {})
                        InterestPicker(setOf(InterestId.JAZZ, InterestId.COFFEE), 5, {})
                        ProgressDots(1, 3)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    PeerHeader(galleryPeer, size = PeerHeaderSize.SMALL)
                    SettingsRow(ChayeonIcon.EYE, "위치와 공개 범위", "상대에게 보이는 항목을 확인해요", {})
                    ConfirmSheetContent("이 관계를 마칠까요?", "대화가 닫혀요. 처음 스친 기록은 남아요.", "관계 끝내기", {}, {})
                }
            }
        }
    }
    @Test fun candidateGallery() = capture("gallery_candidates", 1682, 1210) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            AppHeader(profile = galleryPeer)
            SectionTitle("지금 연결할 수 있는 사람", subtitle = "주행 습관에서 나와 잘 맞는 점을 찾아요")
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                CandidateCard(galleryCandidate, {}, Modifier.weight(1f))
                CandidateCard(galleryCandidate.copy(profile = galleryPeer.copy(nickname = "숲길산책", avatar = AvatarId.FOREST, kind = ProfileKind.DEMO_PEER)), {}, Modifier.weight(1f))
                CandidateCard(galleryCandidate.copy(profile = galleryPeer.copy(nickname = "재즈한잔", avatar = AvatarId.JAZZ, signature = null), chemistry = Chemistry()), {}, Modifier.weight(1f))
            }
            BottomNav(NavigationItem.MEET, true, {}, {})
        }
    }
    @Test
    @Config(sdk = [35], qualifiers = "w840dp-h1210dp-port-mdpi")
    fun narrowGallery() = capture("gallery_narrow", 840, 1210) {
        val chrome = previewChrome()
        ScreenScaffold(topBar = chrome.topBar, bottomBar = { chrome.bottomBar(NavigationItem.MEET) },
            primary = { CandidateCard(galleryCandidate, {}, Modifier.fillMaxWidth()) },
            secondary = { ChemistryCard(galleryChemistry, KnotSeed(2357933), KnotSeed(1842720), DataSource.SYNTHETIC, DataSource.SYNTHETIC, Modifier.fillMaxWidth()) })
    }
}
