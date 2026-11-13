package dev.chayeon.feature.history

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.chayeon.core.model.*
import dev.chayeon.core.ui.*

@Composable
internal fun ContactInputs(contacts: ContactIds, onContacts: (ContactIds) -> Unit, enabled: Boolean = true) {
    ContentColumn {
        GelTextField(contacts.kakaoTalkId.orEmpty(), { onContacts(contacts.copy(kakaoTalkId = it)) }, "카카오톡 아이디 · 선택",
            placeholder = "영문·숫자 등 4~20자", maxLength = 20,
            error = if (!ContactIds(kakaoTalkId = contacts.kakaoTalkId).isValid()) "영문·숫자·밑줄·하이픈·마침표로 4~20자 입력해 주세요" else null,
            enabled = enabled)
        GelTextField(contacts.instagramId.orEmpty(), { onContacts(contacts.copy(instagramId = it)) }, "인스타그램 아이디 · 선택",
            placeholder = "@ 없이 아이디만", maxLength = 31,
            error = if (!ContactIds(instagramId = contacts.instagramId).isValid()) "영문·숫자·밑줄·마침표로 1~30자 입력해 주세요" else null,
            enabled = enabled)
    }
}

@Composable
internal fun ContactSelection(state: HistoryUiState, onContacts: (ContactIds) -> Unit, onSaveToProfile: (Boolean) -> Unit) {
    val saved = state.myProfile?.contacts?.normalized() ?: ContactIds()
    val hasSaved = saved.kakaoTalkId != null || saved.instagramId != null
    val busy = state.command("request").running || state.requests.valueOrNull().orEmpty().any { state.command("respond:${it.id}").running }
    ContentColumn {
        SectionTitle("공유할 아이디를 골라 주세요", subtitle = "하나 이상 골라야 애프터를 이어갈 수 있어요")
        if (hasSaved) {
            saved.kakaoTalkId?.let { id -> GelCheckbox("카카오톡 · $id", state.shareContacts.kakaoTalkId != null,
                { onContacts(state.shareContacts.copy(kakaoTalkId = if (it) id else null)) }, enabled = !busy) }
            saved.instagramId?.let { id -> GelCheckbox("인스타그램 · @$id", state.shareContacts.instagramId != null,
                { onContacts(state.shareContacts.copy(instagramId = if (it) id else null)) }, enabled = !busy) }
        } else {
            ContactInputs(state.shareContacts, onContacts, enabled = !busy)
            GelCheckbox("다음에도 쓸 수 있게 내 프로필에 저장", state.saveContactsToProfile, onSaveToProfile,
                description = "프로필에 저장해도 공개 카드에는 보이지 않아요", enabled = !busy)
        }
        state.contactError?.let { ChayeonText(it, style = ChayeonType.Minimum, color = ChayeonColors.Pink) }
        ChayeonText("한 번 공유한 아이디는 되돌릴 수 없어요.", style = ChayeonType.Minimum)
    }
}
