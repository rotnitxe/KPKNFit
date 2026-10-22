package com.example.kpkn.domain.biomechanics.golden

import com.example.kpkn.domain.biomechanics.metrics.AnalysisTuning
import com.example.kpkn.domain.biomechanics.metrics.TechniqueAnalysisEngine
import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlinx.serialization.json.*

private val M5_GRID = listOf(0.0, 0.5, 0.75, 1.0)
private val M5_LEVELS = listOf("ALTA", "MEDIA", "BAJA", "SIN_SALIDA")
private val M5_CATEGORIES = listOf("G1", "G2", "G3")
private val M5_SHA256_PATTERN = Regex("[a-f0-9]{64}")

/** Compact prediction: no pose series, diagnostic arrays, or FullBankResult is retained between evaluations. */
internal data class M5Prediction(
    val bank: String,
    val id: String,
    val source: String,
    val group: String,
    val fold: Int,
    val category: String,
    val status: String,
    val truthFull: Int,
    val truthPartial: Int,
    val truthFailed: Int,
    val truthMin: Int,
    val truthMax: Int,
    val walking: Boolean,
    val probe: Boolean,
    val processed: Boolean,
    val hasOutput: Boolean,
    val level: String,
    val predictedFull: Int?,
    val predictedPartial: Int?,
    val predictedFailed: Int?,
    val noRepList: Boolean,
) {
    val exactFull: Boolean? get() = if (hasOutput) predictedFull == truthFull else null
    val exactSeries: Boolean? get() = if (hasOutput) {
        predictedFull == truthFull && predictedPartial == truthPartial && predictedFailed == truthFailed
    } else null
    val inRange: Boolean? get() = if (hasOutput) predictedFull?.let { it in truthMin..truthMax } ?: false else null
    val rangeError: Int get() = predictedFull?.let { when { it < truthMin -> truthMin - it; it > truthMax -> it - truthMax; else -> 0 } } ?: 0
    val offered: Boolean get() = hasOutput && level in setOf("ALTA", "MEDIA")

    companion object {
        fun from(bank: String, result: FullBankResult): M5Prediction {
            val report = result.report
            return M5Prediction(bank, result.row.id, result.row.source, result.row.group, result.row.fold,
                result.row.category, result.status, result.truth.full, result.truth.partial, result.truth.failed,
                result.truth.min, result.truth.max, result.truth.walking, result.row.probe, result.processed,
                result.hasOutput, result.level, report?.repCount, report?.partialReps, report?.failedReps,
                report?.reps?.isEmpty() ?: true)
        }
    }

    fun json(): JsonObject = buildJsonObject {
        put("bank", bank); put("id", id); put("source", source); put("groupKey", group); put("fold", fold)
        put("category", category); put("status", status); put("truthFull", truthFull)
        put("predictedFull", predictedFull?.let(::JsonPrimitive) ?: JsonNull)
        put("exactFull", exactFull?.let(::JsonPrimitive) ?: JsonNull)
        put("exactSeries", exactSeries?.let(::JsonPrimitive) ?: JsonNull)
        put("inRange", inRange?.let(::JsonPrimitive) ?: JsonNull)
        put("confidence", level); put("offered", offered)
    }
}

private data class M5Bank(
    val name: String,
    val root: File,
    val manifestHash: String,
    val manifestExpectedCount: Int,
    val packageKind: String?,
    val packagePartition: String?,
    val parentManifestSha256: String?,
    val selectedRows: List<FullBankRow>,
    val validationOnly: Boolean,
    val receipts: JsonObject,
)

internal data class M5Score(
    val altaWorstBank: Double,
    val mediaWorstBank: Double,
    val offeredExactG1WorstBank: Int,
    val offeredExactG1Total: Int,
)

internal data class M5CandidateAssessment(
    val value: Double,
    val eligible: Boolean,
    val reasons: List<String>,
    val score: M5Score,
    val bankMetrics: Map<String, JsonObject>,
)

internal data class M5Selection(
    val heldOutFold: Int,
    val value: Double,
    val fallbackReason: String?,
    val candidates: List<M5CandidateAssessment>,
)

internal data class M5FoldSplit(
    val heldOutFold: Int,
    val trainingByValue: Map<Double, List<M5Prediction>>,
    val heldOutByValue: Map<Double, List<M5Prediction>>,
)

