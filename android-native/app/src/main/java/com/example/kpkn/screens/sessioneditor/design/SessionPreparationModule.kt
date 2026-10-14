package com.example.kpkn.screens.sessioneditor.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.MobilitySeries
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WarmupSetDefinition
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.SessionCardioDose
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType

/** Una fila de la preparación: el nombre del elemento y su dosis («2 min» o «2 × 10»). Sin dato, la dosis es null y no se pinta. */
internal data class SessionPreparationRow(val name: String, val dose: String?)

/**
 * Filas de la preparación de la sesión (SESSION_EDITOR_V1 §2, punto 4): el calentamiento y la movilidad que el editor ya guarda por
 * ejercicio y por parte. Sale de esos datos y de nada más; sin calentamiento ni movilidad, la lista está vacía.
 */
internal fun sessionPreparationRows(session: Session): List<SessionPreparationRow> = buildList {
    session.allExercises().forEach { exercise ->
        if (exercise.warmupSets.isNotEmpty()) {
            add(SessionPreparationRow(name = "Calentamiento · ${exercise.name}", dose = warmupDose(exercise.warmupSets)))
        }
        exercise.mobilitySeries.forEach { add(mobilityRow(it)) }
    }
    session.parts.forEach { part -> part.mobilitySeries.forEach { add(mobilityRow(it)) } }
}

/**
 * Ejercicio que abre «Editar»: el primero con calentamiento o movilidad. Sin él, la preparación es solo de partes y no hay nada que
 * abrir, así que el módulo no ofrece la acción.
 */
internal fun sessionPreparationTargetExerciseId(session: Session): String? =
    session.allExercises().firstOrNull { it.warmupSets.isNotEmpty() || it.mobilitySeries.isNotEmpty() }?.id

private fun mobilityRow(series: MobilitySeries): SessionPreparationRow {
    val seconds = series.durationSeconds?.takeIf { it > 0 }
    val reps = series.reps?.takeIf { it.isNotBlank() }
    val dose = when {
        seconds != null -> SessionCardioDose.minutesText(seconds)
        reps != null -> "${series.sets.coerceAtLeast(1)} × $reps"
        else -> null
    }
    return SessionPreparationRow(name = series.name, dose = dose)
}

/** «3 × 10» si todas las series tienen las mismas repeticiones; si no, «3 series». */
private fun warmupDose(sets: List<WarmupSetDefinition>): String {
    val reps = sets.map { it.targetReps }.distinct()
    return if (reps.size == 1) "${sets.size} × ${reps.first()}" else "${sets.size} series"
}

/**
 * Módulo «Preparación» (SESSION_EDITOR_V1 §2, punto 4): va justo después del impacto y antes de los espacios de cardio y de fuerza. Una fila
 * por elemento, con el nombre en `body` y la dosis en `caption` tabular. [onEdit] despliega el primer ejercicio con preparación; sin él no
 * hay acción. Sin estado.
 */
@Composable
internal fun SessionPreparationModule(
    rows: List<SessionPreparationRow>,
    modifier: Modifier = Modifier,
    onEdit: (() -> Unit)? = null,
) {
    val edit = onEdit
    val action: (@Composable () -> Unit)? = if (edit != null) {
        { KpknTextAction(text = "Editar", onClick = edit) }
    } else {
        null
    }
    KpknModule(modifier = modifier) {
        KpknModuleHeader(label = "Preparación", action = action)
        rows.forEachIndexed { index, row ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(KpknSurface.divider),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
            ) {
                Text(
                    text = row.name,
                    style = KpknType.body,
                    color = WizardColors.text,
                    // Hasta dos líneas, cortadas entre palabras: el nombre nunca se parte a media palabra (KPKN_UI_V2 §5).
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                val dose = row.dose
                if (dose != null) {
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = dose,
                        style = KpknType.caption.copy(fontFeatureSettings = "'tnum' 1"),
                        color = WizardColors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
