package com.hellstation.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.unit.sp
import com.hellstation.domain.model.CrowdLevel
import com.hellstation.domain.model.HeatmapSnapshot
import com.hellstation.domain.model.Station
import com.hellstation.domain.model.StationId
import com.hellstation.ui.component.rememberPulse
import com.hellstation.ui.theme.CrowdPalette
import com.hellstation.ui.theme.HellTheme
import com.hellstation.ui.theme.LineColors
import androidx.compose.ui.graphics.StrokeJoin
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.max
import kotlin.math.min

/**
 * 확대·이동이 되는 노선도.
 *
 * ## 무엇을 그리나
 *
 * 1. 노선 선 — 각 노선 고유색으로. 사용자가 이미 외우고 있는 색이라 바꾸지 않습니다.
 * 2. 역 점 — **혼잡도 색으로**. 이게 이 화면의 핵심입니다.
 * 3. 역 이름 — 충분히 확대했을 때만. 다 그리면 글자가 겹쳐서 아무것도 못 읽습니다.
 *
 * ## 왜 graphicsLayer 로 통째로 확대하지 않나
 *
 * `Modifier.graphicsLayer(scaleX=..., scaleY=...)`로 확대하면 **선 굵기와 글씨까지
 * 같이 커집니다.** 6배로 키우면 노선이 대들보처럼 두꺼워져서 역이 안 보입니다.
 * 그래서 좌표만 확대하고 굵기는 화면 기준으로 따로 계산합니다.
 */
@Composable
fun MetroMapView(
    layout: MetroLayout,
    snapshot: HeatmapSnapshot,
    modifier: Modifier = Modifier,
    selected: StationId? = null,
    state: MetroMapState = rememberMetroMapState(),
    onStationClick: (Station) -> Unit = {},
) {
    val palette = HellTheme.crowd
    val isDark = HellTheme.isDark

    // 지옥 구간은 천천히 숨을 쉽니다. 깜빡이지 않는 이유는 rememberPulse 설명 참고.
    val pulse by rememberPulse()
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val labelStyle = remember(isDark) {
        TextStyle(
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = if (isDark) Color(0xFFF0EAF6) else Color(0xFF1B1030),
        )
    }
    // 테마에서 가져옵니다. 예전에는 크림색이 여기 박혀 있어서, 팔레트를 바꿔도
    // 화면의 대부분을 차지하는 지도만 옛날 색으로 남았습니다.
    val backgroundColor = MaterialTheme.colorScheme.background
    val controlInset = with(density) { CONTROL_INSET.toPx() }

    // 이름이 겹칠 때 무엇을 남길지 정하는 순서입니다. 환승이 많은 역이 먼저 자리를 잡습니다 —
    // 사람들이 길을 찾을 때 기준으로 삼는 역이기 때문입니다.
    // 같은 이름을 두 번 그리지 않습니다. 신길처럼 여러 노선에 걸친 역은 노선마다 따로
    // 들어 있어서, 그냥 두면 이름이 나란히 두 번 찍힙니다.
    val labelOrder = remember(layout) {
        layout.stations
            .sortedByDescending { it.transferLines.size }
            .distinctBy { it.displayName }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .semantics {
                // 역 306개는 Canvas에 그린 그림이라 하나씩 읽어 줄 수 없습니다.
                // 대신 **어디로 가면 역을 고를 수 있는지**를 알려 줍니다.
                contentDescription =
                    "서울 지하철 혼잡도 지도. 두 손가락으로 확대할 수 있습니다. " +
                        "역을 하나씩 살펴보려면 오른쪽의 '역을 목록에서 찾기' 단추를 쓰세요"
            }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    state.applyGesture(centroid, pan, zoom, size.toSize())
                }
            }
            .pointerInput(layout) {
                detectTapGestures { tap ->
                    val normalized = state.screenToNormalized(tap, size.toSize())
                    // 손가락은 뭉툭합니다. 확대할수록 더 정확히 누를 수 있으므로
                    // 허용 반경을 배율에 반비례하게 줍니다.
                    val tolerance = TAP_TOLERANCE_BASE / state.scale
                    layout.nearestTo(normalized, tolerance)?.let(onStationClick)
                }
            },
    ) {
        state.updateContent(size, layout.bounds)

        fun project(normalized: Offset): Offset = state.normalizedToScreen(normalized, size)

        // 1. 노선 선 — 구간마다 혼잡도 색이 얹힙니다
        val lineWidth = with(density) { (2.2f + state.scale * 0.5f).dp.toPx() }
        for (shape in layout.lines) {
            drawLineShape(shape, layout, snapshot, palette, ::project, lineWidth, isDark)
        }

        // 2. 역 점
        //
        // 환승역과 일반역의 크기 차이를 크게 둡니다. 둘이 비슷하면 역이 800개 가까이
        // 되는 축소 상태에서 점이 죽처럼 뭉쳐 아무 구조도 안 보입니다.
        // 실제 노선도도 일반역은 작은 눈금, 환승역은 큰 동그라미로 그립니다.
        val plainRadius = with(density) { (1.5f + state.scale * 0.85f).dp.toPx() }
        val transferRadius = with(density) { (3.4f + state.scale * 1.0f).dp.toPx() }
        val dotRadius = transferRadius
        for (station in layout.stations) {
            val position = layout.positionOf(station.id) ?: continue
            val point = project(position)
            if (!point.isWithin(size, margin = transferRadius * 4)) continue

            val level = snapshot.levelOf(station.id)
            drawStationDot(
                center = point,
                radius = if (station.isTransfer) transferRadius else plainRadius,
                level = level,
                palette = palette,
                isDark = isDark,
                isSelected = station.id == selected,
                strokePx = with(density) { 1.6.dp.toPx() },
                pulse = pulse,
            )
        }

        // 3. 역 이름 — 확대했을 때만
        if (state.scale >= LABEL_MIN_SCALE) {
            // 이미 그린 이름이 차지한 자리. 겹치면 뒤에 오는 이름은 건너뜁니다.
            val taken = ArrayList<Rect>()
            // 오른쪽 확대 단추들이 지도를 덮고 있습니다. 그 밑에 이름을 그리면 잘려 보입니다.
            val labelArea = Rect(0f, 0f, size.width - controlInset, size.height)
            for (station in labelOrder) {
                if (!station.isTransfer && state.scale < LABEL_ALL_SCALE) continue
                val position = layout.positionOf(station.id) ?: continue
                val point = project(position)
                if (!point.isWithin(size, margin = 0f)) continue
                drawStationLabel(textMeasurer, labelStyle, station, point, dotRadius, taken, labelArea)
            }
        }
    }
}

