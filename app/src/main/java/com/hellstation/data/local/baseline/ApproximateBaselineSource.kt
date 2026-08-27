package com.hellstation.data.local.baseline

import com.hellstation.domain.model.BaselineKey
import com.hellstation.domain.model.BaselineQuality
import com.hellstation.domain.model.BaselineSample
import com.hellstation.domain.model.DayType
import com.hellstation.domain.model.Direction
import com.hellstation.domain.model.LineId
import com.hellstation.domain.model.StationId
import com.hellstation.domain.model.StationProfile
import com.hellstation.domain.model.TimeSlot
import com.hellstation.domain.repository.BaselineSource

/**
 * 실측 통계가 없을 때 쓰는 **마지막 대체 수단**.
 *
 * ## 이 값이 무엇이고 무엇이 아닌가
 *
 * **특정 역의 실측 혼잡도가 아닙니다.** 시간대 곡선에 역의 순위를 곱해 만든 어림값입니다.
 * 그래서 [BaselineQuality.APPROXIMATED]로 표시되고, 이 값으로 계산된 혼잡도는
 * **절대 [com.hellstation.domain.model.Confidence.HIGH]가 될 수 없습니다.**
 *
 * ## 왜 역마다 값을 벌리나
 *
 * 처음에는 시간대 곡선 하나만 돌려줬습니다. 그랬더니 **모든 역이 같은 값**이 되어
 * 지도 전체가 한 가지 색으로 칠해졌습니다. "어디가 지옥인가"를 보여주는 앱에서
 * 지도가 단색이면 아무 쓸모가 없습니다.
 *
 * 그래서 역 목록에서 **실제로 관측되는** 특징으로 값을 벌립니다.
 *
 * - **환승 노선 수 · 도심까지의 거리 · 노선 성향** → [StationProfile.busynessPercentile]
 *   (셋을 섞어 네트워크 전체 순위로 만든 값. 순위를 쓰는 이유는 그쪽 설명 참고)
 * - **방향** — 아침에는 도심 방향이, 저녁에는 바깥 방향이 붐빕니다
 *
 * ## 계산식을 이렇게 잡은 근거
 *
 * 처음에는 계수를 전부 곱했습니다(`곡선 × 노선 × 환승 × 도심 × 방향`).
 * 역 306개로 실제로 돌려 보니 **출근 시간에 85%가 최고 등급(WTF)** 으로 몰려서
 * 러시아워에 지도가 다시 단색이 됐습니다. 계수가 서로 곱해지며 폭발한 것입니다.
 *
 * 지금은 곡선을 **중앙값 역 기준**으로 잡고, 순위 하나로만 [WEIGHT_MIN]~[WEIGHT_MAX] 배를
 * 곱합니다. 같은 데이터로 다시 재 보니 08:00 기준
 * `BUSY 1% / BAD 53% / HELL 41% / WTF 6%`, 중앙값 129%, 최대 178% 로 갈렸습니다.
 *
 * ## 실측 CSV를 넣으면
 *
 * [CsvBaselineSource]가 우선하고 이 소스는 1~8호선 밖 노선에만 쓰입니다.
 *
 * @param profileOf 역의 특징을 찾는 함수. 못 찾으면 중간 순위로 봅니다
 */
