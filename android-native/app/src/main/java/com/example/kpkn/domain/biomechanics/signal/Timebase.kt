package com.example.kpkn.domain.biomechanics.signal

/**
 * Base de tiempos uniforme de una serie: muestras en t_k = t0 + k/fs, con k = 0 until [n].
 *
 * @property t0Us primer instante (µs) de la serie ordenada.
 * @property fs frecuencia de muestreo en Hz = 1/mediana(ΔPTS); 0 si hay menos de 2 instantes distintos.
 * @property n número de muestras de la rejilla uniforme (la última cae en o antes del último instante).
 * @property vfr `true` si la cadencia varía: P95/P5 de ΔPTS > 1,3.
 * @property p95OverP5 el cociente P95/P5 de ΔPTS (1 si no hay datos para calcularlo).
 */
data class Timebase(val t0Us: Long, val fs: Double, val n: Int, val vfr: Boolean, val p95OverP5: Double)
