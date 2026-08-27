package com.hellstation.data.remote.mapper

/**
 * 소스마다 다른 역명 표기를 하나로 맞춥니다.
 *
 * **정규화는 반드시 여기 한 곳에서만 하세요.** 두 군데에서 하면 규칙이 갈라지고,
 * 그 순간 역 매칭이 조용히 깨집니다.
 *
 * ```
 * "서울역"            -> "서울"
 * "총신대입구(이수)"   -> "총신대입구"
 * "4·19민주묘지"      -> "4.19민주묘지"
 * "이대"              -> "이대"
 * ```
 *
 * 정규화한 이름은 **매칭 전용**입니다. 화면에는 `Station.displayName`을 쓰세요.
 */
object StationNameNormalizer {

    /**
     * ## "역"을 떼는 게 안전한 이유
     *
     * "역촌역"처럼 이름 자체가 "역"으로 끝나는 역이 있습니다. 여기서 접미사를 떼면
     * "역촌"이 되는데, **모든 소스에 같은 규칙을 적용하므로 양쪽이 똑같이 "역촌"이 되어
     * 매칭은 그대로 성립합니다.** 중요한 것은 규칙의 일관성이지 원래 이름의 보존이 아닙니다.
     *
     * 원래 이름이 필요한 곳(화면)에서는 `Station.displayName`을 쓰세요.
     */
    fun normalize(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        var name = raw.trim()

        // 부역명 제거: "총신대입구(이수)" -> "총신대입구"
        // 소스에 따라 붙기도 하고 안 붙기도 합니다.
        name = name.substringBefore('(').trim()

        // 가운뎃점 표기 통일: "4·19민주묘지" -> "4.19민주묘지"
        name = name.replace('·', '.')

        // 공백 제거: "서울 역" 같은 표기 흔들림 방지
        name = name.replace(" ", "")

        // "역" 접미사 제거. 이름이 "역" 한 글자뿐이면 그대로 둡니다.
        if (name.length > 1 && name.endsWith("역")) {
            val stripped = name.dropLast(1)
            if (stripped.isNotBlank()) name = stripped
        }
        return name
    }

    /**
     * 검색용 비교. 대소문자와 공백을 무시합니다.
     * 사용자가 "서울역"이라고 쳐도 "서울"과 맞아야 합니다.
     */
    fun matches(query: String, stationName: String): Boolean {
        val q = normalize(query)
        if (q.isBlank()) return false
        return normalize(stationName).contains(q)
    }
}
