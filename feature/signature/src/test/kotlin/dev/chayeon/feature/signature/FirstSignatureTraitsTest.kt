package dev.chayeon.feature.signature

import dev.chayeon.core.model.*
import dev.chayeon.core.signature.DefaultSignatureEngine
import dev.chayeon.core.signature.SignatureLanguage
import java.io.File
import kotlinx.serialization.json.*
import org.junit.Test
import kotlin.test.*

class FirstSignatureTraitsTest {
    @Test fun `all three demo scenarios show title then earned care then highest remaining level`() {
        val root = generateSequence(File(checkNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { File(it, "fixtures/drive").isDirectory }
        val scenarios = Json.parseToJsonElement(File(root, "core/signature/src/test/resources/demo-results.json").readText()).jsonArray
        val expected = mapOf(
            "gentle" to listOf(TraitId.GENTLE_RIDE, TraitId.TIRE_CARE, TraitId.EARLY_BRAKE),
            "signal-manners" to listOf(TraitId.SIGNAL_MANNERS, TraitId.ENERGY_PLANNER, TraitId.GENTLE_RIDE),
            "care" to listOf(TraitId.TIRE_CARE, TraitId.GENTLE_RIDE, TraitId.EARLY_BRAKE),
        )
        assertEquals(expected.keys, scenarios.map { it.jsonObject.getValue("id").jsonPrimitive.content }.toSet())
        scenarios.forEach { scenario ->
            val data = scenario.jsonObject
            val name = data.getValue("id").jsonPrimitive.content
            val traits = Json.decodeFromJsonElement<List<PublicTrait>>(data.getValue("traits"))
            val title = DefaultSignatureEngine().title(traits)
            assertEquals(data.getValue("title").jsonPrimitive.content, title)
            val changes = traits.map { TraitChange(it.trait, TraitLevel.RECORDING, it.level, "") }
            val shown = firstSignatureTraits(changes, title)
            assertEquals(expected.getValue(name), shown.map { it.trait }, name)
            assertEquals(SignatureLanguage.titleTrait(title), shown.first().trait)
            assertEquals(shown, firstSignatureTraits(changes.reversed(), title))
        }
    }

    @Test fun `missing care and recording traits do not invent or duplicate rows`() {
        val changes = listOf(
            TraitChange(TraitId.GENTLE_RIDE, TraitLevel.RECORDING, TraitLevel.VISIBLE, ""),
            TraitChange(TraitId.EARLY_BRAKE, TraitLevel.RECORDING, TraitLevel.STEADY, ""),
            TraitChange(TraitId.TIRE_CARE, TraitLevel.RECORDING, TraitLevel.RECORDING, ""),
        )
        assertEquals(listOf(TraitId.EARLY_BRAKE, TraitId.GENTLE_RIDE),
            firstSignatureTraits(changes, "여유롭게 멈추는 드라이버").map { it.trait })
        assertTrue(firstSignatureTraits(emptyList(), SignatureLanguage.RECORDING_TITLE).isEmpty())
    }
}
