package dev.chayeon.data.social

import dev.chayeon.core.model.ServerTimeAnchor
import dev.chayeon.core.model.ServerTimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class EmptyServerTimeProvider : ServerTimeProvider {
    override val serverTime: Flow<ServerTimeAnchor?> = flowOf(null)

    override fun monotonicTimeMillis(): Long = System.nanoTime() / 1_000_000
}
