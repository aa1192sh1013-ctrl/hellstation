package com.hellstation.data.di

import android.content.Context
import com.hellstation.BuildConfig
import com.hellstation.data.local.baseline.ApproximateBaselineSource
import com.hellstation.data.local.baseline.ChainedBaselineSource
import com.hellstation.data.local.baseline.CsvBaselineSource
import com.hellstation.data.local.cache.StationCatalogStore
import com.hellstation.data.remote.SeoulSubwayApi
import com.hellstation.data.remote.mapper.ArrivalMapper
import com.hellstation.data.repository.ArrivalRepositoryImpl
import com.hellstation.data.repository.CrowdRepositoryImpl
import com.hellstation.data.repository.SubwayNetworkRepositoryImpl
import com.hellstation.domain.model.Station
import com.hellstation.domain.repository.ArrivalRepository
import com.hellstation.domain.repository.BaselineSource
import com.hellstation.domain.repository.CrowdRepository
import com.hellstation.domain.repository.SubwayNetworkRepository
import com.hellstation.domain.usecase.CrowdEstimator
import com.hellstation.domain.usecase.DecideRouteUseCase
import com.hellstation.domain.usecase.ForecastCrowdUseCase
import com.hellstation.domain.usecase.GetStationBoardUseCase
import com.hellstation.domain.usecase.RideOrWaitDecider
import com.hellstation.domain.usecase.ServiceCalendar
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 앱이 쓰는 것들을 한 번에 만들어 들고 있습니다.
 *
 * Hilt 같은 DI 라이브러리를 쓰지 않은 이유: 지금 규모에서는 이 파일 하나가 더 읽기 쉽고,
 * 화면 담당이 ViewModel에서 바로 꺼내 쓰기도 간단합니다. 커지면 그때 옮기면 됩니다.
 *
 * ## 화면 담당에게 — 쓰는 법
 *
 * **[facade] 하나만 쓰면 됩니다.** 저장소를 직접 꺼내 조합하지 마세요.
 *
 * ```kotlin
 * // Application 이나 MainActivity 에서 한 번만
 * val container = HellStationContainer.create(
 *     context = applicationContext,
 *     seedStations = SampleMetro.layout.stations,   // 인증키 없을 때 쓸 역 목록
 * )
 *
 * // ViewModel 안에서
 * val snapshot = container.facade.heatmapAt(now)                  // 지도 한 장
 * val curve    = container.facade.dayCurve(now)                   // Time Slider 배경
 * val board    = container.facade.board(stationId, Direction.UP)  // 역 시트
 * val advice   = container.facade.route(originId, destinationId)  // 경로 결과
 * val warning  = container.facade.status().warning                // 낮은 신뢰도 안내
 * ```
 *
 * **하나만 만들어 쓰세요.** 여러 개 만들면 역 목록 캐시가 따로 놀아 API를 중복 호출합니다.
 *
 * @param seedStations 인증키가 없어 API가 역 목록을 제대로 못 줄 때 대신 쓸 목록.
 *   비워 두면 인증키 없이는 지도가 거의 비어 보입니다
 *   (샘플 키는 한 번에 5건 제한 — docs/api-validation.md).
 */
