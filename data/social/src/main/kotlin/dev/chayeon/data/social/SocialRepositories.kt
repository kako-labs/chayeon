package dev.chayeon.data.social

import dev.chayeon.core.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

internal fun <T> SocialCoordinator.observe(selector: (SyncSnapshot) -> T): Flow<LoadState<T>> =
    combine(store.state, driving) { state, drivingState ->
        if (drivingState.policy != DrivingPolicy.ALLOWED) {
            LoadState.Failed(AppError.Restricted, state.cursor.coerceAtLeast(0))
        } else when (val data = state.data) {
            is LoadState.Ready -> LoadState.Ready(selector(data.value), data.version)
            is LoadState.Loading -> data
            is LoadState.Failed -> data
        }
    }

internal class SocialSessionRepository(
    private val coordinator: SocialCoordinator,
    modes: Set<SessionMode>,
) : SessionRepository {
    override val session: Flow<SessionState> = coordinator.session
    override val availableModes: Flow<Set<SessionMode>> = flowOf(modes)
    override val personas: Flow<LoadState<List<Persona>>> = flowOf(LoadState.Ready(listOf(
        Persona("account-a", 1, PersonaCode.ACCOUNT_A, "새벽드라이브", AvatarId.DAWN),
        Persona("account-b", 1, PersonaCode.ACCOUNT_B, "노을산책", AvatarId.SLOW),
    ), 1))
    override suspend fun enter(request: EnterSessionRequest) = coordinator.enter(request)
    override suspend fun signOut(request: SignOutRequest) = coordinator.signOut(request)
    override suspend fun deleteAccount(request: DeleteAccountRequest) = coordinator.deleteAccount(request)
}

internal class SocialProfileRepository(private val coordinator: SocialCoordinator) : ProfileRepository {
    override val profile = coordinator.observe { it.profile }
    override val settings: Flow<LoadState<ParticipationSettings>> = coordinator.observe {
        it.settings ?: ParticipationSettings("settings", it.version)
    }
    override suspend fun saveProfile(request: SaveProfileRequest) = coordinator.command(
        ApiRoutes.SAVE_PROFILE, request, SaveProfileRequest.serializer(), SaveProfileResponse.serializer(), { it.result })
    override suspend fun recordConsent(request: RecordConsentRequest) = coordinator.command(
        ApiRoutes.RECORD_CONSENT, request, RecordConsentRequest.serializer(), RecordConsentResponse.serializer(), { it.result })
    override suspend fun updateSettings(request: UpdateSettingsRequest) = coordinator.command(
        ApiRoutes.UPDATE_SETTINGS, request, UpdateSettingsRequest.serializer(), UpdateSettingsResponse.serializer(), { it.result })
    override suspend fun publishSignature(request: PublishSignatureRequest) = coordinator.command(
        ApiRoutes.PUBLISH_SIGNATURE, request, PublishSignatureRequest.serializer(), PublishSignatureResponse.serializer(), { it.result })
}

internal class SocialDiscoveryRepository(private val coordinator: SocialCoordinator) : DiscoveryRepository {
    override val discovery: Flow<DiscoveryState> = combine(coordinator.store.state, coordinator.driving) { state, driving ->
        val current = (state.data as? LoadState.Ready)?.value?.discovery ?: DiscoveryState(
            version = state.cursor.coerceAtLeast(0),
            status = if (state.data is LoadState.Failed) DiscoveryStatus.NETWORK_FAILURE else DiscoveryStatus.LOADING,
        )
        when (driving.policy) {
            DrivingPolicy.ALLOWED -> current
            DrivingPolicy.RESTRICTED -> current.copy(status = DiscoveryStatus.DRIVING_RESTRICTED, candidates = emptyList())
            DrivingPolicy.UNKNOWN -> current.copy(status = DiscoveryStatus.DRIVING_UNKNOWN, candidates = emptyList())
        }
    }
    override suspend fun setDiscovery(request: SetDiscoveryRequest) = coordinator.command(
        ApiRoutes.SET_DISCOVERY, request, SetDiscoveryRequest.serializer(), SetDiscoveryResponse.serializer(), { it.result },
        requiresAllowed = request.mode != DiscoveryMode.OFF)
    override suspend fun refresh(request: RefreshDiscoveryRequest) = coordinator.command(
        ApiRoutes.REFRESH_DISCOVERY, request, RefreshDiscoveryRequest.serializer(), RefreshDiscoveryResponse.serializer(), { it.result })
    override suspend fun updateLocation(request: UpdateLocationRequest) = coordinator.command(
        ApiRoutes.UPDATE_LOCATION, request, UpdateLocationRequest.serializer(), UpdateLocationResponse.serializer(), { it.result },
        requiresAllowed = false)
    override suspend fun setSyntheticPresence(request: SetSyntheticPresenceRequest) = coordinator.command(
        ApiRoutes.SET_SYNTHETIC_PRESENCE, request, SetSyntheticPresenceRequest.serializer(), SetSyntheticPresenceResponse.serializer(), { it.result })
}

