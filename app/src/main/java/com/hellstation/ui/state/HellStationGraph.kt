package com.hellstation.ui.state

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import com.hellstation.data.di.HellStationContainer
import com.hellstation.data.di.HellStationFacade
import com.hellstation.ui.sample.SampleMetro

/**
 * 앱에서 [HellStationContainer]를 만드는 **유일한 자리**.
 *
 * ## 왜 여기 있나
 *
 * 컨테이너는 `Context`가 필요해서 순수 domain 계층에 둘 수 없고,
 * 여러 개 만들면 역 목록 캐시가 따로 놀아 API를 중복 호출합니다.
 * 그래서 앱 전체에서 하나만 만들어 들고 있습니다.
 *
 * ## 씨앗 역 목록을 넘기는 이유
 *
 * 실시간 인증키가 없으면 서울 열린데이터광장이 **한 번에 5건만** 돌려줍니다
 * (docs/api-validation.md 에서 실측 확인). 역 다섯 개짜리 지도는 쓸모가 없습니다.
 *
 * 그래서 화면이 이미 갖고 있는 역 목록([SampleMetro])을 씨앗으로 넘깁니다.
 * 인증키가 없는 동안에도 **혼잡도 엔진이 306개 역에 대해 실제로 계산**합니다.
 * 키가 생기면 실제 목록이 씨앗을 밀어냅니다.
 */
object HellStationGraph {

    @Volatile
    private var container: HellStationContainer? = null

    fun facade(context: Context): HellStationFacade = container(context).facade

    private fun container(context: Context): HellStationContainer =
        container ?: synchronized(this) {
            container ?: HellStationContainer
                .create(
                    context = context.applicationContext,
                    seedStations = SampleMetro.layout.stations,
                )
                .also { container = it }
        }
}

/**
 * 화면에서 창구를 꺼내는 짧은 길.
 *
 * **미리보기에서는 null을 돌려줍니다.** 미리보기는 Android 런타임이 없어서
 * DataStore나 네트워크를 건드리면 그대로 실패합니다. null이면 화면이
 * 임시 데이터로 그려지므로 디자인 확인에는 지장이 없습니다.
 */
@Composable
fun rememberFacade(): HellStationFacade? {
    if (LocalInspectionMode.current) return null
    val context = LocalContext.current.applicationContext
    return remember(context) { HellStationGraph.facade(context) }
}
