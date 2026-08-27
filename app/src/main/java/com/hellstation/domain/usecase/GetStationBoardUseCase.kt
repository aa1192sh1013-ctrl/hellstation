package com.hellstation.domain.usecase

import com.hellstation.domain.model.Arrival
import com.hellstation.domain.model.BaselineKey
import com.hellstation.domain.model.BaselineSample
import com.hellstation.domain.model.DayType
import com.hellstation.domain.model.CrowdIndex
import com.hellstation.domain.model.Direction
import com.hellstation.domain.model.Loadable
import com.hellstation.domain.model.ServiceStatus
import com.hellstation.domain.model.StationBoard
import com.hellstation.domain.model.StationId
import com.hellstation.domain.model.TimeSlot
import com.hellstation.domain.model.TrainOption
import com.hellstation.domain.model.UnavailableReason
import com.hellstation.domain.repository.ArrivalRepository
import com.hellstation.domain.repository.BaselineSource
import com.hellstation.domain.repository.CrowdRepository
import com.hellstation.domain.repository.SubwayNetworkRepository
import java.time.Instant

/**
 * 역 하나에 대해 화면이 필요로 하는 것을 전부 모아 [StationBoard] 하나로 만듭니다.
 *
 * **화면은 이것만 부르면 됩니다.** 저장소 여러 개를 화면에서 직접 조합하지 마세요.
 *
 * ## 열차마다 혼잡도가 다른 이유
 *
 * 역의 통계 혼잡도는 하나지만, 지금 들어오는 열차와 다음 열차는 서로 다릅니다.
 * 앞 열차와의 간격이 벌어질수록 승객이 더 쌓이기 때문입니다.
 * 그래서 열차별로 **그 앞의 간격**을 따로 계산해 보정합니다 — 이 차이가
 * "지금 탈까 기다릴까"의 근거가 됩니다.
 */
