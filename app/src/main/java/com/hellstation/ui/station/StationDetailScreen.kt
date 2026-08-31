package com.hellstation.ui.station

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hellstation.domain.model.CrowdLevel
import com.hellstation.domain.model.Station
import com.hellstation.domain.model.UnavailableReason
import com.hellstation.ui.character.HellFace
import com.hellstation.ui.component.LoadFailure
import com.hellstation.ui.state.LocalAppSettings
import com.hellstation.ui.state.rememberNow
import com.hellstation.ui.state.rememberNowSlot
import com.hellstation.ui.state.rememberStationBoard
import java.time.Instant

/**
 * 역 하나를 **전체 화면으로** 보는 자리.
 *
 * ## 왜 결과 화면을 재사용하지 않나
 *
 * 예전에는 역을 직접 열면 `ResultScreen`이 떴습니다. 그러면 역 이름만 눌렀는데
 * **"타세요 / 기다리세요"가 화면 가득 뜹니다.** 아직 어디로 갈지 말한 적도 없는데요.
 *
 * 결과 화면은 "출발역 → 도착역"이 정해졌을 때의 결론을 내미는 자리입니다.
 * 역 하나를 보는 것은 지도에서 역을 눌렀을 때와 같은 일이므로,
 * **같은 내용([StationSheetContent])을 전체 화면으로** 보여줍니다.
 */
@Composable
fun StationDetailScreen(
    station: Station,
    onFindRoute: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    now: Instant = rememberNow(),
) {
    val slot = rememberNowSlot(now)
    val defaultDirection = LocalAppSettings.current.defaultDirection
    var direction by remember(station.id.key, defaultDirection) {
        mutableStateOf(defaultDirection)
    }

    val feed = rememberStationBoard(station, direction, now, slot)
    val board = feed.board

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("뒤로") }
        }

        if (board == null) {
            DetailPlaceholder(
                station = station,
                failure = feed.failure?.reason,
                onRetry = feed.retry,
            )
        } else {
            StationSheetContent(
                board = board,
                onDirectionChange = { direction = it },
                onFindRoute = onFindRoute,
                onRetry = feed.retry,
                now = now,
            )
        }
    }
}

@Composable
private fun DetailPlaceholder(
    station: Station,
    failure: UnavailableReason?,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = station.displayName,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )

        // 기다리면 되는 상황과 다시 눌러야 하는 상황은 눈에 띄게 달라야 합니다.
        if (failure != null) {
            LoadFailure(reason = failure, onRetry = onRetry)
        } else {
            HellFace(level = CrowdLevel.UNKNOWN, modifier = Modifier.size(96.dp))
            Text(
                text = "열차 정보를 받아오는 중입니다",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}
