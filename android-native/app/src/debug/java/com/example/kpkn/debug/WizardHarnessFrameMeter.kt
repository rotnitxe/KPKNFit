package com.example.kpkn.debug

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.HandlerThread
import android.view.Choreographer
import android.view.FrameMetrics
import android.view.Window
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
import androidx.compose.ui.platform.LocalView
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

/** Cuántas líneas recuerda el historial del medidor (cada paso deja una y, con `sample`, hasta dos más de baches). */
private const val HISTORY_LINES = 14

/** Lo que dura la «entrada» de un paso: desde que el cursor llega hasta que el arnés escribe la respuesta (40 % de 3,2 s ≈ 1,3 s). */
private const val ENTRY_NANOS = 1_100_000_000L

/** El tramo de la entrada en que la página se desliza (`SlideMillis` = 520 ms y un respiro): lo que la persona ve moverse. */
private const val SLIDE_NANOS = 620_000_000L

/**
 * Lo medido por paso durante el recorrido del arnés, en dos tramos: la ENTRADA (el primer 1,1 s desde que el cursor llegó al paso:
 * su deslizado, el paso siguiente que asoma y se compone, el desenfoque que se afloja) y el RESTO (el arnés escribe la respuesta y
 * pulsa «Continuar»: trabajo del ViewModel y de la persona, no de la entrada). De cada tramo guarda el cuadro más largo, el
 * desglose de ese cuadro según `FrameMetrics` y cuántos cuadros pasaron de 33 ms. Se lee de una sola captura al final, sin
 * capturar durante el recorrido (cada captura del sistema altera la medida).
 *
 * El desglose reparte el cuadro por fases: `a` animación y recomposición, `d` medir, colocar y grabar el dibujo (Compose mide
 * dentro del dibujo), `s` sincronizar con el hilo de dibujo, `c` emitir comandos a la GPU, `g` GPU y `u` retraso antes de empezar
 * el cuadro (el hilo principal estaba ocupado con algo que no es un cuadro: trabajo del ViewModel, recolector de basura…), todo
 * en ms. Con eso se ve de un vistazo si un cuadro lento es de composición (`a`), de medida y dibujo (`d`), de la GPU (`c`, `g`) o
 * de otra cosa (`u`). `onFrame` y `endStep` se llaman desde el hilo principal y `onMetrics` desde el hilo de métricas, por eso
 * van sincronizados.
 */
internal object FrameStats {
    /** Lo medido en un tramo de un paso. */
    private class Span {
        var maxGapNanos = 0L
        var slow = 0
        var frames = 0
        var worstTotalNanos = 0L
        var worstDetail = ""

        /** El mensaje más largo del hilo principal de este tramo, ya descrito por [MainStallSampler] (vacío sin `sample`). */
        var worstStallMs = 0L
        var worstStall = ""

        fun text(): String = String.format(
            java.util.Locale.ROOT, "%.0f/%.0f[%s] %d/%d",
            maxGapNanos / 1e6, worstTotalNanos / 1e6, worstDetail, slow, frames,
        )
    }

    private var lastFrame = 0L
    private var windowStartNanos = System.nanoTime()
    private var entry = Span()
    private var rest = Span()

    /** Solo el deslizado (los primeros 0,62 s de la entrada, ya contados también en [entry]): `s`. */
    private var slide = Span()
    private val lines = ArrayDeque<String>()

    /** El historial que pinta el medidor («GOAL e 41/38[a3 l9 …] 2/80 r …»). */
    var history by mutableStateOf("")
        private set

    private fun spanAt(nanos: Long): Span = if (nanos - windowStartNanos < ENTRY_NANOS) entry else rest

    @Synchronized
    fun onFrame(frameTimeNanos: Long) {
        if (lastFrame != 0L) {
            val gap = frameTimeNanos - lastFrame
            val since = frameTimeNanos - windowStartNanos
            // Sin listas ni cierres: el medidor no debe ensuciar la medida con basura a 120 cuadros por segundo.
            if (since >= ENTRY_NANOS) count(rest, gap) else {
                count(entry, gap)
                if (since < SLIDE_NANOS) count(slide, gap)
            }
        }
        lastFrame = frameTimeNanos
    }

    private fun count(span: Span, gap: Long) {
        span.frames++
        if (gap > span.maxGapNanos) span.maxGapNanos = gap
        if (gap > SLOW_NANOS) span.slow++
    }

