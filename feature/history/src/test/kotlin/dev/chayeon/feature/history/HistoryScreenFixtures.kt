package dev.chayeon.feature.history

import androidx.compose.runtime.Composable
import dev.chayeon.core.model.*
import dev.chayeon.core.ui.*

internal enum class HistoryScreen { FIRST_RUN, PROFILE_SETUP, PROFILE_EDIT, CONSENT, ENCOUNTERS, ENCOUNTER_DETAIL, AFTER_REQUEST, AFTER_INCOMING, AFTER_CONNECTED, BLOCK_REPORT, BLOCKS, CLEANUP }
internal enum class ScreenScenario { POPULATED, LOADING, EMPTY, FAILURE, RESTRICTED, UNKNOWN, NARROW }

internal fun screenState(screen: HistoryScreen, scenario: ScreenScenario): HistoryUiState {
    var state = HistoryFixtures.state
    if (screen == HistoryScreen.ENCOUNTER_DETAIL) state = state.copy(
        encounter = LoadState.Ready(HistoryFixtures.encounter.copy(chemistry = HistoryFixtures.detailedChemistry,
            peer = HistoryFixtures.peer.copy(signature = HistoryFixtures.detailedSignature)), 1),
        profile = LoadState.Ready(HistoryFixtures.profile.copy(publicProfile = HistoryFixtures.mine.copy(signature = HistoryFixtures.detailedSignature)), 1),
    )
    if (screen == HistoryScreen.AFTER_CONNECTED) state = state.copy(relationships = LoadState.Ready(listOf(
        HistoryFixtures.relationship.copy(availablePresets = HistoryFixtures.relationship.availablePresets + setOf(
            PresetCode.NICE_TO_MEET_YOU, PresetCode.SHARED_INTERESTS, PresetCode.TALK_LATER, PresetCode.TAKE_CARE,
        )),
    ), 1))
    if (screen == HistoryScreen.AFTER_REQUEST) state = state.copy(requests = LoadState.Ready(emptyList(), 1))
    if (screen == HistoryScreen.BLOCK_REPORT) state = state.copy(blocks = LoadState.Ready(emptyList(), 1), safetyPeer = HistoryFixtures.peer)
    if (screen == HistoryScreen.CONSENT) state = state.copy(discovery = DiscoveryState(),
        profile = LoadState.Ready(HistoryFixtures.profile.copy(registration = RegistrationState.CONSENT_REQUIRED), 1))
    return when (scenario) {
        ScreenScenario.POPULATED, ScreenScenario.NARROW -> state
        ScreenScenario.RESTRICTED -> state.copy(driving = DrivingState(policy = DrivingPolicy.RESTRICTED, gear = Gear.DRIVE))
        ScreenScenario.UNKNOWN -> state.copy(driving = DrivingState())
        ScreenScenario.LOADING -> state.copy(personas = LoadState.Loading(), profile = LoadState.Loading(), settings = LoadState.Loading(), encounters = LoadState.Loading(), encounter = LoadState.Loading(), requests = LoadState.Loading(), relationships = LoadState.Loading(), messages = LoadState.Loading(), blocks = LoadState.Loading(), connection = LoadState.Loading(), draftLoaded = false, safetyPeer = null)
        ScreenScenario.EMPTY -> state.copy(personas = LoadState.Ready(emptyList(), 2), encounters = LoadState.Ready(emptyList(), 2), encounter = LoadState.Ready(null, 2), requests = LoadState.Ready(emptyList(), 2), relationships = LoadState.Ready(emptyList(), 2), messages = LoadState.Ready(emptyList(), 2), blocks = LoadState.Ready(emptyList(), 2), draft = ProfileDraft("", AvatarId.DAWN, emptySet()), safetyPeer = null)
        ScreenScenario.FAILURE -> state.copy(personas = LoadState.Failed(AppError.Network), encounters = LoadState.Failed(AppError.Network),
            commands = listOf("enter", "save", "request", "respond:request", "compose:after", "report", "block", "unblock:block", "cleanup:DELETE_RECORDS:", "consent:VEHICLE_DATA", "delete").associateWith { CommandState(error = AppError.Network) },
            outbox = listOf(PendingAfterMessage(SendAfterMessageRequest(CommandMeta("send"), "after", "failed-message", AfterMessageContent.Text("다시 만나서 반가워요")), DeliveryState.FAILED)))
    }
}

@Composable
internal fun RenderHistoryScreen(screen: HistoryScreen, state: HistoryUiState, showSourceLabels: Boolean = true) {
    val chrome = previewChrome(policy = state.driving.policy, gear = state.driving.gear, showSourceLabels = showSourceLabels)
    when (screen) {
        HistoryScreen.FIRST_RUN -> FirstRunScreen(state, chrome, {}, {}, {}, {}, {})
        HistoryScreen.PROFILE_SETUP, HistoryScreen.PROFILE_EDIT -> ProfileSetupScreen(state, chrome, screen == HistoryScreen.PROFILE_EDIT, {}, {}, {}, {})
        HistoryScreen.CONSENT -> ConsentScreen(state, chrome, { _, _ -> }, {}, {}, {}, {}, {})
        HistoryScreen.ENCOUNTERS -> EncountersScreen(state, chrome, {}, {}, {})
        HistoryScreen.ENCOUNTER_DETAIL -> EncounterDetailScreen(state, chrome, {}, {}, {}, {}, {}, {}, {})
        HistoryScreen.AFTER_REQUEST, HistoryScreen.AFTER_INCOMING -> AfterRequestScreen(state, chrome, {}, { _, _ -> }, {}, {}, {}, {}, {}, {}, {})
        HistoryScreen.AFTER_CONNECTED -> AfterConnectionsScreen(state, chrome, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
        HistoryScreen.BLOCK_REPORT -> SafetyScreen(state, chrome, "peer", {}, {}, {}, {}, {}, {}, {}, {})
        HistoryScreen.BLOCKS -> BlocksScreen(state, chrome, {}, {}, {}, {}, {})
        HistoryScreen.CLEANUP -> CleanupScreen(state, chrome, { _, _ -> }, {}, {}, {}, {}, {})
    }
}
