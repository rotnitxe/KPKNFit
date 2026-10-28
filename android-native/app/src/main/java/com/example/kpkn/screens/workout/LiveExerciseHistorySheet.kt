package com.example.kpkn.screens.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.workout.components.LiveSheet
import com.example.kpkn.screens.workout.components.LiveSheetSubtitle
import com.example.kpkn.screens.workout.components.LiveSheetTokens
import com.example.kpkn.screens.workout.components.liveSheetTopLine
import com.example.kpkn.ui.design.KpknType
import java.time.LocalDate

/** La mejor serie registrada de una sesión del historial: la de más carga y, a igual carga, más repeticiones. */
internal fun historyBestSet(sets: List<CompletedSet>): CompletedSet? = sets
    .filter { !it.isWarmup && !it.isFailedSet && it.failureReason.isNullOrBlank() && (it.weight > 0 || it.reps > 0) }
    .maxWithOrNull(compareBy<CompletedSet> { it.weight }.thenBy { it.reps })

/** «100 × 8», «Corporal × 12»: la serie en una cifra. */
internal fun historySetValue(set: CompletedSet): String = buildString {
    if (set.weight > 0) append(set.weight.toTrimmedNumberString()) else append("Corporal")
    if (set.reps > 0) append(" × ").append(set.reps)
}

/** «RIR 2 · Competición»: el esfuerzo de la serie y la etiqueta de la sesión. */
internal fun historySetMeta(set: CompletedSet, tag: String?): String = listOfNotNull(
    set.rir?.let { "RIR $it" } ?: set.rpe?.let { "RPE $it" },
    tag?.takeIf { it.isNotBlank() },
).joinToString(" · ").ifBlank { "Sin esfuerzo registrado" }

/**
 * «Historial del ejercicio» del prototipo 02: gráfica pequeña de la mejor carga por sesión y, debajo, una línea por sesión (fecha, esfuerzo
 * y etiqueta, mejor serie y «Usar», que lleva esa carga al borrador). Tocar una línea despliega todas sus series.
 */
@Composable
internal fun LiveExerciseHistorySheet(
    exerciseName: String,
    activeTag: String?,
    history: List<ExerciseHistoryEntry>,
    onUseWeight: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    LiveSheet(title = "Historial del ejercicio", onDismiss = onDismiss, tag = "live-history-sheet") { close ->
        LiveSheetSubtitle("$exerciseName · ${activeTag ?: "Sin etiqueta"}")
        if (history.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(vertical = 25.dp, horizontal = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Aún no hay referencia.", style = KpknType.bodyStrong.copy(fontSize = 14.sp), color = WizardColors.text)
                Text("Tu primer registro iniciará el historial de este ejercicio.", Modifier.padding(top = 6.dp),
                    style = KpknType.caption.copy(fontSize = 12.sp, lineHeight = 20.sp), color = LiveSheetTokens.Faint)
            }
            return@LiveSheet
        }
        val bests = history.take(6).reversed().mapNotNull { historyBestSet(it.sets)?.weight }.filter { it > 0 }
        if (bests.size >= 2) {
            val top = bests.max()
            Row(Modifier.fillMaxWidth().height(80.dp).padding(top = 0.dp).drawBehind {
                drawLine(Color(0x25FFFFFF), Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            }.semantics { contentDescription = "Progresión de la mejor carga en las últimas ${bests.size} sesiones" },
                horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.Bottom) {
                bests.forEachIndexed { index, weight ->
                    Box(Modifier.weight(1f).fillMaxHeight((weight / top).toFloat().coerceIn(.08f, 1f))
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(if (index == bests.lastIndex) WizardColors.text else Color(0x24FFFFFF)))
                }
            }
            Spacer(Modifier.height(23.dp))
        }
        history.forEach { entry ->
            val best = historyBestSet(entry.sets)
            var open by rememberSaveable(entry.date, entry.sets.size) { mutableStateOf(false) }
            Column(Modifier.fillMaxWidth().liveSheetTopLine().clickable(role = Role.Button) { open = !open }
                .padding(vertical = 13.dp).testTag("live-history-line")) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(formatHistoryDate(entry.date, LocalDate.now()), style = KpknType.caption.copy(fontSize = 12.sp), color = WizardColors.text)
                        Text(best?.let { historySetMeta(it, entry.tag) } ?: (entry.tag ?: "Sin series válidas"), Modifier.padding(top = 5.dp),
                            style = KpknType.caption.copy(fontSize = 10.sp, lineHeight = 13.sp), color = LiveSheetTokens.Faint)
                    }
                    best?.let {
                        Text(historySetValue(it), style = KpknType.metricM.copy(fontSize = 15.sp), color = WizardColors.text)
                    }
                    if (best != null && best.weight > 0) {
                        Box(Modifier.heightIn(min = 39.dp).clickable(role = Role.Button) { onUseWeight(best.weight); close() }
                            .padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                            Text("Usar", style = KpknType.caption.copy(fontSize = 11.sp), color = LiveSheetTokens.Muted)
                        }
                    }
                }
                if (open) {
                    entry.sets.filter { !it.isWarmup }.forEach { set ->
                        Text(historySetValue(set) + " · " + historySetMeta(set, null), Modifier.padding(top = 6.dp),
                            style = KpknType.caption.copy(fontSize = 11.sp), color = LiveSheetTokens.Muted)
                    }
                    entry.notes?.takeIf { it.isNotBlank() }?.let {
                        Text(it, Modifier.padding(top = 6.dp), style = KpknType.caption.copy(fontSize = 11.sp), color = LiveSheetTokens.Faint)
                    }
                }
            }
        }
    }
}
