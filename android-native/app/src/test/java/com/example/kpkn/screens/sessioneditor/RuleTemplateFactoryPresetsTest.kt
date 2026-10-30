package com.example.kpkn.screens.sessioneditor

import org.junit.Assert.assertEquals
import org.junit.Test

/** Las cuatro plantillas de reglas de fábrica (pulido `as-reg-valores`). */
class RuleTemplateFactoryPresetsTest {

    private val presets = RuleTemplateStore.factoryPresets().associateBy { it.id }

    @Test
    fun tiempoCortoGuardaElEsfuerzoComoRirDe2_noComoRpe8ConTipoRir() {
        val short = presets.getValue("factory_tiempo_corto").defaults
        assertEquals(DefaultIntensityType.RIR, short.intensityType)
        assertEquals(2.0, short.rpe, 0.0)
    }

    @Test
    fun cuatroPlantillasDeFabricaConSusNombres() {
        assertEquals(
            listOf("Alto volumen", "Fuerza", "Tiempo corto", "Entreno equilibrado"),
            RuleTemplateStore.factoryPresets().map { it.name },
        )
        assertEquals(true, RuleTemplateStore.factoryPresets().all { it.isFactory })
    }

    @Test
    fun entrenoEquilibradoSigueConSusReglasDeCompuestosYAislamientos() {
        val balanced = presets.getValue("factory_entreno_equilibrado").defaults
        assertEquals(DefaultIntensityType.RIR, balanced.isolationIntensityType)
        assertEquals(1.0, balanced.isolationRpe!!, 0.0)
        assertEquals(180, balanced.compoundRestSeconds)
    }
}
