package com.hellstation.data.repository

import com.hellstation.domain.model.BaselineKey
import com.hellstation.domain.model.CrowdIndex
import com.hellstation.domain.model.DataTier
import com.hellstation.domain.model.Direction
import com.hellstation.domain.model.HeatmapSnapshot
import com.hellstation.domain.model.Loadable
import com.hellstation.domain.model.Station
import com.hellstation.domain.model.StationId
import com.hellstation.domain.model.TrainType
import com.hellstation.domain.repository.ArrivalRepository
import com.hellstation.domain.repository.BaselineSource
import com.hellstation.domain.repository.CrowdRepository
import com.hellstation.domain.usecase.ArrivalAnalyzer
import com.hellstation.domain.usecase.CrowdEstimator
import com.hellstation.domain.usecase.CrowdSignals
import com.hellstation.domain.usecase.ServiceCalendar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Instant
import kotlin.math.abs

/**
 * 혼잡도 계산의 조립 지점. 대체(fallback) 순서가 여기 있습니다.
 *
 * ```
 * LIVE          (실측 혼잡도 API)        -- 지금은 없음, 자리만 비워 둠
 *   ↓ 없으면
 * ESTIMATED     (통계 + 실시간 배차 보정)  -- 역 상세 화면의 기본 경로
 *   ↓ 없으면
 * HISTORICAL    (통계만)                 -- Heatmap과 Time Slider의 기본 경로
 *   ↓ 없으면
 * NEIGHBOR      (옆 역에서 추정)          -- 신뢰도 LOW
 *   ↓ 없으면
 * UNKNOWN       (회색)                   -- 거짓말하지 않기
 * ```
 *
 * ## Heatmap과 역 상세가 다른 이유
 *
 * Heatmap은 역이 800개입니다. 전부에 실시간 보정을 걸면 API를 800번 불러야 합니다.
 * 그래서 **지도는 통계값(HISTORICAL)으로 그리고, 사용자가 역을 눌렀을 때만
 * 실시간을 붙여 보정(ESTIMATED)합니다.** 화면에는 이 차이가 신뢰도 표시로 드러납니다.
 */
