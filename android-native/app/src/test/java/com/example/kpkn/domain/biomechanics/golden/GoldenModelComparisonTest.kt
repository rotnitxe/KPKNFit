package com.example.kpkn.domain.biomechanics.golden

import com.example.kpkn.domain.biomechanics.metrics.CountConfidence
import com.example.kpkn.domain.biomechanics.metrics.MetricKey
import com.example.kpkn.domain.biomechanics.metrics.RepKind
import com.example.kpkn.domain.biomechanics.metrics.SetTechniqueReport
import com.example.kpkn.domain.biomechanics.metrics.TechniqueAnalysisEngine
import com.example.kpkn.domain.biomechanics.metrics.TechniqueAnalysisInput
import com.example.kpkn.domain.biomechanics.pose.PoseTrackCodec
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileResolver
import com.example.kpkn.domain.biomechanics.profiles.ExerciseRef
import com.example.kpkn.domain.biomechanics.profiles.ProfileResolution
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.roundToLong
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Comparación emparejada histórica y opcional: 3 clips vistos con 3 estimadores, nunca 9 muestras independientes. */
class GoldenModelComparisonTest {
    @Test
    fun historicalPoseModelsProducePairedDiagnosticReport() {
        val configured = System.getenv("KPKN_GOLDEN_MODEL_BENCH_DIR")
        assumeTrue("Comparación opcional: configura KPKN_GOLDEN_MODEL_BENCH_DIR", !configured.isNullOrBlank())
        val directory = File(requireNotNull(configured)).canonicalFile
        val results = HistoricalModelBench.replay(directory)
        assertEquals("Se conservan tres clips y tres modelos por clip", 9, results.size)
        assertEquals(3, results.map { it.clipId }.toSet().size)
        assertTrue(results.groupBy { it.clipId }.values.all { rows -> rows.map { it.model }.toSet() == setOf("full", "heavy", "mlkit") })
        val report = HistoricalModelBench.render(results)
        println(report)
        val output = System.getenv("KPKN_GOLDEN_REPORT_DIR")
        if (!output.isNullOrBlank()) File(output).apply { mkdirs() }.resolve("GOLDEN_MODEL_COMPARISON.md").writeText(report, Charsets.UTF_8)
    }
}

private object HistoricalModelBench {
    private val json = Json { ignoreUnknownKeys = true }
    private val clipIds = setOf("r14-pexels-4921641", "r20-pexels-6767732", "x14-commons-squat")
    private val expectedModels = setOf("full", "heavy", "mlkit")
    private val expectedEngines = mapOf("full" to "mp-full-cpu", "heavy" to "mp-heavy-gpu", "mlkit" to "mlkit-base")
    private val expectedPoseModels = mapOf("full" to "full", "heavy" to "heavy", "mlkit" to "base")
    private val expectedEquipment = mapOf("r14-pexels-4921641" to "barbell", "r20-pexels-6767732" to "bodyweight", "x14-commons-squat" to "barbell")

