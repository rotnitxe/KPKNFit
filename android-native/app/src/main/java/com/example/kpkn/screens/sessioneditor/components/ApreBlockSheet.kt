package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.ApreBlockReference
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.UnilateralTarget
import com.example.kpkn.data.models.isEffectivelyUnilateral
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.domain.training.Apre
import com.example.kpkn.domain.training.ApreVariant
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.formatEditableNumber
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType
import java.util.UUID

/** La sustitución se confirma en la hoja y conserva la identidad y las referencias del ejercicio. */
internal fun Exercise.withApreBlock(variant: ApreVariant, newId: () -> String = { UUID.randomUUID().toString() }): Exercise {
    val oneRm = reference1RM ?: return this
    val block = Apre.block(oneRm, variant)
    if (block.isEmpty()) return this
    val prescribed = block.map { item ->
        val side = if (isEffectivelyUnilateral()) UnilateralTarget(weight = item.loadKg, targetReps = item.targetReps) else null
        ExerciseSet(
        id = newId(), weight = item.loadKg, targetReps = item.targetReps,
        purpose = item.purpose, amrapMinimumReps = item.minimumReps, calibrationScope = null,
        loadModeV2 = LoadModeV2.LOAD, leftTarget = side, rightTarget = side?.copy(),
    ) }
    return copy(trainingMode = TrainingMode.REPS, sets = prescribed,
        apreBlock = ApreBlockReference(variant.xrmReps, prescribed.map { it.id }))
}

@Composable
internal fun ApreBlockSheet(exercise: Exercise, onApply: ((Exercise) -> Exercise) -> Unit, onDismiss: () -> Unit) {
    var variant by remember { mutableStateOf(ApreVariant.APRE_6) }
    val block = exercise.reference1RM?.let { Apre.block(it, variant) }.orEmpty()
    KpknSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Bloque APRE", style = KpknType.dialogTitle, color = WizardColors.text)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ApreVariant.entries.forEach { option -> KpknChip(text = option.label, selected = variant == option,
                    onClick = { variant = option }) }
            }
            if (block.isEmpty()) Text("Falta el 1RM. Indícalo en Carga inteligente antes de crear el bloque.",
                style = KpknType.caption, color = WizardColors.danger)
            block.forEach { item -> Text("S${item.ordinal} · ${formatEditableNumber(item.loadKg).replace('.', ',')}\u00a0kg · " +
                (item.minimumReps?.let { "$it+" } ?: "${item.targetReps} reps"), style = KpknType.bodyStrong, color = WizardColors.text) }
            Text("S3 es AMRAP. Su resultado ajusta S4 con la tabla de Mann; S4 propone el XRM de la próxima semana.",
                style = KpknType.caption, color = WizardColors.textMuted)
            Text("Al aplicar se sustituirán las ${exercise.sets.size} series actuales por estas cuatro.",
                style = KpknType.body, color = WizardColors.textMuted)
            KpknPrimaryPill(text = "Aplicar bloque APRE", enabled = block.isNotEmpty(), modifier = Modifier.fillMaxWidth(),
                onClick = { onApply { it.withApreBlock(variant) }; onDismiss() })
            KpknTextAction(text = "Cancelar", onClick = onDismiss)
        }
    }
}
