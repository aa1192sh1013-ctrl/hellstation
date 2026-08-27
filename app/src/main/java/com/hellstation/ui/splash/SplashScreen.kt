package com.hellstation.ui.splash

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.hellstation.domain.model.CrowdLevel
import com.hellstation.ui.character.HellFace
import com.hellstation.ui.theme.HellPink
import com.hellstation.ui.theme.HellStationTheme
import com.hellstation.ui.theme.NeonCyanBright

/**
 * 앱을 열자마자 잠깐 보이는 화면.
 *
 * ## 왜 오래 붙잡아 두지 않나
 *
 * 이 앱을 여는 사람은 대개 **플랫폼에 서서 급하게** 봅니다.
 * 그래서 브랜드를 자랑하는 시간은 최소로 두고, 준비가 끝나면 바로 지도로 넘깁니다.
 * 화면을 누르면 기다리지 않고 바로 넘어갈 수 있습니다.
 *
 * @param onReady 준비가 끝났을 때. 실제로는 역 목록 로딩이 끝나는 시점입니다
 */
@Composable
fun SplashScreen(
    onReady: () -> Unit,
    modifier: Modifier = Modifier,
    minimumMillis: Long = 900,
) {
    val inspecting = LocalInspectionMode.current

    LaunchedEffect(Unit) {
        if (inspecting) return@LaunchedEffect
        delay(minimumMillis)
        onReady()
    }

    val transition = rememberInfiniteTransition(label = "splash")
    val shimmer by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shimmer",
    )
    val glow = if (inspecting) 0.5f else shimmer

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        HellPink.copy(alpha = 0.10f + glow * 0.10f),
                        MaterialTheme.colorScheme.background,
                    )
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onReady,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            HellFace(
                level = CrowdLevel.HELL,
                bob = true,
                modifier = Modifier.size(168.dp),
            )

            Text(
                text = "HellStation",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Text(
                text = "오늘 지옥철, 미리 보고 타세요",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp),
            )

            Text(
                text = "지금 탈까 · 기다릴까",
                style = MaterialTheme.typography.labelLarge,
                color = NeonCyanBright,
                modifier = Modifier
                    .padding(top = 18.dp)
                    .alpha(0.55f + glow * 0.45f),
            )
        }
    }
}

@Preview(name = "Splash · 라이트", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun SplashPreviewLight() {
    HellStationTheme(darkTheme = false) { SplashScreen(onReady = {}) }
}

@Preview(name = "Splash · 다크", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun SplashPreviewDark() {
    HellStationTheme(darkTheme = true) { SplashScreen(onReady = {}) }
}
