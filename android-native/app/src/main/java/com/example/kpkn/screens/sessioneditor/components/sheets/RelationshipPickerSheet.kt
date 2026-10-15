package com.example.kpkn.screens.sessioneditor.components.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.exercises.*
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.ProgramExerciseCandidate
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknType

/**
 * Relacionar un ejercicio con un ancla del programa (ER1e): el buscador con la lupa y la lista de candidatos, cada uno en su módulo, con
 * una marca en tinta en el elegido. Mismos datos y mismas llamadas que antes.
 */
@Composable
internal fun RelationshipPickerSheet(
    targetExercise: Exercise?,
    candidates: List<ProgramExerciseCandidate>,
    query: String,
    onSearch: (String) -> Unit,
    onSelectAnchor: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    if (targetExercise == null) {
        SheetCaption(
            text = "No encontramos el ejercicio que quieres vincular.",
            modifier = Modifier.padding(20.dp),
        )
        return
    }

    val currentAnchorId = targetExercise.relativeToCanonicalExerciseId
    val filteredCandidates = remember(candidates, query) {
        candidates
            .filter { candidate ->
                query.isBlank() || candidate.exerciseName.contains(query, ignoreCase = true)
            }
            .sortedBy { it.exerciseName.lowercase() }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Relacionar ejercicio",
                    style = KpknType.title,
                    color = WizardColors.text,
                )
                SheetCaption(targetExercise.name)
            }
            KpknIconButton(
                icon = KpknLineIcons.Close,
                contentDescription = "Cerrar",
                onClick = onDismiss,
            )
        }

        SheetCaption("Elige el ejercicio ancla en el programa (variante, ayuda o sobrecarga).")

        KpknTextField(
            value = query,
            onValueChange = onSearch,
            placeholder = "Buscar ejercicio ancla",
            leadingIcon = KpknLineIcons.Search,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (currentAnchorId != null) {
            KpknSecondaryPill(
                text = "Quitar vínculo actual",
                onClick = { onSelectAnchor(null) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (filteredCandidates.isEmpty()) {
            SheetCaption("No hay ejercicios candidatos para este vínculo.")
        } else {
            LazyColumn(
                modifier = Modifier.heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filteredCandidates, key = { it.exerciseId }) { candidate ->
                    val selected = candidate.exerciseDbId == currentAnchorId ||
                        candidate.exerciseName.equals(currentAnchorId?.removePrefix("custom:"), ignoreCase = true)
                    KpknModule(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onSelectAnchor(candidate.exerciseId) },
                        onClickLabel = candidate.exerciseName,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = candidate.exerciseName,
                                    style = KpknType.bodyStrong,
                                    color = WizardColors.text,
                                )
                                Spacer(Modifier.height(2.dp))
                                SheetCaption(
                                    text = buildString {
                                        append(candidate.sessionName)
                                        if (candidate.partName != null) append(" · ${candidate.partName}")
                                    },
                                )
                            }
                            if (selected) {
                                Spacer(Modifier.size(8.dp))
                                Icon(
                                    imageVector = KpknLineIcons.Check,
                                    contentDescription = "Elegido",
                                    tint = WizardColors.text,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
