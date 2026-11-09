package com.example.kpkn.domain.training.importer

import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2Loader
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Stable labeled benchmark. Extend it with reviewed examples; do not add aliases to make these rows pass. */
class ExerciseNameMatcherBenchmarkTest {

    private data class Example(
        val id: String,
        val input: String,
        val expectedDefinitionId: String?,
        val expectedConfigurationId: String?,
        val label: String,
    )

    @Test
    fun `benchmark separa cobertura precision asignaciones automaticas y falsos positivos`() {
        val catalogFile = listOf(
            File("src/main/assets/exercise_catalog_v2.json"),
            File("app/src/main/assets/exercise_catalog_v2.json"),
        ).first { it.exists() }
        val catalog = ExerciseCatalogV2Loader.decodeApproved(catalogFile.readText())
        val matcher = ExerciseNameMatcher(catalog)
        val definitionByConfigurationId = catalog.families.flatMap { family ->
            family.definitions.flatMap { definition ->
                definition.configurations.map { configuration -> configuration.id to definition.id }
            }
        }.toMap()
        val examples = requireNotNull(javaClass.classLoader?.getResourceAsStream("importer/matcher-benchmark.csv"))
            .bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.drop(1).filter(String::isNotBlank).map { line ->
                    val columns = line.split('\t')
                    require(columns.size == 5) { "Fila inválida del benchmark: $line" }
                    Example(columns[0], columns[1], columns[2].ifBlank { null }, columns[3].ifBlank { null }, columns[4])
                }.toList()
            }
        val results = examples.map { it to matcher.match(it.input) }
        val suggestionCoverage = results.count { (_, match) -> match.candidates.isNotEmpty() }
        val unique = results.filter { (example, _) -> example.label == "UNIQUE" }
        val top1Correct = unique.count { (example, match) -> match.best?.configurationId == example.expectedConfigurationId }
        val top1DefinitionCorrect = unique.count { (example, match) ->
            match.best?.configurationId?.let(definitionByConfigurationId::get) == example.expectedDefinitionId
        }
        val autoResolved = results.count { (example, match) -> matcher.canResolveAutomatically(match) }
        val falseAssignments = results.count { (example, match) ->
            matcher.canResolveAutomatically(match) &&
                (example.label != "UNIQUE" || match.best?.configurationId != example.expectedConfigurationId)
        }
        val ambiguousAuto = results.count { (example, match) -> example.label == "AMBIGUOUS" && matcher.canResolveAutomatically(match) }
        val equipmentConflictAuto = results.count { (example, match) ->
            example.label == "INCOMPATIBLE_EQUIPMENT" && matcher.canResolveAutomatically(match)
        }
        val report = """
            suggestions=${suggestionCoverage}/${examples.size}
            unique_configuration_top1=${top1Correct}/${unique.size}
            unique_definition_top1=${top1DefinitionCorrect}/${unique.size}
            automatic_assignments=$autoResolved/${examples.size}
            false_assignments=$falseAssignments
            ambiguous_auto=$ambiguousAuto
            incompatible_equipment_auto=$equipmentConflictAuto
        """.trimIndent()
        println("IMPORT_MATCHER_BENCHMARK\n$report")

        assertTrue("El benchmark debe cubrir ejemplos con sugerencias: $report", suggestionCoverage >= 6)
        assertEquals("Las coincidencias únicas etiquetadas deben apuntar a su configuración: $report", unique.size, top1Correct)
        assertEquals("Las configuraciones únicas deben pertenecer a su definición etiquetada: $report", unique.size, top1DefinitionCorrect)
        assertEquals("Una variante ambigua no se asigna sin revisar: $report", 0, ambiguousAuto)
        assertEquals("El conflicto de equipo no se asigna solo: $report", 0, equipmentConflictAuto)
        assertEquals("No se permiten asignaciones automáticas equivocadas: $report", 0, falseAssignments)
        assertTrue("El fixture debe conservar las clases acordadas", examples.map { it.label }.toSet().containsAll(
            setOf("UNIQUE", "AMBIGUOUS", "INCOMPATIBLE_EQUIPMENT", "OCR_REVIEW", "UNMATCHED"),
        ))
    }
}
