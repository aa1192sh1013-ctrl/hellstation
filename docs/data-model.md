# 데이터 모델 (data-model)

이 문서는 HellStation이 서울 지하철을 **어떤 모양의 데이터로 다루는지** 정의합니다.

아래 Kotlin 코드는 **데이터·기능 담당이 `app/src/main/java/com/hellstation/domain/model/`에 그대로 만들어야 할 것**입니다.
설계 담당은 `domain/` 폴더를 수정할 수 없으므로 여기에 명세로 남깁니다. (`.agents/ownership.json`)

---

## 0. 이 모델이 풀어야 하는 문제

`docs/api-validation.md`에서 확인한 대로, 소스마다 **같은 대상을 다른 이름과 다른 형식으로** 부릅니다.

| 대상 | 도착정보 API | 좌표 API | 역정보 API | 혼잡도 CSV | TMAP |
|---|---|---|---|---|---|
| 1호선 | `"1001"` | `"1호선"` | `"01호선"` | `1` | `"1호선"` |
| 서울역 | `"서울"` | `"서울역"` | `"서울"` | `"서울역"` | `"서울역"` |
| 역 ID | `"1001000133"` | `"0150"` | `"0150"` | 역번호 `150` | 없음 |
| 상행 | `"상행"` | — | — | `"상선"` | `"상행"` |

**5개 소스를 하나로 합치는 것이 이 데이터 모델의 존재 이유입니다.**
원칙: **앱 안에서는 정규화된 형태 하나만 돌아다니고, 변환은 `data/` 계층의 경계에서만 일어난다.**

---

## 1. 노선 — `LineId`

노선은 개수가 고정되어 있고 소스별 표기를 모두 알아야 하므로, **enum이 변환표를 직접 들고 있게** 합니다.

```kotlin
package com.hellstation.domain.model

/**
 * 서울 수도권 전철 노선.
 *
 * 소스마다 노선 표기가 다르므로(docs/api-validation.md) 변환에 필요한 모든 표기를
 * 이 enum이 직접 들고 있습니다. 앱 안에서는 항상 이 타입만 씁니다.
 *
 * @param apiCode        도착정보 API의 subwayId. 예: "1001"
 * @param displayName    사용자에게 보여줄 이름. 좌표 API의 ROUTE, TMAP의 routeNm과 같은 형식
 * @param csvLineNumber  서울교통공사 혼잡도 CSV의 '호선' 값. null이면 통계 기준선이 없는 노선
 */
enum class LineId(
    val apiCode: String,
    val displayName: String,
    val csvLineNumber: Int?,
) {
    LINE_1("1001", "1호선", 1),
    LINE_2("1002", "2호선", 2),
    LINE_3("1003", "3호선", 3),
    LINE_4("1004", "4호선", 4),
    LINE_5("1005", "5호선", 5),
    LINE_6("1006", "6호선", 6),
    LINE_7("1007", "7호선", 7),
    LINE_8("1008", "8호선", 8),
    LINE_9("1009", "9호선", null),
    GYEONGUI_JUNGANG("1063", "경의중앙선", null),
    AIRPORT("1065", "공항철도", null),
    GYEONGCHUN("1067", "경춘선", null),
    SUIN_BUNDANG("1071", "수인분당선", null),
    SINBUNDANG("1075", "신분당선", null),
    SILLIM("1077", "신림선", null),
    UI_SINSEOL("1092", "우이신설선", null),
    SEOHAE("1093", "서해선", null),
    GYEONGGANG("1081", "경강선", null),
    ;

    /** 통계 기준선(서울교통공사 CSV)을 쓸 수 있는 노선인가. */
    val hasBaseline: Boolean get() = csvLineNumber != null

    companion object {
        private val byApiCode = entries.associateBy { it.apiCode }

        /** 도착정보 API의 subwayId로 찾기. 모르는 코드면 null. */
        fun fromApiCode(code: String): LineId? = byApiCode[code]

        /**
         * 역정보 API의 LINE_NUM("01호선"), 좌표 API의 ROUTE("1호선") 등
         * 사람이 읽는 표기로 찾기. 앞의 0을 떼고 비교합니다.
         */
        fun fromDisplayName(raw: String): LineId? {
            val normalized = raw.trim().removePrefix("0")
            return entries.firstOrNull { it.displayName == normalized }
        }
    }
}
```

