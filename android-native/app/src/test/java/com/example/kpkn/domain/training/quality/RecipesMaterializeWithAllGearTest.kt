package com.example.kpkn.domain.training.quality

import com.example.kpkn.data.models.ApparatusPresence
import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.data.models.EquipmentCategory
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.domain.onboarding.AuthoredPlanFixtures
import com.example.kpkn.domain.training.EFFECTIVE_EQUIPMENT_KEYS
import com.example.kpkn.domain.training.EquipmentKeys
import com.example.kpkn.domain.training.SYMBOL_EQUIPMENT_KEYS
import com.example.kpkn.domain.training.SymbolEquipmentKeys
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DUP-07 · Con TODO el material confirmado, toda receta `protocol:*` se materializa. `texas-method-3d` y
 * `westside-conjugate` no lo hacían: usaban `back_extension_lumbar__default` y `reverse_hyper__machine`, máquinas
 * sin llave curada en el panel, y `evidenceOfKind` las marcaba siempre ausentes («necesita material: machine»).
 */
class RecipesMaterializeWithAllGearTest {

    private val everything = AuthoredPlanFixtures.Gear(
        "E7-todo-el-material",
        EquipmentAvailability(
            categories = EquipmentCategory.entries.toSet(),
            apparatus = (EFFECTIVE_EQUIPMENT_KEYS.map { it.key } + SYMBOL_EQUIPMENT_KEYS.map { it.key })
                .associateWith { ApparatusPresence.PRESENT },
            supports = listOf(
                EquipmentKeys.BENCH_FLAT, EquipmentKeys.BENCH_ADJUSTABLE, EquipmentKeys.SQUAT_RACK, EquipmentKeys.PREACHER_BENCH,
                EquipmentKeys.PULLUP_BAR, EquipmentKeys.DIP_BARS, EquipmentKeys.LOW_BAR_SUPPORT, EquipmentKeys.EZ_BAR,
                SymbolEquipmentKeys.GHD, SymbolEquipmentKeys.AB_WHEEL, SymbolEquipmentKeys.RINGS, SymbolEquipmentKeys.PLYO_BOX,
                SymbolEquipmentKeys.DECLINE_BENCH, SymbolEquipmentKeys.HYPEREXTENSION_BENCH,
            ).associateWith { ApparatusPresence.PRESENT },
        ),
    )

    @Test
    fun `toda receta protocol se materializa con todo el material`() {
        val failures = PersonalizedPlanCatalog.entries()
            .filter { it.recipe != null && it.id.startsWith("protocol:") }
            .mapNotNull { entry ->
                try {
                    AuthoredPlanFixtures.prepare(entry.id, everything)
                    null
                } catch (t: Throwable) {
                    "${entry.id}: ${t.javaClass.simpleName}: ${t.message?.lineSequence()?.firstOrNull().orEmpty().take(160)}"
                }
            }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `las recetas ya no usan las dos maquinas sin llave curada`() {
        val uncurated = setOf("back_extension_lumbar__default", "reverse_hyper__machine")
        val used = PersonalizedPlanCatalog.entries()
            .filter { it.recipe != null }
            .flatMap { entry -> requireNotNull(entry.recipe).weeks.flatMap { week -> week.days.flatMap { day -> day.slots.map { it.lift.configurationId } } } }
            .toSet()
        assertTrue("recetas con máquinas inacreditables: ${used.intersect(uncurated)}", used.intersect(uncurated).isEmpty())
        // Westside cambió el hip thrust por la hiperextensión a 45° (DUP-07): la hiperextensión sigue en uso.
        assertTrue("la hiperextensión a 45° debe seguir en las recetas: $used", CatalogIds.HYPER_45 in used)
    }
}
