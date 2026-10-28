package com.example.kpkn.screens.stories

import com.example.kpkn.domain.training.progress.MonthlyBestMark
import com.example.kpkn.domain.training.progress.MonthlyLiftGain
import com.example.kpkn.domain.training.progress.MonthlyMuscleSets
import com.example.kpkn.domain.training.progress.MonthlyReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.YearMonth

/**
 * Pruebas de la historia del mes: el paso del informe a sus datos, las plantillas que se ofrecen y el render (1080 × 1920, con el
 * negro de la marca abajo). Los datos son los del prototipo: octubre de 2026, «Motor y fuerza».
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class MonthStoryTest {

    /** Informe de octubre de 2026 con 14 sesiones; los parámetros cambian solo lo que se pase. */
    private fun informe(
        minutes: Int = 760,
        tonnageKg: Double = 42400.0,
        hardSets: Int = 246,
        gains: List<MonthlyLiftGain> = emptyList(),
        muscles: List<MonthlyMuscleSets> = emptyList(),
    ): MonthlyReport = MonthlyReport(
        month = YearMonth.of(2026, 10),
        sessions = 14,
        trainedDays = listOf(6, 7, 8, 10, 11, 13, 14, 15, 17, 20, 21, 22, 27, 28),
        minutes = minutes,
        tonnageKg = tonnageKg,
        hardSets = hardSets,
        reps = 1500,
        tonnageVsPreviousPct = 8,
        gains = gains,
        bestMark = MonthlyBestMark(
            exerciseName = "Sentadilla",
            e1rm = 142.3,
            weight = 135.0,
            reps = 5,
            rir = 2,
            date = LocalDate.of(2026, 10, 27),
        ),
        muscles = muscles,
        untrainedMuscles = emptyList(),
        rirMean = 2.0,
        longestStreak = 9,
        eligible = true,
    )

    /** Dos ejercicios con ganancia, como los del prototipo. */
    private val ganancias = listOf(
        MonthlyLiftGain("sentadilla", "Sentadilla", listOf(135.0, 136.0, 138.0, 139.0, 141.0, 142.5), 7.5),
        MonthlyLiftGain("press-banca", "Press banca", listOf(97.0, 97.5, 99.0, 99.0, 100.0, 101.0), 4.0),
    )

    /** Tres músculos con series, como los del prototipo. */
    private val musculos = listOf(
        MonthlyMuscleSets("Dorsales", 58),
        MonthlyMuscleSets("Deltoides", 56),
        MonthlyMuscleSets("Cuádriceps", 52),
    )

    /** Del informe salen el mes, las cifras con su unidad, la marca redondeada a 0,5 y la meta con el plan. */
    @Test
    fun elInformeSeConvierteEnLasCifrasDelMes() {
        val story = monthStoryFrom(informe(), programName = "Motor y fuerza", planPct = 92)

        assertEquals("octubre 2026", story.monthLabel)
        assertEquals(MonthStat("12", "h 40", "Entrenando"), story.stats[1])
        assertEquals(MonthStat("42,4", "t", "Movidas"), story.stats[2])
        assertEquals(MonthStat("92", "%", "Del plan"), story.stats[3])
        assertEquals("Marca: sentadilla 142,5 kg", story.badge)
        assertEquals("12 h 40 min entrenando · 92 % del plan", story.consistencyMeta)
    }

    /** Sin plan, el cuarto dato son las series; sin programa, la historia no lleva nombre de programa. */
    @Test
    fun sinPlanElCuartoDatoSonLasSeriesYSinProgramaNoHayNombre() {
        val story = monthStoryFrom(informe(), programName = null, planPct = null)

        assertEquals(MonthStat("246", null, "Series"), story.stats[3])
        assertNull(story.programName)
    }

    /** Las ganancias van con coma decimal y sin «,0». */
    @Test
    fun lasGananciasSeEscribenConComaYSinCeroDecimal() {
        val story = monthStoryFrom(informe(gains = ganancias), programName = null, planPct = null)

        assertEquals(listOf("+7,5 kg", "+4 kg"), story.lifts.map { it.gain })
    }

    /** Sin ganancias ni series por músculo solo se ofrecen Resumen y Constancia. */
    @Test
    fun sinGananciasNiMusculosSoloSeOfrecenResumenYConstancia() {
        val story = monthStoryFrom(informe(), programName = null, planPct = null)

        assertEquals(listOf(MonthStoryTemplate.RESUMEN, MonthStoryTemplate.CONSTANCIA), monthStoryTemplates(story))
    }

    /** Con ganancias y series se ofrecen las cuatro plantillas, en su orden. */
    @Test
    fun conGananciasYMusculosSeOfrecenLasCuatroPlantillas() {
        val story = monthStoryFrom(informe(gains = ganancias, muscles = musculos), programName = null, planPct = null)

        assertEquals(
            listOf(MonthStoryTemplate.RESUMEN, MonthStoryTemplate.CONSTANCIA, MonthStoryTemplate.FUERZA, MonthStoryTemplate.MUSCULOS),
            monthStoryTemplates(story),
        )
    }

    /** Las cuatro plantillas salen a 1080 × 1920 y el píxel de abajo es el negro de la marca. */
    @Test
    fun lasCuatroPlantillasSaleN1080Por1920YElFondoDeAbajoEsNegro() {
        val story = monthStoryFrom(informe(gains = ganancias, muscles = musculos), programName = "Motor y fuerza", planPct = 92)

        for (template in MonthStoryTemplate.entries) {
            val bitmap = renderMonthStory(RuntimeEnvironment.getApplication(), story, template)
            assertEquals(template.label, 1080, bitmap.width)
            assertEquals(template.label, 1920, bitmap.height)
            assertEquals(template.label, 0xFF0A0A0A.toInt(), bitmap.getPixel(540, 1900))
        }
    }
}
