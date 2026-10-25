package com.example.kpkn.screens.programdetail.design

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.kpkn.data.models.isCardio
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
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
import com.example.kpkn.domain.exercises.exerciseDisplayName
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.programdetail.components.formatExercisePrescription
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknCheckRow
import com.example.kpkn.ui.design.KpknCompactPill
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.tour.TourAnchors
import com.example.kpkn.ui.tour.tourAnchor

/**
 * Una sesión desplegada dentro de su día (P2): sus ejercicios con la dosis debajo de cada nombre; «Sesión personalizada» con «Restaurar»
 * si aplica; la confirmación de la sesión opcional futura; y las acciones: «Empezar» (44 dp), «Editar» y «Más opciones». [showName] pone el
 * nombre arriba, en Syne, cuando el día tiene varias sesiones. Los minutos vienen ya calculados por el estimador. Sin estado: el menú y los
 * diálogos viven en la pantalla.
 */
@Composable
internal fun ProgramSessionBlock(
    ui: ProgramSessionUi,
    showName: Boolean,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onMore: (Rect) -> Unit,
    onRestore: (() -> Unit)?,
    onToggleOptional: (() -> Unit)?,
    modifier: Modifier = Modifier,
    startAnchor: Boolean = false,
    onSwapExercise: ((String) -> Unit)? = null,
) {
    val session = ui.session
    val exerciseLookup = remember { catalogExerciseIndex() }
    val exercises = remember(session) { session.allExercises() }
    var moreBounds by remember { mutableStateOf(Rect.Zero) }
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 12.dp).testTag("programsession-${session.id}")) {
        if (showName) {
            Text(
                text = session.name,
                style = SessionNameStyle,
                color = WizardColors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
        }
        if (exercises.isEmpty()) {
            Text(text = "Sin ejercicios todavía", style = KpknType.caption, color = WizardColors.textMuted)
        }
        exercises.forEach { exercise ->
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = exerciseDisplayName(exercise,exerciseLookup),modifier = Modifier.weight(1f),
                        style = KpknType.bodyStrong,color = WizardColors.text,maxLines = 2,overflow = TextOverflow.Ellipsis)
                    if (onSwapExercise != null && !exercise.isCardio && !exercise.catalogConfigurationId.isNullOrBlank() && exercise.sets.isNotEmpty()) {
                        Box(modifier = Modifier.defaultMinSize(minWidth = 48.dp,minHeight = 48.dp)
                            .semantics { contentDescription = "Cambiar ejercicio: ${exerciseDisplayName(exercise,exerciseLookup)}" }
                            .clickable(role = Role.Button) { onSwapExercise(exercise.id) },
                            contentAlignment = Alignment.Center) { Text("⇄",style = KpknType.bodyStrong,color = WizardColors.text) }
                    }
                }
                formatExercisePrescription(exercise)?.let { dose ->
                    Text(
                        text = dose,
                        style = KpknType.caption.copy(fontFeatureSettings = "'tnum' 1"),
                        color = WizardColors.textMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
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
        val optionalDate = ui.optionalDateIso
        if (optionalDate != null && onToggleOptional != null) {
            val description = if (ui.optionalConfirmed) {
                "Retirar confirmación de sesión opcional"
            } else {
                "Confirmar sesión opcional"
            }
            KpknCheckRow(
                checked = ui.optionalConfirmed,
                title = description,
                onClick = onToggleOptional,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = description },
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            KpknCompactPill(
                text = "Empezar",
                onClick = onStart,
                modifier = if (startAnchor) Modifier.tourAnchor(TourAnchors.TRAINING_EMPEZAR) else Modifier,
            )
            Spacer(Modifier.width(8.dp))
            KpknCompactPill(text = "Editar", onClick = onEdit, filled = false)
            Spacer(Modifier.weight(1f))
            KpknIconButton(
                icon = KpknLineIcons.More,
                contentDescription = "Más opciones de la sesión",
                onClick = { onMore(moreBounds) },
                modifier = Modifier.onGloballyPositioned { moreBounds = it.boundsInWindow() },
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

/** El nombre de cada sesión cuando el día tiene varias: Syne Bold 18 / 22, como título de sección. */
private val SessionNameStyle = TextStyle(
    fontFamily = WizardFonts.display,
    fontWeight = FontWeight.Bold,
    fontSize = 18.sp,
    lineHeight = 22.sp,
)
