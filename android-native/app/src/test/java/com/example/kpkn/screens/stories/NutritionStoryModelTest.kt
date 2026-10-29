package com.example.kpkn.screens.stories

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import com.example.kpkn.data.models.LoggedFood
import com.example.kpkn.data.models.NutritionLog
import com.example.kpkn.data.models.NutritionStatus
import com.example.kpkn.domain.nutrition.MacroGoals
import org.junit.Test
import java.time.LocalDate

/** Pruebas puras del modelo de las historias de Nutrición: textos del lienzo, racha y que nunca salgan alimentos. */
class NutritionStoryModelTest {

    /** Lunes 19 de octubre de 2026 y los % del lienzo (96, 102, 88, 99, 104, 121, 95) sobre 2.300 kcal. */
    private val semana = listOf(96, 102, 88, 99, 104, 121, 95).mapIndexed { i, pct ->
        NutritionStoryDay(LocalDate.of(2026, 10, 19).plusDays(i.toLong()), kcal = 2300 * pct / 100, goal = 2300)
    }

    private val macros = NutritionStoryMacros(162.0, 228.0, 71.0, proteinTargetG = 180.0, carbsTargetG = 250.0, fatTargetG = 75.0, days = 7)

    @Test
    fun `Semana con dias en objetivo y el dia pasado en acento`() {
        val pagina = nutritionStoryPage(NutritionStoryTemplate.SEMANA, semana, macros, null)
        assertEquals("Mi semana · nutrición", pagina.eyebrow)
        assertEquals("6 de 7 días", pagina.title)
        assertEquals("En mi objetivo de 2.300 kcal", pagina.sub)
        assertEquals(listOf("L", "M", "X", "J", "V", "S", "D"), pagina.days.map { it.letter })
        assertEquals(listOf(false, false, false, false, false, true, false), pagina.days.map { it.over })
        assertEquals(3, pagina.ringParts.size)
    }

    @Test
    fun `sin Calorias no sale la cifra del objetivo ni la media`() {
        val pagina = nutritionStoryPage(NutritionStoryTemplate.SEMANA, semana, macros, null, NutritionStoryOptions(calories = false))
        assertEquals("En mi objetivo", pagina.sub)
        assertNull(pagina.ringCenter)
    }

    @Test
    fun `Macros con la proteina de titular y barras frente al objetivo`() {
        val pagina = nutritionStoryPage(NutritionStoryTemplate.MACROS, semana, macros, null)
        assertEquals("Media diaria · 7 días", pagina.eyebrow)
        assertEquals("162 g de proteína", pagina.title)
        assertTrue(pagina.sub!!.endsWith("kcal · objetivo cumplido"))
        assertEquals(listOf("Proteína", "Carbos", "Grasa"), pagina.macros.map { it.label })
        assertEquals(0.9f, pagina.macros.first().fill, 0.001f)
    }

    @Test
    fun `racha de un dia y mejor racha`() {
        val uno = nutritionStoryPage(NutritionStoryTemplate.RACHA, emptyList(), null, NutritionStoryStreak(current = 1, best = 1))
        assertEquals("1 día", uno.title)
        assertNull(uno.badge)
        val veintitres = nutritionStoryPage(NutritionStoryTemplate.RACHA, emptyList(), null, NutritionStoryStreak(current = 23, best = 23))
        assertEquals("23 días", veintitres.title)
        assertEquals("Mi mejor racha", veintitres.badge)
        assertEquals(4, veintitres.streakWeeks)
    }

    @Test
    fun `plantillas solo con datos`() {
        assertEquals(
            listOf(NutritionStoryTemplate.SEMANA, NutritionStoryTemplate.MACROS, NutritionStoryTemplate.RACHA),
            nutritionStoryTemplates(semana, macros, NutritionStoryStreak(3, 5)),
        )
        assertTrue(nutritionStoryTemplates(semana.map { it.copy(kcal = null) }, null, NutritionStoryStreak(0, 4)).isEmpty())
    }

    @Test
    fun `la media de macros cuenta solo los dias registrados y no lo planificado`() {
        val lunes = LocalDate.of(2026, 10, 19)
        val martes = lunes.plusDays(1)
        val logs = listOf(
            NutritionLog(date = "2026-10-19", foods = listOf(LoggedFood(protein = 100.0, carbs = 200.0, fats = 50.0))),
            NutritionLog(date = "2026-10-19T20:00", foods = listOf(LoggedFood(protein = 60.0, carbs = 0.0, fats = 10.0))),
            NutritionLog(date = "2026-10-20", foods = listOf(LoggedFood(protein = 140.0, carbs = 100.0, fats = 70.0))),
            NutritionLog(date = "2026-10-21", status = NutritionStatus.PLANNED, foods = listOf(LoggedFood(protein = 999.0))),
        )
        val objetivos = mapOf(lunes to MacroGoals(proteinGoal = 160, carbGoal = 200, fatGoal = 60), martes to MacroGoals(proteinGoal = 180))
        val media = nutritionStoryMacrosOf(logs, listOf(lunes, martes, lunes.plusDays(2)), objetivos)!!
        assertEquals(2, media.days)
        assertEquals(150.0, media.proteinG, 0.001)
        assertEquals(150.0, media.carbsG, 0.001)
        assertEquals(65.0, media.fatG, 0.001)
        assertEquals(170.0, media.proteinTargetG!!, 0.001)
        assertNull("el martes no tiene objetivo de carbohidratos", media.carbsTargetG)
    }

    @Test
    fun `sin dias registrados no hay media de macros`() {
        val lunes = LocalDate.of(2026, 10, 19)
        assertNull(nutritionStoryMacrosOf(emptyList(), listOf(lunes), emptyMap()))
    }
}
