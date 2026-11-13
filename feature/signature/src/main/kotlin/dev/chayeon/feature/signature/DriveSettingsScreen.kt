package dev.chayeon.feature.signature

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.chayeon.core.model.*
import dev.chayeon.core.ui.*

internal val DrivingConnectMode.label: String get() = when (this) {
    DrivingConnectMode.OFF -> "끄기"
    DrivingConnectMode.RING -> "전화처럼 받기"
    DrivingConnectMode.AUTO -> "조건에 맞으면 자동 연결"
}

@Composable
fun DriveSettingsScreen(
    state: MyProfileUiState,
    chrome: ScreenChrome,
    onBack: () -> Unit,
    onMode: (DrivingConnectMode) -> Unit,
    onMinimumScore: (TraitId, Int?) -> Unit,
    onSharedInterest: (Boolean) -> Unit,
    onRetry: () -> Unit,
) {
    val settings = state.settings.readyValue()
    val enabled = settings != null && !state.updatingCallSetting && state.driving.policy == DrivingPolicy.ALLOWED
    SignatureFrame("주행 중 연결", state.driving.policy, chrome, onBack, recap = true, ratio = .67f,
        primary = {
            SignatureColumn {
                SectionTitle("달리는 동안 어떻게 이어질까요?", subtitle = "설정은 주차 중에만 바꿀 수 있어요")
                DrivingConnectMode.entries.forEach { mode ->
                    DrivingModeCard(mode.label, settings?.drivingConnectMode == mode, { onMode(mode) }, enabled,
                        when (mode) {
                            DrivingConnectMode.OFF -> "새 사람은 주차했을 때 만나요. 진행 중인 통화는 그대로 이어져요."
                            DrivingConnectMode.RING -> "요청이 오면 닉네임, 칭호와 운전 점수를 보고 한 번 눌러 받아요."
                            DrivingConnectMode.AUTO -> "조건에 맞는 사람에게 요청을 보내고, 맞는 요청은 자동으로 받아요. 연결되면 차 안 음성이 알려 줘요."
                        })
                }
                ChayeonText("서로 설정이나 받기로 동의해야 연결돼요. 주차 중에는 직접 요청하고 받아요.", style = ChayeonType.Minimum, color = ChayeonColors.Secondary)
                ChayeonText("통화가 끝나면 10분 동안 쉬어요. 오늘 거절·취소·종료·만료된 사람과는 자동으로 다시 이어지지 않아요.", style = ChayeonType.Minimum, color = ChayeonColors.Secondary)
                ChayeonText("집 보호 구역과 차단한 사람은 언제나 제외해요.", style = ChayeonType.Minimum, color = ChayeonColors.Secondary)
            }
        },
        secondary = {
            SignatureColumn {
                when (val loaded = state.settings) {
                    is LoadState.Failed -> SignatureFailure(loaded.error, "설정을 불러오지 못했어요", onRetry)
                    is LoadState.Loading -> SignatureMessage("설정을 불러오고 있어요", "주행 중 연결 방법을 준비하고 있어요.")
                    is LoadState.Ready -> GlassPanel(Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Large)) {
                        SectionTitle("자동 연결 조건", subtitle = "상대가 공개한 점수로만 판단해요. 공개하지 않은 항목은 조건을 채우지 못해요.")
                        TraitId.entries.filter { it.isScored }.forEach { trait ->
                            val minimum = loaded.value.autoConnectConditions.minimumScores[trait]
                            Column(Modifier.fillMaxWidth()) {
                                Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Small)) {
                                    GelToggle(minimum != null, { onMinimumScore(trait, if (it) 60 else null) },
                                        "${trait.presentation.name} 조건 사용", enabled = enabled)
                                    if (minimum != null) {
                                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Small)) {
                                            SignatureButton("−5", { onMinimumScore(trait, minimum - 5) }, Modifier.width(ChayeonSpacing.Button).semantics { contentDescription = "${trait.presentation.name} 최소 점수 낮추기" },
                                                JellyVariant.SECONDARY, enabled = enabled && minimum > 60)
                                            ChayeonText("${minimum}점 이상", Modifier.weight(1f), ChayeonType.Label)
                                            SignatureButton("+5", { onMinimumScore(trait, minimum + 5) }, Modifier.width(ChayeonSpacing.Button).semantics { contentDescription = "${trait.presentation.name} 최소 점수 높이기" },
                                                JellyVariant.SECONDARY, enabled = enabled && minimum < 100)
                                        }
                                    } else ChayeonText("조건 없음", style = ChayeonType.Minimum, color = ChayeonColors.Secondary)
                                }
                            }
                        }
                        Column(Modifier.fillMaxWidth()) {
                            GelToggle(loaded.value.autoConnectConditions.requireSharedInterest, onSharedInterest,
                                "관심사가 하나 이상 겹치는 사람", enabled = enabled)
                        }
                        InfoRow(ChayeonIcon.PHONE, "이어지면 차 안 음성이 알려 줘요", body = "상대의 닉네임과 연결 사실만 알려 줘요. 통화 끊기 한 번으로 끝낼 수 있어요.")
                        InfoLine("설정은 주차 중에만 바꿀 수 있어요")
                        }
                    }
                }
                if (state.callSettingError != null) ChayeonText("설정을 바꾸지 못했어요. 다시 눌러 주세요", style = ChayeonType.Secondary, color = ChayeonColors.Pink)
            }
        })
}
