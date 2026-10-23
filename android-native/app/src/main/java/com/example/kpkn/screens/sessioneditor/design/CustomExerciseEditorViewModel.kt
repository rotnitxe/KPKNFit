package com.example.kpkn.screens.sessioneditor.design

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.exercises.*
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

internal sealed interface CustomExerciseCoverDraft {
    data object Keep : CustomExerciseCoverDraft
    data object Pattern : CustomExerciseCoverDraft
    data class Reference(val cover: ExerciseCover) : CustomExerciseCoverDraft
    data class PrivatePhoto(val media: WorkoutMedia) : CustomExerciseCoverDraft
    data class DevicePhoto(val uri: String, val importId: String = UUID.randomUUID().toString()) : CustomExerciseCoverDraft
}

internal data class CustomExerciseEditorState(
    val name: String = "",
    val implement: String? = null,
    val station: String? = null,
    val laterality: String? = null,
    val description: String? = null,
    val muscles: List<InvolvedMuscle>? = null,
    val cover: CustomExerciseCoverDraft = CustomExerciseCoverDraft.Keep,
    val preview: SmartCreatePreview? = null,
    val reviewed: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val saved: ExerciseMuscleInfo? = null,
) {
    val candidates: List<ReferenceCandidate> get() = preview?.referenceResult?.let {
        if (it.outcome == ReferenceOutcome.NONE) emptyList() else it.candidates
    }.orEmpty()
}

/** El borrador no publica cambios: referencia, foto y datos manuales solo se guardan al confirmar. */
internal class CustomExerciseEditorViewModel(
    private val initial: ExerciseMuscleInfo?,
    initialName: String,
    private val catalog: List<ExerciseMuscleInfo>,
    private val catalogV2: ExerciseCatalogV2?,
    private val persist: suspend (ExerciseMuscleInfo) -> Result<ExerciseMuscleInfo>,
    private val importPhoto: suspend (CustomExerciseCoverDraft.DevicePhoto, ExerciseMuscleInfo) -> WorkoutMedia,
    private val previewDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    val draftId = initial?.id ?: "custom:${UUID.randomUUID()}"
    private val _state = MutableStateFlow(CustomExerciseEditorState(
        name = initial?.name ?: initialName,
        implement = initial?.equipment?.let(SmartExerciseCreator::implementoIdFromLabel),
        station = initial?.catalogVariantChips?.firstNotNullOfOrNull(SmartExerciseCreator::estacionIdFromLabel),
        laterality = initial?.catalogVariantChips?.firstNotNullOfOrNull(SmartExerciseCreator::lateralidadIdFromLabel),
        description = initial?.description?.takeUnless { initial.descriptionOrigin == "GENERATED" },
        muscles = initial?.involvedMuscles,
    ))
    val state = _state.asStateFlow()
    private var previewJob: Job? = null
    private var version = 0

    init { refresh() }

    fun edit(transform: (CustomExerciseEditorState) -> CustomExerciseEditorState) {
        if (_state.value.saving) return
        val previous = _state.value
        val next = transform(previous)
        val identityChanged = next.name != previous.name || next.implement != previous.implement ||
            next.station != previous.station || next.laterality != previous.laterality
        _state.value = next.copy(reviewed = false, error = null, saved = null, preview = null,
            cover = if (identityChanged && next.cover is CustomExerciseCoverDraft.Reference) CustomExerciseCoverDraft.Pattern else next.cover)
        refresh()
    }

    fun review(value: Boolean) { if (!_state.value.saving && _state.value.preview != null) _state.value = _state.value.copy(reviewed = value) }
    fun cover(value: CustomExerciseCoverDraft) {
        if (_state.value.saving || (value is CustomExerciseCoverDraft.PrivatePhoto && value.media.kind != WorkoutMediaKind.PHOTO)) return
        _state.value = _state.value.copy(cover = value, error = null)
    }
    fun consumeSaved() { _state.value = _state.value.copy(saved = null) }

    private fun refresh() {
        val revision = ++version
        previewJob?.cancel()
        val draft = _state.value
        if (draft.name.isBlank()) return
        previewJob = viewModelScope.launch {
            val result = withContext(previewDispatcher) {
                runCatching {
                    SmartExerciseCreator.preview(SmartCreateRequest(
                        name = draft.name, implementoId = draft.implement.orEmpty(), estacionId = draft.station,
                        lateralidadId = draft.laterality, description = draft.description,
                        musclesOverride = draft.muscles, existingId = draftId, existing = initial,
                    ), catalog, catalogV2).let { preview ->
                        // Vacío explícito también es una edición manual; no vuelve a rellenarse por inferencia.
                        if (draft.muscles == null) preview else preview.copy(exercise = preview.exercise.copy(involvedMuscles = draft.muscles))
                    }
                }
            }
            if (revision == version) _state.value = _state.value.copy(preview = result.getOrNull(),
                error = result.exceptionOrNull()?.let { "No se pudo preparar la vista previa. Revisa el nombre e inténtalo de nuevo." })
        }
    }

    fun save() {
        val draft = _state.value
        val preview = draft.preview ?: return
        if (draft.saving || !draft.reviewed || draft.name.isBlank()) return
        _state.value = draft.copy(saving = true, error = null)
        viewModelScope.launch {
            try {
                var info = preview.exercise
                val selectedCover = when (val choice = draft.cover) {
                    CustomExerciseCoverDraft.Keep -> initial?.cover ?: info.cover
                    CustomExerciseCoverDraft.Pattern -> ExerciseCover(patternId = ExerciseCoverResolver.patternIdFor(info))
                    is CustomExerciseCoverDraft.Reference -> choice.cover
                    is CustomExerciseCoverDraft.PrivatePhoto -> ExerciseCover.userPhoto(choice.media.id)
                    is CustomExerciseCoverDraft.DevicePhoto -> {
                        val media = importPhoto(choice, info)
                        // Un reintento no vuelve a importar la misma foto si Room rechaza el ejercicio.
                        _state.value = _state.value.copy(cover = CustomExerciseCoverDraft.PrivatePhoto(media))
                        ExerciseCover.userPhoto(media.id)
                    }
                }
                info = info.copy(cover = selectedCover)
                val result = persist(info)
                _state.value = _state.value.copy(saving = false, saved = result.getOrNull(),
                    error = if (result.isFailure) "No se pudo guardar «${draft.name}». Lo que escribiste sigue aquí; puedes reintentar." else null)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.value = _state.value.copy(saving = false,
                    error = "No se pudo guardar la foto o el ejercicio. Lo que escribiste sigue aquí; puedes reintentar.")
            }
        }
    }
}
