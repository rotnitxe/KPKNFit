package com.example.kpkn.screens.sessioneditor.components.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.exercises.*
import java.util.UUID
import com.example.kpkn.screens.sessioneditor.SessionEditorUiState
import com.example.kpkn.screens.sessioneditor.SessionCloneApplyMode
import com.example.kpkn.screens.sessioneditor.SessionSaveScope
import com.example.kpkn.screens.sessioneditor.sessionEditorDayLabel
import com.example.kpkn.ui.components.KpknSheetGlassChip
import com.example.kpkn.ui.components.KpknSheetLightChip
import com.example.kpkn.ui.components.KpknSheetTokens
import com.example.kpkn.ui.components.KpknSheetWhiteButton
import com.example.kpkn.screens.sessioneditor.EditorMiniField
import com.example.kpkn.screens.sessioneditor.formatEditableNumber
import com.example.kpkn.screens.sessioneditor.safeIntOrNull
import com.example.kpkn.screens.sessioneditor.safeDoubleOrNull
import com.example.kpkn.screens.sessioneditor.SessionCloneExerciseOption
import com.example.kpkn.screens.sessioneditor.suggestWarmupReps
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknCheckRow
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknRadioRow
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknSwitch
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType

@Composable
internal fun SessionClonerSheet(
    uiState: SessionEditorUiState,
    onCloneCurrentToTargets: (Set<String>, Set<String>?, SessionCloneApplyMode) -> Unit,
    onImportFromSource: (String, Set<String>?, SessionCloneApplyMode) -> Unit,
) {
    val currentSession = uiState.session ?: return
    val availableExercises = remember(currentSession) {
        currentSession.parts.flatMap { part ->
            part.exercises.map { ex -> SessionCloneExerciseOption(ex.id, ex.name.ifBlank { "Ejercicio" }, part.name) }
        } + currentSession.exercises.map { ex ->
            SessionCloneExerciseOption(ex.id, ex.name.ifBlank { "Ejercicio" }, null)
        }
    }

    var mode by rememberSaveable { mutableStateOf(SessionClonerMode.CLONE_TO_DAYS) }
    var applyModeName by rememberSaveable { mutableStateOf(SessionCloneApplyMode.APPEND.name) }
    val applyMode = remember(applyModeName) {
        runCatching { SessionCloneApplyMode.valueOf(applyModeName) }.getOrElse { SessionCloneApplyMode.APPEND }
    }
    var clonePartial by rememberSaveable { mutableStateOf(false) }
    var importPartial by rememberSaveable { mutableStateOf(false) }
    var selectedTargetKeys by rememberSaveable { mutableStateOf(setOf<String>()) }
    var selectedCloneExerciseIds by rememberSaveable { mutableStateOf(setOf<String>()) }
    var selectedSourceSessionId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedImportExerciseIds by rememberSaveable { mutableStateOf(setOf<String>()) }

    val sourceOption = remember(uiState.cloneSourceOptions, selectedSourceSessionId) {
        uiState.cloneSourceOptions.firstOrNull { it.sessionId == selectedSourceSessionId }
    }
    val selectedTargets = remember(uiState.cloneDayOptions, selectedTargetKeys) {
        uiState.cloneDayOptions.filter { it.key in selectedTargetKeys && !it.isCurrentSessionDay }
    }
    val replaceLossCount = remember(selectedTargets, applyMode) {
        if (applyMode != SessionCloneApplyMode.REPLACE) 0
        else selectedTargets.sumOf { it.existingExerciseCount }
    }
    val canSubmitClone = selectedTargetKeys.isNotEmpty() &&
        (!clonePartial || selectedCloneExerciseIds.isNotEmpty())
    val canSubmitImport = selectedSourceSessionId != null &&
        (!importPartial || selectedImportExerciseIds.isNotEmpty())

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Transferir", style = KpknType.title, color = WizardColors.text)
        Text(
            text = "Los cambios quedan en borrador hasta que guardes.",
            style = KpknType.caption,
            color = WizardColors.textMuted,
        )
        if (uiState.pendingTransferToDays != null) {
            Text(
                text = "Pendiente: ${uiState.pendingTransferToDays.targetKeys.size} día(s) al guardar.",
                style = KpknType.caption,
                color = WizardColors.text,
            )
        }

        KpknSegmentedControl(
            options = listOf("Copiar", "Traer"),
            selectedIndex = if (mode == SessionClonerMode.CLONE_TO_DAYS) 0 else 1,
            onSelect = { index ->
                mode = if (index == 0) SessionClonerMode.CLONE_TO_DAYS else SessionClonerMode.IMPORT_FROM_DAY
            },
            modifier = Modifier.fillMaxWidth(),
        )
        KpknSegmentedControl(
            options = listOf("Agregar", "Reemplazar"),
            selectedIndex = if (applyMode == SessionCloneApplyMode.APPEND) 0 else 1,
            onSelect = { index ->
                applyModeName = if (index == 0) SessionCloneApplyMode.APPEND.name else SessionCloneApplyMode.REPLACE.name
            },
            modifier = Modifier.fillMaxWidth(),
        )

        if (mode == SessionClonerMode.CLONE_TO_DAYS) {
            val currentWeekId = uiState.weekId
            val weekTargets = uiState.cloneDayOptions.filter {
                !it.isCurrentSessionDay && it.weekId == currentWeekId
            }
            val sameDayTargets = uiState.cloneDayOptions.filter {
                !it.isCurrentSessionDay && it.dayOfWeek == uiState.dayOfWeek
            }
            KpknModule {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    KpknModuleHeader(label = "Sesiones destino")
                    // El bloque y el mesociclo se dicen una vez, solo si son los mismos para todos los destinos.
                    uiState.cloneDayOptions
                        .filterNot { it.isCurrentSessionDay }
                        .map { "${it.blockName} · ${it.mesoName}" }
                        .distinct()
                        .singleOrNull()
                        ?.let { context ->
                            Text(text = context, style = KpknType.caption, color = WizardColors.textMuted)
                        }
                    if (weekTargets.isNotEmpty() || sameDayTargets.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        ) {
                            if (weekTargets.isNotEmpty()) {
                                KpknChip(
                                    text = "Esta semana",
                                    selected = selectedTargetKeys == weekTargets.map { it.key }.toSet(),
                                    onClick = { selectedTargetKeys = weekTargets.map { it.key }.toSet() },
                                )
                            }
                            if (sameDayTargets.isNotEmpty()) {
                                KpknChip(
                                    text = "Mismo día",
                                    selected = selectedTargetKeys == sameDayTargets.map { it.key }.toSet(),
                                    onClick = { selectedTargetKeys = sameDayTargets.map { it.key }.toSet() },
                                )
                            }
                        }
                    }
                    uiState.cloneDayOptions
                        .filterNot { it.isCurrentSessionDay }
                        .forEachIndexed { index, target ->
                            if (index > 0) SheetDivider()
                            val selected = target.key in selectedTargetKeys
                            KpknCheckRow(
                                checked = selected,
                                title = "${sessionEditorDayLabel(target.dayOfWeek)} · ${target.weekName}",
                                subtitle = if (target.existingSessionId != null) {
                                    "${target.existingSessionName?.ifBlank { "Sesión" } ?: "Sesión"} · ${target.existingExerciseCount} ejercicios"
                                } else {
                                    "Sin sesión"
                                },
                                onClick = {
                                    selectedTargetKeys = if (selected) {
                                        selectedTargetKeys - target.key
                                    } else {
                                        selectedTargetKeys + target.key
                                    }
                                },
                            )
                        }
                }
            }

            PartialTransferModule(
                enabled = clonePartial,
                onToggle = { clonePartial = !clonePartial },
                exercises = availableExercises,
                selectedIds = selectedCloneExerciseIds,
                onToggleExercise = { id ->
                    selectedCloneExerciseIds = if (id in selectedCloneExerciseIds) {
                        selectedCloneExerciseIds - id
                    } else {
                        selectedCloneExerciseIds + id
                    }
                },
            )

            if (applyMode == SessionCloneApplyMode.REPLACE && replaceLossCount > 0) {
                Text(
                    text = "Reemplazar eliminará $replaceLossCount ejercicio(s) en los destinos seleccionados.",
                    style = KpknType.caption,
                    color = WizardColors.danger,
                )
            }

            KpknPrimaryPill(
                text = cloneButtonLabel(selectedTargets.size),
                onClick = {
                    onCloneCurrentToTargets(
                        selectedTargetKeys,
                        if (clonePartial) selectedCloneExerciseIds else null,
                        applyMode,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = canSubmitClone,
            )
        } else {
            KpknModule {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    KpknModuleHeader(label = "Sesión origen")
                    uiState.cloneSourceOptions.forEachIndexed { index, source ->
                        if (index > 0) SheetDivider()
                        KpknRadioRow(
                            selected = selectedSourceSessionId == source.sessionId,
                            title = source.sessionName,
                            subtitle = "${sessionEditorDayLabel(source.dayOfWeek)} · ${source.weekName} · ${source.blockName} · ${source.exerciseCount} ejercicios",
                            onClick = { selectedSourceSessionId = source.sessionId },
                        )
                    }
                }
            }

            PartialTransferModule(
                enabled = importPartial,
                onToggle = { importPartial = !importPartial },
                exercises = sourceOption?.exercises.orEmpty(),
                selectedIds = selectedImportExerciseIds,
                onToggleExercise = { id ->
                    selectedImportExerciseIds = if (id in selectedImportExerciseIds) {
                        selectedImportExerciseIds - id
                    } else {
                        selectedImportExerciseIds + id
                    }
                },
                visible = importPartial && sourceOption != null,
            )

            if (applyMode == SessionCloneApplyMode.REPLACE) {
                val currentCount = currentSession.allExercises().size
                if (currentCount > 0) {
                    Text(
                        text = "Reemplazar sobrescribirá $currentCount ejercicio(s) de la sesión actual.",
                        style = KpknType.caption,
                        color = WizardColors.danger,
                    )
                }
            }

            KpknPrimaryPill(
                text = "Traer al borrador actual",
                onClick = {
                    selectedSourceSessionId?.let { sourceId ->
                        onImportFromSource(
                            sourceId,
                            if (importPartial) selectedImportExerciseIds else null,
                            applyMode,
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = canSubmitImport,
            )
        }
    }
}

/** El texto de «Copiar»: «Copiar a 3 sesiones», y sin elegidas, «Copiar a sesiones» (apagado). */
private fun cloneButtonLabel(count: Int): String = when (count) {
    0 -> "Copiar a sesiones"
    1 -> "Copiar a 1 sesión"
    else -> "Copiar a $count sesiones"
}

/**
 * El interruptor de «Transferencia parcial» y, si está encendido, una fila de selección por ejercicio. Con [visible] en falso no pinta
 * nada más que el interruptor, por si el origen aún no tiene ejercicios que elegir.
 */
@Composable
private fun PartialTransferModule(
    enabled: Boolean,
    onToggle: () -> Unit,
    exercises: List<SessionCloneExerciseOption>,
    selectedIds: Set<String>,
    onToggleExercise: (String) -> Unit,
    visible: Boolean = true,
) {
    KpknModule {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TransferSwitchRow(label = "Transferencia parcial", checked = enabled, onToggle = onToggle)
            if (enabled && visible) {
                exercises.forEach { exercise ->
                    SheetDivider()
                    KpknCheckRow(
                        checked = exercise.exerciseId in selectedIds,
                        title = exercise.name,
                        subtitle = exercise.sourcePartName ?: "Sin grupo",
                        onClick = { onToggleExercise(exercise.exerciseId) },
                    )
                }
            }
        }
    }
}

