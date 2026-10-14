package com.example.kpkn.screens.workout.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.WorkoutContextProfile
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.workout.persistentTagName
import com.example.kpkn.screens.workout.tagDisplayTitle
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknType

/**
 * Cuerpo de la hoja «Etiquetas» de un ejercicio de la sesión en vivo. Una etiqueta por ejercicio: el chip elegido se aplica al tocarlo
 * (como antes), el campo «Nueva etiqueta» aplica lo escrito con «Aplicar» y «Eliminar etiqueta» la quita. [suggestedTag] va primero con
 * su icono de calendario y una nota. «Guardar» aplica lo escrito si cambió y cierra la hoja.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WorkoutTagSheetBody(
    currentTag: String?,
    userTags: List<String>,
    suggestedTag: String?,
    profiles: List<WorkoutContextProfile>,
    onTagSet: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tagText by remember { mutableStateOf(currentTag ?: "") }

    fun profileForTagText(tag: String): WorkoutContextProfile? =
        profiles.firstOrNull { profile ->
            profile.tagId == tag ||
                profile.setupLabel == tag ||
                profile.persistentTagName() == tag ||
                profile.tagDisplayTitle() == tag
        }

    fun displayTag(tag: String): String =
        profileForTagText(tag)?.tagDisplayTitle() ?: tag

    val commonTags = remember(userTags, suggestedTag, profiles) {
        val base = userTags.map(::displayTag).filter { it.isNotBlank() }.distinct()
        val displayedSuggested = suggestedTag?.let(::displayTag)
        if (displayedSuggested != null && displayedSuggested !in base) {
            listOf(displayedSuggested) + base
        } else {
            base
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        KpknModule {
            KpknModuleHeader(label = "Elige una")
            Spacer(Modifier.height(12.dp))
            if (commonTags.isEmpty()) {
                Text(
                    text = "Aún no hay etiquetas. Escribe una abajo.",
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    commonTags.forEach { tag ->
                        KpknChip(
                            text = tag,
                            selected = tagText == tag,
                            onClick = {
                                tagText = tag
                                onTagSet(tag)
                            },
                            leadingIcon = if (tag == suggestedTag) KpknLineIcons.Calendar else null,
                        )
                    }
                }
            }
            if (suggestedTag != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Sugerida por tu historial",
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                )
            }
        }

        KpknTextField(
            value = tagText,
            onValueChange = { tagText = it },
            label = "Nueva etiqueta",
            singleLine = true,
            trailing = {
                KpknIconButton(
                    icon = KpknLineIcons.Check,
                    contentDescription = "Aplicar",
                    onClick = { onTagSet(tagText) },
                )
            },
        )

        if (!currentTag.isNullOrBlank()) {
            KpknTextAction(
                text = "Eliminar etiqueta",
                color = WizardColors.danger,
                onClick = {
                    onTagSet("")
                    tagText = ""
                },
            )
        }

        KpknPrimaryPill(
            text = "Guardar",
            onClick = {
                if (tagText.isNotBlank() && tagText.trim() != (currentTag?.trim() ?: "")) {
                    onTagSet(tagText.trim())
                }
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
