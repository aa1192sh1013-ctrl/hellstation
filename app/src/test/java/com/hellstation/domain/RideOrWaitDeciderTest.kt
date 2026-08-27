package com.hellstation.domain

import com.hellstation.domain.model.Arrival
import com.hellstation.domain.model.ArrivalState
import com.hellstation.domain.model.Confidence
import com.hellstation.domain.model.CrowdIndex
import com.hellstation.domain.model.CrowdSource
import com.hellstation.domain.model.DecisionRule
import com.hellstation.domain.model.Direction
import com.hellstation.domain.model.LineId
import com.hellstation.domain.model.StationId
import com.hellstation.domain.model.Train
import com.hellstation.domain.model.TrainOption
import com.hellstation.domain.model.TrainType
import com.hellstation.domain.model.Verdict
import com.hellstation.domain.usecase.RideOrWaitDecider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * "지금 탈까 기다릴까" 판단 규칙. docs/data-model.md 10절.
 *
 * 핵심 원칙: **근거가 약할 때는 RIDE로 기운다.**
 * 확신도 없이 사용자를 플랫폼에 세워 두는 것이 가장 나쁜 조언입니다.
 */
class RideOrWaitDeciderTest {

    private val decider = RideOrWaitDecider()
    private val now: Instant = Instant.parse("2026-08-24T23:00:00Z")
    private val station = StationId(LineId.LINE_1, "0150")

    private fun option(
        etaSeconds: Int,
        percent: Double,
        confidence: Confidence = Confidence.HIGH,
        isLastTrain: Boolean = false,
        type: TrainType = TrainType.NORMAL,
    ) = TrainOption(
        arrival = Arrival(
            station = station,
            train = Train(
                trainNo = "T$etaSeconds",
                line = LineId.LINE_1,
                direction = Direction.UP,
                type = type,
                destination = "청량리",
                headsign = "청량리행",
                isLastTrain = isLastTrain,
            ),
            state = ArrivalState.RUNNING,
            rawSecondsUntilArrival = etaSeconds,
            observedAt = now,
            message = "곧 도착",
        ),
        crowd = CrowdIndex.of(
            percent = percent,
            confidence = confidence,
            source = CrowdSource.REALTIME_BASELINE,
            at = now,
        ),
    )

    @Test
    fun `지금 열차가 없으면 NO_DATA다`() {
        val result = decider.decide(current = null, next = option(120, 60.0), now = now)
        assertEquals(Verdict.NO_DATA, result.verdict)
        assertEquals(DecisionRule.NO_CURRENT_TRAIN, result.rule)
    }

    @Test
    fun `다음 열차 정보가 없으면 지금 탄다`() {
        val result = decider.decide(option(60, 150.0), null, now)
        assertEquals(Verdict.RIDE, result.verdict)
        assertEquals(DecisionRule.NO_NEXT_TRAIN, result.rule)
    }

    @Test
    fun `막차는 아무리 붐벼도 탄다`() {
        val result = decider.decide(
            current = option(60, 200.0, isLastTrain = true),
            next = option(180, 40.0),
            now = now,
        )
        assertEquals(Verdict.RIDE, result.verdict)
        assertEquals(DecisionRule.LAST_TRAIN, result.rule)
    }

    @Test
    fun `근거가 약하면 기다리게 하지 않는다`() {
        // 다음 열차가 두 등급이나 낫지만 신뢰도가 LOW다
        val result = decider.decide(
            current = option(60, 200.0, confidence = Confidence.LOW),
            next = option(180, 40.0, confidence = Confidence.LOW),
            now = now,
        )
        assertEquals(Verdict.RIDE, result.verdict)
        assertEquals(DecisionRule.LOW_CONFIDENCE, result.rule)
    }

    @Test
    fun `등급이 같으면 기다릴 이유가 없다`() {
        val result = decider.decide(option(60, 100.0), option(180, 120.0), now)
        assertEquals(Verdict.RIDE, result.verdict)
        assertEquals(DecisionRule.SAME_LEVEL, result.rule)
    }

