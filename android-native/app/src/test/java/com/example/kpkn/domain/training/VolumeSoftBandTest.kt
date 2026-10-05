package com.example.kpkn.domain.training

import com.example.kpkn.data.programs.KpknMuscleGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B-02 (cierre 2026-10-02): la banda blanda de glúteos. El límite sigue siendo el MRV global (16), el
 * techo blando es 16 + 1,5 = 17,5 y no existe tolerancia para ningún otro músculo.
 */
class VolumeSoftBandTest {
    private val glutes = KpknMuscleGroup.GLUTES

    @Test
    fun the_band_is_one_and_a_half_sets_over_the_global_glutes_limit_without_changing_it() {
        assertEquals("el MRV global de glúteos no se toca", 16, VolumeSoftBand.globalLimit(glutes))
        assertEquals(1.5, GLUTES_SOFT_BAND, 0.0)
        assertEquals("techo blando de 17,5 series", 17.5, VolumeSoftBand.softCeiling(glutes, 16), 0.0)
        assertEquals(17.5, VolumeSoftBand.softCeilingFor("Glúteos", 16), 0.0)
    }

    @Test
    fun classifies_normal_high_volume_and_over_ceiling_with_the_boundaries_inclusive() {
        fun band(sets: Double) = VolumeSoftBand.bandOf(glutes, sets, 16)
        assertEquals(VolumeBand.WITHIN_LIMIT, band(0.0))
        assertEquals(VolumeBand.WITHIN_LIMIT, band(16.0))
        assertEquals(VolumeBand.HIGH_VOLUME, band(16.5))
        assertEquals(VolumeBand.HIGH_VOLUME, band(17.0))
        assertEquals("17,5 exactos todavía se admite", VolumeBand.HIGH_VOLUME, band(17.5))
        assertEquals("nunca más de 17,5", VolumeBand.OVER_CEILING, band(17.51))
        assertEquals(VolumeBand.OVER_CEILING, band(18.0))
        assertEquals(VolumeBand.OVER_CEILING, band(19.0))
    }

    @Test
    fun only_glutes_has_a_band_and_a_personal_limit_below_the_global_one_is_never_relaxed() {
        // Ningún otro músculo tolera nada: el techo es su propio límite.
        assertEquals(22.0, VolumeSoftBand.softCeiling(KpknMuscleGroup.CHEST, 22), 0.0)
        assertEquals(VolumeBand.OVER_CEILING, VolumeSoftBand.bandOf(KpknMuscleGroup.CHEST, 22.5, 22))
        assertEquals(22.0, VolumeSoftBand.softCeilingFor("Pectorales", 22), 0.0)
        assertEquals(20.0, VolumeSoftBand.softCeilingFor("Cuádriceps", 20), 0.0)
        // «Glúteo Medio» es una fila del presupuesto sin series propias: tampoco tiene banda.
        assertEquals(16.0, VolumeSoftBand.softCeilingFor("Glúteo Medio", 16), 0.0)
        // Una restricción personal más baja que el límite global no se relaja con la banda.
        assertEquals(12.0, VolumeSoftBand.softCeiling(glutes, 12), 0.0)
        assertEquals(VolumeBand.OVER_CEILING, VolumeSoftBand.bandOf(glutes, 12.5, 12))
    }

    @Test
    fun a_zero_band_reproduces_the_previous_rule_exactly() {
        assertEquals(16.0, VolumeSoftBand.softCeiling(glutes, 16, band = 0.0), 0.0)
        assertEquals(VolumeBand.OVER_CEILING, VolumeSoftBand.bandOf(glutes, 16.5, 16, band = 0.0))
        assertNull(VolumeSoftBand.noticeOrNull("Glúteos", 16.5, 16, band = 0.0))
    }

    @Test
    fun the_notice_is_plain_language_without_internal_jargon() {
        val notice = requireNotNull(VolumeSoftBand.noticeOrNull("Glúteos", 17.0, 16))
        assertEquals("Glúteos", notice.muscle)
        assertEquals(17.0, notice.weeklySets, 0.0)
        assertEquals(16, notice.recommendedSets)
        assertEquals(17.5, notice.ceilingSets, 0.0)
        assertEquals("Glúteos 17 series principales (recomendado 16, tolerancia hasta 17,5)", notice.message)
        assertEquals(
            "Glúteos 17,5 series principales (recomendado 16, tolerancia hasta 17,5)",
            requireNotNull(VolumeSoftBand.noticeOrNull("Glúteos", 17.5, 16)).message,
        )
        assertEquals(
            "Glúteos 16,5 series principales (recomendado 16, tolerancia hasta 17,5)",
            requireNotNull(VolumeSoftBand.noticeOrNull("Glúteos", 16.5, 16)).message,
        )
        listOf("MRV", "BL", "puente H", "H1", "W2", "SOFT", "HARD").forEach { jargon ->
            assertFalse("la nota no debe llevar «$jargon»: ${notice.message}", notice.message.contains(jargon))
        }
    }

    @Test
    fun the_notice_exists_only_inside_the_band_and_tolerates_decimal_noise() {
        assertNull("dentro del límite no hay aviso", VolumeSoftBand.noticeOrNull("Glúteos", 16.0, 16))
        assertNull("ruido decimal del conteo no crea aviso", VolumeSoftBand.noticeOrNull("Glúteos", 16.0004, 16))
        assertNotNull("17,5 con ruido decimal sigue dentro de la banda", VolumeSoftBand.noticeOrNull("Glúteos", 17.5004, 16))
        assertNull("por encima del techo no es un aviso, es un rechazo", VolumeSoftBand.noticeOrNull("Glúteos", 17.6, 16))
        assertNull("otros músculos no tienen banda", VolumeSoftBand.noticeOrNull("Pectorales", 23.0, 22))
        assertNull("sin tolerancia sobre un límite personal más bajo", VolumeSoftBand.noticeOrNull("Glúteos", 12.5, 12))
    }

    @Test
    fun sets_are_formatted_with_a_decimal_comma_and_without_trailing_zeros() {
        assertEquals("16", VolumeSoftBand.formatSets(16.0))
        assertEquals("16,5", VolumeSoftBand.formatSets(16.5))
        assertEquals("17,5", VolumeSoftBand.formatSets(17.5))
        assertEquals("17", VolumeSoftBand.formatSets(16.9999999))
        assertEquals("16,3", VolumeSoftBand.formatSets(16.2999999))
        assertTrue(VolumeSoftBand.formatSets(17.0).none { it == '.' })
    }
}
