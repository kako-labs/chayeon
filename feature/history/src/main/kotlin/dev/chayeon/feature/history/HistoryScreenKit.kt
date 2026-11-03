package dev.chayeon.feature.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.chayeon.core.model.*
import dev.chayeon.core.ui.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HistoryFrame(
    state: HistoryUiState,
    chrome: ScreenChrome,
    title: String,
    subtitle: String,
    onBack: (() -> Unit)? = null,
    step: Int? = null,
    tab: NavigationItem? = null,
    ratio: Float = 1f,
    onLeave: (() -> Unit)? = null,
    headerPeer: PublicProfile? = null,
    bottom: (@Composable () -> Unit)? = null,
    secondaryFirstWhenNarrow: Boolean = false,
    equalPanelHeights: Boolean = false,
    primary: @Composable () -> Unit,
    secondary: (@Composable () -> Unit)? = null,
) {
    BackHandler(enabled = state.driving.policy != DrivingPolicy.ALLOWED) {}
    ScreenScaffold(
        modifier = Modifier.testTag("screen"),
        topBar = {
            chrome.topBar()
            if (state.driving.policy == DrivingPolicy.ALLOWED) {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Compact)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.ChipHorizontal)) {
                        if (onBack != null) BackButton(onBack)
                        if (headerPeer != null) AvatarDish(MochiAvatar.from(headerPeer.avatar), Modifier.size(ChayeonSpacing.Touch))
                        if (title == "차연") AppHeader(Modifier.widthIn(max = ChayeonDimensions.ChemistryKnot)) else Column {
                            GradientText(title, style = ChayeonType.Title)
                            ChayeonText(subtitle, style = ChayeonType.Secondary, color = ChayeonColors.Secondary)
                            if (headerPeer != null) ChayeonText(headerPeer.kind.label, style = ChayeonType.Minimum, color = ChayeonColors.Secondary)
                        }
                    }
                    if (step != null) StepIndicator(step)
                }
            }
        },
        bottomBar = {
            Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap)) {
                if (state.driving.policy == DrivingPolicy.ALLOWED) bottom?.invoke()
                if (tab != null) chrome.bottomBar(tab)
            }
        },
        primary = {
            if (state.driving.policy != DrivingPolicy.ALLOWED) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Large)) {
                    StateCard(if (state.driving.policy == DrivingPolicy.RESTRICTED) ExperienceState.DRIVING_RESTRICTED else ExperienceState.DRIVING_UNKNOWN,
                        Modifier.fillMaxWidth())
                    if (onLeave != null) JellyButton("나가기", onLeave, Modifier.fillMaxWidth(), JellyVariant.SECONDARY, allowWhenRestricted = true)
                }
            } else {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (maxWidth < ChayeonSpacing.NarrowBreakpoint - ChayeonSpacing.Screen * 2) {
                        Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap)) {
                            if (secondaryFirstWhenNarrow) { secondary?.invoke(); primary() }
                            else { primary(); secondary?.invoke() }
                        }
                    } else {
                        if (equalPanelHeights && secondary != null) {
                            val scroll = rememberScrollState()
                            Row(Modifier.fillMaxSize().scrollEdgeFade(scroll).verticalScroll(scroll).height(IntrinsicSize.Min),
                                horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap)) {
                                Box(Modifier.weight(ratio).fillMaxHeight()) { primary() }
                                Box(Modifier.weight(1f).fillMaxHeight()) { secondary() }
                            }
                        } else Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap)) {
                            ScrollPane(Modifier.weight(ratio)) { primary() }
                            if (secondary != null) ScrollPane(Modifier.weight(1f)) { secondary() }
                        }
                    }
                }
            }
        },
    )
}

internal val OnboardingPanelArrangement = object : Arrangement.Vertical {
    override val spacing = ChayeonSpacing.Large
    override fun Density.arrange(totalSize: Int, sizes: IntArray, outPositions: IntArray) {
        val minimumGap = spacing.roundToPx()
        val gap = if (sizes.size > 1) maxOf(minimumGap, (totalSize - sizes.sum()) / (sizes.size - 1)) else 0
        var position = 0
        sizes.forEachIndexed { index, size -> outPositions[index] = position; position += size + gap }
    }
}

internal val LocalHistoryPaneHeight = staticCompositionLocalOf { 0.dp }

