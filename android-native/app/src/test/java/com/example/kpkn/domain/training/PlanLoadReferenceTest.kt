package com.example.kpkn.domain.training

import com.example.kpkn.data.models.LoadQuantityConvention
import com.example.kpkn.data.protocols.PlanLoadReference
import com.example.kpkn.data.protocols.PlanLoadReferenceKind
import com.example.kpkn.data.protocols.PlanLoadReferenceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §14.2: resolutor de carga por configuración y convención (los 6 oráculos
 * numéricos de §17.4 + reglas duras de la API expuesta a los 3 call sites).
 * Ningún caso se resuelve con ×2/÷2 implícitos ni con bases heredadas de otra
 * configuración; sin referencia el peso queda PENDING (null ≠ 0 kg ni NaN).
 */
class PlanLoadReferenceTest {
    private val benchBarbell = "bench_press__barbell"
    private val benchDumbbells = "bench_press__dumbbells"
    private val pullUp = "pull_up__pronated__medium"

    private fun reference(
        kind: PlanLoadReferenceKind,
        configurationId: String,
        convention: LoadQuantityConvention,
        capturedKg: Double? = null,
        repMin: Int? = null,
        repMax: Int? = null,
        state: PlanLoadReferenceState = PlanLoadReferenceState.CAPTURED,
    ): PlanLoadReference = PlanLoadReference(
        kind = kind,
        configurationId = configurationId,
        quantityConvention = convention,
        repMin = repMin,
        repMax = repMax,
        state = state,
        capturedLoadKg = capturedKg,
    )

    // ─── Oráculo 1: 1RM de barra ≠ carga de mancuerna ────────────────────────

    @Test
    fun barbell_bench_1rm_never_produces_a_dumbbell_load() {
        val oneRm = reference(
            kind = PlanLoadReferenceKind.EXERCISE_1RM,
            configurationId = benchBarbell,
            convention = LoadQuantityConvention.TOTAL_EXTERNAL,
            capturedKg = 100.0,
        )
        val speed = PlanLoadResolver.resolveSpeedLoad(
            reference = oneRm,
            targetConfigurationId = benchDumbbells,
            targetConvention = LoadQuantityConvention.PER_IMPLEMENT,
        )
        val generic = PlanLoadResolver.resolveReference(
            reference = oneRm,
            targetConfigurationId = benchDumbbells,
            targetConvention = LoadQuantityConvention.PER_IMPLEMENT,
        )
        assertTrue("1RM de barra no basea velocidad de mancuernas: $speed", speed is PlanLoadResolution.Unrepresentable)
        assertTrue("1RM de barra no se transfiere a mancuernas: $generic", generic is PlanLoadResolution.Unrepresentable)
        listOf(speed, generic).forEach { resolution ->
            if (resolution is PlanLoadResolution.Resolved) {
                // 65 kg sería el 65 % de la barra aplicado a la mancuerna.
                assertNotEquals(65.0, resolution.loadKg, 0.0001)
            }
        }

        // En SU configuración y SU convención la referencia sí resuelve.
        val onBarbell = PlanLoadResolver.resolveReference(
            reference = oneRm,
            targetConfigurationId = benchBarbell,
            targetConvention = LoadQuantityConvention.TOTAL_EXTERNAL,
        )
        assertTrue(onBarbell is PlanLoadResolution.Resolved)
        assertEquals(100.0, (onBarbell as PlanLoadResolution.Resolved).loadKg, 0.0001)
    }

    // ─── Oráculo 2: 30 kg por mancuerna → SPEED 19,5-21 kg por mancuerna ─────

    @Test
    fun speed_from_dumbbell_working_set_is_per_implementation() {
        val resolution = PlanLoadResolver.resolveSpeedLoad(
            reference = null,
            targetConfigurationId = benchDumbbells,
            targetConvention = LoadQuantityConvention.PER_IMPLEMENT,
            observation = ObservedWorkingSet(
                configurationId = benchDumbbells,
                quantityConvention = LoadQuantityConvention.PER_IMPLEMENT,
                loadKg = 30.0,
                reps = 4,
                sourceSlotId = "heavy-t1",
            ),
        )
        assertTrue("Se espera carga resuelta: $resolution", resolution is PlanLoadResolution.Resolved)
        val resolved = resolution as PlanLoadResolution.Resolved
        assertEquals(19.5, resolved.loadKg, 0.0001)
        assertEquals(19.5, resolved.targetRange.start, 0.0001)
        assertEquals(21.0, resolved.targetRange.endInclusive, 0.0001)
        assertEquals(30.0, resolved.baseLoadKg, 0.0001)
        assertEquals(PlanLoadResolver.SPEED_BASIS_LABEL, resolved.basisLabel)
        assertEquals(LoadQuantityConvention.PER_IMPLEMENT, resolved.quantityConvention)
        assertEquals(PlanLoadReferenceKind.OBSERVED_WORKING_SET, resolved.sourceReference.kind)
        assertEquals(30.0, resolved.sourceReference.capturedLoadKg!!, 0.0001)
        assertEquals(4, resolved.sourceReference.repMin ?: -1)
        // 39-42 kg sería el total de las dos mancuernas: solo se admite con su
        // propia etiqueta, aquí la base es POR IMPLEMENTO.
        assertTrue(
            "Nunca el total sin etiqueta: ${resolved.targetRange}",
            resolved.targetRange.endInclusive < 39.0,
        )
    }