internal class SocialConnectionRepository(private val coordinator: SocialCoordinator) : ConnectionRepository {
    override val requests = coordinator.observe { it.connectionRequests }
    // A driving screen still needs the active relationship identity to offer the permitted leave action.
    override val connection: Flow<LoadState<Connection?>> = coordinator.store.state.map { state ->
        when (val data = state.data) {
            is LoadState.Ready -> LoadState.Ready(
                data.value.connections.firstOrNull { it.status != ConnectionStatus.ENDED }
                    ?: data.value.connections.maxByOrNull { it.endedAtEpochMillis ?: 0 }, data.version)
            is LoadState.Loading -> data
            is LoadState.Failed -> data
        }
    }
    override fun observeGreetings(connectionId: String): Flow<LoadState<List<ConnectionGreeting>>> =
        combine(coordinator.store.state, coordinator.driving) { state, driving ->
            if (driving.policy != DrivingPolicy.ALLOWED) return@combine LoadState.Failed(AppError.Restricted, state.cursor.coerceAtLeast(0))
            val pending = state.pendingGreetings.map { it.message }.filter { it.connectionId == connectionId }
            when (val data = state.data) {
                is LoadState.Ready -> {
                    val sent = data.value.greetings.filter { it.connectionId == connectionId }
                    LoadState.Ready(sent + pending.filterNot { item -> sent.any {
                        it.author == MessageAuthor.SELF && it.clientMessageId == item.clientMessageId
                    } }, data.version)
                }
                is LoadState.Failed -> if (pending.isNotEmpty()) LoadState.Ready(pending, data.version) else data
                is LoadState.Loading -> data
            }
        }
    override suspend fun requestConnection(request: RequestConnectionRequest) = coordinator.command(
        ApiRoutes.REQUEST_CONNECTION, request, RequestConnectionRequest.serializer(), RequestConnectionResponse.serializer(), { it.result })
    override suspend fun respond(request: RespondConnectionRequest) = coordinator.command(
        ApiRoutes.RESPOND_CONNECTION, request, RespondConnectionRequest.serializer(), RespondConnectionResponse.serializer(), { it.result })
    override suspend fun cancel(request: CancelConnectionRequest) = coordinator.command(
        ApiRoutes.CANCEL_CONNECTION, request, CancelConnectionRequest.serializer(), CancelConnectionResponse.serializer(), { it.result })
    override suspend fun leave(request: LeaveConnectionRequest) = coordinator.command(
        ApiRoutes.LEAVE_CONNECTION, request, LeaveConnectionRequest.serializer(), LeaveConnectionResponse.serializer(), { it.result },
        requiresAllowed = false)

    override suspend fun sendGreeting(request: SendGreetingRequest): AppResult<ConnectionGreeting> {
        if (!coordinator.allowed()) return AppResult.Failure(AppError.Restricted)
        val generation = coordinator.store.state.value.generation
        val stable = coordinator.store.greeting(generation, request, DeliveryState.SENDING)
            ?: return AppResult.Failure(AppError.Unknown)
        val result = try {
            coordinator.command(ApiRoutes.SEND_GREETING, stable, SendGreetingRequest.serializer(),
                SendGreetingResponse.serializer(), { it.result }, refresh = false)
        } catch (cancelled: CancellationException) {
            coordinator.store.greeting(generation, stable, DeliveryState.FAILED)
            throw cancelled
        }
        when (result) {
            is AppResult.Success -> coordinator.store.greeting(generation, stable, DeliveryState.SENT, result.value)
            is AppResult.Failure -> coordinator.store.greeting(generation, stable, DeliveryState.FAILED)
        }
        coordinator.refresh(generation = generation)
        return result
    }
}

internal class SocialEncounterRepository(private val coordinator: SocialCoordinator) : EncounterRepository {
    override val encounters = coordinator.observe { it.encounters }
    override fun observeEncounter(encounterId: String) = coordinator.observe { snapshot ->
        snapshot.encounters.find { it.id == encounterId }
    }
    override suspend fun deleteEncounter(request: DeleteEncounterRequest) = coordinator.command(
        ApiRoutes.DELETE_ENCOUNTER, request, DeleteEncounterRequest.serializer(), DeleteEncounterResponse.serializer(), { it.result })
    override suspend fun deleteAllEncounters(request: DeleteAllEncountersRequest) = coordinator.command(
        ApiRoutes.DELETE_ALL_ENCOUNTERS, request, DeleteAllEncountersRequest.serializer(), DeleteAllEncountersResponse.serializer(), { it.result })
}

