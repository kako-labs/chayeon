package dev.chayeon.core.model

import java.util.Locale
import kotlinx.serialization.Serializable

@Serializable
data class ContactIds(val kakaoTalkId: String? = null, val instagramId: String? = null) {
    fun normalized(): ContactIds = ContactIds(
        kakaoTalkId?.trim()?.takeIf { it.isNotEmpty() },
        instagramId?.trim()?.removePrefix("@")?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() },
    )

    fun isValid(requireAtLeastOne: Boolean = false): Boolean {
        val value = normalized()
        return (!requireAtLeastOne || value.kakaoTalkId != null || value.instagramId != null) &&
            (value.kakaoTalkId == null || Regex("[a-z0-9_.-]{4,20}").matches(value.kakaoTalkId)) &&
            (value.instagramId == null || (Regex("[a-z0-9._]{1,30}").matches(value.instagramId) &&
                !value.instagramId.startsWith(".") && !value.instagramId.endsWith(".") && !value.instagramId.contains("..")))
    }

    override fun toString(): String = "ContactIds(redacted)"
}
