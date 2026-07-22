package com.example.kpkn.screens.sessioneditor

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.AspectOption
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.ModifierType
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.TechnicalAspect
import com.example.kpkn.domain.exercises.TechnicalAspectEngine
import com.example.kpkn.domain.exercises.VariantGroup
import com.example.kpkn.domain.exercises.VariantGroupIndex
import com.example.kpkn.domain.exercises.VariantPreferenceStore
import com.example.kpkn.screens.wikilab.wikilabMuscleColor

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

    // Opens DIRECTLY on Step 1 (Technical Aspects for selected exercise)
    var step by remember { mutableStateOf(1) }
    var selectedVariant by remember { mutableStateOf(initialExercise) }
    val savedAspects = remember(group, selectedVariant) {
        prefStore.loadAspectDefaults(group.id)
    }
    var selectedAspects by remember { mutableStateOf<Map<String, String>>(savedAspects) }

    val defaults = remember(selectedVariant) { computeDefaults(selectedVariant) }
    if (selectedAspects.isEmpty()) {
        selectedAspects = defaults
    }

    val effectiveResult = remember(selectedVariant, selectedAspects) {
        computeEffective(selectedVariant, selectedAspects)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .animateContentSize(),
        ) {
            // Header Sobrio y Profesional
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedVariant.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Configuración técnica y biomecánica",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar")
                }
            }

            Spacer(Modifier.height(14.dp))

            when (step) {
                0 -> VariantSelectorStep(
                    group = group,
                    selectedVariant = selectedVariant,
                    onSelect = { variant ->
                        selectedVariant = variant
                        prefStore.saveLastVariant(group.id, variant.id)
                        val variantDefaults = computeDefaults(variant)
                        if (variantDefaults.isNotEmpty()) {
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

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (step > 0) {
                    TextButton(
                        onClick = { step = 0 },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cambiar Variante", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                if (step < 1) {
                    Button(
                        onClick = { step = 1 },
                        enabled = selectedVariant.variantGroupId != null,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    ) {
                        Text("Ajustes Técnicos →", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = { onConfirm(selectedVariant, selectedAspects) },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.height(42.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Aplicar Ejercicio", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun computeDefaults(variant: ExerciseMuscleInfo): Map<String, String> {
    val aspects = variant.technicalAspects.orEmpty()
    return aspects.mapNotNull { aspect ->
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
private fun VariantSelectorStep(
    group: VariantGroup,
    selectedVariant: ExerciseMuscleInfo,
    onSelect: (ExerciseMuscleInfo) -> Unit,
) {
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

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(variant) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    else
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                border = if (isSelected)
                    androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                else
                    androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = variant.variantName ?: variant.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = primaryMuscles.joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (isSelected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
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
            .height(400.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Aspectos Técnicos con Segmented Controls Visibles simultáneamente
        aspects.forEach { aspect ->
            AspectSelectorCard(
                aspect = aspect,
                selectedOptionId = selectedAspects[aspect.id],
                onSelect = { optId -> onAspectChange(aspect.id, optId) },
            )
        }

        // Vista sobria de Enfoque Muscular Resultante Dinámico
        MuscleActivationPreview(
            baseMuscles = variant.involvedMuscles,
            effectiveMuscles = effectiveResult.effectiveMuscles,
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
    val selectedOption = aspect.options.firstOrNull { it.id == selectedId }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = aspect.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )

        // Segmented Control de Ancho Total - TODAS las opciones visibles al instante sin carrusel ni scroll horizontal
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            aspect.options.forEach { option ->
                val isOptSelected = option.id == selectedId
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(option.id) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isOptSelected)
                        MaterialTheme.colorScheme.primary
                    else
                        Color.Transparent,
                ) {
                    Text(
                        text = option.name,
                        modifier = Modifier.padding(vertical = 7.dp, horizontal = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isOptSelected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isOptSelected) FontWeight.Bold else FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Descripción concisa y seria de la opción activa
        selectedOption?.let { opt ->
            if (!opt.description.isNullOrBlank()) {
                Text(
                    text = opt.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun MuscleActivationPreview(
    baseMuscles: List<InvolvedMuscle>,
    effectiveMuscles: List<InvolvedMuscle>,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Enfoque Muscular Resultante",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Actualizado en tiempo real",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // Lista dinámica ordenada por dominancia muscular resultante
            effectiveMuscles.forEach { muscle ->
                val baseEntry = baseMuscles.firstOrNull { it.muscle == muscle.muscle }
                val baseVC = baseEntry?.volumeContribution ?: muscle.volumeContribution ?: 0.0
                val effVC = muscle.volumeContribution ?: 0.0
                val roleChanged = baseEntry?.role != muscle.role
                val muscleColor = wikilabMuscleColor(muscle.muscle)

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                modifier = Modifier.size(8.dp),
                                shape = CircleShape,
                                color = muscleColor,
                            ) {}
                            Text(
                                text = muscle.muscle,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (roleChanged) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "Cambio de Rol",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                text = formatRoleLabel(muscle.role),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Barra visual de intensidad proporcional
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(effVC.toFloat().coerceIn(0.1f, 1.0f))
                                .clip(RoundedCornerShape(3.dp))
                                .background(muscleColor)
                        )
                    }
                }
            }
        }
    }
}

private fun formatRoleLabel(role: MuscleRole): String = when (role) {
    MuscleRole.PRIMARY -> "Principal"
    MuscleRole.SECONDARY -> "Secundario"
    MuscleRole.STABILIZER -> "Estabilizador"
    MuscleRole.NEUTRALIZER -> "Guiado"
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