    // ─── Oráculo 3: trabajo observado 100 kg → 65-70, distinto de TM 90 ──────

    @Test
    fun speed_range_uses_observed_working_set_not_training_max() {
        val fromWorking = PlanLoadResolver.resolveSpeedLoad(
            reference = null,
            targetConfigurationId = benchBarbell,
            targetConvention = LoadQuantityConvention.TOTAL_EXTERNAL,
            observation = ObservedWorkingSet(
                configurationId = benchBarbell,
                quantityConvention = LoadQuantityConvention.TOTAL_EXTERNAL,
                loadKg = 100.0,
                reps = 3,
            ),
        )
        assertTrue(fromWorking is PlanLoadResolution.Resolved)
        val working = fromWorking as PlanLoadResolution.Resolved
        assertEquals(65.0, working.loadKg, 0.0001)
        assertEquals(70.0, working.targetRange.endInclusive, 0.0001)
        assertEquals(100.0, working.baseLoadKg, 0.0001)
        assertEquals(PlanLoadResolver.SPEED_BASIS_LABEL, working.basisLabel)

        val tmRange = PlanLoadResolver.speedRange(90.0)
        assertEquals(58.5, tmRange.start, 0.0001)
        assertEquals(63.0, tmRange.endInclusive, 0.0001)
        assertNotEquals(working.targetRange.start, tmRange.start, 0.0001)
        assertNotEquals(working.targetRange.endInclusive, tmRange.endInclusive, 0.0001)
        assertEquals(PlanLoadResolver.SPEED_DEFAULT_FRACTION, 0.65, 0.0001)
        assertEquals(PlanLoadResolver.SPEED_MAX_FRACTION, 0.70, 0.0001)

        // Un TM capturado JAMÁS basea la carga de velocidad.
        val tmReference = reference(
            kind = PlanLoadReferenceKind.EXERCISE_TM,
            configurationId = benchBarbell,
            convention = LoadQuantityConvention.TOTAL_EXTERNAL,
            capturedKg = 90.0,
        )
        val fromTm = PlanLoadResolver.resolveSpeedLoad(
            reference = tmReference,
            targetConfigurationId = benchBarbell,
            targetConvention = LoadQuantityConvention.TOTAL_EXTERNAL,
        )
        assertTrue("TM (90 kg) no es trabajo observado de 3-5 reps: $fromTm", fromTm is PlanLoadResolution.Unrepresentable)
    }

    // ─── Oráculo 4: lastre 20 kg ≠ asistencia 20 kg ─────────────────────────