/** Reject rows that would let a source group influence both sides of a held-out rotation. */
internal fun validateM5NoFoldLeakage(rows: List<M5Prediction>) {
    require(rows.all { it.group.isNotBlank() && it.fold in 0..3 }) { "Grupo/fold M5 inválido" }
    require(rows.groupBy { Triple(it.bank, it.source, it.group) }.values.all { group -> group.map { it.fold }.distinct().size == 1 }) {
        "Un grupo M5 aparece en más de un fold"
    }
}

/** Split first, so the selector receives no held-out prediction or truth. */
internal fun splitM5Predictions(
    predictionsByValue: Map<Double, List<M5Prediction>>,
    heldOutFold: Int,
    grid: List<Double> = M5_GRID,
): M5FoldSplit {
    require(heldOutFold in 0..3)
    require(predictionsByValue.keys == grid.toSet()) { "Rejilla M5 distinta de la predeclarada" }
    val baseline = predictionsByValue.getValue(0.0)
    val baselineKeys = baseline.map { it.bank to it.id }
    require(baselineKeys.size == baselineKeys.toSet().size) { "Predicciones duplicadas en la rejilla" }
    validateM5NoFoldLeakage(baseline)
    for (value in grid) {
        val rows = predictionsByValue.getValue(value)
        require(rows.map { it.bank to it.id }.toSet() == baselineKeys.toSet() && rows.size == baseline.size) {
            "Las variantes M5 no evalúan las mismas filas"
        }
        validateM5NoFoldLeakage(rows)
    }
    val trainGroups = baseline.filter { it.fold != heldOutFold }.map { Triple(it.bank, it.source, it.group) }.toSet()
    val holdoutGroups = baseline.filter { it.fold == heldOutFold }.map { Triple(it.bank, it.source, it.group) }.toSet()
    require(trainGroups.intersect(holdoutGroups).isEmpty()) { "Grupo retenido filtrado al entrenamiento" }
    return M5FoldSplit(heldOutFold,
        predictionsByValue.mapValues { (_, rows) -> rows.filter { it.fold != heldOutFold } },
        predictionsByValue.mapValues { (_, rows) -> rows.filter { it.fold == heldOutFold } })
}

/** The selector only accepts the three training folds. */
internal fun selectM5Candidate(
    trainingByValue: Map<Double, List<M5Prediction>>,
    heldOutFold: Int,
    grid: List<Double> = M5_GRID,
): M5Selection {
    require(heldOutFold in 0..3)
    require(trainingByValue.keys == grid.toSet() && 0.0 in grid) { "Rejilla M5 distinta de la predeclarada" }
    val baseline = trainingByValue.getValue(0.0)
    val baselineKeys = baseline.map { it.bank to it.id }
    require(baselineKeys.size == baselineKeys.toSet().size) { "Predicciones duplicadas en el control de rejilla" }
    require(baseline.all { it.fold != heldOutFold }) { "El selector recibió filas del fold retenido" }
    validateM5NoFoldLeakage(baseline)
    for (value in grid) {
        val rows = trainingByValue.getValue(value)
        require(rows.map { it.bank to it.id }.toSet() == baselineKeys.toSet() && rows.size == baseline.size) {
            "Las variantes M5 no evalúan las mismas filas"
        }
        require(rows.all { it.fold != heldOutFold }) { "El selector recibió filas del fold retenido" }
        validateM5NoFoldLeakage(rows)
    }
    val banks = baseline.map { it.bank }.toSortedSet()
    val baseTraining = baseline

    val assessments = grid.map { value ->
        val training = trainingByValue.getValue(value)
        val reasons = mutableListOf<String>()
        for (bank in banks) for (fold in 0..3) if (fold != heldOutFold) {
            val baseExact = baseTraining.count { it.bank == bank && it.fold == fold && it.category == "G1" && it.offered && it.exactFull == true }
            val candidateExact = training.count { it.bank == bank && it.fold == fold && it.category == "G1" && it.offered && it.exactFull == true }
            val loss = baseExact - candidateExact
            if (loss > 1) reasons += "$bank/fold$fold pierde $loss exactas G1 ofrecidas"
        }
        var g2Evidence = false
        for (bank in banks) {
            val g2 = training.filter { it.bank == bank && it.category == "G2" }
            if (g2.isEmpty()) continue
            val offered = g2.filter { it.offered }
            if (offered.isEmpty()) {
                reasons += "$bank sin ofertas G2: precisión no estimable"
            } else {
                g2Evidence = true
                val wrong = offered.count { it.inRange != true }
                if (wrong.toDouble() / offered.size > 0.10 + 1e-12) reasons += "$bank errores G2 ofrecidos >10%"
            }
        }
        if (!g2Evidence) reasons += "sin evidencia de precisión G2 ofrecida"
        val baselineHasAlta = baseTraining.any { it.category == "G1" && it.offered && it.level == "ALTA" }
        if (baselineHasAlta && training.none { it.category == "G1" && it.offered && it.level == "ALTA" }) {
            reasons += "pierde todas las ofertas ALTA G1 de la base"
        }
        if (training.none { it.category in setOf("G1", "G2") && it.offered }) {
            reasons += "sin ofertas G1/G2: no se permite ganar por abstención total"
        }
        if (training.none { it.category == "G1" && it.offered && it.exactFull == true }) reasons += "sin exactas G1 ofrecidas"

        val byBank = banks.associateWith { bank -> m5Metrics(training.filter { it.bank == bank }) }
        fun worstPrecision(level: String): Double = banks.map { bank ->
            val offers = training.filter { it.bank == bank && it.category == "G1" && it.offered && it.level == level }
            if (offers.isEmpty()) -1.0 else offers.count { it.exactFull == true }.toDouble() / offers.size
        }.minOrNull() ?: -1.0
        val perBankG1Exact = banks.map { bank ->
            training.count { it.bank == bank && it.category == "G1" && it.offered && it.exactFull == true }
        }
        M5CandidateAssessment(value, reasons.isEmpty(), reasons.distinct(), M5Score(
            worstPrecision("ALTA"), worstPrecision("MEDIA"), perBankG1Exact.minOrNull() ?: 0,
            perBankG1Exact.sum()), byBank)
    }
    val eligible = assessments.filter { it.eligible }
    val chosen = eligible.maxWithOrNull(compareBy<M5CandidateAssessment> { it.score.altaWorstBank }
        .thenBy { it.score.mediaWorstBank }
        .thenBy { it.score.offeredExactG1WorstBank }
        .thenBy { it.score.offeredExactG1Total }
        .thenBy { it.value })
    return M5Selection(heldOutFold, chosen?.value ?: 0.0,
        if (chosen == null) "Ningún candidato cumple los criterios; se conserva 0.0" else null, assessments)
}

