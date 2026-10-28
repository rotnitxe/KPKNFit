package com.example.kpkn.screens.workout.components

import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.components.RestPausePlanDefaults
import com.example.kpkn.screens.workout.toTrimmedNumberString
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.KpknAlertDismissButton
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknDangerPill
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknProgressBar
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknType

/**
 * Panel de la técnica guiada (LR1c2): un módulo teñido con el acento de la sesión (es lo activo). Los campos son `KpknTextField`, la
 * acción de saltar la técnica es texto, y la confirmación, un `KpknAlertDialog` destructivo porque descarta las sub-series.
 */
@Composable
internal fun GuidedTechniquePanel(
    phase: GuidedTechniquePhase,
    accentColor: Color,
    dropWeightText: String,
    dropRepsText: String,
    restPauseRepsText: String,
    onDropWeightChange: (String) -> Unit,
    onDropRepsChange: (String) -> Unit,
    onRestPauseRepsChange: (String) -> Unit,
    onSkipTechnique: () -> Unit,
    modifier: Modifier = Modifier,
    restPauseStyle: String? = null,
    pauseSeconds: Int = RestPausePlanDefaults.PauseSeconds,
    targetRestReps: Int = RestPausePlanDefaults.Reps,
    loadMode: LoadModeV2 = LoadModeV2.LOAD,
    /** «Principal: 100 kg × 8 · RIR 2» (`inline-note` de «Técnica en curso»). */
    principal: String? = null,
) {
    var confirmSkipTechnique by remember { mutableStateOf(false) }
    // «Técnica en curso» del prototipo 02: el panel de la hoja (#141513, radio 28) con el título en Syne, «Rest-pause · tramo 1 de 2», el
    // reloj de la pausa en Syne 53 o los campos del tramo, la línea de la serie principal y «Terminar técnica con lo realizado». El tramo se
    // registra con el ✓ de siempre.
    val technique = when (phase) {
        is GuidedTechniquePhase.DropSet -> "Drop-set"
        else -> if (restPauseStyle == "myo") "Myo-reps" else "Rest-pause"
    }
    val (index, total) = when (phase) {
        is GuidedTechniquePhase.DropSet -> phase.index to phase.total
        is GuidedTechniquePhase.RestPauseCountdown -> phase.index to phase.total
        is GuidedTechniquePhase.RestPauseReps -> phase.index to phase.total
    }
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(LiveSheetTokens.Panel)
            .padding(start = 19.dp, end = 19.dp, top = 12.dp, bottom = 10.dp).testTag("guided-technique-sheet"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Técnica en curso", style = androidx.compose.ui.text.TextStyle(fontFamily = com.example.kpkn.screens.onboarding.design.WizardFonts.display,
            fontWeight = FontWeight.Bold, fontSize = 21.sp, letterSpacing = (-0.4).sp), color = WizardColors.text)
        LiveSheetSubtitle("$technique · tramo ${index + 1} de $total")
        when (phase) {
            is GuidedTechniquePhase.RestPauseCountdown -> {
                Text("${phase.secondsLeft} s", Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    style = androidx.compose.ui.text.TextStyle(fontFamily = com.example.kpkn.screens.onboarding.design.WizardFonts.display,
                        fontWeight = FontWeight.ExtraBold, fontSize = 53.sp, fontFeatureSettings = "'tnum' 1"),
                    color = WizardColors.text, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                LiveSheetSubtitle("Pausa de $pauseSeconds s. Después podrás registrar el siguiente tramo.")
            }
            is GuidedTechniquePhase.DropSet -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LiveSheetLabeledField(if (loadMode == LoadModeV2.ASSISTED) "Asistencia · kg" else "Carga · kg", dropWeightText, onDropWeightChange,
                    Modifier.weight(1f), keyboardType = KeyboardType.Decimal)
                LiveSheetLabeledField("Repeticiones", dropRepsText, onDropRepsChange, Modifier.weight(1f), keyboardType = KeyboardType.Number)
            }
            is GuidedTechniquePhase.RestPauseReps ->
                LiveSheetLabeledField("Repeticiones", restPauseRepsText, onRestPauseRepsChange, keyboardType = KeyboardType.Number)
        }
        principal?.let { LiveSheetInlineNote("Principal: $it") }
        GuidedSkipAction(
            text = "Terminar técnica con lo realizado",
            onClick = { confirmSkipTechnique = true },
        )
    }
    if (confirmSkipTechnique) {
        // El cuerpo de KpknAlertDialog reparte los dos botones en una sola fila: con «Seguir con la técnica», el de confirmar se aplastaba
        // hasta un círculo. Aquí la confirmación ocupa todo el ancho y «Seguir» va debajo, con los mismos textos y las mismas acciones.
        // `guide-exit` del prototipo 02: «Guardar lo realizado» como acción principal y «Seguir con la técnica» debajo.
        LiveAlert(
            title = "¿Terminar la técnica?",
            text = "Se conservarán la serie principal y los tramos ya realizados. Los tramos pendientes no se inventan.",
            primaryLabel = "Guardar lo realizado",
            onPrimary = {
                confirmSkipTechnique = false
                onSkipTechnique()
            },
            secondaryLabel = "Seguir con la técnica",
            onSecondary = { confirmSkipTechnique = false },
            onDismiss = { confirmSkipTechnique = false },
            icon = com.example.kpkn.ui.components.icons.KpknLineIcons.Flag,
        )
    }
}

/**
 * Acción de texto de la técnica guiada (LR1c2): el mismo estilo que `KpknTextAction` (tinta, `KpknType.action`, sin ripple y con 48 dp de
 * zona táctil), pero con hasta dos líneas antes que cortarse con «…» al 130 % de letra.
 */
@Composable
private fun GuidedSkipAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = KpknType.action,
            color = WizardColors.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
