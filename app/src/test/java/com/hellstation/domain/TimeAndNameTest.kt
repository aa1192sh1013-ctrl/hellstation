package com.hellstation.domain

import com.hellstation.data.remote.mapper.StationNameNormalizer
import com.hellstation.domain.model.Arrival
import com.hellstation.domain.model.ArrivalState
import com.hellstation.domain.model.DayType
import com.hellstation.domain.model.Direction
import com.hellstation.domain.model.LineId
import com.hellstation.ui.copy.HellCopy
import com.hellstation.domain.model.StationId
import com.hellstation.domain.model.TimeSlot
import com.hellstation.domain.model.Train
import com.hellstation.domain.model.TrainType
import com.hellstation.domain.usecase.ServiceCalendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalTime

/** 시간대·요일 계산과 역명 정규화. 여기가 어긋나면 통계 조회가 통째로 빗나갑니다. */
class TimeAndNameTest {

    private val calendar = ServiceCalendar()

    // ── TimeSlot ───────────────────────────────────────────────────────────

    @Test
    fun `시간대는 30분 단위로 내림한다`() {
        assertEquals(8 * 60, TimeSlot.of(LocalTime.of(8, 0)).minutesFromMidnight)
        assertEquals(8 * 60, TimeSlot.of(LocalTime.of(8, 29)).minutesFromMidnight)
        assertEquals(8 * 60 + 30, TimeSlot.of(LocalTime.of(8, 59)).minutesFromMidnight)
    }

    @Test
    fun `자정 넘긴 시각은 24시대로 이어진다`() {
        // 지하철의 하루는 자정에 끝나지 않습니다.
        assertEquals(24 * 60, TimeSlot.of(LocalTime.of(0, 15)).minutesFromMidnight)
        assertEquals(24 * 60 + 30, TimeSlot.of(LocalTime.of(0, 45)).minutesFromMidnight)
    }

    @Test
    fun `통계 시간대는 39칸이다`() {
        // 05:30 ~ 24:30, 30분 단위
        assertEquals(39, TimeSlot.ALL.size)
        assertEquals(TimeSlot.FIRST.minutesFromMidnight, TimeSlot.ALL.first().minutesFromMidnight)
        assertEquals(TimeSlot.LAST.minutesFromMidnight, TimeSlot.ALL.last().minutesFromMidnight)
    }

    @Test
    fun `CSV 컬럼명을 왕복 변환할 수 있다`() {
        assertEquals("5시30분", TimeSlot(5 * 60 + 30).csvLabel)
        assertEquals("24시00분", TimeSlot(24 * 60).csvLabel)
        assertEquals(5 * 60 + 30, TimeSlot.fromCsvLabel("5시30분")!!.minutesFromMidnight)
        assertEquals(24 * 60 + 30, TimeSlot.fromCsvLabel("24시30분")!!.minutesFromMidnight)
        assertNull(TimeSlot.fromCsvLabel("출발역"))
    }

    @Test
    fun `운행 시간 밖은 통계 범위를 벗어난다`() {
        assertTrue(TimeSlot(8 * 60).isWithinServiceHours)
        assertFalse(TimeSlot(3 * 60).isWithinServiceHours)
    }

    // ── ServiceCalendar ────────────────────────────────────────────────────

    @Test
    fun `첫차 전은 전날 운행으로 친다`() {
        // KST 2026-08-25(화) 05:00 -> 운행일은 08-24(월)
        val beforeFirstTrain = Instant.parse("2026-08-24T20:00:00Z")
        assertEquals("2026-08-24", calendar.serviceDateAt(beforeFirstTrain).toString())
        assertEquals(DayType.WEEKDAY, calendar.dayTypeAt(beforeFirstTrain))
    }

    @Test
    fun `토요일과 일요일을 구분한다`() {
        // KST 2026-08-22(토) 12:00
        assertEquals(DayType.SATURDAY, calendar.dayTypeAt(Instant.parse("2026-08-22T03:00:00Z")))
        // KST 2026-08-23(일) 12:00
        assertEquals(DayType.SUNDAY, calendar.dayTypeAt(Instant.parse("2026-08-23T03:00:00Z")))
    }

    @Test
    fun `공휴일은 일요일 통계를 쓰고 대체 사실을 알려 준다`() {
        val holiday = java.time.LocalDate.of(2026, 8, 25) // 화요일이라고 가정
        val withHolidays = ServiceCalendar(holidays = setOf(holiday))
        val noon = Instant.parse("2026-08-25T03:00:00Z") // KST 08-25 12:00

        assertEquals(DayType.SUNDAY, withHolidays.dayTypeAt(noon))
        assertTrue(withHolidays.isHolidayFallback(noon))
        // 공휴일 목록이 없으면 평일 그대로
        assertEquals(DayType.WEEKDAY, calendar.dayTypeAt(noon))
        assertFalse(calendar.isHolidayFallback(noon))
    }

