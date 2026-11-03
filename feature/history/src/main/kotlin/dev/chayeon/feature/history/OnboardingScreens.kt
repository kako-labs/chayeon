package dev.chayeon.feature.history

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import dev.chayeon.core.model.*
import dev.chayeon.core.ui.*

@Composable
fun FirstRunScreen(state: HistoryUiState, chrome: ScreenChrome, onMode: (SessionMode) -> Unit,
    onPersona: (PersonaCode) -> Unit, onRoom: (String) -> Unit, onEnter: () -> Unit, onRetry: (String) -> Unit) {
    HistoryFrame(state, chrome, "차연", "길 위에서 시작되는 인연", step = 0, ratio = 1.3f,
        primary = {
            HeroPanel(Modifier.fillMaxWidth().height(ChayeonDimensions.Knot * 3)) {
                Image(painterResource(ChayeonAssets.KeyVisual), null, Modifier.fillMaxSize().padding(top = ChayeonDimensions.PeerSmall), contentScale = ContentScale.Fit)
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium)) {
                    GradientText("서로 원할 때만\n이어져요")
                    ChayeonText("주행 습관이 나만의 매력이 돼요", style = ChayeonType.Body, color = ChayeonColors.Pink)
                    Spacer(Modifier.weight(1f))
                    CompactInfo("발견을 켠 사람끼리만 보여요")
                    CompactInfo("양쪽이 수락해야 대화가 열려요")
                    CompactInfo("다시 원할 때 애프터로 이어가요")
                }
            }
        }, secondary = {
            ContentColumn {
                GlassCard { ContentColumn {
                    SectionTitle("어떻게 만나볼까요?")
                    if (SessionMode.SOLO in state.modes) GelRadioRow("혼자 둘러보기(가상 상대)", state.selectedMode == SessionMode.SOLO,
                        { onMode(SessionMode.SOLO) }, description = "이 차 안에서 시연용 가상 상대를 만나요", enabled = !state.command("enter").running)
                    if (SessionMode.SERVER_DEMO in state.modes) {
                        GelRadioRow("두 대로 연결(서버)", state.selectedMode == SessionMode.SERVER_DEMO,
                            { onMode(SessionMode.SERVER_DEMO) }, description = "같은 방 코드로 서로 다른 테스트 계정을 골라요", enabled = !state.command("enter").running)
                        if (state.selectedMode == SessionMode.SERVER_DEMO) GelTextField(state.roomCode, onRoom, "방 코드", maxLength = 32, error = state.draftError, enabled = !state.command("enter").running)
                    }
                    SectionTitle("시연 계정을 골라 주세요")
                    Loaded(state.personas, "준비된 계정이 없어요") { personas ->
                        personas.forEach { persona ->
                            Row(horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Compact)) {
                                AvatarDish(MochiAvatar.from(persona.avatar), Modifier.size(ChayeonDimensions.PeerSmall))
                                GelRadioRow(persona.nickname, state.selectedPersona == persona.code, { onPersona(persona.code) }, Modifier.weight(1f),
                                    description = "가상 프로필 · 테스트 계정 ${if (persona.code == PersonaCode.ACCOUNT_A) "A" else "B"}", enabled = !state.command("enter").running)
                            }
                        }
                    }
                } }
                ChayeonText("가상 프로필만 사용해요. 실제 가입과 연령 확인은 아직 제공하지 않아요.", style = ChayeonType.Secondary)
                if (state.session is SessionState.Failed) ChayeonText(state.session.error.copy(), style = ChayeonType.Secondary)
                CommandFeedback(state.command("enter"), { onRetry("enter") })
                JellyButton("이 계정으로 시작", onEnter, Modifier.fillMaxWidth(), enabled = !state.command("enter").running && state.selectedMode in state.modes && state.personas.valueOrNull().orEmpty().any { it.code == state.selectedPersona })
            }
        })
}

@Composable
fun ProfileSetupScreen(state: HistoryUiState, chrome: ScreenChrome, editing: Boolean,
    onDraft: (ProfileDraft) -> Unit, onSave: () -> Unit, onRetry: () -> Unit, onBack: () -> Unit) {
    val policy = state.policy
    val preview = state.myProfile?.publicProfile?.copy(nickname = state.draft.nickname.ifBlank { "닉네임" }, avatar = state.draft.avatar, interests = state.draft.interests)
        ?: PublicProfile("preview", 0, state.draft.nickname.ifBlank { "닉네임" }, state.draft.avatar, state.draft.interests, ProfileKind.VIRTUAL_PROFILE, null)
    HistoryFrame(state, chrome, if (editing) "프로필 고치기" else "프로필 만들기", "언제든 내 프로필에서 고칠 수 있어요",
        onBack = onBack, step = if (editing) null else 1, ratio = .62f,
        primary = {
            HeroPanel(Modifier.fillMaxWidth()) { ContentColumn {
                SectionTitle("상대에게 이렇게 보여요")
                Spacer(Modifier.height(ChayeonSpacing.Large))
                ProfilePresentation(preview)
                Spacer(Modifier.height(ChayeonSpacing.Large))
                InfoRow(ChayeonIcon.LOCK, "사진, 연락처, 차량 정보는 넣지 않아요", body = "직접 고른 공개 시그니처만 함께 보여요")
            } }
        }, secondary = {
            if (policy == null || !state.draftLoaded) Loaded(state.profile) { EmptyCard("참여 설정을 확인하고 있어요", "잠시만 기다려 주세요") }
            else GlassCard { ContentColumn {
                GelTextField(state.draft.nickname, { onDraft(state.draft.copy(nickname = it)) }, "닉네임", placeholder = "실명 대신 부를 이름",
                    maxLength = policy.nicknameMaxLength, error = state.draftError, enabled = !state.command("save").running)
                AvatarPicker(state.draft.avatar, { onDraft(state.draft.copy(avatar = it)) }, enabled = !state.command("save").running)
                InterestPicker(state.draft.interests, policy.maximumInterests, { onDraft(state.draft.copy(interests = it)) }, enabled = !state.command("save").running)
                if (state.command("save").error != null) StateCard(ExperienceState.PROFILE_SAVE_FAILED, Modifier.fillMaxWidth(), onAction = onRetry)
                CommandFeedback(state.command("save"), onRetry, if (editing) "프로필을 저장했어요" else null)
                JellyButton(if (editing) "변경 내용 저장" else "저장하고 다음", onSave, Modifier.fillMaxWidth(), enabled = !state.command("save").running)
            } }
        })
}

