package com.example.kpkn.screens.programeditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.kpkn.data.models.*
import com.example.kpkn.data.programs.PROGRAM_TEMPLATES
import com.example.kpkn.data.programs.buildProgramDraft
import com.example.kpkn.data.programs.resolveProgramTemplate
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.data.splits.SplitTemplate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class EditorSection { DETAILS, STRUCTURE, GOALS, EVENTS, EXPORT }
enum class WizardStep { COVER }

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
            val template = PROGRAM_TEMPLATES.first()
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
            val existing = repository.getProgramById(programId)
            _uiState.update {
                it.copy(
                    programDraft = existing,
                    isWizardMode = false,
                    selectedTemplateId = existing?.structureTemplateId ?: PROGRAM_TEMPLATES.first().id,
                )
            }
        }
    }

    // ─── Wizard ───────────────────────────────────────────────────────────────

    fun nextWizardStep() {
        _uiState.update { it.copy(wizardStep = WizardStep.COVER) }
    }

    fun prevWizardStep() {
        _uiState.update { it.copy(wizardStep = WizardStep.COVER) }
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
    fun updateStartDay(day: Int) = updateDraft { it.copy(startDay = day) }
    fun updateWeekDays(days: Int) = updateDraft { it.copy(weekDays = days.coerceIn(1, 14)) }
    fun updateStructure(structure: ProgramStructure) = updateDraft { it.copy(structure = structure) }
    fun updateMacrocycles(macrocycles: List<Macrocycle>) = updateDraft { it.copy(macrocycles = macrocycles) }
    fun updateCustomSplitPattern(pattern: List<String>) = updateDraft { it.copy(customSplitPattern = pattern) }

    fun applyWizardSplit(split: SplitTemplate, startDay: Int) {
        updateDraft { it.copy(selectedSplitId = split.id, startDay = startDay, customSplitPattern = split.pattern) }
    }

    fun applySplitFromEditor(split: SplitTemplate, startDay: Int) {
        updateDraft { it.copy(selectedSplitId = split.id, startDay = startDay, customSplitPattern = split.pattern) }
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

        val final = draft.copy(isDraft = false)
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

    companion object {
        fun factory(programId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ProgramEditorViewModel(programId) as T
            }
    }
}
