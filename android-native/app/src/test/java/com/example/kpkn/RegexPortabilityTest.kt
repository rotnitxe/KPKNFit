package com.example.kpkn

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guardia de portabilidad (SE9). ICU, el motor de expresiones regulares de Android, rechaza las banderas en línea de Java como
 * `U`, y la JVM no se entera: `(?iU)` pasaba los tests y tumbaba el importador en el teléfono. Este test recorre el código de la
 * app y falla con el archivo y la línea. Los comentarios no cuentan: documentan la regla sin usarla.
 */
class RegexPortabilityTest {

    private val inlineU = Regex("""\(\?[a-zA-Z]*U[a-zA-Z]*[):]""")

    @Test
    fun ningunCodigoDeLaAppUsaLaBanderaUEnLinea() {
        val root = listOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory }
        val offenders = mutableListOf<String>()
        root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .sortedBy { it.path }
            .forEach { file ->
                file.readLines().forEachIndexed { index, line ->
                    val code = line.trim()
                    val comment = code.startsWith("//") || code.startsWith("*") || code.startsWith("/*")
                    if (!comment && inlineU.containsMatchIn(code)) {
                        offenders += "${file.relativeTo(root).path}:${index + 1}: $code"
                    }
                }
            }
        assertTrue(
            "Banderas en línea con U: ICU de Android no las acepta. Usa RegexEs.bounded y RegexOption, no (?iU).\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }
}
