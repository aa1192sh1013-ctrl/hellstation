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
import com.hellstation.ui.theme.HellMagenta
import com.hellstation.ui.theme.HellStationTheme
import com.hellstation.ui.theme.HellTheme
import com.hellstation.ui.theme.NeonCyan
import com.hellstation.ui.theme.SteelBody
import com.hellstation.ui.theme.SteelEdge
import kotlin.math.sin

/**
 * HellStation 대표 캐릭터 — **전동차 앞면에 악마 뿔**.
 *
 * ## 왜 다시 그렸나
 *
 * 처음 버전은 노란 상자에 큰 동그란 눈, 토끼귀 같은 뿔, 돌돌 말린 꼬리였습니다.
 * 귀엽긴 한데 **유아용 캐릭터**로 읽혔습니다. 이 앱을 여는 사람은 출퇴근길의 직장인입니다.
 *
 * 지금은 **실제 전동차 전면부**를 기준으로 잡았습니다 — 각진 차체, 넓은 운전실 창,
 * 행선표시기, 헤드라이트, 아래쪽 배장기. 거기에 악마를 얹되 귀엽게가 아니라
 * **날카롭게**: 뒤로 젖혀진 뿔, 창 너머로 빛나는 가는 눈, 각진 꼬리.
 *
 * ## 표정은 눈매 하나로
 *
 * 동그란 눈에 흰자·하이라이트를 넣으면 바로 만화가 됩니다. 그래서 **가로로 긴 빛줄기**
 * 하나만 두고 **기울기와 꺾임**으로 감정을 냅니다. 여유로우면 완만하고, 지옥이면
 * 안쪽이 치켜 올라가고, 대환장이면 지그재그로 갈라집니다.
 *
 * 이미지가 아니라 Canvas라서 어느 크기로 키워도 깨지지 않고 APK도 안 무거워집니다.
 *
 * ## 어디에 쓰나
 *
 * **평소에는 안 보이는 편이 좋습니다.** 화면마다 큼직하게 박아 두면 다시 유아용이 됩니다.
 * 지옥(HELL·WTF)일 때, 빈 화면일 때, 실패했을 때처럼 **말이 필요한 순간**에만 부르세요.
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

    val accent = HellTheme.crowd.of(level).vivid

    Canvas(
        modifier = modifier.semantics { contentDescription = description },
    ) {
        val unit = size.minDimension / 100f
        val offsetY = if (animate) sin(phase) * 2.2f * unit else 0f

        // 가운데 100x100 좌표계로 옮겨서 그립니다.
        val originX = (size.width - 100f * unit) / 2f
        val originY = (size.height - 100f * unit) / 2f

        translate(left = originX, top = originY + offsetY) {
            if (showGlow) drawGlow(unit, accent)
            drawTail(unit, accent)
            drawHorns(unit, accent)
            drawBody(unit)
            drawDestinationSign(unit)
            drawWindshield(unit)
            drawEyes(unit, level, accent)
            drawSkirt(unit)
            drawHeadlights(unit, accent, level)
        }
    }
}

// ── 부위별 그리기 ────────────────────────────────────────────────────────────

/** 뒤에 깔리는 불빛. 어두운 바탕에서 네온 간판처럼 번지게 합니다. */
private fun DrawScope.drawGlow(u: Float, color: Color) {
    drawCircle(color.copy(alpha = 0.13f), radius = 48f * u, center = Offset(50f * u, 52f * u))
    drawCircle(color.copy(alpha = 0.09f), radius = 38f * u, center = Offset(50f * u, 52f * u))
}

/**
 * 뿔. 토끼귀가 아니라 **뒤로 젖혀진 뿔**입니다.
 *
 * 위로 곧게 세우면 동물 귀처럼 보입니다. 바깥으로 젖히고 끝을 뾰족하게 깎아야
 * 악마 쪽으로 읽힙니다.
 */
private fun DrawScope.drawHorns(u: Float, color: Color) {
    val left = Path().apply {
        moveTo(33f * u, 20f * u)
        lineTo(21f * u, 2f * u)
        lineTo(43f * u, 14f * u)
        close()
    }
    val right = Path().apply {
        moveTo(67f * u, 20f * u)
        lineTo(79f * u, 2f * u)
        lineTo(57f * u, 14f * u)
        close()
    }
    drawPath(left, color)
    drawPath(right, color)
}

