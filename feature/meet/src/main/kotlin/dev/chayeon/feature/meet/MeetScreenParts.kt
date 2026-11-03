package dev.chayeon.feature.meet

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import dev.chayeon.core.model.*
import dev.chayeon.core.ui.*

@Composable
internal fun MeetPane(centered: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(if (centered) Modifier.fillMaxSize() else Modifier.fillMaxWidth()) {
        val scroll = rememberScrollState()
        val viewport = if (constraints.hasBoundedHeight) Modifier.scrollEdgeFade(scroll).verticalScroll(scroll) else Modifier
        val minimum = if (centered && constraints.hasBoundedHeight) Modifier.heightIn(min = maxHeight) else Modifier
        Column(Modifier.fillMaxWidth().then(viewport).then(minimum),
            verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap, if (centered) Alignment.CenterVertically else Alignment.Top), content = content)
    }
}

@Composable
internal fun MeetHeader(chrome: ScreenChrome, title: String, subtitle: String, onBack: (() -> Unit)? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap)) {
        chrome.topBar()
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.ChipHorizontal)) {
            if (onBack != null) BackButton(onBack)
            Column(Modifier.weight(1f)) {
                GradientText(title, style = ChayeonType.Title)
                ChayeonText(subtitle, style = ChayeonType.Secondary, color = ChayeonColors.Secondary)
            }
        }
    }
}

@Composable
internal fun MeetError(error: AppError?) {
    if (error != null) {
        ChayeonText(error.userMessage, Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            ChayeonType.Secondary, ChayeonColors.Pink)
    }
}

@Composable
internal fun MeetPeerSignature(peer: PublicProfile) {
    PeerHeader(peer, size = PeerHeaderSize.SMALL)
    val signature = peer.signature
    if (signature != null) CompositionLocalProvider(LocalDecorativeMotion provides false) {
        SignatureHero(signature, Modifier.fillMaxWidth())
    }
    else HeroPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Large)) {
            AvatarDish(MochiAvatar.from(peer.avatar), Modifier.size(ChayeonDimensions.PeerLarge))
            ChayeonText("시그니처를 만드는 중이에요", style = ChayeonType.Section)
            ChayeonText("공개한 주행 특성이 생기면 여기에 보여요", style = ChayeonType.Secondary, color = ChayeonColors.Secondary)
        }
    }
}

@Composable
internal fun MeetTraits(signature: PublicSignature?) {
    if (signature == null || signature.traits.isEmpty()) return
    GlassCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium)) {
            SectionTitle("주행으로 보여주는 모습", subtitle = "공개하기로 고른 특성만 보여요")
            TraitCategory.entries.forEach { category ->
                val traits = signature.traits.filter { it.trait.presentation.category == category }
                if (traits.isNotEmpty()) {
                    ChayeonText(category.label, style = ChayeonType.Label, color = ChayeonColors.Pink)
                    traits.forEach { trait ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium)) {
                            TraitBadge(trait.trait, trait.level, size = TraitBadgeSize.DETAIL)
                            ChayeonText(trait.trait.presentation.publicPhrase, Modifier.weight(1f), ChayeonType.Secondary, ChayeonColors.Secondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MeetChemistry(chemistry: Chemistry, own: PublicSignature?, peer: PublicSignature?) {
    if (chemistry.lines.isEmpty()) return
    if (own != null && peer != null) CompositionLocalProvider(LocalDecorativeMotion provides false) {
        ChemistryCard(chemistry, own, peer, Modifier.fillMaxWidth())
    }
    else GlassCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium)) {
            SectionTitle("함께하면 잘 맞는 점")
            chemistry.lines.forEach { InfoRow(ChayeonIcon.KNOT, it.text) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MeetInterests(peer: PublicProfile, shared: Set<InterestId> = emptySet()) {
    if (peer.interests.isEmpty()) return
    GlassCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium)) {
            SectionTitle("좋아하는 것")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.TagGap),
                verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.TagGap)) {
                peer.interests.sortedBy { it.ordinal }.forEach { InterestTag(it.label, matched = it in shared) }
            }
        }
    }
}

