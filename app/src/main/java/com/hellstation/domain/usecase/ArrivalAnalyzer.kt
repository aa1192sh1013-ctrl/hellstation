package com.hellstation.domain.usecase

import com.hellstation.domain.model.Arrival
import com.hellstation.domain.model.DayType
import com.hellstation.domain.model.Direction
import com.hellstation.domain.model.LineId
import com.hellstation.domain.model.ServiceStatus
import com.hellstation.domain.model.TimeSlot
import java.time.Instant

/**
 * 도착 정보 묶음에서 배차 간격과 운행 상태를 읽어냅니다.
 *
 * 도착정보 API는 "지연"이라는 필드를 주지 않습니다(docs/api-validation.md).
 * 그래서 여기서 **추정**합니다. 추정 결과는 혼잡도 신뢰도를 낮추는 데 쓰입니다.
 */
object ArrivalAnalyzer {

    /** 지연·중단을 뜻하는 안내 문구 조각. `arvlMsg2`에 섞여 옵니다. */
    private val TROUBLE_KEYWORDS = listOf("지연", "중단", "운행중지", "지장", "사고")

    /**
     * 한 역·한 방향의 도착 정보를 남은 시간 순으로 정렬합니다.
     * 남은 시간을 알 수 없는 건은 뒤로 밀되 버리지는 않습니다 — 열차가 있다는 사실 자체가 정보입니다.
     */
    fun sortByEta(arrivals: List<Arrival>, now: Instant): List<Arrival> =
        arrivals.sortedWith(
            compareBy(
                { it.secondsUntilArrival(now) == null },
                { it.secondsUntilArrival(now) ?: Int.MAX_VALUE },
            )
        )

    /**
     * 노선·방향으로 거릅니다. 환승역은 한 번의 호출로 여러 노선이 섞여 옵니다.
     *
     * **이미 떠난 열차는 여기서 빠집니다.** 실제 응답에는 `arvlMsg2="서울 출발"`인 건이
     * 섞여 오는데, 그걸 "지금 들어오는 열차"로 다루면 이미 지나간 열차를 타라고 안내하게 됩니다.
     */
    fun filter(arrivals: List<Arrival>, line: LineId, direction: Direction): List<Arrival> =
        arrivals.filter {
            it.train.line == line && it.train.direction == direction && it.isBoardable
        }

    /**
     * 관측된 배차 간격(초). 남은 시간을 아는 열차가 둘 이상 있어야 계산됩니다.
     *
     * 첫 열차와 둘째 열차의 도착 시각 차이입니다.
     */
    fun headwaySeconds(sortedArrivals: List<Arrival>, now: Instant): Int? {
        val etas = sortedArrivals.mapNotNull { it.secondsUntilArrival(now) }
        if (etas.size < 2) return null
        val gap = etas[1] - etas[0]
        return if (gap > 0) gap else null
    }

    /**
     * 운행 상태를 추정합니다.
     *
     * @param sortedArrivals [sortByEta]를 거친 목록
     * @param slot           지금 시각이 속한 통계 시간대
     */
    fun serviceStatus(
        sortedArrivals: List<Arrival>,
        now: Instant,
        slot: TimeSlot,
        dayType: DayType,
    ): ServiceStatus {
        if (!slot.isWithinServiceHours) return ServiceStatus.CLOSED
        if (sortedArrivals.isEmpty()) return ServiceStatus.SUSPENDED

        val hasTroubleMessage = sortedArrivals.any { arrival ->
            TROUBLE_KEYWORDS.any { keyword -> arrival.message.contains(keyword) }
        }
        if (hasTroubleMessage) return ServiceStatus.DELAYED

        val headway = headwaySeconds(sortedArrivals, now)
        if (headway != null) {
            val nominal = NominalHeadway.secondsAt(slot, dayType)
            if (headway > nominal * NominalHeadway.DELAY_RATIO_THRESHOLD) {
                return ServiceStatus.DELAYED
            }
        }
        return ServiceStatus.NORMAL
    }

    /**
     * 실시간 데이터가 만들어진 지 몇 초 지났나. 가장 **오래된** 건을 기준으로 잡습니다.
     *
     * 같은 응답 안에서도 `recptnDt`가 최대 몇 분씩 차이 납니다(docs/api-validation.md).
     * 낙관적으로 잡으면 신뢰도를 과대평가하게 됩니다.
     */
    fun worstDataAgeSeconds(arrivals: List<Arrival>, now: Instant): Long? =
        arrivals.maxOfOrNull { it.dataAgeSeconds(now) }
}
