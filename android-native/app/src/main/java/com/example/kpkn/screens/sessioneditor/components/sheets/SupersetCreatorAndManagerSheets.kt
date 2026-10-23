package com.example.kpkn.screens.sessioneditor.components.sheets

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.*
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.sessioneditor.SupersetDraft
import com.example.kpkn.screens.sessioneditor.components.SupersetRestPickerButton
import com.example.kpkn.ui.components.KpknRestPickerChain
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknCellInput
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknNumberCell
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSetFormat
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.design.KpknModuleHeader

/** Pasos del stepper de una hora de descanso: el mismo que en el resto de la app (SE6b). */
private const val REST_STEP_SECONDS = 15

/** Tope y suelo del descanso en segundos: 0 a 10 minutos, el de siempre. */
private const val REST_MAX_SECONDS = 600
private const val REST_MIN_SECONDS = 0

/** Atajos de descanso del tablero C2: «entre ejercicios» y «tras la ronda». */
private val RestBetweenPresets = listOf(0, 30, 60, 90)
private val RestAfterPresets = listOf(60, 90, 120, 180)

/** Rondas que se muestran cuando el borrador no las trae y ningún miembro tiene series aún. */
private const val DEFAULT_PICKER_ROUNDS = 3

/**
 * Crear superserie (ED-D · C2): el único creador de superseries del editor, para todas las entradas. La lista sale agrupada por partes,
 * con casillas circulares que muestran la letra del orden (A, B, C, D); debajo, «Desde el catálogo» y el módulo de rondas y descansos.
 * Con cuatro marcados el resto se apaga con la nota «Máximo 4». El botón fijo crea la superserie con los marcados, en el orden en que se
 * marcaron.
 */
@Composable
internal fun SupersetMemberPickerSheet(
    draft: SupersetDraft,
    sessionExercises: List<Exercise>,
    onUpdateDraft: (SupersetDraft) -> Unit,
    onConfirm: () -> Unit,
    onOpenCatalog: () -> Unit,
    onDismiss: () -> Unit,
    /** Partes de la sesión, para agrupar la lista. Sin ellas, todos los ejercicios van en una sola lista. */
    sessionParts: List<SessionPart> = emptyList(),
) {
    val selectedIds = draft.exerciseIds.distinct()
    val selectedCount = selectedIds.size
    val full = selectedCount >= SupersetMaxMembers
    val groups = remember(sessionExercises, sessionParts, draft.sourceGroupId, selectedIds) {
        supersetPickerGroups(sessionExercises, sessionParts, draft.sourceGroupId, selectedIds.toSet())
    }
    val hasFree = groups.any { it.second.isNotEmpty() }
    val accent = SymbolPalette.mente
    // Las rondas del borrador pueden ser nulas: entonces el motor las deriva de las series de los miembros. Aquí se muestra ese mismo
    // número para no mentir, y solo se escribe en el borrador cuando alguien toca el stepper: nadie cambia datos sin tocarlos.
    val derivedRounds = remember(selectedIds, sessionExercises) {
        selectedIds.mapNotNull { id -> sessionExercises.firstOrNull { it.id == id }?.sets?.size }
            .maxOrNull()
            ?.coerceAtLeast(1)
            ?: DEFAULT_PICKER_ROUNDS
    }
    val minimumRounds = if (selectedIds.isEmpty()) 1 else derivedRounds
    var rounds by rememberSaveable(draft.partId, draft.sourceGroupId, selectedCount) { mutableStateOf(draft.rounds ?: derivedRounds) }
    var restBetween by rememberSaveable(draft.partId, draft.sourceGroupId, selectedCount) { mutableStateOf(draft.restBetweenExercises) }
    var restAfter by rememberSaveable(draft.partId, draft.sourceGroupId, selectedCount) { mutableStateOf(draft.restAfterSuperset) }

    val effectiveRounds = rounds.coerceAtLeast(minimumRounds)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 720.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("SUPERSERIE", style = KpknType.eyebrow, color = accent)
                Text("$selectedCount de $SupersetMaxMembers", style = KpknType.eyebrow, color = accent)
            }
            Text("Crear superserie", style = KpknType.dialogTitle, color = WizardColors.text)
            Text(
                text = if (full) "Máximo 4" else "Marca de 2 a 4 ejercicios de esta sesión",
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )
        }
        Column(
            modifier = Modifier
                .testTag("superset-picker-content")
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!hasFree) {
                Text(
                    "No hay ejercicios libres en esta sesión.",
                    style = KpknType.body,
                    color = WizardColors.textMuted,
                )
            }
            groups.forEach { (name, members) ->
                if (members.isNotEmpty()) {
                    SupersetPickerGroup(
                        name = name,
                        exercises = members,
                        selectedIds = selectedIds,
                        accent = accent,
                        full = full,
                        onToggle = { exercise ->
                            onUpdateDraft(
                                draft.copy(exerciseIds = toggleSupersetMember(draft.exerciseIds, exercise.id)),
                            )
                        },
                    )
                }
            }
            // C2: el atajo al catálogo va punteado y a todo lo ancho, sobre la configuración.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.dp, WizardColors.text.copy(alpha = 0.22f), RoundedCornerShape(24.dp))
                    .clickable(role = Role.Button, onClick = onOpenCatalog),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Desde el catálogo", style = KpknType.chip, color = WizardColors.text)
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = KpknLineIcons.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = WizardColors.text,
                )
            }
            KpknModule(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                SupersetConfigStepperRow(
                    label = "Rondas",
                    value = "$effectiveRounds",
                    contentDescription = "Rondas: $effectiveRounds",
                    minusDescription = "Quitar una ronda",
                    plusDescription = "Añadir una ronda",
                    minusEnabled = effectiveRounds > minimumRounds,
                    onStep = { delta ->
                        val next = (effectiveRounds + delta).coerceIn(minimumRounds, maxOf(10, minimumRounds))
                        rounds = next
                        onUpdateDraft(draft.copy(rounds = next))
                    },
                )
                SessionSheetDivider()
                SupersetConfigRestRow(
                    title = "Descanso entre ejercicios",
                    seconds = restBetween,
                    presets = RestBetweenPresets,
                    onChange = { seconds ->
                        restBetween = seconds
                        onUpdateDraft(draft.copy(restBetweenExercises = seconds))
                    },
                )
                SessionSheetDivider()
                SupersetConfigRestRow(
                    title = "Descanso tras la ronda",
                    seconds = restAfter,
                    presets = RestAfterPresets,
                    onChange = { seconds ->
                        restAfter = seconds
                        onUpdateDraft(draft.copy(restAfterSuperset = seconds))
                    },
                )
                Spacer(Modifier.height(8.dp))
            }
        }
        SupersetPickerActions(selectedCount, onConfirm, onDismiss)
    }
}

