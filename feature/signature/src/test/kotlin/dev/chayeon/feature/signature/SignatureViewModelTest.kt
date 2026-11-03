package dev.chayeon.feature.signature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import dev.chayeon.core.model.*
import dev.chayeon.core.signature.DefaultSignatureEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SignatureViewModelTest {
    private val models = ViewModelStore()

    private fun scenario(block: suspend TestScope.() -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try { block() } finally {
            models.clear()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    private fun <T : ViewModel> keep(model: T): T = model.also { models.put(model.toString(), it) }

    private fun TestScope.observe(state: StateFlow<*>) =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.collect {} }

    @Test
    fun `resubscription starts unknown and cannot replay allowed after stop timeout`() = scenario {
        val deps = SignatureTestDeps()
        val model = keep(SignatureViewModel(deps))
        val first = observe(model.uiState)
        runCurrent()
        assertEquals(DrivingPolicy.ALLOWED, model.uiState.value.driving.policy)
        first.cancel()
        advanceTimeBy(5_001)
        runCurrent()
        assertEquals(DrivingPolicy.UNKNOWN, model.uiState.value.driving.policy)
        deps.drivingStateProvider.drivingState.value = DrivingState(policy = DrivingPolicy.RESTRICTED)
        observe(model.uiState)
        assertEquals(DrivingPolicy.UNKNOWN, model.uiState.value.driving.policy)
        runCurrent()
        assertEquals(DrivingPolicy.RESTRICTED, model.uiState.value.driving.policy)
        assertTrue(model.uiState.value.signature is LoadState.Loading)
    }

    @Test
    fun `all screen observations hide private content for restricted and unknown`() = scenario {
        val deps = SignatureTestDeps()
        val profile = keep(MyProfileViewModel(deps))
        val detail = keep(TraitDetailViewModel(deps, TraitId.GENTLE_RIDE))
        val visibility = keep(SignatureVisibilityViewModel(deps))
        val recap = keep(TripRecapViewModel(deps, "recap"))
        listOf(profile.uiState, detail.uiState, visibility.uiState, recap.uiState).forEach { observe(it) }
        runCurrent()
        assertTrue(detail.uiState.value.evidence is LoadState.Ready)
        for (policy in listOf(DrivingPolicy.RESTRICTED, DrivingPolicy.UNKNOWN)) {
            deps.drivingStateProvider.drivingState.value = DrivingState(policy = policy)
            runCurrent()
            assertTrue(profile.uiState.value.profile is LoadState.Loading)
            assertTrue(profile.uiState.value.signature is LoadState.Loading)
            assertTrue(detail.uiState.value.evidence is LoadState.Loading)
            assertNull(visibility.uiState.value.preview)
            assertNull(visibility.uiState.value.visibility)
            assertTrue(recap.uiState.value.recap is LoadState.Loading)
        }
    }

    @Test
    fun `policy changes between rendering and tapping reject publication and edits`() = scenario {
        val deps = SignatureTestDeps()
        val model = keep(SignatureVisibilityViewModel(deps))
        observe(model.uiState)
        runCurrent()
        assertEquals(DrivingPolicy.ALLOWED, model.uiState.value.driving.policy)
        deps.drivingStateProvider.drivingState.value = DrivingState(policy = DrivingPolicy.RESTRICTED)
        model.setTraitVisible(TraitId.GENTLE_RIDE, false)
        runCurrent()
        assertTrue(deps.signatureRepository.visibilityCalls.isEmpty())
        assertTrue(deps.profileRepository.publishCalls.isEmpty())
        assertEquals(AppError.Restricted, model.uiState.value.error)
        deps.drivingStateProvider.drivingState.value = DrivingState(policy = DrivingPolicy.UNKNOWN)
        model.setPublished(false)
        runCurrent()
        assertTrue(deps.profileRepository.publishCalls.isEmpty())
    }

    @Test
    fun `preview updates before local save and is exactly the publication payload`() = scenario {
        val deps = SignatureTestDeps()
        val gate = CompletableDeferred<Unit>()
        deps.signatureRepository.beforeVisibility = { gate.await() }
        val model = keep(SignatureVisibilityViewModel(deps))
        observe(model.uiState)
        runCurrent()
        model.setTraitVisible(TraitId.GENTLE_RIDE, false)
        runCurrent()
        val preview = model.uiState.value.preview
        assertNotNull(preview)
        assertFalse(preview!!.traits.any { it.trait == TraitId.GENTLE_RIDE })
        assertTrue(model.uiState.value.savingVisibility)
        assertTrue(deps.profileRepository.publishCalls.isEmpty())
        gate.complete(Unit)
        runCurrent()
        assertEquals(preview, deps.profileRepository.publishCalls.single().signature)
        assertFalse(model.uiState.value.savingVisibility)
        assertFalse(model.uiState.value.publishing)
    }

    @Test
    fun `first publication uses the signature revision after trips and recap acknowledgement`() = scenario {
        val deps = SignatureTestDeps()
        val signature = SignatureScreenFixtures.signature.copy(version = 9, publicSignature = null,
            visibility = SignatureScreenFixtures.visibility.copy(version = 0, publishSignature = false))
        deps.signatureRepository.signature.value = LoadState.Ready(signature, signature.version)
        val profile = SignatureScreenFixtures.profile
        deps.profileRepository.profile.value = LoadState.Ready(profile.copy(
            publicProfile = profile.publicProfile.copy(signature = null)), profile.version)
        deps.signatureRepository.beforeVisibility = { request ->
            val current = (deps.signatureRepository.signature.value as LoadState.Ready).value
            deps.signatureRepository.visibilityError = if (request.meta.expectedVersion == current.version) null else AppError.AlreadyClosed
        }
        val model = keep(SignatureVisibilityViewModel(deps))
        observe(model.uiState)
        runCurrent()
        model.setPublished(true)
        runCurrent()
        assertNull(model.uiState.value.error)
        assertTrue(model.uiState.value.published)
        assertFalse(model.uiState.value.pendingChanges)
        assertEquals(9L, deps.signatureRepository.visibilityCalls.single().meta.expectedVersion)
        assertNotNull(deps.profileRepository.publishCalls.single().signature)
    }

    @Test
    fun `failed visibility save retries the same command and duplicate taps do not dispatch`() = scenario {
        val deps = SignatureTestDeps()
        deps.signatureRepository.visibilityError = AppError.Unknown
        val model = keep(SignatureVisibilityViewModel(deps))
        observe(model.uiState)
        runCurrent()
        model.setTraitVisible(TraitId.TIRE_CARE, false)
        model.setTraitVisible(TraitId.TIRE_CARE, false)
        runCurrent()
        assertEquals(1, deps.signatureRepository.visibilityCalls.size)
        assertEquals(AppError.Unknown, model.uiState.value.error)
        deps.signatureRepository.visibilityError = null
        model.retry()
        model.retry()
        runCurrent()
        assertEquals(2, deps.signatureRepository.visibilityCalls.size)
        assertEquals(deps.signatureRepository.visibilityCalls[0], deps.signatureRepository.visibilityCalls[1])
        assertEquals(1, deps.profileRepository.publishCalls.size)
    }

    @Test
    fun `failed publication retries identical payload without repeating local save`() = scenario {
        val deps = SignatureTestDeps()
        deps.profileRepository.publishError = AppError.Network
        val model = keep(SignatureVisibilityViewModel(deps))
        observe(model.uiState)
        runCurrent()
        model.setPublished(false)
        runCurrent()
        assertNull(model.uiState.value.preview)
        assertTrue(model.uiState.value.published)
        assertTrue(model.uiState.value.pendingChanges)
        assertEquals(AppError.Network, model.uiState.value.error)
        deps.profileRepository.publishError = null
        model.retry()
        runCurrent()
        assertEquals(1, deps.signatureRepository.visibilityCalls.size)
        assertEquals(2, deps.profileRepository.publishCalls.size)
        assertEquals(deps.profileRepository.publishCalls[0], deps.profileRepository.publishCalls[1])
        assertNull(deps.profileRepository.publishCalls.last().signature)
        assertFalse(model.uiState.value.published)
    }

    @Test
    fun `newer visibility invalidates an old failed publication retry`() = scenario {
        val deps = SignatureTestDeps()
        deps.profileRepository.publishError = AppError.Network
        val model = keep(SignatureVisibilityViewModel(deps))
        observe(model.uiState)
        runCurrent()
        model.setTraitVisible(TraitId.TIRE_CARE, false)
        runCurrent()
        val current = (deps.signatureRepository.signature.value as LoadState.Ready).value
        deps.signatureRepository.signature.value = LoadState.Ready(current.copy(
            version = current.version + 1,
            visibility = current.visibility.copy(version = current.visibility.version + 1, publishSignature = false)), current.version + 1)
        runCurrent()
        assertNull(model.uiState.value.preview)
        deps.profileRepository.publishError = null
        model.retry()
        runCurrent()
        assertEquals(2, deps.profileRepository.publishCalls.size)
        assertNull(deps.profileRepository.publishCalls.last().signature)
        assertNotEquals(deps.profileRepository.publishCalls.first().meta, deps.profileRepository.publishCalls.last().meta)
        assertNull(model.uiState.value.error)
    }

    @Test
    fun `visibility revoked while publishing gets a corrective retraction`() = scenario {
        val deps = SignatureTestDeps()
        val gate = CompletableDeferred<Unit>()
        deps.profileRepository.beforePublish = { if (deps.profileRepository.publishCalls.size == 1) gate.await() }
        val model = keep(SignatureVisibilityViewModel(deps))
        observe(model.uiState)
        runCurrent()
        model.setTraitVisible(TraitId.TIRE_CARE, false)
        runCurrent()
        val current = (deps.signatureRepository.signature.value as LoadState.Ready).value
        deps.signatureRepository.signature.value = LoadState.Ready(current.copy(
            version = current.version + 1,
            visibility = current.visibility.copy(version = current.visibility.version + 1, publishSignature = false)), current.version + 1)
        gate.complete(Unit)
        runCurrent()
        assertEquals(2, deps.profileRepository.publishCalls.size)
        assertNull(deps.profileRepository.publishCalls.last().signature)
        assertNotEquals(deps.profileRepository.publishCalls.first().meta, deps.profileRepository.publishCalls.last().meta)
        assertNull(model.uiState.value.preview)
    }

    @Test
    fun `policy is checked again between local save and remote publication`() = scenario {
        val deps = SignatureTestDeps()
        deps.signatureRepository.beforeVisibility = {
            deps.drivingStateProvider.drivingState.value = DrivingState(policy = DrivingPolicy.RESTRICTED)
        }
        val model = keep(SignatureVisibilityViewModel(deps))
        observe(model.uiState)
        runCurrent()
        model.setPublished(false)
        runCurrent()
        assertEquals(1, deps.signatureRepository.visibilityCalls.size)
        assertTrue(deps.profileRepository.publishCalls.isEmpty())
        deps.drivingStateProvider.drivingState.value = SignatureScreenFixtures.driving
        model.retry()
        runCurrent()
        assertEquals(1, deps.signatureRepository.visibilityCalls.size)
        assertEquals(1, deps.profileRepository.publishCalls.size)
    }

    @Test
    fun `unknown policy refuses exclusion and marking recap seen`() = scenario {
        val deps = SignatureTestDeps()
        val model = keep(TripRecapViewModel(deps, "recap"))
        observe(model.uiState)
        runCurrent()
        deps.drivingStateProvider.drivingState.value = DrivingState()
        model.excludeTrip()
        model.markSeen()
        runCurrent()
        assertTrue(deps.signatureRepository.exclusionCalls.isEmpty())
        assertTrue(deps.signatureRepository.seenCalls.isEmpty())
        assertFalse(model.uiState.value.openSignature)
    }

    @Test
    fun `exclusion recomputes and retracts and publication retry does not exclude twice`() = scenario {
        val deps = SignatureTestDeps()
        deps.profileRepository.publishError = AppError.Network
        val model = keep(TripRecapViewModel(deps, "recap"))
        observe(model.uiState)
        runCurrent()
        model.excludeTrip()
        model.excludeTrip()
        runCurrent()
        assertFalse((model.uiState.value.recap as LoadState.Ready).value!!.included)
        assertEquals(1, deps.signatureRepository.exclusionCalls.size)
        assertNull(deps.profileRepository.publishCalls.single().signature)
        deps.profileRepository.publishError = null
        model.retry()
        runCurrent()
        assertEquals(1, deps.signatureRepository.exclusionCalls.size)
        assertEquals(deps.profileRepository.publishCalls.first(), deps.profileRepository.publishCalls.last())
        model.excludeTrip()
        runCurrent()
        assertEquals(1, deps.signatureRepository.exclusionCalls.size)
    }

    @Test
    fun `failed exclusion retries its command identity`() = scenario {
        val deps = SignatureTestDeps()
        deps.signatureRepository.exclusionError = AppError.Unknown
        val model = keep(TripRecapViewModel(deps, "recap"))
        observe(model.uiState)
        runCurrent()
        model.excludeTrip()
        runCurrent()
        deps.signatureRepository.exclusionError = null
        model.retry()
        runCurrent()
        assertEquals(2, deps.signatureRepository.exclusionCalls.size)
        assertEquals(deps.signatureRepository.exclusionCalls[0], deps.signatureRepository.exclusionCalls[1])
    }

    @Test
    fun `mark seen retries identity and navigates once after success`() = scenario {
        val deps = SignatureTestDeps()
        deps.signatureRepository.seenError = AppError.Network
        val model = keep(TripRecapViewModel(deps, "recap"))
        observe(model.uiState)
        runCurrent()
        model.markSeen()
        model.markSeen()
        runCurrent()
        assertEquals(1, deps.signatureRepository.seenCalls.size)
        assertFalse(model.uiState.value.openSignature)
        deps.signatureRepository.seenError = null
        model.retry()
        runCurrent()
        assertEquals(deps.signatureRepository.seenCalls[0], deps.signatureRepository.seenCalls[1])
        assertTrue(model.uiState.value.openSignature)
        model.consumeNavigation()
        runCurrent()
        assertFalse(model.uiState.value.openSignature)
    }

    @Test
    fun `first signature visibility action preserves its destination through a failed receipt retry`() = scenario {
        val deps = SignatureTestDeps()
        deps.signatureRepository.seenError = AppError.Network
        val model = keep(TripRecapViewModel(deps, "recap"))
        observe(model.uiState)
        runCurrent()
        model.markSeen(openVisibility = true)
        runCurrent()
        assertFalse(model.uiState.value.openVisibility)
        deps.signatureRepository.seenError = null
        model.retry()
        runCurrent()
        assertTrue(model.uiState.value.openVisibility)
        assertFalse(model.uiState.value.openSignature)
        model.consumeNavigation()
        runCurrent()
        assertFalse(model.uiState.value.openVisibility)
    }

    @Test
    fun `failed observations never keep earlier evidence`() = scenario {
        val deps = SignatureTestDeps()
        val model = keep(TraitDetailViewModel(deps, TraitId.GENTLE_RIDE))
        observe(model.uiState)
        runCurrent()
        assertTrue(model.uiState.value.evidence is LoadState.Ready)
        deps.signatureRepository.evidence.value = LoadState.Failed(AppError.Network)
        runCurrent()
        assertEquals(LoadState.Failed(AppError.Network), model.uiState.value.evidence)
        deps.signatureRepository.evidence.value = LoadState.Ready(null, 6)
        model.retry()
        runCurrent()
        assertNull((model.uiState.value.evidence as LoadState.Ready).value)
    }

    @Test
    fun `projection excludes hidden recording and unsupported traits and preserves valid engine projection`() {
        val original = SignatureScreenFixtures.signature
        val signature = original.copy(publicSignature = DefaultSignatureEngine().publicProjection(original))
        assertEquals(signature.publicSignature, signaturePreview(signature, signature.visibility))
        val preview = signaturePreview(signature, signature.visibility.copy(visibleTraits = TraitId.entries.toSet()))
        assertNotNull(preview)
        assertFalse(preview!!.traits.any { it.trait == TraitId.NIGHT_DRIVE || it.trait == TraitId.BATTERY_KIND })
        assertTrue(preview.traits.none { it.level == TraitLevel.RECORDING })
        assertEquals(DataSource.SYNTHETIC, preview.source)
        assertNull(signaturePreview(signature, signature.visibility.copy(publishSignature = false)))
    }

    @Test
    fun `synthetic evidence cannot keep a stale vehicle source label`() {
        val original = SignatureScreenFixtures.signature
        val stale = original.copy(publicSignature = original.publicSignature!!.copy(source = DataSource.VEHICLE))
        assertEquals(DataSource.SYNTHETIC, signaturePreview(stale, stale.visibility)!!.source)
    }

    @Test
    fun `new evidence arriving during publication is republished under a fresh command`() = scenario {
        val deps = SignatureTestDeps()
        val gate = CompletableDeferred<Unit>()
        deps.profileRepository.beforePublish = { if (deps.profileRepository.publishCalls.size == 1) gate.await() }
        val model = keep(SignatureVisibilityViewModel(deps))
        observe(model.uiState)
        runCurrent()
        model.setTraitVisible(TraitId.TIRE_CARE, false)
        runCurrent()
        val current = (deps.signatureRepository.signature.value as LoadState.Ready).value
        deps.signatureRepository.signature.value = LoadState.Ready(current.copy(version = current.version + 1,
            evidence = current.evidence.map { if (it.trait == TraitId.GENTLE_RIDE) it.copy(level = TraitLevel.SIGNATURE) else it }), current.version + 1)
        gate.complete(Unit)
        runCurrent()
        assertEquals(2, deps.profileRepository.publishCalls.size)
        assertNotEquals(deps.profileRepository.publishCalls.first().meta, deps.profileRepository.publishCalls.last().meta)
        assertEquals(TraitLevel.SIGNATURE, deps.profileRepository.publishCalls.last().signature!!.traits.first { it.trait == TraitId.GENTLE_RIDE }.level)
        assertTrue(samePublicContent(model.uiState.value.preview, deps.profileRepository.publishCalls.last().signature))
    }

    @Test
    fun `failed signature observation prevents stale publication after exclusion`() = scenario {
        val deps = SignatureTestDeps()
        deps.profileRepository.beforePublish = {
            deps.signatureRepository.signature.value = LoadState.Failed(AppError.Network)
        }
        val model = keep(TripRecapViewModel(deps, "recap"))
        observe(model.uiState)
        runCurrent()
        model.excludeTrip()
        runCurrent()
        assertEquals(AppError.Network, model.uiState.value.error)
        model.retry()
        runCurrent()
        assertEquals(1, deps.profileRepository.publishCalls.size)
        assertEquals(1, deps.signatureRepository.exclusionCalls.size)
    }

    @Test
    fun `recreated visibility screen reconciles saved local retraction with old remote publication`() = scenario {
        val deps = SignatureTestDeps()
        deps.profileRepository.publishError = AppError.Network
        val first = keep(SignatureVisibilityViewModel(deps))
        observe(first.uiState)
        runCurrent()
        first.setPublished(false)
        runCurrent()
        assertEquals(AppError.Network, first.uiState.value.error)
        deps.profileRepository.publishError = null
        val recreated = keep(SignatureVisibilityViewModel(deps))
        observe(recreated.uiState)
        runCurrent()
        assertTrue(recreated.uiState.value.pendingChanges)
        recreated.retry()
        runCurrent()
        assertNull(deps.profileRepository.publishCalls.last().signature)
        assertEquals(1, deps.signatureRepository.visibilityCalls.size)
        assertEquals(2, deps.profileRepository.publishCalls.size)
        assertFalse(recreated.uiState.value.pendingChanges)
        assertFalse(recreated.uiState.value.published)
    }

    @Test
    fun `failed remote observation cannot be presented as confirmed publication`() = scenario {
        val deps = SignatureTestDeps()
        val model = keep(SignatureVisibilityViewModel(deps))
        observe(model.uiState)
        runCurrent()
        assertTrue(model.uiState.value.publicationKnown)
        deps.profileRepository.profile.value = LoadState.Failed(AppError.Network)
        runCurrent()
        assertFalse(model.uiState.value.publicationKnown)
        assertEquals(AppError.Network, model.uiState.value.error)
        model.retry()
        runCurrent()
        assertTrue(deps.profileRepository.publishCalls.isEmpty())
    }

    @Test
    fun `throwing observation fails safely at command entry`() = scenario {
        val base = SignatureTestDeps()
        val deps = object : SignatureDeps by base {
            override val signatureRepository = object : SignatureRepository by base.signatureRepository {
                override val signature = flow<LoadState<MySignature>> { throw IllegalStateException("unavailable") }
            }
        }
        val model = keep(SignatureVisibilityViewModel(deps))
        observe(model.uiState)
        runCurrent()
        model.setPublished(false)
        runCurrent()
        assertEquals(AppError.Unknown, model.uiState.value.error)
        assertTrue(base.profileRepository.publishCalls.isEmpty())
        assertTrue(base.signatureRepository.visibilityCalls.isEmpty())
    }

    @Test
    fun `loading signature prevents retry from publishing an old excluded projection`() = scenario {
        val deps = SignatureTestDeps()
        deps.profileRepository.publishError = AppError.Network
        val model = keep(TripRecapViewModel(deps, "recap"))
        observe(model.uiState)
        runCurrent()
        model.excludeTrip()
        runCurrent()
        deps.signatureRepository.signature.value = LoadState.Loading()
        deps.profileRepository.publishError = null
        model.retry()
        runCurrent()
        assertEquals(1, deps.profileRepository.publishCalls.size)
        assertEquals(AppError.Unknown, model.uiState.value.error)
    }

    @Test
    fun `same tick exclusion and mark seen cannot navigate before retraction completes`() = scenario {
        val deps = SignatureTestDeps()
        val publication = CompletableDeferred<Unit>()
        deps.profileRepository.beforePublish = { publication.await() }
        val model = keep(TripRecapViewModel(deps, "recap"))
        observe(model.uiState)
        runCurrent()
        model.excludeTrip()
        model.markSeen()
        runCurrent()
        assertEquals(1, deps.signatureRepository.exclusionCalls.size)
        assertTrue(deps.signatureRepository.seenCalls.isEmpty())
        assertTrue(model.uiState.value.excluding)
        assertTrue(model.uiState.value.pendingChanges)
        assertFalse(model.uiState.value.openSignature)
        model.markSeen()
        runCurrent()
        assertTrue(deps.signatureRepository.seenCalls.isEmpty())
        publication.complete(Unit)
        runCurrent()
        assertFalse(model.uiState.value.pendingChanges)
        assertNull((deps.profileRepository.profile.value as LoadState.Ready).value!!.publicProfile.signature)
        model.markSeen()
        runCurrent()
        assertTrue(model.uiState.value.openSignature)
    }

    @Test
    fun `failed exclusion publication blocks leaving until exclusion retry succeeds`() = scenario {
        val deps = SignatureTestDeps()
        deps.profileRepository.publishError = AppError.Network
        val model = keep(TripRecapViewModel(deps, "recap"))
        observe(model.uiState)
        runCurrent()
        model.excludeTrip()
        runCurrent()
        assertTrue(model.uiState.value.pendingChanges)
        assertFalse(model.uiState.value.excluding)
        model.markSeen()
        runCurrent()
        assertTrue(deps.signatureRepository.seenCalls.isEmpty())
        assertFalse(model.uiState.value.openSignature)
        deps.profileRepository.publishError = null
        model.retry()
        runCurrent()
        assertFalse(model.uiState.value.pendingChanges)
        model.markSeen()
        runCurrent()
        assertTrue(model.uiState.value.openSignature)
    }

    @Test
    fun `same tick mark seen and exclusion cannot start conflicting commands`() = scenario {
        val deps = SignatureTestDeps()
        val seen = CompletableDeferred<Unit>()
        deps.signatureRepository.beforeSeen = { seen.await() }
        val model = keep(TripRecapViewModel(deps, "recap"))
        observe(model.uiState)
        runCurrent()
        model.markSeen()
        model.excludeTrip()
        runCurrent()
        assertEquals(1, deps.signatureRepository.seenCalls.size)
        assertTrue(deps.signatureRepository.exclusionCalls.isEmpty())
        assertFalse(model.uiState.value.openSignature)
        seen.complete(Unit)
        runCurrent()
        assertTrue(model.uiState.value.openSignature)
        model.excludeTrip()
        runCurrent()
        assertTrue(deps.signatureRepository.exclusionCalls.isEmpty())
    }

    @Test
    fun `mark seen rechecks policy after a suspending recap observation`() = scenario {
        val base = SignatureTestDeps()
        var changePolicyOnRead = false
        val deps = object : SignatureDeps by base {
            override val signatureRepository = object : SignatureRepository by base.signatureRepository {
                override fun observeRecap(recapId: String) = flow {
                    if (changePolicyOnRead) {
                        base.drivingStateProvider.drivingState.value = DrivingState(policy = DrivingPolicy.RESTRICTED)
                    }
                    emit(base.signatureRepository.recap.value)
                }
            }
        }
        val model = keep(TripRecapViewModel(deps, "recap"))
        observe(model.uiState)
        runCurrent()
        assertEquals(DrivingPolicy.ALLOWED, model.uiState.value.driving.policy)
        changePolicyOnRead = true
        model.markSeen()
        runCurrent()
        assertTrue(base.signatureRepository.seenCalls.isEmpty())
        assertFalse(model.uiState.value.openSignature)
        assertEquals(AppError.Restricted, model.uiState.value.error)
        changePolicyOnRead = false
        base.drivingStateProvider.drivingState.value = SignatureScreenFixtures.driving
        model.retry()
        runCurrent()
        assertEquals(1, base.signatureRepository.seenCalls.size)
        assertTrue(model.uiState.value.openSignature)
    }

    @Test
    fun `visibility retry restarts a completed failed observation even when a direct read is loading`() = scenario {
        val base = SignatureTestDeps()
        var subscriptions = 0
        val deps = object : SignatureDeps by base {
            override val signatureRepository = object : SignatureRepository by base.signatureRepository {
                override val signature = flow<LoadState<MySignature>> {
                    subscriptions += 1
                    if (subscriptions == 1) {
                        emit(LoadState.Failed(AppError.Network))
                    } else {
                        emit(LoadState.Loading())
                        emit(SignatureScreenFixtures.ready(SignatureScreenFixtures.signature))
                    }
                }
            }
        }
        val model = keep(SignatureVisibilityViewModel(deps))
        observe(model.uiState)
        runCurrent()
        assertTrue(model.uiState.value.signature is LoadState.Failed)
        model.retry()
        runCurrent()
        assertTrue(subscriptions >= 3)
        assertTrue(model.uiState.value.signature is LoadState.Ready)
        assertNull(model.uiState.value.error)
        assertTrue(base.signatureRepository.visibilityCalls.isEmpty())
        assertTrue(base.profileRepository.publishCalls.isEmpty())
    }
}
