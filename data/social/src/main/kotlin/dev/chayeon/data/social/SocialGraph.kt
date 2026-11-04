package dev.chayeon.data.social

import android.content.Context
import android.os.SystemClock
import dev.chayeon.core.engine.SignatureRules
import dev.chayeon.core.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged

/** App wiring supplies the vehicle policy and a cleanup hook for device-only signature data. */
class SocialGraph(
    context: Context,
    serverUrl: String?,
    rules: SignatureRules,
    localCleanup: suspend () -> AppResult<Unit>,
    drivingStateProvider: DrivingStateProvider? = null,
    prepareSession: suspend (Session, suspend (SetSyntheticPresenceRequest, suspend () -> Boolean) -> AppResult<CommandReceipt>) -> Unit = { _, _ -> },
    canStartConnection: suspend () -> Boolean = { true },
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val backends = buildMap<SessionMode, SocialBackend> {
        put(SessionMode.SOLO, LocalBackend(context.applicationContext, rules))
        if (!serverUrl.isNullOrBlank()) {
            put(SessionMode.SERVER_DEMO, RemoteBackend(serverUrl,
                PrivateSessionStorage(context.applicationContext, "remote-session.json", serverUrl.trimEnd('/')),
                secretStorage = PrivateClientSecretStorage(context.applicationContext)))
        }
    }
    private val coordinator = SocialCoordinator(backends, scope, {
        val dormantSessionCleanup = localResult { PrivateSessionStorage(context.applicationContext, "remote-session.json").clear() }
        val deviceCleanup = localCleanup()
        if (dormantSessionCleanup is AppResult.Failure) dormantSessionCleanup else deviceCleanup
    }, SystemClock::elapsedRealtime, drivingStateProvider, PrivateCleanupStorage(context.applicationContext), prepareSession)

    val sessionRepository: SessionRepository = SocialSessionRepository(coordinator, backends.keys)
    val profileRepository: ProfileRepository = SocialProfileRepository(coordinator)
    val discoveryRepository: DiscoveryRepository = SocialDiscoveryRepository(coordinator)
    val connectionRepository: ConnectionRepository = SocialConnectionRepository(coordinator, canStartConnection)
    val encounterRepository: EncounterRepository = SocialEncounterRepository(coordinator)
    val afterRepository: AfterRepository = SocialAfterRepository(coordinator)
    val safetyRepository: SafetyRepository = SocialSafetyRepository(coordinator)
    val serverTimeProvider: ServerTimeProvider = SocialServerTimeProvider(coordinator)

    // Service consent must remain observable while the UI is driving-restricted.
    val recordingSettings = coordinator.store.state.map { state ->
        when (val data = state.data) {
            is LoadState.Ready -> LoadState.Ready(data.value.settings ?: ParticipationSettings("settings", data.version), data.version)
            is LoadState.Loading -> data
            is LoadState.Failed -> data
        }
    }

    // Only opaque event identifiers leave this service channel; no message or profile content.
    val messageSignals = coordinator.store.state.map { state ->
        val snapshot = (state.data as? LoadState.Ready)?.value
        buildList {
            if (snapshot?.settings?.notifyRequests == true) {
                addAll(snapshot.connectionRequests.filter { it.direction == RequestDirection.INCOMING && it.status == ConnectionRequestStatus.PENDING }.map { it.id })
                addAll(snapshot.afterRequests.filter { it.direction == RequestDirection.INCOMING && it.status == AfterRequestStatus.PENDING }.map { it.id })
            }
            if (snapshot?.settings?.notifyGreetings == true) {
                addAll(snapshot.greetings.filter { it.author == MessageAuthor.PEER }.map { it.id })
                addAll(snapshot.afterMessages.filter { it.author == MessageAuthor.PEER }.map { it.id })
            }
        }
    }.distinctUntilChanged()

    fun bindDrivingState(provider: DrivingStateProvider) = coordinator.bindDrivingState(provider)

    override fun close() {
        coordinator.close()
        scope.cancel()
    }
}
