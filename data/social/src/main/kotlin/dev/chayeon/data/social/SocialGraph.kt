package dev.chayeon.data.social

import android.content.Context
import android.os.SystemClock
import dev.chayeon.core.engine.SignatureRules
import dev.chayeon.core.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/** App wiring supplies the vehicle policy and a cleanup hook for device-only signature data. */
class SocialGraph(
    context: Context,
    serverUrl: String?,
    rules: SignatureRules,
    localCleanup: suspend () -> AppResult<Unit>,
    drivingStateProvider: DrivingStateProvider? = null,
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val backends = buildMap<SessionMode, SocialBackend> {
        put(SessionMode.SOLO, LocalBackend(context.applicationContext, rules))
        if (!serverUrl.isNullOrBlank()) {
            put(SessionMode.SERVER_DEMO, RemoteBackend(serverUrl,
                PrivateSessionStorage(context.applicationContext, "remote-session.json", serverUrl.trimEnd('/'))))
        }
    }
    private val coordinator = SocialCoordinator(backends, scope, {
        val dormantSessionCleanup = localResult { PrivateSessionStorage(context.applicationContext, "remote-session.json").clear() }
        val deviceCleanup = localCleanup()
        if (dormantSessionCleanup is AppResult.Failure) dormantSessionCleanup else deviceCleanup
    }, SystemClock::elapsedRealtime, drivingStateProvider, PrivateCleanupStorage(context.applicationContext))

    val sessionRepository: SessionRepository = SocialSessionRepository(coordinator, backends.keys)
    val profileRepository: ProfileRepository = SocialProfileRepository(coordinator)
    val discoveryRepository: DiscoveryRepository = SocialDiscoveryRepository(coordinator)
    val connectionRepository: ConnectionRepository = SocialConnectionRepository(coordinator)
    val encounterRepository: EncounterRepository = SocialEncounterRepository(coordinator)
    val afterRepository: AfterRepository = SocialAfterRepository(coordinator)
    val safetyRepository: SafetyRepository = SocialSafetyRepository(coordinator)
    val serverTimeProvider: ServerTimeProvider = SocialServerTimeProvider(coordinator)

    fun bindDrivingState(provider: DrivingStateProvider) = coordinator.bindDrivingState(provider)

    override fun close() {
        coordinator.close()
        scope.cancel()
    }
}
