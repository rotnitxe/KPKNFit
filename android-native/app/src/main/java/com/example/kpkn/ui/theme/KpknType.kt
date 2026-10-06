package com.example.kpkn.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.kpkn.R

/**
 * Syne, la tipografía de marca de KPKN (SIL Open Font License 1.1; la licencia vive en
 * `assets/fonts/LICENSE-Syne-OFL.txt`). Es la fuente del logotipo (ExtraBold 800, tracking −1 %).
 *
 * Uso: SOLO para títulos importantes, números grandes y botones de marca. Es muy expresiva y cansa en
 * párrafos: el texto largo, las etiquetas y los cuerpos siguen en la sans de la app.
 */
val Syne = FontFamily(
    Font(R.font.syne_regular, FontWeight.Normal),
    Font(R.font.syne_medium, FontWeight.Medium),
    Font(R.font.syne_semibold, FontWeight.SemiBold),
    Font(R.font.syne_bold, FontWeight.Bold),
    Font(R.font.syne_extrabold, FontWeight.ExtraBold),
)
