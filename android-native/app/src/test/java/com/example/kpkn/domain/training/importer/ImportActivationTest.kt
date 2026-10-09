package com.example.kpkn.domain.training.importer

import com.example.kpkn.data.importer.XlsxReader
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.protocols.PlanProvenanceClass
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La activación de un programa importado: un .xlsx de prueba se lee, se analiza y se construye con los días del alta. Las
 * semanas, los días y las series llegan al programa; los ejercicios propios conservan su nombre.
 */
class ImportActivationTest {

    @Test
    fun anImportedWorkbookBecomesAnActivatableProgramWithItsWeeksDaysAndSets() {
        val sheets = XlsxReader.read(ByteArrayInputStream(workbook()))
        val draft = ProgramImportParser.parseSheets(sheets, "Plan de fuerza")
        val exercises = draft.weeks.flatMap { week -> week.days.flatMap { it.exercises } }
        val ownNames = exercises.map { it.rawName }.distinct().associateWith { null as String? }

        val program = ImportedProgramBuilder.build(
            draft = draft,
            resolved = ownNames,
            weekdays = listOf(1, 3, 5),
            programId = "import-activation",
        )

        assertEquals(PlanProvenanceClass.IMPORTED, program.planProvenance?.category)
        assertEquals(draft.weeks.size, program.weeks().size)
        assertEquals(draft.weeks.sumOf { it.days.size }, program.weeks().sumOf { it.sessions.size })
        assertTrue(program.weeks().flatMap { it.sessions }.all { session -> session.dayOfWeek in setOf(1, 3, 5) })
        val built = program.weeks().flatMap { it.sessions }.flatMap(Session::exercises)
        assertEquals(exercises.map { it.rawName }, built.map { it.name })
        assertEquals(exercises.map { it.sets }, built.map { it.sets.size })
        assertTrue(built.all { it.catalogConfigurationId == null })
    }

    private fun Program.weeks() = macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }

    /** Un libro de dos hojas (una por día) con celdas en línea: Sentadilla y Prensa, y Peso muerto. */
    private fun workbook(): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.entry("xl/workbook.xml", WORKBOOK)
            zip.entry("xl/_rels/workbook.xml.rels", RELATIONSHIPS)
            zip.entry("xl/sharedStrings.xml", SHARED_STRINGS)
            zip.entry("xl/worksheets/sheet1.xml", sheetXml(listOf(
                listOf("Ejercicio", "Series", "Reps"),
                listOf("Sentadilla", "4", "6"),
                listOf("Prensa", "3", "10"),
            )))
            zip.entry("xl/worksheets/sheet2.xml", sheetXml(listOf(
                listOf("Ejercicio", "Series", "Reps"),
                listOf("Peso muerto", "3", "5"),
            )))
        }
        return out.toByteArray()
    }

    private fun sheetXml(rows: List<List<String>>): String {
        val body = rows.mapIndexed { row, cells ->
            val cellXml = cells.mapIndexed { column, value ->
                """<c r="${'A' + column}${row + 1}" t="inlineStr"><is><t>$value</t></is></c>"""
            }.joinToString("")
            """<row r="${row + 1}">$cellXml</row>"""
        }.joinToString("")
        return """<?xml version="1.0" encoding="UTF-8"?>""" +
            """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>$body</sheetData></worksheet>"""
    }

    private fun ZipOutputStream.entry(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private companion object {
        const val WORKBOOK = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" """ +
            """xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""" +
            """<sheets><sheet name="Día 1" sheetId="1" r:id="rId1"/><sheet name="Día 2" sheetId="2" r:id="rId2"/></sheets></workbook>"""

        const val RELATIONSHIPS = """<?xml version="1.0" encoding="UTF-8"?>""" +
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
            """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>""" +
            """<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>""" +
            """<Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>""" +
            "</Relationships>"

        const val SHARED_STRINGS = """<?xml version="1.0" encoding="UTF-8"?>""" +
            """<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="0" uniqueCount="0"/>"""
    }
}
