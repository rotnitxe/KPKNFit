package com.example.kpkn.debug

import android.graphics.Color
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.example.kpkn.data.media.video.DecodedFrame
import com.example.kpkn.data.media.video.FrameNeeds
import com.example.kpkn.data.media.video.RetrieverVideoFrameSource
import com.example.kpkn.data.media.video.VideoDecodePath
import com.example.kpkn.data.media.video.VideoDecodeReport
import com.example.kpkn.data.media.video.VideoFrameMath
import com.example.kpkn.data.media.video.VideoFrameSource
import com.example.kpkn.data.media.video.VideoFrameSources
import com.example.kpkn.data.media.video.VideoInfo
import com.example.kpkn.data.media.video.VideoInfoProbe
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

/**
 * SOLO DEPURACIÓN (no se integra): banco de la técnica en vídeo. El modo se pasa con `--es mode <modo>`; hoy solo existe
 * `decode_selftest` (el de por defecto), y VT4b le añadirá más.
 *
 * `decode_selftest` genera con [SyntheticClipEncoder] cuatro clips de 320×240 en `getExternalFilesDir("vt-selftest")` (30 fps,
 * 60 fps, 30 fps con `setOrientationHint(90)` y VFR con saltos de 25 y 41 ms), con un disco blanco que recorre una trayectoria
 * conocida. Cada clip se decodifica con `VideoFrameSources.open` (GL) y con el respaldo forzado (retriever), a
 * `targetLongEdge = 320` y RGBA_AND_LUMA, y por cada camino se miden: los fotogramas esperados y los decodificados, los PTS
 * frente a los del muxer y a los del contenedor, el tamaño de salida, el error del centroide de la luma > 128 (con el giro
 * aplicado a la verdad) y los ms por fotograma. También se prueba que cancelar a mitad libera todo y que se puede reabrir.
 *
 * Escribe `vt-selftest/result.json` (con archivo temporal y renombrado: nadie lo ve a medias; el viejo se borra al arrancar),
 * va anotando `vt-selftest/progress.txt`, muestra «Listo» y deja una línea en logcat con la etiqueta KpknTechniqueBench.
 * Los errores van al JSON y nunca tumban la actividad.
 */
class TechniqueBenchActivity : ComponentActivity() {
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        status = TextView(this).apply {
            setBackgroundColor(Color.BLACK)
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(48, 160, 48, 48)
            text = "Banco de técnica en vídeo…"
        }
        setContentView(status)
        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_DECODE_SELFTEST
        if (mode == MODE_DECODE_SELFTEST) {
            // El result.json de una corrida anterior fuera YA, antes de que termine el arranque: quien espere el archivo no debe ver el viejo.
            runCatching { File(getExternalFilesDir(SELFTEST_DIR), RESULT_FILE).delete() }
        }
        lifecycleScope.launch(Dispatchers.Default) {
            val text = try {
                when (mode) {
                    MODE_DECODE_SELFTEST -> {
                        val dir = getExternalFilesDir(SELFTEST_DIR) ?: error("Sin almacenamiento externo de la app")
                        "Listo: ${DecodeSelfTest(dir).run()}"
                    }
                    else -> "Modo desconocido: $mode"
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                "Error: ${describe(error)}"
            }
            withContext(Dispatchers.Main) { status.text = text }
        }
    }
}

private const val TAG = "KpknTechniqueBench"
private const val EXTRA_MODE = "mode"
private const val MODE_DECODE_SELFTEST = "decode_selftest"
private const val SELFTEST_DIR = "vt-selftest"
private const val RESULT_FILE = "result.json"
private const val PROGRESS_FILE = "progress.txt"
private const val TARGET_LONG_EDGE = 320
private const val GL_CENTROID_TOLERANCE_PX = 1.5
private const val RETRIEVER_CENTROID_TOLERANCE_PX = 3.0
private const val CANCEL_AFTER_FRAMES = 12
private const val NO_CENTROID_PX = 9999.0

