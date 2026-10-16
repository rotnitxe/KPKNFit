package com.example.kpkn.screens.workout.components

import com.example.kpkn.data.models.MuscleRecoveryStatus
import com.example.kpkn.data.models.RecoveryStatus
import com.example.kpkn.data.models.WorkoutLog
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Las piezas puras del estado previo (LV10b): el día de las molestias de la sesión anterior y los valores de todos los músculos para
 * el ajuste del cuerpo. Sin Compose ni Android.
 */
class WorkoutReadinessSupportTest {

    private val utc = ZoneId.of("UTC")

    private fun log(id: String, date: String, still: List<String>) = WorkoutLog(
        id = id,
        programId = "p",
        sessionId = "s",
        sessionName = "Sesión",
        date = date,
        durationMinutes = 60,
        stillPresentDiscomfortIds = still,
    )

    // ── el día de una molestia ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `una molestia lleva el dia de la semana del registro que la dio por presente`() {
        // El 12 de octubre de 2026 fue lunes y el 9, viernes.
        val history = listOf(
            log("a", "2026-10-12T10:00:00.000Z", listOf("knee_patellar")),
            log("b", "2026-10-09T18:30:00.000Z", listOf("lumbar")),
        )
        val result = readinessReportedBefore(
            previousIds = listOf("knee_patellar", "lumbar"),
            history = history,
            today = LocalDate.of(2026, 10, 14),
            zone = utc,
        )
        assertEquals(
            listOf(ReadinessReportedDiscomfort("knee_patellar", "lunes"), ReadinessReportedDiscomfort("lumbar", "viernes")),
            result,
        )
    }

    @Test
    fun `gana el registro mas reciente que la tiene y se ignora Sin molestias y los repetidos`() {
        val history = listOf(
            log("viejo", "2026-10-02T10:00:00.000Z", listOf("lumbar")),
            log("nuevo", "2026-10-12T10:00:00.000Z", listOf("lumbar")),
        )
        val result = readinessReportedBefore(
            previousIds = listOf("none", "lumbar", "lumbar"),
            history = history,
            today = LocalDate.of(2026, 10, 14),
            zone = utc,
        )
        assertEquals(listOf(ReadinessReportedDiscomfort("lumbar", "lunes")), result)
    }

    @Test
    fun `sin registro que la nombre solo sale la molestia, sin dia`() {
        val result = readinessReportedBefore(
            previousIds = listOf("ankle"),
            history = listOf(log("a", "2026-10-12T10:00:00.000Z", listOf("lumbar"))),
            today = LocalDate.of(2026, 10, 14),
            zone = utc,
        )
        assertEquals(listOf(ReadinessReportedDiscomfort("ankle", null)), result)
    }

    @Test
    fun `sin historial no hay dias y sin molestias no hay nada`() {
        assertEquals(
            listOf(ReadinessReportedDiscomfort("ankle", null)),
            readinessReportedBefore(listOf("ankle"), emptyList(), LocalDate.of(2026, 10, 14), utc),
        )
        assertEquals(emptyList<ReadinessReportedDiscomfort>(), readinessReportedBefore(emptyList(), emptyList(), LocalDate.of(2026, 10, 14), utc))
    }

    @Test
    fun `la etiqueta del dia es hoy, ayer, el dia de la semana y despues hace n semanas`() {
        val today = LocalDate.of(2026, 10, 14) // miércoles
        assertEquals("hoy", readinessWhenLabel(today, today))
        assertEquals("ayer", readinessWhenLabel(today.minusDays(1), today))
        assertEquals("lunes", readinessWhenLabel(today.minusDays(2), today))
        assertEquals("jueves", readinessWhenLabel(today.minusDays(6), today))
        assertEquals("hace 1 semana", readinessWhenLabel(today.minusDays(7), today))
        assertEquals("hace 1 semana", readinessWhenLabel(today.minusDays(13), today))
        assertEquals("hace 2 semanas", readinessWhenLabel(today.minusDays(14), today))
        assertEquals("hace 3 semanas", readinessWhenLabel(today.minusDays(24), today))
        assertNull("una fecha futura no tiene etiqueta", readinessWhenLabel(today.plusDays(1), today))
    }

    @Test
    fun `el chip dice el nombre de la molestia y su dia`() {
        assertEquals("Rodilla anterior · lunes", readinessReportedChipText(ReadinessReportedDiscomfort("knee_patellar", "lunes")))
        assertEquals("Lumbar", readinessReportedChipText(ReadinessReportedDiscomfort("lumbar", null)))
    }

    // ── los músculos del cuerpo ─────────────────────────────────────────────────────────────────────────────

    private fun status(name: String, score: Int, hoursSince: Int) =
        MuscleRecoveryStatus(name, score, 0, hoursSince, 4, RecoveryStatus.RECOVERING)

    @Test
    fun `los valores del cuerpo suman los de hoy, los tocados y los del motor con sesion`() {
        val values = readinessBodyValues(
            session = mapOf("Cuádriceps" to 64, "Pectorales" to 92),
            others = mapOf("Dorsales" to 55),
            perMuscle = mapOf(
                "Cuádriceps" to status("Cuádriceps", 54, 12), // ya está en los de hoy: manda el de hoy (el editado)
                "Dorsales" to status("Dorsales", 70, 30), // ya está tocado: manda el tocado
                "Glúteos" to status("Glúteos", 61, 20), // solo del motor
                "Deltoides" to status("Deltoides", 100, -1), // sin sesión: sin dato
            ),
        )
        assertEquals(mapOf("Cuádriceps" to 64, "Pectorales" to 92, "Dorsales" to 55, "Glúteos" to 61), values)
    }

    @Test
    fun `un musculo con otro nombre no entra dos veces en su zona`() {
        val values = readinessBodyValues(
            session = mapOf("Pecho" to 80),
            others = emptyMap(),
            perMuscle = mapOf("Pectorales" to status("Pectorales", 92, 10)),
        )
        // «Pecho» y «Pectorales» son la misma zona: se queda el que ya estaba entre los de hoy.
        assertEquals(mapOf("Pecho" to 80), values)
    }

    @Test
    fun `los valores de partida son los de hoy y los del motor, sin lo tocado`() {
        val before = readinessBodyBefore(
            sessionStart = mapOf("Cuádriceps" to 54),
            perMuscle = mapOf(
                "Cuádriceps" to status("Cuádriceps", 40, 12),
                "Dorsales" to status("Dorsales", 70, 30),
                "Deltoides" to status("Deltoides", 100, -1),
            ),
        )
        assertEquals(mapOf("Cuádriceps" to 54, "Dorsales" to 70), before)
    }

    @Test
    fun `los valores se recortan a 0 y 100`() {
        val values = readinessBodyValues(
            session = mapOf("Cuádriceps" to 140),
            others = mapOf("Dorsales" to -5),
            perMuscle = emptyMap(),
        )
        assertEquals(mapOf("Cuádriceps" to 100, "Dorsales" to 0), values)
    }

    // ── los chips de molestias: filas y orden ───────────────────────────────────────────────────────────────

    @Test
    fun `las filas de chips se cuentan como FlowRow, con la separacion entre chips`() {
        assertEquals("sin chips no hay filas", 0, readinessChipRows(emptyList(), maxWidth = 300, spacing = 8))
        // 100 + 8 + 100 = 208: cabe justo en una fila de 208 y no en una de 207.
        assertEquals(1, readinessChipRows(listOf(100, 100), maxWidth = 208, spacing = 8))
        assertEquals(2, readinessChipRows(listOf(100, 100), maxWidth = 207, spacing = 8))
        // Cada fila empieza con el chip que no cupo en la anterior.
        assertEquals(3, readinessChipRows(List(5) { 140 }, maxWidth = 300, spacing = 8))
    }

    @Test
    fun `un chip mas ancho que la fila ocupa una fila y no desborda la cuenta`() {
        assertEquals(3, readinessChipRows(listOf(50, 900, 50), maxWidth = 300, spacing = 8))
        assertEquals(1, readinessChipRows(listOf(900), maxWidth = 300, spacing = 8))
    }

    @Test
    fun `si todos los chips caben en tres filas se ven todos y no hace falta Mas`() {
        // 6 chips de 140: dos por fila, tres filas justas.
        assertEquals(6, readinessVisibleChips(List(6) { 140 }, maxWidth = 300, spacing = 8, maxRows = 3) { 60 })
        assertEquals(0, readinessVisibleChips(emptyList(), maxWidth = 300, spacing = 8, maxRows = 3) { 60 })
    }

    @Test
    fun `lo que no cabe en tres filas se oculta y deja sitio al chip Mas al final de la ultima`() {
        // 8 chips de 140 ocupan 4 filas. Con 5 visibles (2 + 2 + 1) el «Más» de 60 cabe junto al quinto: 140 + 8 + 60 = 208.
        assertEquals(5, readinessVisibleChips(List(8) { 140 }, maxWidth = 300, spacing = 8, maxRows = 3) { 60 })
    }

    @Test
    fun `si el chip Mas no cabe junto al ultimo visible este cede su sitio`() {
        // Un «Más» de 200 ya no cabe tras un chip de 140 (140 + 8 + 200 > 300): se esconde también el quinto y queda 2 + 2.
        assertEquals(4, readinessVisibleChips(List(8) { 140 }, maxWidth = 300, spacing = 8, maxRows = 3) { 200 })
    }

    @Test
    fun `el ancho del chip Mas depende de cuantos quedan ocultos`() {
        val asked = mutableListOf<Int>()
        // Con pocos ocultos el «Más (n)» es ancho (cifra de dos dígitos o texto largo): solo sirve el caso de 3 ocultos.
        val visible = readinessVisibleChips(List(8) { 140 }, maxWidth = 300, spacing = 8, maxRows = 3) { hidden ->
            asked += hidden
            if (hidden == 3) 60 else 400
        }
        assertEquals(5, visible)
        // Probó de 1 en 1 desde los 7 visibles, con los ocultos que le tocan a cada uno.
        assertEquals(listOf(1, 2, 3), asked)
    }

    @Test
    fun `las molestias elegidas se fijan sin Ninguna ni las anotadas antes ni repetidas`() {
        assertEquals(
            listOf("lumbar", "ankle"),
            readinessPinnedChoices(listOf("none", "lumbar", "knee_patellar", "ankle", "lumbar"), reportedIds = setOf("knee_patellar")),
        )
        assertEquals(emptyList<String>(), readinessPinnedChoices(emptyList(), emptySet()))
    }

    @Test
    fun `el orden de los chips es anotadas, fijadas y sugeridas sin repetir ninguna`() {
        assertEquals(
            listOf("r1", "p1", "s1", "s2"),
            readinessChipOrder(reportedIds = listOf("r1"), pinned = listOf("p1", "r1"), suggestedIds = listOf("s1", "p1", "s2")),
        )
    }
}
