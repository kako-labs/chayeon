package dev.chayeon.feature.meet

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import dev.chayeon.core.model.*
import dev.chayeon.core.ui.*

@Composable
internal fun MeetCandidateContent(
    state: CandidateUiState,
    chrome: ScreenChrome,
    onRequest: () -> Unit,
    onBack: () -> Unit,
    onSafety: () -> Unit,
) {
    val candidate = state.candidate
    BoxWithConstraints {
        val wide = maxWidth >= ChayeonSpacing.NarrowBreakpoint
        ScreenScaffold(
            modifier = Modifier.testTag("meet-screen"),
            topBar = chrome.topBar,
            bottomBar = {
                if (!wide || candidate == null) {
                    if (candidate != null) MeetCandidateActions(state, onRequest, onBack, onSafety)
                    else JellyButton("목록으로", onBack, Modifier.fillMaxWidth(), JellyVariant.SECONDARY)
                }
            },
            primary = {
                if (candidate == null) {
                    StateCard(state.unavailableState ?: ExperienceState.PEER_UNAVAILABLE, Modifier.fillMaxWidth())
                } else if (wide) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap)) {
                        Box(Modifier.weight(.8f).fillMaxHeight()) {
                            MeetCandidatePerson(candidate.profile, Modifier.fillMaxSize())
                        }
                        Column(Modifier.weight(1.2f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap)) {
                            Box(Modifier.weight(1f).fillMaxWidth()) { MeetCandidateDetails(state, candidate, wide = true) }
                            MeetCandidateActions(state, onRequest, onBack, onSafety)
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap)) {
                        MeetCandidatePerson(candidate.profile, Modifier.fillMaxWidth())
                        MeetCandidateDetails(state, candidate, wide = false)
                    }
                }
            },
        )
    }
}

@Composable
private fun MeetCandidatePerson(peer: PublicProfile, modifier: Modifier) {
    HeroPanel(modifier, contentPadding = PaddingValues(ChayeonSpacing.Screen)) {
        MeetPane(centered = true) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Large)) {
                AvatarDish(MochiAvatar.from(peer.avatar), Modifier.size(ChayeonDimensions.Knot))
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Small)) {
                    val nameStyle = if (peer.nickname.codePointCount(0, peer.nickname.length) > 8) ChayeonType.Title else ChayeonType.Hero
                    GradientText(peer.nickname, style = nameStyle.copy(textAlign = TextAlign.Center))
                    ChayeonText(peer.kind.label, style = ChayeonType.Secondary, color = ChayeonColors.Secondary)
                }
                val signature = peer.signature
                if (signature != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Compact)) {
                        SignatureKnot(signature.knotSeed, Modifier.size(ChayeonDimensions.MiniKnot), reveal = false,
                            contentDescription = "${peer.nickname} 님의 붉은 시그니처 매듭")
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Small)) {
                            ChayeonText(signature.title, style = ChayeonType.Section)
                            ChayeonText(signature.source.label, style = ChayeonType.Minimum, color = ChayeonColors.Secondary)
                        }
                    }
                } else {
                    ChayeonText("시그니처를 만드는 중이에요", style = ChayeonType.Section)
                    ChayeonText("공개한 주행 특성이 생기면 여기에 보여요", style = ChayeonType.Secondary, color = ChayeonColors.Secondary)
                }
                MeetPrivacy()
            }
        }
    }
}

@Composable
private fun MeetCandidateDetails(state: CandidateUiState, candidate: Candidate, wide: Boolean) {
    MeetPane(centered = true) {
        MeetPublicTraits(candidate.profile.signature, wide)
        MeetCompactChemistry(candidate.chemistry, state.ownProfile?.signature, candidate.profile.signature)
        if (candidate.profile.interests.isNotEmpty()) {
            ChayeonText("좋아하는 것 · " + candidate.profile.interests.sortedBy { it.ordinal }.joinToString(" · ") { it.label },
                style = ChayeonType.Secondary, color = ChayeonColors.Secondary)
        }
        ChayeonText("서로 수락하면 주차 중에 짧게 인사해요. 멀어지면 연결이 끝나요.",
            style = ChayeonType.Minimum, color = ChayeonColors.Secondary)
    }
}