**주의:** `LINE_9`부터 아래는 `csvLineNumber`가 `null`입니다. 즉 **통계 기준선이 없습니다.**
실시간 데이터가 없으면 이 노선의 역은 `CrowdLevel.UNKNOWN`이 됩니다. 정상 동작이며, 버그가 아닙니다.

`fromApiCode`/`fromDisplayName`이 `null`을 반환하는 것도 정상입니다 — 새 노선이 개통하면 여기 없습니다. **예외를 던지지 말고 해당 데이터를 건너뛰세요.**

---

## 2. 역 — `StationId`, `Station`

### 왜 역명만으로는 안 되는가

"서울역"은 1호선·4호선·경의중앙선·공항철도에 각각 있고, 혼잡도와 열차는 노선마다 완전히 다릅니다.
**역의 정체성은 `(노선, 역)`입니다.** 역명 하나는 식별자가 될 수 없습니다.

```kotlin
package com.hellstation.domain.model

/**
 * 역 식별자. 노선 + 역코드 조합입니다.
 *
 * 역코드는 좌표 API의 BLDN_ID / 역정보 API의 STATION_CD 체계를 씁니다(예: "0150").
 * 도착정보 API의 statnId("1001000133")는 체계가 달라 그대로 쓰지 않습니다 — 3절 참고.
 */
data class StationId(
    val line: LineId,
    val stationCode: String,
) {
    /** 로그·캐시 키용 문자열. 예: "1001:0150" */
    val key: String get() = "${line.apiCode}:$stationCode"
}

/**
 * 역 하나. 좌표 API + 역정보 API를 합쳐 만듭니다.
 *
 * @param name        정규화된 역명. 접미사 "역"을 뗀 형태("서울", "시청"). 4절의 정규화 규칙 참고
 * @param displayName 사용자에게 보여줄 이름("서울역")
 * @param frCode      역 안내판에 적힌 번호("P148", "151"). 없을 수 있음
 * @param location    위경도. 좌표 API에 없는 역은 null — 지도에 찍을 수 없습니다
 * @param transferLines 이 역에서 갈아탈 수 있는 다른 노선들. 자기 자신은 제외
 */
data class Station(
    val id: StationId,
    val name: String,
    val displayName: String,
    val frCode: String?,
    val location: LatLng?,
    val transferLines: List<LineId> = emptyList(),
) {
    val isTransfer: Boolean get() = transferLines.isNotEmpty()
}

data class LatLng(
    val latitude: Double,
    val longitude: Double,
)
```

**`location`이 nullable인 이유:** 좌표 API는 784건, 역정보 API는 799건입니다(`docs/api-validation.md` 2·3번). 약 15개 역은 좌표가 없습니다. Heatmap에서는 이런 역을 **그리지 않거나** 인접 역 사이에 보간해서 배치하세요. `(0, 0)`을 넣으면 아프리카 앞바다에 역이 하나 생깁니다.

---

## 3. 도착정보 API의 `statnId`를 어떻게 다루나

도착정보 API는 `statnId`(`"1001000133"`)와 `statnFid`/`statnTid`(이전/다음 역)를 줍니다.
이 체계는 **`StationId`와 다르지만, 노선 그래프를 만드는 유일한 근거**라서 버리면 안 됩니다.

