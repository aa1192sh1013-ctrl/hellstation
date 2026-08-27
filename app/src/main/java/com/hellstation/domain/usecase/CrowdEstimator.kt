package com.hellstation.domain.usecase

import com.hellstation.domain.model.BaselineQuality
import com.hellstation.domain.model.BaselineSample
import com.hellstation.domain.model.Confidence
import com.hellstation.domain.model.CrowdIndex
import com.hellstation.domain.model.CrowdSource
import com.hellstation.domain.model.DayType
import com.hellstation.domain.model.ServiceStatus
import com.hellstation.domain.model.TimeSlot
import com.hellstation.domain.model.TrainType
import java.time.Instant
import kotlin.math.roundToInt

/**
 * 실시간 신호로 관측한 것들. 통계값을 보정하는 데 쓰입니다.
 *
 * @param headwaySeconds  관측된 배차 간격(초). 열차가 하나뿐이면 null
 * @param dataAgeSeconds  실시간 데이터가 만들어진 지 몇 초 지났나. 실시간이 없으면 null
 * @param trainType       판정 대상 열차의 종류
 * @param serviceStatus   운행 상태
 * @param isHolidayFallback 공휴일이라 일요일 통계로 대신했는가
 */
data class CrowdSignals(
    val headwaySeconds: Int? = null,
    val dataAgeSeconds: Long? = null,
    val trainType: TrainType = TrainType.UNKNOWN,
    val serviceStatus: ServiceStatus = ServiceStatus.NORMAL,
    val isHolidayFallback: Boolean = false,
) {
    val hasLiveData: Boolean get() = dataAgeSeconds != null

    companion object {
        /** 실시간 신호가 전혀 없는 상태 — Time Slider로 미래를 볼 때 등. */
        val NONE = CrowdSignals()
    }
}

/**
 * 혼잡도 계산의 **유일한** 진입점.
 *
 * 등급 경계는 [com.hellstation.domain.model.CrowdLevel.fromPercent]에,
 * 신뢰도 판정은 이 클래스의 [judgeConfidence]에만 있습니다.
 * 다른 곳에서 `if (percent > 130)` 같은 비교를 다시 하지 마세요.
 *
 * 근거는 docs/crowding-levels.md.
 */
class CrowdEstimator {

    /**
     * 통계 기준선과 실시간 신호를 합쳐 혼잡도를 판정합니다.
     *
     * @param baseline    통계값. null이면 결과는 반드시 UNKNOWN입니다
     * @param signals     실시간 관측값. 없으면 [CrowdSignals.NONE]
     * @param at          이 값이 가리키는 시각
     * @param slot        [at]이 속한 통계 시간대
     * @param dayType     [at]의 요일 구분
     * @param fromNeighbor 인접 역에서 가져온 값인가
     */
    fun estimate(
        baseline: BaselineSample?,
        signals: CrowdSignals,
        at: Instant,
        slot: TimeSlot,
        dayType: DayType,
        fromNeighbor: Boolean = false,
    ): CrowdIndex {
        if (baseline == null) {
            return CrowdIndex.unknown(at, note = "이 역·시간대의 혼잡도 자료가 없습니다")
        }

        val adjustment = liveAdjustment(baseline, signals, slot, dayType)
        val percent = (baseline.percent * adjustment.factor).coerceIn(0.0, MAX_PERCENT)

        val source = when {
            fromNeighbor -> CrowdSource.NEIGHBOR
            adjustment.usedLiveSignals -> CrowdSource.REALTIME_BASELINE
            else -> CrowdSource.BASELINE
        }

        val confidence = judgeConfidence(
            baseline = baseline,
            signals = signals,
            fromNeighbor = fromNeighbor,
        )

        return CrowdIndex.of(
            percent = percent,
            confidence = confidence,
            source = source,
            at = at,
            note = buildNote(baseline, adjustment, signals, fromNeighbor),
        )
    }

    // ── 실시간 보정 ──────────────────────────────────────────────────────────

    private data class Adjustment(
        val factor: Double,
        val usedLiveSignals: Boolean,
        val headwayRatio: Double?,
    )

