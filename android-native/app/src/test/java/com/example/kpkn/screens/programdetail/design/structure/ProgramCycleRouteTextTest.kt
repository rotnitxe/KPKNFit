package com.example.kpkn.screens.programdetail.design.structure

import com.example.kpkn.data.models.LoopType
import com.example.kpkn.domain.training.ProgramSnapshot
import com.example.kpkn.screens.programdetail.ProgramDetailSamples
import com.example.kpkn.screens.programdetail.design.numberWithUnit
import com.example.kpkn.ui.components.icons.KpknLineIcons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Las cuentas y los textos con número de Estructura (lienzo v6): ciclos, semana base, versiones guardadas, biblioteca, TM y la hoja
 * «Fechas», y el icono de cada tipo de ciclo. Son funciones puras: se prueban en la JVM, sin Robolectric.
 */
class ProgramCycleRouteTextTest {

    @Test
    fun cada_tipo_de_ciclo_tiene_su_icono_kpkn() {
        assertSame(KpknLineIcons.Repeat, loopTypeIcon(LoopType.DELOAD))
        assertSame(KpknLineIcons.Target, loopTypeIcon(LoopType.ONE_RM_TEST))
        assertSame(KpknLineIcons.Flag, loopTypeIcon(LoopType.COMPETITION))
        assertSame(KpknLineIcons.Repeat, loopTypeIcon(LoopType.CUSTOM))
    }

    @Test
    fun la_cadencia_va_en_plural_salvo_la_de_uno() {
        assertEquals("cada 4 ciclos", cycleCadenceLabel(4))
        assertEquals("cada ciclo", cycleCadenceLabel(1))
    }

    @Test
    fun el_resumen_cuenta_los_ciclos_activos_y_dice_el_ciclo_en_curso() {
        assertEquals(numberWithUnit("2 ciclos activos") + " · vas por el ciclo 5", cycleSummaryLine(2, 5))
        assertEquals(numberWithUnit("1 ciclo activo"), cycleSummaryLine(1, 0))
        assertEquals("Sin ciclos activos", cycleSummaryLine(0, 0))
    }

    @Test
    fun el_aviso_de_eventos_antiguos_concuerda_con_el_numero() {
        assertEquals("Hay 2 eventos antiguos. Pásalos a ciclos para usar el sistema nuevo.", legacyEventsLine(2))
        assertEquals("Hay 1 evento antiguo. Pásalo a ciclos para usar el sistema nuevo.", legacyEventsLine(1))
    }

    @Test
    fun un_ciclo_con_la_misma_cadencia_choca_salvo_consigo_mismo() {
        val existing = listOf(cycle("a", "Descarga", 4), cycle("b", "Test 1RM", 8))
        assertEquals("a", cycleCadenceConflict(existing, editingId = null, cadence = 4)?.id)
        assertNull(cycleCadenceConflict(existing, editingId = "a", cadence = 4))
        assertNull(cycleCadenceConflict(existing, editingId = null, cadence = 6))
        assertNull(cycleCadenceConflict(existing, editingId = null, cadence = 0))
    }

    @Test
    fun el_texto_del_choque_nombra_el_ciclo_y_la_cadencia() {
        assertEquals(
            "Conflicto: Descarga ya usa cada 4 ciclos. Cambia la cadencia para evitar que dos ciclos compitan por la misma semana.",
            cycleConflictLine(cycle("a", "Descarga", 4), 4),
        )
    }

    @Test
    fun la_fila_de_un_ciclo_dice_su_cadencia_en_semanas() {
        assertEquals("Descarga cada 4 semanas", cycleRowTitle("Descarga", LoopType.DELOAD, cadence = 4, cycleWeeks = 1))
        assertEquals("Test de 1RM cada 8 semanas", cycleRowTitle("Test de 1RM", LoopType.ONE_RM_TEST, cadence = 4, cycleWeeks = 2))
        assertEquals("Descarga cada semana", cycleRowTitle("Descarga", LoopType.DELOAD, cadence = 1, cycleWeeks = 1))
    }

    @Test
    fun la_proxima_vez_concuerda_con_el_tipo_de_ciclo() {
        assertEquals("Próxima: semana 8", cycleRowDetail(LoopType.DELOAD, nextWeek = 8, cancelled = false))
        assertEquals("Próximo: semana 8", cycleRowDetail(LoopType.ONE_RM_TEST, nextWeek = 8, cancelled = false))
        assertEquals("Cancelado", cycleRowDetail(LoopType.DELOAD, nextWeek = 8, cancelled = true))
        assertNull(cycleRowDetail(LoopType.CUSTOM, nextWeek = null, cancelled = false))
    }

