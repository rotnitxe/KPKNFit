package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.PlannedTechnique
import com.example.kpkn.data.models.TechniqueType
import java.util.UUID

/** Canonical rest-pause defaults — not user-configurable in the editor. */
internal object RestPausePlanDefaults {
    const val PauseSeconds = 10
    const val Reps = 3
    const val MinCount = 1
    const val MaxCount = 5
    const val DefaultCount = 2
}

internal object DropSetPlanDefaults {
    const val MinDrops = 1
    const val MaxDrops = 3
    const val DefaultDrops = 1

    fun weightPctsFor(dropCount: Int): String = when (dropCount.coerceIn(MinDrops, MaxDrops)) {
        1 -> "-20"
        2 -> "-15,-25"
        else -> "-15,-25,-35"
    }
}

@Composable
internal fun InlineSetRowTechniqueChips(
    set: ExerciseSet,
    onUpdate: ((ExerciseSet) -> ExerciseSet) -> Unit,
    onConfigExpandedChange: (Boolean) -> Unit = {},
) {
    val currentTechniques = set.plannedIntensityTechniques
    val hasDropSet = currentTechniques.any { it.type == TechniqueType.DROP_SET }
    val hasRestPause = currentTechniques.any { it.type == TechniqueType.REST_PAUSE }

    var showDropSetConfig by rememberSaveable(set.id) { mutableStateOf(hasDropSet) }
    var showRestPauseConfig by rememberSaveable(set.id) { mutableStateOf(hasRestPause) }

    val configExpanded = (showDropSetConfig && hasDropSet) || (showRestPauseConfig && hasRestPause)
    androidx.compose.runtime.LaunchedEffect(configExpanded) {
        onConfigExpandedChange(configExpanded)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TechniqueToggleChip(
                label = "Drop-set",
                selected = hasDropSet,
                modifier = Modifier.weight(1f),
                onClick = {
                    if (hasDropSet) {
                        onUpdate { current ->
                            current.copy(
                                plannedIntensityTechniques = current.plannedIntensityTechniques.filter {
                                    it.type != TechniqueType.DROP_SET
                                },
                                isDropSet = false,
                            )
                        }
                        showDropSetConfig = false
                    } else {
                        onUpdate { current ->
                            val drops = DropSetPlanDefaults.DefaultDrops
                            val newTechnique = PlannedTechnique(
                                id = UUID.randomUUID().toString(),
                                type = TechniqueType.DROP_SET,
                                params = mapOf(
                                    "weightPcts" to DropSetPlanDefaults.weightPctsFor(drops),
                                    "count" to drops.toString(),
                                ),
                            )
                            current.copy(
                                plannedIntensityTechniques = current.plannedIntensityTechniques
                                    .filter { it.type != TechniqueType.REST_PAUSE } + newTechnique,
                                isDropSet = true,
                                isRestPause = false,
                            )
                        }
                        showDropSetConfig = true
                        showRestPauseConfig = false
                    }
                },
            )
            TechniqueToggleChip(
                label = "Rest-pause",
                selected = hasRestPause,
                modifier = Modifier.weight(1f),
                onClick = {
                    if (hasRestPause) {
                        onUpdate { current ->
                            current.copy(
                                plannedIntensityTechniques = current.plannedIntensityTechniques.filter {
                                    it.type != TechniqueType.REST_PAUSE
                                },
                                isRestPause = false,
                            )
                        }
                        showRestPauseConfig = false
                    } else {
                        onUpdate { current ->
                            val newTechnique = PlannedTechnique(
                                id = UUID.randomUUID().toString(),
                                type = TechniqueType.REST_PAUSE,
                                params = mapOf(
                                    "count" to RestPausePlanDefaults.DefaultCount.toString(),
                                    "pauseSeconds" to RestPausePlanDefaults.PauseSeconds.toString(),
                                    "reps" to RestPausePlanDefaults.Reps.toString(),
                                ),
                            )
                            current.copy(
                                plannedIntensityTechniques = current.plannedIntensityTechniques
                                    .filter { it.type != TechniqueType.DROP_SET } + newTechnique,
                                isRestPause = true,
                                isDropSet = false,
                            )
                        }
                        showRestPauseConfig = true
                        showDropSetConfig = false
                    }
                },
            )
        }

        AnimatedVisibility(
            visible = showDropSetConfig && hasDropSet,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            val dsTechnique = currentTechniques.firstOrNull { it.type == TechniqueType.DROP_SET }
            if (dsTechnique != null) {
                val dropCount = (dsTechnique.params["count"]?.toIntOrNull()
                    ?: dsTechnique.params["weightPcts"]?.split(",")?.size
                    ?: DropSetPlanDefaults.DefaultDrops)
                    .coerceIn(DropSetPlanDefaults.MinDrops, DropSetPlanDefaults.MaxDrops)
                TechniqueConfigPanel(title = "Drop-set programado") {
                    Text(
                        "¿Cuántos drops después de la serie normal?",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.62f),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        (DropSetPlanDefaults.MinDrops..DropSetPlanDefaults.MaxDrops).forEach { n ->
                            TechniqueStepChip(
                                label = "$n",
                                selected = dropCount == n,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    onUpdate { current ->
                                        val updated = dsTechnique.copy(
                                            params = mapOf(
                                                "weightPcts" to DropSetPlanDefaults.weightPctsFor(n),
                                                "count" to n.toString(),
                                            ),
                                        )
                                        current.copy(
                                            plannedIntensityTechniques = current.plannedIntensityTechniques.map {
                                                if (it.id == dsTechnique.id) updated else it
                                            },
                                        )
                                    }
                                },
                            )
                        }
                    }
                    Text(
                        "En vivo: sin descanso · peso bajado para ~${RestPausePlanDefaults.Reps} reps",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.45f),
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showRestPauseConfig && hasRestPause,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            val rpTechnique = currentTechniques.firstOrNull { it.type == TechniqueType.REST_PAUSE }
            if (rpTechnique != null) {
                val rpCount = (rpTechnique.params["count"]?.toIntOrNull() ?: RestPausePlanDefaults.DefaultCount)
                    .coerceIn(RestPausePlanDefaults.MinCount, RestPausePlanDefaults.MaxCount)
                TechniqueConfigPanel(title = "Rest-pause programado") {
                    Text(
                        "¿Cuántas mini-series tras la principal?",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.62f),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        (RestPausePlanDefaults.MinCount..RestPausePlanDefaults.MaxCount).forEach { n ->
                            TechniqueStepChip(
                                label = "$n",
                                selected = rpCount == n,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    onUpdate { current ->
                                        val updated = rpTechnique.copy(
                                            params = mapOf(
                                                "count" to n.toString(),
                                                "pauseSeconds" to RestPausePlanDefaults.PauseSeconds.toString(),
                                                "reps" to RestPausePlanDefaults.Reps.toString(),
                                            ),
                                        )
                                        current.copy(
                                            plannedIntensityTechniques = current.plannedIntensityTechniques.map {
                                                if (it.id == rpTechnique.id) updated else it
                                            },
                                        )
                                    }
                                },
                            )
                        }
                    }
                    Text(
                        "Pausa fija ${RestPausePlanDefaults.PauseSeconds}s · ${RestPausePlanDefaults.Reps} reps · $rpCount mini-series",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.45f),
                    )
                }
            }
        }
    }
}

@Composable
private fun TechniqueToggleChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .heightIn(min = 40.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) Color.White.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.10f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .size(14.dp),
                    tint = Color.White.copy(alpha = 0.92f),
                )
            }
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = if (selected) 0.94f else 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TechniqueConfigPanel(
    title: String,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = Color.White.copy(alpha = 0.9f),
            )
            content()
        }
    }
}

@Composable
private fun TechniqueStepChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .heightIn(min = 36.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = if (selected) 0.24f else 0.08f)),
    ) {
        Text(
            label,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Black else FontWeight.Medium,
            color = Color.White.copy(alpha = if (selected) 0.94f else 0.7f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
