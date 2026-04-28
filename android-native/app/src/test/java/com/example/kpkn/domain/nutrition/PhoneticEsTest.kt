package com.example.kpkn.domain.nutrition

import org.junit.Assert.assertEquals
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
    fun `similar sounding words match`() {
        // beterraga vs betarraga should have same phonetic code
        assertEquals(PhoneticEs.encode("beterraga"), PhoneticEs.encode("betarraga"))
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
