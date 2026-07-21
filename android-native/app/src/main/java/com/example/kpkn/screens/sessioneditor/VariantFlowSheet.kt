package com.example.kpkn.screens.sessioneditor

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.AspectOption
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.TechnicalAspect
import com.example.kpkn.domain.exercises.TechnicalAspectEngine
import com.example.kpkn.domain.exercises.VariantGroup
import com.example.kpkn.domain.exercises.VariantGroupIndex
import com.example.kpkn.domain.exercises.VariantPreferenceStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VariantFlowSheet(
    initialExercise: ExerciseMuscleInfo,
    sheetState: SheetState,
    onConfirm: (selectedVariant: ExerciseMuscleInfo, selectedAspects: Map<String, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val prefStore = remember { VariantPreferenceStore.getInstance(context) }

    val group = remember(initialExercise) {
        initialExercise.variantGroupId?.let { VariantGroupIndex.getGroup(it) }
    }

    if (group == null) {
        onConfirm(initialExercise, emptyMap())
        return
    }

    var step by remember { mutableStateOf(0) }
    val initialVariant = remember(group) {
        val lastId = prefStore.loadLastVariant(group.id)
        group.variants.firstOrNull { it.id == lastId } ?: initialExercise
    }
    var selectedVariant by remember { mutableStateOf(initialVariant) }
    val savedAspects = remember(group, initialVariant) {
        prefStore.loadAspectDefaults(group.id)
    }
    var selectedAspects by remember { mutableStateOf<Map<String, String>>(savedAspects) }

    val defaults = remember(group) { computeDefaults(group) }
    if (selectedAspects.isEmpty()) {
        selectedAspects = defaults
    }

    val effectiveResult = remember(selectedVariant, selectedAspects) {
        computeEffective(selectedVariant, selectedAspects)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
                .animateContentSize(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    group.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar")
                }
            }

            StepIndicator(step = step, totalSteps = 2)
            Spacer(Modifier.height(16.dp))

            when (step) {
                0 -> VariantSelectorStep(
                    group = group,
                    selectedVariant = selectedVariant,
                    onSelect = { variant ->
                        selectedVariant = variant
                        prefStore.saveLastVariant(group.id, variant.id)
                        val variantDefaults = computeDefaults(VariantGroupIndex.getGroup(group.id) ?: group)
                        if (variantDefaults.isNotEmpty() && selectedAspects.isEmpty()) {
                            selectedAspects = variantDefaults
                        }
                        step = 1
                    },
                )
                1 -> TechnicalAspectsStep(
                    variant = selectedVariant,
                    selectedAspects = selectedAspects,
                    effectiveResult = effectiveResult,
                    onAspectChange = { id, optId ->
                        val updated = selectedAspects + (id to optId)
                        selectedAspects = updated
                        prefStore.saveAspectDefaults(group.id, updated)
                    },
                )
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (step > 0) {
                    TextButton(onClick = { step-- }) {
                        Text("Atrás")
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                if (step < 1) {
                    Button(
                        onClick = { step = 1 },
                        enabled = selectedVariant.variantGroupId != null,
                    ) {
                        Text("Siguiente")
                    }
                } else {
                    Button(
                        onClick = { onConfirm(selectedVariant, selectedAspects) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Agregar ejercicio")
                    }
                }
            }
        }
    }
}

private fun computeDefaults(group: VariantGroup): Map<String, String> {
    val allAspects = group.variants.flatMap { it.technicalAspects.orEmpty() }
    return allAspects.mapNotNull { aspect ->
        val defaultId = aspect.defaultOptionId
            ?: aspect.options.firstOrNull()?.id
        defaultId?.let { aspect.id to it }
    }.toMap()
}

private fun computeEffective(
    variant: ExerciseMuscleInfo,
    selectedAspects: Map<String, String>,
): TechnicalAspectEngine.EffectiveMuscleResult {
    val selectedOptions = selectedAspects.mapNotNull { (aspectId, optId) ->
        variant.technicalAspects
            ?.firstOrNull { it.id == aspectId }
            ?.options
            ?.firstOrNull { it.id == optId }
    }
    return TechnicalAspectEngine.computeEffectiveMuscles(
        baseMuscles = variant.involvedMuscles,
        selectedOptions = selectedOptions,
    )
}

@Composable
private fun StepIndicator(step: Int, totalSteps: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0..totalSteps) {
            val isActive = i == step
            val isDone = i < step
            Box(
                modifier = Modifier
                    .then(
                        if (isActive || isDone) Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(MaterialTheme.colorScheme.primary)
                        else Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    ),
            )
            if (i < totalSteps) {
                Box(
                    modifier = Modifier
                        .width(if (isDone) 24.dp else 16.dp)
                        .height(2.dp)
                        .background(
                            if (isDone) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant
                        ),
                )
            }
        }
    }
}

