package com.example.kpkn.screens.nutrition.design.logger

import com.example.kpkn.data.models.LoggedFood
import com.example.kpkn.data.models.MealType
import com.example.kpkn.data.models.NutritionLog
import com.example.kpkn.data.models.NutritionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * «Repetir» y «Recientes» del registro (lienzo f-empty y s-start): los títulos, los subtítulos de día y de hábito, que hoy no cuente, el
 * tope y que solo entren las comidas consumidas y válidas. Puro: sin pantalla.
 */
class LoggerRepeatTest {

    /** Miércoles. */
    private val today = LocalDate.of(2026, 10, 28)

    private fun food(name: String, kcal: Double, amount: Double = 100.0) =
        LoggedFood(id = "f-$name", foodName = name, amount = amount, calories = kcal)

    private fun log(
        id: String,
        day: LocalDate,
        meal: MealType,
        foods: List<LoggedFood>,
        time: String = "08:30",
        status: NutritionStatus = NutritionStatus.CONSUMED,
    ) = NutritionLog(id = id, date = "${day}T$time:00-03:00", mealType = meal, foods = foods, status = status)

    private val avena = listOf(food("Avena en copos", 150.0), food("Plátano", 262.0))

    @Test
    fun `el titulo une los nombres con con y y`() {
        assertEquals("Yogur griego", loggerRepeatTitle(listOf(food("Yogur griego", 1.0))))
        assertEquals("Avena en copos con plátano", loggerRepeatTitle(avena))
        assertEquals(
            "Pollo con arroz y brócoli",
            loggerRepeatTitle(listOf(food("Pollo", 1.0), food("Arroz", 1.0), food("Brócoli", 1.0))),
        )
        assertEquals(
            "Pollo con arroz, brócoli y palta",
            loggerRepeatTitle(listOf(food("Pollo", 1.0), food("Arroz", 1.0), food("Brócoli", 1.0), food("Palta", 1.0))),
        )
    }

    @Test
    fun `una comida de ayer dice de ayer con sus kcal`() {
        val cards = loggerRepeatCards(listOf(log("a", today.minusDays(1), MealType.BREAKFAST, avena)), MealType.BREAKFAST, today)
        assertEquals(1, cards.size)
        assertEquals("Avena en copos con plátano", cards[0].title)
        assertEquals("Desayuno de ayer · 412 kcal", cards[0].subtitle)
        assertEquals(avena, cards[0].foods)
    }

    @Test
    fun `de dos a seis dias dice el dia de la semana y despues la fecha`() {
        val pollo = listOf(food("Pollo", 400.0), food("Arroz", 240.0))
        val cards = loggerRepeatCards(
            listOf(
                log("lunes", today.minusDays(2), MealType.LUNCH, pollo),
                log("vieja", LocalDate.of(2026, 10, 12), MealType.DINNER, listOf(food("Tortilla", 300.0))),
            ),
            MealType.BREAKFAST,
            today,
        )
        assertEquals("Almuerzo del lunes · 640 kcal", cards[0].subtitle)
        assertEquals("Cena del 12 oct · 300 kcal", cards[1].subtitle)
    }

    @Test
    fun `lo que se repite en la semana va primero como habito`() {
        val yogur = listOf(food("Yogur griego", 146.0))
        val logs = listOf(
            log("y1", today.minusDays(1), MealType.BREAKFAST, yogur),
            log("y2", today.minusDays(3), MealType.BREAKFAST, yogur),
            log("y3", today.minusDays(5), MealType.BREAKFAST, yogur),
            log("otra", today.minusDays(2), MealType.DINNER, avena),
        )
        val cards = loggerRepeatCards(logs, MealType.BREAKFAST, today)
        assertEquals("Yogur griego", cards[0].title)
        assertEquals("3 veces esta semana · 146 kcal", cards[0].subtitle)
        assertTrue(cards[0].key.startsWith("habit:"))
        assertEquals("Avena en copos con plátano", cards[1].title)
        assertEquals(2, cards.size)
    }

