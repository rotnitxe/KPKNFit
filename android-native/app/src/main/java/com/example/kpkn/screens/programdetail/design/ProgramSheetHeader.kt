package com.example.kpkn.screens.programdetail.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.ui.design.KpknType

/** Título de las hojas de Program Detail: Syne Bold 22 / 26, como «Compartir en historias». */
private val sheetTitleStyle = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 26.sp)

/**
 * Cabecera común de las hojas de Program Detail (P9): el [eyebrow] pequeño en mayúsculas, el [title] en Syne y el [subtitle] en gris.
 * Lo que va null no se pinta.
 */
@Composable
internal fun ProgramSheetHeader(title: String, modifier: Modifier = Modifier, eyebrow: String? = null, subtitle: String? = null) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        eyebrow?.let { Text(it.uppercase(), style = KpknType.eyebrow, color = WizardColors.textFaint) }
        Text(title, style = sheetTitleStyle, color = WizardColors.text, modifier = Modifier.semantics { heading() })
        subtitle?.let { Text(it, style = KpknType.body, color = WizardColors.textMuted) }
    }
}
