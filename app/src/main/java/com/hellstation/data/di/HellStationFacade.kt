package com.hellstation.data.di

import com.hellstation.data.repository.SubwayNetworkRepositoryImpl
import com.hellstation.domain.model.CrowdIndex
import com.hellstation.domain.model.CrowdLevel
import com.hellstation.domain.model.DataStatus
import com.hellstation.domain.model.Direction
import com.hellstation.domain.model.HeatmapSnapshot
import com.hellstation.domain.model.LineId
import com.hellstation.domain.model.Loadable
import com.hellstation.domain.model.Segment
import com.hellstation.domain.model.Station
import com.hellstation.domain.model.StationBoard
import com.hellstation.domain.model.StationId
import com.hellstation.domain.model.TimeSlot
import com.hellstation.domain.repository.BaselineSource
import com.hellstation.domain.repository.CrowdRepository
import com.hellstation.domain.usecase.CrowdForecastPoint
import com.hellstation.domain.usecase.DecideRouteUseCase
import com.hellstation.domain.usecase.ForecastCrowdUseCase
import com.hellstation.domain.usecase.GetStationBoardUseCase
import com.hellstation.domain.usecase.RouteAdvice
import com.hellstation.domain.usecase.ServiceCalendar
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

/**
 * 화면이 부를 창구 하나.
 *
 * 화면이 저장소 네 개와 유스케이스 세 개를 직접 조합하지 않도록, **화면이 필요로 하는
 * 모양 그대로** 내놓습니다. 화면 코드에는 혼잡도 계산이나 등급 비교가 한 줄도 없어야 합니다.
 *
 * ## 캐시가 있는 이유
 *
 * Time Slider를 한 칸 움직일 때마다 역 수백 개의 혼잡도를 다시 계산합니다.
 * 슬라이더를 쭉 끌면 초당 수십 번 계산이 일어나므로, 같은 시간대·방향이면
 * 계산 결과를 그대로 재사용합니다.
 *
 * 캐시 키에 실제 시각이 아니라 **시간대(슬롯)**를 쓰는 것이 핵심입니다.
 * 지도는 통계값으로 그리므로 같은 30분 안에서는 값이 같습니다.
 * (실시간 보정은 사용자가 역을 눌렀을 때만 걸립니다 — `CrowdRepositoryImpl` 참고)
 */
