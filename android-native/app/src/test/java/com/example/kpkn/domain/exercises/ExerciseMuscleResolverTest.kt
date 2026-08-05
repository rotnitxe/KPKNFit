package com.example.kpkn.domain.exercises

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ExerciseMuscleResolverTest {

    private val definitionInfo = ExerciseMuscleInfo(
        id = "bench_press",
        name = "Press Banca",
        involvedMuscles = listOf(InvolvedMuscle("Pectorales", MuscleRole.PRIMARY, 1.0)),
    )
    private val configurationInfo = ExerciseMuscleInfo(
        id = "press_banca__barbell__plano",
        name = "Press Banca",
        involvedMuscles = listOf(InvolvedMuscle("Pectorales", MuscleRole.PRIMARY, 1.0)),
    )
    private val snapshotIndex = listOf(definitionInfo, configurationInfo)
        .associateBy { it.id.lowercase() }

    private fun exercise(
        configurationId: String? = null,
        exerciseDbId: String? = null,
        exerciseId: String? = null,
        name: String = "Press Banca",
    ): Exercise = Exercise(
        id = "session-instance",
        name = name,
        exerciseDbId = exerciseDbId,
        exerciseId = exerciseId,
        catalogConfigurationId = configurationId,
    )

    @Test
    fun configuration_id_wins_when_present_in_index() {
        val resolved = ExerciseMuscleResolver.resolveCatalogInfo(
            exercise(
                configurationId = "PRESS_BANCA__BARBELL__PLANO",
                exerciseDbId = "bench_press",
            ),
            snapshotIndex,
        )
        assertEquals(configurationInfo.id, resolved?.id)
    }

    @Test
    fun configuration_miss_falls_through_to_definition_id() {
        val resolved = ExerciseMuscleResolver.resolveCatalogInfo(
            exercise(
                configurationId = "missing__configuration__id",
                exerciseDbId = "bench_press",
            ),
            snapshotIndex,
        )
        assertEquals(definitionInfo.id, resolved?.id)
        assertNotNull(resolved)
    }

    @Test
    fun returns_null_when_no_candidate_resolves() {
        val resolved = ExerciseMuscleResolver.resolveCatalogInfo(
            exercise(
                configurationId = "missing__configuration__id",
                exerciseDbId = null,
                exerciseId = "this is nonexistent",
                name = "Nombre Desconocido",
            ),
            snapshotIndex,
        )
        assertNull(resolved)
    }

    @Test
    fun name_equality_is_the_last_resort() {
        val resolved = ExerciseMuscleResolver.resolveCatalogInfo(
            exercise(
                configurationId = null,
                exerciseDbId = "deleted_legacy_id",
                exerciseId = null,
            ),
            snapshotIndex,
        )
        assertEquals(definitionInfo.id, resolved?.id)
    }
}
