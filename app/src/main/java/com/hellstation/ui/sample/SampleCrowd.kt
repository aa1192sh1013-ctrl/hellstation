package com.hellstation.ui.sample

import com.hellstation.domain.model.Arrival
import com.hellstation.domain.model.ArrivalState
import com.hellstation.domain.model.Confidence
import com.hellstation.domain.model.CrowdIndex
import com.hellstation.domain.model.CrowdSource
import com.hellstation.domain.model.DataTier
import com.hellstation.domain.model.Direction
import com.hellstation.domain.model.HeatmapSnapshot
import com.hellstation.domain.model.ServiceStatus
import com.hellstation.domain.model.Station
import com.hellstation.domain.model.StationBoard
import com.hellstation.domain.model.StationId
import com.hellstation.domain.model.TimeSlot
import com.hellstation.domain.model.Train
import com.hellstation.domain.model.TrainOption
import com.hellstation.domain.model.TrainType
import com.hellstation.domain.usecase.CrowdForecastPoint
import com.hellstation.domain.usecase.RideOrWaitDecider
import java.time.Instant
import kotlin.math.abs

/**
 * 화면 작업용 **가짜 혼잡도**.
 *
 * 실제 계산은 데이터·기능 담당이 이미 만들어 두었습니다
 * (`domain/usecase/CrowdEstimator`). 3단계에서 그걸 붙이면 이 파일은 사라집니다.
 *
 * ## 가짜 데이터를 만들 때 지킨 것
 *
 * 잘 나오는 경우만 만들면 실제 데이터를 붙였을 때 화면이 깨집니다. 그래서 일부러
 * **다섯 단계 전부 + UNKNOWN + 신뢰도 LOW + 지연 상황**이 섞이도록 만들었습니다.
 * 화면을 다 만든 뒤 [debugStates]로 극단적인 경우들을 한 번씩 눈으로 확인하세요.
 *
 * 값은 역 이름 해시로 정해지므로 **다시 그려도 같은 값**이 나옵니다.
 * 그래야 스크롤할 때 색이 춤추지 않습니다.
 */
object SampleCrowd {

    /** 결론 계산은 진짜 로직을 씁니다. 가짜 값을 넣어도 판단 규칙은 실제와 같습니다. */
    private val decider = RideOrWaitDecider()

    /** 지도 한 장. Time Slider가 [at]을 바꾸면 색이 통째로 바뀝니다. */
    fun snapshotAt(at: Instant, slot: TimeSlot): HeatmapSnapshot {
        val entries = SampleMetro.layout.stations.associate { station ->
            station.id to crowdFor(station, slot, at)
        }
        return HeatmapSnapshot(at = at, entries = entries, bestTier = DataTier.HISTORICAL)
    }

    /** 역 하나의 혼잡도. */
    fun crowdFor(station: Station, slot: TimeSlot, at: Instant): CrowdIndex {
        // 데이터가 없는 역도 있어야 합니다. UNKNOWN 색 처리를 확인하려는 것입니다.
        if (hasNoData(station)) {
            return CrowdIndex.unknown(at, note = "이 역·시간대의 혼잡도 자료가 없습니다")
        }

        val percent = percentFor(station, slot)
        val confidence = when {
            noise(station.name + "conf") > 0.88f -> Confidence.LOW
            else -> Confidence.MEDIUM
        }
        return CrowdIndex.of(
            percent = percent,
            confidence = confidence,
            source = CrowdSource.BASELINE,
            at = at,
            note = "같은 요일·시간대 평균값입니다",
        )
    }

