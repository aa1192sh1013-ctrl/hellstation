package com.hellstation.domain.model

/**
 * 운행 상태. 통계 기준선이 통하지 않는 상황을 표시합니다.
 *
 * 도착정보 API는 "지연"이라는 전용 필드를 주지 않습니다. 그래서 아래 두 가지로 추정합니다.
 * 1. `arvlMsg2`에 지연·중단 관련 문구가 들어 있는가
 * 2. 관측된 배차 간격이 그 시간대 정상 간격보다 훨씬 긴가
 *
 * **추정이라는 점을 잊지 마세요.** 이 상태가 [NORMAL]이 아니면 혼잡도 신뢰도를 한 단계 낮춥니다.
 */
enum class ServiceStatus {
    NORMAL,

    /** 배차가 평소보다 벌어졌거나 지연 문구가 감지됨 */
    DELAYED,

    /** 운행 중단으로 보임 — 해당 방향 열차가 아예 없음 */
    SUSPENDED,

    /** 운행 시간 밖 (첫차 전 / 막차 후) */
    CLOSED,
    ;

    val isNormal: Boolean get() = this == NORMAL

    /** 이 상태에서 장난스러운 문구를 써도 되는가. docs/crowding-levels.md 4절. */
    val allowsPlayfulCopy: Boolean get() = this == NORMAL
}
