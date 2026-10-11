package com.example.kpkn.domain.training.importer

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportDraftPersistenceTest {

    /** La misma configuración con la que el alta guarda y lee sus borradores (SetupWizardViewModel). */
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun `un borrador guardado antes de los ids y de los avisos se lee y queda con ids unicos`() {
        val old = json.decodeFromString(ImportedProgramDraft.serializer(), LEGACY_JSON)

        assertEquals(
            listOf(
                "Hoja «Día 2», fila 4: no se entiende «Hacer 3 vueltas»",
                "Línea 9: «@@@» tiene 0 series, fuera de rango; se omite",
            ),
            old.warningMessages,
        )
        assertEquals(listOf("a1", "a2"), old.warnings.map { it.id })
        assertTrue(old.warnings.all { it.kind == ImportWarningKind.SKIPPED_ROW && it.weekIndex == null && it.dayId == null })
        assertTrue(allIds(old).all { it.isEmpty() })
        assertEquals(1, old.nextId)

        val repaired = ImportDraftEditor.withIds(old)

        val ids = allIds(repaired)
        assertTrue(ids.none { it.isEmpty() })
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(listOf("x1", "x2", "x3", "x4", "x5", "x6", "x7"), ids)
        assertEquals(8, repaired.nextId)
        assertEquals(old.warningMessages, repaired.warningMessages)
    }

    @Test
    fun `un borrador con ids no cambia al pasar por withIds`() {
        val parsed = ProgramImportParser.parseText("Día 1\nSentadilla 4x6\n", title = "Igual")

        assertSame(parsed, ImportDraftEditor.withIds(parsed))
    }

    @Test
    fun `ida y vuelta de un borrador nuevo queda igual`() {
        val parsed = ProgramImportParser.parseText(
            "Semana 1\nDía 1 — Pierna\nSentadilla 4x6 @120kg\nHacer 3 vueltas\nDía 2\nPress banca 3x8 RPE8\nDía 3\n",
            title = "Viaje",
        )
        val edited = ImportDraftEditor.add(ImportDraftEditor.ignoreWarning(parsed, "a1"), "w1-d2", 1, "Fondos", 3, 8, 10)

        val text = json.encodeToString(ImportedProgramDraft.serializer(), edited)
        val decoded = json.decodeFromString(ImportedProgramDraft.serializer(), text)

        assertEquals(edited, decoded)
        assertEquals(setOf("a1"), decoded.reviewedWarnings)
        assertEquals(2, decoded.nextId)
        assertEquals(listOf(ImportWarningKind.SKIPPED_ROW, ImportWarningKind.EMPTY_DAY), decoded.warnings.map { it.kind })
        assertNull(decoded.warnings[1].dayId)
        assertTrue(text.contains("\"kind\":\"SKIPPED_ROW\""))
    }

    private fun allIds(draft: ImportedProgramDraft): List<String> =
        draft.weeks.flatMap { week -> week.days.flatMap { day -> listOf(day.id) + day.exercises.map { it.id } } }

    private companion object {
        /** Un borrador como lo guardaba el alta antes de H1: avisos en cadenas y días y ejercicios sin id. */
        val LEGACY_JSON = """
            {
              "title": "Mi programa",
              "weeks": [
                {
                  "index": 1,
                  "days": [
                    {
                      "name": "Día 1",
                      "exercises": [
                        {"rawName": "Sentadilla", "sets": 4, "repsMin": 6, "repsMax": 8, "loadKg": 120.0, "percent1Rm": null, "rpe": 8.0, "rir": null, "restSeconds": 180, "notes": "Profundidad"},
                        {"rawName": "Press banca", "sets": 3, "repsMin": 8, "repsMax": 8, "loadKg": null, "percent1Rm": null, "rpe": null, "rir": null, "restSeconds": null, "notes": null}
                      ]
                    },
                    {
                      "name": "Día 2",
                      "exercises": [
                        {"rawName": "Dominadas", "sets": 3, "repsMin": 5, "repsMax": 5, "loadKg": null, "percent1Rm": null, "rpe": null, "rir": null, "restSeconds": null, "notes": null}
                      ]
                    }
                  ]
                },
                {
                  "index": 2,
                  "days": [
                    {
                      "name": "Día 1",
                      "exercises": [
                        {"rawName": "Sentadilla", "sets": 2, "repsMin": 5, "repsMax": 5, "loadKg": null, "percent1Rm": null, "rpe": null, "rir": null, "restSeconds": null, "notes": null}
                      ]
                    }
                  ]
                }
              ],
              "warnings": [
                "Hoja «Día 2», fila 4: no se entiende «Hacer 3 vueltas»",
                "Línea 9: «@@@» tiene 0 series, fuera de rango; se omite"
              ]
            }
        """.trimIndent()
    }
}