```kotlin
package com.hellstation.domain.model

/**
 * 도착정보 API가 쓰는 역 ID. 앞 4자리가 노선 코드, 뒤 6자리가 역 일련번호입니다.
 * 예: "1001000133" = 1호선(1001) + 000133
 *
 * StationId와는 별개의 체계이므로 섞지 않습니다.
 * 두 체계를 잇는 것은 data 계층의 매핑 테이블(4절) 책임입니다.
 */
@JvmInline
value class RealtimeStationId(val raw: String) {
    val lineCode: String get() = raw.take(4)
    val line: LineId? get() = LineId.fromApiCode(lineCode)

    init {
        require(raw.length == 10) { "realtime statnId는 10자리여야 합니다: $raw" }
    }
}
```

`statnFid`(이전 역) → `statnId`(현재) → `statnTid`(다음 역)를 이어붙이면 **노선의 역 순서**가 나옵니다. 이 순서가 있어야 5절의 `Segment`와 경로 탐색이 가능합니다.

---

## 4. 역명 정규화 규칙

소스를 합칠 때 가장 많이 깨지는 부분입니다. **한 함수에만 두고 모두 그것을 쓰세요.**

```kotlin
/**
 * 소스마다 다른 역명 표기를 하나로 맞춥니다.
 *
 *  "서울역"        -> "서울"
 *  "이대"          -> "이대"
 *  "총신대입구(이수)" -> "총신대입구"
 *  "4·19민주묘지"   -> "4.19민주묘지"
 */
fun normalizeStationName(raw: String): String = raw
    .trim()
    .substringBefore('(')      // 괄호 안 부역명 제거
    .replace('·', '.')          // 가운뎃점 표기 통일
    .removeSuffix("역")
    .trim()
```

### 주의할 예외

- `"역"`으로 끝나지만 떼면 안 되는 역이 있는지 반드시 실제 데이터로 확인하세요. (예: `"신설동"`은 무해하지만, 새 노선 개통 시 위험)
- 부역명이 붙는 역(`총신대입구(이수)`, `숭실대입구(살피재)`)은 **소스마다 붙기도 하고 안 붙기도 합니다.** 괄호 제거는 필수입니다.
- 정규화한 이름은 **매칭 전용**입니다. 화면에는 `Station.displayName`을 쓰세요.

### 매핑 테이블

`RealtimeStationId` ↔ `StationId`를 잇는 표는 **앱 시작 시 한 번 만들어 캐싱**합니다.

```
키: (LineId, normalizeStationName(역명))
값: StationId, RealtimeStationId
```

이 표는 좌표 API(784) + 역정보 API(799)를 합쳐 만들고, 도착정보 응답이 올 때 역명으로 조회합니다.
**매칭에 실패한 역은 조용히 버리지 말고 로그를 남기세요.** 어떤 역이 지도에서 사라졌는지 알 수 있어야 합니다.

---

## 5. 방향 — `Direction`

```kotlin
package com.hellstation.domain.model

/**
 * 진행 방향.
 *
 * 소스별 표기:
 *   도착정보 API updnLine  : "상행" / "하행"  (2호선은 "내선" / "외선")
 *   혼잡도 CSV 상하구분     : "상선" / "하선"  (2호선은 "내선" / "외선")
 *
 * 2호선은 순환선이라 상/하행 개념이 없습니다. UP=내선, DOWN=외선으로 고정합니다.
 */
enum class Direction {
    UP,
    DOWN,
    ;

    /** 해당 노선에서 사용자에게 보여줄 방향 이름. */
    fun labelFor(line: LineId): String = when {
        line == LineId.LINE_2 && this == UP -> "내선"
        line == LineId.LINE_2 && this == DOWN -> "외선"
        this == UP -> "상행"
        else -> "하행"
    }

    companion object {
        /** "상행" "상선" "내선" 등 어떤 표기가 와도 받아냅니다. 모르는 값이면 null. */
        fun parse(raw: String): Direction? = when (raw.trim()) {
            "상행", "상선", "내선" -> UP
            "하행", "하선", "외선" -> DOWN
            else -> null
        }
    }
}
```

**방향을 잃어버리지 마세요.** 같은 역·같은 시각이라도 상행과 하행의 혼잡도는 출퇴근 시간에 정반대입니다. 방향 없는 혼잡도는 의미가 없습니다.

