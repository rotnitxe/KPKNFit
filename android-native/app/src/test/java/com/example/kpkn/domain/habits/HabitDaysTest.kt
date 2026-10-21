package com.example.kpkn.domain.habits

import com.example.kpkn.data.models.BodyMetric
import com.example.kpkn.data.models.BodyObservation
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.NutritionLog
import com.example.kpkn.data.models.NutritionStatus
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.domain.nutrition.expenditure.DayMark
import com.example.kpkn.domain.nutrition.expenditure.DayMarkEntry
import com.example.kpkn.domain.nutrition.expenditure.DayMarkSource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/** Adaptadores de datos a días locales y resumen de los tres hábitos. Valores esperados escritos a mano. */
class HabitDaysTest {

    private val utc: ZoneId = ZoneId.of("UTC")
    private val madrid: ZoneId = ZoneId.of("Europe/Madrid")
    private val today: LocalDate = LocalDate.of(2026, 10, 21)

    private fun day(iso: String): LocalDate = LocalDate.parse(iso)

    private fun dates(vararg iso: String): Set<LocalDate> = iso.map { day(it) }.toSet()

    private fun dayMark(iso: String, value: DayMark): DayMarkEntry =
        DayMarkEntry(date = day(iso), mark = value, source = DayMarkSource.USER, updatedAt = Instant.EPOCH)

    private fun meal(id: String, date: String, status: NutritionStatus = NutritionStatus.CONSUMED): NutritionLog =
        NutritionLog(id = id, date = date, status = status)

    private fun weigh(id: String, iso: String, value: Double, zone: String = "UTC", metric: BodyMetric = BodyMetric.WEIGHT): BodyObservation =
        BodyObservation(
            id = id,
            metric = metric,
            valueSi = value,
            unitSi = "kg",
            timestampEpochMs = Instant.parse(iso).toEpochMilli(),
            zoneId = zone,
        )

    private fun working(count: Int): List<CompletedSet> = List(count) { CompletedSet(id = "s$it", reps = 8) }

    private fun session(
        id: String,
        date: String,
        sets: List<CompletedSet>,
        actualDate: String? = null,
        breakId: String? = null,
    ): WorkoutLog = WorkoutLog(
        id = id,
        programId = "p1",
        sessionId = "sesion-$id",
        sessionName = "Sesion de prueba",
        date = date,
        durationMinutes = 60,
        completedExercises = listOf(CompletedExercise(exerciseId = "e1", exerciseName = "Sentadilla", sets = sets)),
        actualDate = actualDate,
        calendarBreakId = breakId,
    )

    @Test
    fun `las comidas cuentan si estan consumidas o tienen marca de dia completo o ayuno`() {
        val logs = listOf(meal("n1", "2026-10-19T13:00"), meal("n2", "2026-10-20T20:30", NutritionStatus.PLANNED))
        val marks = listOf(
            dayMark("2026-10-18", DayMark.FASTING),
            dayMark("2026-10-17", DayMark.PARTIAL),
            dayMark("2026-10-16", DayMark.COMPLETE_CONFIRMED),
            dayMark("2026-10-19", DayMark.COMPLETE_CONFIRMED),
        )
        assertEquals(dates("2026-10-16", "2026-10-18", "2026-10-19"), mealRecordDays(logs, marks))
    }

    @Test
    fun `el pesaje cuenta solo los pesos validos en su dia local`() {
        val observations = listOf(
            weigh("w1", "2026-10-20T23:30:00Z", 78.4, zone = "Europe/Madrid"),
            weigh("w2", "2026-10-19T07:00:00Z", 80.0),
            weigh("w3", "2026-10-18T07:00:00Z", 5.0),
            weigh("w4", "2026-10-17T07:00:00Z", 90.0, metric = BodyMetric.WAIST),
        )
        assertEquals(dates("2026-10-19", "2026-10-21"), weighInDays(observations, utc))
    }

    @Test
    fun `los entrenos de descanso no cuentan y el dia real manda sobre el planificado`() {
        val logs = listOf(
            session("a", "2026-10-19T10:00:00Z", working(3)),
            session("b", "2026-10-19T11:00:00Z", working(2), breakId = "descanso-1"),
            session(
                "c",
                "2026-10-18T10:00:00Z",
                listOf(
                    CompletedSet(id = "c-calentamiento", reps = 10, isWarmup = true),
                    CompletedSet(id = "c-1", reps = 8),
                    CompletedSet(id = "c-2", reps = 8),
                    CompletedSet(id = "c-saltada", reps = 8, skipped = true),
                ),
                actualDate = "2026-10-20T18:00:00Z",
            ),
            session("d", "2026-10-17T10:00:00Z", listOf(CompletedSet(id = "d-calentamiento", reps = 5, isWarmup = true))),
        )
        assertEquals(mapOf(day("2026-10-19") to 3, day("2026-10-20") to 2), trainingSetsByDay(logs, utc))
    }

    @Test
    fun `el dia de un entreno depende de la zona indicada`() {
        val logs = listOf(session("e", "2026-10-20T23:30:00Z", working(1)))
        assertEquals(mapOf(day("2026-10-21") to 1), trainingSetsByDay(logs, madrid))
        assertEquals(mapOf(day("2026-10-20") to 1), trainingSetsByDay(logs, utc))
    }

    @Test
    fun `una comida futura o planificada no cuenta en la racha`() {
        val logs = listOf(
            meal("p", "2026-10-21T20:00", NutritionStatus.PLANNED),
            meal("f", "2026-10-22T13:00"),
        )
        val snapshot = buildHabitsSnapshot(
            mealDays = mealRecordDays(logs, emptyList()),
            weighInDays = emptySet(),
            trainingSets = emptyMap(),
            today = today,
        )
        val meals = snapshot.summary(HabitKind.MEALS)
        assertEquals(HabitStreak(current = 0, best = 0, unit = HabitUnit.DAYS), meals.streak)
        assertEquals(0, meals.recordedInWindow)
    }

    @Test
    fun `el resumen calcula racha calendario y niveles de cada habito`() {
        val snapshot = buildHabitsSnapshot(
            mealDays = dates("2026-10-19", "2026-10-20"),
            weighInDays = dates("2026-10-21"),
            trainingSets = mapOf(day("2026-10-19") to 9, day("2026-10-20") to 16),
            today = today,
        )
        val meals = snapshot.summary(HabitKind.MEALS)
        assertEquals(HabitStreak(current = 2, best = 2, unit = HabitUnit.DAYS), meals.streak)
        assertEquals(2, meals.recordedInWindow)

        val weighing = snapshot.summary(HabitKind.WEIGHING)
        assertEquals(HabitStreak(current = 1, best = 1, unit = HabitUnit.DAYS), weighing.streak)
        assertEquals(1, weighing.recordedInWindow)

        val training = snapshot.summary(HabitKind.TRAINING)
        assertEquals(HabitStreak(current = 1, best = 1, unit = HabitUnit.WEEKS), training.streak)
        assertEquals(2, training.recordedInWindow)
        assertEquals(2, training.calendar.first { it.date == day("2026-10-19") }.level)
        assertEquals(3, training.calendar.first { it.date == day("2026-10-20") }.level)
    }
}
