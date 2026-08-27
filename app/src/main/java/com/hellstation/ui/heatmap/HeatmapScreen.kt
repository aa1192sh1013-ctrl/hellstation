package com.hellstation.ui.heatmap

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hellstation.domain.model.CrowdLevel
import com.hellstation.domain.model.Direction
import com.hellstation.domain.model.Loadable
import com.hellstation.domain.model.ServiceStatus
import com.hellstation.domain.model.Station
import com.hellstation.domain.model.TimeSlot
import com.hellstation.ui.character.HellFace
import com.hellstation.ui.component.CrowdBadge
import com.hellstation.ui.component.HellMotion
import com.hellstation.ui.component.LoadFailure
import com.hellstation.ui.component.TimeSlider
import com.hellstation.ui.copy.HellCopy
import com.hellstation.ui.map.MetroMapView
import com.hellstation.ui.map.rememberMetroMapState
import com.hellstation.ui.state.LocalAppSettings
import com.hellstation.ui.state.rememberHeatmapData
import com.hellstation.ui.state.rememberNow
import com.hellstation.ui.state.rememberNowSlot
import com.hellstation.ui.state.rememberStationBoard
import com.hellstation.ui.station.StationSheetContent
import com.hellstation.ui.theme.HellStationTheme
import com.hellstation.ui.theme.HellTheme
import java.time.Instant

