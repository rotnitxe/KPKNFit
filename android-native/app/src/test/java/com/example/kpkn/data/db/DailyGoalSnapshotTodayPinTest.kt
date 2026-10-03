package com.example.kpkn.data.db

import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.CalculationOrigin
import com.example.kpkn.data.models.DailyGoalSnapshot
import com.example.kpkn.data.models.PlanDirection
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * C2: las metas diarias son insert-once, con una única excepción: la de HOY se
 * reemplaza cuando se activa un plan DISTINTO el mismo día. Los días pasados y
 * futuros, y las ediciones del MISMO plan, nunca se reescriben.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class DailyGoalSnapshotTodayPinTest {
    private lateinit var db: KpknDatabase
    private val todayDate: LocalDate = LocalDate.now()
    private val today = todayDate.toString()
    private val yesterday = todayDate.minusDays(1).toString()
    private val tomorrow = todayDate.plusDays(1).toString()

    @Before
    fun setUp() {
        db = KpknDatabase.createInMemory(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun snapshot(date: String, planId: String?, kcal: Int) = DailyGoalSnapshot(
        date = date,
        planId = planId,
        calorieTargetKcal = kcal,
        proteinGoalG = 150,
        carbGoalG = 200,
        fatGoalG = 60,
        direction = PlanDirection.MAINTENANCE,
        calculationOrigin = CalculationOrigin.PLAN,
        capturedAtEpochMs = kcal.toLong(),
    ).toEntity()

    private fun stored(date: String): DailyGoalSnapshot? =
        runBlocking { db.nutritionDao().getDailyGoalSnapshot(date) }?.toDailyGoalSnapshot()

    private fun pin(entity: DailyGoalSnapshotEntity): Boolean =
        runBlocking { db.nutritionDao().pinTodayGoalSnapshot(entity, today) }

    @Test
    fun insertsTodaysGoalWhenThereIsNoRow() {
        assertTrue(pin(snapshot(today, "plan-a", 2000)))

        assertEquals("plan-a", stored(today)?.planId)
        assertEquals(2000, stored(today)?.calorieTargetKcal)
    }

    @Test
    fun keepsTodaysGoalWhenTheSamePlanAlreadyFixedIt() {
        assertTrue(pin(snapshot(today, "plan-a", 2000)))

        // Editar el MISMO plan no cambia la meta de hoy: vale desde mañana.
        assertFalse(pin(snapshot(today, "plan-a", 2400)))

        assertEquals(2000, stored(today)?.calorieTargetKcal)
        assertEquals("plan-a", stored(today)?.planId)
    }

    @Test
    fun replacesTodaysGoalWhenAnotherPlanFixedIt() {
        assertTrue(pin(snapshot(today, "plan-a", 2000)))

        assertTrue(pin(snapshot(today, "plan-b", 2600)))

        assertEquals("plan-b", stored(today)?.planId)
        assertEquals(2600, stored(today)?.calorieTargetKcal)
        assertEquals("un solo registro por fecha", 1, runBlocking { db.nutritionDao().getAllDailyGoalSnapshots() }.size)
    }

    @Test
    fun replacesTodaysGoalWhenTheExistingRowBelongsToNoPlan() {
        runBlocking { db.nutritionDao().insertDailyGoalSnapshot(snapshot(today, null, 1800)) }

        assertTrue(pin(snapshot(today, "plan-b", 2600)))

        assertEquals("plan-b", stored(today)?.planId)
        assertEquals(2600, stored(today)?.calorieTargetKcal)
    }

    @Test
    fun neverReplacesAPastOrFutureDayEvenForAnotherPlan() {
        runBlocking {
            db.nutritionDao().insertDailyGoalSnapshot(snapshot(yesterday, "plan-a", 1800))
            db.nutritionDao().insertDailyGoalSnapshot(snapshot(tomorrow, "plan-a", 1900))
        }

        // Fuera de HOY se conserva el insert-once aunque el plan sea otro.
        assertFalse(pin(snapshot(yesterday, "plan-b", 2600)))
        assertFalse(pin(snapshot(tomorrow, "plan-b", 2600)))

        assertEquals("plan-a", stored(yesterday)?.planId)
        assertEquals(1800, stored(yesterday)?.calorieTargetKcal)
        assertEquals("plan-a", stored(tomorrow)?.planId)
        assertEquals(1900, stored(tomorrow)?.calorieTargetKcal)
    }

    @Test
    fun aDayWithoutRowStillGetsItsFirstSnapshotOutsideToday() {
        // La regla de HOY no impide fijar por primera vez otra fecha (insert-once).
        assertTrue(pin(snapshot(yesterday, "plan-a", 1800)))
        assertNull(stored(today))
        assertEquals(1800, stored(yesterday)?.calorieTargetKcal)
    }

    @Test
    fun replacementTouchesOnlyTodaysRow() {
        runBlocking {
            db.nutritionDao().insertDailyGoalSnapshot(snapshot(yesterday, "plan-a", 1800))
            db.nutritionDao().insertDailyGoalSnapshot(snapshot(today, "plan-a", 2000))
        }

        assertTrue(pin(snapshot(today, "plan-b", 2600)))

        assertEquals("plan-a", stored(yesterday)?.planId)
        assertEquals(1800, stored(yesterday)?.calorieTargetKcal)
        assertEquals("plan-b", stored(today)?.planId)
        assertEquals(2, runBlocking { db.nutritionDao().getAllDailyGoalSnapshots() }.size)
    }
}
