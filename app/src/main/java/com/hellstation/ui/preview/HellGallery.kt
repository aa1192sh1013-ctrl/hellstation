package com.hellstation.ui.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hellstation.domain.model.Confidence
import com.hellstation.domain.model.CrowdIndex
import com.hellstation.domain.model.CrowdLevel
import com.hellstation.domain.model.CrowdSource
import com.hellstation.domain.model.Direction
import com.hellstation.domain.model.ServiceStatus
import com.hellstation.domain.model.TimeSlot
import com.hellstation.ui.character.HellFace
import com.hellstation.ui.component.CrowdBadge
import com.hellstation.ui.copy.HellCopy
import com.hellstation.ui.sample.SampleCrowd
import com.hellstation.ui.sample.SampleMetro
import com.hellstation.ui.station.StationSheetContent
import com.hellstation.ui.theme.HellStationTheme
import com.hellstation.ui.theme.HellTheme
import java.time.Instant

/**
 * 디자인 확인용 모음. 앱에서는 쓰이지 않고 **Android Studio 미리보기에서만** 봅니다.
 *
 * ## 여기서 확인할 것
 *
 * 1. **다크 모드에서 다섯 단계가 다 구분되는가** — [CrowdPalettePreview]
 *    라이트와 다크를 나란히 놓았습니다. 인접한 두 칸이 비슷해 보이면 색을 다시 잡아야 합니다.
 * 2. **극단적인 경우에도 화면이 안 깨지는가** — [ExtremeStatesPreview]
 *    운행 중단, 지연, 자료 없음처럼 잘 안 나오는 상황들입니다.
 *    실제 데이터를 붙이면 반드시 마주치게 됩니다.
 */

private val PREVIEW_TIME: Instant = Instant.parse("2026-08-24T23:00:00Z")
private val PEAK_SLOT = TimeSlot(8 * 60)

