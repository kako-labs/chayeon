package dev.chayeon.feature.history

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import dev.chayeon.core.model.*
import dev.chayeon.core.ui.*

@Composable
fun FirstRunScreen(state: HistoryUiState, chrome: ScreenChrome, onMode: (SessionMode) -> Unit,
    onEnter: () -> Unit, onRetry: (String) -> Unit) {
    if (SessionMode.SERVER_DEMO !in state.modes) {
        HistoryFrame(state, chrome, "차연", "프로필을 준비하고 있어요", primary = {
            GlassPanel(Modifier.fillMaxWidth()) {
                ChayeonText("프로필을 준비하고 있어요", style = ChayeonType.Body)
                if (state.session is SessionState.Failed) {
                    ChayeonText(state.session.error.copy(), style = ChayeonType.Secondary)
                    HistoryButton("다시 시작", onEnter, Modifier.fillMaxWidth(), JellyVariant.SECONDARY)
                }
                CommandFeedback(state.command("enter"), { onRetry("enter") })
            }
        })
        return
    }
    HistoryFrame(state, chrome, "차연", "길 위에서 시작되는 인연", step = 0, ratio = 1f,
        bottom = {
            OnboardingActions("시작하기", !state.command("enter").running && state.selectedMode in state.modes, onEnter)
        },
        primary = {
            Column(Modifier.fillMaxWidth().heightIn(min = LocalHistoryPaneHeight.current).testTag("onboarding-left"),
                verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Large)) {
                HeroHeadline("서로 원할 때만\n이어져요", "길 위에서 시작되는 인연", style = ChayeonType.SceneTitle)
                ChayeonText("주행 습관이 나만의 매력이 돼요", Modifier.testTag("first-run-subtitle"), ChayeonType.SceneBody, ChayeonColors.Pink)
                Mascot(MascotPose.WAVE, Modifier.fillMaxWidth().height(ChayeonDimensions.SceneMascot).testTag("first-run-visual"))
                CompactInfo("발견을 켠 사람끼리만 보여요")
                CompactInfo("양쪽이 수락해야 통화가 열려요")
                CompactInfo("다시 원할 때 애프터로 이어가요")
            }
        }, secondary = {
            GlassPanel(Modifier.fillMaxWidth().heightIn(min = LocalHistoryPaneHeight.current).testTag("onboarding-right")) {
                Column(Modifier.fillMaxWidth().heightIn(min = (LocalHistoryPaneHeight.current - ChayeonSpacing.Large * 2).coerceAtLeast(0.dp)),
                    verticalArrangement = OnboardingPanelArrangement) {
                    SectionTitle("어떻게 만나볼까요?")
                    if (SessionMode.SOLO in state.modes) GelRadioRow("혼자 둘러보기(가상 상대)", state.selectedMode == SessionMode.SOLO,
                        { onMode(SessionMode.SOLO) }, description = "가상 상대와 편하게 만나봐요", enabled = !state.command("enter").running)
                    if (SessionMode.SERVER_DEMO in state.modes) {
                        GelRadioRow("두 대로 연결(서버)", state.selectedMode == SessionMode.SERVER_DEMO,
                            { onMode(SessionMode.SERVER_DEMO) }, description = "참여 중인 사람과 연결해요", enabled = !state.command("enter").running)
                    }
                    if (state.selectedMode == SessionMode.SERVER_DEMO) ChayeonText("실제 참여자와 시연용 가상 상대가 함께 보여요", style = ChayeonType.Secondary)
                    if (state.session is SessionState.Failed && state.session.error != AppError.Restricted) ChayeonText(state.session.error.copy(), style = ChayeonType.Secondary)
                    if (state.command("enter").error == AppError.Restricted) {
                        ChayeonText("지금은 참여자가 많아요. 잠시 후 다시 시작해 주세요", style = ChayeonType.Secondary)
                        HistoryButton("다시 시작", { onRetry("enter") }, Modifier.fillMaxWidth(), JellyVariant.SECONDARY)
                    } else CommandFeedback(state.command("enter"), { onRetry("enter") })
                }
            }
        })
}

