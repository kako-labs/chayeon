package dev.chayeon

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.chayeon.core.model.*
import dev.chayeon.core.ui.*
import java.util.UUID
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

const val DEMO_ROUTE = "app/demo"
const val CREATE_SIGNATURE_ROUTE = "app/create-signature"

data class DemoUiState(val driving: DrivingState = DrivingState(), val console: ConsoleState = ConsoleState(),
    val busy: Boolean = false, val message: String? = null,
    val fillProgress: Int? = null, val completedRecapId: String? = null)

class DemoConsoleViewModel(private val graph: AppGraph) : ViewModel() {
    private val busy = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private data class FillState(val progress: Int? = null, val recapId: String? = null)
    private val fillState = MutableStateFlow(FillState())
    val uiState = combine(graph.drivingStateProvider.drivingState, graph.vehicleConsole.consoleState, busy, message, fillState) { driving, console, working, text, fill ->
        DemoUiState(driving, console, working, text, fill.progress, fill.recapId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000, 0), DemoUiState())

    private fun meta() = CommandMeta(UUID.randomUUID().toString())
    private fun command(block: suspend () -> Boolean) {
        if (!graph.demoEnabled || busy.value || graph.currentPolicy() != DrivingPolicy.ALLOWED) return
        busy.value = true
        message.value = null
        viewModelScope.launch {
            try { message.value = if (block()) "시연 내용을 반영했어요" else "반영하지 못했어요. 주차 상태와 차량 데이터 동의를 확인해 주세요" }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { message.value = "반영하지 못했어요. 주차 후 다시 시도해 주세요" }
            finally { busy.value = false }
        }
    }
    fun gear(value: Gear) = command { graph.vehicleConsole.setGear(SetGearRequest(meta(), value)) is AppResult.Success }
    fun presence(value: SyntheticPresence) = command {
        graph.currentPolicy() == DrivingPolicy.ALLOWED &&
            graph.homeZoneCoordinator.setDesiredPresence(value, graph.currentPolicy) is AppResult.Success &&
            confirmPresenceForUi(graph.homeZoneCoordinator) is AppResult.Success
    }
    fun homePosition(inside: Boolean) = command {
        graph.currentPolicy() == DrivingPolicy.ALLOWED && graph.syntheticHomePosition(inside) is AppResult.Success
    }
    fun replay(scenario: DriveScenario) = command { runReplay(scenario) }
    private suspend fun runReplay(scenario: DriveScenario): Boolean {
        if (graph.currentPolicy() != DrivingPolicy.ALLOWED) return false
        if (graph.vehicleConsole.replay(ReplayDriveRequest(meta(), scenario)) !is AppResult.Success) return false
        return withTimeoutOrNull(30_000) {
            graph.vehicleConsole.consoleState.first { it.status != ReplayStatus.PLAYING }.status == ReplayStatus.FINISHED
        } == true
    }
    fun fill(scenario: DriveScenario) {
        if (scenario !in signatureScenarios) return
        command {
            fillState.value = FillState(progress = 0)
            val previous = (graph.signatureRepository.recaps.first() as? LoadState.Ready)?.value ?: return@command false
            // A new demo choice replaces synthetic evidence only; real drives stay included.
            for (recap in previous.filter { it.source == DataSource.SYNTHETIC }) {
                if (graph.currentPolicy() != DrivingPolicy.ALLOWED) return@command false
                if (recap.included && graph.signatureRepository.excludeTrip(ExcludeTripRequest(meta(), recap.tripId)) !is AppResult.Success) return@command false
                if (!recap.seen && graph.signatureRepository.markRecapSeen(MarkRecapSeenRequest(meta(), recap.id)) !is AppResult.Success) return@command false
            }
            var knownIds = previous.map { it.id }.toSet()
            repeat(3) { index ->
                fillState.value = FillState(progress = index + 1)
                if (!runReplay(scenario)) return@command false
                if (graph.currentPolicy() != DrivingPolicy.ALLOWED) return@command false
                val recaps = (graph.signatureRepository.recaps.first() as? LoadState.Ready)?.value ?: return@command false
                val latest = recaps.firstOrNull { it.id !in knownIds && it.source == DataSource.SYNTHETIC && it.included } ?: return@command false
                knownIds = recaps.map { it.id }.toSet()
                if (index < 2) {
                    if (graph.signatureRepository.markRecapSeen(MarkRecapSeenRequest(meta(), latest.id)) !is AppResult.Success) return@command false
                } else fillState.value = FillState(progress = 3, recapId = latest.id)
            }
            true
        }
    }

    fun consumeRecap() { fillState.value = FillState() }

}

internal val signatureScenarios = listOf(DriveScenario.CARE, DriveScenario.GENTLE, DriveScenario.SIGNAL_MANNERS)

private fun DriveScenario.demoLabel() = when (this) {
    DriveScenario.GENTLE -> "편안하게 달리는 사람"
    DriveScenario.SIGNAL_MANNERS -> "다음 움직임을 알려 주는 사람"
    DriveScenario.CARE -> "떠나기 전에 챙기는 사람"
    DriveScenario.EARLY_BRAKE -> "여유로운 제동"
    DriveScenario.UNSUPPORTED -> "지원하지 않는 신호"
}

