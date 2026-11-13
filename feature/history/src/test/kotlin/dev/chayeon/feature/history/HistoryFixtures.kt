package dev.chayeon.feature.history

import dev.chayeon.core.model.*

internal object HistoryFixtures {
    val policy = ParticipationPolicy("policy", 1, 5, 2, 12, null, "1")
    val session = Session("session", 1, "account-a", SessionMode.SOLO, null, null, null, policy)
    val signature = PublicSignature("signature", 1, "여유롭게 멈추는 드라이버", listOf(PublicTrait(TraitId.EARLY_BRAKE, TraitLevel.STEADY)), KnotSeed(42), DataSource.SYNTHETIC, EvidenceBucket.ESTABLISHED)
    val peer = PublicProfile("peer", 1, "느린오후", AvatarId.SLOW, setOf(InterestId.JAZZ, InterestId.COFFEE), ProfileKind.DEMO_PEER, signature)
    val mine = peer.copy(id = "mine", nickname = "새벽드라이브", avatar = AvatarId.DAWN, kind = ProfileKind.VIRTUAL_PROFILE)
    val profile = MyProfile("profile", 1, mine, RegistrationState.READY)
    val emptyProfile = profile.copy(publicProfile = mine.copy(nickname = "", interests = emptySet(), signature = null),
        registration = RegistrationState.PROFILE_REQUIRED, contacts = ContactIds())
    val chemistry = Chemistry(listOf(ChemistryLine(ChemistryKind.SHARED, TraitId.EARLY_BRAKE, TraitId.EARLY_BRAKE, "둘 다 여유롭게 멈춰요")))
    val detailedChemistry = Chemistry(chemistry.lines + listOf(
        ChemistryLine(ChemistryKind.SHARED, TraitId.LONG_ROAD, TraitId.LONG_ROAD, "둘 다 긴 드라이브를 좋아해요"),
        ChemistryLine(ChemistryKind.SHARED, TraitId.CABIN_CARE, TraitId.CABIN_CARE, "둘 다 함께 타는 공기까지 신경 써요"),
    ))
    val detailedSignature = signature.copy(traits = signature.traits + listOf(
        PublicTrait(TraitId.LONG_ROAD, TraitLevel.VISIBLE), PublicTrait(TraitId.CABIN_CARE, TraitLevel.VISIBLE),
    ))
    val encounter = Encounter("encounter", 1, "connection", peer, 1_799_999_000_000, 1_800_000_080_000, ConnectionEndReason.DISTANCE, chemistry, setOf(InterestId.JAZZ), AfterEligibility.AVAILABLE)
    val request = AfterRequest("request", 1, encounter.id, peer, RequestDirection.INCOMING, AfterRequestStatus.PENDING, 1_800_000_080_000, 1_800_086_480_000, chemistry)
    val relationship = AfterConnection("after", 1, request.id, encounter.id, peer, AfterConnectionStatus.CONNECTED, 1_800_000_080_000, chemistry, sharedContacts = ContactIds(instagramId = "demo-contact"), ownSharedContacts = ContactIds(instagramId = "demo-own"))
    val connection = Connection("connection", 1, "connection-request", peer, ConnectionStatus.CONNECTED, 1_799_999_000_000, chemistry)
    val settings = ParticipationSettings("settings", 1)
    val block = Block("block", 1, peer, 1_800_000_000_000)
    val state = HistoryUiState(
        driving = DrivingState(policy = DrivingPolicy.ALLOWED, gear = Gear.PARK), session = SessionState.Active(session),
        modes = setOf(SessionMode.SOLO, SessionMode.SERVER_DEMO),
        profile = LoadState.Ready(profile, 1), settings = LoadState.Ready(settings, 1),
        encounters = LoadState.Ready(listOf(encounter), 1), encounter = LoadState.Ready(encounter, 1),
        requests = LoadState.Ready(listOf(request.copy(status = AfterRequestStatus.ACCEPTED)), 1), relationships = LoadState.Ready(listOf(relationship), 1),
        selectedRelationshipId = relationship.id, targetEncounterId = encounter.id,
        targetPeerId = peer.id, blocks = LoadState.Ready(emptyList(), 1), connection = LoadState.Ready(connection, 1),
        discovery = DiscoveryState(mode = DiscoveryMode.ON), draft = ProfileDraft(mine.nickname, mine.avatar, mine.interests), draftLoaded = true,
        remainingMillis = mapOf(request.id to 82_800_000L),
    )
}
