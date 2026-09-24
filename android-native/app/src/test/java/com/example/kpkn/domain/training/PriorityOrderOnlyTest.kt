package com.example.kpkn.domain.training

import com.example.kpkn.data.exercises.catalogv2.toLegacyConfigurationLookup
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.programs.CatalogLevel
import com.example.kpkn.data.programs.TrainingFocus
import com.example.kpkn.data.protocols.PROTOCOL_LIBRARY
import com.example.kpkn.data.protocols.isVisibleForApplication
import com.example.kpkn.domain.exercises.catalogv2.InMemoryExerciseCatalogRepositoryV2
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contrato: las prioridades musculares son SOLO ORDEN.
 *
 * La bolsa de puntos (máximo 2 por músculo, 5 en total, sin negativos) solo
 * puede cambiar el orden de los ejercicios. Prohibido alterar ejercicios
 * prescritos, series, repeticiones, intensidades, frecuencia muscular ni
 * volumen directo/indirecto. `lowerEmphasisMuscles` no tiene ningún efecto.
 */
class PriorityOrderOnlyTest {
    private val catalog get() = CatalogCompositionTestSupport.catalog
    private fun personalizer() = SimpleCyclePersonalizer(InMemoryExerciseCatalogRepositoryV2(catalog).also { runBlocking { it.load() } })

    /** Día "Brazos" para que bíceps y tríceps convivan en la misma sesión. */
    private val baseInput = PersonalizerInput(
        catalogEntryId = "native:machine-muscle",
        focus = TrainingFocus.FULL_BODY,
        frequency = 3,
        weekdays = listOf(1, 3, 5),
        equipment = setOf("machine"),
        level = CatalogLevel.INTERMEDIATE,
        availableMinutes = 90,
        splitId = "custom",
        splitPattern = listOf("Pecho", "Descanso", "Brazos", "Descanso", "Piernas", "Descanso", "Descanso"),
        splitName = "Orden",
    )

