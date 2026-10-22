package com.example.kpkn.domain.biomechanics.golden

import com.example.kpkn.domain.biomechanics.metrics.TechniqueAnalysisEngine
import java.io.File
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * M5: informe opcional para paquetes MM-Fit separados por partición. No abre un banco si falta cualquiera de las dos rutas.
 * La selección y el veto al examen se comprueban antes de que FullBankReport lea etiquetas o sidecars.
 *
 * Los cuatro splits de ajuste describen qué fold se retendría. No son validación cruzada: para llamarlos CV hace falta
 * ajustar con tres folds, congelar los parámetros de esa rotación y evaluar el cuarto sin volver a ajustarlos.
 */
internal data class MmfitM5Package(val kind: String, val partition: String, val parentManifestSha256: String, val rows: List<FullBankRow>)

/** Solo inspecciona el manifiesto, antes de abrir etiquetas, trazas o resultados. */
internal fun validateMmfitM5Package(manifest: JsonObject): MmfitM5Package {
    val kind = manifest["package"]?.jsonObject?.s("kind")
    require(kind in setOf("mmfit-m5-tune", "mmfit-m5-control")) { "package.kind M5 no autorizado: $kind" }
    require(manifest.b("validationOnly")) { "M5 exige validationOnly=true" }
    val parentHash = manifest.s("parentManifestSha256")
    require(Regex("[a-f0-9]{64}").matches(parentHash)) { "parentManifestSha256 inválido" }
    val rows = FullBankReport.rows(manifest)
    require(rows.all { !it.exam }) { "M5 no admite filas de examen" }
    val allowedFolds = if (kind == "mmfit-m5-tune") 0..3 else 4..4
    require(rows.all { it.fold in allowedFolds }) { "Paquete $kind con folds fuera de su partición" }
    return MmfitM5Package(kind!!, if (kind == "mmfit-m5-tune") "tune-folds-0-3" else "control-fold-4", parentHash, rows)
}

class FullBankMmfitReportTest {
    @Test fun optionalM5MmfitReport() {
        val directory = System.getenv("KPKN_FULL_BANK_DIR")
        val destination = System.getenv("KPKN_FULL_BANK_OUT")
        assumeTrue("Informe MM-Fit opcional: configura ambas rutas externas", !directory.isNullOrBlank() && !destination.isNullOrBlank())
        require(System.getenv("KPKN_FULL_BANK_EXAM") == "0") { "M5 exige KPKN_FULL_BANK_EXAM=0 explícito" }

        val root = File(requireNotNull(directory))
        val output = File(requireNotNull(destination))
        require(!output.exists() || (output.isDirectory && output.list()?.isEmpty() == true)) {
            "KPKN_FULL_BANK_OUT debe ser una carpeta nueva o vacía"
        }
        val manifestFile = File(root, "manifest.json")
        val manifest = FullBankReport.readObject(manifestFile)
        val pack = validateMmfitM5Package(manifest)
        val rows = pack.rows

        // FullBankReport valida hashes y recibos, conserva el recorte temporal y escribe sus JSON por clip fuera del repo.
        // No usar FullBankReportTest: sus aserciones históricas fijan 475 filas y un clip ajeno a MM-Fit.
        val results = FullBankReport.run(root, output, exam = false)
        require(results.size == rows.size) { "El informe no cubrió el expectedCount del manifiesto" }
        val aggregates = linkedMapOf<String, List<FullBankResult>>()
        aggregates["TOTAL"] = results
        for ((exercise, subset) in results.groupBy { it.row.raw.optional("exercise") ?: it.row.profile }.toSortedMap()) {
            aggregates["exercise/$exercise"] = subset
            for ((fold, foldRows) in subset.groupBy { it.row.fold }.toSortedMap()) {
                aggregates["exercise/$exercise/fold/$fold"] = foldRows
            }
        }
        for ((fold, subset) in results.groupBy { it.row.fold }.toSortedMap()) aggregates["fold/$fold"] = subset
        val statuses = results.groupingBy { it.status }.eachCount().toSortedMap()
        val splits = if (pack.kind == "mmfit-m5-tune") (0..3).map { heldOut ->
            val training = results.filter { it.row.fold != heldOut }
            val holdout = results.filter { it.row.fold == heldOut }
            buildJsonObject {
                put("heldOutFold", heldOut)
                put("trainingRows", training.size)
                put("trainingGroups", training.map { it.row.source to it.row.group }.toSet().size)
                put("holdoutRows", holdout.size)
                put("holdoutGroups", holdout.map { it.row.source to it.row.group }.toSet().size)
                put("holdoutCurrentEngine", FullBankReport.metricsJson(holdout))
            }
        } else emptyList()
        val report = buildJsonObject {
            put("packageKind", pack.kind)
            put("partition", pack.partition)
            put("parentManifestSha256", pack.parentManifestSha256)
            put("validationOnly", true)
            put("exam", false)
            put("manifestSha256", FullBankReport.sha256(manifestFile))
            put("engineVersion", TechniqueAnalysisEngine.ENGINE_VERSION)
            put("expectedCount", rows.size)
            put("processed", results.count { it.processed })
            put("statuses", buildJsonObject { statuses.forEach { (status, count) -> put(status, count) } })
            put("aggregates", buildJsonObject {
                aggregates.forEach { (name, subset) -> put(name, FullBankReport.metricsJson(subset)) }
            })
            put("splitPlanOnly", true)
            put("crossValidationNote", "Cada rotación requiere parámetros ajustados con los otros tres folds y congelados antes de medir el retenido; elegir el cambio más conservador. Este desglose usa un único motor y no demuestra CV.")
            put("foldSplits", JsonArray(splits))
        }
        File(output, "MMFIT_M5_AGREGADOS.json").writeText(FullBankReport.objectText(report))
    }

