package com.hellstation.domain.model

/**
 * 열차 종류.
 *
 * **급행을 반드시 구분하세요.** 급행은 정차역이 적어 일반 열차와 혼잡도가 크게 다릅니다.
 * 통계 기준선은 일반 열차 기준이므로 급행에 그대로 적용하면 신뢰도를 낮춰야 합니다.
 */
enum class TrainType {
    NORMAL,   // 일반
    EXPRESS,  // 급행
    RAPID,    // 특급
    ITX,      // ITX
    UNKNOWN,
    ;

    /** 통계 기준선을 그대로 적용해도 되는 종류인가. */
    val matchesBaseline: Boolean get() = this == NORMAL

    companion object {
        /** 도착정보 API의 `btrainSttus` 값을 변환합니다. */
        fun parse(raw: String?): TrainType = when (val v = raw?.trim()) {
            null, "" -> UNKNOWN
            "일반" -> NORMAL
            "특급" -> RAPID
            "ITX" -> ITX
            else -> if (v.startsWith("급행")) EXPRESS else UNKNOWN
        }
    }
}

/**
 * 열차 한 대.
 *
 * @param trainNo     열차 번호(btrainNo). 같은 열차를 여러 역에서 추적하는 키
 * @param destination 종착역 이름(bstatnNm)
 * @param headsign    "양주행 - 시청방면"(trainLineNm). 화면에 그대로 쓸 수 있는 문구
 * @param isLastTrain 막차 여부(lstcarAt == "1")
 */
data class Train(
    val trainNo: String,
    val line: LineId,
    val direction: Direction,
    val type: TrainType,
    val destination: String,
    val headsign: String,
    val isLastTrain: Boolean = false,
)
