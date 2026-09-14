package com.example.kpkn.screens.albums

import android.app.Application
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toSettings
import com.example.kpkn.data.media.WorkoutAlbum
import com.example.kpkn.data.media.WorkoutAlbumGrouping
import com.example.kpkn.data.media.WorkoutMediaGallerySaver
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.repository.WorkoutMediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class WorkoutAlbumsUiState(
    val isLoading: Boolean = true,
    val albums: List<WorkoutAlbum> = emptyList(),
    val media: List<WorkoutMedia> = emptyList(),
    val prOnly: Boolean = false,
    val exerciseQuery: String = "",
    val exerciseOptions: List<String> = emptyList(),
)

class WorkoutAlbumsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = WorkoutMediaRepository.init(application)

    private val _prOnly = MutableStateFlow(false)
    private val _exerciseQuery = MutableStateFlow("")

    val uiState: StateFlow<WorkoutAlbumsUiState> = combine(
        repository.observeAll(),
        _prOnly,
        _exerciseQuery,
    ) { media, prOnly, query ->
        val filtered = WorkoutAlbumGrouping.filterMedia(media, prOnly, query)
        WorkoutAlbumsUiState(
            isLoading = false,
            albums = WorkoutAlbumGrouping.group(filtered),
            media = filtered,
            prOnly = prOnly,
            exerciseQuery = query,
            exerciseOptions = media.mapNotNull { it.exerciseName?.trim()?.takeIf { name -> name.isNotEmpty() } }
                .distinct()
                .sorted(),
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        WorkoutAlbumsUiState(),
    )

    init {
        viewModelScope.launch {
            repository.importLegacyIfNeeded()
        }
    }

    fun setPrOnly(value: Boolean) {
        _prOnly.value = value
    }

    fun setExerciseQuery(value: String) {
        _exerciseQuery.value = value
    }

    fun album(albumKey: String): WorkoutAlbum? =
        uiState.value.albums.firstOrNull { it.albumKey == albumKey }
            ?: WorkoutAlbumGrouping.group(uiState.value.media).firstOrNull { it.albumKey == albumKey }
}

class WorkoutMediaViewerViewModel(
    application: Application,
    private val mediaId: String,
) : AndroidViewModel(application) {
    private val repository = WorkoutMediaRepository.init(application)
    private val _media = MutableStateFlow<WorkoutMedia?>(null)
    val media: StateFlow<WorkoutMedia?> = _media.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()
    private val _poseOverlayEnabled = MutableStateFlow(false)
    val poseOverlayEnabled: StateFlow<Boolean> = _poseOverlayEnabled.asStateFlow()

    init {
        viewModelScope.launch {
            repository.importLegacyIfNeeded()
            _media.value = repository.getById(mediaId)
            _poseOverlayEnabled.value = withContext(Dispatchers.IO) {
                KpknDatabase.getInstance(getApplication())
                    .settingsDao()
                    .get()
                    ?.toSettings()
                    ?.workoutFeatureFlags
                    ?.poseTrajectoryEnabled == true
            }
        }
    }

    fun setCaption(caption: String) {
        viewModelScope.launch {
            val trimmed = caption.trim().ifBlank { null }
            repository.updateCaption(mediaId, trimmed)
            _media.value = repository.getById(mediaId)
        }
    }

    fun delete() {
        viewModelScope.launch {
            repository.delete(mediaId)
            _deleted.value = true
        }
    }

    fun saveToGallery() {
        viewModelScope.launch {
            val item = _media.value ?: repository.getById(mediaId) ?: return@launch
            val ok = WorkoutMediaGallerySaver.save(getApplication(), item)
            _message.value = if (ok) "Guardado en la galería" else "No se pudo guardar en la galería"
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun shareIntent(): Intent? {
        val item = _media.value ?: return null
        val file = File(item.filePath)
        if (!file.isFile) return null
        val context = getApplication<Application>()
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val mime = if (item.kind.name == "VIDEO") "video/*" else "image/*"
        return Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
