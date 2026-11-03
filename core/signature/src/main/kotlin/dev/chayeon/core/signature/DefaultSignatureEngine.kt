package dev.chayeon.core.signature

import dev.chayeon.core.model.*
import kotlin.math.roundToLong

class DefaultSignatureEngine : SignatureEngine {
    override fun aggregate(
        trips: List<DerivedTripMetrics>,
        window: EvidenceWindow,
        previousEvidence: List<TraitEvidence>,
    ): List<TraitEvidence> {
        require(window.fromEpochMillis <= window.untilEpochMillis)
        val effectiveWindow = EvidenceWindow(maxOf(window.fromEpochMillis, window.untilEpochMillis - SignaturePolicy.WINDOW_MILLIS), window.untilEpochMillis)
        val included = eligibleTrips(trips, effectiveWindow)
        val source = source(included)
        return TraitId.entries.map { trait ->
            val old = previousEvidence.firstOrNull { it.trait == trait }
            val observed = included.mapNotNull { trip -> trip.traits.firstOrNull { it.trait == trait } }
            val supported = observed.filter { it.availability != TraitAvailability.UNSUPPORTED }
            val available = supported.filter { it.availability == TraitAvailability.AVAILABLE && it.supportedSampleCount > 0 }
            val count = available.sumOf { it.supportedSampleCount.toLong().coerceAtLeast(0) }
            val passing = available.sumOf { it.passingSampleCount.toLong().coerceIn(0, it.supportedSampleCount.toLong().coerceAtLeast(0)) }
            val availability = when {
                supported.isEmpty() -> TraitAvailability.UNSUPPORTED
                included.size < SignaturePolicy.MIN_VALID_TRIPS || available.size < SignaturePolicy.MIN_VALID_TRIPS || count == 0L -> TraitAvailability.INSUFFICIENT_EVIDENCE
                else -> TraitAvailability.AVAILABLE
            }
            val level = if (availability == TraitAvailability.AVAILABLE) level(passing.toDouble() / count) else TraitLevel.RECORDING
            val metrics = if (supported.isEmpty()) emptyList() else MetricId.entries.mapNotNull { metric ->
                val observations = available.mapNotNull { derived ->
                    derived.metrics.firstOrNull { it.id == metric && it.value.isFinite() }?.let { it to derived.supportedSampleCount.toLong() }
                }
                if (observations.isEmpty()) null else {
                    val weight = observations.sumOf { it.second }
                    PrivateMetric(metric, observations.sumOf { it.first.value * it.second } / weight, observations.first().first.unit)
                }
            }
            val changes = if (old != null && old.level != level) {
                (old.recentChanges + EvidenceChange(effectiveWindow.untilEpochMillis, old.level, level)).takeLast(5)
            } else old?.recentChanges.orEmpty()
            TraitEvidence(
                id = "trait-${trait.name.lowercase()}", version = (old?.version ?: 0) + 1,
                trait = trait, availability = availability, level = level, source = source, window = effectiveWindow,
                validTripCount = available.size, sampleCount = count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                signals = if (supported.isEmpty()) emptySet() else signals(trait), metrics = metrics, recentChanges = changes,
            )
        }
    }

    override fun calculate(
        previous: MySignature,
        trips: List<DerivedTripMetrics>,
        visibility: VisibilitySettings,
        evaluatedAtEpochMillis: Long,
    ): MySignature {
        require(evaluatedAtEpochMillis >= 0)
        val window = EvidenceWindow((evaluatedAtEpochMillis - SignaturePolicy.WINDOW_MILLIS).coerceAtLeast(0), evaluatedAtEpochMillis)
        val evidence = aggregate(trips, window, previous.evidence)
        val result = MySignature(previous.id, previous.version + 1, knotSeed = knot(evidence), evidence = evidence, visibility = visibility)
        return result.copy(publicSignature = publicProjection(result))
    }

    override fun publicProjection(signature: MySignature): PublicSignature? {
        if (!signature.visibility.publishSignature) return null
        val visible = signature.evidence.filter {
            it.trait in signature.visibility.visibleTraits && it.availability == TraitAvailability.AVAILABLE && it.level != TraitLevel.RECORDING && it.validTripCount >= SignaturePolicy.MIN_VALID_TRIPS
        }.distinctBy { it.trait }.sortedBy { it.trait.ordinal }
        val publicTraits = visible.map { PublicTrait(it.trait, it.level) }
        // Rebuild exclusively from public levels; even the private seed must not cross this boundary.
        val publicSeedEvidence = visible.map { it.copy(metrics = emptyList(), sampleCount = 0, recentChanges = emptyList()) }
        val bucket = when {
            visible.isEmpty() -> EvidenceBucket.RECORDING
            visible.all { it.level == TraitLevel.SIGNATURE } -> EvidenceBucket.ESTABLISHED
            else -> EvidenceBucket.GROWING
        }
        return PublicSignature("public-signature", signature.version, title(publicTraits), publicTraits, knot(publicSeedEvidence), source(signature.evidence), bucket)
    }

