package com.example.kpkn.screens.body

import com.example.kpkn.data.models.BodyMetric
import com.example.kpkn.data.models.CalculationOrigin
import com.example.kpkn.data.models.GoalMetric
import com.example.kpkn.data.models.MeasurementSchedule
import com.example.kpkn.screens.body.design.BodyGoalDraft
import com.example.kpkn.screens.body.design.bodyBmiCategory
import com.example.kpkn.screens.body.design.bodyClockText
import com.example.kpkn.screens.body.design.bodyFatTip
import com.example.kpkn.screens.body.design.bodyFfmiCategory
import com.example.kpkn.screens.body.design.bodyFfmiTip
import com.example.kpkn.screens.body.design.bodyManualGoalFor
import com.example.kpkn.screens.body.design.bodyReminderNextLabel
import com.example.kpkn.screens.body.design.bodyReminderPresetLabel
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reglas de las hojas y módulos de Cuerpo que no necesitan pintar nada: objetivo manual, categorías, textos y recordatorio. */
class BodyDesignRulesTest {

    private val ahora = 1_800_000_000_000L

    @Test
    fun sinMetaNoSeGuardaUnObjetivo() {
        assertNull(bodyManualGoalFor(BodyGoalDraft(GoalMetric.WEIGHT), ahora))
        assertNull(bodyManualGoalFor(BodyGoalDraft(GoalMetric.WEIGHT, target = 0.0), ahora))
        assertNull(bodyManualGoalFor(BodyGoalDraft(GoalMetric.BODY_FAT, target = Double.NaN), ahora))
    }

    @Test
    fun unObjetivoNuevoEsManualSinPlanConFechasDeAhora() {
        val objetivo = bodyManualGoalFor(BodyGoalDraft(GoalMetric.WEIGHT, target = 60.0), ahora)!!
        assertEquals(CalculationOrigin.MANUAL, objetivo.origin)
        assertNull(objetivo.linkedPlanId)
        assertEquals(ahora, objetivo.createdAtEpochMs)
        assertEquals(ahora, objetivo.updatedAtEpochMs)
        assertEquals("kg", objetivo.unitSi)
        assertTrue(objetivo.id.isNotBlank())
    }

    @Test
    fun editarLaMismaMetricaConservaIdYFechaDeInicio() {
        val draft = BodyGoalDraft(GoalMetric.WEIGHT, target = 58.0, existingId = "propio-1", createdAtEpochMs = 1_700_000_000_000L)
        val objetivo = bodyManualGoalFor(draft, ahora)!!
        assertEquals("propio-1", objetivo.id)
        assertEquals(1_700_000_000_000L, objetivo.createdAtEpochMs)
        assertEquals(ahora, objetivo.updatedAtEpochMs)
        assertEquals(58.0, objetivo.targetValueSi, 1e-9)
    }

    @Test
    fun cambiarDeMetricaEmpiezaSinMetaNiFechaPeroConservaElId() {
        val draft = BodyGoalDraft(GoalMetric.WEIGHT, target = 60.0, existingId = "propio-1", createdAtEpochMs = 1_700_000_000_000L)
        val cambiado = draft.withMetric(GoalMetric.BODY_FAT)
        assertNull(cambiado.target)
        assertNull(cambiado.createdAtEpochMs)
        assertEquals("propio-1", cambiado.existingId)
        assertEquals(draft, draft.withMetric(GoalMetric.WEIGHT))
    }

    @Test
    fun porcentajesSeGuardanEnPorcentajeYConLaMetricaDeMedida() {
        val grasa = bodyManualGoalFor(BodyGoalDraft(GoalMetric.BODY_FAT, target = 15.0), ahora)!!
        assertEquals(BodyMetric.BODY_FAT_PERCENT, grasa.metric)
        assertEquals("%", grasa.unitSi)
        val musculo = bodyManualGoalFor(BodyGoalDraft(GoalMetric.MUSCLE_MASS, target = 44.0), ahora)!!
        assertEquals(BodyMetric.MUSCLE_MASS_PERCENT, musculo.metric)
    }

