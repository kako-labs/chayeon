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
    HistoryFrame(state, chrome, "인연", "길 위에서 통화한 사람들이 모여요", tab = NavigationItem.RELATIONSHIPS, showHeading = false,
        primary = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                HeroHeadline("인연", "길 위에서 통화한 사람들이 모여요", style = ChayeonType.SceneTitle)
                Mascot(MascotPose.KNOT, Modifier.size(ChayeonDimensions.PeerLarge))
            }
            RelationshipContent(state) {
                if (state.people.isEmpty()) {
                    EmptyCard("아직 인연이 없어요", "서로 연결된 순간이 여기에 남아요")
                    HistoryButton("오늘의 인연 보기", onMeet, Modifier.fillMaxWidth())
                }
                state.people.forEach { person ->
                    val status = when {
                        person.incoming -> BondStatus.INCOMING
                        person.connected -> BondStatus.CONNECTED
                        person.request?.status == AfterRequestStatus.PENDING -> BondStatus.WAITING
                        else -> null
                    }
                    val source = if (LocalSourceLabels.current) " · " + listOfNotNull(person.peer.kind.label, person.peer.signature?.source?.label).distinct().joinToString(" · ") else ""
                    val subLine = listOfNotNull(person.peer.signature?.title,
                        if (status == null) person.label else null, encounterTime(person.latestAt)).joinToString(" · ") + source
                    ListRow(MochiAvatar.from(person.peer.avatar), person.peer.nickname, subLine,
                        Modifier.clickable(enabled = LocalDrivingPolicy.current == DrivingPolicy.ALLOWED, role = Role.Button) { onPerson(person.peer.id) }, status) {
                        when {
                            person.incoming -> PrimaryButton("답하기", { onAnswer(person.request!!.encounterId) }, compact = true)
                            !person.connected && person.request?.status != AfterRequestStatus.PENDING && person.encounter?.afterEligibility == AfterEligibility.AVAILABLE ->
                                OutlineButton("애프터 신청", { onAnswer(person.encounter.id) }, compact = true)
                            else -> OutlineButton("인연 보기", { onPerson(person.peer.id) }, compact = true)
                        }
                    }
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
        tab = NavigationItem.RELATIONSHIPS, ratio = .82f, secondaryFirstWhenNarrow = true,
        primary = {
            RelationshipContent(state) {
                if (person == null) EmptyCard("이 인연은 더 이상 보이지 않아요", "기록이 삭제되었거나 지금은 확인할 수 없어요")
                else ContentColumn {
                    if (person.connected) Mascot(MascotPose.KNOT, Modifier.fillMaxWidth().height(ChayeonDimensions.SceneMascot))
                    GlassPanel { ContentColumn {
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
                    if (person.encounter != null) HistoryButton("이 기록 삭제", onDelete, Modifier.fillMaxWidth(), JellyVariant.SECONDARY)
                    HistoryButton("차단·신고", { onSafety(person.peer.id) }, Modifier.fillMaxWidth(), JellyVariant.DANGER)
                    CommandFeedback(state.command("delete"), { onRetry("delete") })
                } } }
            }
        }, secondary = {
            RelationshipContent(state) {
            if (person != null) ContentColumn {
                if (person.connected && !state.contactsProjectionReady) EmptyCard("아이디를 확인하고 있어요", "잠시만 기다려 주세요")
                else if (person.connected) ContentColumn {
                    ChayeonText("서로 애프터를 수락했어요", style = ChayeonType.SceneNavigation, color = ChayeonColors.Accent)
                    HeroHeadline("${person.peer.nickname}님과\n이어졌어요", "서로 고른 아이디로 이야기를 이어가세요", style = ChayeonType.SceneTitle)
                    person.relationship?.let { SharedContactCard(person.peer, it) }
                    InfoLine("아이디는 둘 다 수락한 순간에만 오가요 · 주행 중에는 숨겨요", icon = ChayeonIcon.LOCK)
                    HistoryButton("관계 끝내기", onEnd, Modifier.fillMaxWidth(), JellyVariant.SECONDARY)
                    CommandFeedback(state.command("end:${person.relationship?.id}"), { onRetry("end:${person.relationship?.id}") })
                } else GlassPanel { ContentColumn {
                    SectionTitle(person.label)
                    ChayeonText("서로 아이디를 공유해야 애프터가 이어져요. 수락 전에는 상대에게 보이지 않아요.")
                    val pending = person.request?.takeIf { it.status == AfterRequestStatus.PENDING }
                    if (pending != null) HistoryButton(if (person.incoming) "답하기" else "신청 상태 보기",
                        { onAfter(pending.encounterId) }, Modifier.fillMaxWidth())
                    else person.encounter?.let { encounter ->
                        if (encounter.afterEligibility == AfterEligibility.AVAILABLE)
                            HistoryButton("애프터 신청", { onAfter(encounter.id) }, Modifier.fillMaxWidth())
                        else ChayeonText(encounter.afterEligibility.label, style = ChayeonType.Secondary)
                    }
                } }
            }
            }
        })
    HistoryConfirm(state, onConfirm, onDismiss)
}
