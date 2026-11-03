package dev.chayeon.core.ui

import dev.chayeon.core.model.*

internal val gallerySignature = PublicSignature(
    "gallery-signature", 1, "깜빡이를 잊지 않는 밤 드라이버",
    listOf(PublicTrait(TraitId.SIGNAL_MANNERS, TraitLevel.SIGNATURE), PublicTrait(TraitId.NIGHT_DRIVE, TraitLevel.STEADY)),
    KnotSeed(2357933), DataSource.SYNTHETIC, EvidenceBucket.ESTABLISHED,
)
internal val galleryPeer = PublicProfile(
    "gallery-peer", 1, "느린오후", AvatarId.SLOW,
    setOf(InterestId.JAZZ, InterestId.NIGHT_VIEW, InterestId.EXHIBITIONS, InterestId.FILM_PHOTOGRAPHY),
    ProfileKind.VIRTUAL_PROFILE, gallerySignature,
)
internal val galleryChemistry = Chemistry(listOf(
    ChemistryLine(ChemistryKind.SHARED, TraitId.SIGNAL_MANNERS, TraitId.SIGNAL_MANNERS, "둘 다 깜빡이를 챙겨요"),
    ChemistryLine(ChemistryKind.SHARED, TraitId.NIGHT_DRIVE, TraitId.NIGHT_DRIVE, "둘 다 밤 드라이브를 좋아해요"),
))
internal val galleryCandidate = Candidate("gallery-candidate", 1, galleryPeer, galleryChemistry, setOf(InterestId.JAZZ, InterestId.NIGHT_VIEW))
