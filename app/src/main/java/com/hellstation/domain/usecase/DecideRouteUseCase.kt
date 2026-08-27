package com.hellstation.domain.usecase

import com.hellstation.domain.model.Direction
import com.hellstation.domain.model.Loadable
import com.hellstation.domain.model.Station
import com.hellstation.domain.model.StationBoard
import com.hellstation.domain.model.StationId
import com.hellstation.domain.model.UnavailableReason
import com.hellstation.domain.repository.SubwayNetworkRepository
import java.time.Instant

/**
 * 출발역과 도착역을 받아 "지금 탈까 기다릴까"를 냅니다.
 *
 * @param directionIsGuess 방향을 추측했는가. true면 화면에 방향 전환 버튼을 보여 주세요
 */
data class RouteAdvice(
    val origin: Station,
    val destination: Station?,
    val direction: Direction,
    val directionIsGuess: Boolean,
    val board: StationBoard,
)

/**
 * 경로 기준 판단.
 *
 * 판단 자체는 **출발역에서** 이루어집니다. 사용자는 출발역 플랫폼에 서 있고,
 * 지금 들어오는 열차를 탈지 말지를 정해야 하기 때문입니다.
 * 도착역은 **어느 방향 열차를 봐야 하는지**를 정하는 데만 쓰입니다.
 *
 * ## 방향 추론이 추측인 이유
 *
 * 노선의 역 순서를 아직 만들지 못했습니다(정식 인증키가 있어야 `statnFid`/`statnTid`로
 * 노선 전체를 이어붙일 수 있습니다 — `SubwayNetworkRepositoryImpl.segmentsOf` 참고).
 *
 * 대신 **역 코드 번호**로 추측합니다. 서울 지하철 역 코드는 노선을 따라 차례로 매겨져 있어서
 * 도착역 번호가 출발역보다 크면 하행, 작으면 상행인 경우가 대부분입니다.
 *
 * **틀릴 수 있습니다.** 특히 2호선(순환선)과 지선이 있는 노선에서 그렇습니다.
 * 그래서 [RouteAdvice.directionIsGuess]로 표시하고, 화면에서 사용자가 뒤집을 수 있어야 합니다.
 */
class DecideRouteUseCase(
    private val network: SubwayNetworkRepository,
    private val stationBoard: GetStationBoardUseCase,
) {

    suspend operator fun invoke(
        originId: StationId,
        destinationId: StationId?,
        at: Instant = Instant.now(),
        directionOverride: Direction? = null,
    ): Loadable<RouteAdvice> {
        network.warmUp()

        val origin = network.station(originId)
            ?: return Loadable.Unavailable(UnavailableReason.NO_DATA, "출발역을 찾지 못했습니다")
        val destination = destinationId?.let { network.station(it) }

        val guessed = directionOverride == null
        val direction = directionOverride
            ?: inferDirection(origin, destination)
            ?: Direction.UP

        return when (val board = stationBoard(originId, direction, at)) {
            is Loadable.Unavailable -> board
            is Loadable.Loading -> Loadable.Loading
            is Loadable.Ready -> Loadable.Ready(
                RouteAdvice(
                    origin = origin,
                    destination = destination,
                    direction = direction,
                    directionIsGuess = guessed,
                    board = board.value,
                ),
                isFallback = board.isFallback,
            )
        }
    }

    /**
     * 역 코드 번호로 방향을 추측합니다. 확신할 수 없으면 null.
     *
     * 다른 노선이면 환승이 필요하다는 뜻이라 여기서는 판단하지 않습니다.
     * 2호선은 순환선이라 번호 비교가 통하지 않습니다.
     */
    internal fun inferDirection(origin: Station, destination: Station?): Direction? {
        if (destination == null) return null
        if (origin.id.line != destination.id.line) return null
        if (origin.id.line.isLoop) return null

        val from = origin.id.stationCode.filter { it.isDigit() }.toIntOrNull() ?: return null
        val to = destination.id.stationCode.filter { it.isDigit() }.toIntOrNull() ?: return null
        return when {
            to > from -> Direction.DOWN
            to < from -> Direction.UP
            else -> null
        }
    }
}
