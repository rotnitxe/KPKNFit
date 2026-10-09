package com.example.kpkn.data.importer

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class CsvReaderTest {

    @Test
    fun `detecta el punto y coma y respeta comillas y comas decimales`() {
        val sheet = CsvReader.parse(
            "Ejercicio;Series;Reps;Peso\n" +
                "\"Press banca; agarre medio\";4;6-8;\"80,5\"\n" +
                "\"Dominadas \"\"pronadas\"\"\";3;8;\n",
            "Día 1",
        )

        assertEquals("Día 1", sheet.name)
        assertEquals(
            listOf(
                listOf("Ejercicio", "Series", "Reps", "Peso"),
                listOf("Press banca; agarre medio", "4", "6-8", "80,5"),
                listOf("Dominadas \"pronadas\"", "3", "8", ""),
            ),
            sheet.rows,
        )
    }

    @Test
    fun `detecta el tabulador y quita el BOM`() {
        val sheet = CsvReader.parse("﻿Sentadilla\t4\t8\nPeso muerto\t3\t10\n", "Texto")

        assertEquals(
            listOf(listOf("Sentadilla", "4", "8"), listOf("Peso muerto", "3", "10")),
            sheet.rows,
        )
    }

    @Test
    fun `lee el archivo de ejemplo de recursos desde el flujo`() {
        val bytes = javaClass.classLoader!!.getResourceAsStream("import/programa.csv")!!.use { it.readBytes() }
        val sheet = CsvReader.read(ByteArrayInputStream(bytes), "programa")

        assertEquals("Press banca; agarre medio", sheet.rows[1][0])
        assertEquals("80,5", sheet.rows[1][3])
        assertEquals("", sheet.rows[2][3])
    }
}