/** Con letra grande, la acción principal conserva su ancho y la lista sigue siendo desplazable. */
@Composable
private fun SupersetPickerActions(selectedCount: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val label = if (selectedCount >= 2) "Crear superserie con $selectedCount" else "Crear superserie"
    if (LocalDensity.current.fontScale >= 1.2f) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            KpknPrimaryPill(text = label, onClick = onConfirm, modifier = Modifier.fillMaxWidth(),
                tag = "superset-picker-confirm", enabled = selectedCount >= 2)
            KpknTextAction(text = "Cancelar", onClick = onDismiss, color = WizardColors.textMuted)
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KpknTextAction(text = "Cancelar", onClick = onDismiss, color = WizardColors.textMuted)
            KpknPrimaryPill(text = label, onClick = onConfirm, modifier = Modifier.weight(1f),
                tag = "superset-picker-confirm", enabled = selectedCount >= 2)
        }
    }
}

/** Un grupo de la lista: el rótulo de la parte y sus filas, una por ejercicio libre. */
@Composable
private fun SupersetPickerGroup(
    name: String?,
    exercises: List<Exercise>,
    selectedIds: List<String>,
    accent: Color,
    full: Boolean,
    onToggle: (Exercise) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = (name ?: "Ejercicios").uppercase(),
            style = KpknType.eyebrow,
            color = WizardColors.textFaint,
            modifier = Modifier.padding(start = 10.dp, bottom = 4.dp),
        )
        exercises.forEach { exercise ->
            val checked = exercise.id in selectedIds
            SupersetPickRow(
                exercise = exercise,
                checked = checked,
                letter = if (checked) ('A' + selectedIds.indexOf(exercise.id)).toString() else null,
                accent = accent,
                selectable = checked || !full,
                onToggle = { onToggle(exercise) },
            )
        }
    }
}

/** Una fila del stepper C2: «−», la cifra en `KpknNumberCell` (SE1) y «+». */
@Composable
private fun SupersetConfigStepperRow(
    label: String,
    value: String,
    contentDescription: String,
    minusDescription: String,
    plusDescription: String,
    onStep: (Int) -> Unit,
    minusEnabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = KpknType.bodyStrong,
            color = WizardColors.text,
            modifier = Modifier.weight(1f),
        )
        KpknIconButton(
            icon = KpknLineIcons.Minus,
            contentDescription = minusDescription,
            enabled = minusEnabled,
            onClick = { onStep(-1) },
        )
        KpknNumberCell(
            value = value,
            input = KpknCellInput.ReadOnly,
            contentDescription = contentDescription,
            onStep = onStep,
            modifier = Modifier.width(72.dp),
        )
        KpknIconButton(
            icon = KpknLineIcons.Plus,
            contentDescription = plusDescription,
            onClick = { onStep(1) },
        )
    }
}