// ── 그리기 ──────────────────────────────────────────────────────────────────

/**
 * 노선 하나. **구간마다 혼잡도가 칠해지고, 45°·90°로만 꺾입니다.**
 *
 * ## 왜 직선으로 잇지 않나
 *
 * 역 위치는 실제 위경도라 두 역을 곧게 이으면 선이 **아무 각도로** 지나갑니다.
 * 노선이 아홉 개 넘게 겹치는 도심에서는 그 선들이 제멋대로 교차해서
 * 어느 선이 어디로 가는지 눈으로 따라갈 수 없습니다.
 *
 * 실제 지하철 노선도가 전부 45°·90°만 쓰는 이유가 이것입니다. 지리적 정확도를
 * 버리는 대신 읽을 수 있게 됩니다. 그래서 역과 역 사이를 **대각선 한 번 +
 * 수평이나 수직 한 번**으로 꺾습니다. 양 끝은 실제 역 자리 그대로입니다.
 *
 * ## 왜 역 점만으로는 부족한가
 *
 * 역 점만 칠하면 "이 역이 붐빈다"는 알 수 있어도 **"이 구간을 타고 가면 어떤가"** 는
 * 모릅니다. 지하철은 역에 서 있는 게 아니라 구간을 타고 가는 것이라, 구간이 칠해져야
 * 지도가 경로를 고르는 데 쓸모가 생깁니다.
 *
 * 구간 혼잡도는 그 구간이 **출발하는 역**의 값을 씁니다 —
 * [com.hellstation.data.di.HellStationFacade.segmentCrowd]와 같은 규칙이라
 * 지도 색과 숫자가 어긋나지 않습니다.
 *
 * ## 노선 색을 지우지 않습니다
 *
 * 혼잡도는 굵은 후광으로, 노선 색은 그 안의 심으로 그립니다.
 * 사용자가 이미 외우고 있는 노선 색을 혼잡도 색으로 덮어 버리면 지도를 못 읽습니다.
 *
 * ## 왜 구간마다 drawLine 을 부르지 않나
 *
 * 지도에는 구간이 800개 가까이 있고, 맥박 애니메이션 때문에 화면이 매 프레임 다시
 * 그려집니다. 구간마다 그리면 프레임마다 수천 번을 호출하게 되므로, **등급이 같은
 * 구간끼리 하나의 경로로 묶어** 등급 수(최대 5)만큼만 그립니다.
 */
