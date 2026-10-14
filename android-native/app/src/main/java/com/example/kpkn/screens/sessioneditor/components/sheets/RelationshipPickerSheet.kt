package com.example.kpkn.screens.sessioneditor.components.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.ProgramExerciseCandidate
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknEmptyState
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknRadioRow
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknType

/**
 * Hoja «Relacionar ejercicio» (SE7). Una fila de radio por candidato, con su sesión y su parte en `caption`. «Quitar vínculo actual» solo
 * aparece si el ejercicio ya tiene ancla. Elegir una fila llama a [onSelectAnchor] con su id; quitar, con null.
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
    val filteredCandidates = remember(candidates, query) {
        candidates
            .filter { candidate ->
                query.isBlank() || candidate.exerciseName.contains(query, ignoreCase = true)
            }
            .sortedBy { it.exerciseName.lowercase() }
    }
    RelationshipPickerBody(
        targetExercise = targetExercise,
        candidates = filteredCandidates,
        query = query,
        onSearch = onSearch,
        onSelectAnchor = onSelectAnchor,
        onDismiss = onDismiss,
    )
}

/** El contenido de la hoja de relación, con los candidatos ya filtrados y ordenados. */
@Composable
internal fun RelationshipPickerBody(
    targetExercise: Exercise?,
    candidates: List<ProgramExerciseCandidate>,
    query: String,
    onSearch: (String) -> Unit,
    onSelectAnchor: (String?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (targetExercise == null) {
        KpknEmptyState(
            icon = KpknLineIcons.Route,
            title = "No encontramos el ejercicio que quieres vincular.",
            body = null,
        )
        return
    }

    val currentAnchorId = targetExercise.relativeToCanonicalExerciseId
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Relacionar ejercicio",
                style = KpknType.title,
                color = WizardColors.text,
                modifier = Modifier.weight(1f),
            )
            KpknIconButton(
                icon = KpknLineIcons.Close,
                contentDescription = "Cerrar",
                onClick = onDismiss,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = targetExercise.name, style = KpknType.bodyStrong, color = WizardColors.text)
            Text(
                text = "Elige el ejercicio ancla en el programa (variante, ayuda o sobrecarga).",
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )
        }

        KpknTextField(
            value = query,
            onValueChange = onSearch,
            placeholder = "Buscar ejercicio ancla",
            leadingIcon = KpknLineIcons.Search,
        )

        if (currentAnchorId != null) {
            KpknSecondaryPill(
                text = "Quitar vínculo actual",
                onClick = { onSelectAnchor(null) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (candidates.isEmpty()) {
            KpknEmptyState(
                icon = KpknLineIcons.Route,
                title = "No hay ejercicios candidatos para este vínculo.",
                body = null,
            )
        } else {
            KpknModule {
                candidates.forEachIndexed { index, candidate ->
                    if (index > 0) {
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
                    }
                    val selected = candidate.exerciseDbId == currentAnchorId ||
                        candidate.exerciseName.equals(currentAnchorId?.removePrefix("custom:"), ignoreCase = true)
                    KpknRadioRow(
                        selected = selected,
                        title = candidate.exerciseName,
                        subtitle = buildString {
                            append(candidate.sessionName)
                            if (candidate.partName != null) append(" · ${candidate.partName}")
                        },
                        onClick = { onSelectAnchor(candidate.exerciseId) },
                    )
                }
            }
        }
    }
}
