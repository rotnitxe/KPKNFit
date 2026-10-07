package com.example.kpkn.domain.training.approach

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.LoadQuantityConvention
import com.example.kpkn.domain.exercises.catalogv2.JointRoleV2
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `ApproachInfoProvider` traduce el catálogo cargado a lo que el planificador necesita: articulaciones principales
 * (`jointInvolvement` PRIMARY y SECONDARY, PRIMARY primero), si es compuesto y si admite carga pesada.
 */
class ApproachInfoProviderTest {

    private val metadata get() = CatalogCompositionTestSupport.metadata
    private val catalog get() = CatalogCompositionTestSupport.catalog

    private fun exerciseOf(configurationId: String, sets: List<ExerciseSet> = emptyList(), convention: LoadQuantityConvention = LoadQuantityConvention.UNSPECIFIED) =
        Exercise(
            id = "e-$configurationId",
            name = configurationId,
            catalogConfigurationId = configurationId,
            sets = sets,
            loadQuantityConvention = convention,
        )

    private fun infoOf(configurationId: String, exercise: Exercise = exerciseOf(configurationId)): ApproachExerciseInfo =
        checkNotNull(ApproachInfoProvider.infoOf(exercise, metadata)) { "sin info para $configurationId" }

    @Test
    fun the_joints_are_the_primary_then_secondary_ones_of_the_catalog_profile() {
        val profile = catalog.families.flatMap { it.definitions }.flatMap { it.configurations }
            .first { it.id == "bench_press__barbell" }.profile
        val primary = profile.jointInvolvement.filter { it.role == JointRoleV2.PRIMARY }.map { it.jointId }
        val secondary = profile.jointInvolvement.filter { it.role == JointRoleV2.SECONDARY }.map { it.jointId }
        val stabilizers = profile.jointInvolvement.filter { it.role == JointRoleV2.STABILIZER }.map { it.jointId }

        val info = infoOf("bench_press__barbell")
        assertEquals(primary + secondary, info.joints.toList())
        assertTrue("el hombro es PRIMARY y va primero", info.joints.first() == JointMobility.GLENOHUMERAL)
        assertTrue("sin estabilizadores", stabilizers.none { it in info.joints })
    }

    @Test
    fun every_catalog_configuration_exposes_at_least_one_principal_joint_known_to_the_mobility_map() {
        val known = JointMobility.allJointIds.toSet()
        catalog.families.flatMap { it.definitions }.flatMap { it.configurations }.forEach { configuration ->
            val joints = checkNotNull(metadata.metadata(configuration.id)).principalJoints
            assertTrue("${configuration.id} sin articulaciones principales", joints.isNotEmpty())
            assertTrue("${configuration.id}: ${joints - known} no tiene mapa de movilidad", known.containsAll(joints))
        }
    }

    @Test
    fun loaded_compounds_can_be_heavy() {
        listOf(
            "high_bar_back_squat__barbell",
            "bench_press__barbell",
            "conventional_deadlift__bilateral__barbell",
            "military_press__barbell",
            "romanian_deadlift__bilateral__barbell",
            "hip_thrust__bilateral__barbell",
            "quads_sentadilla_hack__machine",
            "lat_pulldown__bilateral__machine",
            "chest_supported_row__machine__medium",
            "incline_bench_press__dumbbells",
        ).forEach { id ->
            val info = infoOf(id)
            assertTrue("$id es compuesto", info.isCompound)
            assertTrue("$id admite carga pesada", info.canBeHeavy)
            assertTrue("$id tiene articulaciones", info.joints.isNotEmpty())
        }
    }

    @Test
    fun isolations_and_machine_isolations_are_never_heavy() {
        listOf(
            "standing_biceps_curl__barbell",
            "standing_lateral_raise__dumbbells",
            "seated_leg_curl__bilateral__machine",
            "standing_biceps_curl__cable",
        ).forEach { id ->
            val info = infoOf(id)
            assertFalse("$id no es compuesto", info.isCompound)
            assertFalse("$id no es pesado", info.canBeHeavy)
        }
    }

