package com.hellstation.ui.character

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hellstation.domain.model.CrowdLevel
import com.hellstation.ui.theme.HellPink
import com.hellstation.ui.theme.HellStationTheme
import com.hellstation.ui.theme.HellTheme
import com.hellstation.ui.theme.InkPurple
import com.hellstation.ui.theme.NeonCyan
import com.hellstation.ui.theme.TrainYellow
import kotlin.math.sin

/**
 * HellStation 대표 캐릭터 — 전동차 얼굴에 악마 뿔과 꼬리.
 *
 * **표정이 혼잡도를 따라갑니다.** 여유로우면 웃고, 지옥이면 눈이 소용돌이치고,
 * WTF면 눈이 X가 됩니다. 숫자를 읽지 않아도 캐릭터만 보고 상황을 알 수 있게 하려는 것입니다.
 *
 * 이미지 파일이 아니라 Canvas로 그립니다. 그래서
 * - 어느 크기로 키워도 깨지지 않고
 * - 표정을 코드로 바꿀 수 있고
 * - APK에 이미지가 들어가지 않습니다
 *
 * @param bob 위아래로 살랑이는 움직임. 스플래시에서만 켜세요 — 목록 안에서 흔들리면 정신없습니다
 */
@Composable
fun HellFace(
    level: CrowdLevel,
    modifier: Modifier = Modifier,
    bob: Boolean = false,
    showGlow: Boolean = true,
) {
    val description = "지하철 악마 캐릭터, ${level.moodWord()} 표정"

    // 미리보기와 테스트에서는 애니메이션을 끕니다. 안 그러면 스크린샷이 매번 달라집니다.
    val animate = bob && !LocalInspectionMode.current

    // 조건에 따라 컴포저블 호출을 건너뛰면 상태가 꼬입니다.
    // 항상 호출하고, 쓸지 말지만 뒤에서 정합니다.
    val transition = rememberInfiniteTransition(label = "bob")
    val rawPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "bobPhase",
    )
    val phase = if (animate) rawPhase else 0f

    val glowColor = HellTheme.crowd.of(level).vivid

    Canvas(
        modifier = modifier.semantics { contentDescription = description },
    ) {
        val unit = size.minDimension / 100f
        val offsetY = if (animate) sin(phase) * 2.2f * unit else 0f

        // 가운데 100x100 좌표계로 옮겨서 그립니다.
        val originX = (size.width - 100f * unit) / 2f
        val originY = (size.height - 100f * unit) / 2f

        translate(left = originX, top = originY + offsetY) {
            if (showGlow) drawGlow(unit, glowColor)
            drawTail(unit)
            drawHorns(unit)
            drawBody(unit)
            drawWindshield(unit)
            drawFace(unit, level)
            drawHeadlights(unit)
        }
    }
}

// ── 부위별 그리기 ────────────────────────────────────────────────────────────

private fun DrawScope.drawGlow(u: Float, color: Color) {
    drawCircle(
        color = color.copy(alpha = 0.18f),
        radius = 46f * u,
        center = Offset(50f * u, 54f * u),
    )
    drawCircle(
        color = color.copy(alpha = 0.12f),
        radius = 54f * u,
        center = Offset(50f * u, 54f * u),
    )
}

private fun DrawScope.drawTail(u: Float) {
    val tail = Path().apply {
        moveTo(76f * u, 74f * u)
        quadraticTo(94f * u, 78f * u, 92f * u, 62f * u)
        quadraticTo(91f * u, 53f * u, 84f * u, 56f * u)
    }
    drawPath(tail, HellPink, style = Stroke(width = 3.4f * u))

    // 꼬리 끝 화살촉
    val tip = Path().apply {
        moveTo(80f * u, 58f * u)
        lineTo(90f * u, 51f * u)
        lineTo(89f * u, 63f * u)
        close()
    }
    drawPath(tip, HellPink)
}