    @Test
    fun `두 등급 이상 나아지면 기다린다`() {
        // WTF(172) -> BAD(120): 두 등급
        val result = decider.decide(option(60, 172.0), option(180, 120.0), now)
        assertEquals(Verdict.WAIT, result.verdict)
        assertEquals(DecisionRule.NEXT_MUCH_BETTER, result.rule)
        assertEquals(180, result.waitSeconds)
    }

    @Test
    fun `두 등급 나아져도 8분을 넘게 기다려야 하면 지금 탄다`() {
        // WTF(172) -> BAD(120): 두 등급이지만 10분 대기.
        // 상한이 없던 시절에는 20분이라도 WAIT였습니다.
        val result = decider.decide(option(60, 172.0), option(600, 120.0), now)
        assertEquals(Verdict.RIDE, result.verdict)
        assertEquals(DecisionRule.NEXT_MUCH_BETTER_LONG_WAIT, result.rule)
        // 얼마나 나은지는 그대로 알려 줘야 사용자가 직접 판단할 수 있습니다.
        assertTrue(result.reason.contains("훨씬"))
    }

    @Test
    fun `등급이 많이 나아질수록 더 오래 기다린다`() {
        // 두 등급(WTF -> BAD)은 8분이 경계
        assertEquals(
            DecisionRule.NEXT_MUCH_BETTER,
            decider.decide(option(60, 172.0), option(480, 120.0), now).rule,
        )
        assertEquals(
            DecisionRule.NEXT_MUCH_BETTER_LONG_WAIT,
            decider.decide(option(60, 172.0), option(481, 120.0), now).rule,
        )

        // 세 등급(WTF -> BUSY)은 12분까지 늘어납니다
        assertEquals(
            DecisionRule.NEXT_MUCH_BETTER,
            decider.decide(option(60, 172.0), option(720, 60.0), now).rule,
        )
        assertEquals(
            DecisionRule.NEXT_MUCH_BETTER_LONG_WAIT,
            decider.decide(option(60, 172.0), option(721, 60.0), now).rule,
        )
    }

    @Test
    fun `한 등급 나아지고 대기가 짧으면 기다린다`() {
        // HELL(150) -> BAD(120), 3분 대기
        val result = decider.decide(option(60, 150.0), option(180, 120.0), now)
        assertEquals(Verdict.WAIT, result.verdict)
        assertEquals(DecisionRule.NEXT_BETTER_SHORT_WAIT, result.rule)
    }

    @Test
    fun `한 등급 나아져도 오래 기다려야 하면 지금 탄다`() {
        // HELL(150) -> BAD(120)이지만 10분 대기
        val result = decider.decide(option(60, 150.0), option(600, 120.0), now)
        assertEquals(Verdict.RIDE, result.verdict)
        assertEquals(DecisionRule.NEXT_BETTER_LONG_WAIT, result.rule)
    }

    @Test
    fun `다음 열차가 더 붐비면 지금 탄다`() {
        val result = decider.decide(option(60, 120.0), option(180, 180.0), now)
        assertEquals(Verdict.RIDE, result.verdict)
        assertEquals(DecisionRule.NEXT_NOT_BETTER, result.rule)
    }

    @Test
    fun `대기 시간 경계는 4분이다`() {
        val exactly = decider.decide(option(60, 150.0), option(240, 120.0), now)
        assertEquals(DecisionRule.NEXT_BETTER_SHORT_WAIT, exactly.rule)

        val overBoundary = decider.decide(option(60, 150.0), option(241, 120.0), now)
        assertEquals(DecisionRule.NEXT_BETTER_LONG_WAIT, overBoundary.rule)
    }

    @Test
    fun `결론에는 항상 사람이 읽을 이유가 붙는다`() {
        val result = decider.decide(option(60, 150.0), option(180, 120.0), now)
        assertEquals(true, result.reason.isNotBlank())
    }
}
