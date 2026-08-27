package com.hellstation.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hellstation.domain.model.CrowdLevel
import com.hellstation.domain.model.TimeSlot
import com.hellstation.ui.theme.HellStationTheme
import com.hellstation.ui.theme.HellTextStyles
import com.hellstation.ui.theme.HellTheme
import kotlin.math.roundToInt

/**
 * 시간대를 옮겨 가며 혼잡도를 미리 보는 슬라이더.
 *
 * ## 왜 하루 곡선을 뒤에 깔았나
 *
 * 슬라이더만 있으면 사용자가 **일일이 끌어 보며 언제가 한산한지 찾아야 합니다.**
 * 뒤에 하루치 색 막대를 깔아 두면 끌기 전에 "8시가 제일 빨갛네"를 한눈에 봅니다.
 * 슬라이더는 그 다음에 확인용으로 쓰는 도구가 됩니다.
 *
 * 범위는 05:30~24:30입니다. 통계가 그만큼만 있어서 그 밖은 보여줄 값이 없습니다.
 *
 * @param levels 시간대별 등급. [TimeSlot.ALL]과 같은 순서·길이여야 합니다
 * @param nowSlot 지금 시각이 속한 시간대. 눈금에 표시합니다
 */
@Composable
fun TimeSlider(
    selected: TimeSlot,
    levels: List<CrowdLevel>,
    nowSlot: TimeSlot,
    onSelect: (TimeSlot) -> Unit,
    modifier: Modifier = Modifier,
) {
    val slots = TimeSlot.ALL
    val palette = HellTheme.crowd
    val selectedIndex = slots.indexOfFirst { it.minutesFromMidnight == selected.minutesFromMidnight }
        .coerceAtLeast(0)
    val nowIndex = slots.indexOfFirst { it.minutesFromMidnight == nowSlot.minutesFromMidnight }
    val isNow = selectedIndex == nowIndex

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = selected.displayLabel,
                    style = HellTextStyles.boardNumberSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (isNow) {
                    Text(
                        text = "지금",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                } else {
                    Text(
                        text = "예상",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            if (!isNow && nowIndex >= 0) {
                TextButton(onClick = { onSelect(slots[nowIndex]) }) {
                    Text("지금으로", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        // 하루치 색 막대
        Box(modifier = Modifier.fillMaxWidth()) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .semantics { contentDescription = "시간대별 혼잡도 미리보기" },
            ) {
                if (levels.isEmpty()) return@Canvas
                val gap = 1.5f
                val barWidth = (size.width - gap * (levels.size - 1)) / levels.size

                levels.forEachIndexed { index, level ->
                    val colors = palette.of(level)
                    // 나쁜 등급일수록 막대가 높습니다. 색과 높이 두 가지로 표현해서
                    // 색을 구분하기 어려운 사용자도 모양으로 읽을 수 있게 합니다.
                    val heightRatio = when (level) {
                        CrowdLevel.EASY -> 0.28f
                        CrowdLevel.BUSY -> 0.45f
                        CrowdLevel.BAD -> 0.66f
                        CrowdLevel.HELL -> 0.86f
                        CrowdLevel.WTF -> 1f
                        CrowdLevel.UNKNOWN -> 0.16f
                    }
                    val barHeight = size.height * heightRatio
                    val left = index * (barWidth + gap)
                    val isSelectedBar = index == selectedIndex

                    drawRoundRect(
                        color = if (isSelectedBar) colors.vivid else colors.vivid.copy(alpha = 0.55f),
                        topLeft = Offset(left, size.height - barHeight),
                        size = Size(barWidth, barHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2.5f),
                    )
                }

                // 지금 시각 눈금
                if (nowIndex >= 0) {
                    val x = nowIndex * (barWidth + gap) + barWidth / 2f
                    drawLine(
                        color = Color(0xFFFF3D8B),
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1.6f,
                    )
                }
            }
        }

        Slider(
            value = selectedIndex.toFloat(),
            onValueChange = { value ->
                slots.getOrNull(value.roundToInt())?.let(onSelect)
            },
            valueRange = 0f..(slots.size - 1).toFloat(),
            steps = (slots.size - 2).coerceAtLeast(0),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                // 39칸이라 눈금점을 다 찍으면 지저분합니다.
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
            modifier = Modifier.semantics {
                contentDescription = "시간대 선택. 현재 ${selected.displayLabel}"
            },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = TimeSlot.FIRST.displayLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = TimeSlot.LAST.displayLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(name = "Time Slider · 라이트", showBackground = true, backgroundColor = 0xFFFFF6EE)
@Composable
private fun TimeSliderPreviewLight() {
    HellStationTheme(darkTheme = false) { TimeSliderPreviewBody() }
}

@Preview(name = "Time Slider · 다크", showBackground = true, backgroundColor = 0xFF141020)
@Composable
private fun TimeSliderPreviewDark() {
    HellStationTheme(darkTheme = true) { TimeSliderPreviewBody() }
}

@Composable
private fun TimeSliderPreviewBody() {
    val levels = TimeSlot.ALL.map { slot ->
        when (slot.minutesFromMidnight) {
            in 450..510 -> CrowdLevel.WTF
            in 420..570 -> CrowdLevel.HELL
            in 1050..1140 -> CrowdLevel.HELL
            in 1020..1200 -> CrowdLevel.BAD
            in 600..1020 -> CrowdLevel.BUSY
            else -> CrowdLevel.EASY
        }
    }
    Box(Modifier.background(MaterialTheme.colorScheme.background).padding(12.dp)) {
        TimeSlider(
            selected = TimeSlot(8 * 60),
            levels = levels,
            nowSlot = TimeSlot(12 * 60),
            onSelect = {},
        )
    }
}