/** Una fila de 56 dp con el nombre y un interruptor; toda la fila es el interruptor (`Role.Switch`). */
@Composable
private fun TransferSwitchRow(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = { onToggle() }),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            style = KpknType.bodyStrong,
            color = WizardColors.text,
            modifier = Modifier.weight(1f),
        )
        KpknSwitch(checked = checked, onCheckedChange = null)
    }
}

/** Separador de 1 dp dentro de un módulo (`divider`). */
@Composable
private fun SheetDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(KpknSurface.divider),
    )
}

@Composable
internal fun SaveSheet(
    onSave: (SessionSaveScope) -> Unit,
    onDiscardSwitch: (() -> Unit)?,
    isSimpleProgram: Boolean,
) {
    var saveScope by rememberSaveable { mutableStateOf(SessionSaveScope.SESSION_ONLY) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Guardar cambios", style = KpknType.title, color = WizardColors.text)
        Text(
            text = if (onDiscardSwitch != null) {
                "Hay cambios sin guardar. Si guardas, continuarás editando la sesión destino. Si descartas, los cambios se perderán."
            } else if (isSimpleProgram) {
                "Este programa es simple: el guardado aplica solo a esta sesión."
            } else {
                "Puedes guardar solo esta sesión o propagar el mismo molde al mesociclo."
            },
            style = KpknType.caption,
            color = WizardColors.textMuted,
        )
        if (!isSimpleProgram) {
            KpknSegmentedControl(
                options = listOf("Solo esta sesión", "Todo el mesociclo"),
                selectedIndex = if (saveScope == SessionSaveScope.SESSION_ONLY) 0 else 1,
                onSelect = { index ->
                    saveScope = if (index == 0) SessionSaveScope.SESSION_ONLY else SessionSaveScope.MESOCYCLE
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        KpknPrimaryPill(
            text = if (onDiscardSwitch != null) "Guardar y continuar" else "Guardar y volver al programa",
            onClick = {
                onSave(if (isSimpleProgram) SessionSaveScope.SESSION_ONLY else saveScope)
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (onDiscardSwitch != null) {
            KpknTextAction(
                text = "Descartar y cambiar sesión",
                onClick = onDiscardSwitch,
                color = WizardColors.danger,
            )
        }
    }
}

@Composable
internal fun WarmupSheet(
    exercise: Exercise?,
    onSave: (String, List<WarmupSetDefinition>) -> Unit,
) {
    if (exercise == null) return
    var sets by remember(exercise.id) {
        mutableStateOf(exercise.warmupSets.ifEmpty {
            listOf(WarmupSetDefinition(UUID.randomUUID().toString(), 50.0, 10))
        })
    }
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Series de aproximación · ${exercise.name}", fontWeight = FontWeight.Black, fontSize = 16.sp)

        sets.forEachIndexed { index, set ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("${index + 1}", fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium)
                        EditorMiniField(
                            label = "Carga %",
                            value = formatEditableNumber(set.percentageOfWorkingWeight),
                            keyboardType = KeyboardType.Decimal,
                            stateKey = "warmup-percent-${set.id}",
                            modifier = Modifier.weight(1f),
                            onCommit = {
                                val newPercent = it.safeDoubleOrNull() ?: set.percentageOfWorkingWeight
                                sets = sets.toMutableList().also { list ->
                                    list[index] = set.copy(
                                        percentageOfWorkingWeight = newPercent,
                                        targetReps = suggestWarmupReps(newPercent),
                                    )
                                }
                            },
                        )
                        EditorMiniField(
                            label = "Reps",
                            value = set.targetReps.toString(),
                            keyboardType = KeyboardType.Number,
                            stateKey = "warmup-reps-${set.id}",
                            modifier = Modifier.weight(1f),
                            onCommit = {
                                sets = sets.toMutableList().also { list ->
                                    list[index] = set.copy(targetReps = it.safeIntOrNull() ?: set.targetReps)
                                }
                            },
                        )
                        IconButton(onClick = { sets = sets.filterIndexed { idx, _ -> idx != index } }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        EditorMiniField(
                            label = "Descanso (s, opcional)",
                            value = set.restBetween?.toString().orEmpty(),
                            keyboardType = KeyboardType.Number,
                            stateKey = "warmup-rest-${set.id}",
                            modifier = Modifier.weight(1f),
                            onCommit = {
                                sets = sets.toMutableList().also { list ->
                                    list[index] = set.copy(restBetween = it.safeIntOrNull())
                                }
                            },
                        )
                        Spacer(Modifier.weight(1f)) // spacer to balance the delete button area
                    }
                }
            }
        }

        OutlinedButton(onClick = {
            val last = sets.lastOrNull()
            val nextPercent = ((last?.percentageOfWorkingWeight ?: 40.0) + 10).coerceAtMost(95.0)
            sets = sets + WarmupSetDefinition(UUID.randomUUID().toString(), nextPercent, suggestWarmupReps(nextPercent))
        }) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Agregar aproximación")
        }
        Button(onClick = { onSave(exercise.id, sets) }, modifier = Modifier.fillMaxWidth()) {
            Text("Guardar", fontWeight = FontWeight.Black)
        }
    }
}


internal enum class SessionClonerMode { CLONE_TO_DAYS, IMPORT_FROM_DAY }