/** 차체. 각진 쇳덩이입니다. 위쪽만 살짝 깎고 아래는 곧게 둡니다. */
private fun DrawScope.drawBody(u: Float) {
    drawRoundRect(
        color = SteelBody,
        topLeft = Offset(20f * u, 14f * u),
        size = Size(60f * u, 70f * u),
        cornerRadius = CornerRadius(11f * u, 11f * u),
    )
    drawRoundRect(
        color = SteelEdge,
        topLeft = Offset(20f * u, 14f * u),
        size = Size(60f * u, 70f * u),
        cornerRadius = CornerRadius(11f * u, 11f * u),
        style = Stroke(width = 2f * u),
    )
}

/** 행선표시기. 실제 전동차 이마에 있는 LED 판입니다. 이것 하나로 "전철"이 됩니다. */
private fun DrawScope.drawDestinationSign(u: Float) {
    drawRoundRect(
        color = Color(0xFF0B0913),
        topLeft = Offset(31f * u, 21f * u),
        size = Size(38f * u, 10f * u),
        cornerRadius = CornerRadius(2f * u, 2f * u),
    )
    // LED 글자 대신 점 세 개. 작게 줄여도 뭉개지지 않습니다.
    for (i in 0 until 3) {
        drawRoundRect(
            color = NeonCyan.copy(alpha = 0.85f),
            topLeft = Offset((37f + i * 9f) * u, 24f * u),
            size = Size(5f * u, 4f * u),
            cornerRadius = CornerRadius(1f * u, 1f * u),
        )
    }
}

/** 운전실 창. 아래로 갈수록 넓어지는 사다리꼴이라야 전동차처럼 보입니다. */
private fun DrawScope.drawWindshield(u: Float) {
    val glass = Path().apply {
        moveTo(31f * u, 38f * u)
        lineTo(69f * u, 38f * u)
        lineTo(73f * u, 63f * u)
        lineTo(27f * u, 63f * u)
        close()
    }
    drawPath(glass, Color(0xFF090810))
    drawPath(glass, SteelEdge.copy(alpha = 0.7f), style = Stroke(width = 1.6f * u))
}

/**
 * 눈. 창 너머로 보이는 **빛줄기 두 개**입니다.
 *
 * 흰자와 동공을 그리면 바로 만화가 됩니다. 가로로 긴 선 하나만 두고
 * 기울기로 감정을 냅니다.
 */
private fun DrawScope.drawEyes(u: Float, level: CrowdLevel, color: Color) {
    val leftCenter = Offset(41f * u, 50f * u)
    val rightCenter = Offset(59f * u, 50f * u)

    when (level) {
        // 여유 — 완만하게 처진 눈. 느긋합니다.
        CrowdLevel.EASY -> {
            drawSlit(leftCenter, u, color, tilt = -0.30f)
            drawSlit(rightCenter, u, color, tilt = 0.30f)
        }
        // 보통 — 수평. 무표정.
        CrowdLevel.BUSY -> {
            drawSlit(leftCenter, u, color, tilt = 0f)
            drawSlit(rightCenter, u, color, tilt = 0f)
        }
        // 혼잡 — 안쪽이 조금 올라갑니다. 슬슬 못마땅합니다.
        CrowdLevel.BAD -> {
            drawSlit(leftCenter, u, color, tilt = 0.34f)
            drawSlit(rightCenter, u, color, tilt = -0.34f)
        }
        // 지옥 — 안쪽이 크게 치켜 올라갑니다. 화가 났습니다.
        CrowdLevel.HELL -> {
            drawSlit(leftCenter, u, color, tilt = 0.75f, thickness = 4.2f)
            drawSlit(rightCenter, u, color, tilt = -0.75f, thickness = 4.2f)
        }
        // 대환장 — 지그재그. 회로가 나갔습니다.
        CrowdLevel.WTF -> {
            drawZigzag(leftCenter, u, color)
            drawZigzag(rightCenter, u, color)
        }
        // 모름 — 꺼진 눈.
        CrowdLevel.UNKNOWN -> {
            drawSlit(leftCenter, u, color.copy(alpha = 0.45f), tilt = 0f, thickness = 2.4f)
            drawSlit(rightCenter, u, color.copy(alpha = 0.45f), tilt = 0f, thickness = 2.4f)
        }
    }
}

/**
 * 빛줄기 한 줄.
 *
 * @param tilt 안쪽 끝을 얼마나 올릴지. 양수면 치켜뜬 눈이 됩니다
 */
