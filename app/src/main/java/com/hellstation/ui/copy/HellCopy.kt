package com.hellstation.ui.copy

import com.hellstation.domain.model.ArrivalState
import com.hellstation.domain.model.Confidence
import com.hellstation.domain.model.CrowdIndex
import com.hellstation.domain.model.CrowdLevel
import com.hellstation.domain.model.DataTier
import com.hellstation.domain.model.LineId
import com.hellstation.domain.model.ServiceStatus
import com.hellstation.domain.model.UnavailableReason
import com.hellstation.domain.model.Verdict
import kotlin.math.absoluteValue

/** 문구의 말투. 이 앱에서 가장 중요한 구분입니다. */
enum class CopyTone {
    /** 장난스러운 말투. 평소에 씁니다 */
    PLAYFUL,

    /** 담백한 안내. 뭔가 잘못됐거나 확실하지 않을 때 씁니다 */
    PLAIN,
}

/** 화면 위쪽에 들어가는 제목 한 쌍. */
data class HeadlineCopy(
    val title: String,
    val subtitle: String,
    val tone: CopyTone,
)

/**
 * 화면에 나가는 문구를 한곳에 모읍니다.
 *
 * ## 언제 장난치고 언제 안 치는가
 *
 * HellStation은 장난스러운 앱이지만, **아래 세 경우에는 담백하게 씁니다.**
 *
 * 1. 운행에 문제가 있을 때 (지연·중단·운행시간 아님)
 * 2. 혼잡도를 모를 때 (`CrowdLevel.UNKNOWN`)
 * 3. 값을 믿기 어려울 때 (`Confidence.LOW`)
 *
 * 데이터가 없는데 농담을 하면 사용자는 앱이 고장 났다고 생각합니다.
 * 열차가 멈췄는데 "오늘도 지옥철"이라고 하면 조롱처럼 읽힙니다.
 * 어림값에 확신에 찬 농담을 붙이면 틀렸을 때 신뢰를 잃습니다.
 *
 * **판단은 [toneFor] 한 곳에서만 합니다.** 화면마다 따로 조건을 쓰면 반드시 갈라집니다.
 *
 * ## 왜 등급마다 문구가 여러 개인가
 *
 * 등급당 한 줄만 두면 지도를 훑을 때 같은 문장이 계속 나와서 금방 질립니다.
 * 대신 난수를 쓰면 화면을 다시 그릴 때마다 문구가 바뀌어 읽는 도중에 글자가 변합니다.
 *
 * 그래서 **역 이름과 등급을 섞은 값으로 고릅니다.** 같은 역·같은 등급이면 항상 같은 문구가
 * 나오고, 등급이 바뀌면 문구도 함께 바뀝니다.
 */
object HellCopy {

    // ── 말투 결정 ───────────────────────────────────────────────────────────

    /**
     * 지금 장난쳐도 되는가. **모든 화면이 이 함수 하나만 봅니다.**
     */
    fun toneFor(
        level: CrowdLevel,
        status: ServiceStatus = ServiceStatus.NORMAL,
        confidence: Confidence = Confidence.HIGH,
    ): CopyTone = when {
        !status.allowsPlayfulCopy -> CopyTone.PLAIN
        !level.isKnown -> CopyTone.PLAIN
        confidence == Confidence.LOW -> CopyTone.PLAIN
        else -> CopyTone.PLAYFUL
    }

    /** [CrowdIndex]에서 바로 판단하는 짧은 길. */
    fun toneFor(crowd: CrowdIndex, status: ServiceStatus = ServiceStatus.NORMAL): CopyTone =
        toneFor(crowd.level, status, crowd.confidence)

    // ── 제목 ────────────────────────────────────────────────────────────────

