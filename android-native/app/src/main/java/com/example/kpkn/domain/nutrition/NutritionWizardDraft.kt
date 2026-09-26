package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.GoalMetric
import com.example.kpkn.data.models.PlanDirection
import kotlinx.serialization.Serializable

/**
 * Borrador de la pauta nutricional recogido durante el alta (onboarding) y en
 * la importación de pautas profesionales pendientes.
 *
 * Vive en `domain/` porque es un DTO puro de Kotlin/serialization consumido por
 * el dominio del alta ([com.example.kpkn.domain.onboarding.SetupNutritionPreparation])
 * y por la presentación. `screens.nutrition.NutritionWizardDraft` es solo un
 * typealias a este tipo: hay UNA sola clase, el JSON de los borradores guardados
 * no cambia y no se duplica ningún algoritmo.
 *
 * El nombre y los campos no cambian: el JSON serializado de los borradores
 * guardados debe seguir leyéndose igual.
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
    /**
     * Modo de configuración elegido durante el alta (recomendación automática,
     * objetivos propios o solo registro). Un enum plano serializa por nombre;
     * el default AUTOMATIC conserva los borradores guardados antes de este campo.
     */
    val configurationMode: NutritionConfigurationMode = NutritionConfigurationMode.AUTOMATIC,
    /** Reparto semanal elegido (variado por gasto o uniforme). */
    val weeklyDistribution: NutritionWeeklyDistributionMode = NutritionWeeklyDistributionMode.VARIABLE,
)

/**
 * ÚNICA traducción borrador del alta → borrador del editor. La comparte el
 * editor directo ([com.example.kpkn.screens.nutrition.NutritionPlanEditorViewModel])
 * y el alta ([com.example.kpkn.domain.onboarding.SetupNutritionPreparation]):
 * misma asignación de campos, misma procedencia y mismo modo derivado.
 *
 * @param pendingDraftId borrador pendiente (pauta profesional a medias) que el
 *   guardado consume; el alta no lo tiene (su DTO ES la fuente) → null.
 */
fun editorDraftOf(wizard: NutritionWizardDraft, pendingDraftId: String? = null): NutritionPlanEditorDraft {
    val targetText = when (wizard.goalMetric) {
        GoalMetric.WEIGHT -> wizard.targetWeightText.ifBlank { wizard.targetValueText }
        GoalMetric.BODY_FAT -> wizard.targetBodyFatText.ifBlank { wizard.targetValueText }
        GoalMetric.MUSCLE_MASS -> wizard.targetMuscleText.ifBlank { wizard.targetValueText }
    }
    val protein = parseLocalizedNumber(wizard.manualProteinText)?.takeIf { it >= 0 }?.toInt()
    val carbs = parseLocalizedNumber(wizard.manualCarbsText)?.takeIf { it >= 0 }?.toInt()
    val fat = parseLocalizedNumber(wizard.manualFatText)?.takeIf { it >= 0 }?.toInt()
    val calories = parseLocalizedNumber(wizard.manualCalorieTargetText)?.takeIf { it > 0 }?.toInt()
    val base = if (calories != null && protein != null && carbs != null && fat != null) {
        NutritionEditorBase(calories, protein, carbs, fat)
    } else {
        null
    }
    return NutritionPlanEditorDraft(
        planId = wizard.planId?.takeIf { it.isNotBlank() },
        pendingDraftId = pendingDraftId,
        direction = wizard.direction,
        goalMetric = wizard.goalMetric,
        targetValueText = targetText,
        ageText = wizard.ageText,
        heightText = wizard.heightText,
        weightText = wizard.weightText,
        weightUnit = wizard.weightUnit,
        equationSex = wizard.equationSex,
        activity = wizard.activity,
        eligibilityUnknown = wizard.eligibilityUnknown,
        medicalRestriction = wizard.medicalRestriction,
        pregnant = wizard.pregnant,
        lactating = wizard.lactating,
        bodyFatText = wizard.bodyFatText,
        muscleText = wizard.muscleText,
        base = base,
        baseEdited = base != null,
        provenance = when {
            wizard.direction == PlanDirection.PROFESSIONAL -> NutritionEditorProvenance.PROFESSIONAL
            wizard.configurationMode == NutritionConfigurationMode.SELF_DEFINED -> NutritionEditorProvenance.SELF_DEFINED
            base != null -> NutritionEditorProvenance.SELF_DEFINED
            else -> NutritionEditorProvenance.AUTOMATIC
        },
        mode = if (wizard.configurationMode == NutritionConfigurationMode.TRACKING_ONLY) {
            NutritionPlanEditorMode.TRACKING_ONLY
        } else {
            NutritionPlanEditorMode.ACTIVE_PLAN
        },
        weeklyDistribution = wizard.weeklyDistribution,
    )
}
