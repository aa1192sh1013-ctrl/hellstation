package com.hellstation.ui.station

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hellstation.domain.model.CrowdLevel
import com.hellstation.domain.model.Direction
import com.hellstation.domain.model.ServiceStatus
import com.hellstation.domain.model.StationBoard
import com.hellstation.domain.model.StationId
import com.hellstation.domain.model.TimeSlot
import com.hellstation.domain.model.TrainOption
import com.hellstation.domain.model.TrainType
import com.hellstation.domain.model.Verdict
import com.hellstation.ui.character.HellFace
import com.hellstation.ui.component.ConfidenceChip
import com.hellstation.ui.component.ConfidenceNote
import com.hellstation.ui.component.CrowdBadge
import com.hellstation.ui.component.CrowdGauge
import com.hellstation.ui.component.CrowdPercentText
import com.hellstation.ui.component.HellMotion
import com.hellstation.ui.component.LineDot
import com.hellstation.ui.component.LoadFailure
import com.hellstation.ui.copy.CopyTone
import com.hellstation.ui.copy.HellCopy
import com.hellstation.ui.state.LocalAppSettings
import com.hellstation.ui.state.LocalAppSettingsActions
import com.hellstation.ui.sample.SampleCrowd
import com.hellstation.ui.sample.SampleMetro
import com.hellstation.ui.theme.HellStationTheme
import com.hellstation.ui.theme.HellTextStyles
import com.hellstation.ui.theme.HellTheme
import com.hellstation.ui.theme.LineColors
import java.time.Instant

/**
 * 역을 눌렀을 때 올라오는 시트의 내용.
 *
 * ## 무엇을 먼저 보여줄까
 *
 * 사람은 플랫폼에 서서 몇 초 안에 결정합니다. 그래서 순서를 이렇게 잡았습니다.
 *
 * 1. **얼마나 붐비나** — 캐릭터 표정 + 큰 숫자. 눈을 안 굴려도 보입니다
 * 2. **탈까 말까** — 결론 한 줄
 * 3. **지금 열차 vs 다음 열차** — 왜 그런 결론인지
 * 4. 나머지 열차들
 *
 * 표를 위에 놓고 결론을 밑에 두면 급한 사람은 스크롤하다 열차를 놓칩니다.
 */
@Composable
fun StationSheetContent(
    board: StationBoard,
    onDirectionChange: (Direction) -> Unit,
    onFindRoute: () -> Unit,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
    /**
     * 지금이 아닌 다른 시간대를 보고 있다면 그 시간대. 지금을 보고 있으면 null.
     *
     * 값이 있으면 **열차 정보를 통째로 감춥니다.** 혼잡도는 그 시간대의 예상값인데
     * 열차는 지금 들어오는 열차라, 한 화면에 두면 과거·미래의 혼잡도와 현재의 도착 시각이
     * 섞입니다. 낮 12시에 슬라이더를 08:00으로 옮기고 역을 누르면 "08:00 예상 지옥"과
     * "3분 후 도착"이 나란히 나오던 문제입니다.
     */
    viewingSlot: TimeSlot? = null,
) {
    val now = board.observedAt

    // 말투 판단은 HellCopy 한 곳에서만 합니다. 여기서 조건을 다시 쓰면 화면마다 갈라집니다.
    val copy = HellCopy.headline(board.crowd, board.serviceStatus, seed = board.station.name)
    val playful = copy.tone == CopyTone.PLAYFUL

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        StationHeader(board = board, onDirectionChange = onDirectionChange)

        // 운행에 문제가 있으면 그 사실을 가장 위에 둡니다.
        // 혼잡도보다 "열차가 안 온다"가 먼저 알아야 할 정보입니다.
        //
        // 다만 못 받은 이유를 아는 경우에는 아래 실패 안내가 더 정확히 말해 주므로
        // 여기서 두 번 말하지 않습니다.
        if (board.arrivalFailure == null) {
            ServiceNotice(status = board.serviceStatus)
        }

        CrowdSummary(
            board = board,
            headline = copy.title,
            subtitle = copy.subtitle,
            playful = playful,
        )

        CrowdGauge(
            board.crowd,
            modifier = Modifier.fillMaxWidth(),
            height = 14.dp,
            label = "역 기준 혼잡도",
        )
        ConfidenceNote(board.crowd, modifier = Modifier.fillMaxWidth())

        // 결론("타세요/기다리세요")은 지금 들어오는 열차가 있어야 뜻이 있습니다.
        AnimatedVisibility(
            visible = viewingSlot == null && board.verdict.verdict != Verdict.NO_DATA,
        ) {
            VerdictBanner(board = board)
        }

        val failure = board.arrivalFailure
        when {
            // 다른 시간대를 보는 중에는 열차 이야기를 아예 꺼내지 않습니다.
            viewingSlot != null -> TimeTravelNotice(viewingSlot)

            board.upcoming.isNotEmpty() -> {
                NowVsNext(board = board, now = now)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                UpcomingList(board = board, now = now)
            }

            // 기다리면 되는 상황과 다시 눌러야 하는 상황은 눈에 띄게 달라야 합니다.
            failure != null -> LoadFailure(reason = failure, onRetry = onRetry)

            else -> EmptyTrains(message = HellCopy.emptyTrains(board.serviceStatus))
        }

        Button(
            onClick = onFindRoute,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text("여기서 출발하기", style = MaterialTheme.typography.titleMedium)
        }
    }
}