    /**
     * 화면 제목과 부제.
     *
     * @param seed 같은 등급 안에서 어떤 문구를 고를지 정하는 값. 보통 역 이름을 넣습니다.
     *   비워 두면 항상 첫 번째 문구가 나옵니다
     */
    fun headline(
        crowd: CrowdIndex,
        status: ServiceStatus = ServiceStatus.NORMAL,
        seed: String = "",
    ): HeadlineCopy {
        val tone = toneFor(crowd, status)
        if (tone == CopyTone.PLAIN) {
            return plainHeadline(crowd, status)
        }
        val level = crowd.level
        return HeadlineCopy(
            title = pick(PLAYFUL_TITLES[level].orEmpty(), seed, level),
            subtitle = pick(PLAYFUL_SUBTITLES[level].orEmpty(), seed + "s", level),
            tone = CopyTone.PLAYFUL,
        )
    }

    /**
     * 담백한 제목. 무엇이 문제인지 먼저 말하고, 사용자가 뭘 하면 되는지 알려 줍니다.
     *
     * 순서가 중요합니다. 운행 문제가 자료 부족보다 먼저입니다 —
     * 열차가 멈췄는데 "자료가 없습니다"라고 하면 엉뚱한 안내가 됩니다.
     */
    private fun plainHeadline(crowd: CrowdIndex, status: ServiceStatus): HeadlineCopy = when {
        // 등급을 아는 동안에는 등급을 말합니다.
        //
        // 운행 문제(지연·열차 정보 없음)는 바로 위 안내 배너가 이미 말하고 있습니다.
        // 여기서 또 말하면 같은 문장이 한 화면에 두 번 나오고, 그러는 사이 배지와 게이지는
        // "혼잡"을 가리키고 있어서 화면이 서로 반대말을 하게 됩니다.
        // 열차가 안 와도 **역이 붐빈다는 사실은 그대로**입니다.
        crowd.level.isKnown -> HeadlineCopy(
            title = plainTitle(crowd.level),
            subtitle = plainSubtitle(status),
            tone = CopyTone.PLAIN,
        )

        status == ServiceStatus.CLOSED -> HeadlineCopy(
            "지금은 운행 시간이 아닙니다",
            "첫차는 05:30 무렵입니다",
            CopyTone.PLAIN,
        )

        status == ServiceStatus.SUSPENDED -> HeadlineCopy(
            "이 방향 열차 정보가 없습니다",
            "잠시 후 다시 확인해 주세요",
            CopyTone.PLAIN,
        )

        status == ServiceStatus.DELAYED -> HeadlineCopy(
            "열차가 지연되고 있습니다",
            "평소 혼잡도와 다를 수 있습니다",
            CopyTone.PLAIN,
        )

        else -> HeadlineCopy(
            "혼잡도를 알 수 없습니다",
            "이 역·시간대의 자료가 없습니다",
            CopyTone.PLAIN,
        )
    }

    /**
     * 등급을 담담하게 알리는 한 줄.
     *
     * 배지 글자([levelBadge])에 "-할"을 붙여 만들면 안 됩니다. "혼잡할"은 되지만
     * "지옥할"·"여유할"·"대환장할"은 말이 안 됩니다. 다섯 줄을 따로 씁니다.
     */
    private fun plainTitle(level: CrowdLevel): String = when (level) {
        CrowdLevel.EASY -> "여유로울 것으로 보입니다"
        CrowdLevel.BUSY -> "탈 만할 것으로 보입니다"
        CrowdLevel.BAD -> "혼잡할 것으로 보입니다"
        CrowdLevel.HELL -> "지옥일 것으로 보입니다"
        CrowdLevel.WTF -> "대환장일 것으로 보입니다"
        CrowdLevel.UNKNOWN -> "혼잡도를 알 수 없습니다"
    }

    /**
     * 등급은 아는데 말투를 낮춰야 할 때 붙는 한 줄.
     *
     * **왜 낮췄는지**를 말합니다. 운행이 정상인데 여기까지 왔다면 신뢰도가 낮다는 뜻입니다.
     */
    private fun plainSubtitle(status: ServiceStatus): String = when (status) {
        ServiceStatus.DELAYED -> "지연 중이라 평소와 다를 수 있습니다"
        ServiceStatus.SUSPENDED -> "열차 정보가 없어 역 기준으로 어림한 값입니다"
        ServiceStatus.CLOSED -> "운행 시간이 아니어서 예상값만 보여드립니다"
        ServiceStatus.NORMAL -> "실측이 아닌 예상값이라 실제와 다를 수 있습니다"
    }