    fun replay(directory: File): List<ModelDiagnostic> {
        require(directory.isDirectory) { "KPKN_GOLDEN_MODEL_BENCH_DIR no es una carpeta: $directory" }
        val manifest = read(File(directory, "manifest.json"))
        require(manifest.getValue("version").jsonPrimitive.int == 1) { "Versión de manifiesto no soportada." }
        val cases = manifest.getValue("cases").jsonArray.map { it.jsonObject }
        require(cases.size == 3 && cases.map { it.text("id") }.toSet() == clipIds) { "El diagnóstico autorizado contiene exactamente r14/r20/x14." }
        val outputs = mutableListOf<ModelDiagnostic>()
        for (case in cases) {
            val id = case.text("id")
            val labelFile = resolve(directory, case.text("labelPath"))
            verifyHash(labelFile, case.text("labelSha256"))
            val label = read(labelFile)
            require(label.text("id") == id && label.getValue("usable").jsonPrimitive.boolean) { "Etiqueta de otro clip o no apta: $id" }
            val profileId = AnalysisProfileId.valueOf(label.text("suggestedProfile"))
            require(profileId.name == case.text("profile")) { "Perfil distinto de la etiqueta revisada: $id" }
            val equipment = case.text("equipmentId")
            require(equipment == expectedEquipment.getValue(id)) { "Equipo distinto del diagnóstico fijado: $id" }
            val resolved = AnalysisProfileResolver.resolve(
                ExerciseRef(null, null, null, equipment, "BILATERAL", label.text("exercise"), true, profileId),
            )
            require(resolved is ProfileResolution.Supported) { "Perfil sin resolver: $id" }
            val profile = resolved.profile
            val truth = label.getValue("reps").jsonObject
            val acceptable = truth.getValue("acceptableRange").jsonArray.map { it.jsonPrimitive.int }
            require(acceptable.size == 2 && acceptable[0] <= acceptable[1]) { "Rango de etiqueta inválido: $id" }
            val active = label.getValue("activeWindowS").jsonArray.map { (it.jsonPrimitive.double * 1_000_000).roundToLong() }
            require(active.size == 2 && active[0] <= active[1]) { "Ventana inválida: $id" }
            val models = case.getValue("models").jsonArray.map { it.jsonObject }
            require(models.size == 3 && models.map { it.text("model") }.toSet() == expectedModels) { "Modelos ausentes o repetidos: $id" }
            var referencePts: List<Long>? = null
            var referenceDimensions: Pair<Int, Int>? = null
            for (model in models) {
                val modelName = model.text("model")
                val engineKey = model.text("engineKey")
                require(engineKey == expectedEngines.getValue(modelName)) { "No duplicar full GPU con respaldo CPU: $id/$modelName" }
                val poseFile = resolve(directory, model.text("posePath"))
                verifyHash(poseFile, model.text("poseSha256"))
                val pose = requireNotNull(PoseTrackCodec.decode(poseFile.readBytes())) { "Códec pose inválido: $poseFile" }
                val expectedPoseId = if (modelName == "mlkit") "mlkit-pose" else "mediapipe-pose"
                require(pose.version == 2 && pose.mediaId == id && pose.engine.id == expectedPoseId && pose.engine.model == expectedPoseModels.getValue(modelName)) { "Traza de otro clip/modelo: $id/$modelName" }
                require(pose.engine.delegate == "CPU") { "La selección histórica fijada es CPU real: $id/$modelName/${pose.engine.delegate}" }
                require(pose.frames.isNotEmpty() && pose.frames.zipWithNext().all { (a, b) -> a.tUs < b.tUs }) { "PTS vacíos/desordenados: $id/$modelName" }
                val pts = pose.frames.map { it.tUs }
                val dimensions = pose.frameWidth to pose.frameHeight
                require(referencePts == null || referencePts == pts) { "Los motores no comparten los mismos PTS: $id" }
                require(referenceDimensions == null || referenceDimensions == dimensions) { "Los motores no comparten geometría de fotograma: $id" }
                referencePts = pts
                referenceDimensions = dimensions
                val benchFile = resolve(directory, model.text("benchPath"))
                verifyHash(benchFile, model.text("benchSha256"))
                val bench = read(benchFile)
                require(bench.getValue("ok").jsonPrimitive.boolean && bench.getValue("maxSegundos").jsonPrimitive.int == 0) { "El banco histórico no acabó o recortó el clip: $benchFile" }
                val videos = bench.getValue("videos").jsonArray.map { it.jsonObject }.filter { it.text("video").substringBeforeLast('.') == id }
                require(videos.size == 1) { "Clip ausente/duplicado en bench.json: $id" }
                val video = videos.single()
                val engineRows = video.getValue("motores").jsonArray.map { it.jsonObject }.filter { it.text("motor") == engineKey }
                require(engineRows.size == 1) { "Motor sin coste propio en el bench elegido: $id/$engineKey" }
                val cost = engineRows.single()
                require(cost.getValue("ok").jsonPrimitive.boolean && cost.getValue("pose2Valida").jsonPrimitive.boolean && cost.text("pose2") == poseFile.name) { "Traza y coste de corridas distintas: $id/$engineKey" }
                require(cost.text("delegado") == pose.engine.delegate && cost.text("motorInfo") == "${pose.engine.id}/${pose.engine.model}/${pose.engine.delegate}") { "Delegado o motor distintos entre traza y bench: $id/$engineKey" }
                val summaries = bench.getValue("resumenPorMotor").jsonArray.map { it.jsonObject }.filter { it.text("motor") == engineKey }
                require(summaries.size == 1) { "Resumen de motor ausente/duplicado: $engineKey" }
                val cropped = pose.copy(frames = pose.frames.filter { it.tUs in active[0]..active[1] })
                require(cropped.frames.isNotEmpty()) { "Ventana activa sin fotogramas: $id/$modelName" }
                val started = System.nanoTime()
                // Comparación SOLO DE POSE: añadir el disco de otro pipeline alteraría la señal y confundiría los estimadores.
                val result = TechniqueAnalysisEngine.analyze(TechniqueAnalysisInput(cropped, null, profile, null, null, emptySet()))
                val replayMs = (System.nanoTime() - started) / 1e6
                require(result.repCount == result.reps.count { it.kind == RepKind.FULL } && result.partialReps == result.reps.count { it.kind == RepKind.PARTIAL } && result.failedReps == result.reps.count { it.kind == RepKind.FAILED }) { "Taxonomía de conteo incoherente: $id/$modelName" }
                outputs += ModelDiagnostic(
                    clipId = id, model = modelName, profile = profile.id.name, labelView = label.optionalText("view"),
                    full = truth.getValue("full").jsonPrimitive.int, partial = truth.getValue("partial").jsonPrimitive.int,
                    failed = truth.getValue("failed").jsonPrimitive.int, range = acceptable[0]..acceptable[1],
                    requestedDelegate = cost.text("delegadoPedido"), actualDelegate = pose.engine.delegate,
                    delegateNote = cost.optionalText("notaDelegado"), frames = pose.frames.size, activeFrames = cropped.frames.size,
                    poseCost = cost.getValue("poseMs").jsonObject, decodeCost = cost.getValue("decodificacionMs").jsonObject,
                    modelSummary = summaries.single(), createMs = cost.number("crearMs"), historicalTotalMs = cost.number("msTotal"),
                    device = bench.text("dispositivo"), api = bench.getValue("api").jsonPrimitive.int, abi = bench.text("abi"),
                    replayMs = replayMs, result = result, benchPath = benchFile.absolutePath, poseSha256 = model.text("poseSha256"),
                )
            }
        }
        return outputs
    }