    @Test
    fun lastre_and_assistance_are_never_interchanged() {
        val lastre = reference(
            kind = PlanLoadReferenceKind.BODYWEIGHT_EXTERNAL,
            configurationId = pullUp,
            convention = LoadQuantityConvention.ADDITIONAL_BODYWEIGHT,
            capturedKg = 20.0,
        )
        val asistencia = reference(
            kind = PlanLoadReferenceKind.BODYWEIGHT_EXTERNAL,
            configurationId = pullUp,
            convention = LoadQuantityConvention.ASSISTANCE,
            capturedKg = 20.0,
        )

        val asLastre = PlanLoadResolver.resolveReference(lastre, pullUp, LoadQuantityConvention.ADDITIONAL_BODYWEIGHT)
        assertTrue(asLastre is PlanLoadResolution.Resolved)
        assertEquals(20.0, (asLastre as PlanLoadResolution.Resolved).loadKg, 0.0001)

        val crossed = PlanLoadResolver.resolveReference(lastre, pullUp, LoadQuantityConvention.ASSISTANCE)
        assertTrue("Lastre 20 kg nunca es asistencia 20 kg: $crossed", crossed is PlanLoadResolution.Unrepresentable)
        val crossedBack = PlanLoadResolver.resolveReference(asistencia, pullUp, LoadQuantityConvention.ADDITIONAL_BODYWEIGHT)
        assertTrue("Asistencia 20 kg nunca es lastre 20 kg: $crossedBack", crossedBack is PlanLoadResolution.Unrepresentable)

        // Convención destino UNSPECIFIED acepta la de la referencia, sin ×2/÷2.
        val unspecified = PlanLoadResolver.resolveReference(lastre, pullUp, LoadQuantityConvention.UNSPECIFIED)
        assertTrue(unspecified is PlanLoadResolution.Resolved)
        val resolved = unspecified as PlanLoadResolution.Resolved
        assertEquals(20.0, resolved.loadKg, 0.0001)
        assertEquals(LoadQuantityConvention.ADDITIONAL_BODYWEIGHT, resolved.quantityConvention)

        val speed = PlanLoadResolver.resolveSpeedLoad(
            reference = null,
            targetConfigurationId = pullUp,
            targetConvention = LoadQuantityConvention.ASSISTANCE,
            observation = ObservedWorkingSet(
                configurationId = pullUp,
                quantityConvention = LoadQuantityConvention.ADDITIONAL_BODYWEIGHT,
                loadKg = 20.0,
                reps = 4,
            ),
        )
        assertTrue("Velocidad con otra convención no es representable: $speed", speed is PlanLoadResolution.Unrepresentable)
    }

    // ─── Oráculo 5: sin referencia → PENDING, nunca 0 kg ni NaN ─────────────

    @Test
    fun missing_reference_is_pending_never_zero_kg() {
        val speed = PlanLoadResolver.resolveSpeedLoad(
            reference = null,
            targetConfigurationId = benchDumbbells,
            targetConvention = LoadQuantityConvention.PER_IMPLEMENT,
            pendingRir = 2,
            pendingRepRange = 8..12,
        )
        assertTrue("Sin referencia no hay carga: $speed", speed is PlanLoadResolution.Pending)
        val pending = speed as PlanLoadResolution.Pending
        assertTrue(pending.detail.contains("null ≠ 0 kg"))
        assertEquals(2, pending.targetRir ?: -1)
        assertEquals(IntRange(8, 12), pending.repRange)

        val generic = PlanLoadResolver.resolveReference(null, benchDumbbells, LoadQuantityConvention.PER_IMPLEMENT)
        assertTrue(generic is PlanLoadResolution.Pending)

        val notCaptured = reference(
            kind = PlanLoadReferenceKind.OBSERVED_WORKING_SET,
            configurationId = benchDumbbells,
            convention = LoadQuantityConvention.PER_IMPLEMENT,
            repMin = 3,
            repMax = 5,
            state = PlanLoadReferenceState.PENDING,
        )
        val pendingReference = PlanLoadResolver.resolveSpeedLoad(
            reference = notCaptured,
            targetConfigurationId = benchDumbbells,
            targetConvention = LoadQuantityConvention.PER_IMPLEMENT,
        )
        assertTrue(pendingReference is PlanLoadResolution.Pending)

        listOf(speed, generic, pendingReference).forEach { resolution ->
            assertTrue("Nunca kg resueltos sin referencia: $resolution", resolution !is PlanLoadResolution.Resolved)
        }
    }

    // ─── Oráculo 6: redondeo solo dentro del rango ───────────────────────────

    @Test
    fun rounding_only_realizes_realizable_loads_inside_the_range() {
        val range = PlanLoadResolver.speedRange(30.0) // 19,5-21,0 kg

        val realized = PlanLoadResolver.roundWithinRange(range, listOf(21.0, 20.5, 20.0))
        assertTrue(realized is LoadRounding.Realized)
        assertEquals("El más cercano al límite inferior", 20.0, (realized as LoadRounding.Realized).loadKg, 0.0001)

        val withStockOutside = PlanLoadResolver.roundWithinRange(range, listOf(5.0, 20.0, 40.0))
        assertTrue(withStockOutside is LoadRounding.Realized)
        assertEquals(20.0, (withStockOutside as LoadRounding.Realized).loadKg, 0.0001)

        assertTrue(
            "Sin inventario el rango sigue visible",
            PlanLoadResolver.roundWithinRange(range, emptyList()) is LoadRounding.RangeKept,
        )

        val discrepancy = PlanLoadResolver.roundWithinRange(range, listOf(10.0, 30.0))
        assertTrue("Nada alcanzable dentro del rango: $discrepancy", discrepancy is LoadRounding.Discrepancy)
        assertEquals("30 kg está a 9,0 kg y 10 kg a 9,5 kg", 30.0, (discrepancy as LoadRounding.Discrepancy).nearestKg, 0.0001)
    }

