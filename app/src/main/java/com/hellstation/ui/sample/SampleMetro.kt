package com.hellstation.ui.sample

import androidx.compose.ui.geometry.Offset
import com.hellstation.domain.model.LineId
import com.hellstation.domain.model.Station
import com.hellstation.domain.model.StationId
import com.hellstation.ui.map.MetroLayout
import com.hellstation.ui.map.MetroLineShape
import kotlin.math.abs
import kotlin.math.sign

/**
 * 화면 작업용 **임시 노선도 데이터**.
 *
 * ## 이게 무엇이고 무엇이 아닌가
 *
 * - 역 이름과 역 순서는 **실제**입니다.
 * - 좌표는 **실제 위경도가 아니라 손으로 잡은 도식 배치**입니다. 실제 노선도가 그렇듯,
 *   읽기 쉽게 편 그림입니다.
 * - 역 코드(`StationId.stationCode`)는 **지어낸 값**입니다. 실제 역번호가 아닙니다.
 *
 * ## 실제 데이터로 바꿀 때
 *
 * 3단계에서 데이터·기능 담당이 `SubwayNetworkRepository.mappableStations()`로 실제 역을 받아
 * `MetroLayout.fromGeo(...)`를 부르면 이 파일은 필요 없어집니다.
 * **화면 코드는 [MetroLayout]만 보고 그리므로 고칠 데가 없습니다.**
 */
object SampleMetro {

    /**
     * 주요 환승역과 노선 종점의 자리. 1000 x 1000 도식 공간 기준입니다.
     *
     * 나머지 역은 이 사이를 균등하게 나눠 놓습니다. 그래서 손으로 잡아야 할 좌표가
     * 800개가 아니라 50개 남짓입니다.
     */
    private val anchors: Map<String, Offset> = mapOf(
        // 도심
        "서울" to Offset(420f, 520f),
        "시청" to Offset(420f, 478f),
        "종각" to Offset(462f, 462f),
        "종로3가" to Offset(505f, 455f),
        "동대문" to Offset(556f, 442f),
        "동대문역사문화공원" to Offset(552f, 492f),
        "을지로입구" to Offset(452f, 478f),
        "을지로3가" to Offset(495f, 480f),
        "을지로4가" to Offset(528f, 480f),
        "충무로" to Offset(528f, 512f),
        "신당" to Offset(590f, 470f),
        "동묘앞" to Offset(572f, 432f),
        "약수" to Offset(560f, 522f),
        "청구" to Offset(585f, 502f),
        "삼각지" to Offset(450f, 562f),

        // 동북
        "청량리" to Offset(655f, 392f),
        "왕십리" to Offset(640f, 468f),
        "성수" to Offset(692f, 470f),
        "건대입구" to Offset(722f, 500f),
        "군자" to Offset(700f, 440f),
        "노원" to Offset(700f, 204f),
        "창동" to Offset(640f, 182f),
        "태릉입구" to Offset(702f, 262f),
        "석계" to Offset(672f, 302f),

        // 동남
        "잠실" to Offset(800f, 556f),
        "종합운동장" to Offset(748f, 590f),
        "삼성" to Offset(712f, 592f),
        "선릉" to Offset(672f, 600f),
        "강남" to Offset(620f, 618f),
        "교대" to Offset(586f, 618f),
        "고속터미널" to Offset(556f, 596f),
        "천호" to Offset(818f, 502f),
        "가락시장" to Offset(762f, 624f),

        // 서남
        "사당" to Offset(512f, 660f),
        "총신대입구" to Offset(505f, 640f),
        "이수" to Offset(505f, 640f),
        "동작" to Offset(470f, 632f),
        "노량진" to Offset(392f, 592f),
        "여의도" to Offset(330f, 562f),
        "신도림" to Offset(300f, 570f),
        "대림" to Offset(330f, 618f),
        "가산디지털단지" to Offset(312f, 678f),
        "영등포구청" to Offset(278f, 542f),

        // 서북
        "당산" to Offset(300f, 520f),
        "합정" to Offset(332f, 482f),
        "홍대입구" to Offset(352f, 460f),
        "공덕" to Offset(382f, 512f),
        "디지털미디어시티" to Offset(300f, 430f),
        "연신내" to Offset(330f, 340f),
        "김포공항" to Offset(150f, 486f),

        // 종점
        "도봉산" to Offset(612f, 140f),
        "금천구청" to Offset(292f, 720f),
        "지축" to Offset(296f, 178f),
        "오금" to Offset(762f, 652f),
        "당고개" to Offset(732f, 152f),
        "남태령" to Offset(508f, 700f),
        "방화" to Offset(110f, 470f),
        "상일동" to Offset(868f, 470f),
        "응암" to Offset(296f, 392f),
        "신내" to Offset(798f, 300f),
        "장암" to Offset(662f, 118f),
        "온수" to Offset(228f, 660f),
        "암사" to Offset(846f, 504f),
        "모란" to Offset(816f, 704f),
        "개화" to Offset(110f, 506f),
    )

