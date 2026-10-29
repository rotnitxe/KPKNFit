package com.example.kpkn.screens.competitions

import com.example.kpkn.data.models.CompetitionMovementType
import com.example.kpkn.data.models.CompetitionRecord
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramMode
import com.example.kpkn.domain.competitions.AttemptPlanner
import com.example.kpkn.domain.competitions.CompetitionDiscipline
import com.example.kpkn.domain.multisport.SportId
import com.example.kpkn.domain.training.TrainingMaxMerge

/** Lo que las pantallas de competencias leen de tus programas: la disciplina de cada registro, tus deportes y tus marcas. */
internal object CompetitionPrograms {
    /** El programa al que pertenece una competencia (la fecha clave que la creó o el programa elegido). */
    fun linkedProgram(record: CompetitionRecord, programs: List<Program>): Program? =
        record.plannedProgramId?.let { id -> programs.firstOrNull { it.id == id } }
            ?: record.keyDateId?.let { key -> programs.firstOrNull { p -> p.keyDates.any { it.id == key } } }

    fun disciplineResolver(programs: List<Program>): (CompetitionRecord) -> CompetitionDiscipline = { record ->
        CompetitionDiscipline.of(record, linkedProgram(record, programs)?.mode == ProgramMode.POWERLIFTING)
    }

    /** Los deportes de tus programas, para poner arriba sus disciplinas en «Nueva competencia». */
    fun sportsOf(programs: List<Program>): Set<SportId> = programs.flatMap { program ->
        program.trainingIntent?.goals?.map { it.sport }.orEmpty() +
            listOfNotNull(SportId.POWERLIFTING.takeIf { program.mode == ProgramMode.POWERLIFTING })
    }.toSet()

    /** Tus marcas de sentadilla, banca y peso muerto: las del programa de la competencia o, si no tiene, las del primero que las guarde. */
    fun marks(record: CompetitionRecord?, programs: List<Program>): Map<CompetitionMovementType, Double> {
        val linked = record?.let { linkedProgram(it, programs) }?.takeIf { it.powerliftingProfile != null }
        val program = linked ?: programs.firstOrNull { it.powerliftingProfile != null && it.mode == ProgramMode.POWERLIFTING }
            ?: programs.firstOrNull { it.powerliftingProfile != null } ?: return emptyMap()
        val profile = program.powerliftingProfile ?: return emptyMap()
        val percent = TrainingMaxMerge.trainingMaxPercentOf(program)
        return AttemptPlanner.lifts.mapNotNull { movement -> AttemptPlanner.expectedMaxOf(profile, movement, percent)?.let { movement to it } }.toMap()
    }
}
