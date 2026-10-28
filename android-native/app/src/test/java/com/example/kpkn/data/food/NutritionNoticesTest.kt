package com.example.kpkn.data.food

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NutritionNoticesTest {

    private val dir = File("src/main/assets/$NUTRITION_NOTICES_DIR")

    @Test
    fun hayCincoAvisosConTituloUnico() {
        assertEquals(5, nutritionNotices.size)
        assertEquals(5, nutritionNotices.map { it.title }.toSet().size)
    }

    @Test
    fun cadaAvisoExisteYNoEstaVacio() {
        nutritionNotices.forEach { notice ->
            val file = File(dir, notice.fileName)
            assertTrue("falta ${file.path}", file.isFile)
            assertTrue("vacío ${file.path}", file.length() > 0)
            assertFalse("sin texto ${file.path}", decodeNutritionNotice(file.readBytes()).isBlank())
        }
    }

    @Test
    fun laDecodificacionQuitaElBom() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "Fuentes".toByteArray(Charsets.UTF_8)
        assertEquals("Fuentes", decodeNutritionNotice(bytes))
    }
}
