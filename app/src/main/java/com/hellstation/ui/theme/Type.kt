package com.hellstation.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/**
 * 글씨.
 *
 * ## 왜 커스텀 폰트 파일이 없나
 *
 * 폰트 파일을 저장소에 넣으려면 라이선스 확인과 바이너리 반입이 필요해서
 * 지금은 기기 기본 폰트만 씁니다. 대신 **굵기와 자간을 세게 밀어서** 키치한 느낌을 냅니다.
 *
 * 나중에 폰트를 넣기로 하면 `res/font/` 에 파일을 두고 여기 [Brand] 만 바꾸면 됩니다.
 * 다른 파일은 손댈 필요가 없습니다.
 */
private val Brand = FontFamily.Default

/**
 * 숫자 전용 — 지하철 역 전광판 느낌.
 *
 * 고정폭이라 혼잡도 숫자가 1초마다 바뀌어도 글자가 좌우로 흔들리지 않습니다.
 * 큰 숫자를 다루는 곳(HELL 지수, 남은 시간)에서만 쓰세요.
 */
val BoardFont = FontFamily.Monospace

val HellTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Brand,
        fontWeight = FontWeight.Black,
        fontSize = 52.sp,
        lineHeight = 56.sp,
        letterSpacing = (-1.5).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = Brand,
        fontWeight = FontWeight.Black,
        fontSize = 40.sp,
        lineHeight = 46.sp,
        letterSpacing = (-1).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = Brand,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Brand,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Brand,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Brand,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Brand,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Brand,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Brand,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Brand,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.4.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Brand,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.6.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Brand,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        letterSpacing = 0.8.sp,
    ),
)

/** Material 타이포에 없는, HellStation만 쓰는 글씨들. */
object HellTextStyles {

    /** 큰 숫자 하나만 보여줄 때. 혼잡도 %와 남은 시간. */
    val boardNumber = TextStyle(
        fontFamily = BoardFont,
        fontWeight = FontWeight.Black,
        fontSize = 56.sp,
        lineHeight = 58.sp,
        letterSpacing = (-2).sp,
        textAlign = TextAlign.Center,
    )

    /** 같은 숫자의 작은 버전. Bottom Sheet 안. */
    val boardNumberSmall = TextStyle(
        fontFamily = BoardFont,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.8).sp,
    )

    /** 열차 도착 시간처럼 줄줄이 늘어놓는 숫자. */
    val boardMono = TextStyle(
        fontFamily = BoardFont,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    )

    /** RIDE / WAIT 한 단어. 화면에서 가장 큰 글씨입니다. */
    val verdict = TextStyle(
        fontFamily = Brand,
        fontWeight = FontWeight.Black,
        fontSize = 64.sp,
        lineHeight = 66.sp,
        letterSpacing = (-3).sp,
    )

    /** 등급 배지 안의 글자. 아주 작고 굵고 자간이 넓습니다. */
    val badge = TextStyle(
        fontFamily = Brand,
        fontWeight = FontWeight.Black,
        fontSize = 11.sp,
        lineHeight = 13.sp,
        letterSpacing = 1.2.sp,
    )
}
