package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.exercises.*
import com.example.kpkn.screens.wikilab.components.ExerciseFatigueScenarios
import com.example.kpkn.ui.components.KpknGlass
import com.example.kpkn.ui.components.KpknGlassDialog
import androidx.compose.ui.graphics.Color

@Composable
internal fun ExerciseCatalogInfoDialog(
    exercise: ExerciseMuscleInfo,
    catalog: List<ExerciseMuscleInfo>,
    associatedDiscomforts: List<Pair<String, Int>>,
    onOpenExercise: (String) -> Unit,
    onDismiss: () -> Unit,
    onOpenVariantFlow: ((ExerciseMuscleInfo) -> Unit)? = null,
) {
    val fatigue = remember(exercise.id) { calculateFriendlyFatigue(exercise) }
    val kinship = remember(exercise.id, catalog) { buildExerciseKinships(exercise, catalog) }
    KpknGlassDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(KpknGlass.DialogCornerRadius),
    ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            exercise.name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            listOfNotNull(resolvePrimaryMuscleLabel(exercise), exercise.equipment, exercise.type).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }

                if (onOpenVariantFlow != null && !exercise.variantGroupId.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onOpenVariantFlow(exercise)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Configuración avanzada (Aspectos técnicos)")
                    }
                }

                exercise.description?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                val isHighOrLowBarSquat = remember(exercise.id, exercise.name) {
                    val id = exercise.id.lowercase()
                    val name = exercise.name.lowercase()
                    id.contains("high_bar") || id.contains("low_bar") ||
                    name.contains("barra alta") || name.contains("barra baja") ||
                    name.contains("high bar") || name.contains("low bar")
                }
                if (isHighOrLowBarSquat) {
                    val defaultVariant = if (exercise.id.lowercase().contains("low") || exercise.name.lowercase().contains("baja")) {
                        com.example.kpkn.screens.wikilab.components.SquatVariant.LOW_BAR
                    } else {
                        com.example.kpkn.screens.wikilab.components.SquatVariant.HIGH_BAR
                    }
                    com.example.kpkn.screens.wikilab.components.CaupolicanSquatInteractiveViewer(
                        initialVariant = defaultVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                com.example.kpkn.screens.wikilab.ExerciseMinimalistChipsCarousel(
                    exercise = exercise,
                    fatigueScore = fatigue.overall,
                    modifier = Modifier.fillMaxWidth()
                )

                val muscleContributions = remember(exercise.id, exercise.involvedMuscles) {
                    oneSeriesVolumeContributions(exercise)
                }
                if (muscleContributions.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Músculos involucrados",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Black
                        )
                        muscleContributions.forEach { item ->
                            val color = com.example.kpkn.screens.wikilab.wikilabMuscleColor(item.muscle)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        modifier = Modifier.size(8.dp),
                                        shape = RoundedCornerShape(50),
                                        color = color,
                                    ) {}
                                    Column {
                                        Text(
                                            item.muscle,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(
                                            roleVolumeLabel(item.role),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = color.copy(alpha = 0.08f),
                                ) {
                                    Text(
                                        formatSeriesEquivalent(item.seriesEquivalent),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = color,
                                    )
                                }
                            }
                        }
                    }
                }

                if (associatedDiscomforts.isNotEmpty()) {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Molestias asociadas", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
                            associatedDiscomforts.forEach { (label, count) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                    Text(
                                        "x$count",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Black,
                                    )
                                }
                            }
                        }
                    }
                }

                if (kinship.similar.isNotEmpty()) {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Otras opciones", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
                            Text(
                                "Mismo patrón de movimiento y perfil similar. Pulsa una opción para abrir su ficha.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(kinship.similar, key = { it.exercise.id }) { similar ->
                                    val similarFatigue = calculateFriendlyFatigue(similar.exercise).overall
                                    Card(
                                        modifier = Modifier
                                            .width(250.dp)
                                            .clickable {
                                                onDismiss()
                                                onOpenExercise(similar.exercise.id)
                                            },
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                        ),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.14f),
                                        ),
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Text(similar.exercise.name, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            Text(
                                                listOfNotNull(resolvePrimaryMuscleLabel(similar.exercise), similar.exercise.equipment, similar.exercise.type)
                                                    .joinToString(" · "),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                similar.rationale,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                fatigueLabel(similarFatigue),
                                                color = fatigueColor(similarFatigue),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Black,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (!exercise.setupCues.isNullOrEmpty() || !exercise.executionCues.isNullOrEmpty()) {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Claves rápidas", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
                            exercise.setupCues.orEmpty().take(2).forEach { cue ->
                                Text("Set-up: $cue", style = MaterialTheme.typography.bodySmall)
                            }
                            exercise.executionCues.orEmpty().take(2).forEach { cue ->
                                Text("Ejecución: $cue", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                ExerciseFatigueScenarios(exercise = exercise)
            }
    }
}

@Composable
internal fun FriendlyFatigueRow(label: String, score: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.width(72.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(score / 10f)
                    .clip(RoundedCornerShape(999.dp))
                    .background(fatigueColor(score))
            )
        }
        Text("$score/10", color = fatigueColor(score), fontWeight = FontWeight.Black)
    }
}
