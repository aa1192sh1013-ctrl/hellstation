package com.hellstation.domain

import com.hellstation.data.local.baseline.ApproximateBaselineSource
import com.hellstation.data.local.cache.NetworkTopology
import com.hellstation.data.local.cache.StationCatalog
import com.hellstation.domain.model.BaselineKey
import com.hellstation.domain.model.BaselineQuality
import com.hellstation.domain.model.CrowdLevel
import com.hellstation.domain.model.DayType
import com.hellstation.domain.model.Direction
import com.hellstation.domain.model.LineId
import com.hellstation.domain.model.Station
import com.hellstation.domain.model.StationId
import com.hellstation.domain.model.TimeSlot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 통계가 없는 역의 혼잡도를 어림하는 부분.
 *
 * **여기가 깨지면 지도가 단색이 됩니다.** 실제로 한 번 그렇게 만들었다가 잡은 버그라
 * 그 조건을 테스트로 박아 둡니다.
 */
class NetworkAndBaselineTest {

    // ── 시험용 노선망 ───────────────────────────────────────────────────────

    private fun station(
        line: LineId,
        index: Int,
        name: String,
        transfers: List<LineId> = emptyList(),
    ) = Station(
        id = StationId(line, "%03d".format(index)),
        name = name,
        displayName = name,
        transferLines = transfers,
    )

    /** 1호선 9역 + 2호선 5역. 가운데 "중앙"이 환승역입니다. */
    private fun sampleCatalog(): StationCatalog {
        val line1 = (0 until 9).map { index ->
            val name = if (index == 4) "중앙" else "일_$index"
            station(LineId.LINE_1, index, name, if (index == 4) listOf(LineId.LINE_2) else emptyList())
        }
        val line2 = (0 until 5).map { index ->
            val name = if (index == 2) "중앙" else "이_$index"
            station(LineId.LINE_2, index, name, if (index == 2) listOf(LineId.LINE_1) else emptyList())
        }
        return StationCatalog(line1 + line2)
    }

    private fun topology() = NetworkTopology(
        sampleCatalog(),
        ApproximateBaselineSource::lineWeightOf,
    )

    // ── 노선 순서 ───────────────────────────────────────────────────────────

    @Test
    fun `역 번호 순으로 노선을 세운다`() {
        val ordered = topology().orderedStations(LineId.LINE_1)
        assertEquals(9, ordered.size)
        assertEquals(listOf("000", "001", "002", "003", "004", "005", "006", "007", "008"),
            ordered.map { it.id.stationCode })
    }

    @Test
    fun `환승역을 도심으로 본다`() {
        val profile = topology().profileOf(StationId(LineId.LINE_1, "004"))
        assertNotNull(profile)
        // 1호선에서 환승이 있는 역은 index 4 뿐입니다.
        assertEquals(4, profile!!.coreIndexOnLine)
        assertEquals(1f, profile.centrality, 0.001f)
    }

    @Test
    fun `구간은 방향마다 따로 만들어진다`() {
        val segments = topology().segmentsOf(LineId.LINE_1)
        // 역 9개 -> 인접 쌍 8개 -> 방향까지 16개 (순환선이 아니므로 되돌아오는 구간 없음)
        assertEquals(16, segments.size)
        assertTrue(segments.any { it.from.stationCode == "000" && it.to.stationCode == "001" })
        assertTrue(segments.any { it.from.stationCode == "001" && it.to.stationCode == "000" })
        assertTrue(segments.all { it.isConsistent })
    }

    @Test
    fun `옆 역을 방향에 맞게 찾는다`() {
        val topology = topology()
        val here = StationId(LineId.LINE_1, "004")
        assertEquals("005", topology.neighbor(here, Direction.DOWN)?.id?.stationCode)
        assertEquals("003", topology.neighbor(here, Direction.UP)?.id?.stationCode)
        // 종점 밖으로는 나가지 않습니다.
        assertNull(topology.neighbor(StationId(LineId.LINE_1, "000"), Direction.UP))
    }

    // ── 붐빌 만한 정도의 순위 ───────────────────────────────────────────────

    @Test
    fun `순위는 0과 1을 모두 채운다`() {
        val topology = topology()
        val percentiles = sampleCatalog().stations
            .mapNotNull { topology.profileOf(it.id)?.busynessPercentile }

        assertEquals(14, percentiles.size)
        assertEquals(0f, percentiles.min(), 0.001f)
        assertEquals(1f, percentiles.max(), 0.001f)
    }

    @Test
    fun `환승역이 종점보다 높은 순위를 받는다`() {
        val topology = topology()
        val hub = topology.profileOf(StationId(LineId.LINE_1, "004"))!!
        val terminal = topology.profileOf(StationId(LineId.LINE_1, "000"))!!
        assertTrue(
            "환승역(${hub.busynessPercentile}) 이 종점(${terminal.busynessPercentile}) 보다 높아야 합니다",
            hub.busynessPercentile > terminal.busynessPercentile,
        )
    }

    // ── 어림 통계 ───────────────────────────────────────────────────────────

    private fun sourceOver(topology: NetworkTopology) =
        ApproximateBaselineSource(profileOf = { id -> topology.profileOf(id) })

    private fun key(id: StationId, direction: Direction, slot: TimeSlot) =
        BaselineKey(id, direction, DayType.WEEKDAY, slot)

    @Test
    fun `어림값은 언제나 APPROXIMATED 다`() = runTest {
        val topology = topology()
        val sample = sourceOver(topology)
            .sample(key(StationId(LineId.LINE_1, "004"), Direction.UP, TimeSlot(8 * 60)))
        assertNotNull(sample)
        assertEquals(BaselineQuality.APPROXIMATED, sample!!.quality)
    }

