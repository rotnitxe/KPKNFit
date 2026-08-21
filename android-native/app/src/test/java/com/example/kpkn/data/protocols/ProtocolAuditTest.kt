package com.example.kpkn.data.protocols

import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import com.example.kpkn.domain.templates.CatalogV2TestFixture
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * Invariantes de [PROTOCOL_LIBRARY] + [ProtocolExerciseLibrary] (Fase A).
 */
class ProtocolAuditTest {

    companion object {
        private lateinit var catalogIds: Set<String>

        /** Protocolos históricos sin deload final explícito (lista blanca). */
        val NO_DELOAD_WHITELIST = setOf(
            "juggernaut-base",
            "rts-base",
            "coan-phillipi",
            "smolov-jr",
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
    fun legacyNamedProtocolsAreNotPublishedWithoutExactRecipes() {
        assertTrue(
            "La biblioteca histórica no debe publicar aproximaciones genéricas de protocolos de terceros",
            PROTOCOL_LIBRARY.filterNot { it.id == "kpkn-native-sbd-4" }
                .all { it.publicationStatus == ProtocolPublicationStatus.HIDDEN_UNVERIFIED },
        )
        val native = PROTOCOL_LIBRARY.single { it.id == "kpkn-native-sbd-4" }
        assertTrue(native.publicationStatus == ProtocolPublicationStatus.KPKN_NATIVE)
        assertTrue(native.dayRecipes.size == 4)
        assertTrue(native.dayRecipes.all { it.mainRestSeconds >= 180 })
        assertTrue(native.dayRecipes.any { it.mainLiftConfigurationId == "low_bar_back_squat__barbell" })
        assertTrue(native.dayRecipes.any { it.mainLiftConfigurationId == "bench_press__barbell" })
        assertTrue(native.dayRecipes.any { it.mainLiftConfigurationId == "conventional_deadlift__bilateral__barbell" })
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
    fun protocolExerciseLibraryLiftsResolveToCatalogV2() {
        val failures = mutableListOf<String>()
        val lifts = collectProtocolLifts()
        assertTrue("No se recolectaron lifts de ProtocolExerciseLibrary", lifts.isNotEmpty())
        lifts.forEach { lift ->
            val candidates = listOfNotNull(
                lift.exerciseDbId.trim().lowercase(),
                lift.performanceProfileId.trim().lowercase(),
            )
            if (candidates.none { it in catalogIds }) {
                failures += "${lift.name}: ni '${lift.exerciseDbId}' ni '${lift.performanceProfileId}' en catálogo v2"
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    private fun collectProtocolLifts(): List<ProtocolLift> {
        val out = mutableListOf<ProtocolLift>()
        out += listOf(
            ProtocolExerciseLibrary.SQUAT_MAIN,
            ProtocolExerciseLibrary.LOW_BAR_SQUAT_MAIN,
            ProtocolExerciseLibrary.SQUAT_TECHNIQUE,
            ProtocolExerciseLibrary.BENCH_MAIN,
            ProtocolExerciseLibrary.BENCH_TECHNIQUE,
            ProtocolExerciseLibrary.DEADLIFT_MAIN,
            ProtocolExerciseLibrary.DEADLIFT_TECHNIQUE,
            ProtocolExerciseLibrary.OHP_MAIN,
        )
        ProtocolLiftFocus.entries.forEach { focus ->
            val main = ProtocolExerciseLibrary.mainLiftFor(focus, 0)
            out += main
            out += ProtocolExerciseLibrary.techniqueVariantFor(main)
            out += ProtocolExerciseLibrary.accessoriesFor(main, weekNumber = 1, count = 6)
        }
        return out.distinctBy { it.exerciseDbId }
    }
}
