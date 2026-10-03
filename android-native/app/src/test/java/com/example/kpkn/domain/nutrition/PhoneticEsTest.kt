package com.example.kpkn.domain.nutrition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneticEsTest {

    @Test
    fun `b and v collapse to B`() {
        assertEquals(PhoneticEs.encode("vaca"), PhoneticEs.encode("baca"))
        assertEquals(PhoneticEs.encode("beber"), PhoneticEs.encode("vever"))
    }

    @Test
    fun `h is silent`() {
        assertEquals(PhoneticEs.encode("huevo"), PhoneticEs.encode("uevo"))
        assertEquals(PhoneticEs.encode("hueso"), PhoneticEs.encode("ueso"))
    }

    @Test
    fun `c before e i is S, otherwise K`() {
        val cerveza = PhoneticEs.encode("cerveza")
        val cocina = PhoneticEs.encode("cocina")
        assertTrue(cerveza.startsWith("S"))
        assertTrue(cocina.startsWith("K"))
    }

    @Test
    fun `ll and y collapse`() {
        assertEquals(PhoneticEs.encode("lluvia"), PhoneticEs.encode("yuvia"))
    }

    @Test
    fun `qu maps to K`() {
        val queso = PhoneticEs.encode("queso")
        assertTrue(queso.contains("K"))
    }

    @Test
    fun `variant spellings of one sound still meet through their consonants`() {
        // WP-N11: the vowels no longer fold into one letter, so the sound is carried by the consonants and by w/u, h and qu/k.
        assertEquals(PhoneticEs.encode("poyo"), PhoneticEs.encode("pollo"))
        assertEquals(PhoneticEs.encode("huevo"), PhoneticEs.encode("uebo"))
        assertEquals(PhoneticEs.encode("huevo"), PhoneticEs.encode("wevo"))
        assertEquals(PhoneticEs.encode("queso"), PhoneticEs.encode("keso"))
        assertEquals(PhoneticEs.encode("betarraga"), PhoneticEs.encode("vetarraga"))
    }

    @Test
    fun `only a run of the same vowel collapses`() {
        assertEquals(PhoneticEs.encode("pollo"), PhoneticEs.encode("polloooo"))
        assertEquals(PhoneticEs.encode("arroz"), PhoneticEs.encode("arrooz"))
        assertEquals("UEBO", PhoneticEs.encode("huevo"))
        assertEquals("UBA", PhoneticEs.encode("uva"))
    }

    @Test
    fun `uva huevo ave and haba are four different words`() {
        val codes = listOf("uva", "huevo", "ave", "haba").map { it to PhoneticEs.encode(it) }
        for (i in codes.indices) for (j in i + 1 until codes.size) {
            assertNotEquals("${codes[i].first} vs ${codes[j].first}", codes[i].second, codes[j].second)
        }
    }

    @Test
    fun `a different vowel is a different word`() {
        assertNotEquals(PhoneticEs.encode("pasta"), PhoneticEs.encode("pesto"))
        assertNotEquals(PhoneticEs.encode("pasta"), PhoneticEs.encode("posta"))
        assertNotEquals(PhoneticEs.encode("pesto"), PhoneticEs.encode("posta"))
        assertNotEquals(PhoneticEs.encode("mote"), PhoneticEs.encode("mate"))
        assertNotEquals(PhoneticEs.encode("lima"), PhoneticEs.encode("lomo"))
    }

    @Test
    fun `empty or short input handled`() {
        assertEquals("A", PhoneticEs.encode("a"))
        assertEquals("BC", PhoneticEs.encode("bc"))
    }

    @Test
    fun `diacritics stripped`() {
        assertEquals(PhoneticEs.encode("cancion"), PhoneticEs.encode("canción"))
        assertEquals(PhoneticEs.encode("nino"), PhoneticEs.encode("niño"))
    }
}
