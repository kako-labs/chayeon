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
    private val publicationLock = Mutex()
    val socialGraph by lazy {
        val engine = DefaultSignatureEngine()
        SocialGraph(this, BuildConfig.SERVER_URL, object : SignatureRules {
            override fun title(traits: List<PublicTrait>) = engine.title(traits)
            override fun chemistry(first: PublicSignature, second: PublicSignature) = engine.chemistry(first, second)
        }, localCleanup = {
            vehicleGraph.gateInput.update(null, false)
            vehicleGraph.signatureRepository.clearLocalData()
        }, drivingStateProvider = commandDrivingStateProvider)
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
                vehicleGraph.gateInput.update(null, false)
                return publicationLock.withLock { socialGraph.sessionRepository.signOut(request) }
            }
            override suspend fun deleteAccount(request: DeleteAccountRequest): AppResult<CommandReceipt> {
                if (vehicleGraph.drivingStateProvider.current().policy != DrivingPolicy.ALLOWED) return AppResult.Failure(AppError.Restricted)
                sessionEnding.value = true
                vehicleGraph.gateInput.update(null, false)
                return publicationLock.withLock { socialGraph.sessionRepository.deleteAccount(request) }
            }
        }
        AppGraph(sessions, socialGraph.profileRepository, socialGraph.discoveryRepository,
            socialGraph.connectionRepository, socialGraph.encounterRepository, socialGraph.afterRepository,
            socialGraph.safetyRepository, commandDrivingStateProvider, vehicleGraph.signatureRepository,
            vehicleGraph.vehicleConsole, socialGraph.serverTimeProvider, BuildConfig.SERVER_URL).apply {
                currentPolicy = { vehicleGraph.drivingStateProvider.current().policy }
                isRealVehicle = vehicleGraph.gateway.isRealVehicle
                demoEnabled = BuildConfig.DEMO_CONTROLS_ENABLED
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
                val active = (session as? SessionState.Active)?.session?.takeUnless { ending }
                val consent = (settings as? LoadState.Ready)?.value?.consents?.any {
                    it.scope == ConsentScope.VEHICLE_DATA && it.status == ConsentStatus.GRANTED &&
                        it.documentVersion == active?.policy?.consentDocumentVersion
                } == true
                active?.accountId to consent
            }.collect { (account, consent) -> vehicleGraph.gateInput.update(account, consent) }
        }
        publisher.start()
        events.start()
    }
}
