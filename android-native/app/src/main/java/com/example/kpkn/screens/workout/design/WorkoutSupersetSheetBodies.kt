package com.example.kpkn.screens.workout.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknNativeTimePickerDialog
import com.example.kpkn.ui.design.KpknCellInput
import com.example.kpkn.ui.design.KpknCheckRow
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknNumberCell
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknSetFormat
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType

/** Un ejercicio que se puede marcar en una superserie. [subtitle] va en `caption` (p. ej. «Origen» para el de partida). */
internal data class WorkoutSupersetCandidate(
    val id: String,
    val name: String,
    val subtitle: String? = null,
)

/** Miembros máximos de una superserie en la sesión en vivo, como antes del rediseño. */
internal const val WORKOUT_SUPERSET_MAX_MEMBERS = 4

/**
 * Cuerpo de «Crear superserie» y «Agregar a superserie» (hoja de la sesión en vivo). Los ejercicios se marcan con [KpknCheckRow]; el de
 * partida ([anchorId]) va siempre marcado y no se puede quitar. La selección la guarda quien llama ([selectedIds] y
 * [onSelectedIdsChange]), porque los descansos por defecto dependen de ella. Rondas y descansos son [WorkoutSupersetRoundsFields].
 * «Crear» llama con los ejercicios marcados (de dos en adelante) y con las cifras; «Desde catálogo» solo aparece si [canOpenCatalog].
 */
