package com.hellstation.ui.component

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import kotlin.math.sin

/**
 * 움직임에 대한 약속.
 *
 * ## 왜 애니메이션을 아끼는가
 *
 * 이 앱을 여는 사람은 대개 **플랫폼에 서서 열차가 들어오는 몇 초 안에** 봅니다.
 * 화면이 통통 튀면 읽는 시간을 빼앗깁니다. 그래서 움직임은 두 가지 목적에만 씁니다.
 *
 * 1. **값이 바뀌었다는 신호** — 색 전환, 숫자 전환
 * 2. **위험하다는 신호** — 지옥 구간의 느린 맥박
 *
 * 장식용 움직임은 넣지 않습니다.
 *
 * ## 움직임을 끈 사용자
 *
 * 안드로이드 설정에서 애니메이션을 끄면([Settings.Global.ANIMATOR_DURATION_SCALE] = 0)
 * 시스템 애니메이션이 모두 멈춥니다. 그런데 Compose의 `animate*AsState` 는 그 설정을
 * 자동으로 따르지 않아서, **끈 사람에게도 계속 움직입니다.**
 *
 * 멀미나 어지럼 때문에 끈 사람에게는 실제로 불편한 문제입니다.
 * 그래서 [LocalReduceMotion]을 만들어 두고, 켜져 있으면 반복 애니메이션을 멈춥니다.
 */
object HellMotion {

    /** 색·숫자가 바뀔 때. 눈에 띄되 기다리게 하지 않는 길이. */
    const val QUICK = 220

    /** 등급이 바뀔 때처럼 조금 더 크게 바뀔 때. */
    const val STANDARD = 340

    /** 시트가 올라올 때. */
    const val SHEET = 380

    /** 지옥 구간 맥박 한 번. 심장박동보다 느리게 — 급하게 뛰면 불안해집니다. */
    const val PULSE = 1900

    /** 부드럽게 시작해서 부드럽게 멈춤. */
    val Gentle: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** 값이 튀어나오는 느낌. 강조할 때만. */
    val Pop: Easing = CubicBezierEasing(0.34f, 1.2f, 0.64f, 1f)
}

/**
 * 사용자가 시스템에서 애니메이션을 껐는가.
 *
 * 기본값을 false로 두면 설정을 못 읽는 상황에서도 앱이 평소대로 동작합니다.
 */
val LocalReduceMotion = compositionLocalOf { false }

/**
 * 시스템 설정을 읽어 [LocalReduceMotion]에 넣을 값을 만듭니다.
 * 테마 아래에서 한 번만 부르고 [LocalReduceMotion]으로 내려보내세요.
 *
 * 미리보기에서는 항상 true입니다 — 미리보기가 계속 움직이면 스크린샷이 매번 달라집니다.
 */
@Composable
@ReadOnlyComposable
fun systemReduceMotion(): Boolean {
    if (LocalInspectionMode.current) return true
    val context = LocalContext.current
    return runCatching {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }.getOrDefault(false)
}

/**
 * 0에서 1 사이를 천천히 오가는 값. 지옥 구간을 숨쉬듯 밝혔다 어둡게 하는 데 씁니다.
 *
 * **깜빡이지 않고 숨쉽니다.** 빠른 깜빡임은 광과민성 발작을 유발할 수 있어서
 * (초당 3회 이상이 위험 구간입니다) [HellMotion.PULSE] 를 2초 가까이 길게 잡았습니다.
 * 초당 약 0.5회라 안전 범위 안입니다.
 *
 * @param enabled false면 0.5에 멈춰 있습니다 — 값이 튀지 않도록 중간값입니다
 */
@Composable
fun rememberPulse(enabled: Boolean = true): State<Float> {
    val reduceMotion = LocalReduceMotion.current
    val active = enabled && !reduceMotion

    // 조건에 따라 컴포저블 호출을 건너뛰면 상태가 꼬입니다.
    // 항상 만들어 두고 쓸지 말지만 뒤에서 정합니다.
    val transition = rememberInfiniteTransition(label = "pulse")
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(HellMotion.PULSE, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulsePhase",
    )

    // derivedStateOf 를 쓰는 이유: 이 값을 읽는 곳(지도 그리기)만 다시 그려집니다.
    // 화면 전체가 매 프레임 다시 구성되지 않습니다.
    return remember(active, phase) {
        derivedStateOf {
            if (active) (sin(phase.value) + 1f) / 2f else STILL
        }
    }
}

/** 움직임을 껐을 때 머무는 값. 가장 어둡지도 밝지도 않은 가운데입니다. */
private const val STILL = 0.5f
