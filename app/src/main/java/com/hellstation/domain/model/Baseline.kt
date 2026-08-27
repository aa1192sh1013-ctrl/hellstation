package com.hellstation.domain.model

import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 혼잡도 통계의 요일 구분.
 *
 * 서울교통공사 CSV는 이 세 가지뿐입니다. **공휴일 데이터는 없습니다.**
 * 공휴일에는 [SUNDAY] 값을 쓰되 신뢰도를 낮춰야 합니다.
 */
enum class DayType {
    WEEKDAY,
    SATURDAY,
    SUNDAY,
    ;

    /** CSV의 `요일구분` 컬럼 표기. */
    val csvLabel: String
        get() = when (this) {
            WEEKDAY -> "평일"
            SATURDAY -> "토요일"
            SUNDAY -> "일요일"
        }

    companion object {
        fun parse(raw: String?): DayType? = when (raw?.trim()) {
            "평일" -> WEEKDAY
            "토요일", "토" -> SATURDAY
            "일요일", "일", "공휴일" -> SUNDAY
            else -> null
        }

        fun of(dateTime: LocalDateTime): DayType = when (dateTime.dayOfWeek.value) {
            6 -> SATURDAY
            7 -> SUNDAY
            else -> WEEKDAY
        }
    }
}

/**
 * 통계의 30분 단위 시간대. 05:30 ~ 24:30 범위입니다.
 *
 * 자정을 넘긴 시간대(24:00, 24:30)를 다루기 위해 "자정부터의 분"으로 표현합니다.
 * 즉 24:30 = 1470분입니다. [LocalTime]으로는 표현할 수 없어서 Int를 씁니다.
 */
@JvmInline
value class TimeSlot(val minutesFromMidnight: Int) {

    /** CSV 컬럼명. 예: 330 -> "5시30분" */
    val csvLabel: String
        get() {
            val hour = minutesFromMidnight / 60
            val minute = minutesFromMidnight % 60
            return "${hour}시${minute.toString().padStart(2, '0')}분"
        }

    /** 화면 표시용. 예: "05:30" */
    val displayLabel: String
        get() {
            val hour = minutesFromMidnight / 60
            val minute = minutesFromMidnight % 60
            return "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
        }

    val isWithinServiceHours: Boolean
        get() = minutesFromMidnight in FIRST.minutesFromMidnight..LAST.minutesFromMidnight

    operator fun plus(slots: Int) = TimeSlot(minutesFromMidnight + slots * SLOT_MINUTES)

    companion object {
        const val SLOT_MINUTES = 30

        /** 05:30 */
        val FIRST = TimeSlot(5 * 60 + 30)

        /** 24:30 */
        val LAST = TimeSlot(24 * 60 + 30)

        /** 통계가 존재하는 모든 시간대. Time Slider의 눈금이 됩니다. */
        val ALL: List<TimeSlot> = generateSequence(FIRST) { prev ->
            val next = prev + 1
            if (next.minutesFromMidnight <= LAST.minutesFromMidnight) next else null
        }.toList()

        /**
         * 시각을 30분 단위로 내림합니다.
         *
         * 새벽 0~2시대는 전날의 24시/25시대로 취급합니다 — 지하철은 자정을 넘겨 운행하고
         * 통계도 24:30까지 이어지기 때문입니다.
         */
        fun of(time: LocalTime): TimeSlot {
            val raw = time.hour * 60 + time.minute
            val adjusted = if (raw < FIRST.minutesFromMidnight) raw + 24 * 60 else raw
            val floored = adjusted / SLOT_MINUTES * SLOT_MINUTES
            return TimeSlot(floored)
        }

        /** CSV 컬럼명("5시30분")을 되돌립니다. 형식이 다르면 null. */
        fun fromCsvLabel(label: String): TimeSlot? {
            val match = Regex("""(\d{1,2})시(\d{1,2})분""").find(label.trim()) ?: return null
            val hour = match.groupValues[1].toIntOrNull() ?: return null
            val minute = match.groupValues[2].toIntOrNull() ?: return null
            return TimeSlot(hour * 60 + minute)
        }
    }
}

/** 통계 한 칸을 찾는 키. */
data class BaselineKey(
    val station: StationId,
    val direction: Direction,
    val dayType: DayType,
    val slot: TimeSlot,
)

/**
 * 통계값의 품질.
 *
 * 이 구분이 신뢰도의 상한을 결정합니다. [APPROXIMATED] 값은 **절대 HIGH가 될 수 없습니다.**
 */
enum class BaselineQuality {
    /** 서울교통공사가 실제로 측정해 공개한 값 (assets의 CSV) */
    MEASURED,

    /** 실측 통계가 없어 시간대 패턴으로 근사한 값. 특정 역의 실제 수치가 아닙니다 */
    APPROXIMATED,
    ;

    /** 이 품질로 도달할 수 있는 최고 신뢰도. */
    val maxConfidence: Confidence
        get() = when (this) {
            MEASURED -> Confidence.HIGH
            APPROXIMATED -> Confidence.LOW
        }
}

/** 통계 조회 결과 한 건. */
data class BaselineSample(
    val percent: Double,
    val quality: BaselineQuality,
)