class HellStationContainer private constructor(
    private val api: SeoulSubwayApi,
    private val networkRepositoryImpl: SubwayNetworkRepositoryImpl,
    val arrivalRepository: ArrivalRepository,
    val baselineSource: BaselineSource,
    val crowdRepository: CrowdRepository,
    val facade: HellStationFacade,
) {

    val networkRepository: SubwayNetworkRepository get() = networkRepositoryImpl

    /**
     * 샘플 인증키로 동작 중인가.
     *
     * true면 실시간 도착정보가 서울역에만 붙고 역 목록도 5건만 옵니다.
     * 자세한 안내 문구는 `facade.status().warning` 을 쓰세요.
     */
    val isSampleMode: Boolean get() = api.isSampleMode

    /** 개발 중 상태 확인용. 로그에 찍어 보세요. */
    suspend fun diagnostics(): String = buildString {
        val status = facade.status()
        appendLine("── HellStation 상태 ──")
        appendLine("샘플 키 모드: ${status.usingSampleKey}")
        appendLine("씨앗 역 목록 사용: ${status.usingSeedStations}")
        appendLine("실측 통계(CSV) 있음: ${status.hasMeasuredBaseline}")
        appendLine("역 개수: ${status.stationCount}")
        appendLine("좌표 있는 역: ${networkRepositoryImpl.catalog.mappable.size}")
        appendLine("안내 문구: ${status.warning ?: "(없음)"}")
    }

    companion object {

        fun create(
            context: Context,
            seedStations: List<Station> = emptyList(),
        ): HellStationContainer {
            val appContext = context.applicationContext

            val client = OkHttpClient.Builder()
                // 지하철 안에서는 신호가 약합니다. 오래 기다리느니 빨리 포기하고
                // 통계 기준선으로 넘어가는 편이 사용자 경험이 낫습니다.
                .connectTimeout(6, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .callTimeout(12, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()

            val api = SeoulSubwayApi(
                client = client,
                json = SeoulSubwayApi.defaultJson(),
                realtimeBaseUrl = BuildConfig.SEOUL_REALTIME_BASE_URL,
                openApiBaseUrl = BuildConfig.SEOUL_OPENAPI_BASE_URL,
                realtimeKey = BuildConfig.SEOUL_REALTIME_SUBWAY_KEY,
                openApiKey = BuildConfig.SEOUL_OPENAPI_KEY,
            )

            val networkRepository = SubwayNetworkRepositoryImpl(
                api = api,
                store = StationCatalogStore(appContext),
                seedStations = seedStations,
                // 역 순위를 매길 때 노선 성향도 함께 봅니다.
                // 이 표는 어림 통계 쪽 규칙이므로 거기서 가져옵니다.
                lineWeight = ApproximateBaselineSource::lineWeightOf,
            )

            val arrivalMapper = ArrivalMapper { realtimeId, normalizedName ->
                networkRepository.catalog.resolve(realtimeId, normalizedName)
            }
            val arrivalRepository = ArrivalRepositoryImpl(api, arrivalMapper)

            val baselineSource = ChainedBaselineSource(
                listOf(
                    CsvBaselineSource(
                        assets = appContext.assets,
                        stationNameOf = { id -> networkRepository.station(id)?.name },
                    ),
                    // 실측 통계가 없는 역은 역의 특징으로 어림합니다.
                    // 이 연결이 없으면 모든 역이 같은 값이 되어 지도가 단색이 됩니다.
                    ApproximateBaselineSource(
                        profileOf = { id -> networkRepository.profileOf(id) },
                    ),
                )
            )

            val estimator = CrowdEstimator()
            val calendar = ServiceCalendar()

            val crowdRepository = CrowdRepositoryImpl(
                network = networkRepository,
                arrivals = arrivalRepository,
                baseline = baselineSource,
                estimator = estimator,
                calendar = calendar,
            )

            val stationBoard = GetStationBoardUseCase(
                network = networkRepository,
                arrivals = arrivalRepository,
                crowd = crowdRepository,
                baseline = baselineSource,
                estimator = estimator,
                decider = RideOrWaitDecider(),
                calendar = calendar,
            )

            val facade = HellStationFacade(
                network = networkRepository,
                crowd = crowdRepository,
                baseline = baselineSource,
                stationBoard = stationBoard,
                decideRoute = DecideRouteUseCase(networkRepository, stationBoard),
                forecastUseCase = ForecastCrowdUseCase(crowdRepository, calendar),
                calendar = calendar,
                isSampleKey = api.isSampleMode,
            )

            return HellStationContainer(
                api = api,
                networkRepositoryImpl = networkRepository,
                arrivalRepository = arrivalRepository,
                baselineSource = baselineSource,
                crowdRepository = crowdRepository,
                facade = facade,
            )
        }
    }
}
