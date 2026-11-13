package dev.chayeon.feature.meet

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.core.net.toUri
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.chayeon.core.model.DrivingPolicy
import dev.chayeon.core.ui.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

sealed interface MeetExit {
    data class OpenEncounter(val encounterId: String) : MeetExit
    data class OpenSafety(val peerProfileId: String) : MeetExit
    data object OpenDemo : MeetExit
    data object CreateSignature : MeetExit
    data object OpenMySignature : MeetExit
}

object MeetDestination {
    const val HOME = "meet/home"
    const val CANDIDATE = "meet/candidate/{candidateId}"
    const val REQUESTS = "meet/requests"
    const val CONNECTED = "meet/connected"
    const val ENDED = "meet/ended/{connectionId}"
    const val DRIVING = "meet/driving"

    fun candidate(candidateId: String) = "meet/candidate/${Uri.encode(candidateId)}"
    fun ended(connectionId: String) = "meet/ended/${Uri.encode(connectionId)}"
}

fun NavGraphBuilder.meetGraph(
    deps: MeetDeps,
    chrome: ScreenChrome,
    navController: NavController,
    onExit: (MeetExit) -> Unit,
) {
    listOf(MeetDestination.HOME, MeetDestination.CANDIDATE, MeetDestination.REQUESTS, MeetDestination.CONNECTED, MeetDestination.ENDED, MeetDestination.DRIVING).forEach { route ->
        composable(route) { entry ->
            MeetRoute(deps, chrome, navController, onExit, entry, route)
        }
    }
}

