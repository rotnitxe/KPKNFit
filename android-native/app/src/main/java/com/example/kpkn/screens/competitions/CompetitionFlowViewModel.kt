package com.example.kpkn.screens.competitions

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.competitions.CompetitionMediaStore
import com.example.kpkn.data.exercises.approvedExerciseCatalogV2
import com.example.kpkn.data.exercises.catalogExerciseIndex
import com.example.kpkn.data.exercises.exerciseCatalogSnapshot
import com.example.kpkn.data.models.CompetitionAttemptResult
import com.example.kpkn.data.models.CompetitionBout
import com.example.kpkn.data.models.CompetitionMovementType
import com.example.kpkn.data.models.CompetitionPhoto
import com.example.kpkn.data.models.CompetitionRecord
import com.example.kpkn.data.models.CompetitionRecordStatus
import com.example.kpkn.data.models.CompetitionTechnicalBlock
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.Gender
import com.example.kpkn.data.models.PlannedAttempt
import com.example.kpkn.data.models.PowerliftingCompetitionDetails
import com.example.kpkn.data.repository.CompetitionRepository
import com.example.kpkn.data.repository.CustomExerciseRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.competitions.AttemptPlanner
import com.example.kpkn.domain.competitions.AttemptStrategy
import com.example.kpkn.domain.competitions.CompetitionAttempts
import com.example.kpkn.domain.competitions.CompetitionDiscipline
import com.example.kpkn.domain.competitions.CompetitionDraft
import com.example.kpkn.domain.competitions.CompetitionExerciseSuggestion
import com.example.kpkn.domain.competitions.CompetitionExerciseTypeahead
import com.example.kpkn.domain.competitions.CompetitionFormat
import com.example.kpkn.domain.competitions.CompetitionPlaceHonors
import com.example.kpkn.domain.competitions.CompetitionResultForm
import com.example.kpkn.domain.competitions.CompetitionScoring
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID

/** Los recorridos de Registros v4: «Voy a competir», «Ya competí», el plan, el modo competición, registrar y editar. */
enum class CompetitionFlowMode(val id: String) {
    NEXT("next"), DONE("done"), PLAN("plan"), TODAY("today"), LOG("log"), EDIT("edit");

    companion object {
        fun of(id: String?): CompetitionFlowMode = entries.firstOrNull { it.id == id } ?: NEXT
    }
}

/** c-next (o el paso de datos de «Ya competí»), c-plan, c-today, el resultado de su forma, c-place, c-feel y c-cele. */
enum class CompetitionFlowStep { DATA, PLAN, TODAY, RESULT, PLACE, FEEL, CELE }

data class CompetitionFlowUi(
    val ready: Boolean = false,
    val record: CompetitionRecord = CompetitionRecord(id = "", title = ""),
    val discipline: CompetitionDiscipline = CompetitionDiscipline.POWERLIFTING,
    val mode: CompetitionFlowMode = CompetitionFlowMode.NEXT,
    val step: CompetitionFlowStep = CompetitionFlowStep.DATA,
    val records: List<CompetitionRecord> = emptyList(),
    val marks: Map<CompetitionMovementType, Double> = emptyMap(),
    val currentBodyweightKg: Double? = null,
    val weeklyRateKg: Double? = null,
    /** Hay intentos cambiados a mano y se pidió otra estrategia: se pregunta antes de recalcularlos. */
    val pendingStrategy: AttemptStrategy? = null,
    val message: String? = null,
    /** El recorrido terminó: la pantalla abre la ficha de [finishedId] (o vuelve atrás si [finishedBack]). */
    val finishedId: String? = null,
    val finishedBack: Boolean = false,
)

