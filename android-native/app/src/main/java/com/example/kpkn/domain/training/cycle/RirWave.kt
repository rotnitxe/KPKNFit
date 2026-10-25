package com.example.kpkn.domain.training.cycle

import com.example.kpkn.data.protocols.definitions.NativeDoseLevel

/** Objetivo editorial por semana de entrenamiento; la última semana del ciclo es descarga. */
object RirWave {
    const val DELOAD_RIR = 4
    private val BEGINNER = listOf(3, 3, 3, 2, 2)
    private val TRAINED = listOf(3, 2, 2, 1, 1)

    fun targetRir(level: NativeDoseLevel, weekInCycle: Int, cycleWeeks: Int = 6): Int {
        if (weekInCycle >= cycleWeeks) return DELOAD_RIR
        val wave = if (level == NativeDoseLevel.BEGINNER) BEGINNER else TRAINED
        return wave[(weekInCycle.coerceAtLeast(1) - 1).coerceAtMost(wave.lastIndex)]
    }
}
