package com.hellstation.domain.model

/**
 * 진행 방향.
 *
 * 소스별 표기:
 * - 도착정보 API `updnLine` : "상행" / "하행"  (2호선은 "내선" / "외선")
 * - 혼잡도 CSV `상하구분`    : "상선" / "하선"  (2호선은 "내선" / "외선")
 *
 * 2호선은 순환선이라 상/하행 개념이 없습니다. UP=내선, DOWN=외선으로 고정합니다.
 *
 * **방향을 잃어버리지 마세요.** 같은 역·같은 시각이라도 상행과 하행의 혼잡도는
 * 출퇴근 시간에 정반대입니다. 방향 없는 혼잡도는 의미가 없습니다.
 */
enum class Direction {
    UP,
    DOWN,
    ;

    val opposite: Direction get() = if (this == UP) DOWN else UP

    /** 해당 노선에서 사용자에게 보여줄 방향 이름. */
    fun labelFor(line: LineId): String = when {
        line.isLoop && this == UP -> "내선"
        line.isLoop -> "외선"
        this == UP -> "상행"
        else -> "하행"
    }

    /** 혼잡도 CSV의 `상하구분` 컬럼에 들어가는 표기. */
    fun csvLabelFor(line: LineId): String = when {
        line.isLoop && this == UP -> "내선"
        line.isLoop -> "외선"
        this == UP -> "상선"
        else -> "하선"
    }

    companion object {
        /** "상행" "상선" "내선" 등 어떤 표기가 와도 받아냅니다. 모르는 값이면 null. */
        fun parse(raw: String?): Direction? = when (raw?.trim()) {
            "상행", "상선", "내선", "UP", "0" -> UP
            "하행", "하선", "외선", "DOWN", "1" -> DOWN
            else -> null
        }
    }
}
