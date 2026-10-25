package com.example.kpkn.domain.exercises

import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun empty_catalog_keeps_the_data_unknown_instead_of_inventing_a_structure() {
        val created = SmartExerciseCreator.create(
            SmartCreateRequest(name = "Ejercicio inventado raro", implementoId = "machine"),
            emptyList(),
        )
        assertTrue(created.id.startsWith("custom:"))
        assertEquals("Máquina", created.equipment)
        // MOT · E2: sin referencia confirmada no hay inferencia estructural con cifras inventadas; los datos quedan desconocidos
        // (null, no 0) y el motor de fatiga usa su heurística por nombre hasta que la persona los complete.
        assertFalse((kotlinx.serialization.json.Json.encodeToJsonElement(ExerciseMuscleInfo.serializer(), created) as kotlinx.serialization.json.JsonObject).containsKey("efc"))
        assertFalse((kotlinx.serialization.json.Json.encodeToJsonElement(ExerciseMuscleInfo.serializer(), created) as kotlinx.serialization.json.JsonObject).containsKey("cnc"))
        assertFalse((kotlinx.serialization.json.Json.encodeToJsonElement(ExerciseMuscleInfo.serializer(), created) as kotlinx.serialization.json.JsonObject).containsKey("ssc"))
        assertFalse((kotlinx.serialization.json.Json.encodeToJsonElement(ExerciseMuscleInfo.serializer(), created) as kotlinx.serialization.json.JsonObject).containsKey("axialLoadFactor"))
        assertTrue(created.involvedMuscles.isEmpty())
    }
}
