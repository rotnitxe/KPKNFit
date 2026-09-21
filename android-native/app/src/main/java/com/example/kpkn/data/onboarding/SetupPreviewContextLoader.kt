package com.example.kpkn.data.onboarding

import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toActiveProgramState
import com.example.kpkn.data.db.toNutritionPlan
import com.example.kpkn.data.db.toSettings
import com.example.kpkn.data.models.ActiveProgramState
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.Settings

data class SetupPreviewContext(
    val settings: Settings,
    val activeProgram: ActiveProgramState?,
    val activeNutritionPlan: NutritionPlan?,
)

/** Loads persisted context once for deterministic previews and commit review. */
class SetupPreviewContextLoader(private val db: KpknDatabase) {
    suspend fun load(): SetupPreviewContext {
        val settings = db.settingsDao().get()?.toSettings() ?: Settings()
        val activeProgram = db.stateDao().getActiveProgram()?.toActiveProgramState()
        val activePlanId = db.nutritionDao().getActiveState()?.activePlanId
        val activePlan = activePlanId?.let { id -> db.nutritionDao().getAllPlans().firstOrNull { it.id == id }?.toNutritionPlan() }
        return SetupPreviewContext(settings, activeProgram, activePlan)
    }
}