private val SPECS = listOf(
    SyntheticClipEncoder.Spec("a_30fps", fps = 30, seconds = 3, orientationHint = 0, variableRate = false),
    SyntheticClipEncoder.Spec("b_60fps", fps = 60, seconds = 3, orientationHint = 0, variableRate = false),
    SyntheticClipEncoder.Spec("c_giro90", fps = 30, seconds = 3, orientationHint = 90, variableRate = false),
    SyntheticClipEncoder.Spec("d_vfr", fps = 30, seconds = 3, orientationHint = 0, variableRate = true),
)

private fun describe(error: Throwable): String {
    val cause = error.cause
    return "${error.javaClass.simpleName}: ${error.message}" + if (cause != null) " <- ${cause.javaClass.simpleName}: ${cause.message}" else ""
}

/** JSON no admite NaN ni infinitos. */
private fun finite(value: Double): Any = if (value.isFinite()) value else JSONObject.NULL

/** Centroide (píxeles continuos: el píxel (x, y) ocupa [x, x+1)) de los píxeles con luma > 128; `false` si no hay ninguno. */
private fun centroid(luma: ByteArray, width: Int, height: Int, out: DoubleArray): Boolean {
    var sumX = 0L
    var sumY = 0L
    var count = 0
    var i = 0
    for (y in 0 until height) {
        for (x in 0 until width) {
            if ((luma[i++].toInt() and 0xFF) > 128) {
                sumX += x
                sumY += y
                count++
            }
        }
    }
    if (count == 0) return false
    out[0] = sumX.toDouble() / count + 0.5
    out[1] = sumY.toDouble() / count + 0.5
    return true
}

/** Índice del PTS de [sorted] más cercano a [ptsUs]. */
private fun nearestIndex(sorted: LongArray, ptsUs: Long): Int {
    var best = 0
    var bestGap = Long.MAX_VALUE
    for (i in sorted.indices) {
        val gap = abs(sorted[i] - ptsUs)
        if (gap < bestGap) {
            bestGap = gap
            best = i
        }
    }
    return best
}

/** PTS de las muestras de vídeo del contenedor, ordenados (lo que el archivo dice de verdad). */
private fun readContainerPts(file: File): LongArray {
    val extractor = MediaExtractor()
    try {
        extractor.setDataSource(file.absolutePath)
        var track = -1
        for (i in 0 until extractor.trackCount) {
            if (extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME).orEmpty().startsWith("video/")) {
                track = i
                break
            }
        }
        require(track >= 0) { "Sin pista de vídeo" }
        extractor.selectTrack(track)
        val times = ArrayList<Long>()
        while (true) {
            val t = extractor.sampleTime
            if (t < 0L) break
            times.add(t)
            if (!extractor.advance()) break
        }
        val sorted = times.toLongArray()
        sorted.sort()
        return sorted
    } finally {
        extractor.release()
    }
}

private fun infoJson(info: VideoInfo): JSONObject = JSONObject()
    .put("codificado", "${info.codedWidth}x${info.codedHeight}")
    .put("giro", info.rotationDegrees)
    .put("pantalla", "${info.displayWidth}x${info.displayHeight}")
    .put("duracionUs", info.durationUs)
    .put("mime", info.mime)
    .put("fpsNominal", info.nominalFps?.let { finite(it.toDouble()) } ?: JSONObject.NULL)
    .put("fpsCaptura", info.captureFps?.let { finite(it.toDouble()) } ?: JSONObject.NULL)
    .put("hdr", info.isHdr)

/** Lo que se anota de cada fotograma que llega a `onFrame`: PTS, tamaño y centroide, más la coherencia entre RGBA y luma. */
private class FrameLog(expected: Int) {
    val pts = ArrayList<Long>(expected + 8)
    val cx = ArrayList<Double>(expected + 8)
    val cy = ArrayList<Double>(expected + 8)
    var width = 0
    var height = 0
    var lumaVsRgbaMaxDiff = 0
    private val point = DoubleArray(2)

