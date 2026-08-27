package com.hellstation.data.remote

import com.hellstation.data.remote.dto.ArrivalDto
import com.hellstation.data.remote.dto.ArrivalResponse
import com.hellstation.data.remote.dto.StationLineInfoDto
import com.hellstation.data.remote.dto.StationLineInfoResponse
import com.hellstation.data.remote.dto.StationMasterDto
import com.hellstation.data.remote.dto.StationMasterResponse
import com.hellstation.domain.model.UnavailableReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * 서울 열린데이터광장 두 API를 감싼 클라이언트.
 *
 * 엔드포인트와 응답 구조는 docs/api-validation.md에서 실제 호출로 검증된 것입니다.
 *
 * ## 샘플 키의 제약 (실측으로 확인)
 *
 * 인증키가 `"sample"`이면 서버가 **한 번에 최대 5건**만 돌려줍니다.
 * 6건 이상을 요청하면 이런 응답이 옵니다.
 *
 * ```
 * <RESULT><CODE>ERROR-335</CODE>
 *   <MESSAGE>샘플데이터(샘플키:sample) 는 한번에 최대 5건을 넘을 수 없습니다.</MESSAGE>
 * </RESULT>
 * ```
 *
 * 실시간 도착정보 쪽은 같은 상황에서 `ERROR-336`("1000건을 넘을 수 없습니다")이라는
 * **엉뚱한 메시지**를 주므로 메시지를 그대로 믿으면 안 됩니다.
 *
 * 그래서 [pageSize]는 키가 sample이면 5로 줄어듭니다. 서울역의 도착 정보는 총 19건인데
 * 샘플 키로는 그중 5건만 볼 수 있습니다 — 특정 노선·방향이 통째로 빠질 수 있다는 뜻입니다.
 * 이건 정상이며, 그 경우 혼잡도는 통계 기준선으로 대체됩니다.
 *
 * ## 또 하나의 함정
 *
 * `/json/`으로 요청해도 **에러일 때는 XML이 옵니다.** JSON 파서에 그대로 넣으면 터집니다.
 * [parseOrFailure]가 이 경우를 걸러냅니다.
 */
