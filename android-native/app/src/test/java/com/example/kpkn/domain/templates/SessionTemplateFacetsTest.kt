package com.example.kpkn.domain.templates

import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.sessions.SESSION_TEMPLATES_SYSTEM
import com.example.kpkn.domain.exercises.ExerciseCatalogRegion
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

class SessionTemplateFacetsTest {

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private lateinit var exerciseIndexWithAliases: Map<String, ExerciseMuscleInfo>
        private lateinit var facetsById: Map<String, SessionTemplateFacets>

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
            exerciseIndexWithAliases = merged
            facetsById = SessionTemplateFacetsBuilder.buildAll(SESSION_TEMPLATES_SYSTEM, exerciseIndexWithAliases)
        }

        private fun findDbFile(fileName: String): File {
            val resource = SessionTemplateFacetsTest::class.java.classLoader?.getResource(fileName)
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

        private fun facets(id: String): SessionTemplateFacets =
            facetsById[id] ?: error("Plantilla '$id' sin facetas")
    }

    @Test
    fun pushClassifiesUpperAnteriorAndPectorales() {
        val facets = facets("sys-push-ppl")

        assertTrue("Push debe ser tren superior: ${facets.regions}", ExerciseCatalogRegion.UPPER in facets.regions)
        assertFalse("Push no debe ser tren inferior", ExerciseCatalogRegion.LOWER in facets.regions)
        assertTrue(
            "Push debe incluir cadena anterior: ${facets.chains}",
            SessionTemplateChain.ANTERIOR in facets.chains,
        )
        assertTrue(
            "Push debe listar Pectorales como primario (≥3 series): ${facets.primaryMuscles}",
            facets.primaryMuscles.any { it.equals("Pectorales", ignoreCase = true) },
        )
        assertTrue(SessionTemplateFacetsBuilder.matchesRegion(facets, ExerciseCatalogRegion.UPPER))
        assertTrue(SessionTemplateFacetsBuilder.matchesChain(facets, SessionTemplateChain.ANTERIOR))
        assertTrue(SessionTemplateFacetsBuilder.matchesMuscle(facets, "Pectorales"))
        assertEquals(ExerciseCatalogRegion.UPPER, facets.dominantRegion)
    }

    @Test
    fun legsClassifyPosteriorAndAnteriorByTemplate() {
        val hinge = facets("sys-legs-hinge")
        assertTrue("Bisagra debe ser LOWER: ${hinge.regions}", ExerciseCatalogRegion.LOWER in hinge.regions)
        assertTrue(
            "Bisagra debe incluir cadena posterior: ${hinge.chains}",
            SessionTemplateChain.POSTERIOR in hinge.chains,
        )
        assertTrue(SessionTemplateFacetsBuilder.matchesChain(hinge, SessionTemplateChain.POSTERIOR))

        val quad = facets("sys-legs-quad")
        assertTrue("Quad debe ser LOWER: ${quad.regions}", ExerciseCatalogRegion.LOWER in quad.regions)
        assertTrue(
            "Quad debe incluir cadena anterior: ${quad.chains}",
            SessionTemplateChain.ANTERIOR in quad.chains,
        )
        assertTrue(SessionTemplateFacetsBuilder.matchesChain(quad, SessionTemplateChain.ANTERIOR))

        val ant = facets("sys-ant-chain-ap")
        assertTrue(SessionTemplateChain.ANTERIOR in ant.chains)
        val post = facets("sys-post-chain-ap")
        assertTrue(SessionTemplateChain.POSTERIOR in post.chains)
    }

    @Test
    fun fullBodyDetectsFullOrMultipleRegions() {
        val full = facets("sys-fullbody-base")
        val hasFullFlag = ExerciseCatalogRegion.FULL in full.regions
        val hasUpperAndLower =
            ExerciseCatalogRegion.UPPER in full.regions && ExerciseCatalogRegion.LOWER in full.regions
        assertTrue(
            "Full body debe marcar FULL o upper+lower: regions=${full.regions}",
            hasFullFlag || hasUpperAndLower,
        )
        assertTrue(SessionTemplateFacetsBuilder.matchesRegion(full, ExerciseCatalogRegion.FULL))
        assertEquals(ExerciseCatalogRegion.FULL, full.dominantRegion)

        val antChain = facets("sys-ant-chain-ap")
        assertTrue(
            "Cadena anterior mezcla torso+pierna → FULL o upper+lower: ${antChain.regions}",
            ExerciseCatalogRegion.FULL in antChain.regions ||
                (ExerciseCatalogRegion.UPPER in antChain.regions && ExerciseCatalogRegion.LOWER in antChain.regions),
        )
    }

    @Test
    fun allSystemTemplatesProduceValidFacets() {
        assertEquals(
            "buildAll debe cubrir las 65 plantillas del sistema",
            SESSION_TEMPLATES_SYSTEM.size,
            facetsById.size,
        )
        assertEquals(65, SESSION_TEMPLATES_SYSTEM.size)

        val failures = mutableListOf<String>()
        SESSION_TEMPLATES_SYSTEM.forEach { template ->
            val facets = facetsById[template.id]
            if (facets == null) {
                failures += "'${template.id}': sin facetas"
                return@forEach
            }
            if (facets.totalSets <= 0) {
                failures += "'${template.id}': totalSets=${facets.totalSets}"
            }
            if (facets.realDurationMinutes <= 0) {
                failures += "'${template.id}': duration=${facets.realDurationMinutes}"
            }
            if (facets.primaryMuscles.isEmpty()) {
                failures += "'${template.id}': primaryMuscles vacío"
            }
            val drain = facets.drain
            if (drain.cns !in 0..100 || drain.muscular !in 0..100 || drain.spinal !in 0..100) {
                failures += "'${template.id}': drain inválido cns=${drain.cns} mus=${drain.muscular} spi=${drain.spinal}"
            }
            if (facets.regions.any { it == ExerciseCatalogRegion.ALL }) {
                failures += "'${template.id}': regions incluye ALL"
            }
            if (facets.chains.any { it == SessionTemplateChain.ALL }) {
                failures += "'${template.id}': chains incluye ALL"
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun matchingHelpersRespectAllSentinel() {
        val facets = facets("sys-push-ppl")
        assertTrue(SessionTemplateFacetsBuilder.matchesRegion(facets, ExerciseCatalogRegion.ALL))
        assertTrue(SessionTemplateFacetsBuilder.matchesChain(facets, SessionTemplateChain.ALL))
        assertTrue(SessionTemplateFacetsBuilder.matchesMuscle(facets, null))
        assertTrue(SessionTemplateFacetsBuilder.matchesMuscle(facets, "  "))
        assertTrue(SessionTemplateFacetsBuilder.matchesDuration(facets, SessionTemplateDurationBucket.ALL))
        assertTrue(
            SessionTemplateFacetsBuilder.matchesDuration(
                facets,
                SessionTemplateFacetsBuilder.durationBucket(facets.realDurationMinutes),
            ),
        )
        assertFalse(SessionTemplateFacetsBuilder.matchesMuscle(facets, "Pantorrillas"))
    }

    @Test
    fun normalizeChainIgnoresUnknownAndNull() {
        assertEquals(SessionTemplateChain.ANTERIOR, SessionTemplateFacetsBuilder.normalizeChain("anterior"))
        assertEquals(SessionTemplateChain.POSTERIOR, SessionTemplateFacetsBuilder.normalizeChain(" Posterior "))
        assertEquals(SessionTemplateChain.FULL, SessionTemplateFacetsBuilder.normalizeChain("full"))
        assertEquals(null, SessionTemplateFacetsBuilder.normalizeChain(null))
        assertEquals(null, SessionTemplateFacetsBuilder.normalizeChain(""))
        assertEquals(null, SessionTemplateFacetsBuilder.normalizeChain("lateral"))
    }
}
