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

    Column(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(
                start = KpknSheetTokens.ContentPaddingHorizontal,
                end = KpknSheetTokens.ContentPaddingHorizontal,
                top = KpknSheetTokens.ContentPaddingTop,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Transferir", fontWeight = FontWeight.Black, fontSize = 18.sp, color = KpknSheetTokens.TitleStrong)
            Text(
                "Los cambios quedan en borrador hasta que guardes.",
                color = KpknSheetTokens.MutedStrong,
                style = MaterialTheme.typography.bodySmall,
            )
            if (uiState.pendingTransferToDays != null) {
                Text(
                    "Pendiente: ${uiState.pendingTransferToDays.targetKeys.size} día(s) al guardar.",
                    color = KpknSheetTokens.Body,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelMedium,
                )
            }

            // One compact toolbar row: direction + apply mode (uses horizontal space).
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KpknSheetGlassChip(
                    label = "Copiar",
                    selected = mode == SessionClonerMode.CLONE_TO_DAYS,
                    onClick = { mode = SessionClonerMode.CLONE_TO_DAYS },
                    modifier = Modifier.weight(1f),
                )
                KpknSheetGlassChip(
                    label = "Traer",
                    selected = mode == SessionClonerMode.IMPORT_FROM_DAY,
                    onClick = { mode = SessionClonerMode.IMPORT_FROM_DAY },
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier
                        .height(20.dp)
                        .width(1.dp)
                        .background(Color.White.copy(alpha = 0.2f)),
                )
                KpknSheetGlassChip(
                    label = "Agregar",
                    selected = applyMode == SessionCloneApplyMode.APPEND,
                    onClick = { applyModeName = SessionCloneApplyMode.APPEND.name },
                    modifier = Modifier.weight(1f),
                )
                KpknSheetGlassChip(
                    label = "Reemplazar",
                    selected = applyMode == SessionCloneApplyMode.REPLACE,
                    onClick = { applyModeName = SessionCloneApplyMode.REPLACE.name },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (mode == SessionClonerMode.CLONE_TO_DAYS) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = KpknSheetTokens.ContentPaddingHorizontal),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val currentWeekId = uiState.weekId
                val weekTargets = uiState.cloneDayOptions.filter {
                    !it.isCurrentSessionDay && it.weekId == currentWeekId
                }
                val sameDayTargets = uiState.cloneDayOptions.filter {
                    !it.isCurrentSessionDay && it.dayOfWeek == uiState.dayOfWeek
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Días destino",
                        fontWeight = FontWeight.Bold,
                        color = KpknSheetTokens.Body,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (weekTargets.isNotEmpty()) {
                            KpknSheetGlassChip(
                                label = "Esta semana",
                                selected = selectedTargetKeys == weekTargets.map { it.key }.toSet(),
                                onClick = {
                                    selectedTargetKeys = weekTargets.map { it.key }.toSet()
                                },
                            )
                        }
                        if (sameDayTargets.isNotEmpty()) {
                            KpknSheetGlassChip(
                                label = "Mismo día",
                                selected = selectedTargetKeys == sameDayTargets.map { it.key }.toSet(),
                                onClick = {
                                    selectedTargetKeys = sameDayTargets.map { it.key }.toSet()
                                },
                            )
                        }
                    }
                }
                uiState.cloneDayOptions
                    .filterNot { it.isCurrentSessionDay }
                    .forEach { target ->
                        val selected = target.key in selectedTargetKeys
                        TransferOptionCard(
                            selected = selected,
                            title = "${sessionEditorDayLabel(target.dayOfWeek)} · ${target.weekName}",
                            subtitle = "${target.blockName} · ${target.mesoName}",
                            meta = if (target.existingSessionId != null) {
                                "Destino: ${target.existingSessionName?.ifBlank { "Sesión" } ?: "Sesión"} · ${target.existingExerciseCount} ej."
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

                TransferToggleRow(
                    label = "Transferencia parcial",
                    enabled = clonePartial,
                    onToggle = { clonePartial = !clonePartial },
                )

                if (clonePartial) {
                    availableExercises.forEach { exercise ->
                        TransferExerciseRow(
                            name = exercise.name,
                            detail = exercise.sourcePartName ?: "Sin grupo",
                            selected = exercise.exerciseId in selectedCloneExerciseIds,
                            onClick = {
                                selectedCloneExerciseIds = if (exercise.exerciseId in selectedCloneExerciseIds) {
                                    selectedCloneExerciseIds - exercise.exerciseId
                                } else {
                                    selectedCloneExerciseIds + exercise.exerciseId
                                }
                            },
                        )
                    }
                }

                if (applyMode == SessionCloneApplyMode.REPLACE && replaceLossCount > 0) {
                    Text(
                        "Reemplazar eliminará $replaceLossCount ejercicio(s) en los destinos seleccionados.",
                        color = KpknSheetTokens.Body,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(KpknSheetTokens.ContentPaddingHorizontal),
            ) {
                KpknSheetWhiteButton(
                    text = "Preparar transferencia",
                    enabled = canSubmitClone,
                    onClick = {
                        onCloneCurrentToTargets(
                            selectedTargetKeys,
                            if (clonePartial) selectedCloneExerciseIds else null,
                            applyMode,
                        )
                    },
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = KpknSheetTokens.ContentPaddingHorizontal),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Sesión origen", fontWeight = FontWeight.Bold, color = KpknSheetTokens.Body)
                uiState.cloneSourceOptions.forEach { source ->
                    val selected = selectedSourceSessionId == source.sessionId
                    TransferOptionCard(
                        selected = selected,
                        title = source.sessionName,
                        subtitle = "${sessionEditorDayLabel(source.dayOfWeek)} · ${source.weekName} · ${source.blockName}",
                        meta = "${source.exerciseCount} ejercicios",
                        onClick = { selectedSourceSessionId = source.sessionId },
                    )
                }

                TransferToggleRow(
                    label = "Transferencia parcial",
                    enabled = importPartial,
                    onToggle = { importPartial = !importPartial },
                )

                if (importPartial && sourceOption != null) {
                    sourceOption.exercises.forEach { exercise ->
                        TransferExerciseRow(
                            name = exercise.name,
                            detail = exercise.sourcePartName ?: "Sin grupo",
                            selected = exercise.exerciseId in selectedImportExerciseIds,
                            onClick = {
                                selectedImportExerciseIds = if (exercise.exerciseId in selectedImportExerciseIds) {
                                    selectedImportExerciseIds - exercise.exerciseId
                                } else {
                                    selectedImportExerciseIds + exercise.exerciseId
                                }
                            },
                        )
                    }
                }

                if (applyMode == SessionCloneApplyMode.REPLACE) {
                    val currentCount = currentSession.allExercises().size
                    if (currentCount > 0) {
                        Text(
                            "Reemplazar sobrescribirá $currentCount ejercicio(s) de la sesión actual.",
                            color = KpknSheetTokens.Body,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(KpknSheetTokens.ContentPaddingHorizontal),
            ) {
                KpknSheetWhiteButton(
                    text = "Traer al borrador actual",
                    enabled = canSubmitImport,
                    onClick = {
                        val sourceId = selectedSourceSessionId ?: return@KpknSheetWhiteButton
                        onImportFromSource(
                            sourceId,
                            if (importPartial) selectedImportExerciseIds else null,
                            applyMode,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun TransferOptionCard(
    selected: Boolean,
    title: String,
    subtitle: String,
    meta: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(KpknSheetTokens.PanelRadius))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(KpknSheetTokens.PanelRadius),
        color = if (selected) Color.White.copy(alpha = 0.16f) else KpknSheetTokens.Panel,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Black, fontSize = 14.sp, color = KpknSheetTokens.Body)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = KpknSheetTokens.MutedStrong)
            }
            Text(meta, style = MaterialTheme.typography.labelSmall, color = KpknSheetTokens.MutedStrong)
        }
    }
}

@Composable
private fun TransferToggleRow(
    label: String,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(KpknSheetTokens.PanelRadius))
            .background(KpknSheetTokens.Panel)
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = KpknSheetTokens.Body)
        KpknSheetGlassChip(
            label = if (enabled) "ON" else "OFF",
            selected = enabled,
            onClick = onToggle,
        )
    }
}

@Composable
private fun TransferExerciseRow(
    name: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (selected) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = KpknSheetTokens.Body)
        } else {
            Box(
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f)),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(name, fontWeight = FontWeight.SemiBold, color = KpknSheetTokens.Body)
            Text(detail, style = MaterialTheme.typography.labelSmall, color = KpknSheetTokens.MutedStrong)
        }
    }
}


