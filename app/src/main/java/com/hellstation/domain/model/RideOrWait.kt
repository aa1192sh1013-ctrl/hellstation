package com.hellstation.domain.model

/** 지금 탈까 기다릴까에 대한 답. */
enum class Verdict {
    /** 지금 타세요 */
    RIDE,

    /** 다음 열차를 기다리세요 */
    WAIT,

    /** 판단할 근거가 없습니다 */
    NO_DATA,
}

/** 후보 열차 하나 — 도착 정보 + 그 열차의 예상 혼잡도. */
data class TrainOption(
    val arrival: Arrival,
    val crowd: CrowdIndex,
)

/**
 * 지금 열차와 다음 열차를 비교한 결과. HellStation의 핵심 산출물입니다.
 *
 * @param reason      왜 이런 결론인지. **화면에 그대로 보여줄 수 있는 문구**
 * @param waitSeconds WAIT일 때 얼마나 더 기다려야 하나 (다음 열차까지)
 * @param confidence  이 판단의 신뢰도. current/next 중 낮은 쪽을 따릅니다
 * @param rule        어떤 규칙으로 결정됐는지. 로그·디버깅용이며 화면에는 쓰지 마세요
 */
data class RideOrWait(
    val verdict: Verdict,
    val current: TrainOption?,
    val next: TrainOption?,
    val reason: String,
    val waitSeconds: Int?,
    val confidence: Confidence,
    val rule: DecisionRule,
) {
    companion object {
        fun noData(reason: String) = RideOrWait(
            verdict = Verdict.NO_DATA,
            current = null,
            next = null,
            reason = reason,
            waitSeconds = null,
            confidence = Confidence.LOW,
            rule = DecisionRule.NO_CURRENT_TRAIN,
        )
    }
}

/**
 * 어떤 규칙이 결론을 냈는지. docs/data-model.md 10절의 판단 규칙과 1:1로 대응합니다.
 * 검토 담당이 "왜 이런 결론이 나왔나"를 추적할 수 있도록 남깁니다.
 */
enum class DecisionRule {
    /** 1. 지금 열차 정보가 없음 */
    NO_CURRENT_TRAIN,

    /** 2. 다음 열차 정보가 없음 → RIDE */
    NO_NEXT_TRAIN,

    /** 8. 막차 → 무조건 RIDE */
    LAST_TRAIN,

    /** 7. 근거가 약함 → RIDE로 기움 */
    LOW_CONFIDENCE,

    /** 3. 두 열차 등급이 같음 → RIDE */
    SAME_LEVEL,

    /** 4. 다음 열차가 두 등급 이상 낮음 → WAIT */
    NEXT_MUCH_BETTER,

    /** 5. 한 등급 낮고 대기 시간이 짧음 → WAIT */
    NEXT_BETTER_SHORT_WAIT,

    /** 4의 반대. 두 등급 이상 낮지만 그마저도 못 기다릴 만큼 오래 걸림 → RIDE */
    NEXT_MUCH_BETTER_LONG_WAIT,

    /** 5의 반대. 한 등급 낮지만 너무 오래 기다려야 함 → RIDE */
    NEXT_BETTER_LONG_WAIT,

    /** 6. 다음 열차가 더 나쁘거나 같음 → RIDE */
    NEXT_NOT_BETTER,
}