---

## 6. 구간 — `Segment`

구간은 **인접한 두 역 사이의 한 방향 이동**입니다. Heatmap에서 역과 역을 잇는 선 하나가 `Segment` 하나입니다.

```kotlin
package com.hellstation.domain.model

/**
 * 인접한 두 역 사이의 한 방향 구간. 방향이 있는 간선입니다.
 * (A→B)와 (B→A)는 서로 다른 Segment입니다.
 */
data class Segment(
    val line: LineId,
    val from: StationId,
    val to: StationId,
    val direction: Direction,
    /** 이 구간 통과에 걸리는 평균 시간(초). 실측이 없으면 null */
    val travelSeconds: Int? = null,
) {
    val key: String get() = "${from.key}>${to.key}"

    init {
        require(from.line == line && to.line == line) {
            "Segment의 두 역은 같은 노선이어야 합니다: $key"
        }
    }
}
```

**구간 혼잡도는 어떻게 정하나:** 서울교통공사 CSV는 **역 단위** 값만 줍니다. 구간 값은 `from` 역의 해당 방향 혼잡도를 그 구간의 값으로 씁니다(그 역에서 탄 사람들이 그 구간을 타고 가므로). 인접 역에서 추정한 값이라면 신뢰도를 `LOW`로 강등하세요 (`docs/crowding-levels.md` 3절 규칙 7).

---

## 7. 열차 — `Train`, `TrainType`

```kotlin
package com.hellstation.domain.model

enum class TrainType {
    NORMAL,      // 일반
    EXPRESS,     // 급행
    RAPID,       // 특급
    ITX,         // ITX
    UNKNOWN,
    ;

    companion object {
        /** 도착정보 API의 btrainSttus 값을 변환합니다. */
        fun parse(raw: String?): TrainType = when (raw?.trim()) {
            "일반" -> NORMAL
            "급행", "급행A", "급행B" -> EXPRESS
            "특급" -> RAPID
            "ITX" -> ITX
            else -> UNKNOWN
        }
    }
}

/**
 * 열차 한 대.
 *
 * @param trainNo      열차 번호(btrainNo). 같은 열차를 여러 역에서 추적하는 키
 * @param destination  종착역 이름(bstatnNm)
 * @param headsign     "양주행 - 시청방면" (trainLineNm). 화면에 그대로 쓸 수 있는 문구
 * @param isLastTrain  막차 여부(lstcarAt == "1")
 */
data class Train(
    val trainNo: String,
    val line: LineId,
    val direction: Direction,
    val type: TrainType,
    val destination: String,
    val headsign: String,
    val isLastTrain: Boolean,
)
```

**급행을 반드시 구분하세요.** 급행은 정차역이 적어 일반 열차와 혼잡도가 크게 다릅니다. 통계 기준선은 일반 열차 기준이므로, 급행에 그대로 적용하면 신뢰도를 낮춰야 합니다.

---

## 8. 도착 정보 — `Arrival`, `ArrivalState`

