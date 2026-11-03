package dev.chayeon

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import dev.chayeon.core.model.DiscoveryMode
import dev.chayeon.core.model.DiscoveryState
import dev.chayeon.core.model.DrivingState
import dev.chayeon.core.model.LocationSource
import dev.chayeon.core.ui.BottomNav
import dev.chayeon.core.ui.ChayeonTheme
import dev.chayeon.core.ui.GearStrip
import dev.chayeon.core.ui.NavigationItem
import dev.chayeon.core.ui.ScreenChrome
import dev.chayeon.feature.history.HistoryDestination
import dev.chayeon.feature.history.HistoryExit
import dev.chayeon.feature.history.historyGraph
import dev.chayeon.feature.meet.MeetDestination
import dev.chayeon.feature.meet.MeetExit
import dev.chayeon.feature.meet.meetGraph
import dev.chayeon.feature.signature.SignatureDestination
import dev.chayeon.feature.signature.SignatureExit
import dev.chayeon.feature.signature.signatureGraph

@Composable
fun ChayeonApp(graph: AppGraph, navController: NavHostController = rememberNavController()) {
    val driving by graph.drivingStateProvider.drivingState.collectAsState(DrivingState())
    val discovery by graph.discoveryRepository.discovery.collectAsState(DiscoveryState())
    val chrome = remember(navController) {
        ScreenChrome(
            topBar = {
                GearStrip(
                    gear = driving.gear,
                    policy = driving.policy,
                    syntheticLocation = discovery.locationSource == LocationSource.SYNTHETIC,
                )
            },
            bottomBar = { selected ->
                BottomNav(
                    selected = selected,
                    discoveryOn = discovery.mode == DiscoveryMode.ON,
                    onSelect = { navController.openTab(it) },
                    onDiscoveryToggle = {},
                )
            },
        )
    }
    ChayeonTheme(policy = driving.policy) {
        NavHost(navController, startDestination = HistoryDestination.FIRST_RUN) {
            historyGraph(graph.historyDeps, chrome, navController) { exit ->
                when (exit) {
                    HistoryExit.OpenMeet -> navController.navigate(MeetDestination.HOME)
                    HistoryExit.OpenSignatureVisibility -> navController.navigate(SignatureDestination.VISIBILITY)
                    HistoryExit.SignedOut -> navController.navigate(HistoryDestination.FIRST_RUN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
            meetGraph(graph.meetDeps, chrome, navController) { exit ->
                when (exit) {
                    is MeetExit.OpenEncounter -> navController.navigate(HistoryDestination.encounter(exit.encounterId))
                    is MeetExit.OpenSafety -> navController.navigate(HistoryDestination.safety(exit.peerProfileId))
                    MeetExit.OpenMySignature -> navController.navigate(SignatureDestination.MY_SIGNATURE)
                }
            }
            signatureGraph(graph.signatureDeps, chrome, navController) { exit ->
                when (exit) {
                    SignatureExit.OpenMeet -> navController.navigate(MeetDestination.HOME)
                    SignatureExit.OpenProfileEdit -> navController.navigate(HistoryDestination.PROFILE_EDIT)
                    SignatureExit.OpenConsent -> navController.navigate(HistoryDestination.CONSENT)
                    SignatureExit.OpenBlocks -> navController.navigate(HistoryDestination.BLOCKS)
                    SignatureExit.OpenCleanup -> navController.navigate(HistoryDestination.CLEANUP)
                    SignatureExit.OpenDemoConsole -> Unit
                }
            }
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
        restoreState = true
        popUpTo(MeetDestination.HOME) { saveState = true }
    }
}
