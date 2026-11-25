package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.LimitingFactorCategory
import com.example.kpkn.domain.training.insights.LimitingFactorCatalog
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.workout.PostExerciseFeedbackDraft
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknType

/**
 * Selector de factores limitantes post-ejercicio: permite indicar el cuello de botella real
 * (trabajo como agarre/bracing, músculo específico, articulación o capacidad cardiovascular),
 * mostrando en tiempo real qué músculos secundarios/core quedan asociados.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LimitingFactorSheet(
    draft: PostExerciseFeedbackDraft,
    focusName: String,
    primaryMuscles: List<String>,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val suggestedIds = remember(primaryMuscles) { LimitingFactorCatalog.suggestedIdsFor(primaryMuscles) }
    val cleanQuery = query.trim().lowercase()
    val filteredEntries = remember(cleanQuery) {
        if (cleanQuery.isEmpty()) LimitingFactorCatalog.entries
        else LimitingFactorCatalog.entries.filter { def ->
            def.label.lowercase().contains(cleanQuery) ||
                def.keywords.any { kw -> kw.lowercase().contains(cleanQuery) }
        }
    }
    val selectedReports = draft.limitingFactorIds.map { id ->
        LimitingFactorCatalog.resolveReport(id, primaryMuscles)
    }

    KpknSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .testTag("limiting-factor-sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "¿Qué te limitó en $focusName?",
                style = KpknType.dialogTitle,
                color = WizardColors.text,
            )
            Text(
                text = "Opcional: marca si otro músculo, articulación, agarre o el aire frenó la serie antes del músculo objetivo.",
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )

            KpknTextField(
                value = query,
                onValueChange = { query = it },
                label = "Buscar factor",
                placeholder = "Ej. Agarre, lumbar, rodilla, cardio…",
                modifier = Modifier.testTag("limiting-factor-search"),
            )

            if (selectedReports.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Seleccionados y relación muscular", style = KpknType.eyebrow, color = WizardColors.textMuted)
                    selectedReports.forEach { report ->
                        val muscleNote = buildList {
                            report.linkedMuscleIds
                                .map(LimitingFactorCatalog::canonicalMuscleDisplayName)
                                .distinct()
                                .takeIf { it.isNotEmpty() }
                                ?.let { add(it.joinToString(", ")) }
                            if (report.autoLinkedCore) add("Core incluido automáticamente")
                        }.joinToString(" · ")
                        val line = if (muscleNote.isNotEmpty()) "${report.label} → $muscleNote" else report.label
                        Text(text = line, style = KpknType.caption, color = SymbolPalette.musculo)
                    }
                }
            }

            if (cleanQuery.isEmpty() && suggestedIds.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Frecuentes en este movimiento", style = KpknType.eyebrow, color = WizardColors.textMuted)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        suggestedIds.mapNotNull(LimitingFactorCatalog::findById).forEach { def ->
                            KpknChip(
                                text = def.label,
                                selected = draft.isLimitingFactorSelected(def.id),
                                onClick = { draft.toggleLimitingFactor(def.id) },
                                modifier = Modifier.testTag("limiting-factor-chip-${def.id}"),
                            )
                        }
                    }
                }
            }

            listOf(
                LimitingFactorCategory.MOVEMENT_WORK,
                LimitingFactorCategory.CARDIO,
                LimitingFactorCategory.MUSCLE,
                LimitingFactorCategory.JOINT,
            ).forEach { category ->
                val items = filteredEntries.filter { it.category == category }
                if (items.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = LimitingFactorCatalog.categoryLabel(category),
                            style = KpknType.eyebrow,
                            color = WizardColors.textMuted,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items.forEach { def ->
                                KpknChip(
                                    text = def.label,
                                    selected = draft.isLimitingFactorSelected(def.id),
                                    onClick = { draft.toggleLimitingFactor(def.id) },
                                    modifier = Modifier.testTag("limiting-factor-chip-${category.name.lowercase()}-${def.id}"),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            KpknPrimaryPill(
                text = "Listo",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                tag = "limiting-factor-done",
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}
