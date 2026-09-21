package com.example.kpkn.data.onboarding

import com.example.kpkn.data.models.Settings
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
}
