package com.hellstation.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.hellstation.domain.model.CrowdLevel
import com.hellstation.domain.model.Direction
import com.hellstation.domain.model.HeatmapSnapshot
import com.hellstation.domain.model.LineId
import com.hellstation.domain.model.Loadable
import com.hellstation.domain.model.Station
import com.hellstation.domain.model.StationBoard
import com.hellstation.domain.model.StationId
import com.hellstation.domain.model.TimeSlot
import com.hellstation.ui.map.MetroLayout
import com.hellstation.ui.map.MetroLineShape
import com.hellstation.ui.sample.SampleCrowd
import com.hellstation.ui.sample.SampleMetro
import java.time.Instant

/**
 * 화면과 데이터 계층을 잇는 자리.
 *
 * ## 왜 ViewModel이 아닌가
 *
 * ViewModel이 정석이고 화면 회전에도 값을 지켜 줍니다. 다만 지금은 **아직 아무도 이 프로젝트를
 * 한 번도 컴파일해 본 적이 없는 상태**라(JDK 미설치), 한 번에 많이 바꾸면 어디서 깨졌는지
 * 찾기가 어렵습니다.
 *
 * 그래서 화면 구조는 그대로 두고 **데이터를 가져오는 줄만** 바꿉니다.
 * 창구([com.hellstation.data.di.HellStationFacade])가 시간대 단위로 결과를 캐싱하므로
 * 회전해서 다시 계산해도 값이 바로 나옵니다.
 *
 * 빌드가 확인되면 ViewModel로 옮기는 것이 좋습니다 — `data/di/UiConnectionGuide.kt` 참고.
 *
 * ## 미리보기는 어떻게 되나
 *
 * [rememberFacade]가 미리보기에서 null을 주므로, 아래 함수들은 전부 임시 데이터로
 * 되돌아갑니다. 미리보기 24개가 그대로 동작합니다.
 */

/** 지도 화면이 필요로 하는 것 한 묶음. */
@Immutable
data class HeatmapData(
    val layout: MetroLayout,
    val snapshot: HeatmapSnapshot,
    val dayLevels: List<CrowdLevel>,
    /** 인증키·통계 파일이 없을 때 사용자에게 알릴 한 줄. 없으면 null */
    val warning: String?,
    /** 실제 계산 결과인가, 아니면 아직 임시 데이터인가 */
    val isLive: Boolean,
)

/**
 * 지도 한 장.
 *
 * 처음에는 임시 데이터를 그대로 보여주고, 실제 계산이 끝나면 갈아 끼웁니다.
 * **로딩 중에 빈 화면을 보여주지 않기 위해서**입니다 — 지도가 잠깐이라도 비면
 * 앱이 고장 난 것처럼 보입니다.
 */
@Composable
fun rememberHeatmapData(at: Instant, slot: TimeSlot): HeatmapData {
    val facade = rememberFacade()
    val fallback = remember(slot) { sampleHeatmapData(at, slot) }
    if (facade == null) return fallback

    return produceState(initialValue = fallback, facade, slot) {
        val stations = facade.mapStations()

        // 슬라이더가 가리키는 **시간대를 실제 시각으로 바꿔서** 물어봅니다.
        // 이 줄이 없으면 슬라이더를 아무리 움직여도 지도는 계속 "지금"을 보여줍니다.
        // 시각이 그대로 화면 색을 정하므로, 눈으로는 슬라이더가 도는 것처럼 보여서
        // 한참 못 알아챘습니다.
        val instant = facade.instantFor(slot, at)

        value = HeatmapData(
            layout = layoutFor(stations),
            snapshot = facade.heatmapAt(instant),
            dayLevels = facade.dayCurve(instant),
            warning = facade.status().warning,
            isLive = true,
        )
    }.value
}

/**
 * 역 하나의 도착 정보 묶음.
 *
 * @param board 마지막으로 받아 온 값. **다시 부르는 동안에도 비우지 않습니다** —
 *   30초마다 화면이 빈칸으로 깜빡이면 고장 난 것처럼 보입니다
 * @param isLoading 지금 받아오는 중인가. 보여줄 값이 아직 없을 때만 화면에 드러냅니다
 * @param failure 실패했다면 그 이유. 성공했으면 null
 * @param retry 사용자가 직접 다시 시도
 */
@Immutable
data class StationBoardFeed(
    val board: StationBoard?,
    val isLoading: Boolean,
    val failure: Loadable.Unavailable?,
    val retry: () -> Unit,
) {
    /** 아직 아무것도 못 보여주는 상태인가. 자리 표시를 띄울지 정할 때 씁니다. */
    val isEmpty: Boolean get() = board == null && failure == null
}

