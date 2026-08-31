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
 * 눈. 창 너머로 타오르는 **불꽃 두 개**입니다.
 *
 * 처음에는 가로 빛줄기였는데, 전동차 계기판처럼 보여서 악마가 남지 않았습니다.
 * 불꽃으로 바꾸니 **차가운 쇳덩이 안에서 뭔가 타고 있는** 그림이 됩니다.
 *
 * 두 불꽃 모두 **바깥쪽으로 눕힙니다.** 화난 눈썹이 `\ /` 모양인 것과 같습니다 —
 * 안쪽(미간 쪽)이 낮고 바깥쪽이 높아야 화난 얼굴입니다. 반대로 안쪽으로 모으면
 * `/ \` 가 되어 **걱정하거나 울상인 얼굴**이 됩니다. 처음에 이걸 뒤집어 그렸습니다.
 *
 * 등급은 **불길의 세기**로 나타냅니다. 여유는 작은 불씨, 대환장은 눈 밖으로 넘칩니다.
 * 흰자와 동공을 그리면 그 순간 만화가 되므로 끝까지 넣지 않습니다.
 */
private fun DrawScope.drawEyes(u: Float, level: CrowdLevel, color: Color) {
    val leftCenter = Offset(41f * u, 51f * u)
    val rightCenter = Offset(59f * u, 51f * u)

    // 0(불씨) ~ 1(활활). 등급이 올라갈수록 커지고 사나워집니다.
    val heat = when (level) {
        CrowdLevel.EASY -> 0.20f
        CrowdLevel.BUSY -> 0.42f
        CrowdLevel.BAD -> 0.62f
        CrowdLevel.HELL -> 0.85f
        CrowdLevel.WTF -> 1f
        CrowdLevel.UNKNOWN -> 0.12f
    }
    val ink = if (level.isKnown) color else color.copy(alpha = 0.5f)

    // 왼쪽 불은 왼쪽으로, 오른쪽 불은 오른쪽으로 눕습니다.
    drawFlameEye(leftCenter, u, ink, outward = -1f, heat = heat)
    drawFlameEye(rightCenter, u, ink, outward = 1f, heat = heat)
}

/**
 * 불꽃 하나.
 *
 * ## 삼각형이 되지 않게 하는 것
 *
 * 처음엔 위가 뾰족한 삼각형처럼 보였습니다. 불꽃으로 읽히려면 세 가지가 필요합니다.
 * **둥근 바닥**, 위로 갈수록 **잘록해지는 허리**, 한쪽으로 **눕는 끝**.
 * 셋 중 하나만 빠져도 그냥 세모입니다.
 *
 * @param outward 바깥쪽이 어느 쪽인가. 왼쪽 눈은 -1, 오른쪽 눈은 +1.
 *   불끝을 **바깥으로** 눕혀야 안쪽이 낮아지면서 화난 눈썹 모양이 됩니다
 * @param heat 불길의 세기 0~1
 */
private fun DrawScope.drawFlameEye(
    center: Offset,
    u: Float,
    color: Color,
    outward: Float,
    heat: Float,
) {
    val halfWidth = (3.2f + 1.2f * heat) * u
    val height = (8.5f + 6f * heat) * u
    val bottom = center.y + height * 0.34f
    val tipX = center.x + outward * (2.4f + 2.4f * heat) * u

    drawPath(flamePath(center.x, bottom, halfWidth, height, tipX), color)

    // 심지. 안쪽을 한 겹 밝게 해야 그림이 아니라 불로 보입니다.
    // 끝을 덜 눕혀야 겉불꽃 안에 들어앉습니다.
    drawPath(
        flamePath(
            centerX = center.x,
            bottom = bottom - height * 0.06f,
            halfWidth = halfWidth * 0.46f,
            height = height * 0.55f,
            tipX = center.x + (tipX - center.x) * 0.45f,
        ),
        Color.White.copy(alpha = 0.5f + 0.28f * heat),
    )
}

/** 불꽃 윤곽 하나. 바닥이 둥글고 허리가 잘록하며 끝이 [tipX] 쪽으로 눕습니다. */
private fun flamePath(
    centerX: Float,
    bottom: Float,
    halfWidth: Float,
    height: Float,
    tipX: Float,
): Path = Path().apply {
    val top = bottom - height
    moveTo(centerX - halfWidth, bottom)
    // 바깥쪽 옆구리 -> 잘록한 허리 -> 뾰족한 끝
    cubicTo(
        centerX - halfWidth * 1.12f, bottom - height * 0.42f,
        tipX - halfWidth * 0.95f, bottom - height * 0.66f,
        tipX, top,
    )
    // 끝 -> 반대쪽 옆구리
    cubicTo(
        tipX + halfWidth * 0.55f, bottom - height * 0.62f,
        centerX + halfWidth * 1.12f, bottom - height * 0.40f,
        centerX + halfWidth, bottom,
    )
    // 둥근 바닥. 이게 있어야 세모가 아니라 불이 됩니다.
    cubicTo(
        centerX + halfWidth, bottom + halfWidth * 0.85f,
        centerX - halfWidth, bottom + halfWidth * 0.85f,
        centerX - halfWidth, bottom,
    )
    close()
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