private fun DrawScope.drawHorns(u: Float) {
    val left = Path().apply {
        moveTo(34f * u, 34f * u)
        quadraticTo(24f * u, 22f * u, 27f * u, 12f * u)
        quadraticTo(38f * u, 17f * u, 44f * u, 30f * u)
        close()
    }
    val right = Path().apply {
        moveTo(66f * u, 34f * u)
        quadraticTo(76f * u, 22f * u, 73f * u, 12f * u)
        quadraticTo(62f * u, 17f * u, 56f * u, 30f * u)
        close()
    }
    drawPath(left, HellPink)
    drawPath(right, HellPink)
}

private fun DrawScope.drawBody(u: Float) {
    drawRoundRect(
        color = TrainYellow,
        topLeft = Offset(22f * u, 28f * u),
        size = Size(56f * u, 58f * u),
        cornerRadius = CornerRadius(18f * u, 18f * u),
    )
    // 아래쪽 그림자 띠 — 납작해 보이지 않게
    drawRoundRect(
        color = Color(0xFFE8B92F),
        topLeft = Offset(22f * u, 76f * u),
        size = Size(56f * u, 10f * u),
        cornerRadius = CornerRadius(8f * u, 8f * u),
    )
}

private fun DrawScope.drawWindshield(u: Float) {
    drawRoundRect(
        color = InkPurple,
        topLeft = Offset(28f * u, 36f * u),
        size = Size(44f * u, 30f * u),
        cornerRadius = CornerRadius(11f * u, 11f * u),
    )
}

private fun DrawScope.drawHeadlights(u: Float) {
    drawCircle(NeonCyan, radius = 3.4f * u, center = Offset(31f * u, 81f * u))
    drawCircle(NeonCyan, radius = 3.4f * u, center = Offset(69f * u, 81f * u))
}

/**
 * 표정. 혼잡도가 나빠질수록 얼굴이 무너집니다.
 */
private fun DrawScope.drawFace(u: Float, level: CrowdLevel) {
    val leftEye = Offset(40f * u, 49f * u)
    val rightEye = Offset(60f * u, 49f * u)
    val white = Color.White
    val ink = InkPurple

    when (level) {
        CrowdLevel.EASY -> {
            // ^ ^ 눈웃음
            drawArcEye(leftEye, u, white)
            drawArcEye(rightEye, u, white)
            drawSmile(u, width = 16f, depth = 7f, color = white)
            drawFang(u, white)
        }

        CrowdLevel.BUSY -> {
            drawRoundEye(leftEye, u, white, ink, pupilOffset = Offset(0f, 0f))
            drawRoundEye(rightEye, u, white, ink, pupilOffset = Offset(0f, 0f))
            drawSmile(u, width = 12f, depth = 4f, color = white)
        }

        CrowdLevel.BAD -> {
            // 눈동자가 위로 — 곤란한 표정
            drawRoundEye(leftEye, u, white, ink, pupilOffset = Offset(0f, -1.4f * u))
            drawRoundEye(rightEye, u, white, ink, pupilOffset = Offset(0f, -1.4f * u))
            drawFlatMouth(u, white)
        }

        CrowdLevel.HELL -> {
            // 소용돌이 눈
            drawSwirlEye(leftEye, u, white)
            drawSwirlEye(rightEye, u, white)
            drawWavyMouth(u, white)
        }

        CrowdLevel.WTF -> {
            // X X 눈, 벌어진 입
            drawCrossEye(leftEye, u, white)
            drawCrossEye(rightEye, u, white)
            drawOpenMouth(u, white)
        }

        CrowdLevel.UNKNOWN -> {
            // 점 눈 — 아무 감정 없음. 데이터가 없다는 뜻입니다.
            drawCircle(white.copy(alpha = 0.55f), radius = 2.2f * u, center = leftEye)
            drawCircle(white.copy(alpha = 0.55f), radius = 2.2f * u, center = rightEye)
            repeat(3) { i ->
                drawCircle(
                    color = white.copy(alpha = 0.4f),
                    radius = 1.4f * u,
                    center = Offset((44f + i * 6f) * u, 60f * u),
                )
            }
        }
    }
}

