package com.hellstation.domain

import com.hellstation.domain.model.BaselineQuality
import com.hellstation.domain.model.BaselineSample
import com.hellstation.domain.model.Confidence
import com.hellstation.domain.model.CrowdLevel
import com.hellstation.domain.model.CrowdSource
import com.hellstation.domain.model.DayType
import com.hellstation.domain.model.ServiceStatus
import com.hellstation.domain.model.TimeSlot
import com.hellstation.domain.model.TrainType
import com.hellstation.domain.usecase.CrowdEstimator
import com.hellstation.domain.usecase.CrowdSignals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * 혼잡도 등급과 신뢰도 규칙. 기준은 docs/crowding-levels.md.
 *
 * 이 규칙이 바뀌면 화면의 색과 문구가 전부 따라 바뀝니다. 함부로 고치지 마세요.
 */
class CrowdRulesTest {

    private val estimator = CrowdEstimator()
    private val at: Instant = Instant.parse("2026-08-24T23:00:00Z") // KST 08:00 월요일
    private val peakSlot = TimeSlot(8 * 60)

    // ── 등급 경계 ───────────────────────────────────────────────────────────

    @Test
    fun `경계값은 아래를 포함하고 위를 제외한다`() {
        assertEquals(CrowdLevel.EASY, CrowdLevel.fromPercent(0.0))
        assertEquals(CrowdLevel.EASY, CrowdLevel.fromPercent(44.9))
        assertEquals(CrowdLevel.BUSY, CrowdLevel.fromPercent(45.0))
        assertEquals(CrowdLevel.BUSY, CrowdLevel.fromPercent(79.9))
        assertEquals(CrowdLevel.BAD, CrowdLevel.fromPercent(80.0))
        assertEquals(CrowdLevel.BAD, CrowdLevel.fromPercent(129.9))
        assertEquals(CrowdLevel.HELL, CrowdLevel.fromPercent(130.0))
        assertEquals(CrowdLevel.HELL, CrowdLevel.fromPercent(169.9))
        assertEquals(CrowdLevel.WTF, CrowdLevel.fromPercent(170.0))
        assertEquals(CrowdLevel.WTF, CrowdLevel.fromPercent(300.0))
    }

    @Test
    fun `데이터가 없으면 UNKNOWN이지 EASY가 아니다`() {
        assertEquals(CrowdLevel.UNKNOWN, CrowdLevel.fromPercent(null))
        assertEquals(CrowdLevel.UNKNOWN, CrowdLevel.fromPercent(-1.0))
        assertEquals(CrowdLevel.UNKNOWN, CrowdLevel.fromPercent(Double.NaN))
    }

    @Test
    fun `이력 현상은 경계 근처에서 등급을 유지한다`() {
        // BAD(80~130)를 보여주는 중
        assertEquals(CrowdLevel.BAD, CrowdLevel.fromPercentSticky(131.0, CrowdLevel.BAD))
        assertEquals(CrowdLevel.BAD, CrowdLevel.fromPercentSticky(134.9, CrowdLevel.BAD))
        // 5%p를 확실히 넘으면 바뀐다
        assertEquals(CrowdLevel.HELL, CrowdLevel.fromPercentSticky(135.0, CrowdLevel.BAD))
        // 아래쪽도 마찬가지
        assertEquals(CrowdLevel.BAD, CrowdLevel.fromPercentSticky(76.0, CrowdLevel.BAD))
        assertEquals(CrowdLevel.BUSY, CrowdLevel.fromPercentSticky(74.9, CrowdLevel.BAD))
    }

    @Test
    fun `이전 등급이 없으면 이력 현상은 적용되지 않는다`() {
        assertEquals(CrowdLevel.HELL, CrowdLevel.fromPercentSticky(131.0, null))
        assertEquals(CrowdLevel.HELL, CrowdLevel.fromPercentSticky(131.0, CrowdLevel.UNKNOWN))
    }

    // ── 신뢰도 ─────────────────────────────────────────────────────────────

    @Test
    fun `신뢰도를 합치면 항상 낮은 쪽을 따른다`() {
        assertEquals(Confidence.LOW, Confidence.HIGH.combineWith(Confidence.LOW))
        assertEquals(Confidence.MEDIUM, Confidence.HIGH.combineWith(Confidence.MEDIUM))
        assertEquals(Confidence.HIGH, Confidence.HIGH.combineWith(Confidence.HIGH))
    }

    @Test
    fun `어림 통계는 절대 HIGH가 되지 않는다`() {
        val result = estimator.estimate(
            baseline = BaselineSample(150.0, BaselineQuality.APPROXIMATED),
            signals = CrowdSignals(dataAgeSeconds = 10, trainType = TrainType.NORMAL),
            at = at,
            slot = peakSlot,
            dayType = DayType.WEEKDAY,
        )
        assertEquals(Confidence.LOW, result.confidence)
        // 어림값에는 실시간 보정도 걸지 않는다
        assertEquals(150.0, result.percent!!, 0.001)
        assertEquals(CrowdSource.BASELINE, result.source)
    }

    @Test
    fun `실측 통계에 신선한 실시간이 붙으면 HIGH가 된다`() {
        val result = estimator.estimate(
            baseline = BaselineSample(100.0, BaselineQuality.MEASURED),
            signals = CrowdSignals(dataAgeSeconds = 30, trainType = TrainType.NORMAL),
            at = at,
            slot = peakSlot,
            dayType = DayType.WEEKDAY,
        )
        assertEquals(Confidence.HIGH, result.confidence)
        assertEquals(CrowdSource.REALTIME_BASELINE, result.source)
    }

