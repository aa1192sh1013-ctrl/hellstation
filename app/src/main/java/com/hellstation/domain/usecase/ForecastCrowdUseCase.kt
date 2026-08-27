package com.hellstation.domain.usecase

import com.hellstation.domain.model.CrowdIndex
import com.hellstation.domain.model.Direction
import com.hellstation.domain.model.StationId
import com.hellstation.domain.model.TimeSlot
import com.hellstation.domain.repository.CrowdRepository
import java.time.Instant

/** Time Slider의 눈금 하나. */
data class CrowdForecastPoint(
    val slot: TimeSlot,
    val at: Instant,
    val crowd: CrowdIndex,
) {
    /** 지금 시각이 속한 눈금인가. 슬라이더에서 "현재" 표시를 찍는 데 씁니다. */
    fun isNow(now: Instant, calendar: ServiceCalendar): Boolean =
        calendar.slotAt(now).minutesFromMidnight == slot.minutesFromMidnight
}

/**
 * 하루 전체의 시간대별 예상 혼잡도. **Time Slider 전용입니다.**
 *
 * ## 미래 값은 항상 통계입니다
 *
 * 30분 뒤 열차가 얼마나 붐빌지는 아무도 실시간으로 알 수 없습니다.
 * 그래서 지금 시각을 벗어난 눈금은 전부 통계 기준선
 * ([com.hellstation.domain.model.DataTier.HISTORICAL])이고, 신뢰도는 `HIGH`가 되지 않습니다.
 *
 * 화면에서는 "예상"이라는 사실이 드러나야 합니다 — 지금 값과 미래 값을 똑같이 그리면
 * 사용자는 둘 다 실측이라고 믿습니다.
 *
 * ## 범위
 *
 * 통계가 05:30 ~ 24:30만 있으므로 슬라이더도 그 범위입니다([TimeSlot.ALL], 39칸).
 * 그 밖의 시각은 보여줄 값이 없습니다.
 */
class ForecastCrowdUseCase(
    private val crowd: CrowdRepository,
    private val calendar: ServiceCalendar,
) {

    suspend operator fun invoke(
        stationId: StationId,
        direction: Direction,
        reference: Instant = Instant.now(),
    ): List<CrowdForecastPoint> = TimeSlot.ALL.map { slot ->
        val at = calendar.instantForSlot(slot, reference)
        CrowdForecastPoint(
            slot = slot,
            at = at,
            crowd = crowd.crowdAt(stationId, direction, at),
        )
    }

    /**
     * 하루 중 가장 붐비는 시간대. "이 시간은 피하세요" 문구에 씁니다.
     * 값이 하나도 없으면 null.
     */
    fun worstSlot(points: List<CrowdForecastPoint>): CrowdForecastPoint? =
        points.filter { it.crowd.level.isKnown }
            .maxByOrNull { it.crowd.percent ?: 0.0 }

    /**
     * 지금 이후로 가장 한산해지는 시간대. "조금 이따 가세요" 문구에 씁니다.
     */
    fun calmestUpcoming(
        points: List<CrowdForecastPoint>,
        now: Instant,
    ): CrowdForecastPoint? =
        points.filter { it.at.isAfter(now) && it.crowd.level.isKnown }
            .minByOrNull { it.crowd.percent ?: Double.MAX_VALUE }
}
