package dev.chayeon.feature.signature

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

sealed interface SignatureExit {
    data object OpenMeet : SignatureExit
    data object OpenProfileEdit : SignatureExit
    data object OpenConsent : SignatureExit
    data object OpenBlocks : SignatureExit
    data object OpenCleanup : SignatureExit
    data object OpenDemoConsole : SignatureExit
}

object SignatureDestination {
    const val MY_PROFILE = "signature/profile"
    const val MY_SIGNATURE = "signature/mine"
    const val TRAIT = "signature/trait/{traitId}"
    const val VISIBILITY = "signature/visibility"
    const val RECAP = "signature/recap/{recapId}"

    fun trait(traitId: String) = "signature/trait/$traitId"
    fun recap(recapId: String) = "signature/recap/$recapId"
}

fun NavGraphBuilder.signatureGraph(
    deps: SignatureDeps,
    chrome: ScreenChrome,
    onExit: (SignatureExit) -> Unit,
) {
    listOf(SignatureDestination.MY_PROFILE, SignatureDestination.MY_SIGNATURE, SignatureDestination.TRAIT, SignatureDestination.VISIBILITY, SignatureDestination.RECAP).forEach { route ->
        composable(route) {
            val model: SignatureViewModel = viewModel { SignatureViewModel(deps) }
            val state by model.uiState.collectAsStateWithLifecycle()
            ScaffoldPlaceholder(state, chrome, onExit)
        }
    }
}

@Composable
private fun ScaffoldPlaceholder(state: SignatureUiState, chrome: ScreenChrome, onExit: (SignatureExit) -> Unit) {
    state.hashCode()
    onExit.hashCode()
    ScreenScaffold(
        topBar = chrome.topBar,
        primary = {
            HeroPanel(Modifier.fillMaxWidth()) {
                ChayeonText("내 프로필", style = ChayeonType.Title)
            }
        },
    )
}
