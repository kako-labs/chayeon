package dev.chayeon.feature.meet

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    MeetSceneSplit(chrome, MascotPose.THREAD, onBack,
        actions = {
            if (candidate != null) MeetCandidateActions(state, onRequest, onBack, onSafety)
            else OutlineButton("목록으로", onBack, Modifier.fillMaxWidth())
        },
    ) {
        if (candidate == null) StateCard(state.unavailableState ?: ExperienceState.PEER_UNAVAILABLE, Modifier.fillMaxWidth())
        else {
            InfoLine("오늘의 인연")
            HeroHeadline(candidate.profile.nickname, "근처에 있는 동안 목소리로 이야기해요", style = ChayeonType.SceneTitle)
            MeetProfilePanel(candidate.profile, candidate.chemistry, candidate.sharedInterests)
            MeetPublicTraits(candidate.profile.signature, wide = false)
            MeetPrivacy()
        }
    }
}

@Composable
private fun MeetPublicTraits(signature: PublicSignature?, wide: Boolean) {
    if (signature == null || signature.traits.isEmpty()) return
    val compact = signature.traits.size > 6
    GlassPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(ChayeonSpacing.Medium)) {
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
            Column(Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(ChayeonColors.White.copy(alpha = .18f), ChayeonColors.GlassButtonBottom)), RoundedCornerShape(ChayeonRadii.Tag))
                .border(ChayeonElevation.Border, ChayeonColors.GlassBorder, RoundedCornerShape(ChayeonRadii.Tag))
                .padding(horizontal = ChayeonSpacing.Compact, vertical = if (compact) ChayeonSpacing.Tiny * 0 else ChayeonSpacing.Compact),
                verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Tiny)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Small)) {
                    ChayeonIcon(trait.trait.presentation.icon, Modifier.size(ChayeonDimensions.SmallIcon), ChayeonColors.Pink)
                    ChayeonText(trait.trait.presentation.name, style = if (compact) ChayeonType.Label else ChayeonType.Body)
                }
                ChayeonText(trait.trait.presentation.publicPhrase, style = if (compact) ChayeonType.Minimum else ChayeonType.Secondary, color = ChayeonColors.Secondary)
            }
        }
    }
}

@Composable
private fun MeetCandidateActions(state: CandidateUiState, onRequest: () -> Unit, onBack: () -> Unit, onSafety: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium)) {
        MeetError(state.request.error)
        PrimaryButton(if (state.request.inProgress) "통화를 요청하고 있어요" else if (!state.canRequest) "지금은 요청할 수 없어요" else "통화 요청하기",
            onRequest, Modifier.fillMaxWidth(), icon = ChayeonIcon.PHONE,
            enabled = state.canRequest && !state.request.inProgress)
        Row(horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap)) {
            OutlineButton("목록으로", onBack, Modifier.weight(1f), compact = true)
            OutlineButton("차단·신고", onSafety, Modifier.weight(1f), compact = true)
        }
    }
}

@Composable
internal fun MeetSignatureInvitation(onMySignature: () -> Unit) {
    GlassPanel(Modifier.fillMaxWidth()) {
        MeetTwoColumns(
            first = { SectionTitle("내 시그니처 만들기", subtitle = "주행 스타일과 차량 관리가 나를 보여주는 프로필이 돼요") },
            second = { PrimaryButton("시그니처 만들러 가기", onMySignature, Modifier.fillMaxWidth()) },
        )
    }
}