@Composable
fun ProfileSetupScreen(state: HistoryUiState, chrome: ScreenChrome, editing: Boolean,
    onDraft: (ProfileDraft) -> Unit, onSave: () -> Unit, onRetry: () -> Unit, onBack: () -> Unit) {
    val policy = state.policy
    HistoryFrame(state, chrome, if (editing) "프로필 고치기" else "프로필 만들기", "언제든 내 프로필에서 고칠 수 있어요",
        onBack = onBack, step = if (editing) null else 1, ratio = 1f,
        bottom = { OnboardingActions(if (editing) "변경 내용 저장" else "저장하고 다음",
            policy != null && state.draftLoaded && !state.command("save").running, onSave) },
        primary = {
            Column(Modifier.fillMaxWidth().testTag("onboarding-left"), verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Large)) {
                if (!editing && state.modes == setOf(SessionMode.SOLO)) ChayeonText("혼자 둘러보기에서는 가상 상대와 만나봐요", style = ChayeonType.Secondary)
                HistorySceneHero("운전하는 나를\n소개해요",
                    "닉네임과 캐릭터로 만나요. 연락처는 애프터에서 직접 골라요.", MascotPose.WAVE)
                InfoLine("연락처는 공개 카드에 보이지 않아요", icon = ChayeonIcon.LOCK)
            }
        }, secondary = {
            if (policy == null || !state.draftLoaded) Loaded(state.profile) { EmptyCard("참여 설정을 확인하고 있어요", "잠시만 기다려 주세요") }
            else GlassPanel(Modifier.fillMaxWidth().testTag("onboarding-right")) {
                Column(Modifier.fillMaxWidth().heightIn(min = (LocalHistoryPaneHeight.current - ChayeonSpacing.Large * 2).coerceAtLeast(0.dp)),
                    verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Large)) {
                GelTextField(state.draft.nickname, { onDraft(state.draft.copy(nickname = it)) }, "닉네임", placeholder = "실명 대신 부를 이름",
                    maxLength = policy.nicknameMaxLength, error = state.draftError, enabled = !state.command("save").running)
                ProfileAvatarPicker(state.draft.avatar, { onDraft(state.draft.copy(avatar = it)) }, enabled = !state.command("save").running)
                InterestPicker(state.draft.interests, policy.maximumInterests, { onDraft(state.draft.copy(interests = it)) }, enabled = !state.command("save").running)
                SectionTitle("애프터 때 건넬 아이디 · 선택", subtitle = "서로 수락할 때 고른 상대에게만 공유해요")
                ContactInputs(state.draft.contacts, { onDraft(state.draft.copy(contacts = it)) }, enabled = !state.command("save").running)
                if (state.command("save").error != null) StateCard(ExperienceState.PROFILE_SAVE_FAILED, Modifier.fillMaxWidth(), onAction = onRetry)
                CommandFeedback(state.command("save"), onRetry, if (editing) "프로필을 저장했어요" else null)
            } }
        })
}

