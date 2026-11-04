package dev.chayeon.feature.history

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.chayeon.core.model.*
import dev.chayeon.core.ui.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class HomeZoneUiState(
    val driving: DrivingState = DrivingState(),
    val home: HomeZoneState = HomeZoneState(),
    val selectedRadius: HomeZoneRadius = HomeZoneRadius.MEDIUM,
    val operation: String? = null,
    val error: Boolean = false,
    val confirmingClear: Boolean = false,
    val syntheticEnabled: Boolean = false,
    val locationConsentGranted: Boolean? = null,
)

class HomeZoneViewModel(private val deps: HistoryDeps) : ViewModel() {
    private val local = MutableStateFlow(HomeZoneUiState(syntheticEnabled = deps.syntheticHomeEnabled))
    private val locationConsent = combine(deps.sessionRepository.session, deps.profileRepository.settings) { session, settings ->
        val version = (session as? SessionState.Active)?.session?.policy?.consentDocumentVersion
        (settings as? LoadState.Ready)?.value?.takeIf { version != null }?.consents?.any {
            it.scope == ConsentScope.LOCATION_USE && it.status == ConsentStatus.GRANTED && it.documentVersion == version
        }
    }
    val uiState = combine(deps.drivingStateProvider.drivingState, deps.homeZoneRepository.state, local, locationConsent) { driving, home, ui, consent ->
        ui.copy(driving = driving, home = home, selectedRadius = home.radius ?: ui.selectedRadius, locationConsentGranted = consent)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000, replayExpirationMillis = 0), HomeZoneUiState())

    private suspend fun allowed() = deps.drivingStateProvider.drivingState.first().policy == DrivingPolicy.ALLOWED

    private fun command(name: String, execute: suspend () -> AppResult<Unit>) {
        if (local.value.operation != null) return
        local.update { it.copy(operation = name, error = false) }
        viewModelScope.launch {
            try {
                if (!allowed()) return@launch
                val result = execute()
                local.update { it.copy(error = result is AppResult.Failure,
                    confirmingClear = it.confirmingClear && result !is AppResult.Success) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { local.update { it.copy(error = true) } }
            finally { local.update { it.copy(operation = null) } }
        }
    }

    fun setHome(source: LocationSource) = command("set") {
        if (source == LocationSource.SYNTHETIC && !deps.syntheticHomeEnabled) return@command AppResult.Failure(AppError.Restricted)
        if (source == LocationSource.DEVICE && locationConsent.first() != true) return@command AppResult.Failure(AppError.Restricted)
        if (source == LocationSource.DEVICE && !deps.prepareHomeLocation()) return@command AppResult.Failure(AppError.Restricted)
        if (source == LocationSource.DEVICE && locationConsent.first() != true) return@command AppResult.Failure(AppError.Restricted)
        if (!allowed()) return@command AppResult.Failure(AppError.Restricted)
        deps.homeZoneRepository.setFromCurrentPosition(uiState.value.selectedRadius, source)
    }

    fun selectRadius(radius: HomeZoneRadius) = command("radius") {
        if (deps.homeZoneRepository.state.first().radius != null) deps.homeZoneRepository.changeRadius(radius)
        else { local.update { it.copy(selectedRadius = radius) }; AppResult.Success(Unit) }
    }

    fun askClear() { viewModelScope.launch { if (allowed()) local.update { it.copy(confirmingClear = true) } } }
    fun dismissClear() { if (local.value.operation == null) local.update { it.copy(confirmingClear = false) } }
    fun clear() = command("clear") {
        if (!local.value.confirmingClear) AppResult.Failure(AppError.Restricted) else deps.homeZoneRepository.clear()
    }
    fun back(action: () -> Unit) { viewModelScope.launch { if (allowed() && local.value.operation == null) action() } }
}

@Composable
fun HomeZoneScreen(state: HomeZoneUiState, chrome: ScreenChrome, onSet: (LocationSource) -> Unit,
    onRadius: (HomeZoneRadius) -> Unit, onAskClear: () -> Unit, onClear: () -> Unit, onDismiss: () -> Unit, onBack: () -> Unit) {
    val idle = state.operation == null
    HistoryFrame(HistoryUiState(driving = state.driving), chrome, "집 보호 구역", "집 근처에서는 잠시 쉬어가요", onBack, tab = NavigationItem.PROFILE,
        primary = {
            HeroPanel(Modifier.fillMaxWidth()) { ContentColumn {
                IconOrb(ChayeonIcon.LOCK, variant = OrbVariant.GLASS)
                GradientText("집 근처에서는\n나를 찾을 수 없어요", style = ChayeonType.Title)
                ChayeonText("이어져 있던 사람에게는 멀어져서 끝난 것과 똑같이 보여요", color = ChayeonColors.Secondary)
                InfoRow(ChayeonIcon.LOCK, "집 위치는 이 차에만 저장되고 서버로 보내지 않아요")
                ChayeonText("집을 정하지 않아도 참여할 수 있어요. 로그아웃하거나 계정을 지우면 이 차의 집 위치도 지워요.", style = ChayeonType.Secondary)
            } }
        }, secondary = {
            ContentColumn {
                GlassCard(Modifier.fillMaxWidth()) { ContentColumn {
                    SectionTitle("지금 상태")
                    if (state.home.source == LocationSource.SYNTHETIC) StatusChip("합성 위치 · 가상 집", variant = StatusVariant.PLAIN)
                    ChayeonText(when (state.home.status) {
                        HomeZoneStatus.NOT_SET -> "아직 집을 정하지 않았어요"
                        HomeZoneStatus.OUTSIDE -> "집 보호 구역 밖이에요"
                        HomeZoneStatus.INSIDE -> "집 근처에서는 쉬고 있어요"
                        HomeZoneStatus.LOCATION_UNKNOWN -> "위치를 확인할 때까지 쉬고 있어요"
                        HomeZoneStatus.PERMISSION_OFF -> "위치 사용이 꺼져 있어 쉬고 있어요"
                    }, Modifier.testTag("home-zone-status"), ChayeonType.Section)
                    ChayeonText(when (state.home.status) {
                        HomeZoneStatus.NOT_SET -> "집에서 주차한 뒤 지금 위치를 정해 주세요"
                        HomeZoneStatus.OUTSIDE -> "직접 켜 둔 발견 상태로 돌아가요"
                        HomeZoneStatus.INSIDE -> "벗어나면 다시 찾을 수 있어요"
                        HomeZoneStatus.LOCATION_UNKNOWN -> if (state.home.source == LocationSource.SYNTHETIC) "시연 제어에서 합성 위치를 골라 주세요. 그동안 발견을 쉬어요" else "위치 서비스를 켜고 잠시 기다려 주세요. 그동안 발견을 쉬어요"
                        HomeZoneStatus.PERMISSION_OFF -> "위치 권한과 차량 데이터·위치·공개 범위의 위치 사용 동의를 확인해 주세요. 그동안 발견을 쉬어요"
                    }, style = ChayeonType.Secondary, color = ChayeonColors.Secondary)
                } }
                GlassCard(Modifier.fillMaxWidth()) { ContentColumn {
                    SectionTitle("보호할 범위")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Compact)) {
                        HomeZoneRadius.entries.forEach { radius ->
                            GelRadioRow(when (radius) { HomeZoneRadius.NEAR -> "300m"; HomeZoneRadius.MEDIUM -> "500m"; HomeZoneRadius.WIDE -> "1km" },
                                radius == state.selectedRadius, { onRadius(radius) }, Modifier.weight(1f), enabled = idle)
                        }
                    }
                    JellyButton(if (state.operation == "set") "위치를 확인하고 있어요" else "지금 위치를 집으로 정하기",
                        { onSet(LocationSource.DEVICE) }, Modifier.fillMaxWidth(), enabled = idle && state.locationConsentGranted == true)
                    if (state.locationConsentGranted != true) ChayeonText(
                        if (state.locationConsentGranted == null) "위치 사용 동의를 확인하고 있어요" else "위치 사용에 동의하면 집을 정할 수 있어요",
                        style = ChayeonType.Secondary, color = ChayeonColors.Secondary)
                    if (state.syntheticEnabled) {
                        ChayeonText("실제 위치가 없을 때만 가상 집을 정할 수 있어요", style = ChayeonType.Minimum, color = ChayeonColors.Secondary)
                        JellyButton("합성 위치로 집 정하기", { onSet(LocationSource.SYNTHETIC) }, Modifier.fillMaxWidth(), JellyVariant.SECONDARY, enabled = idle)
                    }
                    if (state.error) TopBanner("위치를 정하지 못했어요. 권한과 위치 서비스를 확인한 뒤 다시 시도해 주세요")
                    if (state.home.radius != null) JellyButton("집 지우기", onAskClear, Modifier.fillMaxWidth(), JellyVariant.SECONDARY, enabled = idle)
                } }
            }
        })
    if (state.confirmingClear && state.driving.policy == DrivingPolicy.ALLOWED) ConfirmSheet("집을 지울까요?",
        "이 차에 저장한 집 위치를 지워요. 발견을 켜 두었다면 집 근처에서도 다시 찾을 수 있어요.", "집 지우기", onClear, onDismiss, busy = !idle)
}
