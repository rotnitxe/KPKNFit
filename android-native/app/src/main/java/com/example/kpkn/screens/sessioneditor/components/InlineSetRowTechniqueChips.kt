package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.exercises.*
import java.util.UUID
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue

@Composable
internal fun InlineSetRowTechniqueChips(
    set: ExerciseSet,
    onUpdate: ((ExerciseSet) -> ExerciseSet) -> Unit,
) {
// AMRAP ahora es un TrainingMode gestionado desde el selector de modo

// ─── Feature 4: Selector de técnica programada (Dropset / Rest-Pause) ─
val currentTechniques = set.plannedIntensityTechniques
val hasDropSet = currentTechniques.any { it.type == TechniqueType.DROP_SET }
val hasRestPause = currentTechniques.any { it.type == TechniqueType.REST_PAUSE }

var showDropSetConfig by rememberSaveable(set.id) { mutableStateOf(hasDropSet) }
var showRestPauseConfig by rememberSaveable(set.id) { mutableStateOf(hasRestPause) }

// Chips de técnica
Row(
    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    // Chip Drop-set
    FilterChip(
        selected = hasDropSet,
        onClick = {
            if (hasDropSet) {
                // Quitar drop-set
                onUpdate { current ->
                    current.copy(
                        plannedIntensityTechniques = current.plannedIntensityTechniques.filter { it.type != TechniqueType.DROP_SET },
                        isDropSet = false,
                    )
                }
                showDropSetConfig = false
            } else {
                // Añadir drop-set con defaults: 3 drops (-15%, -25%, -35%)
                onUpdate { current ->
                    val newTechnique = PlannedTechnique(
                        id = java.util.UUID.randomUUID().toString(),
                        type = TechniqueType.DROP_SET,
                        params = mapOf("weightPcts" to "-15,-25,-35", "count" to "3"),
                    )
                    current.copy(
                        plannedIntensityTechniques = current.plannedIntensityTechniques + newTechnique,
                        isDropSet = true,
                    )
                }
                showDropSetConfig = true
            }
        },
        label = { Text("Drop-set", style = MaterialTheme.typography.labelSmall) },
        leadingIcon = if (hasDropSet) ({ Icon(Icons.Default.Check, null, Modifier.size(12.dp)) }) else null,
        shape = RoundedCornerShape(999.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
            selectedLabelColor = MaterialTheme.colorScheme.primary,
        ),
    )
    // Chip Rest-pause
    FilterChip(
        selected = hasRestPause,
        onClick = {
            if (hasRestPause) {
                onUpdate { current ->
                    current.copy(
                        plannedIntensityTechniques = current.plannedIntensityTechniques.filter { it.type != TechniqueType.REST_PAUSE },
                        isRestPause = false,
                    )
                }
                showRestPauseConfig = false
            } else {
                onUpdate { current ->
                    val newTechnique = PlannedTechnique(
                        id = java.util.UUID.randomUUID().toString(),
                        type = TechniqueType.REST_PAUSE,
                        params = mapOf("count" to "3", "pauseSeconds" to "10", "reps" to "3"),
                    )
                    current.copy(
                        plannedIntensityTechniques = current.plannedIntensityTechniques + newTechnique,
                        isRestPause = true,
                    )
                }
                showRestPauseConfig = true
            }
        },
        label = { Text("Rest-pause", style = MaterialTheme.typography.labelSmall) },
        leadingIcon = if (hasRestPause) ({ Icon(Icons.Default.Check, null, Modifier.size(12.dp)) }) else null,
        shape = RoundedCornerShape(999.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f),
            selectedLabelColor = MaterialTheme.colorScheme.secondary,
        ),
    )
}

// Config expandida de Drop-set
if (showDropSetConfig && hasDropSet) {
    val dsTechnique = currentTechniques.firstOrNull { it.type == TechniqueType.DROP_SET }
    if (dsTechnique != null) {
        val dropPcts = (dsTechnique.params["weightPcts"] ?: "-15,-25,-35")
            .split(",").map { it.trim() }
        AnimatedVisibility(visible = true, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                ),
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "Drop-set programado",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "Mini-drops: ${dropPcts.size}  ·  Reducciones: ${dropPcts.joinToString(", ")}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // Botón para configurar número de drops
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Drops:", style = MaterialTheme.typography.labelSmall)
                        listOf(2, 3, 4).forEach { n ->
                            val isSelected = dropPcts.size == n
                            val defaultPcts = when (n) {
                                2 -> "-15,-25"
                                3 -> "-15,-25,-35"
                                4 -> "-10,-20,-30,-40"
                                else -> "-15,-25,-35"
                            }
                            OutlinedButton(
                                onClick = {
                                    onUpdate { current ->
                                        val updated = dsTechnique.copy(
                                            params = mapOf("weightPcts" to defaultPcts, "count" to n.toString()),
                                        )
                                        current.copy(
                                            plannedIntensityTechniques = current.plannedIntensityTechniques.map {
                                                if (it.id == dsTechnique.id) updated else it
                                            },
                                        )
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent,
                                    contentColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            ) {
                                Text("$n", style = MaterialTheme.typography.labelSmall, fontWeight = if (isSelected) FontWeight.Black else FontWeight.Normal)
                            }
                        }
                    }
                }
            }
        }
    }
}