/**
 * 역 하나의 상세.
 *
 * **여기서만 실시간 도착정보를 부릅니다** — 사용자가 실제로 그 역을 눌렀을 때만요.
 * 지도의 역 300개에 전부 실시간을 붙이면 API를 300번 부르게 됩니다.
 *
 * ## 세 가지 상태를 구분합니다
 *
 * 예전에는 성공이면 값을, 아니면 null을 돌려줬습니다. 그래서 **받아오는 중과 실패가
 * 화면에서 똑같아 보였고**, 지하철에서 신호가 끊겼을 때 사용자가 기다려야 하는지
 * 다시 눌러야 하는지 알 수 없었습니다. 이제 셋을 나눠서 돌려줍니다.
 *
 * ## 30초마다 다시 부릅니다
 *
 * [rememberDataRefreshKey]가 열쇠를 올리면 다시 조회합니다. 그때 **이전 값을 지우지
 * 않습니다** — 새 값이 올 때까지 화면은 마지막으로 성공한 값을 계속 보여 줍니다.
 */
@Composable
fun rememberStationBoard(
    station: Station?,
    direction: Direction,
    at: Instant,
    slot: TimeSlot,
): StationBoardFeed {
    val facade = rememberFacade()
    val refreshKey = rememberDataRefreshKey()
    var retryKey by remember { mutableIntStateOf(0) }

    // 컴포저블 호출을 조건에 따라 건너뛰면 상태가 꼬입니다.
    // 미리보기용 임시 값도 항상 만들어 두고, 무엇을 쓸지만 마지막에 정합니다.
    val fallback = remember(station, direction, slot) {
        station?.let { SampleCrowd.boardFor(it, direction, at, slot) }
    }

    // 시각은 매초 바뀝니다. 열쇠에 넣으면 초당 한 번씩 API를 부르게 되므로
    // 열쇠에서 빼고, 실제로 부를 때만 최신 값을 읽습니다.
    val instant by rememberUpdatedState(at)

    var board by remember(station?.id, direction, slot) { mutableStateOf<StationBoard?>(null) }
    var failure by remember(station?.id, direction, slot) {
        mutableStateOf<Loadable.Unavailable?>(null)
    }
    var loading by remember(station?.id, direction, slot) { mutableStateOf(true) }

    LaunchedEffect(facade, station?.id, direction, slot, refreshKey, retryKey) {
        val source = facade ?: return@LaunchedEffect
        val target = station ?: return@LaunchedEffect

        loading = true
        when (val result = source.board(target.id, direction, source.instantFor(slot, instant))) {
            is Loadable.Ready -> {
                board = result.value
                failure = null
            }
            // 실패해도 이전 값은 그대로 둡니다. 옛 정보라도 빈 화면보다는 낫고,
            // 옛 정보라는 사실은 실패 안내가 함께 알려 줍니다.
            is Loadable.Unavailable -> failure = result
            Loadable.Loading -> Unit
        }
        loading = false
    }

    if (facade == null) {
        return StationBoardFeed(fallback, isLoading = false, failure = null, retry = {})
    }
    return StationBoardFeed(
        board = board,
        isLoading = loading,
        failure = failure,
        retry = { retryKey++ },
    )
}

/** 검색 결과 한 줄. */
@Immutable
data class StationRow(
    val station: Station,
    val level: CrowdLevel,
    val isFavorite: Boolean = false,
)

/**
 * 역 검색.
 *
 * 검색어가 비어 있으면 환승이 많은 역을 먼저 보여줍니다 —
 * 빈 화면을 내밀면 뭘 쳐야 할지 모르는 사람이 막힙니다.
 */
@Composable
fun rememberSearchResults(query: String, at: Instant, slot: TimeSlot): List<StationRow> {
    val facade = rememberFacade()
    val favorites = LocalAppSettings.current.favorites
    val fallback = remember(query, slot) { sampleSearchResults(query, at, slot) }

    val live = produceState(initialValue = fallback, facade, query, slot, favorites) {
        val source = facade ?: return@produceState
        val instant = source.instantFor(slot, at)

        val stations = if (query.isBlank()) {
            // 즐겨찾는 역을 **목록에 넣어** 맨 앞에 둡니다.
            //
            // 순서만 바꾸면 소용이 없습니다. 환승이 없는 작은 역은 애초에 "자주 찾는 역"
            // 목록에 들어오지 않으므로, 즐겨찾기를 해 놓아도 화면에 나타나지 않습니다.
            // 출퇴근에 쓰는 역이 꼭 큰 역인 것도 아닙니다.
            val starred = favorites.mapNotNull { key -> stationIdFromKey(key) }
                .mapNotNull { id -> source.station(id) }
                .sortedBy { it.name }
            (starred + source.popularStations()).distinctBy { it.id.key }
        } else {
            source.search(query)
        }

        value = stations.map { station ->
            StationRow(
                station = station,
                level = source.crowdAt(station.id, Direction.UP, instant).level,
                isFavorite = station.id.key in favorites,
            )
        }
    }.value

    return if (facade == null) fallback else live
}

