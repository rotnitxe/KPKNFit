package com.example.kpkn.screens.programs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramMode
import com.example.kpkn.data.repository.ProgramRepository
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Estado del editor de programa DIRECTO (post-alta). Un alta nueva no persiste
 * nada hasta [ProgramEditorViewModel.save]; el guardado es DURABLE (reporta
 * éxito solo tras la confirmación del repositorio) e idempotente: un tap
 * repetido o un retry no duplica, y el UUID de una operación fallida se
 * reutiliza en el reintento.
 */
data class ProgramEditorUiState(
    val isLoading: Boolean = true,
    /** ID del programa en edición; null = alta nueva aún sin persistir. */
    val programId: String? = null,
    /**
     * UUID estable de un alta nueva sin persistir: se genera en el primer
     * intento de guardado y sobrevive a fallos/retries para no duplicar.
     */
    val pendingNewProgramId: String? = null,
    val isEditMode: Boolean = false,
    val missingProgram: Boolean = false,
    val name: String = "",
    val description: String = "",
    val mode: ProgramMode = ProgramMode.HYPERTROPHY,
    val coverImage: String = "gradient://ember",
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    /** Fijado SOLO tras persistencia confirmada: navegación y nada antes. */
    val savedProgramId: String? = null,
    val error: String? = null,
)

/**
 * ViewModel del editor directo de programas (post-onboarding). El alta nueva
 * solo se materializa al [save] mediante persistencia durable suspendida
 * ([ProgramRepository.addProgramNow]); la edición también espera el commit
 * durable de [ProgramRepository.mutateProgramNow]. Ambos puertos se pueden
 * inyectar en tests.
 * El guardado:
 * - no reporta éxito hasta que la fila existe en Room;
 * - mantiene el formulario y el UUID en un fallo (retry → mismo id, sin duplicar);
 * - NO auto-activa el programa (la activación ocurre desde el detalle, igual
 *   que [ProgramsViewModel.createBlankProgram]).
 */
