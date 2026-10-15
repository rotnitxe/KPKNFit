package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.exercises.catalogv2.CatalogV2SelectionGateIssue
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * SE10: el bloqueo del cortafuegos se explica con el nombre del ejercicio y qué hacer. Nada de ids ni códigos en el texto.
 */
class SessionEditorCatalogGateMessageTest {

    private val legacyId = "facb4184-febc-4e9c-920a-265e21697674"

    @Test
    fun one_exercise_names_it_and_says_what_to_do() {
        val message = catalogGateBlockMessage(
            issues = listOf(issue(legacyId)),
            session = session(exercise(legacyId, "Press banca")),
        )

        assertEquals(
            "No se pudo guardar: «Press banca» no tiene su ficha del catálogo. Cámbialo por uno del catálogo o quítalo.",
            message,
        )
    }

    @Test
    fun two_exercises_name_both() {
        val message = catalogGateBlockMessage(
            issues = listOf(issue("a"), issue("b")),
            session = session(exercise("a", "Press banca"), exercise("b", "Remo con barra")),
        )

        assertEquals(
            "No se pudo guardar: «Press banca» y «Remo con barra» no tienen su ficha del catálogo. Cámbialos por uno del catálogo o quítalos.",
            message,
        )
    }

    @Test
    fun three_or_more_exercises_name_the_first_and_count_the_rest() {
        val message = catalogGateBlockMessage(
            issues = listOf(issue("a"), issue("b"), issue("c")),
            session = session(exercise("a", "Press banca"), exercise("b", "Remo con barra"), exercise("c", "Sentadilla")),
        )

        assertEquals(
            "No se pudo guardar: «Press banca» y 2 más no tienen su ficha del catálogo. Cámbialos por uno del catálogo o quítalos.",
            message,
        )
    }

    @Test
    fun the_same_exercise_with_several_codes_is_named_once() {
        val message = catalogGateBlockMessage(
            issues = listOf(issue("a", "incomplete_v2_identity"), issue("a", "legacy_chip_state_present")),
            session = session(exercise("a", "Press banca")),
        )

        assertEquals(
            "No se pudo guardar: «Press banca» no tiene su ficha del catálogo. Cámbialo por uno del catálogo o quítalo.",
            message,
        )
    }

    @Test
    fun an_exercise_without_name_is_called_ejercicio() {
        val message = catalogGateBlockMessage(
            issues = listOf(issue("a")),
            session = session(exercise("a", "   ")),
        )

        assertEquals(
            "No se pudo guardar: «Ejercicio» no tiene su ficha del catálogo. Cámbialo por uno del catálogo o quítalo.",
            message,
        )
    }

    @Test
    fun session_level_issue_is_explained_without_ids() {
        val message = catalogGateBlockMessage(
            issues = listOf(issue("session:s1", "duplicate_occurrence_id", "occ-1")),
            session = session(exercise("a", "Press banca")),
        )

        assertEquals("No se pudo guardar. Hay un ejercicio repetido en la sesión. Quita la copia y vuelve a guardar.", message)
    }

    @Test
    fun no_message_leaks_ids_codes_or_technical_detail() {
        val messages = listOf(
            catalogGateBlockMessage(listOf(issue(legacyId)), session(exercise(legacyId, "Press banca"))),
            catalogGateBlockMessage(listOf(issue("a"), issue("b")), session(exercise("a", "A"), exercise("b", "B"))),
            catalogGateBlockMessage(listOf(issue("session:s1", "mixed_catalog_revisions", "v1,v2")), session(exercise("a", "A"))),
        )

        messages.forEach { message ->
            assertFalse(message, message.contains(legacyId))
            assertFalse(message, message.contains("incomplete_v2_identity"))
            assertFalse(message, message.contains("session:"))
            assertFalse(message, message.contains("catalogRevision"))
            assertFalse(message, message.contains("("))
        }
    }

    private fun issue(exerciseId: String, code: String = "incomplete_v2_identity", detail: String? = null) =
        CatalogV2SelectionGateIssue(exerciseId = exerciseId, code = code, detail = detail)

    private fun exercise(id: String, name: String): Exercise = Exercise(id = id, name = name)

    private fun session(vararg exercises: Exercise): Session =
        Session(id = "session-sab", name = "Pierna A", exercises = exercises.toList())
}
