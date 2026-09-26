package com.example.kpkn.data.onboarding

import com.example.kpkn.data.models.InitialRecoveryEvidence
import com.example.kpkn.data.models.NutritionTrackingChoice
import com.example.kpkn.data.models.EquipmentInventory
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.UserVitals
import com.example.kpkn.data.models.Gender
import com.example.kpkn.data.models.WeightUnit
import com.example.kpkn.data.models.VolumeCalibrationProfile

/** Explicit three-state field patch used by setup commits. */
sealed interface SetupPatchField<out T> {
    data object Unchanged : SetupPatchField<Nothing>
    data class Set<T>(val value: T) : SetupPatchField<T>
    data object Clear : SetupPatchField<Nothing>
}

/** Apply only the vitals that were actually confirmed, to the newest Settings row. */
data class SetupUserVitalsPatch(
    val age: SetupPatchField<Int?> = SetupPatchField.Unchanged,
    val height: SetupPatchField<Double?> = SetupPatchField.Unchanged,
    val weight: SetupPatchField<Double?> = SetupPatchField.Unchanged,
    val gender: SetupPatchField<Gender?> = SetupPatchField.Unchanged,
) {
    fun applyTo(base: UserVitals): UserVitals = base.copy(
        age = if (age is SetupPatchField.Set) age.value else base.age,
        height = if (height is SetupPatchField.Set) height.value else base.height,
        weight = if (weight is SetupPatchField.Set) weight.value else base.weight,
        gender = if (gender is SetupPatchField.Set) gender.value else base.gender,
    )
}

data class SetupSettingsPatch(
    val username: SetupPatchField<String> = SetupPatchField.Unchanged,
    val age: SetupPatchField<Int?> = SetupPatchField.Unchanged,
    val userVitals: SetupPatchField<UserVitals> = SetupPatchField.Unchanged,
    val weightUnit: SetupPatchField<WeightUnit> = SetupPatchField.Unchanged,
    val vitalsPatch: SetupUserVitalsPatch? = null,
    val dailyCalorieGoal: SetupPatchField<Int?> = SetupPatchField.Unchanged,
    val dailyProteinGoal: SetupPatchField<Int?> = SetupPatchField.Unchanged,
    val dailyCarbGoal: SetupPatchField<Int?> = SetupPatchField.Unchanged,
    val dailyFatGoal: SetupPatchField<Int?> = SetupPatchField.Unchanged,
    val onboardingCompleted: SetupPatchField<Boolean> = SetupPatchField.Unchanged,
    val onboardingNameDone: SetupPatchField<Boolean> = SetupPatchField.Unchanged,
    val onboardingProgramDone: SetupPatchField<Boolean> = SetupPatchField.Unchanged,
    val onboardingNutritionDone: SetupPatchField<Boolean> = SetupPatchField.Unchanged,
    val nutritionTrackingChoice: SetupPatchField<NutritionTrackingChoice> = SetupPatchField.Unchanged,
    val initialRecoveryEvidence: SetupPatchField<InitialRecoveryEvidence?> = SetupPatchField.Unchanged,
    val volumeCalibrationProfile: SetupPatchField<VolumeCalibrationProfile?> = SetupPatchField.Unchanged,
    /**
     * Inventario principal del gimnasio confirmado en el alta. Set persiste el
     * inventario declarado; Clear deriva de [Settings.barbellWeight] +
     * [Settings.availablePlates]; Unchanged conserva el inventario actual.
     */
    val equipmentInventory: SetupPatchField<EquipmentInventory?> = SetupPatchField.Unchanged,
    /**
     * Modo durable de solo registro declarado por parche. La intención efectiva
     * (request O parche) es excluyente con plan, activación, metas derivadas o
     * snapshot en el mismo alta: una contradicción revierte el commit entero.
     * Cuando el alcance no toca nutrición, el modo persiste intacto.
     */
    val nutritionTrackingOnly: SetupPatchField<Boolean> = SetupPatchField.Unchanged,
    /** Categories are patched independently of numeric equipment stock. */
) {
    fun applyTo(base: Settings): Settings = base.copy(
        username = username.resolve(base.username),
        age = age.resolve(base.age),
        userVitals = vitalsPatch?.applyTo(base.userVitals) ?: userVitals.resolve(base.userVitals),
        weightUnit = weightUnit.resolve(base.weightUnit),
        dailyCalorieGoal = dailyCalorieGoal.resolve(base.dailyCalorieGoal),
        dailyProteinGoal = dailyProteinGoal.resolve(base.dailyProteinGoal),
        dailyCarbGoal = dailyCarbGoal.resolve(base.dailyCarbGoal),
        dailyFatGoal = dailyFatGoal.resolve(base.dailyFatGoal),
        onboardingCompleted = onboardingCompleted.resolve(base.onboardingCompleted),
        onboardingNameDone = onboardingNameDone.resolve(base.onboardingNameDone),
        onboardingProgramDone = onboardingProgramDone.resolve(base.onboardingProgramDone),
        onboardingNutritionDone = onboardingNutritionDone.resolve(base.onboardingNutritionDone),
        nutritionTrackingChoice = nutritionTrackingChoice.resolve(base.nutritionTrackingChoice),
        initialRecoveryEvidence = initialRecoveryEvidence.resolve(base.initialRecoveryEvidence),
        volumeCalibrationProfile = volumeCalibrationProfile.resolve(base.volumeCalibrationProfile),
        equipmentInventory = equipmentInventory.resolve(base.equipmentInventory),
        nutritionTrackingOnly = nutritionTrackingOnly.resolve(base.nutritionTrackingOnly),
    )

    private fun <T> SetupPatchField<T>.resolve(previous: T): T = when (this) {
        SetupPatchField.Unchanged -> previous
        is SetupPatchField.Set -> value
        SetupPatchField.Clear -> when (previous) {
            is Boolean -> false as T
            else -> null as T
        }
    }
}
