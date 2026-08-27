package com.hellstation.data.local.cache

import com.hellstation.domain.model.Direction
import com.hellstation.domain.model.LineId
import com.hellstation.domain.model.Segment
import com.hellstation.domain.model.Station
import com.hellstation.domain.model.StationId
import com.hellstation.domain.model.StationProfile

/**
 * 역 목록에서 **노선의 모양**을 뽑아냅니다. 역 순서, 구간, 그리고 역별 특징.
 *
 * ## 순서를 어떻게 알아내나
 *
 * 도착정보 API의 `statnFid`/`statnTid`(이전/다음 역)로 이어붙이는 것이 가장 정확하지만,
 * 그러려면 노선의 모든 역을 한 번씩 조회해야 하고 정식 인증키가 필요합니다
 * (docs/api-validation.md — 샘플 키는 한 번에 5건 제한).
 *
 * 대신 **역 번호가 노선을 따라 차례로 매겨져 있다**는 성질을 씁니다.
 * 1호선 서울역 150 → 시청 151 → 종각 152 처럼요.
 * 안내판 번호(`frCode`)가 있으면 그걸 먼저 쓰고, 없으면 역 코드를 씁니다.
 *
 * **완벽하지 않습니다.** 지선이 있는 노선(2호선 성수지선, 5호선 마천지선 등)은
 * 순서가 어긋날 수 있습니다. 그래서 이걸로 만든 값은 전부 어림값으로 표시됩니다.
 */
