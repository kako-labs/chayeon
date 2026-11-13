package dev.chayeon.feature.signature

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
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
) {
    SignatureFrame("내 프로필", state.driving.policy, chrome,
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
                            HeroPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(ChayeonSpacing.Screen)) {
                                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium)) {
                                    PeerHeader(profile, size = PeerHeaderSize.SMALL)
                                    when (val signature = state.signature) {
                                        is LoadState.Loading -> ChayeonText("시그니처를 불러오고 있어요", color = ChayeonColors.Secondary)
                                        is LoadState.Failed -> SignatureFailure(signature.error, "시그니처를 불러오지 못했어요", onRetry)
                                        is LoadState.Ready -> {
                                            SignatureKnot(signature.value.knotSeed, Modifier.size(ChayeonDimensions.PeerLarge), reveal = false)
                                            GradientText(signature.value.displayTitle, style = ChayeonType.Title)
                                            SignatureSource(signature.value.displaySource)
                                            if (signature.value.isRecording && state.consoleAvailable) {
                                                ChayeonText("주행 3번이 모이면 주차 후 나에게 먼저 보여 드려요. 공개는 내가 골라요.",
                                                    style = ChayeonType.Secondary, color = ChayeonColors.Secondary)
                                                JellyButton(if (LocalSourceLabels.current) "합성 주행으로 먼저 만나보기" else "시그니처 만들기", { onExit(SignatureExit.CreateSignature) }, Modifier.fillMaxWidth())
                                            } else JellyButton("내 시그니처 보기", onSignature, Modifier.fillMaxWidth())
                                        }
                                    }
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.TagGap),
                                        verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.TagGap)) {
                                        profile.interests.sortedBy { it.ordinal }.forEach { InterestTag(it.label, matched = true) }
                                    }
                                    JellyButton("프로필 편집", { onExit(SignatureExit.OpenProfileEdit) },
                                        Modifier.fillMaxWidth(), JellyVariant.SECONDARY)
                                }
                            }
                        }
                    }
                }
            }
        },
        secondary = {
            SignatureColumn {
                GlassCard(Modifier.fillMaxWidth()) {
                    val settings = (state.settings as? LoadState.Ready)?.value
                    Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium)) {
                        GelToggle(settings?.receiveRequestsWhileDriving == true, onReceiveRequestsWhileDriving,
                            "주행 중에도 새 요청 받기", enabled = settings != null && !state.updatingCallSetting)
                        ChayeonText("전화처럼 닉네임과 칭호만 보고 받을 수 있어요", style = ChayeonType.Secondary, color = ChayeonColors.Secondary)
                        if (state.callSettingError != null) ChayeonText("설정을 바꾸지 못했어요. 다시 눌러 주세요", style = ChayeonType.Secondary, color = ChayeonColors.Pink)
                    }
                }
                SettingsRow(ChayeonIcon.KNOT, "내 시그니처", "주행 스타일과 차량 관리에 담긴 나를 알아봐요.", onSignature)
                SettingsRow(ChayeonIcon.EYE, "시그니처 공개 범위", "상대에게 보일 특성을 직접 골라요.", onVisibility)
                SettingsRow(ChayeonIcon.LOCK, "차량 데이터와 위치 사용", "어떤 정보를 읽고 사용하는지 확인해요.", { onExit(SignatureExit.OpenConsent) })
                SettingsRow(ChayeonIcon.LOCK, "집 보호 구역", "집 근처에서는 잠시 발견을 쉬어요.", { onExit(SignatureExit.OpenHomeZone) })
                SettingsRow(ChayeonIcon.PEOPLE, "차단과 신고", "차단한 사람을 확인하고 관리해요.", { onExit(SignatureExit.OpenBlocks) })
                SettingsRow(ChayeonIcon.HISTORY, "기록과 계정 정리", "무엇이 지워지고 무엇이 남는지 확인해요.", { onExit(SignatureExit.OpenCleanup) })
                GlassCard(Modifier.fillMaxWidth()) {
                    InfoRow(ChayeonIcon.LOCK, "차량 정보는 보이지 않아요",
                        body = "사진과 차량 정보는 포함하지 않아요. 연락처 ID는 서로 애프터를 수락한 뒤에만 공유해요.")
                }
                if (LocalSourceLabels.current) ChayeonText("시연용 로그인과 연령 확인이에요. 실제 인증은 아직 제공하지 않아요.",
                    style = ChayeonType.Minimum, color = ChayeonColors.Secondary)
            }
        },
    )
}