class CompetitionFlowViewModel(
    private val competitionId: String,
    private val mode: CompetitionFlowMode,
    private val disciplineId: String?,
    private val repository: CompetitionRepository = CompetitionRepository.getInstance(),
    private val programs: ProgramRepository = ProgramRepository.getInstance(),
) : ViewModel() {

    private val _ui = MutableStateFlow(CompetitionFlowUi(mode = mode))
    val ui: StateFlow<CompetitionFlowUi> = _ui.asStateFlow()

    private val isNew = competitionId.isBlank() || competitionId == NEW_ID

    init {
        viewModelScope.launch {
            repository.isReady.first { it }
            val programList = programs.programs.value
            val settings = runCatching { programs.settings.value }.getOrNull()
            val sex = if (settings?.userVitals?.gender == Gender.FEMALE) "female" else "male"
            val weight = settings?.userVitals?.weight?.takeIf { it.isFinite() && it > 0 }
            val resolver = CompetitionPrograms.disciplineResolver(programList)
            val existing = if (isNew) null else repository.getById(competitionId)
            val discipline = existing?.let(resolver) ?: CompetitionDiscipline.byId(disciplineId) ?: CompetitionDiscipline.POWERLIFTING
            val base = existing?.let { CompetitionDraft.ensureBlocks(it, discipline) }
                ?: CompetitionDraft.createEmpty(discipline, sexCategory = sex, bodyweightKg = weight.takeIf { mode != CompetitionFlowMode.DONE })
            val withSex = if (discipline == CompetitionDiscipline.POWERLIFTING && base.powerliftingDetails?.sexCategory.isNullOrBlank()) {
                base.copy(powerliftingDetails = (base.powerliftingDetails ?: PowerliftingCompetitionDetails()).copy(sexCategory = sex))
            } else base
            val marks = CompetitionPrograms.marks(existing, programList)
            val rate = CompetitionPrograms.linkedProgram(withSex, programList)?.let { program ->
                runCatching { withContext(Dispatchers.IO) { programs.seasonInput(program.id).weeklyWeightRateKg } }.getOrNull()
            }
            val prepared = when (mode) {
                CompetitionFlowMode.TODAY, CompetitionFlowMode.LOG -> CompetitionAttempts.prefill(withSex, discipline, marks)
                CompetitionFlowMode.PLAN -> withPlan(withSex, marks)
                else -> withSex
            }
            _ui.value = CompetitionFlowUi(
                ready = true,
                record = prepared,
                discipline = discipline,
                mode = mode,
                step = firstStep(),
                records = repository.records.value,
                marks = marks,
                currentBodyweightKg = weight,
                weeklyRateKg = rate,
            )
        }
    }

    private fun firstStep(): CompetitionFlowStep = when (mode) {
        CompetitionFlowMode.NEXT, CompetitionFlowMode.DONE -> CompetitionFlowStep.DATA
        CompetitionFlowMode.PLAN -> CompetitionFlowStep.PLAN
        CompetitionFlowMode.TODAY -> CompetitionFlowStep.TODAY
        CompetitionFlowMode.LOG, CompetitionFlowMode.EDIT -> CompetitionFlowStep.RESULT
    }

    /** Los pasos del recorrido, en orden (sin la celebración). */
    fun steps(state: CompetitionFlowUi = _ui.value): List<CompetitionFlowStep> {
        val withPlace = state.discipline.form != CompetitionResultForm.STAGE && state.discipline != CompetitionDiscipline.CLIMBING
        val resultTail = listOfNotNull(CompetitionFlowStep.PLACE.takeIf { withPlace }, CompetitionFlowStep.FEEL)
        return when (mode) {
            CompetitionFlowMode.NEXT -> listOfNotNull(CompetitionFlowStep.DATA, CompetitionFlowStep.PLAN.takeIf { state.discipline == CompetitionDiscipline.POWERLIFTING })
            CompetitionFlowMode.PLAN -> listOf(CompetitionFlowStep.PLAN)
            CompetitionFlowMode.DONE -> listOf(CompetitionFlowStep.DATA, CompetitionFlowStep.RESULT) + resultTail
            CompetitionFlowMode.LOG, CompetitionFlowMode.EDIT -> listOf(CompetitionFlowStep.RESULT) + resultTail
            CompetitionFlowMode.TODAY -> listOf(CompetitionFlowStep.TODAY) + resultTail
        }
    }

    fun update(transform: (CompetitionRecord) -> CompetitionRecord) = _ui.update { it.copy(record = transform(it.record)) }

    fun consumeMessage() = _ui.update { it.copy(message = null) }

    // ─── Navegación entre pasos ───────────────────────────────────────────────────────────────────────────────

    /** Atrás: el paso anterior; false si ya es el primero (la pantalla cierra). */
    fun back(): Boolean {
        val state = _ui.value
        if (state.step == CompetitionFlowStep.CELE) return false
        val list = steps(state)
        val index = list.indexOf(state.step)
        if (index <= 0) return false
        _ui.update { it.copy(step = list[index - 1]) }
        return true
    }

    /** Continuar: valida el paso, guarda donde toca y pasa al siguiente. */
    fun next() {
        val state = _ui.value
        when (state.step) {
            CompetitionFlowStep.DATA -> {
                if (state.record.eventDate.isNullOrBlank()) { message("Elige la fecha de la competencia."); return }
                if (mode == CompetitionFlowMode.NEXT && state.discipline != CompetitionDiscipline.POWERLIFTING) { savePlanned(state.record); return }
                if (mode == CompetitionFlowMode.NEXT) {
                    _ui.update { it.copy(record = withPlan(it.record, it.marks), step = CompetitionFlowStep.PLAN) }
                    return
                }
                _ui.update { it.copy(record = CompetitionAttempts.prefill(it.record, it.discipline, it.marks), step = CompetitionFlowStep.RESULT) }
            }
            CompetitionFlowStep.PLAN -> savePlan()
            CompetitionFlowStep.TODAY -> advanceFrom(state)
            CompetitionFlowStep.RESULT, CompetitionFlowStep.PLACE -> advanceFrom(state)
            CompetitionFlowStep.FEEL -> saveCompleted()
            CompetitionFlowStep.CELE -> Unit
        }
    }

    private fun advanceFrom(state: CompetitionFlowUi) {
        val list = steps(state)
        val nextStep = list.getOrNull(list.indexOf(state.step) + 1) ?: return
        _ui.update { it.copy(step = nextStep) }
    }

    private fun message(text: String) = _ui.update { it.copy(message = text) }

    // ─── c-next: datos ────────────────────────────────────────────────────────────────────────────────────────

    fun setTitle(value: String) = update { it.copy(title = value) }
    fun setDate(date: LocalDate) = update { it.copy(eventDate = date.toString()) }
    fun setStartTime(hour: Int, minute: Int) = update { it.copy(startTime = "%02d:%02d".format(hour, minute)) }
    fun setLocation(value: String) = update { it.copy(location = value) }
    fun setFederation(id: String?, customName: String? = null) = update { CompetitionDraft.applyFederation(it, id, customName) }
    fun setWeightClass(value: String) = update { record ->
        if (_ui.value.discipline == CompetitionDiscipline.POWERLIFTING) {
            record.copy(powerliftingDetails = (record.powerliftingDetails ?: PowerliftingCompetitionDetails()).copy(weightClass = value))
        } else record.copy(targetWeightClass = value)
    }
    fun setDivision(value: String) = update { it.copy(powerliftingDetails = (it.powerliftingDetails ?: PowerliftingCompetitionDetails()).copy(division = value)) }
    fun setEquipment(value: com.example.kpkn.data.models.CompetitionEquipment) =
        update { it.copy(powerliftingDetails = (it.powerliftingDetails ?: PowerliftingCompetitionDetails()).copy(equipment = value)) }
    fun setCategory(value: String) = update { it.copy(category = value.ifBlank { null }) }
    fun setPartner(value: String) = update { it.copy(partner = value.ifBlank { null }) }
    fun setBodyweight(raw: String) = update { it.copy(bodyweightKg = CompetitionFormat.parseKg(raw)?.takeIf { kg -> kg in 20.0..400.0 }) }
    fun setDistance(km: Double?) = update { it.copy(distanceKm = km?.takeIf { d -> d > 0 }) }
    fun setTargetTime(seconds: Double?) = update { it.copy(targetTimeSeconds = seconds?.takeIf { s -> s > 0 }) }
    fun setStageWeight(raw: String) = update {
        it.copy(bodybuildingDetails = (it.bodybuildingDetails ?: com.example.kpkn.data.models.BodybuildingCompetitionDetails())
            .copy(stageWeightKg = CompetitionFormat.parseKg(raw)?.takeIf { kg -> kg in 20.0..400.0 }))
    }
    fun setReminders(week: Boolean? = null, h48: Boolean? = null, start: Boolean? = null) = update {
        it.copy(
            reminderOneWeekEnabled = week ?: it.reminderOneWeekEnabled,
            reminder48hEnabled = h48 ?: it.reminder48hEnabled,
            reminderStartEnabled = start ?: it.reminderStartEnabled,
        )
    }

    private fun savePlanned(record: CompetitionRecord) {
        val discipline = _ui.value.discipline
        val saved = record.copy(title = CompetitionDraft.derivedTitle(record, discipline), status = CompetitionRecordStatus.PLANNED)
        repository.upsert(saved)
        _ui.update { it.copy(record = saved, finishedId = saved.id) }
    }

    // ─── c-plan ───────────────────────────────────────────────────────────────────────────────────────────────

    fun strategy(record: CompetitionRecord = _ui.value.record): AttemptStrategy =
        AttemptStrategy.entries.firstOrNull { it.name == record.attemptStrategy } ?: AttemptStrategy.TARGET

    /** Rellena los nueve intentos que falten con la estrategia sobre tus marcas; lo escrito se respeta. */
    private fun withPlan(record: CompetitionRecord, marks: Map<CompetitionMovementType, Double>): CompetitionRecord {
        val strategy = strategy(record)
        val planned = AttemptPlanner.lifts.flatMap { movement ->
            val own = record.plannedAttempts.filter { it.movementType == movement }
            val proposal = marks[movement]?.let { AttemptPlanner.plan(movement, it, strategy) }.orEmpty()
            (1..3).mapNotNull { n -> own.firstOrNull { it.attemptNumber == n } ?: proposal.firstOrNull { it.attemptNumber == n } }
        }
        return record.copy(plannedAttempts = planned, attemptStrategy = strategy.name)
    }

    /** Cambiar de estrategia recalcula los intentos que no tocaste; si hay a mano, pregunta antes. */
    fun chooseStrategy(strategy: AttemptStrategy) {
        val state = _ui.value
        if (state.record.plannedAttempts.any { it.manual }) _ui.update { it.copy(pendingStrategy = strategy) }
        else applyStrategy(strategy, keepManual = true)
    }

    fun applyStrategy(strategy: AttemptStrategy, keepManual: Boolean) = _ui.update { state ->
        val record = state.record
        val planned = AttemptPlanner.lifts.flatMap { movement ->
            val proposal = state.marks[movement]?.let { AttemptPlanner.plan(movement, it, strategy) }.orEmpty()
            (1..3).mapNotNull { n ->
                val own = record.plannedAttempts.firstOrNull { it.movementType == movement && it.attemptNumber == n }
                if (own != null && own.manual && keepManual) own else proposal.firstOrNull { it.attemptNumber == n } ?: own
            }
        }
        state.copy(record = record.copy(plannedAttempts = planned, attemptStrategy = strategy.name), pendingStrategy = null)
    }

    fun dismissStrategy() = _ui.update { it.copy(pendingStrategy = null) }

    /** Un intento escrito a mano en el plan: queda marcado y la estrategia ya no lo pisa. */
    fun setPlannedWeight(movement: CompetitionMovementType, attemptNumber: Int, kg: Double?) = update { record ->
        val others = record.plannedAttempts.filterNot { it.movementType == movement && it.attemptNumber == attemptNumber }
        val added = kg?.takeIf { it.isFinite() && it > 0 }?.let { PlannedAttempt(movement, attemptNumber, it, basis = "manual", manual = true) }
        record.copy(plannedAttempts = (others + listOfNotNull(added)).sortedWith(compareBy({ AttemptPlanner.lifts.indexOf(it.movementType) }, { it.attemptNumber })))
    }

    private fun savePlan() {
        val state = _ui.value
        val planned = state.record.plannedAttempts
        val valid = AttemptPlanner.lifts.all { movement ->
            val values = planned.filter { it.movementType == movement }.sortedBy { it.attemptNumber }
            values.map { it.attemptNumber } == listOf(1, 2, 3) && values.zipWithNext().all { it.first.weightKg <= it.second.weightKg }
        }
        if (!valid) { message("Completa los nueve intentos, de menor a mayor en cada levantamiento."); return }
        val record = state.record
        val weightClass = record.powerliftingDetails?.weightClass
        val saved = record.copy(
            title = CompetitionDraft.derivedTitle(record, state.discipline),
            status = if (record.status == CompetitionRecordStatus.COMPLETED) record.status else CompetitionRecordStatus.PLANNED,
            targetWeightClass = weightClass ?: record.targetWeightClass,
            targetBodyweightKg = record.targetBodyweightKg ?: CompetitionFormat.weightClassLimit(weightClass),
        )
        repository.upsert(saved)
        _ui.update { it.copy(record = saved, finishedId = saved.id, finishedBack = mode == CompetitionFlowMode.PLAN) }
    }

    // ─── c-today y c-log: la tabla ────────────────────────────────────────────────────────────────────────────

    /** c-log: un toque alterna válido ↔ nulo. */
    fun toggle(block: CompetitionTechnicalBlock, attemptNumber: Int) = update { CompetitionAttempts.toggle(it, block.id, attemptNumber) }

    fun setAttemptResult(block: CompetitionTechnicalBlock, attemptNumber: Int, result: CompetitionAttemptResult) {
        update { CompetitionAttempts.setResult(it, block.id, attemptNumber, result) }
        if (_ui.value.step == CompetitionFlowStep.TODAY) persistToday()
    }

    fun setAttemptWeight(block: CompetitionTechnicalBlock, attemptNumber: Int, kg: Double?) {
        update { CompetitionAttempts.setWeight(it, block.id, attemptNumber, kg) }
        if (_ui.value.step == CompetitionFlowStep.TODAY) persistToday()
    }

    /** c-today: «Válido» o «Nulo» del intento de ahora. Cada toque guarda; al decidir el último, la competencia queda registrada. */
    fun decide(result: CompetitionAttemptResult) {
        val state = _ui.value
        val decided = CompetitionAttempts.decide(state.record, state.discipline, result)
        _ui.update { it.copy(record = decided) }
        persistToday()
        if (CompetitionAttempts.current(decided, state.discipline) == null) advanceFrom(_ui.value)
    }

    fun toggleToday(block: CompetitionTechnicalBlock, attemptNumber: Int) {
        val attempt = block.attempts.firstOrNull { it.attemptNumber == attemptNumber } ?: return
        if (attempt.resultType == CompetitionAttemptResult.PENDING) return
        update { CompetitionAttempts.toggle(it, block.id, attemptNumber) }
        persistToday()
    }

    private fun persistToday() {
        val state = _ui.value
        val done = CompetitionAttempts.current(state.record, state.discipline) == null
        val record = CompetitionScoring.recalculate(
            state.record.copy(
                title = CompetitionDraft.derivedTitle(state.record, state.discipline),
                status = if (done) CompetitionRecordStatus.COMPLETED else CompetitionRecordStatus.PLANNED,
            ),
        )
        repository.upsert(record)
        _ui.update { it.copy(record = record) }
    }

    fun suggestions(query: String): List<CompetitionExerciseSuggestion> =
        CompetitionExerciseTypeahead.suggest(query = query, catalogIndex = catalogExerciseIndex(), catalogV2 = approvedExerciseCatalogV2())

    /** «Cambiar movimiento» del menú largo del intento (C-D6). */
    fun bindExercise(block: CompetitionTechnicalBlock, exercise: ExerciseMuscleInfo) = update { record ->
        record.copy(technicalBlocks = record.technicalBlocks.map { if (it.id == block.id) CompetitionDraft.bindExercise(it, exercise).copy(title = exercise.name) else it })
    }

    fun useCustomExercise(block: CompetitionTechnicalBlock, name: String) {
        viewModelScope.launch {
            val created = withContext(Dispatchers.Default) { CompetitionDraft.createCustomExercise(name, exerciseCatalogSnapshot()) }
            CustomExerciseRepository.upsert(created)
            bindExercise(block, created)
        }
    }

    // ─── Otras formas de resultado ────────────────────────────────────────────────────────────────────────────

    fun setFinishTime(seconds: Double?) = update { it.copy(finishTimeSeconds = seconds?.takeIf { s -> s > 0 }) }
    fun setMark(meters: Double?) = update { it.copy(markMeters = meters?.takeIf { m -> m > 0 }) }
    fun setOverall(place: Int?, field: Int?) = update { it.copy(overallPlace = place?.takeIf { p -> p > 0 }, overallField = field?.takeIf { f -> f > 0 }) }
    fun setCategoryPlace(place: Int?, label: String) = update { it.copy(categoryPlace = place?.takeIf { p -> p > 0 }, category = label.ifBlank { it.category }) }
    fun setSplits(splits: List<Double>) = update { it.copy(splitsSeconds = splits.filter { s -> s > 0 }) }

    fun upsertBout(bout: CompetitionBout) = update { record ->
        val exists = record.bouts.any { it.id == bout.id }
        record.copy(bouts = if (exists) record.bouts.map { if (it.id == bout.id) bout else it } else record.bouts + bout)
    }
    fun removeBout(id: String) = update { it.copy(bouts = it.bouts.filterNot { b -> b.id == id }) }
    fun newBoutId(): String = UUID.randomUUID().toString()

    fun setMetric(id: String?, label: String, value: String) = update { record ->
        if (id == null) record.copy(customMetrics = record.customMetrics + com.example.kpkn.data.models.CustomCompetitionMetric(UUID.randomUUID().toString(), label.trim(), value.trim()))
        else record.copy(customMetrics = record.customMetrics.map { if (it.id == id) it.copy(label = label.trim().ifBlank { it.label }, value = value.trim()) else it })
    }
    fun removeMetric(id: String) = update { it.copy(customMetrics = it.customMetrics.filterNot { m -> m.id == id }) }

    fun setClimbing(topsDone: Int, topsTotal: Int, zonesDone: Int, zonesTotal: Int) = update {
        it.copy(climbing = com.example.kpkn.data.models.CompetitionClimbingResult(topsDone.coerceAtLeast(0), topsTotal.coerceAtLeast(0), zonesDone.coerceAtLeast(0), zonesTotal.coerceAtLeast(0)))
    }

    fun setPlaceOf(place: Int?, field: Int?) = update { CompetitionPlaceHonors.applyPlace(it, place).copy(fieldSize = field?.takeIf { f -> f > 0 }) }

    fun setRounds(value: String) = update {
        it.copy(bodybuildingDetails = (it.bodybuildingDetails ?: com.example.kpkn.data.models.BodybuildingCompetitionDetails()).copy(rounds = value.ifBlank { null }))
    }

    // ─── c-place ──────────────────────────────────────────────────────────────────────────────────────────────

    fun choosePlace(place: Int?) = update { CompetitionPlaceHonors.applyPlace(it, place) }
    fun toggleExtra(extra: com.example.kpkn.domain.competitions.CompetitionExtra) = update { CompetitionPlaceHonors.toggleExtra(it, extra) }

    // ─── c-feel ───────────────────────────────────────────────────────────────────────────────────────────────

    fun setFeeling(id: String) = update { CompetitionDraft.withJournal(it, feeling = id) }
    fun setBest(text: String) = update { CompetitionDraft.withJournal(it, best = text) }
    fun setNext(text: String) = update { CompetitionDraft.withJournal(it, next = text) }

    fun addMedia(context: Context, uris: List<Uri>) {
        val room = CompetitionDraft.MAX_MEDIA - _ui.value.record.photos.size
        if (room <= 0) return
        viewModelScope.launch {
            val id = _ui.value.record.id
            val copied = withContext(Dispatchers.IO) { uris.take(room).mapNotNull { CompetitionMediaStore.copyIntoApp(context, it, id) } }
            update { CompetitionDraft.addMedia(it, copied) }
        }
    }

    /** «Quitar»: sale del álbum y se borra el archivo (las quitadas ya no quedan huérfanas). */
    fun removeMedia(context: Context, photo: CompetitionPhoto) {
        update { CompetitionDraft.removeMedia(it, photo.id) }
        val stillSaved = repository.getById(_ui.value.record.id)?.photos?.any { it.id == photo.id } == true
        viewModelScope.launch(Dispatchers.IO) {
            if (stillSaved) {
                // El registro guardado aún la usa: se guarda sin ella antes de borrar el archivo.
                repository.getById(_ui.value.record.id)?.let { saved -> repository.upsertNow(CompetitionDraft.removeMedia(saved, photo.id)) }
            }
            CompetitionMediaStore.delete(context, photo)
        }
    }

    private fun saveCompleted() {
        val state = _ui.value
        val record = state.record
        val medal = CompetitionPlaceHonors.medalOf(record)
            ?: if (state.discipline.form == CompetitionResultForm.COMBAT) com.example.kpkn.domain.competitions.CompetitionInsights.boutMedal(record) else null
        val completed = CompetitionScoring.recalculate(
            record.copy(
                title = CompetitionDraft.derivedTitle(record, state.discipline),
                status = CompetitionRecordStatus.COMPLETED,
                medal = medal?.id ?: record.medal,
                technicalBlocks = CompetitionScoring.withBestValids(record.technicalBlocks),
            ),
        )
        repository.upsert(completed)
        _ui.update {
            it.copy(
                record = completed,
                records = repository.records.value,
                step = if (mode == CompetitionFlowMode.EDIT) it.step else CompetitionFlowStep.CELE,
                finishedId = if (mode == CompetitionFlowMode.EDIT) completed.id else null,
            )
        }
    }

    companion object {
        const val NEW_ID = "new"
    }
}
