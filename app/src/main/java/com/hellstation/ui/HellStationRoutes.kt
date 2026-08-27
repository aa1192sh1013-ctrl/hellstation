package com.hellstation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hellstation.domain.model.CrowdLevel
import com.hellstation.domain.model.LineId
import com.hellstation.domain.model.StationId
import com.hellstation.ui.character.HellFace
import com.hellstation.ui.heatmap.HeatmapScreen
import com.hellstation.ui.result.ResultScreen
import com.hellstation.ui.search.SearchIntent
import com.hellstation.ui.search.SearchScreen
import com.hellstation.ui.splash.SplashScreen
import com.hellstation.ui.station.StationDetailScreen
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.hellstation.domain.model.DataStatus
import com.hellstation.ui.settings.SettingsScreen
import com.hellstation.ui.state.LocalAppSettings
import com.hellstation.ui.state.LocalAppSettingsActions
import com.hellstation.ui.state.rememberFacade
import com.hellstation.ui.state.rememberStation

/**
 * 화면 이동 계층이 부를 진입점들.
 *
 * ## 왜 인자가 전부 String 인가
 *
 * `navigation` 이 `domain` 을 모르는 구조를 유지하기 위해서입니다(docs/data-model.md 12절).
 * 여기서 [StationId] 로 되돌린 다음, 실제 역은 데이터 계층에서 찾습니다.
 *
 * ## 역을 못 찾으면
 *
 * 앱을 죽이지 않고 안내 화면을 보여줍니다. 인증키가 바뀌어 역 코드 체계가 달라지거나
 * 오래된 딥링크로 들어오는 경우가 실제로 생깁니다.
 */

@Composable
fun SplashRoute(
    onReady: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SplashScreen(onReady = onReady, modifier = modifier)
}

@Composable
fun HeatmapRoute(
    onStationClick: (lineCode: String, stationCode: String) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit = {},
    onBrowseClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    HeatmapScreen(
        onSearchClick = onSearchClick,
        onRouteFrom = { station ->
            onStationClick(station.id.line.apiCode, station.id.stationCode)
        },
        onSettingsClick = onSettingsClick,
        onBrowseClick = onBrowseClick,
        modifier = modifier,
    )
}

/**
 * 설정 화면 자리.
 *
 * 데이터 품질([com.hellstation.domain.model.DataStatus])은 창구에서 읽어 옵니다.
 * 미리보기에서는 창구가 없으므로 기본값이 나옵니다.
 */
@Composable
fun SettingsRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val facade = rememberFacade()
    val settings = LocalAppSettings.current
    val actions = LocalAppSettingsActions.current

    val status by produceState(DataStatus.UNKNOWN, facade) {
        value = facade?.status() ?: DataStatus.UNKNOWN
    }

    SettingsScreen(
        settings = settings,
        status = status,
        onThemeChange = actions.setTheme,
        onDirectionChange = actions.setDefaultDirection,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
fun SearchRoute(
    purposeName: String,
    onStationSelected: (lineCode: String, stationCode: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SearchScreen(
        intent = intentOf(purposeName),
        onStationSelected = { station ->
            onStationSelected(station.id.line.apiCode, station.id.stationCode)
        },
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * 역 하나만 정해진 상태.
 *
 * 결과 화면과 같은 내용을 씁니다 — "출발역은 정했고 목적지는 아직"인 상태와 같기 때문입니다.
 * 다만 주 버튼은 "여기서 출발하기"로 바꿔서, 목적지를 이어서 고를 수 있게 합니다.
 */
@Composable
fun StationDetailRoute(
    lineCode: String,
    stationCode: String,
    onFindRouteClick: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val station = rememberStation(stationIdOf(lineCode, stationCode))
    if (station == null) {
        MissingStation(onBack = onBack, modifier = modifier)
        return
    }
    // 역 하나를 보는 것은 지도에서 역을 눌렀을 때와 같은 일입니다.
    // 결과 화면("타세요/기다리세요")은 도착역이 정해진 뒤에 나와야 합니다.
    StationDetailScreen(
        station = station,
        onFindRoute = onFindRouteClick,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
fun ResultRoute(
    originLineCode: String,
    originStationCode: String,
    destinationLineCode: String,
    destinationStationCode: String,
    onBackToMap: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val origin = rememberStation(stationIdOf(originLineCode, originStationCode))
    val destination = rememberStation(stationIdOf(destinationLineCode, destinationStationCode))

    if (origin == null) {
        MissingStation(onBack = onBack, modifier = modifier)
        return
    }

    ResultScreen(
        origin = origin,
        // 출발역과 같은 역이면 목적지를 안 고른 것으로 봅니다.
        destination = destination?.takeIf { it.id != origin.id },
        onBackToMap = onBackToMap,
        onBack = onBack,
        modifier = modifier,
    )
}

// ── 도우미 ──────────────────────────────────────────────────────────────────

private fun intentOf(purposeName: String): SearchIntent = when (purposeName) {
    "ORIGIN" -> SearchIntent.ORIGIN
    "DESTINATION" -> SearchIntent.DESTINATION
    else -> SearchIntent.BROWSE
}

/** 경로 인자를 역 식별자로. 모르는 노선 코드면 null. */
private fun stationIdOf(lineCode: String, stationCode: String): StationId? {
    if (lineCode.isBlank() || stationCode.isBlank()) return null
    val line = LineId.fromApiCode(lineCode) ?: return null
    return StationId(line, stationCode)
}

@Composable
private fun MissingStation(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            HellFace(level = CrowdLevel.UNKNOWN, modifier = Modifier.size(96.dp))
            Text(
                text = "역 정보를 찾지 못했습니다",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            // 막다른 화면을 만들지 않습니다. 돌아갈 길은 항상 열어 둡니다.
            TextButton(onClick = onBack) { Text("돌아가기") }
        }
    }
}
