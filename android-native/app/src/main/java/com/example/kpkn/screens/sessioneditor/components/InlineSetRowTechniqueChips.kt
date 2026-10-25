package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.PlannedTechnique
import com.example.kpkn.domain.workout.isMyoReps
import com.example.kpkn.domain.workout.MyoRepsPreset
import com.example.kpkn.data.models.TechniqueType
import com.example.kpkn.domain.workout.ScheduledTechniqueDefaults
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknSetFormat
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import java.util.UUID

/** Canonical rest-pause defaults — not user-configurable in the editor. */
internal object RestPausePlanDefaults {
    const val PauseSeconds = ScheduledTechniqueDefaults.REST_PAUSE_SECONDS
    const val Reps = ScheduledTechniqueDefaults.FOLLOW_UP_REPS
    const val MinCount = 1
    const val MaxCount = 5
    const val DefaultCount = 2
}

internal object DropSetPlanDefaults {
    const val MinDrops = 1
    const val MaxDrops = 3
    const val DefaultDrops = 1
    /** Small absolute drop so the next set still allows ~3 reps, not a dump. */
    const val DropKg = ScheduledTechniqueDefaults.DROP_KG
    const val DropReps = ScheduledTechniqueDefaults.FOLLOW_UP_REPS

    fun weightPctsFor(dropCount: Int): String = when (dropCount.coerceIn(MinDrops, MaxDrops)) {
        1 -> "-5"
        2 -> "-5,-10"
        else -> "-5,-10,-15"
    }
}