/**
 * 다른 시간대를 보는 중이라는 안내.
 *
 * 열차 목록이 있던 자리를 대신합니다. 자리를 비워 두면 사용자는 "왜 열차가 안 나오지"
 * 하고 앱을 의심하게 됩니다.
 */
@Composable
private fun TimeTravelNotice(slot: TimeSlot) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 18.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "${slot.displayLabel} 기준 예상 혼잡도입니다",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "열차 도착 정보는 지금 시각에만 보여드립니다",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// ── 머리말 ──────────────────────────────────────────────────────────────────

@Composable
private fun StationHeader(
    board: StationBoard,
    onDirectionChange: (Direction) -> Unit,
) {
    val station = board.station
    val isDark = HellTheme.isDark

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LineDot(
                color = if (isDark) LineColors.onDark(station.id.line) else LineColors.of(station.id.line),
                label = HellCopy.lineShort(station.id.line),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = station.displayName,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            FavoriteToggle(station.id)
        }

        if (station.isTransfer) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "환승",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 2.dp),
                )
                station.transferLines.forEach { line ->
                    LineDot(
                        color = if (isDark) LineColors.onDark(line) else LineColors.of(line),
                        label = HellCopy.lineShort(line),
                        size = 18.dp,
                    )
                }
            }
        }

        // 방향 고르기 — 같은 역이라도 상행과 하행은 혼잡도가 정반대입니다.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Direction.entries.forEach { direction ->
                FilterChip(
                    selected = board.direction == direction,
                    onClick = { onDirectionChange(direction) },
                    label = { Text(direction.labelFor(station.id.line)) },
                    shape = RoundedCornerShape(12.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
        }
    }
}

/**
 * 즐겨찾기 별.
 *
 * 출퇴근에 쓰는 역은 두세 개로 정해져 있습니다. 매번 지도에서 찾게 하는 대신
 * 별을 눌러 두면 검색 화면 맨 위에 올라옵니다.
 */
@Composable
private fun FavoriteToggle(id: StationId) {
    val settings = LocalAppSettings.current
    val actions = LocalAppSettingsActions.current
    val isFavorite = settings.isFavorite(id)

    IconButton(
        onClick = { actions.toggleFavorite(id) },
        modifier = Modifier.semantics {
            contentDescription = if (isFavorite) "즐겨찾기에서 빼기" else "즐겨찾기에 넣기"
        },
    ) {
        Text(
            // 별 하나로 켜짐·꺼짐을 다 보여줍니다. 아이콘을 두 개 들여오지 않아도 됩니다.
            text = if (isFavorite) "★" else "☆",
            style = MaterialTheme.typography.headlineSmall,
            color = if (isFavorite) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

// ── 혼잡도 요약 ─────────────────────────────────────────────────────────────

/**
 * 혼잡도 요약.
 *
 * ## 좁은 화면에서 배치를 바꾸는 이유
 *
 * `캐릭터 84dp + 제목 + 큰 숫자`를 한 줄에 넣으면 360dp 폰에서는 들어가지만
 * **320dp 기기(폴드 접은 상태 등)에서는 제목이 한 글자씩 끊깁니다.**
 * 그래서 좁으면 캐릭터와 숫자를 위로 올리고 글은 아래에 통으로 깔아 줍니다.
 */
@Composable
private fun CrowdSummary(
    board: StationBoard,
    headline: String,
    subtitle: String,
    playful: Boolean,
) {
    val dense = HellTheme.window.allowsDenseRow
    // 운행에 문제가 있거나 값을 못 믿을 때는 캐릭터도 놀리지 않습니다 — 멍한 표정으로.
    val faceLevel = if (playful) board.crowd.level else CrowdLevel.UNKNOWN

    if (dense) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            HellFace(level = faceLevel, modifier = Modifier.size(84.dp))
            SummaryText(
                board = board,
                headline = headline,
                subtitle = subtitle,
                modifier = Modifier.weight(1f),
            )
            CrowdPercentText(board.crowd)
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                HellFace(level = faceLevel, modifier = Modifier.size(72.dp))
                CrowdPercentText(board.crowd)
            }
            SummaryText(board = board, headline = headline, subtitle = subtitle)
        }
    }
}

