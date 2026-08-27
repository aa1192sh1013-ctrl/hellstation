package com.hellstation.domain.repository

import com.hellstation.domain.model.Arrival
import com.hellstation.domain.model.BaselineKey
import com.hellstation.domain.model.BaselineSample
import com.hellstation.domain.model.CrowdIndex
import com.hellstation.domain.model.Direction
import com.hellstation.domain.model.HeatmapSnapshot
import com.hellstation.domain.model.LineId
import com.hellstation.domain.model.Loadable
import com.hellstation.domain.model.Segment
import com.hellstation.domain.model.Station
import com.hellstation.domain.model.StationId
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * ## 실패 처리 원칙 (이 파일의 모든 인터페이스에 적용)
 *
 * **네트워크 실패가 화면을 멈추게 하면 안 됩니다.**
 * 구현체는 예외를 밖으로 던지지 말고, 데이터가 없는 상태
 * (`UNKNOWN`, 빈 리스트, [Loadable.Unavailable])를 돌려주세요.
 *
 * 지하철 안에서 신호가 끊기는 것은 예외 상황이 아니라 **정상 상황**입니다.
 */

/** 역·노선 같은 잘 변하지 않는 정보. 앱 시작 시 한 번 로드해 캐싱합니다. */
interface SubwayNetworkRepository {

    /** 캐시를 채웁니다. 이미 채워져 있으면 아무것도 하지 않습니다. */
    suspend fun warmUp(): Loadable<Unit>

    suspend fun allLines(): List<LineId>

    suspend fun stationsOf(line: LineId): List<Station>

    /** 지도에 찍을 수 있는(좌표가 있는) 역 전체. */
    suspend fun mappableStations(): List<Station>

    suspend fun segmentsOf(line: LineId): List<Segment>

    /** 역명 일부로 찾기. 정규화된 이름과 표시 이름 양쪽을 봅니다. */
    suspend fun findStationsByName(query: String): List<Station>

    suspend fun station(id: StationId): Station?

    /** 같은 이름의 역을 노선별로 모두 찾습니다(환승역). */
    suspend fun stationsNamed(name: String): List<Station>
}

/** 실시간 도착정보. */
interface ArrivalRepository {

    /**
     * 역명으로 도착 정보를 받습니다.
     *
     * 도착정보 API는 역명 기준이라 환승역이면 여러 노선이 섞여 옵니다.
     * 노선·방향 필터링은 호출한 쪽에서 하세요.
     *
     * 실패하면 예외 대신 [Loadable.Unavailable]을 돌려줍니다.
     */
    suspend fun arrivalsAt(stationName: String): Loadable<List<Arrival>>
}

/** 혼잡도 통계 기준선. CSV든 근사값이든 이 인터페이스 뒤에 숨습니다. */
interface BaselineSource {

    /** 값이 없으면 null. 예외를 던지지 않습니다. */
    suspend fun sample(key: BaselineKey): BaselineSample?

    /** 이 소스가 실측 데이터를 들고 있는가. 화면 안내 문구를 고르는 데 씁니다. */
    suspend fun hasMeasuredData(): Boolean
}

/** 혼잡도 판정. */
interface CrowdRepository {

    /** 특정 역·방향·시각의 혼잡도. 근거가 없으면 [CrowdIndex.unknown]. */
    suspend fun crowdAt(station: StationId, direction: Direction, at: Instant): CrowdIndex

    /** Heatmap 화면용. 화면에 보이는 역 전체를 한 번에 받습니다. */
    fun heatmap(at: Instant, direction: Direction? = null): Flow<HeatmapSnapshot>
}