@Composable
fun DemoConsoleScreen(state: DemoUiState, chrome: ScreenChrome, realVehicle: Boolean, enabled: Boolean,
    onGear: (Gear) -> Unit, onReplay: (DriveScenario) -> Unit, onFill: (DriveScenario) -> Unit,
    onPresence: (SyntheticPresence) -> Unit, onBack: () -> Unit, returnToConnection: Boolean = false, onCreate: () -> Unit = {},
    syntheticHomeSet: Boolean = false, onHomePosition: (Boolean) -> Unit = {}) {
    val allowed = state.driving.policy == DrivingPolicy.ALLOWED
    BackHandler { if (allowed && !state.busy) onBack() }
    ScreenScaffold(topBar = {
        chrome.topBar()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium)) {
            BackButton(onBack, enabled = allowed && !state.busy)
            GradientText("시연 제어", style = ChayeonType.Title)
        }
    }, bottomBar = {
        CompositionLocalProvider(LocalDrivingPolicy provides if (state.busy) DrivingPolicy.UNKNOWN else state.driving.policy) {
            chrome.bottomBar(NavigationItem.PROFILE)
        }
    }, primary = {
        DemoColumn {
            if (!allowed) {
                StateCard(if (state.driving.policy == DrivingPolicy.UNKNOWN) ExperienceState.DRIVING_UNKNOWN else ExperienceState.DRIVING_RESTRICTED)
            } else if (!enabled) {
                ChayeonText("이 앱에서는 시연 제어를 제공하지 않아요")
                JellyButton("돌아가기", onBack)
            } else {
                GlassCard(Modifier.fillMaxWidth()) {
                    SectionTitle("합성 주행", subtitle = "주행을 더하거나 제공되지 않는 특성을 살펴봐요")
                    JellyButton("시그니처 만들기", onCreate, Modifier.fillMaxWidth(), enabled = !state.busy)
                    state.message?.let { ChayeonText(it, style = ChayeonType.Secondary) }
                }
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium)) {
                        SectionTitle("한 번씩 더 살펴봐요")
                        Row(horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Compact)) {
                            listOf(DriveScenario.EARLY_BRAKE, DriveScenario.UNSUPPORTED).forEach { scenario ->
                                JellyButton(scenario.demoLabel(), { onReplay(scenario) }, Modifier.weight(1f), JellyVariant.SECONDARY, enabled = !state.busy)
                            }
                        }
                    }
                }
                JellyButton(if (returnToConnection) "연결 화면으로 돌아가기" else "내 프로필로 돌아가기", onBack,
                    Modifier.fillMaxWidth(), JellyVariant.SECONDARY, enabled = !state.busy)
            }
        }
    }, secondary = if (allowed && enabled) {{
        DemoColumn {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium)) {
                    SectionTitle("합성 위치", subtitle = "가상 상대와의 이탈·복귀를 살펴봐요")
                    SyntheticPresence.entries.forEach { presence ->
                        JellyButton(when (presence) {
                            SyntheticPresence.NEARBY -> "가까이 있어요"
                            SyntheticPresence.AWAY -> "서로 멀어져요"
                            SyntheticPresence.STALE -> "위치를 확인할 수 없어요"
                            SyntheticPresence.OFFLINE -> "연결이 끊어져요"
                        }, { onPresence(presence) }, Modifier.fillMaxWidth(), JellyVariant.SECONDARY, enabled = !state.busy)
                    }
                    if (syntheticHomeSet) {
                        SectionTitle("집 보호 구역 · 합성 위치")
                        JellyButton("집 근처로 오기", { onHomePosition(true) }, Modifier.fillMaxWidth(), JellyVariant.SECONDARY, enabled = !state.busy)
                        JellyButton("집에서 멀어지기", { onHomePosition(false) }, Modifier.fillMaxWidth(), JellyVariant.SECONDARY, enabled = !state.busy)
                    }
                }
            }
            if (!realVehicle) GlassCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium)) {
                    SectionTitle("기어 제어")
                    ChayeonText("가상 차량에서만 기어를 바꿀 수 있어요. 주행 중에는 시연 조작도 잠겨요.", style = ChayeonType.Secondary)
                    if (!realVehicle) Gear.entries.filter { it != Gear.UNKNOWN }.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Compact)) {
                            row.forEach { gear ->
                                JellyButton(when (gear) { Gear.PARK -> "P 주차"; Gear.DRIVE -> "D 주행"; Gear.NEUTRAL -> "N 중립"; Gear.REVERSE -> "R 후진"; Gear.UNKNOWN -> "상태 확인 중" },
                                    { onGear(gear) }, Modifier.weight(1f), enabled = !state.busy, variant = JellyVariant.SECONDARY)
                            }
                        }
                    }
                }
            }
        }
    }} else null)
}

@Composable
private fun DemoColumn(content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val scroll = rememberScrollState()
        Column(Modifier.fillMaxWidth().then(if (constraints.hasBoundedHeight) Modifier.scrollEdgeFade(scroll).verticalScroll(scroll) else Modifier),
            verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap), content = content)
    }
}
