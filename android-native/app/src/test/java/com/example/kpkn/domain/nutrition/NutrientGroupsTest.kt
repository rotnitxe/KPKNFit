package com.example.kpkn.domain.nutrition

import org.junit.Assert.*
import org.junit.Test

class NutrientGroupsTest {
    @Test fun cadaNutrienteEstaEnExactamenteUnGrupo() {
        val all = groupedNutrientKeys().flatMap { it.second }
        assertEquals(NutrientKey.entries.size, all.size)
        assertEquals(NutrientKey.entries.toSet(), all.toSet())
        assertEquals(all.size, all.toSet().size)
        NutrientKey.entries.forEach { key -> assertEquals(1, groupedNutrientKeys().count { key in it.second }) }
    }

    @Test fun elOrdenDeGruposEsElDelLienzo() {
        assertEquals(listOf(NutrientGroup.VITAMINS, NutrientGroup.MINERALS, NutrientGroup.FATS, NutrientGroup.CARBS, NutrientGroup.OTHER),
            groupedNutrientKeys().map { it.first })
        assertEquals(listOf("Vitaminas", "Minerales", "Grasas", "Carbos", "Otros"), groupedNutrientKeys().map { it.first.label })
    }

    @Test fun laNiacinaEstaEnVitaminas() {
        assertEquals(NutrientGroup.VITAMINS, NutrientKey.NIACIN.group)
    }

    @Test fun elGrupoDeCadaClaveCoincideConLaLista() {
        groupedNutrientKeys().forEach { (group, keys) -> keys.forEach { assertEquals(group, it.group) } }
    }

    @Test fun laHuellaTieneLas24DelLienzoEnSuOrden() {
        assertEquals(24, footprintSlots.size)
        assertEquals(24, footprintSlots.map { it.key }.toSet().size)
        assertEquals(listOf("A", "B1", "B2", "B3", "B6", "B9", "B12", "Co", "C", "D", "E", "K", "Ca", "P", "Mg", "Fe", "Zn", "Se", "I", "Na", "K", "Fi", "ω3", "Caf"),
            footprintSlots.map { it.sigla })
        assertEquals(12, footprintSlots.count { it.band == FootprintBand.VITAMINS })
        assertEquals(9, footprintSlots.count { it.band == FootprintBand.MINERALS })
        assertEquals(listOf(NutrientKey.FIBER, NutrientKey.ALA, NutrientKey.CAFFEINE), footprintSlots.filter { it.band == FootprintBand.OTHER }.map { it.key })
    }

    @Test fun lasClavesFueraDeLaHuellaSoloVanEnLasListas() {
        val outside = NutrientKey.entries.toSet() - footprintSlots.map { it.key }.toSet()
        assertEquals(setOf(NutrientKey.SUGARS, NutrientKey.ADDED_SUGARS, NutrientKey.SAT_FAT, NutrientKey.MONO_FAT, NutrientKey.POLY_FAT,
            NutrientKey.TRANS_FAT, NutrientKey.EPA_DHA, NutrientKey.CHOLESTEROL, NutrientKey.ALCOHOL), outside)
    }

    @Test fun losTopesSonLosDeLaGuia() {
        assertEquals(setOf(NutrientKey.SODIUM, NutrientKey.CAFFEINE, NutrientKey.SAT_FAT, NutrientKey.TRANS_FAT, NutrientKey.ADDED_SUGARS),
            NutrientKey.entries.filter { it.isCeiling }.toSet())
    }
}
