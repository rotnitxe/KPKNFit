package com.example.kpkn.screens.programdetail.design

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.programdetail.MAX_CHANGES
import com.example.kpkn.screens.programdetail.ProgramPlanDisplaySummary
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** «6 oct – 16 nov»: el primer y el último día del calendario del programa; null sin fechas. */
internal fun planCalendarRange(dates: Collection<LocalDate>): String? {
    val first = dates.minOrNull() ?: return null
    val last = dates.maxOrNull() ?: return null
    val format = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es"))
    fun label(date: LocalDate) = format.format(date).replace(".", "")
    return "${label(first)} – ${label(last)}"
}

/** Un cambio registrado («Remo con barra → remo en máquina · Cambio manual») partido en título y línea pequeña. */
internal fun planChangeEntry(change: String): Pair<String, String> {
    val cut = change.indexOf(" · ")
    return if (cut < 0) change to "" else change.substring(0, cut) to change.substring(cut + 3)
}

/** El origen, al final y en letra pequeña: la procedencia y, si la hay, su fuente o su autor. */
internal fun planOriginLine(summary: ProgramPlanDisplaySummary): String =
    listOfNotNull(summary.provenanceLabel.takeIf { it.isNotBlank() }, summary.sourceLine?.takeIf { it.isNotBlank() }).joinToString(" · ")

/**
 * «Sobre el programa» (lienzo v6, m-about): el nombre, su descripción, cuatro filas `.ratio` (material, sesión más larga, cargas de referencia
 * por resolver y calendario), los cambios registrados en línea de tiempo y el origen al final en letra pequeña. «Ver cómo funciona» abre la
 * hoja de solo lectura del catálogo cuando el programa viene de él.
 *
 * [description] es la descripción del programa, que se usa si el catálogo no trae resumen. [calendarRange] es «6 oct – 16 nov».
 */
@Composable
internal fun ProgramPlanSheet(
    summary: ProgramPlanDisplaySummary,
    onDismiss: () -> Unit,
    /** Abre la hoja de solo lectura «Cómo funciona» de la entrada del catálogo (su id). Sin él, el botón no aparece. */
    onOpenPlanInfo: ((String) -> Unit)? = null,
    description: String? = null,
    calendarRange: String? = null,
) {
    ProgramLienzoSheet(onDismiss = onDismiss) {
        LienzoSheetTitle(title = summary.planName, eyebrow = "Sobre el programa")
        val body = summary.summary ?: description?.lines()?.map { it.trim() }?.firstOrNull { it.isNotEmpty() }
        body?.let { Text(it, style = LienzoBodyStyle, color = WizardColors.textMuted, modifier = Modifier.padding(top = 0.dp, bottom = 4.dp)) }
        val rows = buildList {
            add("Material" to summary.requiredMaterial)
            add("Sesión más larga" to (summary.maxSessionMinutes?.let { "≈ $it min" } ?: "—"))
            if (summary.pendingLoadCount > 0) add("Cargas de referencia" to "${summary.pendingLoadCount} por resolver")
            calendarRange?.let { add("Calendario" to it) }
        }
        rows.forEach { (label, value) ->
            LienzoRatioRow(label = label, value = value, labelStyle = LienzoRatioStyle, labelColor = WizardColors.textFaint)
        }
        if (summary.changes.isNotEmpty()) {
            LienzoGroup("Cambios registrados")
            LienzoTimeline(entries = summary.changes.take(MAX_CHANGES).map(::planChangeEntry))
        }
        val origin = planOriginLine(summary)
        if (origin.isNotBlank()) {
            LienzoGroup("Origen")
            Text(origin, style = LienzoCapStyle, color = WizardColors.textFaint)
        }
        summary.catalogEntry?.let { entry ->
            if (onOpenPlanInfo != null) {
                ProgramLienzoPill(
                    text = "Ver cómo funciona",
                    onClick = { onOpenPlanInfo(entry.id) },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    filled = false,
                    small = true,
                )
            }
        }
    }
}
