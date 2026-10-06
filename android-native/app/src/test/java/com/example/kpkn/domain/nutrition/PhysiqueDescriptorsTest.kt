package com.example.kpkn.domain.nutrition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [bodyFatDescriptor]: la frase «Categoría · rasgo visual» que acompaña al porcentaje en la regla del alta.
 * Pura y sin Android: se prueba cada entero de la escala y los límites exactos de cada banda.
 */
class PhysiqueDescriptorsTest {

    /** Categoría y enteros de la banda, tal como los dicta la tabla de la figura masculina (3–60 %). */
    private val maleBands = listOf(
        "Competición" to 3..9,
        "Atlético" to 10..13,
        "En forma" to 14..17,
        "Saludable" to 18..21,
        "Promedio" to 22..24,
        "Con reserva" to 25..29,
        "Volumen moderado" to 30..34,
        "Volumen alto" to 35..40,
        "Volumen muy alto" to 41..60,
    )

    /** Ídem para la figura femenina. */
    private val femaleBands = listOf(
        "Competición" to 3..13,
        "Atlética" to 14..17,
        "Atlética suave" to 18..20,
        "En forma" to 21..24,
        "Saludable" to 25..28,
        "Promedio" to 29..31,
        "Con reserva" to 32..37,
        "Volumen alto" to 38..44,
        "Volumen muy alto" to 45..60,
    )

    private fun categoryOf(text: String) = text.substringBefore(" · ")

    private fun describe(percent: Int, model: String?) = bodyFatDescriptor(percent.toDouble(), model)

    // ─── Cobertura ───────────────────────────────────────────────────────────

    @Test
    fun everyWholePercentFrom3To60HasADescriptorForBothFigures() {
        for (model in listOf("male", "female")) {
            for (percent in 3..60) {
                val text = describe(percent, model)
                assertTrue("$model $percent %: texto vacío", text.isNotBlank())
                assertTrue("$model $percent %: ${text.length} caracteres (máx. 70)", text.length <= 70)
                assertTrue("$model $percent %: formato «Categoría · rasgo»: $text", " · " in text)
                assertTrue("$model $percent %: la categoría y el rasgo no pueden estar vacíos", categoryOf(text).isNotBlank() && text.substringAfter(" · ").isNotBlank())
            }
        }
    }

    // ─── Límites de banda ────────────────────────────────────────────────────

    @Test
    fun maleBandsStartAndEndExactlyWhereTheTableSays() {
        for ((category, range) in maleBands) {
            for (percent in range) {
                assertEquals("hombre $percent %", category, categoryOf(describe(percent, "male")))
            }
        }
    }

    @Test
    fun femaleBandsStartAndEndExactlyWhereTheTableSays() {
        for ((category, range) in femaleBands) {
            for (percent in range) {
                assertEquals("mujer $percent %", category, categoryOf(describe(percent, "female")))
            }
        }
    }

    @Test
    fun theBandChangesBetweenTheLastIntegerOfOneBandAndTheFirstOfTheNext() {
        // Los pares (último de una banda, primero de la siguiente): hombre 9|10, 13|14, 17|18, 21|22, 24|25, 29|30, 34|35, 40|41.
        for ((low, high) in listOf(9 to 10, 13 to 14, 17 to 18, 21 to 22, 24 to 25, 29 to 30, 34 to 35, 40 to 41)) {
            assertNotEquals("hombre $low|$high", describe(low, "male"), describe(high, "male"))
        }
        // Mujer 13|14, 17|18, 20|21, 24|25, 28|29, 31|32, 37|38, 44|45.
        for ((low, high) in listOf(13 to 14, 17 to 18, 20 to 21, 24 to 25, 28 to 29, 31 to 32, 37 to 38, 44 to 45)) {
            assertNotEquals("mujer $low|$high", describe(low, "female"), describe(high, "female"))
        }
    }

