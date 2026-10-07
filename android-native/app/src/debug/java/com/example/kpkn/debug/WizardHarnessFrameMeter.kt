package com.example.kpkn.debug

import android.view.Choreographer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * SOLO DEPURACIÓN (no se integra): medidor de fluidez para las capturas del arnés (extra `fps=true`). Sin `dumpsys gfxinfo` en el
 * teléfono real, cuenta los cuadros que `Choreographer` entrega y escribe un resumen de los últimos 3 s sobre la pantalla, que
 * sale en las capturas: cuadros por segundo, el cuadro más largo y cuántos tardaron más de 1,5 veces lo normal. Un deslizado
 * fluido se queda sin cuadros lentos; un hilo principal ocupado (o una capa que se repinta entera) los hace subir.
 *
 * Pide un cuadro en cada vsync mientras está puesto, así que mantiene la pantalla a su frecuencia máxima: mide el peor caso.
 */

/** Ventana de medida en nanosegundos (3 s) y tamaño del anillo (de sobra para 120 Hz). */
private const val WINDOW_NANOS = 3_000_000_000L
private const val RING = 512

/** Cada cuánto se reescribe el resumen (400 ms). */
private const val REFRESH_NANOS = 400_000_000L

/** Cuadros que tardan más que esto cuentan como «lentos» en el resumen por paso (dos vsync a 60 Hz). */
private const val SLOW_NANOS = 33_000_000L

/** Cuántos pasos recuerda el historial del medidor. */
private const val HISTORY_STEPS = 9

/**
 * Lo medido por paso durante el recorrido del arnés: el cuadro más largo y cuántos pasaron de 33 ms desde que el cursor llegó al
 * paso (su animación de llegada incluida) hasta que salió. Se lee de una sola captura al final, sin capturar durante el recorrido
 * (cada captura del sistema altera la medida). Solo se toca desde el hilo principal.
 */
internal object FrameStats {
    private var lastFrame = 0L
    private var maxNanos = 0L
    private var slow = 0
    private var frames = 0
    private val lines = ArrayDeque<String>()

    /** El historial que pinta el medidor («GOAL 41 ms · 2 lentos de 880»). */
    var history by mutableStateOf("")
        private set

    fun onFrame(frameTimeNanos: Long) {
        if (lastFrame != 0L) {
            val gap = frameTimeNanos - lastFrame
            frames++
            if (gap > maxNanos) maxNanos = gap
            if (gap > SLOW_NANOS) slow++
        }
        lastFrame = frameTimeNanos
    }

    /** Cierra el paso [name]: guarda su línea y empieza a medir el siguiente. */
    fun endStep(name: String) {
        lines.addLast(String.format(java.util.Locale.ROOT, "%s %.0f ms · %d lentos de %d", name, maxNanos / 1e6, slow, frames))
        while (lines.size > HISTORY_STEPS) lines.removeFirst()
        history = lines.joinToString("\n")
        maxNanos = 0L
        slow = 0
        frames = 0
    }
}

@Composable
internal fun FrameMeter(label: String = "", modifier: Modifier = Modifier) {
    var line by remember { mutableStateOf("fps …") }
    DisposableEffect(Unit) {
        val choreographer = Choreographer.getInstance()
        val stamps = LongArray(RING)
        var head = 0
        var count = 0
        var lastShown = 0L
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                FrameStats.onFrame(frameTimeNanos)
                stamps[head] = frameTimeNanos
                head = (head + 1) % RING
                if (count < RING) count++
                if (frameTimeNanos - lastShown >= REFRESH_NANOS) {
                    lastShown = frameTimeNanos
                    line = summarize(stamps, count, head)
                }
                choreographer.postFrameCallback(this)
            }
        }
        choreographer.postFrameCallback(callback)
        onDispose { choreographer.removeFrameCallback(callback) }
    }
    Text(
        text = listOf(label, line, FrameStats.history).filter { it.isNotEmpty() }.joinToString("\n"),
        color = Color(0xFF9DFF9D),
        fontSize = 12.sp,
        modifier = modifier
            .statusBarsPadding()
            .padding(start = 4.dp, top = 2.dp)
            .background(Color(0xB3000000))
            .padding(horizontal = 4.dp),
    )
}

/** «118 fps · máx 33 ms · lentos 2/354»: lo medido en los últimos 3 s del anillo de marcas de tiempo. */
private fun summarize(stamps: LongArray, count: Int, head: Int): String {
    if (count < 3) return "fps …"
    val newest = stamps[(head - 1 + RING) % RING]
    // Los cuadros de la ventana, del más antiguo al más nuevo.
    val inWindow = ArrayList<Long>(count)
    for (i in 0 until count) {
        val stamp = stamps[(head - count + i + RING * 2) % RING]
        if (newest - stamp <= WINDOW_NANOS) inWindow += stamp
    }
    if (inWindow.size < 3) return "fps …"
    val gaps = LongArray(inWindow.size - 1) { inWindow[it + 1] - inWindow[it] }
    val sorted = gaps.sortedArray()
    val median = sorted[sorted.size / 2].coerceAtLeast(1L)
    val slow = gaps.count { it > median * 3 / 2 }
    val spanSeconds = (inWindow.last() - inWindow.first()) / 1e9
    val fps = if (spanSeconds > 0) gaps.size / spanSeconds else 0.0
    val maxMs = sorted.last() / 1e6
    return String.format(java.util.Locale.ROOT, "%.0f fps · máx %.0f ms · lentos %d/%d", fps, maxMs, slow, gaps.size)
}