    @Test
    fun bodyweight_and_band_exercises_are_light_unless_a_ballast_is_declared() {
        val pullUp = infoOf("pull_up__pronated__medium")
        assertTrue(pullUp.isCompound)
        assertFalse("dominada sin lastre = peso corporal fácil", pullUp.canBeHeavy)
        assertFalse(infoOf("hip_thrust__bilateral__band").canBeHeavy)

        val withBallastConvention = exerciseOf("pull_up__pronated__medium", convention = LoadQuantityConvention.ADDITIONAL_BODYWEIGHT)
        assertTrue("con lastre declarado sí puede ser pesada", infoOf("pull_up__pronated__medium", withBallastConvention).canBeHeavy)
        val withBallastWeight = exerciseOf("pull_up__pronated__medium", sets = listOf(ExerciseSet(id = "s", targetReps = 5, weight = 20.0)))
        assertTrue(infoOf("pull_up__pronated__medium", withBallastWeight).canBeHeavy)
    }

    @Test
    fun an_unknown_or_unidentified_exercise_has_no_info() {
        assertNull(ApproachInfoProvider.infoOf(exerciseOf("no_existe__barbell"), metadata))
        assertNull(ApproachInfoProvider.infoOf(Exercise(id = "x", name = "Sin catálogo"), metadata))
        val provider = ApproachInfoProvider.fromMetadata(metadata)
        assertNotNull(provider(exerciseOf("bench_press__barbell")))
    }

    @Test
    fun the_level_of_a_recipe_maps_to_the_approach_level() {
        assertEquals(ApproachLevel.NOVICE, approachLevelOf("principiante"))
        assertEquals(ApproachLevel.NOVICE, approachLevelOf("beginner"))
        assertEquals(ApproachLevel.INTERMEDIATE, approachLevelOf("intermedio"))
        assertEquals(ApproachLevel.INTERMEDIATE, approachLevelOf("intermediate"))
        assertEquals(ApproachLevel.ADVANCED, approachLevelOf("avanzado"))
        assertEquals(ApproachLevel.ADVANCED, approachLevelOf("ADVANCED"))
        assertEquals("sin dato: intermedio", ApproachLevel.INTERMEDIATE, approachLevelOf(null))
        assertEquals(ApproachLevel.INTERMEDIATE, approachLevelOf("otra cosa"))
    }

    @Test
    fun the_planner_driven_by_the_catalog_prepares_a_real_press_then_squat_day() {
        val ex = { id: String, role: com.example.kpkn.data.protocols.SlotRole ->
            Exercise(
                id = id,
                name = id,
                catalogConfigurationId = id,
                slotRole = role,
                sets = List(3) { ExerciseSet(id = "$id-$it", targetReps = 5, targetRIR = 2) },
            )
        }
        val session = com.example.kpkn.data.models.Session(
            id = "s",
            name = "Día",
            exercises = listOf(
                ex("bench_press__barbell", com.example.kpkn.data.protocols.SlotRole.T1_MAIN),
                ex("high_bar_back_squat__barbell", com.example.kpkn.data.protocols.SlotRole.T1_MAIN),
                ex("standing_biceps_curl__barbell", com.example.kpkn.data.protocols.SlotRole.T3_ACCESSORY),
            ),
        )
        val result = ApproachPlanner.apply(session, ApproachOptions(), ApproachInfoProvider.fromMetadata(metadata))
        val bench = result.exercises[0]
        val squat = result.exercises[1]
        val curl = result.exercises[2]

        assertEquals(listOf(40.0, 60.0, 80.0), bench.warmupSets.map { it.percentageOfWorkingWeight })
        assertTrue("movilidad de hombro antes del press", JointMobility.GLENOHUMERAL in JointMobility.jointsCoveredBy(bench.mobilitySeries))
        assertTrue("la sentadilla lleva su propia rampa", squat.warmupSets.isNotEmpty())
        val squatCovered = JointMobility.jointsCoveredBy(squat.mobilitySeries)
        assertTrue("y movilidad de cadera/rodilla: $squatCovered", JointMobility.HIP in squatCovered || JointMobility.KNEE in squatCovered)
        assertTrue("el curl no se aproxima", curl.warmupSets.isEmpty() && curl.mobilitySeries.isEmpty())
    }
}
