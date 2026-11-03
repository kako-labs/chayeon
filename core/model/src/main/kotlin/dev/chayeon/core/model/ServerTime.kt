package dev.chayeon.core.model

/** Local-only receipt time; the monotonic clock must be the provider's clock. */
data class ServerTimeAnchor(
    val serverEpochMillis: Long,
    val receivedAtMonotonicMillis: Long,
) {
    init {
        require(serverEpochMillis >= 0)
        require(receivedAtMonotonicMillis >= 0)
    }

    /** A zero countdown does not establish the server's terminal request state. */
    fun remainingMillisUntil(deadlineEpochMillis: Long, nowMonotonicMillis: Long): Long? {
        if (deadlineEpochMillis < 0 || nowMonotonicMillis < receivedAtMonotonicMillis) return null
        val elapsed = nowMonotonicMillis - receivedAtMonotonicMillis
        val remainingAtReceipt = deadlineEpochMillis - serverEpochMillis
        return if (remainingAtReceipt <= elapsed) 0 else remainingAtReceipt - elapsed
    }
}
