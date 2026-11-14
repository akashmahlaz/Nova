package com.nova.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.nova.designsystem.R

/** Roboto Flex (SIL OFL), Latin + Latin Extended so Māori place names render correctly. */
val RobotoFlex = FontFamily(
    Font(R.font.roboto_flex_400, FontWeight.Normal),
    Font(R.font.roboto_flex_500, FontWeight.Medium),
    Font(R.font.roboto_flex_600, FontWeight.SemiBold),
    Font(R.font.roboto_flex_700, FontWeight.Bold),
    Font(R.font.roboto_flex_800, FontWeight.ExtraBold),
    Font(R.font.roboto_flex_900, FontWeight.Black),
)

private fun style(size: Int, line: Int, weight: FontWeight, tracking: TextUnit = 0.sp) = TextStyle(
    fontFamily = RobotoFlex,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    letterSpacing = tracking,
)

/**
 * Nova type scale, mapped onto Material roles. Sizes/weights come straight from the canvas:
 * display = hero numbers & onboarding headlines, headline = screen titles, title = rows/cards,
 * body = copy, label = buttons/chips. Emphasized variants bump the weight (M3 Expressive).
 */
internal val NovaTypography = Typography(
    displayLarge = style(72, 72, FontWeight.ExtraBold, (-1).sp),
    displayMedium = style(52, 56, FontWeight.ExtraBold, (-0.8).sp),
    displaySmall = style(44, 46, FontWeight.ExtraBold, (-0.6).sp),
    headlineLarge = style(36, 40, FontWeight.ExtraBold, (-0.5).sp),
    headlineMedium = style(30, 34, FontWeight.ExtraBold, (-0.4).sp),
    headlineSmall = style(26, 30, FontWeight.ExtraBold, (-0.3).sp),
    titleLarge = style(20, 26, FontWeight.Bold),
    titleMedium = style(17, 22, FontWeight.Bold),
    titleSmall = style(15, 20, FontWeight.Bold),
    bodyLarge = style(16, 24, FontWeight.Normal),
    bodyMedium = style(15, 21, FontWeight.Normal),
    bodySmall = style(13, 18, FontWeight.Normal),
    labelLarge = style(16, 20, FontWeight.ExtraBold),
    labelMedium = style(14, 18, FontWeight.Bold),
    labelSmall = style(12, 16, FontWeight.Bold),
    displayLargeEmphasized = style(72, 72, FontWeight.Black, (-1).sp),
    displayMediumEmphasized = style(52, 56, FontWeight.Black, (-0.8).sp),
    displaySmallEmphasized = style(44, 46, FontWeight.Black, (-0.6).sp),
    headlineLargeEmphasized = style(36, 40, FontWeight.Black, (-0.5).sp),
    headlineMediumEmphasized = style(30, 34, FontWeight.Black, (-0.4).sp),
    headlineSmallEmphasized = style(26, 30, FontWeight.Black, (-0.3).sp),
    titleLargeEmphasized = style(20, 26, FontWeight.ExtraBold),
    titleMediumEmphasized = style(17, 22, FontWeight.ExtraBold),
    titleSmallEmphasized = style(15, 20, FontWeight.ExtraBold),
    bodyLargeEmphasized = style(16, 24, FontWeight.SemiBold),
    bodyMediumEmphasized = style(15, 21, FontWeight.SemiBold),
    bodySmallEmphasized = style(13, 18, FontWeight.SemiBold),
    labelLargeEmphasized = style(16, 20, FontWeight.Black),
    labelMediumEmphasized = style(14, 18, FontWeight.ExtraBold),
    labelSmallEmphasized = style(12, 16, FontWeight.ExtraBold),
)
