package com.example.kpkn.screens.programeditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.kpkn.data.models.*
import com.example.kpkn.data.programs.PROGRAM_TEMPLATES
import com.example.kpkn.data.programs.buildProgramDraft
import com.example.kpkn.data.programs.resolveProgramTemplate
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import com.example.kpkn.data.splits.SplitTemplate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class EditorSection { DETAILS, STRUCTURE, GOALS, EVENTS, EXPORT }
enum class WizardStep { COVER, SPLIT }

data class ProgramEditorUiState(
    val programDraft: Program? = null,
    val isWizardMode: Boolean = false,
    val wizardStep: WizardStep = WizardStep.COVER,
    val selectedTemplateId: String = PROGRAM_TEMPLATES.first().id,
    val activeSection: EditorSection = EditorSection.DETAILS,
    val hasUnsavedChanges: Boolean = false,
    val isSplitChangerOpen: Boolean = false,
    val showDeleteDialog: Boolean = false,
)

class ProgramEditorViewModel(private val programId: String) : ViewModel() {

    private val repository = ProgramRepository.getInstance()

    private val _uiState = MutableStateFlow(ProgramEditorUiState())
    val uiState: StateFlow<ProgramEditorUiState> = _uiState.asStateFlow()

    init {
        if (programId == "new") {
            val template = PROGRAM_TEMPLATES.firstOrNull()
            if (template != null) {
                val draft = template.buildProgramDraft(
                    Program(
                        id = java.util.UUID.randomUUID().toString(),
                        name = "",
                        mode = ProgramMode.HYPERTROPHY,
                        structure = template.type,
                        structureTemplateId = template.id,
                        macrocycles = emptyList(),
                        isDraft = true,
                    ),
                )
                _uiState.update {
                    it.copy(
                        programDraft = draft,
                        isWizardMode = true,
                        wizardStep = WizardStep.COVER,
                        selectedTemplateId = template.id,
                    )
                }
            } else {
                _uiState.update { it.copy(programDraft = null, isWizardMode = false) }
            }
        } else {
            val existing = repository.getProgramById(programId)
            _uiState.update {
                it.copy(
                    programDraft = existing,
                    isWizardMode = false,
                    selectedTemplateId = existing?.structureTemplateId ?: PROGRAM_TEMPLATES.firstOrNull()?.id.orEmpty(),
                )
            }
        }
    }

    // ─── Wizard ───────────────────────────────────────────────────────────────

    fun nextWizardStep() {
        _uiState.update { state ->
            val currentIndex = wizardStepOrder.indexOf(state.wizardStep)
            val nextStep = wizardStepOrder.getOrNull(currentIndex + 1) ?: state.wizardStep
            state.copy(wizardStep = nextStep)
        }
    }

    fun prevWizardStep() {
        _uiState.update { state ->
            val currentIndex = wizardStepOrder.indexOf(state.wizardStep)
            val prevStep = wizardStepOrder.getOrNull(currentIndex - 1) ?: state.wizardStep
            state.copy(wizardStep = prevStep)
        }
    }

    fun setWizardStep(step: WizardStep) {
        _uiState.update { it.copy(wizardStep = step) }
    }

    fun selectWizardTemplate(templateId: String) {
        val template = resolveProgramTemplate(templateId)
        updateDraft { current -> template.buildProgramDraft(current) }
        _uiState.update { it.copy(selectedTemplateId = template.id) }
    }

    fun setWizardWeeks(count: Int) {
        updateWizardBlockWeeks(0, count)
    }

    fun updateWizardBlockWeeks(blockIndex: Int, count: Int) {
        val weeks = (1..count.coerceAtLeast(1)).map { i ->
            ProgramWeek(
                id = java.util.UUID.randomUUID().toString(),
                name = "Semana $i",
            )
        }
        updateDraft { program ->
            program.copy(
                macrocycles = program.macrocycles.mapIndexed { macroIndex, macrocycle ->
                    if (macroIndex != 0) return@mapIndexed macrocycle
                    macrocycle.copy(
                        blocks = macrocycle.blocks.mapIndexed { idx, block ->
                            if (idx != blockIndex) {
                                block
                            } else {
                                block.copy(
                                    mesocycles = block.mesocycles.mapIndexed { mesoIndex, meso ->
                                        if (mesoIndex == 0) meso.copy(weeks = weeks) else meso
                                    },
                                )
                            }
                        },
                    )
                },
            )
        }
    }

