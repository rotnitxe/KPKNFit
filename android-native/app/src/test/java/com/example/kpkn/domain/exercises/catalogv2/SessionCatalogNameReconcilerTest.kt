package com.example.kpkn.domain.exercises.catalogv2

import com.example.kpkn.data.exercises.catalogv2.catalogV2SelectionIssues
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

class SessionCatalogNameReconcilerTest {

    companion object {
        private lateinit var displayNameIndex: Map<String, String>

        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            val resource = SessionCatalogNameReconcilerTest::class.java.classLoader?.getResource("exercise_catalog_v2.json")
            val file = if (resource != null) {
                File(resource.toURI())
            } else {
                listOf(
                    "../../android-native/app/src/main/assets/exercise_catalog_v2.json",
                    "../android-native/app/src/main/assets/exercise_catalog_v2.json",
                    "android-native/app/src/main/assets/exercise_catalog_v2.json",
                ).map(::File).first { it.exists() }
            }
            val catalog = ExerciseCatalogV2Loader.decodeApproved(file.readText())
            displayNameIndex = CatalogDisplayNames.buildDisplayNameIndex(catalog)
        }
    }

    private fun catalogExercise(id: String, name: String) = Exercise(
        id = id,
        name = name,
        exerciseDbId = "bench_press__barbell",
        exerciseId = "bench_press__barbell",
        canonicalExerciseId = "bench_press__barbell",
        exerciseFamilyId = "bench_press",
        sets = listOf(
            ExerciseSet(id = "$id-s1", targetReps = 8, targetRPE = 8.0, intensityMode = IntensityMode.RPE),
        ),
        catalogRevision = "v2-approved-2026-08-12-a",
        catalogDefinitionId = "bench_press",
        catalogConfigurationId = "bench_press__barbell",
        performanceProfileId = "bench_press__barbell__press_de_banca",
        occurrenceId = id,
    )

    @Test
    fun reconcile_rewrites_invented_name_with_verbatim_catalog_name() {
        val session = Session(
            id = "s1",
            name = "Test",
            exercises = listOf(catalogExercise("ex1", "Press de Banca con Barra")),
        )
        val reconciled = SessionCatalogNameReconciler.reconcileSession(session, displayNameIndex)
        assertEquals("Press de Banca Plano", reconciled.exercises.first().name)
    }

    @Test
    fun reconcile_strips_technique_suffix_to_verbatim_name() {
        val session = Session(
            id = "s1",
            name = "Test",
            exercises = listOf(
                catalogExercise("ex1", "Press de Banca Plano · barra").copy(
                    variantName = "Velocidad",
                    techniqueModifier = com.example.kpkn.data.protocols.TechniqueModifier.SPEED,
                    relationshipNotes = "Velocidad",
                ),
            ),
        )
        val reconciled = SessionCatalogNameReconciler.reconcileSession(session, displayNameIndex)
        val out = reconciled.exercises.first()
        // El nombre almacenado vuelve al canonical verbatim; la técnica se
        // conserva en campos y se muestra como chip.
        assertEquals("Press de Banca Plano", out.name)
        assertEquals(com.example.kpkn.data.protocols.TechniqueModifier.SPEED, out.techniqueModifier)
        val parts = com.example.kpkn.domain.exercises.exerciseDisplayParts(out, null)
        assertEquals("Press de Banca Plano", parts.parentName)
        assertTrue(parts.chips.isEmpty())
    }

    @Test
    fun gate_blocks_divergent_name_but_passes_after_reconcile() {
        val stale = Session(
            id = "s1",
            name = "Test",
            exercises = listOf(catalogExercise("ex1", "Nombre inventado viejo")),
        )
        val before = stale.catalogV2SelectionIssues(displayNameIndex)
        assertTrue(
            "Debe bloquear por divergencia de nombre: $before",
            before.any { it.code == "catalog_name_divergence" },
        )
        val reconciled = SessionCatalogNameReconciler.reconcileSession(stale, displayNameIndex)
        val after = reconciled.catalogV2SelectionIssues(displayNameIndex)
        assertTrue("Tras reconciliar el gate debe pasar: $after", after.isEmpty())
    }

    @Test
    fun reconcile_preserves_custom_exercises_and_identity() {
        val custom = catalogExercise("ex1", "Mi invento").copy(
            exerciseDbId = "custom:mi-invento",
            exerciseId = "custom:mi-invento",
            canonicalExerciseId = "custom:mi-invento",
            exerciseFamilyId = "custom:mi-invento",
        )
        val session = Session(id = "s1", name = "Test", exercises = listOf(custom))
        val reconciled = SessionCatalogNameReconciler.reconcileSession(session, displayNameIndex)
        val out = reconciled.exercises.first()
        assertEquals("Mi invento", out.name)
        assertEquals(custom.catalogConfigurationId, out.catalogConfigurationId)
        assertEquals(custom.occurrenceId, out.occurrenceId)
        assertEquals(custom.sets, out.sets)
    }

    @Test
    fun normalizeSessionStructure_clears_redundant_loose_exercises() {
        val exercise = catalogExercise("ex1", "Press de Banca Plano")
        val part = com.example.kpkn.data.models.SessionPart(
            id = "part",
            name = "Principal",
            exercises = listOf(exercise),
        )
        val session = Session(
            id = "s1",
            name = "Test",
            exercises = listOf(exercise),
            parts = listOf(part),
        )
        val normalized = SessionCatalogNameReconciler.normalizeSessionStructure(session)
        assertTrue(normalized.exercises.isEmpty())
        assertEquals(1, normalized.parts.single().exercises.size)
    }

    @Test
    fun normalizeSessionStructure_removes_only_mirrored_loose_items() {
        val mirrored = catalogExercise("ex1", "Press de Banca Plano")
        val draft = catalogExercise("draft1", "Mi suelto").copy(
            exerciseDbId = "custom:mi-suelto",
            exerciseId = "custom:mi-suelto",
            canonicalExerciseId = "custom:mi-suelto",
            exerciseFamilyId = "custom:mi-suelto",
        )
        val part = com.example.kpkn.data.models.SessionPart(
            id = "part",
            name = "Principal",
            exercises = listOf(mirrored),
        )
        val session = Session(
            id = "s1",
            name = "Test",
            exercises = listOf(mirrored, draft),
            parts = listOf(part),
        )
        val normalized = SessionCatalogNameReconciler.normalizeSessionStructure(session)
        assertEquals(listOf("draft1"), normalized.exercises.map { it.id })
        assertEquals(1, normalized.parts.single().exercises.size)
    }

    @Test
    fun normalizeSessionStructure_ignores_cardio_parts() {
        val exercise = catalogExercise("ex1", "Cardio")
        val cardioPart = com.example.kpkn.data.models.SessionPart(
            id = "cardio",
            name = "Cardio",
            exercises = listOf(exercise),
            isCardioGroup = true,
        )
        val session = Session(
            id = "s1",
            name = "Test",
            exercises = listOf(exercise),
            parts = listOf(cardioPart),
        )
        val normalized = SessionCatalogNameReconciler.normalizeSessionStructure(session)
        assertEquals(listOf("ex1"), normalized.exercises.map { it.id })
    }

    @Test
    fun remap_legacy_bench_pause_to_catalog_configuration() {
        val stale = catalogExercise("ex1", "Press de Banca Plano").copy(
            techniqueModifier = com.example.kpkn.data.protocols.TechniqueModifier.PAUSE_2S,
            variantName = "Pausa 2s",
        )
        val remapped = SessionCatalogNameReconciler.reconcileExercise(stale, displayNameIndex)
        assertEquals(com.example.kpkn.data.protocols.CatalogIds.BP_PAUSE, remapped.catalogConfigurationId)
        assertEquals("Press de Banca con Pausa", remapped.name)
        assertNull(remapped.techniqueModifier)
    }
}