private fun ratio(success: Int, denominator: Int): JsonObject = buildJsonObject {
    put("numerator", success); put("denominator", denominator)
    put("value", if (denominator == 0) JsonNull else JsonPrimitive(success.toDouble() / denominator))
    put("wilson95", FullBankReport.wilson(success, denominator)?.let { JsonArray(listOf(JsonPrimitive(it.first), JsonPrimitive(it.second))) } ?: JsonNull)
}

private fun targetPrecision(success: Int, denominator: Int, target: Double): JsonObject = buildJsonObject {
    put("numerator", success); put("denominator", denominator); put("target", target)
    put("measured", if (denominator == 0) JsonNull else JsonPrimitive(success.toDouble() / denominator))
    put("wilson95", FullBankReport.wilson(success, denominator)?.let {
        JsonArray(listOf(JsonPrimitive(it.first), JsonPrimitive(it.second)))
    } ?: JsonNull)
    put("status", when {
        denominator == 0 -> "NOT_ESTIMABLE"
        success.toDouble() / denominator >= target -> "MET_IN_CV_PENDING_BLIND"
        else -> "UNMET_IN_CV"
    })
}

/** Mirrors FullBankReport's metric definitions while retaining only scalar predictions. */
private fun m5BaseMetrics(rows: List<M5Prediction>): JsonObject {
    val known = rows.filter { it.processed }
    val output = known.filter { it.hasOutput }
    val offered = output.filter { it.offered }
    val wrong = offered.count { it.inRange != true }
    return buildJsonObject {
        put("n", rows.size); put("processed", known.size); put("missingOrFailed", rows.size - known.size)
        put("sourceGroups", rows.map { it.source to it.group }.toSet().size)
        put("output", ratio(output.size, known.size))
        put("exactFull", ratio(output.count { it.exactFull == true }, output.size))
        put("exactSeries", ratio(output.count { it.exactSeries == true }, output.size))
        put("inRange", ratio(output.count { it.inRange == true }, output.size))
        put("maeFull", output.takeIf { it.isNotEmpty() }?.map { abs(requireNotNull(it.predictedFull) - it.truthFull).toDouble() }?.average()?.let(::JsonPrimitive) ?: JsonNull)
        put("maeRange", output.takeIf { it.isNotEmpty() }?.map { it.rangeError.toDouble() }?.average()?.let(::JsonPrimitive) ?: JsonNull)
        put("offered", ratio(offered.size, known.size))
        put("wrongOffered", ratio(wrong, known.size)); put("wrongAmongOffered", ratio(wrong, offered.size))
        put("levels", buildJsonObject {
            for (level in M5_LEVELS) {
                val levelRows = known.filter { it.level == level }
                put(level, buildJsonObject {
                    put("distribution", ratio(levelRows.size, known.size))
                    put("exact", ratio(levelRows.count { it.hasOutput && it.exactFull == true }, levelRows.count { it.hasOutput }))
                    put("inRange", ratio(levelRows.count { it.hasOutput && it.inRange == true }, levelRows.count { it.hasOutput }))
                })
            }
        })
        val walking = known.filter { it.walking }
        put("walkingNoReps", ratio(walking.count { !it.hasOutput && it.noRepList }, walking.size))
        val g3 = known.filter { it.category == "G3" }
        put("g3Offered", ratio(g3.count { it.offered }, g3.size))
        put("g3ResolverValidated", g3.isNotEmpty() && g3.none { it.probe })
    }
}

