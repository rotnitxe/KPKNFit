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
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import com.example.kpkn.ui.components.KpknSheetTokens
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.exercises.*
import com.example.kpkn.screens.sessioneditor.SupersetDraft
import com.example.kpkn.screens.sessioneditor.EditorMiniField
import com.example.kpkn.screens.sessioneditor.safeIntOrNull
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue

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
                    color = KpknSheetTokens.ChipIdle,
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
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp), tint = KpknSheetTokens.ControlLabel)
                        Text(
                            exercise.name,
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = KpknSheetTokens.ChipLabel,
                        )
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
                    color = Color.White,
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