/** Un descanso del módulo C2: título con sus ±15 s y, debajo, los atajos del tablero. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SupersetConfigRestRow(
    title: String,
    seconds: Int,
    presets: List<Int>,
    onChange: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SupersetConfigStepperRow(
            label = title,
            value = KpknSetFormat.doseTime(seconds),
            contentDescription = "$title: ${KpknSetFormat.doseTime(seconds)}",
            minusDescription = "Quitar 15 segundos",
            plusDescription = "Añadir 15 segundos",
            onStep = { delta ->
                onChange((seconds + delta * REST_STEP_SECONDS).coerceIn(REST_MIN_SECONDS, REST_MAX_SECONDS))
            },
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            presets.forEach { sec ->
                KpknChip(
                    text = KpknSetFormat.doseTime(sec),
                    selected = seconds == sec,
                    onClick = { onChange(sec) },
                )
            }
        }
    }
}

/**
 * Los grupos de la lista: cada parte de la sesión con sus ejercicios libres y, al final, los sueltos. Un ejercicio que ya es miembro de
 * otra superserie no se ofrece. Sin partes, una sola lista sin cabecera.
 */
private fun supersetPickerGroups(
    exercises: List<Exercise>,
    parts: List<SessionPart>,
    sourceGroupId: String?,
    selectedIds: Set<String>,
): List<Pair<String?, List<Exercise>>> {
    val available = exercises.filter { exercise ->
        !exercise.isInSuperset() || exercise.id in selectedIds ||
            (sourceGroupId != null && exercise.supersetGroupRefOrLegacyId() == sourceGroupId)
    }
    if (parts.isEmpty()) return listOf<Pair<String?, List<Exercise>>>(null to available)
    val availableById = available.associateBy { it.id }
    val partGroups: List<Pair<String?, List<Exercise>>> = parts.map { part ->
        (part.name.ifBlank { "Grupo" }) to part.exercises.mapNotNull { availableById[it.id] }
    }
    val partIds = parts.flatMap { part -> part.exercises.map { it.id } }.toSet()
    val loose = available.filter { it.id !in partIds }
    return partGroups + listOf<Pair<String?, List<Exercise>>>("Ejercicios sueltos" to loose)
}

@Composable
internal fun SupersetManagerSheet(
    exercises: List<Exercise>,
    partId: String?,
    supersetId: String,
    restBetweenSeconds: Int?,
    restAfterSeconds: Int?,
    onUpdateRestBetween: (String?, String, Int) -> Unit,
    onUpdateRestAfter: (String?, String, Int) -> Unit,
    onRemove: (String?, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var restBetween by rememberSaveable(supersetId, restBetweenSeconds) { mutableStateOf(restBetweenSeconds ?: 60) }
    var restAfter by rememberSaveable(supersetId, restAfterSeconds) { mutableStateOf(restAfterSeconds ?: 120) }
    var pickingRest by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 620.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Gestionar superserie", style = KpknType.dialogTitle, color = WizardColors.text)
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "${exercises.size} ejercicios vinculados",
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )
            KpknModule(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                exercises.forEachIndexed { index, exercise ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 56.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "${index + 1}",
                            style = KpknType.metricM,
                            color = WizardColors.text,
                            modifier = Modifier.width(24.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                exercise.name,
                                style = KpknType.bodyStrong,
                                color = WizardColors.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${exercise.sets.size} series · descanso individual reemplazado",
                                style = KpknType.caption,
                                color = WizardColors.textMuted,
                            )
                        }
                        KpknIconButton(
                            icon = KpknLineIcons.Close,
                            contentDescription = "Quitar ${exercise.name}",
                            onClick = {
                                onRemove(partId, exercise.id)
                                onDismiss()
                            },
                        )
                    }
                    if (index < exercises.lastIndex) SessionSheetDivider()
                }
            }
            KpknModule(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)) {
                KpknModuleHeader(label = "Descansos")
                Spacer(Modifier.height(8.dp))
                SupersetRestPickerButton(
                    restBetweenSeconds = restBetween,
                    restAfterSeconds = restAfter,
                    accentColor = WizardColors.text,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { pickingRest = true },
                )
            }
        }
    }

    if (pickingRest) {
        KpknRestPickerChain(
            primaryTitle = "Descanso entre ejercicios",
            primarySeconds = restBetween,
            secondaryTitle = "Descanso post-superserie",
            secondarySeconds = restAfter,
            onConfirm = { between, after ->
                val nextAfter = after ?: restAfter
                if (between != restBetween) onUpdateRestBetween(partId, supersetId, between)
                if (nextAfter != restAfter) onUpdateRestAfter(partId, supersetId, nextAfter)
                restBetween = between
                restAfter = nextAfter
                pickingRest = false
            },
            onDismiss = { pickingRest = false },
        )
    }
}
