package com.autoomstudio.mp3studio.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.autoomstudio.mp3studio.R

val DmSansFamily = FontFamily(
    Font(R.font.dm_sans_regular, FontWeight.Normal),
    Font(R.font.dm_sans_medium, FontWeight.Medium),
    Font(R.font.dm_sans_semibold, FontWeight.SemiBold),
    Font(R.font.dm_sans_bold, FontWeight.Bold),
)

val OutfitFamily = FontFamily(
    Font(R.font.outfit_regular, FontWeight.Normal),
    Font(R.font.outfit_medium, FontWeight.Medium),
    Font(R.font.outfit_semibold, FontWeight.SemiBold),
    Font(R.font.outfit_bold, FontWeight.Bold),
    Font(R.font.outfit_extrabold, FontWeight.ExtraBold),
)

private val Base = Typography()

val MPlayTypography = Typography(
    displayLarge = Base.displayLarge.copy(fontFamily = OutfitFamily),
    displayMedium = Base.displayMedium.copy(fontFamily = OutfitFamily),
    displaySmall = Base.displaySmall.copy(fontFamily = OutfitFamily),
    headlineLarge = Base.headlineLarge.copy(fontFamily = OutfitFamily, fontWeight = FontWeight.Bold),
    headlineMedium = Base.headlineMedium.copy(fontFamily = OutfitFamily, fontWeight = FontWeight.Bold),
    headlineSmall = Base.headlineSmall.copy(fontFamily = OutfitFamily, fontWeight = FontWeight.Bold),
    titleLarge = Base.titleLarge.copy(fontFamily = DmSansFamily, fontWeight = FontWeight.Bold),
    titleMedium = Base.titleMedium.copy(fontFamily = DmSansFamily, fontWeight = FontWeight.SemiBold),
    titleSmall = Base.titleSmall.copy(fontFamily = DmSansFamily, fontWeight = FontWeight.SemiBold),
    bodyLarge = Base.bodyLarge.copy(fontFamily = DmSansFamily),
    bodyMedium = Base.bodyMedium.copy(fontFamily = DmSansFamily),
    bodySmall = Base.bodySmall.copy(fontFamily = DmSansFamily),
    labelLarge = Base.labelLarge.copy(fontFamily = DmSansFamily, fontWeight = FontWeight.SemiBold),
    labelMedium = Base.labelMedium.copy(fontFamily = DmSansFamily, fontWeight = FontWeight.Medium),
    labelSmall = Base.labelSmall.copy(fontFamily = DmSansFamily),
)

/** Style for the "MP3 Studio" wordmark in the top bar. */
val WordmarkStyle = TextStyle(
    fontFamily = OutfitFamily,
    fontSize = 28.sp,
    lineHeight = 34.sp,
    fontWeight = FontWeight.ExtraBold,
    letterSpacing = (-0.5).sp,
)
