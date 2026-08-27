package com.hellstation.data.local.cache

import com.hellstation.data.remote.mapper.StationNameNormalizer
import com.hellstation.domain.model.LineId
import com.hellstation.domain.model.RealtimeStationId
import com.hellstation.domain.model.Station
import com.hellstation.domain.model.StationId

/**
 * 역 목록과 조회용 색인.
 *
 * 앱 시작 시 한 번 만들어 계속 씁니다. 역 목록은 잘 변하지 않으므로
 * 매번 API를 부를 이유가 없습니다.
 *
 * 도착정보 API의 [RealtimeStationId]와 앱의 [StationId]는 체계가 달라서
 * 이 색인이 둘을 이어 줍니다(docs/data-model.md 4절).
 */
class StationCatalog(val stations: List<Station>) {

    private val byId: Map<StationId, Station> = stations.associateBy { it.id }

    /** (노선, 정규화 이름) -> 역. 실시간 ID를 앱 ID로 바꿀 때 씁니다. */
    private val byLineAndName: Map<Pair<LineId, String>, Station> =
        stations.associateBy { it.id.line to it.name }

    /** 정규화 이름 -> 같은 이름의 역들(환승역이면 여러 개). */
    private val byName: Map<String, List<Station>> = stations.groupBy { it.name }

    val isEmpty: Boolean get() = stations.isEmpty()

    val mappable: List<Station> by lazy { stations.filter { it.isMappable } }

    val lines: List<LineId> by lazy {
        stations.map { it.id.line }.distinct().sortedBy { it.ordinal }
    }

    fun station(id: StationId): Station? = byId[id]

    fun stationsOf(line: LineId): List<Station> = stations.filter { it.id.line == line }

    fun stationsNamed(name: String): List<Station> =
        byName[StationNameNormalizer.normalize(name)].orEmpty()

    /**
     * 실시간 역 ID를 앱의 [StationId]로 바꿉니다.
     *
     * 실시간 ID 앞 4자리에서 노선을 얻고, 역명으로 맞춥니다.
     * 매칭에 실패하면 null — 호출한 쪽이 임시 ID를 만들거나 건너뜁니다.
     */
    fun resolve(realtimeId: RealtimeStationId, normalizedName: String): StationId? {
        val line = realtimeId.line ?: return null
        return byLineAndName[line to normalizedName]?.id
    }

    /**
     * 검색. 정규화 이름과 표시 이름 양쪽을 봅니다.
     * 이름이 정확히 맞는 역을 앞에, 부분 일치를 뒤에 둡니다.
     */
    fun search(query: String, limit: Int = 30): List<Station> {
        val q = StationNameNormalizer.normalize(query)
        if (q.isBlank()) return emptyList()

        val exact = byName[q].orEmpty()
        if (exact.size >= limit) return exact.take(limit)

        val partial = stations.asSequence()
            .filter { it.name != q }
            .filter { it.name.contains(q) || it.displayName.contains(query.trim()) }
            .sortedWith(compareBy({ it.name.length }, { it.name }))
            .take(limit - exact.size)
            .toList()

        return exact + partial
    }

    /**
     * 인접 역 추정용. 같은 노선에서 좌표상 가장 가까운 역들을 찾습니다.
     *
     * 노선의 역 순서를 실시간 API의 `statnFid`/`statnTid`로 이어붙이는 것이 정확하지만,
     * 그러려면 노선 전체를 훑어야 합니다. 좌표 거리로 근사합니다.
     */
    fun nearestOnSameLine(station: Station, count: Int = 2): List<Station> {
        val origin = station.location ?: return emptyList()
        return stationsOf(station.id.line)
            .asSequence()
            .filter { it.id != station.id && it.location != null }
            .sortedBy { other ->
                val loc = other.location!!
                val dLat = loc.latitude - origin.latitude
                val dLng = loc.longitude - origin.longitude
                dLat * dLat + dLng * dLng
            }
            .take(count)
            .toList()
    }

    companion object {
        val EMPTY = StationCatalog(emptyList())
    }
}