    // ─── Program fields ───────────────────────────────────────────────────────

    fun updateName(name: String) = updateDraft { it.copy(name = name) }
    fun updateDescription(desc: String) = updateDraft { it.copy(description = desc) }
    fun updateCoverImage(coverImage: String?) = updateDraft { it.copy(coverImage = coverImage) }
    fun updateMode(mode: ProgramMode) = updateDraft { it.copy(mode = mode) }
    fun updateStartDay(day: Int) = updateDraft { program ->
        val normalizedDay = day.coerceIn(1, 7)
        val updated = program.copy(startDay = normalizedDay)
        applySplitProjection(updated)
    }
    fun updateWeekDays(days: Int) = updateDraft { it.copy(weekDays = days.coerceIn(1, 14)) }
    fun updateStructure(structure: ProgramStructure) = updateDraft { it.copy(structure = structure) }
    fun updateMacrocycles(macrocycles: List<Macrocycle>) = updateDraft { it.copy(macrocycles = macrocycles) }
    fun updateCustomSplitPattern(pattern: List<String>) = updateDraft { program ->
        val updated = program.copy(customSplitPattern = pattern)
        applySplitProjection(updated)
    }

    fun applyWizardSplit(split: SplitTemplate, startDay: Int) {
        updateDraft { program ->
            applySplitProjection(
                program.copy(
                    selectedSplitId = split.id,
                    startDay = startDay.coerceIn(1, 7),
                    customSplitPattern = split.pattern,
                )
            )
        }
    }

    fun applySplitFromEditor(split: SplitTemplate, startDay: Int) {
        updateDraft { program ->
            applySplitProjection(
                program.copy(
                    selectedSplitId = split.id,
                    startDay = startDay.coerceIn(1, 7),
                    customSplitPattern = split.pattern,
                )
            )
        }
        _uiState.update { it.copy(isSplitChangerOpen = false) }
    }

    // ─── Sections ─────────────────────────────────────────────────────────────

    fun setActiveSection(section: EditorSection) = _uiState.update { it.copy(activeSection = section) }
    fun setSplitChangerOpen(open: Boolean) = _uiState.update { it.copy(isSplitChangerOpen = open) }
    fun setShowDeleteDialog(show: Boolean) = _uiState.update { it.copy(showDeleteDialog = show) }

    // ─── Goals ────────────────────────────────────────────────────────────────

    fun addGoal(exerciseId: String, target1RM: Double) {
        updateDraft { it.copy(exerciseGoals = it.exerciseGoals + (exerciseId to target1RM)) }
    }

    fun removeGoal(exerciseId: String) {
        updateDraft { it.copy(exerciseGoals = it.exerciseGoals - exerciseId) }
    }

    fun updateGoalTarget(exerciseId: String, target: Double) {
        updateDraft { it.copy(exerciseGoals = it.exerciseGoals + (exerciseId to target)) }
    }

    // ─── Events ───────────────────────────────────────────────────────────────

    fun addEvent(event: ProgramEvent) {
        updateDraft { it.copy(events = it.events + event) }
    }

    fun removeEvent(id: String) {
        updateDraft { it.copy(events = it.events.filter { e -> e.id != id }) }
    }

    fun addWizardEvent(event: ProgramEvent) {
        addEvent(event)
    }

    fun removeWizardEvent(id: String) {
        removeEvent(id)
    }

    // ─── Save / Delete ────────────────────────────────────────────────────────