```kotlin
package com.hellstation.domain.model

import java.time.Instant

/** 도착정보 API의 arvlCd. 숫자를 그대로 쓰지 않고 의미를 붙입니다. */
enum class ArrivalState {
    ENTERING,        // 0 진입
    ARRIVED,         // 1 도착
    DEPARTED,        // 2 출발
    PREV_DEPARTED,   // 3 전역 출발
    PREV_ENTERING,   // 4 전역 진입
    PREV_ARRIVED,    // 5 전역 도착
    RUNNING,         // 99 운행중
    UNKNOWN,
    ;

    companion object {
        fun parse(raw: String?): ArrivalState = when (raw?.trim()) {
            "0" -> ENTERING
            "1" -> ARRIVED
            "2" -> DEPARTED
            "3" -> PREV_DEPARTED
            "4" -> PREV_ENTERING
            "5" -> PREV_ARRIVED
            "99" -> RUNNING
            else -> UNKNOWN
        }
    }
}

/**
 * "이 역에 이 열차가 언제 온다"는 한 건.
 *
 * @param rawSecondsUntilArrival  API가 준 barvlDt 원본. "0"이 자주 오므로 그대로 믿으면 안 됩니다
 * @param observedAt              recptnDt. 이 정보가 만들어진 시각
 * @param message                 arvlMsg2. "3분 후 (시청)" 같은 표시 문구
 */
data class Arrival(
    val station: StationId,
    val train: Train,
    val state: ArrivalState,
    val rawSecondsUntilArrival: Int,
    val observedAt: Instant,
    val message: String,
) {
    /** 이 정보가 만들어진 지 몇 초 지났나. 신뢰도 판정의 입력입니다. */
    fun dataAgeSeconds(now: Instant): Long = (now.epochSecond - observedAt.epochSecond).coerceAtLeast(0)

    /**
     * 지연을 보정한 실제 남은 시간(초). 믿을 수 없으면 null.
     *
     * barvlDt == 0 이면서 state == RUNNING 인 경우는 "정보 없음"입니다.
     * (docs/api-validation.md 1번 함정)
     */
    fun secondsUntilArrival(now: Instant): Int? {
        if (rawSecondsUntilArrival == 0 && state == ArrivalState.RUNNING) return null
        val corrected = rawSecondsUntilArrival - dataAgeSeconds(now).toInt()
        return corrected.coerceAtLeast(0)
    }
}
```

---

## 9. 혼잡도 — `CrowdLevel`, `Confidence`, `CrowdIndex`

등급 경계와 신뢰도 판정 근거는 `docs/crowding-levels.md`에 있습니다. 여기는 타입 정의만 둡니다.

```kotlin
package com.hellstation.domain.model

/** 혼잡도 등급. 나쁜 순서대로 나열되어 있습니다(ordinal에 의미가 있음). */
enum class CrowdLevel {
    EASY,     // 0 ~ 45%
    BUSY,     // 45 ~ 80%
    BAD,      // 80 ~ 130%
    HELL,     // 130 ~ 170%
    WTF,      // 170% ~
    UNKNOWN,  // 데이터 없음. 절대 EASY로 대체하지 마세요
    ;

    companion object {
        /**
         * 혼잡도 %를 등급으로 바꿉니다. 앱에서 이 변환을 하는 곳은 여기 하나뿐이어야 합니다.
         * percent가 null이면 UNKNOWN입니다.
         */
        fun fromPercent(percent: Double?): CrowdLevel = when {
            percent == null || percent < 0 -> UNKNOWN
            percent < 45.0 -> EASY
            percent < 80.0 -> BUSY
            percent < 130.0 -> BAD
            percent < 170.0 -> HELL
            else -> WTF
        }
    }
}

/** 이 값을 얼마나 믿을 수 있나. 판정 규칙은 docs/crowding-levels.md 3절. */
enum class Confidence {
    HIGH,
    MEDIUM,
    LOW,
    ;

    /** 두 근거를 합쳤을 때의 신뢰도는 항상 낮은 쪽을 따릅니다. */
    fun combineWith(other: Confidence): Confidence =
        if (this.ordinal >= other.ordinal) this else other
}

/** 근거가 무엇이었는지. 화면에서 안내 문구를 고르는 데 씁니다. */
enum class CrowdSource {
    REALTIME,          // 실시간 도착정보 기반
    BASELINE,          // 서울교통공사 통계 CSV
    REALTIME_BASELINE, // 둘 다
    NEIGHBOR,          // 인접 역에서 추정
    NONE,              // 근거 없음 -> UNKNOWN
}

/**
 * 특정 역·방향·시각의 혼잡도 판정 결과. 화면에 내려보내는 최종 형태입니다.
 *
 * @param percent    정원 대비 %. 160명/칸 = 100%. UNKNOWN이면 null
 * @param level      percent에서 유도된 등급
 * @param confidence 신뢰도
 * @param source     무엇을 근거로 계산했나
 * @param at         이 값이 가리키는 시각(Time Slider로 미래를 볼 수 있으므로 now와 다를 수 있음)
 */
data class CrowdIndex(
    val percent: Double?,
    val level: CrowdLevel,
    val confidence: Confidence,
    val source: CrowdSource,
    val at: java.time.Instant,
) {
    companion object {
        fun unknown(at: java.time.Instant) = CrowdIndex(
            percent = null,
            level = CrowdLevel.UNKNOWN,
            confidence = Confidence.LOW,
            source = CrowdSource.NONE,
            at = at,
        )
    }
}
```

