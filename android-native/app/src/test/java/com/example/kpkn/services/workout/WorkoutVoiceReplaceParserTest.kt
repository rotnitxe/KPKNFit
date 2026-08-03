package com.example.kpkn.services.workout

import java.text.Normalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutVoiceReplaceParserTest {

    private fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .trim()

    private fun parse(transcript: String): VoiceSessionCommand.ReplaceExercise? =
        WorkoutVoiceCommandParser.parseReplaceExercise(normalize(transcript))

    @Test
    fun reemplaza_target_por_reemplazo() {
        val command = parse("reemplaza el press de banca por curl martillo")!!
        assertEquals("press de banca", command.targetName)
        assertEquals("curl martillo", command.replacementPhrase)
    }

    @Test
    fun reemplazar_ejercicio_actual_por_reemplazo_con_opciones() {
        val command = parse("reemplazar el ejercicio actual por curl martillo con mancuernas y supino")!!
        assertEquals("", command.targetName)
        assertEquals("curl martillo con mancuernas y supino", command.replacementPhrase)
    }

    @Test
    fun sustituye_tambien_funciona() {
        val command = parse("sustituye sentadilla por press militar")!!
        assertEquals("sentadilla", command.targetName)
        assertEquals("press militar", command.replacementPhrase)
    }

    @Test
    fun este_es_ejercicio_actual() {
        val command = parse("reemplaza este por dominadas en polea alta")!!
        assertEquals("", command.targetName)
        assertEquals("dominadas en polea alta", command.replacementPhrase)
    }

    @Test
    fun sin_por_no_es_reemplazo() {
        assertNull(parse("reemplaza press de banca"))
    }

    @Test
    fun navegacion_no_colisiona() {
        assertNull(parse("cambiar a press de banca"))
    }
}
