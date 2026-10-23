package com.example.kpkn.data.repository

import com.example.kpkn.data.models.ExerciseMuscleInfo
import org.junit.Assert.assertEquals
import org.junit.Test

/** AUD2-P05: un guardado de un propio que falla se revierte (sin ejercicios «fantasma»), y la lista se publica por UUID sin repetir. */
class CustomExerciseListOpsTest {
    private fun own(id: String, name: String, alias: String? = null) = ExerciseMuscleInfo(id = id, name = name, alias = alias, isCustom = true)

    @Test
    fun publishing_replacesByUuid_ignoringCase_andSortsByName() {
        val a = own("custom:AAA", "Zeta")
        val b = own("custom:bbb", "Alfa")
        val list = CustomExerciseListOps.withExercise(CustomExerciseListOps.withExercise(emptyList(), a), b)
        assertEquals(listOf("custom:bbb", "custom:AAA"), list.map { it.id })
        val renamed = a.copy(id = "custom:aaa", name = "Beta")
        val updated = CustomExerciseListOps.withExercise(list, renamed)
        assertEquals(2, updated.size)
        assertEquals(listOf("Alfa", "Beta"), updated.map { it.name })
    }

    @Test
    fun aFailedNewExercise_disappears() {
        val keep = own("custom:keep", "Keep")
        val failed = own("custom:new", "Nuevo")
        val optimistic = CustomExerciseListOps.withExercise(listOf(keep), failed)
        assertEquals(listOf(keep), CustomExerciseListOps.reverted(optimistic, failed, previous = null))
    }

    @Test
    fun aFailedEdit_restoresThePreviousVersion_withItsData() {
        val previous = own("custom:x", "Antes", alias = "datos viejos")
        val edited = previous.copy(name = "Después", alias = "datos nuevos")
        val optimistic = CustomExerciseListOps.withExercise(listOf(previous), edited)
        assertEquals("Después", optimistic.single().name)
        val reverted = CustomExerciseListOps.reverted(optimistic, edited, previous)
        assertEquals(listOf(previous), reverted)
    }
}
