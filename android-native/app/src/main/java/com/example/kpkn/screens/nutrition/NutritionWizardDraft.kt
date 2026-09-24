package com.example.kpkn.screens.nutrition

import com.example.kpkn.data.models.GoalMetric
import com.example.kpkn.data.models.PlanDirection
import com.example.kpkn.domain.nutrition.EerActivity
import com.example.kpkn.domain.nutrition.EerSex
import com.example.kpkn.domain.nutrition.WizardPacePreset
import kotlinx.serialization.Serializable

/**
 * Borrador de la pauta nutricional recogido durante el alta.
 *
 * Extraído de `NutritionWizardViewModel.kt` en la Fase 7, cuando la pantalla y
 * el ViewModel legacy del wizard nutricional se retiraron. Sigue vivo porque lo
 * usan el onboarding (`SetupWizardModels`, `SetupWizardViewModel`,
 * `SetupPendingNutrition`) y el editor directo (`NutritionPlanEditorViewModel`,
 * para importar una pauta profesional pendiente).
 *
 * El nombre, el paquete y los campos no cambian: el JSON serializado de los
 * borradores guardados debe seguir leyéndose igual.
 */
@Serializable
data class NutritionWizardDraft(
    val mode: String = "create",
    val planId: String? = null,
    val direction: PlanDirection? = null,
    val goalMetric: GoalMetric = GoalMetric.WEIGHT,
    val targetValueText: String = "",
    val weightUnit: String = "kg",
    val ageText: String = "",
    val heightText: String = "",
    val weightText: String = "",
    val equationSex: EerSex? = null,
    val activity: EerActivity = EerActivity.INACTIVE,
    /** True when the user explicitly declined or does not know eligibility. */
    val eligibilityUnknown: Boolean = false,
    val medicalRestriction: Boolean = false,
    val pregnant: Boolean = false,
    val lactating: Boolean = false,
    val manualCalorieTargetText: String = "",
    val higherProteinInDeficit: Boolean = true,
    val manualProteinText: String = "",
    val manualCarbsText: String = "",
    val manualFatText: String = "",
    val visualPhysiqueGroup: Int = 4,
    val physiqueSliderPos: Float = 4f,
    val bodyFatText: String = "",
    val muscleText: String = "",
    val targetWeightText: String = "",
    val targetBodyFatText: String = "",
    val targetMuscleText: String = "",
    val pacePreset: WizardPacePreset = WizardPacePreset.MEDIUM,
    val knowsBodyFat: Boolean = false,
    val showAdvancedMacros: Boolean = false,
)
