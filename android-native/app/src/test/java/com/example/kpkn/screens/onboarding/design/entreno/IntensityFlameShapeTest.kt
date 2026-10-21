package com.example.kpkn.screens.onboarding.design.entreno

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Forma de la llama de la intensidad (función pura): alturas por parada, puntas, chispas y continuidad entre niveles. */
class IntensityFlameShapeTest {

    @Test
    fun laAlturaDeLaLlamaSigueLasCuatroParadas() {
        assertEquals(0.32f, IntensityFlameShape.heightAt(0f), 1e-4f)
        assertEquals(0.55f, IntensityFlameShape.heightAt(1f / 3f), 1e-3f)
        assertEquals(0.78f, IntensityFlameShape.heightAt(2f / 3f), 1e-3f)
        assertEquals(1f, IntensityFlameShape.heightAt(1f), 1e-4f)
    }

    @Test
    fun laCantidadDePuntasCreceConLaIntensidad() {
        assertEquals(1, IntensityFlameShape.tipCountAt(0f))
        assertEquals(2, IntensityFlameShape.tipCountAt(1f / 3f))
        assertEquals(3, IntensityFlameShape.tipCountAt(2f / 3f))
        assertEquals(5, IntensityFlameShape.tipCountAt(1f))
    }

    @Test
    fun laAlturaCambiaDeFormaContinuaEntreNiveles() {
        var previous = IntensityFlameShape.heightAt(0f)
        var t = 0.01f
        while (t <= 1f + 1e-4f) {
            val current = IntensityFlameShape.heightAt(t)
            assertTrue("la altura no baja al subir la intensidad", current >= previous - 1e-6f)
            assertTrue("la altura no da saltos", current - previous < 0.02f)
            previous = current
            t += 0.01f
        }
    }

    @Test
    fun lasChispasSoloAparecenCercaDeMuyDura() {
        assertEquals(0f, IntensityFlameShape.sparkStrengthAt(0f), 1e-6f)
        assertEquals(0f, IntensityFlameShape.sparkStrengthAt(2f / 3f), 1e-6f)
        assertEquals(1f, IntensityFlameShape.sparkStrengthAt(1f), 1e-6f)
    }

    @Test
    fun lasParadasYLaMasCercanaSeCorresponden() {
        assertEquals(0f, IntensityFlameShape.stopT(0), 1e-6f)
        assertEquals(1f, IntensityFlameShape.stopT(3), 1e-6f)
        assertEquals(1, IntensityFlameShape.nearestStop(0.4f))
        assertEquals(2, IntensityFlameShape.nearestStop(0.6f))
        assertEquals(0, IntensityFlameShape.nearestStop(-1f))
        assertEquals(3, IntensityFlameShape.nearestStop(2f))
    }
}