class CrowdRepositoryImpl(
    private val network: SubwayNetworkRepositoryImpl,
    private val arrivals: ArrivalRepository,
    private val baseline: BaselineSource,
    private val estimator: CrowdEstimator,
    private val calendar: ServiceCalendar,
    private val now: () -> Instant = Instant::now,
) : CrowdRepository {

    override suspend fun crowdAt(
        station: StationId,
        direction: Direction,
        at: Instant,
    ): CrowdIndex {
        val resolved = network.station(station)
        val signals = if (isAboutNow(at)) liveSignals(resolved, direction, at) else CrowdSignals.NONE
        return estimate(station, direction, at, signals, allowNeighbor = true)
    }

    override fun heatmap(at: Instant, direction: Direction?): Flow<HeatmapSnapshot> = flow {
        network.warmUp()
        // 좌표가 없는 역만 있을 수도 있습니다(밖에서 넣어 준 씨앗 목록 등).
        // 그때도 혼잡도는 계산할 수 있으므로 지도에서 빼지 않습니다 —
        // 어디에 그릴지는 화면이 알아서 정합니다.
        val stations = network.mappableStations().ifEmpty { network.catalog.stations }
        if (stations.isEmpty()) {
            emit(HeatmapSnapshot.empty(at))
            return@flow
        }

        val entries = LinkedHashMap<StationId, CrowdIndex>(stations.size)
        for (station in stations) {
            entries[station.id] = when (direction) {
                // 방향을 지정하지 않으면 **더 나쁜 쪽**을 보여줍니다.
                // 지도를 보는 사람은 "이 역이 얼마나 힘든가"를 알고 싶어 하지
                // 상행 평균과 하행 평균의 중간값을 알고 싶어 하지 않습니다.
                null -> worseOf(
                    estimate(station.id, Direction.UP, at, CrowdSignals.NONE, allowNeighbor = false),
                    estimate(station.id, Direction.DOWN, at, CrowdSignals.NONE, allowNeighbor = false),
                )

                else -> estimate(station.id, direction, at, CrowdSignals.NONE, allowNeighbor = false)
            }
        }

        val bestTier = entries.values
            .map { it.tier }
            .minByOrNull { it.ordinal }
            ?: DataTier.NONE

        emit(HeatmapSnapshot(at = at, entries = entries, bestTier = bestTier))
    }

    // ── 내부 ────────────────────────────────────────────────────────────────

    /**
     * 한 역·한 방향의 혼잡도를 계산합니다.
     *
     * @param allowNeighbor 통계가 없을 때 옆 역에서 추정할 것인가.
     *   Heatmap에서는 끄세요 — 800개 역마다 이웃을 찾으면 느려집니다.
     */
    private suspend fun estimate(
        station: StationId,
        direction: Direction,
        at: Instant,
        signals: CrowdSignals,
        allowNeighbor: Boolean,
    ): CrowdIndex {
        val slot = calendar.slotAt(at)
        val dayType = calendar.dayTypeAt(at)
        val withHoliday = signals.copy(isHolidayFallback = calendar.isHolidayFallback(at))

        val sample = baseline.sample(BaselineKey(station, direction, dayType, slot))
        val direct = estimator.estimate(
            baseline = sample,
            signals = withHoliday,
            at = at,
            slot = slot,
            dayType = dayType,
        )
        if (direct.level.isKnown) return direct
        if (!allowNeighbor) return direct

        return neighborEstimate(station, direction, at, withHoliday) ?: direct
    }

    /** 옆 역 통계로 메웁니다. 신뢰도는 무조건 LOW입니다. */
    private suspend fun neighborEstimate(
        station: StationId,
        direction: Direction,
        at: Instant,
        signals: CrowdSignals,
    ): CrowdIndex? {
        val self = network.station(station) ?: return null
        val slot = calendar.slotAt(at)
        val dayType = calendar.dayTypeAt(at)

        for (neighbor in network.catalog.nearestOnSameLine(self, count = NEIGHBOR_COUNT)) {
            val sample = baseline.sample(BaselineKey(neighbor.id, direction, dayType, slot))
                ?: continue
            val estimated = estimator.estimate(
                baseline = sample,
                signals = signals,
                at = at,
                slot = slot,
                dayType = dayType,
                fromNeighbor = true,
            )
            if (estimated.level.isKnown) return estimated
        }
        return null
    }

    /** 도착 정보를 받아 배차 간격과 운행 상태를 읽어냅니다. */
    private suspend fun liveSignals(
        station: Station?,
        direction: Direction,
        at: Instant,
    ): CrowdSignals {
        if (station == null) return CrowdSignals.NONE

        val loaded = arrivals.arrivalsAt(station.name)
        val all = if (loaded is Loadable.Ready) loaded.value else return CrowdSignals.NONE

        val relevant = ArrivalAnalyzer.filter(all, station.id.line, direction)
        if (relevant.isEmpty()) {
            // 이 노선·방향 열차가 하나도 안 잡혔습니다. 샘플 키(5건 제한)에서 흔합니다.
            return CrowdSignals.NONE
        }

        val sorted = ArrivalAnalyzer.sortByEta(relevant, at)
        val slot = calendar.slotAt(at)
        val dayType = calendar.dayTypeAt(at)

        return CrowdSignals(
            headwaySeconds = ArrivalAnalyzer.headwaySeconds(sorted, at),
            dataAgeSeconds = ArrivalAnalyzer.worstDataAgeSeconds(sorted, at),
            trainType = sorted.firstOrNull()?.train?.type ?: TrainType.UNKNOWN,
            serviceStatus = ArrivalAnalyzer.serviceStatus(sorted, at, slot, dayType),
        )
    }

    /**
     * Time Slider가 미래·과거를 가리키면 실시간 보정은 의미가 없습니다.
     * 지금 근처일 때만 실시간을 붙입니다.
     */
    private fun isAboutNow(at: Instant): Boolean =
        abs(at.epochSecond - now().epochSecond) <= LIVE_WINDOW_SECONDS

    private fun worseOf(a: CrowdIndex, b: CrowdIndex): CrowdIndex = when {
        !a.level.isKnown -> b
        !b.level.isKnown -> a
        b.level.ordinal > a.level.ordinal -> b
        else -> a
    }

    private companion object {
        /** 이 범위 안이면 "지금"으로 보고 실시간 보정을 겁니다. */
        const val LIVE_WINDOW_SECONDS = 15 * 60L

        const val NEIGHBOR_COUNT = 3
    }
}