private fun DrawScope.drawRoundEye(
    center: Offset,
    u: Float,
    white: Color,
    ink: Color,
    pupilOffset: Offset,
) {
    drawCircle(white, radius = 6f * u, center = center)
    drawCircle(ink, radius = 3f * u, center = center + pupilOffset)
    drawCircle(white, radius = 1.1f * u, center = center + pupilOffset + Offset(1.2f * u, -1.2f * u))
}

private fun DrawScope.drawArcEye(center: Offset, u: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x - 5f * u, center.y + 2f * u)
        quadraticTo(center.x, center.y - 6f * u, center.x + 5f * u, center.y + 2f * u)
    }
    drawPath(path, color, style = Stroke(width = 2.6f * u))
}

private fun DrawScope.drawCrossEye(center: Offset, u: Float, color: Color) {
    val s = 4.6f * u
    drawLine(color, center + Offset(-s, -s), center + Offset(s, s), strokeWidth = 2.6f * u)
    drawLine(color, center + Offset(s, -s), center + Offset(-s, s), strokeWidth = 2.6f * u)
}

private fun DrawScope.drawSwirlEye(center: Offset, u: Float, color: Color) {
    drawCircle(color, radius = 5.6f * u, center = center, style = Stroke(width = 1.8f * u))
    drawCircle(color, radius = 2.6f * u, center = center, style = Stroke(width = 1.8f * u))
}

private fun DrawScope.drawSmile(u: Float, width: Float, depth: Float, color: Color) {
    val path = Path().apply {
        moveTo((50f - width / 2f) * u, 58f * u)
        quadraticTo(50f * u, (58f + depth) * u, (50f + width / 2f) * u, 58f * u)
    }
    drawPath(path, color, style = Stroke(width = 2.4f * u))
}

private fun DrawScope.drawFlatMouth(u: Float, color: Color) {
    drawLine(
        color = color,
        start = Offset(44f * u, 60f * u),
        end = Offset(56f * u, 60f * u),
        strokeWidth = 2.4f * u,
    )
}

private fun DrawScope.drawWavyMouth(u: Float, color: Color) {
    val path = Path().apply {
        moveTo(41f * u, 60f * u)
        quadraticTo(44f * u, 56f * u, 47f * u, 60f * u)
        quadraticTo(50f * u, 64f * u, 53f * u, 60f * u)
        quadraticTo(56f * u, 56f * u, 59f * u, 60f * u)
    }
    drawPath(path, color, style = Stroke(width = 2.4f * u))
}

private fun DrawScope.drawOpenMouth(u: Float, color: Color) {
    drawOval(
        color = color,
        topLeft = Offset(44f * u, 55f * u),
        size = Size(12f * u, 9f * u),
    )
}

private fun DrawScope.drawFang(u: Float, color: Color) {
    val fang = Path().apply {
        moveTo(45f * u, 59f * u)
        lineTo(49f * u, 59f * u)
        lineTo(47f * u, 64f * u)
        close()
    }
    drawPath(fang, color)
}

/** 접근성 설명에 쓰는 한 단어. */
private fun CrowdLevel.moodWord(): String = when (this) {
    CrowdLevel.EASY -> "신난"
    CrowdLevel.BUSY -> "덤덤한"
    CrowdLevel.BAD -> "곤란한"
    CrowdLevel.HELL -> "어지러운"
    CrowdLevel.WTF -> "정신을 놓은"
    CrowdLevel.UNKNOWN -> "멍한"
}

// ── 미리보기 ────────────────────────────────────────────────────────────────

@Preview(name = "표정 6종 · 라이트", showBackground = true, backgroundColor = 0xFFFFF6EE)
@Composable
private fun HellFacePreviewLight() {
    HellStationTheme(darkTheme = false) {
        Row {
            CrowdLevel.entries.forEach { level ->
                HellFace(level = level, modifier = Modifier.size(60.dp))
            }
        }
    }
}

@Preview(name = "표정 6종 · 다크", showBackground = true, backgroundColor = 0xFF141020)
@Composable
private fun HellFacePreviewDark() {
    HellStationTheme(darkTheme = true) {
        Row {
            CrowdLevel.entries.forEach { level ->
                HellFace(level = level, modifier = Modifier.size(60.dp))
            }
        }
    }
}
