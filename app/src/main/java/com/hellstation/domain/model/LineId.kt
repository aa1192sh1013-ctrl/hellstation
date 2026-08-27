package com.hellstation.domain.model

/**
 * 서울 수도권 전철 노선.
 *
 * 소스마다 노선 표기가 다르므로(docs/api-validation.md) 변환에 필요한 모든 표기를
 * 이 enum이 직접 들고 있습니다. 앱 안에서는 항상 이 타입만 씁니다.
 *
 * @param apiCode       도착정보 API의 subwayId. 예: "1001"
 * @param displayName   사용자에게 보여줄 이름. 좌표 API의 ROUTE, TMAP의 routeNm과 같은 형식
 * @param csvLineNumber 서울교통공사 혼잡도 CSV의 '호선' 값. null이면 통계 기준선이 없는 노선
 */
enum class LineId(
    val apiCode: String,
    val displayName: String,
    val csvLineNumber: Int?,
) {
    LINE_1("1001", "1호선", 1),
    LINE_2("1002", "2호선", 2),
    LINE_3("1003", "3호선", 3),
    LINE_4("1004", "4호선", 4),
    LINE_5("1005", "5호선", 5),
    LINE_6("1006", "6호선", 6),
    LINE_7("1007", "7호선", 7),
    LINE_8("1008", "8호선", 8),
    LINE_9("1009", "9호선", null),
    GYEONGUI_JUNGANG("1063", "경의중앙선", null),
    AIRPORT("1065", "공항철도", null),
    GYEONGCHUN("1067", "경춘선", null),
    SUIN_BUNDANG("1071", "수인분당선", null),
    SINBUNDANG("1075", "신분당선", null),
    SILLIM("1077", "신림선", null),
    UI_SINSEOL("1092", "우이신설선", null),
    SEOHAE("1093", "서해선", null),
    GYEONGGANG("1081", "경강선", null),
    ;

    /** 통계 기준선(서울교통공사 CSV)을 쓸 수 있는 노선인가. */
    val hasBaseline: Boolean get() = csvLineNumber != null

    /** 2호선만 순환선이라 방향 표기가 다릅니다. */
    val isLoop: Boolean get() = this == LINE_2

    companion object {
        private val byApiCode: Map<String, LineId> = entries.associateBy { it.apiCode }
        private val byDisplayName: Map<String, LineId> = entries.associateBy { it.displayName }
        private val byCsvNumber: Map<Int, LineId> =
            entries.mapNotNull { line -> line.csvLineNumber?.let { it to line } }.toMap()

        /** 도착정보 API의 subwayId로 찾기. 모르는 코드면 null. */
        fun fromApiCode(code: String): LineId? = byApiCode[code.trim()]

        /**
         * 역정보 API의 LINE_NUM("01호선"), 좌표 API의 ROUTE("1호선") 등
         * 사람이 읽는 표기로 찾기. 앞의 0을 떼고 비교합니다.
         */
        fun fromDisplayName(raw: String): LineId? {
            val trimmed = raw.trim()
            byDisplayName[trimmed]?.let { return it }
            // "01호선" -> "1호선"
            val unpadded = trimmed.trimStart('0')
            return byDisplayName[unpadded]
        }

        /** 혼잡도 CSV의 '호선' 숫자로 찾기. */
        fun fromCsvLineNumber(number: Int): LineId? = byCsvNumber[number]

        /**
         * 어떤 형식이 와도 최대한 받아냅니다. 소스가 섞이는 곳에서만 쓰세요.
         * 확실한 형식을 알고 있다면 위의 구체적인 함수를 쓰는 편이 낫습니다.
         */
        fun parse(raw: String?): LineId? {
            if (raw.isNullOrBlank()) return null
            val trimmed = raw.trim()
            return fromApiCode(trimmed)
                ?: fromDisplayName(trimmed)
                ?: trimmed.toIntOrNull()?.let { fromCsvLineNumber(it) }
        }
    }
}
