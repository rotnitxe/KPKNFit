package com.example.kpkn.debug

import android.content.Context
import android.graphics.Color
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.example.kpkn.data.media.video.DecodedFrame
import com.example.kpkn.data.media.video.FrameNeeds
import com.example.kpkn.data.media.video.RetrieverVideoFrameSource
import com.example.kpkn.data.media.video.ResumableVideoFrameSource
import com.example.kpkn.data.media.video.VideoDecodePath
import com.example.kpkn.data.media.video.VideoDecodeReport
import com.example.kpkn.data.media.video.VideoFrameMath
import com.example.kpkn.data.media.video.VideoFrameSource
import com.example.kpkn.data.media.video.VideoFrameSources
import com.example.kpkn.data.media.video.VideoInfo
import com.example.kpkn.data.media.video.VideoInfoProbe
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.hypot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

/**
 * SOLO DEPURACIÓN (no se integra): banco de la técnica en vídeo. El modo se pasa con `--es mode <modo>`: `decode_selftest` (el de
 * por defecto, VT4a), `pose_bench` y `telemetry_check` (VT4b, ver [PoseBench] y [TelemetryCheck]) y `analyze_file` (VT4c, ver
 * [AnalyzeFileBench]: el pipeline real sobre los vídeos de `vt-bench-in`). Con `--ez quit true` la actividad se cierra al terminar
 * el modo.
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
        // El banco corre en el teléfono bloqueado (VT11b): se muestra y enciende la pantalla sobre el bloqueo.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        status = TextView(this).apply {
            setBackgroundColor(Color.BLACK)
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(48, 160, 48, 48)
            text = "Banco de técnica en vídeo…"
        }
        setContentView(status)
        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_DECODE_SELFTEST
        val quit = intent.getBooleanExtra(EXTRA_QUIT, false)
        if (mode == MODE_RECORDER_PREVIEW) {
            val outDir = getExternalFilesDir("vt-recorder")?.apply { mkdirs() }
            val logFile = File(outDir, "recorder_log.txt")
            logFile.writeText("TechniqueBenchActivity started mode=$mode\n")
            val cm = getSystemService(Context.CAMERA_SERVICE) as? android.hardware.camera2.CameraManager
            logFile.appendText("CameraManager ids: ${cm?.cameraIdList?.joinToString()}\n")
            val repository = com.example.kpkn.data.repository.WorkoutMediaRepository.init(this)
            val mediaCapture = com.example.kpkn.screens.workout.WorkoutMediaCaptureController(
                appContext = applicationContext,
                scope = lifecycleScope,
                repository = repository,
                sessionMeta = {
                    com.example.kpkn.screens.workout.WorkoutMediaSessionMeta(
                        sessionKey = "debug_recorder_session",
                        programId = "debug",
                        sessionId = "debug_session",
                        sessionName = "Prueba de técnica",
                    )
                },
            )
            val fakeExercise = com.example.kpkn.data.models.Exercise(
                id = "debug_squat",
                name = "Sentadilla con barra",
                catalogDefinitionId = "high_bar_back_squat",
                catalogConfigurationId = "high_bar_back_squat__barbell",
            )
            val fakeRequest = com.example.kpkn.screens.workout.technique.TechniqueRecorderRequest(
                captureRequest = com.example.kpkn.screens.workout.WorkoutMediaCaptureRequest(
                    exerciseId = fakeExercise.id,
                    exerciseName = fakeExercise.name,
                    setIndex = 0,
                    catalogDefinitionId = fakeExercise.catalogDefinitionId,
                    catalogConfigurationId = fakeExercise.catalogConfigurationId,
                ),
                exercise = fakeExercise,
                setLabel = "Serie 1",
            )
            setContent {
                com.example.kpkn.screens.workout.technique.TechniqueRecorderScreen(
                    request = fakeRequest,
                    onDismiss = {
                        lifecycleScope.launch(Dispatchers.IO) {
                            runCatching {
                                val outDir = getExternalFilesDir("vt-recorder")
                                if (outDir != null) {
                                    outDir.mkdirs()
                                    val files = applicationContext.filesDir.walkTopDown().toList()
                                    for (file in files) {
                                        if (file.isFile && (file.extension == "mp4" || file.name.endsWith(".capture.json"))) {
                                            file.copyTo(File(outDir, file.name), overwrite = true)
                                        }
                                    }
                                    val statusFile = File(outDir, "recorder_status.txt")
                                    val hasVideos = files.any { it.isFile && it.extension == "mp4" }
                                    if (hasVideos) {
                                        statusFile.writeText("OK: grabacion finalizada\n")
                                    } else {
                                        statusFile.writeText("NO_RECORDING: sin camara o grabacion no realizada\n")
                                    }
                                }
                            }
                            withContext(Dispatchers.Main) {
                                if (quit) finish()
                            }
                        }
                    },
                    mediaCapture = mediaCapture,
                )
            }
            return
        }
        if (mode == MODE_DECODE_SELFTEST) {
            // El result.json de una corrida anterior fuera YA, antes de que termine el arranque: quien espere el archivo no debe ver el viejo.
            runCatching { File(getExternalFilesDir(SELFTEST_DIR), RESULT_FILE).delete() }
        }
        if (mode == MODE_POSE_BENCH) runCatching { File(BenchLayout.resolve(this).outDir, PoseBench.BENCH_FILE).delete() }
        if (mode == MODE_TELEMETRY_CHECK) runCatching { File(BenchLayout.resolve(this).outDir, TelemetryCheck.TELEMETRY_FILE).delete() }
        if (mode == MODE_ANALYZE_FILE) runCatching { File(BenchLayout.resolve(this).outDir, ANALYZE_FILE).delete() }
        lifecycleScope.launch(Dispatchers.Default) {
            val text = try {
                when (mode) {
                    MODE_DECODE_SELFTEST -> {
                        val dir = getExternalFilesDir(SELFTEST_DIR) ?: error("Sin almacenamiento externo de la app")
                        "Listo: ${DecodeSelfTest(dir).run()}"
                    }
                    MODE_POSE_BENCH -> {
                        val bench = PoseBench(
                            this@TechniqueBenchActivity,
                            BenchLayout.resolve(this@TechniqueBenchActivity),
                            engineFilter = listExtra(EXTRA_ENGINES).toSet(),
                            videoFilter = listExtra(EXTRA_VIDEOS),
                            maxSeconds = intent.getIntExtra(EXTRA_MAX_SECONDS, 0),
                        )
                        "Listo: ${bench.run()}"
                    }
                    MODE_TELEMETRY_CHECK -> {
                        val check = TelemetryCheck(
                            this@TechniqueBenchActivity,
                            BenchLayout.resolve(this@TechniqueBenchActivity),
                            waitSeconds = intent.getIntExtra(EXTRA_WAIT_SECONDS, TELEMETRY_WAIT_SECONDS),
                            disableTransport = intent.getBooleanExtra(EXTRA_DISABLE_TRANSPORT, false),
                            detect = intent.getBooleanExtra(EXTRA_DETECT, true),
                        )
                        "Listo: ${check.run()}"
                    }
                    MODE_ANALYZE_FILE -> {
                        val bench = AnalyzeFileBench.fromIntent(
                            this@TechniqueBenchActivity,
                            BenchLayout.resolve(this@TechniqueBenchActivity),
                            intent,
                        )
                        "Listo: ${bench.run()}"
                    }
                    else -> "Modo desconocido: $mode"
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                "Error: ${describe(error)}"
            }
            withContext(Dispatchers.Main) {
                status.text = text
                if (quit) finish()
            }
        }
    }

    /** Un extra de texto con valores separados por comas (`--es engines mp-full-cpu,mlkit-base`); vacío si falta. */
    private fun listExtra(key: String): List<String> =
        intent.getStringExtra(key)?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
}

