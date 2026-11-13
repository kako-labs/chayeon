package dev.chayeon.core.engine

import dev.chayeon.core.model.*
import org.junit.Test
import kotlin.test.*

class PublicScoreValidationTest {
    @Test fun `scored traits require sixty through one hundred with an agreeing stage`() {
        val f = Fixture(); f.join("a")
        for (score in listOf(null, -1, 0, 59, 101, Int.MAX_VALUE)) {
            assertEquals(AppError.Restricted, publish(f, PublicTrait(TraitId.SIGNAL_MANNERS, TraitLevel.VISIBLE, score)).error())
            assertNull(f.snap("a").profile!!.publicProfile.signature)
        }
        for ((score, level) in listOf(60 to TraitLevel.VISIBLE, 74 to TraitLevel.VISIBLE,
            75 to TraitLevel.STEADY, 89 to TraitLevel.STEADY, 90 to TraitLevel.SIGNATURE, 100 to TraitLevel.SIGNATURE)) {
            assertEquals(score, publish(f, PublicTrait(TraitId.SIGNAL_MANNERS, level, score)).ok().publicProfile.signature!!.traits.single().score)
        }
        assertEquals(AppError.Restricted, publish(f, PublicTrait(TraitId.SIGNAL_MANNERS, TraitLevel.SIGNATURE, 60)).error())
    }

    @Test fun `preference traits reject every numeric score and retain badges`() {
        val f = Fixture(); f.join("a")
        for (trait in listOf(TraitId.LONG_ROAD, TraitId.NIGHT_DRIVE)) {
            for (score in listOf(0, 60, 100)) {
                assertEquals(AppError.Restricted, publish(f, PublicTrait(trait, TraitLevel.SIGNATURE, score)).error())
            }
            assertNull(publish(f, PublicTrait(trait, TraitLevel.SIGNATURE)).ok().publicProfile.signature!!.traits.single().score)
        }
    }

    private fun publish(f: Fixture, trait: PublicTrait) = f.engine.publishSignature("a",
        PublishSignatureRequest(f.meta(), PublicSignature("input", 1, "", listOf(trait), KnotSeed(0), DataSource.SYNTHETIC, EvidenceBucket.GROWING)))
}