    // ─── Reglas duras de la ventana de trabajo y captura ─────────────────────

    @Test
    fun speed_load_requires_a_three_to_five_rep_window() {
        listOf(8, 2).forEach { reps ->
            val outside = PlanLoadResolver.resolveSpeedLoad(
                reference = null,
                targetConfigurationId = benchBarbell,
                targetConvention = LoadQuantityConvention.TOTAL_EXTERNAL,
                observation = ObservedWorkingSet(
                    configurationId = benchBarbell,
                    quantityConvention = LoadQuantityConvention.TOTAL_EXTERNAL,
                    loadKg = 100.0,
                    reps = reps,
                ),
            )
            assertTrue("Serie de $reps reps no es base de velocidad: $outside", outside is PlanLoadResolution.Unrepresentable)
        }

        val declaredWide = reference(
            kind = PlanLoadReferenceKind.OBSERVED_WORKING_SET,
            configurationId = benchBarbell,
            convention = LoadQuantityConvention.TOTAL_EXTERNAL,
            capturedKg = 100.0,
            repMin = 8,
            repMax = 12,
        )
        val wide = PlanLoadResolver.resolveSpeedLoad(
            reference = declaredWide,
            targetConfigurationId = benchBarbell,
            targetConvention = LoadQuantityConvention.TOTAL_EXTERNAL,
        )
        assertTrue(wide is PlanLoadResolution.Unrepresentable)

        val otherConfiguration = PlanLoadResolver.resolveSpeedLoad(
            reference = null,
            targetConfigurationId = benchDumbbells,
            targetConvention = LoadQuantityConvention.TOTAL_EXTERNAL,
            observation = ObservedWorkingSet(
                configurationId = benchBarbell,
                quantityConvention = LoadQuantityConvention.TOTAL_EXTERNAL,
                loadKg = 100.0,
                reps = 4,
            ),
        )
        assertTrue(
            "La serie observada solo aplica a su configuración: $otherConfiguration",
            otherConfiguration is PlanLoadResolution.Unrepresentable,
        )

        val invalidKg = PlanLoadResolver.resolveSpeedLoad(
            reference = null,
            targetConfigurationId = benchBarbell,
            targetConvention = LoadQuantityConvention.TOTAL_EXTERNAL,
            observation = ObservedWorkingSet(
                configurationId = benchBarbell,
                quantityConvention = LoadQuantityConvention.TOTAL_EXTERNAL,
                loadKg = 0.0,
                reps = 4,
            ),
        )
        assertTrue(invalidKg is PlanLoadResolution.Unrepresentable)
    }

    @Test
    fun speed_resolution_rejects_1rm_tm_and_bodyweight_bases() {
        listOf(
            PlanLoadReferenceKind.EXERCISE_1RM,
            PlanLoadReferenceKind.EXERCISE_TM,
            PlanLoadReferenceKind.BODYWEIGHT_EXTERNAL,
        ).forEach { kind ->
            val base = reference(
                kind = kind,
                configurationId = benchBarbell,
                convention = LoadQuantityConvention.TOTAL_EXTERNAL,
                capturedKg = 100.0,
            )
            val resolution = PlanLoadResolver.resolveSpeedLoad(
                reference = base,
                targetConfigurationId = benchBarbell,
                targetConvention = LoadQuantityConvention.TOTAL_EXTERNAL,
            )
            assertTrue("Base $kind no fundamenta SPEED: $resolution", resolution is PlanLoadResolution.Unrepresentable)
        }
    }

    @Test
    fun capture_only_accepts_finite_working_sets_inside_the_window() {
        assertNull(PlanLoadResolver.captureObservedWorkingSet(configurationId = benchBarbell, loadKg = 100.0, reps = 6))
        assertNull(PlanLoadResolver.captureObservedWorkingSet(configurationId = benchBarbell, loadKg = 100.0, reps = 2))
        assertNull(PlanLoadResolver.captureObservedWorkingSet(configurationId = benchBarbell, loadKg = 0.0, reps = 4))
        assertNull(PlanLoadResolver.captureObservedWorkingSet(configurationId = benchBarbell, loadKg = -5.0, reps = 4))

        val captured = PlanLoadResolver.captureObservedWorkingSet(
            configurationId = benchBarbell,
            quantityConvention = LoadQuantityConvention.TOTAL_EXTERNAL,
            loadKg = 100.0,
            reps = 5,
            sourceSlotId = "heavy-t1",
            sourceWeekOccurrence = 2,
        )
        assertTrue(captured != null)
        assertEquals(PlanLoadReferenceKind.OBSERVED_WORKING_SET, captured!!.kind)
        assertEquals(PlanLoadReferenceState.CAPTURED, captured.state)
        assertEquals(5, captured.repMin ?: -1)
        assertEquals(5, captured.repMax ?: -1)
        assertEquals(100.0, captured.capturedLoadKg!!, 0.0001)
        assertEquals("heavy-t1", captured.sourceSlotId)
        assertEquals(2, captured.sourceWeekOccurrence ?: -1)
    }

