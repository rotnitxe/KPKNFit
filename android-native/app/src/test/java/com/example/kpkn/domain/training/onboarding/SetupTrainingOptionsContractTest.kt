package com.example.kpkn.domain.training.onboarding

import com.example.kpkn.data.models.AutoregulationMode
import com.example.kpkn.data.models.EquipmentInventory
import com.example.kpkn.data.models.PlateStock
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.programs.TrainingFocus
import com.example.kpkn.data.protocols.SetRecipe
import com.example.kpkn.data.protocols.firstCompoundWarmupPercentSets
import com.example.kpkn.domain.onboarding.SetupTrainingOptions
import com.example.kpkn.domain.training.TrainingValidation
import com.example.kpkn.domain.training.PersonalizerInput
import com.example.kpkn.domain.training.WarmupPolicy
import com.example.kpkn.domain.training.orderPointsFromBag
import com.example.kpkn.domain.training.warmupPolicyOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contrato del `SetupTrainingOptions` que el draft de onboarding aplica al
 * motor: defaults PROPOSE (nunca AUTO escondido), inventario nullable honesto,
 * bolsa de orden SOLO orden (contrato compartido con el engine), aproximación
 * automática por defecto y edición sin mezclarse con recetas de autor.
 */
class SetupTrainingOptionsContractTest {

    /**
     * Cambió de sentido (Entreno v2): el calentamiento ya no es una opción del alta. `warmup == null` (el valor por
     * defecto) deja de significar «preset 40/60/80» y pasa a ser la aproximación AUTOMÁTICA (`ApproachPlanner`);
     * `resolvedWarmupSteps()` solo devuelve los pasos explícitos (ninguno por defecto).
     */
    @Test
    fun defaults_are_propose_with_null_inventory_and_automatic_warmups() {
        val options = SetupTrainingOptions()
        assertEquals(AutoregulationMode.PROPOSE, options.autoregulationMode)
        assertEquals(false, options.automaticConfirmed)
        assertNull("Sin inventario declarado no se afirma nada", options.inventory)
        assertTrue(options.validate() is TrainingValidation.Valid)
        assertEquals(WarmupPolicy.Automatic, options.warmupPolicy())
        assertEquals("sin pasos explícitos por defecto", emptyList<SetRecipe>(), options.resolvedWarmupSteps())
        // El preset del wizard antiguo sigue siendo una política válida si se pasa explícito.
        val preset = SetupTrainingOptions(warmup = firstCompoundWarmupPercentSets())
        val policy = preset.warmupPolicy() as WarmupPolicy.Explicit
        assertEquals(listOf(40.0, 60.0, 80.0), policy.steps.map { it.percent })
        assertEquals(listOf(8, 5, 3), policy.steps.map { it.reps })
        assertTrue(policy.steps.all { it.isWarmup })
        assertEquals(policy.steps, preset.resolvedWarmupSteps())
    }

    @Test
    fun the_warmup_policy_distinguishes_automatic_none_and_explicit() {
        assertEquals(WarmupPolicy.Automatic, warmupPolicyOf(null))
        assertEquals(WarmupPolicy.None, warmupPolicyOf(emptyList()))
        // Pasos sin porcentaje o fuera de rango no cuentan: sin pasos válidos = ninguna aproximación (no la automática).
        assertEquals(WarmupPolicy.None, warmupPolicyOf(listOf(SetRecipe(reps = 8), SetRecipe(reps = 0, percent = 40.0))))
        // Explícita: normalizada (orden ascendente, sin duplicados dentro de ±5 puntos).
        val explicit = warmupPolicyOf(
            listOf(
                SetRecipe(reps = 3, percent = 80.0, isWarmup = true),
                SetRecipe(reps = 8, percent = 40.0, isWarmup = true),
                SetRecipe(reps = 8, percent = 43.0, isWarmup = true),
            ),
        ) as WarmupPolicy.Explicit
        assertEquals(listOf(40.0, 80.0), explicit.steps.map { it.percent })
        assertEquals(WarmupPolicy.None, SetupTrainingOptions(warmup = emptyList()).warmupPolicy())
    }

    @Test
    fun auto_requires_explicit_confirmation_never_coerced() {
        val unconfirmed = SetupTrainingOptions(autoregulationMode = AutoregulationMode.AUTO)
        val invalid = unconfirmed.validate() as TrainingValidation.Invalid
        assertTrue(invalid.reasons.joinToString(" "), invalid.reasons.any { it.contains("confirmación", ignoreCase = true) })

        val confirmed = SetupTrainingOptions(autoregulationMode = AutoregulationMode.AUTO, automaticConfirmed = true)
        assertTrue(confirmed.validate() is TrainingValidation.Valid)
    }

