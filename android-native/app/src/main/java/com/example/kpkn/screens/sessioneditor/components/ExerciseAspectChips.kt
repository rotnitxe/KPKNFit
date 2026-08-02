package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.TechnicalAspect
import com.example.kpkn.screens.sessioneditor.CatalogSelectionDraftBridge
import com.example.kpkn.ui.components.KpknSheetTokens

/** Defaults: each aspect → defaultOptionId or first option. */
fun defaultAspectSelection(exercise: ExerciseMuscleInfo): Map<String, String> {
    val aspects = exercise.catalogOptionAxes.orEmpty()
    if (aspects.isEmpty()) return emptyMap()
    return aspects.mapNotNull { aspect ->
        val opt = aspect.defaultOptionId
            ?: aspect.options.firstOrNull()?.id
            ?: return@mapNotNull null
        aspect.id to opt
    }.toMap()
}

@Composable
fun ExerciseAspectChipsInline(
    exercise: ExerciseMuscleInfo,
    selectedAspects: Map<String, String>,
    onAspectsChange: (Map<String, String>) -> Unit,
    highlightedOptionIds: Set<String> = emptySet(),
    onOptionInfo: ((TechnicalAspect, com.example.kpkn.data.models.AspectOption) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val aspects = exercise.catalogOptionAxes.orEmpty()
    if (aspects.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        aspects.forEach { aspect ->
            AspectChipRow(
                aspect = aspect,
                selectedOptionId = selectedAspects[aspect.id]
                    ?: aspect.defaultOptionId
                    ?: aspect.options.firstOrNull()?.id,
                onSelect = { optId ->
                    onAspectsChange(selectedAspects + (aspect.id to optId))
                },
                highlightedOptionIds = highlightedOptionIds,
                onOptionInfo = onOptionInfo,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun AspectChipRow(
    aspect: TechnicalAspect,
    selectedOptionId: String?,
    onSelect: (String) -> Unit,
    highlightedOptionIds: Set<String>,
    onOptionInfo: ((TechnicalAspect, com.example.kpkn.data.models.AspectOption) -> Unit)?,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = aspect.name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = KpknSheetTokens.MutedStrong,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            aspect.options.forEach { option ->
                val selected = option.id == selectedOptionId
                val highlighted = option.id in highlightedOptionIds
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .combinedClickable(
                            onClick = { onSelect(option.id) },
                            onLongClick = { onOptionInfo?.invoke(aspect, option) },
                        ),
                    shape = RoundedCornerShape(999.dp),
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                    } else if (highlighted) {
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.24f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    },
                    contentColor = KpknSheetTokens.Body,
                ) {
                    Text(
                        text = option.name,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}

/**
 * When [exercise] has technical aspects, keep [CatalogSelectionDraftBridge] in sync so
 * [createExerciseFromInfo] / workout replace pick up the selection without the wizard.
 */
@Composable
fun RememberAspectCacheSync(
    exercise: ExerciseMuscleInfo?,
    selectedAspects: Map<String, String>,
) {
    LaunchedEffect(exercise?.id, selectedAspects) {
        val ex = exercise ?: return@LaunchedEffect
        if (ex.catalogOptionAxes.isNullOrEmpty()) return@LaunchedEffect
        CatalogSelectionDraftBridge.store(
            exerciseDbId = ex.id,
            variantName = ex.variantName,
            variantGroupId = ex.variantGroupId,
            variantGroupName = ex.variantGroupName,
            selectedAspects = selectedAspects.ifEmpty { defaultAspectSelection(ex) },
        )
    }
}
