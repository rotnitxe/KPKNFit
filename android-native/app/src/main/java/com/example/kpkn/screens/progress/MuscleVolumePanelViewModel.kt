package com.example.kpkn.screens.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.exercises.catalogExerciseIndex
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.training.MuscleVolumePanel
import com.example.kpkn.domain.training.MuscleVolumePanelBuilder
import com.example.kpkn.domain.training.MuscleVolumePanelRequest
import com.example.kpkn.domain.training.MuscleVolumeWindow
import com.example.kpkn.domain.training.VolumeCalculator
import com.example.kpkn.domain.training.VolumeCountingMode
import com.example.kpkn.screens.programdetail.design.ProgramVolumeZones
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/** Vista del panel: la lista (que también es la alternativa accesible al mapa) o el cuerpo coloreado. */
enum class MuscleVolumeView { LIST, BODY }

/** Qué pinta el cuerpo: las series registradas (lo nuevo) o la recuperación de AUGE (lo de siempre). Nunca las dos a la vez. */
enum class BodyMetric { VOLUME, RECOVERY }

/** Lado del cuerpo que se ve. */
enum class BodySide { FRONT, BACK }

/**
 * Todo lo que la pantalla necesita. [panel] es null hasta el primer cálculo ([loading]). [selectedMuscle] es la fila desplegada
 * y, a la vez, la región elegida en el cuerpo: son la misma selección. [recovery] son los porcentajes de AUGE por músculo; vacío
 * significa «sin datos», no 100 %.
 */
data class MuscleVolumePanelUiState(
    val request: MuscleVolumePanelRequest = MuscleVolumePanelRequest(),
    val view: MuscleVolumeView = MuscleVolumeView.LIST,
    val metric: BodyMetric = BodyMetric.VOLUME,
    val side: BodySide = BodySide.FRONT,
    val selectedMuscle: String? = null,
    val loading: Boolean = true,
    val panel: MuscleVolumePanel? = null,
    /** Zonas del programa calibrado por músculo, en series por semana; vacío si no hay programa o no está calibrado. */
    val zones: Map<String, ProgramVolumeZones> = emptyMap(),
    val recovery: Map<String, Int> = emptyMap(),
    /** Nombre del programa cuando el panel se abrió desde él; null = todos los programas. */
    val scopeName: String? = null,
)

/**
 * Panel de series por músculo y ventana (T5a). El cálculo (puro, en `domain/training`) corre fuera del hilo principal y se repite al
 * cambiar la ventana, el promedio, el rol o el ajuste, o cuando cambia el historial. Cambiar de vista, de lado, de métrica o de región
 * NO recalcula.
 *
 * Las fuentes se inyectan para poder probarlo y para la vista previa de depuración con datos en memoria.
 */
class MuscleVolumePanelViewModel(
    private val programId: String? = null,
    history: Flow<List<WorkoutLog>> = ProgramRepository.getInstance().history,
    programs: Flow<List<Program>> = ProgramRepository.getInstance().programs,
    recovery: Flow<Map<String, Int>> = flowOf(emptyMap()),
    private val exerciseIndex: () -> Map<String, ExerciseMuscleInfo> = { catalogExerciseIndex() },
    private val today: () -> LocalDate = { LocalDate.now(ZoneId.systemDefault()) },
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.IO,
    initialRequest: MuscleVolumePanelRequest = MuscleVolumePanelRequest(),
) : ViewModel() {

    private val request = MutableStateFlow(initialRequest.copy(programId = programId))
    private val _state = MutableStateFlow(MuscleVolumePanelUiState(request = request.value))
    val state: StateFlow<MuscleVolumePanelUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(history, programs, request) { logs, allPrograms, current -> Inputs(logs, allPrograms, current) }
                .collectLatest { inputs ->
                    val computed = withContext(computeDispatcher) { compute(inputs) }
                    _state.update {
                        it.copy(
                            loading = false,
                            panel = computed.panel,
                            zones = computed.zones,
                            scopeName = computed.scopeName,
                        )
                    }
                }
        }
        viewModelScope.launch {
            recovery.collect { values -> _state.update { it.copy(recovery = values) } }
        }
    }

    fun selectWindow(window: MuscleVolumeWindow) = updateRequest { it.copy(window = window) }

    fun selectAverage(averagePerWeek: Boolean) = updateRequest { it.copy(averagePerWeek = averagePerWeek) }

    fun selectMode(mode: VolumeCountingMode) = updateRequest { it.copy(mode = mode) }

    fun selectAdjustByIntensity(adjust: Boolean) = updateRequest { it.copy(adjustByIntensity = adjust) }

    fun selectView(view: MuscleVolumeView) = _state.update { it.copy(view = view) }

    fun selectMetric(metric: BodyMetric) = _state.update { it.copy(metric = metric) }

    fun selectSide(side: BodySide) = _state.update { it.copy(side = side) }

    /** Toca la fila de un músculo: la despliega, o la pliega si ya lo estaba. Es la misma selección que la región del cuerpo. */
    fun toggleMuscle(muscleName: String) =
        _state.update { it.copy(selectedMuscle = if (it.selectedMuscle == muscleName) null else muscleName) }

    /** Toca una región del cuerpo: la elige (y la fila de la lista queda desplegada). */
    fun selectRegion(muscleName: String) = _state.update { it.copy(selectedMuscle = muscleName) }

    private fun updateRequest(transform: (MuscleVolumePanelRequest) -> MuscleVolumePanelRequest) {
        val next = transform(request.value).copy(programId = programId)
        request.value = next
        _state.update { it.copy(request = next) }
    }

    private class Inputs(val logs: List<WorkoutLog>, val programs: List<Program>, val request: MuscleVolumePanelRequest)

    private class Computed(val panel: MuscleVolumePanel, val zones: Map<String, ProgramVolumeZones>, val scopeName: String?)

    private fun compute(inputs: Inputs): Computed {
        val panel = MuscleVolumePanelBuilder.build(inputs.logs, exerciseIndex(), inputs.request, today(), zone())
        val program = programId?.let { id -> inputs.programs.firstOrNull { it.id == id } }
        // Las zonas son semanales: solo tienen sentido con el promedio por semana y con un programa calibrado.
        val zones = if (inputs.request.averagePerWeek && program != null) programZones(program) else emptyMap()
        return Computed(panel, zones, program?.name)
    }

    companion object {
        /**
         * Zonas por músculo canónico (suma de las recomendaciones que caen en el mismo grupo). Igual que la pestaña Volumen del
         * programa, solo existen con el programa calibrado; a diferencia de ella, no se inventan zonas para los músculos sin
         * recomendación propia: ahí la barra va sin zonas.
         */
        fun programZones(program: Program): Map<String, ProgramVolumeZones> {
            if (program.volumeRecommendations.isEmpty() || program.athleteProfileScore == null) return emptyMap()
            return program.volumeRecommendations
                .groupBy { VolumeCalculator.normalizeCanonicalMuscleGroup(it.muscleGroup) }
                .mapValues { (_, group) ->
                    ProgramVolumeZones(
                        minEffective = group.sumOf { it.minEffectiveVolume },
                        maxAdaptive = group.sumOf { it.maxAdaptiveVolume },
                        maxRecoverable = group.sumOf { it.maxRecoverableVolume },
                    )
                }
        }
    }
}