@Composable
private fun SummaryText(
    board: StationBoard,
    headline: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CrowdBadge(board.crowd.level)
            Spacer(Modifier.width(6.dp))
            ConfidenceChip(board.crowd)
        }
        // 등급이 바뀌면 문구도 바뀝니다. 툭 갈아끼우지 않고 부드럽게 넘깁니다 —
        // 값이 바뀌었다는 것 자체가 사용자에게 필요한 신호입니다.
        AnimatedContent(
            targetState = headline to subtitle,
            transitionSpec = {
                (fadeIn(tween(HellMotion.STANDARD)) togetherWith fadeOut(tween(HellMotion.QUICK)))
            },
            label = "stationHeadline",
        ) { (title, sub) ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 운행 이상 안내.
 *
 * **여기서는 절대 농담하지 않습니다.** 열차가 멈췄는데 "오늘도 지옥철"이라고 하면
 * 조롱처럼 읽힙니다. 정상일 때는 아무것도 그리지 않습니다.
 */
@Composable
private fun ServiceNotice(status: ServiceStatus, modifier: Modifier = Modifier) {
    val title = HellCopy.serviceHeadline(status) ?: return
    val subtitle = HellCopy.serviceSubtitle(status)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "!",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                )
            }
        }
    }
}

// ── 결론 ────────────────────────────────────────────────────────────────────

@Composable
private fun VerdictBanner(board: StationBoard) {
    val verdict = board.verdict
    val colors = HellTheme.crowd.of(board.crowd.level)
    val accent = when (verdict.verdict) {
        Verdict.RIDE -> MaterialTheme.colorScheme.primary
        Verdict.WAIT -> colors.vivid
        Verdict.NO_DATA -> MaterialTheme.colorScheme.outline
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(accent.copy(alpha = 0.12f))
            .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = HellCopy.verdictTag(verdict.verdict),
                style = MaterialTheme.typography.labelSmall,
                color = accent,
            )
            Text(
                text = HellCopy.verdictWord(verdict.verdict),
                style = MaterialTheme.typography.headlineMedium,
                color = accent,
            )
        }
        Text(
            // 이유 문구는 domain 이 만든 것을 그대로 씁니다.
            // 화면이 따로 지어내면 판단과 설명이 어긋납니다.
            text = verdict.reason,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

// ── 지금 열차 vs 다음 열차 ──────────────────────────────────────────────────

@Composable
private fun NowVsNext(board: StationBoard, now: Instant) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "지금 열차 vs 다음 열차",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TrainCompareCard(
                title = "NOW",
                option = board.current,
                now = now,
                modifier = Modifier.weight(1f),
                highlighted = board.verdict.verdict == Verdict.RIDE,
            )
            TrainCompareCard(
                title = "NEXT",
                option = board.next,
                now = now,
                modifier = Modifier.weight(1f),
                highlighted = board.verdict.verdict == Verdict.WAIT,
            )
        }
    }
}

@Composable
private fun TrainCompareCard(
    title: String,
    option: TrainOption?,
    now: Instant,
    highlighted: Boolean,
    modifier: Modifier = Modifier,
) {
    val level = option?.crowd?.level ?: CrowdLevel.UNKNOWN
    val colors = HellTheme.crowd.of(level)
    val borderColor =
        if (highlighted) colors.vivid else MaterialTheme.colorScheme.outlineVariant

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.soft.copy(alpha = if (highlighted) 1f else 0.45f))
            .border(if (highlighted) 2.dp else 1.dp, borderColor, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSoft,
        )
        Text(
            text = HellCopy.etaShort(option?.arrival?.secondsUntilArrival(now)),
            style = HellTextStyles.boardNumberSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        CrowdBadge(level, compact = true)
        if (option != null && option.crowd.showsPercent) {
            Text(
                text = "${option.crowd.percent!!.toInt()}%",
                style = HellTextStyles.boardMono,
                color = colors.onSoft,
            )
        }
        if (option?.arrival?.train?.type == TrainType.EXPRESS) {
            Text(
                text = "급행",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

// ── 다가오는 열차 목록 ──────────────────────────────────────────────────────

@Composable
private fun UpcomingList(board: StationBoard, now: Instant) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "다가오는 열차",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        board.upcoming.forEach { option ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = HellCopy.etaShort(option.arrival.secondsUntilArrival(now)),
                    style = HellTextStyles.boardMono,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(44.dp),
                )
                CrowdBadge(option.crowd.level, compact = true)
                Text(
                    text = option.arrival.train.headsign,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (option.arrival.train.isLastTrain) {
                    Text(
                        text = "막차",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyTrains(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// ── 미리보기 ────────────────────────────────────────────────────────────────

@Preview(name = "역 시트 · 라이트", showBackground = true, widthDp = 380, heightDp = 900)
@Composable
private fun StationSheetPreviewLight() {
    HellStationTheme(darkTheme = false) { StationSheetPreviewBody() }
}

@Preview(name = "역 시트 · 다크", showBackground = true, widthDp = 380, heightDp = 900)
@Composable
private fun StationSheetPreviewDark() {
    HellStationTheme(darkTheme = true) { StationSheetPreviewBody() }
}

@Composable
private fun StationSheetPreviewBody() {
    val at = Instant.parse("2026-08-24T23:00:00Z")
    val slot = com.hellstation.domain.model.TimeSlot(8 * 60)
    val board = SampleCrowd.boardFor(SampleMetro.gangnamStation, Direction.UP, at, slot)
    Box(Modifier.background(MaterialTheme.colorScheme.surface)) {
        StationSheetContent(board = board, onDirectionChange = {}, onFindRoute = {})
    }
}
