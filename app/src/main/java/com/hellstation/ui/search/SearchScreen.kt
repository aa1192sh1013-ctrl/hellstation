package com.hellstation.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hellstation.domain.model.CrowdLevel
import com.hellstation.domain.model.Station
import com.hellstation.domain.model.TimeSlot
import com.hellstation.ui.character.HellFace
import com.hellstation.ui.component.CrowdBadge
import com.hellstation.ui.component.LineDot
import com.hellstation.ui.copy.HellCopy
import com.hellstation.ui.state.rememberNowSlot
import com.hellstation.ui.state.rememberSearchResults
import com.hellstation.ui.theme.HellStationTheme
import com.hellstation.ui.theme.HellTheme
import com.hellstation.ui.theme.LineColors
import java.time.Instant

/** 검색 화면을 무엇 때문에 열었는가. navigation의 `SearchPurpose`와 짝을 이룹니다. */
enum class SearchIntent {
    BROWSE,
    ORIGIN,
    DESTINATION,
    ;

    val title: String
        get() = when (this) {
            BROWSE -> "역 찾기"
            ORIGIN -> "어디서 타세요?"
            DESTINATION -> "어디까지 가세요?"
        }

    val hint: String
        get() = when (this) {
            BROWSE -> "역 이름을 입력하세요"
            ORIGIN -> "출발역"
            DESTINATION -> "도착역"
        }
}

/**
 * 역 검색.
 *
 * ## 결과 줄에 혼잡도를 함께 보여주는 이유
 *
 * 역만 나열하면 사용자는 하나씩 눌러 보며 확인해야 합니다.
 * 목록에서 이미 등급이 보이면 **누르기 전에** 어디가 나은지 알 수 있습니다.
 *
 * 검색어가 비어 있을 때는 주요 환승역을 먼저 보여줍니다.
 * 빈 화면을 내밀면 뭘 쳐야 할지 모르는 사람이 막힙니다.
 */
@Composable
fun SearchScreen(
    intent: SearchIntent,
    onStationSelected: (Station) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    now: Instant = Instant.now(),
    /** 어느 시간대의 혼잡도를 보여줄지. 비우면 지금 시각을 씁니다 */
    slot: TimeSlot? = null,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val nowSlot = rememberNowSlot(now)
    val shownSlot = slot ?: nowSlot

    // 실제 계산 결과. 미리보기에서는 임시 데이터로 되돌아갑니다.
    val results = rememberSearchResults(query, now, shownSlot)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .imePadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("뒤로") }
            Text(
                text = intent.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            placeholder = { Text(intent.hint) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )

        if (query.isBlank()) {
            Text(
                text = if (results.any { it.isFavorite }) "즐겨찾는 역 · 자주 찾는 역" else "자주 찾는 역",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 4.dp),
            )
        }

        if (results.isEmpty()) {
            EmptyResult(query = query)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 12.dp, end = 12.dp, top = 6.dp, bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(results, key = { it.station.id.key }) { row ->
                    StationResultRow(
                        station = row.station,
                        level = row.level,
                        isFavorite = row.isFavorite,
                        onClick = { onStationSelected(row.station) },
                    )
                }
            }
        }
    }
}

@Composable
private fun StationResultRow(
    station: Station,
    level: CrowdLevel,
    isFavorite: Boolean,
    onClick: () -> Unit,
) {
    val isDark = HellTheme.isDark
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LineDot(
            color = if (isDark) LineColors.onDark(station.id.line) else LineColors.of(station.id.line),
            label = HellCopy.lineShort(station.id.line),
        )
        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isFavorite) {
                    Text(
                        text = "★",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                }
                Text(
                    text = station.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = buildString {
                    append(station.id.line.displayName)
                    if (station.isTransfer) {
                        append(" · 환승 ")
                        append(station.transferLines.joinToString(", ") { it.displayName })
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        CrowdBadge(level, compact = true)
    }
}

@Composable
private fun EmptyResult(query: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HellFace(level = CrowdLevel.UNKNOWN, modifier = Modifier.size(96.dp))
            Text(
                // 결과가 없을 때는 농담하지 않습니다. 사용자는 지금 막힌 상태입니다.
                text = "\"$query\"에 맞는 역이 없습니다",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "역 이름 일부만 입력해 보세요",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── 미리보기 ────────────────────────────────────────────────────────────────

@Preview(name = "검색 · 라이트", showBackground = true, widthDp = 380, heightDp = 800)
@Composable
private fun SearchPreviewLight() {
    HellStationTheme(darkTheme = false) {
        SearchScreen(
            intent = SearchIntent.ORIGIN,
            onStationSelected = {},
            onBack = {},
            now = Instant.parse("2026-08-24T23:00:00Z"),
        )
    }
}

@Preview(name = "검색 · 다크", showBackground = true, widthDp = 380, heightDp = 800)
@Composable
private fun SearchPreviewDark() {
    HellStationTheme(darkTheme = true) {
        SearchScreen(
            intent = SearchIntent.DESTINATION,
            onStationSelected = {},
            onBack = {},
            now = Instant.parse("2026-08-24T23:00:00Z"),
        )
    }
}
