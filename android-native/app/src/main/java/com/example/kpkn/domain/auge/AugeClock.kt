package com.example.kpkn.domain.auge

/**
 * Reloj de evaluación AUGE. Los motores aceptan `nowOverrideMs` para tests;
 * el runtime usa este origen único en lugar de `System.currentTimeMillis()`
 * disperso en Energía/Columna.
 */
object AugeClock {
    fun nowMs(): Long = System.currentTimeMillis()
}
