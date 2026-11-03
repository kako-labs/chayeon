package dev.chayeon.feature.signature

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.chayeon.core.model.*
import dev.chayeon.core.ui.*

internal fun <T> LoadState<T>.readyValue(): T? = (this as? LoadState.Ready)?.value

@Composable
internal fun SignatureFrame(
    title: String,
    policy: DrivingPolicy,
    chrome: ScreenChrome,
    onBack: (() -> Unit)? = null,
    recap: Boolean = false,
    backEnabled: Boolean = true,
    primary: @Composable () -> Unit,
    secondary: (@Composable () -> Unit)? = null,
) {
    val allowed = policy == DrivingPolicy.ALLOWED && LocalDrivingPolicy.current == DrivingPolicy.ALLOWED
    ScreenScaffold(
        modifier = Modifier.testTag("signature-screen"),
        topBar = {
            chrome.topBar()
            if (allowed) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.ChipHorizontal)) {
                    if (onBack != null) BackButton(onBack, enabled = backEnabled)
                    GradientText(title, Modifier.semantics { heading() }, ChayeonType.Title)
                }
            }
        },
        bottomBar = { if (!recap) chrome.bottomBar(NavigationItem.PROFILE) },
        primary = {
            if (allowed) primary() else SignatureLocked(
                if (policy == DrivingPolicy.RESTRICTED || LocalDrivingPolicy.current == DrivingPolicy.RESTRICTED)
                    DrivingPolicy.RESTRICTED else DrivingPolicy.UNKNOWN,
            )
        },
        secondary = if (allowed) secondary else null,
    )
}

@Composable
internal fun SignatureColumn(content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val scrolling = if (constraints.hasBoundedHeight) Modifier.verticalScroll(rememberScrollState()) else Modifier
        Column(Modifier.fillMaxWidth().then(scrolling),
            verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap), content = content)
    }
}

@Composable
internal fun SignatureLocked(policy: DrivingPolicy) {
    HeroPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(vertical = ChayeonSpacing.Hero),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Screen)) {
            IconOrb(ChayeonIcon.LOCK, variant = OrbVariant.GLASS)
            GradientText(if (policy == DrivingPolicy.RESTRICTED) "주행 중이에요" else "주행 상태를 확인하고 있어요",
                style = ChayeonType.Hero)
            ChayeonText("주차하면 내 시그니처를 다시 볼 수 있어요.", color = ChayeonColors.Secondary)
            ChayeonText("주행 중에는 기록과 공개 설정을 보여주지 않아요.", style = ChayeonType.Secondary,
                color = ChayeonColors.Secondary)
        }
    }
}

@Composable
internal fun SignatureMessage(
    title: String,
    body: String,
    icon: ChayeonIcon = ChayeonIcon.KNOT,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Large)) {
            IconOrb(icon, variant = OrbVariant.GLASS)
            SectionTitle(title, subtitle = body)
            if (action != null) JellyButton(action, onAction, Modifier.fillMaxWidth(), JellyVariant.SECONDARY)
        }
    }
}

@Composable
internal fun SignatureFailure(error: AppError, action: String, onRetry: () -> Unit) {
    SignatureMessage(action, error.signatureMessage("시도해"), ChayeonIcon.REFRESH, "다시 시도", onRetry)
}

@Composable
internal fun SignatureRecording(consoleAvailable: Boolean, onDemo: () -> Unit) {
    SignatureMessage(
        "시그니처를 만드는 중이에요",
        "최근 30일 동안 유효한 주행이 3회 모이면 특성이 보여요. 평소처럼 지내는 동안 천천히 알아갈게요.",
        action = if (consoleAvailable) "시연 제어" else null,
        onAction = onDemo,
    )
    if (consoleAvailable) ChayeonText("시연 제어에서 합성 주행을 채워 볼 수 있어요.",
        style = ChayeonType.Secondary, color = ChayeonColors.Secondary)
}

internal val MySignature.displaySource: DataSource?
    get() = if (evidence.any { it.source == DataSource.SYNTHETIC }) DataSource.SYNTHETIC
    else publicSignature?.source ?: evidence.firstOrNull()?.source

internal val MySignature.displayTitle: String
    get() = publicSignature?.title ?: evidence
        .filter { it.availability == TraitAvailability.AVAILABLE && it.level != TraitLevel.RECORDING }
        .maxByOrNull { it.level.ordinal }?.trait?.presentation?.publicPhrase ?: "시그니처를 만드는 중이에요"

internal val MySignature.isRecording: Boolean
    get() = evidence.none { it.availability == TraitAvailability.AVAILABLE && it.level != TraitLevel.RECORDING }

@Composable
internal fun SignatureSource(source: DataSource?) {
    if (source != null) StatusChip(source.label, variant = StatusVariant.SYNTHETIC)
}
