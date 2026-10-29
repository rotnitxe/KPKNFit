package com.example.kpkn.screens.competitions

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.competitions.CompetitionMediaStore
import com.example.kpkn.data.models.CompetitionMovementType
import com.example.kpkn.data.models.CompetitionRecord
import com.example.kpkn.data.models.CompetitionRecordStatus
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.repository.CompetitionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.competitions.AttemptPlanner
import com.example.kpkn.domain.competitions.CompetitionDiscipline
import com.example.kpkn.domain.training.TrainingMaxMerge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Una fila de c-marks: el máximo de hoy y el que quedaría. */
data class CompetitionMarkChange(val movement: CompetitionMovementType, val before: Double?, val after: Double?)

data class CompetitionDetailUi(
    val ready: Boolean = false,
    val record: CompetitionRecord? = null,
    val discipline: CompetitionDiscipline = CompetitionDiscipline.POWERLIFTING,
    val records: List<CompetitionRecord> = emptyList(),
    val programs: List<Program> = emptyList(),
    val bodyweightKg: Double? = null,
    /** Se puede proponer «Actualizar tus marcas» (powerlifting completado y ligado a su programa). */
    val canUpdateMarks: Boolean = false,
    val linkedProgramName: String? = null,
)

/** Lo efímero de la ficha: la revisión de marcas abierta, un aviso y si la competencia se fue (archivada o eliminada). */
data class CompetitionDetailTransient(
    val marks: List<CompetitionMarkChange>? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val gone: Boolean = false,
)

class CompetitionDetailViewModel(
    private val competitionId: String,
    private val repository: CompetitionRepository = CompetitionRepository.getInstance(),
    private val programs: ProgramRepository = ProgramRepository.getInstance(),
) : ViewModel() {

    val ui: StateFlow<CompetitionDetailUi> = combine(repository.records, repository.isReady, programs.programs, programs.settings) { records, ready, programList, settings ->
        val record = records.firstOrNull { it.id == competitionId }
        val resolver = CompetitionPrograms.disciplineResolver(programList)
        val discipline = record?.let(resolver) ?: CompetitionDiscipline.POWERLIFTING
        val linked = record?.let { CompetitionPrograms.linkedProgram(it, programList) }
        CompetitionDetailUi(
            ready = ready,
            record = record,
            discipline = discipline,
            records = records,
            programs = programList,
            bodyweightKg = settings.userVitals.weight?.takeIf { it.isFinite() && it > 0 },
            canUpdateMarks = record != null && linked != null && record.status == CompetitionRecordStatus.COMPLETED &&
                discipline == CompetitionDiscipline.POWERLIFTING && programs.isCompetitionLinked(linked, record) && programs.supportsSbdCompetition(linked, record),
            linkedProgramName = linked?.name,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CompetitionDetailUi())

    private val _transient = MutableStateFlow(CompetitionDetailTransient())
    val transient: StateFlow<CompetitionDetailTransient> = _transient.asStateFlow()

    private var preview: ProgramRepository.MeetResultPreview? = null

    fun consumeMessage() = _transient.update { it.copy(message = null) }

    fun save(transform: (CompetitionRecord) -> CompetitionRecord) {
        val record = repository.getById(competitionId) ?: return
        repository.upsert(transform(record))
    }

    /** «Archivar»: sale del historial (estado ARCHIVED); no se borra. */
    fun archive() {
        save { it.copy(status = CompetitionRecordStatus.ARCHIVED) }
        _transient.update { it.copy(gone = true, message = "Competencia archivada") }
    }

    /** «Eliminar»: borra la competencia y sus fotos. */
    fun delete(context: Context) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            withContext(Dispatchers.IO) { CompetitionMediaStore.deleteAll(appContext, competitionId) }
            repository.delete(competitionId)
            _transient.update { it.copy(gone = true, message = "Competencia eliminada") }
        }
    }

    /** c-marks: la revisión de siempre (`previewMeetResult`), con cada levantamiento antes y después. */
    fun reviewMarks() {
        val state = ui.value
        val record = state.record ?: return
        val program = CompetitionPrograms.linkedProgram(record, state.programs) ?: return
        if (_transient.value.busy) return
        viewModelScope.launch {
            _transient.update { it.copy(busy = true) }
            try {
                val found = withContext(Dispatchers.IO) { programs.previewMeetResult(program.id, record.id) }
                preview = found
                if (found == null) {
                    _transient.update { it.copy(message = "No hay marcas superiores que pasar a tu programa.") }
                } else {
                    val percentBefore = TrainingMaxMerge.trainingMaxPercentOf(found.original)
                    val percentAfter = TrainingMaxMerge.trainingMaxPercentOf(found.result)
                    val changes = AttemptPlanner.lifts.map { movement ->
                        CompetitionMarkChange(
                            movement,
                            found.original.powerliftingProfile?.let { AttemptPlanner.expectedMaxOf(it, movement, percentBefore) },
                            found.result.powerliftingProfile?.let { AttemptPlanner.expectedMaxOf(it, movement, percentAfter) },
                        )
                    }
                    _transient.update { it.copy(marks = changes) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _transient.update { it.copy(message = error.message ?: "No se pudo preparar la revisión de marcas.") }
            } finally {
                _transient.update { it.copy(busy = false) }
            }
        }
    }

    fun dismissMarks() {
        preview = null
        _transient.update { it.copy(marks = null) }
    }

    fun confirmMarks() {
        val found = preview ?: return
        val record = ui.value.record ?: return
        val program = CompetitionPrograms.linkedProgram(record, ui.value.programs) ?: return
        viewModelScope.launch {
            _transient.update { it.copy(busy = true) }
            val applied = runCatching { programs.applyMeetResult(program.id, found) }.getOrDefault(false)
            preview = null
            _transient.update {
                it.copy(busy = false, marks = null,
                    message = if (applied) "Tus marcas ya están en «${program.name}»" else "La competencia o el programa cambiaron. Vuelve a revisarlo.")
            }
        }
    }
}
