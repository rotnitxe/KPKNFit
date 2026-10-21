package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.domain.training.VolumeCalculator
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * AUD2-P04 (reservado a AUD1): un hueco de ronda borrada (`isEmptySlot`) no es una serie. Los recuentos de volumen, el recuento que
 * muestra el editor y la intensidad media de Impacto lo ignoraban solo para las series ineficaces.
 */
class SessionEditorEmptySlotVolumeTest {
    private val effective = ExerciseSet(id = "s1", targetReps = 10, targetRPE = 6.0, weight = 40.0)
    private fun hole(id: String) = ExerciseSet(id = id, isEmptySlot = true)

    @Test
    fun emptySlotsDoNotShiftTheAverageIntensityTier() {
        // Una serie suave y dos huecos: la media no puede subir a «Alta» por series que no existen.
        assertEquals(SessionIntensityTier.MEDIA, averageSessionIntensityTier(listOf(effective, hole("h1"), hole("h2"))))
    }

    @Test
    fun emptySlotsAreNotEffectiveSets() {
        assertEquals(1, VolumeCalculator.countEffectiveSets(listOf(effective, hole("h1"))))
        // Solo huecos: no hay series (antes el respaldo del recuento los contaba).
        assertEquals(0, VolumeCalculator.countEffectiveSets(listOf(hole("h1"), hole("h2"))))
        assertEquals(1.0, countDisplaySets(listOf(effective, hole("h1")), adjustByIntensity = false), 1e-9)
        assertEquals(0.0, countDisplaySets(listOf(hole("h1")), adjustByIntensity = false), 1e-9)
    }
}
