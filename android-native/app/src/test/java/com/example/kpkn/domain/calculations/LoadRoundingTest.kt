package com.example.kpkn.domain.calculations

import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class LoadRoundingTest {
    @Test
    fun `elimina decimales irreales con el incremento por defecto de 2_5 kg`() {
        assertEquals(72.5, LoadRounding.roundToStep(72.75), 0.0)
        assertEquals(62.5, LoadRounding.roundToStep(63.7), 0.0)
        assertEquals(65.0, LoadRounding.roundToStep(63.8), 0.0)
    }

    @Test
    fun `respeta todos los incrementos configurables de discos en kg`() {
        assertEquals(75.0, LoadRounding.roundToStep(72.75, 5.0, LoadRoundDirection.NEAREST), 0.0)
        assertEquals(70.0, LoadRounding.roundToStep(72.75, 5.0, LoadRoundDirection.FLOOR), 0.0)
        assertEquals(72.5, LoadRounding.roundToStep(72.75, 1.25, LoadRoundDirection.FLOOR), 0.0)
        assertEquals(73.0, LoadRounding.roundToStep(72.75, 0.5, LoadRoundDirection.CEIL), 0.0)
        assertEquals(72.75, LoadRounding.roundToStep(72.75, 0.25, LoadRoundDirection.NEAREST), 0.0)
    }

    @Test
    fun `la configuracion por defecto usa 2_5 kg y la jerarquia prioriza ejercicio y maquina`() {
        val settings = Settings(defaultLoadIncrementKg = 1.25, defaultLoadIncrementLb = 5.0)
        assertEquals(2.5, Settings().defaultLoadIncrementKg, 0.0)
        assertEquals(1.25, LoadRounding.defaultStepKg(settings), 0.0)
        assertEquals(5.0, LoadRounding.resolveStepKg(exerciseOverrideKg = 5.0, machineStepKg = 2.5, defaultStepKg = 1.25), 0.0)
        assertEquals(2.5, LoadRounding.resolveStepKg(exerciseOverrideKg = null, machineStepKg = 2.5, defaultStepKg = 1.25), 0.0)
        assertEquals(1.25, LoadRounding.resolveStepKg(exerciseOverrideKg = null, machineStepKg = null, defaultStepKg = 1.25), 0.0)
    }
}
