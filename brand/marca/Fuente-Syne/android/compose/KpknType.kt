package com.kpkn.ui.theme // <- cambia por tu paquete

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.kpkn.R // <- cambia por tu paquete

val Syne = FontFamily(
    Font(R.font.syne_regular, FontWeight.Normal),
    Font(R.font.syne_medium, FontWeight.Medium),
    Font(R.font.syne_semibold, FontWeight.SemiBold),
    Font(R.font.syne_bold, FontWeight.Bold),
    Font(R.font.syne_extrabold, FontWeight.ExtraBold),
)

// Syne para titulares y números grandes; el texto largo se lee mejor en una sans neutra.
val KpknTypography = Typography(
    displayLarge = TextStyle(fontFamily = Syne, fontWeight = FontWeight.ExtraBold, fontSize = 57.sp, letterSpacing = (-0.01).em),
    displayMedium = TextStyle(fontFamily = Syne, fontWeight = FontWeight.ExtraBold, fontSize = 45.sp, letterSpacing = (-0.01).em),
    headlineLarge = TextStyle(fontFamily = Syne, fontWeight = FontWeight.Bold, fontSize = 32.sp),
    headlineMedium = TextStyle(fontFamily = Syne, fontWeight = FontWeight.Bold, fontSize = 28.sp),
    titleLarge = TextStyle(fontFamily = Syne, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = Syne, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
)
// Uso: MaterialTheme(typography = KpknTypography) { ... }
