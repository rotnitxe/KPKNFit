package com.example.kpkn.screens.home.design

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.RepRange
import com.example.kpkn.domain.training.HomeDayKind
import com.example.kpkn.domain.training.HomeNextSession
import com.example.kpkn.domain.training.HomeWeekDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** La dosis y el kicker del héroe (I2): funciones puras, sin Android. */
class HomeSessionHeroDoseTest {

    private fun set(
        id: String,
        reps: Int? = null,
        range: RepRange? = null,
        weight: Double? = null,
        rir: Int? = null,
        rpe: Double? = null,
        percent: Double? = null,
        amrap: Boolean = false,
    ) = ExerciseSet(
        id = id,
        targetReps = reps,
        targetRepsRange = range,
        weight = weight,
        targetRIR = rir,
        targetRPE = rpe,
        targetPercentageRM = percent,
        isAmrap = amrap,
        intensityMode = if (amrap) IntensityMode.AMRAP else null,
    )

    private fun exercise(vararg sets: ExerciseSet, reference1RM: Double? = null) =
        Exercise(id = "ej", name = "Ejercicio", sets = sets.toList(), reference1RM = reference1RM)

    @Test
    fun repeticiones_fijas() {
        assertEquals("3 × 8", heroDose(exercise(set("a", reps = 8), set("b", reps = 8), set("c", reps = 8))))
    }

    @Test
    fun rango_y_kilos() {
        val ex = exercise(*Array(4) { set("s$it", range = RepRange(5, 7), weight = 100.0) })
        assertEquals("4 × 5–7 · 100 kg", heroDose(ex))
    }

    @Test
    fun porcentaje_con_una_rm_da_kilos() {
        val ex = exercise(*Array(3) { set("s$it", reps = 5, percent = 80.0) }, reference1RM = 100.0)
        assertEquals("3 × 5 · 80 kg", heroDose(ex))
    }

    @Test
    fun porcentaje_sin_una_rm_da_el_porcentaje() {
        assertEquals("3 × 5 · 80 %", heroDose(exercise(*Array(3) { set("s$it", reps = 5, percent = 80.0) })))
    }

    @Test
    fun rir_y_rpe() {
        assertEquals("3 × 10 · RIR 2", heroDose(exercise(*Array(3) { set("s$it", reps = 10, rir = 2) })))
        assertEquals("2 × 6 · RPE 8,5", heroDose(exercise(*Array(2) { set("s$it", reps = 6, rpe = 8.5) })))
    }

    @Test
    fun amrap() {
        assertEquals("3 × AMRAP", heroDose(exercise(*Array(3) { set("s$it", reps = 8, amrap = true) })))
    }

    @Test
    fun sin_series_de_trabajo_no_hay_dosis() {
        assertNull(heroDose(exercise()))
        assertNull(heroDose(exercise(ExerciseSet(id = "vacia", isEmptySlot = true))))
    }

    @Test
    fun las_series_vacias_no_cuentan() {
        val ex = exercise(set("a", reps = 8, weight = 60.0), ExerciseSet(id = "vacia", isEmptySlot = true))
        assertEquals("1 × 8 · 60 kg", heroDose(ex))
    }

    @Test
    fun el_kicker_de_hoy_lleva_el_dia() {
        assertEquals("HOY · DÍA 2 DE 4", homeHeroKicker(HomeHeroMode.TODAY, LocalDate.of(2026, 10, 13), 2, 4))
    }

    @Test
    fun el_kicker_de_otro_dia_y_del_descanso() {
        assertEquals("JUEVES 15 · DÍA 3 DE 4", homeHeroKicker(HomeHeroMode.OTHER_DAY, LocalDate.of(2026, 10, 15), 3, 4))
        assertEquals("MIÉRCOLES 14", homeHeroKicker(HomeHeroMode.REST, LocalDate.of(2026, 10, 14), null, null))
    }

    @Test
    fun la_linea_siguiente_lleva_dia_numero_y_sesion_o_la_frase_de_hoy() {
        val week = listOf(HomeWeekDay(LocalDate.of(2026, 10, 14), "X", false, HomeDayKind.REST, null))

        assertEquals(
            "Siguiente: jueves 15 · Pierna A",
            homeHeroNextLine(week, HomeNextSession(LocalDate.of(2026, 10, 15), "Pierna A")),
        )
        assertEquals("Siguiente: la próxima semana", homeHeroNextLine(week, null))
        assertNull(homeHeroNextLine(emptyList(), HomeNextSession(LocalDate.of(2026, 10, 15), "Pierna A")))
    }
}
