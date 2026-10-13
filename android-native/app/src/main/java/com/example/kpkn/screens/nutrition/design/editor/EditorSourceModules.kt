package com.example.kpkn.screens.nutrition.design.editor

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.nutrition.NutritionEditorProvenance
import com.example.kpkn.domain.nutrition.NutritionPlanEditorDraft
import com.example.kpkn.domain.nutrition.NutritionPlanEditorMode
import com.example.kpkn.domain.nutrition.provenanceOf
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknRadioRow

/**
 * Módulo «Procedencia»: de dónde sale la pauta. Elegir «Pauta de un profesional» fija también la dirección, que entonces la define
 * el profesional. Sin estado.
 */
@Composable
fun EditorProvenanceModule(
    draft: NutritionPlanEditorDraft,
    onProvenance: (NutritionEditorProvenance) -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = provenanceOf(draft)
    KpknModule(modifier = modifier) {
        KpknModuleHeader(label = "Procedencia")
        Spacer(Modifier.height(8.dp))
        KpknRadioRow(
            selected = current == NutritionEditorProvenance.AUTOMATIC,
            title = "Recomendación automática",
            subtitle = "Calculada con la ecuación EER.",
            onClick = { onProvenance(NutritionEditorProvenance.AUTOMATIC) },
        )
        KpknRadioRow(
            selected = current == NutritionEditorProvenance.SELF_DEFINED,
            title = "Objetivos propios",
            subtitle = "Valores introducidos por ti.",
            onClick = { onProvenance(NutritionEditorProvenance.SELF_DEFINED) },
        )
        KpknRadioRow(
            selected = current == NutritionEditorProvenance.PROFESSIONAL,
            title = "Pauta de un profesional",
            subtitle = "Definida por un profesional.",
            onClick = { onProvenance(NutritionEditorProvenance.PROFESSIONAL) },
        )
    }
}

/**
 * Módulo «Estado»: plan activo (con metas para medir y avisar) o solo registro (sin metas). Los planes anteriores no se borran al
 * pasar a solo registro. Sin estado.
 */
@Composable
fun EditorStatusModule(
    mode: NutritionPlanEditorMode,
    onMode: (NutritionPlanEditorMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    KpknModule(modifier = modifier) {
        KpknModuleHeader(label = "Estado")
        Spacer(Modifier.height(8.dp))
        KpknRadioRow(
            selected = mode == NutritionPlanEditorMode.ACTIVE_PLAN,
            title = "Plan activo",
            subtitle = "Con metas numéricas para medir y avisar.",
            onClick = { onMode(NutritionPlanEditorMode.ACTIVE_PLAN) },
        )
        KpknRadioRow(
            selected = mode == NutritionPlanEditorMode.TRACKING_ONLY,
            title = "Solo registro",
            subtitle = "Registras comidas sin metas: no hay porcentajes ni avisos de «te falta». Los planes anteriores no se borran.",
            onClick = { onMode(NutritionPlanEditorMode.TRACKING_ONLY) },
        )
    }
}