    @Test
    fun theTextsMatchTheApprovedCopyAtEachBand() {
        assertEquals("Competición · definición extrema, venas visibles y mínima reserva", describe(9, "male"))
        assertEquals("Atlético · abdomen marcado y músculo bien separado", describe(10, "male"))
        assertEquals("En forma · abdomen visible y contorno muscular claro", describe(14, "male"))
        assertEquals("Saludable · cintura suave y músculo que se intuye", describe(18, "male"))
        assertEquals("Promedio · forma pareja, el músculo se nota poco", describe(22, "male"))
        assertEquals("Con reserva · el abdomen redondea y la cintura gana volumen", describe(25, "male"))
        assertEquals("Volumen moderado · grasa concentrada en torso y caderas", describe(30, "male"))
        assertEquals("Volumen alto · contorno amplio y uniforme", describe(35, "male"))
        assertEquals("Volumen muy alto · un buen punto de partida para un plan guiado", describe(41, "male"))

        assertEquals("Competición · definición extrema, cerca del mínimo esencial", describe(13, "female"))
        assertEquals("Atlética · abdomen definido y poca grasa en caderas", describe(14, "female"))
        assertEquals("Atlética suave · contorno muscular claro y cintura marcada", describe(18, "female"))
        assertEquals("En forma · curvas firmes y músculo visible en hombros y piernas", describe(21, "female"))
        assertEquals("Saludable · forma redondeada, el músculo se intuye", describe(25, "female"))
        assertEquals("Promedio · más volumen en cintura y caderas", describe(29, "female"))
        assertEquals("Con reserva · volumen en caderas y muslos, estructura cubierta", describe(32, "female"))
        assertEquals("Volumen alto · contorno amplio y uniforme", describe(38, "female"))
        assertEquals("Volumen muy alto · un buen punto de partida para un plan guiado", describe(45, "female"))
    }

    // ─── Figura ──────────────────────────────────────────────────────────────

    @Test
    fun anythingButFemaleUsesTheMaleBands() {
        for (model in listOf(null, "male", "", "otro", "Male")) {
            for (percent in 3..60) {
                assertEquals("modelo=$model $percent %", describe(percent, "male"), describe(percent, model))
            }
        }
    }

    @Test
    fun theFemaleFigureIsRecognisedIgnoringCaseAndSpaces() {
        for (model in listOf("female", "FEMALE", " Female ")) {
            assertEquals("modelo=$model", describe(20, "female"), describe(20, model))
        }
    }

    @Test
    fun theTwoFiguresDescribeTheSamePercentDifferently() {
        assertNotEquals(describe(20, "male"), describe(20, "female"))
        assertNotEquals(describe(30, "male"), describe(30, "female"))
    }

    @Test
    fun theOpenTopBandSharesOneSentenceAcrossFigures() {
        assertEquals(describe(60, "male"), describe(60, "female"))
        assertEquals(describe(41, "male"), describe(45, "female"))
    }

    // ─── Valores raros ───────────────────────────────────────────────────────

    @Test
    fun decimalsRoundToTheNearestWholePercentLikeTheRulerDoes() {
        assertEquals("Atlético", categoryOf(bodyFatDescriptor(13.4, "male")))
        assertEquals("En forma", categoryOf(bodyFatDescriptor(13.5, "male")))
        assertEquals("Competición", categoryOf(bodyFatDescriptor(9.4, "male")))
        assertEquals("Atlético", categoryOf(bodyFatDescriptor(9.5, "male")))
        assertEquals("Atlética suave", categoryOf(bodyFatDescriptor(17.6, "female")))
    }

    @Test
    fun valuesOutsideTheTableStayInTheEndBandsAndNeverThrow() {
        assertEquals("Competición", categoryOf(bodyFatDescriptor(0.0, "male")))
        assertEquals("Competición", categoryOf(bodyFatDescriptor(-12.0, "female")))
        assertEquals("Competición", categoryOf(bodyFatDescriptor(Double.NEGATIVE_INFINITY, "male")))
        assertEquals("Volumen muy alto", categoryOf(bodyFatDescriptor(250.0, "male")))
        assertEquals("Volumen muy alto", categoryOf(bodyFatDescriptor(Double.POSITIVE_INFINITY, "female")))
        // Algo que no es un número no rompe la pantalla: se describe como la mitad de la escala.
        assertEquals(describe(25, "male"), bodyFatDescriptor(Double.NaN, "male"))
        assertEquals(describe(25, "female"), bodyFatDescriptor(Double.NaN, "female"))
    }

    // ─── Tono ────────────────────────────────────────────────────────────────

    @Test
    fun theToneDescribesWithoutJudging() {
        val judgemental = listOf("obes", "gord", "flac", "feo", "mal ", "horrible", "excesiv", "demasiad", "descuid")
        for (model in listOf("male", "female")) {
            for (percent in 3..60) {
                val text = describe(percent, model).lowercase()
                for (word in judgemental) {
                    assertFalse("$model $percent %: «$word» en «$text»", word in text)
                }
            }
        }
    }
}