@Composable
internal fun SaveSheet(
    onSave: (SessionSaveScope) -> Unit,
    onDiscardSwitch: (() -> Unit)?,
    isSimpleProgram: Boolean,
) {
    var saveScope by rememberSaveable { mutableStateOf(SessionSaveScope.SESSION_ONLY) }
    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Guardar cambios", fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color.White)
        Text(
            if (onDiscardSwitch != null) {
                "Hay cambios sin guardar. Si guardas, continuarás editando la sesión destino. Si descartas, los cambios se perderán."
            } else if (isSimpleProgram) {
                "Este programa es simple: el guardado aplica solo a esta sesión."
            } else {
                "Puedes guardar solo esta sesión o propagar el mismo molde al mesociclo."
            },
            color = Color.White.copy(alpha = 0.65f),
        )
        if (!isSimpleProgram) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KpknSheetLightChip(
                    label = "Solo esta sesión",
                    selected = saveScope == SessionSaveScope.SESSION_ONLY,
                    onClick = { saveScope = SessionSaveScope.SESSION_ONLY },
                )
                KpknSheetLightChip(
                    label = "Todo el mesociclo",
                    selected = saveScope == SessionSaveScope.MESOCYCLE,
                    onClick = { saveScope = SessionSaveScope.MESOCYCLE },
                )
            }
        }
        KpknSheetWhiteButton(
            text = if (onDiscardSwitch != null) "Guardar y continuar" else "Guardar y volver al programa",
            onClick = {
                onSave(if (isSimpleProgram) SessionSaveScope.SESSION_ONLY else saveScope)
            },
        )
        if (onDiscardSwitch != null) {
            OutlinedButton(onClick = onDiscardSwitch, modifier = Modifier.fillMaxWidth()) {
                Text("Descartar y cambiar sesión")
            }
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
