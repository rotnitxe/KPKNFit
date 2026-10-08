package com.example.kpkn.debug

import android.os.Looper
import android.util.Printer

/*
 * SOLO DEPURACIÓN (no se integra): muestreador del hilo principal para el medidor del arnés (extra `sample=true`, junto a `fps`).
 *
 * El medidor de cuadros dice CUÁNTO tarda un bache (`u` es el retraso antes de empezar el cuadro: el hilo principal estaba con otra
 * cosa), no QUÉ lo causa, y sin `adb` ni logcat en el teléfono real no hay otra forma de saberlo. Este muestreador escucha cada
 * mensaje del hilo principal (`Looper.setMessageLogging`) y, mientras uno lleva más de [WARMUP_MS], un hilo aparte le pide la pila
 * cada [SAMPLE_EVERY_MS]. Al terminar el mensaje, si duró [MIN_MESSAGE_MS] o más, guarda su duración, de qué clase era (un cuadro
 * de `Choreographer` —«F»— o cualquier otro mensaje —«M»—: una continuación de corrutina, un `post`) y dónde estuvo el hilo la mayor
 * parte del tiempo, de dos maneras: por la categoría de la función más interna de la pila (el paquete: texto, dibujo, composición,
 * serialización…) y por la función de la app más interna (y, si cuelga de una del ViewModel, cuál).
 *
 * Cada muestra frena un instante el hilo principal: por eso es opcional y NO se enciende para las medidas que se comparan con las
 * de antes (esas llevan solo `fps`). Sirve para decidir de quién es un bache, no para medirlo.
 */
internal object MainStallSampler {
    /** Un mensaje que dura esto o más cuenta como bache y se guarda. */
    private const val MIN_MESSAGE_MS = 40L

    /** Cada cuánto se pide la pila del hilo principal mientras dura un mensaje largo. */
    private const val SAMPLE_EVERY_MS = 7L

    /** No se muestrea hasta que el mensaje lleve esto: los cortos no pagan nada. */
    private const val WARMUP_MS = 20L

    /** Cuántos lugares se cuentan de cada bache, por categoría y por función de la app. */
    private const val TOP_CATEGORIES = 2
    private const val TOP_APP = 3

    private val lock = Any()
    private val categories = HashMap<String, Int>()
    private val appFrames = HashMap<String, Int>()
    private var samples = 0

    @Volatile private var inMessage = false
    @Volatile private var messageStartNanos = 0L
    private var messageLabel = ""

    @Volatile private var running = false
    private var thread: Thread? = null

    private val printer = Printer { line ->
        if (line.startsWith(">>>>>")) {
            synchronized(lock) { categories.clear(); appFrames.clear(); samples = 0 }
            messageLabel = labelOf(line)
            messageStartNanos = System.nanoTime()
            inMessage = true
        } else if (line.startsWith("<<<<<")) {
            inMessage = false
            val durationMs = (System.nanoTime() - messageStartNanos) / 1_000_000L
            if (durationMs >= MIN_MESSAGE_MS) report(durationMs)
        }
    }

    fun start() {
        if (running) return
        running = true
        val main = Looper.getMainLooper().thread
        Looper.getMainLooper().setMessageLogging(printer)
        thread = Thread({
            while (running) {
                try {
                    Thread.sleep(SAMPLE_EVERY_MS)
                } catch (_: InterruptedException) {
                    return@Thread
                }
                if (inMessage && (System.nanoTime() - messageStartNanos) / 1_000_000L >= WARMUP_MS) {
                    val stack = main.stackTrace
                    val category = categoryOf(stack)
                    val app = appFrameOf(stack)
                    synchronized(lock) {
                        categories.merge(category, 1, Int::plus)
                        appFrames.merge(app, 1, Int::plus)
                        samples++
                    }
                }
            }
        }, "kpkn-stall-sampler").apply { isDaemon = true; start() }
    }

    fun stop() {
        running = false
        thread?.interrupt()
        thread = null
        Looper.getMainLooper().setMessageLogging(null)
        inMessage = false
    }

    private fun report(durationMs: Long) {
        val (total, byCategory, byApp) = synchronized(lock) {
            Triple(samples, top(categories, TOP_CATEGORIES), top(appFrames, TOP_APP))
        }
        val text = if (total == 0) "sin muestras" else "${share(byCategory, total)} | ${share(byApp, total)}"
        FrameStats.onStall(messageStartNanos, durationMs, "$durationMs ms $messageLabel: $text")
    }

    private fun top(counts: Map<String, Int>, n: Int): List<Pair<String, Int>> =
        counts.entries.sortedByDescending { it.value }.take(n).map { it.key to it.value }

    private fun share(list: List<Pair<String, Int>>, total: Int): String =
        list.joinToString(" · ") { (key, n) -> "${n * 100 / total}% $key" }

    /** «F» si el mensaje es un cuadro de `Choreographer`; si no, «M» con el manejador y lo que ejecuta. */
    private fun labelOf(line: String): String {
        if (line.contains("Choreographer")) return "F"
        // «>>>>> Dispatching to Handler (android.os.Handler) {3f2a1c} kotlinx.coroutines.DispatchedContinuation@9d8e: 0»
        val handler = line.substringAfter("Handler (", "").substringBefore(')').substringAfterLast('.')
        val callback = line.substringAfter("} ", "").substringBefore(':').substringBefore('@').substringAfterLast('.')
        return "M[${handler.ifEmpty { "?" }}/${callback.ifEmpty { "-" }}]"
    }

    /** La categoría del trabajo: «app» si la función más interna es de la app; si no, el paquete (cuatro tramos) de esa función. */
    private fun categoryOf(stack: Array<StackTraceElement>): String {
        val name = stack.firstOrNull()?.className ?: return "?"
        if (name.startsWith("com.example.kpkn")) return "app"
        return name.split('.').take(4).joinToString(".").ifEmpty { name }
    }

    /**
     * Dónde está el hilo en la app: la función de la app más interna de la pila y, si cuelga de una del ViewModel, esa. Sin ninguna
     * de la app (el sistema, el recolector de basura, el dibujo), «–».
     */
    private fun appFrameOf(stack: Array<StackTraceElement>): String {
        var inner: String? = null
        var viewModel: String? = null
        for (frame in stack) {
            if (!frame.className.startsWith("com.example.kpkn")) continue
            val name = shortName(frame)
            if (inner == null) inner = name
            if (viewModel == null && frame.className.contains("ViewModel")) viewModel = name
        }
        return when {
            inner == null -> "–"
            viewModel == null || viewModel == inner -> inner
            else -> "$inner ⟵ $viewModel"
        }
    }

    private fun shortName(frame: StackTraceElement): String =
        "${frame.className.substringAfterLast('.').take(40)}.${frame.methodName.take(30)}"
}