class ApproximateBaselineSource(
    private val profileOf: suspend (StationId) -> StationProfile? = { null },
) : BaselineSource {

    override suspend fun sample(key: BaselineKey): BaselineSample? {
        val base = curveFor(key.dayType, key.slot) ?: return null
        val profile = profileOf(key.station)

        val percent = base *
            stationWeight(profile) *
            directionFactor(profile, key.direction, key.dayType, key.slot) *
            jitter(key.station)

        return BaselineSample(
            percent = percent.coerceIn(MIN_PERCENT, MAX_PERCENT),
            quality = BaselineQuality.APPROXIMATED,
        )
    }

    /** 어림값이므로 실측 데이터를 가진 것이 아닙니다. */
    override suspend fun hasMeasuredData(): Boolean = false

    // ── 보정 ────────────────────────────────────────────────────────────────

    /**
     * 역 순위를 배율로 바꿉니다.
     *
     * 순위가 0~1로 고르게 퍼져 있으므로 배율도 고르게 퍼집니다.
     * 이게 지도에 여러 색이 나오게 하는 핵심입니다.
     */
    private fun stationWeight(profile: StationProfile?): Double {
        val percentile = (profile?.busynessPercentile ?: 0.5f).toDouble()
        return WEIGHT_MIN + percentile * (WEIGHT_MAX - WEIGHT_MIN)
    }

    /**
     * 출퇴근 방향 쏠림.
     *
     * 아침에는 도심으로 들어가는 열차가, 저녁에는 나가는 열차가 붐빕니다.
     * 이 보정이 없으면 상행과 하행이 똑같이 나와서 **방향 선택 버튼이 의미를 잃습니다.**
     * (역 306개로 확인: 이 보정으로 출근 시간에 절반 가까운 역에서 두 방향의 등급이 갈립니다)
     */
    private fun directionFactor(
        profile: StationProfile?,
        direction: Direction,
        dayType: DayType,
        slot: TimeSlot,
    ): Double {
        // 주말에는 출퇴근 쏠림이 없습니다.
        if (dayType != DayType.WEEKDAY) return 1.0
        val inbound = profile?.isInbound(direction) ?: return 1.0

        val minutes = slot.minutesFromMidnight
        val morningPeak = minutes in (7 * 60) until (9 * 60 + 30)
        val eveningPeak = minutes in (17 * 60 + 30) until (20 * 60)

        return when {
            morningPeak -> if (inbound) PEAK_HEAVY else PEAK_LIGHT
            eveningPeak -> if (inbound) PEAK_LIGHT else PEAK_HEAVY
            else -> 1.0
        }
    }

    /**
     * 역마다 조금씩 다른 흔들림.
     *
     * 실제 노선에서도 옆 역끼리 혼잡도가 똑같지는 않습니다. 순위만으로는 인접한 역들이
     * 계단처럼 딱딱 떨어져 보입니다.
     *
     * **난수가 아니라 역 키의 해시**입니다. 같은 역은 몇 번을 다시 그려도 같은 값이 나와야
     * 화면을 갱신할 때 색이 춤추지 않습니다.
     */
    private fun jitter(id: StationId): Double {
        var hash = 2166136261u
        for (char in id.key) {
            hash = hash xor char.code.toUInt()
            hash *= 16777619u
        }
        val unit = (hash % 1000u).toDouble() / 1000.0   // 0.0 ~ 1.0
        return 1.0 - JITTER + unit * (JITTER * 2)
    }

    // ── 시간대 곡선 ─────────────────────────────────────────────────────────

    private fun curveFor(dayType: DayType, slot: TimeSlot): Double? {
        if (!slot.isWithinServiceHours) return null
        val weekday = WEEKDAY_CURVE[slot.minutesFromMidnight] ?: return null
        return when (dayType) {
            DayType.WEEKDAY -> weekday
            DayType.SATURDAY -> flatten(weekday, SATURDAY_PEAK_DAMPING)
            DayType.SUNDAY -> flatten(weekday, SUNDAY_PEAK_DAMPING)
        }
    }

    /**
     * 주말 곡선은 평일 곡선의 봉우리를 눌러서 만듭니다.
     *
     * 주말에도 사람은 타지만 출퇴근 봉우리가 없습니다. 기준선([FLAT_LEVEL]) 위로 솟은
     * 부분만 [damping]배로 줄이면 낮 시간대는 비슷하고 러시아워만 낮아집니다.
     */
    private fun flatten(weekdayPercent: Double, damping: Double): Double {
        val excess = weekdayPercent - FLAT_LEVEL
        return if (excess <= 0) weekdayPercent else FLAT_LEVEL + excess * damping
    }

    companion object {
        /**
         * 노선이 원래 얼마나 붐비는가.
         *
         * 서울교통공사가 발표하는 혼잡도 자료에서 2호선·9호선이 늘 상위, 6·8호선이 하위인 것을
         * 반영한 **대략적인** 값입니다. 역 순위를 매길 때 [com.hellstation.data.local.cache.NetworkTopology]가
         * 이 함수를 받아 씁니다.
         *
         * 실측 CSV를 넣으면 1~8호선은 이 값을 쓰지 않게 됩니다.
         */
        fun lineWeightOf(line: LineId): Double = when (line) {
            LineId.LINE_9 -> 1.30      // 급행 혼잡으로 유명
            LineId.LINE_2 -> 1.24
            LineId.LINE_4 -> 1.14
            LineId.LINE_7 -> 1.10
            LineId.SINBUNDANG -> 1.08
            LineId.LINE_1 -> 1.05
            LineId.LINE_3 -> 1.04
            LineId.LINE_5 -> 1.00
            LineId.LINE_6 -> 0.86
            LineId.LINE_8 -> 0.82
            LineId.UI_SINSEOL, LineId.SILLIM -> 0.72
            else -> 0.92
        }

        /** 가장 한산한 역이 곡선 값의 몇 배인가. */
        const val WEIGHT_MIN = 0.62

        /** 가장 붐비는 역이 곡선 값의 몇 배인가. */
        const val WEIGHT_MAX = 1.30

        private const val FLAT_LEVEL = 46.0
        private const val SATURDAY_PEAK_DAMPING = 0.45
        private const val SUNDAY_PEAK_DAMPING = 0.32

        private const val PEAK_HEAVY = 1.16
        private const val PEAK_LIGHT = 0.84
        private const val JITTER = 0.08

        private const val MIN_PERCENT = 4.0
        private const val MAX_PERCENT = 205.0

        /**
         * 평일 시간대별 혼잡도 어림값 (자정부터의 분 -> %).
         *
         * **"중앙값 역" 기준입니다.** 가장 붐비는 역이 아니라 한가운데 역의 값이라
         * 여기에 역 순위 배율([WEIGHT_MIN]~[WEIGHT_MAX])을 곱해야 실제 값이 됩니다.
         *
         * 출근 봉우리(08:00 전후)가 퇴근 봉우리(18:30 전후)보다 높고 좁은 것은
         * 출근 시각이 퇴근 시각보다 몰려 있기 때문입니다.
         */
        val WEEKDAY_CURVE: Map<Int, Double> = mapOf(
            5 * 60 + 30 to 12.0,
            6 * 60 to 20.0,
            6 * 60 + 30 to 33.0,
            7 * 60 to 60.0,
            7 * 60 + 30 to 92.0,
            8 * 60 to 115.0,
            8 * 60 + 30 to 110.0,
            9 * 60 to 82.0,
            9 * 60 + 30 to 58.0,
            10 * 60 to 46.0,
            10 * 60 + 30 to 42.0,
            11 * 60 to 40.0,
            11 * 60 + 30 to 42.0,
            12 * 60 to 46.0,
            12 * 60 + 30 to 47.0,
            13 * 60 to 45.0,
            13 * 60 + 30 to 44.0,
            14 * 60 to 44.0,
            14 * 60 + 30 to 45.0,
            15 * 60 to 47.0,
            15 * 60 + 30 to 50.0,
            16 * 60 to 54.0,
            16 * 60 + 30 to 58.0,
            17 * 60 to 66.0,
            17 * 60 + 30 to 78.0,
            18 * 60 to 100.0,
            18 * 60 + 30 to 105.0,
            19 * 60 to 85.0,
            19 * 60 + 30 to 68.0,
            20 * 60 to 56.0,
            20 * 60 + 30 to 52.0,
            21 * 60 to 50.0,
            21 * 60 + 30 to 48.0,
            22 * 60 to 46.0,
            22 * 60 + 30 to 42.0,
            23 * 60 to 36.0,
            23 * 60 + 30 to 29.0,
            24 * 60 to 21.0,
            24 * 60 + 30 to 12.0,
        )
    }
}