internal class SocialAfterRepository(private val coordinator: SocialCoordinator) : AfterRepository {
    override val requests = coordinator.observe { it.afterRequests }
    override val connections = coordinator.observe { it.afterConnections }
    override fun observeMessages(afterConnectionId: String): Flow<LoadState<List<AfterMessage>>> =
        combine(coordinator.store.state, coordinator.driving) { state, driving ->
            if (driving.policy != DrivingPolicy.ALLOWED) return@combine LoadState.Failed(AppError.Restricted, state.cursor.coerceAtLeast(0))
            val pending = state.pendingAfterMessages.map { it.message }.filter { it.afterConnectionId == afterConnectionId }
            when (val data = state.data) {
                is LoadState.Ready -> {
                    val sent = data.value.afterMessages.filter { it.afterConnectionId == afterConnectionId }
                    LoadState.Ready(sent + pending.filterNot { item -> sent.any {
                        it.author == MessageAuthor.SELF && it.clientMessageId == item.clientMessageId
                    } }, data.version)
                }
                is LoadState.Failed -> if (pending.isNotEmpty()) LoadState.Ready(pending, data.version) else data
                is LoadState.Loading -> data
            }
        }
    override suspend fun requestAfter(request: RequestAfterRequest) = coordinator.command(
        ApiRoutes.REQUEST_AFTER, request, RequestAfterRequest.serializer(), RequestAfterResponse.serializer(), { it.result })
    override suspend fun respond(request: RespondAfterRequest) = coordinator.command(
        ApiRoutes.RESPOND_AFTER, request, RespondAfterRequest.serializer(), RespondAfterResponse.serializer(), { it.result })
    override suspend fun withdraw(request: WithdrawAfterRequest) = coordinator.command(
        ApiRoutes.WITHDRAW_AFTER, request, WithdrawAfterRequest.serializer(), WithdrawAfterResponse.serializer(), { it.result })
    override suspend fun endRelationship(request: EndAfterConnectionRequest) = coordinator.command(
        ApiRoutes.END_AFTER_CONNECTION, request, EndAfterConnectionRequest.serializer(), EndAfterConnectionResponse.serializer(), { it.result },
        requiresAllowed = false)
    override suspend fun sendMessage(request: SendAfterMessageRequest): AppResult<AfterMessage> {
        if (!coordinator.allowed()) return AppResult.Failure(AppError.Restricted)
        val generation = coordinator.store.state.value.generation
        val stable = coordinator.store.afterMessage(generation, request, DeliveryState.SENDING)
            ?: return AppResult.Failure(AppError.Unknown)
        val result = try {
            coordinator.command(ApiRoutes.SEND_AFTER_MESSAGE, stable, SendAfterMessageRequest.serializer(),
                SendAfterMessageResponse.serializer(), { it.result }, refresh = false)
        } catch (cancelled: CancellationException) {
            coordinator.store.afterMessage(generation, stable, DeliveryState.FAILED)
            throw cancelled
        }
        when (result) {
            is AppResult.Success -> coordinator.store.afterMessage(generation, stable, DeliveryState.SENT, result.value)
            is AppResult.Failure -> coordinator.store.afterMessage(generation, stable, DeliveryState.FAILED)
        }
        coordinator.refresh(generation = generation)
        return result
    }
}

internal class SocialSafetyRepository(private val coordinator: SocialCoordinator) : SafetyRepository {
    override val blocks = coordinator.observe { it.blocks }
    override suspend fun block(request: BlockPeerRequest) = coordinator.command(
        ApiRoutes.BLOCK_PEER, request, BlockPeerRequest.serializer(), BlockPeerResponse.serializer(), { it.result })
    override suspend fun unblock(request: UnblockPeerRequest) = coordinator.command(
        ApiRoutes.UNBLOCK_PEER, request, UnblockPeerRequest.serializer(), UnblockPeerResponse.serializer(), { it.result })
    override suspend fun report(request: ReportPeerRequest) = coordinator.command(
        ApiRoutes.REPORT_PEER, request, ReportPeerRequest.serializer(), ReportPeerResponse.serializer(), { it.result })
}

internal class SocialServerTimeProvider(private val coordinator: SocialCoordinator) : ServerTimeProvider {
    override val serverTime = coordinator.store.state.map { it.anchor }
    override fun monotonicTimeMillis(): Long = coordinator.monotonicMillis()
}
