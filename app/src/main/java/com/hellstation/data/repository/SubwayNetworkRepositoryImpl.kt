package com.hellstation.data.repository

import com.hellstation.data.local.cache.NetworkTopology
import com.hellstation.data.local.cache.StationCatalog
import com.hellstation.data.local.cache.StationCatalogStore
import com.hellstation.data.remote.ApiResult
import com.hellstation.data.remote.SeoulSubwayApi
import com.hellstation.data.remote.mapper.StationCatalogMapper
import com.hellstation.domain.model.LineId
import com.hellstation.domain.model.Loadable
import com.hellstation.domain.model.Segment
import com.hellstation.domain.model.Station
import com.hellstation.domain.model.StationId
import com.hellstation.domain.model.StationProfile
import com.hellstation.domain.model.UnavailableReason
import com.hellstation.domain.repository.SubwayNetworkRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 역 목록을 관리합니다.
 *
 * 순서: **메모리 → 기기 저장소 → 네트워크 → 씨앗 목록**.
 *
 * ## 씨앗 목록(seed)이 왜 있나
 *
 * 실시간 전용 인증키가 없으면 서울 열린데이터광장이 **한 번에 5건만** 돌려줍니다
 * (docs/api-validation.md — 실측으로 확인). 역 다섯 개짜리 지도는 아무 쓸모가 없습니다.
 *
 * 그래서 밖에서 역 목록을 넣어 줄 수 있게 열어 두었습니다. 인증키가 없는 동안에는
 * 그 목록으로 지도와 혼잡도 계산이 **정상적으로 돌아갑니다**. 키가 생기면 실제 목록이
 * 씨앗을 밀어냅니다.
 *
 * 씨앗은 [Station] 목록일 뿐이라 이 계층이 화면 코드를 알 필요가 없습니다.
 */