    /**
     * 통계값에 곱할 보정 계수.
     *
     * 근거: 역에 쌓이는 승객 수는 배차 간격에 대체로 비례합니다.
     * 열차가 평소보다 늦게 오면 그만큼 사람이 더 모여 있습니다.
     *
     * 이 모델은 근사이므로 계수를 [MIN_FACTOR]~[MAX_FACTOR]로 묶어 둡니다.
     * 통계값이 통째로 뒤집힐 만큼 밀어붙이지 않기 위해서입니다.
     */
    private fun liveAdjustment(
        baseline: BaselineSample,
        signals: CrowdSignals,
        slot: TimeSlot,
        dayType: DayType,
    ): Adjustment {
        // 근사 통계에는 보정을 걸지 않습니다. 근거가 약한 값을 더 흔들어 봐야 의미가 없습니다.
        if (baseline.quality == BaselineQuality.APPROXIMATED) {
            return Adjustment(1.0, usedLiveSignals = false, headwayRatio = null)
        }
        if (!signals.hasLiveData) {
            return Adjustment(1.0, usedLiveSignals = false, headwayRatio = null)
        }

        var factor = 1.0
        var ratio: Double? = null

        val observed = signals.headwaySeconds
        if (observed != null && observed > 0) {
            val nominal = NominalHeadway.secondsAt(slot, dayType)
            val observedRatio = observed.toDouble() / nominal
            ratio = observedRatio
            factor *= observedRatio.coerceIn(HEADWAY_MIN_FACTOR, HEADWAY_MAX_FACTOR)
        }

        // 급행은 정차역이 적어 같은 시간대에도 사람이 더 몰립니다.
        factor *= when (signals.trainType) {
            TrainType.EXPRESS -> EXPRESS_FACTOR
            TrainType.RAPID -> RAPID_FACTOR
            else -> 1.0
        }

        return Adjustment(
            factor = factor.coerceIn(MIN_FACTOR, MAX_FACTOR),
            usedLiveSignals = true,
            headwayRatio = ratio,
        )
    }

    // ── 신뢰도 ───────────────────────────────────────────────────────────────

    /**
     * 신뢰도 판정. docs/crowding-levels.md 3절 규칙을 그대로 옮긴 것입니다.
     *
     * 순서가 중요합니다 — 상한을 먼저 정하고, 그다음 강등 조건을 적용합니다.
     */
    fun judgeConfidence(
        baseline: BaselineSample,
        signals: CrowdSignals,
        fromNeighbor: Boolean,
    ): Confidence {
        // 규칙 7: 인접 역 추정은 무조건 LOW
        if (fromNeighbor) return Confidence.LOW

        // 통계 품질이 신뢰도의 상한입니다. 근사값은 절대 HIGH가 될 수 없습니다.
        val ceiling = baseline.quality.maxConfidence
        val age = signals.dataAgeSeconds

        var confidence = when {
            // 규칙 2: 실시간 없이 통계만 -> MEDIUM 이하
            age == null -> ceiling.combineWith(Confidence.MEDIUM)

            // 규칙 3: 실시간이 너무 오래됨 -> LOW
            age > STALE_SECONDS -> Confidence.LOW

            // 규칙 4: 조금 오래됨 -> MEDIUM 이하
            age > FRESH_SECONDS -> ceiling.combineWith(Confidence.MEDIUM)

            // 규칙 5: 신선한 실시간 + 실측 통계 -> 상한 그대로 (HIGH 가능)
            else -> ceiling
        }

        // 자동 강등 조건들
        if (!signals.serviceStatus.isNormal) confidence = confidence.downgrade()
        if (signals.isHolidayFallback) confidence = confidence.downgrade()
        if (signals.trainType != TrainType.UNKNOWN && !signals.trainType.matchesBaseline) {
            confidence = confidence.downgrade()
        }

        return confidence
    }

    // ── 화면에 붙일 근거 문구 ────────────────────────────────────────────────

    private fun buildNote(
        baseline: BaselineSample,
        adjustment: Adjustment,
        signals: CrowdSignals,
        fromNeighbor: Boolean,
    ): String {
        val ratio = adjustment.headwayRatio
        return when {
            fromNeighbor -> "옆 역 자료로 추정한 값입니다"
            baseline.quality == BaselineQuality.APPROXIMATED ->
                "실측 통계가 없어 시간대 패턴으로 어림한 값입니다"
            !signals.serviceStatus.isNormal -> "운행이 평소와 달라 예측이 어렵습니다"
            ratio != null && ratio >= 1.3 ->
                "열차 간격이 평소보다 ${((ratio - 1) * 100).roundToInt()}% 벌어졌습니다"
            ratio != null && ratio <= 0.8 -> "열차가 평소보다 자주 옵니다"
            adjustment.usedLiveSignals -> "실시간 운행 정보로 보정했습니다"
            else -> "같은 요일·시간대 평균값입니다"
        }
    }

    companion object {
        /** 혼잡도 상한. 200%면 정원의 두 배입니다. 100%로 자르면 안 됩니다. */
        const val MAX_PERCENT = 220.0

        /** docs/crowding-levels.md 3절: 90초 이내면 신선함 */
        const val FRESH_SECONDS = 90L

        /** 5분을 넘으면 믿을 수 없음 */
        const val STALE_SECONDS = 300L

        private const val HEADWAY_MIN_FACTOR = 0.7
        private const val HEADWAY_MAX_FACTOR = 1.8
        private const val EXPRESS_FACTOR = 1.15
        private const val RAPID_FACTOR = 1.10
        private const val MIN_FACTOR = 0.6
        private const val MAX_FACTOR = 2.0
    }
}
