package com.example.kpkn.screens.programs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.PowerliftingProfile
import com.example.kpkn.domain.onboarding.LiftMark
import com.example.kpkn.domain.training.TrainingMaxResolver
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.LiftMarksPicker
import com.example.kpkn.screens.onboarding.design.entreno.MarksMath
import com.example.kpkn.screens.programdetail.design.ScrollEdgeFade
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType

/** Etiqueta de prueba del botón de guardar del TM. */
internal const val TM_SAVE_TAG = "program-tm-save"

/** Los levantamientos del TM: los tres de competición y el press militar, que es opcional. */
private val TmLifts = listOf(LiftMark.SQUAT, LiftMark.BENCH, LiftMark.DEADLIFT, LiftMark.OVERHEAD_PRESS)

@Composable
fun TrainingMaxWizard(
    initial: PowerliftingProfile? = null,
    autoEstimate: PowerliftingProfile? = null,
    trainingMaxPercent: Double = 0.90,
    confirmLabel: String = "Aplicar y crear",
    onDismiss: () -> Unit,
    onConfirm: (PowerliftingProfile) -> Unit,
    unit: String = MarksMath.UNIT_KG,
) {
    KpknSheet(onDismissRequest = onDismiss, stableHeightFraction = 0.9f) {
        TrainingMaxContent(
            initial = initial,
            autoEstimate = autoEstimate,
            trainingMaxPercent = trainingMaxPercent,
            confirmLabel = confirmLabel,
            onDismiss = onDismiss,
            onConfirm = onConfirm,
            unit = unit,
        )
    }
}

/**
 * El contenido de la hoja de TM, sin su contenedor: los levantamientos en la regla de marcas, la vista previa y, abajo, «Guardar» y
 * «Más tarde». La unidad es la de Ajustes ([unit]): la hoja no tiene conmutador, y lo guardado es kg.
 */
@Composable
internal fun TrainingMaxContent(
    initial: PowerliftingProfile?,
    autoEstimate: PowerliftingProfile?,
    trainingMaxPercent: Double,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (PowerliftingProfile) -> Unit,
    unit: String = MarksMath.UNIT_KG,
) {
    var marks by remember(initial) { mutableStateOf(tmMarksOf(initial)) }
    val preview = TrainingMaxResolver.hydrateProfile(
        PowerliftingProfile(
            squat1RM = positiveKg(marks[LiftMark.SQUAT]),
            bench1RM = positiveKg(marks[LiftMark.BENCH]),
            deadlift1RM = positiveKg(marks[LiftMark.DEADLIFT]),
            overhead1RM = positiveKg(marks[LiftMark.OVERHEAD_PRESS]),
        ),
        trainingMaxPercent,
    )
    val hasValidInput = TmLifts.any { positiveKg(marks[it]) != null }
    val noMarks = TmLifts.none { positiveKg(marks[it]) != null }
    val scroll = rememberScrollState()
    // Solo cambia el booleano: la franja aparece o se va sin recomponer la hoja en cada tick del scroll.
    val canScrollMore by remember { derivedStateOf { scroll.canScrollForward } }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "Máximo de entrenamiento",
                    style = KpknType.title,
                    color = WizardColors.text,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = trainingMaxIntro(trainingMaxPercent),
                    style = KpknType.body.copy(lineBreak = LineBreak.Paragraph),
                    color = WizardColors.textMuted,
                )
                trainingMaxFiveRepNote(trainingMaxPercent)?.let { note ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = note,
                        style = KpknType.caption.copy(lineBreak = LineBreak.Paragraph),
                        color = WizardColors.textMuted,
                    )
                }
                if (autoEstimate != null && noMarks) {
                    Spacer(Modifier.height(14.dp))
                    KpknSecondaryPill(
                        text = "Calcular desde mi historial",
                        onClick = { marks = marks + tmMarksOf(autoEstimate) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(16.dp))
                LiftMarksPicker(
                    lifts = TmLifts,
                    valuesKg = marks,
                    unit = unit,
                    onValueKg = { lift, kg -> marks = if (kg == null) marks - lift else marks + (lift to kg) },
                    variantLabels = mapOf(LiftMark.OVERHEAD_PRESS to "opcional"),
                    showUnitSwitch = false,
                )
            }
            if (canScrollMore) ScrollEdgeFade(modifier = Modifier.align(Alignment.BottomCenter))
        }
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            // Una fila por levantamiento con TM. Sin ningún TM, la vista previa no se pinta (P6).
            val tmRows = trainingMaxPreviewRows(preview.squatTM, preview.benchTM, preview.deadliftTM, unit, overheadTM = preview.overheadTM)
            if (tmRows.isNotEmpty()) {
                tmRows.forEach { row ->
                    Text(
                        text = row,
                        style = KpknType.caption.copy(fontFeatureSettings = "'tnum' 1", lineBreak = LineBreak.Paragraph),
                        color = WizardColors.text,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
            KpknPrimaryPill(
                text = confirmLabel,
                onClick = { onConfirm(preview) },
                modifier = Modifier.fillMaxWidth(),
                tag = TM_SAVE_TAG,
                enabled = hasValidInput,
            )
            Box(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
                KpknTextAction(text = "Más tarde", onClick = onDismiss)
            }
        }
    }
}

