package com.hellstation.data.local.baseline

import android.content.res.AssetManager
import com.hellstation.data.remote.mapper.StationNameNormalizer
import com.hellstation.domain.model.BaselineKey
import com.hellstation.domain.model.BaselineQuality
import com.hellstation.domain.model.BaselineSample
import com.hellstation.domain.model.DayType
import com.hellstation.domain.model.Direction
import com.hellstation.domain.model.StationId
import com.hellstation.domain.model.TimeSlot
import com.hellstation.domain.repository.BaselineSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.charset.Charset

/**
 * 서울교통공사 지하철혼잡도정보 CSV를 읽어 통계 기준선을 제공합니다.
 *
 * ## 파일을 넣는 방법
 *
 * 이 파일은 저장소에 포함되어 있지 **않습니다**. 직접 받아서 넣어야 합니다.
 *
 * 1. 공공데이터포털 `서울교통공사_지하철혼잡도정보`에서 최신 CSV를 받습니다
 *    (로그인 불필요, 공공누리 3유형: 출처표시 + 변경금지)
 * 2. `app/src/main/assets/[ASSET_NAME]` 로 저장합니다
 *
 * 파일이 없으면 이 소스는 조용히 비어 있는 상태로 동작하고,
 * [ApproximateBaselineSource]가 대신 어림값을 냅니다. **앱은 멈추지 않습니다.**
 *
 * ## 파일 구조 (48열)
 *
 * ```
 * 요일구분, 호선, 역번호, 출발역, 상하구분, 5시30분, 6시00분, ... , 24시30분
 * ```
 *
 * 값은 30분 동안 지나간 열차들의 평균 혼잡도(%)이며, **160명/칸 = 100%** 척도입니다.
 * 자세한 내용은 docs/api-validation.md 4번.
 *
 * ## 덮는 범위
 *
 * **1~8호선만 있습니다.** 9호선·경의중앙선·신분당선·공항철도 등은 이 파일에 없습니다.
 */
