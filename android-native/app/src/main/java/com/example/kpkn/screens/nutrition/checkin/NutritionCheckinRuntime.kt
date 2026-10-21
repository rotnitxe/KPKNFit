package com.example.kpkn.screens.nutrition.checkin

import android.content.Context
import com.example.kpkn.data.db.*
import com.example.kpkn.data.models.*
import com.example.kpkn.data.persistence.PersistenceWriteCoordinator
import com.example.kpkn.data.repository.*
import com.example.kpkn.domain.nutrition.*
import com.example.kpkn.domain.nutrition.checkin.CheckinPreferences
import com.example.kpkn.domain.nutrition.expenditure.*
import java.time.*
import java.time.temporal.WeekFields
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

fun checkinWeekStart(program: Program?, locale: Locale = Locale.getDefault()): DayOfWeek =
    program?.resolvedSchedulePlan()?.weekStartDay?.takeIf { it in 1..7 }?.let(DayOfWeek::of)
        ?: WeekFields.of(locale).firstDayOfWeek

/** Inyección manual; reutiliza las marcas y la EER guardada, sin recalcular ni cambiar N1. */
object NutritionCheckinRuntime {
    fun store(context: Context): NutritionCheckinRepository {
        val db = KpknDatabase.getInstance(context)
        return NutritionCheckinRepository(db, NutritionPlanCommitCoordinator(db, NutritionRepository.getInstance(), ProgramRepository.getInstance()))
    }

    fun dependencies(context: Context): CheckinDependencies {
        val db = KpknDatabase.getInstance(context)
        val nutrition = NutritionRepository.getInstance()
        val programs = ProgramRepository.getInstance()
        val body = BodyProgressRepository.getInstance(context)
        val food = combine(nutrition.nutritionLogs, nutrition.dayMarks, nutrition.nutritionPlans,
            nutrition.activeNutritionPlanId, nutrition.dailyGoalSnapshots) { logs, marks, plans, activeId, snapshots ->
            FoodContext(logs, marks, plans.find { it.id == activeId }, snapshots)
        }
        val program = combine(programs.settings, programs.programs, programs.activeProgramState) { settings, all, active ->
            settings to checkinWeekStart(all.find { it.id == active?.programId })
        }
        val source = combine(food, program, body.observations) { foodData, programData, observations ->
            val (settings, weekStart) = programData
            val plan = foodData.plan.takeUnless { settings.nutritionTrackingOnly }
            val through = LocalDate.now()
            val observationsByDay = buildDayObservations(foodData.logs, foodData.marks, through).associateBy { it.date }
            val planStart = plan?.createdAt?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
            val start = minOf(observationsByDay.keys.minOrNull() ?: through, planStart?.coerceAtMost(through) ?: through)
            val days = generateSequence(start) { if (it < through) it.plusDays(1) else null }
                .map { observationsByDay[it] ?: DayObservation(it, null) }.toList()
            val vitals = settings.userVitals
            val resting = if (vitals.weight != null && vitals.height != null && (vitals.age ?: settings.age) != null && vitals.gender != null) {
                calculateBMR(NutritionInput(vitals.weight, vitals.height, (vitals.age ?: settings.age)!!, vitals.gender,
                    vitals.metabolicProfile, vitals.bodyFatPercentage))
            } else null
            val changes = foodData.snapshots.sortedBy { it.date }.zipWithNext().mapNotNull { (a, b) ->
                if (a.planId != b.planId || a.direction != b.direction || a.calorieTargetKcal != b.calorieTargetKcal)
                    runCatching { LocalDate.parse(b.date) }.getOrNull() else null
            } + listOfNotNull(planStart, plan?.calculationSnapshot?.result?.get("effectiveFrom")?.let { runCatching { LocalDate.parse(it) }.getOrNull() })
            CheckinContext(
                plan, weekStart, settings.nutritionCheckin,
                days,
                observations.filter { it.metric == BodyMetric.WEIGHT }.mapNotNull {
                    runCatching { WeightObservation(Instant.ofEpochMilli(it.timestampEpochMs).atZone(ZoneId.of(it.zoneId)).toLocalDate(), it.valueSi) }.getOrNull()
                },
                plan?.calculationSnapshot?.result?.get("eerKcal")?.toDoubleOrNull(), resting, changes.distinct(),
            )
        }.flowOn(Dispatchers.IO)
        return CheckinDependencies(source, store(context), savePreferences = { transform ->
            programs.isReady.first { it }
            val saved = withContext(Dispatchers.IO) {
                PersistenceWriteCoordinator.mutex.withLock {
                    val current = db.settingsDao().get()?.toSettings() ?: programs.settings.value
                    current.copy(nutritionCheckin = transform(current.nutritionCheckin)).also { db.settingsDao().upsert(it.toEntity()) }
                }
            }
            programs.publishSetupCommit(saved, null, false)
        }, markDay = { date, mark, source -> nutrition.setDayMark(date, mark, source) })
    }

    private data class FoodContext(val logs: List<NutritionLog>, val marks: Map<LocalDate, DayMarkEntry>,
        val plan: NutritionPlan?, val snapshots: List<DailyGoalSnapshot>)
}
