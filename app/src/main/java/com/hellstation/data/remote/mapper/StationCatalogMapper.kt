package com.hellstation.data.remote.mapper

import com.hellstation.data.remote.dto.StationLineInfoDto
import com.hellstation.data.remote.dto.StationMasterDto
import com.hellstation.domain.model.LatLng
import com.hellstation.domain.model.LineId
import com.hellstation.domain.model.Station
import com.hellstation.domain.model.StationId

/**
 * 좌표 API(784건)와 역정보 API(799건)를 합쳐 역 목록을 만듭니다.
 *
 * 두 소스의 건수가 다르므로 **양쪽 어디에도 없는 역이 없도록 합집합**으로 만듭니다.
 * 좌표가 없는 역은 지도에 못 찍지만 검색과 도착 정보에는 나와야 합니다.
 *
 * 조인 키는 `(노선, 정규화된 역명)`입니다. 두 소스의 역 코드 체계는 같지만
 * 실제로 어긋나는 경우가 있어 이름을 기준으로 잡았습니다.
 */
object StationCatalogMapper {

    /** 합치는 과정에서 버려진 행. 어떤 역이 지도에서 사라졌는지 추적하려고 남깁니다. */
    data class MergeReport(
        val stations: List<Station>,
        val droppedMasterRows: Int,
        val droppedLineInfoRows: Int,
        val withoutCoordinates: Int,
    )

    fun merge(
        masterRows: List<StationMasterDto>,
        lineInfoRows: List<StationLineInfoDto>,
    ): MergeReport {
        var droppedMaster = 0
        var droppedLineInfo = 0

        // 1. 좌표 API를 (노선, 정규화 이름) -> 좌표로 정리
        data class Coordinate(val code: String, val displayName: String, val location: LatLng?)

        val coordinates = HashMap<Pair<LineId, String>, Coordinate>()
        for (row in masterRows) {
            val line = LineId.fromDisplayName(row.route ?: "")
            val rawName = row.stationName
            if (line == null || rawName.isNullOrBlank()) {
                droppedMaster++
                continue
            }
            val name = StationNameNormalizer.normalize(rawName)
            val lat = row.latitude?.trim()?.toDoubleOrNull()
            val lng = row.longitude?.trim()?.toDoubleOrNull()
            coordinates[line to name] = Coordinate(
                code = row.stationCode?.trim().orEmpty(),
                displayName = rawName.trim(),
                // (0,0)은 좌표가 아니라 "값 없음"입니다. 그대로 두면 아프리카 앞바다에 역이 생깁니다.
                location = if (lat != null && lng != null && lat != 0.0 && lng != 0.0) {
                    LatLng(lat, lng)
                } else {
                    null
                },
            )
        }

        // 2. 역정보 API를 기준으로 합치기 (이쪽이 799건으로 더 많음)
        val merged = LinkedHashMap<StationId, Station>()
        for (row in lineInfoRows) {
            val line = LineId.fromDisplayName(row.lineNum ?: "")
            val rawName = row.stationName
            if (line == null || rawName.isNullOrBlank()) {
                droppedLineInfo++
                continue
            }
            val name = StationNameNormalizer.normalize(rawName)
            val coordinate = coordinates[line to name]
            val code = row.stationCode?.trim()?.ifBlank { null }
                ?: coordinate?.code?.ifBlank { null }
                ?: continue

            val id = StationId(line, code)
            merged[id] = Station(
                id = id,
                name = name,
                displayName = coordinate?.displayName ?: rawName.trim(),
                frCode = row.frCode?.trim()?.ifBlank { null },
                location = coordinate?.location,
            )
        }

        // 3. 좌표 API에만 있는 역 채워 넣기
        val seen = merged.values.mapTo(HashSet()) { it.id.line to it.name }
        for ((key, coordinate) in coordinates) {
            val (line, name) = key
            if (key in seen) continue
            val code = coordinate.code.ifBlank { continue }
            val id = StationId(line, code)
            if (merged.containsKey(id)) continue
            seen += key
            merged[id] = Station(
                id = id,
                name = name,
                displayName = coordinate.displayName,
                location = coordinate.location,
            )
        }

        // 4. 환승 정보: 같은 이름이 여러 노선에 있으면 서로 환승역
        val byName = merged.values.groupBy { it.name }
        val withTransfers = merged.values.map { station ->
            val others = byName[station.name].orEmpty()
                .map { it.id.line }
                .filter { it != station.id.line }
                .distinct()
            if (others.isEmpty()) station else station.copy(transferLines = others)
        }

        return MergeReport(
            stations = withTransfers,
            droppedMasterRows = droppedMaster,
            droppedLineInfoRows = droppedLineInfo,
            withoutCoordinates = withTransfers.count { it.location == null },
        )
    }
}