class ProgramEditorViewModel(
    private val repository: ProgramRepository,
    /** Alta nueva: se materializa con [ProgramRepository.addProgramNow] (durable). */
    private val persistProgram: suspend (Program) -> Result<Unit> = { program ->
        repository.addProgramNow(program)
    },
    /**
     * Edición: delega en el motor real del repositorio
     * ([ProgramRepository.mutateProgramNow]), que aplica el transform SOBRE EL
     * ÚLTIMO programa y solo confirma tras Room.
     * Puerto de test mínimo: no duplica el motor, solo permite inyectar una
     * barrera o un fallo alrededor de la misma llamada real.
     */
    private val editProgram: suspend (String, (Program) -> Program?) -> Boolean = { programId, transform ->
        repository.mutateProgramNow(programId, transform)
    },
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProgramEditorUiState())
    val uiState: StateFlow<ProgramEditorUiState> = _uiState.asStateFlow()

    /**
     * Carga el estado inicial (edición de un programa o alta nueva). Espera a
     * que el repositorio haya cargado Room antes de decidir [ProgramEditorUiState.missingProgram]:
     * nunca informa un falso missing por llegar antes que la carga.
     */
    fun initialize(programId: String?) {
        if (!_uiState.value.isLoading) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.isReady.first { it } }
            val snapshot = _uiState.value
            if (!snapshot.isLoading) return@launch
            val cleanId = programId?.trim()?.takeIf { it.isNotBlank() }
            if (cleanId == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isEditMode = false,
                        name = "",
                        description = "",
                        mode = ProgramMode.HYPERTROPHY,
                        coverImage = "gradient://ember",
                    )
                }
                return@launch
            }
            val existing = repository.programs.value.firstOrNull { program -> program.id == cleanId }
            if (existing == null) {
                _uiState.update { it.copy(isLoading = false, missingProgram = true) }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        programId = existing.id,
                        isEditMode = true,
                        name = existing.name,
                        description = existing.description.orEmpty(),
                        mode = existing.mode,
                        coverImage = existing.coverImage ?: "gradient://ember",
                    )
                }
            }
        }
    }

    fun setName(value: String) = mutate { it.copy(name = value) }
    fun setDescription(value: String) = mutate { it.copy(description = value) }
    fun setMode(mode: ProgramMode) = mutate { it.copy(mode = mode) }
    fun setCoverImage(cover: String) = mutate { it.copy(coverImage = cover) }

    /**
     * Salida sin efectos. Si el guardado YA fue commitado ([isSaved]), cancelar
     * no revoca la idempotencia: la navegación al detalle sigue siendo válida.
     */
    fun cancel() {
        if (_uiState.value.isSaved) return
        _uiState.update { it.copy(isSaved = false, savedProgramId = null) }
    }

    /**
     * Guarda el programa de forma durable. Idempotente:
     * - una vez commitado ([savedProgramId] != null) un nuevo [save] no vuelve
     *   a persistir;
     * - mientras [isSaving] nadie más entra (busy) y TAMPOCO se muta el
     *   formulario: si se pudiera editar durante el guardado, el éxito
     *   informaría el snapshot persistido mientras la UI muestra cambios nunca
     *   guardados;
     * - el UUID de un alta nueva se genera en el PRIMER intento y se reutiliza
     *   en los retries (fallo → reintento) → nunca duplica;
     * - en EDICIÓN se toca SOLO nombre/descripción/mode/portada y siempre
     *   sobre el ÚLTIMO programa, dentro de [ProgramRepository.mutateProgramNow]
     *   una escritura de calendario entrando entre el snapshot del formulario
     *   y este commit NO se pierde;
     * - un fallo conserva el formulario y el id, expone el error y NO navega.
     */
    fun save() {
        val snapshot = _uiState.value
        if (snapshot.isLoading || snapshot.isSaving || snapshot.isSaved) return
        if (snapshot.missingProgram) return
        val name = snapshot.name.trim()
        if (name.isBlank()) {
            _uiState.update { it.copy(error = "Escribe un nombre para el programa") }
            return
        }
        val description = snapshot.description.trim().takeIf { it.isNotEmpty() }
        val targetId = snapshot.programId
            ?: snapshot.pendingNewProgramId
            ?: UUID.randomUUID().toString()
        _uiState.update {
            it.copy(
                isSaving = true,
                error = null,
                pendingNewProgramId = if (it.programId == null) targetId else it.pendingNewProgramId,
            )
        }
        viewModelScope.launch {
            val outcome: Result<Unit> = if (snapshot.isEditMode) {
                // Transform sobre el ÚLTIMO programa (leído dentro del lock del
                // repositorio), nunca sobre una copia de este snapshot: solo
                // cambian estos cuatro campos y nada más.
                val edited = try {
                    val committed = withContext(Dispatchers.IO) {
                        editProgram(targetId) { latest ->
                            latest.copy(
                                name = name,
                                description = description,
                                mode = snapshot.mode,
                                coverImage = snapshot.coverImage,
                            )
                        }
                    }
                    if (committed) Result.success(Unit) else null
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (failure: Throwable) {
                    Result.failure<Unit>(failure)
                }
                if (edited == null) {
                    // `false` también puede significar que una versión más
                    // reciente ganó el commit. Solo es missing si ya no queda
                    // el id en la caché; en caso contrario se permite revisar
                    // y reintentar en vez de descartar la edición como si
                    // hubiera desaparecido.
                    val stillExists = repository.getProgramById(targetId) != null
                    _uiState.update {
                        if (stillExists) {
                            it.copy(
                                isSaving = false,
                                isSaved = false,
                                savedProgramId = null,
                                error = "El programa cambió mientras se guardaba. Revisa los cambios y vuelve a guardar.",
                            )
                        } else {
                            it.copy(
                                isSaving = false,
                                isSaved = false,
                                savedProgramId = null,
                                missingProgram = true,
                            )
                        }
                    }
                    return@launch
                }
                edited
            } else {
                val program = buildNewProgram(
                    id = targetId,
                    name = name,
                    description = description,
                    coverImage = snapshot.coverImage,
                    mode = snapshot.mode,
                )
                // persistProgram puede lanzar (no solo devolver Result): un throw
                // sin capturar dejaría el spinner clavado y el estado sin resolver.
                try {
                    withContext(Dispatchers.IO) { persistProgram(program) }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (failure: Throwable) {
                    Result.failure<Unit>(failure)
                }
            }
            outcome.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            isSaved = true,
                            savedProgramId = targetId,
                            error = null,
                        )
                    }
                },
                onFailure = { failure ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            isSaved = false,
                            savedProgramId = null,
                            error = failure.message ?: "No se pudo guardar el programa",
                        )
                    }
                },
            )
        }
    }

    private fun mutate(transform: (ProgramEditorUiState) -> ProgramEditorUiState) {
        _uiState.update { current ->
            when {
                // Guardado en curso: el formulario es el snapshot que se está
                // persistiendo; mutarlo desincronizaría UI y fila durable.
                current.isSaving -> current
                // Commit ya realizado: la edición posterior no revoca el éxito.
                current.isSaved -> transform(current).copy(error = null)
                else -> transform(current).copy(isSaved = false, savedProgramId = null, error = null)
            }
        }
    }
}
