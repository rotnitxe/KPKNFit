package com.example.kpkn.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.onboarding.RingsCompletion
import com.example.kpkn.domain.onboarding.SetupRingsMapping

@Composable
fun WizChatReview(state: SetupWizardState, rings: SetupRingsMapping? = null) {
    val draft = state.draft
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        ReviewLine("Nombre", draft.name.ifBlank { "No indicado" })
        ReviewLine("Perfil", listOfNotNull(draft.ageYears?.let { "$it años" }, draft.heightCm?.let { "${it} cm" }, draft.weightKg?.let { "${it} kg" }).joinToString(" · ").ifBlank { "Datos pendientes" })
        ReviewLine("Entrenamiento", when {
            state.mode in setOf(SetupWizardMode.RINGS_ONLY, SetupWizardMode.NUTRITION_ONLY) -> "Sin cambios"
            !draft.includeTraining || draft.programRoute == SetupProgramRoute.LATER -> "Crearás el programa después"
            else -> state.programPreview?.name ?: "Vista previa pendiente"
        })
        ReviewLine("Nutrición", when {
            state.mode in setOf(SetupWizardMode.TRAINING_ONLY, SetupWizardMode.RINGS_ONLY) -> "Sin cambios"
            !draft.includeNutrition -> "Pospuesta"
            else -> state.nutritionPlanPreview?.let { "${it.calorieTarget} kcal · ${it.proteinGoal} g proteína" } ?: "Pendiente"
        })
        ReviewLine("RINGS", when {
            rings?.completion == RingsCompletion.UNKNOWN || draft.ringsAnswers?.recentTrainingState == SetupRecentTrainingState.UNKNOWN -> "Sin calibrar"
            rings?.completion == RingsCompletion.PRESERVE -> if (draft.ringsAnswers?.startAction?.contains("dejar", true) == true) "Sin calibrar" else "Conservar estimación actual"
            rings?.completion == RingsCompletion.OMITTED -> "Quitar estimación inicial"
            rings?.completion == RingsCompletion.VALID -> state.ringsBatteriesPreview?.let {
                "Músculos ${it.muscular} · Energía ${it.cnc} · Columna ${it.spinal} · aproximado"
            } ?: "Calculando el punto de partida"
            draft.ringsAnswers == null -> "Sin calibrar"
            else -> "Pendiente de revisar"
        })
        if (state.isPreviewLoading) CircularProgressIndicator(color = WizChatTokens.stageAccent(com.example.kpkn.domain.onboarding.WizChatStage.TRAINING))
        state.previewReport?.limitations?.takeIf { it.isNotEmpty() }?.forEach { Text(it, color = WizChatTokens.muted) }
        state.nutritionErrors.values.forEach { Text(it, color = WizChatTokens.danger) }
        state.errors.values.forEach { Text(it, color = WizChatTokens.danger, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun ReviewLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, color = WizChatTokens.muted, modifier = Modifier.weight(.36f))
        Text(value, color = WizChatTokens.text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(.64f))
    }
}
