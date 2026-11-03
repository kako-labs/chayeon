package dev.chayeon.feature.history

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.chayeon.core.ui.ChayeonText
import dev.chayeon.core.ui.ChayeonType
import dev.chayeon.core.ui.HeroPanel
import dev.chayeon.core.ui.ScreenChrome
import dev.chayeon.core.ui.ScreenScaffold

sealed interface HistoryExit {
    data object OpenMeet : HistoryExit
    data object OpenSignatureVisibility : HistoryExit
    data object SignedOut : HistoryExit
}

object HistoryDestination {
    const val FIRST_RUN = "history/first-run"
    const val PROFILE_SETUP = "history/profile-setup"
    const val PROFILE_EDIT = "history/profile-edit"
    const val CONSENT = "history/consent"
    const val ENCOUNTERS = "history/encounters"
    const val ENCOUNTER = "history/encounters/{encounterId}"
    const val AFTER_REQUEST = "history/after-request/{encounterId}"
    const val AFTER_CONNECTIONS = "history/after"
    const val SAFETY = "history/safety/{peerProfileId}"
    const val BLOCKS = "history/blocks"
    const val CLEANUP = "history/cleanup"

    fun encounter(encounterId: String) = "history/encounters/$encounterId"
    fun afterRequest(encounterId: String) = "history/after-request/$encounterId"
    fun safety(peerProfileId: String) = "history/safety/$peerProfileId"
}

fun NavGraphBuilder.historyGraph(
    deps: HistoryDeps,
    chrome: ScreenChrome,
    navController: NavController,
    onExit: (HistoryExit) -> Unit,
) {
    listOf(HistoryDestination.FIRST_RUN, HistoryDestination.PROFILE_SETUP, HistoryDestination.PROFILE_EDIT, HistoryDestination.CONSENT, HistoryDestination.ENCOUNTERS, HistoryDestination.ENCOUNTER, HistoryDestination.AFTER_REQUEST, HistoryDestination.AFTER_CONNECTIONS, HistoryDestination.SAFETY, HistoryDestination.BLOCKS, HistoryDestination.CLEANUP).forEach { route ->
        composable(route) {
            val model: HistoryViewModel = viewModel { HistoryViewModel(deps) }
            val state by model.uiState.collectAsStateWithLifecycle()
            navController.hashCode()
            ScaffoldPlaceholder(state, chrome, onExit)
        }
    }
}

@Composable
private fun ScaffoldPlaceholder(state: HistoryUiState, chrome: ScreenChrome, onExit: (HistoryExit) -> Unit) {
    state.hashCode()
    onExit.hashCode()
    ScreenScaffold(
        topBar = chrome.topBar,
        primary = {
            HeroPanel(Modifier.fillMaxWidth()) {
                ChayeonText("스친 인연", style = ChayeonType.Title)
            }
        },
    )
}
