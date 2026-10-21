package com.example.kpkn.data.repository

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.*
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.nutrition.*
import com.example.kpkn.domain.nutrition.checkin.*
import com.example.kpkn.domain.nutrition.expenditure.*
import com.example.kpkn.testsupport.RoomTestRule
import java.time.LocalDate
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class NutritionCheckinRepositoryTest {
    @get:Rule val roomRule = RoomTestRule()
    private lateinit var db: KpknDatabase
    private val today = LocalDate.now()
    private val base = NutritionPlan(id = "plan-checkin", calorieTarget = 2100, isActive = true,
        direction = PlanDirection.MAINTENANCE, weeklyChangeKg = 0.0)
    @Before fun setup() { db = KpknDatabase.createInMemory(ApplicationProvider.getApplicationContext()) }
    @After fun close() { db.close() }
    private fun repository() = NutritionCheckinRepository(db, NutritionPlanCommitCoordinator(db))
    private suspend fun seed() {
        db.nutritionDao().upsertPlan(base.toEntity())
        db.nutritionDao().upsertActiveState(NutritionActiveStateEntity(activePlanId = base.id))
        db.settingsDao().upsert(Settings().toEntity())
    }
    private fun proposal(intakeKcal: Double = 2400.0): CheckinPlan {
        val days = (56L downTo 1L).map { DayObservation(today.minusDays(it), intakeKcal) }
        val series = ExpenditureEstimator().estimateSeries(ExpenditureInput(intakeKcal, 1400.0, days,
            days.map { WeightObservation(it.date, 80.0) }, today.minusDays(1)))
        return CheckinPlanner.build(series.last(), series, days, base, today,
            CheckinPolicy.weekKey(today, today.dayOfWeek))
    }
    private suspend fun target() = db.nutritionDao().getAllPlans().single().toNutritionPlan().calorieTarget

    @Test fun `C1 doble toque y reintento aplican una sola vez sin cambiar hoy ni el pasado`() = runBlocking {
        seed()
        val old = listOf(today.minusDays(4), today).map { DailyGoalSnapshot(it.toString(), base.id, 2100, capturedAtEpochMs = 123L).toEntity() }
        old.forEach { db.nutritionDao().insertDailyGoalSnapshot(it) }
        val proposal = proposal()
        coroutineScope { listOf(async { repository().apply(proposal) }, async { repository().apply(proposal) }).awaitAll() }
        repository().apply(proposal)
        assertEquals(2250, target())
        assertEquals(old.toSet(), db.nutritionDao().getAllDailyGoalSnapshots().toSet())
        assertEquals(2250, planDayTargetForDate(db.nutritionDao().getAllPlans().single().toNutritionPlan(), today.plusDays(1), NutritionGoalSource.PLAN_FORECAST).calorieTargetKcal)
        assertEquals(1, db.setupCommitReceiptDao().getAll().size)
        val record = db.nutritionDao().getCheckIn(proposal.weekKey.toString())!!
        assertEquals("APPLIED", record.status)
        assertEquals(base.id, record.appliedPlanId)
        assertTrue(record.proposalJson!!.contains("usedDates"))
    }

    @Test fun `si hoy no tenia instantanea se fija el objetivo anterior antes del cambio`() = runBlocking {
        seed()
        repository().apply(proposal())
        assertEquals(2100, db.nutritionDao().getDailyGoalSnapshot(today.toString())!!.calorieTargetKcal)
        assertEquals(2250, target())
    }

    @Test fun `confirmar sin cambiar las kcal no introduce otra fecha de cambio de objetivo`() = runBlocking {
        seed()
        repository().apply(proposal(2100.0))
        val stored = db.nutritionDao().getAllPlans().single().toNutritionPlan()
        assertEquals(2100, stored.calorieTarget)
        assertFalse(stored.calculationSnapshot!!.result.containsKey("effectiveFrom"))
    }

    @Test fun `declinar y posponer conservan el objetivo y sus decisiones se leen desde otro repositorio`() = runBlocking {
        seed()
        val plan = proposal()
        repository().postpone(plan.weekKey, today, plan.proposal)
        assertEquals(today.plusDays(1), repository().records.first().single().postponedUntil)
        assertEquals(2100, target())
        repository().decline(plan.weekKey, plan.proposal)
        assertEquals(CheckinDecision.DECLINED, repository().records.first().single().status)
        assertEquals(2100, target())
        assertTrue(db.setupCommitReceiptDao().getAll().isEmpty())
        assertTrue(runCatching { repository().apply(plan) }.isFailure)
    }

    @Test fun `fallo del commit deja PENDING sin recibo ni cambio de objetivo y admite reintentar`() = runBlocking {
        seed()
        val plan = proposal()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fallo_checkin BEFORE UPDATE ON nutrition_plans BEGIN SELECT RAISE(ABORT, 'fallo de prueba'); END")
        assertTrue(runCatching { repository().apply(plan) }.isFailure)
        assertEquals(2100, target())
        assertEquals("PENDING", db.nutritionDao().getCheckIn(plan.weekKey.toString())!!.status)
        assertNull(db.setupCommitReceiptDao().get("checkin-${plan.weekKey}"))
        assertTrue(db.nutritionDao().getAllDailyGoalSnapshots().isEmpty())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fallo_checkin")
        repository().apply(plan)
        assertEquals(2250, target())
    }

    @Test fun `un recibo confirmado recupera una publicacion interrumpida sin volver a aplicar`() = runBlocking {
        seed()
        val plan = proposal()
        repository().apply(plan)
        val applied = db.nutritionDao().getCheckIn(plan.weekKey.toString())!!
        db.nutritionDao().upsertCheckIn(applied.copy(status = "PENDING", appliedPlanId = null))
        repository().apply(plan)
        assertEquals(2250, target())
        assertEquals(1, db.setupCommitReceiptDao().getAll().size)
        assertEquals("APPLIED", db.nutritionDao().getCheckIn(plan.weekKey.toString())!!.status)
    }

    @Test fun `una propuesta obsoleta no pisa un plan editado durante la revision`() = runBlocking {
        seed()
        db.nutritionDao().upsertPlan(base.copy(calorieTarget = 1900).toEntity())
        assertTrue(runCatching { repository().apply(proposal()) }.isFailure)
        assertEquals(1900, target())
        assertEquals("PENDING", db.nutritionDao().getCheckIn(proposal().weekKey.toString())!!.status)
    }

    @Test fun `la decision sobrevive al cierre y reapertura de Room`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "checkin-durable-test.db"
        context.deleteDatabase(name)
        db.close()
        db = Room.databaseBuilder(context, KpknDatabase::class.java, name).allowMainThreadQueries().build()
        try {
            seed()
            val plan = proposal()
            repository().apply(plan)
            db.close()
            db = Room.databaseBuilder(context, KpknDatabase::class.java, name).allowMainThreadQueries().build()
            assertEquals(CheckinDecision.APPLIED, repository().records.first().single().status)
            repository().apply(plan)
            assertEquals(1, db.setupCommitReceiptDao().getAll().size)
            assertEquals(2250, target())
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
