package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.DailyWellbeingLog
import com.example.kpkn.data.models.IntensityLevel
import com.example.kpkn.data.models.MoodState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * El registro que se guarda al confirmar el estado previo (LV10b): la ruta de siempre, ahora con el ajuste oficial. Solo lo editado
 * pasa a ser un ajuste manual; la Columna se invierte UNA vez y los músculos van por pilar.
 */
class ReadinessWellbeingLogTest {

    /** Un inversor reconocible: 100 − valor. Cuenta cuántas veces se le llama. */
    private class Inverter {
        var calls = 0
        val fn: (Int) -> Int = { displayed ->
            calls++
            100 - displayed
        }
    }

    private fun build(
        today: DailyWellbeingLog? = null,
        discomforts: List<String> = emptyList(),
        manualNeural: Int? = null,
        manualSpinal: Int? = null,
        manualMuscular: Int? = null,
        manualMuscles: Map<String, Int> = emptyMap(),
        inverter: Inverter = Inverter(),
    ): DailyWellbeingLog = buildReadinessWellbeingLog(
        today = today,
        newId = "nuevo",
        date = "2026-10-16",
        discomforts = discomforts,
        manualNeural = manualNeural,
        manualSpinal = manualSpinal,
        manualMuscular = manualMuscular,
        manualMuscleBatteries = manualMuscles,
        invertDisplayedStructure = inverter.fn,
    )

    @Test
    fun `sin registro de hoy usa los valores de siempre y no ancla nada`() {
        val inverter = Inverter()
        val log = build(inverter = inverter)
        assertEquals("nuevo", log.id)
        assertEquals("2026-10-16", log.date)
        assertEquals(3, log.sleepQuality)
        assertEquals(3, log.stressLevel)
        assertEquals(1, log.doms)
        assertEquals(3, log.motivation)
        assertNull(log.manualNeuralBattery)
        assertNull(log.manualSpinalBattery)
        assertNull(log.manualMuscularBattery)
        assertEquals(emptyMap<String, Int>(), log.manualMuscleBatteries)
        assertEquals("sin Columna editada no se invierte nada", 0, inverter.calls)
    }

    @Test
    fun `la Columna editada se invierte una sola vez`() {
        val inverter = Inverter()
        val log = build(manualSpinal = 60, inverter = inverter)
        // Se ve 60 y se ancla el canal crudo, que es el inverso: 40. Ni 60 (sin invertir) ni 60 de nuevo (invertido dos veces).
        assertEquals(40, log.manualSpinalBattery)
        assertEquals(1, inverter.calls)
    }

    @Test
    fun `Energia editada se guarda tal cual, sin invertir`() {
        val inverter = Inverter()
        val log = build(manualNeural = 70, inverter = inverter)
        assertEquals(70, log.manualNeuralBattery)
        assertEquals(0, inverter.calls)
    }

    @Test
    fun `lo que no se toco conserva lo que hoy ya habia, sin volver a invertir la Columna`() {
        val inverter = Inverter()
        val today = DailyWellbeingLog(
            id = "hoy",
            date = "2026-10-16",
            sleepQuality = 4,
            stressLevel = 2,
            doms = 3,
            motivation = 5,
            sleepHours = 7.5,
            moodState = MoodState.entries.first(),
            workIntensity = IntensityLevel.entries.first(),
            manualMuscularBattery = 65,
            manualNeuralBattery = 70,
            manualSpinalBattery = 55,
            notes = "nota",
        )
        val log = build(today = today, discomforts = listOf("lumbar"), inverter = inverter)
        assertEquals("hoy", log.id)
        assertEquals(4, log.sleepQuality)
        assertEquals(2, log.stressLevel)
        assertEquals(3, log.doms)
        assertEquals(5, log.motivation)
        assertEquals(7.5, log.sleepHours)
        assertEquals(today.moodState, log.moodState)
        assertEquals(today.workIntensity, log.workIntensity)
        assertEquals("el global de hoy", 65, log.manualMuscularBattery)
        assertEquals("la Energía de hoy", 70, log.manualNeuralBattery)
        assertEquals("la Columna cruda de hoy, tal cual", 55, log.manualSpinalBattery)
        assertEquals("nota", log.notes)
        assertEquals(listOf("lumbar"), log.preWorkoutDiscomforts)
        assertEquals(0, inverter.calls)
    }

    @Test
    fun `lo editado manda sobre lo de hoy y el resto se queda`() {
        val today = DailyWellbeingLog(id = "hoy", date = "2026-10-16", manualNeuralBattery = 70, manualSpinalBattery = 55)
        val log = build(today = today, manualNeural = 40)
        assertEquals(40, log.manualNeuralBattery)
        assertEquals(55, log.manualSpinalBattery)
    }

    @Test
    fun `los musculos editados se suman a los de hoy y van por pilar`() {
        val today = DailyWellbeingLog(
            id = "hoy",
            date = "2026-10-16",
            manualMuscleBatteries = mapOf("Pectorales" to 80, "Dorsales" to 60),
        )
        val log = build(today = today, manualMuscles = mapOf("Pectorales" to 70, "Cuádriceps" to 50))
        assertEquals(mapOf("Pectorales" to 70, "Dorsales" to 60, "Cuádriceps" to 50), log.manualMuscleBatteries)
    }

    @Test
    fun `un musculo con nombre de pantalla se guarda por su pilar`() {
        val log = build(manualMuscles = mapOf("Pecho" to 70, "Hombros" to 65))
        assertEquals(mapOf("Pectorales" to 70, "Deltoides" to 65), log.manualMuscleBatteries)
    }

    @Test
    fun `sin musculos editados quedan los de hoy`() {
        val today = DailyWellbeingLog(id = "hoy", date = "2026-10-16", manualMuscleBatteries = mapOf("Pecho" to 80))
        val log = build(today = today)
        assertEquals(mapOf("Pectorales" to 80), log.manualMuscleBatteries)
    }

    @Test
    fun `el global de Musculos solo sale si alguien lo pide`() {
        val log = build(manualMuscular = 66)
        assertEquals(66, log.manualMuscularBattery)
        assertNull(build().manualMuscularBattery)
    }

    @Test
    fun `las molestias se guardan como llegan`() {
        val log = build(discomforts = listOf("knee_patellar", "lumbar"))
        assertEquals(listOf("knee_patellar", "lumbar"), log.preWorkoutDiscomforts)
    }
}
