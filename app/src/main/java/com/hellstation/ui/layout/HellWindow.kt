package com.hellstation.ui.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration

/**
 * 화면 너비 구분.
 *
 * Material 의 window size class 와 경계를 맞췄습니다. 다만 여기서는 필요한 만큼만 씁니다 —
 * 이 앱은 지도 하나가 화면을 채우는 구조라 태블릿에서도 배치가 크게 달라지지 않습니다.
 */
enum class HellWidth {
    /** 좁은 폰. 갤럭시 폴드 접은 상태(약 320dp)가 여기 들어옵니다 */
    COMPACT,

    /** 보통 폰 */
    REGULAR,

    /** 태블릿·폴더블 펼친 상태·가로 모드 */
    WIDE,
    ;

    val isCompact: Boolean get() = this == COMPACT
    val isWide: Boolean get() = this == WIDE
}

/** 화면 높이 구분. 가로로 눕히면 세로 공간이 급격히 줄어듭니다. */
enum class HellHeight {
    /** 가로 모드나 작은 기기. 세로로 쌓으면 넘칩니다 */
    SHORT,

    /** 보통 */
    TALL,
    ;

    val isShort: Boolean get() = this == SHORT
}

/**
 * 지금 화면 크기.
 *
 * ## 왜 필요한가
 *
 * 이 앱에는 한 줄에 많은 것을 욱여넣는 자리가 몇 군데 있습니다.
 * 역 시트의 `캐릭터 + 등급 + 제목 + 큰 숫자` 한 줄이 대표적입니다.
 *
 * 360dp 폰에서는 들어가지만 **320dp 기기에서는 제목이 한 글자씩 끊깁니다.**
 * 가로 모드에서는 반대로 세로가 부족해서 시트가 화면을 다 덮습니다.
 *
 * 그래서 좁으면 세로로 쌓고, 낮으면 여백을 줄입니다.
 */
@Immutable
data class HellWindowSize(
    val width: HellWidth,
    val height: HellHeight,
    val widthDp: Int,
    val heightDp: Int,
) {
    /** 한 줄에 캐릭터와 큰 숫자를 나란히 둘 수 있는가. */
    val allowsDenseRow: Boolean get() = widthDp >= DENSE_ROW_MIN_DP

    /** 지도 위에 떠 있는 것들의 좌우 여백. */
    val edgePadding: Int get() = if (width.isCompact) 8 else 12

    companion object {
        /**
         * 이 너비 아래에서는 한 줄에 여러 요소를 넣지 않습니다.
         *
         * 캐릭터 84dp + 여백 14 + 최소 제목 폭 120 + 여백 14 + 숫자 70 = 302dp.
         * 좌우 화면 여백 40dp 를 빼면 342dp 는 되어야 안 끊깁니다.
         */
        const val DENSE_ROW_MIN_DP = 342

        val Default = HellWindowSize(HellWidth.REGULAR, HellHeight.TALL, 400, 800)
    }
}

val LocalHellWindow = staticCompositionLocalOf { HellWindowSize.Default }

/**
 * 지금 설정에서 화면 크기를 읽습니다.
 * 테마가 한 번 불러 [LocalHellWindow] 로 내려보냅니다.
 *
 * 미리보기의 `widthDp`/`heightDp` 도 그대로 반영되므로, 좁은 화면 미리보기가
 * 실제 좁은 기기와 같게 보입니다.
 */
@Composable
@ReadOnlyComposable
fun currentWindowSize(): HellWindowSize {
    val configuration = LocalConfiguration.current
    val widthDp = configuration.screenWidthDp
    val heightDp = configuration.screenHeightDp

    return HellWindowSize(
        width = when {
            widthDp < 360 -> HellWidth.COMPACT
            widthDp < 600 -> HellWidth.REGULAR
            else -> HellWidth.WIDE
        },
        height = if (heightDp < 520) HellHeight.SHORT else HellHeight.TALL,
        widthDp = widthDp,
        heightDp = heightDp,
    )
}
