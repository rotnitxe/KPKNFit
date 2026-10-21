package com.example.kpkn.screens.nutrition

import com.example.kpkn.data.models.LoggedFood
import com.example.kpkn.data.models.NutritionLog
import com.example.kpkn.data.models.NutritionStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class CreatineStreakTest {

    /** Un registro de un día con 5 g de creatina. */
    private fun creatine(date: String, status: NutritionStatus = NutritionStatus.CONSUMED) = NutritionLog(
        id = "$date-$status",
        date = date,
        status = status,
        foods = listOf(LoggedFood(id = "creatina-$date", foodName = "Creatina", creatineG = 5.0)),
    )

    @Test
    fun cuentaLosDiasSeguidosHastaElDiaElegido() {
        val logs = listOf(creatine("2026-10-11"), creatine("2026-10-12"), creatine("2026-10-13"))

        assertEquals(3, countCreatineStreak(logs, "2026-10-13"))
    }

    @Test
    fun siHoyNoHayCreatinaLaRachaSigueDesdeAyer() {
        val logs = listOf(creatine("2026-10-11"), creatine("2026-10-12"))

        assertEquals(2, countCreatineStreak(logs, "2026-10-13"))
    }

    @Test
    fun unHuecoCortaLaRacha() {
        val logs = listOf(creatine("2026-10-09"), creatine("2026-10-11"), creatine("2026-10-12"), creatine("2026-10-13"))

        assertEquals(3, countCreatineStreak(logs, "2026-10-13"))
    }

    @Test
    fun loPlanificadoNoCuenta() {
        val logs = listOf(creatine("2026-10-12"), creatine("2026-10-13", NutritionStatus.PLANNED))

        assertEquals(1, countCreatineStreak(logs, "2026-10-13"))
    }
}