    @Test
    fun explicit_light_load_change_captures_a_copy_and_keeps_the_original() {
        val original = reference(
            kind = PlanLoadReferenceKind.OBSERVED_WORKING_SET,
            configurationId = benchDumbbells,
            convention = LoadQuantityConvention.PER_IMPLEMENT,
            repMin = 3,
            repMax = 5,
            state = PlanLoadReferenceState.PENDING,
        )
        val explicit = PlanLoadResolver.explicitLightLoadChange(
            reference = original,
            chosenLoadKg = 20.0,
            slotId = "s-speed",
            prescriptionBefore = "pendiente",
            prescriptionAfter = "20 kg por mancuerna",
        )
        assertTrue("Carga válida debe registrarse: $explicit", explicit != null)
        assertEquals(PlanLoadReferenceState.CAPTURED, explicit!!.reference.state)
        assertEquals(20.0, explicit.reference.capturedLoadKg!!, 0.0001)
        assertEquals("s-speed", explicit.change.slotId)
        assertEquals("bench_press__dumbbells", explicit.change.fromConfigurationId)
        assertEquals("bench_press__dumbbells", explicit.change.toConfigurationId)
        assertTrue(explicit.change.loadReferenceKept == true)
        // La referencia declarada original queda intacta (null ≠ 0 kg).
        assertEquals(PlanLoadReferenceState.PENDING, original.state)
        assertNull(original.capturedLoadKg)

        assertNull(PlanLoadResolver.explicitLightLoadChange(original, 0.0, "s-speed"))
        assertNull(PlanLoadResolver.explicitLightLoadChange(original, -3.0, "s-speed"))
    }

    @Test
    fun resolve_reference_labels_the_basis_of_the_same_configuration() {
        val oneRm = reference(
            kind = PlanLoadReferenceKind.EXERCISE_1RM,
            configurationId = benchBarbell,
            convention = LoadQuantityConvention.TOTAL_EXTERNAL,
            capturedKg = 100.0,
        )
        val resolved = PlanLoadResolver.resolveReference(oneRm, benchBarbell, LoadQuantityConvention.TOTAL_EXTERNAL)
        assertTrue(resolved is PlanLoadResolution.Resolved)
        val first = resolved as PlanLoadResolution.Resolved
        assertEquals("1RM de la misma configuración", first.basisLabel)
        assertEquals(100.0, first.targetRange.start, 0.0001)
        assertEquals(100.0, first.targetRange.endInclusive, 0.0001)

        val tm = PlanLoadResolver.resolveReference(
            reference = reference(
                kind = PlanLoadReferenceKind.EXERCISE_TM,
                configurationId = benchBarbell,
                convention = LoadQuantityConvention.TOTAL_EXTERNAL,
                capturedKg = 90.0,
            ),
            targetConfigurationId = benchBarbell,
            targetConvention = LoadQuantityConvention.TOTAL_EXTERNAL,
        )
        assertEquals("training max de la misma configuración", (tm as PlanLoadResolution.Resolved).basisLabel)

        val pending = PlanLoadResolver.resolveReference(
            reference = reference(
                kind = PlanLoadReferenceKind.EXERCISE_1RM,
                configurationId = benchBarbell,
                convention = LoadQuantityConvention.TOTAL_EXTERNAL,
                state = PlanLoadReferenceState.PENDING,
            ),
            targetConfigurationId = benchBarbell,
            targetConvention = LoadQuantityConvention.TOTAL_EXTERNAL,
        )
        assertTrue("Referencia sin capturar → pendiente: $pending", pending is PlanLoadResolution.Pending)

        val otherConfiguration = PlanLoadResolver.resolveReference(
            reference = reference(
                kind = PlanLoadReferenceKind.EXERCISE_1RM,
                configurationId = benchBarbell,
                convention = LoadQuantityConvention.TOTAL_EXTERNAL,
                capturedKg = 100.0,
            ),
            targetConfigurationId = benchDumbbells,
            targetConvention = LoadQuantityConvention.UNSPECIFIED,
        )
        assertTrue(
            "Nunca se hereda entre configuraciones: $otherConfiguration",
            otherConfiguration is PlanLoadResolution.Unrepresentable,
        )
    }
}