class CsvBaselineSource(
    private val assets: AssetManager,
    /** [StationId]로 정규화된 역명을 얻는 함수. 역 코드가 어긋날 때 이름으로 맞추려고 씁니다. */
    private val stationNameOf: suspend (StationId) -> String?,
    private val assetName: String = ASSET_NAME,
) : BaselineSource {

    private val mutex = Mutex()

    @Volatile
    private var index: BaselineIndex? = null

    /**
     * 로딩을 이미 시도했는가.
     *
     * `@Volatile` 인 이유: 지도를 한 장 그릴 때 역 수백 개가 각자 이 소스를 부릅니다.
     * 매번 뮤텍스를 잡으면 그것만으로 수만 번 잠금이 일어나므로,
     * 이미 끝난 경우에는 잠금 없이 바로 빠져나갑니다.
     */
    @Volatile
    private var loadAttempted = false

    override suspend fun sample(key: BaselineKey): BaselineSample? {
        val loaded = ensureLoaded() ?: return null
        val lineNumber = key.station.line.csvLineNumber ?: return null

        // 1순위: 역번호로 찾기
        val byCode = loaded.byCode[
            CodeKey(lineNumber, normalizeCode(key.station.stationCode), key.direction, key.dayType)
        ]
        val percent = byCode?.get(key.slot.minutesFromMidnight)
            ?: run {
                // 2순위: 역명으로 찾기. 두 소스의 역번호 체계가 어긋나는 경우가 있습니다.
                val name = stationNameOf(key.station) ?: return@run null
                loaded.byName[NameKey(lineNumber, name, key.direction, key.dayType)]
                    ?.get(key.slot.minutesFromMidnight)
            }
            ?: return null

        return BaselineSample(percent = percent, quality = BaselineQuality.MEASURED)
    }

    override suspend fun hasMeasuredData(): Boolean = ensureLoaded()?.rowCount?.let { it > 0 } == true

    /** 진단용. 몇 행을 읽었고 몇 행을 버렸는지. */
    suspend fun report(): String {
        val loaded = ensureLoaded() ?: return "CSV 없음 ($assetName)"
        return "CSV ${loaded.rowCount}행 로드, ${loaded.skippedRows}행 건너뜀"
    }

    // ── 로딩 ────────────────────────────────────────────────────────────────

    private suspend fun ensureLoaded(): BaselineIndex? {
        if (loadAttempted) return index
        return mutex.withLock {
            if (loadAttempted) return@withLock index
            index = withContext(Dispatchers.IO) { parse() }
            loadAttempted = true
            index
        }
    }

    private fun parse(): BaselineIndex? {
        val bytes = try {
            assets.open(assetName).use { it.readBytes() }
        } catch (e: IOException) {
            // 파일이 없는 것은 정상입니다. 어림값 소스가 대신합니다.
            return null
        }
        if (bytes.isEmpty()) return null

        val text = decode(bytes) ?: return null
        val lines = text.lineSequence().filter { it.isNotBlank() }.iterator()
        if (!lines.hasNext()) return null

        val header = splitCsvLine(lines.next()).map { it.trim().removePrefix("﻿") }
        val columns = ColumnLayout.of(header) ?: return null

        val byCode = HashMap<CodeKey, MutableMap<Int, Double>>()
        val byName = HashMap<NameKey, MutableMap<Int, Double>>()
        var rowCount = 0
        var skipped = 0

        while (lines.hasNext()) {
            val cells = splitCsvLine(lines.next())
            if (cells.size < columns.firstSlotColumn) {
                skipped++
                continue
            }

            val dayType = DayType.parse(cells.getOrNull(columns.dayType))
            val lineNumber = parseLineNumber(cells.getOrNull(columns.line))
            val rawName = cells.getOrNull(columns.stationName)
            val direction = Direction.parse(cells.getOrNull(columns.direction))
            val code = cells.getOrNull(columns.stationCode)?.let { normalizeCode(it) }

            if (dayType == null || lineNumber == null || direction == null) {
                skipped++
                continue
            }

            val slots = HashMap<Int, Double>(columns.slots.size)
            for ((columnIndex, slot) in columns.slots) {
                val value = cells.getOrNull(columnIndex)?.trim()?.toDoubleOrNull() ?: continue
                slots[slot.minutesFromMidnight] = value
            }
            if (slots.isEmpty()) {
                skipped++
                continue
            }

            if (!code.isNullOrBlank()) {
                byCode[CodeKey(lineNumber, code, direction, dayType)] = slots
            }
            val name = StationNameNormalizer.normalize(rawName)
            if (name.isNotBlank()) {
                byName[NameKey(lineNumber, name, direction, dayType)] = slots
            }
            rowCount++
        }

        if (rowCount == 0) return null
        return BaselineIndex(byCode, byName, rowCount, skipped)
    }

    /**
     * 인코딩을 알아냅니다.
     *
     * 공공데이터 CSV는 UTF-8일 때도 있고 EUC-KR(MS949)일 때도 있습니다.
     * 헤더에 "호선"이 보이는 쪽을 채택합니다 — 잘못 읽으면 글자가 깨져서 안 보입니다.
     */
    private fun decode(bytes: ByteArray): String? {
        for (charsetName in CHARSETS) {
            val charset = runCatching { Charset.forName(charsetName) }.getOrNull() ?: continue
            val text = runCatching { String(bytes, charset) }.getOrNull() ?: continue
            val head = text.take(300)
            if (head.contains("호선") && head.contains("역")) return text
        }
        return null
    }

    /**
     * 노선 번호를 뽑습니다.
     *
     * 실제 파일의 호선 칸은 **"1호선"** 입니다. 문서 예시에는 "1"로 적혀 있어서
     * 그냥 `toIntOrNull()` 을 쓰고 있었는데, 그러면 전부 null 이 되어 **1671행이
     * 통째로 버려지고 통계가 아예 없는 것처럼 보였습니다.**
     * "1호선" · "1" · "01호선" 어느 쪽으로 와도 읽히게 숫자만 뽑습니다.
     */
    private fun parseLineNumber(raw: String?): Int? =
        raw?.let { Regex("""\d+""").find(it)?.value?.toIntOrNull() }

    private fun normalizeCode(raw: String): String = raw.trim().trimStart('0').ifBlank { "0" }

    /**
     * CSV 한 줄을 셀로 나눕니다. 큰따옴표로 감싼 셀 안의 쉼표를 지킵니다.
     */
    private fun splitCsvLine(line: String): List<String> {
        val cells = ArrayList<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"')
                    i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    cells.add(current.toString())
                    current.setLength(0)
                }
                else -> current.append(c)
            }
            i++
        }
        cells.add(current.toString())
        return cells
    }

    // ── 색인 자료구조 ────────────────────────────────────────────────────────

    private data class CodeKey(
        val lineNumber: Int,
        val stationCode: String,
        val direction: Direction,
        val dayType: DayType,
    )

    private data class NameKey(
        val lineNumber: Int,
        val stationName: String,
        val direction: Direction,
        val dayType: DayType,
    )

    private class BaselineIndex(
        val byCode: Map<CodeKey, Map<Int, Double>>,
        val byName: Map<NameKey, Map<Int, Double>>,
        val rowCount: Int,
        val skippedRows: Int,
    )

    /** 헤더에서 각 컬럼이 몇 번째인지 찾아냅니다. 컬럼 순서가 바뀌어도 견딥니다. */
    private class ColumnLayout(
        val dayType: Int,
        val line: Int,
        val stationCode: Int,
        val stationName: Int,
        val direction: Int,
        /** (컬럼 번호, 시간대) 목록 */
        val slots: List<Pair<Int, TimeSlot>>,
    ) {
        val firstSlotColumn: Int = slots.minOfOrNull { it.first } ?: Int.MAX_VALUE

        companion object {
            fun of(header: List<String>): ColumnLayout? {
                fun find(vararg candidates: String): Int =
                    header.indexOfFirst { cell -> candidates.any { cell.contains(it) } }

                val dayType = find("요일구분", "요일")
                val line = find("호선")
                val stationCode = find("역번호")
                val stationName = find("출발역", "역명")
                val direction = find("상하구분", "상하선")

                val slots = header.mapIndexedNotNull { index, cell ->
                    TimeSlot.fromCsvLabel(cell)?.let { index to it }
                }
                if (dayType < 0 || line < 0 || direction < 0 || slots.isEmpty()) return null
                return ColumnLayout(dayType, line, stationCode, stationName, direction, slots)
            }
        }
    }

    companion object {
        /** assets 안의 파일 이름. 여기 없으면 어림값으로 대체됩니다. */
        const val ASSET_NAME = "seoul_metro_congestion.csv"

        private val CHARSETS = listOf("UTF-8", "MS949", "EUC-KR")
    }
}
