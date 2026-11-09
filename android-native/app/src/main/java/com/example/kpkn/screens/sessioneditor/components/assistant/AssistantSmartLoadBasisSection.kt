package com.example.kpkn.screens.sessioneditor.components.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SmartLoadBasis
import com.example.kpkn.data.models.WeekVariant
import com.example.kpkn.data.models.isCardio
import com.example.kpkn.domain.calculations.resolveSmartLoadReference
import com.example.kpkn.domain.sessionassistant.AssistantSuggestionPreview
import com.example.kpkn.screens.sessioneditor.AssistantSmartLoadBasisUndo

@Composable
internal fun AssistantSmartLoadBasisSection(
    session: Session?,
    activeVariant: WeekVariant,
    outcome: AssistantSmartLoadBasisUndo?,
    onApply: (SmartLoadBasis, List<String>) -> Unit,
    onUndo: () -> Unit,
) {
    val exercises = remember(session) { session?.allExercises().orEmpty().filterNot { it.isCardio }.distinctBy { it.id } }
    val exerciseIds = exercises.map { it.id }
    var selectedIds by remember(session?.id, exerciseIds) { mutableStateOf(exerciseIds.toSet()) }
    var targetBasis by rememberSaveable(session?.id) { mutableStateOf(SmartLoadBasis.TRAINING_MAX) }
    var previewVisible by remember(session?.id) { mutableStateOf(false) }
    val selected = exercises.filter { it.id in selectedIds }
    val changed = selected.filter { it.smartLoadBasis != targetBasis }
    val currentOutcome = outcome?.takeIf {
        it.variant == activeVariant && it.stamp == session?.lastModifiedAtMs
    }

    AsModule(Modifier.padding(top = 12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AsModuleHeader("Carga inteligente")
            AsCaption("Previsualiza una base para los ejercicios elegidos. Las series con %1RM o %TM explícito conservan su prescripción.")
            AsFlow {
                AsChip("1RM", targetBasis == SmartLoadBasis.ONE_RM) {
                    targetBasis = SmartLoadBasis.ONE_RM
                    previewVisible = false
                }
                AsChip("Training Max", targetBasis == SmartLoadBasis.TRAINING_MAX) {
                    targetBasis = SmartLoadBasis.TRAINING_MAX
                    previewVisible = false
                }
                AsChip("Todos", selectedIds.size == exercises.size && exercises.isNotEmpty()) {
                    selectedIds = exerciseIds.toSet()
                    previewVisible = false
                }
                AsChip("Ninguno", selectedIds.isEmpty()) {
                    selectedIds = emptySet()
                    previewVisible = false
                }
            }
            if (exercises.isEmpty()) {
                AsCaption("Añade un ejercicio de fuerza para configurar su base.")
            } else {
                exercises.forEach { exercise ->
                    val checked = exercise.id in selectedIds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(AsColors.cell)
                            .semantics {
                                contentDescription = exercise.name
                                stateDescription = if (checked) "Seleccionado" else "Sin seleccionar"
                            }
                            .asClick(role = Role.Checkbox, label = exercise.name) {
                                selectedIds = if (checked) selectedIds - exercise.id else selectedIds + exercise.id
                                previewVisible = false
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            Modifier.size(20.dp).clip(CircleShape)
                                .background(if (checked) AsColors.ink else AsColors.white(0x1f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (checked) AsText("✓", 12f, color = AsColors.onInk, weight = androidx.compose.ui.text.font.FontWeight.Bold)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            AsText(exercise.name, 13f, color = AsColors.ink, maxLines = 1)
                            val current = resolveSmartLoadReference(exercise)
                            AsCaption("Actual: ${referenceSummary(exercise.smartLoadBasis ?: SmartLoadBasis.ONE_RM, current?.kilograms)}")
                            if (previewVisible && checked) {
                                val proposed = resolveSmartLoadReference(exercise.copy(smartLoadBasis = targetBasis))
                                AsCaption("Con ${basisLabel(targetBasis)}: ${referenceSummary(targetBasis, proposed?.kilograms)}", color = AsColors.energy)
                            }
                        }
                    }
                }
            }
            AsCaption("${selected.size} ${if (selected.size == 1) "ejercicio seleccionado" else "ejercicios seleccionados"}")
            AsButton(
                "Previsualizar cambios",
                Modifier.fillMaxWidth(),
                small = true,
                enabled = selected.isNotEmpty(),
                onClick = { previewVisible = true },
            )
            if (previewVisible) {
                AsButton(
                    "Confirmar y aplicar",
                    Modifier.fillMaxWidth(),
                    small = true,
                    enabled = changed.isNotEmpty(),
                    onClick = { onApply(targetBasis, selected.map { it.id }) },
                )
            }
            currentOutcome?.let { applied ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AsCaption("${applied.exerciseNames.size} ${if (applied.exerciseNames.size == 1) "base aplicada" else "bases aplicadas"}", Modifier.weight(1f))
                    AsButton("Deshacer", small = true, kind = AsButtonKind.GHOST, onClick = onUndo)
                }
            }
        }
    }
}

private fun basisLabel(basis: SmartLoadBasis): String = when (basis) {
    SmartLoadBasis.ONE_RM -> "1RM"
    SmartLoadBasis.TRAINING_MAX -> "Training Max"
}

private fun referenceSummary(basis: SmartLoadBasis, kilograms: Double?): String =
    kilograms?.let { "${basisLabel(basis)} · ${AssistantSuggestionPreview.number(it)} kg" }
        ?: "${basisLabel(basis)} · pendiente"
