package com.example.kpkn.domain.nutrition.label

import org.junit.Assert.*
import org.junit.Test

class NutritionLabelParserTest {
    private fun rows(vararg lines: String) = lines.map { it.split('|').map(String::trim) }
    private fun parse(vararg lines: String) = NutritionLabelParser.parse(rows(*lines))
    private fun value(result: LabelParse, field: LabelField, expected: Double, tolerance: Double = 1e-8) = assertEquals(field.name, expected, requireNotNull(result.fields[field]).value, tolerance)
    @Test fun elOcrQueLee109EnVezDe10GramosSeDescartaSinAdivinarLaCantidad() {
        // Filas observadas en ML Kit nativo: la «g» pegada al 10 se reconoció como otro 9.
        val label = parse("Por 100g", "Energia | 200 kcal", "Proteinas | 109", "Carbohidratos | 20 g", "Grasas | 8g")
        assertFalse(label.fields.containsKey(LabelField.PROTEIN_G))
        assertTrue(label.warnings.any { it.contains("100 g") })
        value(label, LabelField.ENERGY_KCAL, 200.0)
    }
    @Test fun unNumeroSinUnidadNoSeCorrigeQuitandoElUltimoDigito() {
        val label = parse("Nutrition Facts", "Serving size 40 g", "Calories | 200", "Protein | 39", "Total Carbohydrate | 1 g", "Total Fat | 1 g")
        value(label, LabelField.PROTEIN_G, 39.0)
        assertEquals(LabelConfidence.UNSURE, label.fields.getValue(LabelField.PROTEIN_G).confidence)
    }
    @Test fun etiquetaUEConEnergiaDobleYSal() {
        val label = parse("Información nutricional", "Por 100 g", "Valor energético | 840 kJ / 200 kcal", "Proteínas | 10 g", "Hidratos de carbono | 20 g", "Grasas | 8 g", "De los cuales azúcares | 5 g", "Fibra alimentaria | 2 g", "Sal | 0,5 g")
        assertEquals(LabelBasis.PER_100G, label.basis); value(label, LabelField.ENERGY_KCAL, 200.0); value(label, LabelField.SODIUM_MG, 200.0)
        value(label, LabelField.SUGAR_G, 5.0); value(label, LabelField.FIBER_G, 2.0)
        assertEquals(LabelConfidence.DERIVED, label.fields[LabelField.SODIUM_MG]!!.confidence)
    }
    @Test fun etiquetaLatinoamericanaPorPorcion() {
        val label = parse("Información nutrimental", "Por porción de 30 g", "Calorías | 120 kcal", "Proteínas | 3 g", "Carbohidratos | 15 g", "Grasas totales | 5 g", "Sodio | 150 mg")
        assertEquals(LabelBasis.PER_SERVING, label.basis); assertEquals(30.0, label.servingSize!!, 0.0); value(label, LabelField.ENERGY_KCAL, 120.0); value(label, LabelField.SODIUM_MG, 150.0)
    }
    @Test fun etiquetaFDAConCaloriesSinUnidadYTamanioDePorcion() {
        val label = parse("Nutrition Facts", "Serving size 1 cup (40 g)", "Calories | 150", "Protein | 4 g", "Total Carbohydrate | 20 g", "Total Fat | 6 g", "Sodium | 120 mg")
        assertEquals(LabelBasis.PER_SERVING, label.basis); assertEquals(40.0, label.servingSize!!, 0.0); value(label, LabelField.ENERGY_KCAL, 150.0)
        assertEquals(LabelConfidence.READ, label.fields[LabelField.ENERGY_KCAL]!!.confidence)
    }
    @Test fun dosColumnasConservanAmbasYSePrefiereCienGramos() {
        val input = rows("Información nutricional", " | por 100 g | por porción de 30 g", "Energía | 400 kcal | 120 kcal", "Proteínas | 10 g | 3 g", "Hidratos de carbono | 45 g | 13,5 g", "Grasas | 20 g | 6 g")
        val label = NutritionLabelParser.parse(input)
        value(label, LabelField.ENERGY_KCAL, 400.0); assertEquals(2, label.columns.size)
        val portion = label.forBasis(LabelBasis.PER_SERVING)
        value(portion, LabelField.ENERGY_KCAL, 120.0); value(portion, LabelField.CARBS_G, 13.5)
        assertEquals(LabelConfidence.READ, portion.fields[LabelField.ENERGY_KCAL]!!.confidence)
        value(portion.forBasis(LabelBasis.PER_100G), LabelField.ENERGY_KCAL, 400.0)
    }
    @Test fun soloKJSeConvierteConAviso() {
        val label = parse("Por 100 ml", "Energía | 418,4 kJ", "Proteínas | 2 g", "Carbohidratos | 18 g", "Grasas | 1 g")
        assertEquals(LabelBasis.PER_100ML, label.basis); value(label, LabelField.ENERGY_KCAL, 100.0)
        assertEquals(LabelConfidence.DERIVED, label.fields[LabelField.ENERGY_KCAL]!!.confidence); assertTrue(label.warnings.any { "kJ" in it })
    }
    @Test fun salNoSeConfundeConSodioYSeConvierteLaUnidad() {
        val label = parse("Por 100 g", "Sal | 1,2 g", "Proteínas | 2 g", "Carbohidratos | 30 g", "Grasas | 4 g")
        value(label, LabelField.SODIUM_MG, 480.0)
        value(parse("Por 100 g", "Sal | 500 mg"), LabelField.SODIUM_MG, 200.0)
        value(parse("Por 100 g", "Sodio | 0,2 g", "Sal | 1 g"), LabelField.SODIUM_MG, 200.0)
    }
    @Test fun trazasSonCeroConAvisoDudoso() {
        val label = parse("Por 100 g", "Fibra | trazas", "Proteínas | 2 g", "Carbohidratos | 5 g", "Grasas | 1 g")
        value(label, LabelField.FIBER_G, 0.0); assertEquals(LabelConfidence.UNSURE, label.fields[LabelField.FIBER_G]!!.confidence)
        assertTrue(label.warnings.isNotEmpty())
    }
    @Test fun menorQueNoSePresentaComoCeroExacto() {
        val label = parse("Por 100 g", "Azúcares | <0,5 g", "Proteínas | 1 g", "Carbohidratos | 5 g", "Grasas | 2 g")
        value(label, LabelField.SUGAR_G, 0.0); assertEquals(LabelConfidence.UNSURE, label.fields[LabelField.SUGAR_G]!!.confidence)
    }
    @Test fun cerosLeidosSonCerosDeclarados() {
        val label = parse("Por 100 ml", "Calories | 0", "Protein | 0 g", "Total Fat | 0 g", "Total Carbohydrate | 0 g", "Sodium | 0 mg")
        assertEquals(5, label.fields.size); label.fields.values.forEach { assertEquals(0.0, it.value, 0.0); assertEquals(LabelConfidence.READ, it.confidence) }
    }
    @Test fun ruidoDeCifraYUnidadSeCorrigeSoloEnSusPosiciones() {
        val label = parse("Por 100 g", "Proteínas | 1O,5 g", "Grasas | 5 9", "Carbohidratos | 20 g", "Energía | 167 kcal")
        value(label, LabelField.PROTEIN_G, 10.5); value(label, LabelField.FAT_G, 5.0)
        assertEquals(LabelConfidence.UNSURE, label.fields[LabelField.PROTEIN_G]!!.confidence); assertEquals(LabelConfidence.UNSURE, label.fields[LabelField.FAT_G]!!.confidence)
    }
    @Test fun negativosYMasDeCienGramosSeDescartan() {
        val label = parse("Por 100 g", "Proteínas | -2 g", "Grasas | 120 g", "Carbohidratos | 20 g", "Energía | 80 kcal")
        assertFalse(label.fields.containsKey(LabelField.PROTEIN_G)); assertFalse(label.fields.containsKey(LabelField.FAT_G)); value(label, LabelField.CARBS_G, 20.0)
        assertTrue(label.warnings.size >= 2)
    }
    @Test fun coherenciaSeAdvierteSinInventarNiBorrarLoLeido() {
        val label = parse("Por 100 g", "Energía | 800 kcal", "Proteínas | 2 g", "Carbohidratos | 10 g", "Azúcares | 15 g", "Grasas | 2 g", "De las cuales saturadas | 5 g")
        value(label, LabelField.ENERGY_KCAL, 800.0); value(label, LabelField.SATURATED_G, 5.0)
        assertTrue(label.warnings.any { "no cuadran" in it }); assertTrue(label.warnings.any { "saturadas" in it }); assertTrue(label.warnings.any { "azúcares" in it })
    }
    @Test fun vaciaYTextoPromocionalNoSonEtiquetas() {
        assertTrue(NutritionLabelParser.parse(emptyList()).fields.isEmpty())
        assertTrue(parse("Oferta 2x1", "El mejor producto", "20 unidades").fields.isEmpty())
        assertTrue(parse("Protein 20 g", "Barra estrella").fields.isEmpty())
    }
    @Test fun baseNoLeidaSeDeclaraDesconocidaYNuncaSePresumeCien() {
        val label = parse("Calories 100", "Protein 5 g", "Total Carbohydrate 10 g", "Total Fat 2 g")
        assertEquals(LabelBasis.UNKNOWN, label.basis); assertTrue(label.fields.isNotEmpty()); assertTrue(label.warnings.any { "base" in it })
    }
    @Test fun porcionLeidaPermiteConvertirSinPerderLasCifrasOriginales() {
        val label = parse("Información nutricional", "Por 100 g", "Tamaño de porción 30 g", "Energía 200 kcal", "Proteínas 10 g", "Carbohidratos 20 g", "Grasas 8 g")
        val portion = label.forBasis(LabelBasis.PER_SERVING)
        value(portion, LabelField.ENERGY_KCAL, 60.0); value(portion, LabelField.PROTEIN_G, 3.0)
        assertEquals(LabelConfidence.DERIVED, portion.fields[LabelField.PROTEIN_G]!!.confidence)
        value(portion.forBasis(LabelBasis.PER_100G), LabelField.ENERGY_KCAL, 200.0)
    }
    @Test fun signoNegativoSeparadoNoSeConvierteEnUnPositivo() {
        listOf("- 2 g","− 2 g","< -0,5 g").forEach { text ->
            val label=parse("Por 100 g","Proteínas | 10 g","Carbohidratos | 20 g","Grasas | $text")
            assertFalse(label.fields.containsKey(LabelField.FAT_G));assertTrue(label.warnings.isNotEmpty())
        }
    }
}
