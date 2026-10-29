package com.example.kpkn.screens.competitions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.models.CompetitionRecord
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.repository.CompetitionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.competitions.CompetitionDiscipline
import com.example.kpkn.domain.competitions.CompetitionHubModel
import com.example.kpkn.domain.competitions.CompetitionInsights
import com.example.kpkn.domain.multisport.SportId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/** La página «Competencias» (c-hub) y la fila de Perfil (C-D1). */
data class CompetitionsHubUi(
    val ready: Boolean = false,
    val model: CompetitionHubModel? = null,
    val records: List<CompetitionRecord> = emptyList(),
    val programs: List<Program> = emptyList(),
    val sports: Set<SportId> = emptySet(),
    val bodyweightKg: Double? = null,
    val today: LocalDate = LocalDate.now(),
) {
    fun disciplineOf(record: CompetitionRecord): CompetitionDiscipline = CompetitionPrograms.disciplineResolver(programs)(record)
}

class CompetitionsViewModel(
    repository: CompetitionRepository = CompetitionRepository.getInstance(),
    programRepository: ProgramRepository = ProgramRepository.getInstance(),
) : ViewModel() {
    val ui: StateFlow<CompetitionsHubUi> = combine(
        repository.records,
        repository.isReady,
        programRepository.programs,
        programRepository.settings,
    ) { records, ready, programs, settings ->
        val today = LocalDate.now()
        val resolver = CompetitionPrograms.disciplineResolver(programs)
        CompetitionsHubUi(
            ready = ready,
            model = CompetitionInsights.hub(records, today, resolver),
            records = records,
            programs = programs,
            sports = CompetitionPrograms.sportsOf(programs),
            bodyweightKg = settings.userVitals.weight?.takeIf { it.isFinite() && it > 0 },
            today = today,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CompetitionsHubUi())
}