@Composable
private fun ScrollPane(modifier: Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier.fillMaxHeight()) {
        val scroll = rememberScrollState()
        CompositionLocalProvider(LocalHistoryPaneHeight provides maxHeight) {
            Column(Modifier.fillMaxSize().scrollEdgeFade(scroll).verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap)) { content() }
        }
    }
}

@Composable
internal fun StretchPanels(content: @Composable () -> Unit) {
    val minimumHeight = LocalHistoryPaneHeight.current
    Layout(content, Modifier.fillMaxWidth()) { measurables, constraints ->
        val gap = ChayeonSpacing.Large.roundToPx()
        var used = 0
        val children = measurables.mapIndexed { index, measurable ->
            val remaining = if (index == measurables.lastIndex) (minimumHeight.roundToPx() - used).coerceAtLeast(0) else 0
            measurable.measure(constraints.copy(minHeight = remaining)).also { used += it.height + gap }
        }
        layout(constraints.maxWidth, (used - gap).coerceAtLeast(0)) {
            var y = 0
            children.forEach { child -> child.placeRelative(0, y); y += child.height + gap }
        }
    }
}

@Composable
internal fun OnboardingActions(label: String, enabled: Boolean, onContinue: () -> Unit, onBack: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().testTag("onboarding-actions"), horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap)) {
        if (onBack == null) Spacer(Modifier.weight(1f))
        else JellyButton("이전", onBack, Modifier.weight(1f), JellyVariant.SECONDARY)
        JellyButton(label, onContinue, Modifier.weight(2f).testTag("onboarding-primary"), enabled = enabled)
    }
}

@Composable
internal fun ContentColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Large), content = content)
}

@Composable
internal fun CommandFeedback(command: CommandState, onRetry: () -> Unit, success: String? = null) {
    if (command.running) ChayeonText("처리하고 있어요", Modifier.semantics { liveRegion = LiveRegionMode.Polite }, ChayeonType.Secondary)
    command.error?.let { error ->
        GlassCard(Modifier.fillMaxWidth()) {
            ContentColumn {
                ChayeonText(error.copy(), style = ChayeonType.Secondary)
                JellyButton("다시 시도", onRetry, Modifier.fillMaxWidth(), JellyVariant.SECONDARY)
            }
        }
    }
    if (command.completed && success != null) TopBanner(success, icon = ChayeonIcon.CHECK)
}

internal fun AppError.copy(): String = when (this) {
    AppError.NotAuthorized -> "로그인 상태를 확인할 수 없어요. 다시 로그인해 주세요."
    AppError.AlreadyClosed -> "이미 끝난 요청이나 관계예요. 목록에서 현재 상태를 확인해 주세요."
    AppError.Expired -> "응답 기한이 지났어요. 스친 기록에서 신청 가능 여부를 확인해 주세요."
    AppError.PeerUnavailable -> "지금은 이 사람과 이어갈 수 없어요. 다른 인연을 살펴봐 주세요."
    AppError.Restricted -> "주차 상태에서만 할 수 있어요. 주차한 뒤 다시 시도해 주세요."
    AppError.Network -> "연결이 끊겨 처리 결과를 확인하지 못했어요. 연결을 확인하고 다시 시도해 주세요."
    AppError.Unknown -> "처리 결과를 확인하지 못했어요. 잠시 뒤 다시 시도해 주세요."
}

@Composable
internal fun <T> Loaded(value: LoadState<T>, emptyTitle: String = "아직 기록이 없어요", content: @Composable (T) -> Unit) {
    when (value) {
        is LoadState.Loading -> GlassCard(Modifier.fillMaxWidth()) {
            InfoRow(ChayeonIcon.CLOCK, "불러오고 있어요", body = "잠시만 기다려 주세요")
        }
        is LoadState.Failed -> if (value.error == AppError.Network) StateCard(ExperienceState.NETWORK_FAILURE, Modifier.fillMaxWidth())
            else GlassCard(Modifier.fillMaxWidth()) { InfoRow(ChayeonIcon.REFRESH, "불러오지 못했어요", body = value.error.copy()) }
        is LoadState.Ready -> if (value.value == null || ((value.value as? Collection<*>)?.isEmpty() == true)) {
            EmptyCard(emptyTitle, "새 소식이 생기면 여기에 보여요")
        } else content(value.value)
    }
}