internal fun m5Metrics(rows: List<M5Prediction>): JsonObject = buildJsonObject {
    m5BaseMetrics(rows).forEach { (key, value) -> put(key, value) }
    put("byCategory", buildJsonObject {
        for (category in M5_CATEGORIES) put(category, buildJsonObject {
            put("scope", if (category == "G3") "DIAGNOSTIC_NOT_VALIDATED" else "CV_METRICS")
            put("metrics", m5BaseMetrics(rows.filter { it.category == category }))
        })
    })
}

private fun metricsIndex(rows: List<M5Prediction>): JsonObject = buildJsonObject {
    put("overall", m5Metrics(rows))
    put("byFold", buildJsonObject {
        for ((fold, subset) in rows.groupBy { it.fold }.toSortedMap()) put(fold.toString(), m5Metrics(subset))
    })
    put("byConfidence", buildJsonObject {
        for (level in M5_LEVELS) put(level, m5Metrics(rows.filter { it.level == level }))
    })
}

private fun candidateTuning(value: Double): AnalysisTuning = AnalysisTuning.DEFAULT.copy(
    metrics = AnalysisTuning.DEFAULT.metrics.copy(
        countReliability = AnalysisTuning.DEFAULT.metrics.countReliability.copy(initialBoundaryMaxPeriods = value)))

private fun validateManifestReceipt(root: File, manifestHash: String) {
    val receipt = File(root, "manifest.sha256").takeIf { it.isFile } ?: return
    require(receipt.readText(Charsets.UTF_8).trim().substringBefore(' ') == manifestHash) { "Manifiesto congelado cambiado: $root" }
}

internal fun validateM5ExpectedManifestSha256(actual: String, expected: String) {
    require(M5_SHA256_PATTERN.matches(expected)) { "KPKN_M5_LEGACY_MANIFEST_SHA256 debe ser un SHA-256 hexadecimal de 64 caracteres" }
    require(M5_SHA256_PATTERN.matches(actual) && actual == expected) { "El manifiesto legacy no coincide con KPKN_M5_LEGACY_MANIFEST_SHA256" }
}

