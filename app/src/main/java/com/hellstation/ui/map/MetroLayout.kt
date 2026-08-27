package com.hellstation.ui.map

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import com.hellstation.domain.model.LineId
import com.hellstation.domain.model.Station
import com.hellstation.domain.model.StationId
import kotlin.math.pow

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

            val raw = located.associate { station ->
                val location = station.location!!
                val x = (location.longitude - minLng) / span
                // 위도가 클수록 북쪽이므로 화면에서는 위로 => y를 뒤집습니다.
                val y = (maxLat - location.latitude) * latToLngRatio / span
                station.id to Offset(x.toFloat(), y.toFloat())
            }
            return MetroLayout(stations, spreadCenter(raw), lines)
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
 * 도심을 펴고 외곽을 접습니다.
 *
 * ## 왜 필요한가
 *
 * 위경도를 그대로 쓰면 **서울 도심 200여 역이 한 덩어리로 뭉칩니다.** 수도권 전철은
 * 도심 반경 5km 안에 역이 빽빽하고 외곽으로는 수십 km를 뻗기 때문입니다.
 * 실제 노선도가 지리적으로 정확하지 않은 것도 같은 이유입니다 — 정확하게 그리면 못 읽습니다.
 *
 * ## 어떻게 하나
 *
 * 한가운데에서의 거리 `r`만 `r^[SPREAD_POWER]`로 바꿉니다. 1보다 작은 지수를 쓰면
 * 가까운 곳은 크게 밀려나고 먼 곳은 덜 밀려나서, **도심이 펴지고 외곽이 접힙니다.**
 * 방향(각도)은 건드리지 않으므로 어느 역이 어느 쪽에 있는지는 그대로입니다.
 *
 * 한가운데는 **역들의 중앙값**으로 잡습니다. 평균을 쓰면 외곽 종점 몇 개가 중심을 끌고 갑니다.
 */
private fun spreadCenter(raw: Map<StationId, Offset>): Map<StationId, Offset> {
    if (raw.size < 3) return raw

    val xs = raw.values.map { it.x }.sorted()
    val ys = raw.values.map { it.y }.sorted()
    val center = Offset(xs[xs.size / 2], ys[ys.size / 2])

    val maxRadius = raw.values.maxOf { (it - center).getDistance() }
    if (maxRadius <= 0f) return raw

    val spread = raw.mapValues { (_, point) ->
        val delta = point - center
        val radius = delta.getDistance()
        if (radius <= 0f) {
            center
        } else {
            val stretched = (radius / maxRadius).pow(SPREAD_POWER) * maxRadius
            center + delta / radius * stretched
        }
    }

    // 펴고 나면 범위가 달라집니다. 다시 0~1 로 맞춰 화면을 꽉 채웁니다.
    val minX = spread.values.minOf { it.x }
    val minY = spread.values.minOf { it.y }
    val width = spread.values.maxOf { it.x } - minX
    val height = spread.values.maxOf { it.y } - minY
    val scale = 1f / maxOf(width, height).coerceAtLeast(0.0001f)

    return spread.mapValues { (_, point) ->
        Offset((point.x - minX) * scale, (point.y - minY) * scale)
    }
}

/**
 * 도심을 얼마나 펼 것인가. 1이면 그대로, 작을수록 도심이 크게 펴집니다.
 *
 * 0.55는 눈으로 맞춘 값입니다. 더 낮추면 도심은 시원해지지만 외곽 노선이
 * 실제보다 훨씬 짧아 보여서 "우리 동네가 서울 바로 옆인가" 싶어집니다.
 */
private const val SPREAD_POWER = 0.55f

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
