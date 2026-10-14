package com.example.kpkn.debug

import android.content.Context
import android.os.Build
import android.os.Debug
import android.util.Log
import com.example.kpkn.data.media.pose.GpuProbe
import com.example.kpkn.data.media.pose.MediaPipePoseEstimators
import com.example.kpkn.data.media.pose.MlKitPoseEstimator
import com.example.kpkn.data.media.pose.PoseEstimator
import com.example.kpkn.data.media.pose.PoseEstimatorReport
import com.example.kpkn.data.media.pose.PoseModel
import com.example.kpkn.data.media.pose.PoseTrackAssembler
import com.example.kpkn.data.media.pose.RgbaFrame
import com.example.kpkn.data.media.video.FrameNeeds
import com.example.kpkn.data.media.video.VideoDecodeReport
import com.example.kpkn.data.media.video.VideoFrameSources
import com.example.kpkn.data.media.video.VideoInfo
import com.example.kpkn.data.media.video.VideoInfoProbe
import com.example.kpkn.domain.biomechanics.pose.PoseTrackCodec
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.io.File
import kotlin.math.ceil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Dónde vive el banco en el almacenamiento externo de la app (`Android/data/<id>/files/`). El diseño anidado es el del brief
 * (`vt-bench/in`, `vt-bench/out`, `vt-bench/models`); `emu_run.py` solo sube y baja subcarpetas de UN nivel, así que si los
 * vídeos están en `vt-bench-in` (o hay una `vt-bench-models`) se usa el diseño plano: `vt-bench-in`, `vt-bench-out`,
 * `vt-bench-models`. Los vídeos y el modelo heavy se empujan al dispositivo; jamás entran en el repo.
 */
internal class BenchLayout(val inDir: File, val outDir: File, val modelsDir: File, val flat: Boolean) {
    companion object {
        fun resolve(context: Context): BenchLayout {
            val base = context.getExternalFilesDir(null) ?: error("Sin almacenamiento externo de la app")
            val nestedHasVideos = hasFiles(File(base, "vt-bench/in"))
            val flat = !nestedHasVideos && (hasFiles(File(base, "vt-bench-in")) || File(base, "vt-bench-models").isDirectory)
            val layout = if (flat) {
                BenchLayout(File(base, "vt-bench-in"), File(base, "vt-bench-out"), File(base, "vt-bench-models"), true)
            } else {
                BenchLayout(File(base, "vt-bench/in"), File(base, "vt-bench/out"), File(base, "vt-bench/models"), false)
            }
            layout.inDir.mkdirs()
            layout.outDir.mkdirs()
            return layout
        }

        private fun hasFiles(dir: File): Boolean = dir.listFiles()?.any { it.isFile } == true
    }
}

/** Estadísticas sin boxing de una serie de tiempos (ms). */
internal class Samples {
    private var data = DoubleArray(512)
    var size = 0
        private set

    fun add(value: Double) {
        if (size == data.size) data = data.copyOf(size * 2)
        data[size++] = value
    }

    /** Añade las muestras de [other], sin la primera si [skipFirst] (el arranque: códec, EGL, grafo, caché de kernels). */
    fun addAll(other: Samples, skipFirst: Boolean) {
        for (i in (if (skipFirst) 1 else 0) until other.size) add(other.data[i])
    }

    /** n, media, p50, p95 y máximo (rango más cercano), más la primera muestra aparte. Sin la primera si [skipFirst]. */
    fun stats(skipFirst: Boolean): JSONObject {
        val from = if (skipFirst && size > 1) 1 else 0
        val n = size - from
        val json = JSONObject().put("n", n)
        if (n <= 0) return json
        val sorted = data.copyOfRange(from, size)
        sorted.sort()
        var sum = 0.0
        for (v in sorted) sum += v
        json.put("media", round2(sum / n))
        json.put("p50", round2(percentile(sorted, 50.0)))
        json.put("p95", round2(percentile(sorted, 95.0)))
        json.put("max", round2(sorted[n - 1]))
        json.put("primero", round2(data[0]))
        return json
    }

