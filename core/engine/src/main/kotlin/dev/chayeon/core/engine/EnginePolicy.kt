package dev.chayeon.core.engine

import dev.chayeon.core.model.ParticipationPolicy

data class EnginePolicy(
    val requestTtlMillis: Long = 120_000,
    val afterTtlMillis: Long = 86_400_000,
    val requestCooldownMillis: Long = 30_000,
    val afterCooldownMillis: Long = 86_400_000,
    val enterRadiusMeters: Double = 200.0,
    val exitRadiusMeters: Double = 300.0,
    val exitGraceMillis: Long = 10_000,
    val statusUnknownGraceMillis: Long = 30_000,
    val locationFreshnessMillis: Long = 30_000,
    val maximumInterests: Int = 5,
    val nicknameMinLength: Int = 2,
    val nicknameMaxLength: Int = 12,
    val maximumCommandIdLength: Int = 128,
    val maximumCachedCommands: Int = 512,
    val consentDocumentVersion: String = "2026-11-03",
    val presenceLeaseMillis: Long? = null,
) {
    init {
        require(requestTtlMillis > 0 && afterTtlMillis > 0)
        require(presenceLeaseMillis == null || presenceLeaseMillis > 0)
        require(requestCooldownMillis >= 0 && afterCooldownMillis >= 0)
        require(enterRadiusMeters > 0 && exitRadiusMeters > enterRadiusMeters)
        require(exitGraceMillis > 0 && statusUnknownGraceMillis > 0 && locationFreshnessMillis > 0)
        require(nicknameMinLength > 0 && nicknameMaxLength >= nicknameMinLength && maximumInterests > 0)
        require(maximumCommandIdLength > 0 && maximumCachedCommands > 0)
    }

    fun participation(version: Long) = ParticipationPolicy(
        "participation-policy", version, maximumInterests, nicknameMinLength, nicknameMaxLength,
        reportRetentionDays = null, consentDocumentVersion = consentDocumentVersion,
    )
}
