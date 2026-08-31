package com.hellstation.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hellstation.domain.model.AppSettings
import com.hellstation.domain.model.DataStatus
import com.hellstation.domain.model.Direction
import com.hellstation.domain.model.LineId
import com.hellstation.domain.model.ThemeChoice
import com.hellstation.ui.theme.HellStationTheme

/**
 * 설정 화면.
 *
 * ## 왜 출처 표기가 여기 있어야 하는가
 *
 * 서울교통공사 혼잡도 통계는 **공공누리 제3유형(출처표시 + 변경금지)** 입니다.
 * 이 데이터를 앱에 넣어 쓰려면 출처 표시가 **의무**입니다. 지킬 곳이 없으면 지킬 수 없으므로,
 * 설정 화면이 없다는 것은 곧 라이선스 의무를 어기고 있다는 뜻이었습니다.
 *
 * ## 왜 "지금 보는 값" 칸이 있는가
 *
 * 인증키와 실측 통계가 없으면 화면에 % 숫자가 안 나옵니다. 일부러 그렇게 만든 규칙인데,
 * 사용자 눈에는 그냥 **기능이 빠진 것처럼** 보입니다. 왜 안 나오는지 여기서 설명합니다.
 */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    status: DataStatus,
    onThemeChange: (ThemeChoice) -> Unit,
    onDirectionChange: (Direction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("뒤로") }
            Text(
                text = "설정",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            Section(title = "화면") {
                ChoiceRow(
                    options = ThemeChoice.entries,
                    selected = settings.theme,
                    label = { it.label },
                    onSelect = onThemeChange,
                )
            }

            Section(
                title = "기본 방향",
                note = "역을 열었을 때 먼저 보여줄 방향입니다. 2호선처럼 순환하는 노선에서는 내선·외선이 됩니다.",
            ) {
                ChoiceRow(
                    options = Direction.entries,
                    selected = settings.defaultDirection,
                    // 대표로 1호선 기준 이름을 씁니다. 순환선 이름은 그 노선 화면에서 바뀝니다.
                    label = { it.labelFor(LineId.LINE_1) },
                    onSelect = onDirectionChange,
                )
            }

            Section(title = "지금 보는 값") {
                DataStatusCard(status)
            }

            Section(title = "데이터 출처") {
                SourceCard(
                    name = "서울교통공사 지하철혼잡도정보",
                    detail = "요일·시간대·역별 평균 혼잡도(1~8호선)",
                    license = "공공누리 제3유형 — 출처표시 + 변경금지",
                )
                Spacer(Modifier.height(10.dp))
                SourceCard(
                    name = "서울 열린데이터광장",
                    detail = "실시간 지하철 도착정보 · 지하철역 좌표 · 노선별 역 정보",
                    license = "출처표시",
                )
            }

            Section(
                title = "혼잡도 기준",
                note = "100%는 \"꽉 참\"이 아니라 정원입니다. 한 칸에 160명이 타면 100%이고, " +
                    "34%면 앉을 자리가 다 찹니다. 출퇴근 시간에 150%를 넘는 것은 정상입니다.",
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── 조각 ────────────────────────────────────────────────────────────────────

@Composable
private fun Section(
    title: String,
    note: String? = null,
    content: @Composable () -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (note != null) {
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        content()
    }
}

@Composable
private fun <T> ChoiceRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
                shape = RoundedCornerShape(12.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}

@Composable
private fun DataStatusCard(status: DataStatus) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StatusLine("역 ${status.stationCount}개", true)
        StatusLine("실시간 도착정보", !status.usingSampleKey)
        StatusLine("실측 혼잡도 통계", status.hasMeasuredBaseline)

        if (!status.isFullyLive) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "위 항목이 갖춰지지 않으면 어림값이라 % 숫자를 숨깁니다. " +
                    "일부러 그렇게 했습니다 — 어림값에 정확한 숫자를 붙이면 실측으로 오해합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusLine(text: String, ok: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = if (ok) "✓" else "—",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (ok) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SourceCard(name: String, detail: String, license: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = license,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Preview(name = "설정", showBackground = true, heightDp = 900)
@Composable
private fun PreviewSettings() {
    HellStationTheme {
        SettingsScreen(
            settings = AppSettings.DEFAULT,
            status = DataStatus(
                usingSampleKey = true,
                usingSeedStations = true,
                hasMeasuredBaseline = false,
                stationCount = 306,
            ),
            onThemeChange = {},
            onDirectionChange = {},
            onBack = {},
        )
    }
}
