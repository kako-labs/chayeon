package dev.chayeon.feature.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import dev.chayeon.core.model.*
import dev.chayeon.core.ui.*

internal data class PersonRelationship(
    val peer: PublicProfile,
    val encounter: Encounter?,
    val request: AfterRequest?,
    val relationship: AfterConnection?,
    val latestAt: Long,
) {
    val incoming: Boolean get() = request?.status == AfterRequestStatus.PENDING && request.direction == RequestDirection.INCOMING
    val connected: Boolean get() = relationship?.status == AfterConnectionStatus.CONNECTED
    val label: String get() = when {
        incoming -> "애프터가 왔어요"
        connected -> "♥ 이어진 인연"
        request?.status == AfterRequestStatus.PENDING -> "답을 기다려요"
        relationship?.status == AfterConnectionStatus.CLOSED -> "관계가 끝났어요"
        request?.status == AfterRequestStatus.ACCEPTED -> "애프터를 수락했어요"
        request?.status == AfterRequestStatus.DECLINED -> "신청이 받아들여지지 않았어요"
        request?.status == AfterRequestStatus.EXPIRED -> "응답 기한이 지났어요"
        request?.status == AfterRequestStatus.WITHDRAWN -> "신청을 철회했어요"
        request?.status == AfterRequestStatus.UNAVAILABLE -> "지금은 이어갈 수 없어요"
        else -> "스친 인연"
    }
}

internal val HistoryUiState.people: List<PersonRelationship> get() {
    if (blocks !is LoadState.Ready) return emptyList()
    val hidden = blocks.valueOrNull().orEmpty().map { it.peer.id }.toSet()
    val encounters = encounters.valueOrNull().orEmpty()
    val requests = requests.valueOrNull().orEmpty()
    val relationships = relationships.valueOrNull().orEmpty()
    val peers = encounters.map { it.peer } + requests.map { it.peer } + relationships.map { it.peer }
    return peers.groupBy { it.id }.filterKeys { it !in hidden }.map { (id, versions) ->
        val encounter = encounters.filter { it.peer.id == id }.maxWithOrNull(compareBy<Encounter> { it.endedAtEpochMillis }.thenBy { it.version })
        val request = requests.filter { it.peer.id == id }.maxWithOrNull(compareBy<AfterRequest> { it.createdAtEpochMillis }.thenBy { it.version })
        val relationship = relationships.filter { it.peer.id == id }.maxWithOrNull(compareBy<AfterConnection> { it.connectedAtEpochMillis }.thenBy { it.version })
        val latest = maxOf(encounter?.endedAtEpochMillis ?: 0, request?.createdAtEpochMillis ?: 0,
            relationship?.closedAtEpochMillis ?: relationship?.connectedAtEpochMillis ?: 0)
        PersonRelationship(versions.maxBy { it.version }, encounter, request, relationship, latest)
    }.sortedWith(compareByDescending<PersonRelationship> { it.incoming }.thenByDescending { it.latestAt })
}

internal val HistoryUiState.contactsProjectionReady: Boolean get() {
    val sources = listOf(encounters, requests, relationships, blocks)
    return sources.all { it is LoadState.Ready } && sources.map { it.version }.distinct().size == 1
}

internal val HistoryUiState.person: PersonRelationship? get() = people.firstOrNull { it.peer.id == targetPeerId }