private fun DrawScope.drawLineShape(
    shape: MetroLineShape,
    layout: MetroLayout,
    snapshot: HeatmapSnapshot,
    palette: CrowdPalette,
    project: (Offset) -> Offset,
    width: Float,
    isDark: Boolean,
) {
    val ids = shape.stationIds
    if (ids.size < 2) return
    val points = ids.map { layout.positionOf(it)?.let(project) }

    val core = Path()
    val halos = HashMap<CrowdLevel, Path>()
    val segmentCount = if (shape.isLoop) ids.size else ids.size - 1

    for (index in 0 until segmentCount) {
        val from = points[index] ?: continue
        val to = points[(index + 1) % ids.size] ?: continue
        val knee = kneeBetween(from, to)

        core.moveTo(from.x, from.y)
        core.lineTo(knee.x, knee.y)
        core.lineTo(to.x, to.y)

        val level = snapshot.levelOf(ids[index])
        if (!level.isKnown) continue
        halos.getOrPut(level) { Path() }.apply {
            moveTo(from.x, from.y)
            lineTo(knee.x, knee.y)
            lineTo(to.x, to.y)
        }
    }

    for ((level, path) in halos) {
        drawPath(
            path = path,
            color = palette.of(level).vivid.copy(alpha = SEGMENT_HALO_ALPHA),
            style = Stroke(width = width * SEGMENT_HALO_WIDTH, join = StrokeJoin.Round),
        )
    }

    val color = if (isDark) LineColors.onDark(shape.line) else LineColors.of(shape.line)
    drawPath(
        path = core,
        color = color.copy(alpha = 0.9f),
        style = Stroke(width = width * SEGMENT_CORE_WIDTH, join = StrokeJoin.Round),
    )
}

/**
 * 두 역 사이에서 선이 꺾이는 지점.
 *
 * 긴 쪽으로 먼저 45° 대각선을 긋고, 남은 만큼을 수평이나 수직으로 갑니다.
 * 이렇게 하면 어떤 두 점을 이어도 선이 45°와 0°(또는 90°)로만 이루어집니다.
 */
private fun kneeBetween(start: Offset, end: Offset): Offset {
    val dx = end.x - start.x
    val dy = end.y - start.y
    return if (abs(dx) > abs(dy)) {
        Offset(start.x + sign(dx) * abs(dy), end.y)
    } else {
        Offset(end.x, start.y + sign(dy) * abs(dx))
    }
}

/**
 * 역 점 하나.
 *
 * ## 지옥 구간만 숨을 쉬는 이유
 *
 * 색이 다섯 단계뿐이라 지도를 멀리서 보면 HELL 과 WTF 가 붙어 보입니다.
 * 가장 나쁜 두 단계에만 천천히 커졌다 작아지는 후광을 둬서, 색을 자세히 안 봐도
 * "여기가 제일 심하다"가 눈에 들어오게 했습니다.
 *
 * **깜빡임이 아니라 맥박입니다.** 빠른 깜빡임은 광과민성 발작 위험이 있고
 * 지도를 읽는 데도 방해가 됩니다(`HellMotion.PULSE` 참고).
 * 사용자가 시스템에서 애니메이션을 껐다면 [pulse] 가 고정값으로 들어와 멈춥니다.
 */
private fun DrawScope.drawStationDot(
    center: Offset,
    radius: Float,
    level: CrowdLevel,
    palette: CrowdPalette,
    isDark: Boolean,
    isSelected: Boolean,
    strokePx: Float,
    pulse: Float,
) {
    val colors = palette.of(level)

    val baseGlow = when (level) {
        CrowdLevel.HELL -> 2.4f
        CrowdLevel.WTF -> 3.2f
        else -> 0f
    }
    if (baseGlow > 0f) {
        // 0.85배에서 1.15배 사이로만 움직입니다. 더 키우면 옆 역을 덮습니다.
        val breathing = baseGlow * (0.85f + pulse * 0.30f)
        val alpha = 0.16f + pulse * 0.10f
        drawCircle(colors.vivid.copy(alpha = alpha), radius = radius * breathing, center = center)
    }

    if (isSelected) {
        drawCircle(
            color = colors.vivid,
            radius = radius * 2.2f,
            center = center,
            style = Stroke(width = strokePx * 1.6f),
        )
    }

    drawCircle(colors.vivid, radius = radius, center = center)
    drawCircle(
        color = if (isDark) Color(0xFF0E0A18) else Color.White,
        radius = radius,
        center = center,
        style = Stroke(width = strokePx),
    )
}

