package com.hellstation.navigation

import kotlinx.serialization.Serializable

/**
 * 앱의 화면 목적지 정의.
 *
 * navigation-compose의 타입 안전 경로(type-safe route)를 씁니다.
 * 문자열 경로를 직접 만들거나 파싱하지 마세요 — 이 파일의 타입만 씁니다.
 *
 * ## 왜 인자가 전부 String인가
 *
 * navigation 계층은 domain 모델(StationId, LineId 등)을 **모릅니다**.
 * 의존 방향이 `navigation -> domain` 한 방향이면 화면 이동을 바꿀 때마다
 * 데이터 모델이 흔들리기 때문입니다. (docs/data-model.md 12절)
 *
 * 그래서 경로 인자는 원시 타입만 주고받고, 화면 안에서
 * `StationId(LineId.fromApiCode(lineCode)!!, stationCode)` 처럼 복원합니다.
 *
 * `lineCode`는 도착정보 API의 subwayId("1001"), `stationCode`는 역 코드("0150")입니다.
 */
sealed interface HellDestination {

    /** 앱을 열자마자 보이는 화면. 역 목록을 준비하는 동안만 잠깐 머뭅니다. */
    @Serializable
    data object Splash : HellDestination

    /**
     * 서울 지하철 전체 혼잡도 지도. **이 앱의 홈 화면입니다.**
     * 검색창보다 지도가 먼저 보여야 합니다.
     */
    @Serializable
    data object Heatmap : HellDestination

    /**
     * 역 검색.
     *
     * ## 출발역을 들고 다니는 이유
     *
     * 도착역을 고르는 화면에서는 **앞에서 고른 출발역을 기억하고 있어야** 합니다.
     * 예전에는 이 값이 없어서 출발역이 조용히 버려지고, 결과 화면에
     * "도착역 → 목적지 없음"이 뜨는 버그가 있었습니다.
     *
     * 화면 이동 계층은 domain 을 모르므로 원시 타입으로만 들고 다닙니다.
     * 아직 안 골랐으면 빈 문자열입니다.
     *
     * @param purpose 검색 결과를 어디에 쓸 것인가. [SearchPurpose] 값의 이름을 담습니다
     * @param originLineCode 앞에서 고른 출발역의 노선 코드. 없으면 빈 문자열
     * @param originStationCode 앞에서 고른 출발역의 역 코드. 없으면 빈 문자열
     */
    @Serializable
    data class Search(
        val purpose: String,
        val originLineCode: String = "",
        val originStationCode: String = "",
    ) : HellDestination {
        val hasOrigin: Boolean get() = originLineCode.isNotBlank() && originStationCode.isNotBlank()
    }

    /**
     * 역 하나의 상세 정보. Heatmap 위에 Bottom Sheet로 올라옵니다.
     */
    @Serializable
    data class StationDetail(
        val lineCode: String,
        val stationCode: String,
    ) : HellDestination

    /**
     * 설정.
     *
     * 취향(테마·기본 방향)뿐 아니라 **데이터 출처 표기**가 여기 있습니다.
     * 서울교통공사 혼잡도 통계가 공공누리 제3유형이라 출처 표시가 의무입니다.
     */
    @Serializable
    data object Settings : HellDestination

    /**
     * 출발역 -> 도착역의 "지금 탈까 기다릴까" 결과.
     */
    @Serializable
    data class Result(
        val originLineCode: String,
        val originStationCode: String,
        val destinationLineCode: String,
        val destinationStationCode: String,
    ) : HellDestination
}

/** 검색 화면을 무엇 때문에 열었는지. */
enum class SearchPurpose {
    /** 그냥 역을 찾아보는 중. 고르면 역 상세로 갑니다 */
    BROWSE,

    /** 경로의 출발역을 고르는 중 */
    ORIGIN,

    /** 경로의 도착역을 고르는 중 */
    DESTINATION,
    ;

    companion object {
        /** 경로 인자로 온 문자열을 되돌립니다. 모르는 값이면 [BROWSE]. */
        fun parse(raw: String): SearchPurpose =
            entries.firstOrNull { it.name == raw } ?: BROWSE
    }
}