    fun add(frame: DecodedFrame) {
        width = frame.width
        height = frame.height
        pts.add(frame.ptsUs)
        val luma = checkNotNull(frame.luma) { "El fotograma ${frame.index} no trae luma" }
        val rgba = checkNotNull(frame.rgba) { "El fotograma ${frame.index} no trae RGBA" }
        val pixels = frame.width * frame.height
        check(luma.size == pixels) { "Luma de ${luma.size} bytes para $pixels píxeles" }
        check(rgba.isDirect && rgba.capacity() == pixels * 4) { "RGBA no directo o de tamaño ${rgba.capacity()}" }
        if (centroid(luma, frame.width, frame.height, point)) {
            cx.add(point[0])
            cy.add(point[1])
        } else {
            cx.add(Double.NaN)
            cy.add(Double.NaN)
        }
        if (frame.index % 7 == 0) {
            for (p in 0 until pixels) {
                val r = rgba.get(p * 4).toInt() and 0xFF
                val g = rgba.get(p * 4 + 1).toInt() and 0xFF
                val b = rgba.get(p * 4 + 2).toInt() and 0xFF
                val diff = abs(VideoFrameMath.luma(r, g, b) - (luma[p].toInt() and 0xFF))
                if (diff > lumaVsRgbaMaxDiff) lumaVsRgbaMaxDiff = diff
            }
        }
    }
}

private class DecodeSelfTest(private val dir: File) {
    private val progressFile = File(dir, PROGRESS_FILE)
    private val expectedFrames = HashMap<String, Int>()

    private fun note(message: String) {
        Log.i(TAG, message)
        runCatching { progressFile.appendText("${System.currentTimeMillis() % 100_000_000L} $message\n") }
    }

