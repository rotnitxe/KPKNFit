package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * LV5, auditoría: «Reemplazar» llegue por donde llegue (opciones del ejercicio, relator…) abre el selector que toca. Un ejercicio de cardio
 * se reemplaza con el selector del cardio; mandarlo al catálogo de fuerza lo convertía en un ejercicio de fuerza.
 */
class WorkoutReplacePickerTest {

    @Test
    fun elCardioReemplazaConSuSelectorYElRestoConElCatalogoDeFuerza() {
        val cardio = Exercise(id = "c1", name = "Cinta", cardioDetails = CardioDetails(type = CardioType.TREADMILL))
        val strength = Exercise(id = "s1", name = "Remo", sets = listOf(ExerciseSet(id = "r0", targetReps = 10)))

        assertEquals(ReplacePicker.CARDIO, replacePickerFor(cardio))
        assertEquals(ReplacePicker.STRENGTH_CATALOG, replacePickerFor(strength))
        assertEquals(ReplacePicker.STRENGTH_CATALOG, replacePickerFor(null))
    }
}
