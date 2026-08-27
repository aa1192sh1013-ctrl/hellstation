package com.hellstation.domain.model

/**
 * 사용자가 직접 정하는 것들.
 *
 * 계산에 쓰이는 값이 아니라 **취향**입니다. 혼잡도 계산은 이 값에 영향을 받지 않습니다.
 */
data class AppSettings(
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    /** 역을 열었을 때 먼저 보여줄 방향. 출퇴근은 매일 같은 방향이라 기억해 두면 한 번 덜 누릅니다 */
    val defaultDirection: Direction = Direction.UP,
    /**
     * 즐겨찾는 역. [StationId.key] 값을 담습니다.
     *
     * `StationId`가 아니라 문자열로 두는 이유: 저장할 때 어차피 문자열이 되고,
     * 되돌리려면 노선 코드를 다시 해석해야 하는데 **모르는 노선 코드가 섞이면 통째로
     * 실패**할 수 있습니다. 화면에서 비교만 하면 되는 값이라 문자열이 안전합니다.
     */
    val favorites: Set<String> = emptySet(),
) {
    fun isFavorite(id: StationId): Boolean = id.key in favorites

    companion object {
        val DEFAULT = AppSettings()
    }
}

/** 밝게/어둡게를 직접 고를 수 있게 합니다. */
enum class ThemeChoice {
    /** 기기 설정을 따라갑니다 */
    SYSTEM,
    LIGHT,
    DARK,
    ;

    /**
     * 실제로 어두운 화면을 쓸 것인가.
     *
     * @param systemIsDark 기기가 지금 어두운 모드인가
     */
    fun resolveDark(systemIsDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemIsDark
        LIGHT -> false
        DARK -> true
    }

    val label: String
        get() = when (this) {
            SYSTEM -> "기기 설정"
            LIGHT -> "밝게"
            DARK -> "어둡게"
        }

    companion object {
        /** 저장된 문자열을 되돌립니다. 모르는 값이면 [SYSTEM]. */
        fun parse(raw: String?): ThemeChoice =
            entries.firstOrNull { it.name == raw } ?: SYSTEM
    }
}