    @Test
    fun la_semana_base_cuenta_sesiones_y_duracion_aproximada() {
        assertEquals("4 sesiones · ≈ 3 h 10", baseWeekHeadline(sessions = 4, minutes = 190))
        assertEquals("1 sesión · ≈ 45 min", baseWeekHeadline(sessions = 1, minutes = 45))
        assertEquals("≈ 3 h", approximateDurationLabel(180))
        assertEquals("≈ 1 h 05", approximateDurationLabel(65))
        assertEquals("Sin sesiones", baseWeekHeadline(sessions = 0, minutes = 0))
    }

    @Test
    fun la_fecha_de_una_copia_es_relativa_a_hoy() {
        val zone = ZoneId.of("UTC")
        val today = LocalDate.of(2026, 10, 28)
        assertEquals("Hoy 08:12", programCopyStamp(Instant.parse("2026-10-28T08:12:00Z").toEpochMilli(), today, zone))
        assertEquals("Lun 26 oct", programCopyStamp(Instant.parse("2026-10-26T18:00:00Z").toEpochMilli(), today, zone))
        assertEquals("1 oct", programCopyStamp(Instant.parse("2026-10-01T09:00:00Z").toEpochMilli(), today, zone))
        assertEquals("1 oct 2025", programCopyStamp(Instant.parse("2025-10-01T09:00:00Z").toEpochMilli(), today, zone))
    }

    @Test
    fun la_linea_de_versiones_dice_la_ultima_copia_y_su_motivo() {
        val zone = ZoneId.of("UTC")
        val today = LocalDate.of(2026, 10, 28)
        val snapshot = ProgramSnapshot(
            id = "s",
            programId = "p",
            program = ProgramDetailSamples.active,
            savedAtMs = Instant.parse("2026-10-28T08:12:00Z").toEpochMilli(),
            reason = "Antes de aplicar la propuesta",
        )
        assertEquals("Última: hoy 08:12, antes de aplicar la propuesta", programLatestCopyLine(listOf(snapshot), today, zone))
        assertEquals("Aún no hay versiones guardadas", programLatestCopyLine(emptyList(), today, zone))
    }

    @Test
    fun la_biblioteca_dice_los_dias_de_la_plantilla_y_la_primera_frase_del_metodo() {
        assertEquals("4 sesiones por semana", libraryFrequencyLine(4..4))
        assertEquals("De 3 a 5 sesiones por semana", libraryFrequencyLine(3..5))
        assertEquals("1 sesión por semana", libraryFrequencyLine(1..1))
        assertEquals("Ondas de 3 semanas", libraryMethodLine("Ondas de 3 semanas. Sube el peso al final."))
        assertNull(libraryMethodLine("  "))
    }

    @Test
    fun la_linea_de_tm_nombra_lo_que_falta() {
        assertEquals("4 ejercicios · falta press militar", trainingMaxRowLine(4, listOf("Press militar")))
        assertEquals("3 ejercicios · faltan sentadilla y banca", trainingMaxRowLine(3, listOf("Sentadilla", "Banca")))
        assertEquals("5 ejercicios · faltan 3", trainingMaxRowLine(5, listOf("a", "b", "c")))
        assertEquals("1 ejercicio", trainingMaxRowLine(1, emptyList()))
    }

    @Test
    fun la_hoja_fechas_escribe_la_fecha_larga_y_la_competicion() {
        assertEquals("Martes 6 oct 2026", datesFieldLabel(LocalDate.of(2026, 10, 6)))
        assertEquals("Open regional · 16 nov", competitionRowDetail(LocalDate.of(2026, 11, 16), "Open regional", hasStart = true))
        assertEquals("16 nov", competitionRowDetail(LocalDate.of(2026, 11, 16), "Competición", hasStart = true))
        assertEquals("Elige antes cuándo empieza el programa", competitionRowDetail(null, null, hasStart = false))
        assertEquals("Sin fecha", competitionRowDetail(null, null, hasStart = true))
    }

    private fun cycle(id: String, title: String, cadence: Int) = ProgramCycleLoop(
        id = id,
        title = title,
        type = LoopType.CUSTOM,
        cadence = cadence,
        cancelled = false,
    )
}