class SubwayNetworkRepositoryImpl(
    private val api: SeoulSubwayApi,
    private val store: StationCatalogStore,
    private val seedStations: List<Station> = emptyList(),
    /**
     * 노선별 혼잡 성향. 역 순위를 매기는 데 씁니다.
     * 기본은 "노선 차이 없음"이고, 실제 값은 어림 통계 소스가 넘겨줍니다.
     */
    private val lineWeight: (LineId) -> Double = { 1.0 },
) : SubwayNetworkRepository {

    private val mutex = Mutex()

    @Volatile
    private var cached: StationCatalog = StationCatalog.EMPTY

    @Volatile
    private var cachedTopology: NetworkTopology = NetworkTopology(StationCatalog.EMPTY, lineWeight)

    @Volatile
    private var usingSeed: Boolean = false

    /** 다른 저장소가 역 색인을 함께 쓰기 위한 접근자. */
    val catalog: StationCatalog get() = cached

    /** 노선 순서·구간·역 특징. 혼잡도 어림 계산이 이걸 씁니다. */
    val topology: NetworkTopology get() = cachedTopology

    /** 지금 씨앗 목록으로 돌아가고 있는가. 화면에 안내를 띄울 때 씁니다. */
    val isUsingSeed: Boolean get() = usingSeed

    override suspend fun warmUp(): Loadable<Unit> = mutex.withLock {
        if (!cached.isEmpty) return@withLock Loadable.Ready(Unit, isFallback = usingSeed)

        // 1. 기기에 저장된 목록
        val stored = store.load()?.takeIf { it.stations.size >= MIN_USEFUL_STATIONS }
        if (stored != null) {
            adopt(stored, fromSeed = false)
            return@withLock Loadable.Ready(Unit)
        }

        // 2. 네트워크
        val masterRows = fetchPaged { start, end -> api.stationMaster(start, end) }
        val lineInfoRows = fetchPaged { start, end -> api.stationLineInfo(start, end) }
        val fetched = if (masterRows.isEmpty() && lineInfoRows.isEmpty()) {
            emptyList()
        } else {
            StationCatalogMapper.merge(masterRows, lineInfoRows).stations
        }

        if (fetched.size >= MIN_USEFUL_STATIONS) {
            val catalog = StationCatalog(fetched)
            adopt(catalog, fromSeed = false)
            store.save(catalog)
            return@withLock Loadable.Ready(Unit)
        }

        // 3. 씨앗 목록. 실패가 아니라 "덜 좋은 상태"입니다.
        if (seedStations.isNotEmpty()) {
            adopt(StationCatalog(seedStations), fromSeed = true)
            return@withLock Loadable.Ready(Unit, isFallback = true)
        }

        // 4. 정말 아무것도 없을 때만 실패. 받은 게 조금이라도 있으면 그거라도 씁니다.
        if (fetched.isNotEmpty()) {
            adopt(StationCatalog(fetched), fromSeed = false)
            return@withLock Loadable.Ready(Unit, isFallback = true)
        }

        Loadable.Unavailable(
            UnavailableReason.NETWORK,
            "역 목록을 받지 못했습니다. 네트워크를 확인해 주세요.",
        )
    }

    override suspend fun allLines(): List<LineId> = ensure().lines

    override suspend fun stationsOf(line: LineId): List<Station> =
        ensure().let { cachedTopology.orderedStations(line).ifEmpty { it.stationsOf(line) } }

    override suspend fun mappableStations(): List<Station> = ensure().mappable

    /**
     * 구간 목록.
     *
     * 역 번호 순서로 이어 만듭니다([NetworkTopology] 참고). 지선이 있는 노선은
     * 순서가 어긋날 수 있으므로, 이걸로 만든 값은 어림값으로 표시됩니다.
     */
    override suspend fun segmentsOf(line: LineId): List<Segment> {
        ensure()
        return cachedTopology.segmentsOf(line)
    }

    override suspend fun findStationsByName(query: String): List<Station> = ensure().search(query)

    override suspend fun station(id: StationId): Station? = ensure().station(id)

    override suspend fun stationsNamed(name: String): List<Station> = ensure().stationsNamed(name)

    /** 역의 특징. 통계가 없는 역의 혼잡도를 어림할 때 씁니다. */
    suspend fun profileOf(id: StationId): StationProfile? {
        ensure()
        return cachedTopology.profileOf(id)
    }

    // ── 내부 ────────────────────────────────────────────────────────────────

    private fun adopt(catalog: StationCatalog, fromSeed: Boolean) {
        cached = catalog
        cachedTopology = NetworkTopology(catalog, lineWeight)
        usingSeed = fromSeed
    }

    private suspend fun ensure(): StationCatalog {
        if (cached.isEmpty) warmUp()
        return cached
    }

    /**
     * 페이지를 나눠 전부 받습니다.
     *
     * 한 페이지라도 실패하면 **거기서 멈추고 지금까지 받은 것을 돌려줍니다.**
     * 절반이라도 있는 편이 아무것도 없는 것보다 낫습니다.
     */
    private suspend fun <T> fetchPaged(
        fetch: suspend (start: Int, end: Int) -> ApiResult<List<T>>,
    ): List<T> {
        val pageSize = api.catalogPageSize
        val collected = ArrayList<T>()
        var start = 1
        var page = 0

        while (page < MAX_PAGES) {
            val end = start + pageSize - 1
            when (val result = fetch(start, end)) {
                is ApiResult.Failure -> return collected
                is ApiResult.Success -> {
                    collected += result.value
                    if (result.value.size < pageSize) return collected
                    start = end + 1
                    page++
                }
            }
        }
        return collected
    }

    private companion object {
        /** 무한 루프 방지. 1000건씩 받으면 1페이지로 끝납니다. */
        const val MAX_PAGES = 20

        /**
         * 이보다 적으면 "역 목록을 받았다"고 치지 않습니다.
         * 샘플 키는 5건만 주는데, 역 다섯 개짜리 지도는 없는 것과 같습니다.
         */
        const val MIN_USEFUL_STATIONS = 50
    }
}
