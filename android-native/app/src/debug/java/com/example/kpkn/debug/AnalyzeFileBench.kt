package com.example.kpkn.debug

import android.content.Context
import android.os.Build
import android.os.Debug
import android.util.Log
import com.example.kpkn.data.media.pose.MediaPipePoseEstimators
import com.example.kpkn.data.media.pose.PoseEstimator
import com.example.kpkn.data.media.pose.PoseModel
import com.example.kpkn.data.media.technique.AnalysisMode
import com.example.kpkn.data.media.technique.PipelineDependencies
import com.example.kpkn.data.media.technique.PipelineRunStats
import com.example.kpkn.data.media.technique.PipelineTuning
import com.example.kpkn.data.media.technique.TechniqueAnalysisOutcome
import com.example.kpkn.data.media.technique.TechniqueAnalysisPipeline
import com.example.kpkn.data.media.technique.TechniqueAnalysisRequest
import com.example.kpkn.data.media.technique.TechniqueSidecars
import com.example.kpkn.domain.biomechanics.metrics.SetTechniqueReport
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.domain.biomechanics.profiles.ExerciseRef
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject

/** Un vídeo del banco y con qué ejercicio se analiza: el perfil que se fuerza y el material (decide si se sigue el disco). */
internal class AnalyzeSpec(val key: String, val profile: AnalysisProfileId, val equipmentId: String?) {
    companion object {
        /**
         * `r14=SQUAT/barbell,x14=SQUAT/barbell,r20=HIP_THRUST/none`: cada entrada es un texto que ha de contener el nombre del
         * vídeo, el perfil y, tras la barra, el material del catálogo (`barbell`, `dumbbells`… o `none`). Una entrada que no se
         * entiende se ignora.
         */
        fun parse(text: String?): List<AnalyzeSpec> = text.orEmpty().split(',').mapNotNull { entry ->
            val key = entry.substringBefore('=', "").trim()
            val rest = entry.substringAfter('=', "").trim()
            if (key.isEmpty() || rest.isEmpty()) return@mapNotNull null
            val profile = AnalysisProfileId.entries.firstOrNull { it.name == rest.substringBefore('/').trim().uppercase() }
                ?: return@mapNotNull null
            val equipment = rest.substringAfter('/', "").trim().lowercase().takeIf { it.isNotEmpty() && it != "none" }
            AnalyzeSpec(key, profile, equipment)
        }
    }
}

/**
 * SOLO DEPURACIÓN (no se integra): modo `analyze_file` de [TechniqueBenchActivity] (VT4c). Analiza cada vídeo de [BenchLayout.inDir] con
 * el pipeline REAL (`TechniqueAnalysisPipeline`: decodificación de VT4a, MediaPipe de VT4b, disco de VT6a y motor) tal como lo usa el
 * Worker, en modo FULL y con el ejercicio que dicen [specs] (perfil forzado y material).
 *
 * El vídeo se copia a la caché de la app antes de analizarlo, para que los sidecars no ensucien la carpeta de entrada. Escribe en
 * `out`:
 * - `<vídeo>.analysis.json`: el informe del motor, el resultado (Done, Failed…), el progreso emitido, las cifras de la pasada
 *   (`PipelineRunStats`: camino de decodificación, delegado, semilla automática del disco, ms por fotograma) y la memoria nativa;
 * - `<vídeo>.pose2.json.gz` y `<vídeo>.bar.json.gz`: los sidecars que dejó el pipeline;
 * - `analyze.json` (archivo temporal + renombrado; el viejo se borra al arrancar): el resumen de todos, que es lo que espera `emu_run`.
 *
 * Extras opcionales de la actividad: `--es videos r14,x14` (solo esos), `--es exercises r14=SQUAT/barbell,r20=HIP_THRUST/none`,
 * `--es model heavy` (el modelo heavy de `vt-bench-models`, en vez del full empaquetado), `--ei plateMm 450` y `--ei heightCm 175`.
 */