@Composable
private fun MeetPublicTraits(signature: PublicSignature?, wide: Boolean) {
    if (signature == null || signature.traits.isEmpty()) return
    val compact = signature.traits.size > 6
    GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(ChayeonSpacing.Medium)) {
        val groups = TraitCategory.entries.map { category -> category to signature.traits.filter { it.trait.presentation.category == category } }
            .filter { it.second.isNotEmpty() }
        if (wide) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Large)) {
            groups.forEach { (category, traits) -> MeetTraitGroup(category, traits, compact, Modifier.weight(1f)) }
        } else Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Large)) {
            groups.forEach { (category, traits) -> MeetTraitGroup(category, traits, compact, Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
private fun MeetTraitGroup(category: TraitCategory, traits: List<PublicTrait>, compact: Boolean, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Compact)) {
        ChayeonText(category.label, style = ChayeonType.Label, color = ChayeonColors.Pink)
        traits.forEach { trait ->
            Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Tiny)) {
                ChayeonText(trait.trait.presentation.name, style = if (compact) ChayeonType.Label else ChayeonType.Body)
                ChayeonText(trait.trait.presentation.publicPhrase, style = if (compact) ChayeonType.Minimum else ChayeonType.Secondary, color = ChayeonColors.Secondary)
            }
        }
    }
}

@Composable
internal fun MeetCompactChemistry(chemistry: Chemistry, own: PublicSignature?, peer: PublicSignature?) {
    if (chemistry.lines.isEmpty()) return
    GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(ChayeonSpacing.Medium)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium)) {
            if (own != null && peer != null) SignatureKnot(own.knotSeed,
                Modifier.size(ChayeonDimensions.PeerLarge, ChayeonDimensions.Avatar),
                reveal = false, interlockedWith = peer.knotSeed)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Small)) {
                ChayeonText("함께하면 잘 맞는 점", style = ChayeonType.Label)
                if (own != null && peer != null) ChayeonText(
                    if (own.source == peer.source) own.source.label else "${own.source.label} · ${peer.source.label}",
                    style = ChayeonType.Minimum, color = ChayeonColors.Secondary)
                chemistry.lines.forEach { ChayeonText(it.text, style = ChayeonType.Secondary, color = ChayeonColors.Secondary) }
            }
        }
    }
}

@Composable
private fun MeetCandidateActions(state: CandidateUiState, onRequest: () -> Unit, onBack: () -> Unit, onSafety: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap)) {
        MeetError(state.request.error)
        JellyButton(if (state.request.inProgress) "연결을 요청하고 있어요" else if (!state.canRequest) "지금은 요청할 수 없어요" else "연결 요청 보내기",
            onRequest, Modifier.fillMaxWidth().heightIn(min = ChayeonSpacing.Touch + ChayeonSpacing.Hero),
            enabled = state.canRequest && !state.request.inProgress)
        Row(horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap)) {
            JellyButton("목록으로", onBack, Modifier.weight(1f).heightIn(min = ChayeonSpacing.Button + ChayeonSpacing.Small), JellyVariant.SECONDARY)
            JellyButton("차단·신고", onSafety, Modifier.weight(1f).heightIn(min = ChayeonSpacing.Button + ChayeonSpacing.Small), JellyVariant.DANGER)
        }
    }
}

@Composable
internal fun MeetSignatureInvitation(onMySignature: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        MeetTwoColumns(
            first = { SectionTitle("내 시그니처 만들기", subtitle = "주행 스타일과 차량 관리가 나를 보여주는 프로필이 돼요") },
            second = { JellyButton("시그니처 만들러 가기", onMySignature, Modifier.fillMaxWidth(), JellyVariant.WHITE) },
        )
    }
}