private fun loadM5Bank(name: String, rootArgument: File, expectedManifestSha256: String? = null): M5Bank {
    val root = rootArgument.canonicalFile
    require(root.isDirectory) { "Falta el banco $name" }
    val manifestFile = File(root, "manifest.json")
    require(manifestFile.isFile) { "Falta manifest.json en $name" }
    val manifestHash = FullBankReport.sha256(manifestFile)
    if (name == "legacy") {
        validateM5ExpectedManifestSha256(manifestHash, requireNotNull(expectedManifestSha256) {
            "KPKN_M5_LEGACY_MANIFEST_SHA256 es obligatorio para el banco legacy"
        })
    }
    validateManifestReceipt(root, manifestHash)
    val manifest = FullBankReport.readObject(manifestFile)
    val allRows = FullBankReport.rows(manifest)
    val manifestExpectedCount = manifest.getValue("expectedCount").jsonPrimitive.int
    val validationOnly = manifest.b("validationOnly")
    var packageKind: String? = null
    var packagePartition: String? = null
    var parentManifestSha256: String? = null
    val selected = if (name == "mmfit") {
        val pkg = manifest["package"]?.jsonObject ?: error("MM-Fit exige metadatos package")
        packageKind = pkg.s("kind")
        packagePartition = pkg.optional("partition")
        require(packageKind == "mmfit-m5-tune") { "La CV solo acepta package.kind mmfit-m5-tune" }
        val parent = manifest.s("parentManifestSha256")
        require(Regex("[a-f0-9]{64}").matches(parent) && pkg.s("parentManifestSha256") == parent) {
            "Hash de origen MM-Fit incoherente"
        }
        parentManifestSha256 = parent
        require(validationOnly && allRows.all { !it.exam && it.fold in 0..3 }) {
            "MM-Fit debe ser validationOnly, folds 0–3, exam=false; control/fold4 rechazado"
        }
        allRows
    } else {
        val legacyPackageKind = manifest["package"]?.jsonObject?.optional("kind")
        require(legacyPackageKind !in setOf("mmfit-m5-tune", "mmfit-m5-control")) {
            "La ruta legacy apunta a un paquete MM-Fit"
        }
        allRows.filter { !it.exam && it.fold in 0..3 }
    }
    require(selected.isNotEmpty()) { "Partición M5 vacía en $name" }
    require(selected.all { !it.exam && it.fold in 0..3 }) { "Fila fuera de folds 0–3 o en examen en $name" }
    require(selected.map { it.fold }.toSet() == (0..3).toSet()) { "$name debe contener filas retenibles en folds 0–3" }
    require(selected.groupBy { it.source to it.group }.values.all { group -> group.map { it.fold }.distinct().size == 1 }) {
        "Grupo repartido entre folds en $name"
    }
    val state = File(root, "extraction-state.json").takeIf { it.isFile }?.let { FullBankReport.readObject(it) }
    require(state == null || state.s("manifestSha256") == manifestHash) { "Estado de otra versión del manifiesto en $name" }
    val stateReceipts = state?.get("clips")?.jsonObject ?: JsonObject(emptyMap())
    val receipts = JsonObject(selected.mapNotNull { row -> stateReceipts[row.id]?.let { row.id to it } }.toMap())
    return M5Bank(name, root, manifestHash, manifestExpectedCount, packageKind, packagePartition,
        parentManifestSha256, selected, validationOnly, receipts)
}

private fun validateOutput(output: File, roots: List<File>) {
    FullBankReport.requireOutsideRepository(output)
    val outPath = output.canonicalFile.toPath()
    for (root in roots) {
        val bankPath = root.canonicalFile.toPath()
        require(!outPath.startsWith(bankPath) && !bankPath.startsWith(outPath)) { "CV_OUT debe quedar fuera de ambos bancos" }
    }
    require(!output.exists() || (output.isDirectory && output.list()?.isEmpty() == true)) {
        "KPKN_M5_CV_OUT debe ser una carpeta nueva o vacía"
    }
}

