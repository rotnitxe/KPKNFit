package com.example.kpkn.domain.training.schedule

import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.domain.training.TrainingWindow
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class InactivityNotice(val daysSinceLastLog: Int,val level: Int,val message: String)

object InactivityDetector {
    fun detect(logs: List<WorkoutLog>,programId: String,today: LocalDate,zone: ZoneId): InactivityNotice? {
        val last = logs.filter { it.programId == programId }.mapNotNull {
            TrainingWindow.calendarDate(it.actualDate ?: it.date,zone)?.takeUnless { date -> date > today }
        }.maxOrNull() ?: return null
        val days = ChronoUnit.DAYS.between(last,today).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        if (days < 7) return null
        val level = if (days >= 21) 2 else 1
        return InactivityNotice(days,level,
            if (level == 2) "Llevas $days días sin entrenar: te recomendamos repetir la semana con una vuelta suave antes de seguir."
            else "Último registro de este programa hace $days días. Esto no indica si entrenaste fuera de KPKN. Puedes conservar la semana o revisar una vuelta suave.")
    }
}
