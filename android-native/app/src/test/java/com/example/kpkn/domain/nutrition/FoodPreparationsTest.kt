package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.CookingMethod
import com.example.kpkn.data.models.LoggedFood
import com.example.kpkn.domain.nutrition.FoodPreparation.BAKED
import com.example.kpkn.domain.nutrition.FoodPreparation.COOKED
import com.example.kpkn.domain.nutrition.FoodPreparation.FRIED
import com.example.kpkn.domain.nutrition.FoodPreparation.GRILLED
import com.example.kpkn.domain.nutrition.FoodPreparation.RAW
import com.example.kpkn.domain.nutrition.FoodPreparation.SAUTEED
import com.example.kpkn.domain.nutrition.FoodPreparation.STEAMED
import com.example.kpkn.domain.nutrition.v2.FoodStateV2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** «Cómo lo preparaste» y la fila «Aceite» de la hoja «Ajustar un alimento» (Arreglos v1). */
class FoodPreparationsTest {

    @Test fun chipsKeepTheFixedOrderAndTheirTexts() {
        assertEquals(
            listOf("Crudo", "Cocido", "Al vapor", "A la plancha", "Al horno", "Salteado", "Frito"),
            FoodPreparation.entries.map { it.label },
        )
        val all = FoodPreparations.available(listOf(FRIED, BAKED, GRILLED, STEAMED, COOKED, RAW), "VERDURAS", FRIED)
        assertEquals(listOf(RAW, COOKED, STEAMED, GRILLED, BAKED, SAUTEED, FRIED), all)
    }

    @Test fun onlyPreparationsWithARecordPlusTheCurrentOne() {
        assertEquals(listOf(BAKED, FRIED), FoodPreparations.available(listOf(FRIED, BAKED), "TUBERCULOS", FRIED))
        // Sin fichas, solo la actual (registrador clásico).
        assertEquals(listOf(GRILLED), FoodPreparations.available(emptyList(), null, GRILLED))
    }

    @Test fun sauteedAppearsOnlyWhenCookedExists() {
        assertTrue(SAUTEED in FoodPreparations.available(listOf(COOKED, FRIED), "VERDURAS", FRIED))
        assertFalse(SAUTEED in FoodPreparations.available(listOf(BAKED, FRIED), "VERDURAS", FRIED))
        assertEquals(COOKED, FoodPreparations.recordOf(SAUTEED))
        assertEquals(FRIED, FoodPreparations.recordOf(FRIED))
    }

    @Test fun rawOnlyForFoodsEatenRaw() {
        // Papas fritas caseras: ficha frita, horneada y cruda; a las papas no se les ofrece «Crudo».
        assertEquals(listOf(BAKED, FRIED), FoodPreparations.available(listOf(FRIED, BAKED, RAW), "TUBERCULOS", FRIED))
        listOf("TUBERCULOS", "CEREALES_PANES", "PASTAS_ARROZ", "LEGUMBRES", "CARNES", "HUEVOS").forEach {
            assertFalse(it, FoodPreparations.eatenRaw(it))
            assertFalse(it, RAW in FoodPreparations.available(listOf(RAW, COOKED), it, COOKED))
        }
        // Verduras, frutas, pescados (ceviche) y frutos secos sí.
        listOf("VERDURAS", "FRUTAS", "PESCADOS_MARISCOS", "FRUTOS_SECOS", null).forEach {
            assertTrue(it.toString(), RAW in FoodPreparations.available(listOf(RAW, COOKED), it, COOKED))
        }
        // Registrado en crudo (arroz pesado en crudo): «Crudo» se queda, también tras pasar a cocido en la misma hoja.
        assertEquals(listOf(RAW, COOKED, SAUTEED), FoodPreparations.available(listOf(RAW, COOKED), "PASTAS_ARROZ", RAW))
        assertEquals(listOf(RAW, COOKED, SAUTEED), FoodPreparations.available(listOf(RAW, COOKED), "PASTAS_ARROZ", COOKED, keepRaw = true))
    }

    @Test fun statesAndMethodsMapToPreparations() {
        assertEquals(FRIED, FoodPreparations.of(FoodStateV2.FRIED))
        assertEquals(STEAMED, FoodPreparations.of(FoodStateV2.STEAMED))
        assertEquals(FRIED, FoodPreparations.of(CookingMethod.EMPANIZADO_FRITO))
        assertEquals(GRILLED, FoodPreparations.of(CookingMethod.ASADO_PARRILLA))
        assertNull(FoodPreparations.of(CookingMethod.AHUMADO))
        // Las chips de estado del catálogo llegan con estas etiquetas.
        assertEquals(GRILLED, FoodPreparations.fromLabel("A la plancha"))
        assertEquals(BAKED, FoodPreparations.fromLabel("Al horno"))
        assertNull(FoodPreparations.fromLabel("Sin especificar"))
    }

    @Test fun oilOptionsPerPreparation() {
        val fryLike = listOf(OilAmount.LITTLE, OilAmount.NORMAL, OilAmount.LOTS)
        val ovenLike = listOf(OilAmount.NONE, OilAmount.LITTLE, OilAmount.NORMAL)
        assertEquals(fryLike, FoodPreparations.oilOptions(FRIED))
        assertEquals(fryLike, FoodPreparations.oilOptions(SAUTEED))
        assertEquals(ovenLike, FoodPreparations.oilOptions(BAKED))
        assertEquals(ovenLike, FoodPreparations.oilOptions(GRILLED))
        listOf(COOKED, STEAMED, RAW, null).forEach { assertEquals(emptyList<OilAmount>(), FoodPreparations.oilOptions(it)) }
        assertEquals(listOf("Poco", "Normal", "Mucho"), fryLike.map { it.label })
        assertEquals(listOf("Sin", "Poco", "Normal"), ovenLike.map { it.label })
    }

