package dev.chayeon.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.chayeon.core.model.ExperienceState

private data class StatePresentation(val section: String, val tag: String, val title: String, val body: String, val action: String?, val icon: ChayeonIcon, val warning: Boolean = false)

private fun ExperienceState.presentation(): StatePresentation = when (this) {
    ExperienceState.DISCOVERY_LOADING -> StatePresentation("발견", "준비 중", "근처 참여자를 찾고 있어요", "잠시 후 카드가 나타나요. 내 카드도 이때부터 보여요.", null, ChayeonIcon.REFRESH)
    ExperienceState.LOCATION_PERMISSION_OFF -> StatePresentation("위치", "권한 꺼짐", "위치 사용이 꺼져 있어요", "위치가 없으면 근처 사람을 찾을 수 없어요. 내 카드도 보이지 않아요.", "설정 열기", ChayeonIcon.PIN, true)
    ExperienceState.LOCATION_STALE -> StatePresentation("위치", "오래됨", "위치 정보가 오래됐어요", "새 위치를 받을 때까지 카드를 보여주지 않아요.", "다시 확인", ChayeonIcon.CLOCK, true)
    ExperienceState.NO_CANDIDATES -> StatePresentation("오늘의 인연", "후보 없음", "지금은 근처에 아무도 없어요", "발견을 켜두면 새 참여자가 생길 때 보여드려요.", null, ChayeonIcon.PEOPLE)
    ExperienceState.NETWORK_FAILURE -> StatePresentation("통신", "연결 실패", "서버에 연결하지 못했어요", "네트워크를 확인하고 다시 시도하세요. 이전 카드는 보여주지 않아요.", "다시 시도", ChayeonIcon.OFFLINE, true)
    ExperienceState.DRIVING_RESTRICTED -> StatePresentation("주행 상태", "주행 제한", "주차하면 볼 수 있어요", "주행 중에는 카드 보기, 요청, 수락을 막아요.", null, ChayeonIcon.CAR, true)
    ExperienceState.DRIVING_UNKNOWN -> StatePresentation("주행 상태", "확인 불가", "주행 상태를 확인하지 못했어요", "확인될 때까지 주행 중과 똑같이 조작을 막아요.", null, ChayeonIcon.WARNING, true)
    ExperienceState.ALREADY_CONNECTED -> StatePresentation("연결", "이미 연결 중", "한 번에 한 사람과 연결돼요", "지금 연결을 마치면 다시 요청할 수 있어요.", "연결 보기", ChayeonIcon.THREAD)
    ExperienceState.PROFILE_SAVE_FAILED -> StatePresentation("프로필", "저장 실패", "프로필을 저장하지 못했어요", "입력한 내용은 그대로 있어요. 다시 저장해보세요.", "다시 저장", ChayeonIcon.EDIT, true)
    ExperienceState.REQUEST_DECLINED -> StatePresentation("보낸 요청", "거절됨", "이번에는 연결되지 않았어요", "이유는 전달되지 않아요. 같은 사람에게 바로 다시 요청할 수 없어요.", null, ChayeonIcon.CLOSE)
    ExperienceState.REQUEST_EXPIRED -> StatePresentation("보낸 요청", "만료됨", "응답 시간이 지나 요청이 끝났어요", "연결은 만들어지지 않았고 기록에도 남지 않아요.", "목록으로", ChayeonIcon.CLOCK)
    ExperienceState.REQUEST_CANCELLED -> StatePresentation("받은 요청", "취소됨", "상대가 요청을 취소했어요", "수락할 수 없어요. 다른 사람과 자동으로 연결하지 않아요.", null, ChayeonIcon.BACK)
    ExperienceState.PEER_UNAVAILABLE -> StatePresentation("수락 시점", "연결 불가", "지금은 연결할 수 없어요", "지금은 상대와 연결할 수 없어요. 다른 참여자를 확인해보세요.", "목록으로", ChayeonIcon.PEOPLE, true)
    ExperienceState.MESSAGE_SEND_FAILED -> StatePresentation("인사", "전송 실패", "인사를 보내지 못했어요", "연결은 유지 중이에요. 한 번 더 눌러 다시 보낼 수 있어요.", "다시 보내기", ChayeonIcon.CHAT, true)
    ExperienceState.CONNECTION_CHECKING -> StatePresentation("일시 연결", "확인 중", "연결 상태를 확인하고 있어요", "위치나 통신이 끊겼어요. 멀어졌다고 판단한 것은 아니에요.", null, ChayeonIcon.WARNING, true)
    ExperienceState.AFTER_DECLINED -> StatePresentation("애프터", "거절됨", "애프터가 이어지지 않았어요", "스친 인연 기록은 남아요. 한동안 다시 신청할 수 없어요.", null, ChayeonIcon.KNOT)
    ExperienceState.AFTER_EXPIRED -> StatePresentation("애프터", "만료됨", "응답 없이 신청이 끝났어요", "새 대화는 열리지 않았어요.", null, ChayeonIcon.CLOCK)
    ExperienceState.AFTER_WITHDRAWN -> StatePresentation("애프터", "철회됨", "상대가 신청을 철회했어요", "수락할 수 없어요. 기록은 그대로 남아요.", null, ChayeonIcon.CUT)
}

@Composable
fun StateCard(state: ExperienceState, modifier: Modifier = Modifier, onAction: (() -> Unit)? = null) {
    val presentation = state.presentation()
    GlassCard(modifier.heightIn(min = ChayeonDimensions.StateCard)) {
        Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Compact)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap)) {
                IconOrb(presentation.icon, Modifier.size(ChayeonDimensions.StateOrb), if (presentation.warning) OrbVariant.WHITE else OrbVariant.GLASS)
                ChayeonText(presentation.section, Modifier.weight(1f), ChayeonType.Label, ChayeonColors.Secondary)
                StatusChip(presentation.tag, variant = if (presentation.warning) StatusVariant.WARNING else StatusVariant.PLAIN)
            }
            ChayeonText(presentation.title, style = ChayeonType.Section)
            ChayeonText(presentation.body, style = ChayeonType.Secondary, color = ChayeonColors.Secondary)
        }
        if (presentation.action != null && onAction != null) {
            Spacer(Modifier.weight(1f))
            JellyButton(presentation.action, onAction, Modifier.fillMaxWidth(), JellyVariant.SECONDARY)
        }
    }
}

@Composable
fun ChatBubble(text: String, mine: Boolean, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(modifier.gelSurface(if (mine) JellyVariant.PRIMARY else JellyVariant.SECONDARY, ChayeonRadii.Card)
        .padding(horizontal = ChayeonSpacing.BubbleHorizontal, vertical = ChayeonSpacing.Gap)) {
        ChayeonText(text, style = ChayeonType.Body)
    }
}

@Composable
fun PresetButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    JellyButton(text, onClick, modifier, JellyVariant.SECONDARY, enabled)
}
