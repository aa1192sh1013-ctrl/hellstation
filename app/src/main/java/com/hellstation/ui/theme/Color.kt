package com.hellstation.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.hellstation.domain.model.CrowdLevel
import com.hellstation.domain.model.LineId

/**
 * HellStation 색.
 *
 * **여기가 색의 원본입니다.** res/values/colors.xml 에는 안드로이드 시스템이 요구하는 색
 * (창 배경, 아이콘)만 있습니다.
 */

// ── 브랜드 ──────────────────────────────────────────────────────────────────

/** 지옥철의 핫핑크. 캐릭터 뿔과 강조에 씁니다. */
val HellPink = Color(0xFFFF3D8B)
val HellPinkBright = Color(0xFFFF6BA5)

/** 전동차 노랑. 캐릭터 몸통. */
val TrainYellow = Color(0xFFFFD84D)

/** 전조등 시안. */
val NeonCyan = Color(0xFF17C4C4)
val NeonCyanBright = Color(0xFF34E0E0)

val InkPurple = Color(0xFF1B1030)
val CreamLight = Color(0xFFFFF6EE)
val NightBase = Color(0xFF141020)
val NightSurface = Color(0xFF201A2E)
val NightSurfaceHigh = Color(0xFF2B2340)

// ── 혼잡도 5단계 ────────────────────────────────────────────────────────────

/**
 * 혼잡도 한 단계의 색 묶음.
 *
 * @param vivid  지도·배지처럼 색 자체가 정보인 곳
 * @param on     [vivid] 위에 올리는 글자색
 * @param soft   칩·카드 배경처럼 은은해야 하는 곳
 * @param onSoft [soft] 위에 올리는 글자색
 */
@Immutable
data class CrowdColor(
    val vivid: Color,
    val on: Color,
    val soft: Color,
    val onSoft: Color,
)

/**
 * 5단계 + UNKNOWN 색표.
 *
 * ## 지켜야 할 것 (docs/crowding-levels.md 4절)
 *
 * 1. **EASY → WTF 로 갈수록 색이 뜨거워집니다.** 초록 → 노랑 → 주황 → 빨강 → 보라.
 *    중간에 밝기가 튀면 사용자가 순서를 못 읽습니다.
 * 2. **UNKNOWN은 무채색입니다.** 등급 색 계열을 쓰면 "데이터 없음"이 전달되지 않습니다.
 * 3. **색만으로 구분하지 않습니다.** 색각 이상 사용자를 위해 배지에 항상 글자를 함께 넣습니다
 *    (CrowdBadge 참고).
 */
@Immutable
data class CrowdPalette(
    val easy: CrowdColor,
    val busy: CrowdColor,
    val bad: CrowdColor,
    val hell: CrowdColor,
    val wtf: CrowdColor,
    val unknown: CrowdColor,
) {
    fun of(level: CrowdLevel): CrowdColor = when (level) {
        CrowdLevel.EASY -> easy
        CrowdLevel.BUSY -> busy
        CrowdLevel.BAD -> bad
        CrowdLevel.HELL -> hell
        CrowdLevel.WTF -> wtf
        CrowdLevel.UNKNOWN -> unknown
    }
}

/**
 * 밝은 배경(크림색) 위에서 쓰는 색.
 *
 * ## 왜 흔한 파스텔 톤이 아닌가
 *
 * 처음에는 더 밝고 통통 튀는 색을 썼는데, 지도 배경(#FFFBF6) 위에서 대비를 재 보니
 * EASY 2.27:1, BUSY 1.72:1 로 **역 점이 배경에 묻혔습니다.**
 * 지도에서는 점의 색이 정보의 전부라 밝기를 낮춰 3:1 이상으로 맞췄습니다.
 *
 * 지금 값의 배경 대비: EASY 3.08 / BUSY 3.12 / BAD 3.05 / HELL 3.89 / WTF 4.71 / UNKNOWN 3.06
 * 인접 등급 색차는 최소 ΔE 30.4 로, 옆 등급과도 확실히 갈립니다.
 */
val LightCrowdPalette = CrowdPalette(
    easy = CrowdColor(
        vivid = Color(0xFF1CA566),
        on = Color(0xFF04301C),
        soft = Color(0xFFD7F5E7),
        onSoft = Color(0xFF0B6B41),
    ),
    busy = CrowdColor(
        vivid = Color(0xFFB98708),
        on = Color(0xFF2A1E00),
        soft = Color(0xFFFDEFCC),
        onSoft = Color(0xFF7A5800),
    ),
    bad = CrowdColor(
        vivid = Color(0xFFFF5900),
        on = Color(0xFF3A1500),
        soft = Color(0xFFFFE2D2),
        onSoft = Color(0xFF8F3A08),
    ),
    hell = CrowdColor(
        vivid = Color(0xFFF0333B),
        // 흰 글자는 4.01:1 로 작은 글씨 기준에 못 미쳤습니다. 어두운 글자가 4.5:1 을 넘깁니다.
        on = Color(0xFF3B0509),
        soft = Color(0xFFFFD9DB),
        onSoft = Color(0xFF9E1017),
    ),
    wtf = CrowdColor(
        vivid = Color(0xFFA63BE0),
        // 여기만 흰 글자가 낫습니다(4.86:1 vs 3.72:1).
        on = Color(0xFFFFFFFF),
        soft = Color(0xFFEEDBFA),
        onSoft = Color(0xFF6A159C),
    ),
    unknown = CrowdColor(
        vivid = Color(0xFF938F9D),
        on = Color(0xFF23202B),
        soft = Color(0xFFE7E5EA),
        onSoft = Color(0xFF4F4B59),
    ),
)

