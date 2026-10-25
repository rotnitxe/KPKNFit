package com.example.kpkn.debug

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.room.withTransaction
import com.example.kpkn.MainActivity
import com.example.kpkn.data.backup.AppBootstrap
import com.example.kpkn.data.db.*
import com.example.kpkn.data.models.*
import com.example.kpkn.data.persistence.PersistenceWriteCoordinator
import com.example.kpkn.data.repository.*
import com.example.kpkn.domain.nutrition.*
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.theme.KPKNTheme
import java.time.LocalDate
import java.time.ZoneId
import java.util.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** SOLO QA (.mfp), después del arnés persona=qa activate=1500. Extra scenario: 1 limpio, 2 con huecos. */
class NutritionHistorySeedActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) { setShowWhenLocked(true); setTurnScreenOn(true) }
        var status by mutableStateOf("Preparando 84 días de nutrición…")
        setContent { KPKNTheme { Box(Modifier.fillMaxSize().background(KpknSurface.canvas).padding(24.dp)) {
            Text(status, style = KpknType.body, color = WizardColors.text)
        } } }
        val scenario = intent.getIntExtra("scenario", 1)
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    check(packageName == "com.example.kpkn.mfp") { "Esta siembra solo está disponible en la variante QA .mfp." }
                    require(scenario in 1..2) { "El escenario debe ser 1 o 2." }
                    AppBootstrap.prepare(application)
                    check(AppBootstrap.state.value == AppBootstrap.State.Ready) { "La app está recuperando sus datos." }
                    val programs = ProgramRepository.getInstance()
                    check(programs.activeProgramState.value != null) { "Antes activa la persona QA con el arnés del alta." }
                    val db = KpknDatabase.getInstance(application)
                    val today = LocalDate.now()
                    val seed = nutritionHistorySeed(today, scenario)
                    PersistenceWriteCoordinator.mutex.withLock {
                        db.withTransaction {
                            val dao = db.nutritionDao()
                            dao.deactivateAllPlans()
                            dao.upsertPlan(seed.plan.toEntity())
                            dao.upsertActiveState(NutritionActiveStateEntity(activePlanId = seed.plan.id))
                            for (index in 0 until 84) {
                                val date = today.minusDays(83L - index)
                                dao.deleteLog("qa-nutrition-$date")
                                dao.deleteDayMark(date.toString())
                                db.bodyProgressDao().deleteObservation("qa-weight-$date")
                            }
                            seed.logs.forEach { dao.upsertLog(it.toEntity()) }
                            seed.weights.forEach { db.bodyProgressDao().upsertObservation(it.toEntity()) }
                            seed.fastingDate?.let { dao.upsertDayMark(NutritionDayMarkEntity(it.toString(), "FASTING", "USER", java.time.Instant.now().toString())) }
                            val settings = (db.settingsDao().get()?.toSettings() ?: programs.settings.value).copy(
                                nutritionTrackingOnly = false,
                                nutritionCheckin = com.example.kpkn.domain.nutrition.checkin.CheckinPreferences(dayOfWeek = today.dayOfWeek.value),
                            )
                            db.settingsDao().upsert(settings.toEntity())
                        }
                    }
                    programs.publishSetupCommit(db.settingsDao().get()!!.toSettings(), null, false)
                    NutritionRepository.getInstance().refreshData(application)
                    BodyProgressRepository.getInstance(application).refreshFromStorage()
                }
                startActivity(Intent(this@NutritionHistorySeedActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
                finish()
            } catch (error: Exception) { status = error.message ?: "No se pudo sembrar el historial." }
        }
    }
}

internal data class NutritionHistorySeed(val plan: NutritionPlan, val logs: List<NutritionLog>, val weights: List<BodyObservation>, val fastingDate: LocalDate?)

/** Balance simétrico conocido: gasto de 2400 kcal/día, ingesta real de 2100 y ruido con semilla fija. */
internal fun nutritionHistorySeed(today: LocalDate, scenario: Int): NutritionHistorySeed {
    val random = Random(20261021L)
    val start = today.minusDays(83)
    val recommendation = NutritionEnergyEngine.recommendPlan(EerInput(30, 175.0, 80.0, EerSex.AVERAGE, EerActivity.LOW_ACTIVE), PlanDirection.DEFICIT,
        manualCalorieTargetKcal = 2100, now = start.atStartOfDay(ZoneId.of("UTC")).toInstant())
    val plan = NutritionPlan(id = "qa-nutrition-history", name = "Historial de prueba", calorieTarget = 2100,
        proteinGoal = 160, carbGoal = 230, fatGoal = 60, isActive = true,
        createdAt = start.toString(), direction = PlanDirection.DEFICIT, weeklyChangeKg = 300.0 * 7 / 7700,
        calculationSnapshot = recommendation.snapshot, engineVersion = NutritionEnergyEngine.ENGINE_VERSION)
    val logs = mutableListOf<NutritionLog>()
    val weights = mutableListOf<BodyObservation>()
    val fasting = if (scenario == 2) start.plusDays(75) else null
    var tissue = 80.0
    for (index in 0 until 84) {
        val date = start.plusDays(index.toLong())
        val trueIntake = if (date == fasting) 0.0 else 2100.0
        tissue += (trueIntake - 2400.0) / 7700.0
        val partial = scenario == 2 && index in setOf(12, 37, 78, 81)
        val gap = scenario == 2 && index in 65..72
        val loggedKcal = if (partial) 300.0 else trueIntake
        val fraction = loggedKcal / 2100.0
        if (!gap && date != fasting) logs += NutritionLog(id = "qa-nutrition-$date", date = "${date}T12:00:00Z", mealType = MealType.LUNCH,
            foods = listOf(LoggedFood(id = "qa-food-$date", foodName = "Comidas del día de prueba", amount = 1.0, unit = "porción",
                calories = loggedKcal, protein = 160.0 * fraction, carbs = 230.0 * fraction, fats = 60.0 * fraction)))
        if (scenario != 2 || index !in 77..83 || index in setOf(77, 83)) weights += BodyObservation(
            id = "qa-weight-$date", metric = BodyMetric.WEIGHT, valueSi = tissue + random.nextGaussian() * 0.12,
            unitSi = "kg", timestampEpochMs = date.atTime(8, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            zoneId = ZoneId.systemDefault().id,
        )
    }
    return NutritionHistorySeed(plan, logs, weights, fasting)
}
