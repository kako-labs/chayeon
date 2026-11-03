package dev.chayeon.feature.signature

import dev.chayeon.core.model.AppError
import dev.chayeon.core.model.AppResult
import dev.chayeon.core.model.CommandMeta
import dev.chayeon.core.model.DataSource
import dev.chayeon.core.model.DrivingPolicy
import dev.chayeon.core.model.EvidenceBucket
import dev.chayeon.core.model.LoadState
import dev.chayeon.core.model.MySignature
import dev.chayeon.core.model.PublicSignature
import dev.chayeon.core.model.PublicTrait
import dev.chayeon.core.model.TraitAvailability
import dev.chayeon.core.model.TraitLevel
import dev.chayeon.core.model.VisibilitySettings
import dev.chayeon.core.signature.DefaultSignatureEngine
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart

internal val signatureSharing = SharingStarted.WhileSubscribed(
    stopTimeoutMillis = 5_000,
    replayExpirationMillis = 0,
)

internal fun commandMeta(expectedVersion: Long? = null) = CommandMeta(UUID.randomUUID().toString(), expectedVersion)

internal suspend fun SignatureDeps.allowedNow(): Boolean = try {
    drivingStateProvider.drivingState.first().policy == DrivingPolicy.ALLOWED
} catch (error: CancellationException) {
    throw error
} catch (_: Exception) {
    false
}

internal suspend fun <T> commandResult(block: suspend () -> AppResult<T>): AppResult<T> = try {
    block()
} catch (error: CancellationException) {
    throw error
} catch (_: Exception) {
    AppResult.Failure(AppError.Unknown)
}

internal suspend fun <T> Flow<LoadState<T>>.currentObservation(): LoadState<T> = try {
    first()
} catch (error: CancellationException) {
    throw error
} catch (_: Exception) {
    LoadState.Failed(AppError.Unknown)
}

@OptIn(ExperimentalCoroutinesApi::class)
internal fun <T> observeAgain(retry: MutableStateFlow<Int>, source: () -> Flow<LoadState<T>>): Flow<LoadState<T>> =
    retry.flatMapLatest {
        source().onStart { emit(LoadState.Loading()) }.catch { emit(LoadState.Failed(AppError.Unknown)) }
    }

internal fun signaturePreview(signature: MySignature, visibility: VisibilitySettings): PublicSignature? {
    if (!visibility.publishSignature) return null
    val engine = DefaultSignatureEngine()
    val evidence = signature.evidence.filter {
        it.trait in visibility.visibleTraits && it.availability == TraitAvailability.AVAILABLE &&
            it.level != TraitLevel.RECORDING
    }.sortedBy { it.trait.ordinal }
    val traits = evidence.map { PublicTrait(it.trait, it.level) }
    if (traits.isEmpty()) return null
    val source = if (evidence.any { it.source == DataSource.SYNTHETIC }) DataSource.SYNTHETIC else DataSource.VEHICLE
    val projected = engine.publicProjection(signature.copy(visibility = visibility))
    if (projected != null && projected.traits.sortedBy { it.trait.ordinal } == traits && projected.source == source) return projected
    return PublicSignature(
        id = projected?.id ?: signature.id,
        version = signature.version,
        title = engine.title(traits),
        traits = traits,
        knotSeed = engine.knot(evidence),
        source = source,
        evidenceBucket = projected?.evidenceBucket?.takeUnless { it == EvidenceBucket.RECORDING } ?: EvidenceBucket.GROWING,
    )
}

internal fun VisibilitySettings.sameSelection(other: VisibilitySettings): Boolean =
    visibleTraits == other.visibleTraits && publishSignature == other.publishSignature
