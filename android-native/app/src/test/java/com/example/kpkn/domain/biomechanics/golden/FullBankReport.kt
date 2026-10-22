package com.example.kpkn.domain.biomechanics.golden

import com.example.kpkn.domain.biomechanics.bar.BarTrackCodec
import com.example.kpkn.domain.biomechanics.kinematics.ViewKind
import com.example.kpkn.domain.biomechanics.metrics.AnalysisFlag
import com.example.kpkn.domain.biomechanics.metrics.AnalysisTuning
import com.example.kpkn.domain.biomechanics.metrics.CountConfidence
import com.example.kpkn.domain.biomechanics.metrics.MetricKey
import com.example.kpkn.domain.biomechanics.metrics.RepKind
import com.example.kpkn.domain.biomechanics.metrics.SetTechniqueReport
import com.example.kpkn.domain.biomechanics.metrics.TechniqueAnalysisEngine
import com.example.kpkn.domain.biomechanics.metrics.TechniqueAnalysisInput
import com.example.kpkn.domain.biomechanics.pose.PoseTrackCodec
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfile
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileResolver
import com.example.kpkn.domain.biomechanics.profiles.ExerciseRef
import com.example.kpkn.domain.biomechanics.profiles.ProfileResolution
import com.example.kpkn.domain.biomechanics.reps.ConfidenceBreakdown
import com.example.kpkn.domain.biomechanics.reps.CountSeriesBuilder
import com.example.kpkn.domain.biomechanics.reps.CountSeriesResult
import com.example.kpkn.domain.biomechanics.reps.RepConfidence
import com.example.kpkn.domain.biomechanics.reps.RepFlags
import com.example.kpkn.domain.biomechanics.reps.RepSegmenter
import com.example.kpkn.domain.biomechanics.reps.TrackPreparation
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.math.sqrt
import kotlinx.serialization.json.*

/** Optional full-bank tooling. Inputs and clip diagnostics never belong to the repository.
 *
 * Crop BEFORE segmenting, exactly as GoldenReport.evaluate (historical VT10 comparability).
 * Do not infer an output from repCount=0: only MetricKey.REPS establishes a count output.
 * G3 forced-profile transport is published as a PROBE, never as validation of the app resolver.
 */
