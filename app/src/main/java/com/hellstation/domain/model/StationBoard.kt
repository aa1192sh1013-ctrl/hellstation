package com.hellstation.domain.model

import java.time.Instant

/**
 * 역 하나·방향 하나에 대해 화면이 필요로 하는 모든 것.
 *
 * 역 Bottom Sheet와 결과 화면은 이것만 있으면 그려집니다.
 * 화면이 여러 저장소를 직접 조합하지 않도록 여기서 미리 합쳐 둡니다.
 *
 * @param upcoming        다가오는 열차들. 이미 도착 시간 보정과 정렬이 끝난 상태
 * @param headwaySeconds  관측된 배차 간격(초). 열차가 하나뿐이면 null
 * @param observedAt      이 묶음을 만든 시각
 * @param arrivalFailure  실시간 도착 정보를 **왜** 못 받았는가. 잘 받았으면 null.
 *
 *   실시간이 없어도 통계로 화면은 채워지므로 이 묶음 자체는 성공([Loadable.Ready])입니다.
 *   다만 그 사실을 여기 안 실어 두면 화면에서 **"지금 오는 열차가 없다"와 "신호가 끊겼다"가
 *   똑같아 보입니다.** 앞은 기다리면 되고 뒤는 다시 눌러야 하니, 사용자가 할 일이 다릅니다
 */
data class StationBoard(
    val station: Station,
    val direction: Direction,
    val crowd: CrowdIndex,
    val upcoming: List<TrainOption>,
    val verdict: RideOrWait,
    val serviceStatus: ServiceStatus,
    val headwaySeconds: Int?,
    val observedAt: Instant,
    val arrivalFailure: UnavailableReason? = null,
) {
    val current: TrainOption? get() = upcoming.firstOrNull()
    val next: TrainOption? get() = upcoming.getOrNull(1)

    /**
     * 데이터가 오래됐는가. 화면에 "정보가 오래됐습니다" 안내를 띄울지 결정합니다.
     * 지하철 안에서는 신호가 자주 끊기므로 자주 발생하는 정상 상황입니다.
     */
    fun isStale(now: Instant): Boolean =
        now.epochSecond - observedAt.epochSecond > STALE_AFTER_SECONDS

    companion object {
        const val STALE_AFTER_SECONDS = 120L
    }
}

/**
 * Heatmap 화면 한 장. 특정 시각의 역별 혼잡도 전체입니다.
 *
 * Time Slider를 움직이면 [at]만 바뀐 새 스냅샷이 옵니다.
 */
data class HeatmapSnapshot(
    val at: Instant,
    val entries: Map<StationId, CrowdIndex>,
    /** 이 스냅샷을 만드는 데 실제로 쓰인 가장 높은 데이터 계층 */
    val bestTier: DataTier,
) {
    val knownCount: Int get() = entries.count { it.value.level.isKnown }
    val unknownCount: Int get() = entries.size - knownCount

    fun levelOf(station: StationId): CrowdLevel =
        entries[station]?.level ?: CrowdLevel.UNKNOWN

    companion object {
        fun empty(at: Instant) = HeatmapSnapshot(at, emptyMap(), DataTier.NONE)
    }
}

/**
 * 화면이 로딩·실패·성공을 구분할 수 있게 감싸는 타입.
 *
 * **실패해도 화면은 멈추지 않습니다.** 대체 데이터가 있으면 [Ready]에 담아 내려보내고,
 * 정말 아무것도 없을 때만 [Unavailable]입니다.
 */
sealed interface Loadable<out T> {
    data object Loading : Loadable<Nothing>

    /** @param isFallback 대체 데이터로 채워졌는가. true면 화면에 안내가 필요합니다 */
    data class Ready<T>(val value: T, val isFallback: Boolean = false) : Loadable<T>

    data class Unavailable(val reason: UnavailableReason, val message: String) : Loadable<Nothing>
}

/** 데이터를 못 준 이유. 화면 문구를 고르는 데 씁니다. */
enum class UnavailableReason {
    /** 통신 실패 — 지하철 안에서 흔합니다 */
    NETWORK,

    /** 인증키가 없거나 sample 키라 조회 범위 밖입니다 */
    NO_KEY,

    /** 서울시 구간 밖이라 실시간 정보가 제공되지 않는 역입니다 */
    OUTSIDE_SEOUL,

    /** 운행 시간이 아닙니다 */
    CLOSED,

    /** 그 밖에 데이터가 없음 */
    NO_DATA,
}