    override fun chemistry(first: PublicSignature, second: PublicSignature): Chemistry {
        val a = first.traits.filter { it.level != TraitLevel.RECORDING }.map { it.trait }.toSet()
        val b = second.traits.filter { it.level != TraitLevel.RECORDING }.map { it.trait }.toSet()
        val shared = TraitId.entries.filter { it in a && it in b }.map {
            ChemistryLine(ChemistryKind.SHARED, it, it, SignatureLanguage.sharedSentences.getValue(it))
        }
        val complementary = listOf(
            Triple(TraitId.EARLY_BRAKE, TraitId.STEADY_PACE, "한 분은 미리 멈추고, 한 분은 흐름을 지켜요"),
            Triple(TraitId.GENTLE_RIDE, TraitId.CABIN_CARE, "한 분은 편안하게 달리고, 한 분은 쾌적함을 챙겨요"),
            Triple(TraitId.LONG_ROAD, TraitId.ENERGY_PLANNER, "한 분은 긴 여정을 즐기고, 한 분은 미리 채워요"),
        ).filter { (x, y) -> (x in a && y in b) || (y in a && x in b) }.map { (x, y, text) ->
            ChemistryLine(ChemistryKind.COMPLEMENTARY, x, y, text)
        }
        return Chemistry((shared + complementary).take(3))
    }

    override fun title(traits: List<PublicTrait>): String {
        val sorted = traits.filter { it.level != TraitLevel.RECORDING }.groupBy { it.trait }.values.map { matching -> matching.maxBy { it.level.ordinal } }
            .sortedWith(compareByDescending<PublicTrait> { it.level.ordinal }.thenBy { it.trait.ordinal })
        if (sorted.isEmpty()) return SignatureLanguage.RECORDING_TITLE
        val noun = when (sorted.firstOrNull { it.trait == TraitId.LONG_ROAD || it.trait == TraitId.NIGHT_DRIVE }?.trait) {
            TraitId.LONG_ROAD -> SignatureLanguage.nouns[1]
            TraitId.NIGHT_DRIVE -> SignatureLanguage.nouns[2]
            else -> SignatureLanguage.nouns[0]
        }
        return "${SignatureLanguage.modifiers.getValue(sorted.first().trait)} $noun"
    }

    override fun knot(evidence: List<TraitEvidence>): KnotSeed {
        val levels = TraitId.entries.associateWith { trait ->
            evidence.firstOrNull { it.trait == trait && it.availability == TraitAvailability.AVAILABLE }?.level?.ordinal ?: 0
        }
        // SignatureKnot mixes the seed before reading these four parameter bytes. Invert that
        // permutation so one coarse level changes only a small, independent geometry parameter.
        val tilt = 96 + levels.getValue(TraitId.GENTLE_RIDE) * 12 + levels.getValue(TraitId.EARLY_BRAKE) * 4 + levels.getValue(TraitId.BELT_FIRST) * 2 + levels.getValue(TraitId.BATTERY_KIND)
        val width = 96 + levels.getValue(TraitId.LONG_ROAD) * 16 + levels.getValue(TraitId.NIGHT_DRIVE) * 4 + levels.getValue(TraitId.ENERGY_PLANNER) * 2 + levels.getValue(TraitId.FLUID_READY)
        val lobe = 96 + levels.getValue(TraitId.STEADY_PACE) * 10 + levels.getValue(TraitId.SIGNAL_MANNERS) * 5 + levels.getValue(TraitId.TIRE_CARE) * 2 + levels.getValue(TraitId.CABIN_CARE)
        var hash = 128L or (tilt.toLong() shl 8) or (width.toLong() shl 16) or (lobe.toLong() shl 24)
        TraitId.entries.forEach { trait -> hash = hash or (levels.getValue(trait).toLong() shl (32 + trait.ordinal * 2)) }
        // Quantized metrics affect only unused high bits, retaining a stable visible silhouette.
        val metricBucket = evidence.flatMap { it.metrics }.filter { it.value.isFinite() }.sumOf { (it.value.coerceIn(0.0, 10.0) * 4).roundToLong() } and 255
        hash = hash or (metricBucket shl 56)
        return KnotSeed(unmix(hash))
    }

    override fun recap(previous: MySignature, current: MySignature, trip: DerivedTripMetrics): TripRecap {
        val changes = if (trip.excluded || trip.eligibility != TripEligibility.ELIGIBLE) emptyList() else current.evidence.mapNotNull { evidence ->
            val previousLevel = previous.evidence.firstOrNull { it.trait == evidence.trait }?.level ?: TraitLevel.RECORDING
            if (evidence.level == previousLevel) null else TraitChange(evidence.trait, previousLevel, evidence.level,
                if (evidence.level.ordinal > previousLevel.ordinal) "이번 주행으로 ${traitName(evidence.trait)}의 모습이 조금 더 쌓였어요" else "최근 주행을 모아 ${traitName(evidence.trait)}의 모습을 다시 살펴봤어요")
        }
        return TripRecap("recap-${trip.id}", current.version, trip.id, trip.endedAtEpochMillis, trip.source,
            previous.knotSeed, current.knotSeed, changes, included = !trip.excluded && trip.eligibility == TripEligibility.ELIGIBLE)
    }