    /** Un cuadro medido por el sistema (empezó en [intendedVsyncNanos]): si es el más largo de su tramo, guarda su desglose. */
    @Synchronized
    fun onMetrics(intendedVsyncNanos: Long, totalNanos: Long, detail: String) {
        val since = intendedVsyncNanos - windowStartNanos
        if (since >= ENTRY_NANOS) {
            noteWorst(rest, totalNanos, detail)
        } else {
            noteWorst(entry, totalNanos, detail)
            if (since < SLIDE_NANOS) noteWorst(slide, totalNanos, detail)
        }
    }

    private fun noteWorst(span: Span, totalNanos: Long, detail: String) {
        if (totalNanos > span.worstTotalNanos) {
            span.worstTotalNanos = totalNanos
            span.worstDetail = detail
        }
    }

    /** Un mensaje largo del hilo principal que empezó en [startNanos] (ver [MainStallSampler]): de cada tramo se guarda el peor. */
    @Synchronized
    fun onStall(startNanos: Long, durationMs: Long, text: String) {
        val span = spanAt(startNanos)
        if (durationMs > span.worstStallMs) {
            span.worstStallMs = durationMs
            span.worstStall = text
        }
    }

    /** Lo que tardó la llamada del arnés a `submitCurrentStep` (la parte del ViewModel que corre en el hilo principal); -1 si no se midió. */
    private var submitMs = -1L

    @Synchronized
    fun noteSubmit(ms: Long) {
        submitMs = ms
    }

    /** Cierra el paso [name]: guarda su línea (y, con `sample`, el peor bache de cada tramo) y empieza a medir el siguiente. */
    @Synchronized
    fun endStep(name: String) {
        val submit = if (submitMs >= 0) " · sub $submitMs" else ""
        // `s`: lo mismo (cuadro más largo / hueco más largo) pero solo mientras la página se desliza; `e`, la entrada entera (1,1 s).
        val slideText = String.format(java.util.Locale.ROOT, "%.0f/%.0f", slide.maxGapNanos / 1e6, slide.worstTotalNanos / 1e6)
        lines.addLast("$name e ${entry.text()} · s $slideText · r ${rest.text()}$submit")
        if (entry.worstStall.isNotEmpty()) lines.addLast("  e⚠ ${entry.worstStall}")
        if (rest.worstStall.isNotEmpty()) lines.addLast("  r⚠ ${rest.worstStall}")
        while (lines.size > HISTORY_LINES) lines.removeFirst()
        history = lines.joinToString("\n")
        entry = Span()
        rest = Span()
        slide = Span()
        submitMs = -1L
        windowStartNanos = System.nanoTime()
    }
}

/** El desglose de un cuadro en milisegundos enteros (ver [FrameStats]). */
@android.annotation.SuppressLint("InlinedApi")
private fun detailOf(fm: FrameMetrics): String {
    fun ms(metric: Int): Long = fm.getMetric(metric) / 1_000_000L
    val gpu = if (android.os.Build.VERSION.SDK_INT >= 31) " g${ms(FrameMetrics.GPU_DURATION)}" else ""
    return "a${ms(FrameMetrics.ANIMATION_DURATION)} d${ms(FrameMetrics.DRAW_DURATION)} " +
        "s${ms(FrameMetrics.SYNC_DURATION)} c${ms(FrameMetrics.COMMAND_ISSUE_DURATION)}$gpu " +
        "u${ms(FrameMetrics.UNKNOWN_DELAY_DURATION)}"
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
internal fun FrameMeter(label: String = "", sample: Boolean = false, modifier: Modifier = Modifier) {
    var line by remember { mutableStateOf("fps …") }
    // Con `sample`, un muestreador del hilo principal dice QUÉ llena los baches largos (ver [MainStallSampler]).
    DisposableEffect(sample) {
        if (sample) MainStallSampler.start()
        onDispose { if (sample) MainStallSampler.stop() }
    }
    // El desglose por fases de cada cuadro (FrameMetrics) llega en un hilo propio, para no sumar trabajo al principal.
    val view = LocalView.current
    DisposableEffect(view) {
        val window: Window? = view.context.findActivity()?.window
        val thread = HandlerThread("kpkn-frame-metrics").apply { start() }
        val listener = Window.OnFrameMetricsAvailableListener { _, fm, _ ->
            FrameStats.onMetrics(fm.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP), fm.getMetric(FrameMetrics.TOTAL_DURATION), detailOf(fm))
        }
        runCatching { window?.addOnFrameMetricsAvailableListener(listener, Handler(thread.looper)) }
        onDispose {
            runCatching { window?.removeOnFrameMetricsAvailableListener(listener) }
            thread.quitSafely()
        }
    }
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
