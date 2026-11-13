package dev.chayeon.feature.signature

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.chayeon.core.model.*
import dev.chayeon.core.ui.*

@Composable
fun MySignatureScreen(
    state: SignatureUiState,
    chrome: ScreenChrome,
    onBack: () -> Unit,
    onVisibility: () -> Unit,
    onTrait: (TraitId) -> Unit,
    onDemo: () -> Unit,
    onRetry: () -> Unit,
) {
    SignatureFrame("내 시그니처", state.driving.policy, chrome, onBack,
        primary = {
            when (val loaded = state.signature) {
                is LoadState.Loading -> SignatureMessage("주행의 이야기를 모으고 있어요", "매듭과 특성을 불러오고 있어요.")
                is LoadState.Failed -> SignatureFailure(loaded.error, "시그니처를 불러오지 못했어요", onRetry)
                is LoadState.Ready -> {
                    val signature = loaded.value
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val bounded = constraints.hasBoundedHeight
                        val knotSize = if (bounded) minOf(ChayeonDimensions.ChemistryKnot, maxHeight * .55f) else ChayeonDimensions.ChemistryKnot
                        HeroPanel(Modifier.fillMaxWidth().then(if (bounded) Modifier.fillMaxHeight() else Modifier),
                            contentPadding = PaddingValues(ChayeonSpacing.Screen)) {
                            val scroll = rememberScrollState()
                            Column(Modifier.fillMaxWidth().then(if (bounded) Modifier.fillMaxHeight().scrollEdgeFade(scroll).verticalScroll(scroll) else Modifier),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Large, Alignment.CenterVertically)) {
                                ChayeonText("주행으로 쓰는 나의 한 문장", style = ChayeonType.Label, color = ChayeonColors.Pink)
                                SignatureKnot(signature.knotSeed, Modifier.size(knotSize), reveal = false)
                                GradientText(signature.displayTitle, style = ChayeonType.Title)
                                SignatureSource(signature.displaySource)
                                JellyButton("공개 범위", onVisibility, Modifier.fillMaxWidth(), JellyVariant.SECONDARY)
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
                    if (signature.isRecording) {
                        ProfileBuilding(signature, state.recordingEnabled)
                        SignatureRecording(state.consoleAvailable, onDemo)
                    } else ProfileTraitRows(signature, onTrait)
                    InfoRow(ChayeonIcon.LOCK, "내 기록은 나에게만 보여요",
                        body = "상대에게는 내가 고른 특성만 보여요. 공개한 특성의 점수만 공유해요. 주행 기록과 차량 정보는 공유하지 않아요.")
                }
            }
        },
    )
}