    /** 노선별 역 순서. 이름과 순서는 실제와 같습니다. */
    private val routes: List<SampleRoute> = listOf(
        SampleRoute(
            LineId.LINE_1,
            listOf(
                "도봉산", "도봉", "방학", "창동", "녹천", "월계", "광운대", "석계",
                "신이문", "외대앞", "회기", "청량리", "제기동", "신설동", "동묘앞",
                "동대문", "종로5가", "종로3가", "종각", "시청", "서울", "남영", "용산",
                "노량진", "대방", "신길", "영등포", "신도림", "구로",
                "가산디지털단지", "독산", "금천구청",
            ),
        ),
        SampleRoute(
            LineId.LINE_2,
            listOf(
                "시청", "을지로입구", "을지로3가", "을지로4가", "동대문역사문화공원",
                "신당", "상왕십리", "왕십리", "한양대", "뚝섬", "성수", "건대입구",
                "구의", "강변", "잠실나루", "잠실", "잠실새내", "종합운동장", "삼성",
                "선릉", "역삼", "강남", "교대", "서초", "방배", "사당", "낙성대",
                "서울대입구", "봉천", "신림", "신대방", "구로디지털단지", "대림",
                "신도림", "문래", "영등포구청", "당산", "합정", "홍대입구", "신촌",
                "이대", "아현", "충정로",
            ),
            isLoop = true,
        ),
        SampleRoute(
            LineId.LINE_3,
            listOf(
                "지축", "구파발", "연신내", "불광", "녹번", "홍제", "무악재", "독립문",
                "경복궁", "안국", "종로3가", "을지로3가", "충무로", "동대입구", "약수",
                "금호", "옥수", "압구정", "신사", "잠원", "고속터미널", "교대",
                "남부터미널", "양재", "매봉", "도곡", "대치", "학여울", "대청", "일원",
                "수서", "가락시장", "경찰병원", "오금",
            ),
        ),
        SampleRoute(
            LineId.LINE_4,
            listOf(
                "당고개", "상계", "노원", "창동", "쌍문", "수유", "미아", "미아사거리",
                "길음", "성신여대입구", "한성대입구", "혜화", "동대문",
                "동대문역사문화공원", "충무로", "명동", "회현", "서울", "숙대입구",
                "삼각지", "신용산", "이촌", "동작", "총신대입구", "사당", "남태령",
            ),
        ),
        SampleRoute(
            LineId.LINE_5,
            listOf(
                "방화", "개화산", "김포공항", "송정", "마곡", "발산", "우장산", "화곡",
                "까치산", "신정", "목동", "오목교", "양평", "영등포구청", "영등포시장",
                "신길", "여의도", "여의나루", "마포", "공덕", "애오개", "충정로",
                "서대문", "광화문", "종로3가", "을지로4가", "동대문역사문화공원",
                "청구", "신금호", "행당", "왕십리", "마장", "답십리", "장한평", "군자",
                "아차산", "광나루", "천호", "강동", "길동", "굽은다리", "명일", "고덕",
                "상일동",
            ),
        ),
        SampleRoute(
            LineId.LINE_6,
            listOf(
                "응암", "역촌", "불광", "독바위", "연신내", "구산", "새절", "증산",
                "디지털미디어시티", "월드컵경기장", "마포구청", "망원", "합정", "상수",
                "광흥창", "대흥", "공덕", "효창공원앞", "삼각지", "녹사평", "이태원",
                "한강진", "버티고개", "약수", "청구", "신당", "동묘앞", "창신", "보문",
                "안암", "고려대", "월곡", "상월곡", "돌곶이", "석계", "태릉입구",
                "화랑대", "봉화산", "신내",
            ),
        ),
        SampleRoute(
            LineId.LINE_7,
            listOf(
                "장암", "도봉산", "수락산", "마들", "노원", "중계", "하계", "공릉",
                "태릉입구", "먹골", "중화", "상봉", "면목", "사가정", "용마산", "중곡",
                "군자", "어린이대공원", "건대입구", "뚝섬유원지", "청담", "강남구청",
                "학동", "논현", "반포", "고속터미널", "내방", "이수", "남성",
                "숭실대입구", "상도", "장승배기", "신대방삼거리", "보라매", "신풍",
                "대림", "남구로", "가산디지털단지", "철산", "광명사거리", "천왕", "온수",
            ),
        ),
        SampleRoute(
            LineId.LINE_8,
            listOf(
                "암사", "천호", "강동구청", "몽촌토성", "잠실", "석촌", "송파",
                "가락시장", "문정", "장지", "복정", "산성", "남한산성입구",
                "단대오거리", "신흥", "수진", "모란",
            ),
        ),
        SampleRoute(
            LineId.LINE_9,
            listOf(
                "개화", "김포공항", "공항시장", "신방화", "마곡나루", "양천향교",
                "가양", "증미", "등촌", "염창", "신목동", "선유도", "당산",
                "국회의사당", "여의도", "샛강", "노량진", "노들", "흑석", "동작",
                "구반포", "신반포", "고속터미널", "신논현", "언주", "선정릉",
                "삼성중앙", "봉은사", "종합운동장",
            ),
        ),
    )