/**
 * 앱의 홈 화면. **검색창보다 지도가 먼저 보입니다.**
 *
 * 이 앱을 여는 사람은 "지금 어디가 지옥인가"를 알고 싶어 합니다.
 * 검색창을 먼저 내밀면 목적지를 정하고 온 사람만 쓸 수 있는 앱이 됩니다.
 *
 * ## 화면 구성
 *
 * ```
 * ┌──────────────────────────┐
 * │ HellStation      [검색]   │  떠 있는 막대
 * │                          │
 * │      노선도 (확대·이동)     │  화면 전체
 * │                    [+][-] │
 * │ ┌──────────────────────┐ │
 * │ │  Time Slider          │ │  떠 있는 카드
 * │ └──────────────────────┘ │
 * └──────────────────────────┘
 * ```
 *
 * 역을 누르면 [StationSheetContent]가 시트로 올라옵니다.
 *
 * ## 데이터
 *
 * `com.hellstation.ui.state` 의 도우미들이 실제 계산 결과를 가져옵니다.
 * 결과가 오기 전에는 임시 데이터가 먼저 그려져서 지도가 한 순간도 비지 않습니다.
 * 미리보기에서는 창구가 null 이라 계속 임시 데이터로 동작합니다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeatmapScreen(
    onSearchClick: () -> Unit,
    onRouteFrom: (Station) -> Unit,
    onSettingsClick: () -> Unit = {},
    onBrowseClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    now: Instant = rememberNow(),
) {
    val nowSlot = rememberNowSlot(now)

    // 슬라이더 위치는 화면을 돌려도 유지되어야 합니다.
    //
    // 슬라이더 눈금은 05:30~24:30 뿐인데, 새벽 0시~5시 반 사이의 "지금"은 25:00~29:30 이라
    // 눈금 밖입니다. 그대로 두면 **손잡이는 맨 왼쪽(05:30)에 붙어 있는데 글씨는 26:00** 이라고
    // 하는 상태가 됩니다. 눈금 안으로 당겨서 손잡이와 글씨를 맞춥니다.
    val startSlot = remember(nowSlot) {
        val first = TimeSlot.ALL.first().minutesFromMidnight
        val last = TimeSlot.ALL.last().minutesFromMidnight
        TimeSlot(nowSlot.minutesFromMidnight.coerceIn(first, last))
    }
    var selectedSlotMinutes by rememberSaveable(startSlot.minutesFromMidnight) {
        mutableIntStateOf(startSlot.minutesFromMidnight)
    }
    val selectedSlot = TimeSlot(selectedSlotMinutes)

    var selectedStation by remember { mutableStateOf<Station?>(null) }

    // 출퇴근은 매일 같은 방향이라, 설정에 정해 둔 방향을 먼저 보여줍니다.
    val defaultDirection = LocalAppSettings.current.defaultDirection
    var direction by remember(defaultDirection) { mutableStateOf(defaultDirection) }

    // 실제 계산 결과입니다. 아직 안 왔으면 임시 데이터가 먼저 그려집니다.
    val data = rememberHeatmapData(now, selectedSlot)
    val snapshot = data.snapshot
    val mapState = rememberMetroMapState()

    val window = HellTheme.window
    val edge = window.edgePadding.dp
    val shortScreen = window.height.isShort

    // 홈 화면 문구도 HELL 지수를 따라갑니다.
    // 지금 시간대에 **가장 심한 역**을 기준으로 잡습니다 — 평균을 쓰면 문구가 늘 밋밋해집니다.
    val worst = remember(snapshot) {
        snapshot.entries.values
            .filter { it.level.isKnown }
            .maxByOrNull { it.percent ?: 0.0 }
    }
    // 씨앗을 시간대로 잡아서, 슬라이더를 움직이면 문구도 함께 바뀝니다.
    val headline = worst?.let {
        HellCopy.headline(it, ServiceStatus.NORMAL, seed = selectedSlot.displayLabel)
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    Box(modifier = modifier.fillMaxSize()) {
        MetroMapView(
            layout = data.layout,
            snapshot = snapshot,
            selected = selectedStation?.id,
            state = mapState,
            onStationClick = { station -> selectedStation = station },
        )

        TopBar(
            subtitle = headline?.title ?: "역을 누르면 자세히 보여드려요",
            warning = data.warning,
            onSearchClick = onSearchClick,
            onSettingsClick = onSettingsClick,
            modifier = Modifier
                .align(Alignment.TopCenter)
                // 지도 자체는 화면 끝까지 그리고, 위에 떠 있는 것만 비켜 줍니다.
                // 노치가 있는 기기의 가로 모드에서 막대가 잘리지 않게 좌우도 함께 봅니다.
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                    )
                )
                .padding(horizontal = edge, vertical = 10.dp),
        )

        ZoomButtons(
            onZoomIn = { mapState.zoomBy(1.4f) },
            onZoomOut = { mapState.zoomBy(1f / 1.4f) },
            onReset = { mapState.reset() },
            onBrowse = onBrowseClick,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
                )
                .padding(end = edge),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
                    )
                )
                .padding(horizontal = edge, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (shortScreen) 6.dp else 10.dp),
        ) {
            // 가로 모드처럼 세로가 짧으면 범례를 접습니다.
            // 지도가 보일 자리를 슬라이더와 범례가 다 먹으면 안 됩니다.
            if (!shortScreen) CrowdLegend()
            TimeSlider(
                selected = selectedSlot,
                levels = data.dayLevels,
                nowSlot = startSlot,
                onSelect = { selectedSlotMinutes = it.minutesFromMidnight },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    val station = selectedStation
    if (station != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedStation = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            val feed = rememberStationBoard(station, direction, now, selectedSlot)
            val board = feed.board
            if (board == null) {
                // 아직 보여줄 값이 없습니다. 시트를 비워 두면 앱이 멈춘 것처럼 보이므로
                // 역 이름만이라도 먼저 띄우고, 기다리는 중인지 실패인지를 구분해 알려 줍니다.
                SheetPlaceholder(
                    stationName = station.displayName,
                    failure = feed.failure,
                    onRetry = feed.retry,
                )
            } else {
                StationSheetContent(
                    board = board,
                    onDirectionChange = { direction = it },
                    onFindRoute = {
                        selectedStation = null
                        onRouteFrom(station)
                    },
                    onRetry = feed.retry,
                    // 슬라이더가 지금을 벗어났으면 시트도 그 사실을 알아야 합니다.
                    viewingSlot = selectedSlot.takeIf {
                        it.minutesFromMidnight != nowSlot.minutesFromMidnight
                    },
                )
            }
        }
    }
}

// ── 위쪽 막대 ───────────────────────────────────────────────────────────────

@Composable
private fun TopBar(
    subtitle: String,
    warning: String?,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        shadowElevation = 6.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HellFace(
                level = CrowdLevel.BUSY,
                showGlow = false,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "HellStation",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                // 문구가 바뀔 때 툭 바뀌지 않고 부드럽게 넘어갑니다.
                AnimatedContent(
                    targetState = subtitle,
                    transitionSpec = {
                        (fadeIn(tween(HellMotion.STANDARD)) togetherWith
                            fadeOut(tween(HellMotion.QUICK)))
                    },
                    label = "topBarSubtitle",
                ) { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Surface(
                onClick = onSearchClick,
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primary,
            ) {
                Text(
                    text = "경로 검색",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            Spacer(Modifier.width(4.dp))
            FilledTonalIconButton(
                onClick = onSettingsClick,
                modifier = Modifier.semantics { contentDescription = "설정" },
            ) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = null,
                )
            }
        }

        // 인증키나 실측 통계가 없으면 그 사실을 여기서 알립니다.
        // 사용자가 어림값을 실측으로 오해하지 않게 하는 것이 목적입니다.
        if (warning != null) {
            Text(
                text = warning,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        }
    }
}

/**
 * 시트가 올라왔는데 아직 내용이 없을 때.
 *
 * 지하철 안에서는 신호가 자주 끊깁니다. 그때 빈 시트를 보여주면 앱이 멈춘 것처럼 보이므로,
 * 최소한 어느 역을 눌렀는지는 알려 줍니다.
 */