    @Test
    fun order_bag_contract_is_shared_with_the_engine() {
        // Inválidas: > 2 por músculo, > 5 en total y ningún negativo.
        assertTrue(SetupTrainingOptions(orderPriorities = mapOf("Pectorales" to 3)).validate() is TrainingValidation.Invalid)
        assertTrue(SetupTrainingOptions(orderPriorities = mapOf("Pectorales" to 2, "Dorsales" to 2, "Tríceps" to 2)).validate() is TrainingValidation.Invalid)
        assertTrue(SetupTrainingOptions(orderPriorities = mapOf("Pectorales" to -1)).validate() is TrainingValidation.Invalid)
        assertTrue(SetupTrainingOptions(orderPriorities = mapOf("Pectorales" to 0)).validate() is TrainingValidation.Valid)
        assertTrue(SetupTrainingOptions(orderPriorities = mapOf(" Pectorales " to 1)).validate() is TrainingValidation.Valid)
        // Válida: 2 + 2 + 1 = 5 en el límite.
        assertTrue(SetupTrainingOptions(orderPriorities = mapOf("Pectorales" to 2, "Dorsales" to 2, "Tríceps" to 1)).validate() is TrainingValidation.Valid)
        // La canonicalización vive en el mismo sitio que usa el engine.
        assertEquals(mapOf("Pectorales" to 1), orderPointsFromBag(mapOf("Pecho" to 1)))
        assertNull(orderPointsFromBag(mapOf("Pecho" to 3)))
    }

    @Test
    fun inventory_and_warmup_validation_is_honest() {
        val badPlates = EquipmentInventory(barbellWeightKg = -20.0, plates = listOf(PlateStock(weightKg = 10.0)))
        assertTrue(SetupTrainingOptions(inventory = badPlates).validate() is TrainingValidation.Invalid)
        val negativeCount = EquipmentInventory(
            barbellWeightKg = 20.0,
            plates = listOf(PlateStock(weightKg = 10.0, countPerSide = -1)),
        )
        assertTrue(SetupTrainingOptions(inventory = negativeCount).validate() is TrainingValidation.Invalid)
        val good = EquipmentInventory(barbellWeightKg = 20.0, plates = listOf(PlateStock(weightKg = 10.0, countPerSide = 2)))
        assertTrue(SetupTrainingOptions(inventory = good).validate() is TrainingValidation.Valid)

        // Paso sin porcentaje o con reps fuera de rango → contrato roto.
        assertTrue(SetupTrainingOptions(warmup = listOf(SetRecipe(reps = 8))).validate() is TrainingValidation.Invalid)
        assertTrue(SetupTrainingOptions(warmup = listOf(SetRecipe(reps = 0, percent = 40.0))).validate() is TrainingValidation.Invalid)
        assertTrue(SetupTrainingOptions(warmup = listOf(SetRecipe(reps = 5, percent = 40.0))).validate() is TrainingValidation.Valid)
        // Vacío = sin calentamientos automáticos (no el preset).
        assertEquals(emptyList<SetRecipe>(), SetupTrainingOptions(warmup = emptyList()).resolvedWarmupSteps())
    }

    @Test
    fun applyTo_input_only_overrides_the_order_bag_when_nonEmpty() {
        val input = PersonalizerInput(
            catalogEntryId = "native:machine-muscle",
            focus = TrainingFocus.FULL_BODY,
            frequency = 3,
        )
        val untouched = SetupTrainingOptions().applyTo(input)
        assertTrue(untouched.exerciseOrderPriorities.isEmpty())

        val applied = SetupTrainingOptions(orderPriorities = mapOf("Bíceps" to 2)).applyTo(input)
        assertEquals(mapOf("Bíceps" to 2), applied.exerciseOrderPriorities)

        // La bolsa de options gana sobre las prioridades heredadas del input.
        val legacy = input.copy(priorityMuscles = setOf("Tríceps"))
        val merged = SetupTrainingOptions(orderPriorities = mapOf("Bíceps" to 2)).applyTo(legacy)
        assertEquals(mapOf("Bíceps" to 2), merged.exerciseOrderPriorities)
        assertEquals(setOf("Tríceps"), merged.priorityMuscles)
    }

    @Test
    fun applyTo_program_sets_only_the_chosen_autoregulation_mode() {
        val base = Program(id = "p", name = "T", autoregulationMode = AutoregulationMode.OFF)
        val applied = SetupTrainingOptions(autoregulationMode = AutoregulationMode.PROPOSE).applyTo(base)
        assertEquals(AutoregulationMode.PROPOSE, applied.autoregulationMode)
        // Estructura y receta intactas: no se reescribe nada.
        assertEquals(base.macrocycles, applied.macrocycles)
        assertEquals(base.sourceRecipe, applied.sourceRecipe)
        assertEquals(base.sourceProtocolId, applied.sourceProtocolId)
    }
}