    fun saveProgram(): String? {
        val draft = _uiState.value.programDraft ?: return null
        if (draft.name.isBlank()) return null

        val final = applySplitProjection(draft).copy(isDraft = false)
        val hadRealProgramsBefore = repository.programs.value.any { !it.isDraft }
        if (programId == "new") {
            repository.addProgram(final)
            if (!hadRealProgramsBefore) {
                repository.startProgram(final.id)
            }
        } else {
            repository.updateProgram(final)
        }
        _uiState.update { it.copy(hasUnsavedChanges = false) }
        return final.id
    }

    fun deleteProgram() {
        repository.deleteProgram(programId)
    }

    fun duplicateProgram(): String? {
        val draft = _uiState.value.programDraft ?: return null
        val copy = draft.copy(
            id = java.util.UUID.randomUUID().toString(),
            name = "${draft.name} (Copia)",
            isDraft = false,
        )
        repository.addProgram(copy)
        return copy.id
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun updateDraft(transform: (Program) -> Program) {
        _uiState.update { state ->
            state.copy(
                programDraft = state.programDraft?.let(transform),
                hasUnsavedChanges = true,
            )
        }
    }

    private fun applySplitProjection(program: Program): Program {
        val pattern = when {
            program.customSplitPattern.isNotEmpty() -> program.customSplitPattern
            !program.selectedSplitId.isNullOrBlank() -> {
                SPLIT_TEMPLATES.firstOrNull { it.id == program.selectedSplitId }?.pattern.orEmpty()
            }
            else -> emptyList()
        }
        if (pattern.isEmpty()) return program

        val startDay = (program.startDay ?: 1).coerceIn(1, 7)

        return program.copy(
            startDay = startDay,
            macrocycles = program.macrocycles.map { macrocycle ->
                macrocycle.copy(
                    blocks = macrocycle.blocks.map { block ->
                        block.copy(
                            mesocycles = block.mesocycles.map { mesocycle ->
                                mesocycle.copy(
                                    weeks = mesocycle.weeks.map { week ->
                                        week.copy(
                                            sessions = projectWeekSessions(
                                                existingSessions = week.sessions,
                                                pattern = pattern,
                                                startDay = startDay,
                                            )
                                        )
                                    }
                                )
                            }
                        )
                    }
                )
            }
        )
    }

    private fun projectWeekSessions(
        existingSessions: List<Session>,
        pattern: List<String>,
        startDay: Int,
    ): List<Session> {
        val rotatedDays = (startDay..7).toList() + (1 until startDay).toList()
        val targets = pattern.mapIndexedNotNull { index, label ->
            if (label.equals("Descanso", ignoreCase = true)) return@mapIndexedNotNull null
            val dayOfWeek = rotatedDays[index % rotatedDays.size]
            SplitTarget(label = label, dayOfWeek = dayOfWeek)
        }
        if (targets.isEmpty()) return emptyList()

        return targets.mapIndexed { index, target ->
            val existing = existingSessions.getOrNull(index)
            if (existing != null) {
                existing.copy(
                    dayOfWeek = target.dayOfWeek,
                    scheduleLabel = dayName(target.dayOfWeek),
                    isMainSession = index == 0,
                )
            } else {
                Session(
                    id = java.util.UUID.randomUUID().toString(),
                    name = target.label,
                    dayOfWeek = target.dayOfWeek,
                    scheduleLabel = dayName(target.dayOfWeek),
                    isMainSession = index == 0,
                )
            }
        }
    }

    private fun dayName(dayOfWeek: Int): String = when (dayOfWeek) {
        1 -> "Lunes"
        2 -> "Martes"
        3 -> "Miércoles"
        4 -> "Jueves"
        5 -> "Viernes"
        6 -> "Sábado"
        7 -> "Domingo"
        else -> "Lunes"
    }

    private data class SplitTarget(
        val label: String,
        val dayOfWeek: Int,
    )

    companion object {
        fun factory(programId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ProgramEditorViewModel(programId) as T
            }
    }

    private val wizardStepOrder = listOf(
        WizardStep.COVER,
        WizardStep.SPLIT,
    )
}
