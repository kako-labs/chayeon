package dev.chayeon.core.model

import java.util.UUID
import org.junit.Test
import kotlin.test.*

class ContactIdsTest {
    private fun letters(length: Int) = UUID.randomUUID().toString().replace("-", "").repeat(2).take(length)

    @Test fun `empty optional contacts normalize and require explicit exchange consent`() {
        assertEquals(ContactIds(), ContactIds("  ", " @ ").normalized())
        assertTrue(ContactIds().isValid())
        assertFalse(ContactIds().isValid(true))
        assertEquals("ContactIds(redacted)", ContactIds(letters(10), letters(10)).toString())
    }

    @Test fun `KakaoTalk enforces lengths and its exact alphabet`() {
        for (length in listOf(4, 20)) assertTrue(ContactIds(kakaoTalkId = letters(length)).isValid(true))
        for (length in listOf(1, 3, 21)) assertFalse(ContactIds(kakaoTalkId = letters(length)).isValid(true))
        for (suffix in listOf("@", "/", " ", "한", "\n")) assertFalse(ContactIds(kakaoTalkId = letters(5) + suffix + letters(2)).isValid(true))
        val value = letters(8)
        assertEquals(value, ContactIds(kakaoTalkId = " $value ").normalized().kakaoTalkId)
        assertFalse(ContactIds(kakaoTalkId = value.uppercase() + "A").isValid(true))
    }

    @Test fun `Instagram normalizes case and prefix but rejects impossible demo IDs`() {
        for (length in listOf(1, 30)) assertTrue(ContactIds(instagramId = letters(length)).isValid(true))
        assertFalse(ContactIds(instagramId = letters(31)).isValid(true))
        val value = letters(8)
        assertEquals(value, ContactIds(instagramId = " @${value.uppercase()} ").normalized().instagramId)
        for (invalid in listOf(".$value", "$value.", "$value..$value", "$value-$value", "$value/$value", "@@$value", "한$value")) {
            assertFalse(ContactIds(instagramId = invalid).isValid(true))
        }
    }
}