// Config expandida de Rest-pause
if (showRestPauseConfig && hasRestPause) {
    val rpTechnique = currentTechniques.firstOrNull { it.type == TechniqueType.REST_PAUSE }
    if (rpTechnique != null) {
        val rpCount = rpTechnique.params["count"]?.toIntOrNull() ?: 3
        val rpPause = rpTechnique.params["pauseSeconds"]?.toIntOrNull() ?: 10
        val rpReps  = rpTechnique.params["reps"]?.toIntOrNull() ?: 3
        AnimatedVisibility(visible = true, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f),
                ),
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "Rest-pause programado",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        // Mini-sets
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Mini-series", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = {
                                    if (rpCount > 2) onUpdate { current ->
                                        val updated = rpTechnique.copy(params = rpTechnique.params + ("count" to (rpCount - 1).toString()))
                                        current.copy(plannedIntensityTechniques = current.plannedIntensityTechniques.map { if (it.id == rpTechnique.id) updated else it })
                                    }
                                }, modifier = Modifier.size(24.dp)) { Text("-", fontWeight = FontWeight.Black) }
                                Text("$rpCount", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                IconButton(onClick = {
                                    if (rpCount < 6) onUpdate { current ->
                                        val updated = rpTechnique.copy(params = rpTechnique.params + ("count" to (rpCount + 1).toString()))
                                        current.copy(plannedIntensityTechniques = current.plannedIntensityTechniques.map { if (it.id == rpTechnique.id) updated else it })
                                    }
                                }, modifier = Modifier.size(24.dp)) { Text("+", fontWeight = FontWeight.Black) }
                            }
                        }
                        // Pausa
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Pausa (s)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = {
                                    if (rpPause > 5) onUpdate { current ->
                                        val updated = rpTechnique.copy(params = rpTechnique.params + ("pauseSeconds" to (rpPause - 5).toString()))
                                        current.copy(plannedIntensityTechniques = current.plannedIntensityTechniques.map { if (it.id == rpTechnique.id) updated else it })
                                    }
                                }, modifier = Modifier.size(24.dp)) { Text("-", fontWeight = FontWeight.Black) }
                                Text("${rpPause}s", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                IconButton(onClick = {
                                    if (rpPause < 30) onUpdate { current ->
                                        val updated = rpTechnique.copy(params = rpTechnique.params + ("pauseSeconds" to (rpPause + 5).toString()))
                                        current.copy(plannedIntensityTechniques = current.plannedIntensityTechniques.map { if (it.id == rpTechnique.id) updated else it })
                                    }
                                }, modifier = Modifier.size(24.dp)) { Text("+", fontWeight = FontWeight.Black) }
                            }
                        }
                        // Reps por mini-serie
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Reps", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = {
                                    if (rpReps > 1) onUpdate { current ->
                                        val updated = rpTechnique.copy(params = rpTechnique.params + ("reps" to (rpReps - 1).toString()))
                                        current.copy(plannedIntensityTechniques = current.plannedIntensityTechniques.map { if (it.id == rpTechnique.id) updated else it })
                                    }
                                }, modifier = Modifier.size(24.dp)) { Text("-", fontWeight = FontWeight.Black) }
                                Text("$rpReps", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                IconButton(onClick = {
                                    if (rpReps < 10) onUpdate { current ->
                                        val updated = rpTechnique.copy(params = rpTechnique.params + ("reps" to (rpReps + 1).toString()))
                                        current.copy(plannedIntensityTechniques = current.plannedIntensityTechniques.map { if (it.id == rpTechnique.id) updated else it })
                                    }
                                }, modifier = Modifier.size(24.dp)) { Text("+", fontWeight = FontWeight.Black) }
                            }
                        }
                    }
                    Text(
                        "Resumen: $rpCount × $rpReps reps · Pausa ${rpPause}s entre mini-series",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    }
}
