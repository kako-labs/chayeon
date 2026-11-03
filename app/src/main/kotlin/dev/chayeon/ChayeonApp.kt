package dev.chayeon

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import dev.chayeon.feature.history.HistoryDestination
import dev.chayeon.feature.history.historyGraph
import dev.chayeon.feature.meet.MeetDestination
import dev.chayeon.feature.meet.meetGraph
import dev.chayeon.feature.signature.SignatureDestination
import dev.chayeon.feature.signature.signatureGraph

@Composable
fun ChayeonApp(graph: AppGraph, navController: NavHostController = rememberNavController()) {
    NavHost(navController, startDestination = HistoryDestination.ROUTE) {
        historyGraph(
            deps = graph.historyDeps,
            onOpenMeet = { navController.openFeature(MeetDestination.ROUTE) },
            onOpenProfile = { navController.openFeature(SignatureDestination.ROUTE) },
        )
        meetGraph(
            deps = graph.meetDeps,
            onOpenHistory = { navController.openFeature(HistoryDestination.ROUTE) },
            onOpenProfile = { navController.openFeature(SignatureDestination.ROUTE) },
        )
        signatureGraph(
            deps = graph.signatureDeps,
            onOpenMeet = { navController.openFeature(MeetDestination.ROUTE) },
            onOpenHistory = { navController.openFeature(HistoryDestination.ROUTE) },
        )
    }
}

private fun NavHostController.openFeature(route: String) {
    navigate(route) {
        launchSingleTop = true
        popUpTo(HistoryDestination.ROUTE) { saveState = true }
        restoreState = true
    }
}