class NetworkTopology(
    private val catalog: StationCatalog,
    /**
     * 노선이 원래 얼마나 붐비는가 (1.0 = 보통).
     *
     * 어림 계산에 쓰는 값이라 기본은 "노선 차이 없음"입니다.
     * 실제 값은 [com.hellstation.data.local.baseline.ApproximateBaselineSource]가 넘겨줍니다 —
     * 노선별 성향은 그쪽의 어림 규칙이지 노선도의 성질이 아니기 때문입니다.
     */
    private val lineWeight: (LineId) -> Double = { 1.0 },
) {

    /** 노선별로 정렬된 역 목록. */
    private val orderedByLine: Map<LineId, List<Station>> by lazy {
        catalog.stations
            .groupBy { it.id.line }
            .mapValues { (_, stations) -> stations.sortedWith(ORDER) }
    }

    /**
     * 노선마다 "도심에 해당하는 지점"의 순번.
     *
     * 환승 노선이 가장 많은 역을 도심으로 봅니다. 실제로 서울에서 환승이 많이 몰리는 역은
     * 대체로 도심이거나 큰 부도심입니다. 동점이면 노선 한가운데에 가까운 쪽을 택합니다.
     */
    private val coreIndexByLine: Map<LineId, Int> by lazy {
        orderedByLine.mapValues { (_, stations) ->
            if (stations.isEmpty()) {
                0
            } else {
                val middle = stations.size / 2
                stations.indices.maxWithOrNull(
                    // 1순위: 환승 노선이 많은 역. 2순위: 노선 한가운데에 가까운 역.
                    compareBy<Int> { stations[it].transferLines.size }
                        .thenBy { -kotlin.math.abs(it - middle) }
                ) ?: middle
            }
        }
    }

    /**
     * 역별 "붐빌 만한 정도"의 **네트워크 전체 순위**.
     *
     * 환승 수·도심 거리·노선 성향을 섞어 점수를 낸 다음, 그 점수를 순위로 바꿉니다.
     * 순위로 바꾸는 이유는 [StationProfile]의 설명을 보세요 — 한 줄로 요약하면
     * **점수를 그대로 쓰면 지도가 단색이 되기 때문**입니다.
     */
    private val busynessPercentiles: Map<StationId, Float> by lazy {
        val stations = catalog.stations
        if (stations.size < 2) {
            return@lazy stations.associate { it.id to 0.5f }
        }

        val weights = stations.map { lineWeight(it.id.line) }
        val minWeight = weights.min()
        val maxWeight = weights.max()
        val weightSpan = (maxWeight - minWeight).takeIf { it > 0.0 }

        val scores = stations.associate { station ->
            val profile = rawProfileOf(station)
            val hub = (minOf(profile.transferCount, MAX_COUNTED_TRANSFERS).toDouble()
                / MAX_COUNTED_TRANSFERS)
            val central = profile.centrality.toDouble()
            val line = weightSpan
                ?.let { (lineWeight(station.id.line) - minWeight) / it }
                ?: 0.5

            station.id to (HUB_SHARE * hub + CENTRAL_SHARE * central + LINE_SHARE * line)
        }

        // 점수 순으로 세워서 0~1 을 고르게 나눠 줍니다.
        val ranked = scores.entries.sortedBy { it.value }
        val last = (ranked.size - 1).toFloat()
        ranked.withIndex().associate { (index, entry) -> entry.key to index / last }
    }

    private val profiles: Map<StationId, StationProfile> by lazy {
        buildMap {
            for (station in catalog.stations) {
                put(
                    station.id,
                    rawProfileOf(station).copy(
                        busynessPercentile = busynessPercentiles[station.id] ?: 0.5f,
                    ),
                )
            }
        }
    }

    /** 순위를 매기기 전의 특징. 순위 계산 자체가 이걸 써야 해서 따로 뽑아 두었습니다. */
    private fun rawProfileOf(station: Station): StationProfile {
        val stations = orderedByLine[station.id.line].orEmpty()
        val index = stations.indexOfFirst { it.id == station.id }.coerceAtLeast(0)
        val core = coreIndexByLine[station.id.line] ?: (stations.size / 2)
        return StationProfile(
            stationId = station.id,
            transferCount = station.transferLines.size,
            indexOnLine = index,
            lineLength = stations.size.coerceAtLeast(1),
            coreIndexOnLine = core,
        )
    }

    private val segmentsByLine: Map<LineId, List<Segment>> by lazy {
        orderedByLine.mapValues { (line, stations) ->
            buildSegments(line, stations)
        }
    }

    val isEmpty: Boolean get() = catalog.isEmpty

    fun orderedStations(line: LineId): List<Station> = orderedByLine[line].orEmpty()

    fun profileOf(id: StationId): StationProfile? = profiles[id]

    fun segmentsOf(line: LineId): List<Segment> = segmentsByLine[line].orEmpty()

    /**
     * 어떤 역 다음에 오는 역. 방향에 따라 다릅니다.
     * 구간 혼잡도를 계산할 때 "이 구간이 어디서 어디로 가는지" 알아내는 데 씁니다.
     */
    fun neighbor(id: StationId, direction: Direction): Station? {
        val stations = orderedByLine[id.line] ?: return null
        val index = stations.indexOfFirst { it.id == id }
        if (index < 0) return null
        // Direction.DOWN 을 "순번이 커지는 쪽"으로 봅니다(StationProfile 과 같은 약속).
        val step = if (direction == Direction.DOWN) 1 else -1
        val target = index + step
        return when {
            target in stations.indices -> stations[target]
            // 순환선은 끝에서 처음으로 이어집니다.
            id.line.isLoop && stations.isNotEmpty() ->
                stations[(target + stations.size) % stations.size]

            else -> null
        }
    }

    private fun buildSegments(line: LineId, stations: List<Station>): List<Segment> {
        if (stations.size < 2) return emptyList()
        val result = ArrayList<Segment>((stations.size - 1) * 2)

        fun connect(from: Station, to: Station) {
            // 구간은 방향이 있습니다. (A->B)와 (B->A)는 다른 구간입니다.
            result += Segment(line, from.id, to.id, Direction.DOWN)
            result += Segment(line, to.id, from.id, Direction.UP)
        }

        for (index in 0 until stations.size - 1) {
            connect(stations[index], stations[index + 1])
        }
        if (line.isLoop) {
            connect(stations.last(), stations.first())
        }
        return result
    }

    private companion object {
        /** 환승 노선이 이보다 많아도 더 붐빈다고 보지 않습니다. */
        const val MAX_COUNTED_TRANSFERS = 4

        // 붐빌 만한 정도를 이루는 세 가지의 비중. 합이 1이 되게 둡니다.
        const val HUB_SHARE = 0.40
        const val CENTRAL_SHARE = 0.35
        const val LINE_SHARE = 0.25

        /**
         * 역을 노선 순서대로 세우는 기준.
         *
         * 안내판 번호(P148, 151)에서 숫자만 뽑아 씁니다. 없으면 역 코드의 숫자를,
         * 그것도 없으면 문자열 순서를 씁니다.
         */
        val ORDER: Comparator<Station> = compareBy(
            { it.frCode?.digitsOrNull() ?: it.id.stationCode.digitsOrNull() ?: Int.MAX_VALUE },
            { it.id.stationCode },
        )

        fun String.digitsOrNull(): Int? =
            filter { it.isDigit() }.takeIf { it.isNotEmpty() }?.toIntOrNull()
    }
}