    /** Contrato sintético: ninguna prueba de esta clase necesita abrir un banco para comprobar el veto al examen. */
    private fun manifest(kind: String = "mmfit-m5-tune", folds: List<Int> = listOf(0, 1, 2),
                         exam: Boolean = false, validationOnly: Boolean = true, expectedCount: Int = folds.size): JsonObject = buildJsonObject {
        put("package", buildJsonObject { put("kind", kind) })
        put("parentManifestSha256", "a".repeat(64))
        put("validationOnly", validationOnly)
        put("expectedCount", expectedCount)
        put("rows", JsonArray(folds.mapIndexed { index, fold -> buildJsonObject {
            put("id", "synthetic_$index")
            put("source", "synthetic")
            put("groupKey", "group_$index")
            put("category", "G1")
            put("profile", "SQUAT")
            put("fold", fold)
            put("exam", exam)
            put("labelSha256", "b".repeat(64))
        } }))
    }

    @Test fun tuneAndControlAcceptDynamicCountsWithinTheirOwnFolds() {
        assertEquals(3, validateMmfitM5Package(manifest()).rows.size)
        assertEquals("tune-folds-0-3", validateMmfitM5Package(manifest()).partition)
        assertEquals(2, validateMmfitM5Package(manifest(kind = "mmfit-m5-control", folds = listOf(4, 4))).rows.size)
    }

    @Test fun examWrongPartitionAndIncompleteManifestAreRejectedBeforeInputs() {
        assertThrows(IllegalArgumentException::class.java) { validateMmfitM5Package(manifest(exam = true)) }
        assertThrows(IllegalArgumentException::class.java) { validateMmfitM5Package(manifest(folds = listOf(0, 4))) }
        assertThrows(IllegalArgumentException::class.java) { validateMmfitM5Package(manifest(kind = "mmfit-m5-control", folds = listOf(3))) }
        assertThrows(IllegalArgumentException::class.java) { validateMmfitM5Package(manifest(kind = "unknown")) }
        assertThrows(IllegalArgumentException::class.java) { validateMmfitM5Package(manifest(validationOnly = false)) }
        assertThrows(IllegalArgumentException::class.java) { validateMmfitM5Package(manifest(expectedCount = 4)) }
    }
}
