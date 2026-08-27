package com.hellstation.domain.model

/**
 * 역 하나의 "붐빌 만한 정도"를 설명하는 특징들.
 *
 * ## 왜 필요한가
 *
 * 서울교통공사 실측 통계(CSV)가 없는 역 — 9호선·신분당선·공항철도 전체, 그리고 CSV를
 * 아직 넣지 않은 상태의 모든 역 — 은 시간대 곡선 하나만으로 값을 만들어야 합니다.
 *
 * 그런데 곡선 하나만 쓰면 **모든 역이 같은 값**이 되어 지도가 통째로 한 가지 색이 됩니다.
 * 그러면 "어디가 지옥인가"를 보여주는 앱의 존재 이유가 사라집니다.
 *
 * 그래서 실제로 알 수 있는 특징으로 역마다 값을 벌립니다. 여기 있는 것들은 전부
 * 역 목록에서 **실제로 관측 가능한** 값이지 지어낸 것이 아닙니다.
 *
 * @param transferCount   환승 가능한 다른 노선 수. 많을수록 사람이 몰립니다
 * @param indexOnLine     노선에서 몇 번째 역인가 (0부터)
 * @param lineLength      그 노선의 역 수
 * @param coreIndexOnLine 그 노선에서 가장 도심에 가까운 역의 순번.
 *   "도심 방향"이 어느 쪽인지 판단하는 기준점입니다
 * @param busynessPercentile 네트워크 전체에서 이 역이 몇 번째로 붐빌 만한가.
 *   0=가장 한산, 1=가장 붐빔. [com.hellstation.data.local.cache.NetworkTopology]가 매깁니다
 *
 * ## 왜 "비율"이 아니라 "순위"인가
 *
 * 환승 수와 도심 거리를 그냥 섞어 점수를 내면 값이 좁은 구간에 몰립니다.
 * 이 앱의 역 목록으로 실제로 재 보니 0.04~0.80 사이였고 대부분은 0.25~0.45에 뭉쳐 있었습니다.
 *
 * 그 점수를 혼잡도에 곱하면 **역들이 거의 같은 값**이 되어 지도가 단색이 됩니다.
 * 순위로 바꾸면 0~1이 고르게 채워져서 지도에 항상 여러 색이 나옵니다.
 */
data class StationProfile(
    val stationId: StationId,
    val transferCount: Int,
    val indexOnLine: Int,
    val lineLength: Int,
    val coreIndexOnLine: Int,
    val busynessPercentile: Float = 0.5f,
) {
    /** 노선 끝(종점)에서 얼마나 떨어져 있나. 0=종점, 1=한가운데. */
    val distanceFromEnds: Float
        get() {
            if (lineLength <= 1) return 1f
            val fromStart = indexOnLine
            val fromEnd = lineLength - 1 - indexOnLine
            val nearest = minOf(fromStart, fromEnd)
            return (nearest.toFloat() / (lineLength / 2f)).coerceIn(0f, 1f)
        }

    /** 노선의 도심 지점에 얼마나 가까운가. 1=도심, 0=가장 먼 끝. */
    val centrality: Float
        get() {
            if (lineLength <= 1) return 0.5f
            val distance = kotlin.math.abs(indexOnLine - coreIndexOnLine).toFloat()
            val worst = maxOf(coreIndexOnLine, lineLength - 1 - coreIndexOnLine).toFloat()
            if (worst <= 0f) return 1f
            return (1f - distance / worst).coerceIn(0f, 1f)
        }

    /**
     * 이 방향이 도심으로 **들어가는** 방향인가.
     *
     * 노선의 역 순서에서 도심 지점([coreIndexOnLine])이 어느 쪽에 있는지로 판단합니다.
     * 도심보다 바깥에 있는 역이라면 순번이 커지는 쪽이 도심 방향이고, 안쪽이면 반대입니다.
     *
     * 출근 시간에는 도심 방향이, 퇴근 시간에는 바깥 방향이 붐빕니다.
     * 이 구분이 없으면 상행과 하행이 똑같이 나와서 방향 선택이 의미를 잃습니다.
     *
     * 도심 역 자신(양쪽에서 사람이 들어오고 나감)은 null입니다.
     */
    fun isInbound(direction: Direction): Boolean? = when {
        indexOnLine == coreIndexOnLine -> null
        // Direction.DOWN 을 "순번이 커지는 쪽"으로 봅니다.
        // (역 코드가 노선을 따라 차례로 매겨져 있다는 성질을 이용합니다)
        indexOnLine < coreIndexOnLine -> direction == Direction.DOWN
        else -> direction == Direction.UP
    }

    companion object {
        /** 특징을 전혀 모를 때. 모든 보정이 1배가 되어 곡선 값 그대로 나옵니다. */
        fun unknown(stationId: StationId) = StationProfile(
            stationId = stationId,
            transferCount = 0,
            indexOnLine = 0,
            lineLength = 1,
            coreIndexOnLine = 0,
        )
    }
}
