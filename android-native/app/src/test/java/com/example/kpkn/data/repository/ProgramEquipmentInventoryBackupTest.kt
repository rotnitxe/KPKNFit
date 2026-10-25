package com.example.kpkn.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.dbJson
import com.example.kpkn.data.models.*
import com.example.kpkn.data.settings.SettingsExportPayload
import com.example.kpkn.data.settings.SettingsJsonBackup
import com.example.kpkn.testsupport.RoomTestRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = android.app.Application::class)
class ProgramEquipmentInventoryBackupTest {
    @get:Rule val room = RoomTestRule()

    @Test fun elBackupJsonConservaElInventarioCompletoSinPerderNingunCampo() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = KpknDatabase.createInMemory(context)
        try {
            val inventory = EquipmentInventory(15.0, listOf(PlateStock(1.25, null), PlateStock(20.0, 3)),
                listOf(DumbbellPairStock(12.5, false), DumbbellPairStock(20.0)), listOf(KettlebellStock(16.0)),
                listOf(MachineLoadRange("Prensa", 10.0, 200.0, 5.0, 10.0, "machine", "leg_press__machine"),
                    MachineLoadRange("Polea", 0.0, 100.0, 2.5, 0.0, "cable"),
                    MachineLoadRange("Multipower", 15.0, 180.0, 2.5, 15.0, "smith_machine")),
                setOf("support", "pull_up_bar", "band", "ball", "cardio"))
            val settings = Settings(equipmentInventory = inventory, equipmentAvailability = EquipmentAvailability(), barbellWeight = 10.0, availablePlates = listOf(5.0))
            val payload = SettingsJsonBackup.buildPayload(context, settings, profilePhotoJpegBase64 = null,
                programs = emptyList(), workoutLogs = emptyList(), activeProgramState = null, ongoingWorkout = null,
                nutritionLogs = emptyList(), nutritionPlans = emptyList(), activeNutritionPlanId = null, pantryItems = emptyList(),
                mealTemplates = emptyList(), customFoods = emptyList(), learnedResolutions = emptyList(), foodCatalogMeta = null,
                bodyObservations = emptyList(), bodyGoals = emptyList(), measurementSchedule = null, calibrationProfile = null,
                dailyGoalSnapshots = emptyList(), wellbeingLogs = emptyList(), sleepLogs = emptyList(), sleepLogsExtended = emptyList(),
                postSessionFeedback = emptyList(), sessionTemplates = emptyList(), customExercises = emptyList(), db = db)
            val restored = dbJson.decodeFromString<SettingsExportPayload>(dbJson.encodeToString(payload))
            assertEquals(settings, restored.settings)
            assertEquals(inventory, restored.settings.resolvedEquipmentInventory())
        } finally { db.close() }
    }
}