    private fun percentile(sorted: DoubleArray, p: Double): Double {
        val rank = ceil(p / 100.0 * sorted.size).toInt() - 1
        return sorted[rank.coerceIn(0, sorted.size - 1)]
    }
}

internal fun round2(value: Double): Any = if (value.isFinite()) Math.round(value * 100) / 100.0 else JSONObject.NULL

internal fun describeError(error: Throwable): String {
    val cause = error.cause
    return "${error.javaClass.simpleName}: ${error.message}" + if (cause != null) " <- ${cause.javaClass.simpleName}: ${cause.message}" else ""
}

/**
 * SOLO DEPURACIÓN (no se integra): modo `pose_bench` de [TechniqueBenchActivity] (VT4b). Por cada vídeo de [BenchLayout.inDir] y
 * cada motor (`mp-full-gpu`, `mp-full-cpu`, `mp-heavy-gpu` si hay `pose_landmarker_heavy.task` en `models`, `mlkit-base`)
 * decodifica con `VideoFrameSources.open` a 720 px y calcula la pose DENTRO de `onFrame` (el `DecodedFrame` solo vale ahí),
 * bloqueando hasta tener el resultado. Pose en cada fotograma, o en uno de cada dos si el vídeo pasa de 35 fps o `GpuProbe` lo
 * recomienda; los fotogramas sin pose se piden como SKIP y ni se dibujan.
 *
 * Escribe en `out`:
 * - `<vídeo>.<motor>.pose2.json.gz`: la traza v2 (`PoseTrackCodec`);
 * - `bench.json` (archivo temporal + renombrado; el viejo se borra al arrancar). Por vídeo y motor: ms por fotograma de
 *   decodificación y de pose (media, p50, p95; sin la primera muestra, que va aparte en `primero`), fotogramas con pose, % con
 *   2 poses, delegado real, camino de decodificación, creación del motor y errores; y `resumenPorMotor` con todo agregado.
 *
 * Tiempo de decodificación de una muestra = lo que pasa entre dos llamadas a `onFrame` (sin el tiempo de la pose) dividido
 * entre los fotogramas decodificados en medio (con stride 2, también el que se salta). Tiempo de pose = lo que tarda `estimate`.
 *
 * Extras opcionales de la actividad: `--es engines mp-full-cpu,mlkit-base` (solo esos), `--es videos r14,x14` (los que contengan
 * esos textos) y `--ei maxSeconds 20` (solo los primeros segundos de cada vídeo).
 */
