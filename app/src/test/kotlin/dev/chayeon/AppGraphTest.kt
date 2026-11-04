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
    private class FakeNotifier : MessageNotifier {
        val active = mutableSetOf<String>()
        val cancelled = mutableListOf<String>()
        override fun notifyMessage(id: String) { active += id }
        override fun cancelMessage(id: String) { active -= id; cancelled += id }
    }

    @Test fun viewingAfterConversationCancelsOnlyItsNotificationsEvenAfterBannerTimeout() = runTest {
        val fixture = TestGraph().apply { ready(); driving.value = DrivingState(policy = DrivingPolicy.RESTRICTED) }
        val notifier = FakeNotifier()
        val events = AppEvents(fixture.graph, notifier, backgroundScope)
        events.start()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { events.banner.collect {} }
        runCurrent()
        fixture.requests.value = LoadState.Ready(listOf(ConnectionRequest("request", 2, fixture.publicProfile,
            RequestDirection.INCOMING, ConnectionRequestStatus.PENDING, 0, 100_000, Chemistry())), 2)
        fixture.afterConnections.value = LoadState.Ready(listOf("after", "other").map {
            AfterConnection(it, 2, "after-request-$it", "encounter-$it", fixture.publicProfile,
                AfterConnectionStatus.CONNECTED, 0, Chemistry())
        }, 2)
        fixture.afterMessages.value = LoadState.Ready(listOf("after", "other").map {
            AfterMessage("message-$it", 2, it, "client-$it", MessageAuthor.PEER,
                AfterMessageContent.Text("반가워요"), 0, DeliveryState.SENT)
        }, 2)
        runCurrent()
        assertEquals(setOf("request", "message-after", "message-other"), notifier.active)
        fixture.driving.value = DrivingState(policy = DrivingPolicy.ALLOWED, gear = Gear.PARK)
        runCurrent()
        advanceTimeBy(15_001); runCurrent()
        assertNull(events.banner.value)
        val release = events.viewing(null, "after")
        assertEquals(listOf("message-after"), notifier.cancelled)
        assertEquals(setOf("request", "message-other"), notifier.active)
        fixture.afterMessages.value = LoadState.Ready((fixture.afterMessages.value as LoadState.Ready).value +
            AfterMessage("reply", 3, "after", "reply", MessageAuthor.PEER,
                AfterMessageContent.Text("저도 반가워요"), 1, DeliveryState.SENT), 3)
        runCurrent()
        assertEquals(setOf("request", "message-other"), notifier.active)
        release()
        events.viewing("request", null)
        assertEquals(setOf("message-other"), notifier.active)
        events.viewing(null, "other")
        assertTrue(notifier.active.isEmpty())
    }

    @Test fun restrictedSignalIsCancelledWhenItsConversationBecomesReadableAndIsAlreadyViewed() = runTest {
        val fixture = TestGraph().apply { ready(); driving.value = DrivingState() }
        val signals = MutableStateFlow<List<String>>(emptyList())
        val notifier = FakeNotifier()
        val events = AppEvents(fixture.graph, notifier, backgroundScope, signals)
        events.start()
        runCurrent()
        signals.value = listOf("greeting")
        runCurrent()
        assertEquals(setOf("greeting"), notifier.active)
        fixture.driving.value = DrivingState(policy = DrivingPolicy.ALLOWED, gear = Gear.PARK)
        events.viewing(null, "connection")
        fixture.connection.value = LoadState.Ready(Connection("connection", 2, "request", fixture.publicProfile,
            ConnectionStatus.CONNECTED, 0, Chemistry()), 2)
        fixture.greetings.value = LoadState.Ready(listOf(ConnectionGreeting("greeting", 2, "connection", "client",
            MessageAuthor.PEER, PresetCode.HELLO, 0, DeliveryState.SENT)), 2)
        runCurrent()
        assertEquals(listOf("greeting"), notifier.cancelled)
        assertTrue(notifier.active.isEmpty())
        fixture.driving.value = DrivingState(policy = DrivingPolicy.RESTRICTED)
        signals.value += "unread"
        runCurrent()
        assertEquals(setOf("unread"), notifier.active)
        fixture.sessions.value = SessionState.SignedOut()
        runCurrent()
        assertTrue(notifier.active.isEmpty())
    }

    @Test fun restoredNotificationIsCancelledFromHistoricalMessageIdsAfterRestart() = runTest {
        for (viewBeforeRestore in listOf(false, true)) {
            val fixture = TestGraph().apply { ready() }
            val notifier = FakeNotifier().apply { active += "historical" }
            val events = AppEvents(fixture.graph, notifier, backgroundScope, MutableStateFlow(listOf("historical")))
            events.start()
            runCurrent()
            if (viewBeforeRestore) events.viewing(null, "connection")
            fixture.connection.value = LoadState.Ready(Connection("connection", 2, "request", fixture.publicProfile,
                ConnectionStatus.CONNECTED, 0, Chemistry()), 2)
            fixture.greetings.value = LoadState.Ready(listOf(ConnectionGreeting("historical", 2, "connection", "client",
                MessageAuthor.PEER, PresetCode.HELLO, 0, DeliveryState.SENT)), 2)
            runCurrent()
            if (!viewBeforeRestore) {
                assertEquals(setOf("historical"), notifier.active)
                events.viewing(null, "connection")
            }
            assertEquals(listOf("historical"), notifier.cancelled)
            assertTrue(notifier.active.isEmpty())
            fixture.greetings.value = LoadState.Ready((fixture.greetings.value as LoadState.Ready).value, 3)
            runCurrent()
            assertEquals(listOf("historical"), notifier.cancelled)
        }
    }

    @Test fun openingTheRelevantContentClearsOnlyItsBannerAndSuppressesNewArrivals() = runTest {
        val fixture = TestGraph().apply { ready() }
        val events = AppEvents(fixture.graph, FakeNotifier(), backgroundScope)
        events.start()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { events.banner.collect {} }
        runCurrent()
        fixture.requests.value = LoadState.Ready(listOf(ConnectionRequest("request", 2, fixture.publicProfile,
            RequestDirection.INCOMING, ConnectionRequestStatus.PENDING, 0, 100_000, Chemistry())), 2)
        runCurrent()
        val unrelated = events.viewing("other-request", null)
        assertEquals("request", events.banner.value?.id)
        val requestView = events.viewing("request", null)
        runCurrent()
        assertNull(events.banner.value)
        requestView(); unrelated()
        fixture.connection.value = LoadState.Ready(Connection("connection", 3, "request", fixture.publicProfile,
            ConnectionStatus.CONNECTED, 0, Chemistry()), 3)
        runCurrent()
        fixture.greetings.value = LoadState.Ready(listOf(ConnectionGreeting("greeting", 4, "connection", "message",
            MessageAuthor.PEER, PresetCode.HELLO, 0, DeliveryState.SENT)), 4)
        runCurrent()
        assertEquals("greeting", events.banner.value?.id)
        val conversation = events.viewing(null, "connection")
        runCurrent()
        assertNull(events.banner.value)
        fixture.greetings.value = LoadState.Ready((fixture.greetings.value as LoadState.Ready).value +
            ConnectionGreeting("reply", 5, "connection", "reply-message", MessageAuthor.PEER, PresetCode.NICE_TO_MEET_YOU, 0, DeliveryState.SENT), 5)
        runCurrent()
        assertNull(events.banner.value)
        conversation()
        runCurrent()
        assertNull(events.banner.value)
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
        val events = AppEvents(fixture.graph, object : MessageNotifier {
            override fun cancelMessage(id: String) = Unit
            override fun notifyMessage(id: String) { notifications += id } }, backgroundScope)
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
            val events = AppEvents(fixture.graph, object : MessageNotifier {
                override fun cancelMessage(id: String) = Unit
                override fun notifyMessage(id: String) { notifications += id }
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
        val events = AppEvents(fixture.graph, object : MessageNotifier {
            override fun cancelMessage(id: String) = Unit
            override fun notifyMessage(id: String) { notifications += id }
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

    @Test fun endedRelationshipRemovesItsGreetingBanner() = runTest {
        val fixture = TestGraph().apply { ready() }
        val events = AppEvents(fixture.graph, FakeNotifier(), backgroundScope)
        events.start()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { events.banner.collect {} }
        val connection = Connection("connection", 1, "request", fixture.publicProfile, ConnectionStatus.CONNECTED, 0, Chemistry())
        fixture.connection.value = LoadState.Ready(connection, 1)
        fixture.greetings.value = LoadState.Ready(listOf(ConnectionGreeting("greeting", 1, connection.id, "client-message", MessageAuthor.PEER, PresetCode.HELLO, 1, DeliveryState.SENT)), 1)
        runCurrent()
        assertEquals("greeting", events.banner.value?.id)
        fixture.connection.value = LoadState.Loading()
        runCurrent()
        assertEquals("greeting", events.banner.value?.id)
        fixture.connection.value = LoadState.Ready(connection.copy(status = ConnectionStatus.ENDED), 2)
        runCurrent()
        assertNull(events.banner.value)
    }
}
