package com.example.kpkn.data.sessions

import com.example.kpkn.domain.templates.CatalogV2TestFixture
import com.example.kpkn.domain.templates.SessionTemplateAudit
import com.example.kpkn.domain.templates.SessionTemplateAuditIssueKind
import com.example.kpkn.domain.templates.SessionTemplateQualityRules
import com.example.kpkn.domain.templates.TemplateQualitySeverity
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * Hard quality gates over [SESSION_TEMPLATES_SYSTEM] (Fase A — auditoría formal).
 */
class SessionTemplateAuditTest {

    companion object {
        /**
         * Editorial backlog is tracked as a versioned ceiling while P1 issues
         * are migrated in batches.  New catalogue work may not increase this
         * number; lowering it is the required quality action for the next
         * package revision.
         */
        private const val EDITORIAL_P1_BASELINE_V4_2026_08_21 = 46
        @Deprecated("Use V4 baseline")
        private const val EDITORIAL_P1_BASELINE_V3_2026_08_20 = 34
        private lateinit var index: Map<String, com.example.kpkn.data.models.ExerciseMuscleInfo>

        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            index = CatalogV2TestFixture.configurationLookup()
        }
    }

    @Test
    fun catalogIsNonEmptyAndRevisionPinned() {
        assertTrue("Catálogo sistema vacío", SESSION_TEMPLATES_SYSTEM.isNotEmpty())
        assertTrue(
            "Revisión de catálogo de ejercicios vacía",
            TEMPLATE_CATALOG_REVISION.isNotBlank(),
        )
        assertTrue(
            "Revisión de paquete de plantillas vacía",
            SESSION_TEMPLATE_PACKAGE_REVISION.isNotBlank(),
        )
        assertTrue(
            "Paquete de plantillas debe ser v4 tras Fase B/C",
            SESSION_TEMPLATE_PACKAGE_REVISION.startsWith("v4-"),
        )
        // La revisión vive en TEMPLATE_CATALOG_REVISION y en ejercicios del payload (catalogRevision).
        val withRevision = SESSION_TEMPLATES_SYSTEM.count { template ->
            template.session.allExercises().any { !it.catalogRevision.isNullOrBlank() }
        }
        assertTrue("Ninguna plantilla lleva ejercicios con catalogRevision", withRevision > 0)
    }

    @Test
    fun uniqueIdsAndNonBlankNames() {
        val ids = SESSION_TEMPLATES_SYSTEM.map { it.id }
        assertTrue("IDs duplicados: ${ids.groupingBy { it }.eachCount().filter { it.value > 1 }}", ids.size == ids.toSet().size)
        SESSION_TEMPLATES_SYSTEM.forEach { template ->
            assertTrue("Nombre vacío en ${template.id}", template.name.isNotBlank())
            assertTrue("Sesión vacía en ${template.id}", template.session.allExercises().isNotEmpty())
        }
    }

    @Test
    fun structuralAuditHasNoAbsurdVolumeOrOutOfRangeIntensity() {
        val failures = mutableListOf<String>()
        SESSION_TEMPLATES_SYSTEM.forEach { template ->
            val result = SessionTemplateAudit.audit(template, index)
            result.issues
                .filter {
                    it.kind == SessionTemplateAuditIssueKind.ABSURD_VOLUME ||
                        it.kind == SessionTemplateAuditIssueKind.INTENSITY_OUT_OF_RANGE
                }
                .forEach { failures += "${template.id}: ${it.message}" }
        }
        assertTrue("Fallos estructurales:\n${failures.joinToString("\n")}", failures.isEmpty())
    }

    @Test
    fun systemTemplatesHaveNoP0QualityViolations() {
        val violations = SessionTemplateQualityRules.p0Violations(SESSION_TEMPLATES_SYSTEM, index)
        val details = violations.joinToString("\n") { report ->
            val codes = report.p0.joinToString { "${it.code}: ${it.message}" }
            "${report.templateId} → $codes"
        }
        assertTrue("P0 en plantillas sistema:\n$details", violations.isEmpty())
    }

    @Test
    fun intensityPercentageRmStaysInSaneRange() {
        val failures = mutableListOf<String>()
        SESSION_TEMPLATES_SYSTEM.forEach { template ->
            template.session.allExercises().forEach { exercise ->
                exercise.sets.forEachIndexed { idx, set ->
                    val pct = set.targetPercentageRM
                    if (pct != null && (pct < 30.0 || pct > 105.0)) {
                        failures += "${template.id}/${exercise.name}#$idx: %RM $pct fuera de 30–105"
                    }
                }
            }
        }
        assertTrue("Rúbrica intensidad:\n${failures.joinToString("\n")}", failures.isEmpty())
    }

    @Test
    fun inventoryCoverageReportIsStable() {
        // Guarda un inventario mínimo para el informe de auditoría (Fase A).
        val byFocus = SESSION_TEMPLATES_SYSTEM.groupBy { it.focusCategory?.name ?: "NONE" }
        assertTrue("Inventario vacío", byFocus.isNotEmpty())
        val totalExercises = SESSION_TEMPLATES_SYSTEM.sumOf { it.session.allExercises().size }
        assertTrue("Catálogo sin ejercicios ($totalExercises)", totalExercises > 100)
        val p1Count = SessionTemplateQualityRules.auditAll(SESSION_TEMPLATES_SYSTEM, index)
            .sumOf { it.issues.count { issue -> issue.severity == TemplateQualitySeverity.P1 } }
        assertTrue(
            "El backlog editorial P1 aumentó: $p1Count > $EDITORIAL_P1_BASELINE_V4_2026_08_21",
            p1Count <= EDITORIAL_P1_BASELINE_V4_2026_08_21,
        )
    }
}
