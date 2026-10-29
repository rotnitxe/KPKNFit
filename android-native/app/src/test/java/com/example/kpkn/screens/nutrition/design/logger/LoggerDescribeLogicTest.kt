package com.example.kpkn.screens.nutrition.design.logger

import com.example.kpkn.data.models.MealType
import com.example.kpkn.data.models.PortionPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Las reglas puras de «Describir» (Registros v4, escenas f-*): trozos del texto, mensajes, unidades, avisos, guardado y dictado. */
class LoggerDescribeLogicTest {

    private val meal = "Dos huevos revueltos con dos tostadas, medio aguacate y un café con leche"

    private fun pieces(text: String) = loggerMentionRanges(text).map { text.substring(it.first, it.last + 1) }

    @Test
    fun `el texto se parte por comas y por con y`() {
        assertEquals(listOf("Dos huevos revueltos", "dos tostadas", "medio aguacate", "un café", "leche"), pieces(meal))
        assertEquals(listOf("pollo", "arroz"), pieces("pollo + arroz."))
    }

    @Test
    fun `mientras se lee los trozos pasan de leyendo a leidos`() {
        val states = loggerReadingTokens(meal, 2).map { it.state }
        assertEquals(LoggerTokenState.DONE, states[0])
        assertEquals(LoggerTokenState.DONE, states[1])
        assertEquals(LoggerTokenState.READING, states[2])
        assertEquals(LoggerTokenState.PLAIN, states[3])
        assertTrue(loggerReadingTokens(meal, -1).all { it.state == LoggerTokenState.READING })
    }

    @Test
    fun `tras analizar quedan en verde los trozos que son alimento`() {
        val done = loggerResultTokens(meal, listOf("huevos revueltos", "tostadas", "aguacate")).map { meal.substring(it.start, it.end) }
        assertEquals(listOf("Dos huevos revueltos", "dos tostadas", "medio aguacate"), done)
    }

    @Test
    fun `el mensaje del plato dice lo que encontro`() {
        assertEquals("Leyendo tu comida" to "Estimando cantidades…", loggerAnalysisMessage(meal, 0))
        assertEquals("Encontré huevos revueltos y tostadas", loggerAnalysisMessage(meal, 2).first)
        assertEquals("Encontré huevos revueltos y tostadas, …", loggerAnalysisMessage(meal, 3).first)
    }

    @Test
    fun `las cifras se leen a medias y los gramos enteros`() {
        val egg = LoggerUnit(LoggerUnitKind.NATURAL, "huevo", "huevos", 55.0)
        val grams = LoggerUnit(LoggerUnitKind.GRAMS, "gramo", "gramos", 1.0)
        assertEquals("2", loggerFormatValue(2.0, egg))
        assertEquals("½", loggerFormatValue(0.5, egg))
        assertEquals("1½", loggerFormatValue(1.5, egg))
        assertEquals("110", loggerFormatValue(110.4, grams))
        assertEquals("huevo", loggerUnitNoun(1.0, egg))
        assertEquals("huevos", loggerUnitNoun(2.0, egg))
        assertEquals("2 huevos · 110 g", loggerAmountLine(110.0, listOf(egg, grams)))
        assertEquals("150 g", loggerAmountLine(150.0, listOf(grams)))
        assertEquals("240 ml", loggerAmountLine(240.0, listOf(grams), volumeUnit = true))
    }

    @Test
    fun `el menos y el mas van de diez en diez o de unidad en unidad`() {
        val egg = LoggerUnit(LoggerUnitKind.NATURAL, "huevo", "huevos", 55.0)
        val grams = LoggerUnit(LoggerUnitKind.GRAMS, "gramo", "gramos", 1.0)
        assertEquals(160.0, loggerStepGrams(150.0, grams, +1), 1e-9)
        assertEquals(140.0, loggerStepGrams(150.0, grams, -1), 1e-9)
        assertEquals(5.0, loggerStepGrams(10.0, grams, -1), 1e-9)
        assertEquals(165.0, loggerStepGrams(110.0, egg, +1), 1e-9)
        assertEquals(27.5, loggerStepGrams(55.0, egg, -1), 1e-9)
        assertEquals(27.5, loggerStepGrams(27.5, egg, -1), 1e-9)
    }

    @Test
    fun `sin alimento las unidades son gramos y raciones`() {
        assertEquals(listOf(LoggerUnitKind.GRAMS, LoggerUnitKind.SERVINGS), loggerUnits("Lentejas guisadas", null).map { it.kind })
        assertEquals(LoggerUnitKind.SPOONS, loggerUnits("Miel", null).last().kind)
        assertEquals(listOf("Gramos", "Raciones"), loggerUnits("Lentejas guisadas", null).map { it.chipLabel })
    }

    @Test
    fun `el aviso de error usa los textos del lienzo`() {
        val nothing = loggerNoticeText(LoggerNoticeKind.NOTHING, emptyList())
        assertEquals("No pude leer tu comida", nothing.title)
        val partial = loggerNoticeText(LoggerNoticeKind.PARTIAL, listOf("las lentejas", "el chorizo"))
        assertEquals("Lo leí a medias", partial.title)
        assertEquals(
            "Reconocí las lentejas y el chorizo, pero no las cantidades. Las dejé en una ración normal: revísalas antes de guardar.",
            partial.message,
        )
        assertEquals("No pude leer tu comida", loggerNoticeText(LoggerNoticeKind.PARTIAL, emptyList()).title)
    }

    @Test
    fun `guardado dice la comida y lo que queda del dia`() {
        assertEquals("Desayuno guardado", loggerSavedTitle(MealType.BREAKFAST))
        assertEquals("Merienda guardada", loggerSavedTitle(MealType.SNACK))
        assertEquals("Cambios guardados", loggerSavedTitle(MealType.LUNCH, editing = true))
        assertEquals("548 kcal · te quedan 1.752 para hoy", loggerSavedLine(548, 1752))
        assertEquals("548 kcal · llegaste a tu objetivo de hoy", loggerSavedLine(548, -20))
        assertEquals("548 kcal", loggerSavedLine(548, null))
    }

    @Test
    fun `lo dictado se suma a lo escrito`() {
        assertEquals("Pollo con arroz", loggerAppendDictation("", "pollo con arroz"))
        assertEquals("Pollo con arroz, una manzana", loggerAppendDictation("Pollo con arroz", "una manzana"))
        assertEquals("Pollo, una manzana", loggerAppendDictation("Pollo,", "una manzana"))
        assertEquals("Pollo", loggerAppendDictation("Pollo", "  "))
    }

    @Test
    fun `la hoja de ajuste nombra tamanos coincidencia y cantidades escritas`() {
        assertEquals(listOf("Pequeña", "Mediana", "Grande", "Extra"), PortionPreset.entries.map(::loggerSizeLabel))
        assertEquals("Coincidencia alta · base KPKN", loggerMatchLine(0.92, false, "base KPKN"))
        assertEquals("Coincidencia media · Bimbo", loggerMatchLine(0.6, false, "Bimbo"))
        assertEquals("Estimación · base KPKN", loggerMatchLine(0.9, true, "base KPKN"))
        assertEquals(1.5, loggerParseAmount("1,5")!!, 1e-9)
        assertEquals(75.5, loggerParseAmount("75.5")!!, 1e-9)
        assertNull(loggerParseAmount("0"))
        assertNull(loggerParseAmount("abc"))
    }
}
