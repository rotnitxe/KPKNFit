package com.example.kpkn.domain.onboarding

import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.data.models.EquipmentCategory
import com.example.kpkn.data.programs.CatalogEntry
import com.example.kpkn.data.programs.CatalogLevel
import com.example.kpkn.data.programs.CatalogSource
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.programs.TrainingFocus
import com.example.kpkn.data.programs.TrainingReference
import com.example.kpkn.domain.training.TrainingOptions
import com.example.kpkn.domain.training.effectiveEquipment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T-027 — CONTRATO DE FRECUENCIA DE LA FAMILIA NATIVA `strength-cardio` (fuerza + cardio).
 *
 * Qué demuestra y qué NO demuestra:
 * - Evalúa la frontera REAL de [PersonalizedPlanCatalog] y del planificador REAL
 *   [SetupTrainingPlanner]. No hay copia del planificador, ni predicado duplicado, ni
 *   generador alterno, ni respuesta de usuario alterada: sólo se llama a la API pública.
 * - El material declarado se PROYECTA con el contrato de producción
 *   ([com.example.kpkn.domain.training.effectiveEquipment]) desde las 11 categorías
 *   declaradas, igual que hace el wizard; aquí no se reinventa ninguna decisión de material.
 * - El perfil es el MIXED de la matriz de 24: referencia HIPERTROPHY (la que el objetivo
 *   MÚSCULO infiere), nivel INTERMEDIATE, enfoque FULL_BODY, las 11 categorías.
 * - NO materializa programas: eso lo prueba la matriz real del ViewModel
 *   ([com.example.kpkn.screens.onboarding.SetupExecutableAvailabilityMatrixTest]). Aquí sólo se
 *   afirma el rango canónico de la familia y que ninguna otra familia nativa cambia de rango.
 *
 * D1 (punto 12): `strength-cardio` es una versión anterior (`listed = false`). El planificador ya no
 * publica la familia mixta a ninguna frecuencia; sigue resolviendo por id y programando cardio para
 * los programas ya activos. Los casos que afirmaban su publicación ahora fijan que no se publica.
 *
 * LO QUE ESTE ARCHIVO NO HACE: no relaja recetas de autor, no fuerza elegibilidad de ninguna
 * receta fija y no inventa material.
 */
class NativeMixedFrequencyContractTest {

    private val mixedId = "native:strength-cardio"

    /** Las 11 categorías del wizard, proyectadas por el CONTRATO DE PRODUCCIÓN. */
    private val declaredMaterial: Set<String> =
        TrainingOptions(availability = EquipmentAvailability(EquipmentCategory.entries.toSet()))
            .effectiveEquipment(emptySet())

    private fun mixedInput(frequency: Int) = SetupTrainingPlannerInput(
        // Referencia que el objetivo MÚSCULO infiere; MIXED no infiere por su cuenta.
        reference = TrainingReference.HYPERTROPHY,
        frequency = frequency,
        equipment = declaredMaterial,
        level = CatalogLevel.INTERMEDIATE,
        focus = TrainingFocus.FULL_BODY,
        protocolOnly = false,
        mixedTraining = true,
    )

    private fun mixedCandidates(frequency: Int): List<CatalogEntry> =
        SetupTrainingPlanner.candidates(mixedInput(frequency))

    // ─── 1. La entrada canónica que gobierna la mezcla ────────────────────────

    @Test
    fun t027_01_strengthCardioEntryIsNativePublishedAndSchedulesCardio() {
        val entry = PersonalizedPlanCatalog.find(mixedId)
        assertNotNull("$mixedId debe existir en el catálogo de producción", entry)
        val resolved = requireNotNull(entry)
        assertEquals(
            "$mixedId debe seguir siendo una entrada NATIVE (no una receta de autor)",
            CatalogSource.NATIVE,
            resolved.source,
        )
        assertEquals("$mixedId debe conservar su sourceId", "strength-cardio", resolved.sourceId)
        assertEquals(
            "$mixedId debe seguir publicándose",
            com.example.kpkn.data.programs.PublicationState.PUBLISHED,
            resolved.publication,
        )
        assertEquals(
            "$mixedId debe admitir CADA frecuencia que el wizard deja elegir (1..6)",
            1..6,
            resolved.supportedFrequencies,
        )
        assertTrue(
            "$mixedId debe seguir siendo la única familia que programa cardio de verdad",
            resolved.schedulesCardio,
        )
    }

    // ─── 2. Las 6 frecuencias admitidas publican la familia ───────────────────

    /** D1: con la familia oculta, el planificador no la publica a ninguna frecuencia admitida (1..6). */
    @Test
    fun t027_02_theHiddenMixedFamilyIsNotPublishedAtAnyAdmittedFrequency() {
        (1..6).forEach { frequency ->
            assertTrue(
                "con $frequency día(s) el planificador no publica $mixedId (versión anterior, listed = false): " +
                    "publica ${mixedCandidates(frequency).map { it.id }}",
                mixedCandidates(frequency).none { it.id == mixedId },
            )
        }
    }

    /** D1: con 1 día el modo mixto no publica nada listado; la familia sigue resolviendo por id. */
    @Test
    fun t027_03_singleDayPublishesNoMixedPlanButTheIdStillResolves() {
        assertTrue(
            "con 1 día el modo mixto no publica ninguna familia listada: ${mixedCandidates(1).map { it.id }}",
            mixedCandidates(1).isEmpty(),
        )
        assertNotNull("$mixedId sigue resolviendo por id", PersonalizedPlanCatalog.find(mixedId))
    }