    @Test
    fun `실시간이 없으면 MEDIUM 이하로 내려간다`() {
        val result = estimator.estimate(
            baseline = BaselineSample(100.0, BaselineQuality.MEASURED),
            signals = CrowdSignals.NONE,
            at = at,
            slot = peakSlot,
            dayType = DayType.WEEKDAY,
        )
        assertEquals(Confidence.MEDIUM, result.confidence)
        assertEquals(CrowdSource.BASELINE, result.source)
    }

    @Test
    fun `5분을 넘은 실시간은 LOW다`() {
        val result = estimator.estimate(
            baseline = BaselineSample(100.0, BaselineQuality.MEASURED),
            signals = CrowdSignals(dataAgeSeconds = 301, trainType = TrainType.NORMAL),
            at = at,
            slot = peakSlot,
            dayType = DayType.WEEKDAY,
        )
        assertEquals(Confidence.LOW, result.confidence)
    }

    @Test
    fun `운행 이상과 급행은 신뢰도를 한 단계씩 낮춘다`() {
        val delayed = estimator.estimate(
            baseline = BaselineSample(100.0, BaselineQuality.MEASURED),
            signals = CrowdSignals(
                dataAgeSeconds = 10,
                trainType = TrainType.NORMAL,
                serviceStatus = ServiceStatus.DELAYED,
            ),
            at = at, slot = peakSlot, dayType = DayType.WEEKDAY,
        )
        assertEquals(Confidence.MEDIUM, delayed.confidence)

        val express = estimator.estimate(
            baseline = BaselineSample(100.0, BaselineQuality.MEASURED),
            signals = CrowdSignals(dataAgeSeconds = 10, trainType = TrainType.EXPRESS),
            at = at, slot = peakSlot, dayType = DayType.WEEKDAY,
        )
        assertEquals(Confidence.MEDIUM, express.confidence)
    }

    @Test
    fun `인접 역 추정은 무조건 LOW다`() {
        val result = estimator.estimate(
            baseline = BaselineSample(100.0, BaselineQuality.MEASURED),
            signals = CrowdSignals(dataAgeSeconds = 5, trainType = TrainType.NORMAL),
            at = at, slot = peakSlot, dayType = DayType.WEEKDAY,
            fromNeighbor = true,
        )
        assertEquals(Confidence.LOW, result.confidence)
        assertEquals(CrowdSource.NEIGHBOR, result.source)
    }

    // ── 실시간 보정 ─────────────────────────────────────────────────────────

    @Test
    fun `배차 간격이 벌어지면 혼잡도가 올라간다`() {
        // 출근 시간대 정상 배차는 150초. 300초면 두 배로 쌓인 셈.
        val result = estimator.estimate(
            baseline = BaselineSample(100.0, BaselineQuality.MEASURED),
            signals = CrowdSignals(
                headwaySeconds = 300,
                dataAgeSeconds = 10,
                trainType = TrainType.NORMAL,
            ),
            at = at, slot = peakSlot, dayType = DayType.WEEKDAY,
        )
        // 계수는 1.8로 묶여 있다
        assertEquals(180.0, result.percent!!, 0.001)
        assertEquals(CrowdLevel.WTF, result.level)
    }

    @Test
    fun `배차 간격이 좁으면 혼잡도가 내려간다`() {
        val result = estimator.estimate(
            baseline = BaselineSample(150.0, BaselineQuality.MEASURED),
            signals = CrowdSignals(
                headwaySeconds = 60,
                dataAgeSeconds = 10,
                trainType = TrainType.NORMAL,
            ),
            at = at, slot = peakSlot, dayType = DayType.WEEKDAY,
        )
        // 0.4배지만 하한 0.7로 묶인다
        assertEquals(105.0, result.percent!!, 0.001)
        assertEquals(CrowdLevel.BAD, result.level)
    }

    @Test
    fun `혼잡도는 100퍼센트에서 잘리지 않는다`() {
        val result = estimator.estimate(
            baseline = BaselineSample(160.0, BaselineQuality.MEASURED),
            signals = CrowdSignals.NONE,
            at = at, slot = peakSlot, dayType = DayType.WEEKDAY,
        )
        assertTrue("정원 초과 값이 그대로 남아야 한다", result.percent!! > 100.0)
    }

    @Test
    fun `통계가 없으면 UNKNOWN을 낸다`() {
        val result = estimator.estimate(
            baseline = null,
            signals = CrowdSignals(dataAgeSeconds = 5),
            at = at, slot = peakSlot, dayType = DayType.WEEKDAY,
        )
        assertEquals(CrowdLevel.UNKNOWN, result.level)
        assertNull(result.percent)
        assertEquals(CrowdSource.NONE, result.source)
    }

    @Test
    fun `신뢰도가 낮으면 숫자를 보여주지 않는다`() {
        val low = estimator.estimate(
            baseline = BaselineSample(120.0, BaselineQuality.APPROXIMATED),
            signals = CrowdSignals.NONE,
            at = at, slot = peakSlot, dayType = DayType.WEEKDAY,
        )
        assertEquals(Confidence.LOW, low.confidence)
        assertTrue("LOW에서는 % 숫자를 숨겨야 한다", !low.showsPercent)
    }
}