@Composable
fun RelationshipsScreen(state: HistoryUiState, chrome: ScreenChrome, onPerson: (String) -> Unit,
    onAnswer: (String) -> Unit, onMeet: () -> Unit) {
    HistoryFrame(state, chrome, "인연", "스친 순간부터 이어진 마음까지", tab = NavigationItem.RELATIONSHIPS,
        primary = {
            RelationshipContent(state) {
                if (state.people.isEmpty()) {
                    EmptyCard("아직 인연이 없어요", "서로 연결된 순간이 여기에 남아요")
                    JellyButton("오늘의 인연 보기", onMeet, Modifier.fillMaxWidth())
                }
                state.people.forEach { person ->
                    val actions: @Composable () -> Unit = {
                        Row(horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap)) {
                            JellyButton("인연 보기", { onPerson(person.peer.id) }, Modifier.weight(1f), JellyVariant.SECONDARY)
                            if (person.incoming) JellyButton("답하기", { onAnswer(person.request!!.encounterId) }, Modifier.weight(1f), JellyVariant.WHITE)
                            else if (!person.connected && person.request?.status != AfterRequestStatus.PENDING && person.encounter?.afterEligibility == AfterEligibility.AVAILABLE)
                                JellyButton("애프터 신청", { onAnswer(person.encounter.id) }, Modifier.weight(1f))
                        }
                    }
                    val summary: @Composable () -> Unit = {
                        Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Small)) {
                            ChayeonText(person.peer.nickname, style = ChayeonType.Section)
                            if (LocalSourceLabels.current) ChayeonText(person.peer.kind.label, style = ChayeonType.Minimum, color = ChayeonColors.Secondary)
                            person.peer.signature?.let { signature ->
                                ChayeonText(signature.title, style = ChayeonType.Secondary)
                                if (LocalSourceLabels.current) ChayeonText(signature.source.label, style = ChayeonType.Minimum, color = ChayeonColors.Secondary)
                            }
                            if (person.incoming) StatusChip(person.label)
                            else ChayeonText(person.label, style = ChayeonType.Secondary, color = ChayeonColors.Pink)
                            ChayeonText(encounterTime(person.latestAt), style = ChayeonType.Minimum)
                            if (!person.incoming && !person.connected && person.request?.status != AfterRequestStatus.PENDING)
                                ChayeonText(person.encounter?.afterEligibility?.label ?: "지금은 신청할 수 없어요", style = ChayeonType.Minimum)
                        }
                    }
                    val row: @Composable () -> Unit = {
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            if (maxWidth < ChayeonSpacing.NarrowBreakpoint - ChayeonSpacing.Screen * 2) ContentColumn {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.AvatarGap)) {
                                    AvatarDish(MochiAvatar.from(person.peer.avatar), Modifier.size(ChayeonDimensions.PeerSmall))
                                    summary()
                                }
                                actions()
                            } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.AvatarGap)) {
                                AvatarDish(MochiAvatar.from(person.peer.avatar), Modifier.size(ChayeonDimensions.PeerSmall))
                                Box(Modifier.weight(1f)) { summary() }
                                Box(Modifier.weight(1f)) { actions() }
                            }
                        }
                    }
                    val rowModifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { onPerson(person.peer.id) }
                    if (person.incoming) HeroPanel(rowModifier) { row() } else GlassCard(rowModifier) { row() }
                }
            }
        })
}

@Composable
internal fun RelationshipContent(state: HistoryUiState, content: @Composable () -> Unit) {
    val sources = listOf(state.encounters, state.requests, state.relationships, state.blocks)
    val failed = sources.filterIsInstance<LoadState.Failed>().firstOrNull()
    when {
        failed != null -> Loaded<Any>(failed) {}
        sources.any { it is LoadState.Loading } -> Loaded<Any>(LoadState.Loading()) {}
        else -> ContentColumn { content() }
    }
}