    /** 5 y 6 días: el rango canónico de la familia (1..6) se conserva aunque esté oculta. */
    @Test
    fun t027_04_fiveAndSixDaysKeepTheCanonicalRangeOfTheHiddenFamily() {
        val mixed = requireNotNull(PersonalizedPlanCatalog.find(mixedId))
        assertEquals(
            "el rango canónico de $mixedId no puede encogerse: ${mixed.supportedFrequencies}",
            1..6,
            mixed.supportedFrequencies,
        )
        listOf(5, 6).forEach { frequency ->
            assertTrue(
                "la frecuencia pedida ($frequency) cae dentro del rango canónico (${mixed.supportedFrequencies})",
                frequency in mixed.supportedFrequencies,
            )
            assertTrue(
                "con $frequency día(s) el modo mixto no publica ninguna familia listada",
                mixedCandidates(frequency).isEmpty(),
            )
        }
    }

    // ─── 3. Frecuencias NO admitidas ──────────────────────────────────────────

    @Test
    fun t027_05_invalidFrequenciesDoNotPublishTheNativeMixedFamily() {
        listOf(0, 7).forEach { frequency ->
            val candidates = mixedCandidates(frequency)
            assertFalse(
                "con una frecuencia inválida ($frequency) el planificador NO debe publicar $mixedId; " +
                    "publica ${candidates.map { it.id }}",
                candidates.any { it.id == mixedId },
            )
        }
    }

    // ─── 4. La prefiltración mixta sigue en pie (no se sortea la puerta) ─────

    /** D1: el prefijo mixto no publica ninguna familia listada mientras la única que programa cardio está oculta. */
    @Test
    fun t027_06_mixedPrefilterPublishesNothingWhileTheOnlyCardioFamilyIsHidden() {
        val candidates = mixedCandidates(3)
        assertTrue("el prefijo mixto no publica familias listadas: ${candidates.map { it.id }}", candidates.isEmpty())
        assertTrue(
            "la familia oculta sigue programando cardio por id (programas ya activos)",
            requireNotNull(PersonalizedPlanCatalog.find(mixedId)).schedulesCardio,
        )
    }

    /** D1: ninguna otra familia ocupa el lugar de la oculta en el modo mixto, a ninguna frecuencia de las probadas. */
    @Test
    fun t027_07_noOtherFamilyTakesTheMixedPlaceAtAnyFrequency() {
        listOf(1, 4, 6).forEach { frequency ->
            val ids = mixedCandidates(frequency).map { it.id }
            assertTrue("con $frequency día(s) el modo mixto no publica otra familia: $ids", ids.isEmpty())
        }
    }

    // ─── 5. Ninguna receta fija se fuerza a ser elegible ──────────────────────

    @Test
    fun t027_08_noFixedRecipeIsForcedIntoTheMixedGoal() {
        listOf(1, 2, 3, 4, 5, 6).forEach { frequency ->
            mixedCandidates(frequency).forEach { entry ->
                assertTrue(
                    "con mixedTraining=true una receta de autor o plantilla no puede colarse: ${entry.id}",
                    entry.source == CatalogSource.NATIVE,
                )
            }
        }
    }

    // ─── 6. Los rangos ORIGINALES de las demás familias nativas ──────────────

    @Test
    fun t027_09_otherNativeFamiliesKeepTheirOriginalFrequencyRanges() {
        // Rangos originales, congelados como oráculo de no-regresión del catálogo.
        val originalRanges = mapOf(
            "full-body" to (2..3),
            "gym-muscle" to (3..6),
            "machine-muscle" to (2..4),
            "home-training" to (2..4),
            "bodyweight" to (2..4),
            "return-training" to (2..3),
            "one-day" to (1..1),
            "strength-cardio" to (1..6),
            // Los cuatro planes propios de §11.1 (T-004a) cubren 1..6 días.
            "strength-foundation" to (1..6),
            "muscle-foundation" to (1..6),
            "powerbuilding-foundation" to (1..6),
            "complete-athlete" to (1..6),
        )
        // Los programas «a medida» del generador (Entreno v2) son NATIVE pero no son familias del personalizador.
        val natives = PersonalizedPlanCatalog.entries()
            .filter { it.source == CatalogSource.NATIVE && !it.isGenerated }
            .associateBy { it.sourceId }
        assertEquals(
            "no puede aparecer ni desaparecer ninguna familia nativa: ${natives.keys.sorted()}",
            originalRanges.keys.sorted(),
            natives.keys.sorted(),
        )
        originalRanges.forEach { (sourceId, range) ->
            val entry = requireNotNull(natives[sourceId]) { "falta la familia nativa $sourceId" }
            assertEquals(
                "la familia nativa '$sourceId' cambió de rango: ${entry.supportedFrequencies} != $range",
                range,
                entry.supportedFrequencies,
            )
            val canonicalId = when (sourceId) {
                "strength-foundation" -> "native:strength-foundation-v2"
                "muscle-foundation" -> "native:muscle-foundation-v2"
                "powerbuilding-foundation" -> "native:powerbuilding-foundation-v2"
                "complete-athlete" -> "native:complete-athlete-v2"
                else -> "native:$sourceId"
            }
            assertEquals(
                "la familia nativa '$sourceId' debe conservar su id canónico",
                canonicalId,
                entry.id,
            )
        }
    }

    /** `strength-cardio` es la ÚNICA familia que programa cardio; el resto sigue sin cardio. */
    @Test
    fun t027_10_strengthCardioIsTheOnlyNativeFamilySchedulingCardio() {
        val scheduling = PersonalizedPlanCatalog.entries()
            .filter { it.schedulesCardio }
            .map { it.id }
            .sorted()
        assertEquals(
            "sólo la familia mixta nativa puede programar cardio; las que lo hacen son $scheduling",
            listOf(mixedId),
            scheduling,
        )
    }
}
