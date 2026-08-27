package com.hellstation.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hellstation.ui.component.LocalReduceMotion
import com.hellstation.ui.component.systemReduceMotion
import com.hellstation.ui.layout.LocalHellWindow
import com.hellstation.ui.layout.currentWindowSize

/**
 * HellStation 테마.
 *
 * ## 다이나믹 컬러(Material You)를 쓰지 않는 이유
 *
 * 기기 배경화면에서 색을 뽑아 오면 기기마다 앱 색이 달라집니다.
 * HellStation은 **핫핑크 악마**가 정체성이고, 혼잡도 5단계 색은 정보 그 자체라
 * 기기 취향에 맡길 수 없습니다. 그래서 색을 고정합니다.
 */
@Composable
fun HellStationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val crowdPalette = if (darkTheme) DarkCrowdPalette else LightCrowdPalette

    // 화면 크기와 "움직임 줄이기" 설정은 여기서 한 번만 읽어 아래로 내려보냅니다.
    // 화면마다 각자 읽으면 미리보기에서 강제로 바꾼 값이 반영되지 않습니다.
    CompositionLocalProvider(
        LocalCrowdPalette provides crowdPalette,
        LocalIsDarkTheme provides darkTheme,
        LocalHellWindow provides currentWindowSize(),
        LocalReduceMotion provides systemReduceMotion(),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = HellTypography,
            shapes = HellShapes,
            content = content,
        )
    }
}

/**
 * 지금 다크 모드인가.
 *
 * `isSystemInDarkTheme()`을 화면 곳곳에서 다시 부르면 미리보기에서 강제로 테마를 바꿔도
 * 그 부분만 따로 놉니다. 테마가 정한 값을 그대로 내려 받으세요.
 */
val LocalIsDarkTheme = staticCompositionLocalOf { false }

/** 테마가 들고 있는 것들을 짧게 꺼내는 통로. `HellTheme.crowd.of(level)` */
object HellTheme {
    val crowd: CrowdPalette
        @Composable @ReadOnlyComposable get() = LocalCrowdPalette.current

    val isDark: Boolean
        @Composable @ReadOnlyComposable get() = LocalIsDarkTheme.current

    /** 지금 화면 크기. 좁은 기기에서 배치를 바꿀 때 씁니다. */
    val window: com.hellstation.ui.layout.HellWindowSize
        @Composable @ReadOnlyComposable get() = LocalHellWindow.current

    /** 사용자가 시스템에서 애니메이션을 껐는가. */
    val reduceMotion: Boolean
        @Composable @ReadOnlyComposable get() = LocalReduceMotion.current
}

/** 둥글둥글하게. 키치한 느낌의 절반은 모서리에서 나옵니다. */
val HellShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(34.dp),
)

private val LightColors = lightColorScheme(
    primary = HellPink,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E7),
    onPrimaryContainer = Color(0xFF66002F),

    secondary = NeonCyan,
    onSecondary = Color(0xFF00201F),
    secondaryContainer = Color(0xFFC7F3F3),
    onSecondaryContainer = Color(0xFF00403F),

    tertiary = Color(0xFF8A6B00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE7A0),
    onTertiaryContainer = Color(0xFF2A1F00),

    background = CreamLight,
    onBackground = InkPurple,
    surface = Color(0xFFFFFFFF),
    onSurface = InkPurple,
    surfaceVariant = Color(0xFFF2E9E2),
    onSurfaceVariant = Color(0xFF524A55),
    surfaceContainer = Color(0xFFFDF1E8),
    surfaceContainerHigh = Color(0xFFF8E9DE),

    outline = Color(0xFFB8AFB6),
    outlineVariant = Color(0xFFE3D8DD),

    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),

    scrim = Color(0xFF000000),
)

private val DarkColors = darkColorScheme(
    primary = HellPinkBright,
    onPrimary = Color(0xFF4C0022),
    primaryContainer = Color(0xFF7A1042),
    onPrimaryContainer = Color(0xFFFFD9E7),

    secondary = NeonCyanBright,
    onSecondary = Color(0xFF003736),
    secondaryContainer = Color(0xFF11504F),
    onSecondaryContainer = Color(0xFFC7F3F3),

    tertiary = TrainYellow,
    onTertiary = Color(0xFF3A2A00),
    tertiaryContainer = Color(0xFF574200),
    onTertiaryContainer = Color(0xFFFFE7A0),

    background = NightBase,
    onBackground = Color(0xFFF0EAF6),
    surface = NightSurface,
    onSurface = Color(0xFFF0EAF6),
    surfaceVariant = Color(0xFF3A3348),
    onSurfaceVariant = Color(0xFFCCC2D6),
    surfaceContainer = NightSurface,
    surfaceContainerHigh = NightSurfaceHigh,

    outline = Color(0xFF6F667C),
    outlineVariant = Color(0xFF3E3649),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    scrim = Color(0xFF000000),
)