class HellStationFacade internal constructor(
    private val network: SubwayNetworkRepositoryImpl,
    private val crowd: CrowdRepository,
    private val baseline: BaselineSource,
    private val stationBoard: GetStationBoardUseCase,
    private val decideRoute: DecideRouteUseCase,
    private val forecastUseCase: ForecastCrowdUseCase,
    private val calendar: ServiceCalendar,
    private val isSampleKey: Boolean,
) {

    private val cacheLock = Mutex()
    private val snapshotCache = LinkedHashMap<SnapshotKey, HeatmapSnapshot>()
    private var dayCurveCache: Pair<CurveKey, List<CrowdLevel>>? = null

    // ── 준비 ────────────────────────────────────────────────────────────────

    /** 역 목록을 채웁니다. 앱을 열 때 한 번 부르세요. */
    suspend fun prepare(): Loadable<Unit> = network.warmUp()

    /** 지금 데이터 품질. 화면 안내 문구를 고르는 데 씁니다. */
    suspend fun status(): DataStatus = DataStatus(
        usingSampleKey = isSampleKey,
        usingSeedStations = network.isUsingSeed,
        hasMeasuredBaseline = baseline.hasMeasuredData(),
        stationCount = network.catalog.stations.size,
    )

    // ── 시각 ────────────────────────────────────────────────────────────────

    /**
     * 지금이 속한 30분 시간대. Time Slider 의 시작 위치입니다.
     *
     * 화면이 시간 계산을 직접 하지 않게 하려고 여기 둡니다 —
     * 자정을 넘긴 운행(24:30까지)과 한국 시간대 처리가 생각보다 까다롭습니다
     * (`ServiceCalendar` 참고).
     */
    fun nowSlot(at: Instant = Instant.now()): TimeSlot = calendar.slotAt(at)

    /** Time Slider 눈금을 실제 시각으로. 오늘 운행일 기준입니다. */
    fun instantFor(slot: TimeSlot, reference: Instant = Instant.now()): Instant =
        calendar.instantForSlot(slot, reference)

    /** 지금 운행 시간인가. 첫차 전·막차 후에는 보여줄 값이 없습니다. */
    fun isInService(at: Instant = Instant.now()): Boolean = calendar.isInService(at)

    // ── 지도 ────────────────────────────────────────────────────────────────

    /** 지도에 찍을 수 있는(좌표가 있는) 역. 좌표가 하나도 없으면 전체를 돌려줍니다. */
    suspend fun mapStations(): List<Station> {
        prepare()
        return network.mappableStations().ifEmpty { network.catalog.stations }
    }

    suspend fun allStations(): List<Station> {
        prepare()
        return network.catalog.stations
    }

    /**
     * 특정 시각의 지도 한 장.
     *
     * @param direction null이면 상행·하행 중 **더 나쁜 쪽**을 보여줍니다.
     *   지도를 보는 사람은 "이 역이 얼마나 힘든가"를 알고 싶어 하지
     *   두 방향의 평균을 알고 싶어 하지 않습니다.
     */
    suspend fun heatmapAt(at: Instant, direction: Direction? = null): HeatmapSnapshot {
        prepare()
        val key = SnapshotKey(calendar.slotAt(at).minutesFromMidnight, calendar.dayTypeAt(at).name, direction)

        cacheLock.withLock { snapshotCache[key] }?.let { cached ->
            // 캐시에는 다른 시각으로 만든 값이 들어 있을 수 있습니다.
            // 값은 같지만 화면이 "언제 값인가"를 표시하므로 시각만 갈아 끼웁니다.
            return cached.copy(at = at)
        }

        val snapshot = crowd.heatmap(at, direction).first()
        cacheLock.withLock {
            if (snapshotCache.size >= MAX_CACHED_SNAPSHOTS) {
                snapshotCache.remove(snapshotCache.keys.first())
            }
            snapshotCache[key] = snapshot
        }
        return snapshot
    }

    /**
     * Time Slider 뒤에 깔 하루치 곡선.
     *
     * 시간대마다 **가장 붐비는 역**의 등급을 씁니다. 평균을 쓰면 곡선이 밋밋해져서
     * 러시아워가 드러나지 않습니다 — 슬라이더를 끌어 보기 전에 "8시가 제일 빨갛네"를
     * 알 수 있게 하는 것이 이 곡선의 목적입니다.
     */
    suspend fun dayCurve(reference: Instant, direction: Direction? = null): List<CrowdLevel> {
        prepare()
        val key = CurveKey(calendar.dayTypeAt(reference).name, direction)
        cacheLock.withLock { dayCurveCache }?.let { (cachedKey, value) ->
            if (cachedKey == key) return value
        }

        val curve = TimeSlot.ALL.map { slot ->
            val at = calendar.instantForSlot(slot, reference)
            heatmapAt(at, direction).entries.values
                .filter { it.level.isKnown }
                .maxByOrNull { it.percent ?: 0.0 }
                ?.level
                ?: CrowdLevel.UNKNOWN
        }
        cacheLock.withLock { dayCurveCache = key to curve }
        return curve
    }

    // ── 역 ──────────────────────────────────────────────────────────────────

    suspend fun station(id: StationId): Station? {
        prepare()
        return network.station(id)
    }

    suspend fun search(query: String): List<Station> {
        prepare()
        return network.findStationsByName(query)
    }

    /** 검색어가 없을 때 보여줄 기본 목록. 환승이 많은 역이 대개 사람들이 찾는 역입니다. */
    suspend fun popularStations(limit: Int = 20): List<Station> {
        prepare()
        val stations = network.catalog.stations.distinctBy { it.name }

        // 환승 수만으로 줄을 세우면 동점이 아주 많고, 동점이면 목록에 담긴 순서
        // (= 노선 번호 순서)가 그대로 나옵니다. 그래서 위쪽이 1호선으로만 채워졌습니다.
        // 붐비는 정도로 한 번 더 갈라 주면 도심 역이 올라오면서 노선도 자연히 섞입니다.
        val busyness = stations.associate { station ->
            station.id to (network.profileOf(station.id)?.busynessPercentile ?: 0f)
        }

        return stations
            .sortedWith(
                compareByDescending<Station> { it.transferLines.size }
                    .thenByDescending { busyness[it.id] ?: 0f },
            )
            .take(limit)
    }

    /** 역 하나의 혼잡도만 필요할 때 (검색 결과 줄 등). */
    suspend fun crowdAt(id: StationId, direction: Direction, at: Instant): CrowdIndex {
        prepare()
        return crowd.crowdAt(id, direction, at)
    }

    /**
     * 역 상세 시트에 필요한 모든 것.
     * **여기서만 실시간 도착정보를 부릅니다** — 사용자가 실제로 그 역을 눌렀을 때만요.
     */
    suspend fun board(
        id: StationId,
        direction: Direction,
        at: Instant = Instant.now(),
    ): Loadable<StationBoard> {
        prepare()
        return stationBoard(id, direction, at)
    }

    // ── 경로 ────────────────────────────────────────────────────────────────

    suspend fun route(
        originId: StationId,
        destinationId: StationId?,
        at: Instant = Instant.now(),
        directionOverride: Direction? = null,
    ): Loadable<RouteAdvice> {
        prepare()
        return decideRoute(originId, destinationId, at, directionOverride)
    }

    // ── Time Slider (역 단위) ───────────────────────────────────────────────

    suspend fun forecast(
        id: StationId,
        direction: Direction,
        reference: Instant = Instant.now(),
    ): List<CrowdForecastPoint> {
        prepare()
        return forecastUseCase(id, direction, reference)
    }

    // ── 구간 ────────────────────────────────────────────────────────────────

    /**
     * 노선의 구간별 혼잡도.
     *
     * 구간 값은 **출발 쪽 역**의 혼잡도를 씁니다. 그 역에서 탄 사람들이 이 구간을 타고
     * 가기 때문입니다(docs/data-model.md 6절).
     *
     * @return 구간 키 -> 혼잡도. 키는 [Segment.key]("1001:0150>1001:0151")
     */
    suspend fun segmentCrowd(
        line: LineId,
        direction: Direction,
        at: Instant,
    ): Map<String, CrowdIndex> {
        prepare()
        val snapshot = heatmapAt(at, direction)
        return network.segmentsOf(line)
            .filter { it.direction == direction }
            .associate { segment ->
                segment.key to (
                    snapshot.entries[segment.from]
                        ?: CrowdIndex.unknown(at)
                    )
            }
    }

    suspend fun segmentsOf(line: LineId): List<Segment> {
        prepare()
        return network.segmentsOf(line)
    }

    // ── 캐시 ────────────────────────────────────────────────────────────────

    /** 역 목록이 바뀌었을 때처럼 계산을 처음부터 다시 해야 할 때. */
    suspend fun invalidate() = cacheLock.withLock {
        snapshotCache.clear()
        dayCurveCache = null
    }

    private data class SnapshotKey(
        val slotMinutes: Int,
        val dayType: String,
        val direction: Direction?,
    )

    private data class CurveKey(
        val dayType: String,
        val direction: Direction?,
    )

    private companion object {
        /** 하루가 39칸이라 방향별로 다 담아도 넉넉합니다. */
        const val MAX_CACHED_SNAPSHOTS = 128
    }
}
