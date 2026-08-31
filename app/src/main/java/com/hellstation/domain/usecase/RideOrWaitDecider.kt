package com.hellstation.domain.usecase

import com.hellstation.domain.model.Confidence
import com.hellstation.domain.model.CrowdLevel
import com.hellstation.domain.model.DecisionRule
import com.hellstation.domain.model.RideOrWait
import com.hellstation.domain.model.TrainOption
import com.hellstation.domain.model.Verdict
import java.time.Instant
import kotlin.math.roundToInt

/**
 * 지금 열차와 다음 열차를 비교해 RIDE / WAIT를 결정합니다.
 *
 * 판단 규칙은 docs/data-model.md 10절 그대로입니다. 순서대로 검사하고 먼저 맞는 것을 채택합니다.
 *
 * ## 설계 원칙
 *
 * **근거가 약할 때는 RIDE로 기웁니다.** 확신도 없으면서 사용자를 플랫폼에 세워 두는 것이
 * 잘못된 조언 중에서도 가장 나쁩니다. 기다렸는데 다음 열차가 더 붐비면 시간만 버립니다.
 */
class RideOrWaitDecider {

    fun decide(
        current: TrainOption?,
        next: TrainOption?,
        now: Instant,
    ): RideOrWait {
        // 규칙 1
        if (current == null) {
            return RideOrWait.noData("지금 들어오는 열차 정보가 없습니다")
        }

        val combinedConfidence = next
            ?.let { current.crowd.confidence.combineWith(it.crowd.confidence) }
            ?: current.crowd.confidence

        val waitSeconds = next?.arrival?.secondsUntilArrival(now)

        // 규칙 8: 막차는 무조건 탑니다
        if (current.arrival.train.isLastTrain) {
            return ride(
                current, next, waitSeconds, combinedConfidence,
                DecisionRule.LAST_TRAIN,
                "막차입니다. 지금 타세요.",
            )
        }

        // 규칙 2
        if (next == null) {
            return ride(
                current, null, null, combinedConfidence,
                DecisionRule.NO_NEXT_TRAIN,
                "다음 열차 정보가 없습니다. 지금 타는 편이 낫습니다.",
            )
        }

        // 규칙 7: 근거가 약하면 기다리게 하지 않습니다
        if (combinedConfidence == Confidence.LOW) {
            return ride(
                current, next, waitSeconds, combinedConfidence,
                DecisionRule.LOW_CONFIDENCE,
                "혼잡도 정보가 확실하지 않습니다. 지금 타는 편이 낫습니다.",
            )
        }

        val currentLevel = current.crowd.level
        val nextLevel = next.crowd.level

        // 등급을 모르면 비교할 수 없습니다
        if (!currentLevel.isKnown || !nextLevel.isKnown) {
            return ride(
                current, next, waitSeconds, combinedConfidence,
                DecisionRule.LOW_CONFIDENCE,
                "두 열차의 혼잡도를 비교할 수 없습니다. 지금 타세요.",
            )
        }

        // 규칙 3
        if (nextLevel == currentLevel) {
            return ride(
                current, next, waitSeconds, combinedConfidence,
                DecisionRule.SAME_LEVEL,
                "다음 열차도 ${label(nextLevel)}입니다. 기다릴 이유가 없습니다.",
            )
        }

        val levelGain = currentLevel.ordinal - nextLevel.ordinal

        // 규칙 6: 다음 열차가 더 나쁨
        if (levelGain <= 0) {
            return ride(
                current, next, waitSeconds, combinedConfidence,
                DecisionRule.NEXT_NOT_BETTER,
                "다음 열차가 더 붐빕니다(${label(nextLevel)}). 지금 타세요.",
            )
        }

        // 규칙 4: 두 등급 이상 좋아짐 — 그래도 기다림에는 상한이 있습니다.
        //
        // 상한이 없으면 "두 등급 낫기만 하면 20분도 기다려라"가 됩니다. 막차 직전처럼
        // 배차가 벌어졌을 때 실제로 나오는 상황이고, 기획의 핵심인 "대기 비용 vs 혼잡도 감소"에서
        // 이 분기만 대기 비용을 0으로 치는 셈입니다.
        if (levelGain >= 2) {
            val limit = waitLimitFor(levelGain)
            return if (waitSeconds != null && waitSeconds <= limit) {
                wait(
                    current, next, waitSeconds, combinedConfidence,
                    DecisionRule.NEXT_MUCH_BETTER,
                    "다음 열차는 ${label(nextLevel)}입니다. ${waitPhrase(waitSeconds)} 기다릴 값어치가 있습니다.",
                )
            } else {
                // 등급 차이는 알려 줍니다. 그래야 사용자가 직접 판단할 수 있습니다.
                ride(
                    current, next, waitSeconds, combinedConfidence,
                    DecisionRule.NEXT_MUCH_BETTER_LONG_WAIT,
                    // 등급 이름의 받침에 따라 "으로/로"가 갈립니다.
                    // 손으로 "로"라고 써 두면 "혼잡로 훨씬 낫지만"이 나옵니다.
                    "다음 열차는 ${label(nextLevel)}${toParticle(label(nextLevel))} 훨씬 낫지만 " +
                        // "3분을"은 되지만 "잠시을"은 안 됩니다. 조사를 빼면 둘 다 자연스럽습니다.
                        "${waitPhrase(waitSeconds)} 기다려야 합니다. 지금 타세요.",
                )
            }
        }

        // 규칙 5: 한 등급 좋아짐 — 대기 시간이 짧을 때만
        return if (waitSeconds != null && waitSeconds <= SHORT_WAIT_SECONDS) {
            wait(
                current, next, waitSeconds, combinedConfidence,
                DecisionRule.NEXT_BETTER_SHORT_WAIT,
                "${waitPhrase(waitSeconds)}만 기다리면 ${label(nextLevel)}입니다.",
            )
        } else {
            ride(
                current, next, waitSeconds, combinedConfidence,
                DecisionRule.NEXT_BETTER_LONG_WAIT,
                "다음 열차가 조금 낫지만 ${waitPhrase(waitSeconds)} 넘게 기다려야 합니다. 지금 타세요.",
            )
        }
    }

