package dev.chayeon.feature.signature

import dev.chayeon.core.model.AppError
import dev.chayeon.core.model.DataSource
import dev.chayeon.core.model.EvidenceWindow
import dev.chayeon.core.model.MetricId
import dev.chayeon.core.model.MetricUnit
import dev.chayeon.core.model.PrivateMetric
import dev.chayeon.core.model.SignalKind
import dev.chayeon.core.model.TraitAvailability
import dev.chayeon.core.model.TraitEvidence
import dev.chayeon.core.model.TraitId
import dev.chayeon.core.model.TraitLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SignatureEvidencePresentationTest {
    @Test
    fun `notes use the unit of the aggregate without exposing raw measurements`() {
        assertEquals("82.5%", PrivateMetric(MetricId.SIGNAL_USAGE_RATIO, .825, MetricUnit.RATIO).noteValue())
        assertEquals("100%", PrivateMetric(MetricId.SIGNAL_USAGE_RATIO, 1.0, MetricUnit.RATIO).noteValue())
        assertEquals("10분당 1.3회", PrivateMetric(MetricId.ABRUPT_ACCELERATION_EVENTS_PER_TEN_MINUTES,
            1.25, MetricUnit.EVENTS_PER_TEN_MINUTES).noteValue())
        assertEquals("평균 대비 8%", PrivateMetric(MetricId.CRUISE_VARIATION_COEFFICIENT,
            .08, MetricUnit.COEFFICIENT).noteValue())
    }

    @Test
    fun `malformed measurements are unavailable instead of appearing as zero`() {
        listOf(Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, -.1, 1.1).forEach { value ->
            assertEquals("기록을 확인할 수 없어요", PrivateMetric(MetricId.SIGNAL_USAGE_RATIO,
                value, MetricUnit.RATIO).noteValue())
        }
        assertEquals("기록을 확인할 수 없어요", PrivateMetric(MetricId.SIGNAL_USAGE_RATIO,
            .8, MetricUnit.EVENTS_PER_TEN_MINUTES).noteValue())
        assertEquals("기록을 확인할 수 없어요", PrivateMetric(MetricId.CRUISE_VARIATION_COEFFICIENT,
            Double.MAX_VALUE, MetricUnit.COEFFICIENT).noteValue())
    }

    @Test
    fun `unsupported and recording notes never call missing evidence a low score`() {
        val unsupported = evidence().copy(availability = TraitAvailability.UNSUPPORTED)
        assertTrue(unsupported.levelExplanation().startsWith("이 차량에서 제공되지 않아요"))
        assertFalse(unsupported.levelExplanation().contains("0"))
        val recording = evidence().copy(level = TraitLevel.RECORDING, validTripCount = 2)
        assertTrue(recording.levelExplanation().contains("유효한 주행이 3회 이상"))
        val insufficient = evidence().copy(availability = TraitAvailability.INSUFFICIENT_EVIDENCE)
        assertTrue(insufficient.levelExplanation().contains("기록을 모으고 있어요"))
        assertFalse(insufficient.levelExplanation().contains("운전해"))
    }

    @Test
    fun `level explanations reuse canonical names and treat preferences as preferences`() {
        assertTrue(evidence().levelExplanation().contains("‘부드러운 주행’ 특성을 ‘꾸준해요’"))
        assertTrue(evidence().copy(trait = TraitId.NIGHT_DRIVE).levelExplanation().contains("취향이"))
        assertFalse(evidence().copy(trait = TraitId.NIGHT_DRIVE).levelExplanation().contains("습관이"))
        TraitLevel.entries.forEach { level ->
            val copy = evidence().copy(level = level).levelExplanation()
            assertFalse(copy.contains("점수"))
            assertFalse(copy.contains("올리"))
        }
    }

    @Test
    fun `every signal and metric has plain Korean copy`() {
        MetricId.entries.forEach { assertFalse(it.noteLabel().contains(it.name)) }
        SignalKind.entries.forEach { assertFalse(it.noteLabel().contains(it.name)) }
        assertFalse(MetricId.LONG_TRIP_RATIO.noteLabel().contains("km"))
        assertEquals("핸들을 돌린 정도", SignalKind.STEERING_MAGNITUDE.noteLabel())
    }

    @Test
    fun `period uses Korean calendar dates and validates window ordering`() {
        val from = Instant.parse("2026-11-02T15:00:00Z").toEpochMilli()
        val until = Instant.parse("2026-11-03T15:00:00Z").toEpochMilli()
        assertEquals("2026년 11월 3일", evidenceDate(from))
        assertEquals("2026년 11월 3일 ~ 2026년 11월 4일", evidencePeriod(EvidenceWindow(from, until)))
        assertEquals("2026년 11월 3일", evidencePeriod(EvidenceWindow(from, from + 1)))
        assertEquals("기록 기간을 확인할 수 없어요", evidencePeriod(EvidenceWindow(until, from)))
        assertEquals("날짜를 확인할 수 없어요", evidenceDate(-1))
    }

    @Test
    fun `all failures provide an action without exposing internals or blame`() {
        listOf(AppError.NotAuthorized, AppError.AlreadyClosed, AppError.Expired, AppError.PeerUnavailable,
            AppError.Restricted, AppError.Network, AppError.Unknown).forEach { error ->
            val message = error.signatureMessage("저장해")
            assertTrue(message.contains("저장해 주세요"))
            assertFalse(message.contains(error::class.simpleName.orEmpty()))
            assertFalse(message.contains("죄송"))
            assertFalse(message.contains("차단"))
        }
    }

    private fun evidence() = TraitEvidence(
        id = "evidence-gentle", version = 1, trait = TraitId.GENTLE_RIDE,
        availability = TraitAvailability.AVAILABLE, level = TraitLevel.STEADY,
        source = DataSource.SYNTHETIC, window = EvidenceWindow(0, 1), validTripCount = 4,
        sampleCount = 10, signals = setOf(SignalKind.SPEED), metrics = emptyList(),
    )
}
