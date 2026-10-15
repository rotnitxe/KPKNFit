package com.example.kpkn.screens.sessioneditor.components.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.*
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.SupersetDraft
import com.example.kpkn.screens.sessioneditor.components.SupersetRestPickerButton
import com.example.kpkn.ui.components.KpknRestPickerChain
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType

/**
 * Crear superserie (ER1c2): la lista de los ejercicios libres de la sesión, por grupo, con casillas. El actual va marcado y se marcan de
 * 2 a 4. Con cuatro marcados, el resto se apaga con la nota «Máximo 4». El botón fijo crea la superserie con los marcados, en el orden
 * en que se marcaron.
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
    val groups = remember(sessionExercises, sessionParts) { supersetPickerGroups(sessionExercises, sessionParts) }
    val hasFree = groups.any { it.second.isNotEmpty() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 640.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Crear superserie", style = KpknType.dialogTitle, color = WizardColors.text)
            Text(
                text = if (full) "Máximo 4" else "Marca de 2 a 4 ejercicios de esta sesión",
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )
        }
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
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
                    KpknModule(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)) {
                        if (name != null) KpknModuleHeader(label = name)
                        members.forEachIndexed { index, exercise ->
                            val checked = exercise.id in selectedIds
                            SupersetPickRow(
                                exercise = exercise,
                                checked = checked,
                                selectable = checked || !full,
                                onToggle = {
                                    onUpdateDraft(
                                        draft.copy(exerciseIds = toggleSupersetMember(draft.exerciseIds, exercise.id)),
                                    )
                                },
                            )
                            if (index < members.lastIndex) SessionSheetDivider()
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KpknTextAction(
                text = "Abrir catálogo",
                onClick = onOpenCatalog,
                icon = KpknLineIcons.ChevronRight,
            )
            KpknTextAction(
                text = "Cerrar",
                onClick = onDismiss,
                color = WizardColors.textMuted,
            )
        }
        KpknPrimaryPill(
            text = if (selectedCount >= 2) "Crear superserie con $selectedCount" else "Crear superserie",
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth(),
            tag = "superset-picker-confirm",
            enabled = selectedCount >= 2,
        )
    }
}

/**
 * Los grupos de la lista: cada parte de la sesión con sus ejercicios libres y, al final, los sueltos. Un ejercicio que ya es miembro de
 * otra superserie no se ofrece. Sin partes, una sola lista sin cabecera.
 */
private fun supersetPickerGroups(
    exercises: List<Exercise>,
    parts: List<SessionPart>,
): List<Pair<String?, List<Exercise>>> {
    val free = exercises.filterNot { it.isInSuperset() }
    if (parts.isEmpty()) return listOf<Pair<String?, List<Exercise>>>(null to free)
    val freeById = free.associateBy { it.id }
    val partGroups: List<Pair<String?, List<Exercise>>> = parts.map { part ->
        (part.name.ifBlank { "Grupo" }) to part.exercises.mapNotNull { freeById[it.id] }
    }
    val partIds = parts.flatMap { part -> part.exercises.map { it.id } }.toSet()
    val loose = free.filter { it.id !in partIds }
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