    private fun sessionsOf(program: Program) =
        program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }.flatMap { it.sessions }

    private fun rowsByMuscle(result: PersonalizationResult): Map<String, MuscleBudgetReport> =
        result.report.muscles.associateBy { it.muscle }

    private fun assertSameVolumeAndFrequency(a: PersonalizationResult, b: PersonalizationResult) {
        val rowsA = rowsByMuscle(a)
        val rowsB = rowsByMuscle(b)
        assertEquals(rowsA.keys, rowsB.keys)
        rowsA.forEach { (muscle, rowA) ->
            val rowB = rowsB.getValue(muscle)
            assertEquals("$muscle volumen directo", rowA.directSets, rowB.directSets, 0.0001)
            assertEquals("$muscle volumen indirecto", rowA.indirectSets, rowB.indirectSets, 0.0001)
            assertEquals("$muscle frecuencia", rowA.frequency.toLong(), rowB.frequency.toLong())
            assertEquals("$muscle objetivo", rowA.targetSets, rowB.targetSets, 0.0001)
        }
    }

    private fun prescriptionOf(session: Session): List<String> = session.exercises.map { exercise ->
        val sets = exercise.sets.joinToString(",") { "${it.targetReps}:${it.targetRIR}" }
        "${exercise.catalogConfigurationId}/${exercise.sets.size}/$sets"
    }.sorted()

    private fun prescriptions(program: Program): List<List<String>> = sessionsOf(program).map(::prescriptionOf)

    private fun orderOf(session: Session): List<String?> = session.exercises.map { it.catalogConfigurationId }

    private fun directMusclesOf(exercise: Exercise): Set<String> {
        val lookup = catalog.toLegacyConfigurationLookup()
        val volume = VolumeCalculator.calculateRoleSeparatedMuscleVolume(
            listOf(Session("probe", "probe", exercises = listOf(exercise))),
            lookup.values.toList(),
        )
        return volume.filterValues { it.directSets > 0.0 }.keys
    }

    @Test
    fun orderPointBagsChangeOnlyExerciseOrderNotPrescriptionOrVolume() {
        val bicepsFirst = personalizer().personalize("orden-biceps", baseInput.copy(exerciseOrderPriorities = mapOf("Bíceps" to 2)))
        val tricepsFirst = personalizer().personalize("orden-triceps", baseInput.copy(exerciseOrderPriorities = mapOf("Tríceps" to 2)))
        val noPoints = personalizer().personalize("orden-sin-puntos", baseInput)
        val programBiceps = requireNotNull(bicepsFirst.program) { bicepsFirst.report.limitations.toString() }
        val programTriceps = requireNotNull(tricepsFirst.program) { tricepsFirst.report.limitations.toString() }
        val programNoPoints = requireNotNull(noPoints.program) { noPoints.report.limitations.toString() }

        // Dos bolsas distintas (y ninguna) producen exactamente el mismo
        // volumen directo, indirecto y frecuencia por músculo.
        assertSameVolumeAndFrequency(bicepsFirst, tricepsFirst)
        assertSameVolumeAndFrequency(noPoints, bicepsFirst)
        assertSameVolumeAndFrequency(noPoints, tricepsFirst)

        // La prescripción no cambia: mismos ejercicios, series, repeticiones e intensidades.
        assertEquals(prescriptions(programNoPoints), prescriptions(programBiceps))
        assertEquals(prescriptions(programNoPoints), prescriptions(programTriceps))
        sessionsOf(programBiceps).zip(sessionsOf(programTriceps)).forEach { (a, b) ->
            assertEquals(a.name, b.name)
            assertEquals(prescriptionOf(a), prescriptionOf(b))
        }

        // Solo cambia el orden, y cambia hacia el músculo puntuado (2 → 1 → 0).
        val armsBiceps = sessionsOf(programBiceps).first { it.name == "Brazos" }
        val armsTriceps = sessionsOf(programTriceps).first { it.name == "Brazos" }
        assertNotEquals(orderOf(armsBiceps), orderOf(armsTriceps))
        assertTrue("Con 2 puntos en Bíceps su ejercicio va primero", "Bíceps" in directMusclesOf(armsBiceps.exercises.first()))
        assertTrue("Con 2 puntos en Tríceps su ejercicio va primero", "Tríceps" in directMusclesOf(armsTriceps.exercises.first()))
    }

    @Test
    fun orderPointBudgetCapsAreRespected() {
        val planner = personalizer()
        // Válida: 2 + 2 + 1 = 5 con máximo 2 por músculo.
        assertEquals(
            mapOf("Pectorales" to 2, "Dorsales" to 2, "Tríceps" to 1),
            planner.orderPoints(baseInput.copy(exerciseOrderPriorities = mapOf("Pectorales" to 2, "Dorsales" to 2, "Tríceps" to 1))),
        )
        // No es obligatorio gastar los cinco puntos.
        assertEquals(
            mapOf("Glúteos" to 1),
            planner.orderPoints(baseInput.copy(exerciseOrderPriorities = mapOf("Glúteos" to 1))),
        )
        // Máximo 2 puntos por músculo.
        assertNull(planner.orderPoints(baseInput.copy(exerciseOrderPriorities = mapOf("Pectorales" to 3))))
        // Máximo 5 puntos en total.
        assertNull(
            planner.orderPoints(baseInput.copy(exerciseOrderPriorities = mapOf("Pectorales" to 2, "Dorsales" to 2, "Tríceps" to 2))),
        )
        // Nada de negativos: la despriorización no existe.
        assertNull(planner.orderPoints(baseInput.copy(exerciseOrderPriorities = mapOf("Pectorales" to -1))))
        // También aplica a las prioridades heredadas (1 punto por músculo).
        assertEquals(
            mapOf("Pectorales" to 1, "Bíceps" to 1),
            planner.orderPoints(baseInput.copy(priorityMuscles = setOf("Pecho", "Bíceps"))),
        )
        assertNull(planner.orderPoints(baseInput.copy(priorityMuscles = setOf("Pecho", "Bíceps", "Tríceps", "Dorsales", "Glúteos", "Cuádriceps"))))

        // Una bolsa inválida se rechaza con motivo y sin programa.
        val rejected = planner.personalize("bolsa-invalida", baseInput.copy(exerciseOrderPriorities = mapOf("Pectorales" to 5)))
        assertNull(rejected.program)
        assertTrue(rejected.report.limitations.toString(), rejected.report.limitations.any { it.contains("2 puntos") })
    }

    @Test
    fun lowerEmphasisMusclesNoLongerAffectsVolumeOrderOrPrescription() {
        val plain = personalizer().personalize("sin-despriorizar", baseInput)
        val lowered = personalizer().personalize(
            "despriorizado",
            baseInput.copy(lowerEmphasisMuscles = setOf("Pectorales", "Tríceps", "Pantorrillas")),
        )
        val a = requireNotNull(plain.program) { plain.report.limitations.toString() }
        val b = requireNotNull(lowered.program) { lowered.report.limitations.toString() }
        assertSameVolumeAndFrequency(plain, lowered)
        // Los ids de programa difieren por construcción ("sin-despriorizar" frente a
        // "despriorizado"), así que la igualdad se comprueba sobre lo que promete el
        // test: prescripción y orden de ejercicios, no sobre el objeto Program entero.
        assertEquals("lowerEmphasisMuscles no puede cambiar la prescripción", prescriptions(a), prescriptions(b))
        assertEquals(
            "lowerEmphasisMuscles no puede cambiar el orden",
            sessionsOf(a).map { orderOf(it) },
            sessionsOf(b).map { orderOf(it) },
        )
    }

    @Test
    fun legacyPriorityMusclesAffectOnlyOrderNotVolume() {
        val plain = personalizer().personalize("heredada-base", baseInput)
        val prioritized = personalizer().personalize("heredada-triceps", baseInput.copy(priorityMuscles = setOf("Tríceps")))
        val a = requireNotNull(plain.program) { plain.report.limitations.toString() }
        val b = requireNotNull(prioritized.program) { prioritized.report.limitations.toString() }
        assertSameVolumeAndFrequency(plain, prioritized)
        assertEquals(prescriptions(a), prescriptions(b))
        val arms = sessionsOf(b).first { it.name == "Brazos" }
        assertTrue("La prioridad heredada solo ordena (1 punto)", "Tríceps" in directMusclesOf(arms.exercises.first()))
    }

    @Test
    fun protocolRecipesExplainThatPrioritiesAndSplitCannotBeApplied() {
        val visibleProtocols = PROTOCOL_LIBRARY.filter { it.isVisibleForApplication }
        assertTrue(visibleProtocols.isNotEmpty())
        visibleProtocols.forEach { protocol ->
            val result = personalizer().personalize(
                "protocolo-${protocol.id}",
                PersonalizerInput(
                    catalogEntryId = "protocol:${protocol.id}",
                    focus = TrainingFocus.FULL_BODY,
                    frequency = protocol.recipe?.daysPerWeek ?: 3,
                    exerciseOrderPriorities = mapOf("Pectorales" to 2),
                    splitId = "ul_x4",
                ),
            )
            assertNull("${protocol.id} debe conservar su receta", result.program)
            val reason = result.report.limitations.joinToString(" ")
            assertTrue("$protocol -> $reason", reason.contains("receta original"))
            assertNotNull("El protocolo sigue disponible por su receta, sin reescribirse", result.report.provenance)
        }
    }
}