    @Test
    fun `Time Slider 눈금을 실제 시각으로 되돌릴 수 있다`() {
        val reference = Instant.parse("2026-08-22T03:00:00Z") // KST 08-22 12:00
        val slot = TimeSlot(8 * 60)
        assertEquals(slot.minutesFromMidnight, calendar.slotAt(calendar.instantForSlot(slot, reference)).minutesFromMidnight)
    }

    // ── 역명 정규화 ─────────────────────────────────────────────────────────

    @Test
    fun `소스마다 다른 역명 표기를 하나로 맞춘다`() {
        // 좌표 API는 "서울역", 도착정보 API는 "서울"
        assertEquals("서울", StationNameNormalizer.normalize("서울역"))
        assertEquals("서울", StationNameNormalizer.normalize("서울"))
        assertEquals("시청", StationNameNormalizer.normalize("시청"))
    }

    @Test
    fun `부역명 괄호를 떼어낸다`() {
        assertEquals("총신대입구", StationNameNormalizer.normalize("총신대입구(이수)"))
        assertEquals("숭실대입구", StationNameNormalizer.normalize("숭실대입구(살피재)"))
    }

    @Test
    fun `가운뎃점 표기를 통일한다`() {
        assertEquals(
            StationNameNormalizer.normalize("4·19민주묘지"),
            StationNameNormalizer.normalize("4.19민주묘지"),
        )
    }

    @Test
    fun `빈 값에도 터지지 않는다`() {
        assertEquals("", StationNameNormalizer.normalize(null))
        assertEquals("", StationNameNormalizer.normalize("   "))
        assertEquals("역", StationNameNormalizer.normalize("역"))
    }

    @Test
    fun `검색은 역 접미사를 무시한다`() {
        assertTrue(StationNameNormalizer.matches("서울역", "서울"))
        assertTrue(StationNameNormalizer.matches("서울", "서울역"))
        assertFalse(StationNameNormalizer.matches("", "서울"))
    }

    // ── Arrival: barvlDt 함정 ──────────────────────────────────────────────

    private fun arrival(
        rawEta: Int,
        state: ArrivalState,
        observedAt: Instant,
    ) = Arrival(
        station = StationId(LineId.LINE_1, "0150"),
        train = Train("0064", LineId.LINE_1, Direction.UP, TrainType.NORMAL, "양주", "양주행", false),
        state = state,
        rawSecondsUntilArrival = rawEta,
        observedAt = observedAt,
        message = "",
    )

    @Test
    fun `barvlDt가 0이고 운행중이면 도착 시간을 모르는 것이다`() {
        val now = Instant.parse("2026-08-22T03:00:00Z")
        // "0초 후 도착"이 아니라 "정보 없음"입니다.
        assertNull(arrival(0, ArrivalState.RUNNING, now).secondsUntilArrival(now))
    }

    @Test
    fun `이미 도착한 열차는 0초다`() {
        val now = Instant.parse("2026-08-22T03:00:00Z")
        assertEquals(0, arrival(0, ArrivalState.ARRIVED, now).secondsUntilArrival(now))
        assertEquals(0, arrival(0, ArrivalState.ENTERING, now).secondsUntilArrival(now))
    }

    @Test
    fun `데이터가 만들어진 뒤 흐른 시간만큼 도착 시간을 당긴다`() {
        val observed = Instant.parse("2026-08-22T03:00:00Z")
        val now = observed.plusSeconds(120)
        // 3분 후 도착이라고 했는데 그 정보가 2분 전 것이면 실제로는 1분 남았다
        assertEquals(60, arrival(180, ArrivalState.RUNNING, observed).secondsUntilArrival(now))
        assertEquals(120L, arrival(180, ArrivalState.RUNNING, observed).dataAgeSeconds(now))
    }

    @Test
    fun `이미 지나간 시간보다 더 오래됐으면 0으로 묶는다`() {
        val observed = Instant.parse("2026-08-22T03:00:00Z")
        val now = observed.plusSeconds(600)
        assertEquals(0, arrival(180, ArrivalState.RUNNING, observed).secondsUntilArrival(now))
    }

    @Test
    fun `떠난 열차는 탈 수 없다`() {
        val now = Instant.parse("2026-08-22T03:00:00Z")
        // arvlCd=2 "서울 출발" — 실제 응답에 섞여 옵니다
        assertFalse(arrival(0, ArrivalState.DEPARTED, now).isBoardable)
        // 전역 출발은 이쪽으로 오는 중이므로 탈 수 있습니다
        assertTrue(arrival(120, ArrivalState.PREV_DEPARTED, now).isBoardable)
    }

