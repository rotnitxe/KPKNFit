package com.example.kpkn.screens.technique.importer

import android.app.Application
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.exercises.catalogExerciseIndex
import com.example.kpkn.data.exercises.initializeExerciseDatabase
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.models.WorkoutMediaKind
import com.example.kpkn.data.models.techniqueExerciseKey
import com.example.kpkn.data.repository.VideoAnalysisRepository
import com.example.kpkn.data.repository.WorkoutMediaRepository
import com.example.kpkn.screens.sessioneditor.CatalogLaunchOrigin
import com.example.kpkn.screens.sessioneditor.CatalogLaunchRequest
import com.example.kpkn.screens.sessioneditor.CatalogResult
import com.example.kpkn.screens.sessioneditor.CatalogSelectionMode
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ImportedVideoMetadata(
    val durationMs: Long,
    val width: Int? = null,
    val height: Int? = null,
    val fps: Double? = null,
    val recordedAtMs: Long? = null,
) {
    val warning: String? get() {
        val longEdge = maxOf(width ?: 0, height ?: 0)
        val shortEdge = minOf(width ?: 0, height ?: 0)
        return if (longEdge > 3840 || shortEdge > 2160 || (fps ?: 0.0) > 60.0)
            "Este vídeo supera 4K o 60 fps. El análisis puede tardar más y consumir más memoria." else null
    }
}

@Serializable
data class VideoImportExercise(
    val id: String,
    val name: String,
    val catalogDefinitionId: String? = null,
    val catalogConfigurationId: String? = null,
) {
    companion object {
        fun from(info: ExerciseMuscleInfo) = VideoImportExercise(info.id, info.name, info.catalogDefinitionId, info.catalogConfigurationId)
    }
}

data class VideoImportUiState(
    val isOpen: Boolean = false,
    val uri: String? = null,
    val pickerInFlight: Boolean = false,
    val isReadingMetadata: Boolean = false,
    val metadata: ImportedVideoMetadata? = null,
    val exercise: VideoImportExercise? = null,
    val weightText: String = "",
    val isImporting: Boolean = false,
    val importedMediaId: String? = null,
    val error: String? = null,
    val isSelectionLocked: Boolean = false,
    val videoError: String? = null,
) {
    val canAnalyze: Boolean get() = uri != null && metadata != null && exercise != null && !isReadingMetadata && !isImporting
}

/** Adaptador de importación; permite comprobar la semilla sin copiar medios reales. */
interface VideoImportSource {
    suspend fun metadata(uri: Uri): ImportedVideoMetadata
    suspend fun recover()
    suspend fun findMedia(id: String): WorkoutMedia?
    fun submitUriCapture(uri: Uri, seed: WorkoutMedia): Deferred<WorkoutMedia?>
    suspend fun requestAnalysis(mediaId: String): Boolean
    suspend fun resolveSelection(result: CatalogResult): VideoImportExercise?
    suspend fun resolveRecent(exerciseKey: String): VideoImportExercise?
}

private class RepositoryVideoImportSource(private val application: Application) : VideoImportSource {
    private val media = WorkoutMediaRepository.init(application)
    private val analysis = VideoAnalysisRepository.init(application)
    override suspend fun recover() { media.recoverPendingCaptures().await() }
    override suspend fun findMedia(id: String) = media.getById(id)
    override fun submitUriCapture(uri: Uri, seed: WorkoutMedia) = media.submitUriCapture(uri, seed)
    override suspend fun requestAnalysis(mediaId: String) = analysis.requestAnalysis(mediaId)
    override suspend fun resolveSelection(result: CatalogResult): VideoImportExercise? {
        initializeExerciseDatabase(application)
        return result.resolveSelectedInfos(catalogExerciseIndex()).singleOrNull()?.let(VideoImportExercise::from)
    }
    override suspend fun resolveRecent(exerciseKey: String): VideoImportExercise? {
        initializeExerciseDatabase(application)
        val latest = media.observeAll().first().filter { it.kind == WorkoutMediaKind.VIDEO && it.techniqueExerciseKey() == exerciseKey }.maxByOrNull { it.createdAtMs }
        val index = catalogExerciseIndex()
        val info = latest?.catalogConfigurationId?.let { index[it.lowercase()] }
            ?: latest?.exerciseId?.let { index[it.lowercase()] }
            ?: index[exerciseKey.lowercase()]
        return info?.let(VideoImportExercise::from)
    }
    override suspend fun metadata(uri: Uri): ImportedVideoMetadata {
        try {
            application.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // Algunos proveedores solo conceden acceso temporal; la copia usa esa concesión.
        }
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(application, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: throw IllegalArgumentException("No se pudo leer la duración del vídeo.")
            val rawDate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
            return ImportedVideoMetadata(
                durationMs = duration,
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull(),
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull(),
                fps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toDoubleOrNull(),
                recordedAtMs = parseVideoDate(rawDate),
            )
        } finally {
            retriever.release()
        }
    }
}

