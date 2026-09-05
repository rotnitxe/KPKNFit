package com.example.kpkn.domain.training

import com.example.kpkn.data.models.CompetitionRecord
import com.example.kpkn.data.models.CompetitionRecordStatus
import com.example.kpkn.data.models.KeyDateType
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.isSimpleTemporalProgram
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.Locale

enum class CompetitionHomePhase {
    UPCOMING,
    REGISTER,
}

data class HomeCompetitionState(
    val recordId: String,
    val programId: String,
    val programName: String,
    val title: String,
    val competitionDate: String,
    val competitionDateLabel: String,
    val daysUntil: Long,
    val countdownLabel: String,
    val competitionWeekLabel: String?,
    val phase: CompetitionHomePhase,
)

object HomeCompetitionResolver {

    fun resolve(
        program: Program?,
        records: List<CompetitionRecord>,
        today: LocalDate = LocalDate.now(),
    ): HomeCompetitionState? {
        if (program == null || program.isSimpleTemporalProgram) return null
        val keyDate = program.keyDates.firstOrNull { it.type == KeyDateType.COMPETITION } ?: return null
        val competitionDate = parseDate(keyDate.eventDate ?: keyDate.startDate) ?: return null
        val matching = records.firstOrNull { record ->
            record.keyDateId == keyDate.id &&
                (record.plannedProgramId == null || record.plannedProgramId == program.id)
        } ?: records.firstOrNull { record ->
            record.plannedProgramId == program.id && record.eventDate == competitionDate.toString()
        }
        if (matching != null && matching.status != CompetitionRecordStatus.PLANNED) return null

        val daysUntil = ChronoUnit.DAYS.between(today, competitionDate)
        val weekStart = parseDate(keyDate.startDate)
        val weekEnd = parseDate(keyDate.endDate)
        return HomeCompetitionState(
            recordId = matching?.id.orEmpty(),
            programId = program.id,
            programName = program.name,
            title = matching?.title?.takeIf { it.isNotBlank() } ?: keyDate.title.ifBlank { "Competición" },
            competitionDate = competitionDate.toString(),
            competitionDateLabel = formatHomeDate(competitionDate),
            daysUntil = daysUntil,
            countdownLabel = formatCountdown(daysUntil),
            competitionWeekLabel = if (weekStart != null && weekEnd != null) {
                "${formatHomeDate(weekStart)} → ${formatHomeDate(weekEnd)}"
            } else {
                null
            },
            phase = if (daysUntil > 0) CompetitionHomePhase.UPCOMING else CompetitionHomePhase.REGISTER,
        )
    }

    private fun parseDate(raw: String?): LocalDate? {
        if (raw.isNullOrBlank()) return null
        return try {
            LocalDate.parse(raw.trim())
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun formatHomeDate(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es-CL")))

    private fun formatCountdown(daysUntil: Long): String = when {
        daysUntil > 1L -> "$daysUntil días"
        daysUntil == 1L -> "Mañana"
        daysUntil == 0L -> "Hoy"
        daysUntil == -1L -> "Ayer"
        else -> "Hace ${-daysUntil} días"
    }
}
