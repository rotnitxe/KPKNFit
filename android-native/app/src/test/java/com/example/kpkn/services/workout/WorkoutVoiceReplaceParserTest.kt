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
    fun reemplaza_por_sin_target() {
        val command = parse("reemplaza por press inclinado")!!
        assertEquals("", command.targetName)
        assertEquals("press inclinado", command.replacementPhrase)
    }

    @Test
    fun cambia_este_ejercicio_por() {
        val command = parse("cambia este ejercicio por remo con barra")!!
        assertEquals("", command.targetName)
        assertEquals("remo con barra", command.replacementPhrase)
    }

    @Test
    fun pon_en_vez_de() {
        val command = parse("pon dominadas en vez de press de banca")!!
        assertEquals("press de banca", command.targetName)
        assertEquals("dominadas", command.replacementPhrase)
    }

    @Test
    fun navegacion_no_colisiona() {
        assertNull(parse("cambiar a press de banca"))
    }
}
