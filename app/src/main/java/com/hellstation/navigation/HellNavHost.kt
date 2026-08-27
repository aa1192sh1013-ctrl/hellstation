package com.hellstation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.hellstation.ui.HeatmapRoute
import com.hellstation.ui.ResultRoute
import com.hellstation.ui.SearchRoute
import com.hellstation.ui.SettingsRoute
import com.hellstation.ui.SplashRoute
import com.hellstation.ui.StationDetailRoute

/**
 * 앱의 화면 이동 전체.
 *
 * ## 화면 안에서 navController 를 직접 부르지 마세요
 *
 * 화면은 콜백(`onXxx`)만 받고, 실제로 어디로 갈지는 여기서 정합니다.
 * 그래야 흐름을 바꿀 때 이 파일 하나만 고치면 됩니다.
 *
 * ## 경로 흐름
 *
 * ```
 * Splash ─▶ Heatmap ─┬─ [경로 검색] ─▶ Search(ORIGIN)
 *                    │                      │ 출발역 선택
 *                    │                      ▼
 *                    ├─ [역 → 여기서 출발] ─▶ Search(DESTINATION, 출발역 함께)
 *                    │                          │ 도착역 선택
 *                    │                          ▼
 *                    │                        Result(출발역, 도착역)
 *                    │
 *                    └─ [역 찾기] ─▶ Search(BROWSE) ─▶ StationDetail
 * ```
 *
 * 마지막 줄이 **지도를 대신하는 길**입니다. 지도는 Canvas에 그린 그림이라 스크린리더가
 * 역을 하나씩 읽지 못하므로, 목록으로 역을 찾아 여는 길이 따로 있어야 합니다.
 *
 * **출발역은 경로 인자로 계속 실려 다닙니다.** 예전에는 이걸 안 넘겨서
 * 도착역이 출발역 자리에 들어가고 목적지가 사라지는 버그가 있었습니다.
 */
@Composable
fun HellNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = HellDestination.Splash,
        modifier = modifier,
    ) {
        // ── Splash ──────────────────────────────────────────────────────────
        composable<HellDestination.Splash> {
            SplashRoute(
                onReady = {
                    navController.navigate(HellDestination.Heatmap) {
                        // 뒤로 가기로 Splash에 돌아오면 안 됩니다
                        popUpTo(HellDestination.Splash) { inclusive = true }
                    }
                },
            )
        }

        // ── Heatmap (홈) ────────────────────────────────────────────────────
        composable<HellDestination.Heatmap> {
            HeatmapRoute(
                // 역 시트의 "여기서 출발하기". 이 역을 출발역으로 들고 도착역 검색으로 갑니다.
                onStationClick = { lineCode, stationCode ->
                    navController.navigate(
                        HellDestination.Search(
                            purpose = SearchPurpose.DESTINATION.name,
                            originLineCode = lineCode,
                            originStationCode = stationCode,
                        )
                    )
                },
                onSearchClick = {
                    navController.navigate(HellDestination.Search(SearchPurpose.ORIGIN.name))
                },
                onSettingsClick = { navController.navigate(HellDestination.Settings) },
                // 지도를 대신하는 길. 스크린리더 사용자에게는 이것이 유일한 길입니다.
                onBrowseClick = {
                    navController.navigate(HellDestination.Search(SearchPurpose.BROWSE.name))
                },
            )
        }

        // ── 설정 ────────────────────────────────────────────────────────────
        composable<HellDestination.Settings> {
            SettingsRoute(onBack = { navController.popBackStack() })
        }

        // ── 검색 ────────────────────────────────────────────────────────────
        composable<HellDestination.Search> { entry ->
            val route = entry.toRoute<HellDestination.Search>()
            SearchRoute(
                purposeName = route.purpose,
                onStationSelected = { lineCode, stationCode ->
                    when (SearchPurpose.parse(route.purpose)) {
                        // 둘러보는 중이면 역 화면으로. 경로를 묻지 않습니다.
                        SearchPurpose.BROWSE ->
                            navController.navigate(
                                HellDestination.StationDetail(lineCode, stationCode)
                            )

                        // 출발역을 골랐습니다. **그 역을 들고** 도착역 검색으로 넘어갑니다.
                        SearchPurpose.ORIGIN ->
                            navController.navigate(
                                HellDestination.Search(
                                    purpose = SearchPurpose.DESTINATION.name,
                                    originLineCode = lineCode,
                                    originStationCode = stationCode,
                                )
                            )

                        SearchPurpose.DESTINATION -> {
                            if (route.hasOrigin) {
                                navController.navigate(
                                    HellDestination.Result(
                                        originLineCode = route.originLineCode,
                                        originStationCode = route.originStationCode,
                                        destinationLineCode = lineCode,
                                        destinationStationCode = stationCode,
                                    )
                                )
                            } else {
                                // 출발역 없이 도착역 화면에 들어온 경우(딥링크 등).
                                // 고른 역을 출발역으로 보고 결과를 보여줍니다 — 막다른 길을 만들지 않습니다.
                                navController.navigate(
                                    HellDestination.StationDetail(lineCode, stationCode)
                                )
                            }
                        }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        // ── 역 상세 ─────────────────────────────────────────────────────────
        composable<HellDestination.StationDetail> { entry ->
            val route = entry.toRoute<HellDestination.StationDetail>()
            StationDetailRoute(
                lineCode = route.lineCode,
                stationCode = route.stationCode,
                // 이 역을 출발역으로 들고 도착역 검색으로.
                onFindRouteClick = {
                    navController.navigate(
                        HellDestination.Search(
                            purpose = SearchPurpose.DESTINATION.name,
                            originLineCode = route.lineCode,
                            originStationCode = route.stationCode,
                        )
                    )
                },
                onBack = { navController.popBackStack() },
            )
        }

        // ── Ride or Wait 결과 ───────────────────────────────────────────────
        composable<HellDestination.Result> { entry ->
            val route = entry.toRoute<HellDestination.Result>()
            ResultRoute(
                originLineCode = route.originLineCode,
                originStationCode = route.originStationCode,
                destinationLineCode = route.destinationLineCode,
                destinationStationCode = route.destinationStationCode,
                onBackToMap = {
                    navController.navigate(HellDestination.Heatmap) {
                        popUpTo(HellDestination.Heatmap) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
    }
}