    @Test
    fun `CSV 의 자정 이후 시간대는 24시로 읽는다`() {
        // 서울교통공사 파일은 "24시00분"이 아니라 "00시00분"으로 적습니다.
        // 그대로 0분으로 읽으면 운행 시간(05:30~24:30) 밖이라 조용히 버려집니다.
        assertEquals(TimeSlot(24 * 60), TimeSlot.fromCsvLabel("00시00분"))
        assertEquals(TimeSlot(24 * 60 + 30), TimeSlot.fromCsvLabel("00시30분"))

        // 낮 시간대는 그대로입니다.
        assertEquals(TimeSlot(5 * 60 + 30), TimeSlot.fromCsvLabel("5시30분"))
        assertEquals(TimeSlot(23 * 60 + 30), TimeSlot.fromCsvLabel("23시30분"))

        // 둘 다 운행 시간 안에 들어와야 실제로 쓰입니다.
        assertTrue(TimeSlot.fromCsvLabel("00시30분")!! in TimeSlot.ALL)
    }

    @Test
    fun `시설 구간 이름도 운영 노선으로 이어진다`() {
        // 좌표 API 는 노선을 시설 구간명으로 부릅니다. 승객에게는 다 같은 1호선입니다.
        assertEquals(LineId.LINE_1, LineId.fromDisplayName("경부선"))
        assertEquals(LineId.LINE_1, LineId.fromDisplayName("경인선"))
        assertEquals(LineId.LINE_1, LineId.fromDisplayName("경원선"))
        assertEquals(LineId.LINE_3, LineId.fromDisplayName("일산선"))
        assertEquals(LineId.LINE_4, LineId.fromDisplayName("안산선"))

        // 역정보 API 쪽 이름 흔들림
        assertEquals(LineId.GYEONGUI_JUNGANG, LineId.fromDisplayName("경의선"))
        assertEquals(LineId.UI_SINSEOL, LineId.fromDisplayName("우이신설경전철"))
        assertEquals(LineId.SUIN_BUNDANG, LineId.fromDisplayName("분당선"))

        // 원래 이름은 그대로 동작해야 합니다
        assertEquals(LineId.LINE_1, LineId.fromDisplayName("1호선"))
        assertEquals(LineId.LINE_1, LineId.fromDisplayName("01호선"))
        assertEquals(LineId.UI_SINSEOL, LineId.fromDisplayName("우이신설선"))

        // 모르는 이름에는 아무거나 돌려주면 안 됩니다.
        // 여기서 대충 짐작하면 엉뚱한 노선에 역이 붙습니다.
        assertNull(LineId.fromDisplayName("있지도않은선"))
        assertNull(LineId.fromDisplayName(""))
    }

    @Test
    fun `서울시 밖 노선도 이름으로 이어진다`() {
        assertEquals(LineId.INCHEON_1, LineId.fromDisplayName("인천선"))
        assertEquals(LineId.INCHEON_1, LineId.fromDisplayName("인천1호선"))
        assertEquals(LineId.INCHEON_2, LineId.fromDisplayName("인천2호선"))
        assertEquals(LineId.EVERLINE, LineId.fromDisplayName("용인경전철"))
        assertEquals(LineId.EVERLINE, LineId.fromDisplayName("에버라인선"))
        assertEquals(LineId.UIJEONGBU, LineId.fromDisplayName("의정부경전철"))
        assertEquals(LineId.GIMPO_GOLD, LineId.fromDisplayName("김포도시철도"))
        assertEquals(LineId.GTX_A, LineId.fromDisplayName("GTX-A"))
        assertEquals(LineId.GTX_A, LineId.fromDisplayName("수도권 광역급행철도"))
    }

    @Test
    fun `실시간을 받을 수 있는 노선인지 구분한다`() {
        // 서울시 코드(10xx)만 실시간 도착정보가 옵니다.
        assertTrue(LineId.LINE_1.hasRealtime)
        assertTrue(LineId.GTX_A.hasRealtime)      // 수서·동탄에서 실제 확인
        assertFalse(LineId.INCHEON_1.hasRealtime) // 인천교통공사 운영
        assertFalse(LineId.GIMPO_GOLD.hasRealtime)
    }

    @Test
    fun `노선 배지 글자는 서로 겹치지 않는다`() {
        // "인천"으로만 줄이면 1호선과 2호선이 배지에서 구분되지 않습니다.
        assertEquals("인1", HellCopy.lineShort(LineId.INCHEON_1))
        assertEquals("인2", HellCopy.lineShort(LineId.INCHEON_2))
        assertEquals("A", HellCopy.lineShort(LineId.GTX_A))
        assertEquals("1", HellCopy.lineShort(LineId.LINE_1))
        assertEquals("신분", HellCopy.lineShort(LineId.SINBUNDANG))

        // 노선 전체에서 배지 글자가 하나도 겹치면 안 됩니다.
        val labels = LineId.entries.map { HellCopy.lineShort(it) }
        assertEquals(labels.size, labels.toSet().size)
    }
}