    private fun eligibleTrips(trips: List<DerivedTripMetrics>, window: EvidenceWindow): List<DerivedTripMetrics> = trips.groupBy { it.id }.values.map { versions ->
        versions.sortedWith(compareByDescending<DerivedTripMetrics> { it.version }.thenByDescending { it.excluded }).first()
    }.filter { !it.excluded && it.eligibility == TripEligibility.ELIGIBLE && it.startedAtEpochMillis >= window.fromEpochMillis && it.endedAtEpochMillis <= window.untilEpochMillis && it.endedAtEpochMillis >= it.startedAtEpochMillis }
        .sortedBy { it.id }

    private fun source(trips: List<DerivedTripMetrics>): DataSource = if (trips.any { it.source == DataSource.SYNTHETIC }) DataSource.SYNTHETIC else DataSource.VEHICLE
    private fun source(evidence: Collection<TraitEvidence>): DataSource = if (evidence.any { it.source == DataSource.SYNTHETIC }) DataSource.SYNTHETIC else DataSource.VEHICLE
    private fun level(ratio: Double): TraitLevel = when {
        ratio >= SignaturePolicy.SIGNATURE_RATIO -> TraitLevel.SIGNATURE
        ratio >= SignaturePolicy.STEADY_RATIO -> TraitLevel.STEADY
        ratio >= SignaturePolicy.VISIBLE_RATIO -> TraitLevel.VISIBLE
        else -> TraitLevel.RECORDING
    }

    private fun unmix(value: Long): Long {
        fun undoRightXor(input: Long, shift: Int): Long {
            var output = input
            var distance = shift
            while (distance < 64) { output = output xor (input ushr distance); distance += shift }
            return output
        }
        val b = undoRightXor(value, 31) * 3573116690164977347L
        val a = undoRightXor(b, 27) * -7575587736534282103L
        return undoRightXor(a, 30)
    }

    private fun signals(trait: TraitId): Set<SignalKind> = when (trait) {
        TraitId.GENTLE_RIDE -> setOf(SignalKind.SPEED)
        TraitId.EARLY_BRAKE -> setOf(SignalKind.SPEED, SignalKind.BRAKE)
        TraitId.SIGNAL_MANNERS -> setOf(SignalKind.SPEED, SignalKind.STEERING_MAGNITUDE, SignalKind.TURN_INDICATOR)
        TraitId.STEADY_PACE -> setOf(SignalKind.SPEED, SignalKind.CRUISE)
        TraitId.BELT_FIRST -> setOf(SignalKind.SEAT_BELT, SignalKind.GEAR)
        TraitId.LONG_ROAD -> setOf(SignalKind.TRIP_LENGTH)
        TraitId.NIGHT_DRIVE -> setOf(SignalKind.CLOCK)
        TraitId.TIRE_CARE -> setOf(SignalKind.TIRE_PRESSURE, SignalKind.TIRE_WARNING)
        TraitId.ENERGY_PLANNER -> setOf(SignalKind.FUEL_LEVEL, SignalKind.BATTERY_LEVEL)
        TraitId.BATTERY_KIND -> setOf(SignalKind.CHARGE_LIMIT)
        TraitId.FLUID_READY -> setOf(SignalKind.OIL_WARNING, SignalKind.BRAKE_FLUID_WARNING, SignalKind.WASHER_FLUID_WARNING)
        TraitId.CABIN_CARE -> setOf(SignalKind.AIR_CLEANING)
    }

    private fun traitName(trait: TraitId): String = when (trait) {
        TraitId.GENTLE_RIDE -> "부드러운 주행"
        TraitId.EARLY_BRAKE -> "여유로운 제동"
        TraitId.SIGNAL_MANNERS -> "깜빡이 매너"
        TraitId.STEADY_PACE -> "한결같은 항속"
        TraitId.BELT_FIRST -> "출발 전 벨트 습관"
        TraitId.LONG_ROAD -> "긴 여정"
        TraitId.NIGHT_DRIVE -> "밤 드라이브"
        TraitId.TIRE_CARE -> "타이어 관리"
        TraitId.ENERGY_PLANNER -> "미리 채우는 습관"
        TraitId.BATTERY_KIND -> "배터리 아끼기"
        TraitId.FLUID_READY -> "소모품 점검"
        TraitId.CABIN_CARE -> "실내 공기 챙김"
    }
}
