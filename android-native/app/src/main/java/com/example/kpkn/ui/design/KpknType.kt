package com.example.kpkn.ui.design

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardFonts

/**
 * Roles tipográficos de la app (KPKN_UI_V2 §5). Syne para lo importante y las cifras; Inter para leer. Las cifras van con números
 * tabulares. El eyebrow y la nota suben a 13 sp (la regla de mínimo del wizard), en vez de los 12 de §5.
 */
object KpknType {
    /** Saludo y título de pantalla: Syne ExtraBold 32 / 36. Si no cabe, la pantalla lo encoge hasta 24. */
    val display = TextStyle(
        fontFamily = WizardFonts.display,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 32.sp,
        lineHeight = 36.sp,
    )

    /** Título de módulo protagonista (nombre de la sesión): Syne Bold 24 / 28. */
    val title = TextStyle(
        fontFamily = WizardFonts.display,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 28.sp,
    )

    /** Peso y kcal del día: Syne ExtraBold 40 / 44, con cifras de altura de mayúscula (lnum) y tabulares (tnum). */
    val metricXL = TextStyle(
        fontFamily = WizardFonts.display,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 40.sp,
        lineHeight = 44.sp,
        fontFeatureSettings = "'lnum' 1, 'tnum' 1",
    )

    /** Valores de RINGS: Syne Bold 28 / 32, con cifras de altura de mayúscula (lnum) y tabulares (tnum). */
    val metricL = TextStyle(
        fontFamily = WizardFonts.display,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        fontFeatureSettings = "'lnum' 1, 'tnum' 1",
    )

    /** Cifras pequeñas (grasa, IMC, FFMI): Syne Bold 18 / 22, con cifras de altura de mayúscula (lnum) y tabulares (tnum). */
    val metricM = TextStyle(
        fontFamily = WizardFonts.display,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 22.sp,
        fontFeatureSettings = "'lnum' 1, 'tnum' 1",
    )

    /** Unidad junto a una cifra: Inter SemiBold 13 / 16. El color lo pone quien la usa (`textMuted` en `KpknMetric`). */
    val unit = TextStyle(
        fontFamily = WizardFonts.body,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 16.sp,
    )

    /** Cabecera de módulo y kickers: Inter SemiBold 13 / 18, espaciado 1,2 sp. Va en mayúsculas; lo hace quien lo usa. */
    val eyebrow = TextStyle(
        fontFamily = WizardFonts.body,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 1.2.sp,
    )

    /** Texto de lectura: Inter Regular 15 / 21. */
    val body = TextStyle(
        fontFamily = WizardFonts.body,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    )

    /** Nombres de ejercicio y filas: Inter SemiBold 15 / 21. */
    val bodyStrong = TextStyle(
        fontFamily = WizardFonts.body,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    )

    /** Notas y pies: Inter Regular 13 / 18. */
    val caption = TextStyle(
        fontFamily = WizardFonts.body,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    )

    /** Acciones de texto: Inter SemiBold 14 / 20. */
    val action = TextStyle(
        fontFamily = WizardFonts.body,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    )

    /** Etiqueta de las pestañas de la barra inferior: Inter Medium 11 / 14. */
    val navLabel = TextStyle(
        fontFamily = WizardFonts.body,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    )
}
