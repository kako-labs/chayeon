package dev.chayeon.feature.signature

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.chayeon.core.model.*
import dev.chayeon.core.ui.*

@Composable
fun SignatureVisibilityScreen(
    state: VisibilityUiState,
    chrome: ScreenChrome,
    onBack: () -> Unit,
    onTraitVisible: (TraitId, Boolean) -> Unit,
    onPublished: (Boolean) -> Unit,
    onRetry: () -> Unit,
) {
    SignatureFrame("공개 범위", state.driving.policy, chrome, onBack,
        backEnabled = !state.savingVisibility && !state.publishing,
        primary = {
            SignatureColumn {
                when (val loaded = state.signature) {
                    is LoadState.Loading -> SignatureMessage("공개 설정을 불러오고 있어요", "내가 고른 특성을 확인하고 있어요.")
                    is LoadState.Failed -> SignatureFailure(loaded.error, "공개 설정을 불러오지 못했어요", onRetry)
                    is LoadState.Ready -> {
                        val visibility = state.visibility ?: loaded.value.visibility
                        GlassCard(Modifier.fillMaxWidth()) {
                            GelToggle(visibility.publishSignature, onPublished, "시그니처 공개",
                                Modifier.fillMaxWidth().testTag("publish-toggle"), enabled = !state.savingVisibility && !state.publishing)
                            ChayeonText(if (state.savingVisibility || state.publishing) "공개 내용을 반영하고 있어요."
                                else if (!state.publicationKnown) "현재 공개 상태를 확인하고 있어요."
                                else if (state.pendingChanges) "선택한 내용을 아직 모두 반영하지 못했어요."
                                else if (state.published) "고른 특성이 상대에게 보여요." else "시그니처를 공개하지 않아요.",
                                style = ChayeonType.Secondary, color = ChayeonColors.Secondary)
                        }
                        state.error?.let { SignatureFailure(it, "공개 내용을 반영하지 못했어요", onRetry) }
                        if (state.pendingChanges && state.publicationKnown && state.error == null &&
                            !state.savingVisibility && !state.publishing) {
                            JellyButton("공개 내용 반영", onRetry, Modifier.fillMaxWidth(), JellyVariant.WHITE)
                        }
                        TraitCategory.entries.forEach { category ->
                            GlassCard(Modifier.fillMaxWidth()) {
                                Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium)) {
                                    SectionTitle(category.label)
                                    TraitId.entries.filter { it.presentation.category == category }.forEach { trait ->
                                        val evidence = loaded.value.evidence.firstOrNull { it.trait == trait }
                                        Column {
                                            GelToggle(evidence?.isPublishable == true && trait in visibility.visibleTraits, { onTraitVisible(trait, it) },
                                                trait.presentation.name, Modifier.fillMaxWidth().testTag("visibility-${trait.name}"),
                                                enabled = !state.savingVisibility && !state.publishing && evidence?.isPublishable == true)
                                            when {
                                                evidence?.availability == TraitAvailability.UNSUPPORTED ->
                                                    ChayeonText(evidence.unsupportedLabel(LocalSourceLabels.current), style = ChayeonType.Minimum, color = ChayeonColors.Secondary)
                                                evidence == null || evidence.level == TraitLevel.RECORDING ->
                                                    ChayeonText("기록 중이에요. 근거가 모이기 전에는 공개하지 않아요.", style = ChayeonType.Minimum, color = ChayeonColors.Secondary)
                                            }
                                            if (trait == TraitId.NIGHT_DRIVE) ChayeonText(
                                                "생활 시간이 드러날 수 있어 처음에는 꺼 두었어요. 원할 때만 공개해요.",
                                                style = ChayeonType.Minimum, color = ChayeonColors.Secondary)
                                        }
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
                HeroPanel(Modifier.fillMaxWidth().testTag("public-preview"), contentPadding = PaddingValues(ChayeonSpacing.Screen)) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium)) {
                        SectionTitle("상대에게 이렇게 보여요", subtitle = "내가 고른 공개 내용의 미리보기예요")
                        val preview = state.preview
                        if (state.signature !is LoadState.Ready) {
                            IconOrb(ChayeonIcon.EYE, Modifier.padding(vertical = ChayeonSpacing.Hero), OrbVariant.GLASS)
                            ChayeonText("공개 내용을 확인하고 있어요", style = ChayeonType.Section)
                            ChayeonText("설정을 불러오면 상대에게 보일 내용을 살펴볼 수 있어요.", color = ChayeonColors.Secondary)
                        } else if (preview == null) {
                            IconOrb(ChayeonIcon.LOCK, Modifier.padding(vertical = ChayeonSpacing.Hero), OrbVariant.GLASS)
                            GradientText("나만 간직해요", style = ChayeonType.Title)
                            ChayeonText("상대에게는 시그니처가 보이지 않아요.", color = ChayeonColors.Secondary)
                        } else {
                            SignatureKnot(preview.knotSeed, Modifier.size(ChayeonDimensions.Avatar), reveal = false)
                            GradientText(preview.title, style = ChayeonType.Section)
                            SignatureSource(preview.source)
                            if (preview.traits.isEmpty()) ChayeonText("공개할 특성을 모으고 있어요.", color = ChayeonColors.Secondary)
                            preview.traits.forEach { trait ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium)) {
                                    ChayeonIcon(trait.trait.presentation.icon, Modifier.size(ChayeonDimensions.UtilityIcon), ChayeonColors.Pink)
                                    Column(Modifier.weight(1f)) {
                                        ChayeonText(trait.trait.presentation.name, style = ChayeonType.Label, color = ChayeonColors.Pink)
                                        ChayeonText(trait.trait.presentation.publicPhrase, style = ChayeonType.Secondary)
                                    }
                                }
                            }
                        }
                    }
                }
                if (state.signature is LoadState.Ready && (state.pendingChanges || state.error != null)) TopBanner("아직 반영되지 않은 내용이 있어요. 다시 시도해 주세요.", icon = ChayeonIcon.REFRESH)
                GlassCard(Modifier.fillMaxWidth()) {
                    InfoRow(ChayeonIcon.LOCK, "나의 수치는 공유하지 않아요",
                        body = "읽은 신호와 개인 메모는 특성 상세에서 나만 볼 수 있어요. 위치와 차량 정보도 보이지 않아요.")
                }
            }
        },
    )
}
