package com.example.kpkn.domain.biomechanics.signal

/** Derivadas numéricas de una serie uniforme (§4, punto 10). Se aplican sobre la señal ya filtrada. */
object Derivative {
    /**
     * Derivada por diferencias centrales: v[k] = (x[k+1] − x[k−1]) · fs/2; en los extremos, diferencia hacia un
     * solo lado: v[0] = (x[1] − x[0]) · fs y v[N−1] = (x[N−1] − x[N−2]) · fs. Unidades: las de [x] por segundo.
     *
     * El NaN se propaga por la propia aritmética: v[k] es NaN si lo es alguno de los dos vecinos que usa (un NaN
     * aislado en x[k] no impide calcular v[k] a partir de sus vecinos). Con una sola muestra no hay derivada: NaN.
     */
    fun central(x: DoubleArray, fs: Double): DoubleArray {
        val n = x.size
        val v = DoubleArray(n)
        if (n == 0) return v
        if (n == 1) {
            v[0] = Double.NaN
            return v
        }
        v[0] = (x[1] - x[0]) * fs
        val half = fs / 2.0
        for (k in 1 until n - 1) v[k] = (x[k + 1] - x[k - 1]) * half
        v[n - 1] = (x[n - 1] - x[n - 2]) * fs
        return v
    }
}
