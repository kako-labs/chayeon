package dev.chayeon.core.signature

import dev.chayeon.core.model.TraitId
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SignatureLanguageTest {
    @Test fun `every permitted modifier and noun resolves to its modifier trait`() {
        assertEquals(TraitId.entries.toSet(), SignatureLanguage.modifiers.keys)
        SignatureLanguage.modifiers.forEach { (trait, modifier) ->
            SignatureLanguage.nouns.forEach { noun ->
                assertEquals(trait.takeIf { noun in SignatureLanguage.nounsFor(trait) },
                    SignatureLanguage.titleTrait("$modifier $noun"))
            }
        }
    }

    @Test fun `recording unknown and partial titles have no trait`() {
        listOf(SignatureLanguage.RECORDING_TITLE, "", "깜빡이를 잊지 않는", "깜빡이를 잊지 않는 다른 사람",
            "깜빡이를 잊지 않는 드라이버 추가").forEach { assertNull(SignatureLanguage.titleTrait(it)) }
    }
}
