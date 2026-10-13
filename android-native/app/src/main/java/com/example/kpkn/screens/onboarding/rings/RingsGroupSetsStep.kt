package com.example.kpkn.screens.onboarding.rings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.RingsMuscleGroup
import com.example.kpkn.domain.onboarding.MuscleSymbol
import com.example.kpkn.domain.onboarding.RingsSessionDaysState
import com.example.kpkn.domain.onboarding.SetupRingsMapper
import com.example.kpkn.screens.onboarding.SetupRingsAnswers
import com.example.kpkn.screens.onboarding.SetupWizardState
import com.example.kpkn.screens.onboarding.SetupWizardViewModel
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardSpacing
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.entreno.CountRuler
import com.example.kpkn.screens.onboarding.design.entreno.RingsMuscleGlyph
import com.example.kpkn.screens.onboarding.design.entreno.dayFullName
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import java.time.LocalDate

/** Series por grupo que admite la regla (0–20, el mismo rango que el reductor). */
private val SETS_RANGE = 0..20

/** Alto mínimo de la fila de un grupo y tamaño de su silueta (56 dp, estática). */
private val GroupHeaderMin = 56.dp
private val GroupGlyph = 56.dp

/**
 * Paso `RINGS_GROUP_SETS`: las series por grupo de las últimas 72 h. Arriba, la ventana («Desde el martes.»); debajo, seis filas
 * (Pecho, Espalda, Hombros, Brazos, Piernas y Core). Cada fila se despliega con su regla y solo una a la vez; «No lo sé» es un
 * interruptor de texto que deja el grupo sin dato. Omitible: sin respuesta, el paso no cuenta series.
 */
@Composable
internal fun RingsGroupSetsStep(state: SetupWizardState, vm: SetupWizardViewModel, modifier: Modifier = Modifier) {
    val today = remember { LocalDate.now() }
    RingsGroupSetsContent(
        answers = state.draft.ringsAnswers,
        today = today,
        onSets = { group, sets -> vm.setGroupSets(group, sets) },
        modifier = modifier,
    )
}

/**
 * Cuerpo del paso sin estado propio (lo usa también la hoja de render). [onSets] escribe las series de un grupo; `null` es «No lo
 * sé». [initialOpen] deja desplegada una fila al empezar.
 */
@Composable
internal fun RingsGroupSetsContent(
    answers: SetupRingsAnswers?,
    today: LocalDate,
    onSets: (RingsMuscleGroup, Int?) -> Unit,
    modifier: Modifier = Modifier,
    initialOpen: RingsMuscleGroup? = null,
) {
    var openGroup by remember { mutableStateOf(initialOpen) }
    val declared = answers?.sessionDaysState == RingsSessionDaysState.DECLARED
    val oldest = if (declared) {
        answers?.trainedDayOffsets.orEmpty()
            .filter { it <= SetupRingsMapper.GROUP_SETS_MAX_DAYS_BACK }
            .maxOrNull()
    } else {
        null
    }
    val windowLabel = ringsWindowStartLabel(oldest, today)
    Column(modifier = modifier.fillMaxWidth()) {
        if (windowLabel != null) {
            Text(
                text = windowLabel,
                style = WizardTypography.controlLabel,
                color = WizardColors.text,
                modifier = Modifier.testTag("rings-group-window"),
            )
            Spacer(Modifier.height(WizardSpacing.titleGap))
        }
        RingsMuscleGroup.entries.forEachIndexed { index, group ->
            if (index > 0) RingsQuestionDivider()
            RingsGroupRow(
                group = group,
                sets = answers?.groupSets?.get(group),
                unknown = answers?.groupSetsUnknown?.contains(group) == true,
                open = openGroup == group,
                onOpenChange = { openGroup = if (openGroup == group) null else group },
                onSets = { sets -> onSets(group, sets) },
                onUnknown = { onSets(group, null) },
            )
        }
    }
}

/**
 * Una fila: la silueta del músculo, el nombre y el resumen («12 series», «No lo sé» o «Sin responder»); a la derecha, «No lo
 * sé». Tocar la fila despliega su regla; «No lo sé» activado y apagado con la regla escribe `null` o abre la regla para elegir.
 */
@Composable
private fun RingsGroupRow(
    group: RingsMuscleGroup,
    sets: Int?,
    unknown: Boolean,
    open: Boolean,
    onOpenChange: () -> Unit,
    onSets: (Int) -> Unit,
    onUnknown: () -> Unit,
) {
    val summary = when {
        sets != null -> seriesLabel(sets)
        unknown -> "No lo sé"
        else -> "Sin responder"
    }
    val reduced = wizardReducedMotion()
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = GroupHeaderMin)
                    .testTag("rings-group-${group.name}")
                    .clickable(role = Role.Button, onClick = onOpenChange)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "${group.label}: $summary"
                        stateDescription = if (open) "Desplegado" else "Plegado"
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RingsMuscleGlyph(muscle = group.muscle(), modifier = Modifier.size(GroupGlyph))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = group.label, style = WizardTypography.controlLabel, color = WizardColors.text)
                    Text(
                        text = summary,
                        style = WizardTypography.note,
                        color = if (sets != null) WizardColors.text else WizardColors.textMuted,
                    )
                }
            }
            RingsTextSwitch(
                label = "No lo sé",
                checked = unknown,
                onCheckedChange = { checked ->
                    if (checked) {
                        onUnknown()
                    } else if (!open) {
                        // Apagarlo no escribe nada: la persona elige el número en la regla.
                        onOpenChange()
                    }
                },
                tag = "rings-group-unknown-${group.name}",
            )
        }
        AnimatedVisibility(
            visible = open,
            enter = if (reduced) EnterTransition.None else fadeIn(tween(200)) + expandVertically(tween(220)),
            exit = if (reduced) ExitTransition.None else fadeOut(tween(140)) + shrinkVertically(tween(180)),
        ) {
            CountRuler(
                range = SETS_RANGE,
                value = sets,
                onValue = onSets,
                unitLabel = "series",
                tag = "rings-group-ruler-${group.name}",
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}

/**
 * La ventana de las series en el lenguaje de la pantalla: «Desde hoy.», «Desde ayer.» o «Desde el martes.», según el día más
 * antiguo entrenado dentro de las 72 h ([oldestOffset]: 0 = hoy). Sin días en la ventana, nada.
 */
internal fun ringsWindowStartLabel(oldestOffset: Int?, today: LocalDate): String? = when (oldestOffset) {
    null -> null
    0 -> "Desde hoy."
    1 -> "Desde ayer."
    else -> "Desde el ${dayFullName(today.minusDays(oldestOffset.toLong()).dayOfWeek.value).lowercase()}."
}

/** El resumen de una fila con series: «1 serie» o «12 series». */
private fun seriesLabel(sets: Int): String = if (sets == 1) "1 serie" else "$sets series"

/** El músculo de [MuscleArt] que representa cada grupo de la pregunta. */
private fun RingsMuscleGroup.muscle(): MuscleSymbol = when (this) {
    RingsMuscleGroup.CHEST -> MuscleSymbol.CHEST
    RingsMuscleGroup.BACK -> MuscleSymbol.BACK
    RingsMuscleGroup.SHOULDERS -> MuscleSymbol.SHOULDERS
    RingsMuscleGroup.ARMS -> MuscleSymbol.ARMS
    RingsMuscleGroup.LEGS -> MuscleSymbol.QUADS
    RingsMuscleGroup.CORE -> MuscleSymbol.ABS
}