@Composable
fun ConsentScreen(state: HistoryUiState, chrome: ScreenChrome, onConsent: (ConsentScope, Boolean) -> Unit,
    onDiscovery: (Boolean) -> Unit, onVisibility: () -> Unit, onContinue: () -> Unit, onRetry: (String) -> Unit, onBack: () -> Unit,
    onHomeZone: () -> Unit = {}, onMicrophone: () -> Unit = {}) {
    HistoryFrame(state, chrome, "차량 데이터·위치·공개 범위", "무엇을 읽고 보여줄지 직접 골라 주세요", onBack, step = 2,
        bottom = { OnboardingActions("선택한 설정으로 시작", state.requiredConsentsGranted && state.myProfile?.registration == RegistrationState.READY, onContinue) },
        primary = {
            StretchPanels {
                GlassPanel(contentPadding = PaddingValues(ChayeonSpacing.Large)) { Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Small)) {
                    SectionTitle("상대에게 보여요")
                    state.myProfile?.publicProfile?.let {
                        PeerHeader(it, size = PeerHeaderSize.SMALL)
                        it.signature?.let { signature -> SignatureMini(signature) }
                        InterestRow(it.interests)
                    }
                    InfoRow(ChayeonIcon.LOCK, "상대에게 보이지 않아요", body = "정확한 위치와 이동 경로, 집과 목적지, 실명, 차량번호와 차량 외형은 보여주지 않아요. 저장한 아이디는 애프터에서 직접 골라 서로 수락할 때만 공유해요.")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Compact)) {
                        HistoryButton("시그니처 공개 범위 고르기", onVisibility, Modifier.weight(1.2f), JellyVariant.SECONDARY)
                        HistoryButton("집 보호 구역 · 선택", onHomeZone, Modifier.weight(1f), JellyVariant.SECONDARY,
                            enabled = ConsentScope.entries.none { state.command("consent:$it").running })
                    }
                } }
                GlassPanel(Modifier.fillMaxWidth().testTag("onboarding-left")) {
                    Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium)) {
                    SectionTitle("원시 값은 차 밖으로 나가지 않아요")
                    ChayeonText("속도·제동·조향·방향지시등·크루즈·안전벨트·기어를 읽어요. 주행 시간과 길이, 타이어 상태, 연료·배터리와 충전 한도, 소모품 경고, 실내 공기 상태도 사용해요.", style = ChayeonType.Secondary)
                    ChayeonText("지원하는 신호만 차 안에서 요약해요. 원시 값은 저장하거나 전송하지 않아요. 차량을 식별하는 정보는 읽지 않아요.", style = ChayeonType.Secondary)
                    ChayeonText("공개를 고른 특성과 점수만 서버와 상대에게 보여요. 주행 기록과 순위는 공유하지 않아요.", style = ChayeonType.Secondary)
                } }
            }
        }, secondary = {
            StretchPanels {
                ContentColumn {
                Loaded(state.settings) { settings ->
                    ConsentScope.entries.forEach { scope ->
                        val record = settings.consents.firstOrNull { it.scope == scope }
                        val granted = record?.status == ConsentStatus.GRANTED && record.documentVersion == state.policy?.consentDocumentVersion
                        val copy = consentCopy(scope, LocalSourceLabels.current)
                        Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Small)) {
                            GelCheckbox(copy.first, granted, { onConsent(scope, it) }, description = copy.second,
                                enabled = !state.command("consent:$scope").running && state.policy != null)
                            CommandFeedback(state.command("consent:$scope"), { onRetry("consent:$scope") })
                        }
                    }
                }
                }
                GelCheckbox("마이크 · 선택", state.microphoneGranted, { if (it) onMicrophone() },
                    description = "연결된 상대와 통화할 때만 써요. 허용하지 않아도 들을 수 있어요.",
                    enabled = !state.microphoneGranted)
                GlassPanel(Modifier.fillMaxWidth().testTag("onboarding-right")) { ContentColumn {
                    GelToggle(state.requiredConsentsGranted && state.discovery.mode == DiscoveryMode.ON, onDiscovery, "발견 켜기",
                        enabled = state.requiredConsentsGranted && state.myProfile?.registration == RegistrationState.READY && !state.command("discovery").running)
                    if (!state.requiredConsentsGranted) ChayeonText("위의 네 항목에 동의한 뒤 발견을 켤 수 있어요.", style = ChayeonType.Secondary, color = ChayeonColors.Pink)
                    ChayeonText("켜도 통화는 따로 수락해요. 원하지 않으면 꺼둬도 돼요.", style = ChayeonType.Secondary)
                    CommandFeedback(state.command("discovery"), { onRetry("discovery") })
                } }
            }
        })
}

internal fun consentCopy(scope: ConsentScope, showSourceLabels: Boolean = true): Pair<String, String> = when (scope) {
    ConsentScope.ADULT_ATTESTATION -> "성인이고, 직접 참여해요" to if (showSourceLabels) "시연용 자기 확인이에요. 실제 연령 인증이 아니에요." else "성인임을 직접 확인하고 참여해요."
    ConsentScope.VEHICLE_DATA -> "차량 데이터 사용에 동의해요" to "내 주행 습관을 시그니처로 만들어요. 원시 값은 차 안에만 있어요."
    ConsentScope.LOCATION_USE -> "위치 사용에 동의해요" to "발견과 집 보호 구역을 판단할 때 써요. 동의를 끄면 위치 읽기를 멈춰요."
    ConsentScope.PUBLIC_PROFILE -> "프로필 공개에 동의해요" to "발견을 켜면 닉네임, 가상 아바타, 공개 시그니처와 관심사가 보여요."
}

@Composable
private fun CompactInfo(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium)) {
        ChayeonIcon(ChayeonIcon.CHECK, Modifier.size(ChayeonDimensions.UtilityIcon), ChayeonColors.Pink)
        ChayeonText(text, style = ChayeonType.Body)
    }
}
