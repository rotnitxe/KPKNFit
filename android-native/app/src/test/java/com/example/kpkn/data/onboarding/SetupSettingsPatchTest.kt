package com.example.kpkn.data.onboarding

import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.UserVitals
import com.example.kpkn.data.models.Gender
import com.example.kpkn.data.models.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class SetupSettingsPatchTest {
    @Test
    fun unchangedFieldsComeFromTheCurrentSettingsSnapshot() {
        val base = Settings(username = "Actual", dailyFiberGoal = 31, gymName = "Concurrent")
        val result = SetupSettingsPatch(username = SetupPatchField.Set("Setup"), dailyCalorieGoal = SetupPatchField.Set(2100)).applyTo(base)
        assertEquals("Setup", result.username)
        assertEquals(2100, result.dailyCalorieGoal)
        assertEquals(31, result.dailyFiberGoal)
        assertEquals("Concurrent", result.gymName)
    }

    @Test
    fun profileOnlyChangesDeclaredVitalsOnTheCurrentSettingsRow() {
        val current = Settings(userVitals = UserVitals(age = 41, height = 170.0, weight = 81.0,
            gender = Gender.OTHER, bodyFatPercentage = 21.0))
        val result = SetupSettingsPatch(vitalsPatch = SetupUserVitalsPatch(
            weight = SetupPatchField.Set(75.0),
        )).applyTo(current)
        assertEquals(41, result.userVitals.age)
        assertEquals(170.0, result.userVitals.height)
        assertEquals(75.0, result.userVitals.weight)
        assertEquals(Gender.OTHER, result.userVitals.gender)
        assertEquals(21.0, result.userVitals.bodyFatPercentage)
    }

    @Test
    fun choosingPoundsChangesDisplayPreferenceWithoutChangingStoredKilograms() {
        val current = Settings(weightUnit = WeightUnit.KG, userVitals = UserVitals(weight = 72.4))
        val result = SetupSettingsPatch(weightUnit = SetupPatchField.Set(WeightUnit.LBS)).applyTo(current)
        assertEquals(WeightUnit.LBS, result.weightUnit)
        assertEquals(72.4, result.userVitals.weight)
    }
}
