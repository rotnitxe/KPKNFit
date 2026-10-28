package com.example.kpkn.domain.nutrition

import org.junit.Assert.*
import org.junit.Test

class GuideLimitsTest {
    private val adult = DriProfile(31, DriSex.FEMALE)
    @Test fun `grasa saturada es el diez por ciento de la energia a nueve kcal por gramo`() {
        val limit = GuideLimits.lookup(NutrientKey.SAT_FAT, null)!!
        assertEquals(22.222222, limit.ceiling(2000.0)!!, 1e-4)
        assertTrue(limit.exceeds(23.0, 2000.0)); assertFalse(limit.exceeds(22.0, 2000.0))
        assertEquals("https://www.who.int/publications/i/item/9789240073630", limit.sourceUrl)
    }
    @Test fun `sin energia valida el tope es nulo y nada excede`() {
        val limit = GuideLimits.lookup(NutrientKey.SAT_FAT, adult)!!
        listOf<Double?>(null, 0.0, -5.0, Double.NaN, Double.POSITIVE_INFINITY).forEach {
            assertNull(it.toString(), limit.ceiling(it)); assertFalse(limit.exceeds(500.0, it))
        }
        assertFalse(limit.exceeds(null, 2000.0)); assertFalse(limit.exceeds(Double.NaN, 2000.0))
    }
    @Test fun `grasa trans y azucares anadidos dependen de la energia`() {
        assertEquals(2.222222, GuideLimits.lookup(NutrientKey.TRANS_FAT, null)!!.ceiling(2000.0)!!, 1e-4)
        val sugars = GuideLimits.lookup(NutrientKey.ADDED_SUGARS, adult)!!
        assertEquals(50.0, sugars.ceiling(2000.0)!!, 1e-9)
        assertEquals("https://www.who.int/publications/i/item/9789241549028", sugars.sourceUrl)
    }
    @Test fun `cafeina segun etapa y nula sin perfil`() {
        assertNull(GuideLimits.lookup(NutrientKey.CAFFEINE, null))
        assertEquals(400.0, GuideLimits.lookup(NutrientKey.CAFFEINE, adult)!!.ceiling(null)!!, 0.0)
        assertEquals(200.0, GuideLimits.lookup(NutrientKey.CAFFEINE, DriProfile(31, DriSex.FEMALE, DriStage.PREGNANT))!!.ceiling(null)!!, 0.0)
        val lactating = GuideLimits.lookup(NutrientKey.CAFFEINE, DriProfile(31, DriSex.FEMALE, DriStage.LACTATING))!!
        assertEquals(200.0, lactating.ceiling(0.0)!!, 0.0)
        assertTrue(lactating.exceeds(200.1, null)); assertFalse(lactating.exceeds(200.0, null))
    }
    @Test fun `sodio y el resto no tienen limite de guia`() {
        assertNull(GuideLimits.lookup(NutrientKey.SODIUM, adult))
        listOf(NutrientKey.SUGARS, NutrientKey.CHOLESTEROL, NutrientKey.ALCOHOL, NutrientKey.PHOSPHORUS).forEach { assertNull(GuideLimits.lookup(it, adult)) }
    }
}
