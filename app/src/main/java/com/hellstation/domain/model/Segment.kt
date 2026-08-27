package com.hellstation.domain.model

/**
 * 인접한 두 역 사이의 한 방향 구간. 방향이 있는 간선입니다.
 * (A→B)와 (B→A)는 서로 다른 Segment입니다.
 *
 * Heatmap에서 역과 역을 잇는 선 하나가 Segment 하나입니다.
 *
 * ## 구간 혼잡도는 어떻게 정하나
 *
 * 서울교통공사 CSV는 **역 단위** 값만 줍니다. 구간 값은 [from] 역의 해당 방향
 * 혼잡도를 그대로 씁니다 — 그 역에서 탄 사람들이 이 구간을 타고 가기 때문입니다.
 */
data class Segment(
    val line: LineId,
    val from: StationId,
    val to: StationId,
    val direction: Direction,
    /** 이 구간 통과에 걸리는 평균 시간(초). 실측이 없으면 null */
    val travelSeconds: Int? = null,
) {
    val key: String get() = "${from.key}>${to.key}"

    /** 두 역이 같은 노선인가. 생성 시 예외를 던지는 대신 확인용으로 둡니다. */
    val isConsistent: Boolean get() = from.line == line && to.line == line
}