    fun render(results: List<ModelDiagnostic>): String = buildString {
        appendLine("# VT10 · comparación histórica de estimadores")
        appendLine()
        appendLine("DIAGNÓSTICO, SIN ACEPTACIÓN. Tres clips observados, con nueve reproducciones emparejadas; no son nueve muestras independientes ni prueban independencia entre los tres clips. Motor de análisis actual: ${TechniqueAnalysisEngine.ENGINE_VERSION}. Las poses y costes son del banco histórico VT4b del 14-oct-2026.")
        appendLine("Se recorta activeWindowS sin desplazar PTS y se usan Engine/Resolver reales con el perfil revisado. El replay solo usa pose: no prueba el seguimiento del disco ni la calidad del pipeline completo. x14 ya era un humo observado y no recupera condición de reserva ciega.")
        appendLine()
        appendLine("| Clip | Modelo | Perfil | Etiqueta F/P/f [rango] | Motor F/P/f | REPS disponible | Confianza | Vista etiqueta → motor | Exacto FULL / en rango |")
        appendLine("|---|---|---|---|---|---|---|---|---|")
        for (r in results.sortedWith(compareBy({ it.clipId }, { it.model }))) {
            val available = MetricKey.REPS in r.result.available
            val exact = available && r.result.repCount == r.full
            val correct = available && r.result.repCount in r.range
            appendLine("| ${r.clipId} | ${r.model} | ${r.profile} | ${r.full}/${r.partial}/${r.failed} [${r.range.first},${r.range.last}] | ${r.result.repCount}/${r.result.partialReps}/${r.result.failedReps} | $available | ${r.result.confidence} | ${r.labelView ?: "sin etiqueta"} → ${r.result.view} | $exact / $correct |")
        }
        appendLine()
        appendLine("| Modelo | Clips | Salidas | FULL exacto | Dentro del rango | ALTA correctas / ALTA |")
        appendLine("|---|---:|---:|---:|---:|---| ")
        for ((model, rows) in results.groupBy { it.model }.toSortedMap()) {
            val outputs = rows.filter { MetricKey.REPS in it.result.available }
            val high = outputs.filter { it.result.confidence == CountConfidence.ALTA }
            appendLine("| $model | ${rows.size} | ${outputs.size} | ${outputs.count { it.result.repCount == it.full }}/${outputs.size} | ${outputs.count { it.result.repCount in it.range }}/${outputs.size} | ${high.count { it.result.repCount in it.range }}/${high.size} |")
        }
        appendLine()
        appendLine("| Clip/modelo | Delegado pedido → real | Frames completos/activos | Pose p50/p95 ms | Pose media ms | Decodificación p50 ms | Crear ms | Total histórico ms | Replay del dominio JVM ms |")
        appendLine("|---|---|---|---|---|---|---|---|---|")
        for (r in results.sortedWith(compareBy({ it.clipId }, { it.model }))) appendLine("| ${r.clipId}/${r.model} | ${r.requestedDelegate} → ${r.actualDelegate} | ${r.frames}/${r.activeFrames} | ${fmt(r.poseCost.number("p50"))}/${fmt(r.poseCost.number("p95"))} | ${fmt(r.poseCost.number("media"))} | ${fmt(r.decodeCost.number("p50"))} | ${fmt(r.createMs)} | ${fmt(r.historicalTotalMs)} | ${fmt(r.replayMs)} |")
        appendLine()
        appendLine("Los p50/p95 históricos son los publicados por cada bench, no percentiles calculados sobre los tres p50. La estadística del arnés puede excluir el primer fotograma; crear, primer fotograma y estado de calentamiento difieren. El replay JVM mide solo el dominio actual y no es latencia del estimador ni del teléfono.")
        appendLine()
        appendLine("| Modelo | Motor de benchmark | p50/p95 pose agregado publicado ms | Frames de la estadística | Dispositivo/API/ABI |")
        appendLine("|---|---|---|---:|---|")
        for ((model, rows) in results.groupBy { it.model }.toSortedMap()) {
            val first = rows.first(); val aggregate = first.modelSummary.getValue("poseMs").jsonObject
            appendLine("| $model | ${first.modelSummary.text("motor")} | ${fmt(aggregate.number("p50"))}/${fmt(aggregate.number("p95"))} | ${aggregate.getValue("n").jsonPrimitive.int} | ${first.device}/${first.api}/${first.abi} |")
        }
        appendLine()
        appendLine("Selección fija: full-cpu y mlkit-base de outA; heavy de outB. Full-gpu era respaldo CPU y tenía los mismos bytes que full-cpu; no se duplica. outT conserva duplicados de outB y no aporta observaciones nuevas. El identificador mp-heavy-gpu expresa lo solicitado: el delegado real fue CPU por ${results.first { it.model == "heavy" }.delegateNote ?: "respaldo registrado"}.")
        appendLine()
        appendLine(recommendation(results))
        appendLine()
        appendLine("No se validan GPU real, teléfono, temperatura, consumo, m/s, fases a precisión de fotograma ni generalización por perfil. D1 mantiene MediaPipe full en producción y ML Kit solo en debug; esta prueba no cambia esa decisión. Hace falta comparar más perfiles y sujetos con la misma captura antes de justificar heavy o cambiar el estimador.")
        appendLine()
        appendLine("| Clip/modelo | SHA-256 pose | Bench original |")
        appendLine("|---|---|---|")
        for (r in results.sortedWith(compareBy({ it.clipId }, { it.model }))) appendLine("| ${r.clipId}/${r.model} | ${r.poseSha256} | ${r.benchPath} |")
    }

