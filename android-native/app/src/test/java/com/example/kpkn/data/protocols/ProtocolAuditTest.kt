package com.example.kpkn.data.protocols

import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import com.example.kpkn.domain.templates.CatalogV2TestFixture
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.domain.training.RecipeContractPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * Invariantes de [PROTOCOL_LIBRARY] (Fase A).
 */
class ProtocolAuditTest {

    companion object {
        private lateinit var catalogIds: Set<String>

        /**
         * Protocolos sin bloque final de descarga (lista blanca). B.S6 parte 1 retira los que ya no la necesitan: KPKN SBD-4
         * termina en un bloque «Taper», PPL y estilo RP en un bloque «Descarga» (y sus semanas de descarga llevan `kind = DELOAD`
         * y series recortadas) y Smolov Jr tiene un solo bloque, que esta comprobación no mira. Quedan:
         * - los que se repiten (un ciclo que se repite no necesita descarga final): Texas ×2, 5/3/1 ×2, Madcow, nSuns, GZCLP,
         *   Westside, PHUL y PHAT heredados;
         * - los que no se repiten, duran 8 semanas o más y no traen ninguna semana de descarga ni de taper (hallazgo C5 del
         *   contrato de receta): J&T, Rippler, UHF-9, Juggernaut, Sheiko, Coan, Korte, Cube, Calgary, RTS y SBS;
         * - los que tienen su descarga o su taper DENTRO de un bloque que no se llama así (Candito, Lilliebridge y TSA 9): su
         *   último `ProtocolBlock` no es de descarga, y renombrarlo rompería la estructura que fijan otras pruebas;
         * - los tres índices históricos ocultos (`juggernaut-base`, `rts-base` y `coan-phillipi`).
         */
        val NO_DELOAD_WHITELIST = setOf(
            "juggernaut-base",
            "rts-base",
            "coan-phillipi",
            "coan-phillipi-dl",
            "texas-method-3d",
            "texas-method-4d",
            "wendler-531-bbb",
            "wendler-531-fsl",
            "madcow-5x5",
            "gzclp",
            "gzcl-jt-2",
            "gzcl-rippler",
            "gzcl-uhf-9",
            "juggernaut-2",
            "sheiko-29-32",
            "candito-6",
            "korte-3x3",
            "cube-method",
            "lilliebridge",
            "westside-conjugate",
            "calgary-16",
            "tsa-9",
            "phul-verified",
            "phat-verified",
            "kpkn-rts-style",
            "kpkn-sbs-rtf",
            "nsuns-531-lp-4d",
        )

        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            catalogIds = CatalogV2TestFixture.configurationLookup().keys
        }

