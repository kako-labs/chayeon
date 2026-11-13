package dev.chayeon.feature.history

import androidx.compose.runtime.Composable
import dev.chayeon.core.model.*
import dev.chayeon.core.ui.*

internal enum class HistoryScreen { FIRST_RUN, PROFILE_SETUP, PROFILE_EDIT, CONSENT, RELATIONSHIPS, PERSON, AFTER_REQUEST, AFTER_INCOMING, BLOCK_REPORT, BLOCKS, CLEANUP }
internal enum class ScreenScenario { POPULATED, LOADING, EMPTY, FAILURE, RESTRICTED, UNKNOWN, NARROW }

internal fun screenState(screen: HistoryScreen, scenario: ScreenScenario): HistoryUiState {
    var state = HistoryFixtures.state
    if (screen == HistoryScreen.AFTER_INCOMING) state = state.copy(requests = LoadState.Ready(listOf(HistoryFixtures.request), 1), relationships = LoadState.Ready(emptyList(), 1))
    if (screen == HistoryScreen.RELATIONSHIPS) {
        val incoming = HistoryFixtures.peer.copy(id = "incoming", nickname = "별보는밤", avatar = AvatarId.STAR)
        val outgoing = HistoryFixtures.peer.copy(id = "outgoing", nickname = "숲길산책", avatar = AvatarId.FOREST)
        val crossed = HistoryFixtures.peer.copy(id = "crossed", nickname = "재즈한잔", avatar = AvatarId.JAZZ)
        state = state.copy(encounters = LoadState.Ready(listOf(HistoryFixtures.encounter,
            HistoryFixtures.encounter.copy(id = "incoming-encounter", peer = incoming),
            HistoryFixtures.encounter.copy(id = "outgoing-encounter", peer = outgoing),
            HistoryFixtures.encounter.copy(id = "crossed-encounter", peer = crossed)), 1),
            requests = LoadState.Ready(listOf(HistoryFixtures.request.copy(status = AfterRequestStatus.ACCEPTED),
                HistoryFixtures.request.copy(id = "incoming-request", encounterId = "incoming-encounter", peer = incoming),
                HistoryFixtures.request.copy(id = "outgoing-request", encounterId = "outgoing-encounter", peer = outgoing, direction = RequestDirection.OUTGOING)), 1))
    }
    if (screen == HistoryScreen.AFTER_REQUEST) state = state.copy(requests = LoadState.Ready(emptyList(), 1))
    if (screen == HistoryScreen.BLOCK_REPORT) state = state.copy(safetyPeer = HistoryFixtures.peer)
    if (screen == HistoryScreen.BLOCKS) state = state.copy(blocks = LoadState.Ready(listOf(HistoryFixtures.block), 1))
    if (screen == HistoryScreen.CONSENT) state = state.copy(discovery = DiscoveryState(), profile = LoadState.Ready(HistoryFixtures.profile.copy(registration = RegistrationState.CONSENT_REQUIRED), 1))
    return when (scenario) {
        ScreenScenario.POPULATED, ScreenScenario.NARROW -> state
        ScreenScenario.RESTRICTED -> state.copy(driving = DrivingState(policy = DrivingPolicy.RESTRICTED, gear = Gear.DRIVE))
        ScreenScenario.UNKNOWN -> state.copy(driving = DrivingState())
        ScreenScenario.LOADING -> state.copy(personas = LoadState.Loading(), profile = LoadState.Loading(), settings = LoadState.Loading(), encounters = LoadState.Loading(), encounter = LoadState.Loading(), requests = LoadState.Loading(), relationships = LoadState.Loading(), blocks = LoadState.Loading(), connection = LoadState.Loading(), draftLoaded = false, safetyPeer = null)
        ScreenScenario.EMPTY -> state.copy(personas = LoadState.Ready(emptyList(), 2), encounters = LoadState.Ready(emptyList(), 2), encounter = LoadState.Ready(null, 2), requests = LoadState.Ready(emptyList(), 2), relationships = LoadState.Ready(emptyList(), 2), blocks = LoadState.Ready(emptyList(), 2), draft = ProfileDraft("", AvatarId.DAWN, emptySet()), safetyPeer = null)
        ScreenScenario.FAILURE -> state.copy(personas = LoadState.Failed(AppError.Network), encounters = LoadState.Failed(AppError.Network),
            commands = listOf("enter", "save", "request", "respond:request", "report", "block", "unblock:block", "cleanup:DELETE_RECORDS:", "consent:VEHICLE_DATA", "delete").associateWith { CommandState(error = AppError.Network) })
    }
}

@Composable
internal fun RenderHistoryScreen(screen: HistoryScreen, state: HistoryUiState, showSourceLabels: Boolean = true) {
    val chrome = previewChrome(policy = state.driving.policy, gear = state.driving.gear, showSourceLabels = showSourceLabels)
    when (screen) {
        HistoryScreen.FIRST_RUN -> FirstRunScreen(state, chrome, {}, {}, {}, {}, {})
        HistoryScreen.PROFILE_SETUP, HistoryScreen.PROFILE_EDIT -> ProfileSetupScreen(state, chrome, screen == HistoryScreen.PROFILE_EDIT, {}, {}, {}, {})
        HistoryScreen.CONSENT -> ConsentScreen(state, chrome, { _, _ -> }, {}, {}, {}, {}, {})
        HistoryScreen.RELATIONSHIPS -> RelationshipsScreen(state, chrome, {}, {}, {})
        HistoryScreen.PERSON -> PersonScreen(state, chrome, {}, {}, {}, {}, {}, {}, {}, {})
        HistoryScreen.AFTER_REQUEST, HistoryScreen.AFTER_INCOMING -> AfterRequestScreen(state, chrome, {}, { _, _ -> }, {}, {}, {}, {}, {}, {}, {})
        HistoryScreen.BLOCK_REPORT -> SafetyScreen(state, chrome, "peer", {}, {}, {}, {}, {}, {}, {}, {})
        HistoryScreen.BLOCKS -> BlocksScreen(state, chrome, {}, {}, {}, {}, {})
        HistoryScreen.CLEANUP -> CleanupScreen(state, chrome, { _, _ -> }, {}, {}, {}, {}, {})
    }
}