@Composable
fun ConsentScreen(state: HistoryUiState, chrome: ScreenChrome, onConsent: (ConsentScope, Boolean) -> Unit,
    onDiscovery: (Boolean) -> Unit, onVisibility: () -> Unit, onContinue: () -> Unit, onRetry: (String) -> Unit, onBack: () -> Unit) {
    HistoryFrame(state, chrome, "차량 데이터·위치·공개 범위", "무엇을 읽고 보여줄지 직접 골라 주세요", onBack, step = 2,
        primary = {
            ContentColumn {
                HeroPanel { Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap)) {
                    SectionTitle("상대에게 보여요")
                    state.myProfile?.publicProfile?.let {
                        PeerHeader(it, size = PeerHeaderSize.SMALL)
                        it.signature?.let { signature -> SignatureMini(signature) }
                        InterestRow(it.interests)
                    }
                    InfoRow(ChayeonIcon.LOCK, "상대에게 보이지 않아요", body = "정확한 위치와 이동 경로, 집과 목적지, 실명과 연락처, 차량번호와 차량 외형은 보여주지 않아요.")
                    JellyButton("시그니처 공개 범위 고르기", onVisibility, Modifier.fillMaxWidth(), JellyVariant.SECONDARY)
                } }
                GlassCard { ContentColumn {
                    SectionTitle("원시 값은 차 밖으로 나가지 않아요")
                    ChayeonText("속도·제동·조향·방향지시등·크루즈·안전벨트·기어를 읽어요. 주행 시간과 길이, 타이어 상태, 연료·배터리와 충전 한도, 소모품 경고, 실내 공기 상태도 사용해요.", style = ChayeonType.Secondary)
                    ChayeonText("지원하는 신호만 차 안에서 요약해요. 원시 값은 저장하거나 전송하지 않아요. 차량을 식별하는 정보는 읽지 않아요.", style = ChayeonType.Secondary)
                    ChayeonText("공개를 고른 특성과 단계만 서버와 상대에게 보여요. 수치와 순위는 공유하지 않아요.", style = ChayeonType.Secondary)
                } }
            }
        }, secondary = {
            ContentColumn {
                Loaded(state.settings) { settings ->
                    ConsentScope.entries.forEach { scope ->
                        val record = settings.consents.firstOrNull { it.scope == scope }
                        val granted = record?.status == ConsentStatus.GRANTED && record.documentVersion == state.policy?.consentDocumentVersion
                        val copy = consentCopy(scope)
                        Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Small)) {
                            GelCheckbox(copy.first, granted, { onConsent(scope, it) }, description = copy.second,
                                enabled = !state.command("consent:$scope").running && state.policy != null)
                            CommandFeedback(state.command("consent:$scope"), { onRetry("consent:$scope") })
                        }
                    }
                }
                GlassCard { ContentColumn {
                    GelToggle(state.discovery.mode == DiscoveryMode.ON, onDiscovery, "발견 켜기", enabled = state.myProfile?.registration == RegistrationState.READY && !state.command("discovery").running)
                    ChayeonText("켜도 대화는 따로 수락해요. 원하지 않으면 꺼둬도 돼요.", style = ChayeonType.Secondary)
                    CommandFeedback(state.command("discovery"), { onRetry("discovery") })
                } }
                JellyButton("선택한 설정으로 시작", onContinue, Modifier.fillMaxWidth(), enabled = state.myProfile?.registration == RegistrationState.READY)
                ChayeonText("동의는 각각 켜고 끌 수 있어요. 참여에 필요한 항목을 끄면 발견과 연결이 제한돼요.", style = ChayeonType.Minimum)
            }
        })
}

internal fun consentCopy(scope: ConsentScope): Pair<String, String> = when (scope) {
    ConsentScope.ADULT_ATTESTATION -> "성인이고, 직접 참여해요" to "시연용 자기 확인이에요. 실제 연령 인증이 아니에요."
    ConsentScope.VEHICLE_DATA -> "차량 데이터 사용에 동의해요" to "내 주행 습관을 시그니처로 만들어요. 원시 값은 차 안에만 있어요."
    ConsentScope.LOCATION_USE -> "위치 사용에 동의해요" to "근처 여부를 판단할 때만 써요. 상대에게 위치를 전달하지 않아요. 합성 위치를 쓰면 화면에 표시해요."
    ConsentScope.PUBLIC_PROFILE -> "프로필 공개에 동의해요" to "발견을 켜면 닉네임, 가상 아바타, 공개 시그니처와 관심사가 보여요."
}

@Composable
private fun CompactInfo(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium)) {
        ChayeonIcon(ChayeonIcon.CHECK, Modifier.size(ChayeonDimensions.UtilityIcon), ChayeonColors.Pink)
        ChayeonText(text, style = ChayeonType.Body)
    }
}
