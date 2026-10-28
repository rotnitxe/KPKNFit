package com.example.kpkn.screens.monthsummary

import com.example.kpkn.domain.training.progress.MonthlyBestMark
import com.example.kpkn.domain.training.progress.MonthlyReport
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Pruebas del aviso «Tu <mes> está listo» de Inicio: el título y la línea de cifras. */
class MonthlySummaryNoticeTest {

    private val octubre = YearMonth.of(2026, 10)

    private val marca = MonthlyBestMark(
        exerciseName = "Sentadilla",
        e1rm = 150.0,
        weight = 140.0,
        reps = 3,
        rir = 1,
        date = LocalDate.of(2026, 10, 14),
    )

    /** Informe de octubre con [sessions] sesiones, [tonnageKg] de tonelaje y, si lo hay, la [bestMark]. */
    private fun informe(
        sessions: Int = 14,
        tonnageKg: Double = 42400.0,
        bestMark: MonthlyBestMark? = null,
    ): MonthlyReport = MonthlyReport(
        month = octubre,
        sessions = sessions,
        trainedDays = emptyList(),
        minutes = 0,
        tonnageKg = tonnageKg,
        hardSets = 0,
        reps = 0,
        tonnageVsPreviousPct = null,
        gains = emptyList(),
        bestMark = bestMark,
        muscles = emptyList(),
        untrainedMuscles = emptyList(),
        rirMean = null,
        longestStreak = 0,
        eligible = true,
    )

    @Test
    fun `el titulo nombra el mes en minusculas`() {
        assertEquals("Tu octubre está listo", monthlyNoticeTitle(octubre))
    }

    @Test
    fun `el titulo usa el nombre de cada uno de los doce meses`() {
        val meses = listOf(
            "enero", "febrero", "marzo", "abril", "mayo", "junio",
            "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
        )
        meses.forEachIndexed { indice, nombre ->
            assertEquals("Tu $nombre está listo", monthlyNoticeTitle(YearMonth.of(2026, indice + 1)))
        }
    }

    @Test
    fun `la linea con marca nueva cuenta sesiones toneladas y la marca`() {
        assertEquals("14 sesiones, 42,4 t y una marca nueva.", monthlyNoticeLine(informe(bestMark = marca)))
    }

    @Test
    fun `la linea sin marca cuenta sesiones y toneladas`() {
        assertEquals("14 sesiones y 42,4 t.", monthlyNoticeLine(informe()))
    }

    @Test
    fun `una sola sesion va en singular`() {
        assertEquals("1 sesión y 0,5 t.", monthlyNoticeLine(informe(sessions = 1, tonnageKg = 500.0)))
    }
}
