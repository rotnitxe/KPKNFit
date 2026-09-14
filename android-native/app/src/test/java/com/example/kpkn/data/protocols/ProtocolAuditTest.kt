package com.example.kpkn.data.protocols

import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import com.example.kpkn.domain.templates.CatalogV2TestFixture
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * Invariantes de [PROTOCOL_LIBRARY] (Fase A).
 */
class ProtocolAuditTest {

    companion object {
        private lateinit var catalogIds: Set<String>

        /** Protocolos históricos sin deload final explícito (lista blanca). */
        val NO_DELOAD_WHITELIST = setOf(
            "juggernaut-base",
            "rts-base",
            "coan-phillipi",
            "coan-phillipi-dl",
            "smolov-jr",
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
            "kpkn-native-sbd-4",
            "kpkn-ppl-6",
            "kpkn-rp-style",
        )

        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            catalogIds = CatalogV2TestFixture.configurationLookup().keys
        }

        private fun goalRank(goal: String): Int = when {
            goal.contains("acumul", ignoreCase = true) -> 1
            goal.contains("intensif", ignoreCase = true) -> 2
            goal.contains("realiz", ignoreCase = true) || goal.contains("pico", ignoreCase = true) -> 3
            goal.contains("descarga", ignoreCase = true) || goal.contains("deload", ignoreCase = true) -> 4
            goal.contains("custom", ignoreCase = true) -> 2
            else -> 2
        }
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
            assertTrue("${protocol.id} necesita URL", !protocol.source.primaryUrl.isNullOrBlank())
            assertTrue("${protocol.id} necesita disclaimer", !protocol.source.disclaimer.isNullOrBlank())
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
