package dev.chayeon

import android.app.Application
import dev.chayeon.core.engine.SignatureRules
import dev.chayeon.core.model.*
import dev.chayeon.core.signature.DefaultSignatureEngine
import dev.chayeon.data.social.SocialGraph
import dev.chayeon.data.vehicle.VehicleGraph
import dev.chayeon.data.vehicle.VehicleGraphOwner
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ChayeonApplication : Application(), VehicleGraphOwner {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    override val vehicleGraph by lazy { VehicleGraph(this, demoEnabled = BuildConfig.DEMO_CONTROLS_ENABLED) }
    private val commandDrivingStateProvider = object : DrivingStateProvider {
        override val drivingState: Flow<DrivingState> = flow {
            emit(vehicleGraph.drivingStateProvider.current())
            emitAll(vehicleGraph.drivingStateProvider.drivingState.map { vehicleGraph.drivingStateProvider.current() })
        }.distinctUntilChanged()
    }
    private val sessionEnding = MutableStateFlow(false)
    private val sessionConsent = SessionConsent()
    private val publicationLock = Mutex()
    val homeZoneCoordinator by lazy { HomeZoneCoordinator(vehicleGraph.homeZoneRepository,
        { account -> vehicleGraph.homeZoneRepository.bindAccount(account) }, scope) }
    val socialGraph by lazy {
        val engine = DefaultSignatureEngine()
        SocialGraph(this, BuildConfig.SERVER_URL, object : SignatureRules {
            override fun title(traits: List<PublicTrait>) = engine.title(traits)
            override fun chemistry(first: PublicSignature, second: PublicSignature) = engine.chemistry(first, second)
        }, localCleanup = {
            // Keep protection active if the cleanup marker cannot be written and the session survives.
            homeZoneCoordinator.endSession()
            vehicleGraph.gateInput.update(null, false)
            vehicleGraph.updateLocationConsent(null, null)
            vehicleGraph.clearHomeLocationRequest()
            val home = vehicleGraph.homeZoneRepository.clearLocalData()
            val signature = vehicleGraph.signatureRepository.clearLocalData()
            if (home is AppResult.Failure) home else signature
        }, drivingStateProvider = commandDrivingStateProvider, prepareSession = homeZoneCoordinator::prepareSession,
            canStartConnection = { !vehicleGraph.homeZoneRepository.state.first().suppressed })
    }
    val graph by lazy {
        val sessions = object : SessionRepository by socialGraph.sessionRepository {
            override suspend fun enter(request: EnterSessionRequest): AppResult<Session> = publicationLock.withLock {
                socialGraph.sessionRepository.enter(request).also {
                    if (it is AppResult.Success) sessionEnding.value = false
                }
            }
            override suspend fun signOut(request: SignOutRequest): AppResult<CommandReceipt> {
                if (vehicleGraph.drivingStateProvider.current().policy != DrivingPolicy.ALLOWED) return AppResult.Failure(AppError.Restricted)
                sessionEnding.value = true
                sessionConsent.clear()
                vehicleGraph.gateInput.update(null, false)
                vehicleGraph.updateLocationConsent(null, null)
                return publicationLock.withLock { socialGraph.sessionRepository.signOut(request) }
            }
            override suspend fun deleteAccount(request: DeleteAccountRequest): AppResult<CommandReceipt> {
                if (vehicleGraph.drivingStateProvider.current().policy != DrivingPolicy.ALLOWED) return AppResult.Failure(AppError.Restricted)
                sessionEnding.value = true
                sessionConsent.clear()
                vehicleGraph.gateInput.update(null, false)
                vehicleGraph.updateLocationConsent(null, null)
                return publicationLock.withLock { socialGraph.sessionRepository.deleteAccount(request) }
            }
        }
        AppGraph(sessions, socialGraph.profileRepository, HomeProtectedDiscoveryRepository(socialGraph.discoveryRepository, vehicleGraph.homeZoneRepository, homeZoneCoordinator),
            HomeProtectedConnectionRepository(socialGraph.connectionRepository, vehicleGraph.homeZoneRepository), socialGraph.encounterRepository, socialGraph.afterRepository,
            socialGraph.safetyRepository, commandDrivingStateProvider, vehicleGraph.signatureRepository,
            vehicleGraph.vehicleConsole, socialGraph.serverTimeProvider, BuildConfig.SERVER_URL,
            vehicleGraph.homeZoneRepository, homeZoneCoordinator).apply {
                currentPolicy = { vehicleGraph.drivingStateProvider.current().policy }
                isRealVehicle = vehicleGraph.gateway.isRealVehicle
                demoEnabled = BuildConfig.DEMO_CONTROLS_ENABLED
                syntheticHomePosition = { vehicleGraph.homeZoneRepository.setSyntheticPosition(it) }
            }
    }
    val publisher by lazy { SignaturePublisher(graph, sessionEnding, publicationLock, scope,
        vehicleGraph.gateInput.permission.map { it.accountId }.distinctUntilChanged()) }
    val events by lazy { AppEvents(graph, AppNotifications(this), scope, socialGraph.messageSignals,
        socialGraph.recordingSettings.map { it is LoadState.Ready }) }

    override fun onCreate() {
        super.onCreate()
        vehicleGraph.start()
        scope.launch {
            combine(graph.sessionRepository.session, socialGraph.recordingSettings, sessionEnding) { session, settings, ending ->
                sessionConsent.update(session, settings, ending)
            }.distinctUntilChanged().collect { (account, consent, locationConsent) ->
                vehicleGraph.gateInput.update(account, consent)
                vehicleGraph.updateLocationConsent(account, locationConsent)
            }
        }
        publisher.start()
        events.start()
        scope.launch {
            combine(graph.sessionRepository.session, graph.discoveryRepository.discovery, graph.connectionRepository.connection) { session, discovery, connection ->
                session is SessionState.Active && (discovery.mode == DiscoveryMode.ON || discovery.status == DiscoveryStatus.NETWORK_FAILURE || connection !is LoadState.Ready ||
                    (connection as? LoadState.Ready)?.value?.status?.let { it != ConnectionStatus.ENDED } == true)
            }.distinctUntilChanged().collect(vehicleGraph::setPresenceNeeded)
        }
    }
}