@Composable
private fun VariantSelectorStep(
    group: VariantGroup,
    selectedVariant: ExerciseMuscleInfo,
    onSelect: (ExerciseMuscleInfo) -> Unit,
) {
    Text(
        "Selecciona la variante",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(12.dp))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        group.variants.forEach { variant ->
            val isSelected = variant.id == selectedVariant.id
            val primaryMuscles = variant.involvedMuscles
                .filter { it.role == MuscleRole.PRIMARY }
                .map { it.muscle }
                .take(3)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                    .then(
                        if (isSelected) Modifier.border(
                            2.dp,
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(12.dp),
                        )
                        else Modifier
                    )
                    .clickable { onSelect(variant) }
                    .padding(12.dp),
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            variant.variantName ?: variant.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        )
                        if (isSelected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        primaryMuscles.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TechnicalAspectsStep(
    variant: ExerciseMuscleInfo,
    selectedAspects: Map<String, String>,
    effectiveResult: TechnicalAspectEngine.EffectiveMuscleResult,
    onAspectChange: (aspectId: String, optionId: String) -> Unit,
) {
    val aspects = variant.technicalAspects.orEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            "Aspectos técnicos",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            variant.variantName ?: variant.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))

        aspects.forEach { aspect ->
            AspectSelectorCard(
                aspect = aspect,
                selectedOptionId = selectedAspects[aspect.id],
                onSelect = { optId -> onAspectChange(aspect.id, optId) },
            )
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        Text(
            "Activación muscular",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        MuscleActivationPreview(
            baseMuscles = variant.involvedMuscles,
            effectiveMuscles = effectiveResult.effectiveMuscles,
            summary = effectiveResult.summary,
        )
    }
}

@Composable
private fun AspectSelectorCard(
    aspect: TechnicalAspect,
    selectedOptionId: String?,
    onSelect: (String) -> Unit,
) {
    val selectedId = selectedOptionId ?: aspect.defaultOptionId
        ?: aspect.options.firstOrNull()?.id

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(12.dp),
    ) {
        Text(
            aspect.name,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
        )
        if (!aspect.description.isNullOrBlank()) {
            Text(
                aspect.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            aspect.options.forEach { option ->
                val isOptSelected = option.id == selectedId
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isOptSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable { onSelect(option.id) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        option.name,
                        fontSize = 13.sp,
                        color = if (isOptSelected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isOptSelected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }

        val selectedOption = aspect.options.firstOrNull { it.id == selectedId }
        if (selectedOption != null && !selectedOption.description.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                selectedOption.description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }

        if (selectedOption != null && selectedOption.modifiers.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                selectedOption.modifiers.joinToString(", ") { m ->
                    when (m.type) {
                        com.example.kpkn.data.models.ModifierType.SET -> "${m.muscle} → ${m.role?.name ?: ""}"
                        com.example.kpkn.data.models.ModifierType.ADD -> "${m.muscle} +${m.value}"
                        com.example.kpkn.data.models.ModifierType.MULT -> "${m.muscle} ×${m.value}"
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
internal fun MuscleActivationPreview(
    baseMuscles: List<InvolvedMuscle>,
    effectiveMuscles: List<InvolvedMuscle>,
    summary: String,
) {
    val maxBase = baseMuscles.maxOfOrNull { it.volumeContribution ?: 1.0 } ?: 1.0
    val maxEff = effectiveMuscles.maxOfOrNull { it.volumeContribution ?: 1.0 } ?: 1.0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(12.dp),
    ) {
        Text(
            "Base: $summary",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        effectiveMuscles.take(8).forEach { muscle ->
            val baseEntry = baseMuscles.firstOrNull { it.muscle == muscle.muscle }
            val baseVC = baseEntry?.volumeContribution ?: muscle.volumeContribution ?: 0.0
            val effVC = muscle.volumeContribution ?: 0.0
            val changed = baseEntry == null || baseEntry.role != muscle.role || baseVC != effVC

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        muscle.muscle,
                        fontSize = 12.sp,
                        fontWeight = if (changed) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (changed) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                    )
                    if (changed) {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "•",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        muscle.role.name.lowercase().replaceFirstChar { it.uppercase() },
                        fontSize = 11.sp,
                        color = roleColor(muscle.role),
                    )
                    val pct = (effVC * 100).toInt()
                    Text(
                        "$pct% VC",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun roleColor(role: MuscleRole): Color = when (role) {
    MuscleRole.PRIMARY -> MaterialTheme.colorScheme.primary
    MuscleRole.SECONDARY -> MaterialTheme.colorScheme.secondary
    MuscleRole.STABILIZER -> Color(0xFF8B8B8B)
    MuscleRole.NEUTRALIZER -> Color(0xFFAAAAAA)
}

object VariantFlowResultCache {
    private data class VariantResult(
        val variantName: String?,
        val variantGroupId: String?,
        val variantGroupName: String?,
        val selectedAspects: Map<String, String>,
    )

    private val results = mutableMapOf<String, VariantResult>()

    fun store(
        exerciseDbId: String,
        variantName: String?,
        variantGroupId: String?,
        variantGroupName: String?,
        selectedAspects: Map<String, String>,
    ) {
        results[exerciseDbId.lowercase()] = VariantResult(
            variantName = variantName,
            variantGroupId = variantGroupId,
            variantGroupName = variantGroupName,
            selectedAspects = selectedAspects,
        )
    }

    fun consume(exerciseDbId: String?): VariantSelection? {
        if (exerciseDbId == null) return null
        val result = results.remove(exerciseDbId.lowercase()) ?: return null
        return VariantSelection(
            variantName = result.variantName,
            variantGroupId = result.variantGroupId,
            variantGroupName = result.variantGroupName,
            selectedAspects = result.selectedAspects,
        )
    }

    data class VariantSelection(
        val variantName: String?,
        val variantGroupId: String?,
        val variantGroupName: String?,
        val selectedAspects: Map<String, String>,
    )

    fun clear() {
        results.clear()
    }
}
