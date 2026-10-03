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
 *   afirma que la familia canónica se PUBLICA para cada frecuencia admitida y que ninguna otra
 *   familia nativa cambia de rango.
 *
 * LO QUE ESTE ARCHIVO NO HACE: no relaja recetas de autor, no fuerza elegibilidad de ninguna
 * receta fija, no inventa material y no afirma nada sobre frecuencias fuera del 1..6 del wizard
 * más allá de que la familia `strength-cardio` NO se publique para ellas.
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

    @Test
    fun t027_02_everyAdmittedFrequencyPublishesTheNativeMixedFamily() {
        (1..6).forEach { frequency ->
            val candidates = mixedCandidates(frequency)
            val mixed = candidates.filter { it.id == mixedId }
            assertEquals(
                "con $frequency día(s) el planificador debe publicar exactamente una entrada $mixedId, " +
                    "y lo publica como ${candidates.map { it.id }}",
                1,
                mixed.size,
            )
            assertEquals(
                "la entrada publicada para $frequency día(s) debe conservar su rango canónico 1..6",
                1..6,
                mixed.single().supportedFrequencies,
            )
        }
    }

    /** 1 día NO puede degenerar en cero: el wizard admite 1 y la familia debe responder. */
    @Test
    fun t027_03_singleDayIsNotSilentlyEmpty() {
        val candidates = mixedCandidates(1)
        assertTrue(
            "con 1 día el planificador no puede devolver una lista vacía (devuelve ${candidates.map { it.id }})",
            candidates.isNotEmpty(),
        )
        assertTrue(
            "con 1 día debe seguir apareciendo $mixedId, no una lista sin cardio",
            candidates.any { it.id == mixedId },
        )
    }

    /** 5 y 6 días NO pueden truncarse a 2..4 ni recortarse. */
    @Test
    fun t027_04_fiveAndSixDaysAreNotTruncated() {
        listOf(5, 6).forEach { frequency ->
            val mixed = mixedCandidates(frequency).single { it.id == mixedId }
            assertTrue(
                "la frecuencia pedida ($frequency) debe caer dentro del rango publicado (${mixed.supportedFrequencies})",
                frequency in mixed.supportedFrequencies,
            )
            assertEquals(
                "con $frequency día(s) el rango no puede encogerse: ${mixed.supportedFrequencies}",
                1..6,
                mixed.supportedFrequencies,
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

    @Test
    fun t027_06_mixedPrefilterKeepsOnlyNativeCardioSchedulingFamilies() {
        val candidates = mixedCandidates(3)
        assertTrue("el prefijo mixto no puede devolver una lista vacía", candidates.isNotEmpty())
        candidates.forEach { entry ->
            assertTrue(
                "con mixedTraining=true sólo pueden pasar planes que programen cardio de verdad; " +
                    "${entry.id} no cumple schedulesCardio",
                entry.schedulesCardio,
            )
        }
        assertEquals(
            "con mixedTraining=true la única familia que programa cardio es la mixta",
            listOf(mixedId),
            candidates.filter { it.source == CatalogSource.NATIVE }.map { it.id },
        )
    }

    /** La segunda puerta (frecuencia) sigue consumiendo el catálogo canónico actualizado. */
    @Test
    fun t027_07_frequencyGateIsNotBypassedForNativeEntries() {
        // Una entrada nativa que NO admita la frecuencia pedida se excluye igual que
        // cualquier otra: se lee del catálogo, no de un rango reescrito en el test.
        val excluded = mixedCandidates(6).map { it.id }.filter { it != mixedId }
        assertTrue(
            "con 6 días el resto de familias nativas no son candidatas mixtas por rango: $excluded",
            excluded.isEmpty(),
        )
        // Y la frecuencia pedida se sigue leyendo del catálogo canónico: cada entrada
        // DEVUELTA tiene que admitir la frecuencia que se pidió. Ésta es la invariante real
        // de la segunda puerta (SetupTrainingPlanner.kt:32), y no se reimplementa aquí.
        //
        // Por qué 1 día y 4 días publican el MISMO conjunto de ids: con `mixedTraining = true`
        // el prefiltrado mixto (SetupTrainingPlanner.kt:50) sólo deja pasar entradas que
        // programen cardio de verdad, y hoy eso es exactamente la familia mixta nativa, que
        // cubre 1..6. La IDENTIDAD de la entrada es por tanto la misma a 1 y a 4 días; lo que
        // cambia al materializar es el CALENDARIO y las sesiones (días, slots, duración), no el
        // id. Exigir que los ids difieran sería inventar una obligación que el contrato original
        // nunca pidió y que contradice el prefiltrado real. Lo que sí es una obligación es que
        // la frecuencia pedida quepa en el rango publicado: eso se comprueba abajo.
        listOf(1, 4).forEach { frequency ->
            val candidates = mixedCandidates(frequency)
            assertEquals(
                "con $frequency día(s) el objetivo MIXED debe publicar exactamente la familia " +
                    "mixta nativa y nada más, pero publica ${candidates.map { it.id }}",
                setOf(mixedId),
                candidates.map { it.id }.toSet(),
            )
            candidates.forEach { entry ->
                assertTrue(
                    "la frecuencia pedida ($frequency) debe estar dentro del rango publicado " +
                        "por ${entry.id} (${entry.supportedFrequencies})",
                    frequency in entry.supportedFrequencies,
                )
            }
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
        val natives = PersonalizedPlanCatalog.entries()
            .filter { it.source == CatalogSource.NATIVE }
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
