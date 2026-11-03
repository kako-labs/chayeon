package dev.chayeon

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.chayeon.core.model.*
import dev.chayeon.core.ui.*
import java.util.UUID
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

const val DEMO_ROUTE = "app/demo"

data class DemoUiState(val driving: DrivingState = DrivingState(), val console: ConsoleState = ConsoleState(),
    val busy: Boolean = false, val message: String? = null)

class DemoConsoleViewModel(private val graph: AppGraph) : ViewModel() {
    private val busy = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    val uiState = combine(graph.drivingStateProvider.drivingState, graph.vehicleConsole.consoleState, busy, message) { driving, console, working, text ->
        DemoUiState(driving, console, working, text)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000, 0), DemoUiState())

    private fun meta() = CommandMeta(UUID.randomUUID().toString())
    private fun command(block: suspend () -> Boolean) {
        if (!graph.demoEnabled || busy.value || graph.currentPolicy() != DrivingPolicy.ALLOWED) return
        busy.value = true
        message.value = null
        viewModelScope.launch {
            try { message.value = if (block()) "시연 내용을 반영했어요" else "반영하지 못했어요. 주차 상태와 차량 데이터 동의를 확인해 주세요" }
            finally { busy.value = false }
        }
    }
    fun gear(value: Gear) = command { graph.vehicleConsole.setGear(SetGearRequest(meta(), value)) is AppResult.Success }
    fun presence(value: SyntheticPresence) = command {
        graph.currentPolicy() == DrivingPolicy.ALLOWED && graph.discoveryRepository.setSyntheticPresence(SetSyntheticPresenceRequest(meta(), value)) is AppResult.Success
    }
    fun replay(scenario: DriveScenario) = command { runReplay(scenario) }
    private suspend fun runReplay(scenario: DriveScenario): Boolean {
        if (graph.currentPolicy() != DrivingPolicy.ALLOWED) return false
        if (graph.vehicleConsole.replay(ReplayDriveRequest(meta(), scenario)) !is AppResult.Success) return false
        return withTimeoutOrNull(30_000) {
            graph.vehicleConsole.consoleState.first { it.status != ReplayStatus.PLAYING }.status == ReplayStatus.FINISHED
        } == true
    }
    fun fill() = command {
        repeat(3) { if (!runReplay(DriveScenario.CARE)) return@command false }
        if (graph.currentPolicy() != DrivingPolicy.ALLOWED) return@command false
        val signature = (graph.signatureRepository.signature.first() as? LoadState.Ready)?.value ?: return@command false
        if (graph.signatureRepository.updateVisibility(UpdateVisibilityRequest(meta(), signature.visibility.visibleTraits, true)) !is AppResult.Success) return@command false
        val updated = (graph.signatureRepository.signature.first() as? LoadState.Ready)?.value ?: return@command false
        val public = updated.publicSignature ?: return@command false
        val published = graph.profileRepository.publishSignature(PublishSignatureRequest(meta(), public)) is AppResult.Success
        if (published) {
            val recaps = (graph.signatureRepository.recaps.first() as? LoadState.Ready)?.value.orEmpty()
            recaps.filter { it.source == DataSource.SYNTHETIC && !it.seen }.forEach {
                graph.signatureRepository.markRecapSeen(MarkRecapSeenRequest(meta(), it.id))
            }
        }
        published
    }
}

@Composable
fun DemoConsoleScreen(state: DemoUiState, chrome: ScreenChrome, realVehicle: Boolean, enabled: Boolean,
    onGear: (Gear) -> Unit, onReplay: (DriveScenario) -> Unit, onFill: () -> Unit,
    onPresence: (SyntheticPresence) -> Unit, onBack: () -> Unit, returnToConnection: Boolean = false) {
    BackHandler { if (state.driving.policy == DrivingPolicy.ALLOWED) onBack() }
    ScreenScaffold(topBar = chrome.topBar, bottomBar = { chrome.bottomBar(NavigationItem.PROFILE) }, primary = {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap)) {
            AppHeader(title = "시연 제어", subtitle = "합성 주행과 합성 위치를 직접 살펴봐요")
            if (state.driving.policy != DrivingPolicy.ALLOWED) {
                StateCard(if (state.driving.policy == DrivingPolicy.UNKNOWN) ExperienceState.DRIVING_UNKNOWN else ExperienceState.DRIVING_RESTRICTED)
            } else if (!enabled) {
                ChayeonText("이 앱에서는 시연 제어를 제공하지 않아요")
                JellyButton("돌아가기", onBack)
            } else {
                StatusChip("합성 주행 · 합성 위치", variant = StatusVariant.SYNTHETIC)
                GlassCard { Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap)) {
                    SectionTitle("시그니처를 만나봐요", subtitle = "합성 주행 세 번을 채우고 시그니처를 공개해요. 실제 주행 기록과 구분해서 표시해요.")
                    JellyButton(if (state.busy) "합성 주행을 반영하고 있어요" else "합성 주행 3회로 시그니처 채우기", onFill, enabled = !state.busy)
                    state.message?.let { ChayeonText(it, style = ChayeonType.Secondary) }
                } }
                SectionTitle("합성 주행 재생")
                DriveScenario.entries.forEach { scenario ->
                    JellyButton(when (scenario) {
                        DriveScenario.GENTLE -> "부드러운 주행"
                        DriveScenario.EARLY_BRAKE -> "여유로운 제동"
                        DriveScenario.SIGNAL_MANNERS -> "깜빡이 매너"
                        DriveScenario.CARE -> "차량 관리"
                        DriveScenario.UNSUPPORTED -> "지원하지 않는 신호"
                    }, { onReplay(scenario) }, Modifier.fillMaxWidth(), JellyVariant.SECONDARY, enabled = !state.busy)
                }
                JellyButton(if (returnToConnection) "연결 화면으로 돌아가기" else "내 프로필로 돌아가기", onBack, Modifier.fillMaxWidth())
            }
        }
    }, secondary = if (state.driving.policy == DrivingPolicy.ALLOWED && enabled) {{
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap)) {
            GlassCard { Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap)) {
                SectionTitle("기어 제어")
                ChayeonText(if (realVehicle) "실제 차량 서비스가 있어요. 기어는 차량의 Virtual Console에서 바꿔 주세요." else "가상 차량에서만 기어를 바꿀 수 있어요. 주행 중에는 시연 조작도 잠겨요.", style = ChayeonType.Secondary)
                if (!realVehicle) Gear.entries.filter { it != Gear.UNKNOWN }.forEach { gear ->
                    JellyButton(when (gear) { Gear.PARK -> "P 주차"; Gear.DRIVE -> "D 주행"; Gear.NEUTRAL -> "N 중립"; Gear.REVERSE -> "R 후진"; Gear.UNKNOWN -> "상태 확인 중" }, { onGear(gear) }, enabled = !state.busy, variant = JellyVariant.SECONDARY)
                }
            } }
            GlassCard { Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap)) {
                SectionTitle("합성 위치")
                SyntheticPresence.entries.forEach { presence ->
                    JellyButton(when (presence) {
                        SyntheticPresence.NEARBY -> "가까이 있어요"
                        SyntheticPresence.AWAY -> "서로 멀어져요"
                        SyntheticPresence.STALE -> "위치를 확인할 수 없어요"
                        SyntheticPresence.OFFLINE -> "연결이 끊어져요"
                    }, { onPresence(presence) }, Modifier.fillMaxWidth(), JellyVariant.SECONDARY, enabled = !state.busy)
                }
            } }
        }
    }} else null)
}