internal class PoseBench(
    private val context: Context,
    private val layout: BenchLayout,
    private val engineFilter: Set<String>,
    private val videoFilter: List<String>,
    private val maxSeconds: Int,
) {
    private class EngineSpec(val id: String, val requestedDelegate: String, val create: (Context) -> PoseEstimator)

    /** Para cortar la decodificación antes de tiempo (`maxSeconds`) sin tratarla como un fallo. */
    private class StopBench : CancellationException("fin del banco")

    /** Lo agregado de un motor en todos los vídeos. */
    private class EngineTotals {
        val decodeMs = Samples()
        val poseMs = Samples()
        val delegates = LinkedHashSet<String>()
        var videos = 0
        var posedFrames = 0
        var errors = 0
    }

    private val progressFile = File(layout.outDir, PROGRESS_FILE)

    private fun note(message: String) {
        Log.i(TAG, message)
        runCatching { progressFile.appendText("${System.currentTimeMillis() % 100_000_000L} $message\n") }
    }

    suspend fun run(): String {
        runCatching { File(layout.outDir, BENCH_FILE).delete() }
        runCatching { progressFile.delete() }
        val probe = GpuProbe.get(context)
        val report = JSONObject()
            .put("modo", "pose_bench")
            .put("dispositivo", "${Build.MANUFACTURER} ${Build.MODEL}")
            .put("api", Build.VERSION.SDK_INT)
            .put("abi", Build.SUPPORTED_ABIS.firstOrNull().orEmpty())
            .put("disposicion", if (layout.flat) "plana" else "anidada")
            .put("sondaGpuAlInicio", probe.diagnostics)
            .put("maxSegundos", maxSeconds)
        var allOk = true
        val summary = StringBuilder()
        val totals = LinkedHashMap<String, EngineTotals>()
        try {
            val engines = engineSpecs()
            report.put("motores", JSONArray(engines.map { it.id }))
            val videos = videoFiles()
            val videosJson = JSONArray()
            for (video in videos) {
                val outcome = try {
                    runVideo(video, engines, totals)
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (error: Throwable) {
                    note("${video.name}: fallo: ${describeError(error)}")
                    JSONObject().put("video", video.name).put("error", describeError(error)).put("ok", false)
                }
                videosJson.put(outcome)
                allOk = allOk && outcome.optBoolean("ok")
            }
            report.put("videos", videosJson)
            val perEngine = JSONArray()
            for ((id, t) in totals) {
                perEngine.put(
                    JSONObject()
                        .put("motor", id)
                        .put("delegados", JSONArray(t.delegates.toList()))
                        .put("videos", t.videos)
                        .put("fotogramasConPose", t.posedFrames)
                        .put("errores", t.errors)
                        .put("decodificacionMs", t.decodeMs.stats(skipFirst = false))
                        .put("poseMs", t.poseMs.stats(skipFirst = false)),
                )
                val pose = t.poseMs.stats(false)
                summary.append("$id[${t.delegates.joinToString("/")} pose p50=${pose.opt("p50")} p95=${pose.opt("p95")}ms] ")
            }
            report.put("resumenPorMotor", perEngine)
            report.put("sondaGpuAlFinal", probe.diagnostics)
            if (videos.isEmpty()) {
                allOk = false
                report.put("error", "sin vídeos en ${layout.inDir.absolutePath}")
            }
        } catch (cancel: CancellationException) {
            report.put("error", "cancelado: la actividad se cerró antes de terminar")
            allOk = false
            throw cancel
        } catch (error: Throwable) {
            report.put("error", describeError(error))
            allOk = false
        } finally {
            report.put("ok", allOk)
            val tmp = File(layout.outDir, "$BENCH_FILE.tmp")
            tmp.writeText(report.toString(2))
            check(tmp.renameTo(File(layout.outDir, BENCH_FILE))) { "No se pudo renombrar $BENCH_FILE" }
            Log.i(TAG, "pose_bench ok=$allOk $summary")
        }
        return "pose_bench ${if (allOk) "OK" else "CON FALLOS"} · $summary"
    }

    private fun engineSpecs(): List<EngineSpec> {
        val all = ArrayList<EngineSpec>()
        all += EngineSpec("mp-full-gpu", "GPU") {
            MediaPipePoseEstimators.create(it, RunningMode.VIDEO, PoseModel.Full, preferGpu = true)
        }
        all += EngineSpec("mp-full-cpu", "CPU") {
            MediaPipePoseEstimators.create(it, RunningMode.VIDEO, PoseModel.Full, preferGpu = false)
        }
        val heavy = File(layout.modelsDir, HEAVY_FILE)
        if (heavy.isFile) {
            all += EngineSpec("mp-heavy-gpu", "GPU") {
                MediaPipePoseEstimators.create(it, RunningMode.VIDEO, PoseModel.HeavyFromFile(heavy.absolutePath), preferGpu = true)
            }
        }
        all += EngineSpec("mlkit-base", "CPU") { MlKitPoseEstimator() }
        return if (engineFilter.isEmpty()) all else all.filter { it.id in engineFilter }
    }

    /** Los vídeos de `in`, por nombre; sin ninguno, un clip sintético (sin persona: solo comprueba que nada falla). */
    private suspend fun videoFiles(): List<File> {
        val found = layout.inDir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in VIDEO_EXTENSIONS }
            ?.sortedBy { it.name }
            .orEmpty()
            .filter { file -> videoFilter.isEmpty() || videoFilter.any { file.name.contains(it) } }
        if (found.isNotEmpty()) return found
        val file = File(context.cacheDir, "vt-bench-synthetic.mp4")
        note("sin vídeos en ${layout.inDir.absolutePath}: se genera un clip sintético")
        withContext(Dispatchers.IO) {
            SyntheticClipEncoder.encodeWithTimeout(file, SyntheticClipEncoder.Spec("synthetic", fps = 30, seconds = 3, orientationHint = 0, variableRate = false))
        }
        return listOf(file)
    }

    private suspend fun runVideo(video: File, engines: List<EngineSpec>, totals: MutableMap<String, EngineTotals>): JSONObject {
        val info = VideoInfoProbe.probe(video)
        val fps = info.nominalFps ?: info.captureFps ?: 30f
        val recommended = GpuProbe.get(context).recommendedPoseStride
        val stride = if (fps > STRIDE_FPS || recommended == 2) 2 else 1
        val json = JSONObject()
            .put("video", video.name)
            .put("bytes", video.length())
            .put("codificado", "${info.codedWidth}x${info.codedHeight}")
            .put("giro", info.rotationDegrees)
            .put("pantalla", "${info.displayWidth}x${info.displayHeight}")
            .put("duracionUs", info.durationUs)
            .put("mime", info.mime)
            .put("fpsNominal", info.nominalFps?.let { round2(it.toDouble()) } ?: JSONObject.NULL)
            .put("fpsCaptura", info.captureFps?.let { round2(it.toDouble()) } ?: JSONObject.NULL)
            .put("hdr", info.isHdr)
            .put("stride", stride)
        val enginesJson = JSONArray()
        var ok = true
        for (spec in engines) {
            val outcome = try {
                runEngine(video, info, spec, stride, totals.getOrPut(spec.id) { EngineTotals() })
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                note("${video.name} · ${spec.id}: fallo: ${describeError(error)}")
                JSONObject().put("motor", spec.id).put("error", describeError(error)).put("ok", false)
            }
            enginesJson.put(outcome)
            ok = ok && outcome.optBoolean("ok")
        }
        json.put("motores", enginesJson)
        json.put("ok", ok)
        return json
    }

    private suspend fun runEngine(video: File, info: VideoInfo, spec: EngineSpec, stride: Int, totals: EngineTotals): JSONObject {
        note("${video.name} · ${spec.id}: creando")
        val json = JSONObject().put("motor", spec.id).put("delegadoPedido", spec.requestedDelegate)
        val decodeMs = Samples()
        val poseMs = Samples()
        var estimator: PoseEstimator? = null
        var assembler: PoseTrackAssembler? = null
        var posed = 0
        var poseErrors = 0
        var firstError: String? = null
        var lastIndex = -1
        val limitUs = if (maxSeconds > 0) maxSeconds * 1_000_000L else Long.MAX_VALUE
        val wallStart = System.nanoTime()
        try {
            val createStart = System.nanoTime()
            val created = withContext(Dispatchers.Default) { spec.create(context) }
            estimator = created
            json.put("crearMs", round2((System.nanoTime() - createStart) / 1e6))
            note("${video.name} · ${spec.id}: ${created.engine.delegate} (${(created as? PoseEstimatorReport)?.delegateNote ?: "sin nota"}); decodificando")
            VideoFrameSources.open(video).use { source ->
                var lastExitNs = System.nanoTime()
                try {
                    source.decode(
                        fromUs = 0L,
                        targetLongEdge = TARGET_LONG_EDGE,
                        needs = { index, _ -> if (index % stride == 0) FrameNeeds.RGBA else FrameNeeds.SKIP },
                    ) { frame ->
                        val enterNs = System.nanoTime()
                        val decoded = (frame.index - lastIndex).coerceAtLeast(1)
                        decodeMs.add((enterNs - lastExitNs) / 1e6 / decoded)
                        lastIndex = frame.index
                        if (frame.ptsUs > limitUs) throw StopBench()
                        val target = assembler ?: PoseTrackAssembler(
                            mediaId = video.nameWithoutExtension,
                            frameWidth = frame.width,
                            frameHeight = frame.height,
                            rotationApplied = info.rotationDegrees,
                            poseStride = stride,
                            captureFps = info.captureFps,
                            fallbackFps = info.nominalFps,
                        ).also { assembler = it }
                        // El DecodedFrame solo vale aquí dentro: la pose se calcula ahora y se espera su resultado.
                        val poseStartNs = System.nanoTime()
                        try {
                            val poses = created.estimate(RgbaFrame(frame.width, frame.height, checkNotNull(frame.rgba)), frame.ptsUs / 1000L)
                            poseMs.add((System.nanoTime() - poseStartNs) / 1e6)
                            target.add(frame.ptsUs, poses)
                            posed++
                        } catch (cancel: CancellationException) {
                            throw cancel
                        } catch (error: Exception) {
                            poseErrors++
                            if (firstError == null) firstError = describeError(error)
                            if (poseErrors > MAX_POSE_ERRORS) throw error
                        }
                        lastExitNs = System.nanoTime()
                    }
                } catch (stop: StopBench) {
                    note("${video.name} · ${spec.id}: cortado a los $maxSeconds s")
                }
                val report = source as? VideoDecodeReport
                json.put("caminoDecodificacion", report?.path?.name ?: JSONObject.NULL)
                json.put("motivoRespaldo", report?.fallbackReason ?: JSONObject.NULL)
                json.put("detallesDecodificacion", report?.details.orEmpty())
            }
            val built = assembler
            if (built == null || built.frameCount == 0) {
                json.put("error", "ningún fotograma llegó al estimador")
                json.put("ok", false)
                return json
            }
            val finalEngine = created.engine
            val track = built.build(finalEngine)
            val bytes = PoseTrackCodec.encode(track)
            val name = "${video.nameWithoutExtension}.${spec.id}.pose2.json.gz"
            File(layout.outDir, name).writeBytes(bytes)
            val back = PoseTrackCodec.decode(bytes)
            json.put("pose2", name)
            json.put("pose2Bytes", bytes.size)
            json.put("pose2Valida", back != null && back.frames.size == track.frames.size)
            json.put("motor", spec.id)
            json.put("motorInfo", "${finalEngine.id}/${finalEngine.model}/${finalEngine.delegate}")
            json.put("delegado", finalEngine.delegate)
            json.put("notaDelegado", (created as? PoseEstimatorReport)?.delegateNote ?: JSONObject.NULL)
            json.put("fotogramasConPose", built.framesWithAthlete)
            json.put("fotogramasEstimados", built.frameCount)
            json.put("fotogramasDecodificados", lastIndex + 1)
            json.put("dosPosesPct", round2(built.multiPersonFraction * 100.0))
            json.put("multiPersona", built.isMultiPerson)
            json.put("fpsNominalTraza", round2(track.nominalFps.toDouble()))
            json.put("vfr", track.vfr)
            json.put("decodificacionMs", decodeMs.stats(skipFirst = true))
            json.put("poseMs", poseMs.stats(skipFirst = true))
            json.put("errores", poseErrors)
            json.put("primerError", firstError ?: JSONObject.NULL)
            totals.delegates.add(finalEngine.delegate)
            totals.videos++
            totals.posedFrames += built.framesWithAthlete
            totals.errors += poseErrors
            totals.decodeMs.addAll(decodeMs, skipFirst = true)
            totals.poseMs.addAll(poseMs, skipFirst = true)
            // Sin persona en el vídeo (el clip sintético) también es un éxito: lo que se comprueba es que nada falla.
            json.put("ok", poseErrors == 0 && back != null && back.frames.size == track.frames.size)
        } finally {
            runCatching { estimator?.close() }
            json.put("msTotal", round2((System.nanoTime() - wallStart) / 1e6))
            json.put("nativoMb", round2(Debug.getNativeHeapAllocatedSize() / 1048576.0))
            note("${video.name} · ${spec.id}: listo")
        }
        return json
    }

    companion object {
        const val BENCH_FILE = "bench.json"
        const val PROGRESS_FILE = "progress.txt"
        const val HEAVY_FILE = "pose_landmarker_heavy.task"
        private const val TAG = "KpknTechniqueBench"
        private const val TARGET_LONG_EDGE = 720
        private const val STRIDE_FPS = 35f
        private const val MAX_POSE_ERRORS = 20
        private val VIDEO_EXTENSIONS = setOf("mp4", "webm", "mkv", "mov", "3gp", "m4v")
    }
}