@Preview(name = "혼잡도 5단계 · 라이트/다크 비교", showBackground = true, widthDp = 400, heightDp = 560)
@Composable
private fun CrowdPalettePreview() {
    Row(Modifier.fillMaxWidth()) {
        HellStationTheme(darkTheme = false) {
            PaletteColumn(title = "라이트", modifier = Modifier.weight(1f))
        }
        HellStationTheme(darkTheme = true) {
            PaletteColumn(title = "다크", modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun PaletteColumn(title: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        CrowdLevel.entries.forEach { level ->
            val colors = HellTheme.crowd.of(level)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(colors.vivid),
                )
                HellFace(level = level, showGlow = false, modifier = Modifier.size(34.dp))
                Column {
                    CrowdBadge(level, compact = true)
                    Text(
                        text = HellCopy.levelHeadline(level),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
        }
    }
}

@Preview(name = "극단 상황 · 라이트", showBackground = true, widthDp = 380, heightDp = 1400)
@Composable
private fun ExtremeStatesPreviewLight() {
    HellStationTheme(darkTheme = false) { ExtremeStatesBody() }
}

@Preview(name = "극단 상황 · 다크", showBackground = true, widthDp = 380, heightDp = 1400)
@Composable
private fun ExtremeStatesPreviewDark() {
    HellStationTheme(darkTheme = true) { ExtremeStatesBody() }
}

@Composable
private fun ExtremeStatesBody() {
    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState()),
    ) {
        SampleCrowd.debugStates(PREVIEW_TIME, PEAK_SLOT).forEach { (label, board) ->
            Text(
                text = "── $label ──",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 4.dp),
            )
            StationSheetContent(
                board = board,
                onDirectionChange = {},
                onFindRoute = {},
            )
        }
    }
}

// ── 화면 크기 ───────────────────────────────────────────────────────────────

/**
 * 좁은 기기(폴드 접은 상태 등). 캐릭터·제목·큰 숫자가 한 줄에 안 들어가는 폭입니다.
 * 세로로 쌓여야 하고 글자가 끊기면 안 됩니다.
 */
@Preview(name = "폭 320dp (좁은 폰)", showBackground = true, widthDp = 320, heightDp = 720)
@Composable
private fun NarrowSheetPreview() {
    HellStationTheme(darkTheme = false) { SheetPreviewBody() }
}

/** 보통 폰. */
@Preview(name = "폭 360dp (보통 폰)", showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun RegularSheetPreview() {
    HellStationTheme(darkTheme = false) { SheetPreviewBody() }
}

/** 가로 모드. 세로가 짧아 여백이 줄고 범례가 접힙니다. */
@Preview(name = "가로 모드 (740x360)", showBackground = true, widthDp = 740, heightDp = 360)
@Composable
private fun LandscapeSheetPreview() {
    HellStationTheme(darkTheme = false) { SheetPreviewBody() }
}

/** 태블릿. 시트가 지나치게 늘어지지 않는지 봅니다. */
@Preview(name = "태블릿 (840dp)", showBackground = true, widthDp = 840, heightDp = 900)
@Composable
private fun TabletSheetPreview() {
    HellStationTheme(darkTheme = false) { SheetPreviewBody() }
}

@Composable
private fun SheetPreviewBody() {
    val board = SampleCrowd.boardFor(
        SampleMetro.gangnamStation,
        Direction.UP,
        PREVIEW_TIME,
        PEAK_SLOT,
    )
    Box(Modifier.background(MaterialTheme.colorScheme.surface)) {
        StationSheetContent(board = board, onDirectionChange = {}, onFindRoute = {})
    }
}

// ── 말투 ────────────────────────────────────────────────────────────────────

/**
 * **완료조건 확인용.** 같은 혼잡도라도 상황에 따라 말투가 갈리는지 봅니다.
 *
 * 왼쪽은 평소(장난스러움), 오른쪽은 예외 상황(담백함)입니다.
 * 오른쪽에 농담이 하나라도 섞여 있으면 [HellCopy.toneFor] 가 잘못된 것입니다.
 */
@Preview(name = "말투 · 평소 vs 예외", showBackground = true, widthDp = 420, heightDp = 700)
@Composable
private fun CopyTonePreview() {
    HellStationTheme(darkTheme = false) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "장난스러운 말투 (평소)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            listOf(CrowdLevel.EASY, CrowdLevel.BUSY, CrowdLevel.BAD, CrowdLevel.HELL, CrowdLevel.WTF)
                .forEach { level ->
                    CopyRow(
                        crowd = CrowdIndex.of(
                            percent = midPercentOf(level),
                            confidence = Confidence.HIGH,
                            source = CrowdSource.REALTIME_BASELINE,
                            at = PREVIEW_TIME,
                        ),
                        status = ServiceStatus.NORMAL,
                    )
                }

            Text(
                text = "담백한 안내 (예외 상황)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
            val hell = CrowdIndex.of(
                percent = 150.0,
                confidence = Confidence.HIGH,
                source = CrowdSource.REALTIME_BASELINE,
                at = PREVIEW_TIME,
            )
            CopyRow(hell, ServiceStatus.DELAYED, "지연")
            CopyRow(hell, ServiceStatus.SUSPENDED, "운행 중단")
            CopyRow(hell, ServiceStatus.CLOSED, "운행 시간 아님")
            CopyRow(
                CrowdIndex.of(150.0, Confidence.LOW, CrowdSource.BASELINE, PREVIEW_TIME),
                ServiceStatus.NORMAL,
                "신뢰도 낮음",
            )
            CopyRow(CrowdIndex.unknown(PREVIEW_TIME), ServiceStatus.NORMAL, "자료 없음")
        }
    }
}

@Composable
private fun CopyRow(
    crowd: CrowdIndex,
    status: ServiceStatus,
    label: String? = null,
) {
    val copy = HellCopy.headline(crowd, status, seed = "강남")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CrowdBadge(crowd.level, compact = true)
        Column {
            Text(
                text = copy.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = copy.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (label != null) {
                Text(
                    text = "[$label] ${copy.tone}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

private fun midPercentOf(level: CrowdLevel): Double = when (level) {
    CrowdLevel.EASY -> 25.0
    CrowdLevel.BUSY -> 62.0
    CrowdLevel.BAD -> 105.0
    CrowdLevel.HELL -> 150.0
    CrowdLevel.WTF -> 190.0
    CrowdLevel.UNKNOWN -> -1.0
}
