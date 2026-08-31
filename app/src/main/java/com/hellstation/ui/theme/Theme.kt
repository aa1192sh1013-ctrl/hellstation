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
    // 둥글둥글할수록 어려 보입니다. 34dp 짜리 모서리는 장난감 같았습니다.
    // 정보를 담는 화면이라 각을 살려 계기판 쪽으로 당깁니다.
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
)

private val LightColors = lightColorScheme(
    primary = NeonCyanDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC7F0EA),
    onPrimaryContainer = Color(0xFF00332C),

    secondary = HellMagentaDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD6E6),
    onSecondaryContainer = Color(0xFF52002A),

    tertiary = Color(0xFF6B5E00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF2E7A8),
    onTertiaryContainer = Color(0xFF201C00),

    background = PaperLight,
    onBackground = InkPurple,
    surface = Color(0xFFFFFFFF),
    onSurface = InkPurple,
    surfaceVariant = Color(0xFFE7E5EE),
    onSurfaceVariant = Color(0xFF4A4757),
    surfaceContainer = Color(0xFFEEEDF3),
    surfaceContainerHigh = Color(0xFFE6E4EC),

    outline = Color(0xFF9C98A8),
    outlineVariant = Color(0xFFD6D3DE),

    error = Color(0xFFB3001E),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD9),
    onErrorContainer = Color(0xFF40000A),

    scrim = Color(0xFF000000),
)

/**
 * 다크가 **기본값에 가까운 얼굴**입니다.
 *
 * 지하철은 대부분 지하를 달리고, 이 앱을 여는 시간은 이른 아침 아니면 늦은 저녁입니다.
 * 어두운 바탕이라야 네온 색이 간판처럼 뜨고, 눈도 덜 부십니다.
 */
private val DarkColors = darkColorScheme(
    primary = NeonCyan,
    onPrimary = Color(0xFF04231F),
    primaryContainer = Color(0xFF00544A),
    onPrimaryContainer = Color(0xFFA9F5EB),

    secondary = HellMagenta,
    onSecondary = Color(0xFF3D0020),
    secondaryContainer = Color(0xFF6E0038),
    onSecondaryContainer = Color(0xFFFFD6E6),

    tertiary = Color(0xFFF2C53D),
    onTertiary = Color(0xFF332600),
    tertiaryContainer = Color(0xFF4A3800),
    onTertiaryContainer = Color(0xFFF7E3A8),

    background = NightBase,
    onBackground = Color(0xFFECE8F5),
    surface = NightSurface,
    onSurface = Color(0xFFECE8F5),
    surfaceVariant = Color(0xFF2B2839),
    onSurfaceVariant = Color(0xFF9E98B2),
    surfaceContainer = NightSurface,
    surfaceContainerHigh = NightSurfaceHigh,

    outline = Color(0xFF615C75),
    outlineVariant = Color(0xFF322E42),

    error = Color(0xFFFF8A94),
    onError = Color(0xFF4A0010),
    errorContainer = Color(0xFF7A0021),
    onErrorContainer = Color(0xFFFFD9DC),

    scrim = Color(0xFF000000),
)