@Composable
internal fun InlineSetRowTechniqueChips(
    set: ExerciseSet,
    onUpdate: ((ExerciseSet) -> ExerciseSet) -> Unit,
    onConfigExpandedChange: (Boolean) -> Unit = {},
    presentation: SetRowPresentation = SetRowPresentation.Card,
) {
    val listSkin = presentation == SetRowPresentation.List
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
                listSkin = listSkin,
                onClick = {
                    if (hasDropSet) {
                        onUpdate { current ->
                            current.copy(
                                plannedIntensityTechniques = current.plannedIntensityTechniques.filter {
                                    it.type != TechniqueType.DROP_SET
                                },
                                isDropSet = false,
                                dropSets = emptyList(),
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
                                    // Editor-created techniques are volume-replaced.  The
                                    // marker is deliberately persisted in the existing params
                                    // blob so manual STACKED_ON_SET techniques keep their flow.
                                    "betweenMarked" to "true",
                                ),
                            )
                            current.copy(
                                plannedIntensityTechniques = current.plannedIntensityTechniques
                                    .filter { it.type != TechniqueType.REST_PAUSE } + newTechnique,
                                isDropSet = true,
                                isRestPause = false,
                                dropSets = emptyList(),
                                restPauses = emptyList(),
                            )
                        }
                        showDropSetConfig = true
                        showRestPauseConfig = false
                    }
                },
            )
            TechniqueToggleChip(
                label = "Rest-pause",
                selected = hasRestPause && !set.isMyoReps(),
                modifier = if (listSkin) Modifier else Modifier.weight(1f),
                listSkin = listSkin,
                onClick = {
                    if (hasRestPause && !set.isMyoReps()) {
                        onUpdate { current ->
                            current.copy(
                                plannedIntensityTechniques = current.plannedIntensityTechniques.filter {
                                    it.type != TechniqueType.REST_PAUSE
                                },
                                isRestPause = false,
                                restPauses = emptyList(),
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
                                    "betweenMarked" to "true",
                                ),
                            )
                            current.copy(
                                plannedIntensityTechniques = current.plannedIntensityTechniques
                                    .filter { it.type != TechniqueType.DROP_SET } + newTechnique,
                                isRestPause = true,
                                isDropSet = false,
                                dropSets = emptyList(),
                                restPauses = emptyList(),
                            )
                        }
                        showRestPauseConfig = true
                        showDropSetConfig = false
                    }
                },
            )
        }

        TechniqueToggleChip(label = "Myo-reps", selected = set.isMyoReps(), modifier = Modifier.fillMaxWidth().heightIn(min=48.dp), listSkin = listSkin,
            onClick = {
                if(set.isMyoReps()) {
                    onUpdate { current -> current.copy(isRestPause=false,restPauses=emptyList(),restAfterSeconds=null,
                        plannedIntensityTechniques=current.plannedIntensityTechniques.filterNot { it.type == TechniqueType.REST_PAUSE }) }
                    showRestPauseConfig=false
                } else {
                    onUpdate { current -> MyoRepsPreset.applyTo(current) };showRestPauseConfig=true;showDropSetConfig=false
                }
            })
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
                TechniqueConfigPanel(title = "Drop-set programado", listSkin = listSkin) {
                    Text(
                        "¿Cuántos drops después de la serie normal?",
                        style = if (listSkin) KpknType.caption else MaterialTheme.typography.labelSmall,
                        color = if (listSkin) WizardColors.textMuted else Color.White.copy(alpha = 0.62f),
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
                                listSkin = listSkin,
                                onClick = {
                                    onUpdate { current ->
                                        val updated = dsTechnique.copy(
                                            params = dsTechnique.params + mapOf(
                                                "weightPcts" to DropSetPlanDefaults.weightPctsFor(n),
                                                "count" to n.toString(),
                                                "betweenMarked" to "true",
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
                        style = if (listSkin) KpknType.caption else MaterialTheme.typography.labelSmall,
                        color = if (listSkin) WizardColors.textMuted else Color.White.copy(alpha = 0.45f),
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
                TechniqueConfigPanel(title = if(set.isMyoReps()) "Myo-reps programadas" else "Rest-pause programado", listSkin = listSkin) {
                    Text(
                        "¿Cuántas mini-series tras la principal?",
                        style = if (listSkin) KpknType.caption else MaterialTheme.typography.labelSmall,
                        color = if (listSkin) WizardColors.textMuted else Color.White.copy(alpha = 0.62f),
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
                                listSkin = listSkin,
                                onClick = {
                                    onUpdate { current ->
                                        val updated = rpTechnique.copy(
                                            params = rpTechnique.params + mapOf(
                                                "count" to n.toString(),
                                                "pauseSeconds" to (if(set.isMyoReps()) rpTechnique.params["pauseSeconds"] ?: "15" else RestPausePlanDefaults.PauseSeconds.toString()),
                                                "reps" to (if(set.isMyoReps()) rpTechnique.params["reps"] ?: "4" else RestPausePlanDefaults.Reps.toString()),
                                                "betweenMarked" to "true",
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
                    if(set.isMyoReps()) {
                        Text("Repeticiones por mini-serie",style=KpknType.caption,color=WizardColors.textMuted)
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { (3..5).forEach { reps ->
                            KpknChip("$reps",rpTechnique.params["reps"] == reps.toString(),{
                                onUpdate { current -> MyoRepsPreset.applyTo(current,rpCount,reps,rpTechnique.params["pauseSeconds"]?.toIntOrNull() ?: 15) }
                            })
                        } }
                        Text("Pausa corta",style=KpknType.caption,color=WizardColors.textMuted)
                        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf(10,15,20,25,30).forEach { seconds ->
                            KpknChip("$seconds s",rpTechnique.params["pauseSeconds"] == seconds.toString(),{
                                onUpdate { current -> MyoRepsPreset.applyTo(current,rpCount,rpTechnique.params["reps"]?.toIntOrNull() ?: 4,seconds) }
                            })
                        } }
                    }
                    Text(
                        if(set.isMyoReps()) "Activación cerca del fallo · ${rpTechnique.params["reps"]} reps · ${rpTechnique.params["pauseSeconds"]} s · $rpCount mini-series" else "Pausa fija ${KpknSetFormat.doseTime(RestPausePlanDefaults.PauseSeconds)} · ${RestPausePlanDefaults.Reps} reps · $rpCount miniseries",
                        style = if (listSkin) KpknType.caption else MaterialTheme.typography.labelSmall,
                        color = if (listSkin) WizardColors.textMuted else Color.White.copy(alpha = 0.45f),
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
    listSkin: Boolean,
    onClick: () -> Unit,
) {
    if (listSkin) {
        val tone = if (label == "Drop-set") Color(0xFFF49A6E) else Color(0xFFC9B8FF)
        Surface(modifier = modifier.heightIn(min = 88.dp).clickable(onClick = onClick),
            shape = RoundedCornerShape(16.dp), color = if (selected) tone.copy(alpha = 0.14f) else KpknSurface.inset,
            border = BorderStroke(1.dp, if (selected) tone else Color.White.copy(alpha = 0.10f))) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(label, style = KpknType.bodyStrong, color = WizardColors.text)
                Text(if (label == "Drop-set") "Sin descanso · ~${DropSetPlanDefaults.DropReps} reps"
                    else "Pausa fija ${RestPausePlanDefaults.PauseSeconds} s · ${RestPausePlanDefaults.Reps} reps",
                    style = KpknType.caption, color = WizardColors.textMuted)
            }
        }
        return
    }
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
    listSkin: Boolean,
    content: @Composable () -> Unit,
) {
    if (listSkin) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(KpknSurface.inset, RoundedCornerShape(16.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = KpknType.bodyStrong, color = WizardColors.text)
            content()
        }
        return
    }
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
    listSkin: Boolean,
    onClick: () -> Unit,
) {
    if (listSkin) {
        KpknChip(text = label, selected = selected, onClick = onClick, modifier = modifier)
        return
    }
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
