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
         * 두 API가 같은 노선을 **다른 이름으로** 부르는 것을 이어 줍니다.
         *
         * 역정보 API는 사람들이 타는 **운영 노선** 이름을 씁니다("1호선", "경의선").
         * 좌표 API는 철도 **시설 구간** 이름을 씁니다("경부선", "경인선", "일산선").
         * 서울역 1호선은 시설상 경부선이고, 소요산 방면은 경원선입니다 —
         * 둘 다 승객에게는 그냥 1호선입니다.
         *
         * 이걸 이어 주지 않으면 두 가지가 한꺼번에 깨집니다.
         * 1. 역정보 쪽에서 "경의선" 57개, "우이신설경전철" 13개가 **통째로 버려집니다**
         * 2. 좌표 쪽에서 이름이 안 맞아 **좌표가 안 붙습니다** — 지도에 안 그려집니다
         *
         * 실측: 이 표가 없을 때 620개 역 중 좌표가 붙는 것은 373개(60%)뿐이었습니다.
         * 넣고 나면 690개 중 652개(94%)가 됩니다.
         *
         * 여기 없는 노선(인천1·2호선, 용인·의정부경전철, 김포골드라인, GTX-A)은
         * **아직 모델에 없는 노선**이라 별칭이 아니라 새 항목이 필요합니다.
         */
        private val aliases: Map<String, LineId> = mapOf(
            // 1호선 — 시설상 네 구간으로 나뉩니다
            "경부선" to LINE_1,
            "경인선" to LINE_1,
            "경원선" to LINE_1,
            "장항선" to LINE_1,

            "일산선" to LINE_3,

            "과천선" to LINE_4,
            "안산선" to LINE_4,
            "진접선" to LINE_4,

            "7호선(인천)" to LINE_7,
            "별내선" to LINE_8,
            "9호선(연장)" to LINE_9,

            "경의선" to GYEONGUI_JUNGANG,
            "중앙선" to GYEONGUI_JUNGANG,
            "분당선" to SUIN_BUNDANG,
            "수인선" to SUIN_BUNDANG,
            "공항철도1호선" to AIRPORT,
            "우이신설경전철" to UI_SINSEOL,
            "신분당선(연장)" to SINBUNDANG,
            "신분당선(연장2)" to SINBUNDANG,
        )

        /**
         * 역정보 API의 LINE_NUM("01호선"), 좌표 API의 ROUTE("1호선") 등
         * 사람이 읽는 표기로 찾기. 앞의 0을 떼고 비교하며, [aliases]도 함께 봅니다.
         */
        fun fromDisplayName(raw: String): LineId? {
            val trimmed = raw.trim()
            byDisplayName[trimmed]?.let { return it }
            aliases[trimmed]?.let { return it }
            // "01호선" -> "1호선"
            val unpadded = trimmed.trimStart('0')
            return byDisplayName[unpadded] ?: aliases[unpadded]
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
