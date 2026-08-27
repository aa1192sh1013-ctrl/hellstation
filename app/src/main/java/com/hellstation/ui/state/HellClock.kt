package com.hellstation.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import java.time.Instant

/**
 * 흐르는 현재 시각과 데이터 갱신 박자.
 *
 * ## 왜 필요한가
 *
 * 화면 함수가 `now: Instant = Instant.now()`를 기본값으로 받으면 그 값은 **처음 그려질 때
 * 한 번만** 정해집니다. 그래서 "3분 후 도착"이 계속 3분 후로 멈춰 있었습니다.
 * 열차 도착 시각을 보여주는 앱에서 시계가 멈춰 있는 것은 기능 결함에 가깝습니다.
 *
 * ## 왜 박자가 둘인가
 *
 * 남은 시간은 **1초마다** 줄어야 사람이 믿습니다. 반대로 도착 정보까지 1초마다 다시 부르면
 * 지하철 API를 초당 한 번씩 두드리게 됩니다. 그래서 **화면에 흐르는 시계**([rememberNow])와
 * **데이터를 다시 부르는 박자**([rememberDataRefreshKey])를 나눠 두었습니다.
 *
 * ## 화면 밖에서는 멈춥니다
 *
 * 둘 다 `RESUMED` 동안에만 돕니다. 주머니 속에서 30초마다 API를 부르면 사용자에게 아무
 * 값어치도 없이 배터리와 데이터만 씁니다.
 */

/** 남은 시간 표시가 실제로 줄어들도록, 1초마다 새로 읽은 현재 시각. */
@Composable
fun rememberNow(intervalMillis: Long = CLOCK_TICK_MILLIS): Instant {
    var now by remember { mutableStateOf(Instant.now()) }
    OnResumedTicker(intervalMillis) { now = Instant.now() }
    return now
}

/**
 * [DATA_REFRESH_MILLIS]마다 1씩 오르는 값.
 *
 * 데이터를 다시 부르는 열쇠로 씁니다. 시각 자체를 열쇠로 쓰면 1초마다 다시 부르게 됩니다.
 */
@Composable
fun rememberDataRefreshKey(intervalMillis: Long = DATA_REFRESH_MILLIS): Int {
    var key by remember { mutableIntStateOf(0) }
    // 첫 조회는 화면 쪽에서 이미 합니다. 여기서 또 올리면 열자마자 두 번 부릅니다.
    OnResumedTicker(intervalMillis, fireImmediately = false) { key++ }
    return key
}

/**
 * 화면이 보이는 동안에만 도는 시계.
 *
 * 미리보기에는 생명주기가 없고 시간이 흐를 이유도 없으므로 아무것도 하지 않습니다.
 */
@Composable
private fun OnResumedTicker(
    intervalMillis: Long,
    fireImmediately: Boolean = true,
    onTick: () -> Unit,
) {
    if (LocalInspectionMode.current) return

    // onTick 이 매번 새 람다여도 시계를 다시 시작하지 않도록 최신 것만 붙잡아 둡니다.
    val tick by rememberUpdatedState(onTick)
    val owner = LocalLifecycleOwner.current

    LaunchedEffect(owner, intervalMillis, fireImmediately) {
        owner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            // 화면으로 돌아왔을 때 멈춰 있던 시각이 그대로 보이면 안 됩니다.
            if (fireImmediately) tick()
            while (true) {
                delay(intervalMillis)
                tick()
            }
        }
    }
}

/** 남은 시간을 1초 단위로 보여주므로 시계도 1초입니다. */
const val CLOCK_TICK_MILLIS = 1_000L

/**
 * 도착 정보를 다시 부르는 간격.
 *
 * 서울 지하철 배차가 가장 촘촘할 때가 2분 남짓이라, 30초면 한 열차를 놓치지 않고 따라갑니다.
 */
const val DATA_REFRESH_MILLIS = 30_000L
