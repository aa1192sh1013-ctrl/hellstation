package com.hellstation.domain.model

/**
 * 지금 앱이 어떤 품질의 데이터로 돌아가고 있는지.
 *
 * 화면 위쪽에 "지금 보는 값이 어디서 왔는지" 안내를 띄우는 데 씁니다.
 * 사용자가 어림값을 실측으로 오해하지 않게 하는 것이 목적입니다.
 *
 * @param usingSampleKey 인증키 없이 샘플 키로 돌아가는 중인가.
 *   샘플 키는 한 번에 5건만 주므로 실시간 도착정보가 서울역에만 붙습니다
 * @param usingSeedStations 역 목록을 API가 아니라 밖에서 넣어 준 목록으로 쓰고 있는가
 * @param hasMeasuredBaseline 서울교통공사 실측 통계(CSV)를 읽을 수 있는가
 * @param stationCount 지금 다루고 있는 역 수
 */
data class DataStatus(
    val usingSampleKey: Boolean,
    val usingSeedStations: Boolean,
    val hasMeasuredBaseline: Boolean,
    val stationCount: Int,
) {
    /** 실측에 기반한 값을 낼 수 있는 상태인가. */
    val isFullyLive: Boolean
        get() = !usingSampleKey && !usingSeedStations && hasMeasuredBaseline

    /**
     * 화면에 띄울 안내 문구. 모든 게 갖춰졌으면 null입니다.
     *
     * 가장 큰 문제 하나만 알려 줍니다. 세 줄을 한꺼번에 띄우면 아무도 안 읽습니다.
     */
    val warning: String?
        get() = when {
            usingSampleKey && usingSeedStations ->
                "인증키가 없어 예상 혼잡도만 보여드립니다"

            usingSampleKey ->
                "실시간 인증키가 없어 서울역 외에는 예상값입니다"

            !hasMeasuredBaseline ->
                "실측 통계 파일이 없어 어림값으로 보여드립니다"

            usingSeedStations ->
                "역 목록을 받아오지 못해 기본 목록으로 보여드립니다"

            else -> null
        }

    companion object {
        val UNKNOWN = DataStatus(
            usingSampleKey = true,
            usingSeedStations = true,
            hasMeasuredBaseline = false,
            stationCount = 0,
        )
    }
}
