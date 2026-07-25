package com.example.kpkn.screens.sessioneditor.components.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.exercises.*
import com.example.kpkn.screens.sessioneditor.SupersetDraft
import com.example.kpkn.screens.sessioneditor.EditorMiniField
import com.example.kpkn.screens.sessioneditor.safeIntOrNull
import com.example.kpkn.screens.sessioneditor.components.SupersetRestPickerButton
import com.example.kpkn.screens.sessioneditor.components.SupersetRestPickerDialog
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue

@Composable
internal fun SupersetCreatorSheet(
    draft: SupersetDraft,
    sessionExercises: List<Exercise>,
    onUpdateDraft: (SupersetDraft) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showRestPicker by rememberSaveable { mutableStateOf(false) }
    val selectedExercises = remember(draft.exerciseIds, sessionExercises) {
        draft.exerciseIds.mapNotNull { id -> sessionExercises.find { it.id == id } }
    }
    val availableExercises = remember(sessionExercises, draft.exerciseIds) {
        sessionExercises.filter { exercise ->
            exercise.id in draft.exerciseIds || !exercise.isInSuperset()
        }
    }

    fun toggleExercise(exerciseId: String) {
        val nextIds = if (exerciseId in draft.exerciseIds) {
            draft.exerciseIds.filterNot { it == exerciseId }
        } else {
            (draft.exerciseIds + exerciseId).take(4)
        }
        onUpdateDraft(draft.copy(exerciseIds = nextIds.distinct()))
    }

    fun moveSelectedExercise(exerciseId: String, delta: Int) {
        val currentIndex = draft.exerciseIds.indexOf(exerciseId)
        if (currentIndex < 0) return
        val targetIndex = (currentIndex + delta).coerceIn(0, draft.exerciseIds.lastIndex)
        if (targetIndex == currentIndex) return
        val nextIds = draft.exerciseIds.toMutableList().also { ids ->
            val moved = ids.removeAt(currentIndex)
            ids.add(targetIndex, moved)
        }
        onUpdateDraft(draft.copy(exerciseIds = nextIds))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 520.dp)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Crear superserie", fontWeight = FontWeight.Black, fontSize = 18.sp)
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Elige entre 2 y 4 ejercicios de la sesión, ordena la secuencia y configura descansos.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text("Seleccionar ejercicios", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                availableExercises.forEach { exercise ->
                    val selected = exercise.id in draft.exerciseIds
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth().clickable(enabled = selected || draft.exerciseIds.size < 4) { toggleExercise(exercise.id) },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Checkbox(
                                checked = selected,
                                onCheckedChange = { toggleExercise(exercise.id) },
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(exercise.name, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold)
                                if (exercise.id in draft.exerciseIds) {
                                    Text(
                                        "Orden ${draft.exerciseIds.indexOf(exercise.id) + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                } else if (draft.exerciseIds.size >= 4) {
                                    Text(
                                        "Limite 4 ejercicios",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Text("Orden de la superserie", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
            if (selectedExercises.isEmpty()) {
                Text(
                    "Selecciona ejercicios arriba para armar la superserie.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    selectedExercises.forEachIndexed { index, exercise ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text("${index + 1}", fontWeight = FontWeight.Black, modifier = Modifier.width(24.dp))
                                Text(exercise.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                IconButton(onClick = { moveSelectedExercise(exercise.id, -1) }, enabled = index > 0) {
                                    Icon(Icons.Default.KeyboardArrowUp, null)
                                }
                                IconButton(onClick = { moveSelectedExercise(exercise.id, 1) }, enabled = index < selectedExercises.lastIndex) {
                                    Icon(Icons.Default.KeyboardArrowDown, null)
                                }
                                IconButton(onClick = { toggleExercise(exercise.id) }) {
                                    Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }

            SupersetRestPickerButton(
                restBetweenSeconds = draft.restBetweenExercises,
                restAfterSeconds = draft.restAfterSuperset,
                accentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
                onClick = { showRestPicker = true },
            )
            if (showRestPicker) {
                SupersetRestPickerDialog(
                    initialRestBetweenSeconds = draft.restBetweenExercises,
                    initialRestAfterSeconds = draft.restAfterSuperset,
                    accentColor = MaterialTheme.colorScheme.primary,
                    onDismiss = { showRestPicker = false },
                    onConfirm = { restBetween, restAfter ->
                        onUpdateDraft(
                            draft.copy(
                                restBetweenExercises = restBetween,
                                restAfterSuperset = restAfter,
                            ),
                        )
                        showRestPicker = false
                    },
                )
            }
            EditorMiniField(
                label = "Rondas (opcional)",
                value = draft.rounds?.toString().orEmpty(),
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                onCommit = { input ->
                    val clean = input.filter { it.isDigit() }.take(3)
                    onUpdateDraft(draft.copy(rounds = clean.toIntOrNull()))
                },
            )
        }

        // Action buttons
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                Text("Cancelar")
            }
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                enabled = draft.exerciseIds.distinct().size in 2..4,
            ) {
                Text("Crear superserie", fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
internal fun SupersetMemberPickerSheet(
    draft: SupersetDraft,
    sessionExercises: List<Exercise>,
    onUpdateDraft: (SupersetDraft) -> Unit,
    onConfirm: () -> Unit,
    onOpenCatalog: () -> Unit,
    onDismiss: () -> Unit,
) {
    val selectedIds = draft.exerciseIds.toSet()
    val availableExercises = remember(sessionExercises, draft.exerciseIds) {
        sessionExercises.filter { it.id !in selectedIds && !it.isInSuperset() }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 520.dp)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Añadir a superserie", fontWeight = FontWeight.Black, fontSize = 18.sp)
        Text(
            "Mueve un ejercicio que ya está en esta sesión o abre el catálogo para crear otro.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(
            modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            availableExercises.forEach { exercise ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = draft.exerciseIds.size < 4) {
                            onUpdateDraft(draft.copy(exerciseIds = (draft.exerciseIds + exercise.id).distinct().take(4)))
                            onConfirm()
                        },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(exercise.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (availableExercises.isEmpty()) {
                Text("No hay ejercicios libres en esta sesión.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        OutlinedButton(onClick = onOpenCatalog, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Abrir catálogo")
        }
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cerrar") }
    }
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
    var restBetween by rememberSaveable(supersetId, restBetweenSeconds) {
        mutableStateOf(restBetweenSeconds?.toString().orEmpty())
    }
    var restAfter by rememberSaveable(supersetId, restAfterSeconds) {
        mutableStateOf(restAfterSeconds?.toString().orEmpty())
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 520.dp)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Gestionar superserie", fontWeight = FontWeight.Black, fontSize = 18.sp)
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "${exercises.size} ejercicios vinculados",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            exercises.forEachIndexed { index, exercise ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("${index + 1}", fontWeight = FontWeight.Black, modifier = Modifier.width(24.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(exercise.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${exercise.sets.size} series · descanso individual reemplazado",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(
                            onClick = { onRemove(partId, exercise.id); onDismiss() },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            EditorMiniField(
                label = "Descanso entre ejercicios (s)",
                value = restBetween,
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            ) { input ->
                restBetween = input
                input.safeIntOrNull()?.let { onUpdateRestBetween(partId, supersetId, it) }
            }
            EditorMiniField(
                label = "Descanso post-superserie (s)",
                value = restAfter,
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            ) { input ->
                restAfter = input
                input.safeIntOrNull()?.let { onUpdateRestAfter(partId, supersetId, it) }
            }
        }
    }
}
