package com.example.kpkn.screens.onboarding.design.entreno

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos

/*
 * Un control con muchos elementos iguales (las filas de objetivos, las cuadrículas de material y de músculos, las capacidades…)
 * los componía todos en el mismo cuadro en que aparecía. Con el paso siguiente asomando bajo el activo, ese cuadro caía justo
 * cuando la persona pulsaba «Continuar»: 100–200 ms en una compilación de depuración, que va interpretada. Aquí se reparte: los
 * primeros elementos (lo único que el asomo enseña) se componen de golpe y el resto, de [perFrame] en [perFrame] por cuadro, en
 * los siguientes. Cuando ya están todos, no hay nada que repartir.
 */

/**
 * Cuántos de [total] elementos se componen ya: [first] al principio y [perFrame] más en cada cuadro hasta llegar a [total]. Si el
 * total crece (llegan elementos nuevos) sigue repartiendo desde donde estaba; si encoge, devuelve el menor. Nunca devuelve
 * menos de lo ya compuesto: una fila que ya se ve no desaparece para volver a aparecer.
 */
@Composable
internal fun rememberProgressiveCount(total: Int, first: Int, perFrame: Int = 1): Int {
    var shown by remember { mutableIntStateOf(first.coerceAtLeast(0)) }
    if (shown < total) {
        LaunchedEffect(total) {
            while (shown < total) {
                withFrameNanos { }
                shown = (shown + perFrame.coerceAtLeast(1)).coerceAtMost(total)
            }
        }
    }
    return shown.coerceAtMost(total)
}
