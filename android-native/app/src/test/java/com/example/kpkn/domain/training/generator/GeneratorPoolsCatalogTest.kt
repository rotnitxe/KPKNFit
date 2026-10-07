package com.example.kpkn.domain.training.generator

import com.example.kpkn.data.models.MobilityExerciseCatalog
import com.example.kpkn.domain.exercises.catalogv2.CatalogReviewStatusV2
import com.example.kpkn.domain.onboarding.EquipmentSymbolId
import com.example.kpkn.domain.onboarding.EquipmentSymbols
import com.example.kpkn.domain.onboarding.TrainingPlace
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.domain.training.supportRequirementsFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regla «nunca inventar ids de catálogo» para el generador de rutinas: todo id que citan las reservas
 * ([MovementPools]), las escaleras de peso corporal ([BodyweightLadders]) y la movilidad existe, APPROVED, en el catálogo
 * cargado por las pruebas del catálogo v2, y es ejecutable con el material que declara su entrada. Si un id desaparece del
 * catálogo, esta prueba falla con su origen.
 */
class GeneratorPoolsCatalogTest {

    private val index: GeneratorCatalog by lazy { GeneratorCatalog.of(CatalogCompositionTestSupport.catalog) }

    private data class Source(val origin: String, val id: String, val requires: List<String>)

    private val sources: List<Source> by lazy {
        val pool = MovementPools.byPattern.flatMap { (pattern, groups) ->
            groups.flatMap { group -> group.entries.map { Source("pool ${pattern.name}", it.id, it.requires) } }
        }
        val ladders = BodyweightLadders.all.flatMap { ladder ->
            LadderTier.entries.flatMap { tier ->
                ladder.rungs(tier).map { Source("escalera ${ladder.pattern.name}/${ladder.skill?.name ?: "-"}/${tier.name}", it.id, it.requires) }
            }
        }
        pool + ladders
    }

    @Test
    fun every_cited_configuration_exists_approved_in_the_loaded_catalog() {
        val problems = sources.mapNotNull { source ->
            val entry = index.entry(source.id)
            when {
                entry == null -> "${source.origin}: ${source.id} no existe APPROVED en el catálogo"
                entry.configuration.evidence.reviewStatus != CatalogReviewStatusV2.APPROVED -> "${source.origin}: ${source.id} no está APPROVED"
                else -> null
            }
        }.distinct()
        assertTrue("ids sin respaldo en el catálogo:\n${problems.joinToString("\n")}", problems.isEmpty())
        assertTrue("las reservas no pueden estar vacías", sources.size > 150)
    }

    @Test
    fun a_group_never_mixes_equipment_tiers() {
        val problems = MovementPools.byPattern.flatMap { (pattern, groups) ->
            groups.mapNotNull { group ->
                val tiers = group.entries.mapNotNull { index.entry(it.id)?.tier }.toSet()
                if (tiers.size > 1) "${pattern.name}: ${group.entries.map { it.id }} mezcla $tiers" else null
            }
        }
        assertTrue("grupos con material mezclado:\n${problems.joinToString("\n")}", problems.isEmpty())
    }

    @Test
    fun declared_requirements_include_what_the_shared_support_contract_asks() {
        val problems = sources.mapNotNull { source ->
            val shared = supportRequirementsFor(source.id)
            val missing = shared.filter { token -> token !in source.requires }
            if (missing.isEmpty()) null else "${source.origin}: ${source.id} no declara $missing (declara ${source.requires})"
        }.distinct()
        assertTrue("requisitos compartidos sin declarar:\n${problems.joinToString("\n")}", problems.isEmpty())
    }

    private fun symbolFor(requirement: String): EquipmentSymbolId? = when (requirement) {
        "bench", "bench_incline", "support" -> EquipmentSymbolId.BENCH
        "rack", "low_bar_support" -> EquipmentSymbolId.RACK
        "pull_up_bar" -> EquipmentSymbolId.PULL_UP_BAR
        "dip_bars" -> EquipmentSymbolId.PARALLEL_BARS
        "rings", "trx" -> EquipmentSymbolId.RINGS
        "plyo_box" -> EquipmentSymbolId.BOX
        "ball" -> EquipmentSymbolId.BALL
        "jump_rope" -> EquipmentSymbolId.JUMP_ROPE
        else -> null
    }

    private fun tierSymbol(tier: EquipmentTier): EquipmentSymbolId? = when (tier) {
        EquipmentTier.BARBELL -> EquipmentSymbolId.BARBELL
        EquipmentTier.DUMBBELL -> EquipmentSymbolId.DUMBBELLS
        EquipmentTier.MACHINE -> EquipmentSymbolId.MACHINES
        EquipmentTier.CABLE -> EquipmentSymbolId.CABLE
        EquipmentTier.SMITH -> EquipmentSymbolId.SMITH
        EquipmentTier.KETTLEBELL -> EquipmentSymbolId.KETTLEBELL
        EquipmentTier.BAND -> EquipmentSymbolId.BANDS
        EquipmentTier.RINGS -> EquipmentSymbolId.RINGS
        EquipmentTier.BODYWEIGHT, EquipmentTier.OTHER -> null
    }

    @Test
    fun every_entry_is_executable_with_the_material_it_declares() {
        val problems = sources.mapNotNull { source ->
            val entry = index.entry(source.id) ?: return@mapNotNull null
            val needed = (source.requires.map { it.split('|').first() } + supportRequirementsFor(source.id)).toSet()
            val symbols = buildSet {
                tierSymbol(entry.tier)?.let(::add)
                needed.mapNotNull(::symbolFor).forEach(::add)
                // La barra EZ y la barra baja solo se acreditan en gimnasio con su símbolo madre elegido.
                if (entry.equipmentId == "ez_bar") add(EquipmentSymbolId.BARBELL)
                if ("low_bar_support" in needed) add(EquipmentSymbolId.RACK)
            }
            val places = if (entry.equipmentId == "ez_bar" || "low_bar_support" in needed) setOf(TrainingPlace.GYM) else setOf(TrainingPlace.HOME)
            val equipment = DayEquipment(EquipmentSymbols.availabilityOf(symbols, places))
            val own = source.requires.all { equipment.satisfied(it) }
            if (!equipment.allows(entry, source.requires) || !own) {
                "${source.origin}: ${source.id} no se puede ejecutar con $symbols en $places (tokens ${equipment.tokens.sorted()})"
            } else {
                null
            }
        }.distinct()
        assertTrue("entradas no ejecutables con su material:\n${problems.joinToString("\n")}", problems.isEmpty())
    }

    @Test
    fun mobility_ids_exist_in_the_mobility_catalog() {
        val missing = MobilityBuilder.allIds.filter { MobilityExerciseCatalog.findById(it) == null }
        assertEquals("ids de movilidad que ya no existen: $missing", emptyList<String>(), missing)
        assertTrue(MobilityBuilder.allIds.size >= 30)
    }

    @Test
    fun every_ladder_tier_has_at_least_one_rung_reachable_with_some_material_except_the_documented_empties() {
        val emptyTiers = BodyweightLadders.all.flatMap { ladder ->
            LadderTier.entries.filter { ladder.rungs(it).isEmpty() }.map { ladder.pattern to (ladder.skill to it) }
        }
        // El único hueco documentado: fondos sin tramo fácil (los fondos entre bancos viven en la reserva de tríceps).
        assertEquals(
            listOf(RoutinePattern.HORIZONTAL_PUSH to (com.example.kpkn.domain.onboarding.CapabilitySkill.DIP to LadderTier.EASY)),
            emptyTiers,
        )
    }
}