class GetStationBoardUseCase(
    private val network: SubwayNetworkRepository,
    private val arrivals: ArrivalRepository,
    private val crowd: CrowdRepository,
    private val baseline: BaselineSource,
    private val estimator: CrowdEstimator,
    private val decider: RideOrWaitDecider,
    private val calendar: ServiceCalendar,
) {

    suspend operator fun invoke(
        stationId: StationId,
        direction: Direction,
        at: Instant = Instant.now(),
    ): Loadable<StationBoard> {
        network.warmUp()
        val station = network.station(stationId)
            ?: return Loadable.Unavailable(
                UnavailableReason.NO_DATA,
                "역 정보를 찾지 못했습니다",
            )

        val slot = calendar.slotAt(at)
        val dayType = calendar.dayTypeAt(at)
        val isHolidayFallback = calendar.isHolidayFallback(at)

        // 1. 실시간 도착 정보
        val loaded = arrivals.arrivalsAt(station.name)
        val allArrivals: List<Arrival>
        val isArrivalFallback: Boolean
        // 실시간을 못 받아도 통계로 화면은 채웁니다. 다만 **왜** 못 받았는지는 들고 갑니다 —
        // 화면이 "열차가 없다"와 "연결이 끊겼다"를 구분해야 하기 때문입니다.
        var arrivalFailure: UnavailableReason? = null
        when (loaded) {
            is Loadable.Ready -> {
                allArrivals = loaded.value
                isArrivalFallback = loaded.isFallback
            }

            is Loadable.Unavailable -> {
                allArrivals = emptyList()
                isArrivalFallback = true
                arrivalFailure = loaded.reason
            }

            Loadable.Loading -> {
                allArrivals = emptyList()
                isArrivalFallback = true
            }
        }

        val relevant = ArrivalAnalyzer.filter(allArrivals, station.id.line, direction)
        val sorted = ArrivalAnalyzer.sortByEta(relevant, at)

        val serviceStatus = ArrivalAnalyzer.serviceStatus(sorted, at, slot, dayType)
        val headway = ArrivalAnalyzer.headwaySeconds(sorted, at)
        val dataAge = ArrivalAnalyzer.worstDataAgeSeconds(sorted, at)

        // 2. 역 전체 혼잡도 (열차를 구분하지 않는 값)
        val stationCrowd = crowd.crowdAt(stationId, direction, at)

        // 3. 열차별 혼잡도
        val sample = baseline.sample(BaselineKey(stationId, direction, dayType, slot))
        val gaps = gapsBefore(sorted, at)
        val options = sorted.mapIndexed { index, arrival ->
            TrainOption(
                arrival = arrival,
                crowd = crowdForTrain(
                    sample = sample,
                    arrival = arrival,
                    gapSeconds = gaps.getOrNull(index),
                    dataAge = dataAge,
                    serviceStatus = serviceStatus,
                    isHolidayFallback = isHolidayFallback,
                    at = at,
                    slot = slot,
                    dayType = dayType,
                    fallback = stationCrowd,
                ),
            )
        }

        // 4. 결론
        val verdict = decider.decide(
            current = options.getOrNull(0),
            next = options.getOrNull(1),
            now = at,
        )

        val board = StationBoard(
            station = station,
            direction = direction,
            crowd = stationCrowd,
            upcoming = options,
            verdict = verdict,
            serviceStatus = serviceStatus,
            headwaySeconds = headway,
            observedAt = at,
            arrivalFailure = arrivalFailure,
        )

        // 실시간이 없어도 통계로 화면은 채워집니다. 대체 데이터라는 사실만 알려 줍니다.
        return Loadable.Ready(board, isFallback = isArrivalFallback || options.isEmpty())
    }

    // ── 내부 ────────────────────────────────────────────────────────────────

    /**
     * 각 열차 **앞의** 간격(초). 승객이 얼마나 쌓였는지를 재는 값입니다.
     *
     * ## 첫 열차는 왜 null인가
     *
     * 첫 열차 앞의 간격은 **관측할 수 없습니다.** 앞 열차가 언제 지나갔는지 API가 알려 주지
     * 않기 때문입니다. 그래서 null을 주고, [CrowdEstimator]가 보정 없이 통계값을 그대로 쓰게 합니다.
     *
     * 처음에는 뒤쪽 간격들의 중앙값으로 채워 봤는데, **열차가 두 대뿐일 때 첫 열차와
     * 둘째 열차의 간격이 같아져서 두 열차의 혼잡도가 항상 똑같이 나왔습니다.**
     * 그러면 "지금 탈까 기다릴까"가 영원히 "기다릴 이유 없음"이 됩니다.
     *
     * 지금 방식이 물리적으로도 맞습니다.
     * - 다음 열차 앞의 간격이 **좁으면** → 사람이 덜 쌓임 → 다음 열차가 한산 → 기다릴 값어치
     * - 간격이 **넓으면** → 사람이 더 쌓임 → 다음 열차가 더 붐빔 → 지금 타는 게 나음
     */
    internal fun gapsBefore(sorted: List<Arrival>, now: Instant): List<Int?> {
        val etas = sorted.map { it.secondsUntilArrival(now) }
        return etas.mapIndexed { index, current ->
            if (index == 0) return@mapIndexed null
            val previous = etas[index - 1]
            if (previous != null && current != null && current > previous) {
                current - previous
            } else {
                null
            }
        }
    }

    private fun crowdForTrain(
        sample: BaselineSample?,
        arrival: Arrival,
        gapSeconds: Int?,
        dataAge: Long?,
        serviceStatus: ServiceStatus,
        isHolidayFallback: Boolean,
        at: Instant,
        slot: TimeSlot,
        dayType: DayType,
        fallback: CrowdIndex,
    ): CrowdIndex {
        if (sample == null) return fallback

        val signals = CrowdSignals(
            headwaySeconds = gapSeconds,
            dataAgeSeconds = dataAge,
            trainType = arrival.train.type,
            serviceStatus = serviceStatus,
            isHolidayFallback = isHolidayFallback,
        )
        val estimated = estimator.estimate(
            baseline = sample,
            signals = signals,
            at = at,
            slot = slot,
            dayType = dayType,
        )
        return if (estimated.level.isKnown) estimated else fallback
    }
}
