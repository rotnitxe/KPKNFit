package com.example.kpkn.screens.programdetail.design

import com.example.kpkn.domain.training.ScheduleIssue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** El aviso de calendario al empezar una sesión: cada caso con su texto, su prioridad y los nombres de los días y los meses. */
class ProgramScheduleCopyTest {

    private val thursday = LocalDate.of(2026, 10, 29)
    private val friday = LocalDate.of(2026, 10, 30)

    @Test
    fun laSesionDeOtroDiaNombraSuDiaYElDeHoy() {
        val copy = scheduleCopy(
            issue = ScheduleIssue.WrongDate(plannedDate = friday, actualDate = thursday),
            isOutsideProgram = false,
            scheduledDate = null,
            today = thursday,
        )
        assertEquals(
            ScheduleCopy(
                title = "Esta sesión es del viernes",
                message = "Hoy es jueves 29. Puedes entrenarla igual; KPKN guardará la diferencia de calendario.",
            ),
            copy,
        )
    }

    @Test
    fun hoyFueraDelProgramaDaLasDosFechasDelPrograma() {
        val copy = scheduleCopy(
            issue = ScheduleIssue.OutsideProgram(
                actualDate = LocalDate.of(2026, 10, 27),
                startDate = LocalDate.of(2026, 11, 12),
                endDate = LocalDate.of(2027, 1, 30),
            ),
            isOutsideProgram = true,
            scheduledDate = null,
            today = LocalDate.of(2026, 10, 27),
        )
        assertEquals(
            ScheduleCopy(
                title = "Hoy está fuera del programa",
                message = "El programa va del 12 nov al 30 ene. Puedes entrenar igual; KPKN lo registrará como desviación del plan.",
            ),
            copy,
        )
    }

    @Test
    fun hoyFueraDelProgramaSinUnaDeLasFechasSoloDiceLaDesviacion() {
        val copy = scheduleCopy(
            issue = ScheduleIssue.OutsideProgram(actualDate = thursday, startDate = null, endDate = LocalDate.of(2027, 1, 30)),
            isOutsideProgram = true,
            scheduledDate = null,
            today = thursday,
        )
        assertEquals(
            ScheduleCopy(
                title = "Hoy está fuera del programa",
                message = "Puedes entrenar igual; KPKN lo registrará como desviación del plan.",
            ),
            copy,
        )
    }

    @Test
    fun hoyNoEsDiaDeEntrenoLoDiceSinNombrarFechas() {
        val copy = scheduleCopy(
            issue = ScheduleIssue.OutsideProgramDay(actualDate = thursday),
            isOutsideProgram = false,
            scheduledDate = null,
            today = thursday,
        )
        assertEquals(
            ScheduleCopy(
                title = "Hoy no es día de entreno",
                message = "Tu plan con fechas no tiene entreno hoy. Puedes entrenar igual; KPKN lo registrará como desviación del plan.",
            ),
            copy,
        )
    }

    @Test
    fun undiaFueraDelProgramaSinSesionAvisaDeLaDesviacion() {
        val copy = scheduleCopy(issue = null, isOutsideProgram = true, scheduledDate = null, today = thursday)
        assertEquals(
            ScheduleCopy(
                title = "Este día está fuera del programa",
                message = "Entrenar aquí se registrará como desviación del plan.",
            ),
            copy,
        )
    }

    @Test
    fun unaFechaAncladaDistintaDeHoyNombraSuDia() {
        val copy = scheduleCopy(issue = null, isOutsideProgram = false, scheduledDate = friday, today = thursday)
        assertEquals(
            ScheduleCopy(
                title = "Esta sesión es del viernes",
                message = "Hoy es jueves 29. Puedes entrenarla igual; KPKN guardará la diferencia de calendario.",
            ),
            copy,
        )
    }

    @Test
    fun sinAvisoSiLaSesionEsDeHoyOSinFecha() {
        assertNull(scheduleCopy(issue = null, isOutsideProgram = false, scheduledDate = thursday, today = thursday))
        assertNull(scheduleCopy(issue = null, isOutsideProgram = false, scheduledDate = null, today = thursday))
    }

    @Test
    fun unProblemaDelMotorGanaAFueraDelProgramaYALaFecha() {
        val copy = scheduleCopy(
            issue = ScheduleIssue.OutsideProgramDay(actualDate = thursday),
            isOutsideProgram = true,
            scheduledDate = friday,
            today = thursday,
        )
        assertEquals("Hoy no es día de entreno", copy?.title)
    }

    @Test
    fun fueraDelProgramaGanaAUnaFechaDistintaDeHoy() {
        val copy = scheduleCopy(issue = null, isOutsideProgram = true, scheduledDate = friday, today = thursday)
        assertEquals("Este día está fuera del programa", copy?.title)
    }

    @Test
    fun losDiasSalenEnEspanolYEnMinusculas() {
        val week = listOf(
            LocalDate.of(2026, 10, 26) to "lunes",
            LocalDate.of(2026, 10, 27) to "martes",
            LocalDate.of(2026, 10, 28) to "miércoles",
            LocalDate.of(2026, 10, 29) to "jueves",
            LocalDate.of(2026, 10, 30) to "viernes",
            LocalDate.of(2026, 10, 31) to "sábado",
            LocalDate.of(2026, 11, 1) to "domingo",
        )
        for ((planned, name) in week) {
            val copy = scheduleCopy(
                issue = ScheduleIssue.WrongDate(plannedDate = planned, actualDate = thursday),
                isOutsideProgram = false,
                scheduledDate = null,
                today = thursday,
            )
            assertEquals("Esta sesión es del $name", copy?.title)
        }
    }

    @Test
    fun laHojaDeLaFechaNombraElDiaEnMinusculas() {
        assertEquals("Fecha del viernes", dayDateSheetTitle("Viernes"))
        assertEquals("Fecha del miércoles", dayDateSheetTitle("Miércoles"))
    }
}
