package com.chabakon.app.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import com.chabakon.app.R

/**
 * 나눔스퀘어라운드 (네이버 나눔글꼴, SIL Open Font License 1.1 · 상업적 사용 가능)
 * 굵기: Light / Regular / Bold / ExtraBold. 나머지 굵기는 가장 가까운 파일로 연결한다.
 */
val NanumRound = FontFamily(
    Font(R.font.nanum_round_light, FontWeight.Light),
    Font(R.font.nanum_round_light, FontWeight.ExtraLight),
    Font(R.font.nanum_round_regular, FontWeight.Normal),
    Font(R.font.nanum_round_regular, FontWeight.Medium),
    Font(R.font.nanum_round_bold, FontWeight.SemiBold),
    Font(R.font.nanum_round_bold, FontWeight.Bold),
    Font(R.font.nanum_round_extrabold, FontWeight.ExtraBold),
    Font(R.font.nanum_round_extrabold, FontWeight.Black)
)

/** 기본 자간: 원티드 산스처럼 살짝 촘촘하게 */
val Tracking = (-0.02).em

/** 큰 제목·큰 숫자용 자간 */
val TitleTracking = (-0.035).em

private fun TextStyle.n() = copy(fontFamily = NanumRound, letterSpacing = Tracking)

fun chabakTypography(): Typography {
    val b = Typography()
    return Typography(
        displayLarge = b.displayLarge.n(),
        displayMedium = b.displayMedium.n(),
        displaySmall = b.displaySmall.n(),
        headlineLarge = b.headlineLarge.n(),
        headlineMedium = b.headlineMedium.n(),
        headlineSmall = b.headlineSmall.n(),
        titleLarge = b.titleLarge.n(),
        titleMedium = b.titleMedium.n(),
        titleSmall = b.titleSmall.n(),
        bodyLarge = b.bodyLarge.n(),
        bodyMedium = b.bodyMedium.n(),
        bodySmall = b.bodySmall.n(),
        labelLarge = b.labelLarge.n(),
        labelMedium = b.labelMedium.n(),
        labelSmall = b.labelSmall.n()
    )
}