@Composable
internal fun EmptyCard(title: String, body: String) {
    GlassCard(Modifier.fillMaxWidth()) { ContentColumn {
        IconOrb(ChayeonIcon.THREAD, variant = OrbVariant.GLASS)
        SectionTitle(title, subtitle = body)
    } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProfilePresentation(profile: PublicProfile, large: Boolean = true) {
    ContentColumn {
        PeerHeader(profile, size = if (large) PeerHeaderSize.LARGE else PeerHeaderSize.SMALL)
        profile.signature?.let { signature ->
            SignatureMini(signature)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.TagGap), verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.TagGap)) {
                signature.traits.forEach { TraitBadge(it.trait, it.level) }
            }
        } ?: ChayeonText("시그니처를 만드는 중이에요", style = ChayeonType.Secondary)
        InterestRow(profile.interests)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InterestRow(interests: Set<InterestId>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.TagGap), verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.TagGap)) {
        interests.sortedBy { it.ordinal }.forEach { InterestTag(it.label) }
    }
}

@Composable
internal fun PeerChemistry(chemistry: Chemistry, mine: PublicProfile?, peer: PublicProfile) {
    val first = mine?.signature
    val second = peer.signature
    if (first != null && second != null) ChemistryCard(chemistry, first, second, Modifier.fillMaxWidth())
    else if (chemistry.lines.isNotEmpty()) GlassCard(Modifier.fillMaxWidth()) {
        ContentColumn {
            SectionTitle("함께하면 잘 맞는 점")
            chemistry.lines.forEach { InfoRow(ChayeonIcon.KNOT, it.text) }
        }
    }
}

@Composable
internal fun HistoryConfirm(state: HistoryUiState, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val confirmation = state.confirmation ?: return
    if (state.driving.policy != DrivingPolicy.ALLOWED) return
    val busy = state.command(confirmation.commandKey).running
    val body = confirmation.body + (state.command(confirmation.commandKey).error?.let { "\n" + it.copy() } ?: "")
    val secondaryAction = confirmation.kind in setOf(ConfirmationKind.DISCOVERY_OFF, ConfirmationKind.SIGN_OUT, ConfirmationKind.UNBLOCK)
    if (!secondaryAction) {
        ConfirmSheet(confirmation.title, body, confirmation.actionLabel, onConfirm, onDismiss, busy = busy)
        return
    }
    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(
        usePlatformDefaultWidth = false, dismissOnClickOutside = false, dismissOnBackPress = !busy,
    )) {
        Box(Modifier.widthIn(max = ChayeonDimensions.ConfirmWidth).padding(ChayeonSpacing.Large)
            .semantics { paneTitle = confirmation.title }) {
            HeroPanel {
                val scroll = rememberScrollState()
                Column(Modifier.scrollEdgeFade(scroll).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Large)) {
                    IconOrb(ChayeonIcon.WARNING, variant = OrbVariant.WHITE)
                    SectionTitle(confirmation.title, subtitle = body)
                    JellyButton(if (busy) "처리하고 있어요" else confirmation.actionLabel, onConfirm,
                        Modifier.fillMaxWidth(), JellyVariant.SECONDARY, enabled = !busy)
                    JellyButton("돌아가기", onDismiss, Modifier.fillMaxWidth(), JellyVariant.SECONDARY, enabled = !busy)
                }
            }
        }
    }
}

internal fun encounterTime(epoch: Long): String = DateTimeFormatter.ofPattern("M월 d일 a h:mm", Locale.KOREAN)
    .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(epoch))
internal fun encounterDuration(encounter: Encounter): String {
    val minutes = ((encounter.endedAtEpochMillis - encounter.startedAtEpochMillis).coerceAtLeast(0) / 60_000)
    return if (minutes == 0L) "1분 미만" else "${minutes}분"
}
internal val AfterEligibility.label: String get() = when (this) {
    AfterEligibility.AVAILABLE -> "애프터 신청 가능"
    AfterEligibility.PENDING -> "응답을 기다리고 있어요"
    AfterEligibility.CONNECTED -> "이어진 인연"
    AfterEligibility.COOLDOWN -> "잠시 쉬어가요"
    AfterEligibility.UNAVAILABLE -> "지금은 신청할 수 없어요"
}