@Composable
internal fun WorkoutSupersetCreatorBody(
    candidates: List<WorkoutSupersetCandidate>,
    anchorId: String?,
    selectedIds: List<String>,
    onSelectedIdsChange: (List<String>) -> Unit,
    defaultRounds: Int,
    defaultRestBetween: Int,
    defaultRestAfter: Int,
    confirmLabel: String,
    canOpenCatalog: Boolean,
    onConfirm: (exerciseIds: List<String>, rounds: Int, restBetween: Int, restAfter: Int) -> Unit,
    onOpenCatalog: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var rounds by remember(defaultRounds) { mutableIntStateOf(defaultRounds) }
    var restBetween by remember(defaultRestBetween) { mutableIntStateOf(defaultRestBetween) }
    var restAfter by remember(defaultRestAfter) { mutableIntStateOf(defaultRestAfter) }

    fun toggle(id: String) {
        if (id == anchorId) return
        onSelectedIdsChange(
            when {
                id in selectedIds -> selectedIds - id
                selectedIds.size >= WORKOUT_SUPERSET_MAX_MEMBERS -> selectedIds
                else -> selectedIds + id
            },
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Selecciona ejercicios pendientes de la sesión. La superserie se aplicará solo a este entrenamiento.",
            style = KpknType.caption,
            color = WizardColors.textMuted,
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            KpknModuleHeader(label = "Ejercicios")
            if (candidates.size < 2) {
                Text(
                    text = "No hay otro ejercicio disponible en este grupo.",
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                )
            }
            KpknModule(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                candidates.forEachIndexed { index, candidate ->
                    if (index > 0) WorkoutStructureDivider()
                    val checked = candidate.id in selectedIds
                    val isAnchor = candidate.id == anchorId
                    KpknCheckRow(
                        checked = checked,
                        title = candidate.name,
                        subtitle = if (isAnchor) "Origen" else candidate.subtitle,
                        enabled = isAnchor || checked || selectedIds.size < WORKOUT_SUPERSET_MAX_MEMBERS,
                        onClick = { toggle(candidate.id) },
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            KpknModuleHeader(label = "Configuración inicial")
            WorkoutSupersetRoundsFields(
                rounds = rounds,
                restBetween = restBetween,
                restAfter = restAfter,
                onRoundsChange = { rounds = it },
                onRestBetweenChange = { restBetween = it },
                onRestAfterChange = { restAfter = it },
            )
        }

        KpknPrimaryPill(
            text = confirmLabel,
            onClick = { onConfirm(selectedIds, rounds, restBetween, restAfter) },
            modifier = Modifier.fillMaxWidth(),
            enabled = selectedIds.size >= 2,
        )
        if (canOpenCatalog) {
            KpknSecondaryPill(
                text = "Desde catálogo",
                onClick = onOpenCatalog,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        KpknTextAction(text = "Cancelar", onClick = onDismiss)
    }
}

/**
 * Cuerpo de «Rondas y descansos» de una superserie ya creada: las mismas cifras que el creador, sin la selección de ejercicios.
 * «Guardar» llama con las tres cifras y «Cancelar» solo cierra.
 */
@Composable
internal fun WorkoutSupersetRoundsBody(
    rounds: Int,
    restBetween: Int,
    restAfter: Int,
    onSave: (rounds: Int, restBetween: Int, restAfter: Int) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var roundsDraft by remember(rounds) { mutableIntStateOf(rounds) }
    var restBetweenDraft by remember(restBetween) { mutableIntStateOf(restBetween) }
    var restAfterDraft by remember(restAfter) { mutableIntStateOf(restAfter) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        WorkoutSupersetRoundsFields(
            rounds = roundsDraft,
            restBetween = restBetweenDraft,
            restAfter = restAfterDraft,
            onRoundsChange = { roundsDraft = it },
            onRestBetweenChange = { restBetweenDraft = it },
            onRestAfterChange = { restAfterDraft = it },
        )
        KpknPrimaryPill(
            text = "Guardar",
            onClick = { onSave(roundsDraft, restBetweenDraft, restAfterDraft) },
            modifier = Modifier.fillMaxWidth(),
        )
        KpknTextAction(text = "Cancelar", onClick = onCancel)
    }
}

/**
 * Las tres cifras de una superserie en un módulo: las rondas como [KpknNumberCell] escrita con teclado (de 1 a 99) y los dos descansos en
 * m:ss con el selector de tiempo, en segundos por dentro. Sin estado de negocio: quien llama guarda los valores.
 */
@Composable
internal fun WorkoutSupersetRoundsFields(
    rounds: Int,
    restBetween: Int,
    restAfter: Int,
    onRoundsChange: (Int) -> Unit,
    onRestBetweenChange: (Int) -> Unit,
    onRestAfterChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickingRest by remember { mutableStateOf<SupersetRestField?>(null) }
    val betweenLabel = KpknSetFormat.rest(restBetween)
    val afterLabel = KpknSetFormat.rest(restAfter)

    KpknModule(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
    ) {
        WorkoutStructureSettingRow(
            label = "Rondas",
            control = {
                KpknNumberCell(
                    value = rounds.toString(),
                    input = KpknCellInput.Text(keyboardType = KeyboardType.Number) { raw ->
                        raw.filter(Char::isDigit).take(2).toIntOrNull()?.let { onRoundsChange(it.coerceIn(1, 99)) }
                    },
                    contentDescription = "Rondas, $rounds",
                    modifier = Modifier.width(96.dp),
                )
            },
        )
        WorkoutStructureDivider()
        WorkoutStructureSettingRow(
            label = "Entre ejercicios",
            control = {
                KpknNumberCell(
                    value = betweenLabel,
                    input = KpknCellInput.Picker(onOpen = { pickingRest = SupersetRestField.BETWEEN }),
                    contentDescription = "Entre ejercicios, ${betweenLabel.ifEmpty { "sin tiempo" }}",
                    modifier = Modifier.width(96.dp),
                )
            },
        )
        WorkoutStructureDivider()
        WorkoutStructureSettingRow(
            label = "Después de la ronda",
            control = {
                KpknNumberCell(
                    value = afterLabel,
                    input = KpknCellInput.Picker(onOpen = { pickingRest = SupersetRestField.AFTER }),
                    contentDescription = "Después de la ronda, ${afterLabel.ifEmpty { "sin tiempo" }}",
                    modifier = Modifier.width(96.dp),
                )
            },
        )
    }

    pickingRest?.let { field ->
        val current = if (field == SupersetRestField.BETWEEN) restBetween else restAfter
        KpknNativeTimePickerDialog(
            title = if (field == SupersetRestField.BETWEEN) "Descanso entre ejercicios" else "Descanso después de la ronda",
            initialHour = (current / 60).coerceIn(0, 23),
            initialMinute = (current % 60).coerceIn(0, 59),
            hint = "Minutos : segundos",
            onConfirm = { hour, minute ->
                val seconds = (hour * 60 + minute).coerceAtLeast(0)
                if (field == SupersetRestField.BETWEEN) onRestBetweenChange(seconds) else onRestAfterChange(seconds)
                pickingRest = null
            },
            onDismiss = { pickingRest = null },
        )
    }
}

/** Qué descanso de la superserie abre el selector de tiempo. */
private enum class SupersetRestField { BETWEEN, AFTER }
