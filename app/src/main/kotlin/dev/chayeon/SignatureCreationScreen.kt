package dev.chayeon

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import dev.chayeon.core.model.*
import dev.chayeon.core.ui.*

@Composable
fun SignatureCreationScreen(state: DemoUiState, onChoose: (DriveScenario) -> Unit, onBack: () -> Unit) {
    val allowed = state.driving.policy == DrivingPolicy.ALLOWED
    BackHandler { if (allowed && !state.busy) onBack() }
    ScreenScaffold(topBar = { BackButton(onBack, enabled = allowed && !state.busy) }, primary = {
        if (!allowed) StateCard(if (state.driving.policy == DrivingPolicy.UNKNOWN)
            ExperienceState.DRIVING_UNKNOWN else ExperienceState.DRIVING_RESTRICTED)
        else BoxWithConstraints(Modifier.fillMaxWidth()) {
            val scroll = rememberScrollState()
            Column(Modifier.fillMaxWidth().then(if (constraints.hasBoundedHeight) Modifier.scrollEdgeFade(scroll).verticalScroll(scroll) else Modifier),
                verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Large)) {
                StatusChip("합성 주행", variant = StatusVariant.SYNTHETIC)
                SectionTitle("어떤 주행이 나를 닮았나요?", subtitle = "한 가지를 고르면 주행과 차량 관리가 나의 매듭이 돼요")
                signatureScenarios.forEach { choice ->
                    GlassCard(Modifier.fillMaxWidth().testTag("fill-${choice.name}").clickable(
                        enabled = !state.busy, role = Role.Button, onClick = { onChoose(choice) },
                    )) {
                        Row(horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Large)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Small)) {
                                ChayeonText(choice.creationTitle(), style = ChayeonType.Section)
                                ChayeonText(when (choice) {
                                    DriveScenario.CARE -> "타이어와 에너지를 미리 챙기고, 함께 탈 공간을 돌봐요."
                                    DriveScenario.GENTLE -> "편안하게 달리고 여유롭게 멈춰요. 떠나기 전 타이어도 살펴요."
                                    else -> "방향을 미리 알려 주고, 에너지와 실내 공기를 챙겨요."
                                }, style = ChayeonType.Secondary, color = ChayeonColors.Secondary)
                            }
                            ChayeonIcon(ChayeonIcon.FORWARD, Modifier.size(ChayeonDimensions.UtilityIcon))
                        }
                    }
                }
                ChayeonText(if (state.busy && state.fillProgress != null) "합성 주행 ${state.fillProgress}/3"
                    else "합성 주행 3번을 모아요. 완성된 시그니처는 나에게 먼저 보여요.",
                    Modifier.testTag("demo-progress").semantics { liveRegion = LiveRegionMode.Polite },
                    ChayeonType.Section, ChayeonColors.Pink)
                state.message?.let { ChayeonText(it, color = ChayeonColors.Secondary) }
            }
        }
    })
}

private fun DriveScenario.creationTitle(): String = when (this) {
    DriveScenario.CARE -> "떠나기 전에 챙기는 사람"
    DriveScenario.GENTLE -> "편안하게 달리는 사람"
    else -> "다음 움직임을 알려 주는 사람"
}