private fun DrawScope.drawSlit(
    center: Offset,
    u: Float,
    color: Color,
    tilt: Float,
    thickness: Float = 3.4f,
) {
    val half = 7f * u
    val lift = tilt * 5f * u
    drawLine(
        color = color,
        start = Offset(center.x - half, center.y + lift),
        end = Offset(center.x + half, center.y - lift),
        strokeWidth = thickness * u,
    )
}

/** 대환장 눈. 회로가 튄 것처럼 꺾입니다. */
private fun DrawScope.drawZigzag(center: Offset, u: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x - 7f * u, center.y + 3f * u)
        lineTo(center.x - 2f * u, center.y - 3f * u)
        lineTo(center.x + 2f * u, center.y + 3f * u)
        lineTo(center.x + 7f * u, center.y - 3f * u)
    }
    drawPath(path, color, style = Stroke(width = 3.2f * u))
}

/** 배장기. 전동차 아래쪽 검은 치마. 이게 있어야 바닥이 붕 뜨지 않습니다. */
private fun DrawScope.drawSkirt(u: Float) {
    val skirt = Path().apply {
        moveTo(24f * u, 78f * u)
        lineTo(76f * u, 78f * u)
        lineTo(71f * u, 90f * u)
        lineTo(29f * u, 90f * u)
        close()
    }
    drawPath(skirt, Color(0xFF16141F))
    drawPath(skirt, SteelEdge.copy(alpha = 0.6f), style = Stroke(width = 1.6f * u))
}

/** 헤드라이트. 지옥 쪽에서는 등급 색으로 물들어 경고등처럼 보입니다. */
private fun DrawScope.drawHeadlights(u: Float, accent: Color, level: CrowdLevel) {
    val lamp = when (level) {
        CrowdLevel.HELL, CrowdLevel.WTF -> accent
        else -> NeonCyan
    }
    for (x in listOf(31f, 69f)) {
        drawCircle(lamp.copy(alpha = 0.28f), radius = 6.4f * u, center = Offset(x * u, 71f * u))
        drawCircle(lamp, radius = 3.4f * u, center = Offset(x * u, 71f * u))
    }
}

/** 꼬리. 돌돌 말린 하트 꼬리가 아니라 **각진 화살촉**입니다. */
private fun DrawScope.drawTail(u: Float, color: Color) {
    val tail = Path().apply {
        moveTo(79f * u, 66f * u)
        cubicTo(92f * u, 64f * u, 95f * u, 74f * u, 88f * u, 82f * u)
    }
    drawPath(tail, color, style = Stroke(width = 3.2f * u))

    val tip = Path().apply {
        moveTo(88f * u, 80f * u)
        lineTo(96f * u, 86f * u)
        lineTo(85f * u, 90f * u)
        close()
    }
    drawPath(tip, color)
}

/**
 * 이 등급에서 캐릭터를 **큼직하게** 보여줄 것인가.
 *
 * 평소에도 띄우면 정보 화면이 캐릭터 화면이 됩니다. 지옥에서만 나와야
 * "나왔다"는 사실 자체가 신호가 됩니다 — 숫자를 안 읽어도 오늘이 어떤 날인지 압니다.
 *
 * 빈 화면·실패 화면은 예외입니다. 거기서는 등급이 아니라 **말을 걸 상대**가 필요합니다.
 */
val CrowdLevel.showsMascot: Boolean
    get() = this == CrowdLevel.HELL || this == CrowdLevel.WTF

private fun CrowdLevel.moodWord(): String = when (this) {
    CrowdLevel.EASY -> "느긋한"
    CrowdLevel.BUSY -> "덤덤한"
    CrowdLevel.BAD -> "못마땅한"
    CrowdLevel.HELL -> "성난"
    CrowdLevel.WTF -> "회로가 나간"
    CrowdLevel.UNKNOWN -> "멍한"
}

@Preview(name = "표정 6단계 · 다크", showBackground = true, backgroundColor = 0xFF0A0912)
@Composable
private fun PreviewFacesDark() {
    HellStationTheme(darkTheme = true) {
        Row {
            CrowdLevel.entries.forEach { HellFace(level = it, modifier = Modifier.size(72.dp)) }
        }
    }
}

@Preview(name = "표정 6단계 · 라이트", showBackground = true, backgroundColor = 0xFFF4F3F7)
@Composable
private fun PreviewFacesLight() {
    HellStationTheme(darkTheme = false) {
        Row {
            CrowdLevel.entries.forEach { HellFace(level = it, modifier = Modifier.size(72.dp)) }
        }
    }
}