    @Test
    fun `운행 시간 밖에는 값이 없다`() = runTest {
        val sample = sourceOver(topology())
            .sample(key(StationId(LineId.LINE_1, "004"), Direction.UP, TimeSlot(3 * 60)))
        assertNull(sample)
    }

    /**
     * **가장 중요한 테스트.**
     *
     * 한때 모든 역이 같은 값을 받아 지도가 통째로 한 색이 된 적이 있습니다.
     * 역마다 값이 갈리는지 확인합니다.
     */
    @Test
    fun `역마다 혼잡도가 다르게 나온다`() = runTest {
        val topology = topology()
        val source = sourceOver(topology)
        val slot = TimeSlot(8 * 60)

        val values = sampleCatalog().stations.map { station ->
            source.sample(key(station.id, Direction.UP, slot))!!.percent
        }

        val distinct = values.distinct()
        assertTrue("역 ${values.size}개인데 값이 ${distinct.size}종뿐입니다", distinct.size >= values.size - 1)

        val spread = values.max() - values.min()
        assertTrue("가장 붐비는 역과 한산한 역의 차이가 ${spread}%p 로 너무 작습니다", spread > 40.0)
    }

    @Test
    fun `출근 시간에는 등급이 여러 개로 갈린다`() = runTest {
        val topology = topology()
        val source = sourceOver(topology)
        val slot = TimeSlot(8 * 60)

        val levels = sampleCatalog().stations.map { station ->
            // 지도 기본값과 같은 규칙: 두 방향 중 나쁜 쪽
            val up = source.sample(key(station.id, Direction.UP, slot))!!.percent
            val down = source.sample(key(station.id, Direction.DOWN, slot))!!.percent
            CrowdLevel.fromPercent(maxOf(up, down))
        }.distinct()

        assertTrue("출근 시간인데 등급이 ${levels.size}종뿐입니다: $levels", levels.size >= 2)
    }

    @Test
    fun `출근 시간에는 도심 방향이 더 붐빈다`() = runTest {
        val topology = topology()
        val source = sourceOver(topology)
        // index 0 은 도심(index 4)보다 바깥이므로 순번이 커지는 DOWN 이 도심 방향입니다.
        val outer = StationId(LineId.LINE_1, "000")
        val morning = TimeSlot(8 * 60)

        val inbound = source.sample(key(outer, Direction.DOWN, morning))!!.percent
        val outbound = source.sample(key(outer, Direction.UP, morning))!!.percent
        assertTrue("도심 방향($inbound) 이 반대($outbound) 보다 붐벼야 합니다", inbound > outbound)
    }

    @Test
    fun `퇴근 시간에는 방향이 뒤집힌다`() = runTest {
        val source = sourceOver(topology())
        val outer = StationId(LineId.LINE_1, "000")
        val evening = TimeSlot(18 * 60 + 30)

        val inbound = source.sample(key(outer, Direction.DOWN, evening))!!.percent
        val outbound = source.sample(key(outer, Direction.UP, evening))!!.percent
        assertTrue("퇴근에는 바깥 방향($outbound) 이 도심 방향($inbound) 보다 붐벼야 합니다", outbound > inbound)
    }

    @Test
    fun `주말에는 출퇴근 쏠림이 없다`() = runTest {
        val source = sourceOver(topology())
        val outer = StationId(LineId.LINE_1, "000")
        val morning = TimeSlot(8 * 60)

        val up = source.sample(BaselineKey(outer, Direction.UP, DayType.SUNDAY, morning))!!.percent
        val down = source.sample(BaselineKey(outer, Direction.DOWN, DayType.SUNDAY, morning))!!.percent
        assertEquals(up, down, 0.001)
    }

    @Test
    fun `같은 역을 여러 번 물어도 같은 값이 나온다`() = runTest {
        val source = sourceOver(topology())
        val id = StationId(LineId.LINE_1, "003")
        val slot = TimeSlot(12 * 60)

        val first = source.sample(key(id, Direction.UP, slot))!!.percent
        val second = source.sample(key(id, Direction.UP, slot))!!.percent
        // 난수를 쓰면 화면을 갱신할 때마다 색이 바뀝니다.
        assertEquals(first, second, 0.0)
    }

    @Test
    fun `시간대가 바뀌면 값도 바뀐다`() = runTest {
        val source = sourceOver(topology())
        val id = StationId(LineId.LINE_1, "004")

        val morning = source.sample(key(id, Direction.UP, TimeSlot(8 * 60)))!!.percent
        val midday = source.sample(key(id, Direction.UP, TimeSlot(12 * 60)))!!.percent
        val night = source.sample(key(id, Direction.UP, TimeSlot(23 * 60)))!!.percent

        assertTrue("출근($morning) 이 낮($midday) 보다 붐벼야 합니다", morning > midday)
        assertTrue("낮($midday) 이 밤($night) 보다 붐벼야 합니다", midday > night)
    }

    @Test
    fun `역을 모를 때도 값을 낸다`() = runTest {
        // profileOf 가 null 을 돌려줘도 곡선 값은 나와야 합니다. 앱이 멈추면 안 됩니다.
        val source = ApproximateBaselineSource()
        val sample = source.sample(
            key(StationId(LineId.LINE_1, "없는역"), Direction.UP, TimeSlot(8 * 60))
        )
        assertNotNull(sample)
        assertTrue(sample!!.percent > 0)
    }
}
