package com.hellstation.domain.model

import java.time.Instant

/**
 * 혼잡도 등급. 나쁜 순서대로 나열되어 있습니다(ordinal에 의미가 있음).
 *
 * 경계와 근거는 docs/crowding-levels.md 2절.
 * **이 변환을 하는 곳은 [fromPercent] 하나뿐이어야 합니다.**
 * 화면 코드에서 `if (percent > 130)` 같은 비교를 다시 하면 두 기준이 갈라집니다.
 */
enum class CrowdLevel {
    EASY,     // 0 ~ 45%    앉거나 널널하게 서서 감
    BUSY,     // 45 ~ 80%   서서 가지만 편함
    BAD,      // 80 ~ 130%  사람과 닿음
    HELL,     // 130 ~ 170% 몸이 끼임
    WTF,      // 170% ~     보내는 게 낫습니다
    UNKNOWN,  // 데이터 없음. **절대 EASY로 대체하지 마세요**
    ;

    val isKnown: Boolean get() = this != UNKNOWN

    /** 등급 비교. UNKNOWN은 비교 대상이 아니므로 null. */
    fun isWorseThan(other: CrowdLevel): Boolean? =
        if (!isKnown || !other.isKnown) null else ordinal > other.ordinal

    companion object {
        /** 등급 하한값(%). UNKNOWN은 없음. */
        val THRESHOLDS: List<Pair<CrowdLevel, Double>> = listOf(
            EASY to 0.0,
            BUSY to 45.0,
            BAD to 80.0,
            HELL to 130.0,
            WTF to 170.0,
        )

        /** 경계에서 등급이 깜빡이지 않도록 요구하는 여유폭(%p). docs/crowding-levels.md 2절. */
        const val HYSTERESIS_MARGIN = 5.0

        /**
         * 혼잡도 %를 등급으로 바꿉니다. percent가 null이거나 음수면 [UNKNOWN].
         * 경계는 아래를 포함하고 위를 제외합니다 (`[하한, 상한)`).
         */
        fun fromPercent(percent: Double?): CrowdLevel = when {
            percent == null || percent.isNaN() || percent < 0.0 -> UNKNOWN
            percent < 45.0 -> EASY
            percent < 80.0 -> BUSY
            percent < 130.0 -> BAD
            percent < 170.0 -> HELL
            else -> WTF
        }

        /**
         * 이력 현상(hysteresis)을 적용한 등급 변환.
         *
         * 이미 [current] 등급을 보여주고 있을 때, 경계를 [HYSTERESIS_MARGIN]만큼
         * 확실히 넘지 않으면 등급을 유지합니다. 129.9% ↔ 130.1%를 오갈 때
         * 화면이 깜빡이는 것을 막습니다.
         *
         * 화면을 갱신할 때만 쓰세요. 저장하는 값은 항상 [fromPercent]입니다.
         */
        fun fromPercentSticky(percent: Double?, current: CrowdLevel?): CrowdLevel {
            val raw = fromPercent(percent)
            if (current == null || !current.isKnown || !raw.isKnown || raw == current) return raw
            if (percent == null) return raw

            val movingUp = raw.ordinal > current.ordinal
            val boundary = if (movingUp) {
                // current를 벗어나 위로 가려면 current의 상한 + margin 을 넘어야 함
                THRESHOLDS.firstOrNull { it.first.ordinal == current.ordinal + 1 }?.second
            } else {
                // 아래로 내려가려면 current의 하한 - margin 아래로 내려가야 함
                THRESHOLDS.firstOrNull { it.first == current }?.second
            } ?: return raw

            val crossed =
                if (movingUp) percent >= boundary + HYSTERESIS_MARGIN
                else percent < boundary - HYSTERESIS_MARGIN
            return if (crossed) raw else current
        }
    }
}

/**
 * 이 값을 얼마나 믿을 수 있나. 판정 규칙은 docs/crowding-levels.md 3절.
 */
enum class Confidence {
    HIGH,
    MEDIUM,
    LOW,
    ;

    /** 두 근거를 합쳤을 때의 신뢰도는 **항상 낮은 쪽**을 따릅니다. */
    fun combineWith(other: Confidence): Confidence =
        if (this.ordinal >= other.ordinal) this else other