    @Test fun oilDefaultsAndLines() {
        listOf(FRIED, SAUTEED, BAKED, GRILLED).forEach { assertEquals(OilAmount.NORMAL, FoodPreparations.defaultOil(it)) }
        assertNull(FoodPreparations.defaultOil(COOKED))
        assertEquals(OilAmount.NORMAL, FoodPreparations.recordOil(FRIED))
        assertEquals(OilAmount.NONE, FoodPreparations.recordOil(BAKED))
        assertEquals("Cocido en agua: no hay aceite que sumar.", FoodPreparations.noOilLine(COOKED))
        assertEquals("Al vapor: no hay aceite que sumar.", FoodPreparations.noOilLine(STEAMED))
        assertEquals("Crudo: no hay aceite que sumar.", FoodPreparations.noOilLine(RAW))
        assertNull(FoodPreparations.noOilLine(FRIED))
    }

    @Test fun oilAmountsMapToTheLoggerLevels() {
        assertEquals(
            listOf("sin aceite", "poco", "medio", "abundante"),
            listOf(OilAmount.NONE, OilAmount.LITTLE, OilAmount.NORMAL, OilAmount.LOTS).map { it.level },
        )
        assertEquals(OilAmount.NORMAL, OilAmount.fromLevel("medio"))
        assertEquals(OilAmount.LOTS, OilAmount.fromLevel("Abundante"))
        assertNull(OilAmount.fromLevel("con mantequilla"))
    }

    @Test fun oilKcalFollowsThePreparationAndTheAmount() {
        val starch = OilAbsorptionCategory.STARCH_BATTER // 12 g por 100 g al freír
        // 200 g de papas fritas: 24 g de aceite «Normal» = 216 kcal.
        assertEquals(24.0, FoodPreparations.oilGrams(FRIED, OilAmount.NORMAL, starch, 200.0), 1e-9)
        assertEquals(216, FoodPreparations.oilKcal(FRIED, OilAmount.NORMAL, starch, 200.0))
        assertTrue(FoodPreparations.oilKcal(FRIED, OilAmount.LITTLE, starch, 200.0) < 216)
        assertTrue(FoodPreparations.oilKcal(FRIED, OilAmount.LOTS, starch, 200.0) > 216)
        // Al horno absorbe la mitad que frito; «Sin» no suma.
        assertEquals(108, FoodPreparations.oilKcal(BAKED, OilAmount.NORMAL, starch, 200.0))
        assertEquals(0, FoodPreparations.oilKcal(BAKED, OilAmount.NONE, starch, 200.0))
        // Proporcional a los gramos; sin aceite en cocido o en una opción que la preparación no ofrece.
        assertEquals(2 * FoodPreparations.oilKcal(SAUTEED, OilAmount.NORMAL, starch, 100.0), FoodPreparations.oilKcal(SAUTEED, OilAmount.NORMAL, starch, 200.0))
        assertEquals(0, FoodPreparations.oilKcal(COOKED, OilAmount.NORMAL, starch, 200.0))
        assertEquals(0, FoodPreparations.oilKcal(FRIED, OilAmount.NONE, starch, 200.0))
        assertEquals("+216 kcal", FoodPreparations.oilCaption(216))
        assertEquals("+1.080 kcal", FoodPreparations.oilCaption(1080))
        assertEquals("sin aceite", FoodPreparations.oilCaption(0))
    }

    @Test fun theFriedRecordAlreadyCarriesNormalOil() {
        val starch = OilAbsorptionCategory.STARCH_BATTER
        assertEquals(0.0, FoodPreparations.oilDeltaGrams(FRIED, OilAmount.NORMAL, starch, 200.0), 1e-9)
        assertTrue(FoodPreparations.oilDeltaGrams(FRIED, OilAmount.LITTLE, starch, 200.0) < 0.0)
        assertTrue(FoodPreparations.oilDeltaGrams(FRIED, OilAmount.LOTS, starch, 200.0) > 0.0)
        // Horno, plancha y salteado parten de una ficha sin aceite.
        assertEquals(12.0, FoodPreparations.oilDeltaGrams(BAKED, OilAmount.NORMAL, starch, 200.0), 1e-9)
        assertEquals(12.0, FoodPreparations.oilDeltaGrams(SAUTEED, OilAmount.NORMAL, starch, 200.0), 1e-9)
        assertEquals(0.0, FoodPreparations.oilDeltaGrams(COOKED, null, starch, 200.0), 1e-9)
    }

    @Test fun oilIsAddedAndRemovedFromTheLoggedFood() {
        val base = LoggedFood(foodName = "Papas", amount = 200.0, calories = 174.0, protein = 4.0, carbs = 40.0, fats = 0.2)
        val oiled = FoodPreparations.withOil(base, 12.0)
        assertEquals(282.0, oiled.calories, 1e-9)
        assertEquals(12.2, oiled.fats, 1e-9)
        assertEquals(4.0, oiled.protein, 1e-9)
        val less = FoodPreparations.withOil(base, -50.0)
        assertEquals(0.0, less.fats, 1e-9)
        assertEquals(0.0, less.calories, 1e-9)
        assertEquals(base, FoodPreparations.withOil(base, 0.0))
    }
}