@Composable
private fun SheetPlaceholder(
    stationName: String,
    failure: Loadable.Unavailable?,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stationName,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )

        // 기다리면 되는 상황과 다시 눌러야 하는 상황은 눈에 띄게 달라야 합니다.
        if (failure != null) {
            LoadFailure(reason = failure.reason, onRetry = onRetry)
        } else {
            HellFace(level = CrowdLevel.UNKNOWN, modifier = Modifier.size(72.dp))
            Text(
                text = "열차 정보를 받아오는 중입니다",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

// ── 확대 버튼 ───────────────────────────────────────────────────────────────

@Composable
private fun ZoomButtons(
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onReset: () -> Unit,
    onBrowse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 두 손가락 확대를 모르는 사람도 있고, 한 손으로 들고 볼 때도 필요합니다.
        MapButton(label = "+", onClick = onZoomIn, description = "확대")
        MapButton(label = "−", onClick = onZoomOut, description = "축소")
        MapButton(label = "⌂", onClick = onReset, description = "지도 원래대로")

        // 지도는 Canvas에 그린 그림이라 스크린리더가 역 하나하나를 읽지 못합니다.
        // 이 단추가 **지도를 대신하는 길**입니다 — 목록으로 역을 찾아 그대로 열 수 있습니다.
        MapButton(label = "⌕", onClick = onBrowse, description = "역을 목록에서 찾기")
    }
}

@Composable
private fun MapButton(
    label: String,
    description: String,
    onClick: () -> Unit,
) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier
            .size(42.dp)
            .semantics { contentDescription = description },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}

// ── 범례 ────────────────────────────────────────────────────────────────────

/**
 * 색이 무슨 뜻인지 알려 주는 줄.
 *
 * 색만 칠해 두면 "빨간 게 나쁜 건가?"를 사용자가 추측해야 합니다.
 * 다섯 단계를 한 줄로 늘어놓으면 지도를 보며 바로 대조할 수 있습니다.
 */
@Composable
private fun CrowdLegend(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(
                CrowdLevel.EASY,
                CrowdLevel.BUSY,
                CrowdLevel.BAD,
                CrowdLevel.HELL,
                CrowdLevel.WTF,
                CrowdLevel.UNKNOWN,
            ).forEach { level ->
                CrowdBadge(level, compact = true)
            }
        }
    }
}

// ── 미리보기 ────────────────────────────────────────────────────────────────

@Preview(name = "Heatmap · 라이트", showBackground = true, widthDp = 380, heightDp = 800)
@Composable
private fun HeatmapPreviewLight() {
    HellStationTheme(darkTheme = false) {
        Box(Modifier.background(MaterialTheme.colorScheme.background)) {
            HeatmapScreen(
                onSearchClick = {},
                onRouteFrom = {},
                now = Instant.parse("2026-08-24T23:00:00Z"),
            )
        }
    }
}

@Preview(name = "Heatmap · 다크", showBackground = true, widthDp = 380, heightDp = 800)
@Composable
private fun HeatmapPreviewDark() {
    HellStationTheme(darkTheme = true) {
        Box(Modifier.background(MaterialTheme.colorScheme.background)) {
            HeatmapScreen(
                onSearchClick = {},
                onRouteFrom = {},
                now = Instant.parse("2026-08-24T23:00:00Z"),
            )
        }
    }
}
