package com.example.kpkn.screens.programdetail.design.structure

import androidx.compose.ui.graphics.Color
import com.example.kpkn.data.models.LoopType
import com.example.kpkn.domain.training.ProgramKeyDateEngine
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
 * Las cuentas y los textos con número de las hojas de Ciclos, de Ruta, de Plantillas y de Historial (P3c), y el icono de cada tipo de
 * ciclo. Son funciones puras: se prueban en la JVM, sin Robolectric.
 */
class ProgramCycleRouteTextTest {

    private val needsMore = ProgramKeyDateEngine.KeyDateFitStatus.NEEDS_MORE_WEEKS
    private val hasSurplus = ProgramKeyDateEngine.KeyDateFitStatus.HAS_SURPLUS_WEEKS
    private val fits = ProgramKeyDateEngine.KeyDateFitStatus.FITS

    @Test
    fun cada_tipo_de_ciclo_tiene_su_icono_kpkn() {
        assertSame(KpknLineIcons.Moon, loopTypeIcon(LoopType.DELOAD))
        assertSame(KpknLineIcons.Flag, loopTypeIcon(LoopType.ONE_RM_TEST))
        assertSame(KpknLineIcons.Calendar, loopTypeIcon(LoopType.COMPETITION))
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
    fun el_encaje_de_la_competicion_dice_cuantas_semanas_faltan_o_sobran() {
        assertEquals(
            "Tu competición cae ${numberWithUnit("2 semanas")} después del final del programa.",
            competitionFitLine(needsMore, 2),
        )
        assertEquals(
            "Tu competición cae ${numberWithUnit("1 semana")} después del final del programa.",
            competitionFitLine(needsMore, 1),
        )
        assertEquals(
            "Tu competición cae ${numberWithUnit("3 semanas")} antes del final del programa.",
            competitionFitLine(hasSurplus, 3),
        )
        assertEquals("Tu competición cae en la última semana del programa.", competitionFitLine(fits, 0))
        assertNull(competitionFitLine(null, 0))
    }

    @Test
    fun la_accion_de_encaje_es_corta_y_dice_en_que_bloque_se_aplica() {
        assertEquals("Añadir " + numberWithUnit("2 semanas"), competitionFixLabel(needsMore, 2))
        assertEquals("Quitar " + numberWithUnit("1 semana"), competitionFixLabel(hasSurplus, 1))
        assertEquals("Se suman al último bloque.", competitionFixNote(needsMore, 2))
        assertEquals("Se quitan del último bloque.", competitionFixNote(hasSurplus, 1))
        assertNull(competitionFixLabel(fits, 0))
        assertNull(competitionFixNote(fits, 0))
        assertEquals(2, competitionFixDelta(needsMore, 2))
        assertEquals(-2, competitionFixDelta(hasSurplus, 2))
        assertEquals(0, competitionFixDelta(fits, 0))
    }

    @Test
    fun la_linea_de_inicio_del_bloque_lleva_el_nombre_si_el_titulo_es_la_fase() {
        val block = ProgramRouteBlock(
            id = "block-2",
            name = "Bloque 2",
            firstWeekIndex = 3,
            weeks = 3,
            start = LocalDate.of(2026, 11, 3),
            firstWeekId = "week-4",
            color = Color.Unspecified,
            phaseLabel = "Intensificación",
        )
        // La fecha no se parte: «mar 3 nov» va con espacios sin corte.
        assertEquals("Bloque 2 · empieza el mar 3 nov", blockStartLine(block))
    }

    @Test
    fun sin_fase_la_linea_de_inicio_no_repite_el_nombre() {
        val block = ProgramRouteBlock(
            id = "block-2",
            name = "Bloque 2",
            firstWeekIndex = 3,
            weeks = 3,
            start = LocalDate.of(2026, 11, 3),
            firstWeekId = "week-4",
            color = Color.Unspecified,
        )
        assertEquals("empieza el mar 3 nov", blockStartLine(block))
    }

    @Test
    fun el_resumen_de_la_ruta_cuenta_bloques_semanas_y_fechas() {
        // Las fechas van con espacios sin corte: «25 ene 2027» no se parte en dos líneas.
        assertEquals(
            numberWithUnit("4 bloques") + " · " + numberWithUnit("12 semanas") + " · 6 oct → 25 ene 2027",
            routeSummaryLine(4, 12, LocalDate.of(2026, 10, 6), LocalDate.of(2027, 1, 25)),
        )
    }

    @Test
    fun la_linea_de_bloques_y_semanas_omite_lo_que_no_hay() {
        assertEquals(numberWithUnit("3 bloques") + " · " + numberWithUnit("12 semanas"), programBlocksAndWeeksLine(3, 12))
        assertEquals(numberWithUnit("12 semanas"), programBlocksAndWeeksLine(0, 12))
        assertNull(programBlocksAndWeeksLine(0, 0))
    }

    @Test
    fun la_fecha_de_una_copia_lleva_dia_mes_y_hora() {
        val millis = Instant.parse("2026-10-13T10:42:00Z").toEpochMilli()
        assertEquals("mar 13 oct · 10:42", programCopyStamp(millis, ZoneId.of("UTC")))
    }

    @Test
    fun el_pie_del_historial_cuenta_las_copias_y_sin_ellas_no_dice_nada() {
        assertNull(programHistoryCaption(0))
        assertEquals(
            numberWithUnit("3 copias guardadas") + ". Restaurar sustituye el programa actual.",
            programHistoryCaption(3),
        )
    }

    private fun cycle(id: String, title: String, cadence: Int) = ProgramCycleLoop(
        id = id,
        title = title,
        type = LoopType.CUSTOM,
        cadence = cadence,
        cancelled = false,
    )
}
