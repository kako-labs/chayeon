package dev.chayeon.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class DrivingConnectMode { OFF, RING, AUTO }

@Serializable
data class AutoConnectConditions(
    val minimumScores: Map<TraitId, Int> = emptyMap(),
    val requireSharedInterest: Boolean = false,
) {
    fun isValid(): Boolean = minimumScores.all { (trait, score) ->
        trait != TraitId.LONG_ROAD && trait != TraitId.NIGHT_DRIVE && score in 60..100 && score % 5 == 0
    }

    fun matches(peer: PublicProfile, ownInterests: Set<InterestId>): Boolean = isValid() &&
        (!requireSharedInterest || peer.interests.any { it in ownInterests }) &&
        minimumScores.all { (trait, minimum) ->
            peer.signature?.traits?.any { it.trait == trait && it.level != TraitLevel.RECORDING &&
                it.score != null && it.score in minimum..100 } == true
        }

    fun exceededCount(peer: PublicProfile): Int = minimumScores.count { (trait, minimum) ->
        peer.signature?.traits?.any { it.trait == trait && (it.score ?: 0) > minimum } == true
    }
}

/** Service-only public data. Never contains contact IDs or private trip evidence. */
data class AutoConnectSnapshot(
    val accountId: String,
    val settings: ParticipationSettings,
    val ownInterests: Set<InterestId>,
    val discovery: DiscoveryState,
    val requests: List<ConnectionRequest>,
    val connections: List<Connection>,
    val blockedProfileIds: Set<String>,
)
