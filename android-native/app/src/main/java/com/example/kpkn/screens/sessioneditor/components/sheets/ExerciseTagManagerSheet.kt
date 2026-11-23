package com.example.kpkn.screens.sessioneditor.components.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.SubTagCategory
import com.example.kpkn.data.models.WorkoutSubTag
import com.example.kpkn.data.models.WorkoutTag
import com.example.kpkn.domain.exercises.resolvedCanonicalExerciseId
import com.example.kpkn.domain.workout.WorkoutTagResolver
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.ui.components.KpknAlertConfirmButton
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknType
import java.time.Instant
import java.util.UUID

/**
 * Gestor de etiquetas y sub-etiquetas por ejercicio en el editor (#12).
 * Permite pre-crear, seleccionar, renombrar y eliminar etiquetas, y administrar sus sub-etiquetas
 * (que luego se asignan por serie individual).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExerciseTagManagerSheet(
    exercise: Exercise,
    availableTags: List<WorkoutTag>,
    onSelectTag: (String?) -> Unit,
    onSaveTag: (WorkoutTag) -> Unit,
    onDeleteTag: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val selectedTag = availableTags.firstOrNull { it.id == exercise.workoutTagId }
    var newTagName by remember { mutableStateOf("") }
    var renameTagName by remember(selectedTag?.id, selectedTag?.name) {
        mutableStateOf(selectedTag?.name.orEmpty())
    }
    var newSubTagName by remember { mutableStateOf("") }
    var newSubTagCategory by remember { mutableStateOf(SubTagCategory.LIBRE) }

    KpknSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Etiquetas del ejercicio", style = KpknType.dialogTitle, color = WizardColors.text)
                    Text(exercise.name, style = KpknType.caption, color = WizardColors.textMuted, maxLines = 1)
                }
                KpknAlertConfirmButton(text = "Listo", onClick = onDismiss)
            }

            // Selección de etiqueta activa
            KpknModule {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Etiqueta activa", style = KpknType.bodyStrong, color = WizardColors.text)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        KpknChip(
                            text = "Sin etiqueta",
                            selected = exercise.workoutTagId == null,
                            onClick = { onSelectTag(null) },
                        )
                        availableTags.forEach { tag ->
                            KpknChip(
                                text = tag.name,
                                selected = tag.id == exercise.workoutTagId,
                                onClick = { onSelectTag(tag.id) },
                            )
                        }
                    }
                }
            }

            // Crear nueva etiqueta
            KpknModule {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Nueva etiqueta", style = KpknType.bodyStrong, color = WizardColors.text)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        KpknTextField(
                            value = newTagName,
                            onValueChange = { newTagName = it },
                            label = "Nombre",
                            placeholder = "ej: Barra competición, Máquina Hammer",
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        KpknPrimaryPill(
                            text = "Crear",
                            enabled = newTagName.isNotBlank(),
                            onClick = {
                                val trimmed = newTagName.trim()
                                if (trimmed.isNotEmpty()) {
                                    val nowIso = Instant.now().toString()
                                    val created = WorkoutTagResolver.withNormalized(
                                        WorkoutTag(
                                            id = UUID.randomUUID().toString(),
                                            name = trimmed,
                                            exerciseKey = exercise.resolvedCanonicalExerciseId(),
                                            createdAtIso = nowIso,
                                            lastUsedAtIso = nowIso,
                                        ),
                                    )
                                    onSaveTag(created)
                                    onSelectTag(created.id)
                                    newTagName = ""
                                }
                            },
                        )
                    }
                }
            }

            // Sub-etiquetas + Renombrar / Borrar etiqueta seleccionada
            if (selectedTag != null) {
                KpknModule {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Sub-etiquetas de «${selectedTag.name}»", style = KpknType.bodyStrong, color = WizardColors.text)
                        Text(
                            "Puedes activarlas por serie individual en el editor y durante la sesión.",
                            style = KpknType.caption,
                            color = WizardColors.textMuted,
                        )
                        if (selectedTag.subTags.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                selectedTag.subTags.forEach { sub ->
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(KpknSurface.inset)
                                            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(999.dp))
                                            .padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(sub.name, style = KpknType.chip, color = WizardColors.text)
                                        Icon(
                                            imageVector = KpknLineIcons.Close,
                                            contentDescription = "Quitar ${sub.name}",
                                            tint = WizardColors.textMuted,
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clickable {
                                                    val nextSubs = selectedTag.subTags.filterNot { it.id == sub.id }
                                                    onSaveTag(WorkoutTagResolver.withNormalized(selectedTag.copy(subTags = nextSubs)))
                                                },
                                        )
                                    }
                                }
                            }
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            listOf(
                                SubTagCategory.LIBRE to "Libre",
                                SubTagCategory.TECNICA to "Técnica",
                                SubTagCategory.SETUP to "Setup",
                                SubTagCategory.MARCA to "Marca",
                            ).forEach { (cat, label) ->
                                KpknChip(
                                    text = label,
                                    selected = newSubTagCategory == cat,
                                    onClick = { newSubTagCategory = cat },
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            KpknTextField(
                                value = newSubTagName,
                                onValueChange = { newSubTagName = it },
                                label = "Nueva sub-etiqueta",
                                placeholder = "ej: Con pausa, Pin 4, Cadenas",
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            KpknSecondaryPill(
                                text = "Añadir",
                                enabled = newSubTagName.isNotBlank(),
                                onClick = {
                                    val subName = newSubTagName.trim()
                                    if (subName.isNotEmpty()) {
                                        val nextSub = WorkoutSubTag(
                                            id = UUID.randomUUID().toString(),
                                            name = subName,
                                            category = newSubTagCategory,
                                        )
                                        onSaveTag(
                                            WorkoutTagResolver.withNormalized(
                                                selectedTag.copy(subTags = selectedTag.subTags + nextSub),
                                            ),
                                        )
                                        newSubTagName = ""
                                    }
                                },
                            )
                        }
                    }
                }

                KpknModule {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Gestionar «${selectedTag.name}»", style = KpknType.bodyStrong, color = WizardColors.text)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            KpknTextField(
                                value = renameTagName,
                                onValueChange = { renameTagName = it },
                                label = "Renombrar",
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            KpknSecondaryPill(
                                text = "Guardar",
                                enabled = renameTagName.isNotBlank() && renameTagName.trim() != selectedTag.name,
                                onClick = {
                                    val trimmed = renameTagName.trim()
                                    if (trimmed.isNotEmpty()) {
                                        onSaveTag(WorkoutTagResolver.withNormalized(selectedTag.copy(name = trimmed)))
                                    }
                                },
                            )
                        }
                        if (!selectedTag.isDefault) {
                            Spacer(Modifier.height(2.dp))
                            KpknTextAction(
                                text = "Eliminar etiqueta",
                                color = WizardColors.danger,
                                onClick = {
                                    onDeleteTag(selectedTag.id)
                                    onSelectTag(null)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
