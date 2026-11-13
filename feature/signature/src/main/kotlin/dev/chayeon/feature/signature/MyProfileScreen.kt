package dev.chayeon.feature.signature

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.chayeon.core.model.*
import dev.chayeon.core.ui.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MyProfileScreen(
    state: MyProfileUiState,
    chrome: ScreenChrome,
    onSignature: () -> Unit,
    onVisibility: () -> Unit,
    onExit: (SignatureExit) -> Unit,
    onRetry: () -> Unit,
    onReceiveRequestsWhileDriving: (Boolean) -> Unit = {},
    onTrait: (TraitId) -> Unit = {},
    onDrivingSettings: () -> Unit = {},
) {
    val settingsAnchor = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val readySignature = state.signature.readyValue()
    val building = readySignature?.isRecording == true
    SignatureFrame("내 프로필", state.driving.policy, chrome, scene = if (building) Scene.ROAD else Scene.PLAIN,
        primary = {
            SignatureColumn {
                when (val loaded = state.profile) {
                    is LoadState.Loading -> SignatureMessage("프로필을 불러오고 있어요", "나의 아바타와 시그니처를 준비하고 있어요.")
                    is LoadState.Failed -> SignatureFailure(loaded.error, "프로필을 불러오지 못했어요", onRetry)
                    is LoadState.Ready -> {
                        val profile = loaded.value?.publicProfile
                        if (profile == null) {
                            SignatureMessage("나를 소개해 주세요", "아바타와 관심사를 고르면 프로필을 시작할 수 있어요.",
                                ChayeonIcon.PROFILE, "프로필 만들기", { onExit(SignatureExit.OpenProfileEdit) })
                        } else {
                            if (building) {
                                StatusChip(if (state.settings.readyValue()?.recordingEnabled == true) "주행 기록 켜짐" else "주행 기록 꺼짐", variant = StatusVariant.PLAIN)
                                HeroHeadline("달릴수록\n프로필이 쌓여요", "평소처럼 운전하면 차가 읽은 신호로 프로필이 만들어져요.", style = ChayeonType.SceneTitle)
                                Mascot(MascotPose.THREAD, Modifier.fillMaxWidth().height(ChayeonDimensions.SceneMascot))
                                if (state.consoleAvailable) SignatureButton(if (LocalSourceLabels.current) "합성 주행으로 먼저 만나보기" else "시그니처 만들기",
                                    { onExit(SignatureExit.CreateSignature) }, Modifier.fillMaxWidth())
                                SignatureButton("프로필 편집", { onExit(SignatureExit.OpenProfileEdit) }, Modifier.fillMaxWidth(), JellyVariant.SECONDARY)
                            } else GlassPanel(Modifier.fillMaxWidth()) {
                                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Large)) {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.ChipHorizontal)) {
                                        AvatarDish(MochiAvatar.from(profile.avatar), Modifier.size(ChayeonSpacing.Button))
                                        Column {
                                            ChayeonText(profile.nickname, style = ChayeonType.SceneName)
                                            ChayeonText(profile.interests.sortedBy { it.ordinal }.joinToString(" · ") { it.label }, style = ChayeonType.Minimum, color = ChayeonColors.InfoText)
                                            if (LocalSourceLabels.current) ChayeonText(profile.kind.label, style = ChayeonType.Minimum, color = ChayeonColors.InfoText)
                                        }
                                    }
                                    when (val signature = state.signature) {
                                        is LoadState.Loading -> ChayeonText("시그니처를 불러오고 있어요", color = ChayeonColors.Secondary)
                                        is LoadState.Failed -> SignatureFailure(signature.error, "시그니처를 불러오지 못했어요", onRetry)
                                        is LoadState.Ready -> {
                                            SignatureKnot(signature.value.knotSeed, Modifier.fillMaxWidth().height(ChayeonDimensions.HeroKnot), reveal = false)
                                            ChayeonText("내 칭호", style = ChayeonType.Label, color = ChayeonColors.Accent)
                                            ChayeonText(signature.value.displayTitle, style = ChayeonType.SceneButton)
                                            SignatureSource(signature.value.displaySource)
                                            ChayeonText("최근 30일 · 주행 ${signature.value.includedTripCount}회로 만들었어요", style = ChayeonType.Minimum, color = ChayeonColors.InfoText)
                                            SignatureButton("내 시그니처 보기", onSignature, Modifier.fillMaxWidth(), JellyVariant.SECONDARY)
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap)) {
                                        SignatureButton("프로필 편집", { onExit(SignatureExit.OpenProfileEdit) }, Modifier.weight(1f), JellyVariant.SECONDARY)
                                        SignatureButton("설정", { scope.launch { settingsAnchor.bringIntoView() } }, Modifier.weight(1f), JellyVariant.SECONDARY)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        secondary = {
            SignatureColumn {
                val signature = state.signature.readyValue()
                if (signature != null) {
                    if (signature.isRecording) ProfileBuilding(signature, state.settings.readyValue()?.recordingEnabled == true)
                    else ProfileTraitRows(signature, onTrait)
                }
                Box(Modifier.bringIntoViewRequester(settingsAnchor)) {
                    SettingsRow(ChayeonIcon.PHONE, "주행 중 연결", "달리는 동안 새 사람과 어떻게 이어질지 골라요.", onDrivingSettings)
                }
                SettingsRow(ChayeonIcon.KNOT, "내 시그니처", "주행 스타일과 차량 관리에 담긴 나를 알아봐요.", onSignature)
                SettingsRow(ChayeonIcon.EYE, "시그니처 공개 범위", "상대에게 보일 특성을 직접 골라요.", onVisibility)
                SettingsRow(ChayeonIcon.LOCK, "차량 데이터와 위치 사용", "어떤 정보를 읽고 사용하는지 확인해요.", { onExit(SignatureExit.OpenConsent) })
                SettingsRow(ChayeonIcon.LOCK, "집 보호 구역", "집 근처에서는 잠시 발견을 쉬어요.", { onExit(SignatureExit.OpenHomeZone) })
                SettingsRow(ChayeonIcon.PEOPLE, "차단과 신고", "차단한 사람을 확인하고 관리해요.", { onExit(SignatureExit.OpenBlocks) })
                SettingsRow(ChayeonIcon.HISTORY, "기록과 계정 정리", "무엇이 지워지고 무엇이 남는지 확인해요.", { onExit(SignatureExit.OpenCleanup) })
                GlassPanel(Modifier.fillMaxWidth()) {
                    InfoRow(ChayeonIcon.LOCK, "차량 정보는 보이지 않아요",
                        body = "사진과 차량 정보는 포함하지 않아요. 연락처 ID는 서로 애프터를 수락한 뒤에만 공유해요.")
                }
                if (LocalSourceLabels.current) ChayeonText("시연용 로그인과 연령 확인이에요. 실제 인증은 아직 제공하지 않아요.",
                    style = ChayeonType.Minimum, color = ChayeonColors.Secondary)
            }
        },
    )
}
