package com.hellstation.domain.usecase

import com.hellstation.domain.model.DayType
import com.hellstation.domain.model.TimeSlot
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 시각을 통계 조회용 (요일구분, 시간대)로 바꿉니다.
 *
 * ## 왜 그냥 요일을 쓰면 안 되나
 *
 * 지하철의 하루는 자정에 끝나지 않습니다. 금요일 밤 00:30에 타는 열차는
 * 달력상 토요일이지만 **금요일 운행의 연장**입니다. 통계도 24:30까지 이어집니다.
 * 그래서 첫차 시각(05:30) 이전은 전날로 취급합니다.
 *
 * ## 공휴일
 *
 * 서울교통공사 통계에는 공휴일 구분이 없습니다(평일/토요일/일요일뿐).
 * 공휴일에는 일요일 값을 쓰되 **신뢰도를 한 단계 낮춰야 합니다** —
 * [isHolidayFallback]로 알려 줍니다.
 */
class ServiceCalendar(
    private val zone: ZoneId = ZoneId.of("Asia/Seoul"),
    /** 공휴일 판정. 기본값은 "공휴일을 모른다"입니다. */
    private val holidays: Set<LocalDate> = emptySet(),
) {

    /** 이 시각이 속한 운행일의 요일 구분. */
    fun dayTypeAt(instant: Instant): DayType {
        val serviceDate = serviceDateAt(instant)
        if (serviceDate in holidays) return DayType.SUNDAY
        return when (serviceDate.dayOfWeek.value) {
            6 -> DayType.SATURDAY
            7 -> DayType.SUNDAY
            else -> DayType.WEEKDAY
        }
    }

    /** 공휴일이라 일요일 통계로 대신했는가. */
    fun isHolidayFallback(instant: Instant): Boolean {
        val date = serviceDateAt(instant)
        return date in holidays && date.dayOfWeek.value < 6
    }

    /** 이 시각이 속한 30분 단위 통계 시간대. */
    fun slotAt(instant: Instant): TimeSlot =
        TimeSlot.of(instant.atZone(zone).toLocalTime())

    /**
     * 운행일. 첫차(05:30) 이전이면 전날입니다.
     */
    fun serviceDateAt(instant: Instant): LocalDate {
        val zoned = instant.atZone(zone)
        val minutes = zoned.hour * 60 + zoned.minute
        return if (minutes < TimeSlot.FIRST.minutesFromMidnight) {
            zoned.toLocalDate().minusDays(1)
        } else {
            zoned.toLocalDate()
        }
    }

    /** 운행 시간 안인가. */
    fun isInService(instant: Instant): Boolean = slotAt(instant).isWithinServiceHours

    /**
     * Time Slider가 특정 시간대를 가리킬 때, 그 시각의 [Instant]를 만듭니다.
     * 오늘 운행일 기준입니다.
     */
    fun instantForSlot(slot: TimeSlot, reference: Instant): Instant {
        val serviceDate = serviceDateAt(reference)
        val minutes = slot.minutesFromMidnight
        val dayOffset = minutes / (24 * 60)
        val minuteOfDay = minutes % (24 * 60)
        return serviceDate
            .plusDays(dayOffset.toLong())
            .atStartOfDay(zone)
            .plusMinutes(minuteOfDay.toLong())
            .toInstant()
    }
}