    /** 한 단계 강등. LOW에서는 더 내려가지 않습니다. */
    fun downgrade(): Confidence = when (this) {
        HIGH -> MEDIUM
        MEDIUM -> LOW
        LOW -> LOW
    }
}

/**
 * 데이터 계층. **대체(fallback) 순서를 나타냅니다.**
 *
 * `LIVE → ESTIMATED → HISTORICAL → NONE` 순으로 내려갑니다.
 * 위 단계를 못 구하면 아래 단계로 떨어지되, 절대 멈추지 않습니다.
 */
enum class DataTier {
    /**
     * 지금 이 열차의 실측 혼잡도.
     *
     * **현재는 도달할 수 없는 단계입니다.** 공개된 실시간 혼잡도 API가 없습니다
     * (docs/api-validation.md 7번). TMAP appkey를 받으면 그때 채워집니다.
     */
    LIVE,

    /** 통계 기준선을 실시간 신호(배차 간격, 지연)로 보정한 값. 지금의 기본 경로입니다. */
    ESTIMATED,

    /** 보정 없는 통계 평균. Time Slider로 미래를 볼 때와 실시간이 없을 때 씁니다. */
    HISTORICAL,

    /** 아무 근거도 없음. 등급은 반드시 [CrowdLevel.UNKNOWN]. */
    NONE,
    ;

    val hasData: Boolean get() = this != NONE
}

/**
 * 근거가 정확히 무엇이었는지. 화면에서 안내 문구를 고르는 데 씁니다.
 * [DataTier]보다 한 단계 자세합니다.
 */
enum class CrowdSource {
    /** 실시간 실측 혼잡도 */
    REALTIME,

    /** 통계 기준선 + 실시간 보정 */
    REALTIME_BASELINE,

    /** 통계 기준선만 */
    BASELINE,

    /** 인접 역에서 추정 */
    NEIGHBOR,

    /** 근거 없음 */
    NONE,
    ;

    val tier: DataTier
        get() = when (this) {
            REALTIME -> DataTier.LIVE
            REALTIME_BASELINE, NEIGHBOR -> DataTier.ESTIMATED
            BASELINE -> DataTier.HISTORICAL
            NONE -> DataTier.NONE
        }
}

/**
 * 특정 역·방향·시각의 혼잡도 판정 결과. 화면에 내려보내는 최종 형태입니다.
 *
 * @param percent    정원 대비 %. 160명/칸 = 100%. 모르면 null
 * @param level      percent에서 유도된 등급
 * @param confidence 신뢰도
 * @param source     무엇을 근거로 계산했나
 * @param at         이 값이 가리키는 시각 (Time Slider로 미래를 볼 수 있으므로 now와 다를 수 있음)
 * @param note       화면에 덧붙일 짧은 근거 설명. 없으면 null
 */
data class CrowdIndex(
    val percent: Double?,
    val level: CrowdLevel,
    val confidence: Confidence,
    val source: CrowdSource,
    val at: Instant,
    val note: String? = null,
) {
    val tier: DataTier get() = source.tier

    /**
     * 화면에 숫자를 보여줘도 되는가.
     *
     * 신뢰도가 LOW일 때 정확해 보이는 숫자는 신뢰를 과장합니다.
     * docs/crowding-levels.md 4절 규칙 4.
     */
    val showsPercent: Boolean get() = percent != null && confidence != Confidence.LOW

    companion object {
        fun unknown(at: Instant, note: String? = null) = CrowdIndex(
            percent = null,
            level = CrowdLevel.UNKNOWN,
            confidence = Confidence.LOW,
            source = CrowdSource.NONE,
            at = at,
            note = note,
        )

        /** percent에서 level을 자동으로 유도해 만듭니다. 직접 생성자를 쓰는 것보다 안전합니다. */
        fun of(
            percent: Double?,
            confidence: Confidence,
            source: CrowdSource,
            at: Instant,
            note: String? = null,
        ): CrowdIndex {
            val level = CrowdLevel.fromPercent(percent)
            return CrowdIndex(
                percent = if (level.isKnown) percent else null,
                level = level,
                confidence = if (level.isKnown) confidence else Confidence.LOW,
                source = if (level.isKnown) source else CrowdSource.NONE,
                at = at,
                note = note,
            )
        }
    }
}
