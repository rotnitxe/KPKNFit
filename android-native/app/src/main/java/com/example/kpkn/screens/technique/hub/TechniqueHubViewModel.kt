package com.example.kpkn.screens.technique.hub

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.db.VideoAnalysisExerciseRow
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.TechniqueVideoSettings
import com.example.kpkn.data.models.VideoAnalysisSummary
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.models.WorkoutMediaKind
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.data.repository.VideoAnalysisRepository
import com.example.kpkn.data.repository.WorkoutMediaRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TechniqueExerciseItem(
    val exerciseKey: String,
    val name: String,
    val videoCount: Int,
    val lastRecordedAtMs: Long,
    val sparkline: List<Float> = emptyList(),
    val metricLabel: String? = null,
)

data class TechniqueHubUiState(
    val isLoading: Boolean = true,
    val exercises: List<TechniqueExerciseItem> = emptyList(),
    val pendingMediaIds: List<String> = emptyList(),
    val settings: TechniqueVideoSettings = TechniqueVideoSettings(),
    val isEnqueuing: Boolean = false,
    val message: String? = null,
)

/** Frontera inyectable del hub: las lecturas siguen pasando por los repositorios. */
interface TechniqueHubSource {
    val summaries: Flow<List<VideoAnalysisExerciseRow>>
    val media: Flow<List<WorkoutMedia>>
    val settings: Flow<Settings>
    suspend fun analysis(mediaId: String): VideoAnalysisSummary?
    suspend fun trend(exerciseKey: String): List<VideoAnalysisSummary>
    suspend fun requestAnalysis(mediaId: String): Boolean
    fun updateSettings(change: (TechniqueVideoSettings) -> TechniqueVideoSettings)
}

private class RepositoryTechniqueHubSource(application: Application) : TechniqueHubSource {
    private val analysisRepository = VideoAnalysisRepository.init(application)
    private val mediaRepository = WorkoutMediaRepository.init(application)
    private val programs = ProgramRepository.getInstance()
    override val summaries = analysisRepository.observeExerciseSummaries()
    override val media = mediaRepository.observeAll()
    override val settings = programs.settings
    override suspend fun analysis(mediaId: String) = analysisRepository.getByMedia(mediaId)
    override suspend fun trend(exerciseKey: String) = analysisRepository.observeTrend(exerciseKey).first()
    override suspend fun requestAnalysis(mediaId: String) = analysisRepository.requestAnalysis(mediaId)
    override fun updateSettings(change: (TechniqueVideoSettings) -> TechniqueVideoSettings) {
        programs.updateSettings { current -> current.copy(techniqueVideo = change(current.techniqueVideo)) }
    }
}

class TechniqueHubViewModel @JvmOverloads constructor(
    application: Application,
    private val source: TechniqueHubSource = RepositoryTechniqueHubSource(application),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TechniqueHubUiState())
    val uiState: StateFlow<TechniqueHubUiState> = _uiState.asStateFlow()
    private val refresh = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            combine(source.summaries, source.media, source.settings, refresh) { rows, media, settings, _ ->
                Triple(rows, media, settings.techniqueVideo)
            }.collectLatest { (rows, media, settings) ->
                try {
                    val (items, pending) = withContext(ioDispatcher) {
                        val items = rows.map { row ->
                            val trend = source.trend(row.exerciseKey).sortedBy { it.recordedAtMs }
                            val loss = trend.mapNotNull { it.velocityLossPct?.takeIf(Double::isFinite)?.toFloat() }
                            val rom = trend.mapNotNull { it.romMeanDeg?.takeIf(Double::isFinite)?.toFloat() }
                            val useLoss = loss.size >= 2
                            val points = if (useLoss) loss else rom
                            TechniqueExerciseItem(
                                exerciseKey = row.exerciseKey,
                                name = row.exerciseName?.takeIf(String::isNotBlank) ?: "Ejercicio sin nombre",
                                videoCount = row.videoCount,
                                lastRecordedAtMs = row.lastRecordedAtMs,
                                sparkline = points.takeLast(12).takeIf { it.size >= 2 }.orEmpty(),
                                metricLabel = if (points.size < 2) null else if (useLoss) "Pérdida de velocidad, %" else "ROM, °",
                            )
                        }
                        val pending = media.filter { it.kind == WorkoutMediaKind.VIDEO }
                            .filter { source.analysis(it.id) == null }.map { it.id }
                        items to pending
                    }
                    _uiState.update { it.copy(isLoading = false, exercises = items, pendingMediaIds = pending, settings = settings) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    _uiState.update { it.copy(isLoading = false, message = "No se pudieron cargar los vídeos. Vuelve a intentarlo.") }
                }
            }
        }
    }

    fun setAnalyzeVideos(value: Boolean) = source.updateSettings { it.copy(analyzeVideos = value) }
    fun setPrefer60Fps(value: Boolean) = source.updateSettings { it.copy(prefer60Fps = value) }
    fun setPlateDiameterMm(value: Int) {
        if (value in 100..700) source.updateSettings { it.copy(plateDiameterMm = value) }
    }
    fun setMaxRecordSeconds(value: Int) {
        if (value in listOf(60, 120, 180, 300)) source.updateSettings { it.copy(maxRecordSeconds = value) }
    }
    fun analyzePrevious() {
        if (_uiState.value.isEnqueuing) return
        val ids = _uiState.value.pendingMediaIds.toList()
        if (ids.isEmpty()) return
        _uiState.update { it.copy(isEnqueuing = true, message = null) }
        viewModelScope.launch {
            try {
                val accepted = withContext(ioDispatcher) { ids.count { source.requestAnalysis(it) } }
                _uiState.update { it.copy(message = if (accepted == ids.size) "Vídeos añadidos a la cola de análisis" else "Algunos vídeos no se pudieron añadir. Vuelve a intentarlo.") }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update { it.copy(message = "No se pudo iniciar el análisis. Vuelve a intentarlo.") }
            } finally {
                _uiState.update { it.copy(isEnqueuing = false) }
                refresh.update { it + 1 }
            }
        }
    }
}
