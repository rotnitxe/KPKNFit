package com.example.kpkn.services.workout

import java.text.Normalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutVoiceAddExerciseParserTest {

    private fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .trim()

    private fun parse(transcript: String): VoiceSessionCommand.AddExercise? =
        WorkoutVoiceCommandParser.parseAddExercise(normalize(transcript))

    @Test
    fun agrega_ejercicio_simple() {
        val command = parse("agrega sentadilla hack")!!
        assertEquals("sentadilla hack", command.exercisePhrase)
        assertFalse(command.atEnd)
    }

    @Test
    fun anade_ejercicio_al_final() {
        val command = parse("añade curl de biceps al final")!!
        assertEquals("curl de biceps", command.exercisePhrase)
        assertTrue(command.atEnd)
    }

    @Test
    fun agregar_el_ejercicio_despues_de_este() {
        val command = parse("agregar el ejercicio elevaciones laterales después de este")!!
        assertEquals("elevaciones laterales", command.exercisePhrase)
        assertFalse(command.atEnd)
    }

    @Test
    fun suma_ejercicio() {
        val command = parse("suma extensiones de cuadriceps")!!
        assertEquals("extensiones de cuadriceps", command.exercisePhrase)
        assertFalse(command.atEnd)
    }

    @Test
    fun agregar_serie_no_colisiona() {
        assertNull(parse("agrega una serie"))
        assertNull(parse("añadir serie"))
        assertNull(parse("agregar otra serie"))
    }

    @Test
    fun palabras_cortas_o_no_ejercicios_no_colisionan() {
        assertNull(parse("agrega mas peso"))
        assertNull(parse("agrega kilos"))
    }
}
