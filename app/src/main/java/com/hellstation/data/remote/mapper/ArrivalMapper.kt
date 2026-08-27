package com.hellstation.data.remote.mapper

import com.hellstation.data.remote.dto.ArrivalDto
import com.hellstation.domain.model.Arrival
import com.hellstation.domain.model.ArrivalState
import com.hellstation.domain.model.Direction
import com.hellstation.domain.model.LineId
import com.hellstation.domain.model.RealtimeStationId
import com.hellstation.domain.model.StationId
import com.hellstation.domain.model.Train
import com.hellstation.domain.model.TrainType
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 도착정보 DTO를 domain [Arrival]로 바꿉니다.
 *
 * **여기서 실패하면 예외 대신 null을 돌려줍니다.** API가 이상한 값을 하나 보냈다고
 * 나머지 19건까지 버릴 이유가 없습니다.
 */
class ArrivalMapper(
    /**
     * 실시간 역 ID를 앱의 [StationId]로 바꾸는 함수.
     * 두 체계가 다르기 때문에 역 목록 캐시가 필요합니다(docs/data-model.md 4절).
     */
    private val resolveStationId: (RealtimeStationId, normalizedName: String) -> StationId?,
) {

    fun map(dto: ArrivalDto): Arrival? {
        val line = LineId.fromApiCode(dto.subwayId ?: return null) ?: return null
        val direction = Direction.parse(dto.updnLine) ?: return null
        val realtimeId = RealtimeStationId.of(dto.statnId) ?: return null
        val normalizedName = StationNameNormalizer.normalize(dto.statnNm)

        // 역 목록에 없는 역이면 노선 + 실시간 ID 뒷자리로 임시 식별자를 만듭니다.
        // 버리지 않는 이유: 그 역에 열차가 온다는 사실 자체가 정보이기 때문입니다.
        val stationId = resolveStationId(realtimeId, normalizedName)
            ?: StationId(line, realtimeId.raw.takeLast(6))

        val train = Train(
            trainNo = dto.btrainNo.orEmpty().ifBlank { "?" },
            line = line,
            direction = direction,
            type = TrainType.parse(dto.btrainSttus),
            destination = dto.bstatnNm.orEmpty(),
            headsign = dto.trainLineNm.orEmpty(),
            isLastTrain = dto.lstcarAt == "1",
        )

        return Arrival(
            station = stationId,
            train = train,
            state = ArrivalState.parse(dto.arvlCd),
            rawSecondsUntilArrival = dto.barvlDt?.trim()?.toIntOrNull() ?: 0,
            observedAt = parseReceptionTime(dto.recptnDt),
            message = dto.arvlMsg2.orEmpty(),
        )
    }

    fun mapAll(dtos: List<ArrivalDto>): List<Arrival> = dtos.mapNotNull { map(it) }

    /**
     * `recptnDt`("2026-08-22 11:35:50")를 [Instant]로 바꿉니다.
     *
     * 서버는 한국 시간으로 보냅니다. 기기가 다른 시간대에 있어도 맞도록 명시적으로 KST를 씁니다.
     * 파싱에 실패하면 "지금"으로 봅니다 — 그러면 데이터 나이가 0이 되어 신뢰도가 과대평가되므로,
     * 실패가 잦다면 로그를 확인해야 합니다.
     */
    private fun parseReceptionTime(raw: String?): Instant {
        if (raw.isNullOrBlank()) return Instant.now()
        return try {
            LocalDateTime.parse(raw.trim(), FORMATTER).atZone(SEOUL).toInstant()
        } catch (e: Exception) {
            Instant.now()
        }
    }

    companion object {
        private val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
        private val FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
