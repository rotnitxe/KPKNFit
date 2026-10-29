package com.example.kpkn.screens.programdetail.design

import androidx.compose.runtime.Composable
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.training.block.ContinuationChoice
import com.example.kpkn.ui.components.icons.KpknLineIcons

/**
 * El resumen de m-continue: «Sentadilla +7,5 kg, banca +4 kg y 92 % de cumplimiento en 6 semanas.». [gains] son los levantamientos que
 * subieron (nombre y kg ganados), de los que se toman los dos primeros; sin subidas, solo el cumplimiento. null si no hay nada que decir.
 */
internal fun continuationSummary(gains: List<Pair<String, Double>>, adherencePercent: Int?, weekCount: Int?): String? {
    val lifts = gains.filter { it.second > 0.05 }.take(2).mapIndexed { index, (name, kg) ->
        val label = if (index == 0) name else name.replaceFirstChar { it.lowercase(java.util.Locale("es")) }
        "$label +${blockReviewNumber(kg)} kg"
    }
    val adherence = adherencePercent?.let { percent ->
        val weeks = weekCount?.takeIf { it > 0 }?.let { " en $it ${if (it == 1) "semana" else "semanas"}" }.orEmpty()
        "$percent % de cumplimiento$weeks"
    }
    val parts = lifts + listOfNotNull(adherence)
    if (parts.isEmpty()) return null
    val sentence = if (parts.size == 1) parts[0] else parts.dropLast(1).joinToString(", ") + " y " + parts.last()
    return sentence.replaceFirstChar { it.uppercase() } + "."
}

/**
 * Al terminar el programa (lienzo v6, m-continue): «Programa terminado», «¿Cómo sigues?», el resumen y las tres salidas como filas `.mi` con
 * su explicación. Cada una pide su vista previa; «Elegir otro del catálogo» abre el catálogo directamente.
 */
@Composable
internal fun ProgramContinueChoiceSheet(
    summary: String?,
    busy: Boolean,
    onChoose: (ContinuationChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    ProgramLienzoSheet(onDismiss = onDismiss, dismissible = !busy) {
        LienzoSheetTitle(title = "¿Cómo sigues?", eyebrow = "Programa terminado", sub = summary)
        LienzoMenuRow(
            icon = KpknLineIcons.Repeat,
            title = "Repetir con tus nuevas marcas",
            sub = "Misma estructura; los registros de este quedan guardados",
            onClick = { onChoose(ContinuationChoice.REPEAT_WITH_NEW_MAXES) },
            divider = false,
            enabled = !busy,
        )
        LienzoMenuRow(
            icon = KpknLineIcons.Book,
            title = "Elegir otro del catálogo",
            onClick = { onChoose(ContinuationChoice.PICK_FROM_CATALOG) },
            enabled = !busy,
        )
        LienzoMenuRow(
            icon = KpknLineIcons.Pause,
            title = "Una semana de mantenimiento",
            sub = "Mitad de series, RIR 4, carga por elegir",
            onClick = { onChoose(ContinuationChoice.MAINTENANCE_WEEK) },
            enabled = !busy,
        )
    }
}

/**
 * La confirmación de una continuación (la «hoja de confirmación» de las notas de m-continue), con el patrón corto de m-offdate: qué se crea
 * y «Crear y activar». Si la opción no prepara un programa nuevo, lo dice y deja cerrar.
 */
@Composable
internal fun ProgramContinuationSheet(preview: ProgramRepository.ContinuationPreview, busy: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val next = preview.nextProgram ?: run {
        ProgramUnavailableSheet("Programa terminado", "No hay un programa nuevo que preparar con esta opción.", onDismiss)
        return
    }
    val maintenance = preview.choice == ContinuationChoice.MAINTENANCE_WEEK
    ProgramConfirmSheet(
        title = if (maintenance) "Una semana de mantenimiento" else "Repetir con tus nuevas marcas",
        eyebrow = next.name,
        sub = if (maintenance) {
            "Mitad de series, RIR 4 y carga por elegir al entrenar. El programa terminado conserva sus registros."
        } else {
            "Misma estructura con tus marcas confirmadas. El programa terminado conserva sus registros y decisiones."
        },
        confirmLabel = "Crear y activar",
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        busy = busy,
        confirmTag = "continuation_confirm",
    )
}
