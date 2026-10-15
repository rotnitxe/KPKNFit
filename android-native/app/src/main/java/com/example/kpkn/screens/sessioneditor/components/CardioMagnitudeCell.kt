package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknMetric
import com.example.kpkn.ui.design.KpknSetFormat
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import java.util.Locale

/** Radio de las celdas interiores del cardio: un escalón por debajo del módulo de 24 dp. */
internal val CardioCellShape = RoundedCornerShape(16.dp)

/**
 * Celda `inset` de una magnitud del cardio: el nombre en eyebrow y, debajo, la cifra en Syne tabular con su unidad. Toda la celda es un
 * botón de al menos 64 dp de alto. Sin unidad (p. ej. «Libre» o «1:30»), la cifra va sola.
 */
@Composable
internal fun CardioMagnitudeCell(
    label: String,
    value: String,
    unit: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .defaultMinSize(minHeight = 64.dp)
            .clip(CardioCellShape)
            .background(KpknSurface.inset, CardioCellShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .semantics { contentDescription = listOfNotNull(label, value, unit).joinToString(" ") },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = KpknType.caption,
            color = WizardColors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        KpknMetric(value = value, unit = unit, style = KpknType.metricM)
    }
}

/** Cifra y unidad de una duración: «20» «min»; «1:30» sin unidad; «30» «s»; nulo es «Libre». */
internal fun durationCellParts(seconds: Int?): Pair<String, String?> = when {
    seconds == null -> "Libre" to null
    seconds >= 60 && seconds % 60 == 0 -> "${seconds / 60}" to "min"
    seconds >= 60 -> KpknSetFormat.rest(seconds) to null
    else -> "${seconds.coerceAtLeast(0)}" to "s"
}

/** Cifra y unidad de una distancia: «5» «km», «2,5» «km»; nulo es «—». */
internal fun distanceCellParts(km: Double?): Pair<String, String?> = when (km) {
    null -> "—" to null
    else -> cellNumber(km) to "km"
}

/** Cifra y unidad de un ritmo: «5:30» «/km»; nulo es «—». */
internal fun paceCellParts(secondsPerKm: Int?): Pair<String, String?> = when (secondsPerKm) {
    null -> "—" to null
    else -> KpknSetFormat.rest(secondsPerKm) to "/km"
}

/** Número con coma decimal y sin decimales si es entero: «5», «2,5». */
internal fun cellNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString()
    else String.format(Locale.forLanguageTag("es"), "%.1f", value)
