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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import com.example.kpkn.screens.sessioneditor.DarkChoiceChip
import com.example.kpkn.screens.sessioneditor.DarkEditorSurfaceSoft
import com.example.kpkn.screens.sessioneditor.sessionEditorDayLabel
import com.example.kpkn.screens.sessioneditor.DarkEditorChip
import com.example.kpkn.screens.sessioneditor.DarkEditorChipSelected
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

    Column(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Clonador de sesiones", fontWeight = FontWeight.Black, fontSize = 18.sp)
            Text(
                "Copia esta sesión a varios días o trae una sesión de otro día/semana/bloque.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DarkChoiceChip(
                    label = "Copiar hacia",
                    selected = mode == SessionClonerMode.CLONE_TO_DAYS,
                    onClick = { mode = SessionClonerMode.CLONE_TO_DAYS },
                )
                DarkChoiceChip(
                    label = "Traer desde",
                    selected = mode == SessionClonerMode.IMPORT_FROM_DAY,
                    onClick = { mode = SessionClonerMode.IMPORT_FROM_DAY },
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SessionCloneApplyMode.entries.forEach { candidate ->
                    DarkChoiceChip(
                        label = if (candidate == SessionCloneApplyMode.APPEND) "Agregar" else "Reemplazar",
                        selected = applyMode == candidate,
                        onClick = { applyModeName = candidate.name },
                    )
                }
            }
        }

        if (mode == SessionClonerMode.CLONE_TO_DAYS) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Selecciona días destino", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                uiState.cloneDayOptions
                    .filterNot { it.isCurrentSessionDay }
                    .forEach { target ->
                        val selected = target.key in selectedTargetKeys
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    selectedTargetKeys = if (selected) {
                                        selectedTargetKeys - target.key
                                    } else {
                                        selectedTargetKeys + target.key
                                    }
                                },
        shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                else DarkEditorSurfaceSoft,
                            ),
                        ) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "${sessionEditorDayLabel(target.dayOfWeek)} · ${target.weekName}",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp,
                                    )
                                    Text(
                                        "${target.blockName} · ${target.mesoName}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    if (target.existingSessionId != null) {
                                        "Destino: ${target.existingSessionName?.ifBlank { "Sesión" } ?: "Sesión"}"
                                    } else {
                                        "Sin sesión"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (target.existingSessionId != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                )
                            }
                        }
                    }
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(DarkEditorSurfaceSoft)
                        .clickable { clonePartial = !clonePartial }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Clonación parcial (ejercicios seleccionados)")
                    DarkChoiceChip(
                        label = if (clonePartial) "ON" else "OFF",
                        selected = clonePartial,
                        onClick = { clonePartial = !clonePartial },
                    )
                }

                if (clonePartial) {
                    availableExercises.forEach { exercise ->
                        val selected = exercise.exerciseId in selectedCloneExerciseIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    selectedCloneExerciseIds = if (selected) {
                                        selectedCloneExerciseIds - exercise.exerciseId
                                    } else {
                                        selectedCloneExerciseIds + exercise.exerciseId
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (selected) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                Box(Modifier.size(24.dp).clip(CircleShape).background(DarkEditorChip))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(exercise.name, fontWeight = FontWeight.SemiBold)
                                Text(exercise.sourcePartName ?: "Sin grupo", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            ) {
                Button(
                    onClick = {
                        onCloneCurrentToTargets(
                            selectedTargetKeys,
                            if (clonePartial) selectedCloneExerciseIds else null,
                            applyMode,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = DarkEditorChipSelected,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Text("Clonar hacia días seleccionados", fontWeight = FontWeight.Black)
                }
            }
        } else {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Selecciona sesión origen", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                uiState.cloneSourceOptions.forEach { source ->
                    val selected = selectedSourceSessionId == source.sessionId
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { selectedSourceSessionId = source.sessionId },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            else DarkEditorSurfaceSoft,
                        ),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(source.sessionName, fontWeight = FontWeight.Black, fontSize = 14.sp)
                                Text(
                                    "${sessionEditorDayLabel(source.dayOfWeek)} · ${source.weekName} · ${source.blockName} · ${source.mesoName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                "${source.exerciseCount} ejercicios",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(DarkEditorSurfaceSoft)
                        .clickable { importPartial = !importPartial }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Importación parcial (ejercicios seleccionados)")
                    DarkChoiceChip(
                        label = if (importPartial) "ON" else "OFF",
                        selected = importPartial,
                        onClick = { importPartial = !importPartial },
                    )
                }

                if (importPartial && sourceOption != null) {
                    sourceOption.exercises.forEach { exercise ->
                        val selected = exercise.exerciseId in selectedImportExerciseIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    selectedImportExerciseIds = if (selected) {
                                        selectedImportExerciseIds - exercise.exerciseId
                                    } else {
                                        selectedImportExerciseIds + exercise.exerciseId
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (selected) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                Box(Modifier.size(24.dp).clip(CircleShape).background(DarkEditorChip))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(exercise.name, fontWeight = FontWeight.SemiBold)
                                Text(exercise.sourcePartName ?: "Sin grupo", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            ) {
                Button(
                    onClick = {
                        val sourceId = selectedSourceSessionId ?: return@Button
                        onImportFromSource(
                            sourceId,
                            if (importPartial) selectedImportExerciseIds else null,
                            applyMode,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selectedSourceSessionId != null,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = DarkEditorChipSelected,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        disabledContainerColor = DarkEditorChip,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Text("Traer sesión al editor actual", fontWeight = FontWeight.Black)
                }
            }
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
        Text("Guardar cambios", fontWeight = FontWeight.Black, fontSize = 18.sp)
        Text(
            if (onDiscardSwitch != null) {
                "Hay cambios sin guardar. Si guardas, continuarás editando la sesión destino. Si descartas, los cambios se perderán."
            } else if (isSimpleProgram) {
                "Este programa es simple: el guardado aplica solo a esta sesión."
            } else {
                "Puedes guardar solo esta sesión o propagar el mismo molde al mesociclo."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!isSimpleProgram) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = saveScope == SessionSaveScope.SESSION_ONLY,
                    onClick = { saveScope = SessionSaveScope.SESSION_ONLY },
                    label = { Text("Solo esta sesión") },
                )
                FilterChip(
                    selected = saveScope == SessionSaveScope.MESOCYCLE,
                    onClick = { saveScope = SessionSaveScope.MESOCYCLE },
                    label = { Text("Todo el mesociclo") },
                )
            }
        }
        Button(
            onClick = {
                onSave(if (isSimpleProgram) SessionSaveScope.SESSION_ONLY else saveScope)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (onDiscardSwitch != null) "Guardar y continuar" else "Guardar y volver al programa",
                fontWeight = FontWeight.Black,
            )
        }
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
