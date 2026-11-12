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
import com.example.kpkn.screens.workout.components.liveSetRomLabel
import com.example.kpkn.screens.workout.components.liveSheetTopLine
import com.example.kpkn.ui.design.KpknType
import java.time.LocalDate

/** La mejor serie registrada de una sesión del historial: respeta modalidad de carga y luego repeticiones. */
internal fun historyBestSet(sets: List<CompletedSet>): CompletedSet? = sets
    .filter {
        !it.isWarmup && !it.skipped && !it.isFailedSet && it.failureReason.isNullOrBlank() &&
            ((com.example.kpkn.domain.workout.CompletedSetLoadSemantics.externalLoadKg(it) ?: 0.0) > 0.0 ||
                (com.example.kpkn.domain.workout.CompletedSetLoadSemantics.assistanceKg(it) ?: 0.0) > 0.0 ||
                it.reps > 0 ||
                (it.timeSeconds ?: 0) > 0)
    }
    .maxWithOrNull(
        compareBy<CompletedSet>(
            {
                when (com.example.kpkn.domain.workout.CompletedSetLoadSemantics.mode(it)) {
                    com.example.kpkn.data.models.LoadModeV2.ASSISTED ->
                        com.example.kpkn.domain.workout.CompletedSetLoadSemantics.assistanceKg(it)?.let { kg -> -kg }
                            ?: Double.NEGATIVE_INFINITY
                    com.example.kpkn.data.models.LoadModeV2.BODYWEIGHT -> 0.0
                    com.example.kpkn.data.models.LoadModeV2.LASTRE,
                    com.example.kpkn.data.models.LoadModeV2.LOAD,
                    null,
                    -> com.example.kpkn.domain.workout.CompletedSetLoadSemantics.externalLoadKg(it)
                        ?: Double.NEGATIVE_INFINITY
                }
            },
            { it.reps },
            { it.timeSeconds ?: 0 },
        ),
    )

private fun historySetRecoveredInputLoad(set: CompletedSet): Double? =
    when (com.example.kpkn.domain.workout.CompletedSetLoadSemantics.mode(set)) {
        com.example.kpkn.data.models.LoadModeV2.BODYWEIGHT -> null
        com.example.kpkn.data.models.LoadModeV2.ASSISTED ->
            com.example.kpkn.domain.workout.CompletedSetLoadSemantics.assistanceKg(set)?.takeIf { it > 0.0 }
        com.example.kpkn.data.models.LoadModeV2.LASTRE,
        com.example.kpkn.data.models.LoadModeV2.LOAD,
        null,
        -> com.example.kpkn.domain.workout.CompletedSetLoadSemantics.externalLoadKg(set)?.takeIf { it > 0.0 }
    }

/** «100 × 8», «Corporal × 12»: la serie en una cifra. */
internal fun historySetValue(set: CompletedSet): String = buildString {
    val mode = com.example.kpkn.domain.workout.CompletedSetLoadSemantics.mode(set)
    val load = historySetRecoveredInputLoad(set)
    when (mode) {
        com.example.kpkn.data.models.LoadModeV2.BODYWEIGHT -> append("Corporal")
        com.example.kpkn.data.models.LoadModeV2.LASTRE ->
            if (load != null) append("+${load.toTrimmedNumberString()} kg") else append("Lastre · sin detalle")
        com.example.kpkn.data.models.LoadModeV2.ASSISTED ->
            if (load != null) append("${load.toTrimmedNumberString()} asist.") else append("Asist. · sin detalle")
        com.example.kpkn.data.models.LoadModeV2.LOAD, null ->
            if (load != null) append(load.toTrimmedNumberString()) else append("Corporal")
    }
    if (set.reps > 0) append(" × ").append(set.reps)
}

/**
 * «RIR 2 · Competición»: el esfuerzo de la serie y la etiqueta de la sesión. En la línea de la sesión el ROM solo se dice cuando la mejor
 * serie no fue completa («RIR 2 · ROM 80 %»): con un recorrido completo ([com.example.kpkn.domain.workout.LiveRom.FULL_FROM] o más) no suma ruido.
 */
internal fun historySetMeta(set: CompletedSet, tag: String?): String = listOfNotNull(
    set.rir?.let { "RIR $it" } ?: set.rpe?.let { "RPE $it" },
    set.rom?.takeIf { it < com.example.kpkn.domain.workout.LiveRom.FULL_FROM }?.let(::liveSetRomLabel),
    tag?.takeIf { it.isNotBlank() },
).joinToString(" · ").ifBlank { "Sin esfuerzo registrado" }

/** Una serie desplegada del historial: «120 × 4 · RIR 2 · ROM 80 %» (aquí el ROM se dice siempre que se anotó). */
internal fun historyExpandedSetText(set: CompletedSet): String =
    historySetValue(set) + " · " + listOfNotNull(
        (set.rir?.let { "RIR $it" } ?: set.rpe?.let { "RPE $it" }),
        set.rom?.let(::liveSetRomLabel),
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
        val bests = history.take(6).reversed().mapNotNull { entry ->
            historyBestSet(entry.sets)?.let(::historySetRecoveredInputLoad)
        }.filter { it > 0 }
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
                    val bestInputLoad = best?.let(::historySetRecoveredInputLoad)
                    if (best != null && bestInputLoad != null && bestInputLoad > 0) {
                        Box(Modifier.heightIn(min = 39.dp).clickable(role = Role.Button) { onUseWeight(bestInputLoad); close() }
                            .padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                            Text("Usar", style = KpknType.caption.copy(fontSize = 11.sp), color = LiveSheetTokens.Muted)
                        }
                    }
                }
                if (open) {
                    entry.sets.filter { !it.isWarmup }.forEach { set ->
                        Text(historyExpandedSetText(set), Modifier.padding(top = 6.dp),
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
