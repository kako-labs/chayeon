package dev.chayeon

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import dev.chayeon.core.model.*
import dev.chayeon.core.ui.*
import dev.chayeon.feature.history.*
import dev.chayeon.feature.meet.*
import dev.chayeon.feature.signature.*
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

fun rootRoute(session: SessionState, profile: LoadState<MyProfile?>): String? = when (session) {
    is SessionState.SignedOut, is SessionState.Failed -> HistoryDestination.FIRST_RUN
    is SessionState.Entering -> null
    is SessionState.Active -> when (profile) {
        is LoadState.Ready -> when (profile.value?.registration) {
            RegistrationState.READY -> MeetDestination.HOME
            RegistrationState.CONSENT_REQUIRED -> HistoryDestination.CONSENT
            else -> HistoryDestination.PROFILE_SETUP
        }
        else -> MeetDestination.HOME
    }
}

@Composable
fun ChayeonApp(graph: AppGraph, navController: NavHostController = rememberNavController(), events: AppEvents? = null,
    publisherFailure: StateFlow<Boolean> = remember { MutableStateFlow(false) },
    recordingFailure: StateFlow<Boolean> = remember { MutableStateFlow(false) }) {
    val driving by graph.drivingStateProvider.drivingState.collectAsStateWithLifecycle(DrivingState())
    val discovery by graph.discoveryRepository.discovery.collectAsStateWithLifecycle(DiscoveryState())
    val homeZone by graph.homeZoneRepository.state.collectAsStateWithLifecycle(HomeZoneState(HomeZoneStatus.LOCATION_UNKNOWN))
    val session by graph.sessionRepository.session.collectAsStateWithLifecycle(SessionState.Entering())
    val profile by graph.profileRepository.profile.collectAsStateWithLifecycle(LoadState.Loading())
    val recaps by graph.signatureRepository.recaps.collectAsStateWithLifecycle(LoadState.Loading())
    val publicationFailed by publisherFailure.collectAsStateWithLifecycle()
    val recordingFailed by recordingFailure.collectAsStateWithLifecycle()
    val event by (events?.banner ?: remember { MutableStateFlow(null) }).collectAsStateWithLifecycle()
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val scope = rememberCoroutineScope()
    var discoveryError by remember { mutableStateOf(false) }
    var discoveryBusy by remember { mutableStateOf(false) }
    var routedSession by remember { mutableStateOf<String?>(null) }
    var shownRecap by remember { mutableStateOf<String?>(null) }

    fun guarded(action: () -> Unit) { if (graph.currentPolicy() == DrivingPolicy.ALLOWED) action() }
    fun open(destination: String) { guarded { navController.navigate(destination) { launchSingleTop = true } } }
    val chrome = ScreenChrome(topBar = {
        Column {
            BoxWithConstraints(Modifier.fillMaxWidth().height(ChayeonDimensions.Banner), contentAlignment = Alignment.Center) {
                GearStrip(driving.gear, driving.policy, discovery.locationSource == LocationSource.SYNTHETIC,
                    onDemo = if (graph.demoEnabled && route != DEMO_ROUTE) ({ open(DEMO_ROUTE) }) else null,
                    resting = driving.policy != DrivingPolicy.ALLOWED && discovery.mode == DiscoveryMode.ON && homeZone.suppressed)
                if (driving.policy == DrivingPolicy.ALLOWED) {
                    val notice = event
                    val text = notice?.text ?: when {
                        publicationFailed -> "공개 내용을 반영하지 못했어요. 연결되면 다시 시도해요"
                        recordingFailed -> "주행 기록을 시작하지 못했어요. 앱을 다시 열어 주세요"
                        discoveryError -> "발견 상태를 바꾸지 못했어요. 다시 눌러 주세요"
                        else -> null
                    }
                    val narrow = maxWidth < ChayeonSpacing.NarrowBreakpoint
                    val label = if (!narrow) text else when {
                        notice != null -> "새 소식 확인하기"
                        publicationFailed -> "공개 내용을 다시 반영해요"
                        recordingFailed -> "앱을 다시 열어 주세요"
                        discoveryError -> "발견을 다시 눌러 주세요"
                        else -> null
                    }
                    if (label != null) TopBanner(label, Modifier.padding(start = if (narrow) ChayeonDimensions.Avatar else ChayeonDimensions.PeerLarge, end = ChayeonDimensions.Knot)
                        .then(if (notice != null) Modifier.clickable {
                            guarded {
                                navController.navigate(notice.route) { launchSingleTop = true }
                                notice.requestId?.let { navController.currentBackStackEntry?.savedStateHandle?.set("meet.requestId", it) }
                                events?.acknowledge(notice.id)
                            }
                        } else Modifier))
                }
            }
        }
    }, bottomBar = { selected ->
        BottomNav(selected, discovery.mode == DiscoveryMode.ON,
            onSelect = { guarded { navController.openTab(it) } },
            onDiscoveryToggle = {
                if (!discoveryBusy && graph.currentPolicy() == DrivingPolicy.ALLOWED) {
                    discoveryBusy = true
                    val request = SetDiscoveryRequest(CommandMeta(UUID.randomUUID().toString()),
                        if (discovery.mode == DiscoveryMode.ON) DiscoveryMode.OFF else DiscoveryMode.ON)
                    scope.launch {
                        try {
                            if (graph.currentPolicy() == DrivingPolicy.ALLOWED)
                                discoveryError = graph.discoveryRepository.setDiscovery(request) is AppResult.Failure
                        } finally { discoveryBusy = false }
                    }
                }
            }, resting = homeZone.suppressed)
    }, onViewing = { request, relationship -> events?.viewing(request, relationship) ?: {} })
    ChayeonTheme(policy = driving.policy) {
        NavHost(navController, startDestination = HistoryDestination.FIRST_RUN) {
            historyGraph(graph.historyDeps, chrome, navController) { exit ->
                when (exit) {
                    HistoryExit.OpenMeet -> open(MeetDestination.HOME)
                    HistoryExit.OpenSignatureVisibility -> open(SignatureDestination.VISIBILITY)
                    HistoryExit.SignedOut -> navController.navigate(HistoryDestination.FIRST_RUN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
            meetGraph(graph.meetDeps, chrome, navController) { exit ->
                when (exit) {
                    is MeetExit.OpenEncounter -> open(HistoryDestination.encounter(exit.encounterId))
                    is MeetExit.OpenSafety -> open(HistoryDestination.safety(exit.peerProfileId))
                    MeetExit.CreateSignature -> open(if (graph.demoEnabled) CREATE_SIGNATURE_ROUTE else SignatureDestination.MY_SIGNATURE)
                    MeetExit.OpenMySignature -> open(SignatureDestination.MY_SIGNATURE)
                }
            }
            signatureGraph(graph.signatureDeps, chrome, navController) { exit ->
                when (exit) {
                    SignatureExit.OpenMeet -> open(MeetDestination.HOME)
                    SignatureExit.OpenProfileEdit -> open(HistoryDestination.PROFILE_EDIT)
                    SignatureExit.OpenConsent -> open(HistoryDestination.CONSENT)
                    SignatureExit.OpenHomeZone -> open(HistoryDestination.HOME_ZONE)
                    SignatureExit.OpenBlocks -> open(HistoryDestination.BLOCKS)
                    SignatureExit.OpenCleanup -> open(HistoryDestination.CLEANUP)
                    SignatureExit.CreateSignature -> if (graph.demoEnabled) open(CREATE_SIGNATURE_ROUTE)
                }
            }
            listOf(DEMO_ROUTE, CREATE_SIGNATURE_ROUTE).forEach { destination -> composable(destination) {
                val model: DemoConsoleViewModel = viewModel { DemoConsoleViewModel(graph) }
                val state by model.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(state.completedRecapId, state.busy, state.driving.policy) {
                    val recapId = state.completedRecapId
                    if (recapId != null && !state.busy && graph.currentPolicy() == DrivingPolicy.ALLOWED) {
                        shownRecap = recapId
                        navController.navigate(SignatureDestination.recap(recapId)) { launchSingleTop = true }
                        model.consumeRecap()
                    }
                }
                if (destination == CREATE_SIGNATURE_ROUTE) SignatureCreationScreen(state, model::fill, {
                    guarded { if (!navController.popBackStack()) open(MeetDestination.HOME) }
                }) else DemoConsoleScreen(state, chrome, graph.isRealVehicle, graph.demoEnabled, model::gear,
                    model::replay, model::fill, model::presence, {
                        guarded {
                            if (navController.previousBackStackEntry?.destination?.route == MeetDestination.CONNECTED) navController.popBackStack()
                            else open(SignatureDestination.MY_PROFILE)
                        }
                    }, returnToConnection = navController.previousBackStackEntry?.destination?.route == MeetDestination.CONNECTED,
                    onCreate = { open(CREATE_SIGNATURE_ROUTE) }, syntheticHomeSet = homeZone.radius != null && homeZone.source == LocationSource.SYNTHETIC,
                    onHomePosition = model::homePosition)
            } }
        }
    }
    LaunchedEffect(session, profile, driving.policy) {
        val target = rootRoute(session, profile) ?: return@LaunchedEffect
        if (target == HistoryDestination.FIRST_RUN) {
            routedSession = null
            shownRecap = null
            if (route != HistoryDestination.FIRST_RUN) navController.navigate(target) { popUpTo(0) { inclusive = true } }
        } else if (graph.currentPolicy() == DrivingPolicy.ALLOWED) {
            val id = (session as? SessionState.Active)?.session?.id
            val current = navController.currentDestination?.route
            val onboarding = target == HistoryDestination.PROFILE_SETUP || target == HistoryDestination.CONSENT
            if (routedSession != id || (current == HistoryDestination.FIRST_RUN) ||
                (onboarding && current != target)) {
                routedSession = id
                navController.navigate(target) { popUpTo(0) { inclusive = true }; launchSingleTop = true }
            }
        }
    }
    LaunchedEffect(recaps, driving.policy, route) {
        val next = (recaps as? LoadState.Ready)?.value?.firstOrNull { !it.seen && it.id != shownRecap }
        val ready = (profile as? LoadState.Ready)?.value?.registration == RegistrationState.READY
        if (ready && next != null && graph.currentPolicy() == DrivingPolicy.ALLOWED && route != DEMO_ROUTE && route != CREATE_SIGNATURE_ROUTE && route != SignatureDestination.RECAP) {
            shownRecap = next.id
            open(SignatureDestination.recap(next.id))
        }
    }
}

private fun NavHostController.openTab(item: NavigationItem) {
    val route = when (item) {
        NavigationItem.MEET -> MeetDestination.HOME
        NavigationItem.HISTORY -> HistoryDestination.ENCOUNTERS
        NavigationItem.AFTER -> HistoryDestination.AFTER_CONNECTIONS
        NavigationItem.PROFILE -> SignatureDestination.MY_PROFILE
    }
    navigate(route) {
        launchSingleTop = true
        popUpTo(MeetDestination.HOME)
    }
}