internal object FullBankReport {
    val json = Json { ignoreUnknownKeys = true; prettyPrint = true; allowSpecialFloatingPointValues = true }
    private val safeId = Regex("[A-Za-z0-9_-]+")
    private val hashPattern = Regex("[a-f0-9]{64}")

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val block = ByteArray(1024 * 1024)
            while (true) {
                val count = input.read(block)
                if (count < 0) break
                digest.update(block, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    fun readObject(file: File): JsonObject = json.parseToJsonElement(file.readText(Charsets.UTF_8).removePrefix("\uFEFF")).jsonObject
    fun objectText(value: JsonElement): String = json.encodeToString(JsonElement.serializer(), value) + "\n"
    fun path(root: File, value: String): File = File(value).let { if (it.isAbsolute) it else File(root, value) }.canonicalFile

    fun rows(manifest: JsonObject): List<FullBankRow> {
        val rows = manifest.getValue("rows").jsonArray.map { FullBankRow(it.jsonObject) }
        require(rows.size == manifest.getValue("expectedCount").jsonPrimitive.int && rows.isNotEmpty()) { "Manifiesto incompleto" }
        require(rows.map { it.id }.toSet().size == rows.size && rows.all { safeId.matches(it.id) }) { "IDs repetidos/inválidos" }
        for (r in rows) {
            require(r.category in setOf("G1", "G2", "G3") && r.fold in 0..4 && r.group.isNotBlank())
            require(hashPattern.matches(r.raw.s("labelSha256"))) { "Hash de etiqueta inválido: ${r.id}" }
            require(r.raw.getValue("exam").jsonPrimitive.booleanOrNull != null) { "exam debe ser booleano" }
        }
        require(rows.groupBy { it.source to it.group }.values.all { group -> group.map { it.exam to it.fold }.toSet().size == 1 }) {
            "Un grupo está partido entre desarrollo/examen o folds"
        }
        if (!manifest.b("validationOnly")) {
            require(rows.size == 1076 && manifest.b("frozenBeforeExtraction")) { "El banco completo debe conservar 1.076 entradas congeladas" }
            require(rows.groupingBy { it.category }.eachCount() == mapOf("G1" to 513, "G2" to 278, "G3" to 285))
        }
        return rows
    }

    /** Selection happens before opening ANY label, sidecar or clip result. EXAM=1 selects only exam. */
    fun select(rows: List<FullBankRow>, exam: Boolean): List<FullBankRow> = rows.filter { it.exam == exam }

    fun run(root: File, output: File, exam: Boolean, tuning: AnalysisTuning = AnalysisTuning.DEFAULT): List<FullBankResult> {
        requireOutsideRepository(output)
        require(!output.canonicalFile.toPath().startsWith(File(root, "traces").canonicalFile.toPath())) { "Salida sobre las trazas" }
        val manifestFile = File(root, "manifest.json")
        val manifestHash = sha256(manifestFile)
        val manifest = readObject(manifestFile)
        File(root, "manifest.sha256").takeIf { it.isFile }?.let {
            require(it.readText().trim().substringBefore(' ') == manifestHash) { "Manifiesto congelado cambiado" }
        }
        val selected = select(rows(manifest), exam)
        require(selected.isNotEmpty()) { "Partición vacía" }
        require(!exam || !manifest.b("validationOnly")) { "El manifiesto histórico no es el examen nuevo" }
        val state = File(root, "extraction-state.json").takeIf { it.isFile }?.let(::readObject)
        require(state == null || state.s("manifestSha256") == manifestHash) { "Estado de otra versión del manifiesto" }
        val receipts = state?.get("clips")?.jsonObject ?: JsonObject(emptyMap())
        // Refuse a second final examination, even when a different output directory is requested.
        if (exam) {
            val marker = File(root, "exam-evaluation.json")
            require(marker.createNewFile()) { "Examen ya iniciado: no repetir ni ajustar con él" }
            marker.writeText(objectText(buildJsonObject {
                put("manifestSha256", manifestHash); put("startedAt", Instant.now().toString()); put("output", output.canonicalPath)
            }))
        }
        output.mkdirs()
        val clipOutput = File(output, if (exam) "exam-clips" else "development-clips").apply { mkdirs() }
        val results = selected.map { row ->
            evaluate(root, row, receipts[row.id]?.jsonObject, manifest.b("validationOnly"), tuning).also { result ->
                File(clipOutput, row.id + ".json").writeText(objectText(clipJson(result, manifestHash)))
            }
        }
        val groups = breakdowns(results)
        val summary = buildJsonObject {
            put("manifestSha256", manifestHash); put("engineVersion", TechniqueAnalysisEngine.ENGINE_VERSION)
            put("partition", if (exam) "exam" else "development"); put("validationOnly", manifest.b("validationOnly"))
            put("selected", results.size); put("processed", results.count { it.processed })
            put("groups", buildJsonObject { groups.forEach { (key, values) -> put(key, metricsJson(values)) } })
        }
        File(output, "summary.json").writeText(objectText(summary))
        File(output, "BANCO_COMPLETO_RESULTADOS.md").writeText(render(results, groups, manifestHash, exam, manifest.b("validationOnly")))
        println("FullBank: ${results.count { it.processed }}/${results.size}; examen=$exam; motor=${TechniqueAnalysisEngine.ENGINE_VERSION}; $output")
        return results
    }

    internal fun requireOutsideRepository(file: File) {
        var ancestor: File? = file.canonicalFile
        while (ancestor != null) {
            require(!File(ancestor, ".git").exists()) { "Los informes por clip deben quedar FUERA del repo: $file" }
            ancestor = ancestor.parentFile
        }
    }

    private fun checked(file: File, expected: String): File {
        require(hashPattern.matches(expected) && file.isFile && sha256(file) == expected) { "Falta/cambió hash: $file" }
        return file
    }

    internal fun evaluate(
        root: File,
        row: FullBankRow,
        receipt: JsonObject?,
        validation: Boolean,
        tuning: AnalysisTuning = AnalysisTuning.DEFAULT,
        includeDiagnostics: Boolean = true,
    ): FullBankResult {
        val labelFile = checked(path(root, row.raw.s("labelPath")), row.raw.s("labelSha256"))
        val label = readObject(labelFile)
        require(label.s("id") == row.id && label.s("suggestedProfile") == row.profile) { "ID/perfil de etiqueta cambiado" }
        require(row.category == if (label.b("usable")) "G1" else if (label.s("suggestedProfile") == "UNSUPPORTED") "G3" else "G2")
        val expected = FullBankTruth.from(label)
        if (!validation && receipt == null) return FullBankResult(row, expected, "MISSING_RECEIPT")
        if (receipt != null && receipt.optional("status") != "complete") return FullBankResult(row, expected, "INCOMPLETE_EXTRACTION")
        fun trace(kind: String, suffix: String, required: Boolean): File? {
            val file = path(root, row.raw.optional(kind + "Path") ?: "traces/${row.id}$suffix")
            val hash = if (validation) row.raw.optional(kind + "Sha256") else receipt?.get("sidecars")?.jsonObject?.get(file.name)?.jsonObject?.optional("sha256")
            if (hash == null) {
                require(!file.exists()) { "Sidecar sin hash: $file" }
                require(!required) { "Falta el hash de $file" }
                return null
            }
            return checked(file, hash)
        }
        val analysisFile = trace("analysis", ".analysis.json", required = true)!!
        val analysis = readObject(analysisFile)
        require(analysis.s("video").substringBeforeLast('.') == row.id && analysis.s("perfil") == row.benchProfile)
        if (analysis.optional("resultado") == "Unsupported") {
            return FullBankResult(row, expected, "UNSUPPORTED", androidOutcome = "Unsupported")
        }
        if (analysis.optional("resultado") != "Done" || !analysis.b("ok")) {
            return FullBankResult(row, expected, "EXTRACTION_FAILED", androidOutcome = analysis.optional("resultado"))
        }
        val poseFile = trace("pose", ".pose2.json.gz", required = true)!!
        val pose = requireNotNull(PoseTrackCodec.decode(poseFile.readBytes())) { "Pose v2 inválida: $poseFile" }
        require(pose.version == 2 && pose.mediaId == row.id && pose.engine.model == "full" && pose.frames.isNotEmpty())
        val bar = trace("bar", ".bar.json.gz", required = false)?.let { file ->
            requireNotNull(BarTrackCodec.decode(file.readBytes())).also {
                require(it.mediaId == row.id && it.version in 1..2 && it.frameWidth == pose.frameWidth && it.frameHeight == pose.frameHeight)
            }
        }
        val profile = resolve(row)
        val window = label["activeWindowS"]?.takeUnless { it is JsonNull }?.jsonArray?.map { (it.jsonPrimitive.double * 1_000_000).roundToLong() }
        require(window == null || (window.size == 2 && window[0] <= window[1]))
        val start = window?.first() ?: pose.frames.first().tUs
        val end = window?.last() ?: pose.frames.last().tUs
        val cropped = pose.copy(frames = pose.frames.filter { it.tUs in start..end })
        val croppedBar = bar?.copy(samples = bar.samples.filter { it.tUs in start..end })
        require(cropped.frames.isNotEmpty()) { "Ventana vacía: ${row.id}" }
        val report = TechniqueAnalysisEngine.analyze(TechniqueAnalysisInput(cropped, croppedBar, profile, null, null,
            emptySet(), tuning = tuning))
        require(report.repCount == report.reps.count { it.kind == RepKind.FULL })
        require(report.partialReps == report.reps.count { it.kind == RepKind.PARTIAL })
        require(report.failedReps == report.reps.count { it.kind == RepKind.FAILED })
        // Reconstruct the unrounded, non-persisted factors through the SAME domain APIs.
        val prepared = TrackPreparation.prepare(cropped, tuning)
        val built = CountSeriesBuilder.build(prepared, croppedBar, profile, tuning)
        val confidence: ConfidenceBreakdown? = if (built is CountSeriesResult.Ok) {
            val segmented = RepSegmenter.segment(built.series, profile, emptySet(), tuning, prepared)
            require(segmented.fullCount == report.repCount && segmented.partialCount == report.partialReps && segmented.failedCount == report.failedReps)
            RepConfidence.score(built.series, segmented, prepared.view, profile, RepFlags.of(prepared, built.series, segmented), tuning).also {
                require(it.level == report.confidence && abs(it.score - report.confidenceScore) <= .000501) { "Desglose distinto del motor: ${row.id}" }
            }
        } else null
        val posed = cropped.frames.filter { it.hasPose }
        return FullBankResult(row, expected, "DONE", report, confidence, prepared.timebase.fs,
            cropped.frames.maxOf { it.posesDetected },
            posed.takeIf { it.isNotEmpty() }?.let { frames -> frames.count { it.posesDetected >= 2 }.toDouble() / frames.size },
            goodFraming(expected, profile), "Done", (built as? CountSeriesResult.Ok)?.series?.source,
            if (includeDiagnostics) fullBankSignalDiagnostics(prepared, built, profile, croppedBar, tuning) else null)
    }

    fun resolve(row: FullBankRow): AnalysisProfile {
        val resolved = AnalysisProfileResolver.resolve(ExerciseRef(null, null, null, row.raw.optional("equipmentId"), "BILATERAL",
            row.raw.optional("exercise") ?: row.benchProfile, true, AnalysisProfileId.valueOf(row.benchProfile)))
        require(resolved is ProfileResolution.Supported)
        return resolved.profile
    }

    fun goodFraming(truth: FullBankTruth, profile: AnalysisProfile): Boolean =
        truth.fullBodyVisible && truth.people == 1 && ViewKind.entries.any { it.name == truth.view && it in profile.countViews }

    fun fpsBand(fs: Double?): String = when {
        fs == null || !fs.isFinite() || fs <= 0 -> "unknown"
        fs < 20 -> "<20"
        fs < 30 -> "20-<30"
        fs < 50 -> "30-<50"
        else -> ">=50"
    }

    /** Wilson score interval with z=1.959963984540054; no fictitious 0% precision for n=0. */
    fun wilson(success: Int, n: Int): Pair<Double, Double>? {
        require(n >= 0 && success in 0..n)
        if (n == 0) return null
        val z = 1.959963984540054
        val p = success.toDouble() / n
        val z2 = z * z
        val center = (p + z2 / (2 * n)) / (1 + z2 / n)
        val radius = z * sqrt(p * (1 - p) / n + z2 / (4 * n.toDouble() * n)) / (1 + z2 / n)
        return (center - radius).coerceAtLeast(0.0) to (center + radius).coerceAtMost(1.0)
    }

    private fun proportion(success: Int, n: Int): JsonObject = buildJsonObject {
        put("numerator", success); put("denominator", n)
        put("value", if (n == 0) JsonNull else JsonPrimitive(success.toDouble() / n))
        put("wilson95", wilson(success, n)?.let { JsonArray(listOf(JsonPrimitive(it.first), JsonPrimitive(it.second))) } ?: JsonNull)
    }

    fun metricsJson(rows: List<FullBankResult>): JsonObject {
        val known = rows.filter { it.processed }
        val output = known.filter { it.hasOutput }
        val offered = output.filter { it.offered }
        val wrong = offered.count { !it.inRange }
        return buildJsonObject {
            put("n", rows.size); put("processed", known.size); put("missingOrFailed", rows.size - known.size)
            put("sourceGroups", rows.map { it.row.source to it.row.group }.toSet().size)
            put("output", proportion(output.size, known.size))
            put("exactFull", proportion(output.count { it.exact }, output.size))
            put("exactSeries", proportion(output.count { it.exactSeries }, output.size))
            put("inRange", proportion(output.count { it.inRange }, output.size))
            put("maeFull", output.takeIf { it.isNotEmpty() }?.map { abs(it.report!!.repCount - it.truth.full).toDouble() }?.average()?.let(::JsonPrimitive) ?: JsonNull)
            put("maeRange", output.takeIf { it.isNotEmpty() }?.map { it.rangeError.toDouble() }?.average()?.let(::JsonPrimitive) ?: JsonNull)
            put("offered", proportion(offered.size, known.size))
            put("wrongOffered", proportion(wrong, known.size))
            put("wrongAmongOffered", proportion(wrong, offered.size))
            put("levels", buildJsonObject {
                for (level in listOf("ALTA", "MEDIA", "BAJA", "SIN_SALIDA")) {
                    val levelRows = known.filter { it.level == level }
                    put(level, buildJsonObject {
                        put("distribution", proportion(levelRows.size, known.size))
                        put("exact", proportion(levelRows.count { it.hasOutput && it.exact }, levelRows.count { it.hasOutput }))
                        put("inRange", proportion(levelRows.count { it.hasOutput && it.inRange }, levelRows.count { it.hasOutput }))
                    })
                }
            })
            val walking = known.filter { it.truth.walking }
            put("walkingNoReps", proportion(walking.count { !it.hasOutput && (it.report == null || it.report.reps.isEmpty()) }, walking.size))
            val g3 = known.filter { it.row.category == "G3" }
            put("g3Offered", proportion(g3.count { it.offered }, g3.size))
            put("g3ResolverValidated", g3.isNotEmpty() && g3.none { it.row.probe })
        }
    }

    fun breakdowns(results: List<FullBankResult>): Map<String, List<FullBankResult>> = linkedMapOf<String, List<FullBankResult>>().apply {
        put("TOTAL", results)
        put("G1/goodFraming", results.filter { it.row.category == "G1" && it.goodFraming })
        for ((category, rows) in results.groupBy { it.row.category }.toSortedMap()) {
            put(category, rows)
            val dimensions: List<Pair<String, (FullBankResult) -> String>> = listOf(
                "profile" to { it.row.profile }, "view" to { it.truth.view ?: "unknown" },
                "source" to { it.row.source }, "analyzedFps" to { fpsBand(it.fs) },
                "sourceFps" to { fpsBand(it.row.raw["sourceFps"]?.jsonPrimitive?.doubleOrNull) },
                "fold" to { it.row.fold.toString() },
            )
            for ((name, key) in dimensions) for ((value, subset) in rows.groupBy(key).toSortedMap()) put("$category/$name/$value", subset)
        }
        // Preserve external VT10's separated subsets, including its exclusions.
        val external = results.filter { it.row.raw.optional("validationSet") == "vt10-external" }
        if (external.isNotEmpty()) {
            put("VT10/external", external)
            for ((source, subset) in external.groupBy { it.row.source }.toSortedMap()) put("VT10/$source", subset)
            val eligible = external.filter { r ->
                r.row.source != "Kaggle_WorkoutFitness" && !r.truth.uncertain && r.truth.min > 0 &&
                    !r.row.id.startsWith("ucf_v_WallPushups_") && !r.row.id.startsWith("ucf_v_BenchPress_") &&
                    (r.truth.view != "BACK" || ViewKind.BACK in resolve(r.row).countViews)
            }
            val enough = eligible.groupBy { it.row.profile }.values.filter { it.size >= 3 }.flatten()
            for ((tier, subset) in enough.groupBy { resolve(it.row).tier.name }.toSortedMap()) put("VT10/eligibleTier/$tier", subset)
        }
        val tuning = results.filter { it.row.raw.optional("validationSet") == "vt10-tuning" }
        if (tuning.isNotEmpty()) put("VT10/tuning", tuning)
    }

    private fun clipJson(r: FullBankResult, manifestHash: String): JsonObject = buildJsonObject {
        put("id", r.row.id); put("manifestSha256", manifestHash); put("status", r.status)
        put("category", r.row.category); put("source", r.row.source); put("groupKey", r.row.group)
        put("exam", r.row.exam); put("fold", r.row.fold); put("suggestedProfile", r.row.profile)
        put("benchProfile", r.row.benchProfile); put("forcedProfileProbe", r.row.probe)
        put("countSource", r.countSource?.let(::JsonPrimitive) ?: JsonNull)
        put("androidOutcome", r.androidOutcome?.let(::JsonPrimitive) ?: JsonNull)
        put("output", r.report?.let { json.encodeToJsonElement(SetTechniqueReport.serializer(), it) } ?: JsonNull)
        put("hasOutput", r.hasOutput); put("level", r.level); put("offered", r.offered)
        put("exactFull", if (r.hasOutput) JsonPrimitive(r.exact) else JsonNull)
        put("inRange", if (r.hasOutput) JsonPrimitive(r.inRange) else JsonNull)
        put("confidence", r.confidence?.let { c -> buildJsonObject {
            put("c_track", c.cTrack); put("c_snr", c.cSnr); put("c_amp", c.cAmp); put("c_rhy", c.cRhy)
            put("c_view", c.cView); put("n_amb", c.nAmbiguous); put("score", c.score)
            put("forcedLowReason", c.forcedLowReason?.let(::JsonPrimitive) ?: JsonNull)
        } } ?: JsonNull)
        put("confidenceUnavailableReason", if (r.confidence == null) JsonPrimitive(r.report?.noRepsReason ?: r.status) else JsonNull)
        put("fs", r.fs?.let(::JsonPrimitive) ?: JsonNull)
        put("LOW_FPS", r.report?.let { JsonPrimitive(AnalysisFlag.LOW_FPS in it.flags) } ?: JsonNull)
        put("peopleAnnotated", r.truth.people?.let(::JsonPrimitive) ?: JsonNull)
        put("peopleDetectedMax", r.detectedPeople?.let(::JsonPrimitive) ?: JsonNull)
        put("multiPersonFrameFraction", r.multiPersonFraction?.let(::JsonPrimitive) ?: JsonNull)
        put("flags", JsonArray(r.report?.flags.orEmpty().map { JsonPrimitive(it.name) }))
        put("goodFraming", r.goodFraming); put("walkingLunge", r.truth.walking)
        put("signalDiagnostics", r.signalDiagnostics ?: JsonNull)
    }

    fun render(results: List<FullBankResult>, groups: Map<String, List<FullBankResult>>, hash: String, exam: Boolean, validation: Boolean): String = buildString {
        appendLine("# Banco completo · ${if (validation) "validación histórica separada" else if (exam) "EXAMEN" else "desarrollo"}")
        appendLine()
        appendLine("Motor ${TechniqueAnalysisEngine.ENGINE_VERSION}. Manifiesto SHA-256 `$hash`.")
        appendLine("Procesados ${results.count { it.processed }}/${results.size}. Fallos/faltantes se separan: nunca son abstenciones correctas.")
        appendLine("Política temporal: recorte activeWindowS antes del motor, idéntico a GoldenReport. Exacto FULL compara el punto; serie exacta compara F/P/f. MAE usa FULL, solo salidas REPS. Sin salida no acierta ni siquiera un rango que contiene cero.")
        appendLine("Ofrecido = REPS y ALTA/MEDIA. Equivocado = ofrecido fuera del rango. Encuadre bueno = G1, vista etiquetada admitida por countViews, cuerpo entero y una persona anotada.")
        appendLine("Todas las proporciones llevan Wilson bilateral 95%; 0/0 es no estimable. Son intervalos descriptivos por clip, no prueban independencia entre clips del mismo grupo ni entre fuentes.")
        appendLine("G3 con forcedProfileProbe usa SQUAT solo para obtener trazas: su tasa es de estrés bajo perfil forzado. Rechazo real del resolver/UI: NOT_VALIDATED; AnalyzeSpec no acepta UNSUPPORTED. Los componentes sin señal son null, nunca ceros inventados.")
        appendLine()
        appendLine("| Subconjunto | n / grupos | Procesados | Salidas | FULL exacto | Serie F/P/f exacta | Rango | MAE FULL | Ofrecido | Ofrecido equivocado / procesados | Equivocado / ofrecidos |")
        appendLine("|---|---:|---:|---|---|---|---|---:|---|---|---|")
        for ((key, rows) in groups) {
            val m = metricsJson(rows)
            appendLine("| $key | ${rows.size} / ${m["sourceGroups"]} | ${m["processed"]} | ${fraction(m, "output")} | ${fraction(m, "exactFull")} | ${fraction(m, "exactSeries")} | ${fraction(m, "inRange")} | ${m["maeFull"]?.jsonPrimitive?.doubleOrNull?.let(::number) ?: "—"} | ${fraction(m, "offered")} | ${fraction(m, "wrongOffered")} | ${fraction(m, "wrongAmongOffered")} |")
        }
        appendLine()
        appendLine("| Subconjunto | Nivel | Reparto | Precisión exacta | Precisión en rango |")
        appendLine("|---|---|---|---|---|")
        for ((key, rows) in groups) for ((level, value) in metricsJson(rows).getValue("levels").jsonObject) {
            val m = value.jsonObject
            appendLine("| $key | $level | ${fraction(m, "distribution")} | ${fraction(m, "exact")} | ${fraction(m, "inRange")} |")
        }
        appendLine()
        for (category in listOf("G1", "G2", "G3")) {
            val m = metricsJson(results.filter { it.row.category == category })
            appendLine("- $category, zancada caminando sin reps: ${fraction(m, "walkingNoReps")}; G3 número ofrecido: ${fraction(m, "g3Offered")}; resolver G3 validado: ${m["g3ResolverValidated"]}.")
        }
        appendLine()
        appendLine("Los JSON por clip y summary.json están solo en esta carpeta externa. No se ajustaron el motor ni las etiquetas. Este informe no mide m/s, fases por fotograma, calor ni latencia del teléfono.")
    }

    private fun fraction(obj: JsonObject, key: String): String {
        val p = obj.getValue(key).jsonObject
        val n = p.getValue("numerator").jsonPrimitive.int
        val d = p.getValue("denominator").jsonPrimitive.int
        val bounds = wilson(n, d) ?: return "$n/$d (no estimable)"
        return "$n/$d (${number(100.0 * n / d)}%; IC95 ${number(100 * bounds.first)}–${number(100 * bounds.second)}%)"
    }
    private fun number(x: Double) = String.format(Locale.ROOT, "%.2f", x)
}

internal data class FullBankRow(val raw: JsonObject) {
    val id get() = raw.s("id")
    val source get() = raw.s("source")
    val group get() = raw.s("groupKey")
    val category get() = raw.s("category")
    val profile get() = raw.s("profile")
    val benchProfile get() = raw.optional("benchProfile") ?: profile
    val exam get() = raw.getValue("exam").jsonPrimitive.boolean
    val fold get() = raw.getValue("fold").jsonPrimitive.int
    val probe get() = raw.b("forcedProfileProbe")
}

internal data class FullBankTruth(
    val full: Int, val partial: Int, val failed: Int, val min: Int, val max: Int,
    val view: String?, val people: Int?, val fullBodyVisible: Boolean, val uncertain: Boolean, val walking: Boolean,
) {
    companion object {
        fun from(obj: JsonObject): FullBankTruth {
            val reps = obj.getValue("reps").jsonObject
            val range = reps.getValue("acceptableRange").jsonArray.map { it.jsonPrimitive.int }
            require(range.size == 2 && range[0] >= 0 && range[0] <= range[1])
            val reasons = obj["unusableReasons"]?.jsonArray.orEmpty().joinToString(" ") { it.jsonPrimitive.content }.lowercase(Locale.ROOT)
            val exercise = obj.optional("exercise").orEmpty().lowercase(Locale.ROOT)
            return FullBankTruth(reps.getValue("full").jsonPrimitive.int, reps.getValue("partial").jsonPrimitive.int,
                reps.getValue("failed").jsonPrimitive.int, range[0], range[1], obj.optional("view"),
                obj["people"]?.jsonPrimitive?.intOrNull, obj.b("fullBodyVisible"), reps.b("uncertain"),
                obj.s("suggestedProfile") == "LUNGE" && ("caminando" in reasons || "caminando" in exercise || "walking" in reasons || "walking" in exercise))
        }
    }
}

internal data class FullBankResult(
    val row: FullBankRow, val truth: FullBankTruth, val status: String,
    val report: SetTechniqueReport? = null, val confidence: ConfidenceBreakdown? = null, val fs: Double? = null,
    val detectedPeople: Int? = null, val multiPersonFraction: Double? = null, val goodFraming: Boolean = false,
    val androidOutcome: String? = null,
    val countSource: String? = null,
    val signalDiagnostics: JsonObject? = null,
) {
    val processed get() = status == "DONE" || status == "UNSUPPORTED"
    val hasOutput get() = report?.available?.contains(MetricKey.REPS) == true
    val level get() = if (hasOutput) report!!.confidence.name else "SIN_SALIDA"
    val offered get() = hasOutput && report!!.confidence in setOf(CountConfidence.ALTA, CountConfidence.MEDIA)
    val exact get() = hasOutput && report!!.repCount == truth.full
    val exactSeries get() = exact && report!!.partialReps == truth.partial && report.failedReps == truth.failed
    val rangeError get() = report?.repCount?.let { when { it < truth.min -> truth.min - it; it > truth.max -> it - truth.max; else -> 0 } } ?: 0
    val inRange get() = hasOutput && rangeError == 0
}

internal fun JsonObject.s(key: String): String = getValue(key).jsonPrimitive.content
internal fun JsonObject.optional(key: String): String? = get(key)?.jsonPrimitive?.contentOrNull
internal fun JsonObject.b(key: String): Boolean = get(key)?.jsonPrimitive?.booleanOrNull == true