/**
 * 어두운 배경 위에서 쓰는 색.
 *
 * 밝은 테마 색을 그대로 쓰면 어두운 배경에서 초록과 노랑이 뭉개집니다.
 * 채도를 올리고 명도를 확실히 벌려서 다섯 단계가 다 구분되게 다시 잡았습니다.
 */
val DarkCrowdPalette = CrowdPalette(
    easy = CrowdColor(
        vivid = Color(0xFF3DE58F),
        on = Color(0xFF00301A),
        soft = Color(0xFF16402F),
        onSoft = Color(0xFF7FF0B7),
    ),
    busy = CrowdColor(
        vivid = Color(0xFFFFD34D),
        on = Color(0xFF302300),
        soft = Color(0xFF3D3212),
        onSoft = Color(0xFFFFE494),
    ),
    bad = CrowdColor(
        vivid = Color(0xFFFF9445),
        on = Color(0xFF351400),
        soft = Color(0xFF442612),
        onSoft = Color(0xFFFFBE8A),
    ),
    hell = CrowdColor(
        vivid = Color(0xFFFF5B62),
        on = Color(0xFF3B0509),
        soft = Color(0xFF4A1A20),
        onSoft = Color(0xFFFF9EA2),
    ),
    wtf = CrowdColor(
        vivid = Color(0xFFC77BFF),
        on = Color(0xFF2C0044),
        soft = Color(0xFF3A2151),
        onSoft = Color(0xFFDDB2FF),
    ),
    unknown = CrowdColor(
        vivid = Color(0xFF6B6878),
        on = Color(0xFF0F0C16),
        soft = Color(0xFF2A2635),
        onSoft = Color(0xFFA5A1B0),
    ),
)

val LocalCrowdPalette = staticCompositionLocalOf { LightCrowdPalette }

// ── 노선 색 ─────────────────────────────────────────────────────────────────

/**
 * 서울 지하철 노선 고유색.
 *
 * 실제 노선도에서 쓰는 색과 같습니다. 사용자가 이미 외우고 있는 색이라
 * 브랜드 색으로 덮어쓰면 안 됩니다.
 */
object LineColors {
    private val map: Map<LineId, Color> = mapOf(
        LineId.LINE_1 to Color(0xFF0052A4),
        LineId.LINE_2 to Color(0xFF00A84D),
        LineId.LINE_3 to Color(0xFFEF7C1C),
        LineId.LINE_4 to Color(0xFF00A5DE),
        LineId.LINE_5 to Color(0xFF996CAC),
        LineId.LINE_6 to Color(0xFFCD7C2F),
        LineId.LINE_7 to Color(0xFF747F00),
        LineId.LINE_8 to Color(0xFFE6186C),
        LineId.LINE_9 to Color(0xFFBB8336),
        LineId.GYEONGUI_JUNGANG to Color(0xFF77C4A3),
        LineId.AIRPORT to Color(0xFF0090D2),
        LineId.GYEONGCHUN to Color(0xFF178C72),
        LineId.SUIN_BUNDANG to Color(0xFFF5A200),
        LineId.SINBUNDANG to Color(0xFFD4003B),
        LineId.SILLIM to Color(0xFF6789CA),
        LineId.UI_SINSEOL to Color(0xFFB7C450),
        LineId.SEOHAE to Color(0xFF8FC31F),
        LineId.GYEONGGANG to Color(0xFF0054A6),
    )

    fun of(line: LineId): Color = map[line] ?: Color(0xFF8C8A93)

    /**
     * 어두운 배경에서 쓸 노선색.
     * 1호선 남색처럼 어두운 색은 밤에 배경과 붙어 버려서 살짝 밝힙니다.
     */
    fun onDark(line: LineId): Color {
        val base = of(line)
        val luminance = base.red * 0.299f + base.green * 0.587f + base.blue * 0.114f
        return if (luminance < 0.45f) base.lighten(0.38f) else base
    }
}

private fun Color.lighten(amount: Float): Color = Color(
    red = red + (1f - red) * amount,
    green = green + (1f - green) * amount,
    blue = blue + (1f - blue) * amount,
    alpha = alpha,
)