    /** Corre todo, escribe `result.json` y devuelve el texto corto que se muestra en pantalla. */
    suspend fun run(): String {
        dir.mkdirs()
        runCatching { progressFile.delete() }
        val report = JSONObject()
            .put("modo", MODE_DECODE_SELFTEST)
            .put("dispositivo", "${Build.MANUFACTURER} ${Build.MODEL}")
            .put("api", Build.VERSION.SDK_INT)
            .put("abi", Build.SUPPORTED_ABIS.firstOrNull().orEmpty())
        var allOk = true
        val summary = StringBuilder()
        try {
            val clips = JSONArray()
            for (spec in SPECS) {
                val outcome = try {
                    runClip(spec)
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (error: Throwable) {
                    JSONObject().put("nombre", spec.name).put("error", describe(error)).put("ok", false)
                }
                clips.put(outcome)
                allOk = allOk && outcome.optBoolean("ok")
                summary.append(summaryOf(outcome)).append(' ')
            }
            report.put("clips", clips)
            val cancellation = try {
                runCancelCheck()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                JSONObject().put("error", describe(error)).put("ok", false)
            }
            report.put("cancelacion", cancellation)
            allOk = allOk && cancellation.optBoolean("ok")
            summary.append("cancel=").append(cancellation.optBoolean("ok")).append(' ')
            val needsCheck = try {
                JSONObject().put("gl", runNeedsCheck(forceRetriever = false)).put("retriever", runNeedsCheck(forceRetriever = true))
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                JSONObject().put("error", describe(error))
            }
            val needsOk = needsCheck.optJSONObject("gl")?.optBoolean("ok") == true && needsCheck.optJSONObject("retriever")?.optBoolean("ok") == true
            needsCheck.put("ok", needsOk)
            report.put("necesidades", needsCheck)
            allOk = allOk && needsOk
            summary.append("needs=").append(needsOk).append(' ')
            val resume = try {
                runSeekCheck()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                JSONObject().put("error", describe(error)).put("ok", false)
            }
            report.put("desdeUnInstante", resume)
            allOk = allOk && resume.optBoolean("ok")
            summary.append("seek=").append(resume.optBoolean("ok"))
        } catch (cancel: CancellationException) {
            report.put("error", "cancelado: la actividad se cerró antes de terminar")
            allOk = false
            throw cancel
        } catch (error: Throwable) {
            report.put("error", describe(error))
            allOk = false
        } finally {
            report.put("ok", allOk)
            val tmp = File(dir, "$RESULT_FILE.tmp")
            tmp.writeText(report.toString(2))
            check(tmp.renameTo(File(dir, RESULT_FILE))) { "No se pudo renombrar $RESULT_FILE" }
            Log.i(TAG, "decode_selftest ok=$allOk $summary")
        }
        return "decode_selftest ${if (allOk) "OK" else "CON FALLOS"} · $summary"
    }

    private fun summaryOf(clip: JSONObject): String {
        val name = clip.optString("nombre")
        val gl = clip.optJSONObject("gl")
        val retriever = clip.optJSONObject("retriever")
        fun part(label: String, json: JSONObject?): String =
            if (json == null) "$label=-" else "$label=${json.optInt("fotogramasDecodificados")}f/${json.optString("centroideErrorMaxPx")}px/${json.optString("msPorFotograma")}ms"
        return "$name[${part("gl", gl)} ${part("rt", retriever)} ok=${clip.optBoolean("ok")}]"
    }

    private suspend fun runClip(spec: SyntheticClipEncoder.Spec): JSONObject {
        val json = JSONObject()
            .put("nombre", spec.name)
            .put("fps", spec.fps)
            .put("segundos", spec.seconds)
            .put("giroContenedor", spec.orientationHint)
            .put("vfr", spec.variableRate)
        val file = File(dir, "${spec.name}.mp4")
        note("${spec.name}: generando")
        val clip = try {
            withContext(Dispatchers.IO) { SyntheticClipEncoder.encodeWithTimeout(file, spec) }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Throwable) {
            note("${spec.name}: no se pudo generar: ${describe(error)}")
            return json.put("error", "generar: ${describe(error)}").put("ok", false)
        }
        expectedFrames[spec.name] = clip.ptsUs.size
        json.put("fotogramasEsperados", clip.ptsUs.size).put("bytes", file.length())
        val container = try {
            readContainerPts(file)
        } catch (error: Exception) {
            note("${spec.name}: no se pudo leer el contenedor: ${describe(error)}")
            null
        }
        if (container != null) {
            json.put("fotogramasContenedor", container.size)
            var worst = 0L
            if (container.size == clip.ptsUs.size) {
                for (i in container.indices) worst = maxOf(worst, abs(container[i] - clip.ptsUs[i]))
                json.put("contenedorVsMuxerMaxUs", worst)
            }
        }
        try {
            json.put("info", infoJson(VideoInfoProbe.probe(file)))
        } catch (error: Exception) {
            json.put("info", describe(error))
        }
        val gl = measure(file, clip, container, forceRetriever = false)
        val retriever = measure(file, clip, container, forceRetriever = true)
        json.put("gl", gl)
        json.put("retriever", retriever)
        // El respaldo por tiempo (getScaledFrameAtTime a <= 15 fps, el de las APIs viejas) en el clip recto y en el girado.
        var timedOk = true
        if (spec.name == SPECS[0].name || spec.name == SPECS[2].name) {
            val timed = measure(file, clip, container, forceRetriever = true, indexed = false)
            json.put("retrieverPorTiempo", timed)
            timedOk = timed.optBoolean("ok")
        }
        json.put("ok", gl.optBoolean("ok") && retriever.optBoolean("ok") && timedOk)
        note("${spec.name}: gl ok=${gl.optBoolean("ok")} retriever ok=${retriever.optBoolean("ok")} porTiempo ok=$timedOk")
        return json
    }

    /** Decodifica [file] por GL o, con [forceRetriever], por el respaldo, y mide todo contra la verdad del generador. */
    private suspend fun measure(
        file: File,
        clip: SyntheticClipEncoder.Clip,
        container: LongArray?,
        forceRetriever: Boolean,
        indexed: Boolean = true,
    ): JSONObject {
        val label = if (!forceRetriever) "gl" else if (indexed) "retriever" else "retriever por tiempo"
        val spec = clip.spec
        val display = VideoFrameMath.displaySize(SyntheticClipEncoder.WIDTH, SyntheticClipEncoder.HEIGHT, spec.orientationHint)
        val expectedSize = VideoFrameMath.outputSize(display.width, display.height, TARGET_LONG_EDGE)
        val json = JSONObject().put("tamanoEsperado", "${expectedSize.width}x${expectedSize.height}")
        try {
            note("${spec.name}: decodificando ($label)")
            val info = VideoInfoProbe.probe(file)
            val source: VideoFrameSource =
                if (forceRetriever) RetrieverVideoFrameSource(file, info, indexed = indexed) else VideoFrameSources.open(file)
            source.use { src ->
                val log = FrameLog(clip.ptsUs.size)
                var callbackNs = 0L
                var firstFrameNs = -1L
                val startNs = System.nanoTime()
                src.decode(fromUs = 0L, targetLongEdge = TARGET_LONG_EDGE, needs = { _, _ -> FrameNeeds.RGBA_AND_LUMA }) { frame ->
                    val enteredNs = System.nanoTime()
                    if (firstFrameNs < 0L) firstFrameNs = enteredNs - startNs
                    log.add(frame)
                    callbackNs += System.nanoTime() - enteredNs
                }
                val wallNs = System.nanoTime() - startNs
                val report = src as? VideoDecodeReport
                val path = report?.path ?: if (forceRetriever) VideoDecodePath.RETRIEVER else VideoDecodePath.GL
                json.put("camino", path.name)
                json.put("motivoRespaldo", report?.fallbackReason ?: JSONObject.NULL)
                json.put("detalles", report?.details.orEmpty())
                fill(json, log, clip, container, wallNs, callbackNs, firstFrameNs, expectedSize, path, forceRetriever)
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Throwable) {
            note("${spec.name}: fallo ($label): ${describe(error)}")
            json.put("error", describe(error))
            json.put("ok", false)
        }
        return json
    }

    private fun fill(
        json: JSONObject,
        log: FrameLog,
        clip: SyntheticClipEncoder.Clip,
        container: LongArray?,
        wallNs: Long,
        callbackNs: Long,
        firstFrameNs: Long,
        expectedSize: VideoFrameMath.Size,
        path: VideoDecodePath,
        forceRetriever: Boolean,
    ) {
        val spec = clip.spec
        val decoded = log.pts.size
        val muxed = clip.ptsUs
        val sameCountAsMuxer = decoded == muxed.size
        json.put("fotogramasDecodificados", decoded)
        json.put("tamanoSalida", "${log.width}x${log.height}")

        // Tiempos: frente al muxer (lo que se pidió) y frente al contenedor (lo que quedó en el archivo).
        var mismatchVsMuxer = -1
        var maxVsMuxerUs = -1L
        if (sameCountAsMuxer) {
            mismatchVsMuxer = 0
            maxVsMuxerUs = 0L
            for (k in 0 until decoded) {
                val gap = abs(log.pts[k] - muxed[k])
                if (gap != 0L) mismatchVsMuxer++
                if (gap > maxVsMuxerUs) maxVsMuxerUs = gap
            }
        }
        var mismatchVsContainer = -1
        var maxVsContainerUs = -1L
        if (container != null && container.size == decoded) {
            mismatchVsContainer = 0
            maxVsContainerUs = 0L
            for (k in 0 until decoded) {
                val gap = abs(log.pts[k] - container[k])
                if (gap != 0L) mismatchVsContainer++
                if (gap > maxVsContainerUs) maxVsContainerUs = gap
            }
        }
        json.put("ptsDistintosDelMuxer", if (mismatchVsMuxer >= 0) mismatchVsMuxer else JSONObject.NULL)
        json.put("ptsErrorMaxVsMuxerUs", if (maxVsMuxerUs >= 0) maxVsMuxerUs else JSONObject.NULL)
        json.put("ptsDistintosDelContenedor", if (mismatchVsContainer >= 0) mismatchVsContainer else JSONObject.NULL)
        json.put("ptsErrorMaxVsContenedorUs", if (maxVsContainerUs >= 0) maxVsContainerUs else JSONObject.NULL)
        json.put("ptsPrimeros", JSONArray(log.pts.take(6)))
        json.put("ptsMuxerPrimeros", JSONArray(muxed.take(6)))

        // Centroide: el fotograma k se compara con la posición del disco en el k-ésimo fotograma escrito (o, si faltan, el más cercano por PTS).
        val point = DoubleArray(2)
        val opposite = DoubleArray(2)
        var errMax = 0.0
        var errSum = 0.0
        var errCount = 0
        // Para depurar una orientación mal: el mayor error si el resultado estuviera espejado, girado 180° o girado al revés.
        var mirrorXMax = 0.0
        var mirrorYMax = 0.0
        var turn180Max = 0.0
        var oppositeTurnMax = 0.0
        val outW = log.width.toDouble()
        val outH = log.height.toDouble()
        for (k in 0 until decoded) {
            val truthIndex = if (sameCountAsMuxer) k else nearestIndex(muxed, log.pts[k])
            val t = muxed[truthIndex]
            val diskX = SyntheticClipEncoder.diskX(t)
            val diskY = SyntheticClipEncoder.diskY(t)
            VideoFrameMath.rotatePoint(diskX, diskY, SyntheticClipEncoder.WIDTH, SyntheticClipEncoder.HEIGHT, spec.orientationHint, point)
            VideoFrameMath.rotatePoint(
                diskX,
                diskY,
                SyntheticClipEncoder.WIDTH,
                SyntheticClipEncoder.HEIGHT,
                360 - spec.orientationHint,
                opposite,
            )
            val x = log.cx[k]
            val y = log.cy[k]
            if (x.isNaN() || y.isNaN()) {
                errMax = NO_CENTROID_PX
                continue
            }
            val err = hypot(x - point[0], y - point[1])
            if (err > errMax) errMax = err
            errSum += err
            errCount++
            mirrorXMax = maxOf(mirrorXMax, hypot(x - (outW - point[0]), y - point[1]))
            mirrorYMax = maxOf(mirrorYMax, hypot(x - point[0], y - (outH - point[1])))
            turn180Max = maxOf(turn180Max, hypot(x - (outW - point[0]), y - (outH - point[1])))
            oppositeTurnMax = maxOf(oppositeTurnMax, hypot(x - opposite[0], y - opposite[1]))
        }
        val errMean = if (errCount > 0) errSum / errCount else NO_CENTROID_PX
        json.put("centroideErrorMaxPx", finite(Math.round(errMax * 100) / 100.0))
        json.put("centroideErrorMedioPx", finite(Math.round(errMean * 100) / 100.0))
        json.put(
            "hipotesisErrorMaxPx",
            JSONObject()
                .put("espejoX", finite(Math.round(mirrorXMax * 10) / 10.0))
                .put("espejoY", finite(Math.round(mirrorYMax * 10) / 10.0))
                .put("giro180", finite(Math.round(turn180Max * 10) / 10.0))
                .put("giroContrario", finite(Math.round(oppositeTurnMax * 10) / 10.0)),
        )
        if (decoded > 0 && !log.cx[0].isNaN()) {
            json.put("centroidePrimerFotograma", JSONArray(listOf(Math.round(log.cx[0] * 100) / 100.0, Math.round(log.cy[0] * 100) / 100.0)))
        }
        json.put("lumaVsRgbaDifMax", log.lumaVsRgbaMaxDiff)

        val totalMs = wallNs / 1e6
        val perFrame = if (decoded > 0) totalMs / decoded else 0.0
        val perFrameNoCallback = if (decoded > 0) (wallNs - callbackNs) / 1e6 / decoded else 0.0
        json.put("msTotal", finite(Math.round(totalMs * 10) / 10.0))
        json.put("msPorFotograma", finite(Math.round(perFrame * 100) / 100.0))
        json.put("msPorFotogramaSinCallback", finite(Math.round(perFrameNoCallback * 100) / 100.0))
        // Régimen estable: sin el arranque (EGL, códec, primer fotograma) ni el tiempo de la prueba en cada fotograma.
        if (decoded > 1 && firstFrameNs >= 0L) {
            val steady = (wallNs - firstFrameNs - callbackNs) / 1e6 / (decoded - 1)
            json.put("msPrimerFotograma", finite(Math.round(firstFrameNs / 1e5) / 10.0))
            json.put("msPorFotogramaEstable", finite(Math.round(steady * 100) / 100.0))
        }

        val sizeOk = log.width == expectedSize.width && log.height == expectedSize.height
        val ok = if (forceRetriever) {
            decoded > 0 && sizeOk && errMax <= RETRIEVER_CENTROID_TOLERANCE_PX && log.lumaVsRgbaMaxDiff == 0
        } else {
            val timesOk = if (container != null) mismatchVsContainer == 0 else maxVsMuxerUs in 0L..2L
            path == VideoDecodePath.GL && sameCountAsMuxer && timesOk && sizeOk &&
                errMax <= GL_CENTROID_TOLERANCE_PX && log.lumaVsRgbaMaxDiff == 0
        }
        json.put("ok", ok)
    }

    /**
     * `FrameNeeds` por fotograma: índice 0 de cada tres pide RGBA, el 1 LUMA y el 2 SKIP. Se entregan solo los dos primeros,
     * con el campo pedido y el otro a `null`, y los índices son los de todos los fotogramas (el SKIP también gasta el suyo).
     */
    private suspend fun runNeedsCheck(forceRetriever: Boolean): JSONObject {
        val json = JSONObject()
        val spec = SPECS[0]
        val file = File(dir, "${spec.name}.mp4")
        val total = expectedFrames[spec.name] ?: return json.put("error", "sin el clip ${spec.name}").put("ok", false)
        note("necesidades: ${if (forceRetriever) "retriever" else "gl"}")
        val info = VideoInfoProbe.probe(file)
        val source: VideoFrameSource = if (forceRetriever) RetrieverVideoFrameSource(file, info) else VideoFrameSources.open(file)
        val indices = ArrayList<Int>()
        var wrongFields = 0
        source.use { src ->
            src.decode(
                needs = { index, _ ->
                    when (index % 3) {
                        0 -> FrameNeeds.RGBA
                        1 -> FrameNeeds.LUMA
                        else -> FrameNeeds.SKIP
                    }
                },
            ) { frame ->
                indices.add(frame.index)
                val fieldsOk = when (frame.index % 3) {
                    0 -> frame.rgba != null && frame.luma == null
                    1 -> frame.rgba == null && frame.luma != null
                    else -> false
                }
                if (!fieldsOk) wrongFields++
            }
        }
        val expectedIndices = (0 until total).filter { it % 3 != 2 }
        json.put("entregados", indices.size)
        json.put("esperados", expectedIndices.size)
        json.put("camposMal", wrongFields)
        json.put("ok", wrongFields == 0 && indices == expectedIndices)
        return json
    }

    /** `fromUs > 0`: salta al sincronismo anterior, descarta hasta `fromUs` y numera desde 0, con los PTS del contenedor. */
    private suspend fun runSeekCheck(): JSONObject {
        val json = JSONObject()
        val spec = SPECS[1]
        val file = File(dir, "${spec.name}.mp4")
        if (!file.isFile) return json.put("error", "sin el clip ${spec.name}").put("ok", false)
        val fromUs = 1_500_000L
        val expected = readContainerPts(file).filter { it >= fromUs }
        val seenPts = ArrayList<Long>()
        val seenIndex = ArrayList<Int>()
        note("desde $fromUs us: decodificando ${spec.name}")
        VideoFrameSources.open(file).use { source ->
            source.decode(fromUs = fromUs, needs = { _, _ -> FrameNeeds.LUMA }) { frame ->
                seenPts.add(frame.ptsUs)
                seenIndex.add(frame.index)
            }
        }
        json.put("desdeUs", fromUs)
        json.put("fotogramasEsperados", expected.size)
        json.put("fotogramasDecodificados", seenPts.size)
        json.put("primerPtsUs", seenPts.firstOrNull() ?: JSONObject.NULL)
        json.put("primerIndice", seenIndex.firstOrNull() ?: JSONObject.NULL)
        val samePts = seenPts.size == expected.size && seenPts.indices.all { seenPts[it] == expected[it] }
        val countedFromZero = seenIndex.indices.all { seenIndex[it] == it }
        json.put("ok", samePts && countedFromZero && seenPts.isNotEmpty())
        return json
    }

    /** Cancelar a mitad de una decodificación libera todo (hilos incluidos) y la fuente se puede abrir otra vez. */
    private suspend fun runCancelCheck(): JSONObject {
        val json = JSONObject()
        val spec = SPECS[1]
        val file = File(dir, "${spec.name}.mp4")
        val expected = expectedFrames[spec.name] ?: return json.put("error", "sin el clip ${spec.name}").put("ok", false)
        note("cancelación: abriendo ${spec.name}")
        var seen = 0
        var cancelMs = -1L
        var arrived = false
        VideoFrameSources.open(file).use { source ->
            coroutineScope {
                val reached = CompletableDeferred<Unit>()
                val worker = launch(Dispatchers.Default) {
                    source.decode(needs = { _, _ -> FrameNeeds.LUMA }) { _ ->
                        seen++
                        if (seen == CANCEL_AFTER_FRAMES) reached.complete(Unit)
                    }
                }
                arrived = withTimeoutOrNull(30_000L) { reached.await() } != null
                val requestedNs = System.nanoTime()
                worker.cancelAndJoin()
                cancelMs = (System.nanoTime() - requestedNs) / 1_000_000L
            }
        }
        if (!arrived) json.put("error", "no llegaron $CANCEL_AFTER_FRAMES fotogramas en 30 s")
        delay(500L)
        val threads = Thread.getAllStackTraces().keys.filter { it.isAlive }
        val frameThreads = threads.count { it.name == "vt-frames" }
        val decodeThreads = threads.count { it.name.startsWith("vt-decode") }
        note("cancelación: reabriendo")
        var reopened = 0
        VideoFrameSources.open(file).use { source ->
            source.decode(needs = { _, _ -> FrameNeeds.LUMA }) { _ -> reopened++ }
        }
        json.put("fotogramasAntesDeCancelar", seen)
        json.put("cancelarMs", cancelMs)
        json.put("hilosVtFramesVivos", frameThreads)
        json.put("hilosVtDecodeVivos", decodeThreads)
        json.put("fotogramasTrasReabrir", reopened)
        json.put("fotogramasEsperados", expected)
        json.put("ok", !json.has("error") && seen >= CANCEL_AFTER_FRAMES && cancelMs in 0L..1500L && frameThreads == 0 && decodeThreads == 0 && reopened == expected)
        return json
    }
}