class SeoulSubwayApi(
    private val client: OkHttpClient,
    private val json: Json,
    private val realtimeBaseUrl: String,
    private val openApiBaseUrl: String,
    private val realtimeKey: String,
    private val openApiKey: String,
) {

    private val usingSampleRealtimeKey: Boolean get() = realtimeKey.isSampleKey()
    private val usingSampleOpenApiKey: Boolean get() = openApiKey.isSampleKey()

    /** 실시간 도착정보를 한 번에 몇 건까지 받을 수 있나. */
    val arrivalPageSize: Int get() = if (usingSampleRealtimeKey) SAMPLE_MAX_ROWS else DEFAULT_PAGE_SIZE

    /** 역 목록을 한 번에 몇 건까지 받을 수 있나. */
    val catalogPageSize: Int get() = if (usingSampleOpenApiKey) SAMPLE_MAX_ROWS else DEFAULT_PAGE_SIZE

    /**
     * 샘플 키로 동작 중인가. 화면에 "키를 넣으면 전체 역을 볼 수 있습니다" 안내를 띄울 때 씁니다.
     */
    val isSampleMode: Boolean get() = usingSampleRealtimeKey || usingSampleOpenApiKey

    // ── 실시간 도착정보 ──────────────────────────────────────────────────────

    /**
     * 역명으로 도착 정보를 받습니다.
     *
     * @param stationName **"역" 접미사를 뗀 이름**("서울", "강남"). docs/api-validation.md 1번
     */
    suspend fun arrivals(stationName: String): ApiResult<List<ArrivalDto>> {
        val url = realtimeBaseUrl.toHttpUrlOrNull()
            ?.newBuilder()
            ?.addPathSegment(realtimeKey)
            ?.addPathSegment("json")
            ?.addPathSegment("realtimeStationArrival")
            ?.addPathSegment("0")
            ?.addPathSegment(arrivalPageSize.toString())
            ?.addPathSegment(stationName)
            ?.build()
            ?: return ApiResult.Failure(
                UnavailableReason.NO_DATA,
                "실시간 API 주소가 잘못되었습니다: $realtimeBaseUrl",
            )

        val body = when (val fetched = fetch(url)) {
            is ApiResult.Failure -> return fetched
            is ApiResult.Success -> fetched.value
        }
        val response = when (val parsed = parseOrFailure<ArrivalResponse>(body)) {
            is ApiResult.Failure -> return parsed
            is ApiResult.Success -> parsed.value
        }

        response.realtimeArrivalList?.let { return ApiResult.Success(it) }

        val reason = SeoulApiCode.toReason(response.resultCode)
            ?: return ApiResult.Success(emptyList())
        return ApiResult.Failure(
            reason,
            "도착정보 조회 실패: ${response.resultCode} ${response.resultMessage}",
        )
    }

    // ── 역 목록 ─────────────────────────────────────────────────────────────

    /** 역 좌표 목록. 총 784건이며 [catalogPageSize] 단위로 나눠 받습니다. */
    suspend fun stationMaster(start: Int, end: Int): ApiResult<List<StationMasterDto>> {
        val url = openApiUrl("subwayStationMaster", start, end)
            ?: return ApiResult.Failure(
                UnavailableReason.NO_DATA,
                "열린데이터광장 주소가 잘못되었습니다: $openApiBaseUrl",
            )

        return when (val body = fetch(url)) {
            is ApiResult.Failure -> body
            is ApiResult.Success -> when (val parsed = parseOrFailure<StationMasterResponse>(body.value)) {
                is ApiResult.Failure -> parsed
                is ApiResult.Success -> {
                    val response = parsed.value
                    val rows = response.subwayStationMaster?.row
                    val code = response.subwayStationMaster?.result?.code ?: response.result?.code
                    rowsOrFailure(rows, code, "역 좌표")
                }
            }
        }
    }

    /** 노선별 역 정보. 총 799건입니다. */
    suspend fun stationLineInfo(start: Int, end: Int): ApiResult<List<StationLineInfoDto>> {
        val url = openApiUrl("SearchSTNBySubwayLineInfo", start, end)
            ?: return ApiResult.Failure(
                UnavailableReason.NO_DATA,
                "열린데이터광장 주소가 잘못되었습니다: $openApiBaseUrl",
            )

        return when (val body = fetch(url)) {
            is ApiResult.Failure -> body
            is ApiResult.Success -> when (val parsed = parseOrFailure<StationLineInfoResponse>(body.value)) {
                is ApiResult.Failure -> parsed
                is ApiResult.Success -> {
                    val response = parsed.value
                    val rows = response.body?.row
                    val code = response.body?.result?.code ?: response.result?.code
                    rowsOrFailure(rows, code, "역 정보")
                }
            }
        }
    }

    // ── 내부 ────────────────────────────────────────────────────────────────

    private fun openApiUrl(service: String, start: Int, end: Int): HttpUrl? =
        openApiBaseUrl.toHttpUrlOrNull()
            ?.newBuilder()
            ?.addPathSegment(openApiKey)
            ?.addPathSegment("json")
            ?.addPathSegment(service)
            ?.addPathSegment(start.toString())
            ?.addPathSegment(end.toString())
            ?.build()

    private fun <T> rowsOrFailure(
        rows: List<T>?,
        code: String?,
        what: String,
    ): ApiResult<List<T>> = when {
        rows != null -> ApiResult.Success(rows)
        else -> {
            val reason = SeoulApiCode.toReason(code) ?: UnavailableReason.NO_DATA
            ApiResult.Failure(reason, "$what 조회 실패: $code")
        }
    }

    private suspend fun fetch(url: HttpUrl): ApiResult<String> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).get().build()
        try {
            client.newCall(request).execute().use { response ->
                // OkHttp 5 부터 Response.body 가 non-null 입니다. 안전 호출이 필요 없습니다.
                val body = response.body.string()
                if (!response.isSuccessful) {
                    // 서울시 API는 실패해도 200을 주는 경우가 많아 여기까지 오는 일은 드뭅니다.
                    return@withContext ApiResult.Failure(
                        UnavailableReason.NETWORK,
                        "HTTP ${response.code}",
                    )
                }
                ApiResult.Success(body)
            }
        } catch (e: IOException) {
            // 지하철 안에서는 자주 일어납니다. 예외가 아니라 정상 상황으로 다룹니다.
            ApiResult.Failure(UnavailableReason.NETWORK, "통신 실패: ${e.message}", e)
        }
    }

    /**
     * JSON으로 파싱하되, XML 에러 응답이면 그 안의 코드를 읽어 실패로 바꿉니다.
     * `/json/`으로 요청해도 에러일 때는 XML이 옵니다.
     */
    private inline fun <reified T> parseOrFailure(body: String): ApiResult<T> {
        val trimmed = body.trimStart()
        if (trimmed.startsWith("<")) {
            val code = XML_CODE.find(trimmed)?.groupValues?.getOrNull(1)
            val reason = when {
                code == null -> UnavailableReason.NO_DATA
                code == SAMPLE_LIMIT_CODE -> UnavailableReason.NO_KEY
                else -> SeoulApiCode.toReason(code) ?: UnavailableReason.NO_DATA
            }
            return ApiResult.Failure(reason, "XML 에러 응답: $code")
        }
        return try {
            ApiResult.Success(json.decodeFromString<T>(body))
        } catch (e: Exception) {
            ApiResult.Failure(
                UnavailableReason.NO_DATA,
                "응답을 해석하지 못했습니다: ${e.message}",
                e,
            )
        }
    }

    private fun String.isSampleKey(): Boolean = isBlank() || equals(SAMPLE_KEY, ignoreCase = true)

    companion object {
        const val SAMPLE_KEY = "sample"

        /** 샘플 키의 한 번 요청 상한. 서버가 강제합니다. */
        const val SAMPLE_MAX_ROWS = 5

        /** 정식 키일 때의 페이지 크기. 서버 상한은 1000건입니다. */
        const val DEFAULT_PAGE_SIZE = 1000

        /** 샘플 키 초과 요청 시 오는 코드 */
        const val SAMPLE_LIMIT_CODE = "ERROR-335"

        private val XML_CODE = Regex("""<CODE>([^<]+)</CODE>""")

        /** 기본 설정으로 만든 JSON 파서. API가 모르는 필드를 추가해도 터지지 않습니다. */
        fun defaultJson(): Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
            explicitNulls = false
        }
    }
}
