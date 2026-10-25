package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.sessions.SessionTemplate
import com.example.kpkn.domain.exercises.exerciseDisplayParts
import com.example.kpkn.domain.templates.SessionTemplateCatalogPolicy
import com.example.kpkn.domain.templates.SessionTemplateFacets
import com.example.kpkn.domain.templates.SessionTemplateFacetsBuilder
import com.example.kpkn.domain.templates.TemplateCatalogFilterLogic
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.formatEditorOneDecimal
import com.example.kpkn.screens.sessioneditor.components.sheets.SheetCaption
import com.example.kpkn.screens.sessioneditor.components.sheets.SheetEyebrow
import com.example.kpkn.screens.sessioneditor.components.sheets.SheetTag
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class TemplateExpandedDetailsLoadState(
    val facets: SessionTemplateFacets? = null,
    val estimatedVolume: Map<String, Double> = emptyMap(),
    val isLoading: Boolean = true,
)

/**
 * Tarjeta de plantilla (ER1e): un módulo con el nombre, una línea de dosis, el enfoque y, al pie, «Aplicar» como chip de tinta. Tocar la
 * cabecera la despliega (fatiga AUGE, ejercicios y volumen), como antes. [glassDark] ya no cambia la piel: el módulo es el mismo en
 * todas las superficies.
 */
@Composable
internal fun CompactTemplateCard(
    template: SessionTemplate,
    onApply: () -> Unit,
    exerciseIndex: Map<String, ExerciseMuscleInfo>,
    @Suppress("UNUSED_PARAMETER") glassDark: Boolean = false,
    facets: SessionTemplateFacets? = null,
) {
    var expanded by rememberSaveable(template.id) { mutableStateOf(false) }

    val durationMin = facets?.realDurationMinutes ?: template.estimatedDurationMinutes
    val totalSets = facets?.totalSets ?: template.session.allExercises().sumOf { it.sets.size }
    val exerciseCount = template.session.allExercises().size
    val primaryMuscles = facets?.primaryMuscles?.sortedBy { it.lowercase() }.orEmpty()
    val shortCopy = template.shortDescription.ifBlank {
        template.description.take(120).ifBlank { template.muscleGroupsSummary }
    }
    val diffText = TemplateCatalogFilterLogic.difficultyLabel(template.difficulty)
    val metaLine = buildString {
        durationMin?.let { append("$it min · ") }
        append("$exerciseCount ej. · $totalSets series · $diffText")
    }

    KpknModule(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = buildString {
                    append(template.name)
                    durationMin?.let { append(", $it minutos") }
                    append(", $exerciseCount ejercicios, $totalSets series, $diffText")
                    if (primaryMuscles.isNotEmpty()) {
                        append(", músculos: ${primaryMuscles.joinToString()}")
                    }
                }
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button) { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = template.name,
                    style = KpknType.bodyStrong,
                    color = WizardColors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (shortCopy.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    SheetCaption(
                        text = shortCopy,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                SheetCaption(text = metaLine, color = WizardColors.textFaint)
                if (primaryMuscles.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    SheetCaption(
                        text = "Enfoque: ${primaryMuscles.take(4).joinToString(" · ")}",
                        color = WizardColors.textFaint,
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = if (expanded) KpknLineIcons.ChevronUp else KpknLineIcons.ChevronDown,
                contentDescription = if (expanded) "Colapsar detalles" else "Expandir detalles",
                tint = WizardColors.textMuted,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KpknChip(
                text = "Aplicar",
                selected = true,
                onClick = onApply,
                modifier = Modifier.semantics { contentDescription = "Aplicar plantilla ${template.name}" },
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(KpknSurface.divider),
                )
                Spacer(modifier = Modifier.height(12.dp))
                TemplateExpandedDetails(
                    template = template,
                    exerciseIndex = exerciseIndex,
                    facets = facets,
                    advanced = true,
                )
            }
        }
    }
}




@Composable
internal fun TemplateExpandedDetails(
    template: SessionTemplate,
    exerciseIndex: Map<String, ExerciseMuscleInfo>,
    @Suppress("UNUSED_PARAMETER") glassDark: Boolean = false,
    facets: SessionTemplateFacets? = null,
    advanced: Boolean = false,
) {
    val detailsState by produceState(
        initialValue = TemplateExpandedDetailsLoadState(
            facets = facets,
            isLoading = facets == null,
        ),
        template.id,
        exerciseIndex.size,
        facets?.templateId,
    ) {
        try {
            val details = withContext(Dispatchers.Default) {
                val resolvedFacets = facets ?: SessionTemplateFacetsBuilder.build(template, exerciseIndex)
                val estimatedVolume = SessionTemplateCatalogPolicy.calculateSessionMuscleVolume(
                    template.session,
                    exerciseIndex,
                )
                TemplateExpandedDetailsLoadState(
                    facets = resolvedFacets,
                    estimatedVolume = estimatedVolume,
                    isLoading = false,
                )
            }
            value = details
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            value = TemplateExpandedDetailsLoadState(facets = facets, isLoading = false)
        }
    }
    val resolvedFacets = detailsState.facets ?: facets
    val estimatedVol = detailsState.estimatedVolume

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (template.description.isNotBlank()) {
            SheetCaption(text = template.description)
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SheetEyebrow("Ejercicios incluidos")
            template.session.allExercises().forEachIndexed { idx, ex ->
                val catalogInfo = ex.catalogConfigurationId
                    ?.trim()
                    ?.lowercase()
                    ?.let { exerciseIndex[it] }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        val nameParts = exerciseDisplayParts(ex, catalogInfo)
                        Text(
                            text = "${idx + 1}. ${nameParts.text}",
                            style = KpknType.caption,
                            color = WizardColors.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // Chips SOLO del catálogo (variantChips + técnica de receta vía exerciseDisplayParts). Sin parseo de id.
                        val chips = nameParts.chips.ifEmpty {
                            catalogInfo?.catalogVariantChips.orEmpty()
                        }
                        if (chips.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                chips.forEach { chip -> SheetTag(text = chip) }
                            }
                        }
                    }
                    val setsCount = ex.sets.size
                    Spacer(modifier = Modifier.width(8.dp))
                    SheetCaption(
                        text = "$setsCount ${if (setsCount == 1) "serie" else "series"}",
                        color = WizardColors.textMuted,
                    )
                }
            }
        }

        if (estimatedVol.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SheetEyebrow("Volumen estimado por músculo")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    estimatedVol.entries.sortedByDescending { it.value }.forEach { (muscle, sets) ->
                        SheetTag(text = "$muscle: ${formatEditorOneDecimal(sets)} series")
                    }
                }
            }
        }

        if (advanced) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SheetEyebrow("Detalles del plan")
                resolvedFacets?.averageTargetRpe?.let { rpe ->
                    SheetCaption("RPE medio objetivo: ${formatEditorOneDecimal(rpe)}")
                }
                if (resolvedFacets != null && resolvedFacets.primaryMuscles.isNotEmpty()) {
                    SheetCaption("Enfoque: ${resolvedFacets.primaryMuscles.sortedBy { it.lowercase() }.joinToString(" · ")}")
                }

            }
        }
    }
}