class FullBankM5CrossValidationTest {
    @Test fun optionalM5CrossValidation() {
        val raw = listOf("KPKN_M5_MMFIT_TUNE_DIR", "KPKN_M5_LEGACY_TUNE_DIR", "KPKN_M5_CV_OUT")
            .map { System.getenv(it) }
        val expectedLegacyManifestSha256 = System.getenv("KPKN_M5_LEGACY_MANIFEST_SHA256")?.trim()
        if (raw.all { it.isNullOrBlank() } && expectedLegacyManifestSha256.isNullOrBlank()) {
            assumeTrue("M5 CV opcional: configura sus rutas y el hash del manifiesto legacy", false)
        }
        require(raw.all { !it.isNullOrBlank() } && !expectedLegacyManifestSha256.isNullOrBlank()) {
            "Configura KPKN_M5_MMFIT_TUNE_DIR, KPKN_M5_LEGACY_TUNE_DIR, KPKN_M5_CV_OUT y KPKN_M5_LEGACY_MANIFEST_SHA256"
        }
        require(M5_SHA256_PATTERN.matches(requireNotNull(expectedLegacyManifestSha256))) {
            "KPKN_M5_LEGACY_MANIFEST_SHA256 debe ser un SHA-256 hexadecimal de 64 caracteres"
        }
        require(System.getenv("KPKN_FULL_BANK_EXAM") == "0") { "M5 CV exige KPKN_FULL_BANK_EXAM=0 explícito" }
        val mmfitRoot = File(requireNotNull(raw[0])).canonicalFile
        val legacyRoot = File(requireNotNull(raw[1])).canonicalFile
        val output = File(requireNotNull(raw[2])).canonicalFile
        require(mmfitRoot != legacyRoot) { "Los dos bancos M5 deben tener rutas diferentes" }
        validateOutput(output, listOf(mmfitRoot, legacyRoot))

        // Read and validate BOTH manifests, partitions, groups and receipts before any label or trace is opened.
        val banks = listOf(loadM5Bank("mmfit", mmfitRoot), loadM5Bank("legacy", legacyRoot, expectedLegacyManifestSha256))
        val byCandidate = M5_GRID.associateWith { mutableListOf<M5Prediction>() }
        for (bank in banks) for (value in M5_GRID) {
            val tuning = candidateTuning(value)
            for (row in bank.selectedRows) {
                val result = FullBankReport.evaluate(bank.root, row, bank.receipts[row.id]?.jsonObject,
                    bank.validationOnly, tuning, includeDiagnostics = false)
                byCandidate.getValue(value).add(M5Prediction.from(bank.name, result))
            }
        }
        require(byCandidate.values.flatten().all { it.processed }) { "CV incompleta: hay filas sin extracción completa" }

        val rotations = (0..3).map { fold ->
            val split = splitM5Predictions(byCandidate, fold)
            val selection = selectM5Candidate(split.trainingByValue, fold)
            val retained = split.heldOutByValue.getValue(selection.value)
            require(retained.isNotEmpty()) { "Fold retenido vacío: $fold" }
            Triple(selection, retained, selection.value)
        }
        val outOfFold = rotations.flatMap { it.second }
        require(outOfFold.map { it.bank to it.id }.toSet().size == outOfFold.size) { "Predicción retenida duplicada" }
        val finalValue = rotations.maxOfOrNull { it.third } ?: 0.0
        val fullCandidateMetrics = buildJsonObject {
            for (value in M5_GRID) put(value.toString(), buildJsonObject {
                put("initialBoundaryMaxPeriods", value)
                put("byBank", buildJsonObject {
                    for (bank in banks) put(bank.name, metricsIndex(byCandidate.getValue(value).filter { it.bank == bank.name }))
                })
            })
        }
        val rotationJson = JsonArray(rotations.map { (selection, retained, _) -> buildJsonObject {
            put("heldOutFold", selection.heldOutFold); put("selectedInitialBoundaryMaxPeriods", selection.value)
            put("fallbackReason", selection.fallbackReason?.let(::JsonPrimitive) ?: JsonNull)
            put("candidates", JsonArray(selection.candidates.map { assessment -> buildJsonObject {
                put("value", assessment.value); put("eligible", assessment.eligible)
                put("reasons", JsonArray(assessment.reasons.map(::JsonPrimitive)))
                put("score", buildJsonObject {
                    put("worstBankAltaExactPrecision", assessment.score.altaWorstBank)
                    put("worstBankMediaExactPrecision", assessment.score.mediaWorstBank)
                    put("worstBankOfferedExactG1", assessment.score.offeredExactG1WorstBank)
                    put("totalOfferedExactG1", assessment.score.offeredExactG1Total)
                })
                put("trainingMetricsByBank", buildJsonObject { assessment.bankMetrics.forEach { (bank, metric) -> put(bank, metric) } })
            } }))
            put("retainedMetricsByBank", buildJsonObject {
                for (bank in banks) put(bank.name, metricsIndex(retained.filter { it.bank == bank.name }))
            })
            put("retainedPredictions", JsonArray(retained.sortedWith(compareBy<M5Prediction> { it.bank }.thenBy { it.id }).map { it.json() }))
        } })
        fun targetByConfidence(level: String, target: Double): JsonObject {
            val g1Heldout = outOfFold.filter { it.category == "G1" && it.offered && it.level == level }
            return buildJsonObject {
                put("category", "G1"); put("confidence", level); put("target", target)
                put("overallAcrossBanks", targetPrecision(g1Heldout.count { it.exactFull == true }, g1Heldout.size, target))
                put("byBank", buildJsonObject {
                    for (bank in banks) {
                        val bankRows = g1Heldout.filter { it.bank == bank.name }
                        put(bank.name, targetPrecision(bankRows.count { it.exactFull == true }, bankRows.size, target))
                    }
                })
            }
        }
        val targetReport = buildJsonObject {
            put("altaExactPrecision", targetByConfidence("ALTA", 0.98))
            put("mediaExactPrecision", targetByConfidence("MEDIA", 0.90))
            put("note", "Las metas describen precisión FULL exacta G1 en predicciones retenidas. MET_IN_CV_PENDING_BLIND requiere todavía el control ciego; esta CV no demuestra generalización estructural ni lo sustituye.")
        }
        val report = buildJsonObject {
            put("schemaVersion", 1); put("protocol", "M5-3fold-train-1fold-heldout")
            put("engineVersion", TechniqueAnalysisEngine.ENGINE_VERSION); put("exam", false)
            put("evidenceStatus", "OPENPOSE_PRELIMINARY_PENDING_MEDIAPIPE")
            put("validationOnly", banks.all { it.validationOnly })
            put("gridInitialBoundaryMaxPeriods", JsonArray(M5_GRID.map(::JsonPrimitive)))
            put("candidateMetricsDescriptiveAllRowsNotForSelection", fullCandidateMetrics)
            put("banks", buildJsonObject { for (bank in banks) put(bank.name, buildJsonObject {
                put("manifestSha256", bank.manifestHash); put("expectedCount", bank.manifestExpectedCount)
                put("sourcePackagePartition", bank.packagePartition?.let(::JsonPrimitive) ?: JsonNull)
                put("validationOnly", bank.validationOnly)
                put("packageKind", bank.packageKind?.let(::JsonPrimitive) ?: JsonNull)
                put("parentManifestSha256", bank.parentManifestSha256?.let(::JsonPrimitive) ?: JsonNull)
                put("partition", if (bank.name == "mmfit") "mmfit-m5-tune-folds-0-3" else "legacy-exam-false-folds-0-3")
                put("selectedFolds", JsonArray(bank.selectedRows.map { it.fold }.distinct().sorted().map(::JsonPrimitive)))
                put("selectedRows", bank.selectedRows.size)
            }) })
            put("rotations", rotationJson); put("retainedOutOfFoldMetricsByBank", buildJsonObject {
                for (bank in banks) put(bank.name, metricsIndex(outOfFold.filter { it.bank == bank.name }))
            })
            put("retainedOutOfFoldPredictions", JsonArray(outOfFold.sortedWith(compareBy<M5Prediction> { it.fold }.thenBy { it.bank }.thenBy { it.id }).map { it.json() }))
            put("finalMostConservativeInitialBoundaryMaxPeriods", finalValue)
            put("targets", targetReport)
            put("interpretation", "Los resultados conservan métricas separadas por banco, fold, categoría y confianza. OpenPose es preliminar mientras se completa MediaPipe. La selección nunca usa el fold retenido ni el control. Esta CV no prueba generalización del diseño estructural y no reemplaza un control ciego independiente.")
        }
        validateOutput(output, listOf(mmfitRoot, legacyRoot))
        require(output.mkdirs() || output.isDirectory) { "No se pudo crear KPKN_M5_CV_OUT" }
        File(output, "M5_CV.json").writeText(FullBankReport.objectText(report), Charsets.UTF_8)
        require(outOfFold.size == banks.sumOf { bank -> bank.selectedRows.count { it.fold in 0..3 } }) {
            "CV incompleta: revisar M5_CV.json"
        }
    }

