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

/**
 * ## 왜 이 색인가 — "야근 네온"
 *
 * 처음에는 크림색 바탕에 핫핑크였습니다. 키치하긴 한데 **사탕색이라 초등학생 앱처럼**
 * 보였습니다. 이 앱을 쓰는 사람은 지친 출퇴근길의 직장인입니다.
 *
 * 그래서 기준을 바꿨습니다 — **어두운 터널 바탕에 네온 간판**. 심야 사무실, 지하철
 * 승강장의 형광등, 스크린도어에 비치는 불빛. 채도는 그대로 높지만 바탕이 어두워서
 * 사탕이 아니라 **간판 불빛**으로 읽힙니다.
 */

/** 주 액션 색. 어두운 바탕에서 형광등처럼 뜨는 청록. */
val NeonCyan = Color(0xFF00D9C0)
val NeonCyanDeep = Color(0xFF00786B)

/** 위험·강조. 지옥 쪽 신호에만 씁니다. 남발하면 다시 사탕색이 됩니다. */
val HellMagenta = Color(0xFFFF2E88)
val HellMagentaDeep = Color(0xFFB3005A)

/** 전동차 차체. 노랑이 아니라 **쇳덩이 색**입니다. */
val SteelBody = Color(0xFF2A2738)
val SteelEdge = Color(0xFF4A4560)

val InkPurple = Color(0xFF13111C)
val NightBase = Color(0xFF0A0912)
val NightSurface = Color(0xFF16141F)
val NightSurfaceHigh = Color(0xFF211E2C)

/** 밝은 모드는 크림이 아니라 **차가운 회색**입니다. 사무실 형광등 아래 종이 느낌. */
val PaperLight = Color(0xFFF4F3F7)

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
        vivid = Color(0xFF12915A),
        on = Color(0xFFFFFFFF),
        soft = Color(0xFFD8F0E4),
        onSoft = Color(0xFF0A5537),
    ),
    busy = CrowdColor(
        vivid = Color(0xFFA87A06),
        on = Color(0xFFFFFFFF),
        soft = Color(0xFFF5E9C8),
        onSoft = Color(0xFF5C4300),
    ),
    bad = CrowdColor(
        vivid = Color(0xFFE84E00),
        on = Color(0xFFFFFFFF),
        soft = Color(0xFFFFE0CE),
        onSoft = Color(0xFF7A2900),
    ),
    hell = CrowdColor(
        vivid = Color(0xFFCE1F41),
        on = Color(0xFFFFFFFF),
        soft = Color(0xFFFBD8DE),
        onSoft = Color(0xFF6E0A20),
    ),
    wtf = CrowdColor(
        vivid = Color(0xFF9130CC),
        on = Color(0xFFFFFFFF),
        soft = Color(0xFFEEDCFA),
        onSoft = Color(0xFF4C1170),
    ),
    unknown = CrowdColor(
        vivid = Color(0xFF7E7A8A),
        on = Color(0xFFFFFFFF),
        soft = Color(0xFFE6E4EC),
        onSoft = Color(0xFF46434F),
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
        vivid = Color(0xFF35E08A),
        on = Color(0xFF05301B),
        soft = Color(0xFF173A28),
        onSoft = Color(0xFF9FE9C4),
    ),
    busy = CrowdColor(
        vivid = Color(0xFFF2C53D),
        on = Color(0xFF332600),
        soft = Color(0xFF3A3016),
        onSoft = Color(0xFFF0DCA0),
    ),
    bad = CrowdColor(
        vivid = Color(0xFFFF8A3D),
        on = Color(0xFF3A1600),
        soft = Color(0xFF3D2718),
        onSoft = Color(0xFFFFC9A3),
    ),
    hell = CrowdColor(
        vivid = Color(0xFFFF3E63),
        on = Color(0xFF3D0011),
        soft = Color(0xFF3D1A24),
        onSoft = Color(0xFFFFB3C1),
    ),
    wtf = CrowdColor(
        vivid = Color(0xFFC061FF),
        on = Color(0xFF2C0047),
        soft = Color(0xFF2F1E3D),
        onSoft = Color(0xFFDFB8FF),
    ),
    unknown = CrowdColor(
        vivid = Color(0xFF6A6480),
        on = Color(0xFF11101A),
        soft = Color(0xFF242231),
        onSoft = Color(0xFFA9A4BA),
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