@Composable
internal fun MeetPrivacy() {
    ChayeonText("위치와 차량 정보는 서로에게 보이지 않아요", style = ChayeonType.Minimum, color = ChayeonColors.Secondary)
}

@Composable
internal fun MeetDiscoveryHero(state: HomeUiState, onToggle: (Boolean) -> Unit) {
    val enabled = state.discovery.mode == DiscoveryMode.ON
    HeroPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues()) {
        BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = ChayeonDimensions.HeroKnot + ChayeonSpacing.Screen)) {
            val wide = maxWidth >= ChayeonSpacing.NarrowBreakpoint
            if (wide) Box(Modifier.matchParentSize()) {
                Image(painterResource(ChayeonAssets.KeyVisual), null,
                    Modifier.align(Alignment.CenterEnd).width(ChayeonDimensions.ChemistryKnot + ChayeonDimensions.PeerSmall).fillMaxHeight()
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            drawRect(Brush.horizontalGradient(0f to ChayeonColors.Transparent, .38f to ChayeonColors.White), blendMode = BlendMode.DstIn)
                        }, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
            }
            Column(Modifier.padding(ChayeonSpacing.Large).fillMaxWidth(if (wide) .66f else 1f), verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Compact)) {
                StatusChip(if (enabled) "오늘의 인연" else "발견 꺼짐", variant = if (enabled) StatusVariant.ACTIVE else StatusVariant.PLAIN)
                GradientText(if (!enabled) "지금은 아무에게도 보이지 않아요" else when (state.discovery.status) {
                    DiscoveryStatus.AVAILABLE -> "근처에서 ${state.discovery.candidates.size}명이 발견을 켰어요"
                    DiscoveryStatus.ALREADY_CONNECTED -> "지금 이어진 인연이 있어요"
                    else -> "새로운 인연을 천천히 만나봐요"
                }, style = ChayeonType.Title)
                ChayeonText(if (enabled) "서로 수락하면 대화가 시작돼요" else "켜면 닉네임과 공개한 시그니처, 관심사가 보여요",
                    style = ChayeonType.Secondary, color = ChayeonColors.Secondary)
                GlassCard(Modifier.width(ChayeonDimensions.Knot), contentPadding = PaddingValues(horizontal = ChayeonSpacing.Compact, vertical = ChayeonSpacing.Tiny)) {
                    GelToggle(enabled, onToggle, if (state.toggle.inProgress) "바꾸고 있어요" else if (enabled) "발견 켜짐" else "발견 꺼짐",
                        Modifier.fillMaxWidth(), enabled = !state.toggle.inProgress)
                }
                MeetError(state.toggle.error)
            }
        }
    }
}

@Composable
internal fun MeetRelationStage(own: PublicProfile?, peer: PublicProfile, stage: ThreadStage, title: String, subtitle: String,
    modifier: Modifier = Modifier, animate: Boolean = true, centered: Boolean = false, largeAvatars: Boolean = centered) {
    HeroPanel(modifier.fillMaxWidth(), contentPadding = PaddingValues(ChayeonSpacing.Large)) {
        Column(Modifier.fillMaxWidth().align(Alignment.Center), verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium),
            horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start) {
            GradientText(title, style = if (centered) ChayeonType.Hero.copy(textAlign = TextAlign.Center) else ChayeonType.Title)
            ChayeonText(subtitle, style = if (centered) ChayeonType.Secondary.copy(textAlign = TextAlign.Center) else ChayeonType.Secondary, color = ChayeonColors.Secondary)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth >= ChayeonSpacing.NarrowBreakpoint) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium)) {
                        MeetRelationAvatar(own, "나", large = largeAvatars)
                        ThreadView(stage, Modifier.weight(1f), animateTransition = animate)
                        MeetRelationAvatar(peer, peer.nickname, large = largeAvatars)
                    }
                } else {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Compact)) {
                        MeetRelationAvatar(peer, peer.nickname, large = stage == ThreadStage.CONNECTED)
                        Box(Modifier.size(ChayeonDimensions.Avatar), contentAlignment = Alignment.Center) {
                            ThreadView(stage, Modifier.width(ChayeonDimensions.Avatar).rotate(90f), animateTransition = animate)
                        }
                        MeetRelationAvatar(own, "나")
                    }
                }
            }
        }
    }
}