    /** 지도 위쪽 같은 좁은 자리에 쓰는 한 줄. */
    fun shortHeadline(crowd: CrowdIndex, status: ServiceStatus, seed: String = ""): String =
        headline(crowd, status, seed).title

    // ── 등급 이름 ───────────────────────────────────────────────────────────

    /** 배지 안에 들어가는 아주 짧은 이름. 색과 **항상 함께** 보여야 합니다. */
    fun levelBadge(level: CrowdLevel): String = when (level) {
        CrowdLevel.EASY -> "여유"
        CrowdLevel.BUSY -> "보통"
        CrowdLevel.BAD -> "혼잡"
        CrowdLevel.HELL -> "지옥"
        CrowdLevel.WTF -> "대환장"
        CrowdLevel.UNKNOWN -> "정보없음"
    }

    /**
     * 등급 하나만 보고 고르는 제목. 미리보기와 범례처럼 **맥락이 없는 곳** 전용입니다.
     * 실제 화면에서는 [headline]을 쓰세요 — 운행 상태와 신뢰도를 함께 봅니다.
     */
    fun levelHeadline(level: CrowdLevel): String =
        PLAYFUL_TITLES[level]?.firstOrNull() ?: "아직 모르겠습니다"

    fun levelSubtitle(level: CrowdLevel): String =
        PLAYFUL_SUBTITLES[level]?.firstOrNull() ?: "이 역의 자료가 없습니다"

    // ── 결론 ────────────────────────────────────────────────────────────────

    /** 결론 한 단어. 화면에서 가장 큰 글씨입니다. */
    fun verdictWord(verdict: Verdict): String = when (verdict) {
        Verdict.RIDE -> "타세요"
        Verdict.WAIT -> "기다리세요"
        Verdict.NO_DATA -> "모르겠어요"
    }

    /** 결론 옆에 붙는 영문 — 키치한 맛. */
    fun verdictTag(verdict: Verdict): String = when (verdict) {
        Verdict.RIDE -> "RIDE"
        Verdict.WAIT -> "WAIT"
        Verdict.NO_DATA -> "NO DATA"
    }

    /**
     * 결론 위에 얹는 한마디. **이유 문구가 아닙니다** — 이유는 domain이 만든 것을 그대로 씁니다.
     *
     * 말투가 [CopyTone.PLAIN]이면 아무것도 얹지 않습니다. 근거가 약할 때
     * "믿고 타세요" 같은 말을 덧붙이면 안 됩니다.
     */
    fun verdictQuip(verdict: Verdict, tone: CopyTone, seed: String = ""): String? {
        if (tone == CopyTone.PLAIN) return null
        val variants = when (verdict) {
            Verdict.RIDE -> RIDE_QUIPS
            Verdict.WAIT -> WAIT_QUIPS
            Verdict.NO_DATA -> return null
        }
        return pick(variants, seed, verdict)
    }

    // ── 신뢰도 ──────────────────────────────────────────────────────────────

    /**
     * 신뢰도 안내.
     *
     * `HIGH`일 때는 아무 말도 하지 않습니다 — 잘 맞는다고 굳이 자랑할 필요가 없고,
     * 매번 뭔가 적혀 있으면 정작 낮을 때 눈에 안 들어옵니다.
     */
    fun confidenceNote(confidence: Confidence, tier: DataTier): String? = when (confidence) {
        Confidence.HIGH -> null
        Confidence.MEDIUM -> when (tier) {
            DataTier.HISTORICAL -> "같은 요일·시간대 평균으로 예상한 값입니다"
            else -> "실시간 정보가 조금 오래됐습니다"
        }

        Confidence.LOW -> when (tier) {
            DataTier.NONE -> "이 역의 혼잡도 자료가 없습니다"
            DataTier.HISTORICAL -> "참고용 어림값입니다. 실제와 다를 수 있습니다"
            else -> "정보가 확실하지 않습니다. 참고만 하세요"
        }
    }