private const val TAG = "KpknTechniqueBench"
private const val EXTRA_MODE = "mode"
private const val MODE_DECODE_SELFTEST = "decode_selftest"
private const val MODE_POSE_BENCH = "pose_bench"
private const val MODE_TELEMETRY_CHECK = "telemetry_check"
private const val MODE_ANALYZE_FILE = "analyze_file"
private const val MODE_RECORDER_PREVIEW = "recorder_preview"
private const val ANALYZE_FILE = "analyze.json"
private const val EXTRA_QUIT = "quit"
private const val EXTRA_ENGINES = "engines"
private const val EXTRA_VIDEOS = "videos"
private const val EXTRA_MAX_SECONDS = "maxSeconds"
private const val EXTRA_WAIT_SECONDS = "waitSeconds"
private const val EXTRA_DISABLE_TRANSPORT = "disableTransport"
private const val EXTRA_DETECT = "detect"
private const val TELEMETRY_WAIT_SECONDS = 75
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
private fun readContainerPts(file: File, sortForPresentation: Boolean = true): LongArray {
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
        if (sortForPresentation) sorted.sort()
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
            val bFrames = runBFrameCheck()
            report.put("bFrames", bFrames)
            // An ignored/unsupported encoder request is explicitly NOT_VALIDATED, never a B-frame PASS.
            if (bFrames.optString("estado") != "NOT_VALIDATED") allOk = allOk && bFrames.optBoolean("ok")
            summary.append("bFrames=").append(bFrames.optString("estado")).append(' ')
            val cancellation = runLifecyclePair(listOf(SPECS[1], SPECS[3])) { route, spec -> runCancelCheck(route, spec) }
            report.put("cancelacion", cancellation)
            allOk = allOk && cancellation.optBoolean("ok")
            summary.append("cancel=").append(cancellation.optBoolean("ok")).append(' ')
            val needsCheck = runLifecyclePair(listOf(SPECS[0], SPECS[3])) { route, spec -> runNeedsCheck(route, spec) }
            report.put("necesidades", needsCheck)
            allOk = allOk && needsCheck.optBoolean("ok")
            summary.append("needs=").append(needsCheck.optBoolean("ok")).append(' ')
            val resume = runLifecyclePair(listOf(SPECS[1], SPECS[3])) { route, spec -> runSeekCheck(route, spec) }
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

    /** Instrumented diagnostic with a known logical frame ID, not merely a requested B-frame count. */
    private suspend fun runBFrameCheck(): JSONObject {
        val json = JSONObject().put("solicitudMaxBFrames", 2).put("estado", "NOT_VALIDATED").put("ok", false)
        if (Build.VERSION.SDK_INT < 29) return json.put("motivo", "encoder_api_unsupported")
        try {
            val spec = SyntheticClipEncoder.Spec("e_bframes", 30, 3, 0, false, maxBFrames = 2, frameCodes = true)
            val clip = withContext(Dispatchers.IO) { SyntheticClipEncoder.encodeWithTimeout(File(dir, "e_bframes.mp4"), spec) }
            val sampleOrder = readContainerPts(clip.file, sortForPresentation = false)
            val reordered = (1 until sampleOrder.size).any { sampleOrder[it] < sampleOrder[it - 1] }
            val encoderReordered = (1 until clip.muxerPtsInWriteOrder.size).any {
                clip.muxerPtsInWriteOrder[it] < clip.muxerPtsInWriteOrder[it - 1]
            }
            json.put("reordenacionContenedorObservada", reordered)
            json.put("reordenacionEncoderObservada", encoderReordered)
            json.put("ptsMuestrasPrimeros", JSONArray(sampleOrder.take(12)))
            json.put("ptsMuxerOrdenEscrituraPrimeros", JSONArray(clip.muxerPtsInWriteOrder.take(12)))
            if (!reordered) return json.put("motivo", "encoder_ignored_b_request_or_muxer_did_not_preserve_reorder")
            val info = VideoInfoProbe.probe(clip.file)
            val ids = ArrayList<Int>()
            val outputPts = ArrayList<Long>()
            val source = RetrieverVideoFrameSource(clip.file, info)
            source.use { src ->
                src.decode(targetLongEdge = TARGET_LONG_EDGE, needs = { _, _ -> FrameNeeds.LUMA }) { frame ->
                    val luma = checkNotNull(frame.luma)
                    check(frame.width == SyntheticClipEncoder.WIDTH && frame.height == SyntheticClipEncoder.HEIGHT)
                    var code = 0
                    for (bit in 0 until 8) {
                        val x = 12 + bit * 12
                        val y = frame.height - 12
                        if ((luma[y * frame.width + x].toInt() and 255) > 128) code = code or (1 shl bit)
                    }
                    ids.add(code)
                    outputPts.add(frame.ptsUs)
                }
                json.put("detalles", (src as VideoDecodeReport).details)
            }
            val sameCount = ids.size == clip.ptsUs.size && ids.size == sampleOrder.size
            val idsOk = sameCount && ids.indices.all { ids[it] == it }
            val timesOk = sameCount && outputPts.indices.all { outputPts[it] == clip.ptsUs[it] }
            val ok = idsOk && timesOk
            json.put("estado", if (ok) "PASS" else "FAIL")
            json.put("ok", ok).put("precisionTemporalVerificada", ok)
            json.put("idsPresentacionCorrectos", idsOk).put("ptsPresentacionCorrectos", timesOk)
            json.put("idsDecodificados", JSONArray(ids)).put("ptsDecodificados", JSONArray(outputPts))
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Throwable) {
            // If reorder was already demonstrated, this is a route failure, not an unsupported encoder.
            if (json.optBoolean("reordenacionContenedorObservada")) json.put("estado", "FAIL")
            json.put("motivo", describe(error))
        }
        return json
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
                if (forceRetriever) RetrieverVideoFrameSource(file, info, indexed = indexed, allowUnverifiedTimedDiagnostic = !indexed) else VideoFrameSources.open(file)
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
                fill(json, log, clip, container, wallNs, callbackNs, firstFrameNs, expectedSize, path, forceRetriever, indexed)
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
        indexed: Boolean,
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
        val preciseTimeRequired = !forceRetriever || indexed
        val exactContainerTime = container != null && sameCountAsMuxer && mismatchVsContainer == 0
        json.put("precisionTemporalVerificada", preciseTimeRequired && exactContainerTime)
        json.put("ptsSemantica", if (preciseTimeRequired) "presentacion_contenedor" else "solicitud_no_pts_del_bitmap")
        json.put("alcance", if (preciseTimeRequired) "fotogramas_y_reloj" else "geometria_sin_validacion_temporal")
        val ok = if (forceRetriever) {
            val timesOk = if (indexed) exactContainerTime else true
            decoded > 0 && sizeOk && timesOk && errMax <= RETRIEVER_CENTROID_TOLERANCE_PX && log.lumaVsRgbaMaxDiff == 0
        } else {
            val timesOk = if (container != null) mismatchVsContainer == 0 else maxVsMuxerUs in 0L..2L
            path == VideoDecodePath.GL && sameCountAsMuxer && timesOk && sizeOk &&
                errMax <= GL_CENTROID_TOLERANCE_PX && log.lumaVsRgbaMaxDiff == 0
        }
        json.put("ok", ok)
    }

    /** A requested GL case must actually finish through GL; fallback never earns a GL PASS. */
    private class LifecycleRoute(
        val name: String,
        val expectedPath: VideoDecodePath,
        val open: (File) -> VideoFrameSource,
    )

    private val lifecycleRoutes = listOf(
        LifecycleRoute("gl", VideoDecodePath.GL) { VideoFrameSources.open(it) },
        LifecycleRoute("retrieverIndexed", VideoDecodePath.RETRIEVER) {
            RetrieverVideoFrameSource(it, VideoInfoProbe.probe(it), indexed = true, allowUnverifiedTimedDiagnostic = false)
        },
    )

    /** Isolate every route/fixture failure, so GL failure does not prevent testing forced retriever. */
    private suspend fun runLifecyclePair(
        specs: List<SyntheticClipEncoder.Spec>,
        check: suspend (LifecycleRoute, SyntheticClipEncoder.Spec) -> JSONObject,
    ): JSONObject {
        val result = JSONObject().put("esquemaCicloVida", 2)
        var allOk = true
        for (route in lifecycleRoutes) {
            val cases = JSONArray()
            var routeOk = true
            for (spec in specs) {
                val item = try {
                    check(route, spec)
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (error: Throwable) {
                    JSONObject().put("error", describe(error)).put("ok", false)
                }
                item.put("fixture", spec.name).put("rutaSolicitada", route.name)
                cases.put(item)
                routeOk = routeOk && item.optBoolean("ok")
            }
            result.put(if (route.expectedPath == VideoDecodePath.GL) "gl" else "retriever",
                JSONObject().put("casos", cases).put("ok", routeOk))
            allOk = allOk && routeOk
        }
        return result.put("ok", allOk)
    }

    /** The oracle is the measured container clock of the SAME generated fixture for both routes. */
    private fun lifecyclePts(file: File, spec: SyntheticClipEncoder.Spec): LongArray {
        check(file.isFile) { "sin el clip ${spec.name}" }
        val expected = checkNotNull(expectedFrames[spec.name]) { "sin conteo muxer ${spec.name}" }
        val pts = readContainerPts(file)
        check(pts.size == expected && pts.size > CANCEL_AFTER_FRAMES) { "conteo contenedor/muxer no coincide" }
        check((1 until pts.size).all { pts[it] > pts[it - 1] }) { "PTS repetidos/no crecientes" }
        if (spec.variableRate) {
            check(hasExpectedVfrCadence(pts)) { "fixture VFR no conservó la alternancia 25/41 ms (±1 µs)" }
        }
        return pts
    }

    /** The fixture requests 25/41 ms, not merely two different microsecond deltas (CFR quantizes too). */
    private fun hasExpectedVfrCadence(pts: LongArray): Boolean {
        if (pts.size < 3) return false
        for (i in 1 until pts.size) {
            val expectedDeltaUs = if (i % 2 == 1) 25_000L else 41_000L
            val deltaUs = pts[i] - pts[i - 1]
            if (deltaUs !in (expectedDeltaUs - 1L)..(expectedDeltaUs + 1L)) return false
        }
        return true
    }

    private fun lifecycleProvenance(json: JSONObject, src: VideoFrameSource, route: LifecycleRoute): Boolean {
        val report = src as? VideoDecodeReport
        val details = report?.details.orEmpty()
        val pathOk = report?.path == route.expectedPath
        val indexedOk = route.expectedPath != VideoDecodePath.RETRIEVER ||
            (details.startsWith("retriever mode=indexed ") && details.contains("pts=container-index") && details.contains("timing=verified"))
        json.put("caminoObservado", report?.path?.name ?: JSONObject.NULL)
            .put("detalles", details).put("motivoRespaldo", report?.fallbackReason ?: JSONObject.NULL)
            .put("procedenciaCorrecta", pathOk && indexedOk)
            .put("timedDiagnosticoPermitido", false)
        return pathOk && indexedOk
    }

    private fun lifecycleThreads(): JSONObject {
        val names = Thread.getAllStackTraces().keys.filter { it.isAlive }.map { it.name }
        return JSONObject().put("vtFrames", names.count { it == "vt-frames" })
            .put("vtDecode", names.count { it.startsWith("vt-decode") })
            .put("vtDecodeRt", names.count { it == "vt-decode-rt" })
    }

    private fun noLifecycleThreads(json: JSONObject): Boolean =
        json.getInt("vtFrames") == 0 && json.getInt("vtDecode") == 0 && json.getInt("vtDecodeRt") == 0

    /** Every needs() call, including SKIP, must carry the exact indexed container PTS. */
    private suspend fun runNeedsCheck(route: LifecycleRoute, spec: SyntheticClipEncoder.Spec): JSONObject {
        val file = File(dir, "${spec.name}.mp4")
        val expectedPts = lifecyclePts(file, spec)
        val askedIndices = ArrayList<Int>()
        val askedPts = ArrayList<Long>()
        val indices = ArrayList<Int>()
        val deliveredPts = ArrayList<Long>()
        var wrongFields = 0
        val json = JSONObject()
        var provenanceOk = false
        note("necesidades ${route.name}: ${spec.name}")
        route.open(file).use { src ->
            src.decode(targetLongEdge = TARGET_LONG_EDGE, needs = { index, ptsUs ->
                askedIndices.add(index)
                askedPts.add(ptsUs)
                when (index % 3) {
                    0 -> FrameNeeds.RGBA
                    1 -> FrameNeeds.LUMA
                    else -> FrameNeeds.SKIP
                }
            }) { frame ->
                indices.add(frame.index)
                deliveredPts.add(frame.ptsUs)
                val fieldsOk = when (frame.index % 3) {
                    0 -> frame.rgba != null && frame.luma == null
                    1 -> frame.rgba == null && frame.luma != null
                    else -> false
                }
                if (!fieldsOk) wrongFields++
            }
            provenanceOk = lifecycleProvenance(json, src, route)
        }
        val allIndices = expectedPts.indices.toList()
        val expectedIndices = allIndices.filter { it % 3 != 2 }
        val expectedDeliveredPts = expectedIndices.map { expectedPts[it] }
        val ptsOk = askedPts == expectedPts.toList() && deliveredPts == expectedDeliveredPts
        return json.put("entregados", indices.size).put("esperados", expectedIndices.size)
            .put("camposMal", wrongFields).put("indicesSolicitados", JSONArray(askedIndices))
            .put("indicesEntregados", JSONArray(indices)).put("ptsSolicitados", JSONArray(askedPts))
            .put("ptsEntregados", JSONArray(deliveredPts)).put("ptsContenedor", JSONArray(expectedPts.toList()))
            .put("ptsExactos", ptsOk)
            .put("ok", provenanceOk && wrongFields == 0 && indices == expectedIndices && askedIndices == allIndices && ptsOk)
    }

    /** Exact, between-samples and EOF lower bounds; resume offset only through the public optional API. */
    private suspend fun runSeekCheck(route: LifecycleRoute, spec: SyntheticClipEncoder.Spec): JSONObject {
        val file = File(dir, "${spec.name}.mp4")
        val pts = lifecyclePts(file, spec)
        val middle = pts.size / 2
        val cuts = listOf(1_500_000L, pts[middle], pts[middle] + 1L, pts.last() + 1L).distinct()
        val cases = JSONArray()
        var allOk = true
        for (fromUs in cuts) {
            val expected = pts.filter { it >= fromUs }
            val seenPts = ArrayList<Long>()
            val seenIndex = ArrayList<Int>()
            val json = JSONObject().put("desdeUs", fromUs)
            var provenanceOk = false
            route.open(file).use { source ->
                source.decode(fromUs = fromUs, targetLongEdge = TARGET_LONG_EDGE,
                    needs = { _, _ -> FrameNeeds.LUMA }) { frame ->
                    seenPts.add(frame.ptsUs)
                    seenIndex.add(frame.index)
                }
                provenanceOk = lifecycleProvenance(json, source, route)
            }
            val ok = provenanceOk && seenPts == expected && seenIndex == expected.indices.toList() &&
                (fromUs != 1_500_000L || seenPts.isNotEmpty()) // Preserve the original positive seek assertion.
            cases.put(json.put("ptsEsperados", JSONArray(expected)).put("ptsDecodificados", JSONArray(seenPts))
                .put("indices", JSONArray(seenIndex)).put("ok", ok))
            allOk = allOk && ok
        }
        val resume = JSONObject().put("startIndex", 37)
        val fromUs = pts[middle] + 1L
        val expected = pts.filter { it >= fromUs }
        var supported = false
        var resumeOk = false
        route.open(file).use { source ->
            val resumable = source as? ResumableVideoFrameSource
            supported = resumable != null
            if (resumable != null) {
                val seenPts = ArrayList<Long>()
                val indices = ArrayList<Int>()
                resumable.decodeFrom(fromUs, TARGET_LONG_EDGE, { _, _ -> FrameNeeds.LUMA }, { frame ->
                    seenPts.add(frame.ptsUs)
                    indices.add(frame.index)
                }, startIndex = 37)
                resumeOk = lifecycleProvenance(resume, source, route) && seenPts == expected &&
                    indices == expected.indices.map { it + 37 }
                resume.put("desdeUs", fromUs).put("ptsEsperados", JSONArray(expected))
                    .put("ptsDecodificados", JSONArray(seenPts)).put("indices", JSONArray(indices))
            }
        }
        resume.put("apiExpuesta", supported).put("estado", if (!supported) "NOT_EXPOSED" else if (resumeOk) "PASS" else "FAIL")
        // Retriever implements ResumableVideoFrameSource. GL's public source does not expose this extension.
        val resumeRequired = route.expectedPath == VideoDecodePath.RETRIEVER
        return JSONObject().put("cortes", cases).put("resumeConOffset", resume)
            .put("ok", allOk && if (resumeRequired || supported) resumeOk else true)
    }

    /** Suspend inside frame 12, leaving the indexed block tail unconsumed, then cancel and join. */
    private suspend fun runCancelCheck(route: LifecycleRoute, spec: SyntheticClipEncoder.Spec): JSONObject {
        val file = File(dir, "${spec.name}.mp4")
        val pts = lifecyclePts(file, spec)
        val json = JSONObject()
        val callbacks = AtomicInteger()
        val needsCalls = AtomicInteger()
        val cancelledPts = ArrayList<Long>()
        var arrived = false
        var cancelMs = -1L
        var provenanceOk = false
        route.open(file).use { source ->
            coroutineScope {
                val reached = CompletableDeferred<Unit>()
                val worker = launch(Dispatchers.Default) {
                    source.decode(targetLongEdge = TARGET_LONG_EDGE, needs = { _, _ ->
                        needsCalls.incrementAndGet()
                        FrameNeeds.LUMA
                    }) { frame ->
                        cancelledPts.add(frame.ptsUs)
                        val n = callbacks.incrementAndGet()
                        if (n == CANCEL_AFTER_FRAMES) {
                            reached.complete(Unit)
                            awaitCancellation() // No race in which the entire clip finishes before cancelAndJoin().
                        }
                    }
                }
                arrived = withTimeoutOrNull(30_000L) { reached.await() } != null
                val requestedNs = System.nanoTime()
                worker.cancelAndJoin()
                cancelMs = (System.nanoTime() - requestedNs) / 1_000_000L
            }
            provenanceOk = lifecycleProvenance(json, source, route)
        }
        val callbacksAtClose = callbacks.get()
        val needsAtClose = needsCalls.get()
        delay(500L)
        val closedThreads = lifecycleThreads()
        val noTail = callbacks.get() == callbacksAtClose && needsCalls.get() == needsAtClose &&
            callbacksAtClose == CANCEL_AFTER_FRAMES && needsAtClose == CANCEL_AFTER_FRAMES
        val prefixOk = cancelledPts == pts.take(CANCEL_AFTER_FRAMES)
        val reopen = JSONObject()
        val reopenedPts = ArrayList<Long>()
        val reopenedIndices = ArrayList<Int>()
        var reopenProvenanceOk = false
        note("cancelación ${route.name}: reabriendo ${spec.name}")
        route.open(file).use { source ->
            source.decode(targetLongEdge = TARGET_LONG_EDGE, needs = { _, _ -> FrameNeeds.LUMA }) { frame ->
                reopenedPts.add(frame.ptsUs)
                reopenedIndices.add(frame.index)
            }
            reopenProvenanceOk = lifecycleProvenance(reopen, source, route)
        }
        delay(500L)
        val reopenedThreads = lifecycleThreads()
        val reopenedOk = reopenProvenanceOk && reopenedPts == pts.toList() && reopenedIndices == pts.indices.toList()
        reopen.put("ptsDecodificados", JSONArray(reopenedPts)).put("indices", JSONArray(reopenedIndices))
            .put("hilosTrasCerrar", reopenedThreads).put("ok", reopenedOk && noLifecycleThreads(reopenedThreads))
        return json.put("fotogramasAntesDeCancelar", callbacksAtClose).put("cancelarMs", cancelMs)
            .put("alcanzoPuntoCancelacion", arrived).put("ptsAntesDeCancelar", JSONArray(cancelledPts))
            .put("prefijoPtsExacto", prefixOk).put("sinCallbacksNiNeedsTardios", noTail)
            .put("hilosTrasCancelarYCerrar", closedThreads).put("reapertura", reopen)
            .put("recicladoBitmapsNoConsumidos", "NOT_OBSERVABLE_FROM_PUBLIC_SOURCE_API")
            .put("ok", arrived && provenanceOk && prefixOk && noTail && cancelMs in 0L..1500L &&
                noLifecycleThreads(closedThreads) && reopen.optBoolean("ok"))
    }
}
