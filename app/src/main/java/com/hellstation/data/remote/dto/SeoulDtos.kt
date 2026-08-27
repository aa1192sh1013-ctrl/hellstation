package com.hellstation.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 서울 열린데이터광장 API 응답을 **생긴 그대로** 옮긴 타입들.
 *
 * 여기에 계산 로직을 넣지 마세요. domain 타입으로 바꾸는 일은 mapper가 합니다.
 * 필드가 거의 다 nullable인 이유는 API가 실제로 null과 빈 문자열을 섞어 보내기 때문입니다.
 *
 * 실제 응답 예시는 docs/api-validation.md에 있습니다.
 */

// ── 실시간 도착정보 (realtimeStationArrival) ────────────────────────────────

@Serializable
data class ArrivalResponse(
    val errorMessage: ErrorMessageDto? = null,
    val realtimeArrivalList: List<ArrivalDto>? = null,
    // 에러일 때는 위 구조 대신 아래 필드들이 최상위에 옵니다.
    val status: Int? = null,
    val code: String? = null,
    val message: String? = null,
) {
    /** 성공/실패에 관계없이 응답 코드를 꺼냅니다. */
    val resultCode: String? get() = errorMessage?.code ?: code
    val resultMessage: String? get() = errorMessage?.message ?: message
}

@Serializable
data class ErrorMessageDto(
    val status: Int? = null,
    val code: String? = null,
    val message: String? = null,
    val total: Int? = null,
)

@Serializable
data class ArrivalDto(
    /** 호선 코드. "1001" */
    val subwayId: String? = null,
    /** "상행" / "하행" / "내선" / "외선" */
    val updnLine: String? = null,
    /** "양주행 - 시청방면" */
    val trainLineNm: String? = null,
    /** 이전 역 ID */
    val statnFid: String? = null,
    /** 다음 역 ID */
    val statnTid: String? = null,
    /** 이 역 ID. "1001000133" */
    val statnId: String? = null,
    /** 역명. "역" 접미사 없음 */
    val statnNm: String? = null,
    /** 환승 노선 수 */
    val trnsitCo: String? = null,
    /** "일반" / "급행" / "ITX" */
    val btrainSttus: String? = null,
    /** 도착까지 남은 초. **"0"이 자주 옵니다** */
    val barvlDt: String? = null,
    /** 열차 번호 */
    val btrainNo: String? = null,
    /** 종착역 ID */
    val bstatnId: String? = null,
    /** 종착역 이름 */
    val bstatnNm: String? = null,
    /** 데이터 생성 시각. "2026-08-22 11:35:50" */
    val recptnDt: String? = null,
    /** 표시 문구. "3분 후 (시청)" */
    val arvlMsg2: String? = null,
    val arvlMsg3: String? = null,
    /** 도착 코드. "0"~"5", "99" */
    val arvlCd: String? = null,
    /** 막차면 "1" */
    val lstcarAt: String? = null,
)

// ── 지하철역 좌표 (subwayStationMaster) ─────────────────────────────────────

@Serializable
data class StationMasterResponse(
    val subwayStationMaster: StationMasterBody? = null,
    @SerialName("RESULT") val result: SeoulResultDto? = null,
)

@Serializable
data class StationMasterBody(
    @SerialName("list_total_count") val totalCount: Int? = null,
    @SerialName("RESULT") val result: SeoulResultDto? = null,
    val row: List<StationMasterDto>? = null,
)

@Serializable
data class StationMasterDto(
    /** 역 코드. "0150" */
    @SerialName("BLDN_ID") val stationCode: String? = null,
    /** 역명. **여기는 "서울역"처럼 "역"이 붙습니다** */
    @SerialName("BLDN_NM") val stationName: String? = null,
    /** 호선. "1호선" */
    @SerialName("ROUTE") val route: String? = null,
    /** 위도. 문자열입니다 */
    @SerialName("LAT") val latitude: String? = null,
    /** 경도. 오타가 아니라 실제 필드명이 LOT입니다 */
    @SerialName("LOT") val longitude: String? = null,
)

// ── 노선별 역 정보 (SearchSTNBySubwayLineInfo) ──────────────────────────────

@Serializable
data class StationLineInfoResponse(
    @SerialName("SearchSTNBySubwayLineInfo") val body: StationLineInfoBody? = null,
    @SerialName("RESULT") val result: SeoulResultDto? = null,
)

@Serializable
data class StationLineInfoBody(
    @SerialName("list_total_count") val totalCount: Int? = null,
    @SerialName("RESULT") val result: SeoulResultDto? = null,
    val row: List<StationLineInfoDto>? = null,
)

@Serializable
data class StationLineInfoDto(
    @SerialName("STATION_CD") val stationCode: String? = null,
    /** 역명. **여기는 "서울"처럼 "역"이 없습니다** */
    @SerialName("STATION_NM") val stationName: String? = null,
    @SerialName("STATION_NM_ENG") val stationNameEng: String? = null,
    /** "01호선" 형식입니다 */
    @SerialName("LINE_NUM") val lineNum: String? = null,
    /** 안내판 표기 번호. "P148" */
    @SerialName("FR_CODE") val frCode: String? = null,
)

// ── 공통 ────────────────────────────────────────────────────────────────────

@Serializable
data class SeoulResultDto(
    @SerialName("CODE") val code: String? = null,
    @SerialName("MESSAGE") val message: String? = null,
)
