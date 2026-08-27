package com.hellstation.domain.model

import java.time.Instant

/** 도착정보 API의 `arvlCd`. 숫자를 그대로 쓰지 않고 의미를 붙입니다. */
enum class ArrivalState {
    ENTERING,       // 0  진입
    ARRIVED,        // 1  도착
    DEPARTED,       // 2  출발
    PREV_DEPARTED,  // 3  전역 출발
    PREV_ENTERING,  // 4  전역 진입
    PREV_ARRIVED,   // 5  전역 도착
    RUNNING,        // 99 운행중
    UNKNOWN,
    ;

    /** 이미 이 역에 와 있는 상태인가. */
    val isAtStation: Boolean get() = this == ENTERING || this == ARRIVED

    /**
     * 이 역을 이미 떠난 열차인가.
     *
     * 실제 응답에서 `arvlCd=2`, `arvlMsg2="서울 출발"`인 건이 목록에 섞여 옵니다.
     * **떠난 열차는 탈 수 없으므로 "다음 열차" 후보에서 빼야 합니다.**
     * 빼지 않으면 이미 지나간 열차를 "지금 타세요"라고 안내하게 됩니다.
     *
     * [PREV_DEPARTED](전역 출발)와 헷갈리지 마세요 — 그건 이쪽으로 오고 있는 열차입니다.
     */
    val hasLeftStation: Boolean get() = this == DEPARTED

    companion object {
        fun parse(raw: String?): ArrivalState = when (raw?.trim()) {
            "0" -> ENTERING
            "1" -> ARRIVED
            "2" -> DEPARTED
            "3" -> PREV_DEPARTED
            "4" -> PREV_ENTERING
            "5" -> PREV_ARRIVED
            "99" -> RUNNING
            else -> UNKNOWN
        }
    }
}

/**
 * "이 역에 이 열차가 언제 온다"는 한 건.
 *
 * @param rawSecondsUntilArrival API가 준 `barvlDt` 원본. **"0"이 자주 오므로 그대로 믿으면 안 됩니다**
 * @param observedAt             `recptnDt`. 이 정보가 만들어진 시각
 * @param message                `arvlMsg2`. "3분 후 (시청)" 같은 표시 문구
 */
data class Arrival(
    val station: StationId,
    val train: Train,
    val state: ArrivalState,
    val rawSecondsUntilArrival: Int,
    val observedAt: Instant,
    val message: String,
) {
    /** 이 정보가 만들어진 지 몇 초 지났나. 신뢰도 판정의 입력입니다. */
    fun dataAgeSeconds(now: Instant): Long =
        (now.epochSecond - observedAt.epochSecond).coerceAtLeast(0L)

    /**
     * 지연을 보정한 실제 남은 시간(초). 믿을 수 없으면 null.
     *
     * docs/api-validation.md 1번 함정 두 가지를 여기서 처리합니다.
     * 1. `barvlDt == 0 && state == RUNNING` 이면 "정보 없음"입니다 (0초 후 도착이 아님)
     * 2. `recptnDt`가 최대 몇 분씩 밀려 오므로 그만큼 빼야 합니다
     */
    fun secondsUntilArrival(now: Instant): Int? {
        if (state.isAtStation) return 0
        if (rawSecondsUntilArrival <= 0) {
            // 이미 도착/출발 상태가 아닌데 0이면 API가 값을 못 준 것입니다.
            return null
        }
        val corrected = rawSecondsUntilArrival - dataAgeSeconds(now).toInt()
        return corrected.coerceAtLeast(0)
    }

    /** 남은 시간을 알 수 있는 도착 건인가. */
    fun hasUsableEta(now: Instant): Boolean = secondsUntilArrival(now) != null

    /** 지금 이 역에서 탈 수 있는 열차인가. 이미 떠난 열차는 후보가 아닙니다. */
    val isBoardable: Boolean get() = !state.hasLeftStation
}
