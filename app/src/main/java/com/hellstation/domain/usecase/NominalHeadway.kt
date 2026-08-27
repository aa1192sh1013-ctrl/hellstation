package com.hellstation.domain.usecase

import com.hellstation.domain.model.DayType
import com.hellstation.domain.model.TimeSlot

/**
 * 시간대별 "정상" 배차 간격(초).
 *
 * 관측된 배차 간격이 이보다 길면 승객이 더 쌓였다는 뜻이고, 짧으면 덜 쌓였다는 뜻입니다.
 * [com.hellstation.domain.usecase.CrowdEstimator]가 통계값을 실시간으로 보정할 때 기준으로 씁니다.
 *
 * ## 주의: 이건 실측이 아니라 근사값입니다
 *
 * 노선별 실제 배차표를 공개 API로 받을 수 없어서(docs/api-validation.md) 시간대 구간별
 * 대표값을 씁니다. 그래서 이 보정이 만들어낸 값은 **절대 실측으로 취급하지 않습니다** —
 * 데이터 계층은 [com.hellstation.domain.model.DataTier.ESTIMATED]이지 LIVE가 아닙니다.
 */
object NominalHeadway {

    private const val PEAK = 150            // 2분 30초
    private const val NEAR_PEAK = 240       // 4분
    private const val NORMAL = 330          // 5분 30초
    private const val LATE = 450            // 7분 30초
    private const val EARLY = 480           // 8분

    /** 주말은 배차가 더 깁니다. */
    private const val WEEKEND_MULTIPLIER = 1.35

    /**
     * @param slot    관심 시간대
     * @param dayType 요일 구분
     * @return 그 시간대의 정상 배차 간격(초)
     */
    fun secondsAt(slot: TimeSlot, dayType: DayType): Int {
        val minutes = slot.minutesFromMidnight
        val weekdayBase = when (minutes) {
            in (7 * 60) until (9 * 60) -> PEAK           // 07:00 ~ 09:00 출근
            in (18 * 60) until (20 * 60) -> PEAK          // 18:00 ~ 20:00 퇴근
            in (6 * 60 + 30) until (7 * 60) -> NEAR_PEAK  // 06:30 ~ 07:00
            in (9 * 60) until (10 * 60) -> NEAR_PEAK      // 09:00 ~ 10:00
            in (17 * 60) until (18 * 60) -> NEAR_PEAK     // 17:00 ~ 18:00
            in (20 * 60) until (21 * 60) -> NEAR_PEAK     // 20:00 ~ 21:00
            in (10 * 60) until (17 * 60) -> NORMAL        // 낮
            in (21 * 60) until (22 * 60 + 30) -> NORMAL
            in 0 until (6 * 60 + 30) -> EARLY             // 첫차 직후
            else -> LATE                                  // 22:30 이후 · 자정 넘김
        }
        return when (dayType) {
            DayType.WEEKDAY -> weekdayBase
            DayType.SATURDAY, DayType.SUNDAY ->
                (weekdayBase * WEEKEND_MULTIPLIER).toInt()
        }
    }

    /**
     * 관측 간격이 정상 간격 대비 이 배수를 넘으면 지연으로 봅니다.
     * 배차는 원래 들쭉날쭉해서 여유 있게 잡았습니다.
     */
    const val DELAY_RATIO_THRESHOLD = 2.0
}
