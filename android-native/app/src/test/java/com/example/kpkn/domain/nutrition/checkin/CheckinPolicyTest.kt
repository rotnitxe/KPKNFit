package com.example.kpkn.domain.nutrition.checkin

import java.time.*
import org.junit.Assert.*
import org.junit.Test

class CheckinPolicyTest {
    private val monday = LocalDate.of(2026, 10, 19)

    @Test fun `cada dia elegido abre una ventana de tres dias de calendario`() {
        for (day in DayOfWeek.values()) {
            val start = monday.plusDays((day.value - 1).toLong())
            for (offset in 0L..6L) {
                assertEquals("$day + $offset", if (offset < 3) CheckinAvailability.NOW else CheckinAvailability.FUTURE,
                    CheckinPolicy.state(start.plusDays(offset), DayOfWeek.MONDAY, null, checkinDay = day))
            }
        }
    }

    @Test fun `sin plan no hay checkin aun si quedan decisiones anteriores`() {
        assertEquals(CheckinAvailability.NONE, CheckinPolicy.state(monday, DayOfWeek.MONDAY,
            CheckinRecord(monday, CheckinDecision.PENDING), hasActivePlan = false))
    }

    @Test fun `aplicado declinado o descartado no se ofrecen de nuevo esta semana`() {
        for (status in listOf(CheckinDecision.APPLIED, CheckinDecision.DECLINED, CheckinDecision.DISMISSED)) {
            val record = CheckinRecord(monday, status)
            assertEquals(CheckinAvailability.DISMISSED, CheckinPolicy.state(monday.plusDays(1), DayOfWeek.MONDAY, record))
            assertEquals(CheckinAvailability.NOW, CheckinPolicy.state(monday.plusWeeks(1), DayOfWeek.MONDAY, record))
        }
    }

    @Test fun `posponer espera a manana incluso en el ultimo dia de la ventana`() {
        val record = CheckinRecord(monday, CheckinDecision.POSTPONED, monday.plusDays(3))
        assertEquals(CheckinAvailability.FUTURE, CheckinPolicy.state(monday.plusDays(2), DayOfWeek.MONDAY, record))
        assertEquals(CheckinAvailability.NOW, CheckinPolicy.state(monday.plusDays(3), DayOfWeek.MONDAY, record))
        assertEquals(CheckinAvailability.FUTURE, CheckinPolicy.state(monday.plusDays(4), DayOfWeek.MONDAY, record))
    }

    @Test fun `la ventana del domingo conserva su clave al cruzar el lunes`() {
        val sunday = monday.plusDays(6)
        val key = CheckinPolicy.weekKey(sunday, DayOfWeek.MONDAY, DayOfWeek.SUNDAY)
        assertEquals(key, CheckinPolicy.weekKey(sunday.plusDays(2), DayOfWeek.MONDAY, DayOfWeek.SUNDAY))
        assertEquals(CheckinAvailability.DISMISSED, CheckinPolicy.state(sunday.plusDays(1), DayOfWeek.MONDAY,
            CheckinRecord(key, CheckinDecision.APPLIED), checkinDay = DayOfWeek.SUNDAY))
    }

    @Test fun `una decision de la semana anterior no oculta que viene la proxima revision`() {
        val priorKey = CheckinPolicy.weekKey(monday.plusDays(1), DayOfWeek.MONDAY, DayOfWeek.TUESDAY)
        assertEquals(CheckinAvailability.FUTURE, CheckinPolicy.state(monday.plusWeeks(1), DayOfWeek.MONDAY,
            CheckinRecord(priorKey, CheckinDecision.APPLIED), checkinDay = DayOfWeek.TUESDAY))
    }

    @Test fun `posponer el ultimo dia de una ventana que cruza semana sigue disponible manana`() {
        val sunday = monday.plusDays(6)
        val record = CheckinRecord(monday, CheckinDecision.POSTPONED, sunday.plusDays(3))
        assertEquals(CheckinAvailability.NOW, CheckinPolicy.state(sunday.plusDays(3), DayOfWeek.MONDAY, record, checkinDay = DayOfWeek.SUNDAY))
    }

    @Test fun `cambiar de zona conserva la decision de la semana local`() {
        val instant = Instant.parse("2026-10-20T10:30:00Z")
        val dates = listOf("Pacific/Honolulu", "Pacific/Kiritimati", "America/Santiago").map { instant.atZone(ZoneId.of(it)).toLocalDate() }
        val applied = CheckinRecord(monday, CheckinDecision.APPLIED)
        dates.forEach { assertEquals(CheckinAvailability.DISMISSED, CheckinPolicy.state(it, DayOfWeek.MONDAY, applied)) }
    }
}
