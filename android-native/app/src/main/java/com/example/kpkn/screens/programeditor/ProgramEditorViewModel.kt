package com.example.kpkn.screens.programeditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.data.splits.SplitTemplate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class EditorSection { DETAILS, STRUCTURE, GOALS, EVENTS, EXPORT }
enum class WizardStep { NAME, MODE, SPLIT, DONE }

data class ProgramEditorUiState(
    val programDraft: Program? = null,
    val isWizardMode: Boolean = false,
    val wizardStep: WizardStep = WizardStep.NAME,
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
            _uiState.update {
                it.copy(
                    programDraft = Program(
                        id = java.util.UUID.randomUUID().toString(),
                        name = "",
                        mode = ProgramMode.HYPERTROPHY,
                        structure = ProgramStructure.SIMPLE,
                        macrocycles = listOf(
                            Macrocycle(
                                id = java.util.UUID.randomUUID().toString(),
                                name = "Macrociclo 1",
                                blocks = listOf(
                                    Block(
                                        id = java.util.UUID.randomUUID().toString(),
                                        name = "Bloque 1",
                                        mesocycles = listOf(
                                            Mesocycle(
                                                id = java.util.UUID.randomUUID().toString(),
                                                name = "Fase Inicial",
                                                goal = MesocycleGoal.ACCUMULATION,
                                                weeks = emptyList(),
                                            )
                                        ),
                                    )
                                ),
                            )
                        ),
                        isDraft = true,
                    ),
                    isWizardMode = true,
                    wizardStep = WizardStep.NAME,
                )
            }
        } else {
            val existing = repository.getProgramById(programId)
            _uiState.update {
                it.copy(
                    programDraft = existing,
                    isWizardMode = false,
                )
            }
        }
    }

    // ─── Wizard ───────────────────────────────────────────────────────────────

    fun nextWizardStep() {
        val current = _uiState.value.wizardStep
        val next = when (current) {
            WizardStep.NAME -> WizardStep.MODE
            WizardStep.MODE -> WizardStep.SPLIT
            WizardStep.SPLIT -> WizardStep.DONE
            WizardStep.DONE -> WizardStep.DONE
        }
        _uiState.update { it.copy(wizardStep = next) }
    }

    fun prevWizardStep() {
        val current = _uiState.value.wizardStep
        val prev = when (current) {
            WizardStep.NAME -> WizardStep.NAME
            WizardStep.MODE -> WizardStep.NAME
            WizardStep.SPLIT -> WizardStep.MODE
            WizardStep.DONE -> WizardStep.SPLIT
        }
        _uiState.update { it.copy(wizardStep = prev) }
    }

    fun setWizardWeeks(count: Int) {
        updateDraft { program ->
            val weeks = (1..count).map { i ->
                ProgramWeek(
                    id = java.util.UUID.randomUUID().toString(),
                    name = "Semana $i",
                )
            }
            program.copy(
                macrocycles = program.macrocycles.map { macro ->
                    macro.copy(blocks = macro.blocks.map { block ->
                        block.copy(mesocycles = block.mesocycles.mapIndexed { i, meso ->
                            if (i == 0) meso.copy(weeks = weeks) else meso
                        })
                    })
                }
            )
        }
    }

    // ─── Program fields ───────────────────────────────────────────────────────

    fun updateName(name: String) = updateDraft { it.copy(name = name) }
    fun updateDescription(desc: String) = updateDraft { it.copy(description = desc) }
    fun updateMode(mode: ProgramMode) = updateDraft { it.copy(mode = mode) }
    fun updateStartDay(day: Int) = updateDraft { it.copy(startDay = day) }
    fun updateStructure(structure: ProgramStructure) = updateDraft { it.copy(structure = structure) }
    fun updateMacrocycles(macrocycles: List<Macrocycle>) = updateDraft { it.copy(macrocycles = macrocycles) }

    fun applyWizardSplit(split: SplitTemplate, startDay: Int) {
        updateDraft { it.copy(selectedSplitId = split.id, startDay = startDay) }
        nextWizardStep()
    }

    fun applySplitFromEditor(split: SplitTemplate, startDay: Int) {
        updateDraft { it.copy(selectedSplitId = split.id, startDay = startDay) }
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

    // ─── Save / Delete ────────────────────────────────────────────────────────

    fun saveProgram(): String? {
        val draft = _uiState.value.programDraft ?: return null
        if (draft.name.isBlank()) return null

        val final = draft.copy(isDraft = false)
        if (programId == "new") {
            repository.addProgram(final)
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