    private fun synthetic(rows: List<Triple<String, Int, Int>>): List<M5Prediction> = rows.mapIndexed { index, (bank, fold, truth) ->
        M5Prediction(bank, "synthetic_$index", "src", "group_$bank-$index", fold, "G1", "DONE",
            truth, 0, 0, 0, 10, false, false, true, true, "ALTA", truth, 0, 0, false)
    } + rows.mapIndexed { index, (bank, fold, truth) ->
        M5Prediction(bank, "synthetic_g2_$index", "src", "group_g2_$bank-$index", fold, "G2", "DONE",
            truth, 0, 0, 0, 10, false, false, true, true, "ALTA", truth, 0, 0, false)
    }

    @Test fun heldOutTruthDoesNotChangeSelection() {
        val rows = listOf("mmfit", "legacy").flatMap { bank -> (0..3).map { fold -> Triple(bank, fold, 5) } }
        val base = synthetic(rows)
        val candidates = M5_GRID.associateWith { value -> base.map { row ->
            if (row.fold != 2) row else row.copy(predictedFull = when (value) {
                0.0 -> 5
                0.5 -> 4
                0.75 -> 3
                else -> 2
            })
        } }
        val before = selectM5Candidate(splitM5Predictions(candidates, 2).trainingByValue, 2)
        val changedHoldout = candidates.mapValues { (_, predictions) -> predictions.map { row ->
            if (row.fold == 2) row.copy(truthFull = 4) else row
        } }
        val after = selectM5Candidate(splitM5Predictions(changedHoldout, 2).trainingByValue, 2)
        assertEquals(1.0, before.value, 0.0)
        assertEquals(before.value, after.value, 0.0)
    }

