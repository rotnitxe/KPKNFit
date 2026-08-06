package com.example.kpkn.domain.exercises

import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartExerciseCreatorRegressionTest {

    private val catalog = listOf(
        ExerciseMuscleInfo(
            id = "flat_chest_fly",
            name = "Aperturas Planas",
            alias = "aperturas planas, chest fly planas, tren_superior_aperturas",
            equipment = "Mancuerna",
            force = "Empuje Horizontal",
            movementPattern = "horizontal_push",
            category = "Fuerza",
            type = "Accesorio",
            bodyPart = "upper",
            chain = "anterior",
            tier = "T3",
            efc = 2.5,
            cnc = 2.2,
            ssc = 0.6,
            ttc = 1.8,
            axialLoadFactor = 0.0,
            averageRestSeconds = 90,
            involvedMuscles = listOf(
                InvolvedMuscle("Pectorales", MuscleRole.PRIMARY, null, "medio"),
                InvolvedMuscle("Deltoides", MuscleRole.SECONDARY, null, "anterior"),
            ),
        ),
    )

    @Test
    fun aperturas_planas_keeps_real_muscle_volume_even_with_mismatched_implemento() {
        val preview = SmartExerciseCreator.preview(
            SmartCreateRequest(name = "Aperturas Planas", implementoId = "barbell"),
            catalog,
        )
        val created = preview.exercise

        val pectoral = created.involvedMuscles.firstOrNull { it.muscle == "Pectorales" }
        assertNotNull(pectoral)
        assertEquals(MuscleRole.PRIMARY, pectoral?.role)
        assertEquals(1.0, pectoral?.volumeContribution ?: 0.0, 0.0001)

        val deltoid = created.involvedMuscles.firstOrNull { it.muscle == "Deltoides" }
        assertNotNull(deltoid)
        assertEquals(MuscleRole.SECONDARY, deltoid?.role)
        assertEquals(0.5, deltoid?.volumeContribution ?: 0.0, 0.0001)

        assertEquals("medio", pectoral?.emphasis)
        assertEquals(1, preview.matchCount)
        assertFalse(preview.manualRecommended)
    }

    @Test
    fun auto_generates_description_when_name_is_clear() {
        val created = SmartExerciseCreator.create(
            SmartCreateRequest(name = "Aperturas Planas", implementoId = "dumbbells"),
            catalog,
        )
        assertNotNull(created.description)
        assertTrue(created.description?.isNotBlank() == true)
        assertTrue(created.description.orEmpty().contains("Pectorales", ignoreCase = true))
    }

    @Test
    fun keeps_user_description_when_provided() {
        val description = "Mi apertura con polea alta, manteniendo el arco controlado."
        val created = SmartExerciseCreator.create(
            SmartCreateRequest(
                name = "Aperturas Planas",
                implementoId = "dumbbells",
                description = description,
            ),
            catalog,
        )
        assertEquals(description, created.description)
    }

    @Test
    fun manual_muscle_override_is_respected() {
        val override = listOf(
            InvolvedMuscle("Pectorales", MuscleRole.PRIMARY, 0.9),
            InvolvedMuscle("Deltoides", MuscleRole.SECONDARY, 0.4),
        )
        val created = SmartExerciseCreator.create(
            SmartCreateRequest(
                name = "Aperturas Planas",
                implementoId = "dumbbells",
                musclesOverride = override,
            ),
            catalog,
        )
        assertEquals(2, created.involvedMuscles.size)
        assertEquals(0.9, created.involvedMuscles.first { it.muscle == "Pectorales" }.volumeContribution ?: 0.0, 0.0001)
        assertEquals(0.4, created.involvedMuscles.first { it.muscle == "Deltoides" }.volumeContribution ?: 0.0, 0.0001)
    }

    @Test
    fun editing_preserves_existing_id() {
        val created = SmartExerciseCreator.create(
            SmartCreateRequest(
                name = "Aperturas Planas",
                implementoId = "dumbbells",
                existingId = "custom:abc",
            ),
            catalog,
        )
        assertEquals("custom:abc", created.id)
    }

    @Test
    fun unknown_name_recommends_manual_input_and_has_no_auto_description() {
        val preview = SmartExerciseCreator.preview(
            SmartCreateRequest(name = "Kárate kick alfa", implementoId = "machine"),
            emptyList(),
        )
        assertTrue(preview.manualRecommended)
        assertEquals(0, preview.matchCount)
        assertNull(preview.detectedPattern)
        assertNull(preview.exercise.description)
    }

    @Test
    fun automatic_creation_uses_search_name_and_reference_equipment() {
        val created = SmartExerciseCreator.createAutomatic("Aperturas Planas", catalog)
        assertEquals("Aperturas Planas", created.name)
        assertEquals("Mancuerna", created.equipment)
        val pectoral = created.involvedMuscles.firstOrNull { it.muscle == "Pectorales" }
        assertEquals(1.0, pectoral?.volumeContribution ?: 0.0, 0.0001)
        assertTrue(created.description?.isNotBlank() == true)
    }

    @Test
    fun automatic_creation_takes_chips_from_reference() {
        val withChips = catalog + ExerciseMuscleInfo(
            id = "lateral_raise_seated",
            name = "Elevación Lateral Sentada",
            alias = "elevacion lateral sentada, lateral raise sentado",
            equipment = "Mancuerna",
            force = "Abducción Hombro",
            movementPattern = "shoulder_abduction",
            category = "Fuerza",
            type = "Aislamiento",
            bodyPart = "upper",
            chain = "anterior",
            efc = 1.5,
            cnc = 1.0,
            ssc = 0.2,
            ttc = 1.0,
            axialLoadFactor = 0.0,
            averageRestSeconds = 60,
            catalogVariantChips = listOf("Mancuerna", "Sentado", "Unilateral"),
            involvedMuscles = listOf(InvolvedMuscle("Deltoides", MuscleRole.PRIMARY, 1.0, "lateral")),
        )
        val created = SmartExerciseCreator.createAutomatic("Elevación Lateral Sentada", withChips)
        assertEquals(listOf("Mancuerna", "Sentado", "Unilateral"), created.catalogVariantChips)
        assertEquals("Mancuerna", created.equipment)
    }

    @Test
    fun automatic_creation_finds_reference_with_typo_and_accent() {
        val created = SmartExerciseCreator.createAutomatic("Apérturas Planas", catalog)
        assertEquals("Apérturas Planas", created.name)
        val pectoral = created.involvedMuscles.firstOrNull { it.muscle == "Pectorales" }
        assertEquals(1.0, pectoral?.volumeContribution ?: 0.0, 0.0001)
        assertEquals("Mancuerna", created.equipment)
    }

    @Test
    fun automatic_creation_finds_reference_by_english_synonym() {
        val created = SmartExerciseCreator.createAutomatic("Chest Fly Planas", catalog)
        assertEquals("Chest Fly Planas", created.name)
        val pectoral = created.involvedMuscles.firstOrNull { it.muscle == "Pectorales" }
        assertEquals(1.0, pectoral?.volumeContribution ?: 0.0, 0.0001)
        assertEquals("Mancuerna", created.equipment)
    }
}