    @Test
    fun categoriaDeFfmiConLosLimitesQueYaUsabaLaPantalla() {
        assertEquals("Bajo", bodyFfmiCategory(17.95))
        assertEquals("Promedio", bodyFfmiCategory(18.0))
        assertEquals("Promedio", bodyFfmiCategory(19.95))
        assertEquals("Avanzado", bodyFfmiCategory(20.0))
        assertEquals("Avanzado", bodyFfmiCategory(22.45))
        assertEquals("Élite", bodyFfmiCategory(22.5))
        assertEquals("Élite", bodyFfmiCategory(24.95))
        assertEquals("Excepcional", bodyFfmiCategory(25.0))
    }

    @Test
    fun categoriaDeImcSaleDelValorRedondeadoACuatroCategorias() {
        assertEquals("Bajo peso", bodyBmiCategory(18.44))
        assertEquals("Normal", bodyBmiCategory(18.45))
        assertEquals("Normal", bodyBmiCategory(24.94))
        assertEquals("Sobrepeso", bodyBmiCategory(24.95))
        assertEquals("Sobrepeso", bodyBmiCategory(29.9))
        // 29,95 se muestra como 30,0: se clasifica como lo que se ve.
        assertEquals("Obesidad", bodyBmiCategory(29.95))
    }

    @Test
    fun losConsejosYaExistentesConservanSusRangos() {
        assertTrue(bodyFatTip(9.0)!!.contains("muy bajo"))
        assertEquals("Excelente nivel de grasa corporal para rendimiento y estética.", bodyFatTip(12.0))
        assertEquals("Buen rango de grasa corporal. Ideal para fases de volumen controlado.", bodyFatTip(17.0))
        assertNull(bodyFatTip(22.0))
        assertTrue(bodyFatTip(27.0)!!.contains("déficit"))
        assertNull(bodyFatTip(null))
        assertTrue(bodyFfmiTip(17.0)!!.contains("potencial de crecimiento"))
        assertEquals("Muy buen desarrollo muscular. Estás en rango avanzado.", bodyFfmiTip(21.0))
        assertTrue(bodyFfmiTip(26.0)!!.contains("excepcional"))
        assertNull(bodyFfmiTip(19.0))
    }

    @Test
    fun etiquetasDeLasPildorasDeFrecuencia() {
        assertEquals("Cada día", bodyReminderPresetLabel(1))
        assertEquals("7 días", bodyReminderPresetLabel(7))
        assertEquals("30 días", bodyReminderPresetLabel(30))
    }

    @Test
    fun laProximaMedicionSeEscribeConDiaYHora() {
        // «a las» y la hora van unidos con espacios de no separación ( ): la hora no queda sola al partir la línea.
        assertEquals("martes 20 de octubre a las 09:00", bodyReminderNextLabel("2026-10-20", 9, 0))
        assertEquals("martes 20 de octubre a las 07:30", bodyReminderNextLabel("2026-10-20", 7, 30))
        assertNull(bodyReminderNextLabel(null, 9, 0))
        assertNull(bodyReminderNextLabel("no es una fecha", 9, 0))
    }

    @Test
    fun laHoraSiempreTieneDosCifras() {
        assertEquals("09:05", bodyClockText(9, 5))
        assertEquals("23:59", bodyClockText(23, 59))
    }

    @Test
    fun unHorarioGuardadoAntesDeEditarLaHoraSigueLeyendose() {
        val json = Json
        val antiguo = json.decodeFromString<MeasurementSchedule>("""{"enabled":true,"intervalDays":7,"nextDate":"2026-10-20"}""")
        assertEquals(9, antiguo.reminderHour)
        assertEquals(0, antiguo.reminderMinute)
        val editado = antiguo.copy(reminderHour = 7, reminderMinute = 30)
        assertEquals(editado, json.decodeFromString<MeasurementSchedule>(json.encodeToString(MeasurementSchedule.serializer(), editado)))
    }
}