@Composable
private fun MeetRelationAvatar(profile: PublicProfile?, fallback: String, large: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Small)) {
        if (profile != null) AvatarDish(MochiAvatar.from(profile.avatar), Modifier.size(if (large) ChayeonDimensions.PeerLarge else ChayeonDimensions.Avatar))
        else IconOrb(ChayeonIcon.PROFILE, Modifier.size(ChayeonDimensions.Avatar), OrbVariant.GLASS)
        ChayeonText(profile?.nickname ?: fallback, style = ChayeonType.Section)
        if (profile != null) ChayeonText(profile.kind.label, style = ChayeonType.Minimum, color = ChayeonColors.Secondary)
    }
}

@Composable
internal fun MeetTwoColumns(first: @Composable () -> Unit, second: @Composable () -> Unit, secondWeight: Float = 1f) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= ChayeonSpacing.NarrowBreakpoint) Row(horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap)) {
            Box(Modifier.weight(1f)) { first() }
            Box(Modifier.weight(secondWeight)) { second() }
        } else Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap)) {
            first()
            second()
        }
    }
}

@Composable
internal fun MeetGreeting(message: GreetingUi, canRetry: Boolean, onRetry: (String) -> Unit) {
    val mine = message.author == MessageAuthor.SELF
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Small)) {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
            ChatBubble(message.code.label, mine, Modifier.widthIn(max = minOf(maxWidth * .88f, ChayeonDimensions.ChemistryKnot)))
        }
        ChayeonText(when (message.delivery) {
            DeliveryState.SENDING -> "보내고 있어요"
            DeliveryState.SENT -> if (mine) "보냈어요" else "받았어요"
            DeliveryState.FAILED -> "보내지 못했어요"
        }, style = ChayeonType.Minimum, color = ChayeonColors.Secondary)
        if (message.delivery == DeliveryState.FAILED && mine) {
            MeetError(message.error)
            JellyButton("다시 보내기", { onRetry(message.clientMessageId) }, variant = JellyVariant.SECONDARY, enabled = canRetry)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MeetHomeCandidate(candidate: Candidate, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val peer = candidate.profile
    val signature = peer.signature
    GlassCard(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Compact)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium)) {
                AvatarDish(MochiAvatar.from(peer.avatar), Modifier.size(ChayeonDimensions.PeerSmall))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Tiny)) {
                    ChayeonText(peer.nickname, style = ChayeonType.Section)
                    ChayeonText(signature?.title ?: "시그니처를 만드는 중이에요", style = ChayeonType.Label)
                    ChayeonText(peer.kind.label + (signature?.let { " · ${it.source.label}" } ?: ""),
                        style = ChayeonType.Minimum, color = ChayeonColors.Secondary)
                }
            }
            signature?.traits?.maxByOrNull { it.level.ordinal }?.let { TraitBadge(it.trait, it.level) }
            if (candidate.chemistry.lines.isNotEmpty()) ChayeonText("잘 맞는 점 ${candidate.chemistry.lines.size}가지",
                style = ChayeonType.Label, color = ChayeonColors.Pink)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.TagGap), verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.TagGap)) {
                peer.interests.sortedBy { it.ordinal }.forEach { InterestTag(it.label, matched = it in candidate.sharedInterests) }
            }
            JellyButton("프로필 보기", onClick, Modifier.fillMaxWidth())
        }
    }
}
