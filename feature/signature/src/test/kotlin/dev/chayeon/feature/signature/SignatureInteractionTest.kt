package dev.chayeon.feature.signature

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import dev.chayeon.core.model.*
import dev.chayeon.core.ui.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w1682dp-h1210dp-land-mdpi")
class SignatureInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val models = ViewModelStore()
    private val f = SignatureScreenFixtures

    @After fun clearModels() { compose.runOnIdle { models.clear() } }

    @Test fun unavailableTraitsShowOffWithoutLosingTheirStoredPreference() {
        val signature = mutableStateOf(f.signature.copy(visibility = VisibilitySettings(publishSignature = true)))
        compose.setContent { ChayeonTheme(DrivingPolicy.ALLOWED, 0f) {
            SignatureVisibilityScreen(VisibilityUiState(driving = f.driving, signature = f.ready(signature.value)),
                previewChrome(), {}, { _, _ -> error("Unavailable trait must be disabled") }, {}, {})
        } }
        compose.onNodeWithTag("visibility-SIGNAL_MANNERS").performScrollTo().assertIsOff().assertIsNotEnabled()
        compose.onNodeWithTag("visibility-BATTERY_KIND").performScrollTo().assertIsOff().assertIsNotEnabled()
        compose.onNodeWithText("이번 합성 주행에는 없는 특성이에요").assertExists()
        compose.runOnIdle {
            assertTrue(TraitId.BATTERY_KIND in signature.value.visibility.visibleTraits)
            signature.value = signature.value.copy(evidence = signature.value.evidence.map {
                if (it.trait == TraitId.BATTERY_KIND) it.copy(availability = TraitAvailability.AVAILABLE,
                    level = TraitLevel.VISIBLE, validTripCount = 3) else it
            })
        }
        compose.onNodeWithTag("visibility-BATTERY_KIND").assertIsOn().assertIsEnabled()
    }

    @Test fun firstSyntheticMomentKeepsCareAmongAtMostThreeTraitsAndHidesExclusion() {
        val recap = f.recap.copy(firstSignature = true, changes = listOf(TraitId.GENTLE_RIDE,
            TraitId.EARLY_BRAKE, TraitId.SIGNAL_MANNERS, TraitId.ENERGY_PLANNER).map {
            TraitChange(it, TraitLevel.RECORDING, if (it == TraitId.ENERGY_PLANNER) TraitLevel.STEADY else TraitLevel.SIGNATURE, "")
        })
        compose.setContent { ChayeonTheme(DrivingPolicy.ALLOWED, 0f) {
            TripRecapScreen(TripRecapUiState(driving = f.driving, recap = f.ready(recap)), previewChrome(), {}, {}, {}, {})
        } }
        compose.onNodeWithText("이 주행이 매듭이 됐어요").assertIsDisplayed()
        compose.onNodeWithText("미리 채우는 습관").assertIsDisplayed()
        compose.onNodeWithText("깜빡이 매너").assertDoesNotExist()
        compose.onNodeWithTag("exclude-trip").assertDoesNotExist()
        compose.onRoot().captureRoboImage("src/test/screenshots/first_moment_care.png")
    }

    @Test fun traitToggleChangesTheActualPublishedProjection() {
        val deps = SignatureTestDeps()
        val model = SignatureVisibilityViewModel(deps).also { models.put("visibility", it) }
        compose.setContent {
            val state by model.uiState.collectAsState()
            ChayeonTheme(state.driving.policy, 0f) {
                SignatureVisibilityScreen(state, previewChrome(policy = state.driving.policy), {},
                    model::setTraitVisible, model::setPublished, model::retry)
            }
        }
        compose.waitUntil { model.uiState.value.visibility != null }
        compose.onNodeWithTag("visibility-GENTLE_RIDE").performClick()
        compose.waitUntil { deps.profileRepository.publishCalls.isNotEmpty() && !model.uiState.value.publishing }
        val published = deps.profileRepository.publishCalls.last().signature
        assertTrue(samePublicContent(model.uiState.value.preview, published))
        assertFalse(published!!.traits.any { it.trait == TraitId.GENTLE_RIDE })
        compose.onAllNodesWithContentDescription("부드러운 주행, 꾸준해요").assertCountEquals(0)
        compose.onNodeWithText("10분당 0.8회").assertDoesNotExist()
    }

    @Test fun restoredVisibilityExposesAnActionToReconcilePendingPublication() {
        val deps = SignatureTestDeps()
        deps.signatureRepository.signature.value = f.ready(f.signature.copy(visibility = f.visibility.copy(publishSignature = false)))
        val model = SignatureVisibilityViewModel(deps).also { models.put("visibility", it) }
        compose.setContent {
            val state by model.uiState.collectAsState()
            ChayeonTheme(state.driving.policy, 0f) {
                SignatureVisibilityScreen(state, previewChrome(policy = state.driving.policy), {},
                    model::setTraitVisible, model::setPublished, model::retry)
            }
        }
        compose.waitUntil { model.uiState.value.pendingChanges && model.uiState.value.publicationKnown }
        compose.onNodeWithText("공개 내용 반영").performClick()
        compose.waitForIdle()
        assertTrue("publish calls=${deps.profileRepository.publishCalls.size}, state=${model.uiState.value}", deps.profileRepository.publishCalls.isNotEmpty())
        assertFalse("state=${model.uiState.value}", model.uiState.value.pendingChanges)
        assertNull(deps.profileRepository.publishCalls.single().signature)
    }

    @Test fun publishFailureOffersARealRetryWithTheSameCommand() {
        val deps = SignatureTestDeps()
        deps.profileRepository.publishError = AppError.Network
        val model = SignatureVisibilityViewModel(deps).also { models.put("visibility", it) }
        compose.setContent {
            val state by model.uiState.collectAsState()
            ChayeonTheme(state.driving.policy, 0f) {
                SignatureVisibilityScreen(state, previewChrome(policy = state.driving.policy), {},
                    model::setTraitVisible, model::setPublished, model::retry)
            }
        }
        compose.waitUntil { model.uiState.value.visibility != null }
        compose.onNodeWithTag("publish-toggle").performClick()
        compose.waitUntil { model.uiState.value.error != null }
        compose.onNodeWithText("다시 시도").performScrollTo().assertIsDisplayed()
        val failed = deps.profileRepository.publishCalls.single()
        compose.runOnIdle { deps.profileRepository.publishError = null }
        compose.onNodeWithText("다시 시도").performClick()
        compose.waitUntil { deps.profileRepository.publishCalls.size == 2 && model.uiState.value.error == null }
        assertEquals(failed, deps.profileRepository.publishCalls.last())
        assertNull(deps.profileRepository.publishCalls.last().signature)
        compose.onNodeWithText("나만 간직해요").assertIsDisplayed()
    }

    @Test fun recapExclusionRemovesTheTripAndRetractsItsPublicTraits() {
        val deps = SignatureTestDeps()
        deps.signatureRepository.recap.value = f.ready(f.recap.copy(source = DataSource.VEHICLE))
        val model = TripRecapViewModel(deps, f.recap.id).also { models.put("recap", it) }
        compose.setContent {
            val state by model.uiState.collectAsState()
            ChayeonTheme(state.driving.policy, 0f) {
                TripRecapScreen(state, previewChrome(policy = state.driving.policy), {}, model::excludeTrip, model::markSeen, model::retry)
            }
        }
        compose.waitUntil { model.uiState.value.recap is LoadState.Ready }
        compose.onNodeWithTag("exclude-trip").performClick()
        compose.waitUntil { model.uiState.value.recap.readyValue()?.included == false && !model.uiState.value.excluding }
        compose.onNodeWithText("이 주행은 빼 두었어요").assertIsDisplayed()
        compose.onNodeWithTag("exclude-trip").assertDoesNotExist()
        assertEquals(1, deps.signatureRepository.exclusionCalls.size)
        assertNull(deps.profileRepository.publishCalls.last().signature)
    }

    @Test fun recapFailureKeepsTheSameTripForRetry() {
        val deps = SignatureTestDeps()
        deps.signatureRepository.recap.value = f.ready(f.recap.copy(source = DataSource.VEHICLE))
        deps.signatureRepository.exclusionError = AppError.Network
        val model = TripRecapViewModel(deps, f.recap.id).also { models.put("recap", it) }
        compose.setContent {
            val state by model.uiState.collectAsState()
            ChayeonTheme(state.driving.policy, 0f) {
                TripRecapScreen(state, previewChrome(policy = state.driving.policy), {}, model::excludeTrip, model::markSeen, model::retry)
            }
        }
        compose.waitUntil { model.uiState.value.recap is LoadState.Ready }
        compose.onNodeWithTag("exclude-trip").performClick()
        compose.waitUntil { model.uiState.value.error != null }
        compose.runOnIdle { deps.signatureRepository.exclusionError = null }
        compose.onNodeWithText("다시 시도").performScrollTo().performClick()
        compose.waitUntil { model.uiState.value.recap.readyValue()?.included == false }
        assertEquals(deps.signatureRepository.exclusionCalls.first(), deps.signatureRepository.exclusionCalls.last())
    }

    @Test fun drivingChangeRemovesPrivateEvidenceAndBrowsing() {
        val deps = SignatureTestDeps()
        val model = TraitDetailViewModel(deps, f.evidence.trait).also { models.put("trait", it) }
        compose.setContent {
            val state by model.uiState.collectAsState()
            ChayeonTheme(state.driving.policy, 0f) {
                TraitDetailScreen(state, previewChrome(policy = state.driving.policy), {}, model::retry)
            }
        }
        compose.waitUntil { model.uiState.value.evidence is LoadState.Ready }
        compose.onNodeWithText("10분당 0.8회").assertIsDisplayed()
        compose.runOnIdle { deps.drivingStateProvider.drivingState.value = f.driving.copy(policy = DrivingPolicy.RESTRICTED) }
        compose.onNodeWithText("주행 중이에요").assertIsDisplayed()
        compose.onNodeWithText("10분당 0.8회").assertDoesNotExist()
        compose.onNodeWithContentDescription("뒤로").assertDoesNotExist()
    }

    @Test fun recapConfirmationWaitsForTheSeenCommand() {
        val deps = SignatureTestDeps()
        val model = TripRecapViewModel(deps, f.recap.id).also { models.put("recap", it) }
        compose.setContent {
            val state by model.uiState.collectAsState()
            ChayeonTheme(state.driving.policy, 0f) {
                TripRecapScreen(state, previewChrome(policy = state.driving.policy), {}, model::excludeTrip, model::markSeen, model::retry)
            }
        }
        compose.waitUntil { model.uiState.value.recap is LoadState.Ready }
        compose.onNodeWithTag("recap-open-signature").performClick()
        compose.waitUntil { model.uiState.value.openSignature }
        assertEquals(f.recap.id, deps.signatureRepository.seenCalls.single().recapId)
    }

    @Test fun signaturePrioritizesEarnedTraitsAndKeepsVehicleTouchSize() {
        compose.setContent {
            ChayeonTheme(DrivingPolicy.ALLOWED, 0f) {
                MySignatureScreen(SignatureUiState(f.driving, f.ready(f.signature)), previewChrome(), {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("함께 타면 알게 되는 나").assertIsDisplayed()
        compose.onNodeWithText("아직 모으는 이야기").assertIsDisplayed()
        compose.onNodeWithText("공개 범위").assertIsDisplayed()
        compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().forEach { node ->
            assertTrue("Touch width is below 76dp", node.size.width >= 76)
            assertTrue("Touch height is below 76dp", node.size.height >= 76)
        }
    }

    @Test fun recordingAndUnsupportedTraitsExpandWithoutTakingOverEarnedCards() {
        compose.setContent {
            ChayeonTheme(DrivingPolicy.ALLOWED, 0f) {
                MySignatureScreen(SignatureUiState(f.driving, f.ready(f.signature)), previewChrome(), {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("배터리 아끼기").assertDoesNotExist()
        compose.onNodeWithText("아직 모으는 이야기").performClick()
        compose.onNodeWithText("배터리 아끼기").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("signature-screen").captureRoboImage("src/test/screenshots/signature_recording_expanded.png")
    }

    @Test fun profileMainActionAndFailureRecoveryAreReachable() {
        var opens = 0
        var retries = 0
        val state = mutableStateOf(MyProfileUiState(f.driving, f.ready(f.profile), f.ready(f.signature)))
        compose.setContent {
            ChayeonTheme(DrivingPolicy.ALLOWED, 0f) {
                MyProfileScreen(state.value, previewChrome(), { opens++ }, {}, {}, { retries++ })
            }
        }
        compose.onNodeWithText("내 시그니처 보기").performClick()
        assertEquals(1, opens)
        compose.runOnIdle { state.value = state.value.copy(profile = LoadState.Failed(AppError.Network)) }
        compose.onNodeWithText("다시 시도").performClick()
        assertEquals(1, retries)
    }

    @Test fun signatureTraitActionAndFailureRecoveryAreReachable() {
        var selected: TraitId? = null
        var retries = 0
        val state = mutableStateOf(SignatureUiState(f.driving, f.ready(f.signature)))
        compose.setContent {
            ChayeonTheme(DrivingPolicy.ALLOWED, 0f) {
                MySignatureScreen(state.value, previewChrome(), {}, {}, { selected = it }, {}, { retries++ })
            }
        }
        compose.onNodeWithText("부드러운 주행").performClick()
        assertEquals(TraitId.GENTLE_RIDE, selected)
        compose.runOnIdle { state.value = state.value.copy(signature = LoadState.Failed(AppError.Network)) }
        compose.onNodeWithText("다시 시도").performClick()
        assertEquals(1, retries)
    }

    @Test fun evidenceFailureRecoveryAndBackAreReachable() {
        var retries = 0
        var backs = 0
        compose.setContent {
            ChayeonTheme(DrivingPolicy.ALLOWED, 0f) {
                TraitDetailScreen(TraitDetailUiState(f.driving, f.evidence.trait, LoadState.Failed(AppError.Network)),
                    previewChrome(), { backs++ }, { retries++ })
            }
        }
        compose.onNodeWithText("다시 시도").performClick()
        compose.onNodeWithContentDescription("뒤로").performClick()
        assertEquals(1, retries)
        assertEquals(1, backs)
    }

    @Test
    @Config(sdk = [35], qualifiers = "w840dp-h1210dp-port-mdpi")
    fun narrowVisibilityScrollsToNightPrivacyExplanation() {
        compose.setContent {
            ChayeonTheme(DrivingPolicy.ALLOWED, 0f) {
                SignatureVisibilityScreen(VisibilityUiState(f.driving, f.ready(f.signature), f.visibility, f.public),
                    previewChrome(), {}, { _, _ -> }, {}, {})
            }
        }
        compose.onNodeWithTag("visibility-NIGHT_DRIVE").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("생활 시간이 드러날 수 있어 처음에는 꺼 두었어요. 원할 때만 공개해요.")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("public-preview").performScrollTo().assertIsDisplayed()
    }
}