    @Test fun groupCannotLeakAcrossFolds() {
        val rows = synthetic(listOf(Triple("mmfit", 0, 5), Triple("mmfit", 1, 5)))
            .mapIndexed { i, row -> row.copy(group = "same-group-$i") }
        val leaked = rows.map { it.copy(group = "same-group") }
        assertThrows(IllegalArgumentException::class.java) { validateM5NoFoldLeakage(leaked) }
        assertThrows(IllegalArgumentException::class.java) {
            splitM5Predictions(M5_GRID.associateWith { leaked.map { row -> row.copy() } }, 0)
        }
    }

    @Test fun scoreTiesChooseMoreConservativeValue() {
        val rows = listOf("mmfit", "legacy").flatMap { bank -> (0..3).map { fold -> Triple(bank, fold, 5) } }
        val same = synthetic(rows)
        val candidates = mapOf(0.0 to same, 0.5 to same.map { it.copy() }, 0.75 to same.map { it.copy() }, 1.0 to same.map { it.copy() })
        assertEquals(1.0, selectM5Candidate(splitM5Predictions(candidates, 3).trainingByValue, 3).value, 0.0)
    }

    @Test fun changingG3RowsDoesNotChangeSelection() {
        val sourceRows = listOf("mmfit", "legacy").flatMap { bank -> (0..3).map { fold -> Triple(bank, fold, 5) } }
        val g3Rows = sourceRows.mapIndexed { index, (bank, fold, truth) ->
            M5Prediction(bank, "synthetic_g3_$index", "src", "group_g3_$bank-$fold", fold, "G3", "PROBE",
                truth, 0, 0, truth, truth, false, true, true, true, "ALTA", truth, 0, 0, false)
        }
        val base = synthetic(sourceRows) + g3Rows
        fun predictions(flipG3: Boolean): Map<Double, List<M5Prediction>> = M5_GRID.associateWith { value ->
            base.map { row ->
                if (row.category != "G3") row else {
                    val exact = if (flipG3) value != 0.0 else value == 0.0
                    row.copy(predictedFull = if (exact) row.truthFull else row.truthFull - 1)
                }
            }
        }
        val before = selectM5Candidate(splitM5Predictions(predictions(flipG3 = false), 3).trainingByValue, 3)
        val after = selectM5Candidate(splitM5Predictions(predictions(flipG3 = true), 3).trainingByValue, 3)
        assertEquals(1.0, before.value, 0.0)
        assertEquals(before.value, after.value, 0.0)
        assertEquals(8, m5Metrics(base).getValue("byCategory").jsonObject.getValue("G3").jsonObject
            .getValue("metrics").jsonObject.getValue("n").jsonPrimitive.int)
    }

    @Test fun zeroDenominatorsStayUnestimatedAndSelectionFallsBack() {
        val metric = m5Metrics(emptyList())
        assertTrue(metric.getValue("exactFull").jsonObject.getValue("value") is JsonNull)
        assertEquals("NOT_ESTIMABLE", targetPrecision(0, 0, 0.98).getValue("status").jsonPrimitive.content)
        val empty = listOf("mmfit", "legacy").flatMap { bank -> (0..3).map { fold ->
            M5Prediction(bank, "$bank-$fold", "src", "group-$bank-$fold", fold, "G1", "DONE", 0, 0, 0,
                0, 0, false, false, true, false, "SIN_SALIDA", null, null, null, true)
        } }
        val selection = selectM5Candidate(splitM5Predictions(
            M5_GRID.associateWith { empty.map { row -> row.copy() } }, 0).trainingByValue, 0)
        assertEquals(0.0, selection.value, 0.0)
        assertTrue(selection.fallbackReason != null)
    }

    @Test fun legacyManifestIdentityMustMatchExpectedSha256() {
        val expected = "a".repeat(64)
        validateM5ExpectedManifestSha256(expected, expected)
        assertThrows(IllegalArgumentException::class.java) {
            validateM5ExpectedManifestSha256("b".repeat(64), expected)
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateM5ExpectedManifestSha256(expected, "A".repeat(64))
        }
    }
}
