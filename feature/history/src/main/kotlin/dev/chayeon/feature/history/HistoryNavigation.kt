package dev.chayeon.feature.history

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.chayeon.core.model.*
import dev.chayeon.core.ui.ScreenChrome
import dev.chayeon.core.ui.ViewedContent

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
    const val HOME_ZONE = "history/home-zone"
    const val RELATIONSHIPS = "history/relationships"
    const val PERSON = "history/person/{peerId}"
    const val AFTER_REQUEST = "history/after-request/{encounterId}"
    const val SAFETY = "history/safety/{peerProfileId}"
    const val BLOCKS = "history/blocks"
    const val CLEANUP = "history/cleanup"

    fun person(peerId: String) = "history/person/$peerId"
    fun afterRequest(encounterId: String) = "history/after-request/$encounterId"
    fun safety(peerProfileId: String) = "history/safety/$peerProfileId"
}

fun NavGraphBuilder.historyGraph(
    deps: HistoryDeps,
    chrome: ScreenChrome,
    navController: NavController,
    onExit: (HistoryExit) -> Unit,
) {
    fun open(route: String) { navController.navigate(route) { launchSingleTop = true } }
    fun back() { if (!navController.popBackStack()) onExit(HistoryExit.OpenMeet) }

    composable(HistoryDestination.FIRST_RUN) {
        val model: FirstRunViewModel = viewModel { FirstRunViewModel(deps) }
        val state by model.uiState.collectAsStateWithLifecycle()
        LaunchedEffect(state.session, state.profile, state.driving.policy, state.resumeHandled) {
            if (!state.resumeHandled && state.driving.policy == DrivingPolicy.ALLOWED && state.session is SessionState.Active && state.profile is LoadState.Ready) {
                model.markResumed()
                when (state.myProfile?.registration) {
                    RegistrationState.READY -> onExit(HistoryExit.OpenMeet)
                    RegistrationState.CONSENT_REQUIRED -> open(HistoryDestination.CONSENT)
                    else -> open(HistoryDestination.PROFILE_SETUP)
                }
            }
        }
        LaunchedEffect(state.modes, state.session, state.driving.policy) {
            model.enterSoloIfNeeded()
        }
        FirstRunScreen(state, chrome, model::selectMode, model::enter, model::retry)
    }
    listOf(HistoryDestination.PROFILE_SETUP, HistoryDestination.PROFILE_EDIT).forEach { route ->
        composable(route) {
            val model: ProfileSetupViewModel = viewModel { ProfileSetupViewModel(deps) }
            val state by model.uiState.collectAsStateWithLifecycle()
            val editing = route == HistoryDestination.PROFILE_EDIT
            LaunchedEffect(state.profile, state.driving.policy, state.command("save").completed) {
                if (!editing && state.command("save").completed && state.driving.policy == DrivingPolicy.ALLOWED) {
                    when (state.myProfile?.registration) {
                        RegistrationState.CONSENT_REQUIRED -> {
                            model.consumeSavedNavigation()
                            open(HistoryDestination.CONSENT)
                        }
                        RegistrationState.READY -> {
                            model.consumeSavedNavigation()
                            onExit(HistoryExit.OpenMeet)
                        }
                        else -> Unit
                    }
                }
            }
            ProfileSetupScreen(state, chrome, editing, model::updateDraft, model::save, { model.retry("save") }, { model.navigate(::back) })
        }
    }
    composable(HistoryDestination.CONSENT) {
        val model: ConsentViewModel = viewModel { ConsentViewModel(deps) }
        val state by model.uiState.collectAsStateWithLifecycle()
        ConsentScreen(state, chrome, model::record, model::setDiscovery,
            { model.navigate { onExit(HistoryExit.OpenSignatureVisibility) } },
            { model.navigate { if (state.myProfile?.registration == RegistrationState.READY) onExit(HistoryExit.OpenMeet) } },
            model::retry, { model.navigate(::back) }, { model.openHomeZone { open(HistoryDestination.HOME_ZONE) } }, model::requestMicrophone)
    }
    composable(HistoryDestination.HOME_ZONE) {
        val model: HomeZoneViewModel = viewModel { HomeZoneViewModel(deps) }
        val state by model.uiState.collectAsStateWithLifecycle()
        HomeZoneScreen(state, chrome, model::setHome, model::selectRadius, model::askClear,
            model::clear, model::dismissClear, { model.back(::back) })
    }
    composable(HistoryDestination.RELATIONSHIPS) {
        val model: RelationshipsViewModel = viewModel { RelationshipsViewModel(deps) }
        val state by model.uiState.collectAsStateWithLifecycle()
        RelationshipsScreen(state, chrome,
            { id -> model.navigate { open(HistoryDestination.person(id)) } },
            { id -> model.navigate { open(HistoryDestination.afterRequest(id)) } },
            { model.navigate { onExit(HistoryExit.OpenMeet) } })
    }
    composable(HistoryDestination.PERSON) { entry ->
        val id = entry.arguments?.getString("peerId").orEmpty()
        val model: RelationshipsViewModel = viewModel { RelationshipsViewModel(deps, id) }
        val state by model.uiState.collectAsStateWithLifecycle()
        ViewedContent(chrome, state.driving.policy, requestId = state.person?.request?.id)
        PersonScreen(state, chrome,
            { encounter -> model.navigate { open(HistoryDestination.afterRequest(encounter)) } },
            { peer -> model.navigate { open(HistoryDestination.safety(peer)) } },
            model::askDelete, model::askEnd, model::confirmAction, model::dismissConfirmation, model::retry,
            { model.navigate(::back) })
    }
    composable(HistoryDestination.AFTER_REQUEST) { entry ->
        val id = entry.arguments?.getString("encounterId").orEmpty()
        val model: AfterRequestViewModel = viewModel { AfterRequestViewModel(deps, id) }
        val state by model.uiState.collectAsStateWithLifecycle()
        ViewedContent(chrome, state.driving.policy, requestId = state.requests.valueOrNull()?.firstOrNull { it.encounterId == id }?.id)
        AfterRequestScreen(state, chrome, model::request, model::respond, model::askWithdraw,
            { model.navigate {
                val peerId = state.encounter.valueOrNull()?.peer?.id ?: state.requests.valueOrNull()?.filter { it.encounterId == id }?.maxByOrNull { it.createdAtEpochMillis }?.peer?.id
                peerId?.let { open(HistoryDestination.person(it)) }
            } },
            { peer -> model.navigate { open(HistoryDestination.safety(peer)) } },
            model::retry, model::withdraw, model::dismissConfirmation, { model.navigate(::back) }, model::setContacts, model::setSaveToProfile)
    }
    composable(HistoryDestination.SAFETY) { entry ->
        val id = entry.arguments?.getString("peerProfileId").orEmpty()
        val model: SafetyViewModel = viewModel { SafetyViewModel(deps, id) }
        val state by model.uiState.collectAsStateWithLifecycle()
        SafetyScreen(state, chrome, id, model::selectReason, model::report, model::askBlock,
            { model.navigate { open(HistoryDestination.BLOCKS) } }, model::retry, model::confirmAction, model::dismissConfirmation,
            { model.navigate(::back) })
    }
    composable(HistoryDestination.BLOCKS) {
        val model: SafetyViewModel = viewModel { SafetyViewModel(deps) }
        val state by model.uiState.collectAsStateWithLifecycle()
        BlocksScreen(state, chrome, model::askUnblock, model::retry, model::confirmAction, model::dismissConfirmation, { model.navigate(::back) })
    }
    composable(HistoryDestination.CLEANUP) {
        val model: CleanupViewModel = viewModel { CleanupViewModel(deps) }
        val state by model.uiState.collectAsStateWithLifecycle()
        LaunchedEffect(state.commands) {
            if (state.command("cleanup:SIGN_OUT:").completed || state.command("cleanup:DELETE_ACCOUNT:").completed) onExit(HistoryExit.SignedOut)
        }
        CleanupScreen(state, chrome, model::ask, model::execute, model::dismissConfirmation, model::retry, model::leaveWhileLocked, { model.navigate(::back) })
    }
}
