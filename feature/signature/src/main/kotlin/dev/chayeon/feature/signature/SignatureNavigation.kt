package dev.chayeon.feature.signature

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import dev.chayeon.core.model.TraitId
import dev.chayeon.core.ui.ScreenChrome
import kotlinx.coroutines.launch

sealed interface SignatureExit {
    data object OpenMeet : SignatureExit
    data object OpenProfileEdit : SignatureExit
    data object OpenConsent : SignatureExit
    data object OpenHomeZone : SignatureExit
    data object OpenBlocks : SignatureExit
    data object OpenCleanup : SignatureExit
    data object CreateSignature : SignatureExit
}

object SignatureDestination {
    const val MY_PROFILE = "signature/profile"
    const val MY_SIGNATURE = "signature/mine"
    const val TRAIT = "signature/trait/{traitId}"
    const val VISIBILITY = "signature/visibility"
    const val RECAP = "signature/recap/{recapId}"

    fun trait(traitId: String) = "signature/trait/${Uri.encode(traitId)}"
    fun recap(recapId: String) = "signature/recap/${Uri.encode(recapId)}"
}

fun NavGraphBuilder.signatureGraph(
    deps: SignatureDeps,
    chrome: ScreenChrome,
    navController: NavController,
    onExit: (SignatureExit) -> Unit,
) {
    composable(SignatureDestination.MY_PROFILE) {
        val model: MyProfileViewModel = viewModel { MyProfileViewModel(deps) }
        val state by model.uiState.collectAsStateWithLifecycle()
        val allowed = allowedAction(deps)
        BackHandler { allowed { onExit(SignatureExit.OpenMeet) } }
        MyProfileScreen(state, chrome,
            onSignature = { allowed { navController.navigate(SignatureDestination.MY_SIGNATURE) { launchSingleTop = true } } },
            onVisibility = { allowed { navController.navigate(SignatureDestination.VISIBILITY) { launchSingleTop = true } } },
            onExit = { exit -> allowed { onExit(exit) } }, onRetry = model::retry,
            onReceiveRequestsWhileDriving = model::setReceiveRequestsWhileDriving)
    }
    composable(SignatureDestination.MY_SIGNATURE) {
        val model: SignatureViewModel = viewModel { SignatureViewModel(deps) }
        val state by model.uiState.collectAsStateWithLifecycle()
        val allowed = allowedAction(deps)
        val back = { allowed { if (!navController.popBackStack()) onExit(SignatureExit.OpenMeet) } }
        BackHandler(onBack = back)
        MySignatureScreen(state, chrome, back,
            onVisibility = { allowed { navController.navigate(SignatureDestination.VISIBILITY) { launchSingleTop = true } } },
            onTrait = { trait -> allowed { navController.navigate(SignatureDestination.trait(trait.name)) { launchSingleTop = true } } },
            onDemo = { allowed { onExit(SignatureExit.CreateSignature) } }, onRetry = model::retry)
    }
    composable(SignatureDestination.TRAIT, arguments = listOf(navArgument("traitId") { type = NavType.StringType })) { entry ->
        val trait = TraitId.entries.firstOrNull { it.name == entry.arguments?.getString("traitId") }
        val allowed = allowedAction(deps)
        val back = { allowed { if (!navController.popBackStack()) navController.navigate(SignatureDestination.MY_SIGNATURE) } }
        BackHandler(onBack = back)
        if (trait != null) {
            val model: TraitDetailViewModel = viewModel { TraitDetailViewModel(deps, trait) }
            val state by model.uiState.collectAsStateWithLifecycle()
            TraitDetailScreen(state, chrome, back, model::retry)
        } else {
            val model: SignatureViewModel = viewModel { SignatureViewModel(deps) }
            val state by model.uiState.collectAsStateWithLifecycle()
            SignatureFrame("특성 상세", state.driving.policy, chrome, back, primary = {
                SignatureMessage("특성을 찾을 수 없어요", "내 시그니처에서 특성을 다시 골라 주세요.", action = "내 시그니처 보기",
                    onAction = { allowed { navController.navigate(SignatureDestination.MY_SIGNATURE) { launchSingleTop = true } } })
            })
        }
    }
    composable(SignatureDestination.VISIBILITY) {
        val model: SignatureVisibilityViewModel = viewModel { SignatureVisibilityViewModel(deps) }
        val state by model.uiState.collectAsStateWithLifecycle()
        val allowed = allowedAction(deps)
        val back = { allowed {
            if (!model.uiState.value.savingVisibility && !model.uiState.value.publishing && !navController.popBackStack())
                navController.navigate(SignatureDestination.MY_PROFILE)
        } }
        BackHandler(onBack = back)
        SignatureVisibilityScreen(state, chrome, back, model::setTraitVisible, model::setPublished, model::retry)
    }
    composable(SignatureDestination.RECAP, arguments = listOf(navArgument("recapId") { type = NavType.StringType }),
        enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None }) { entry ->
        val recapId = entry.arguments?.getString("recapId").orEmpty()
        val model: TripRecapViewModel = viewModel { TripRecapViewModel(deps, recapId) }
        val state by model.uiState.collectAsStateWithLifecycle()
        val allowed = allowedAction(deps)
        val back = { allowed {
            val current = model.uiState.value
            if (!current.excluding && !current.markingSeen && !current.pendingChanges && !navController.popBackStack())
                onExit(SignatureExit.OpenMeet)
        } }
        BackHandler(onBack = back)
        LaunchedEffect(state.openSignature, state.openVisibility) {
            if (state.openSignature || state.openVisibility) {
                val destination = if (state.openVisibility) SignatureDestination.VISIBILITY else SignatureDestination.MY_SIGNATURE
                if (deps.allowedNow()) navController.navigate(destination) {
                    popUpTo(entry.destination.id) { inclusive = true }
                    launchSingleTop = true
                }
                model.consumeNavigation()
            }
        }
        TripRecapScreen(state, chrome, back, model::excludeTrip, onSignature = {
            if (state.recap.readyValue() == null) allowed { navController.navigate(SignatureDestination.MY_SIGNATURE) { launchSingleTop = true } }
            else model.markSeen()
        }, onRetry = model::retry, onVisibility = { model.markSeen(openVisibility = true) })
    }
}

@Composable
private fun allowedAction(deps: SignatureDeps): (() -> Unit) -> Unit {
    val scope = rememberCoroutineScope()
    return { action -> scope.launch { if (deps.allowedNow()) action() }; Unit }
}
