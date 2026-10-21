package com.example.kpkn.screens.settings.equipment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.exercises.catalogExerciseIndex
import com.example.kpkn.data.models.EquipmentInventory
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.equipment.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface EquipmentInventoryStore {
    suspend fun load(): EquipmentInventory
    suspend fun save(inventory: EquipmentInventory)
}

class ProgramEquipmentInventoryStore(private val repository: ProgramRepository) : EquipmentInventoryStore {
    override suspend fun load(): EquipmentInventory {
        repository.isReady.first { it }
        return repository.settings.value.resolvedEquipmentInventory()
    }

    override suspend fun save(inventory: EquipmentInventory) = repository.saveEquipmentInventory(inventory)
}

enum class EquipmentSection { PLATES, DUMBBELLS, KETTLEBELLS, MACHINES, DUMBBELL_RANGE, TEMPLATES }
data class EquipmentEditTarget(val section: EquipmentSection, val index: Int = 0)

data class EquipmentInventoryUiState(
    val loading: Boolean = true,
    val draft: EquipmentInventoryDraft = EquipmentInventoryDraft(),
    val dirty: Boolean = false,
    val errors: Map<String, String> = emptyMap(),
    val summary: EquipmentLoadSummary = EquipmentLoadSummary(),
    val summaryLoading: Boolean = false,
    val configurations: List<EquipmentMachineConfiguration> = emptyList(),
    val editing: EquipmentEditTarget? = null,
    val range: DumbbellRangeDraft = DumbbellRangeDraft(),
    val rangeError: String? = null,
    val configurationQuery: String = "",
    val saving: Boolean = false,
    val loadError: String? = null,
    val saveError: String? = null,
    val showDiscardDialog: Boolean = false,
    val exitRequested: Boolean = false,
)