        /**
         * Orden de fase de un goal de bloque. Reconoce los goals en español y en inglés, y en los
         * dos idiomas las mismas variantes de cada fase (acumulación o hipertrofia, intensificación
         * o fuerza, realización o pico, descarga o taper): los bloques de Juggernaut salen del
         * nombre del enum («Accumulation», «Peak») y antes caían en el «else», así que nunca
         * contaban como regresión de fase.
         */
        private fun goalRank(goal: String): Int = when {
            goal.contains("acumul", ignoreCase = true) ||
                goal.contains("accumul", ignoreCase = true) ||
                goal.contains("hipertrofia", ignoreCase = true) ||
                goal.contains("hypertroph", ignoreCase = true) -> 1
            goal.contains("intensif", ignoreCase = true) ||
                goal.contains("fuerza", ignoreCase = true) ||
                goal.contains("strength", ignoreCase = true) -> 2
            goal.contains("realiz", ignoreCase = true) ||
                goal.contains("pico", ignoreCase = true) ||
                goal.contains("peak", ignoreCase = true) -> 3
            goal.contains("descarga", ignoreCase = true) ||
                goal.contains("deload", ignoreCase = true) ||
                goal.contains("taper", ignoreCase = true) -> 4
            goal.contains("custom", ignoreCase = true) -> 2
            else -> 2
        }
    }

    @Test
    fun goalRank_recognizes_spanish_and_english_goals() {
        val expected = mapOf(
            "Acumulación" to 1, "Accumulation" to 1, "Hipertrofia" to 1, "Hypertrophy" to 1,
            "Intensificación" to 2, "Intensification" to 2, "Fuerza" to 2, "Strength" to 2,
            "Realización" to 3, "Realization" to 3, "Pico" to 3, "Peak" to 3,
            "Descarga" to 4, "Deload" to 4, "Taper" to 4,
            "Custom" to 2, "Especificidad" to 2,
        )
        expected.forEach { (goal, rank) -> org.junit.Assert.assertEquals(goal, rank, goalRank(goal)) }
    }

    /**
     * Coherencia de [NO_DELOAD_WHITELIST] con el contrato de receta válida (C5). El objetivo de B.S6 es
     * que la lista salga de las exenciones C5: las recetas visibles que exigen descarga (no se repiten,
     * duran 8 semanas o más y no traen ninguna semana de descarga ni de taper) más las que se repiten
     * (un ciclo que se repite no necesita descarga final). Hoy solo falla lo peligroso: una receta que
     * exige descarga y no está en la lista. Lo que sobra en la lista se imprime y no falla.
     */
    @Test
    fun no_deload_whitelist_covers_every_visible_recipe_that_needs_a_deload() {
        val metadata = CatalogCompositionTestSupport.metadata
        val visible = PROTOCOL_LIBRARY.filter { it.isVisibleForApplication }
        val visibleIds = visible.map { it.id }.toSet()
        val needsDeload = visible
            .filter { protocol ->
                RecipeContractPolicy.evaluate(requireNotNull(protocol.recipe), metadata)
                    .any { it.rule == RecipeContractPolicy.C5_DELOAD_REQUIRED }
            }
            .map { it.id }
            .toSet()
        val repeating = visible.filter { it.recipe?.repeats == true }.map { it.id }.toSet()
        val target = needsDeload + repeating
        val current = NO_DELOAD_WHITELIST.intersect(visibleIds)

        println("WHITELIST en visibles (${current.size}): ${current.sorted()}")
        println("WHITELIST objetivo = C5 + repeats (${target.size}): ${target.sorted()}")
        println("WHITELIST exigen descarga y faltan en la lista: ${(needsDeload - NO_DELOAD_WHITELIST).sorted()}")
        println("WHITELIST sobran (ni exigen descarga ni se repiten): ${(current - target).sorted()}")
        println("WHITELIST objetivo que la lista aún no trae: ${(target - current).sorted()}")

        val missing = needsDeload - NO_DELOAD_WHITELIST
        assertTrue(
            "Recetas visibles que exigen descarga (C5) y no están en NO_DELOAD_WHITELIST: ${missing.sorted()}",
            missing.isEmpty(),
        )
    }

    @Test
    fun uniqueProtocolIdsAndNonEmptyBlocks() {
        val ids = PROTOCOL_LIBRARY.map { it.id }
        assertTrue("IDs duplicados", ids.size == ids.toSet().size)
        PROTOCOL_LIBRARY.forEach { protocol ->
            assertTrue("${protocol.id}: sin bloques", protocol.blocks.isNotEmpty())
            assertTrue("${protocol.id}: nombre vacío", protocol.name.isNotBlank())
        }
    }

    @Test
    fun visible_protocols_have_exact_recipes_and_attribution() {
        val visible = PROTOCOL_LIBRARY.filter { it.isVisibleForApplication }
        assertTrue(visible.isNotEmpty())
        visible.forEach { protocol ->
            assertTrue("${protocol.id} debe tener receta", protocol.recipe != null && protocol.recipe!!.weeks.isNotEmpty())
            when (protocol.publicationStatus) {
                // Un método de tercero cita su fuente: URL y aviso de no afiliación.
                ProtocolPublicationStatus.VERIFIED -> {
                    assertTrue("${protocol.id} necesita URL", !protocol.source.primaryUrl.isNullOrBlank())
                    assertTrue("${protocol.id} necesita disclaimer", !protocol.source.disclaimer.isNullOrBlank())
                }
                // Un plan propio no tiene página de fuente que enlazar ni a quién desafiliarse (E-11).
                ProtocolPublicationStatus.KPKN_NATIVE -> {
                    assertNull("${protocol.id} KPKN_NATIVE no lleva URL de fuente", protocol.source.primaryUrl)
                    assertFalse(
                        "${protocol.id} KPKN_NATIVE no dice «No afiliado a KPKN»",
                        protocol.source.disclaimer.orEmpty().contains("No afiliado a KPKN", ignoreCase = true),
                    )
                }
                ProtocolPublicationStatus.HIDDEN_UNVERIFIED -> Unit
            }
            assertTrue("${protocol.id} necesita fidelitySpec", protocol.fidelitySpec != null)
            if (protocol.publicationStatus == ProtocolPublicationStatus.KPKN_NATIVE) {
                assertTrue("${protocol.id} KPKN_NATIVE no declara exenciones", protocol.exemptions.isEmpty() && protocol.recipe!!.exemptions.isEmpty())
            }
        }
        val hidden = PROTOCOL_LIBRARY.filter { it.publicationStatus == ProtocolPublicationStatus.HIDDEN_UNVERIFIED }
        assertTrue(hidden.any { it.id == "gzcl-base" })
        val native = PROTOCOL_LIBRARY.single { it.id == "kpkn-native-sbd-4" }
        assertTrue(native.publicationStatus == ProtocolPublicationStatus.KPKN_NATIVE)
        val ids = native.recipe!!.weeks.first().days.flatMap { it.slots.filter { slot -> slot.role == SlotRole.T1_MAIN }.map { it.lift.configurationId } }
        assertTrue(ids.any { it == "low_bar_back_squat__barbell" || it == "high_bar_back_squat__barbell" })
        assertTrue(ids.any { it == "bench_press__barbell" })
        assertTrue(native.recipe!!.weeks.any { week ->
            week.days.any { day -> day.slots.any { it.lift.configurationId == "conventional_deadlift__bilateral__barbell" } }
        })
    }

    @Test
    fun intensityVolumeAndDefaultSplitInvariants() {
        val failures = mutableListOf<String>()
        val splitIds = SPLIT_TEMPLATES.map { it.id }.toSet()
        PROTOCOL_LIBRARY.forEach { protocol ->
            protocol.blocks.forEachIndexed { index, block ->
                if (block.intensityMin >= block.intensityMax) {
                    failures += "${protocol.id}[$index] ${block.name}: intensityMin(${block.intensityMin}) >= max(${block.intensityMax})"
                }
                val mod = block.volumeModifier
                if (mod != null && (mod < 0.25 || mod > 1.6)) {
                    failures += "${protocol.id}[$index] ${block.name}: volumeModifier $mod fuera de [0.25, 1.6]"
                }
            }
            val defaultSplit = protocol.defaultSplit
            if (defaultSplit != null && defaultSplit !in splitIds) {
                failures += "${protocol.id}: defaultSplit '$defaultSplit' no existe en SPLIT_TEMPLATES"
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun blocksTrendTowardPeakThenDeloadWhenPresent() {
        val failures = mutableListOf<String>()
        PROTOCOL_LIBRARY.forEach { protocol ->
            val blocks = protocol.blocks
            if (blocks.size < 2) return@forEach
            val last = blocks.last()
            val isDeloadLast = last.goal.contains("descarga", ignoreCase = true) ||
                last.name.contains("descarga", ignoreCase = true) ||
                last.name.contains("taper", ignoreCase = true) ||
                last.name.contains("deload", ignoreCase = true) ||
                last.goal.contains("deload", ignoreCase = true)
            if (!isDeloadLast && protocol.id !in NO_DELOAD_WHITELIST) {
                failures += "${protocol.id}: falta deload final (whitelist: $NO_DELOAD_WHITELIST)"
            }
            val working = if (isDeloadLast) blocks.dropLast(1) else blocks
            // Intensidad media no debe caer monótonamente hacia el pico.
            val midpoints = working.map { (it.intensityMin + it.intensityMax) / 2.0 }
            if (midpoints.size >= 2) {
                val first = midpoints.first()
                val peak = midpoints.maxOrNull() ?: first
                if (peak + 1 < first) {
                    failures += "${protocol.id}: pico ($peak) menor que inicio ($first)"
                }
            }
            // Orden de goals razonable (acumulación → intensificación → realización).
            val ranks = working.map { goalRank(it.goal) }
            for (i in 1 until ranks.size) {
                if (ranks[i - 1] == 4) continue // descarga intermedia, p. ej. TSA 9
                if (ranks[i] + 1 < ranks[i - 1] && ranks[i] != 4) {
                    // Permite mesetas; solo falla si baja más de un escalón (salvo custom).
                    if (ranks[i - 1] - ranks[i] > 1) {
                        failures += "${protocol.id}: regresión de goal ${working[i - 1].goal} → ${working[i].goal}"
                    }
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun visible_recipe_lifts_resolve_to_catalog_v2() {
        val failures = mutableListOf<String>()
        val ids = collectRecipeConfigurationIds()
        assertTrue("No se recolectaron configurationId de recetas", ids.isNotEmpty())
        ids.forEach { configurationId ->
            if (configurationId.lowercase() !in catalogIds) {
                failures += configurationId
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    private fun collectRecipeConfigurationIds(): Set<String> =
        PROTOCOL_LIBRARY.flatMap { protocol ->
            protocol.recipe?.weeks.orEmpty().flatMap { week ->
                week.days.flatMap { day -> day.slots.map { it.lift.configurationId } }
            }
        }.toSet()
}