/** Las marcas declaradas de un perfil (solo las mayores que cero): lo que la regla muestra como marca, por levantamiento. */
internal fun tmMarksOf(profile: PowerliftingProfile?): Map<LiftMark, Double> = buildMap {
    positiveKg(profile?.squat1RM)?.let { put(LiftMark.SQUAT, it) }
    positiveKg(profile?.bench1RM)?.let { put(LiftMark.BENCH, it) }
    positiveKg(profile?.deadlift1RM)?.let { put(LiftMark.DEADLIFT, it) }
    positiveKg(profile?.overhead1RM)?.let { put(LiftMark.OVERHEAD_PRESS, it) }
}

/** Un 1RM válido es mayor que cero; con 0 o sin dato, el levantamiento cuenta como «No la sé». */
private fun positiveKg(kg: Double?): Double? = kg?.takeIf { it > 0 }

/** Porcentaje del 1RM que usan Texas Method y Madcow como TM (D7): un peso parecido a un 5RM. */
private const val TEXAS_MADCOW_TM_PERCENT = 87

/** Explicación bajo el título; el porcentaje es el de la receta del plan que se va a crear. */
internal fun trainingMaxIntro(trainingMaxPercent: Double): String =
    "Introduce 1RM de competición. El TM se calcula al ${(trainingMaxPercent * 100).toInt()} % de tu 1RM."

/**
 * Nota de D7. Solo aparece cuando el TM del plan es el 87 % del 1RM (Texas Method y Madcow): en los
 * demás planes el porcentaje es otro y la nota no aplica.
 */
internal fun trainingMaxFiveRepNote(trainingMaxPercent: Double): String? =
    if ((trainingMaxPercent * 100).toInt() == TEXAS_MADCOW_TM_PERCENT) {
        "Texas Method y Madcow usan el $TEXAS_MADCOW_TM_PERCENT % de tu 1RM como TM (parecido a un 5RM)."
    } else {
        null
    }

/**
 * Filas de la vista previa: una por levantamiento con TM, como «Sentadilla · TM 124 kg», en la unidad de Ajustes. Sin TM no hay filas, y
 * así no quedan «—» huérfanos.
 */
internal fun trainingMaxPreviewRows(squatTM: Double?, benchTM: Double?, deadliftTM: Double?, unit: String,
    overheadTM: Double? = null): List<String> =
    listOfNotNull(
        squatTM?.let { "Sentadilla · TM ${MarksMath.formatMark(it, unit)}" },
        benchTM?.let { "Press de banca · TM ${MarksMath.formatMark(it, unit)}" },
        deadliftTM?.let { "Peso muerto · TM ${MarksMath.formatMark(it, unit)}" },
        overheadTM?.let { "Press militar · TM ${MarksMath.formatMark(it, unit)}" },
    )