class EquipmentInventoryViewModel(
    private val store: EquipmentInventoryStore = ProgramEquipmentInventoryStore(ProgramRepository.getInstance()),
    private val loadConfigurations: suspend () -> List<EquipmentMachineConfiguration> = {
        catalogExerciseIndex().values.mapNotNull { exercise ->
            val id = exercise.catalogConfigurationId
            val kind = exercise.equipment
            if (id != null && kind in EquipmentInventoryEditor.machineKinds)
                EquipmentMachineConfiguration(id, exercise.name + exercise.catalogVariantChips.takeIf { it.isNotEmpty() }
                    ?.joinToString(prefix = " · ", separator = " · ").orEmpty(), kind!!) else null
        }.distinctBy { it.id }.sortedBy { it.name }
    },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _state = MutableStateFlow(EquipmentInventoryUiState())
    val state = _state.asStateFlow()
    private var baseline = EquipmentInventoryDraft()
    private var summaryJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                val (draft, configurations) = withContext(ioDispatcher) {
                    EquipmentInventoryEditor.fromInventory(store.load()) to loadConfigurations()
                }
                baseline = draft
                _state.value = _state.value.copy(loading = false, configurations = configurations)
                edit(draft)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.value = _state.value.copy(loading = false, loadError = "No se pudo leer tu material. Vuelve a abrir esta pantalla.")
            }
        }
    }

    fun edit(draft: EquipmentInventoryDraft) {
        if (_state.value.saving || _state.value.loading || _state.value.loadError != null) return
        val validation = EquipmentInventoryEditor.validate(draft)
        _state.value = _state.value.copy(draft = draft, dirty = draft != baseline, errors = validation.errors,
            summary = EquipmentLoadSummary(), summaryLoading = validation.isValid, saveError = null)
        summaryJob?.cancel()
        summaryJob = viewModelScope.launch {
            if (validation.isValid) delay(180)
            val summary = validation.inventory?.let { withContext(ioDispatcher) { EquipmentInventoryEditor.summarize(it) } }
                ?: EquipmentLoadSummary()
            if (_state.value.draft == draft) _state.value = _state.value.copy(summary = summary, summaryLoading = false)
        }
    }

    fun useTemplate(template: EquipmentInventoryTemplate) {
        if (_state.value.saving || _state.value.loading || _state.value.loadError != null) return
        edit(EquipmentInventoryEditor.template(template))
        closeEditor()
    }

    fun openEditor(section: EquipmentSection, index: Int? = null) {
        if (_state.value.saving || _state.value.loading || _state.value.loadError != null) return
        val draft = _state.value.draft
        val actualIndex = index ?: when (section) {
            EquipmentSection.PLATES -> draft.plates.size
            EquipmentSection.DUMBBELLS -> draft.dumbbells.size
            EquipmentSection.KETTLEBELLS -> draft.kettlebells.size
            EquipmentSection.MACHINES -> draft.machines.size
            EquipmentSection.DUMBBELL_RANGE, EquipmentSection.TEMPLATES -> 0
        }
        if (index == null && section != EquipmentSection.DUMBBELL_RANGE && section != EquipmentSection.TEMPLATES) {
            if (actualIndex >= EquipmentInventoryEditor.MAX_ROWS) return
            edit(when (section) {
                EquipmentSection.PLATES -> draft.copy(plates = draft.plates + PlateDraft())
                EquipmentSection.DUMBBELLS -> draft.copy(dumbbells = draft.dumbbells + DumbbellDraft())
                EquipmentSection.KETTLEBELLS -> draft.copy(kettlebells = draft.kettlebells + "")
                EquipmentSection.MACHINES -> draft.copy(machines = draft.machines + MachineDraft())
                else -> draft
            })
        }
        _state.value = _state.value.copy(editing = EquipmentEditTarget(section, actualIndex), configurationQuery = "")
    }

    fun closeEditor() { _state.value = _state.value.copy(editing = null) }
    fun configurationQuery(value: String) { _state.value = _state.value.copy(configurationQuery = value) }
    fun editRange(range: DumbbellRangeDraft) { _state.value = _state.value.copy(range = range, rangeError = null) }

    fun addRange() {
        val result = EquipmentInventoryEditor.addDumbbellRange(_state.value.draft, _state.value.range)
        if (result.error == null) {
            edit(result.draft)
            closeEditor()
        } else _state.value = _state.value.copy(rangeError = result.error)
    }

    fun removeEditingRow() {
        val target = _state.value.editing ?: return
        val draft = _state.value.draft
        edit(when (target.section) {
            EquipmentSection.PLATES -> draft.copy(plates = draft.plates.filterIndexed { index, _ -> index != target.index })
            EquipmentSection.DUMBBELLS -> draft.copy(dumbbells = draft.dumbbells.filterIndexed { index, _ -> index != target.index })
            EquipmentSection.KETTLEBELLS -> draft.copy(kettlebells = draft.kettlebells.filterIndexed { index, _ -> index != target.index })
            EquipmentSection.MACHINES -> draft.copy(machines = draft.machines.filterIndexed { index, _ -> index != target.index })
            else -> draft
        })
        closeEditor()
    }

    fun requestExit() {
        if (_state.value.saving) return
        if (_state.value.editing != null) { closeEditor(); return }
        _state.value = _state.value.copy(showDiscardDialog = _state.value.dirty, exitRequested = !_state.value.dirty)
    }

    fun keepEditing() { _state.value = _state.value.copy(showDiscardDialog = false) }
    fun discard() {
        if (_state.value.saving) return
        summaryJob?.cancel()
        _state.value = _state.value.copy(draft = baseline, dirty = false, showDiscardDialog = false, editing = null, exitRequested = true)
    }

    fun save() {
        val current = _state.value
        if (current.loading || current.saving || current.loadError != null) return
        val validation = EquipmentInventoryEditor.validate(current.draft)
        _state.value = current.copy(errors = validation.errors)
        val inventory = validation.inventory ?: return
        _state.value = _state.value.copy(saving = true, saveError = null)
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) { store.save(inventory) }
                baseline = current.draft
                _state.value = _state.value.copy(saving = false, dirty = false, exitRequested = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.value = _state.value.copy(saving = false, saveError = "No se pudo guardar. Tus cambios siguen aquí; vuelve a intentarlo.")
            }
        }
    }
}