@Composable
private fun MeetRoute(
    deps: MeetDeps,
    chrome: ScreenChrome,
    navController: NavController,
    onExit: (MeetExit) -> Unit,
    entry: NavBackStackEntry,
    route: String,
) {
    val owner = remember(entry) {
        runCatching { navController.getBackStackEntry(MeetDestination.HOME) }.getOrDefault(entry)
    }
    val flowModel: MeetViewModel = viewModel(owner) { MeetViewModel(deps) }
    val flowState by flowModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val currentExit by rememberUpdatedState(onExit)
    val guarded: (() -> Unit) -> Unit = { action ->
        scope.launch { if (flowModel.allowsNavigation()) action() }
    }
    val home: () -> Unit = {
        guarded {
            navController.navigate(MeetDestination.HOME) {
                popUpTo(MeetDestination.HOME) { inclusive = false }
                launchSingleTop = true
            }
        }
    }
    BackHandler(enabled = flowState.driving.policy != DrivingPolicy.ALLOWED) { }
    LaunchedEffect(flowModel, entry) {
        entry.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            flowModel.effectsFor(route).collect { effect ->
                if (effect is MeetNavigationEffect.Connected && !flowModel.allowsConnectedNavigation()) return@collect
                if ((effect is MeetNavigationEffect.Ended || effect is MeetNavigationEffect.Requests ||
                        effect == MeetNavigationEffect.ResumeHome) && !flowModel.allowsNavigation()) return@collect
                flowModel.handled(effect)
                when (effect) {
                    is MeetNavigationEffect.Connected -> navController.replaceMeet(MeetDestination.CONNECTED)
                    is MeetNavigationEffect.Ended -> navController.replaceMeet(MeetDestination.ended(effect.connectionId))
                    is MeetNavigationEffect.Requests -> navController.openMeetRequest(effect.requestId)
                    MeetNavigationEffect.LockHome -> navController.navigate(MeetDestination.DRIVING) { launchSingleTop = true }
                    MeetNavigationEffect.ResumeHome -> if (!navController.popBackStack()) navController.replaceMeet(MeetDestination.HOME)
                }
            }
        }
    }
    when (route) {
        MeetDestination.HOME -> {
            val model: HomeViewModel = viewModel { HomeViewModel(deps) }
            val state by model.uiState.collectAsStateWithLifecycle()
            val context = LocalContext.current
            var requestGeneration by rememberSaveable { mutableIntStateOf(0) }
            HomeScreen(state, chrome, model::setDiscovery, model::refresh,
                requestAction = { candidate, onBusy ->
                    val candidateModel: CandidateViewModel = viewModel(key = "home-candidate-${candidate.id}-$requestGeneration") { CandidateViewModel(deps, candidate.id) }
                    val candidateState by candidateModel.uiState.collectAsStateWithLifecycle()
                    SideEffect { onBusy(candidateState.request.inProgress) }
                    LaunchedEffect(candidateModel, entry) {
                        entry.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                            candidateModel.requestSent.collect { requestId ->
                                requestGeneration++
                                navController.openMeetRequest(requestId)
                            }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Small)) {
                        MeetError(candidateState.request.error)
                        PrimaryButton(if (candidateState.request.inProgress) "통화를 요청하고 있어요" else "통화 요청하기",
                            candidateModel::requestConnection, Modifier.fillMaxWidth(), icon = ChayeonIcon.PHONE,
                            enabled = candidateState.canRequest && !candidateState.request.inProgress && state.pendingRequest == null)
                    }
                },
                onCandidate = { id -> guarded { navController.navigate(MeetDestination.candidate(id)) } },
                onRequests = { guarded { navController.openMeetRequest(state.pendingRequest?.id) } },
                onConnected = { guarded { navController.replaceMeet(MeetDestination.CONNECTED) } },
                onDemo = { guarded { currentExit(MeetExit.OpenDemo) } },
                onCreateSignature = { guarded { currentExit(MeetExit.CreateSignature) } },
                onMySignature = { guarded { currentExit(MeetExit.OpenMySignature) } },
                onLocationSettings = {
                    guarded {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
                        if (intent.resolveActivity(context.packageManager) != null) context.startActivity(intent)
                    }
                },
            )
        }
        MeetDestination.CANDIDATE -> {
            val candidateId = entry.arguments?.getString("candidateId").orEmpty()
            val model: CandidateViewModel = viewModel { CandidateViewModel(deps, candidateId) }
            val state by model.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(model, entry) {
                entry.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    model.requestSent.collect { requestId ->
                        navController.openMeetRequest(requestId)
                    }
                }
            }
            CandidateScreen(state, chrome, model::requestConnection, home,
                onSafety = { guarded { state.candidate?.profile?.id?.let { currentExit(MeetExit.OpenSafety(it)) } } },
            )
        }
        MeetDestination.REQUESTS -> {
            val requestId = entry.savedStateHandle.get<String>("meet.requestId")
            val model: RequestViewModel = viewModel { RequestViewModel(deps, requestId) }
            val state by model.uiState.collectAsStateWithLifecycle()
            RequestScreen(state, chrome, model::accept, model::decline, model::cancel, home,
                onSafety = { guarded { state.request?.peer?.id?.let { currentExit(MeetExit.OpenSafety(it)) } } },
            )
        }
        MeetDestination.CONNECTED -> {
            val model: ConnectedViewModel = viewModel { ConnectedViewModel(deps) }
            val state by model.uiState.collectAsStateWithLifecycle()
            val anchor by deps.serverTimeProvider.serverTime.collectAsStateWithLifecycle(initialValue = null)
            val elapsed by produceState<Long?>(null, state.connection?.id, anchor, state.driving.policy) {
                value = null
                val connection = state.connection
                val receipt = anchor
                if (connection != null && receipt != null && state.driving.policy == DrivingPolicy.ALLOWED) {
                    while (true) {
                        val now = deps.serverTimeProvider.monotonicTimeMillis()
                        value = if (now >= receipt.receivedAtMonotonicMillis)
                            (receipt.serverEpochMillis - connection.startedAtEpochMillis + now - receipt.receivedAtMonotonicMillis).coerceAtLeast(0)
                        else null
                        delay(1_000)
                    }
                }
            }
            ConnectedScreen(state, chrome, model::leave, elapsedMillis = elapsed,
                onMute = model::toggleMute, onMicrophone = model::requestMicrophone,
                onSafety = { guarded { state.connection?.peer?.id?.let { currentExit(MeetExit.OpenSafety(it)) } } },
            )
        }
        MeetDestination.ENDED -> {
            val connectionId = entry.arguments?.getString("connectionId").orEmpty()
            val model: EndedViewModel = viewModel { EndedViewModel(deps, connectionId) }
            val state by model.uiState.collectAsStateWithLifecycle()
            EndedScreen(state, chrome, home, onEncounter = { id -> guarded { currentExit(MeetExit.OpenEncounter(id)) } })
        }
        MeetDestination.DRIVING -> {
            val model: ConnectedViewModel = viewModel { ConnectedViewModel(deps) }
            val state by model.uiState.collectAsStateWithLifecycle()
            DrivingScreen(flowState.driving, chrome, state.canLeave, state.leave, model::leave)
        }
    }
}

private fun NavController.replaceMeet(destination: String) {
    navigate(destination) {
        popUpTo(MeetDestination.HOME) { inclusive = false }
        launchSingleTop = true
    }
}

private fun NavController.openMeetRequest(requestId: String?) {
    navigate(MeetDestination.REQUESTS) { launchSingleTop = true }
    currentBackStackEntry?.savedStateHandle?.set("meet.requestId", requestId)
}
