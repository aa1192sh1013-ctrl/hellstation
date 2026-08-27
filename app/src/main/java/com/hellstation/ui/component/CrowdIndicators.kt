package com.hellstation.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hellstation.domain.model.Confidence
import com.hellstation.domain.model.CrowdIndex
import com.hellstation.domain.model.CrowdLevel
import com.hellstation.ui.copy.HellCopy
import com.hellstation.ui.theme.HellStationTheme
import com.hellstation.ui.theme.HellTextStyles
import com.hellstation.ui.theme.HellTheme
import kotlin.math.roundToInt

/**
 * 혼잡도 등급 배지.
 *
 * **색과 글자를 항상 같이 냅니다.** 색만으로 등급을 표시하면 색각 이상 사용자와
 * 흑백 환경에서 정보가 사라집니다(docs/crowding-levels.md 4절 규칙 1).
 */
@Composable
fun CrowdBadge(
    level: CrowdLevel,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val colors = HellTheme.crowd.of(level)
    val background by animateColorAsState(colors.vivid, tween(320), label = "badgeBg")

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(if (compact) 7.dp else 10.dp))
            .background(background)
            .padding(
                horizontal = if (compact) 6.dp else 10.dp,
                vertical = if (compact) 2.dp else 5.dp,
            ),
    ) {
        Text(
            text = HellCopy.levelBadge(level),
            style = HellTextStyles.badge,
            color = colors.on,
        )
    }
}

/**
 * 혼잡도 게이지.
 *
 * **최대치가 200%입니다.** 100%는 "꽉 참"이 아니라 "정원"이고, 출퇴근 시간에는
 * 150%를 넘는 게 정상입니다. 100%에서 자르면 가장 지옥 같은 상황이 표현되지 않습니다
 * (docs/crowding-levels.md 4절 규칙 5).
 *
 * 100% 자리에 눈금을 그어서 "여기가 정원"이라는 걸 알 수 있게 했습니다.
 *
 * @param label 이 게이지가 무엇을 재는지 한 줄로. 열차 카드 옆에 놓일 때는 꼭 넣으세요 —
 *   카드가 "정보 없음"인데 게이지만 주황색이면 화면이 서로 반대말을 하는 것처럼 보입니다
 */
@Composable
fun CrowdGauge(
    crowd: CrowdIndex,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 12.dp,
    label: String? = null,
) {
    val colors = HellTheme.crowd.of(crowd.level)
    val target = ((crowd.percent ?: 0.0) / MAX_GAUGE_PERCENT).toFloat().coerceIn(0f, 1f)
    val fraction by animateFloatAsState(target, tween(420), label = "gauge")

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (crowd.level.isKnown) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(height)
                        .clip(CircleShape)
                        .background(colors.vivid),
                )
            }

            // 정원(100%) 눈금
            Box(
                modifier = Modifier
                    .fillMaxWidth(CAPACITY_MARK)
                    .height(height),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 2.dp, height = height)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)),
                )
            }
        }
    }
}

/**
 * 혼잡도 숫자.
 *
 * **신뢰도가 LOW면 숫자를 보여주지 않습니다.** 정확해 보이는 숫자는 신뢰를 과장합니다
 * (docs/crowding-levels.md 4절 규칙 4). 그 판단은 도메인이 이미 해 두었으므로
 * [CrowdIndex.showsPercent]만 확인하면 됩니다.
 */
@Composable
fun CrowdPercentText(
    crowd: CrowdIndex,
    modifier: Modifier = Modifier,
    large: Boolean = false,
) {
    val colors = HellTheme.crowd.of(crowd.level)
    val style = if (large) HellTextStyles.boardNumber else HellTextStyles.boardNumberSmall

    if (crowd.showsPercent) {
        Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
            Text(
                text = crowd.percent!!.roundToInt().toString(),
                style = style,
                color = colors.vivid,
            )
            Text(
                text = "%",
                style = MaterialTheme.typography.titleMedium,
                color = colors.vivid.copy(alpha = 0.75f),
                modifier = Modifier.padding(bottom = if (large) 10.dp else 3.dp, start = 2.dp),
            )
        }
    } else {
        // 숫자 대신 등급만. 자리가 비어 보이지 않도록 같은 크기를 유지합니다.
        Text(
            text = HellCopy.levelBadge(crowd.level),
            style = style,
            color = colors.vivid,
            modifier = modifier,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 신뢰도 안내 줄.
 *
 * 신뢰도가 `HIGH`면 아무것도 그리지 않습니다 — 매번 뭔가 적혀 있으면
 * 정작 낮을 때 눈에 안 들어옵니다.
 */
@Composable
fun ConfidenceNote(
    crowd: CrowdIndex,
    modifier: Modifier = Modifier,
) {
    val note = HellCopy.confidenceNote(crowd.confidence, crowd.tier) ?: return
    val emphasis = crowd.confidence == Confidence.LOW

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (emphasis) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = if (emphasis) "!" else "i",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 6.dp, vertical = 1.dp),
        )
        Text(
            text = note,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 데이터 출처 칩. "실시간 / 예상 / 참고용" */
@Composable
fun ConfidenceChip(
    crowd: CrowdIndex,
    modifier: Modifier = Modifier,
) {
    val colors = HellTheme.crowd.of(crowd.level)
    Text(
        text = HellCopy.confidenceBadge(crowd.confidence),
        style = MaterialTheme.typography.labelSmall,
        color = colors.onSoft,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(colors.soft)
            .border(1.dp, colors.vivid.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/**
 * 노선 색 동그라미 안에 번호. 역 이름 옆에 붙입니다.
 *
 * 글자색을 흰색으로 고정하지 않습니다. 우이신설선(#B7C450)이나 서해선(#8FC31F)처럼
 * 밝은 노선색 위에서는 흰 글자가 안 보이기 때문입니다.
 */
@Composable
fun LineDot(
    color: Color,
    label: String,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 22.dp,
) {
    val luminance = color.red * 0.299f + color.green * 0.587f + color.blue * 0.114f
    val labelColor = if (luminance > 0.62f) Color(0xFF1B1030) else Color.White

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = labelColor,
        )
    }
}

private const val MAX_GAUGE_PERCENT = 200.0
private const val CAPACITY_MARK = 100f / MAX_GAUGE_PERCENT.toFloat()

// ── 미리보기 ────────────────────────────────────────────────────────────────

@Preview(name = "혼잡도 표시 · 라이트", showBackground = true, backgroundColor = 0xFFFFF6EE)
@Composable
private fun IndicatorsPreviewLight() {
    HellStationTheme(darkTheme = false) { IndicatorsPreviewBody() }
}

@Preview(name = "혼잡도 표시 · 다크", showBackground = true, backgroundColor = 0xFF141020)
@Composable
private fun IndicatorsPreviewDark() {
    HellStationTheme(darkTheme = true) { IndicatorsPreviewBody() }
}

@Composable
private fun IndicatorsPreviewBody() {
    val now = java.time.Instant.parse("2026-08-24T23:00:00Z")
    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        listOf(20.0, 60.0, 105.0, 150.0, 190.0, null).forEach { percent ->
            val crowd = CrowdIndex.of(
                percent = percent,
                confidence = Confidence.MEDIUM,
                source = com.hellstation.domain.model.CrowdSource.BASELINE,
                at = now,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CrowdBadge(crowd.level)
                ConfidenceChip(crowd)
                CrowdGauge(crowd, modifier = Modifier.weight(1f))
            }
        }
    }
}
