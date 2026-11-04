package dev.chayeon.feature.meet

import dev.chayeon.core.model.*
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

internal val meetSharing = SharingStarted.WhileSubscribed(
    stopTimeoutMillis = 5_000,
    replayExpirationMillis = 0,
)

internal fun freshMeta(version: Long? = null) = CommandMeta(UUID.randomUUID().toString(), version)

internal suspend fun MeetDeps.allowsInteraction(): Boolean =
    withTimeoutOrNull(1_000) { drivingStateProvider.drivingState.first().policy } == DrivingPolicy.ALLOWED

internal fun LoadState<MyProfile?>.publicProfile(): PublicProfile? =
    (this as? LoadState.Ready)?.value?.publicProfile

internal fun <T> LoadState<T>.readyValue(): T? = (this as? LoadState.Ready)?.value

/** Only uncertain delivery retains a payload and its idempotency key. */
internal class MeetCommand<T> {
    private val mutableState = MutableStateFlow(MeetCommandState())
    val state: StateFlow<MeetCommandState> = mutableState.asStateFlow()
    private var retryPayload: T? = null

    fun launch(
        scope: CoroutineScope,
        prepare: suspend () -> T?,
        permitted: suspend () -> Boolean,
        execute: suspend (T) -> AppResult<*>,
        isSameTarget: suspend (T) -> Boolean = { true },
        onSuccess: (Any?) -> Unit = {},
    ) {
        if (mutableState.value.inProgress) return
        mutableState.value = MeetCommandState(inProgress = true)
        scope.launch {
            try {
                if (!permitted()) {
                    retryPayload = null
                    mutableState.value = MeetCommandState(error = AppError.Restricted)
                    return@launch
                }
                val retained = retryPayload
                if (retained != null && !isSameTarget(retained)) retryPayload = null
                val payload = retryPayload ?: prepare()
                if (payload == null) {
                    retryPayload = null
                    mutableState.value = MeetCommandState(error = AppError.AlreadyClosed)
                    return@launch
                }
                retryPayload = payload
                if (!permitted()) {
                    retryPayload = null
                    mutableState.value = MeetCommandState(error = AppError.Restricted)
                    return@launch
                }
                when (val result = execute(payload)) {
                    is AppResult.Success -> {
                        retryPayload = null
                        mutableState.value = MeetCommandState()
                        onSuccess(result.value)
                    }
                    is AppResult.Failure -> {
                        if (result.error !in setOf(AppError.Network, AppError.Unknown)) retryPayload = null
                        mutableState.value = MeetCommandState(error = result.error)
                    }
                }
            } catch (cancelled: CancellationException) {
                mutableState.value = MeetCommandState()
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = MeetCommandState(error = AppError.Unknown)
            }
        }
    }

    fun discardRetry() {
        if (!state.value.inProgress) retryPayload = null
    }
}