    /** 신뢰도 배지에 들어갈 짧은 말. */
    fun confidenceBadge(confidence: Confidence): String = when (confidence) {
        Confidence.HIGH -> "실시간"
        Confidence.MEDIUM -> "예상"
        Confidence.LOW -> "참고용"
    }

    /** 데이터 계층 설명 — 어디서 온 값인지 궁금해하는 사용자를 위해. */
    fun tierLabel(tier: DataTier): String = when (tier) {
        DataTier.LIVE -> "실시간 측정값"
        DataTier.ESTIMATED -> "실시간 보정"
        DataTier.HISTORICAL -> "통계 평균"
        DataTier.NONE -> "자료 없음"
    }

    // ── 운행 상태 ───────────────────────────────────────────────────────────

    /**
     * 운행에 문제가 있을 때의 제목. 정상이면 null입니다.
     * 대부분의 화면은 [headline]을 쓰면 되고, 이건 따로 배너를 띄울 때만 씁니다.
     */
    fun serviceHeadline(status: ServiceStatus): String? = when (status) {
        ServiceStatus.NORMAL -> null
        ServiceStatus.DELAYED -> "열차가 지연되고 있습니다"
        ServiceStatus.SUSPENDED -> "이 방향 열차 정보가 없습니다"
        ServiceStatus.CLOSED -> "지금은 운행 시간이 아닙니다"
    }

    fun serviceSubtitle(status: ServiceStatus): String? = when (status) {
        ServiceStatus.NORMAL -> null
        ServiceStatus.DELAYED -> "평소 혼잡도와 다를 수 있습니다"
        ServiceStatus.SUSPENDED -> "잠시 후 다시 확인해 주세요"
        ServiceStatus.CLOSED -> "첫차는 05:30 무렵입니다"
    }

    /**
     * 정보를 못 받았을 때의 제목.
     *
     * 이유마다 사용자가 할 수 있는 일이 다릅니다. "실패했습니다" 한 줄로 뭉뚱그리면
     * 기다려야 하는지 다시 눌러야 하는지 알 수 없습니다.
     */
    fun failureHeadline(reason: UnavailableReason): String = when (reason) {
        UnavailableReason.NETWORK -> "지금은 연결이 안 됩니다"
        UnavailableReason.NO_KEY -> "이 역은 실시간 조회 범위 밖입니다"
        UnavailableReason.OUTSIDE_SEOUL -> "서울시 구간 밖이라 실시간 정보가 없습니다"
        UnavailableReason.CLOSED -> "지금은 운행 시간이 아닙니다"
        UnavailableReason.NO_DATA -> "도착 정보를 받지 못했습니다"
    }

    fun failureSubtitle(reason: UnavailableReason): String = when (reason) {
        UnavailableReason.NETWORK -> "지하철 안에서는 흔한 일입니다. 다시 해 보세요"
        UnavailableReason.NO_KEY -> "인증키가 없어 서울역 말고는 실시간 정보를 받을 수 없습니다"
        UnavailableReason.OUTSIDE_SEOUL -> "역 기준 예상 혼잡도만 보여드립니다"
        UnavailableReason.CLOSED -> "첫차는 05:30 무렵입니다"
        UnavailableReason.NO_DATA -> "잠시 뒤 다시 해 보세요"
    }

    /**
     * 다시 눌러 볼 값어치가 있는 실패인가.
     *
     * 인증키가 없거나 운행 시간이 아닌 것은 **다시 눌러도 그대로**입니다.
     * 그런 상황에 "다시 시도" 버튼을 주면 사용자를 헛수고시키는 것입니다.
     */
    fun isRetryable(reason: UnavailableReason): Boolean = when (reason) {
        UnavailableReason.NETWORK, UnavailableReason.NO_DATA -> true
        UnavailableReason.NO_KEY,
        UnavailableReason.OUTSIDE_SEOUL,
        UnavailableReason.CLOSED,
        -> false
    }