    /**
     * 역 상세 Bottom Sheet에 넣을 한 덩어리.
     *
     * 다가오는 열차 3~4대를 만들고, 열차마다 앞 간격에 따라 혼잡도를 조금씩 다르게 줍니다.
     * 그래야 "지금 탈까 기다릴까"가 의미 있는 답을 냅니다.
     */
    fun boardFor(
        station: Station,
        direction: Direction,
        at: Instant,
        slot: TimeSlot,
        serviceStatus: ServiceStatus = ServiceStatus.NORMAL,
    ): StationBoard {
        val stationCrowd = crowdFor(station, slot, at)

        val options = if (stationCrowd.level.isKnown && serviceStatus != ServiceStatus.SUSPENDED) {
            buildTrains(station, direction, at, slot, stationCrowd)
        } else {
            emptyList()
        }

        val verdict = decider.decide(
            current = options.getOrNull(0),
            next = options.getOrNull(1),
            now = at,
        )

        return StationBoard(
            station = station,
            direction = direction,
            crowd = stationCrowd,
            upcoming = options,
            verdict = verdict,
            serviceStatus = serviceStatus,
            headwaySeconds = options.getOrNull(1)?.arrival?.secondsUntilArrival(at)
                ?.minus(options.getOrNull(0)?.arrival?.secondsUntilArrival(at) ?: 0),
            observedAt = at,
        )
    }

    /** Time Slider용 하루치 곡선. */
    fun forecastFor(station: Station, at: Instant): List<CrowdForecastPoint> =
        TimeSlot.ALL.map { slot ->
            CrowdForecastPoint(
                slot = slot,
                at = at,
                crowd = crowdFor(station, slot, at),
            )
        }

    /**
     * 화면이 버텨야 하는 극단적인 경우들.
     * 미리보기에서 한 번씩 확인하세요 — 이 중 하나라도 깨지면 실제 데이터에서도 깨집니다.
     */
    fun debugStates(at: Instant, slot: TimeSlot): List<Pair<String, StationBoard>> {
        val station = SampleMetro.seoulStation
        return listOf(
            "정상" to boardFor(station, Direction.UP, at, slot),
            "운행 지연" to boardFor(station, Direction.UP, at, slot, ServiceStatus.DELAYED),
            "운행 중단" to boardFor(station, Direction.UP, at, slot, ServiceStatus.SUSPENDED),
            "운행 시간 아님" to boardFor(station, Direction.UP, at, slot, ServiceStatus.CLOSED),
        )
    }

    // ── 값 만들기 ───────────────────────────────────────────────────────────

    /**
     * 시간대 × 위치로 혼잡도를 만듭니다.
     *
     * 도심에 가까울수록, 러시아워에 가까울수록 붐빕니다.
     * 실제 통계와는 다르지만 **화면에서 색이 어떻게 퍼지는지 확인하기에는 충분**합니다.
     */
    private fun percentFor(station: Station, slot: TimeSlot): Double {
        val timeFactor = timeCurve(slot.minutesFromMidnight)
        val centrality = centralityOf(station.id)
        val transferBoost = if (station.isTransfer) 1.18f else 1.0f
        val wobble = 0.72f + noise(station.name) * 0.62f

        val percent = timeFactor * (0.55f + centrality * 1.15f) * transferBoost * wobble * 120f
        return percent.coerceIn(6f, 205f).toDouble()
    }

    /** 05:30~24:30. 출근 봉우리가 퇴근 봉우리보다 높고 좁습니다. */
    private fun timeCurve(minutes: Int): Float = when {
        minutes < 6 * 60 -> 0.12f
        minutes < 7 * 60 -> 0.30f
        minutes < 7 * 60 + 30 -> 0.72f
        minutes < 8 * 60 -> 1.05f
        minutes < 8 * 60 + 30 -> 1.30f
        minutes < 9 * 60 -> 1.20f
        minutes < 10 * 60 -> 0.80f
        minutes < 12 * 60 -> 0.48f
        minutes < 14 * 60 -> 0.52f
        minutes < 17 * 60 -> 0.56f
        minutes < 18 * 60 -> 0.82f
        minutes < 18 * 60 + 30 -> 1.14f
        minutes < 19 * 60 -> 1.18f
        minutes < 20 * 60 -> 0.92f
        minutes < 21 * 60 -> 0.66f
        minutes < 22 * 60 -> 0.56f
        minutes < 23 * 60 -> 0.44f
        minutes < 24 * 60 -> 0.28f
        else -> 0.14f
    }

    /** 지도 한가운데(도심)에 가까울수록 1에 가깝습니다. */
    private fun centralityOf(id: StationId): Float {
        val position = SampleMetro.layout.positionOf(id) ?: return 0.5f
        val dx = position.x - 0.47f
        val dy = position.y - 0.52f
        val distance = kotlin.math.sqrt(dx * dx + dy * dy)
        return (1f - distance * 2.2f).coerceIn(0f, 1f)
    }

