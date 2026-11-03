package dev.chayeon.core.engine

import dev.chayeon.core.model.Chemistry
import dev.chayeon.core.model.PublicSignature
import dev.chayeon.core.model.PublicTrait

/** Signature rules the engine needs. The composition root supplies them from core:signature. */
interface SignatureRules {
    fun chemistry(first: PublicSignature, second: PublicSignature): Chemistry

    /** The only title a published signature may carry, so a title can never become free text. */
    fun title(traits: List<PublicTrait>): String
}