    /** 손으로 만든 도식 노선도. 화면에서는 이것만 쓰면 됩니다. */
    val layout: MetroLayout by lazy { build() }

    /** 예제 역 몇 개 — 미리보기와 검색 기본 목록에 씁니다. */
    val seoulStation: Station get() = requireStation(LineId.LINE_1, "서울")
    val gangnamStation: Station get() = requireStation(LineId.LINE_2, "강남")
    val hongdaeStation: Station get() = requireStation(LineId.LINE_2, "홍대입구")

    private fun requireStation(line: LineId, name: String): Station =
        layout.stations.first { it.id.line == line && it.name == name }

    // ── 만들기 ──────────────────────────────────────────────────────────────

    private data class SampleRoute(
        val line: LineId,
        val stationNames: List<String>,
        val isLoop: Boolean = false,
    )

    private fun build(): MetroLayout {
        val stations = ArrayList<Station>()
        val positions = HashMap<StationId, Offset>()
        val shapes = ArrayList<MetroLineShape>()

        // 같은 이름이 여러 노선에 있으면 환승역입니다.
        val linesByName = HashMap<String, MutableSet<LineId>>()
        for (route in routes) {
            for (name in route.stationNames) {
                linesByName.getOrPut(name) { linkedSetOf() } += route.line
            }
        }

        for (route in routes) {
            val ids = route.stationNames.indices.map { index ->
                StationId(route.line, syntheticCode(route.line, index))
            }
            val points = placeAlongAnchors(route)

            route.stationNames.forEachIndexed { index, name ->
                val id = ids[index]
                stations += Station(
                    id = id,
                    name = name,
                    displayName = displayNameOf(name),
                    frCode = null,
                    location = null, // 도식 배치라 실제 좌표가 없습니다
                    transferLines = linesByName[name].orEmpty().filter { it != route.line },
                )
                positions[id] = points[index] / 1000f
            }

            shapes += MetroLineShape(route.line, ids, route.isLoop)
        }

        return MetroLayout(stations, positions, shapes)
    }

    /**
     * 지어낸 역 코드. **실제 역번호가 아닙니다.**
     * 3단계에서 실제 데이터로 갈아끼우면 사라집니다.
     */
    private fun syntheticCode(line: LineId, index: Int): String =
        "S${line.csvLineNumber ?: line.ordinal}${index.toString().padStart(3, '0')}"

    private fun displayNameOf(name: String): String = when (name) {
        "서울" -> "서울역"
        "총신대입구" -> "총신대입구(이수)"
        else -> name
    }

    /**
     * 앵커가 있는 역은 그 자리에 놓고, 사이의 역들은 균등하게 나눠 놓습니다.
     *
     * 각 노선의 첫 역과 끝 역은 반드시 앵커여야 합니다 — 그래야 바깥으로 뻗는
     * 구간을 추정할 필요가 없습니다.
     */
    /**
     * 앵커를 이어 역을 놓습니다.
     *
     * ## 왜 직선이 아니라 꺾은선인가
     *
     * 실제 지하철 노선도는 전부 **0°·45°·90°만 씁니다.** 지리적 정확도를 버리는 대신
     * 선이 여러 개 겹쳐도 눈으로 따라갈 수 있게 되기 때문입니다. 앵커 사이를 임의 각도의
     * 직선으로 이으면, 노선 아홉 개가 만나는 도심에서 어느 선이 어디로 가는지 분간이
     * 안 됩니다. 실제로 그렇게 보였습니다.
     *
     * 그래서 앵커와 앵커 사이를 **대각선 한 번 + 수평이나 수직 한 번**으로 꺾습니다.
     * 꺾이는 지점을 "무릎"이라 부릅니다.
     */
    private fun placeAlongAnchors(route: SampleRoute): List<Offset> {
        val names = route.stationNames
        val result = arrayOfNulls<Offset>(names.size)

        val anchorIndices = names.indices.filter { anchors.containsKey(names[it]) }
        if (anchorIndices.isEmpty()) {
            // 앵커가 하나도 없는 노선을 추가했을 때 앱이 죽지 않도록 가로로 늘어놓습니다.
            return names.indices.map { i ->
                Offset(100f + 800f * i / maxOf(1, names.size - 1), 500f)
            }
        }

        anchorIndices.forEach { index -> result[index] = anchors.getValue(names[index]) }

        for (i in 0 until anchorIndices.size - 1) {
            routeBetween(result, anchorIndices[i], anchorIndices[i + 1])
        }

        val first = anchorIndices.first()
        val last = anchorIndices.last()

        if (route.isLoop) {
            // 순환선은 마지막 앵커에서 첫 앵커로 되돌아오는 구간도 채워야 합니다.
            // 2호선의 신촌·이대·아현·충정로가 여기 해당합니다 — 안 채우면 전부
            // 홍대입구 자리에 겹쳐 버립니다.
            val start = result[last]!!
            val end = result[first]!!
            val knee = kneeOf(start, end)
            val steps = (names.size - last) + first
            for (offset in 1 until steps) {
                val index = (last + offset) % names.size
                if (result[index] == null) {
                    result[index] = pointAlong(start, knee, end, offset.toFloat() / steps)
                }
            }
        } else {
            // 앵커 바깥에 남은 역은 **마지막 구간의 방향으로 이어서 뻗습니다.**
            // 예전에는 전부 끝 앵커 자리에 겹쳐 놓아서, 노선 끝이 굵은 덩어리로 보였습니다.
            extendBeyond(result, anchor = first, neighbor = first + 1, indices = (first - 1) downTo 0)
            extendBeyond(result, anchor = last, neighbor = last - 1, indices = (last + 1) until names.size)
        }

        return result.map { it ?: Offset(500f, 500f) }
    }