internal class AnalyzeFileBench(
    private val context: Context,
    private val layout: BenchLayout,
    private val videoFilter: List<String>,
    private val specs: List<AnalyzeSpec>,
    private val useHeavy: Boolean,
    private val plateMm: Int,
    private val heightCm: Double?,
) {
    private val progressFile = File(layout.outDir, PROGRESS_FILE)
    private val json = Json {
        encodeDefaults = true
        allowSpecialFloatingPointValues = true
    }

    private fun note(message: String) {
        Log.i(TAG, message)
        runCatching { progressFile.appendText("${System.currentTimeMillis() % 100_000_000L} $message\n") }
    }

    suspend fun run(): String {
        runCatching { File(layout.outDir, SUMMARY_FILE).delete() }
        runCatching { progressFile.delete() }
        val report = JSONObject()
            .put("modo", "analyze_file")
            .put("dispositivo", "${Build.MANUFACTURER} ${Build.MODEL}")
            .put("api", Build.VERSION.SDK_INT)
            .put("abi", Build.SUPPORTED_ABIS.firstOrNull().orEmpty())
            .put("disposicion", if (layout.flat) "plana" else "anidada")
            .put("modelo", if (useHeavy) "heavy" else "full")
            .put("discoMm", plateMm)
        var allOk = true
        val summary = StringBuilder()
        try {
            val videos = videoFiles()
            val results = JSONArray()
            for (video in videos) {
                val outcome = try {
                    analyze(video)
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (error: Throwable) {
                    note("${video.name}: fallo del banco: ${describeError(error)}")
                    JSONObject().put("video", video.name).put("error", describeError(error)).put("ok", false)
                }
                results.put(outcome)
                allOk = allOk && outcome.optBoolean("ok")
                summary.append(outcome.optString("resumen", video.name)).append(' ')
            }
            report.put("videos", results)
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
            val tmp = File(layout.outDir, "$SUMMARY_FILE.tmp")
            tmp.writeText(report.toString(2))
            check(tmp.renameTo(File(layout.outDir, SUMMARY_FILE))) { "No se pudo renombrar $SUMMARY_FILE" }
            Log.i(TAG, "analyze_file ok=$allOk $summary")
        }
        return "analyze_file ${if (allOk) "OK" else "CON FALLOS"} · $summary"
    }

    private fun videoFiles(): List<File> = layout.inDir.listFiles()
        ?.filter { it.isFile && it.extension.lowercase() in VIDEO_EXTENSIONS }
        ?.sortedBy { it.name }
        .orEmpty()
        .filter { file -> videoFilter.isEmpty() || videoFilter.any { file.name.contains(it) } }

    private suspend fun analyze(video: File): JSONObject {
        val json = JSONObject().put("video", video.name).put("bytes", video.length())
        val spec = specs.firstOrNull { video.name.contains(it.key) }
        if (spec == null) {
            note("${video.name}: sin ejercicio (usa --es exercises ${video.nameWithoutExtension.substringBefore('-')}=PERFIL/material)")
            return json.put("error", "sin ejercicio para este vídeo").put("ok", false).put("resumen", "${video.name}[sin ejercicio]")
        }
        json.put("perfil", spec.profile.name).put("material", spec.equipmentId ?: JSONObject.NULL)

        val work = File(context.cacheDir, "vt-analyze").apply { mkdirs() }
        val copy = File(work, video.name)
        withContext(Dispatchers.IO) {
            TechniqueSidecars.deleteAll(copy)
            video.copyTo(copy, overwrite = true)
        }

        val progress = ArrayList<Int>()
        var stats: PipelineRunStats? = null
        val base = PipelineDependencies.forApp(context)
        val poseFactory: () -> PoseEstimator = if (useHeavy) heavyFactory() else base.createPose
        val deps = base.copy(
            loadMedia = { null },
            createPose = poseFactory,
            onRunStats = { stats = it },
        )
        val pipeline = TechniqueAnalysisPipeline(deps, PipelineTuning())
        val request = TechniqueAnalysisRequest(
            mediaId = copy.nameWithoutExtension,
            videoFile = copy,
            mode = AnalysisMode.FULL,
            exercise = ExerciseRef(
                definitionId = null,
                configurationId = null,
                movementPatternId = null,
                equipmentId = spec.equipmentId,
                laterality = "BILATERAL",
                name = spec.profile.name,
                isCustom = true,
                userProfileOverride = spec.profile,
            ),
            loadKg = null,
            plateDiameterMm = plateMm,
            plateSeed = null,
            userHeightCm = heightCm,
            setHints = emptySet(),
        )
        note("${video.name}: analizando como ${spec.profile} (${spec.equipmentId ?: "sin material"})")
        val started = System.nanoTime()
        val outcome = pipeline.analyze(request) { progress.add(it) }
        val wallMs = (System.nanoTime() - started) / 1e6
        json.put("totalMs", round2(wallMs))
        json.put("progreso", JSONArray(progress))
        json.put("progresoMonotono", progress.zipWithNext().all { (a, b) -> b > a })
        json.put("progresoFinal", progress.lastOrNull() ?: JSONObject.NULL)
        json.put("nativoMb", round2(Debug.getNativeHeapAllocatedSize() / 1048576.0))
        stats?.let { json.put("pasada", statsJson(it)) }

        var ok = false
        var line = "${video.nameWithoutExtension.substringBefore('-')}["
        when (outcome) {
            is TechniqueAnalysisOutcome.Done -> {
                val r = outcome.report
                json.put("resultado", "Done")
                json.put("informe", JSONObject(this.json.encodeToString(SetTechniqueReport.serializer(), r)))
                json.put("resumenInforme", summaryOf(r))
                ok = true
                val s = stats
                val perFrame = if (s != null && s.framesDecoded > 0) s.wallMs.toDouble() / s.framesDecoded else Double.NaN
                line += "${r.repCount} reps (+${r.partialReps}p ${r.failedReps}f) ${r.confidence} ${String.format(Locale.ROOT, "%.0f", wallMs / 1000.0)} s " +
                    "${if (perFrame.isNaN()) "-" else String.format(Locale.ROOT, "%.1f", perFrame)} ms/f ${s?.decodePath ?: "-"} ${s?.poseDelegate ?: "-"} " +
                    "disco=${s?.autoSeed?.let { "semilla" } ?: s?.plateStage}"
            }
            is TechniqueAnalysisOutcome.Failed -> {
                json.put("resultado", "Failed").put("codigo", outcome.errorCode).put("transitorio", outcome.transient)
                line += "FAILED ${outcome.errorCode}"
            }
            is TechniqueAnalysisOutcome.Unsupported -> {
                json.put("resultado", "Unsupported").put("motivo", outcome.reason)
                line += "UNSUPPORTED ${outcome.reason}"
            }
            is TechniqueAnalysisOutcome.NeedsProfile -> {
                json.put("resultado", "NeedsProfile").put("conjetura", outcome.guess?.name ?: JSONObject.NULL)
                line += "NEEDS_PROFILE"
            }
        }
        line += "]"
        json.put("ok", ok).put("resumen", line)

        // Los sidecars que dejó el pipeline, a la carpeta de salida (junto al informe).
        withContext(Dispatchers.IO) {
            val sidecars = listOf(
                TechniqueSidecars.poseFile(copy) to "${video.nameWithoutExtension}${TechniqueSidecars.POSE_SUFFIX}",
                TechniqueSidecars.barFile(copy) to "${video.nameWithoutExtension}${TechniqueSidecars.BAR_SUFFIX}",
            )
            val sizes = JSONObject()
            for ((file, name) in sidecars) {
                if (!file.isFile) continue
                file.copyTo(File(layout.outDir, name), overwrite = true)
                sizes.put(name, file.length())
            }
            json.put("sidecars", sizes)
            json.put("pendienteDeBorrar", TechniqueSidecars.partFile(copy).isFile)
            val tmp = File(layout.outDir, "${video.nameWithoutExtension}.analysis.json.tmp")
            tmp.writeText(json.toString(2))
            check(tmp.renameTo(File(layout.outDir, "${video.nameWithoutExtension}.analysis.json"))) { "No se pudo renombrar el informe" }
            TechniqueSidecars.deleteAll(copy)
            copy.delete()
        }
        note("${video.name}: $line")
        return json
    }

    /** El estimador con el modelo heavy de `models` (que se empuja al dispositivo): falla con claridad si falta. */
    private fun heavyFactory(): () -> PoseEstimator {
        val heavy = File(layout.modelsDir, PoseBench.HEAVY_FILE)
        require(heavy.isFile) { "No existe ${heavy.absolutePath}" }
        return {
            MediaPipePoseEstimators.create(context, RunningMode.VIDEO, PoseModel.HeavyFromFile(heavy.absolutePath), preferGpu = true, numPoses = 2)
        }
    }

    private fun summaryOf(r: SetTechniqueReport): JSONObject = JSONObject()
        .put("repeticiones", r.repCount)
        .put("parciales", r.partialReps)
        .put("fallidas", r.failedReps)
        .put("confianza", r.confidence.name)
        .put("puntuacion", round2(r.confidenceScore))
        .put("vista", r.view.name)
        .put("ladoCercano", r.nearSide ?: JSONObject.NULL)
        .put("perfil", r.profileId.name)
        .put("avisos", JSONArray(r.flags.map { it.name }))
        .put("disponible", JSONArray(r.available.map { it.name }))
        .put("motivoSinReps", r.noRepsReason ?: JSONObject.NULL)
        .put("fpsAnalizado", round2(r.analyzedFps))
        .put("fpsOrigen", r.sourceFps?.let { round2(it) } ?: JSONObject.NULL)
        .put("tutMs", r.tutMs ?: JSONObject.NULL)
        .put("romMedioGrados", r.romMeanDeg?.let { round2(it) } ?: JSONObject.NULL)
        .put("escala", r.scale?.source?.name ?: JSONObject.NULL)

    private fun statsJson(s: PipelineRunStats): JSONObject {
        val framesPerPose = if (s.framesPosed > 0) s.poseMs / s.framesPosed else Double.NaN
        val perFrame = if (s.framesDecoded > 0) s.wallMs.toDouble() / s.framesDecoded else Double.NaN
        return JSONObject()
            .put("modo", s.mode.name)
            .put("fotogramasDecodificados", s.framesDecoded)
            .put("fotogramasConPose", s.framesPosed)
            .put("saltoPose", s.poseStride)
            .put("caminoDecodificacion", s.decodePath?.name ?: JSONObject.NULL)
            .put("detallesDecodificacion", s.decodeDetails)
            .put("motivoRespaldo", s.fallbackReason ?: JSONObject.NULL)
            .put("delegado", s.poseDelegate ?: JSONObject.NULL)
            .put("notaDelegado", s.delegateNote ?: JSONObject.NULL)
            .put("usaDisco", s.usesPlate)
            .put("etapaDisco", s.plateStage)
            .put("semillaAutomatica", s.autoSeed?.let { JSONObject().put("tUs", it.tUs).put("cx", it.cx.toDouble()).put("cy", it.cy.toDouble()).put("r", it.r.toDouble()) } ?: JSONObject.NULL)
            .put("muestrasDisco", s.barSamples)
            .put("intentosDisco", s.plateAttempts)
            .put("reanudadoDesdeUs", s.resumedFromPtsUs ?: JSONObject.NULL)
            .put("totalMs", s.wallMs)
            .put("msPorFotogramaTotal", round2(perFrame))
            .put("msPorFotogramaPose", round2(framesPerPose))
            .put("esperaDecodificacionMs", round2(s.decodeWaitMs))
            .put("poseMsTotal", round2(s.poseMs))
            .put("discoMsTotal", round2(s.plateMs))
            .put("motorMs", s.engineMs)
    }

    private companion object {
        const val TAG = "KpknTechniqueBench"
        const val SUMMARY_FILE = "analyze.json"
        const val PROGRESS_FILE = "progress.txt"
        val VIDEO_EXTENSIONS = setOf("mp4", "webm", "mkv", "mov", "3gp", "m4v")
    }
}
