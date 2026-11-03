package dev.chayeon.feature.meet

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.chayeon.core.ui.ChayeonText
import dev.chayeon.core.ui.ChayeonType
import dev.chayeon.core.ui.HeroPanel
import dev.chayeon.core.ui.ScreenChrome
import dev.chayeon.core.ui.ScreenScaffold

sealed interface MeetExit {
    data class OpenEncounter(val encounterId: String) : MeetExit
    data class OpenSafety(val peerProfileId: String) : MeetExit
    data object OpenMySignature : MeetExit
}

object MeetDestination {
    const val HOME = "meet/home"
    const val CANDIDATE = "meet/candidate/{candidateId}"
    const val REQUESTS = "meet/requests"
    const val CONNECTED = "meet/connected"
    const val ENDED = "meet/ended/{connectionId}"
    const val DRIVING = "meet/driving"

    fun candidate(candidateId: String) = "meet/candidate/$candidateId"
    fun ended(connectionId: String) = "meet/ended/$connectionId"
}

fun NavGraphBuilder.meetGraph(
    deps: MeetDeps,
    chrome: ScreenChrome,
    onExit: (MeetExit) -> Unit,
) {
    listOf(MeetDestination.HOME, MeetDestination.CANDIDATE, MeetDestination.REQUESTS, MeetDestination.CONNECTED, MeetDestination.ENDED, MeetDestination.DRIVING).forEach { route ->
        composable(route) {
            val model: MeetViewModel = viewModel { MeetViewModel(deps) }
            val state by model.uiState.collectAsStateWithLifecycle()
            ScaffoldPlaceholder(state, chrome, onExit)
        }
    }
}

@Composable
private fun ScaffoldPlaceholder(state: MeetUiState, chrome: ScreenChrome, onExit: (MeetExit) -> Unit) {
    state.hashCode()
    onExit.hashCode()
    ScreenScaffold(
        topBar = chrome.topBar,
        primary = {
            HeroPanel(Modifier.fillMaxWidth()) {
                ChayeonText("오늘의 인연", style = ChayeonType.Title)
            }
        },
    )
}