/**
 * 역 이름 하나.
 *
 * ## 겹치면 그리지 않습니다
 *
 * 도심에는 환승역이 몰려 있어서 이름을 다 그리면 글자가 서로를 덮어 **아무것도 못 읽게**
 * 됩니다. 겹치는 이름을 지우면 몇 개는 안 보이지만, 보이는 것은 읽을 수 있습니다.
 * 확대하면 자리가 생기면서 가려졌던 이름이 하나씩 나타납니다.
 *
 * 어느 것을 남길지는 [labelOrder]가 정합니다 — 환승이 많은 역이 먼저입니다.
 *
 * @param taken 이미 그린 이름들이 차지한 자리. 그린 자리를 여기에 더합니다
 */
private fun DrawScope.drawStationLabel(
    measurer: TextMeasurer,
    style: TextStyle,
    station: Station,
    point: Offset,
    dotRadius: Float,
    taken: MutableList<Rect>,
    area: Rect,
) {
    val measured = measurer.measure(station.displayName, style)
    val width = measured.size.width.toFloat()
    val height = measured.size.height.toFloat()

    // 점 오른쪽에 붙입니다. 그 자리가 좁으면 왼쪽으로 넘깁니다.
    val right = point.x + dotRadius + 4f
    val left = point.x - dotRadius - 4f - width
    val x = if (right + width > area.right) left else right
    val y = point.y - height / 2f

    // 글자끼리 살짝 떨어져 있어야 읽힙니다. 자리를 조금 넉넉히 잡습니다.
    val box = Rect(x - LABEL_GAP, y - LABEL_GAP, x + width + LABEL_GAP, y + height + LABEL_GAP)
    // 잘려 나갈 이름은 아예 그리지 않습니다. 반쪽짜리 이름은 없느니만 못합니다.
    if (box.left < area.left || box.right > area.right) return
    if (taken.any { it.overlaps(box) }) return

    taken += box
    drawText(textLayoutResult = measured, topLeft = Offset(x, y))
}

private fun Offset.isWithin(bounds: Size, margin: Float): Boolean =
    x >= -margin && y >= -margin && x <= bounds.width + margin && y <= bounds.height + margin

// ── 상태 ────────────────────────────────────────────────────────────────────

/**
 * 확대 배율과 이동 위치.
 *
 * 화면 밖으로 지도를 완전히 날려 버리면 돌아올 방법이 없으므로 이동 범위를 묶어 둡니다.
 */
@Stable
class MetroMapState internal constructor(initialScale: Float) {

    var scale by mutableFloatStateOf(initialScale)
        private set

    var offset by mutableStateOf(Offset.Zero)
        private set

    private var viewport: Size = Size.Zero
    private var bounds: MetroBounds = MetroBounds.WHOLE

    internal fun updateContent(size: Size, bounds: MetroBounds) {
        viewport = size
        this.bounds = bounds
    }

    /**
     * 정규화 좌표 1.0이 화면에서 몇 px인가(배율 1 기준).
     *
     * **역이 실제로 차지하는 범위에 맞춥니다.** 정규화 공간 전체(0~1)를 기준으로 잡으면
     * 노선도가 안 쓰는 여백까지 화면을 차지해서, 세로로 긴 폰에서 지도가 절반도 안 되는
     * 크기로 그려집니다. 가로세로 중 더 빡빡한 쪽에 맞춰야 비율이 유지됩니다.
     */
    fun contentSize(size: Size): Float =
        min(size.width / bounds.width, size.height / bounds.height) * FIT_RATIO

    fun normalizedToScreen(normalized: Offset, size: Size): Offset {
        val content = contentSize(size) * scale
        return Offset(
            x = size.width / 2f + offset.x + (normalized.x - bounds.center.x) * content,
            y = size.height / 2f + offset.y + (normalized.y - bounds.center.y) * content,
        )
    }