    // ── 만들기 도우미 ────────────────────────────────────────────────────────

    private fun ride(
        current: TrainOption,
        next: TrainOption?,
        waitSeconds: Int?,
        confidence: Confidence,
        rule: DecisionRule,
        reason: String,
    ) = RideOrWait(Verdict.RIDE, current, next, reason, waitSeconds, confidence, rule)

    private fun wait(
        current: TrainOption,
        next: TrainOption?,
        waitSeconds: Int?,
        confidence: Confidence,
        rule: DecisionRule,
        reason: String,
    ) = RideOrWait(Verdict.WAIT, current, next, reason, waitSeconds, confidence, rule)

    private fun label(level: CrowdLevel): String = when (level) {
        CrowdLevel.EASY -> "여유"
        CrowdLevel.BUSY -> "보통"
        CrowdLevel.BAD -> "혼잡"
        CrowdLevel.HELL -> "지옥"
        CrowdLevel.WTF -> "탑승 불가 수준"
        CrowdLevel.UNKNOWN -> "알 수 없음"
    }

    /**
     * 등급이 이만큼 좋아진다면 몇 초까지 기다릴 만한가.
     *
     * 한 등급에 [SHORT_WAIT_SECONDS]씩 늘려 줍니다 — 한 등급 4분(규칙 5) · 두 등급 8분 ·
     * 세 등급 이상 12분. 등급을 넘게 벌려도 12분에서 멈추는 이유는, 그보다 오래 기다리면
     * **얼마나 나아지든** 지하철을 타러 온 목적 자체가 흐려지기 때문입니다.
     */
    private fun waitLimitFor(levelGain: Int): Int =
        SHORT_WAIT_SECONDS * levelGain.coerceIn(1, MAX_WAIT_STEPS)

    /**
     * 앞말의 받침에 따라 "으로 / 로"를 고릅니다.
     *
     * 값이 문장 가운데 끼어들면 받침이 그때그때 달라져서 손으로 쓸 수 없습니다.
     * 여유(받침 없음)는 "여유로", 혼잡(ㅂ)은 "혼잡으로"가 맞습니다.
     *
     * 한글 음절은 유니코드에서 (초성, 중성, 종성) 순서로 배열돼 있어서,
     * 가(0xAC00)로부터의 거리를 28로 나눈 나머지가 0이면 받침이 없습니다.
     */
    private fun toParticle(word: String): String {
        val last = word.trimEnd().lastOrNull() ?: return "로"
        if (last !in '가'..'힣') return "로"
        return if ((last.code - 0xAC00) % 28 == 0) "로" else "으로"
    }

    private fun waitPhrase(seconds: Int?): String {
        if (seconds == null) return "잠시"
        val minutes = (seconds / 60.0).roundToInt()
        return if (minutes <= 0) "곧" else "${minutes}분"
    }

    companion object {
        /** 이보다 짧게 기다리면 한 등급 개선도 값어치가 있습니다. docs/data-model.md 10절 규칙 5. */
        const val SHORT_WAIT_SECONDS = 240

        /** 등급이 아무리 크게 좋아져도 기다림은 이 배수(= 12분)에서 멈춥니다. */
        const val MAX_WAIT_STEPS = 3
    }
}