    /**
     * 도착 목록이 비었을 때 그 자리에 넣을 한 줄.
     *
     * 제목·부제·안내 배너와 겹치지 않도록 **열차 이야기만** 합니다.
     */
    fun emptyTrains(status: ServiceStatus): String = when (status) {
        ServiceStatus.CLOSED -> "오늘 운행이 끝났습니다"
        ServiceStatus.SUSPENDED -> "들어오는 열차가 없습니다"
        ServiceStatus.DELAYED -> "도착 예정 정보를 아직 받지 못했습니다"
        ServiceStatus.NORMAL -> "도착 정보를 받지 못했습니다"
    }

    // ── 그 밖 ───────────────────────────────────────────────────────────────

    /**
     * 노선 동그라미 안에 들어갈 아주 짧은 표기.
     * 숫자 노선은 숫자만, 이름 노선은 앞 두 글자를 씁니다.
     */
    fun lineShort(line: LineId): String {
        val name = line.displayName

        // "1호선" -> "1"
        val leading = name.takeWhile { it.isDigit() }
        if (leading.isNotEmpty()) return leading

        // "인천1호선" -> "인1". 앞 두 글자만 쓰면 인천1호선과 인천2호선이
        // 똑같이 "인천"이 되어 배지로 구분이 안 됩니다.
        val embedded = name.firstOrNull { it.isDigit() }
        if (embedded != null) return "${name.first()}$embedded"

        // "GTX-A" -> "A". 실제 노선도에서도 알파벳 하나로 표기합니다.
        val afterDash = name.substringAfter('-', "")
        if (afterDash.isNotBlank() && afterDash.length <= 2) return afterDash

        return name.removeSuffix("선").take(2)
    }

    /**
     * 남은 시간을 사람 말로.
     *
     * ## 초를 모를 때 "--" 를 쓰지 않습니다
     *
     * 실측해 보니 API가 주는 열차의 **절반은 `barvlDt` 가 0**입니다(서울역 20대 중 5대만
     * 초가 있었습니다). 그렇다고 정보가 없는 건 아닙니다 — `arvlCd` 에 "전역 도착",
     * "전역 출발" 같은 **위치**가 들어 있습니다. "--" 만 내밀면 있는 정보를 버리는 셈이고,
     * 사용자는 앱이 고장 난 줄 압니다.
     *
     * 초를 믿을 수 없을 때는 **그 열차가 어디쯤인지**를 대신 말합니다.
     */
    fun etaText(seconds: Int?, state: ArrivalState = ArrivalState.UNKNOWN): String = when {
        seconds == null -> stateLong(state)
        seconds < 30 -> "곧 도착"
        seconds < 60 -> "1분 이내"
        else -> "${seconds / 60}분 ${(seconds % 60).toString().padStart(2, '0')}초"
    }

    /** 짧은 버전 — 목록 안에서. 칸이 좁아 두 글자로 줄입니다. */
    fun etaShort(seconds: Int?, state: ArrivalState = ArrivalState.UNKNOWN): String = when {
        seconds == null -> stateShort(state)
        seconds < 60 -> "곧"
        else -> "${seconds / 60}분"
    }

    /** 카드 안. 한 건만 보여주므로 풀어서 씁니다. */
    private fun stateLong(state: ArrivalState): String = when (state) {
        ArrivalState.ENTERING -> "진입 중"
        ArrivalState.ARRIVED -> "도착"
        ArrivalState.DEPARTED -> "출발함"
        ArrivalState.PREV_DEPARTED -> "전역 출발"
        ArrivalState.PREV_ENTERING -> "전역 진입"
        ArrivalState.PREV_ARRIVED -> "전역 도착"
        ArrivalState.RUNNING -> "운행 중"
        ArrivalState.UNKNOWN -> "정보 없음"
    }

