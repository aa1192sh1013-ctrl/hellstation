package com.hellstation.domain.model

/**
 * 역 식별자. 노선 + 역코드 조합입니다.
 *
 * "서울역"은 1호선·4호선·경의중앙선·공항철도에 각각 있고 혼잡도가 전혀 다릅니다.
 * **역의 정체성은 (노선, 역)이지 역명 하나가 아닙니다.**
 *
 * 역코드는 좌표 API의 BLDN_ID / 역정보 API의 STATION_CD 체계를 씁니다(예: "0150").
 * 도착정보 API의 statnId("1001000133")는 체계가 달라 [RealtimeStationId]로 따로 다룹니다.
 */
data class StationId(
    val line: LineId,
    val stationCode: String,
) {
    /** 로그·캐시·화면 이동 인자용 문자열. 예: "1001:0150" */
    val key: String get() = "${line.apiCode}:$stationCode"

    companion object {
        /** [key] 형식 문자열을 되돌립니다. 형식이 깨졌으면 null. */
        fun fromKey(key: String): StationId? {
            val parts = key.split(':')
            if (parts.size != 2) return null
            val line = LineId.fromApiCode(parts[0]) ?: return null
            return StationId(line, parts[1])
        }
    }
}

data class LatLng(
    val latitude: Double,
    val longitude: Double,
)

/**
 * 역 하나. 좌표 API + 역정보 API를 합쳐 만듭니다.
 *
 * @param name          정규화된 역명. 접미사 "역"을 뗀 형태("서울", "시청"). 매칭 전용
 * @param displayName   사용자에게 보여줄 이름("서울역")
 * @param frCode        역 안내판에 적힌 번호("P148", "151"). 없을 수 있음
 * @param location      위경도. 좌표 API에 없는 역은 null — 지도에 찍을 수 없습니다
 * @param transferLines 이 역에서 갈아탈 수 있는 다른 노선들. 자기 자신은 제외
 * @param realtimeId    도착정보 API가 쓰는 ID. 매칭에 실패했으면 null
 */
data class Station(
    val id: StationId,
    val name: String,
    val displayName: String,
    val frCode: String? = null,
    val location: LatLng? = null,
    val transferLines: List<LineId> = emptyList(),
    val realtimeId: RealtimeStationId? = null,
) {
    val isTransfer: Boolean get() = transferLines.isNotEmpty()

    /** 지도에 그릴 수 있는 역인가. 좌표가 없는 역이 약 15개 있습니다. */
    val isMappable: Boolean get() = location != null
}

/**
 * 도착정보 API가 쓰는 역 ID. 앞 4자리가 노선 코드, 뒤 6자리가 역 일련번호입니다.
 * 예: "1001000133" = 1호선(1001) + 000133
 *
 * [StationId]와는 별개의 체계이므로 섞지 않습니다.
 * 두 체계를 잇는 것은 data 계층의 매핑 테이블 책임입니다.
 */
@JvmInline
value class RealtimeStationId(val raw: String) {
    val lineCode: String get() = raw.take(4)
    val line: LineId? get() = LineId.fromApiCode(lineCode)

    companion object {
        /** 10자리가 아니면 null. 예외를 던지지 않습니다 — API가 이상한 값을 줄 수 있습니다. */
        fun of(raw: String?): RealtimeStationId? {
            val trimmed = raw?.trim() ?: return null
            return if (trimmed.length == 10) RealtimeStationId(trimmed) else null
        }
    }
}
