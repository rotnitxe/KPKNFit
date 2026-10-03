package com.example.kpkn.data.db

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * WP-U14 / C10: `NutritionDao.getLogsForDayPrefix` busca los logs de UN día por prefijo de fecha ISO. El cajón de
 * comidas guarda `"<fecha>T12:00:00.000Z"`, así que la igualdad de `getLogsForDate` no los encuentra; los receivers de
 * recordatorios leen Room con esta consulta en vez de los StateFlow del repositorio.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class NutritionLogsForDayPrefixTest {
    private lateinit var db: KpknDatabase

    @Before
    fun setUp() {
        db = KpknDatabase.createInMemory(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun insert(id: String, date: String) =
        db.nutritionDao().upsertLog(NutritionLogEntity(id = id, date = date, mealType = "LUNCH", data = "{}"))

    @Test
    fun findsEveryLogOfTheDayWhateverFollowsTheDateAndNothingElse() = runBlocking {
        insert("drawer", "2026-10-03T12:00:00.000Z")
        insert("plain", "2026-10-03")
        insert("with-time", "2026-10-03 08:30")
        insert("day-before", "2026-10-02T23:59:59.999Z")
        insert("day-after", "2026-10-04T00:00:00.000Z")
        insert("same-month-other-day", "2026-10-30T12:00:00.000Z")
        insert("other-year", "2025-10-03T12:00:00.000Z")

        val ids = db.nutritionDao().getLogsForDayPrefix("2026-10-03").map { it.id }

        assertEquals(setOf("drawer", "plain", "with-time"), ids.toSet())
        // El orden es el de siempre: la fecha más reciente primero.
        assertEquals(listOf("drawer", "with-time", "plain"), ids)
    }

    @Test
    fun aDayWithoutLogsIsEmpty() = runBlocking {
        insert("other", "2026-10-04T12:00:00.000Z")

        assertTrue(db.nutritionDao().getLogsForDayPrefix("2026-10-03").isEmpty())
    }
}
