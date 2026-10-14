package com.example.kpkn.screens.workout.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknType
import java.util.Locale

/**
 * Cabecera de una página de calentamiento o movilidad (KPKN LS5b): el eyebrow («Aproximación 1 de 3», en mayúsculas por estilo), el
 * elemento actual en `title`, su dosis en `metricXL` tabular y, si hay, una línea de `caption`.
 *
 * [dose] es la cifra («60» o «2 × 10»). [doseUnit] es la unidad que comparte su línea base («kg») y [doseLead] un prefijo pequeño que va
 * delante («≈» en una estimación). Sin [dose], no hay línea de dosis. Sin estado.
 */
@Composable
fun PrepPageHeader(
    eyebrow: String,
    title: String,
    modifier: Modifier = Modifier,
    dose: String? = null,
    doseUnit: String? = null,
    doseLead: String? = null,
    caption: String? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = eyebrow.uppercase(Locale("es")),
            style = KpknType.eyebrow,
            color = WizardColors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = title,
            style = KpknType.title,
            color = WizardColors.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (dose != null) {
            Text(
                text = prepDoseText(lead = doseLead, value = dose, unit = doseUnit),
                style = KpknType.metricXL,
                color = WizardColors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!caption.isNullOrBlank()) {
            // Dos líneas como mucho: la leyenda se parte entre palabras, nunca a media palabra.
            Text(
                text = caption,
                style = KpknType.caption,
                color = WizardColors.textMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * La cifra y su unidad en `unit` (13 sp, `textMuted`, sobre la misma línea base), con el prefijo [lead] pequeño delante, todo en un solo
 * texto.
 */
private fun prepDoseText(lead: String?, value: String, unit: String?): AnnotatedString = buildAnnotatedString {
    val small = SpanStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = WizardColors.textMuted)
    if (!lead.isNullOrBlank()) {
        withStyle(small) { append("$lead ") }
    }
    append(value)
    if (!unit.isNullOrBlank()) {
        withStyle(small) { append(" $unit") }
    }
}
