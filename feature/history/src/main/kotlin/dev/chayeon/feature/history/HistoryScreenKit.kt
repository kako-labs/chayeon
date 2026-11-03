package dev.chayeon.feature.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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
    primary: @Composable () -> Unit,
    secondary: (@Composable () -> Unit)? = null,
) {
    ScreenScaffold(
        modifier = Modifier.testTag("screen"),
        topBar = {
            chrome.topBar()
            if (state.driving.policy == DrivingPolicy.ALLOWED) {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Compact)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.ChipHorizontal)) {
                        if (onBack != null) BackButton(onBack)
                        if (title == "차연") AppHeader(Modifier.widthIn(max = ChayeonDimensions.ChemistryKnot)) else Column {
                            GradientText(title, style = ChayeonType.Title)
                            ChayeonText(subtitle, style = ChayeonType.Secondary, color = ChayeonColors.Secondary)
                        }
                    }
                    if (step != null) StepIndicator(step)
                }
            }
        },
        bottomBar = { if (tab != null) chrome.bottomBar(tab) },
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
                        Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap)) { primary(); secondary?.invoke() }
                    } else {
                        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap)) {
                            ScrollPane(Modifier.weight(ratio)) { primary() }
                            if (secondary != null) ScrollPane(Modifier.weight(1f)) { secondary() }
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun ScrollPane(modifier: Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap)) { content() }
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
    ConfirmSheet(confirmation.title, confirmation.body + (state.command(confirmation.commandKey).error?.let { "\n" + it.copy() } ?: ""), "확인하고 진행", onConfirm, onDismiss,
        busy = state.command(confirmation.commandKey).running)
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