    fun screenToNormalized(screen: Offset, size: Size): Offset {
        val content = contentSize(size) * scale
        if (content == 0f) return Offset.Zero
        return Offset(
            x = (screen.x - size.width / 2f - offset.x) / content + bounds.center.x,
            y = (screen.y - size.height / 2f - offset.y) / content + bounds.center.y,
        )
    }

    /**
     * 두 손가락 제스처를 반영합니다.
     *
     * 확대할 때 **손가락 사이 중심점이 제자리에 있도록** 이동값을 함께 보정합니다.
     * 이 보정이 없으면 확대할 때마다 화면 중앙으로 끌려가서 원하는 곳을 볼 수 없습니다.
     */
    fun applyGesture(centroid: Offset, pan: Offset, zoom: Float, size: Size) {
        val oldScale = scale
        val newScale = (oldScale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
        val actualZoom = newScale / oldScale

        val center = Offset(size.width / 2f, size.height / 2f)
        val focus = centroid - center - offset
        val zoomCorrection = focus * (1f - actualZoom)

        scale = newScale
        offset = clampOffset(offset + pan + zoomCorrection, size, newScale)
    }

    /** 버튼으로 확대·축소. 화면 가운데를 기준으로 움직입니다. */
    fun zoomBy(factor: Float) {
        val size = viewport
        if (size == Size.Zero) {
            scale = (scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
            return
        }
        applyGesture(Offset(size.width / 2f, size.height / 2f), Offset.Zero, factor, size)
    }

    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }

    /** 특정 역이 화면 가운데 오도록 옮깁니다. 검색 결과로 이동할 때 씁니다. */
    fun centerOn(normalized: Offset, targetScale: Float = 2.6f) {
        val size = viewport
        scale = targetScale.coerceIn(MIN_SCALE, MAX_SCALE)
        if (size == Size.Zero) return
        val content = contentSize(size) * scale
        offset = clampOffset(
            Offset(
                x = -(normalized.x - bounds.center.x) * content,
                y = -(normalized.y - bounds.center.y) * content,
            ),
            size,
            scale,
        )
    }

    private fun clampOffset(candidate: Offset, size: Size, currentScale: Float): Offset {
        val content = contentSize(size) * currentScale
        // 지도의 절반까지는 화면 밖으로 나갈 수 있게 두되, 그 이상은 막습니다.
        // 가로와 세로가 서로 다르므로 각각 실제로 그려지는 길이를 씁니다.
        val limitX = max(0f, bounds.width * content / 2f + size.width * 0.15f)
        val limitY = max(0f, bounds.height * content / 2f + size.height * 0.15f)
        return Offset(
            x = candidate.x.coerceIn(-limitX, limitX),
            y = candidate.y.coerceIn(-limitY, limitY),
        )
    }

    companion object {
        const val MIN_SCALE = 0.8f
        const val MAX_SCALE = 6f
        private const val FIT_RATIO = 0.95f
    }
}

@Composable
fun rememberMetroMapState(initialScale: Float = 1f): MetroMapState =
    remember { MetroMapState(initialScale) }

/**
 * 이 배율을 넘으면 환승역 이름이 보입니다.
 *
 * 기본 배율이 1.0이므로 **처음부터 보입니다.** 이름이 하나도 없는 노선도는 색깔 있는
 * 점 무더기일 뿐이라, 어디가 어디인지 알 수 없어 확대할 마음조차 들지 않습니다.
 */
private const val LABEL_MIN_SCALE = 1f

/** 이 배율을 넘으면 모든 역 이름이 보입니다. */
private const val LABEL_ALL_SCALE = 2.6f

/** 오른쪽 확대 단추 줄이 차지하는 폭. 이 안에는 이름을 그리지 않습니다. */
private val CONTROL_INSET = 58.dp

/** 이름끼리 최소한 이만큼은 떨어져 있어야 합니다(px). */
private const val LABEL_GAP = 3f

/** 손가락 하나 굵기 정도. 정규화 좌표 단위입니다. */
private const val TAP_TOLERANCE_BASE = 0.045f

/** 구간 혼잡도 후광의 굵기와 진하기. 노선 색 심보다 넓어야 색이 보입니다. */
private const val SEGMENT_HALO_WIDTH = 1.45f
private const val SEGMENT_HALO_ALPHA = 0.38f

/** 노선 색 심. 후광 안에서 노선을 알아볼 수 있을 만큼만 남깁니다. */
private const val SEGMENT_CORE_WIDTH = 0.62f
