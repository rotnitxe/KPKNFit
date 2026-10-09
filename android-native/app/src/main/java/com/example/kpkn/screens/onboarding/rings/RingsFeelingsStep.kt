package com.example.kpkn.screens.onboarding.rings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.screens.onboarding.SetupRingsAnswers
import com.example.kpkn.screens.onboarding.SetupWizardDraft
import com.example.kpkn.screens.onboarding.SetupWizardState
import com.example.kpkn.screens.onboarding.SetupWizardViewModel
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardSpacing
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.entreno.RingsLevelArt
import com.example.kpkn.screens.onboarding.design.entreno.SymbolArt
import com.example.kpkn.screens.onboarding.design.entreno.SymbolClock
import com.example.kpkn.screens.onboarding.design.entreno.rememberSymbolClock
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.onboarding.withFeelings
import com.example.kpkn.ui.components.rings.KpknRingColors

/** Los tres canales de las sensaciones, en el orden de las filas. */
internal enum class RingsFeelingChannel { MUSCULAR, ENERGY, STRUCTURE }

/** Arco lleno de cada escala: 1 = el mejor estado (anillo entero) y 5 = el peor (un 10 %). */
private val RingLevelFill = listOf(1f, 0.75f, 0.5f, 0.3f, 0.1f)

/** Las etiquetas de cada escala, de 1 a 5 (la sensación de hoy, no un porcentaje). */
private val MuscularLabels = listOf("Como nuevos", "Bien", "Normales", "Cargados", "Muy cargados")
private val EnergyLabels = listOf("De sobra", "Bien", "Normal", "Baja", "Agotada")
private val StructureLabels = listOf("Perfecta", "Bien", "Normal", "Cargada", "Con molestias")

/** Tamaño de cada anillo de la escala: cinco caben en 312 dp de contenido. */
private val LevelCanvas = 56.dp

/**
 * Paso `RINGS_FEELINGS`: tres filas (Músculos, Energía y Columna), cada una con una escala de cinco anillos de elección única.
 * Debajo de la fila, solo la etiqueta del elegido. «Prefiero no decirlo» deja ese canal sin responder. Escribe por
 * `updateStep` leyendo el borrador más reciente, para que dos toques seguidos no se pisen.
 */
@Composable
internal fun RingsFeelingsStep(state: SetupWizardState, vm: SetupWizardViewModel, modifier: Modifier = Modifier) {
    RingsFeelingsContent(
        answers = state.draft.ringsAnswers,
        onLevel = { channel, level ->
            vm.updateStep(SetupStepId.RINGS_FEELINGS) { latest ->
                latest.withChannelLevel(channel, level, System.currentTimeMillis())
            }
        },
        onNotSay = { channel ->
            vm.updateStep(SetupStepId.RINGS_FEELINGS) { latest ->
                latest.withChannelLevel(channel, null, System.currentTimeMillis())
            }
        },
        modifier = modifier,
    )
}

/** Cuerpo del paso sin estado propio (lo usa también la hoja de render). */
@Composable
internal fun RingsFeelingsContent(
    answers: SetupRingsAnswers?,
    onLevel: (RingsFeelingChannel, Int) -> Unit,
    onNotSay: (RingsFeelingChannel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = wizardReducedMotion()
    val anyChosen = answers?.muscleFeeling != null || answers?.energy != null || answers?.structureFeeling != null
    val clock = rememberSymbolClock(active = !reduced && anyChosen)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(WizardSpacing.sectionGap),
    ) {
        FeelingRow(
            channel = RingsFeelingChannel.MUSCULAR,
            title = "Músculos",
            accent = KpknRingColors.musculos,
            labels = MuscularLabels,
            chosen = answers?.muscleFeeling,
            clock = clock,
            reduced = reduced,
            onLevel = onLevel,
            onNotSay = onNotSay,
        )
        RingsQuestionDivider()
        FeelingRow(
            channel = RingsFeelingChannel.ENERGY,
            title = "Energía",
            accent = KpknRingColors.energia,
            labels = EnergyLabels,
            chosen = answers?.energy,
            clock = clock,
            reduced = reduced,
            onLevel = onLevel,
            onNotSay = onNotSay,
        )
        RingsQuestionDivider()
        FeelingRow(
            channel = RingsFeelingChannel.STRUCTURE,
            title = "Columna",
            accent = KpknRingColors.columna,
            labels = StructureLabels,
            chosen = answers?.structureFeeling,
            clock = clock,
            reduced = reduced,
            onLevel = onLevel,
            onNotSay = onNotSay,
        )
    }
}

/**
 * Una sensación: su título, la acción «Prefiero no decirlo» y la escala de cinco anillos del color de su canal. Solo se ve la
 * etiqueta del elegido, bajo la escala.
 */
@Composable
private fun FeelingRow(
    channel: RingsFeelingChannel,
    title: String,
    accent: Color,
    labels: List<String>,
    chosen: Int?,
    clock: SymbolClock,
    reduced: Boolean,
    onLevel: (RingsFeelingChannel, Int) -> Unit,
    onNotSay: (RingsFeelingChannel) -> Unit,
) {
    val arts: List<SymbolArt> = remember(accent) { RingLevelFill.map { fill -> RingsLevelArt(fill, accent) } }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(WizardSpacing.titleGap),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = WizardTypography.controlLabel,
                color = WizardColors.text,
                modifier = Modifier.weight(1f),
            )
            RingsTextAction(
                label = "Prefiero no decirlo",
                onClick = { onNotSay(channel) },
                tag = "rings-feeling-skip-${channel.name}",
            )
        }
        Row(modifier = Modifier.fillMaxWidth().selectableGroup()) {
            for (level in 1..RingLevelFill.size) {
                RingsRadioSymbol(
                    art = arts[level - 1],
                    description = "$title: ${labels[level - 1]}",
                    tag = "rings-feeling-${channel.name}-$level",
                    selected = chosen == level,
                    clock = clock,
                    reducedMotion = reduced,
                    onSelect = { if (chosen != level) onLevel(channel, level) },
                    canvasSize = LevelCanvas,
                    artScale = 1f,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        // Una sola etiqueta por fila: la del elegido. Con nada elegido, la línea queda vacía para no saltar al elegir.
        Text(
            text = chosen?.let { labels.getOrNull(it - 1) }.orEmpty(),
            style = WizardTypography.note,
            color = WizardColors.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().heightIn(min = 18.dp),
        )
    }
}

/**
 * El borrador con el canal [channel] fijado en [level] (null = sin responder). Los otros dos canales se leen del propio
 * borrador, así que escribir uno nunca borra otro.
 */
private fun SetupWizardDraft.withChannelLevel(channel: RingsFeelingChannel, level: Int?, nowEpochMs: Long): SetupWizardDraft {
    val answers = ringsAnswers
    return withFeelings(
        muscular = if (channel == RingsFeelingChannel.MUSCULAR) level else answers?.muscleFeeling,
        energy = if (channel == RingsFeelingChannel.ENERGY) level else answers?.energy,
        structure = if (channel == RingsFeelingChannel.STRUCTURE) level else answers?.structureFeeling,
        nowEpochMs = nowEpochMs,
    )
}
