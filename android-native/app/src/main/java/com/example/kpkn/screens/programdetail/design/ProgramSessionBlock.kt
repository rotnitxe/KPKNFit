package com.example.kpkn.screens.programdetail.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.exercises.catalogExerciseIndex
import com.example.kpkn.data.models.isCardio
import com.example.kpkn.domain.exercises.exerciseDisplayName
import com.example.kpkn.domain.training.VariantLoadEstimates
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.programdetail.components.formatExercisePrescription
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.tour.TourAnchors
import com.example.kpkn.ui.tour.tourAnchor

/**
 * El cuerpo de una sesión desplegada en la agenda (s-open): sus ejercicios (`.ex`: el nombre, la dosis debajo y «Cambiar ejercicio» a la
 * derecha, con un filete entre uno y otro), «Sesión personalizada» con «Restaurar» si aplica y las acciones (`.sess-acts`): «Empezar» y
 * «Editar», a partes iguales. El nombre, la línea de datos y ⋯ los pone la tarjeta. Sin estado: los menús y los diálogos viven en la
 * pantalla.
 */
@Composable
internal fun ProgramSessionBlock(
    ui: ProgramSessionUi,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onRestore: (() -> Unit)?,
    modifier: Modifier = Modifier,
    startAnchor: Boolean = false,
    onSwapExercise: ((String) -> Unit)? = null,
) {
    val session = ui.session
    val exerciseLookup = remember { catalogExerciseIndex() }
    val exercises = remember(session) { session.allExercises() }
    Column(modifier = modifier.fillMaxWidth().testTag("programsession-${session.id}")) {
        exercises.forEachIndexed { index, exercise ->
            val name = exerciseDisplayName(exercise, exerciseLookup)
            if (index > 0) Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        style = ExerciseNameStyle,
                        color = WizardColors.text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    formatExercisePrescription(exercise)?.let { dose ->
                        val estimateNote = exercise.loadEstimate?.let { estimate ->
                            VariantLoadEstimates.accessibilityLabel(estimate, exerciseLookup[estimate.sourceConfigurationId]?.name)
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = dose,
                            modifier = if (estimateNote != null) Modifier.semantics { contentDescription = "$dose. $estimateNote" } else Modifier,
                            style = ExerciseDoseStyle,
                            color = WizardColors.textFaint,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (onSwapExercise != null && !exercise.isCardio && !exercise.catalogConfigurationId.isNullOrBlank() && exercise.sets.isNotEmpty()) {
                    Spacer(Modifier.width(10.dp))
                    ProgramRoundButton(
                        icon = KpknLineIcons.Swap,
                        contentDescription = "Cambiar ejercicio: $name",
                        onClick = { onSwapExercise(exercise.id) },
                        iconSize = 18.dp,
                        tint = WizardColors.textFaint,
                    )
                }
            }
        }
        if (ui.customized) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Sesión personalizada",
                style = KpknType.caption.copy(fontWeight = FontWeight.SemiBold),
                color = WizardColors.textMuted,
            )
            if (onRestore != null) {
                RestoreAction(onClick = onRestore)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ProgramLienzoPill(
                text = "Empezar",
                onClick = onStart,
                modifier = Modifier
                    .weight(1f)
                    .then(if (startAnchor) Modifier.tourAnchor(TourAnchors.TRAINING_EMPEZAR) else Modifier),
                small = true,
                icon = KpknLineIcons.PlayFilled,
            )
            Spacer(Modifier.width(8.dp))
            ProgramLienzoPill(
                text = "Editar",
                onClick = onEdit,
                modifier = Modifier.weight(1f),
                filled = false,
                small = true,
                icon = KpknLineIcons.Edit,
            )
        }
    }
}

/**
 * «Restaurar esta sesión desde el plan», en línea: una acción de texto que puede ocupar dos líneas a 130 % sin cortar la frase. La de
 * `KpknTextAction` se queda en una y acaba con elipsis.
 */
@Composable
private fun RestoreAction(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = "Restaurar esta sesión desde el plan",
            style = KpknType.action,
            color = WizardColors.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** El nombre de un ejercicio (`.ex strong`): Inter SemiBold 14 / 18. */
private val ExerciseNameStyle = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = FontWeight.SemiBold,
    fontSize = 14.sp,
    lineHeight = 18.sp,
)

/** La dosis de un ejercicio (`.ex span`): Inter 12 / 16 con cifras tabulares. */
private val ExerciseDoseStyle = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    fontFeatureSettings = "'tnum' 1",
)