    /** 목록 안. 44dp 칸에 들어가야 해서 두 글자까지만 씁니다. */
    private fun stateShort(state: ArrivalState): String = when (state) {
        ArrivalState.ENTERING -> "진입"
        ArrivalState.ARRIVED -> "도착"
        ArrivalState.DEPARTED -> "출발"
        ArrivalState.PREV_DEPARTED,
        ArrivalState.PREV_ENTERING,
        ArrivalState.PREV_ARRIVED,
        -> "전역"
        ArrivalState.RUNNING -> "운행"
        ArrivalState.UNKNOWN -> "--"
    }

    // ── 문구 고르기 ─────────────────────────────────────────────────────────

    /**
     * 여러 문구 중 하나를 **항상 같은 방식으로** 고릅니다.
     *
     * 난수를 쓰면 화면을 다시 그릴 때마다 문구가 바뀌어서, 읽는 도중에 글자가 변합니다.
     * 씨앗과 등급을 섞은 해시를 쓰면 같은 상황에서 항상 같은 문구가 나오고,
     * 등급이 바뀌면 문구도 함께 바뀝니다.
     */
    private fun <T : Enum<T>> pick(variants: List<String>, seed: String, discriminator: T): String {
        if (variants.isEmpty()) return ""
        if (variants.size == 1) return variants[0]

        // Int.MIN_VALUE 는 절댓값을 취해도 음수로 남습니다(2의 보수).
        // 그대로 나머지 연산을 하면 음수 인덱스가 나와 앱이 죽습니다.
        // Long 으로 올린 뒤 절댓값을 취하면 그 함정이 없습니다.
        val hash = (seed + discriminator.name).hashCode().toLong().absoluteValue
        return variants[(hash % variants.size).toInt()]
    }

    private val PLAYFUL_TITLES: Map<CrowdLevel, List<String>> = mapOf(
        CrowdLevel.EASY to listOf(
            "이 정도면 천국철",
            "오늘은 운이 좋네요",
            "이런 날도 있군요",
        ),
        CrowdLevel.BUSY to listOf(
            "적당히 붐빕니다",
            "그럭저럭 탈 만해요",
            "딱 평소만큼",
        ),
        CrowdLevel.BAD to listOf(
            "각오는 하고 타세요",
            "가방은 앞으로 메세요",
            "슬슬 지옥철 냄새가",
        ),
        CrowdLevel.HELL to listOf(
            "지옥문이 열렸습니다",
            "오늘도 지옥철입니다",
            "영혼까지 압축됩니다",
        ),
        CrowdLevel.WTF to listOf(
            "이건 좀 아닙니다",
            "한 대 보내시는 걸 권합니다",
            "인간 젤리 주의보",
        ),
    )

    private val PLAYFUL_SUBTITLES: Map<CrowdLevel, List<String>> = mapOf(
        CrowdLevel.EASY to listOf(
            "앉아서 갈 수도 있겠는데요",
            "창밖 볼 여유까지 있습니다",
            "이 시간대를 기억해 두세요",
        ),
        CrowdLevel.BUSY to listOf(
            "서서 가면 편합니다",
            "손잡이는 잡으세요",
            "가방 둘 자리는 있습니다",
        ),
        CrowdLevel.BAD to listOf(
            "옆 사람과 닿습니다",
            "내릴 역 한 정거장 전에 움직이세요",
            "이어폰은 미리 꽂아 두세요",
        ),
        CrowdLevel.HELL to listOf(
            "내릴 때 미리 움직이세요",
            "휴대폰 꺼낼 생각은 접으세요",
            "숨은 들이쉬고 타세요",
        ),
        CrowdLevel.WTF to listOf(
            "다음 열차를 기다리는 편이 낫습니다",
            "타는 것보다 기다리는 게 빠를 수도",
            "오늘은 그냥 걸어 볼까요",
        ),
    )

    private val RIDE_QUIPS = listOf(
        "지금이 그나마 낫습니다",
        "고민할 시간에 타세요",
        "이만하면 괜찮습니다",
    )

    private val WAIT_QUIPS = listOf(
        "조금만 참으면 편해집니다",
        "한 대 보낼 값어치가 있습니다",
        "지금 타면 후회합니다",
    )
}