    private fun recommendation(results: List<ModelDiagnostic>): String {
        val groups = results.groupBy { it.model }
        val full = groups.getValue("full"); val heavy = groups.getValue("heavy"); val mlkit = groups.getValue("mlkit")
        fun output(rows: List<ModelDiagnostic>) = rows.count { MetricKey.REPS in it.result.available }
        fun correct(rows: List<ModelDiagnostic>) = rows.count { MetricKey.REPS in it.result.available && it.result.repCount in it.range }
        fun median(rows: List<ModelDiagnostic>) = rows.first().modelSummary.getValue("poseMs").jsonObject.number("p50")
        val fullMs = median(full); val heavyMs = median(heavy); val mlkitMs = median(mlkit)
        val speed = if (fullMs != null && fullMs > 0 && heavyMs != null && mlkitMs != null) " p50 histórico heavy/full = ${fmt(heavyMs / fullMs)}×; ML Kit/full = ${fmt(mlkitMs / fullMs)}×." else ""
        val quality = if (output(heavy) >= output(full) && correct(heavy) > correct(full)) {
            "Heavy mejora el conteo en rango observado (${correct(heavy)}/3 frente a ${correct(full)}/3), con cobertura ${output(heavy)}/3 frente a ${output(full)}/3; merece ampliar el banco diagnóstico antes de decidir."
        } else {
            "Estos tres clips no muestran una mejora suficiente de heavy para justificar por sí solos sustituir full (rango heavy ${correct(heavy)}/3, full ${correct(full)}/3; salidas ${output(heavy)}/3 y ${output(full)}/3)."
        }
        return "Recomendación provisional: conservar full como valor de producción y heavy como comparación del banco. $quality$speed ML Kit queda como referencia de depuración de coste/conteo, sin decisión de producto basada en n=3."
    }

