package com.hellstation.ui.result

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hellstation.domain.model.CrowdLevel
import com.hellstation.domain.model.ArrivalState
import com.hellstation.domain.model.Direction
import com.hellstation.domain.model.Loadable
import com.hellstation.domain.model.Station
import com.hellstation.domain.model.StationBoard
import com.hellstation.domain.model.TimeSlot
import com.hellstation.domain.model.TrainOption
import com.hellstation.domain.model.Verdict
import com.hellstation.ui.character.HellFace
import com.hellstation.ui.character.showsMascot
import com.hellstation.ui.component.ConfidenceNote
import com.hellstation.ui.component.CrowdBadge
import com.hellstation.ui.component.CrowdGauge
import com.hellstation.ui.component.LoadFailure
import com.hellstation.ui.copy.CopyTone
import com.hellstation.ui.copy.HellCopy
import com.hellstation.ui.state.LocalAppSettings
import com.hellstation.ui.state.rememberNow
import com.hellstation.ui.state.rememberNowSlot
import com.hellstation.ui.state.rememberStationBoard
import com.hellstation.ui.sample.SampleMetro
import com.hellstation.ui.theme.HellStationTheme
import com.hellstation.ui.theme.HellTextStyles
import com.hellstation.ui.theme.HellTheme
import java.time.Instant

/**
 * "지금 탈까 기다릴까" 결과 화면.
 *
 * ## 화면에서 가장 큰 글씨가 결론인 이유
 *
 * 사용자는 열차가 들어오는 몇 초 안에 이 화면을 봅니다.
 * 숫자와 표를 먼저 보여주면 읽는 동안 열차가 떠납니다.
 * **"타세요" 한 단어를 먼저, 근거는 그 아래에** 둡니다.
 *
 * ## 방향을 바꿀 수 있게 둔 이유
 *
 * 출발역·도착역만으로 방향을 추론하는 것은 역 번호 비교라 **틀릴 수 있습니다**
 * (`DecideRouteUseCase.inferDirection`). 특히 2호선과 지선이 있는 노선에서요.
 * 그래서 사용자가 직접 뒤집을 수 있어야 합니다.
 */