    @Test
    fun `un habito pide dos veces en siete dias y en esa comida`() {
        val yogur = listOf(food("Yogur griego", 146.0))
        val logs = listOf(
            log("fuera1", today.minusDays(8), MealType.BREAKFAST, yogur),
            log("fuera2", today.minusDays(9), MealType.BREAKFAST, yogur),
            log("cena", today.minusDays(1), MealType.DINNER, yogur),
        )
        val cards = loggerRepeatCards(logs, MealType.BREAKFAST, today)
        assertTrue(cards.none { it.key.startsWith("habit:") })
        // Aun así salen como comidas recientes, sin repetir el título.
        assertEquals(listOf("Yogur griego"), cards.map { it.title })
        assertEquals("Cena de ayer · 146 kcal", cards[0].subtitle)
    }

    @Test
    fun `las comidas de hoy no son recientes`() {
        val cards = loggerRepeatCards(listOf(log("hoy", today, MealType.BREAKFAST, avena)), MealType.BREAKFAST, today)
        assertTrue(cards.isEmpty())
    }

    @Test
    fun `respeta el tope y pone las mas nuevas primero`() {
        val logs = (1..5).map { n -> log("l$n", today.minusDays(n.toLong()), MealType.LUNCH, listOf(food("Plato $n", 100.0))) }
        assertEquals(listOf("Plato 1", "Plato 2", "Plato 3"), loggerRepeatCards(logs, MealType.LUNCH, today).map { it.title })
        assertEquals(listOf("Plato 1", "Plato 2"), loggerRepeatCards(logs, MealType.LUNCH, today, max = 2).map { it.title })
        assertTrue(loggerRepeatCards(logs, MealType.LUNCH, today, max = 0).isEmpty())
    }

    @Test
    fun `solo cuentan las comidas consumidas y validas`() {
        val logs = listOf(
            log("plan", today.minusDays(1), MealType.BREAKFAST, avena, status = NutritionStatus.PLANNED),
            log("cero", today.minusDays(2), MealType.BREAKFAST, listOf(food("Aire", 0.0, amount = 0.0))),
            log("vacia", today.minusDays(3), MealType.BREAKFAST, emptyList()),
            log("sin nombre", today.minusDays(4), MealType.BREAKFAST, listOf(food("", 50.0))),
            log("ok", today.minusDays(5), MealType.BREAKFAST, listOf(food("Pan", 80.0))),
            log("futura", today.plusDays(1), MealType.BREAKFAST, listOf(food("Mañana", 80.0))),
        )
        assertEquals(listOf("Pan"), loggerRepeatCards(logs, MealType.BREAKFAST, today).map { it.title })
    }

    @Test
    fun `las kcal de mil o mas llevan punto de millar`() {
        val cards = loggerRepeatCards(
            listOf(log("a", today.minusDays(1), MealType.LUNCH, listOf(food("Asado", 1752.0)))),
            MealType.LUNCH,
            today,
        )
        assertEquals("Almuerzo de ayer · 1.752 kcal", cards[0].subtitle)
    }

    @Test
    fun `recientes saca los alimentos de las comidas mas nuevas sin repetir nombre`() {
        val logs = listOf(
            log("hoy", today, MealType.BREAKFAST, listOf(food("Avena en copos", 150.0, amount = 40.0)), time = "08:00"),
            log("ayer", today.minusDays(1), MealType.BREAKFAST, listOf(food("avena en copos", 150.0, amount = 40.0), food("Plátano", 105.0, amount = 1.5))),
            log("lunes", today.minusDays(2), MealType.LUNCH, listOf(food("Leche", 92.0, amount = 200.0), food("Pan", 80.0))),
        )
        val recents = loggerRecentFoods(logs, today)
        assertEquals(listOf("Avena en copos", "Plátano", "Leche"), recents.map { it.name })
        assertEquals("40 g · desayuno de hoy", recents[0].caption)
        assertEquals("1,5 g · desayuno de ayer", recents[1].caption)
        assertEquals("200 g · almuerzo del lunes", recents[2].caption)
        assertEquals(150, recents[0].kcal)
        assertEquals(1, loggerRecentFoods(logs, today, max = 1).size)
    }

    @Test
    fun `el dia se dice de hoy de ayer del lunes o con fecha`() {
        assertEquals("de hoy", loggerDayPhrase(today, today))
        assertEquals("de ayer", loggerDayPhrase(today.minusDays(1), today))
        assertEquals("del lunes", loggerDayPhrase(today.minusDays(2), today))
        assertEquals("del jueves", loggerDayPhrase(today.minusDays(6), today))
        assertEquals("del 21 oct", loggerDayPhrase(today.minusDays(7), today))
    }
}