    private fun resolve(directory: File, text: String): File = File(text).let { if (it.isAbsolute) it else File(directory, text) }.canonicalFile
    private fun read(file: File): JsonObject {
        require(file.isFile) { "Falta archivo del manifiesto histórico: ${file.absolutePath}" }
        return json.parseToJsonElement(file.readText(Charsets.UTF_8).removePrefix("\uFEFF")).jsonObject
    }
    private fun verifyHash(file: File, expected: String) {
        require(file.isFile) { "Falta traza o metadato histórico: ${file.absolutePath}" }
        val hash = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it.toInt() and 255) }
        require(hash == expected) { "El archivo histórico cambió: ${file.absolutePath}" }
    }
    private fun JsonObject.text(key: String): String = getValue(key).jsonPrimitive.content
    private fun JsonObject.optionalText(key: String): String? = get(key)?.jsonPrimitive?.contentOrNull
    private fun JsonObject.number(key: String): Double? = get(key)?.jsonPrimitive?.doubleOrNull
    private fun fmt(value: Double?): String = value?.let { String.format(Locale.ROOT, "%.2f", it) } ?: "—"
}

private data class ModelDiagnostic(
    val clipId: String, val model: String, val profile: String, val labelView: String?,
    val full: Int, val partial: Int, val failed: Int, val range: IntRange,
    val requestedDelegate: String, val actualDelegate: String, val delegateNote: String?,
    val frames: Int, val activeFrames: Int, val poseCost: JsonObject, val decodeCost: JsonObject,
    val modelSummary: JsonObject, val createMs: Double?, val historicalTotalMs: Double?,
    val device: String, val api: Int, val abi: String, val replayMs: Double,
    val result: SetTechniqueReport, val benchPath: String, val poseSha256: String,
)
