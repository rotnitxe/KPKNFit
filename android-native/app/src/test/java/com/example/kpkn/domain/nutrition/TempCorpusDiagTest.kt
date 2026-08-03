package com.example.kpkn.domain.nutrition

import org.junit.Test

class TempCorpusDiagTest {

    private fun show(desc: String) {
        val result = parseMealDescription(desc)
        val repr = result.items.joinToString(" | ") {
            "tag=${it.tag} q=${it.quantity} g=${it.amountGrams} intent=${it.amountIntent} " +
                "cook=${it.cookingMethod} portion=${it.portion} excl=${it.isExcluded}"
        }
        println("DIAGC [$desc] -> $repr")
    }

    @Test
    fun diagnostic() {
        for (desc in listOf(
            "té con leche", "omelette de queso", "leche con chocolate", "licuado de plátano",
            "batido de proteína", "arroz con pollo", "pollo al curry", "guiso de pollo",
            "hamburguesa con queso", "camarones al ajo", "pan con palta", "galletas de chocolate",
            "queque de plátano", "helado de vainilla", "café con leche y azúcar", "yogurt con granola",
            "avena con leche y canela", "claras de huevo", "té de hierbas", "jugo de manzana",
            "un vaso de jugo", "palta con limón", "frutas con yogurt", "mariscos al pil pil",
            "ensalada mixta", "sopa de pollo", "cazuela de ave", "pollo con papas", "atún con palta",
        )) {
            show(desc)
        }
    }
}