    /** 앵커 둘 사이를 꺾은선으로 잇고 그 위에 역을 고르게 놓습니다. */
    private fun routeBetween(result: Array<Offset?>, startIndex: Int, endIndex: Int) {
        val start = result[startIndex] ?: return
        val end = result[endIndex] ?: return
        val steps = endIndex - startIndex
        val knee = kneeOf(start, end)
        for (offset in 1 until steps) {
            result[startIndex + offset] = pointAlong(start, knee, end, offset.toFloat() / steps)
        }
    }

    /**
     * 꺾이는 지점.
     *
     * 긴 쪽으로 먼저 대각선을 그은 다음, 남은 만큼을 수평이나 수직으로 갑니다.
     * 이렇게 하면 두 점을 잇는 선이 항상 45°와 0°(또는 90°)로만 이루어집니다.
     */
    private fun kneeOf(start: Offset, end: Offset): Offset {
        val dx = end.x - start.x
        val dy = end.y - start.y
        return if (abs(dx) > abs(dy)) {
            Offset(start.x + sign(dx) * abs(dy), end.y)
        } else {
            Offset(end.x, start.y + sign(dy) * abs(dx))
        }
    }

    /** 꺾은선 위에서 [t](0~1) 만큼 간 지점. 길이 비례로 나눕니다. */
    private fun pointAlong(start: Offset, knee: Offset, end: Offset, t: Float): Offset {
        val first = (knee - start).getDistance()
        val second = (end - knee).getDistance()
        val total = first + second
        if (total <= 0f) return start

        val walked = t * total
        return if (walked <= first) {
            lerp(start, knee, if (first == 0f) 0f else walked / first)
        } else {
            lerp(knee, end, if (second == 0f) 0f else (walked - first) / second)
        }
    }

    /**
     * 앵커 바깥으로 노선을 이어 뻗습니다.
     *
     * @param anchor   기준이 되는 끝 앵커의 순번
     * @param neighbor 그 안쪽으로 한 칸 붙어 있는 역의 순번. 방향과 간격을 여기서 얻습니다
     * @param indices  채워야 할 순번들. 앵커에서 먼 쪽으로 순서대로 옵니다
     */
    private fun extendBeyond(
        result: Array<Offset?>,
        anchor: Int,
        neighbor: Int,
        indices: Iterable<Int>,
    ) {
        val origin = result.getOrNull(anchor) ?: return
        val inside = result.getOrNull(neighbor)
        val gap = inside?.let { (origin - it) } ?: Offset(0f, -TAIL_STEP)
        val length = gap.getDistance()
        // 안쪽 이웃이 겹쳐 있으면 방향을 알 수 없습니다. 위로 뻗어 둡니다.
        val step = if (length <= 0.01f) Offset(0f, -TAIL_STEP) else gap / length * TAIL_STEP

        var current = origin
        for (index in indices) {
            current += step
            result[index] = current
        }
    }

    /** 종점 바깥으로 뻗을 때의 역 간격. 도식 공간(1000 x 1000) 기준입니다. */
    private const val TAIL_STEP = 16f

    private fun lerp(start: Offset, end: Offset, t: Float): Offset =
        Offset(
            x = start.x + (end.x - start.x) * t,
            y = start.y + (end.y - start.y) * t,
        )
}
