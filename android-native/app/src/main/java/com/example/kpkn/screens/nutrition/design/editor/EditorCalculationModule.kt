package com.example.kpkn.screens.nutrition.design.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.nutrition.EerActivity
import com.example.kpkn.domain.nutrition.EerSex
import com.example.kpkn.domain.nutrition.NutritionPlanEditorDraft
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknSegmentedSize
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknSwitch
import com.example.kpkn.ui.design.KpknType

/** Ecuación en tres segmentos, en el orden de [EerSex]: femenina, masculina y promedio. */
private val SexOptions = listOf("Femenina", "Masculina", "Promedio")

/**
 * Módulo «Datos de cálculo»: edad, altura y peso en una fila (en dos al 130 % de letra); la ecuación, con la nota del promedio; la
 * actividad en píldoras y las cuatro condiciones como interruptores de fila entera. Sin estado.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorCalculationModule(
    draft: NutritionPlanEditorDraft,
    onAge: (String) -> Unit,
    onHeight: (String) -> Unit,
    onWeight: (String) -> Unit,
    onEquation: (EerSex) -> Unit,
    onActivity: (EerActivity) -> Unit,
    onEligibilityUnknown: (Boolean) -> Unit,
    onMedicalRestriction: (Boolean) -> Unit,
    onPregnant: (Boolean) -> Unit,
    onLactating: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val weightLabel = "Peso (${draft.weightUnit})"
    KpknModule(modifier = modifier) {
        KpknModuleHeader(label = "Datos de cálculo")
        Spacer(Modifier.height(12.dp))
        if (LocalDensity.current.fontScale >= EditorLargeFontScale) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EditorNumberField(
                    value = draft.ageText,
                    onValueChange = onAge,
                    label = "Edad",
                    keyboard = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                )
                EditorNumberField(
                    value = draft.heightText,
                    onValueChange = onHeight,
                    label = "Altura (cm)",
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EditorNumberField(
                    value = draft.weightText,
                    onValueChange = onWeight,
                    label = weightLabel,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.weight(1f))
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EditorNumberField(
                    value = draft.ageText,
                    onValueChange = onAge,
                    label = "Edad",
                    keyboard = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                )
                EditorNumberField(
                    value = draft.heightText,
                    onValueChange = onHeight,
                    label = "Altura (cm)",
                    modifier = Modifier.weight(1f),
                )
                EditorNumberField(
                    value = draft.weightText,
                    onValueChange = onWeight,
                    label = weightLabel,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // La ecuación usa el entorno hormonal, no la identidad: femenina, masculina o el promedio de ambas.
        Spacer(Modifier.height(16.dp))
        EditorCaption("Ecuación")
        Spacer(Modifier.height(8.dp))
        val sexes = EerSex.entries
        val sexIndex = draft.equationSex?.let { sexes.indexOf(it) }
        if (sexIndex != null) {
            KpknSegmentedControl(
                options = SexOptions,
                selectedIndex = sexIndex,
                onSelect = { onEquation(sexes[it]) },
                modifier = Modifier.fillMaxWidth(),
                size = KpknSegmentedSize.Compact,
            )
        } else {
            // Sin sexo en el perfil ni elegido: ninguna ecuación parece elegida hasta que el usuario toque una.
            EditorSegmentedTrack(
                options = SexOptions,
                selectedIndex = null,
                onSelect = { onEquation(sexes[it]) },
                modifier = Modifier.fillMaxWidth(),
                size = KpknSegmentedSize.Compact,
            )
        }
        if (draft.equationSex == EerSex.AVERAGE) {
            Spacer(Modifier.height(8.dp))
            EditorCaption("Promedio de la ecuación femenina y la masculina, para un contexto hormonal mixto o no declarado.")
        }

        Spacer(Modifier.height(16.dp))
        EditorCaption("Actividad")
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EerActivity.entries.forEach { activity ->
                KpknChip(
                    text = activityLabel(activity),
                    selected = draft.activity == activity,
                    onClick = { onActivity(activity) },
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        EditorSwitchRow("No puedo confirmar si me aplica", draft.eligibilityUnknown, onEligibilityUnknown)
        EditorRowDivider()
        EditorSwitchRow("Condición médica", draft.medicalRestriction, onMedicalRestriction)
        EditorRowDivider()
        EditorSwitchRow("Embarazo", draft.pregnant, onPregnant)
        EditorRowDivider()
        EditorSwitchRow("Lactancia", draft.lactating, onLactating)
    }
}

/**
 * Fila de condición de 56 dp: el texto en `body` y el interruptor a la derecha. Toda la fila es el interruptor (`Role.Switch`); el de
 * dentro es solo de lectura para que no haya dos toques distintos.
 */
@Composable
private fun EditorSwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .toggleable(
                value = checked,
                role = Role.Switch,
                interactionSource = interaction,
                indication = null,
                onValueChange = onChange,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = KpknType.body, color = WizardColors.text, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        KpknSwitch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun EditorRowDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
}

private fun activityLabel(activity: EerActivity): String = when (activity) {
    EerActivity.INACTIVE -> "Inactivo"
    EerActivity.LOW_ACTIVE -> "Poco activo"
    EerActivity.ACTIVE -> "Activo"
    EerActivity.VERY_ACTIVE -> "Muy activo"
}
