package com.example.kpkn.screens.monthsummary

import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.domain.training.progress.MonthlyBestMark
import com.example.kpkn.domain.training.progress.MonthlyLiftGain
import com.example.kpkn.domain.training.progress.MonthlyMuscleSets
import com.example.kpkn.domain.training.progress.MonthlyReport
import com.example.kpkn.screens.stories.trainingTimeText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.YearMonth

/**
 * Pruebas del modelo del Resumen del mes: diapositivas, meses con resumen y aviso en Inicio. Va con Robolectric como las pruebas de
 * historias porque [trainingTimeText] vive en MonthStory.kt, cuyos estilos de texto cargan WizardFonts al inicializarse el archivo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MonthlySummaryModelTest {

    private val octubre = YearMonth.of(2026, 10)

    /** Informe de octubre de 2026 con todo lo que puede traer; cada prueba cambia lo que necesita con copy(). */
    private fun informeCompleto(): MonthlyReport = MonthlyReport(
        month = octubre,
        sessions = 14,
        trainedDays = listOf(6, 7, 8, 10, 11, 13, 14, 15, 17, 20, 21, 22, 27, 28),
        minutes = 760,
        tonnageKg = 42400.0,
        hardSets = 246,
        reps = 1986,
        tonnageVsPreviousPct = 9,
        gains = listOf(
            MonthlyLiftGain(exerciseKey = "sentadilla", exerciseName = "Sentadilla", points = listOf(135.0, 142.5), deltaKg = 7.5),
        ),
        bestMark = MonthlyBestMark(
            exerciseName = "Sentadilla",
            e1rm = 142.3,
            weight = 135.0,
            reps = 5,
            rir = 2,
            date = LocalDate.of(2026, 10, 27),
        ),
        muscles = listOf(MonthlyMuscleSets("Dorsales", 58), MonthlyMuscleSets("Deltoides", 56)),
        untrainedMuscles = listOf("Isquiotibiales", "Gemelos", "Abdominales"),
        rirMean = 1.0,
        longestStreak = 9,
        eligible = true,
    )

    private fun List<MonthSlide>.porTipo(kind: MonthSlideKind): MonthSlide = single { it.kind == kind }

    /** Registros de [programId] en las fechas ISO [fechas] («2026-09-01»); sin ejercicios, cada registro cuenta como una sesión. */
    private fun sesiones(programId: String, fechas: List<String>): List<WorkoutLog> = fechas.mapIndexed { index, fecha ->
        WorkoutLog(
            id = "$programId-$fecha-$index",
            programId = programId,
            sessionId = "s$index",
            sessionName = "Sesión $index",
            date = "${fecha}T10:00:00.000Z",
            durationMinutes = 60,
        )
    }

    /** Los días 1 a [cuantos] de [mes] («2026-09») en formato ISO. */
    private fun dias(mes: String, cuantos: Int): List<String> = (1..cuantos).map { "$mes-${it.toString().padStart(2, '0')}" }

    @Test
    fun elTiempoEntrenadoSeEscribeSinCeroDelante() {
        assertEquals("45 min", trainingTimeText(45))
        assertEquals("2 h", trainingTimeText(120))
        assertEquals("12 h 40 min", trainingTimeText(760))
        assertEquals("1 h 5 min", trainingTimeText(65))
    }

    @Test
    fun conInformeCompletoSeEnsenanLasOchoDiapositivasEnOrden() {
        val slides = monthlySummarySlides(informeCompleto(), programName = "Motor y fuerza", plannedRir = 2.0)

        assertEquals(
            listOf(
                MonthSlideKind.COVER, MonthSlideKind.CONSISTENCY, MonthSlideKind.WORK, MonthSlideKind.STRENGTH,
                MonthSlideKind.BEST_MARK, MonthSlideKind.MUSCLES, MonthSlideKind.EFFORT, MonthSlideKind.SHARE,
            ),
            slides.map { it.kind },
        )
        assertEquals("Tu octubre en Motor y fuerza", slides.porTipo(MonthSlideKind.COVER).headline)
        assertEquals("42,4", slides.porTipo(MonthSlideKind.WORK).bigValue)
        assertEquals("135 kg × 5 con RIR 2, el martes 27.", slides.porTipo(MonthSlideKind.BEST_MARK).body)
        assertEquals(
            "12 h 40 min entrenando. Tu racha más larga: 9 sesiones seguidas.",
            slides.porTipo(MonthSlideKind.CONSISTENCY).body,
        )
    }

    @Test
    fun laPortadaYLaDiapositivaFinalNombranElMesSinProgramaNiTitulo() {
        val slides = monthlySummarySlides(informeCompleto(), programName = null)

        assertEquals("Octubre 2026", slides.porTipo(MonthSlideKind.COVER).eyebrow)
        assertEquals("Tu octubre", slides.porTipo(MonthSlideKind.COVER).headline)
        assertEquals("Un mes de entreno. Esto es lo que pasó.", slides.porTipo(MonthSlideKind.COVER).body)
        assertEquals("Tu octubre", slides.porTipo(MonthSlideKind.SHARE).eyebrow)
    }

    @Test
    fun laConstanciaEscribeSesionesEnSingularYMencionaLaRachaDesdeDosSesiones() {
        val constancia = monthlySummarySlides(informeCompleto(), programName = null).porTipo(MonthSlideKind.CONSISTENCY)
        assertEquals("14", constancia.bigValue)
        assertEquals("sesiones", constancia.bigUnit)

        val una = monthlySummarySlides(informeCompleto().copy(sessions = 1), programName = null).porTipo(MonthSlideKind.CONSISTENCY)
        assertEquals("sesión", una.bigUnit)

        val sinRacha = monthlySummarySlides(informeCompleto().copy(longestStreak = 1), programName = null)
            .porTipo(MonthSlideKind.CONSISTENCY)
        assertEquals("12 h 40 min entrenando.", sinRacha.body)

        val dosSeguidas = monthlySummarySlides(informeCompleto().copy(longestStreak = 2), programName = null)
            .porTipo(MonthSlideKind.CONSISTENCY)
        assertEquals("12 h 40 min entrenando. Tu racha más larga: 2 sesiones seguidas.", dosSeguidas.body)
    }

    @Test
    fun elTrabajoTotalPonMilesConPuntoYCompareConElMesAnterior() {
        val work = monthlySummarySlides(informeCompleto(), programName = null).porTipo(MonthSlideKind.WORK)
        assertEquals("toneladas", work.bigUnit)
        assertEquals("246 series duras y 1.986 repeticiones. +9 % que en septiembre.", work.body)

        fun trabajo(pct: Int?) = monthlySummarySlides(informeCompleto().copy(tonnageVsPreviousPct = pct), programName = null)
            .porTipo(MonthSlideKind.WORK).body

        assertEquals("246 series duras y 1.986 repeticiones. Igual que en septiembre.", trabajo(0))
        assertEquals("246 series duras y 1.986 repeticiones. −3 % que en septiembre.", trabajo(-3))
        assertEquals("246 series duras y 1.986 repeticiones.", trabajo(null))
    }

    @Test
    fun laFuerzaCuentaLosEjerciciosConSingularYPlural() {
        val una = monthlySummarySlides(informeCompleto(), programName = null).porTipo(MonthSlideKind.STRENGTH)
        assertEquals("Subiste en 1 ejercicio", una.headline)
        assertEquals("e1RM de tu mejor serie de cada sesión.", una.body)

        val dos = informeCompleto().copy(
            gains = listOf(
                MonthlyLiftGain("sentadilla", "Sentadilla", listOf(135.0, 142.5), 7.5),
                MonthlyLiftGain("remo", "Remo", listOf(60.0, 62.0, 64.0), 4.0),
            ),
        )
        assertEquals("Subiste en 2 ejercicios", monthlySummarySlides(dos, programName = null).porTipo(MonthSlideKind.STRENGTH).headline)

        val sinGanancias = informeCompleto().copy(gains = emptyList())
        assertNull(monthlySummarySlides(sinGanancias, programName = null).find { it.kind == MonthSlideKind.STRENGTH })
    }

    @Test
    fun laMejorMarcaEscribeElE1rmSinCeroDecimalYLaSerie() {
        val marca = monthlySummarySlides(informeCompleto(), programName = null).porTipo(MonthSlideKind.BEST_MARK)
        assertEquals("Tu mejor marca", marca.eyebrow)
        assertEquals("142,3", marca.bigValue)
        assertEquals("kg", marca.bigUnit)
        assertEquals("Sentadilla", marca.headline)
        assertEquals("135 kg × 5 con RIR 2, el martes 27.", marca.body)

        val bestMark = informeCompleto().bestMark!!
        fun marcaCon(mark: MonthlyBestMark) = monthlySummarySlides(informeCompleto().copy(bestMark = mark), programName = null)
            .porTipo(MonthSlideKind.BEST_MARK)

        assertEquals("142,5", marcaCon(bestMark.copy(e1rm = 142.5)).bigValue)
        assertEquals("140", marcaCon(bestMark.copy(e1rm = 140.0)).bigValue)
        assertEquals("135 kg × 5, el martes 27.", marcaCon(bestMark.copy(rir = null)).body)
    }

    @Test
    fun losMusculosDicenElQueMasSYLosQueNoSeTrabajaron() {
        val musculos = monthlySummarySlides(informeCompleto(), programName = null).porTipo(MonthSlideKind.MUSCLES)
        assertEquals("Lo que más trabajaste", musculos.eyebrow)
        assertEquals("58 series de dorsales", musculos.headline)
        assertEquals("Sin trabajo directo de isquiotibiales, gemelos y abdominales en todo el mes.", musculos.body)

        fun musculosCon(muscles: List<MonthlyMuscleSets>, untrained: List<String>) = monthlySummarySlides(
            informeCompleto().copy(muscles = muscles, untrainedMuscles = untrained),
            programName = null,
        ).porTipo(MonthSlideKind.MUSCLES)

        assertEquals(
            "Sin trabajo directo de gemelos y abdominales en todo el mes.",
            musculosCon(informeCompleto().muscles, listOf("Gemelos", "Abdominales")).body,
        )
        assertNull(musculosCon(informeCompleto().muscles, emptyList()).body)
        assertEquals("1 serie de pecho", musculosCon(listOf(MonthlyMuscleSets("Pecho", 1)), emptyList()).headline)
    }

    @Test
    fun esfuerzoComparaElRirMedioConElQuePidioElPlan() {
        fun esfuerzo(rirMean: Double, plannedRir: Double?) = monthlySummarySlides(
            informeCompleto().copy(rirMean = rirMean),
            programName = null,
            plannedRir = plannedRir,
        ).porTipo(MonthSlideKind.EFFORT)

        val masCerca = esfuerzo(1.0, 2.0)
        assertEquals("Esfuerzo", masCerca.eyebrow)
        assertEquals("1,0", masCerca.bigValue)
        assertEquals("RIR medio", masCerca.bigUnit)
        assertEquals("Más cerca del fallo de lo pedido", masCerca.headline)
        assertEquals("El plan pedía 2.", masCerca.body)

        assertEquals("Cerca de lo que pedía el plan", esfuerzo(2.0, 2.0).headline)
        assertEquals("Cerca de lo que pedía el plan", esfuerzo(2.5, 2.0).headline)
        assertEquals("Más lejos del fallo de lo pedido", esfuerzo(3.0, 2.0).headline)

        val sinPlan = esfuerzo(1.0, null)
        assertNull(sinPlan.headline)
        assertNull(sinPlan.body)
    }

    @Test
    fun sinGananciasMarcaMusculosNiRirSoloPortadaConstanciaTrabajoYCompartir() {
        val report = informeCompleto().copy(gains = emptyList(), bestMark = null, muscles = emptyList(), rirMean = null)

        assertEquals(
            listOf(MonthSlideKind.COVER, MonthSlideKind.CONSISTENCY, MonthSlideKind.WORK, MonthSlideKind.SHARE),
            monthlySummarySlides(report, programName = null).map { it.kind },
        )
    }

    @Test
    fun losResumenesSonLosMesesElegiblesAnterioresAlActualDelProgramaActual() {
        val logs = sesiones("p1", dias("2026-09", 5)) +
            sesiones("p1", dias("2026-08", 2)) +
            sesiones("p2", dias("2026-08", 5)) +
            sesiones("p1", dias("2026-10", 4))

        assertEquals(listOf(YearMonth.of(2026, 9)), summaryMonths(logs, "p1", LocalDate.of(2026, 10, 15)))
    }

    @Test
    fun losMesesVanDelMasRecienteAlMasAntiguoYRespetanElLimite() {
        val logs = sesiones("p1", dias("2026-07", 4)) + sesiones("p1", dias("2026-09", 4)) + sesiones("p1", dias("2026-08", 4))
        val today = LocalDate.of(2026, 10, 1)

        assertEquals(
            listOf(YearMonth.of(2026, 9), YearMonth.of(2026, 8), YearMonth.of(2026, 7)),
            summaryMonths(logs, "p1", today),
        )
        assertEquals(listOf(YearMonth.of(2026, 9)), summaryMonths(logs, "p1", today, limit = 1))
    }

    @Test
    fun lasFechasQueNoSeLeenNoCuentan() {
        val logs = sesiones("p1", dias("2026-09", 4)) + sesiones("p1", listOf("2026-08-40", "2026-08-41", "2026-08-42", "2026-08-43"))

        assertEquals(listOf(YearMonth.of(2026, 9)), summaryMonths(logs, "p1", LocalDate.of(2026, 10, 1)))
    }

    @Test
    fun elAvisoEnInicioSoloEnLosDiezPrimerosDiasSiElMesEsElegibleYNoSeHaVisto() {
        val septiembre = YearMonth.of(2026, 9)
        val visto = setOf(summaryKey("p1", septiembre))

        assertEquals(septiembre, homeNoticeMonth(LocalDate.of(2026, 10, 3), listOf(septiembre), emptySet(), "p1"))
        assertEquals(septiembre, homeNoticeMonth(LocalDate.of(2026, 10, 10), listOf(septiembre), emptySet(), "p1"))
        assertNull(homeNoticeMonth(LocalDate.of(2026, 10, 11), listOf(septiembre), emptySet(), "p1"))
        assertNull(homeNoticeMonth(LocalDate.of(2026, 10, 3), listOf(septiembre), visto, "p1"))
        assertNull(homeNoticeMonth(LocalDate.of(2026, 10, 3), emptyList(), emptySet(), "p1"))
        // Un resumen visto de otro programa no silencia este.
        assertEquals(
            septiembre,
            homeNoticeMonth(LocalDate.of(2026, 10, 3), listOf(septiembre), setOf(summaryKey("p2", septiembre)), "p1"),
        )
    }

    @Test
    fun elAvisoDeEneroMiraDiciembreDelAnoAnterior() {
        val diciembre = YearMonth.of(2025, 12)

        assertEquals(diciembre, homeNoticeMonth(LocalDate.of(2026, 1, 4), listOf(diciembre), emptySet(), "p1"))
    }

    @Test
    fun laClaveDelResumenUneProgramaYMes() {
        assertEquals("p1|2026-09", summaryKey("p1", YearMonth.of(2026, 9)))
    }
}
