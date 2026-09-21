package com.example.kpkn.data.onboarding

import com.example.kpkn.data.models.InitialRecoveryEvidence
import com.example.kpkn.data.models.NutritionTrackingChoice
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.UserVitals
import com.example.kpkn.data.models.VolumeCalibrationProfile

/** Explicit three-state field patch used by setup commits. */
sealed interface SetupPatchField<out T> {
    data object Unchanged : SetupPatchField<Nothing>
    data class Set<T>(val value: T) : SetupPatchField<T>
    data object Clear : SetupPatchField<Nothing>
}

data class SetupSettingsPatch(
    val username: SetupPatchField<String> = SetupPatchField.Unchanged,
    val age: SetupPatchField<Int?> = SetupPatchField.Unchanged,
    val userVitals: SetupPatchField<UserVitals> = SetupPatchField.Unchanged,
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
) {
    fun applyTo(base: Settings): Settings = base.copy(
        username = username.resolve(base.username),
        age = age.resolve(base.age),
        userVitals = userVitals.resolve(base.userVitals),
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
