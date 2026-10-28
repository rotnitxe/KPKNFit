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

    @Test fun elOrdenDeGruposEsElPedido() {
        assertEquals(listOf(NutrientGroup.CARBS, NutrientGroup.FATS, NutrientGroup.VITAMINS, NutrientGroup.MINERALS, NutrientGroup.OTHER),
            groupedNutrientKeys().map { it.first })
        assertEquals(listOf("Carbohidratos", "Grasas", "Vitaminas", "Minerales", "Otros"), groupedNutrientKeys().map { it.first.label })
    }

    @Test fun laNiacinaEstaEnVitaminas() {
        assertEquals(NutrientGroup.VITAMINS, NutrientKey.NIACIN.group)
    }

    @Test fun elGrupoDeCadaClaveCoincideConLaLista() {
        groupedNutrientKeys().forEach { (group, keys) -> keys.forEach { assertEquals(group, it.group) } }
    }
}
