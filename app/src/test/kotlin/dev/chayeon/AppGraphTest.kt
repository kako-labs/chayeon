package dev.chayeon

import dev.chayeon.core.model.*
import dev.chayeon.feature.history.HistoryDestination
import dev.chayeon.feature.meet.MeetDestination
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppGraphTest {
    private class FakeNotifier : RequestNotifier {
        val active = mutableSetOf<String>()
        val cancelled = mutableListOf<String>()
        override fun notifyRequest(id: String, route: String) { active += id }
        override fun cancelRequest(id: String) { active -= id; cancelled += id }
    }

    @Test fun bannerDismissesAfterFiveVisibleSecondsAndDoesNotReappear() = runTest {
        val fixture = TestGraph().apply { ready() }
        val events = AppEvents(fixture.graph, FakeNotifier(), backgroundScope)
        events.start()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { events.banner.collect {} }
        runCurrent()
        val request = ConnectionRequest("arrival", 2, fixture.publicProfile,
            RequestDirection.INCOMING, ConnectionRequestStatus.PENDING, 0, 100_000, Chemistry())
        fixture.requests.value = LoadState.Ready(listOf(request), 2)
        runCurrent()
        advanceTimeBy(4_999); runCurrent()
        assertEquals("arrival", events.banner.value?.id)
        advanceTimeBy(1); runCurrent()
        assertNull(events.banner.value)
        fixture.requests.value = LoadState.Ready(listOf(request.copy(version = 3)), 3)
        runCurrent()
        assertNull(events.banner.value)
    }

    @Test fun routingFollowsRestoredRegistration() {
        val fixture = TestGraph()
        assertEquals(HistoryDestination.FIRST_RUN, rootRoute(SessionState.SignedOut(), fixture.profiles.value))
        assertNull(rootRoute(SessionState.Entering(), fixture.profiles.value))
        assertEquals(MeetDestination.HOME, rootRoute(SessionState.Active(fixture.active), LoadState.Loading()))
        val routes = mapOf(RegistrationState.PROFILE_REQUIRED to HistoryDestination.PROFILE_SETUP,
            RegistrationState.CONSENT_REQUIRED to HistoryDestination.CONSENT, RegistrationState.READY to MeetDestination.HOME)
        routes.forEach { (registration, route) ->
            assertEquals(route, rootRoute(SessionState.Active(fixture.active), LoadState.Ready(MyProfile("profile", 1, fixture.publicProfile, registration), 1)))
        }
    }

    @Test fun publisherRetriesOneCommandAndStopsBeforeSignOut() = runTest {
        val fixture = TestGraph().apply { ready() }
        val remote = PublicSignature("published", 7, "시그니처를 만드는 중이에요", emptyList(), KnotSeed(0), DataSource.SYNTHETIC, EvidenceBucket.RECORDING)
        fixture.profiles.value = LoadState.Ready(MyProfile("profile", 7, fixture.publicProfile.copy(signature = remote), RegistrationState.READY), 7)
        val ending = MutableStateFlow(false)
        SignaturePublisher(fixture.graph, ending, Mutex(), backgroundScope).start()
        runCurrent()
        assertEquals(1, fixture.publications.size)
        assertNull(fixture.publications[0].signature)
        advanceTimeBy(2_001)
        runCurrent()
        assertEquals(2, fixture.publications.size)
        assertEquals(fixture.publications[0], fixture.publications[1])
        ending.value = true
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(2, fixture.publications.size)
    }

    @Test fun incomingRequestIsQueuedWhileLockedAndNotifiedWithoutPayload() = runTest {
        val fixture = TestGraph().apply { ready(); driving.value = DrivingState() }
        val notifications = mutableListOf<String>()
        val events = AppEvents(fixture.graph, object : RequestNotifier {
            override fun cancelRequest(id: String) = Unit
            override fun notifyRequest(id: String, route: String) { notifications += id } }, backgroundScope)
        events.start()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { events.banner.collect {} }
        runCurrent()
        fixture.requests.value = LoadState.Ready(listOf(ConnectionRequest("request", 2, fixture.publicProfile,
            RequestDirection.INCOMING, ConnectionRequestStatus.PENDING, 0, 100_000, Chemistry())), 2)
        runCurrent()
        assertEquals(listOf("request"), notifications)
        assertEquals(MeetDestination.REQUESTS, events.banner.value?.route)
        fixture.driving.value = DrivingState(policy = DrivingPolicy.ALLOWED, gear = Gear.PARK)
        runCurrent()
        assertEquals(1, notifications.size)
        events.acknowledge("request")
        runCurrent()
        assertNull(events.banner.value)
    }

    @Test fun signalBaselineWaitsForTheFirstReadySnapshotIncludingAnEmptyHistory() = runTest {
        for (history in listOf(emptyList(), (1..30).map { "history-$it" })) {
            val fixture = TestGraph().apply { ready(); driving.value = DrivingState(policy = DrivingPolicy.RESTRICTED) }
            val signals = MutableStateFlow<List<String>>(emptyList())
            val ready = MutableStateFlow(false)
            val notifications = mutableListOf<String>()
            val events = AppEvents(fixture.graph, object : RequestNotifier {
                override fun cancelRequest(id: String) = Unit
                override fun notifyRequest(id: String, route: String) { notifications += id }
            }, backgroundScope, signals, ready)
            events.start()
            runCurrent()
            signals.value = history
            ready.value = true
            runCurrent()
            assertTrue(notifications.isEmpty())
            signals.value = history + "new-arrival"
            runCurrent()
            assertEquals(listOf("new-arrival"), notifications)
        }
    }

    @Test fun nonEmptyInitialSignalsAreHistoryAndNeverNotifyAcrossSessions() = runTest {
        val fixture = TestGraph().apply { ready(); driving.value = DrivingState(policy = DrivingPolicy.RESTRICTED) }
        val signals = MutableStateFlow((1..30).map { "old-$it" })
        val notifications = mutableListOf<String>()
        val events = AppEvents(fixture.graph, object : RequestNotifier {
            override fun cancelRequest(id: String) = Unit
            override fun notifyRequest(id: String, route: String) { notifications += id }
        }, backgroundScope, signals)
        events.start()
        runCurrent()
        assertTrue(notifications.isEmpty())
        signals.value += "new"
        runCurrent()
        assertEquals(listOf("new"), notifications)
        signals.value = signals.value.toList()
        runCurrent()
        assertEquals(1, notifications.size)
        fixture.sessions.value = SessionState.SignedOut()
        runCurrent()
        fixture.sessions.value = SessionState.Active(fixture.active.copy(id = "next-session"))
        runCurrent()
        assertEquals(1, notifications.size)
        signals.value += "next-new"
        runCurrent()
        assertEquals(listOf("new", "next-new"), notifications)
    }

    @Test fun publisherWaitsForLocalAccountBindingBeforeRetracting() = runTest {
        val fixture = TestGraph().apply { ready() }
        val remote = PublicSignature("published", 1, "시그니처를 만드는 중이에요", emptyList(), KnotSeed(0), DataSource.SYNTHETIC, EvidenceBucket.RECORDING)
        fixture.profiles.value = LoadState.Ready(MyProfile("profile", 1, fixture.publicProfile.copy(signature = remote), RegistrationState.READY), 1)
        val localAccount = MutableStateFlow<String?>(null)
        SignaturePublisher(fixture.graph, MutableStateFlow(false), Mutex(), backgroundScope, localAccount).start()
        runCurrent()
        assertTrue(fixture.publications.isEmpty())
        localAccount.value = fixture.active.accountId
        runCurrent()
        assertEquals(1, fixture.publications.size)
    }


    @Test fun incomingAfterRoutesToPersonAndViewingClearsOnlyThatRequest() = runTest {
        val fixture = TestGraph().apply { ready(); driving.value = DrivingState(policy = DrivingPolicy.RESTRICTED) }
        val notifier = FakeNotifier()
        val events = AppEvents(fixture.graph, notifier, backgroundScope)
        events.start()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { events.banner.collect {} }
        runCurrent()
        val request = AfterRequest("incoming-after", 2, "encounter", fixture.publicProfile,
            RequestDirection.INCOMING, AfterRequestStatus.PENDING, 0, 100_000, Chemistry())
        fixture.afterRequests.value = LoadState.Ready(listOf(request), 2)
        runCurrent()
        assertEquals(HistoryDestination.person(fixture.publicProfile.id), events.banner.value?.route)
        assertEquals(setOf("incoming-after"), notifier.active)
        fixture.driving.value = DrivingState(policy = DrivingPolicy.ALLOWED)
        events.viewing(request.id)
        runCurrent()
        assertNull(events.banner.value)
        assertTrue(notifier.active.isEmpty())
        fixture.afterRequests.value = LoadState.Ready(listOf(request.copy(version = 3)), 3)
        runCurrent()
        assertNull(events.banner.value)
    }

    @Test fun contactExchangeDoesNotCreateAnEventOrNotification() = runTest {
        val fixture = TestGraph().apply { ready(); driving.value = DrivingState(policy = DrivingPolicy.RESTRICTED) }
        val notifier = FakeNotifier()
        val events = AppEvents(fixture.graph, notifier, backgroundScope)
        events.start()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { events.banner.collect {} }
        runCurrent()
        fixture.afterConnections.value = LoadState.Ready(listOf(AfterConnection("after", 2, "request", "encounter",
            fixture.publicProfile, AfterConnectionStatus.CONNECTED, 0, Chemistry(),
            sharedContacts = ContactIds(instagramId = "byeol-night.drive"))), 2)
        runCurrent()
        assertNull(events.banner.value)
        assertTrue(notifier.active.isEmpty())
    }
}