@Composable
fun ResultScreen(
    origin: Station,
    destination: Station?,
    onBackToMap: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** 아래쪽 큰 버튼에 쓸 말. 역 하나만 정해진 상태에서는 "여기서 출발하기"가 됩니다 */
    primaryLabel: String = "지도로 돌아가기",
    now: Instant = rememberNow(),
    /** 어느 시간대로 볼지. 비우면 지금 시각을 씁니다 */
    slot: TimeSlot? = null,
) {
    val nowSlot = rememberNowSlot(now)
    val shownSlot = slot ?: nowSlot

    // 목적지가 정해져 있으면 그쪽 방향을 먼저 보여줍니다.
    // 역 번호 비교라 틀릴 수 있어서 사용자가 뒤집을 수 있게 두었습니다.
    val defaultDirection = LocalAppSettings.current.defaultDirection
    var direction by rememberSaveable(origin.id.key, destination?.id?.key, defaultDirection) {
        mutableStateOf(inferDirection(origin, destination) ?: defaultDirection)
    }

    val feed = rememberStationBoard(origin, direction, now, shownSlot)
    val board = feed.board
    if (board == null) {
        ResultPlaceholder(
            origin = origin,
            failure = feed.failure,
            onRetry = feed.retry,
            onBack = onBack,
            modifier = modifier,
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // safeDrawing 은 상태바·제스처바·노치를 한 번에 피합니다.
            // statusBarsPadding + navigationBarsPadding 만 쓰면 가로 모드에서
            // 노치 쪽 글자가 잘립니다.
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("뒤로") }
        }

        RouteHeader(origin = origin, destination = destination)

        VerdictHero(board = board)

        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            DirectionSwitch(
                board = board,
                onSwap = { direction = direction.opposite },
            )

            NowNextRow(board = board, now = now)

            // 열차 카드가 "정보 없음"인 이유가 연결 문제라면 그렇게 말해 줘야 합니다.
            board.arrivalFailure?.let { reason ->
                LoadFailure(reason = reason, onRetry = feed.retry)
            }

            CrowdGauge(
                board.crowd,
                modifier = Modifier.fillMaxWidth(),
                height = 14.dp,
                label = "역 기준 혼잡도",
            )
            ConfidenceNote(board.crowd, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = onBackToMap,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(primaryLabel, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * 아직 결과가 없을 때.
 *
 * 지하철 안에서는 신호가 자주 끊깁니다. 빈 화면 대신 무엇을 기다리는지 알려 주고,
 * **돌아갈 길을 항상 열어 둡니다.**
 */
@Composable
private fun ResultPlaceholder(
    origin: Station,
    failure: Loadable.Unavailable?,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            TextButton(onClick = onBack) { Text("뒤로") }
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            Text(
                text = origin.displayName,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )

            // 기다리면 되는 상황과 다시 눌러야 하는 상황은 눈에 띄게 달라야 합니다.
            if (failure != null) {
                LoadFailure(reason = failure.reason, onRetry = onRetry)
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
}

/**
 * 역 번호로 방향을 추측합니다. 서울 지하철 역 코드는 노선을 따라 차례로 매겨져 있어서
 * 도착역 번호가 크면 하행, 작으면 상행인 경우가 대부분입니다.
 *
 * **틀릴 수 있습니다** — 특히 2호선(순환선)과 지선이 있는 노선에서요.
 * 그래서 화면에 "반대 방향" 버튼을 함께 둡니다.
 */
private fun inferDirection(origin: Station, destination: Station?): Direction? {
    if (destination == null) return null
    if (origin.id.line != destination.id.line) return null
    if (origin.id.line.isLoop) return null

    val from = origin.id.stationCode.filter { it.isDigit() }.toIntOrNull() ?: return null
    val to = destination.id.stationCode.filter { it.isDigit() }.toIntOrNull() ?: return null
    return when {
        to > from -> Direction.DOWN
        to < from -> Direction.UP
        else -> null
    }
}

// ── 경로 머리말 ─────────────────────────────────────────────────────────────

@Composable
private fun RouteHeader(origin: Station, destination: Station?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = origin.displayName,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "→",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = destination?.displayName ?: "목적지 없음",
            style = MaterialTheme.typography.titleLarge,
            color = if (destination == null) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onBackground
            },
        )
    }
}

// ── 결론 ────────────────────────────────────────────────────────────────────

/**
 * 화면의 주인공. 결론 한 단어를 최대한 크게 씁니다.
 *
 * 움직임은 넣지 않았습니다. 급하게 보는 화면에서 애니메이션은 방해가 됩니다.
 */
@Composable
private fun VerdictHero(board: StationBoard) {
    val verdict = board.verdict
    val colors = HellTheme.crowd.of(board.crowd.level)
    val accent = when (verdict.verdict) {
        Verdict.RIDE -> MaterialTheme.colorScheme.primary
        Verdict.WAIT -> colors.vivid
        Verdict.NO_DATA -> MaterialTheme.colorScheme.outline
    }

    val tone = HellCopy.toneFor(board.crowd, board.serviceStatus)
    val quip = HellCopy.verdictQuip(verdict.verdict, tone, seed = board.station.name)
    // 좁은 화면에서 "기다리세요" 64sp 는 두 줄로 넘어갑니다. 한 단계 줄입니다.
    val verdictStyle = if (HellTheme.window.width.isCompact) {
        HellTextStyles.verdict.copy(fontSize = 48.sp, lineHeight = 52.sp)
    } else {
        HellTextStyles.verdict
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.22f), accent.copy(alpha = 0.06f))
                )
            )
            .padding(vertical = 22.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // 캐릭터는 지옥일 때만 나옵니다. 평소에는 결론 글자가 주인공입니다.
        if (board.crowd.level.showsMascot) {
            HellFace(
                // 근거가 약하거나 운행에 문제가 있으면 캐릭터도 놀리지 않습니다.
                level = if (tone == CopyTone.PLAYFUL) board.crowd.level else CrowdLevel.UNKNOWN,
                modifier = Modifier.size(if (HellTheme.window.height.isShort) 72.dp else 96.dp),
            )
        }

        Text(
            text = HellCopy.verdictTag(verdict.verdict),
            style = MaterialTheme.typography.labelLarge,
            color = accent,
        )
        Text(
            text = HellCopy.verdictWord(verdict.verdict),
            style = verdictStyle,
            color = accent,
            textAlign = TextAlign.Center,
        )
        if (quip != null) {
            Text(
                text = quip,
                style = MaterialTheme.typography.titleMedium,
                color = accent.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
            )
        }
        Text(
            // 이유는 domain 이 만든 문구 그대로. 화면이 새로 지어내면 판단과 설명이 갈라집니다.
            text = verdict.reason,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )

        val serviceNote = HellCopy.serviceSubtitle(board.serviceStatus)
        if (serviceNote != null) {
            Text(
                text = serviceNote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ── 방향 ────────────────────────────────────────────────────────────────────

@Composable
private fun DirectionSwitch(board: StationBoard, onSwap: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = board.direction.labelFor(board.station.id.line),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "방향이 다르면 바꿔 주세요",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = onSwap, shape = RoundedCornerShape(12.dp)) {
            Text("반대 방향")
        }
    }
}

// ── 지금 vs 다음 ────────────────────────────────────────────────────────────

@Composable
private fun NowNextRow(board: StationBoard, now: Instant) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        VerdictTrainCard(
            title = "NOW",
            subtitle = "지금 들어오는 열차",
            option = board.current,
            now = now,
            emphasized = board.verdict.verdict == Verdict.RIDE,
            modifier = Modifier.weight(1f),
        )
        VerdictTrainCard(
            title = "NEXT",
            subtitle = "다음 열차",
            option = board.next,
            now = now,
            emphasized = board.verdict.verdict == Verdict.WAIT,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun VerdictTrainCard(
    title: String,
    subtitle: String,
    option: TrainOption?,
    now: Instant,
    emphasized: Boolean,
    modifier: Modifier = Modifier,
) {
    val level = option?.crowd?.level ?: CrowdLevel.UNKNOWN
    val colors = HellTheme.crowd.of(level)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(colors.soft.copy(alpha = if (emphasized) 1f else 0.4f))
            .border(
                width = if (emphasized) 2.dp else 1.dp,
                color = if (emphasized) colors.vivid else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(18.dp),
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSoft,
            )
            if (emphasized) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "◀ 이쪽",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSoft,
                )
            }
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = HellCopy.etaText(
                option?.arrival?.secondsUntilArrival(now),
                option?.arrival?.state ?: ArrivalState.UNKNOWN,
            ),
            style = HellTextStyles.boardNumberSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            CrowdBadge(level, compact = true)
            if (option != null && option.crowd.showsPercent) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "${option.crowd.percent!!.toInt()}%",
                    style = HellTextStyles.boardMono,
                    color = colors.onSoft,
                )
            }
        }
    }
}

// ── 미리보기 ────────────────────────────────────────────────────────────────

@Preview(name = "결과 · 라이트", showBackground = true, widthDp = 380, heightDp = 860)
@Composable
private fun ResultPreviewLight() {
    HellStationTheme(darkTheme = false) {
        ResultScreen(
            origin = SampleMetro.gangnamStation,
            destination = SampleMetro.hongdaeStation,
            onBackToMap = {},
            onBack = {},
            now = Instant.parse("2026-08-24T23:00:00Z"),
        )
    }
}

@Preview(name = "결과 · 다크", showBackground = true, widthDp = 380, heightDp = 860)
@Composable
private fun ResultPreviewDark() {
    HellStationTheme(darkTheme = true) {
        ResultScreen(
            origin = SampleMetro.seoulStation,
            destination = SampleMetro.gangnamStation,
            onBackToMap = {},
            onBack = {},
            now = Instant.parse("2026-08-24T23:00:00Z"),
        )
    }
}