**`percent`가 nullable이고 `level`이 함께 있는 이유:** 화면은 등급(색)만 필요할 때가 많고, 신뢰도가 `LOW`일 때는 숫자를 아예 보여주지 않습니다 (`docs/crowding-levels.md` 4절). 둘을 따로 들고 있어야 그 처리가 가능합니다.

---

## 10. 결론 — `RideOrWait`

HellStation의 핵심 산출물입니다. "지금 탈까, 기다릴까"에 대한 답.

```kotlin
package com.hellstation.domain.model

enum class Verdict {
    RIDE,       // 지금 타세요
    WAIT,       // 다음 열차를 기다리세요
    NO_DATA,    // 판단할 근거가 없습니다
}

/**
 * 지금 열차와 다음 열차를 비교한 결과.
 *
 * @param reason      왜 이런 결론인지. 화면에 그대로 보여줄 수 있는 문구
 * @param waitSeconds WAIT일 때 얼마나 더 기다려야 하나
 * @param confidence  이 판단의 신뢰도. current/next 중 낮은 쪽을 따릅니다
 */
data class RideOrWait(
    val verdict: Verdict,
    val current: TrainOption?,
    val next: TrainOption?,
    val reason: String,
    val waitSeconds: Int?,
    val confidence: Confidence,
)

data class TrainOption(
    val arrival: Arrival,
    val crowd: CrowdIndex,
)
```

### 판단 규칙 (데이터·기능 담당이 구현)

1. `current`가 없으면 → `NO_DATA`
2. `next`가 없으면 → `RIDE` ("다음 열차 정보가 없습니다")
3. 두 열차의 `CrowdLevel`이 같으면 → `RIDE` (기다릴 이유 없음)
4. `next`가 `current`보다 **두 등급 이상** 낮으면 → `WAIT`
5. `next`가 한 등급 낮고 대기 시간이 **4분 이하**면 → `WAIT`
6. 그 외 → `RIDE`
7. 어느 쪽이든 `confidence == LOW`면 → `RIDE`로 기울입니다. **근거가 약할 때 사용자를 기다리게 하지 마세요.**
8. `current.arrival.train.isLastTrain`이면 → 무조건 `RIDE` ("막차입니다")

---

## 11. 저장소 인터페이스 (경계)

`domain/repository/`에 둡니다. **화면은 이 인터페이스만 알고, `data/`의 구현체는 모릅니다.**

```kotlin
package com.hellstation.domain.repository

import com.hellstation.domain.model.*
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** 역·노선 같은 잘 변하지 않는 정보. 앱 시작 시 한 번 로드해 캐싱합니다. */
interface SubwayNetworkRepository {
    suspend fun allLines(): List<LineId>
    suspend fun stationsOf(line: LineId): List<Station>
    suspend fun segmentsOf(line: LineId): List<Segment>
    suspend fun findStationsByName(query: String): List<Station>
    suspend fun station(id: StationId): Station?
}

/** 실시간 도착정보. */
interface ArrivalRepository {
    /** 실패하면 예외 대신 빈 리스트를 돌려주고, 호출한 쪽이 UNKNOWN으로 처리하게 합니다. */
    suspend fun arrivalsAt(stationName: String): List<Arrival>
}

/** 혼잡도 판정. */
interface CrowdRepository {
    suspend fun crowdAt(station: StationId, direction: Direction, at: Instant): CrowdIndex

    /** Heatmap 화면용. 화면에 보이는 역 전체를 한 번에 받습니다. */
    fun crowdMap(at: Instant): Flow<Map<StationId, CrowdIndex>>
}

/** Ride or Wait 판단. */
interface VerdictRepository {
    suspend fun decide(origin: StationId, destination: StationId, at: Instant): RideOrWait
}
```