internal fun parseVideoDate(raw: String?): Long? {
    if (raw.isNullOrBlank()) return null
    val formats = listOf("yyyyMMdd'T'HHmmss.SSSX", "yyyyMMdd'T'HHmmssX", "yyyy-MM-dd'T'HH:mm:ss.SSSX", "yyyy-MM-dd'T'HH:mm:ssX")
    return formats.firstNotNullOfOrNull { pattern ->
        runCatching { SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC"); isLenient = false }.parse(raw)?.time }.getOrNull()
    }
}

/** La URI, la petición del catálogo y la semilla sobreviven a recreaciones; un UUID identifica todos los reintentos. */
class VideoImportViewModel @JvmOverloads constructor(
    application: Application,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    private val source: VideoImportSource = RepositoryVideoImportSource(application),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val nowMs: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private fun <T> decode(key: String, decode: (String) -> T): T? = savedState.get<String>(key)?.let { runCatching { decode(it) }.getOrNull() }
    private val _uiState = MutableStateFlow(VideoImportUiState(
        isOpen = savedState[OPEN] ?: false,
        uri = savedState[URI],
        pickerInFlight = savedState[PICKER] ?: false,
        metadata = decode(METADATA) { Json.decodeFromString<ImportedVideoMetadata>(it) },
        exercise = decode(EXERCISE) { Json.decodeFromString<VideoImportExercise>(it) },
        weightText = savedState[WEIGHT] ?: "",
        importedMediaId = savedState[COMPLETE],
        isSelectionLocked = savedState.get<String>(SEED) != null,
    ))
    val uiState: StateFlow<VideoImportUiState> = _uiState.asStateFlow()
    private var metadataJob: Job? = null
    private var importJob: Job? = null
    private var catalogJob: Job? = null

    init {
        val seed = decode(SEED) { Json.decodeFromString<WorkoutMedia>(it) }
        if (seed != null && _uiState.value.uri != null && _uiState.value.importedMediaId == null) startImport(seed)
        else if (_uiState.value.uri != null && _uiState.value.metadata == null) selectVideo(Uri.parse(_uiState.value.uri))
        decode(CATALOG_RESULT) { Json.decodeFromString<CatalogResult>(it) }?.let(::applyCatalogResult)
    }

    fun open() {
        savedState[OPEN] = true
        _uiState.update { it.copy(isOpen = true, error = it.videoError) }
    }
    fun dismiss() {
        if (_uiState.value.isImporting) return
        metadataJob?.cancel()
        catalogJob?.cancel()
        listOf(OPEN, URI, PICKER, METADATA, EXERCISE, WEIGHT, CATALOG, CATALOG_RESULT, SEED, COMPLETE).forEach { savedState.remove<Any>(it) }
        _uiState.value = VideoImportUiState()
    }
    fun pickerLaunched() {
        savedState[PICKER] = true
        _uiState.update { it.copy(pickerInFlight = true) }
    }
    fun selectVideo(uri: Uri?) {
        savedState[PICKER] = false
        if (uri == null) {
            _uiState.update { it.copy(pickerInFlight = false) }
            if (_uiState.value.uri == null) dismiss()
            return
        }
        if (_uiState.value.isImporting) return
        metadataJob?.cancel()
        savedState[URI] = uri.toString()
        savedState.remove<String>(METADATA)
        savedState.remove<String>(SEED)
        _uiState.update { it.copy(uri = uri.toString(), pickerInFlight = false, metadata = null, error = null, isReadingMetadata = true, isSelectionLocked = false, videoError = null) }
        metadataJob = viewModelScope.launch {
            try {
                val metadata = withContext(ioDispatcher) { source.metadata(uri) }
                require(metadata.durationMs > 0L) { "El vídeo no tiene una duración válida." }
                require(metadata.durationMs <= 600_000L) { "El vídeo debe durar 10 minutos como máximo." }
                savedState[METADATA] = Json.encodeToString(metadata)
                _uiState.update { it.copy(metadata = metadata, isReadingMetadata = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val message = error.message ?: "No se pudo abrir el vídeo. Elígelo de nuevo."
                _uiState.update { it.copy(isReadingMetadata = false, error = message, videoError = message) }
            }
        }
    }
    fun setWeight(raw: String) {
        if (_uiState.value.isImporting || _uiState.value.isSelectionLocked) return
        savedState[WEIGHT] = raw
        _uiState.update { it.copy(weightText = raw, error = it.videoError) }
    }
    fun chooseExercise(exercise: VideoImportExercise) {
        if (_uiState.value.isImporting || _uiState.value.isSelectionLocked) return
        savedState[EXERCISE] = Json.encodeToString(exercise)
        _uiState.update { it.copy(exercise = exercise, error = it.videoError) }
    }
    fun chooseRecent(exerciseKey: String) {
        if (_uiState.value.isImporting || _uiState.value.isSelectionLocked) return
        viewModelScope.launch {
            try {
                val exercise = withContext(ioDispatcher) { source.resolveRecent(exerciseKey) }
                if (exercise != null) chooseExercise(exercise)
                else _uiState.update { it.copy(error = "Busca este ejercicio en el catálogo para elegir su variante.") }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _uiState.update { it.copy(error = "No se pudo recuperar el ejercicio. Búscalo en el catálogo.") } }
        }
    }
    fun beginCatalogSelection(): CatalogLaunchRequest {
        val existing = decode(CATALOG) { Json.decodeFromString<CatalogLaunchRequest>(it) }
        return existing ?: CatalogLaunchRequest(origin = CatalogLaunchOrigin.TECHNIQUE, selectionMode = CatalogSelectionMode.SINGLE).also {
            savedState[CATALOG] = Json.encodeToString(it)
        }
    }
    fun applyCatalogResult(result: CatalogResult) {
        val request = decode(CATALOG) { Json.decodeFromString<CatalogLaunchRequest>(it) } ?: return
        if (!result.isValidFor(request)) return
        if (result.canceled) {
            savedState.remove<String>(CATALOG)
            savedState.remove<String>(CATALOG_RESULT)
            return
        }
        savedState[CATALOG_RESULT] = Json.encodeToString(result)
        if (catalogJob?.isActive == true) return
        catalogJob = viewModelScope.launch {
            try {
                val exercise = withContext(ioDispatcher) { source.resolveSelection(result) }
                if (exercise != null) {
                    chooseExercise(exercise)
                    savedState.remove<String>(CATALOG)
                    savedState.remove<String>(CATALOG_RESULT)
                } else _uiState.update { it.copy(error = "No se pudo recuperar esa variante. Elige el ejercicio de nuevo.") }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _uiState.update { it.copy(error = "No se pudo abrir el ejercicio. Vuelve a intentarlo.") } }
        }
    }
    fun analyze() {
        val state = _uiState.value
        if (!state.canAnalyze || importJob?.isActive == true) return
        val load = state.weightText.trim().replace(',', '.').takeIf { it.isNotBlank() }?.toDoubleOrNull()
        if (state.weightText.isNotBlank() && (load == null || !load.isFinite() || load < 0.0)) {
            _uiState.update { it.copy(error = "Introduce una carga válida en kg o deja el campo vacío.") }
            return
        }
        val exercise = state.exercise ?: return
        val metadata = state.metadata ?: return
        val seed = decode(SEED) { Json.decodeFromString<WorkoutMedia>(it) } ?: WorkoutMedia(
            id = UUID.randomUUID().toString(), kind = WorkoutMediaKind.VIDEO, filePath = "",
            createdAtMs = metadata.recordedAtMs ?: nowMs(), exerciseId = exercise.id,
            canonicalExerciseId = exercise.catalogDefinitionId ?: exercise.id, exerciseName = exercise.name,
            weightKg = load, durationMs = metadata.durationMs, width = metadata.width, height = metadata.height,
            catalogDefinitionId = exercise.catalogDefinitionId, catalogConfigurationId = exercise.catalogConfigurationId,
        ).also { savedState[SEED] = Json.encodeToString(it) }
        startImport(seed)
    }
    private fun startImport(seed: WorkoutMedia) {
        if (importJob?.isActive == true) return
        val uri = _uiState.value.uri?.let(Uri::parse) ?: return
        _uiState.update { it.copy(isImporting = true, error = null, isSelectionLocked = true) }
        importJob = viewModelScope.launch {
            try {
                val media = withContext(ioDispatcher) {
                    source.recover()
                    val imported = source.findMedia(seed.id) ?: source.submitUriCapture(uri, seed).await()
                    requireNotNull(imported) { "No se pudo guardar el vídeo. Vuelve a intentarlo." }
                    require(imported.kind == WorkoutMediaKind.VIDEO) { "El archivo seleccionado no es un vídeo." }
                    check(source.requestAnalysis(imported.id)) { "El vídeo se guardó, pero no se pudo iniciar el análisis. Vuelve a intentarlo." }
                    imported
                }
                savedState[COMPLETE] = media.id
                savedState.remove<String>(SEED)
                _uiState.update { it.copy(isImporting = false, importedMediaId = media.id) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update { it.copy(isImporting = false, error = error.message ?: "No se pudo importar el vídeo. Vuelve a intentarlo.") }
            }
        }
    }
    fun analysisOpened() {
        savedState.remove<String>(COMPLETE)
        dismiss()
    }

    companion object {
        private const val OPEN = "technique.import.open"
        private const val URI = "technique.import.uri"
        private const val PICKER = "technique.import.picker"
        private const val METADATA = "technique.import.metadata"
        private const val EXERCISE = "technique.import.exercise"
        private const val WEIGHT = "technique.import.weight"
        private const val CATALOG = "technique.import.catalog"
        private const val CATALOG_RESULT = "technique.import.catalog.result"
        private const val SEED = "technique.import.seed"
        private const val COMPLETE = "technique.import.complete"
    }
}
