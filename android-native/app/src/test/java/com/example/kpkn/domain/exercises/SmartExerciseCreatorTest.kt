package com.example.kpkn.domain.exercises

import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartExerciseCreatorTest {

    private val catalog = listOf(
        ExerciseMuscleInfo(
            id = "romanian_deadlift",
            name = "Peso Muerto Rumano",
            alias = "rdl, peso muerto rumano, rumano",
            equipment = "Barra",
            force = "Bisagra",
            category = "Fuerza",
            type = "Básico",
            bodyPart = "lower",
            chain = "posterior",
            tier = "T1",
            efc = 3.5,
            cnc = 3.0,
            ssc = 2.0,
            ttc = 3.0,
            axialLoadFactor = 1.0,
            averageRestSeconds = 180,
            involvedMuscles = listOf(
                InvolvedMuscle("Isquiosurales", MuscleRole.PRIMARY, 1.0),
                InvolvedMuscle("Glúteos", MuscleRole.SECONDARY, 0.5, "mayor"),
                InvolvedMuscle("Erectores Espinales", MuscleRole.STABILIZER, 0.4, "mayor"),
            ),
        ),
        ExerciseMuscleInfo(
            id = "deadlift",
            name = "Peso Muerto Convencional",
            alias = "peso muerto, deadlift",
            equipment = "Barra",
            force = "Bisagra",
            category = "Fuerza",
            type = "Básico",
            bodyPart = "lower",
            chain = "posterior",
            tier = "T1",
            efc = 4.0,
            cnc = 3.5,
            ssc = 2.0,
            ttc = 4.0,
            axialLoadFactor = 1.0,
            averageRestSeconds = 180,
            involvedMuscles = listOf(InvolvedMuscle("Erectores Espinales", MuscleRole.PRIMARY, 0.6)),
        ),
        ExerciseMuscleInfo(
            id = "bicep_curl",
            name = "Curl de Bíceps",
            alias = "curl biceps",
            equipment = "Mancuerna",
            force = "Tirón",
            category = "Hipertrofia",
            type = "Aislamiento",
            bodyPart = "upper",
            chain = "anterior",
            tier = "T3",
            efc = 1.5,
            cnc = 1.0,
            ssc = 0.0,
            ttc = 1.0,
            axialLoadFactor = 0.0,
            averageRestSeconds = 90,
            involvedMuscles = listOf(InvolvedMuscle("Bíceps", MuscleRole.PRIMARY, 1.0, "medio")),
        ),
    )

    @Test
    fun derives_from_best_name_match_for_variant() {
        val created = SmartExerciseCreator.create(
            SmartCreateRequest(name = "Peso muerto rumano con mancuernas", implementoId = "dumbbells"),
            catalog,
        )

        assertTrue(created.id.startsWith("custom:"))
        assertTrue(created.isCustom)
        assertEquals("Mancuerna", created.equipment)
        assertEquals("Peso muerto rumano con mancuernas", created.name)
        // Los valores AUGE llegan del mejor match (RDL), ponderados por el equipo
        // (mancuerna baja un poco el efc). Rango en vez de valor exacto: el scoring
        // interno puede ponderar el deadlift de maneras distintas.
        val efc = created.efc ?: 0.0
        val cnc = created.cnc ?: 0.0
        assertTrue("efc=$efc muscles=${created.involvedMuscles.map { it.muscle }} name=${created.name}", efc in 3.0..4.2)
        assertTrue("cnc=$cnc", cnc in 2.8..3.9)
        assertEquals("posterior", created.chain)
        assertEquals("lower", created.bodyPart)
        assertTrue(created.involvedMuscles.map { it.muscle }.let { "Isquiosurales" in it && "Glúteos" in it })
    }

    @Test
    fun emphasis_is_propagated_from_the_best_match() {
        val created = SmartExerciseCreator.create(
            SmartCreateRequest(name = "Peso muerto rumano", implementoId = "barbell"),
            catalog,
        )
        val glutes = created.involvedMuscles.firstOrNull { it.muscle == "Glúteos" }
        assertEquals("mayor", glutes?.emphasis)
    }

    @Test
    fun laterality_and_station_are_recorded_as_chips() {
        val created = SmartExerciseCreator.create(
            SmartCreateRequest(
                name = "Elevación lateral sentado",
                implementoId = "dumbbells",
                estacionId = "seated",
                lateralidadId = "bilateral",
            ),
            catalog,
        )
        assertEquals("Sentado", created.catalogVariantChips[1])
        assertEquals("Bilateral", created.catalogVariantChips[2])
    }

    @Test
    fun empty_catalog_falls_back_to_structural_inference() {
        val created = SmartExerciseCreator.create(
            SmartCreateRequest(name = "Ejercicio inventado raro", implementoId = "machine"),
            emptyList(),
        )
        assertTrue(created.id.startsWith("custom:"))
        assertEquals("Máquina", created.equipment)
        assertTrue(created.efc != null)
    }
}