/** [StationId.key]("1001:0150")를 되돌립니다. 모르는 노선 코드면 null. */
private fun stationIdFromKey(key: String): StationId? {
    val parts = key.split(":")
    if (parts.size != 2) return null
    val line = LineId.fromApiCode(parts[0]) ?: return null
    return StationId(line, parts[1])
}

/** 경로 인자를 역으로 되돌립니다. 못 찾으면 null. */
@Composable
fun rememberStation(id: StationId?): Station? {
    val facade = rememberFacade()

    val fallback = remember(id) {
        id?.let { target -> SampleMetro.layout.stations.firstOrNull { it.id == target } }
    }

    val live = produceState(initialValue = fallback, facade, id) {
        val source = facade ?: return@produceState
        val target = id ?: return@produceState
        value = source.station(target) ?: fallback
    }.value

    return if (facade == null) fallback else live
}

/** 지금 시각이 속한 30분 시간대. 자정을 넘긴 운행까지 계산에 넣습니다. */
@Composable
fun rememberNowSlot(at: Instant): TimeSlot {
    val facade = rememberFacade()
    return remember(facade, at) {
        facade?.nowSlot(at)
            ?: TimeSlot.of(at.atZone(java.time.ZoneId.of("Asia/Seoul")).toLocalTime())
    }
}

// ── 배치 ────────────────────────────────────────────────────────────────────

/**
 * 역 목록으로 노선도 배치를 만듭니다.
 *
 * 씨앗 목록(= 손으로 잡은 도식 배치)을 그대로 쓰는 동안에는 그 배치를 씁니다.
 * 실제 인증키로 받은 역 목록이면 위경도로 새 배치를 만듭니다.
 *
 * 판단 기준을 "8할 이상 겹치는가"로 둔 이유: 일부 역만 갈아끼워진 중간 상태에서도
 * 이미 잘 보이는 도식 배치를 유지하는 편이 낫기 때문입니다.
 */
private fun layoutFor(stations: List<Station>): MetroLayout {
    if (stations.isEmpty()) return SampleMetro.layout

    val schematic = SampleMetro.layout
    val covered = stations.count { schematic.positions.containsKey(it.id) }
    if (covered >= stations.size * SCHEMATIC_MATCH_RATIO) return schematic

    val shapes = stations
        .groupBy { it.id.line }
        .map { (line, list) ->
            MetroLineShape(
                line = line,
                stationIds = list.sortedBy { it.id.stationCode }.map { it.id },
                isLoop = line.isLoop,
            )
        }
    return MetroLayout.fromGeo(stations, shapes)
}

private const val SCHEMATIC_MATCH_RATIO = 0.8

// ── 미리보기·초기값용 임시 데이터 ───────────────────────────────────────────

private fun sampleHeatmapData(at: Instant, slot: TimeSlot): HeatmapData {
    val layout = SampleMetro.layout
    return HeatmapData(
        layout = layout,
        snapshot = SampleCrowd.snapshotAt(at, slot),
        dayLevels = TimeSlot.ALL.map { candidate ->
            SampleCrowd.snapshotAt(at, candidate).entries.values
                .filter { it.level.isKnown }
                .maxByOrNull { it.percent ?: 0.0 }
                ?.level
                ?: CrowdLevel.UNKNOWN
        },
        warning = null,
        isLive = false,
    )
}

private fun sampleSearchResults(query: String, at: Instant, slot: TimeSlot): List<StationRow> {
    val stations = SampleMetro.layout.stations
    val matched = if (query.isBlank()) {
        stations.distinctBy { it.name }.sortedByDescending { it.transferLines.size }.take(20)
    } else {
        val needle = query.trim().removeSuffix("역")
        stations.filter { it.name.contains(needle) }.sortedBy { it.name.length }.take(30)
    }
    return matched.map { StationRow(it, SampleCrowd.crowdFor(it, slot, at).level) }
}
