package com.hellstation.data.di

/**
 * # 화면에 실제 데이터를 붙이는 방법
 *
 * 데이터 쪽 준비는 끝났습니다. 남은 것은 **화면이 임시 데이터 대신 [HellStationFacade]를
 * 부르게 하는 것**뿐인데, `ui` 폴더는 화면 담당 소유라 데이터·기능 담당이 고칠 수 없습니다.
 *
 * (참고: Kotlin 은 블록 주석이 중첩됩니다. 주석 안에 `ui` 뒤로 별 두 개를 붙인 경로 표기를
 * 쓰면 그게 새 주석을 여는 것으로 읽혀서 파일 전체가 깨집니다. 여기서 한 번 겪었습니다.)
 * 그래서 바꿔야 할 곳과 바꿀 내용을 여기 적어 둡니다.
 *
 * 이 파일은 **문서 전용**입니다. 실행되는 코드가 없습니다.
 *
 * ---
 *
 * ## 지금 임시 데이터를 부르는 곳 (5군데)
 *
 * ```
 * ui/heatmap/HeatmapScreen.kt:89    remember { SampleMetro.layout }
 * ui/heatmap/HeatmapScreen.kt:98    SampleCrowd.snapshotAt(now, selectedSlot)
 * ui/heatmap/HeatmapScreen.kt:106   SampleCrowd.snapshotAt(now, slot)      (하루 곡선)
 * ui/heatmap/HeatmapScreen.kt:165   SampleCrowd.boardFor(...)
 * ui/search/SearchScreen.kt:91,151  SampleMetro.layout / SampleCrowd.crowdFor(...)
 * ui/result/ResultScreen.kt:83      SampleCrowd.boardFor(...)
 * ui/HellStationRoutes.kt:174       SampleMetro.layout.stations (역 찾기)
 * ```
 *
 * ## 1단계 — 컨테이너를 한 번 만들기
 *
 * `Application` 이나 `MainActivity` 에서 **딱 하나만** 만듭니다.
 *
 * ```kotlin
 * val container = HellStationContainer.create(
 *     context = applicationContext,
 *     // 인증키가 없는 동안 쓸 역 목록. 화면이 이미 갖고 있는 것을 그대로 넘기면 됩니다.
 *     seedStations = SampleMetro.layout.stations,
 * )
 * ```
 *
 * `seedStations` 를 넘기는 것이 핵심입니다. 인증키가 없으면 서울 열린데이터광장이
 * **한 번에 5건만** 주기 때문에(docs/api-validation.md), 이걸 안 넘기면 지도에 역이
 * 다섯 개만 뜹니다. 넘기면 역 300개짜리 지도에 **실제 계산된** 혼잡도가 칠해집니다.
 *
 * ## 2단계 — ViewModel 하나
 *
 * 화면이 [HellStationFacade]를 직접 부르면 회전할 때마다 다시 계산합니다.
 *
 * ```kotlin
 * class HeatmapViewModel(private val facade: HellStationFacade) : ViewModel() {
 *
 *     private val _state = MutableStateFlow(HeatmapState())
 *     val state: StateFlow<HeatmapState> = _state.asStateFlow()
 *
 *     init { select(facade.nowSlot()) }   // 또는 TimeSlot.of(...)
 *
 *     fun select(slot: TimeSlot) = viewModelScope.launch {
 *         val at = facade.instantFor(slot)
 *         _state.update {
 *             it.copy(
 *                 slot = slot,
 *                 snapshot = facade.heatmapAt(at),          // 캐시가 있어 슬라이더가 끊기지 않습니다
 *                 dayLevels = facade.dayCurve(at),
 *                 warning = facade.status().warning,
 *             )
 *         }
 *     }
 *
 *     fun openStation(id: StationId, direction: Direction) = viewModelScope.launch {
 *         _state.update { it.copy(board = facade.board(id, direction)) }
 *     }
 * }
 * ```
 *
 * ## 3단계 — 화면에서 바꿀 곳
 *
 * ### HeatmapScreen
 *
 * ```kotlin
 * // 전
 * val layout = remember { SampleMetro.layout }
 * val snapshot = remember(selectedSlot) { SampleCrowd.snapshotAt(now, selectedSlot) }
 * val dayLevels = remember(layout) { ... 39번 계산 ... }
 *
 * // 후 — 계산은 ViewModel 이 이미 해 놓았습니다
 * val state by viewModel.state.collectAsStateWithLifecycle()
 * val snapshot = state.snapshot
 * val dayLevels = state.dayLevels
 * ```
 *
 * **지도 배치([com.hellstation.ui.map.MetroLayout])는 그대로 두세요.** 실제 API 좌표를
 * 쓰려면 실시간 인증키가 필요한데, 그전까지는 손으로 잡은 도식 배치가 더 잘 보입니다.
 * 혼잡도만 실제 값으로 바뀌면 됩니다 — 둘은 `StationId` 로 이어집니다.
 *
 * 키가 생겨서 실제 좌표를 쓰게 되면 그때 `MetroLayout.fromGeo(facade.mapStations(), ...)`
 * 로 바꾸면 됩니다.
 *
 * ### 낮은 신뢰도 안내 (완료조건 3)
 *
 * 두 군데에서 나옵니다. **둘 다 이미 화면에 있습니다.**
 *
 * 1. 역·열차 단위 — `ConfidenceNote(crowd)` 가 [com.hellstation.domain.model.CrowdIndex.confidence]
 *    를 보고 알아서 뜹니다. 실제 데이터를 붙이면 자동으로 동작합니다.
 * 2. 앱 전체 — `facade.status().warning` 을 지도 위쪽 막대에 한 줄로 띄우세요.
 *    인증키가 없거나 실측 통계 파일이 없을 때 그 사실을 알려 줍니다.
 *
 * ### SearchScreen · ResultScreen
 *
 * ```kotlin
 * SampleMetro.layout.stations -> facade.search(query) / facade.popularStations()
 * SampleCrowd.crowdFor(...)   -> facade.crowdAt(station.id, direction, at)
 * SampleCrowd.boardFor(...)   -> facade.board(station.id, direction, at)   // Loadable 로 옵니다
 * ```
 *
 * `board(...)` 는 [com.hellstation.domain.model.Loadable] 을 돌려줍니다.
 * `Loadable.Ready.isFallback` 이 true 면 실시간을 못 받아 통계로 채운 것입니다.
 *
 * ## 4단계 — 임시 데이터 지우기
 *
 * 위를 다 바꾸면 `ui/sample/SampleCrowd.kt` 는 미리보기에서만 쓰입니다.
 * `SampleMetro` 는 지도 배치와 씨앗 목록으로 계속 필요하니 **지우지 마세요.**
 *
 * ---
 *
 * ## 숫자가 안 보이는 이유 (미리 알아 두세요)
 *
 * 실측 통계 CSV가 없으면 모든 값이 `APPROXIMATED` 이고, 그러면 신뢰도가
 * [com.hellstation.domain.model.Confidence.LOW] 로 묶입니다
 * (docs/crowding-levels.md 3절). 그리고 `CrowdIndex.showsPercent` 가 false 라서
 * **화면이 % 숫자 대신 등급 이름을 보여줍니다.**
 *
 * 색과 등급은 시간대에 따라 제대로 바뀌지만, 숫자는 안 나옵니다. 일부러 그렇게 만든 규칙입니다 —
 * 어림값에 정확한 숫자를 붙이면 사용자가 실측으로 오해합니다.
 *
 * 숫자를 보이게 하려면 둘 중 하나입니다.
 *
 * 1. **실측 CSV를 `app/src/main/assets/seoul_metro_congestion.csv` 에 넣기** (권장).
 *    그러면 1~8호선이 `MEASURED` 가 되고 신뢰도가 `MEDIUM` 으로 올라가 숫자가 나옵니다.
 * 2. 화면에서 `showsPercent` 가 false 일 때도 "약 128%" 처럼 **작게** 보여주기.
 *    docs/crowding-levels.md 4절이 "숨기거나 작게 처리"라고 했으니 이것도 규칙에 맞습니다.
 *    다만 이건 화면 담당이 정할 문제라 데이터 쪽에서 바꾸지 않았습니다.
 */
object UiConnectionGuide
