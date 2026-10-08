package com.example.kpkn.domain.training.split

import com.example.kpkn.data.exercises.catalogv2.CatalogCompositionMetadataProvider
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.LoadQuantityConvention
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.programs.KpknMuscleGroup
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.domain.training.PatternFamily
import com.example.kpkn.domain.training.approach.ApproachExerciseInfo
import com.example.kpkn.domain.training.approach.ApproachInfoProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/** El resolutor por defecto clasifica las 521 configuraciones del catálogo v2 y cae con elegancia en lo que no conoce. */
class ExerciseTraitResolverTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }
    }

    private val resolver get() = SplitTestSupport.resolver

    private fun traits(configurationId: String): ExerciseTraits =
        requireNotNull(resolver.traitsOf(SplitTestSupport.exercise("x", configurationId))) { configurationId }

    @Test
    fun every_configuration_of_the_catalog_has_traits_with_a_primary_muscle() {
        val configurations = SplitTestSupport.catalog.families.flatMap { it.definitions }.flatMap { it.configurations }
        assertTrue(configurations.size > 500)
        val problems = configurations.mapNotNull { configuration ->
            val traits = resolver.traitsOf(Exercise(id = "x", name = "x", catalogConfigurationId = configuration.id))
            when {
                traits == null -> "${configuration.id}: sin rasgos"
                traits.primaryMuscles.isEmpty() -> "${configuration.id}: sin músculo primario"
                traits.source != TraitSource.CATALOG -> "${configuration.id}: no sale del catálogo"
                else -> null
            }
        }
        assertEquals(emptyList<String>(), problems)
    }

    @Test
    fun the_bench_press_is_a_heavy_push_compound_for_the_chest() {
        val bench = traits(CatalogIds.BP)
        assertEquals(PatternFamily.HORIZONTAL_PUSH, bench.pattern)
        assertEquals(setOf(KpknMuscleGroup.CHEST), bench.primaryMuscles)
        assertEquals(ExerciseTraits.SECONDARY, bench.muscles.getValue(KpknMuscleGroup.TRICEPS), 0.0)
        assertEquals(ExerciseTraits.SECONDARY, bench.muscles.getValue(KpknMuscleGroup.DELT_FRONT), 0.0)
        assertEquals(SplitGroup.PUSH, bench.group)
        assertEquals(BodyRegion.UPPER, bench.region)
        assertEquals(KineticChain.ANTERIOR, bench.chain)
        assertTrue(bench.isCompound)
        assertTrue(bench.canBeHeavy)
        assertTrue("glenohumeral" in bench.joints)
        assertEquals("bench_press", bench.definitionId)
        // Las configuraciones de una misma definición comparten identidad (el remo con barra y en polea).
        assertEquals(traits(CatalogIds.ROW).definitionId, traits(CatalogIds.ROW_CABLE).definitionId)
        assertTrue(traits(CatalogIds.BP).definitionId != traits(CatalogIds.BP_INC_DB).definitionId)
    }

    @Test
    fun legs_hinges_and_calves_belong_to_the_leg_group_and_the_lower_region() {
        val squat = traits(CatalogIds.SQ_HIGH)
        assertEquals(PatternFamily.SQUAT, squat.pattern)
        assertEquals(setOf(KpknMuscleGroup.QUADS, KpknMuscleGroup.GLUTES), squat.primaryMuscles)
        assertEquals(SplitGroup.LEGS, squat.group)
        assertEquals(BodyRegion.LOWER, squat.region)

        val deadlift = traits(CatalogIds.DL)
        assertEquals(PatternFamily.HINGE, deadlift.pattern)
        assertEquals(SplitGroup.LEGS, deadlift.group)
        assertEquals(BodyRegion.LOWER, deadlift.region)

        val calf = traits(CatalogIds.CALF)
        assertEquals(setOf(KpknMuscleGroup.CALVES), calf.primaryMuscles)
        assertFalse(calf.isCompound)
        assertEquals(SplitGroup.LEGS, calf.group)

        val legCurl = traits(CatalogIds.CURL_L)
        assertEquals(setOf(KpknMuscleGroup.HAMS), legCurl.primaryMuscles)
        assertFalse(legCurl.isCompound)
    }

    @Test
    fun the_deltoid_is_split_by_head_from_the_movement() {
        assertEquals(setOf(KpknMuscleGroup.DELT_FRONT), traits(CatalogIds.OHP).primaryMuscles)
        assertEquals(setOf(KpknMuscleGroup.DELT_LATERAL), traits(CatalogIds.LATERAL).primaryMuscles)
        assertEquals(setOf(KpknMuscleGroup.DELT_REAR), traits(CatalogIds.FACE).primaryMuscles)
        assertEquals(SplitGroup.PUSH, traits(CatalogIds.LATERAL).group)
        assertEquals(SplitGroup.PULL, traits(CatalogIds.FACE).group)
    }

    @Test
    fun arms_and_pulls_are_classified_by_muscle_and_pattern() {
        val curl = traits(CatalogIds.CURL)
        assertEquals(setOf(KpknMuscleGroup.BICEPS), curl.primaryMuscles)
        assertEquals(SplitGroup.PULL, curl.group)
        assertFalse(curl.isCompound)
        assertEquals(SplitGroup.PUSH, traits(CatalogIds.PUSHDOWN).group)
        assertEquals(PatternFamily.VERTICAL_PULL, traits(CatalogIds.LAT).pattern)
        assertEquals(PatternFamily.HORIZONTAL_PULL, traits(CatalogIds.ROW).pattern)
        assertEquals(SplitGroup.PULL, traits(CatalogIds.ROW).group)
        assertEquals(setOf(KpknMuscleGroup.CORE), traits(CatalogIds.CRUNCH).primaryMuscles)
        assertEquals(SplitGroup.CORE, traits(CatalogIds.CRUNCH).group)
    }

    @Test
    fun a_bodyweight_exercise_is_never_heavy_and_a_band_neither() {
        val pullUp = traits(CatalogIds.PULLUP)
        assertTrue(pullUp.isCompound)
        assertFalse(pullUp.canBeHeavy)
    }

    @Test
    fun an_exercise_without_catalog_identity_is_found_by_its_catalog_name() {
        val definition = SplitTestSupport.catalog.families.flatMap { it.definitions }
            .first { it.defaultConfigurationId == CatalogIds.BP }
        val byName = resolver.traitsOf(Exercise(id = "x", name = definition.canonicalName))
        assertNotNull(byName)
        assertEquals(traits(CatalogIds.BP), byName)
        // También por el id de la definición.
        val byDefinition = resolver.traitsOf(Exercise(id = "x", name = "otro nombre", catalogDefinitionId = definition.id))
        assertEquals(traits(CatalogIds.BP), byDefinition)
    }

    @Test
    fun a_custom_exercise_falls_back_to_its_declared_muscles_and_then_to_its_name() {
        val declared = Exercise(
            id = "x",
            name = "Mi ejercicio especial",
            effectiveMuscles = listOf(
                InvolvedMuscle(muscle = "Pectorales", role = MuscleRole.PRIMARY),
                InvolvedMuscle(muscle = "Tríceps", role = MuscleRole.SECONDARY),
                InvolvedMuscle(muscle = "Deltoides", role = MuscleRole.SECONDARY, emphasis = "anterior"),
            ),
        )
        val fromMuscles = requireNotNull(resolver.traitsOf(declared))
        assertEquals(TraitSource.EXERCISE_MUSCLES, fromMuscles.source)
        assertEquals(setOf(KpknMuscleGroup.CHEST), fromMuscles.primaryMuscles)
        assertEquals(SplitGroup.PUSH, fromMuscles.group)
        assertTrue(fromMuscles.isCompound)

        val byName = mapOf(
            "Press de banca con barra" to (PatternFamily.HORIZONTAL_PUSH to KpknMuscleGroup.CHEST),
            "Remo con mancuerna" to (PatternFamily.HORIZONTAL_PULL to KpknMuscleGroup.BACK_LATS),
            "Jalón al pecho" to (PatternFamily.VERTICAL_PULL to KpknMuscleGroup.BACK_LATS),
            "Sentadilla búlgara" to (PatternFamily.SQUAT to KpknMuscleGroup.QUADS),
            "Curl femoral tumbado" to (PatternFamily.KNEE_FLEXION to KpknMuscleGroup.HAMS),
            "Peso muerto rumano" to (PatternFamily.HINGE to KpknMuscleGroup.HAMS),
            "Elevaciones laterales" to (PatternFamily.SHOULDER_ABDUCTION to KpknMuscleGroup.DELT_LATERAL),
            "Press militar de pie" to (PatternFamily.VERTICAL_PUSH to KpknMuscleGroup.DELT_FRONT),
            "Curl de bíceps con barra Z" to (PatternFamily.ELBOW_FLEXION to KpknMuscleGroup.BICEPS),
            "Extensión de tríceps en polea" to (PatternFamily.ELBOW_EXTENSION to KpknMuscleGroup.TRICEPS),
            "Elevación de talones" to null,
        )
        byName.forEach { (name, expected) ->
            val found = resolver.traitsOf(Exercise(id = "x", name = name))
            if (expected == null) {
                // «Elevación de talones» no tiene regla propia: o se resuelve por catálogo o queda sin clasificar.
                return@forEach
            }
            val traits = requireNotNull(found) { name }
            assertTrue("$name se clasifica", traits.pattern == expected.first || traits.source == TraitSource.CATALOG)
            if (traits.source == TraitSource.NAME) assertTrue(name, expected.second in traits.primaryMuscles)
        }
    }

    @Test
    fun name_rules_match_whole_words_so_variants_do_not_mislead() {
        fun pattern(name: String) = resolver.traitsOf(Exercise(id = "x", name = name))?.pattern
        assertEquals(PatternFamily.HORIZONTAL_PUSH, pattern("Narrow grip bench press"))
        assertEquals(PatternFamily.HORIZONTAL_PUSH, pattern("Press banca agarre cerrado"))
        assertEquals(PatternFamily.HORIZONTAL_PULL, pattern("Barbell row"))
        assertEquals(PatternFamily.ELBOW_FLEXION, pattern("Hammer curls"))
        assertNull("una máquina de remo no es un remo con peso", pattern("Rowing machine"))
    }

    // ─── Aproximación: la misma respuesta que el materializador y el generador ───────────────────────────────

    @Test
    fun the_approach_info_of_every_catalog_exercise_is_the_one_the_materializer_and_the_generator_use() {
        val metadata = CatalogCompositionMetadataProvider.fromCatalog(SplitTestSupport.catalog)
        val configurations = SplitTestSupport.catalog.families.flatMap { it.definitions }.flatMap { it.configurations }
        assertTrue(configurations.size > 500)
        val problems = configurations.mapNotNull { configuration ->
            val exercise = Exercise(id = "x", name = "x", catalogConfigurationId = configuration.id)
            val expected = ApproachInfoProvider.infoOf(exercise, metadata)
            val actual = resolver.approachInfoOf(exercise)
            if (expected != actual) "${configuration.id}: esperaba $expected y salió $actual" else null
        }
        assertEquals(emptyList<String>(), problems)
    }

    @Test
    fun a_bodyweight_exercise_with_declared_ballast_can_be_heavy_for_the_redistributor_exactly_as_in_the_materializer() {
        val id = "pull_up__pronated__medium"
        val plain = Exercise(id = "x", name = "x", catalogConfigurationId = id)
        assertFalse("la dominada sin lastre es peso corporal fácil", requireNotNull(resolver.approachInfoOf(plain)).canBeHeavy)
        val ballasted = plain.copy(loadQuantityConvention = LoadQuantityConvention.ADDITIONAL_BODYWEIGHT)
        assertTrue("con lastre declarado sí puede ser pesada", requireNotNull(resolver.approachInfoOf(ballasted)).canBeHeavy)
    }

    @Test
    fun an_exercise_without_catalog_identity_still_gets_its_approach_info_from_its_traits() {
        val declared = Exercise(
            id = "x",
            name = "Mi ejercicio especial",
            effectiveMuscles = listOf(
                InvolvedMuscle(muscle = "Pectorales", role = MuscleRole.PRIMARY),
                InvolvedMuscle(muscle = "Tríceps", role = MuscleRole.SECONDARY),
            ),
        )
        val traits = requireNotNull(resolver.traitsOf(declared))
        assertEquals(
            ApproachExerciseInfo(joints = traits.joints, isCompound = traits.isCompound, canBeHeavy = traits.canBeHeavy),
            resolver.approachInfoOf(declared),
        )
        assertNull(resolver.approachInfoOf(Exercise(id = "x", name = "Ejercicio inventado xyz")))
    }

    @Test
    fun an_unknown_exercise_has_no_traits() {
        assertNull(resolver.traitsOf(Exercise(id = "x", name = "Ejercicio inventado xyz")))
        assertNull(resolver.traitsOf(Exercise(id = "x", name = "")))
    }

    @Test
    fun the_resolver_is_stable_across_calls() {
        val a = traits(CatalogIds.BP)
        val b = traits(CatalogIds.BP)
        assertEquals(a, b)
    }
}
