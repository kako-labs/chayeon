package dev.chayeon.feature.signature

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import dev.chayeon.core.model.*
import dev.chayeon.core.signature.DefaultSignatureEngine
import dev.chayeon.core.signature.SignatureLanguage
import dev.chayeon.core.ui.*

internal fun firstSignatureTraits(changes: List<TraitChange>, title: String): List<TraitChange> {
    val earned = changes.filter { it.currentLevel != TraitLevel.RECORDING }
        .sortedWith(compareByDescending<TraitChange> { it.currentLevel.ordinal }.thenBy { it.trait.ordinal })
    val titleTrait = earned.firstOrNull { it.trait == SignatureLanguage.titleTrait(title) }
    val care = earned.firstOrNull { it.trait.presentation.category == TraitCategory.CARE }
    return (listOfNotNull(titleTrait, care) + earned).distinctBy { it.trait }.take(3)
}

@Composable
internal fun FirstSignatureMoment(
    state: TripRecapUiState,
    recap: TripRecap,
    chrome: ScreenChrome,
    onExclude: () -> Unit,
    onSignature: () -> Unit,
    onVisibility: () -> Unit,
    onRetry: () -> Unit,
) {
    val earned = remember(recap.id, recap.changes) {
        recap.changes.filter { it.currentLevel != TraitLevel.RECORDING }
            .sortedByDescending { it.currentLevel.ordinal }
    }
    val title = remember(earned) { DefaultSignatureEngine().title(earned.map { PublicTrait(it.trait, it.currentLevel) }) }
    val traits = remember(earned, title) { firstSignatureTraits(earned, title) }
    val allowed = state.driving.policy == DrivingPolicy.ALLOWED && LocalDrivingPolicy.current == DrivingPolicy.ALLOWED
    val motion = LocalDecorativeMotion.current && allowed
    val timeline = remember(recap.id) { Animatable(0f) }
    val step = ChayeonMotion.Press * 2
    val end = ChayeonMotion.Reveal + step * (traits.size + 2)
    LaunchedEffect(recap.id, motion) {
        if (motion) {
            timeline.snapTo(0f)
            timeline.animateTo(end.toFloat(), tween(end, easing = LinearEasing))
        } else timeline.snapTo(end.toFloat())
    }
    fun fraction(index: Int) = if (!motion) 1f else ((timeline.value - ChayeonMotion.Reveal - index * step) / step).coerceIn(0f, 1f)
    val busy = state.excluding || state.markingSeen || state.pendingChanges
    ScreenScaffold(Modifier.testTag("signature-screen").then(if (!allowed) Modifier.clearAndSetSemantics {
        contentDescription = "주차하면 첫 시그니처를 이어서 볼 수 있어요."
        disabled()
    } else Modifier).onPreviewKeyEvent { !allowed }.pointerInput(allowed) {
        if (!allowed) awaitPointerEventScope {
            while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }, showSourceLabels = chrome.showSourceLabels, topBar = chrome.topBar, primary = {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= ChayeonSpacing.NarrowBreakpoint && constraints.hasBoundedHeight
            val scroll = rememberScrollState()
            Column(Modifier.fillMaxSize().then(if (!wide && constraints.hasBoundedHeight) Modifier.scrollEdgeFade(scroll).verticalScroll(scroll, enabled = allowed) else Modifier),
                verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Large)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium)) {
                    ChayeonText("나에게 먼저 도착한 시그니처", Modifier.weight(1f), ChayeonType.Label, ChayeonColors.Secondary)
                    SignatureSource(recap.source)
                }
                val knot: @Composable () -> Unit = {
                    Box(Modifier.fillMaxWidth().background(Brush.radialGradient(
                        listOf(ChayeonColors.Wine.copy(alpha = .65f), ChayeonColors.Transparent))), contentAlignment = Alignment.Center) {
                        SignatureKnot(recap.currentKnot, Modifier.size(ChayeonDimensions.ChemistryKnot).testTag("recap-knot"),
                            reveal = allowed, contentDescription = if (LocalSourceLabels.current && recap.source == DataSource.SYNTHETIC) "합성 주행으로 만든 첫 붉은 시그니처 매듭" else "내 주행으로 만든 첫 붉은 시그니처 매듭")
                    }
                }
                val story: @Composable () -> Unit = {
                    SignatureColumn(enabled = allowed) {
                        MomentPart(fraction(0)) {
                            ChayeonText(if (recap.source == DataSource.SYNTHETIC) "이 주행이 매듭이 됐어요" else "내 주행이 매듭이 됐어요", style = ChayeonType.Section, color = ChayeonColors.Pink)
                            Spacer(Modifier.height(ChayeonSpacing.Medium))
                            GradientText(title, Modifier.testTag("first-signature-title"), ChayeonType.Hero)
                        }
                        traits.forEachIndexed { index, trait ->
                            MomentPart(fraction(index + 1)) {
                                Row(Modifier.fillMaxWidth().padding(vertical = ChayeonSpacing.Small),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium)) {
                                    IconOrb(trait.trait.presentation.icon, Modifier.size(ChayeonSpacing.Touch), OrbVariant.GLASS)
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Tiny)) {
                                        ChayeonText(trait.trait.presentation.name, style = ChayeonType.Label, color = ChayeonColors.Pink)
                                        ChayeonText(trait.trait.presentation.publicPhrase, style = ChayeonType.Body)
                                    }
                                }
                            }
                        }
                    }
                }
                if (wide) {
                    Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Hero)) {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { knot() }
                        Box(Modifier.weight(1.25f)) { story() }
                    }
                } else {
                    knot()
                    story()
                }
                state.error?.let { SignatureFailure(it, "주행 요약을 반영하지 못했어요", onRetry) }
                MomentPart(fraction(traits.size + 1)) {
                    if (wide) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium)) {
                        JellyButton("상대에게 어떻게 보일지 고르기", onVisibility,
                            Modifier.weight(1.5f).testTag("recap-open-visibility"), enabled = allowed && !busy && fraction(traits.size + 1) > 0f)
                        JellyButton("내 시그니처 보기", onSignature, Modifier.weight(1f).testTag("recap-open-signature"),
                            JellyVariant.SECONDARY, enabled = allowed && !busy && fraction(traits.size + 1) > 0f)
                    } else Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap)) {
                        JellyButton("상대에게 어떻게 보일지 고르기", onVisibility,
                            Modifier.fillMaxWidth().testTag("recap-open-visibility"), enabled = allowed && !busy && fraction(traits.size + 1) > 0f)
                        JellyButton("내 시그니처 보기", onSignature, Modifier.fillMaxWidth().testTag("recap-open-signature"),
                            JellyVariant.SECONDARY, enabled = allowed && !busy && fraction(traits.size + 1) > 0f)
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Large)) {
                    ChayeonText("공개는 내가 골라요. 내 주행 수치는 나에게만 보여요.", Modifier.weight(1f), ChayeonType.Minimum, ChayeonColors.Secondary)
                    if (recap.source == DataSource.VEHICLE) JellyButton(if (state.excluding) "주행을 빼고 있어요" else "내가 운전하지 않았어요", onExclude,
                        Modifier.testTag("exclude-trip"), JellyVariant.SECONDARY, enabled = allowed && !state.excluding && !state.markingSeen)
                }
            }
        }
    })
}

@Composable
private fun MomentPart(fraction: Float, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().alpha(fraction).then(if (fraction == 0f) Modifier.clearAndSetSemantics {} else Modifier), content = content)
}