@Composable
fun PersonScreen(state: HistoryUiState, chrome: ScreenChrome, onAfter: (String) -> Unit, onSafety: (String) -> Unit,
    onDelete: () -> Unit, onEnd: () -> Unit, onConfirm: () -> Unit, onDismiss: () -> Unit,
    onRetry: (String) -> Unit, onBack: () -> Unit) {
    val person = state.person
    HistoryFrame(state, chrome, person?.peer?.nickname ?: "인연", "함께했던 순간과 나눈 마음이에요", onBack,
        tab = NavigationItem.RELATIONSHIPS, ratio = 1.3f, secondaryFirstWhenNarrow = true,
        primary = {
            RelationshipContent(state) {
                if (person == null) EmptyCard("이 인연은 더 이상 보이지 않아요", "기록이 삭제되었거나 지금은 확인할 수 없어요")
                else GlassCard { ContentColumn {
                    ProfilePresentation(person.peer, large = false)
                    StatusChip(person.label)
                    person.encounter?.let { encounter ->
                        SectionTitle("함께했던 순간")
                        ChayeonText("${encounterTime(encounter.startedAtEpochMillis)} · ${encounterDuration(encounter)}")
                        ChayeonText(encounter.endReason.presentation(encounter.peer.nickname).label, style = ChayeonType.Secondary)
                        if (encounter.sharedInterests.isNotEmpty()) InterestRow(encounter.sharedInterests)
                        ChayeonText("만난 위치와 이동 경로는 남기지 않아요.", style = ChayeonType.Minimum)
                    }
                    PeerChemistry(person.encounter?.chemistry ?: person.relationship?.chemistry ?: Chemistry(), state.myProfile?.publicProfile, person.peer)
                    if (person.encounter != null) JellyButton("이 기록 삭제", onDelete, Modifier.fillMaxWidth(), JellyVariant.SECONDARY)
                    JellyButton("차단·신고", { onSafety(person.peer.id) }, Modifier.fillMaxWidth(), JellyVariant.DANGER)
                    CommandFeedback(state.command("delete"), { onRetry("delete") })
                } }
            }
        }, secondary = {
            RelationshipContent(state) {
            if (person != null) ContentColumn {
                if (person.connected && !state.contactsProjectionReady) EmptyCard("아이디를 확인하고 있어요", "잠시만 기다려 주세요")
                else if (person.connected) HeroPanel { ContentColumn {
                    SectionTitle("♥ 마음이 이어졌어요", subtitle = "서로 고른 아이디를 나눴어요")
                    ThreadView(ThreadStage.AFTER_CONNECTED, Modifier.fillMaxWidth(), animateTransition = false)
                    val contacts = person.relationship?.sharedContacts
                    if (contacts == null) ChayeonText("공유한 아이디를 확인할 수 없어요", style = ChayeonType.Secondary)
                    else {
                        contacts.kakaoTalkId?.let { id ->
                            SectionTitle("카카오톡 아이디")
                            ChayeonText(id, style = ChayeonType.Title)
                        }
                        contacts.instagramId?.let { id ->
                            InfoRow(ChayeonIcon.KNOT, "인스타그램", body = "@$id")
                            InstagramContactQr(id)
                        }
                    }
                    person.relationship?.ownSharedContacts?.let { own ->
                        SectionTitle("내가 공유한 아이디")
                        own.kakaoTalkId?.let { ChayeonText("카카오톡 · $it", style = ChayeonType.Secondary) }
                        own.instagramId?.let { ChayeonText("인스타그램 · @$it", style = ChayeonType.Secondary) }
                    }
                    JellyButton("관계 끝내기", onEnd, Modifier.fillMaxWidth(), JellyVariant.SECONDARY)
                    CommandFeedback(state.command("end:${person.relationship?.id}"), { onRetry("end:${person.relationship?.id}") })
                } } else GlassCard { ContentColumn {
                    SectionTitle(person.label)
                    ChayeonText("서로 아이디를 공유해야 애프터가 이어져요. 수락 전에는 상대에게 보이지 않아요.")
                    val pending = person.request?.takeIf { it.status == AfterRequestStatus.PENDING }
                    if (pending != null) JellyButton(if (person.incoming) "답하기" else "신청 상태 보기",
                        { onAfter(pending.encounterId) }, Modifier.fillMaxWidth())
                    else person.encounter?.let { encounter ->
                        if (encounter.afterEligibility == AfterEligibility.AVAILABLE)
                            JellyButton("애프터 신청", { onAfter(encounter.id) }, Modifier.fillMaxWidth())
                        else ChayeonText(encounter.afterEligibility.label, style = ChayeonType.Secondary)
                    }
                } }
            }
            }
        })
    HistoryConfirm(state, onConfirm, onDismiss)
}
