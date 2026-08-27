package com.hellstation.ui.map

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import com.hellstation.domain.model.LineId
import com.hellstation.domain.model.Station
import com.hellstation.domain.model.StationId

/**
 * 노선도를 그리기 위한 배치 정보.
 *
 * ## 왜 위경도를 그대로 쓰지 않나
 *
 * 실제 노선도는 지리적으로 정확하지 않습니다. 역 간격을 고르게 펴고 선을 곧게 펴야
 * 읽을 수 있기 때문입니다. 그래서 화면 좌표를 따로 둡니다.
 *
 * [positions]는 **0~1로 정규화된 좌표**입니다. 화면 크기와 확대 배율은 그리는 쪽이 곱합니다.
 * y는 아래로 갈수록 큽니다(화면 좌표계).
 *
 * 지금은 [com.hellstation.ui.sample.SampleMetro]가 손으로 배치한 도식 좌표를 넣습니다.
 * 실제 API 좌표를 쓰게 되면 [fromGeo]로 만들면 됩니다 — 그리는 코드는 그대로입니다.
 */
@Immutable
data class MetroLayout(
    val stations: List<Station>,
    val positions: Map<StationId, Offset>,
    val lines: List<MetroLineShape>,
) {
    private val byId: Map<StationId, Station> = stations.associateBy { it.id }

    /**
     * 역이 실제로 차지하는 정규화 좌표 범위.
     *
     * 도식 좌표든 위경도 좌표든 0~1 정사각형을 꽉 채우지는 않습니다. 손으로 잡은 도식은
     * 가로 76% · 세로 60%만 씁니다. 그리는 쪽이 정사각형 전체를 화면에 맞추면 그 빈 여백까지
     * 자리를 차지해서 지도가 실제보다 작게 나옵니다. 그래서 내용의 진짜 경계를 따로 알려 줍니다.
     */
    val bounds: MetroBounds = MetroBounds.of(positions.values)

    fun station(id: StationId): Station? = byId[id]

    fun positionOf(id: StationId): Offset? = positions[id]

    /**
     * 화면 좌표에서 가장 가까운 역. 손가락으로 누른 지점 근처의 역을 찾는 데 씁니다.
     *
     * @param maxDistance 이보다 멀면 아무 역도 안 누른 것으로 봅니다(정규화 단위)
     */
    fun nearestTo(point: Offset, maxDistance: Float): Station? {
        var best: Station? = null
        var bestDistanceSquared = maxDistance * maxDistance

        for (station in stations) {
            val position = positions[station.id] ?: continue
            val dx = position.x - point.x
            val dy = position.y - point.y
            val distanceSquared = dx * dx + dy * dy
            if (distanceSquared <= bestDistanceSquared) {
                bestDistanceSquared = distanceSquared
                best = station
            }
        }
        return best
    }

    companion object {
        /**
         * 실제 위경도로 배치를 만듭니다. **3단계에서 실제 데이터를 붙일 때 쓰세요.**
         *
         * 서울 정도의 좁은 범위에서는 위경도를 그냥 선형으로 펴도 눈에 띄는 왜곡이 없습니다.
         * 다만 위도 1도와 경도 1도의 실제 거리가 다르므로 세로를 보정합니다
         * (서울 위도에서 경도 1도는 위도 1도의 약 0.81배).
         *
         * 좌표가 없는 역은 결과에서 빠집니다 — 약 15개 역이 여기 해당합니다.
         */
        fun fromGeo(
            stations: List<Station>,
            lines: List<MetroLineShape>,
        ): MetroLayout {
            val located = stations.filter { it.location != null }
            if (located.isEmpty()) return MetroLayout(stations, emptyMap(), lines)

            val latitudes = located.map { it.location!!.latitude }
            val longitudes = located.map { it.location!!.longitude }
            val minLat = latitudes.min()
            val maxLat = latitudes.max()
            val minLng = longitudes.min()
            val maxLng = longitudes.max()

            val lngSpan = (maxLng - minLng).takeIf { it > 0 } ?: 1.0
            val latSpan = (maxLat - minLat).takeIf { it > 0 } ?: 1.0

            // 가로세로 비율을 지키려면 넓은 쪽에 맞춰 같은 배율을 씁니다.
            val latToLngRatio = 1.0 / 0.81
            val scaledLatSpan = latSpan * latToLngRatio
            val span = maxOf(lngSpan, scaledLatSpan)

            val positions = located.associate { station ->
                val location = station.location!!
                val x = (location.longitude - minLng) / span
                // 위도가 클수록 북쪽이므로 화면에서는 위로 => y를 뒤집습니다.
                val y = (maxLat - location.latitude) * latToLngRatio / span
                station.id to Offset(x.toFloat(), y.toFloat())
            }
            return MetroLayout(stations, positions, lines)
        }
    }
}

/**
 * 노선도 내용이 차지하는 정규화 좌표 범위.
 *
 * 가로세로 비율을 어떻게 지킬지는 그리는 쪽이 정합니다. 여기서는 범위만 알려 줍니다.
 */
@Immutable
data class MetroBounds(
    val center: Offset,
    val width: Float,
    val height: Float,
) {
    companion object {
        /** 좌표가 하나도 없을 때 쓰는 값. 정규화 공간 전체입니다. */
        val WHOLE = MetroBounds(Offset(0.5f, 0.5f), 1f, 1f)

        fun of(points: Collection<Offset>): MetroBounds {
            if (points.isEmpty()) return WHOLE

            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE
            var maxY = -Float.MAX_VALUE
            for (point in points) {
                if (point.x < minX) minX = point.x
                if (point.x > maxX) maxX = point.x
                if (point.y < minY) minY = point.y
                if (point.y > maxY) maxY = point.y
            }

            // 역이 한 점에 몰려 있으면 폭이 0이 됩니다. 나중에 나눗셈이 터지지 않게 막습니다.
            return MetroBounds(
                center = Offset((minX + maxX) / 2f, (minY + maxY) / 2f),
                width = (maxX - minX).coerceAtLeast(MIN_SPAN),
                height = (maxY - minY).coerceAtLeast(MIN_SPAN),
            )
        }

        private const val MIN_SPAN = 0.001f
    }
}

/**
 * 한 노선의 역 순서. 노선도의 선은 이 순서대로 역을 이어 그립니다.
 *
 * @param isLoop 2호선처럼 마지막 역이 첫 역으로 돌아오는가
 */
@Immutable
data class MetroLineShape(
    val line: LineId,
    val stationIds: List<StationId>,
    val isLoop: Boolean = false,
)