### 실패 처리 원칙

**네트워크 실패가 화면을 멈추게 하면 안 됩니다.** 저장소는 예외를 밖으로 던지지 말고, 데이터가 없는 상태(`UNKNOWN`, 빈 리스트)를 돌려주세요. 지하철 안에서는 신호가 자주 끊깁니다 — 이건 예외 상황이 아니라 **정상 상황**입니다.

---

## 12. 패키지 구조

```
app/src/main/java/com/hellstation/
├── navigation/          [설계 담당]  화면 이동, MainActivity, 화면 목적지 정의
│   ├── MainActivity.kt
│   ├── HellDestination.kt
│   ├── HellNavHost.kt
│   └── PlaceholderScreens.kt   <- 화면 담당이 만들면 지울 임시 화면
│
├── domain/              [데이터·기능 담당]  순수 Kotlin. Android 의존성 없음
│   ├── model/           이 문서의 1~10절 타입들
│   ├── repository/      11절 인터페이스
│   └── usecase/         혼잡도 계산, Ride or Wait 판단
│
├── data/                [데이터·기능 담당]  실제 API·파일 접근
│   ├── remote/
│   │   ├── dto/         API 응답 그대로의 모양. domain 타입과 섞지 말 것
│   │   ├── SeoulSubwayApi.kt
│   │   └── mapper/      dto -> domain 변환. 정규화는 전부 여기서
│   ├── local/
│   │   ├── baseline/    혼잡도 CSV 로더
│   │   └── cache/       역 목록 캐시
│   └── repository/      11절 인터페이스 구현체
│
└── ui/                  [화면 담당]  Compose 화면
    ├── theme/           색·타이포. 혼잡도 5단계 색 포함
    ├── component/       공통 컴포넌트
    ├── splash/
    ├── heatmap/
    ├── search/
    ├── station/         역 Bottom Sheet
    └── result/          Ride or Wait 결과
```

### 의존 방향

```
ui  ──▶  domain  ◀──  data
             ▲
        navigation
```

- `domain`은 **아무것도 의존하지 않습니다.** Android SDK도, Compose도 import하지 않습니다. 그래야 순수 JVM 테스트가 가능합니다.
- `ui`와 `data`는 서로를 **모릅니다.** 둘 다 `domain`만 봅니다.
- `navigation`은 화면 이동만 알고, 데이터는 모릅니다. 그래서 `String` 같은 원시 타입만 경로 인자로 씁니다.

---

## 13. 열려 있는 결정

다음 사람이 정해야 할 것들입니다. 지금 정하지 않아도 진행에 지장은 없습니다.

- [ ] **칸별 혼잡도를 지원할 것인가** — 지원하면 `Train`에 `cars: List<CrowdIndex>`가 붙고 TMAP 키가 필요합니다 (`docs/api-validation.md` 6번). 1차에서는 빼는 것을 권장합니다.
- [ ] **경로 탐색을 직접 할 것인가** — `Segment` 그래프로 최단 경로를 구현할지, 아니면 출발/도착역만 받고 환승은 사용자에게 맡길지. 1차는 후자를 권장합니다.
- [ ] **Time Slider의 범위** — 통계 기준선이 05:30~24:30이므로 그 밖은 보여줄 값이 없습니다. 슬라이더를 이 범위로 제한할지, 밖에서는 `UNKNOWN`을 보여줄지.
- [ ] **혼잡도 CSV를 앱에 동봉할지 서버에서 받을지** — 동봉하면 오프라인에서 동작하지만 분기마다 앱을 새로 내야 합니다. 1차는 동봉을 권장합니다 (`app/src/main/assets/`).