    /** 자료가 없는 역. 실제로도 서울시계 밖 역은 실시간 정보가 없습니다. */
    private fun hasNoData(station: Station): Boolean = noise(station.name + "nodata") > 0.94f

    private fun buildTrains(
        station: Station,
        direction: Direction,
        at: Instant,
        slot: TimeSlot,
        stationCrowd: CrowdIndex,
    ): List<TrainOption> {
        val base = stationCrowd.percent ?: return emptyList()
        val nominalGap = if (slot.minutesFromMidnight in (7 * 60)..(9 * 60)) 160 else 320

        // 첫 열차까지 남은 시간과 열차 간격을 역마다 조금씩 다르게 흔듭니다.
        val firstEta = (20 + noise(station.name + "eta") * 260).toInt()
        val gaps = listOf(
            (nominalGap * (0.55f + noise(station.name + "g1") * 1.1f)).toInt(),
            (nominalGap * (0.7f + noise(station.name + "g2") * 0.9f)).toInt(),
            (nominalGap * (0.8f + noise(station.name + "g3") * 0.8f)).toInt(),
        )

        var eta = firstEta
        return List(4) { index ->
            if (index > 0) eta += gaps[index - 1]

            // 앞 간격이 넓을수록 사람이 더 쌓입니다 — 데이터·기능 담당의 모델과 같은 생각입니다.
            val gapRatio = if (index == 0) 1.0f else gaps[index - 1].toFloat() / nominalGap
            val type = if (index == 2 && station.isTransfer) TrainType.EXPRESS else TrainType.NORMAL
            val typeFactor = if (type == TrainType.EXPRESS) 1.15f else 1.0f

            val percent = (base * gapRatio.coerceIn(0.7f, 1.6f) * typeFactor).coerceIn(6.0, 205.0)

            TrainOption(
                arrival = Arrival(
                    station = station.id,
                    train = Train(
                        trainNo = "%04d".format(1000 + (abs(station.name.hashCode()) + index * 37) % 8999),
                        line = station.id.line,
                        direction = direction,
                        type = type,
                        destination = terminusFor(station, direction),
                        headsign = "${terminusFor(station, direction)}행",
                        isLastTrain = slot.minutesFromMidnight >= 24 * 60 && index == 0,
                    ),
                    state = if (index == 0 && eta < 30) ArrivalState.ENTERING else ArrivalState.RUNNING,
                    rawSecondsUntilArrival = eta,
                    observedAt = at,
                    message = etaMessage(eta),
                ),
                crowd = CrowdIndex.of(
                    percent = percent,
                    confidence = stationCrowd.confidence,
                    source = CrowdSource.REALTIME_BASELINE,
                    at = at,
                    note = null,
                ),
            )
        }
    }

    private fun etaMessage(etaSeconds: Int): String = when {
        etaSeconds < 30 -> "곧 도착"
        etaSeconds < 60 -> "잠시 후 도착"
        else -> "${etaSeconds / 60}분 후"
    }

    /** 그럴듯한 종착역 이름. 실제 운행 계통과는 무관합니다. */
    private fun terminusFor(station: Station, direction: Direction): String {
        val shape = SampleMetro.layout.lines.firstOrNull { it.line == station.id.line }
            ?: return "종착역"
        val ids = shape.stationIds
        val terminusId = if (direction == Direction.UP) ids.first() else ids.last()
        return SampleMetro.layout.station(terminusId)?.displayName ?: "종착역"
    }

    /**
     * 문자열에서 0~1 사이 값을 만듭니다. 같은 입력이면 항상 같은 값이 나옵니다.
     * 진짜 난수를 쓰면 화면을 다시 그릴 때마다 색이 바뀌어서 눈이 아픕니다.
     */
    private fun noise(seed: String): Float {
        var hash = 2166136261u
        for (char in seed) {
            hash = hash xor char.code.toUInt()
            hash *= 16777619u
        }
        return (hash % 10000u).toFloat() / 10000f
    }
}
