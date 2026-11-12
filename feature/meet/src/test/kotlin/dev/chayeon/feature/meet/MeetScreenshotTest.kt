package dev.chayeon.feature.meet

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureRoboImage
import dev.chayeon.core.model.*
import dev.chayeon.core.ui.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w1682dp-h1210dp-land-mdpi")
class MeetScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val allTraits = MeetFixtures.signature.copy(traits = TraitId.entries.map { PublicTrait(it, TraitLevel.SIGNATURE) })
    private val allTraitsCandidate = MeetFixtures.candidate.copy(profile = MeetFixtures.peer.copy(signature = allTraits))
    private val home = HomeUiState(MeetFixtures.parked, MeetFixtures.discovery, MeetFixtures.own, profileLoaded = true)
    private val candidate = CandidateUiState(MeetFixtures.parked, MeetFixtures.candidate, MeetFixtures.own, unavailableState = null, canRequest = true)
    private val incoming = RequestUiState(MeetFixtures.parked, MeetFixtures.request, MeetFixtures.own, loading = false, remainingMillis = 108_000)
    private val sent = incoming.copy(request = MeetFixtures.request.copy(direction = RequestDirection.OUTGOING))
    private val greeting = GreetingUi("greeting-self", PresetCode.HELLO, MessageAuthor.SELF, DeliveryState.SENT)
    private val connected = ConnectedUiState(MeetFixtures.parked, MeetFixtures.connection, MeetFixtures.own,
        loading = false, greetings = listOf(greeting, GreetingUi("greeting-peer", PresetCode.NICE_TO_MEET_YOU, MessageAuthor.PEER, DeliveryState.SENT)), greetingsLoading = false)
    private val ended = EndedUiState(MeetFixtures.parked, MeetFixtures.ended, MeetFixtures.own, loading = false)

    private fun driving(policy: DrivingPolicy) = MeetFixtures.parked.copy(policy = policy,
        gear = if (policy == DrivingPolicy.RESTRICTED) Gear.DRIVE else Gear.UNKNOWN)

    private fun capture(name: String, policy: DrivingPolicy = DrivingPolicy.ALLOWED, scrollTo: String? = null, showSourceLabels: Boolean = false,
        content: @Composable (ScreenChrome) -> Unit) {
        compose.setContent {
            ChayeonTheme(policy, animatorScaleOverride = 0f) { content(previewChrome(showSourceLabels = showSourceLabels, gear = when (policy) { DrivingPolicy.ALLOWED -> Gear.PARK; DrivingPolicy.RESTRICTED -> Gear.DRIVE; DrivingPolicy.UNKNOWN -> Gear.UNKNOWN }, policy = policy)) }
        }
        if (scrollTo != null) compose.onNodeWithText(scrollTo).performScrollTo()
        compose.onNodeWithTag("meet-screen").captureRoboImage("src/test/screenshots/$name.png")
    }

    private fun home(name: String, state: HomeUiState = home) = capture("home_$name", state.driving.policy) { chrome ->
        HomeScreen(state, if (state.homeZone.suppressed) previewChrome(policy = state.driving.policy, resting = true, showSourceLabels = false) else chrome, {}, {}, {}, {}, {}, {}, {})
    }
    private fun discovery(name: String, status: DiscoveryStatus) = home(name, home.copy(discovery = MeetFixtures.discovery.copy(status = status,
        candidates = if (status == DiscoveryStatus.AVAILABLE) MeetFixtures.discovery.candidates else emptyList())))
    private fun candidate(name: String, state: CandidateUiState = candidate, scrollTo: String? = null) = capture("candidate_$name", state.driving.policy, scrollTo) { chrome ->
        CandidateScreen(state, chrome, {}, {}, {})
    }
    private fun request(name: String, state: RequestUiState) = capture("request_$name", state.driving.policy) { chrome ->
        RequestScreen(state, chrome, {}, {}, {}, {}, {})
    }
    private fun connected(name: String, state: ConnectedUiState = connected) = capture("connected_$name", state.driving.policy) { chrome ->
        ConnectedScreen(state, chrome, {}, {}, {}, {})
    }
    private fun ended(name: String, state: EndedUiState = ended) = capture("ended_$name", state.driving.policy) { chrome ->
        EndedScreen(state, chrome, {}, {})
    }

    @Test fun homePopulated() = home("populated", home.copy(discovery = MeetFixtures.discovery.copy(candidates = listOf(
        MeetFixtures.candidate,
        MeetFixtures.candidate.copy(id = "candidate-sea", profile = MeetFixtures.peer.copy(id = "peer-sea", nickname = "바다보러가요", avatar = AvatarId.SEA)),
        MeetFixtures.candidate.copy(id = "candidate-forest", profile = MeetFixtures.peer.copy(id = "peer-forest", nickname = "숲길산책", avatar = AvatarId.FOREST)),
    ))))
    @Test fun serverMixedPeersRetainLabels() = capture("home_server_mixed", showSourceLabels = true) { chrome ->
        HomeScreen(home.copy(discovery = MeetFixtures.discovery.copy(candidates = listOf(MeetFixtures.candidate,
            MeetFixtures.candidate.copy(id = "participant", profile = MeetFixtures.own)))), chrome, {}, {}, {}, {}, {}, {}, {})
    }
    @Test fun serverCandidateRetainsLabels() = capture("candidate_server", showSourceLabels = true) {
        CandidateScreen(candidate, it, {}, {}, {})
    }
    @Test fun serverConnectedRetainsLabels() = capture("connected_server", showSourceLabels = true) {
        ConnectedScreen(connected, it, {}, {}, {}, {})
    }
    @Test fun homeMixedPeers() = home("mixed_peers", home.copy(discovery = MeetFixtures.discovery.copy(candidates = listOf(
        MeetFixtures.candidate,
        MeetFixtures.candidate.copy(id = "participant", profile = MeetFixtures.own),
    ))))
    @Test fun homeDemoAway() = home("demo_away", home.copy(demoPresence = SyntheticPresence.AWAY,
        discovery = MeetFixtures.discovery.copy(status = DiscoveryStatus.EMPTY, candidates = emptyList())))
    @Test @Config(sdk = [35], qualifiers = "w840dp-h1210dp-port-mdpi")
    fun homeDemoAwayNarrow() = home("demo_away_narrow", home.copy(demoPresence = SyntheticPresence.AWAY,
        discovery = MeetFixtures.discovery.copy(status = DiscoveryStatus.EMPTY, candidates = emptyList())))
    @Test fun homeZoneInside() = home("zone_inside", home.copy(homeZone = HomeZoneState(HomeZoneStatus.INSIDE, HomeZoneRadius.MEDIUM, LocationSource.DEVICE)))
    @Test fun homeZoneUnknown() = home("zone_unknown", home.copy(homeZone = HomeZoneState(HomeZoneStatus.LOCATION_UNKNOWN, HomeZoneRadius.MEDIUM, LocationSource.DEVICE)))
    @Test fun homeZonePermissionOff() = home("zone_permission_off", home.copy(homeZone = HomeZoneState(HomeZoneStatus.PERMISSION_OFF, HomeZoneRadius.MEDIUM, LocationSource.DEVICE)))
    @Test fun homeZoneSynthetic() = home("zone_synthetic", home.copy(homeZone = HomeZoneState(HomeZoneStatus.INSIDE, HomeZoneRadius.MEDIUM, LocationSource.SYNTHETIC)))
    @Test fun homeOff() = home("off", home.copy(discovery = DiscoveryState()))
    @Test fun homeLoading() = discovery("loading", DiscoveryStatus.LOADING)
    @Test fun homeEmpty() = discovery("empty", DiscoveryStatus.EMPTY)
    @Test fun homeNetworkFailure() = discovery("network_failure", DiscoveryStatus.NETWORK_FAILURE)
    @Test fun homeLocationPermissionOff() = discovery("location_permission_off", DiscoveryStatus.LOCATION_PERMISSION_OFF)
    @Test fun homeLocationStale() = discovery("location_stale", DiscoveryStatus.LOCATION_STALE)
    @Test fun homeAlreadyConnected() = discovery("already_connected", DiscoveryStatus.ALREADY_CONNECTED)
    @Test fun homeUnpublished() = home("unpublished", home.copy(profile = MeetFixtures.own.copy(signature = null),
        discovery = MeetFixtures.discovery.copy(candidates = AvatarId.entries.drop(1).mapIndexed { index, avatar ->
            MeetFixtures.candidate.copy(id = "candidate-$index", profile = MeetFixtures.peer.copy(id = "peer-$index", avatar = avatar,
                nickname = listOf("느린오후", "재즈한잔", "바다보러가요", "숲길산책", "별보는밤")[index]))
        })))
    @Test fun homePendingIncoming() = home("pending_incoming", home.copy(pendingRequest = MeetFixtures.request))
    @Test fun homePendingOutgoing() = home("pending_outgoing", home.copy(pendingRequest = sent.request))
    @Test fun homeToggleFailure() = home("toggle_failure", home.copy(toggle = MeetCommandState(error = AppError.Network)))
    @Test fun homeToggleSending() = home("toggle_sending", home.copy(toggle = MeetCommandState(inProgress = true)))
    @Test fun homeRefreshSending() = home("refresh_sending", home.copy(refresh = MeetCommandState(inProgress = true)))
    @Test fun homeRestricted() = home("restricted", home.copy(driving = driving(DrivingPolicy.RESTRICTED)))
    @Test fun homeUnknown() = home("unknown", home.copy(driving = driving(DrivingPolicy.UNKNOWN)))
    @Test @Config(sdk = [35], qualifiers = "w840dp-h1210dp-port-mdpi")
    fun homeNarrow() = home("narrow")

    @Test fun candidateTourSharedCare() = candidate("tour_shared_care", candidate.copy(candidate = MeetFixtures.tourCandidate, ownProfile = MeetFixtures.tourOwn))
    @Test @Config(sdk = [35], qualifiers = "w840dp-h1210dp-port-mdpi")
    fun candidateTourSharedCareNarrow() = candidate("tour_shared_care_narrow", candidate.copy(candidate = MeetFixtures.tourCandidate, ownProfile = MeetFixtures.tourOwn), scrollTo = "둘 다 함께 타는 공기까지 신경 써요")
    @Test fun candidatePopulated() = candidate("populated")
    @Test fun candidateNoSignature() = candidate("no_signature", candidate.copy(candidate = MeetFixtures.candidate.copy(profile = MeetFixtures.peer.copy(signature = null))))
    @Test fun candidateNoOwnSignature() = candidate("no_own_signature", candidate.copy(ownProfile = MeetFixtures.own.copy(signature = null)))
    @Test fun candidateAllTraits() = candidate("all_traits", candidate.copy(candidate = allTraitsCandidate))
    @Test fun candidateCompleteProfile() = candidate("complete_profile", candidate.copy(candidate = MeetFixtures.completeCandidate))
    @Test fun candidateAllTraitsScrolled() = candidate("all_traits_scrolled", candidate.copy(candidate = allTraitsCandidate), TraitId.CABIN_CARE.presentation.publicPhrase)
    @Test fun candidateLoading() = candidate("loading", candidate.copy(candidate = null, unavailableState = ExperienceState.DISCOVERY_LOADING, canRequest = false))
    @Test fun candidateEmpty() = candidate("empty", candidate.copy(candidate = null, unavailableState = ExperienceState.PEER_UNAVAILABLE, canRequest = false))
    @Test fun candidateFailure() = candidate("failure", candidate.copy(candidate = null, unavailableState = ExperienceState.NETWORK_FAILURE, canRequest = false))
    @Test fun candidateRequestFailure() = candidate("request_failure", candidate.copy(request = MeetCommandState(error = AppError.Network)))
    @Test fun candidateRequestSending() = candidate("request_sending", candidate.copy(request = MeetCommandState(inProgress = true)))
    @Test fun candidateCooldown() = candidate("cooldown", candidate.copy(canRequest = false))
    @Test fun candidateRestricted() = candidate("restricted", candidate.copy(driving = driving(DrivingPolicy.RESTRICTED)))
    @Test fun candidateUnknown() = candidate("unknown", candidate.copy(driving = driving(DrivingPolicy.UNKNOWN)))
    @Test @Config(sdk = [35], qualifiers = "w840dp-h1210dp-port-mdpi")
    fun candidateNarrow() = candidate("narrow")

    @Test fun requestSent() = request("sent_populated", sent)
    @Test fun requestIncoming() = request("incoming_populated", incoming)
    @Test fun requestHomeSuppressed() = request("home_suppressed", incoming.copy(homeSuppressed = true, accept = MeetCommandState(error = AppError.PeerUnavailable)))
    @Test fun requestLoading() = request("loading", incoming.copy(request = null, loading = true))
    @Test fun requestEmpty() = request("empty", incoming.copy(request = null))
    @Test fun requestFailure() = request("failure", incoming.copy(request = null, observationError = AppError.Network))
    @Test fun requestDeclined() = request("declined", sent.copy(request = sent.request!!.copy(status = ConnectionRequestStatus.DECLINED)))
    @Test fun requestExpired() = request("expired", sent.copy(request = sent.request!!.copy(status = ConnectionRequestStatus.EXPIRED)))
    @Test fun requestIncomingCancelled() = request("incoming_cancelled", incoming.copy(request = MeetFixtures.request.copy(status = ConnectionRequestStatus.CANCELLED)))
    @Test fun requestOutgoingCancelled() = request("outgoing_cancelled", sent.copy(request = sent.request!!.copy(status = ConnectionRequestStatus.CANCELLED)))
    @Test fun requestUnavailable() = request("unavailable", incoming.copy(request = MeetFixtures.request.copy(status = ConnectionRequestStatus.UNAVAILABLE)))
    @Test fun requestAccepted() = request("accepted", incoming.copy(request = MeetFixtures.request.copy(status = ConnectionRequestStatus.ACCEPTED, connectionId = "connection")))
    @Test fun requestUnknownCountdown() = request("unknown_countdown", incoming.copy(remainingMillis = null))
    @Test fun requestZeroCountdown() = request("zero_countdown", incoming.copy(remainingMillis = 0))
    @Test fun requestSentUnknownCountdown() = request("sent_unknown_countdown", sent.copy(remainingMillis = null))
    @Test fun requestSentZeroCountdown() = request("sent_zero_countdown", sent.copy(remainingMillis = 0))
    @Test fun requestAcceptFailure() = request("accept_failure", incoming.copy(accept = MeetCommandState(error = AppError.PeerUnavailable)))
    @Test fun requestDeclineFailure() = request("decline_failure", incoming.copy(decline = MeetCommandState(error = AppError.Network)))
    @Test fun requestCancelFailure() = request("cancel_failure", sent.copy(cancel = MeetCommandState(error = AppError.Network)))
    @Test fun requestAccepting() = request("accepting", incoming.copy(accept = MeetCommandState(inProgress = true)))
    @Test fun requestDeclining() = request("declining", incoming.copy(decline = MeetCommandState(inProgress = true)))
    @Test fun requestCancelling() = request("cancelling", sent.copy(cancel = MeetCommandState(inProgress = true)))
    @Test fun requestRestricted() = request("restricted", incoming.copy(driving = driving(DrivingPolicy.RESTRICTED)))
    @Test fun requestUnknown() = request("unknown", incoming.copy(driving = driving(DrivingPolicy.UNKNOWN)))
    @Test @Config(sdk = [35], qualifiers = "w840dp-h1210dp-port-mdpi")
    fun requestIncomingNarrow() = request("incoming_narrow", incoming)
    @Test @Config(sdk = [35], qualifiers = "w840dp-h1210dp-port-mdpi")
    fun requestSentNarrow() = request("sent_narrow", sent)

    @Test fun connectedPopulated() = connected("populated")
    @Test fun connectedLoading() = connected("loading", connected.copy(connection = null, loading = true, greetings = emptyList(), greetingsLoading = true))
    @Test fun connectedEmpty() = connected("empty", connected.copy(connection = null, greetings = emptyList()))
    @Test fun connectedFailure() = connected("failure", connected.copy(connection = null, observationError = AppError.Network, greetings = emptyList()))
    @Test fun connectedChecking() = connected("checking", connected.copy(connection = MeetFixtures.connection.copy(status = ConnectionStatus.CHECKING)))
    @Test fun connectedSending() = connected("sending", connected.copy(greetings = listOf(greeting.copy(delivery = DeliveryState.SENDING)), sendingPresets = setOf(PresetCode.HELLO)))
    @Test fun connectedGreetingFailed() = connected("greeting_failed", connected.copy(greetings = listOf(greeting.copy(delivery = DeliveryState.FAILED, error = AppError.Network))))
    @Test fun connectedGreetingsLoading() = connected("greetings_loading", connected.copy(greetings = emptyList(), greetingsLoading = true))
    @Test fun connectedGreetingsEmpty() = connected("greetings_empty", connected.copy(greetings = emptyList()))
    @Test fun connectedGreetingsFailure() = connected("greetings_failure", connected.copy(greetings = emptyList(), greetingsError = AppError.Network))
    @Test fun connectedLeaveFailure() = connected("leave_failure", connected.copy(leave = MeetCommandState(error = AppError.Network)))
    @Test fun connectedLeaving() = connected("leaving", connected.copy(leave = MeetCommandState(inProgress = true)))
    @Test fun connectedRestricted() = connected("restricted", connected.copy(driving = driving(DrivingPolicy.RESTRICTED)))
    @Test fun connectedUnknown() = connected("unknown", connected.copy(driving = driving(DrivingPolicy.UNKNOWN)))
    @Test fun connectedEndedWhileDriving() = connected("ended_while_driving", connected.copy(
        driving = driving(DrivingPolicy.RESTRICTED), connection = MeetFixtures.ended))
    @Test @Config(sdk = [35], qualifiers = "w840dp-h1210dp-port-mdpi")
    fun connectedNarrow() = connected("narrow")
    @Test @Config(sdk = [35], qualifiers = "w840dp-h1210dp-port-mdpi")
    fun connectedDrivingNarrow() = connected("driving_narrow", connected.copy(driving = driving(DrivingPolicy.RESTRICTED)))

    @Test fun endedDistance() = ended("distance")
    @Test fun endedPeerLeft() = ended("peer_left", ended.copy(connection = MeetFixtures.ended.copy(endReason = ConnectionEndReason.PEER_LEFT)))
    @Test fun endedSelfLeft() = ended("self_left", ended.copy(connection = MeetFixtures.ended.copy(endReason = ConnectionEndReason.SELF_LEFT)))
    @Test fun endedStatusUnknown() = ended("status_unknown", ended.copy(connection = MeetFixtures.ended.copy(endReason = ConnectionEndReason.STATUS_UNKNOWN)))
    @Test fun endedDiscoveryOff() = ended("discovery_off", ended.copy(connection = MeetFixtures.ended.copy(endReason = ConnectionEndReason.DISCOVERY_OFF)))
    @Test fun endedBlocked() = ended("blocked", ended.copy(connection = MeetFixtures.ended.copy(endReason = ConnectionEndReason.BLOCKED)))
    @Test fun endedLoading() = ended("loading", ended.copy(connection = null, loading = true))
    @Test fun endedEmpty() = ended("empty", ended.copy(connection = null))
    @Test fun endedFailure() = ended("failure", ended.copy(connection = null, observationError = AppError.Network))
    @Test fun endedNoRecord() = ended("no_record", ended.copy(connection = MeetFixtures.ended.copy(encounterId = null)))
    @Test fun endedRestricted() = ended("restricted", ended.copy(driving = driving(DrivingPolicy.RESTRICTED)))
    @Test fun endedUnknown() = ended("unknown", ended.copy(driving = driving(DrivingPolicy.UNKNOWN)))
    @Test @Config(sdk = [35], qualifiers = "w840dp-h1210dp-port-mdpi")
    fun endedNarrow() = ended("narrow")

    @Test fun drivingRestricted() = capture("driving_restricted", DrivingPolicy.RESTRICTED) { DrivingScreen(driving(DrivingPolicy.RESTRICTED), it) }
    @Test fun drivingUnknown() = capture("driving_unknown", DrivingPolicy.UNKNOWN) { DrivingScreen(driving(DrivingPolicy.UNKNOWN), it) }
    @Test fun drivingNewConnection() = capture("driving_new_connection", DrivingPolicy.RESTRICTED) {
        DrivingScreen(driving(DrivingPolicy.RESTRICTED), it, hasConnection = true)
    }
    @Test fun unknownNewConnection() = capture("unknown_new_connection", DrivingPolicy.UNKNOWN) {
        DrivingScreen(driving(DrivingPolicy.UNKNOWN), it, hasConnection = true)
    }
    @Test fun drivingNewConnectionLeaving() = capture("driving_new_connection_leaving", DrivingPolicy.RESTRICTED) {
        DrivingScreen(driving(DrivingPolicy.RESTRICTED), it, hasConnection = true, leave = MeetCommandState(inProgress = true))
    }
    @Test fun drivingNewConnectionLeaveFailed() = capture("driving_new_connection_leave_failed", DrivingPolicy.RESTRICTED) {
        DrivingScreen(driving(DrivingPolicy.RESTRICTED), it, hasConnection = true, leave = MeetCommandState(error = AppError.Network))
    }
    @Test @Config(sdk = [35], qualifiers = "w840dp-h1210dp-port-mdpi")
    fun drivingNarrow() = capture("driving_narrow", DrivingPolicy.RESTRICTED) { DrivingScreen(driving(DrivingPolicy.RESTRICTED), it) }
}
