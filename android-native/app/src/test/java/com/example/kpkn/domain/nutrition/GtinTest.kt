package com.example.kpkn.domain.nutrition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GtinTest {

    // (texto leído, formato, forma canónica de 13 dígitos)
    private val validos = listOf(
        Triple("4006381333931", Gtin.Format.EAN13, "4006381333931"),
        Triple("7801234567894", Gtin.Format.EAN13, "7801234567894"),
        Triple("7804133000035", Gtin.Format.EAN13, "7804133000035"),
        Triple("036000291452", Gtin.Format.UPCA, "0036000291452"),
        Triple("0036000291452", Gtin.Format.EAN13, "0036000291452"),
        Triple("96385074", Gtin.Format.EAN8, "0000096385074"),
        Triple("73513537", Gtin.Format.EAN8, "0000073513537"),
        // UPC-E de 8 dígitos que NO cuadran como EAN-8: se expanden a su UPC-A (042100005264) y de ahí a 13.
        Triple("04252614", Gtin.Format.UPCE, "0042100005264"),
        Triple("01234514", Gtin.Format.UPCE, "0012100003454"),
        Triple("02345622", Gtin.Format.UPCE, "0023200004562"),
        Triple("19876502", Gtin.Format.UPCE, "0198000007652"),
        Triple("01234531", Gtin.Format.UPCE, "0012300000451"),
        Triple("01234543", Gtin.Format.UPCE, "0012340000053"),
        Triple("15432132", Gtin.Format.UPCE, "0154300000212"),
    )

    @Test fun `los codigos validos se reconocen con su formato y su forma canonica`() {
        validos.forEach { (leido, formato, canonico) ->
            val parsed = Gtin.parse(leido)
            assertEquals("formato de $leido", formato, parsed?.format)
            assertEquals("canónico de $leido", canonico, parsed?.ean13)
            assertEquals(canonico, Gtin.normalize(leido))
            assertTrue(Gtin.isValid(leido))
        }
    }

    @Test fun `espacios y guiones se descartan`() {
        assertEquals("4006381333931", Gtin.normalize(" 4006-3813 33931 "))
        assertEquals("0036000291452", Gtin.normalize("0 36000-291452"))
    }

    @Test fun `un UPC-A y su EAN-13 con cero delante dan la misma forma canonica`() {
        assertEquals(Gtin.normalize("036000291452"), Gtin.normalize("0036000291452"))
    }

    @Test fun `un codigo de 8 digitos que cuadra como EAN-8 y como UPC-E se lee como EAN-8 pero prueba las dos lecturas`() {
        // 01234565 cuadra por los dos caminos (su UPC-A es 012345000065): parse dice EAN-8 y las variantes llevan las dos lecturas.
        assertEquals(Gtin.Format.EAN8, Gtin.parse("01234565")?.format)
        assertEquals("0000001234565", Gtin.normalize("01234565"))
        val variantes = Gtin.candidates("01234565")
        assertTrue(variantes.containsAll(listOf("0000001234565", "1234565".padStart(8, '0'), "0012345000065", "012345000065", "12345000065")))
    }

    @Test fun `con compresion de 5 a 9 todo UPC-E cuadra tambien como EAN-8 y la camara lo expande antes`() {
        // Sin esto, la cámara perdería casi todos los UPC-E: por eso el lector expande con upcEToUpcA lo que ML Kit marca como UPC-E.
        assertEquals("012345000065", Gtin.upcEToUpcA("01234565"))
        assertEquals("012345000058", Gtin.upcEToUpcA("01234558"))
        assertEquals("042100005264", Gtin.upcEToUpcA("04252614"))
        assertEquals(Gtin.normalize("012345000065"), "0012345000065")
        assertNull(Gtin.upcEToUpcA("04252615"))
        assertNull(Gtin.upcEToUpcA("036000291452"))
        assertNull(Gtin.upcEToUpcA("27890124"))
    }

    @Test fun `un UPC-E se expande segun la cifra de compresion`() {
        // f = 0, 1, 2 → «a b f 0000 c d e»; 3 → «a b c 00000 d e»; 4 → «a b c d 00000 e».
        assertEquals("012100003454", Gtin.upcEToUpcA("01234514"))
        assertEquals("023200004562", Gtin.upcEToUpcA("02345622"))
        assertEquals("198000007652", Gtin.upcEToUpcA("19876502"))
        assertEquals("012300000451", Gtin.upcEToUpcA("01234531"))
        assertEquals("012340000053", Gtin.upcEToUpcA("01234543"))
    }

    @Test fun `un digito de control erroneo se rechaza en todos los formatos`() {
        listOf("4006381333932", "7801234567890", "036000291453", "96385075", "04252615").forEach {
            assertNull("$it debería ser inválido", Gtin.parse(it))
            assertFalse(Gtin.isValid(it))
            assertTrue(Gtin.candidates(it).isEmpty())
        }
    }

    @Test fun `longitudes raras letras y direcciones no son un codigo de producto`() {
        listOf(
            "", " ", "1234567", "123456789", "1234567890", "12345678901", "12345678901234", "123456789012345",
            "ABCD1234", "40063813339x1", "https://example.com/4006381333931", "4006381333931.0", "+4006381333931",
        ).forEach {
            assertNull("«$it» no debería ser un código", Gtin.parse(it))
            assertTrue(Gtin.candidates(it).isEmpty())
        }
    }

    @Test fun `un UPC-E de sistema distinto de 0 y 1 no se expande`() {
        // 8 dígitos con sistema 2, dígito de control EAN-8 y UPC-E erróneos: no hay lectura posible.
        assertNull(Gtin.parse("27890124"))
    }

    @Test fun `las variantes de un UPC-A incluyen EAN-13, 14 digitos, 12 y sin ceros`() {
        val variantes = Gtin.candidates("036000291452")
        assertEquals("0036000291452", variantes.first())
        assertTrue(variantes.containsAll(listOf("0036000291452", "00036000291452", "036000291452", "36000291452")))
        assertEquals(variantes.size, variantes.toSet().size)
    }

    @Test fun `las variantes de un EAN-8 incluyen su forma corta y las rellenas`() {
        val variantes = Gtin.candidates("96385074")
        assertTrue(variantes.containsAll(listOf("96385074", "0000096385074", "000096385074")))
        assertEquals(variantes.size, variantes.toSet().size)
    }

    @Test fun `las variantes de un UPC-E parten de su expansion y nunca del texto de 8 digitos`() {
        val variantes = Gtin.candidates("04252614")
        assertTrue(variantes.contains("0042100005264"))
        assertTrue(variantes.contains("042100005264"))
        assertFalse("sus 8 dígitos podrían ser el EAN-8 de otro producto", variantes.contains("04252614"))
    }

    @Test fun `un EAN-13 sin ceros delante solo tiene dos variantes`() {
        assertEquals(listOf("7801234567894", "07801234567894"), Gtin.candidates("7801234567894"))
    }

    @Test fun `el digito de control sale de la regla de pesos 3 y 1 desde la derecha`() {
        assertEquals(1, Gtin.checkDigitFor("400638133393"))
        assertEquals(4, Gtin.checkDigitFor("780123456789"))
        assertEquals(2, Gtin.checkDigitFor("03600029145"))
        assertEquals(4, Gtin.checkDigitFor("9638507"))
    }
}
