package dev.chayeon

import androidx.compose.ui.test.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import dev.chayeon.core.model.*
import dev.chayeon.core.ui.*
import org.junit.Assert.assertEquals
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import dev.chayeon.data.vehicle.EmptyVehicleConsole
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1682dp-h1210dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationSmokeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun creationScreenOnlyShowsChoicesAndProgressWithCareFirst() {
        compose.setContent { ChayeonTheme(DrivingPolicy.ALLOWED, 0f) {
            SignatureCreationScreen(DemoUiState(DrivingState(policy = DrivingPolicy.ALLOWED, gear = Gear.PARK)), {}, {})
        } }
        val care = compose.onNodeWithTag("fill-CARE").assertIsDisplayed().getUnclippedBoundsInRoot()
        val gentle = compose.onNodeWithTag("fill-GENTLE").assertIsDisplayed().getUnclippedBoundsInRoot()
        org.junit.Assert.assertTrue(care.bottom < gentle.top)
        compose.onNodeWithTag("fill-SIGNAL_MANNERS").assertIsDisplayed()
        compose.onNodeWithText("합성 주행").assertIsDisplayed()
        compose.onNodeWithText("기어 제어").assertDoesNotExist()
        compose.onNodeWithText("합성 위치").assertDoesNotExist()
        compose.onNodeWithText("시연 제어").assertDoesNotExist()
        compose.onRoot().captureRoboImage("src/test/screenshots/signature-creation.png")
    }

    @Config(sdk = [35], qualifiers = "w840dp-h1210dp-port-mdpi")
    @Test fun narrowCreationAndLockedStates() {
        var policy by androidx.compose.runtime.mutableStateOf(DrivingPolicy.ALLOWED)
        compose.setContent { ChayeonTheme(policy, 0f) { SignatureCreationScreen(DemoUiState(DrivingState(policy = policy)), {}, {}) } }
        compose.onNodeWithTag("fill-CARE").assertIsDisplayed()
        compose.onRoot().captureRoboImage("src/test/screenshots/signature-creation-narrow.png")
        for (blocked in listOf(DrivingPolicy.RESTRICTED, DrivingPolicy.UNKNOWN)) {
            compose.runOnIdle { policy = blocked }
            compose.onNodeWithTag("fill-CARE").assertDoesNotExist()
            compose.onRoot().captureRoboImage("src/test/screenshots/signature-creation-${blocked.name.lowercase()}.png")
        }
    }

    @Test fun chromeTracksLiveGearAndDiscoveryCommand() {
        val fixture = TestGraph().apply { ready() }
        compose.setContent { ChayeonApp(fixture.graph) }
        compose.onNodeWithText("주차 중").assertIsDisplayed()
        compose.onNodeWithContentDescription("발견 켜기").performClick()
        compose.runOnIdle { assertEquals(1, fixture.discoveryCalls) }
        compose.onNodeWithContentDescription("발견 끄기").assertIsDisplayed()
        compose.onRoot().captureRoboImage("src/test/screenshots/app-ready.png")
        compose.runOnIdle { fixture.driving.value = DrivingState(policy = DrivingPolicy.RESTRICTED, gear = Gear.DRIVE) }
        compose.onNodeWithText("주행 중", useUnmergedTree = true).assertExists()
        compose.onRoot().captureRoboImage("src/test/screenshots/app-restricted.png")
    }

    @Test fun demoConsoleClearlyLabelsSyntheticDataAndDisablesRealGear() {
        val chrome = ScreenChrome({}, {})
        compose.setContent {
            ChayeonTheme(policy = DrivingPolicy.ALLOWED) {
                DemoConsoleScreen(DemoUiState(DrivingState(policy = DrivingPolicy.ALLOWED, gear = Gear.PARK)), chrome,
                    realVehicle = true, enabled = true, {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("시연 제어").assertIsDisplayed()
        compose.onNodeWithText("시그니처 만들기").assertIsDisplayed()
        compose.onNodeWithText("기어 제어").assertDoesNotExist()
        compose.onRoot().captureRoboImage("src/test/screenshots/demo-console.png")
    }
    @Test fun unseenRecapWaitsUntilPark() {
        val fixture = TestGraph().apply {
            ready()
            driving.value = DrivingState(policy = DrivingPolicy.RESTRICTED, gear = Gear.DRIVE)
            recaps.value = LoadState.Ready(listOf(TripRecap("recap", 1, "trip", 1, DataSource.VEHICLE,
                KnotSeed(1), KnotSeed(2), emptyList())), 1)
        }
        compose.setContent { ChayeonApp(fixture.graph) }
        compose.onNodeWithText("주행 후 요약").assertDoesNotExist()
        compose.runOnIdle { fixture.driving.value = DrivingState(policy = DrivingPolicy.ALLOWED, gear = Gear.PARK) }
        compose.onNodeWithText("주행 후 요약").assertExists()
        compose.onRoot().captureRoboImage("src/test/screenshots/app-recap.png")
    }

    @Test fun demoLockedAndUnavailableStates() {
        var policy by androidx.compose.runtime.mutableStateOf(DrivingPolicy.UNKNOWN)
        compose.setContent {
            ChayeonTheme(policy = policy) {
                DemoConsoleScreen(DemoUiState(DrivingState(policy = policy)), ScreenChrome({}, {}), true, false, {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("편안하게 달리는 사람").assertDoesNotExist()
        compose.onRoot().captureRoboImage("src/test/screenshots/demo-unknown.png")
        compose.runOnIdle { policy = DrivingPolicy.RESTRICTED }
        compose.onRoot().captureRoboImage("src/test/screenshots/demo-restricted.png")
        compose.runOnIdle { policy = DrivingPolicy.ALLOWED }
        compose.onNodeWithText("이 앱에서는 시연 제어를 제공하지 않아요").assertIsDisplayed()
        compose.onRoot().captureRoboImage("src/test/screenshots/demo-unavailable.png")
    }

    @Test fun consoleCompletionOpensFirstMomentAndVisibilityActionMarksItSeen() {
        lateinit var fixture: TestGraph
        var count = 0
        val consoleState = MutableStateFlow(ConsoleState(available = true))
        val console = object : VehicleConsole by EmptyVehicleConsole() {
            override val consoleState = consoleState
            override suspend fun replay(request: ReplayDriveRequest): AppResult<ConsoleState> {
                count++
                val recap = TripRecap("recap-$count", count.toLong(), "trip-$count", count.toLong(), DataSource.SYNTHETIC,
                    KnotSeed(1), KnotSeed(42), listOf(TraitChange(TraitId.GENTLE_RIDE, TraitLevel.RECORDING, TraitLevel.SIGNATURE, "함께 타면 편안한 운전이에요")), firstSignature = count == 3)
                fixture.recaps.value = LoadState.Ready(listOf(recap) + (fixture.recaps.value as LoadState.Ready).value, count.toLong())
                consoleState.value = ConsoleState(available = true, status = ReplayStatus.FINISHED)
                return AppResult.Success(consoleState.value)
            }
        }
        fixture = TestGraph(console).apply { ready() }
        compose.setContent { ChayeonApp(fixture.graph) }
        compose.onNodeWithText("내 프로필").performClick()
        compose.onNodeWithText("합성 주행으로 먼저 만나보기").performClick()
        compose.onNodeWithText("편안하게 달리는 사람").performClick()
        compose.waitUntil { count == 3 }
        compose.onNodeWithText("이 주행이 매듭이 됐어요").assertIsDisplayed()
        compose.onRoot().captureRoboImage("src/test/screenshots/app-first-signature.png")
        compose.runOnIdle { fixture.driving.value = DrivingState(policy = DrivingPolicy.RESTRICTED, gear = Gear.DRIVE) }
        compose.onNodeWithContentDescription("주차하면 첫 시그니처를 이어서 볼 수 있어요.").assertIsNotEnabled()
        compose.onNodeWithTag("recap-open-visibility").assertDoesNotExist()
        compose.onRoot().captureRoboImage("src/test/screenshots/app-first-signature-restricted.png")
        compose.runOnIdle { fixture.driving.value = DrivingState(policy = DrivingPolicy.ALLOWED, gear = Gear.PARK) }
        compose.onNodeWithText("이 주행이 매듭이 됐어요").assertIsDisplayed()
        compose.onNodeWithTag("recap-open-visibility").assertIsEnabled()
        compose.onRoot().captureRoboImage("src/test/screenshots/app-first-signature-resumed.png")
        compose.onNodeWithTag("recap-open-visibility").performClick()
        compose.onNodeWithText("공개 범위").assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf("recap-1", "recap-2", "recap-3"), fixture.seenCalls.map { it.recapId }) }
    }

    @Test fun arrivalBannerOverlaysChromeWithoutMovingProfileActions() {
        assertArrivalBanner("새 연결 요청이 왔어요 · 확인하기", "app-arrival-overlay")
    }

    @Config(sdk = [35], qualifiers = "w840dp-h1210dp-port-mdpi")
    @Test fun narrowArrivalBannerKeepsProfileAndGearInPlace() {
        assertArrivalBanner("새 소식 확인하기", "app-arrival-overlay-narrow")
    }

    private fun assertArrivalBanner(label: String, screenshot: String) {
        val fixture = TestGraph().apply { ready() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val events = AppEvents(fixture.graph, object : MessageNotifier { override fun notifyMessage(id: String) = Unit }, scope)
        try {
            events.start()
            compose.setContent { ChayeonApp(fixture.graph, events = events) }
            compose.onNodeWithText("내 프로필").performClick()
            val action = compose.onNodeWithText("프로필 편집")
            val before = action.getUnclippedBoundsInRoot()
            compose.runOnIdle {
                fixture.requests.value = LoadState.Ready(listOf(ConnectionRequest("arrived", 2, fixture.publicProfile,
                    RequestDirection.INCOMING, ConnectionRequestStatus.PENDING, 0, 100_000, Chemistry())), 2)
            }
            compose.onNodeWithText(label).assertIsDisplayed()
            compose.onNodeWithText("주차 중").assertIsDisplayed()
            assertEquals(before, action.getUnclippedBoundsInRoot())
            compose.onRoot().captureRoboImage("src/test/screenshots/$screenshot.png")
        } finally { scope.cancel() }
    }

    @Test fun consoleProgressAndNarrowControlsStayReachable() {
        compose.setContent {
            ChayeonTheme(DrivingPolicy.ALLOWED, 0f) {
                SignatureCreationScreen(DemoUiState(DrivingState(policy = DrivingPolicy.ALLOWED, gear = Gear.PARK),
                    ConsoleState(available = true), busy = true, fillProgress = 2), {}, {})
            }
        }
        compose.onNodeWithText("합성 주행 2/3").assertIsDisplayed()
        compose.onNodeWithTag("fill-GENTLE").assertIsNotEnabled()
        compose.onRoot().captureRoboImage("src/test/screenshots/demo-progress.png")
    }

    @Config(sdk = [35], qualifiers = "w840dp-h1210dp-port-mdpi")
    @Test fun narrowConsoleIncludesPresenceAndBack() {
        compose.setContent {
            ChayeonTheme(DrivingPolicy.ALLOWED, 0f) {
                DemoConsoleScreen(DemoUiState(DrivingState(policy = DrivingPolicy.ALLOWED, gear = Gear.PARK)),
                    previewChrome(), false, true, {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithContentDescription("뒤로").assertIsDisplayed()
        compose.onRoot().captureRoboImage("src/test/screenshots/demo-narrow.png")
        compose.onNodeWithText("연결이 끊어져요").performScrollTo().assertIsDisplayed()
    }

}
