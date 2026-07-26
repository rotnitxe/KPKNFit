package com.example.kpkn.domain.templates

import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.sessions.SESSION_TEMPLATES_SYSTEM
import com.example.kpkn.data.sessions.SessionTemplateFocusCategory
import com.example.kpkn.data.splits.Difficulty
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

class TemplateCatalogFilterLogicTest {

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private lateinit var facetsById: Map<String, SessionTemplateFacets>
        private val templates = SESSION_TEMPLATES_SYSTEM

        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            val dbFile = findDbFile("exercise_database.json")
            val aliasesFile = findDbFile("exercise_id_aliases.json")
            val database = json.decodeFromString<List<ExerciseMuscleInfo>>(dbFile.readText())
            val byId = database.associateBy { it.id.lowercase() }
            val aliases = json.decodeFromString<Map<String, String>>(aliasesFile.readText())
                .mapKeys { it.key.lowercase() }
                .mapValues { it.value.lowercase() }
            val merged = byId.toMutableMap()
            aliases.forEach { (alias, canonical) ->
                byId[canonical]?.let { merged[alias] = it }
            }
            facetsById = SessionTemplateFacetsBuilder.buildAll(templates, merged)
        }

        private fun findDbFile(fileName: String): File {
            val resource = TemplateCatalogFilterLogicTest::class.java.classLoader?.getResource(fileName)
            if (resource != null) return File(resource.toURI())
            val candidates = listOf(
                "src/main/assets/$fileName",
                "../app/src/main/assets/$fileName",
                "app/src/main/assets/$fileName",
                "android-native/app/src/main/assets/$fileName",
                "../android-native/app/src/main/assets/$fileName",
            )
            for (path in candidates) {
                val f = File(path)
                if (f.exists()) return f
            }
            error("No se encontró $fileName.")
        }
    }

    @Test
    fun empujeHipertrofiaIntermedioEncuentraPush() {
        val filters = TemplateCatalogFilters(
            sessionType = TemplateSessionType.EMPUJE,
            goal = TemplateSessionGoal.HIPERTROFIA,
            difficulty = Difficulty.INTERMEDIO,
        )
        val result = TemplateCatalogFilterLogic.filterTemplates(templates, facetsById, filters)
        assertTrue("Debe encontrar el push intermedio de hipertrofia: $result", result.isNotEmpty())
        assertTrue("El push (sys-push-ppl) debe estar presente", result.any { it.id == "sys-push-ppl" })

        result.forEach { template ->
            assertTrue(TemplateCatalogFilterLogic.matchesSessionType(template, TemplateSessionType.EMPUJE))
            assertTrue(TemplateCatalogFilterLogic.matchesGoal(template, TemplateSessionGoal.HIPERTROFIA))
            assertEquals(Difficulty.INTERMEDIO, template.difficulty)
        }
    }

    @Test
    fun pushTambienMatcheaPechoPorTagsYfocus() {
        val push = templates.first { it.id == "sys-push-ppl" }
        assertTrue(TemplateCatalogFilterLogic.matchesSessionType(push, TemplateSessionType.EMPUJE))
        assertTrue(TemplateCatalogFilterLogic.matchesSessionType(push, TemplateSessionType.PECHO))
    }

    @Test
    fun tironNoIncluyePush() {
        val result = TemplateCatalogFilterLogic.filterTemplates(
            templates,
            facetsById,
            TemplateCatalogFilters(sessionType = TemplateSessionType.TIRON),
        )
        assertTrue("Debe haber sesiones de tirón", result.isNotEmpty())
        assertFalse(
            "El push no debe aparecer entre las sesiones de tirón",
            result.any { it.id == "sys-push-ppl" },
        )
    }

    @Test
    fun zonaInferiorExcluyePush() {
        val result = TemplateCatalogFilterLogic.filterTemplates(
            templates,
            facetsById,
            TemplateCatalogFilters(zone = TemplateSessionZone.INFERIOR),
        )
        assertFalse(
            "Una sesión de empuje (tren superior) no puede aparecer en zona inferior",
            result.any { it.id == "sys-push-ppl" },
        )
        result.forEach { template ->
            val facets = facetsById.getValue(template.id)
            assertTrue(TemplateCatalogFilterLogic.matchesZone(facets, TemplateSessionZone.INFERIOR))
        }
    }

    @Test
    fun zonaSuperiorIncluyePush() {
        val result = TemplateCatalogFilterLogic.filterTemplates(
            templates,
            facetsById,
            TemplateCatalogFilters(zone = TemplateSessionZone.SUPERIOR),
        )
        assertTrue(
            "El push debe reconocerse como sesión de tren superior",
            result.any { it.id == "sys-push-ppl" },
        )
    }

    @Test
    fun agrupacionPorTipoDeSesionSinDuplicadosInternos() {
        val filtered = TemplateCatalogFilterLogic.filterTemplates(
            templates,
            facetsById,
            TemplateCatalogFilters(),
        )
        val sections = TemplateCatalogFilterLogic.groupTemplates(
            filtered,
            facetsById,
            TemplateGroupMode.SESSION_TYPE,
        )
        assertTrue(sections.isNotEmpty())

        sections.forEach { section ->
            val ids = section.templates.map { it.id }
            assertEquals(
                "Sección '${section.title}' no debe duplicar plantillas",
                ids.distinct().size,
                ids.size,
            )
        }

        // Cobertura: toda plantilla filtrada aparece al menos en una sección.
        val covered = sections.flatMap { it.templates }.map { it.id }.toSet()
        assertEquals(filtered.map { it.id }.toSet(), covered)
    }

    @Test
    fun agrupacionPorObjetivoSinDuplicadosInternos() {
        val filtered = TemplateCatalogFilterLogic.filterTemplates(
            templates,
            facetsById,
            TemplateCatalogFilters(),
        )
        val sections = TemplateCatalogFilterLogic.groupTemplates(
            filtered,
            facetsById,
            TemplateGroupMode.GOAL,
        )
        assertTrue(sections.isNotEmpty())

        sections.forEach { section ->
            val ids = section.templates.map { it.id }
            assertEquals(
                "Sección '${section.title}' no debe duplicar plantillas",
                ids.distinct().size,
                ids.size,
            )
        }

        val covered = sections.flatMap { it.templates }.map { it.id }.toSet()
        assertEquals(filtered.map { it.id }.toSet(), covered)
    }

    @Test
    fun agrupacionPorNivelParticionaSinDuplicados() {
        val filtered = TemplateCatalogFilterLogic.filterTemplates(
            templates,
            facetsById,
            TemplateCatalogFilters(),
        )
        val sections = TemplateCatalogFilterLogic.groupTemplates(
            filtered,
            facetsById,
            TemplateGroupMode.LEVEL,
        )
        assertTrue(sections.isNotEmpty())

        // El nivel es partición: cada plantilla aparece en exactamente una sección.
        val all = sections.flatMap { it.templates.map { t -> t.id } }
        assertEquals("El nivel no debe duplicar plantillas entre secciones", all.distinct().size, all.size)
        assertEquals(filtered.map { it.id }.toSet(), all.toSet())
    }

    @Test
    fun busquedaSeCombinaConFiltrosNoLosOmite() {
        val soloBusqueda = TemplateCatalogFilterLogic.filterTemplates(
            templates,
            facetsById,
            TemplateCatalogFilters(searchQuery = "push"),
        )
        assertTrue(soloBusqueda.isNotEmpty())

        val busquedaMasTiron = TemplateCatalogFilterLogic.filterTemplates(
            templates,
            facetsById,
            TemplateCatalogFilters(
                searchQuery = "push",
                sessionType = TemplateSessionType.TIRON,
            ),
        )
        assertFalse(busquedaMasTiron.any { it.id == "sys-push-ppl" })
    }

    @Test
    fun hasActiveFiltersReflejaEstado() {
        assertFalse(TemplateCatalogFilters().hasActiveFilters)
        assertTrue(TemplateCatalogFilters(sessionType = TemplateSessionType.EMPUJE).hasActiveFilters)
        assertTrue(TemplateCatalogFilters(goal = TemplateSessionGoal.FUERZA).hasActiveFilters)
        assertTrue(TemplateCatalogFilters(zone = TemplateSessionZone.INFERIOR).hasActiveFilters)
        assertTrue(TemplateCatalogFilters(difficulty = Difficulty.AVANZADO).hasActiveFilters)
        assertFalse(TemplateCatalogFilters(searchQuery = "   ").hasActiveFilters)
    }

    @Test
    fun labelsSonSobrios() {
        assertEquals("Todas", TemplateSessionType.ALL.label)
        assertEquals("Empuje", TemplateSessionType.EMPUJE.label)
        assertEquals("Tirón", TemplateSessionType.TIRON.label)
        assertEquals("Piernas", TemplateSessionType.PIERNAS.label)
        assertEquals("Full body", TemplateSessionType.FULL_BODY.label)

        assertEquals("Todos", TemplateSessionGoal.ALL.label)
        assertEquals("Hipertrofia", TemplateSessionGoal.HIPERTROFIA.label)
        assertEquals("Fuerza", TemplateSessionGoal.FUERZA.label)

        assertEquals("Tren superior", TemplateSessionZone.SUPERIOR.label)
        assertEquals("Tren inferior", TemplateSessionZone.INFERIOR.label)

        assertEquals("Por rutina", TemplateGroupMode.SPLIT.label)
        assertEquals("Tipo de sesión", TemplateGroupMode.SESSION_TYPE.label)
        assertEquals("Objetivo", TemplateGroupMode.GOAL.label)

        assertEquals("Pecho", TemplateCatalogFilterLogic.focusCategoryLabel(SessionTemplateFocusCategory.PECHO))
        assertEquals("Core", TemplateCatalogFilterLogic.focusCategoryLabel(SessionTemplateFocusCategory.CORE))
        assertEquals("Full body", TemplateCatalogFilterLogic.focusCategoryLabel(SessionTemplateFocusCategory.FULL_BODY))
    }

    @Test
    fun availableSessionTypesYGoalsSonSubconjuntoOrdenado() {
        val types = TemplateCatalogFilterLogic.availableSessionTypes(templates)
        assertTrue(types.isNotEmpty())
        assertFalse(types.contains(TemplateSessionType.ALL))
        assertEquals(
            types,
            TemplateSessionType.entries.filter { it in types },
        )

        val goals = TemplateCatalogFilterLogic.availableGoals(templates)
        assertTrue(goals.isNotEmpty())
        assertFalse(goals.contains(TemplateSessionGoal.ALL))
    }
}
