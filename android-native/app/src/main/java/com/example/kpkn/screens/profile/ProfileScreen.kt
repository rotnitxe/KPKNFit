package com.example.kpkn.screens.profile

import android.app.Application
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.data.db.VideoAnalysisExerciseRow
import com.example.kpkn.data.models.Gender
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.VideoAnalysisSummary
import com.example.kpkn.data.models.WeightUnit
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.profile.ProfilePhotoStore
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.data.repository.VideoAnalysisRepository
import com.example.kpkn.data.repository.WorkoutMediaRepository
import com.example.kpkn.domain.training.NutritionGoalProgress
import com.example.kpkn.domain.training.StarredExerciseProgress
import com.example.kpkn.domain.training.buildNutritionGoalProgress
import com.example.kpkn.domain.training.buildStarredExerciseProgress
import com.example.kpkn.screens.competitions.ProfileCompetitionsArchive
import com.example.kpkn.screens.technique.hub.design.TechniqueRecentVideo
import com.example.kpkn.screens.technique.hub.design.techniqueConclusion
import com.example.kpkn.screens.technique.hub.design.techniqueDate
import com.example.kpkn.ui.components.LocalKpknOverlayHost
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknTopIsland
import com.example.kpkn.ui.tour.TourAnchors
import com.example.kpkn.ui.tour.TourIds
import com.example.kpkn.ui.tour.TourScripts
import com.example.kpkn.ui.tour.tourAnchor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ProfileUiState(
    val settings: Settings = Settings(),
    val starredExercises: List<StarredExerciseProgress> = emptyList(),
    val nutritionGoal: NutritionGoalProgress? = null,
    val technique: ProfileTechniqueUiState = ProfileTechniqueUiState(),
    val progressExercises: List<com.example.kpkn.domain.training.ExerciseProgressChoice> = emptyList(),
)

data class ProfileTechniqueUiState(
    val recentVideos: List<TechniqueRecentVideo> = emptyList(),
    val analyzedVideoCount: Int = 0,
    val lastRecordedAtMs: Long? = null,
    val latestConclusion: VideoAnalysisSummary? = null,
) {
    val summary: String get() =
        "$analyzedVideoCount ${if (analyzedVideoCount == 1) "vídeo analizado" else "vídeos analizados"}" +
            lastRecordedAtMs?.let { " · último el ${techniqueDate(it)}" }.orEmpty()

    val conclusion: String? get() = latestConclusion?.let {
        "${it.exerciseName?.takeIf(String::isNotBlank) ?: "Ejercicio sin nombre"} · ${techniqueConclusion(it)}"
    }
}

/** La conclusión sigue siendo la última terminada aunque los tres recientes aún estén en cola. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun observeProfileTechnique(
    recent: Flow<List<VideoAnalysisSummary>>,
    summaries: Flow<List<VideoAnalysisExerciseRow>>,
    media: Flow<List<WorkoutMedia>>,
    trend: (String) -> Flow<List<VideoAnalysisSummary>>,
): Flow<ProfileTechniqueUiState> = summaries.flatMapLatest { exercises ->
    val lastExercise = exercises.maxByOrNull { it.lastRecordedAtMs }
    val latest = lastExercise?.let { row ->
        trend(row.exerciseKey).map { videos -> videos.filter { it.isDone }.maxByOrNull { it.recordedAtMs } }
    } ?: flowOf<VideoAnalysisSummary?>(null)
    combine(recent, media, latest) { videos, allMedia, conclusion ->
        val mediaById = allMedia.associateBy { it.id }
        ProfileTechniqueUiState(
            recentVideos = videos.take(3).map { TechniqueRecentVideo(it, mediaById[it.mediaId]) },
            analyzedVideoCount = exercises.sumOf { it.videoCount },
            lastRecordedAtMs = lastExercise?.lastRecordedAtMs,
            latestConclusion = conclusion,
        )
    }
}

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val programRepository = ProgramRepository.getInstance()
    private val nutritionRepository = NutritionRepository.getInstance()
    private val analysisRepository = VideoAnalysisRepository.init(application)
    private val technique = observeProfileTechnique(
        analysisRepository.observeRecent(3),
        analysisRepository.observeExerciseSummaries(),
        WorkoutMediaRepository.init(application).observeAll(),
        analysisRepository::observeTrend,
    )

    private val activeNutritionPlan = nutritionRepository.nutritionPlans
        .combine(nutritionRepository.activeNutritionPlanId) { plans, activeId ->
            activeId?.let { id -> plans.firstOrNull { it.id == id } }
        }

    val uiState: StateFlow<ProfileUiState> = combine(
        programRepository.settings,
        programRepository.programs,
        programRepository.history,
        activeNutritionPlan,
        nutritionRepository.bodyProgressRepository.observations,
    ) { settings, programs, history, plan, observations ->
        ProfileUiState(
            settings = settings,
            starredExercises = buildStarredExerciseProgress(programs, history),
            progressExercises = com.example.kpkn.domain.training.ExerciseProgress.choices(history).take(4),
            nutritionGoal = buildNutritionGoalProgress(plan, observations),
        )
    }.combine(technique) { profile, videos ->
        profile.copy(technique = videos)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

    fun saveProfile(
        name: String,
        gender: Gender?,
        heightText: String,
        weightText: String,
    ): String? {
        val normalizedName = name.trim()
        if (normalizedName.isBlank()) return "Escribe un nombre para continuar."
        val current = programRepository.settings.value
        val height = parseOptionalPositive(heightText)
            ?: if (heightText.isBlank()) null else return "La estatura debe ser mayor que cero."
        val displayedWeight = parseOptionalPositive(weightText)
            ?: if (weightText.isBlank()) null else return "El peso debe ser mayor que cero."
        val weightKg = displayedWeight?.let { value ->
            if (current.weightUnit == WeightUnit.LBS) value / LB_PER_KG else value
        }
        programRepository.updateSettings { settings ->
            settings.copy(
                username = normalizedName,
                onboardingNameDone = true,
                userVitals = settings.userVitals.copy(
                    gender = gender,
                    height = height,
                    weight = weightKg,
                ),
            )
        }
        return null
    }

    fun savePhoto(context: android.content.Context, uri: android.net.Uri, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { ProfilePhotoStore.saveFromUri(context, uri) }
                .onSuccess { token ->
                    programRepository.updateSettings { it.copy(profilePicture = token) }
                }
                .onFailure { error ->
                    withContext(Dispatchers.Main) { onError(error.message ?: "No se pudo guardar la foto") }
                }
        }
    }

    fun removePhoto(context: android.content.Context) {
        viewModelScope.launch(Dispatchers.IO) {
            ProfilePhotoStore.delete(context)
            programRepository.updateSettings { it.copy(profilePicture = null) }
        }
    }

    private fun parseOptionalPositive(value: String): Double? {
        if (value.isBlank()) return null
        return value.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 }
    }

    companion object {
        private const val LB_PER_KG = 2.2046226218
    }
}

/** Hojas del perfil: elegir o quitar la foto y editar los datos. */
internal enum class ProfileSheet { PHOTO, EDIT }

/**
 * Perfil (propuesta aprobada `docs/design/profile-concepts-v1`): héroe con foto, nombre y ficha corporal; ejercicios estrella, meta
 * corporal, progreso, técnica, archivo y competiciones. Sin barra de Material: atrás y Ajustes flotan en vidrio y, al desplazar, la
 * isla superior muestra el nombre. La edición y la foto van en hojas del kit de la sesión en vivo.
 */
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    onOpenCompetition: (String) -> Unit = {},
    onCreateCompetition: () -> Unit = {},
    onOpenAlbums: () -> Unit = {},
    viewModel: ProfileViewModel = viewModel(),
    onOpenTechnique: () -> Unit = {},
    onOpenTechniqueAnalysis: (String) -> Unit = {},
    onOpenMuscleVolume: () -> Unit = {},
    onOpenHabits: () -> Unit = {},
    onOpenExerciseProgress: (String?) -> Unit = {},
) {
    // Recorrido de Perfil: la primera vez que se abre. El icono de ajustes y la competición están en esta pantalla.
    val tourHost = LocalKpknOverlayHost.current
    LaunchedEffect(Unit) { tourHost?.startTour(TourIds.PROFILE, TourScripts.profile) }
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    var sheet by remember { mutableStateOf<ProfileSheet?>(null) }

    val avatar: Bitmap? by produceState<Bitmap?>(initialValue = null, state.settings.profilePicture) {
        value = withContext(Dispatchers.IO) {
            ProfilePhotoStore.loadBitmap(context, state.settings.profilePicture)
        }
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        viewModel.savePhoto(context, uri) { error ->
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
        }
    }
    val choosePhoto = { photoPicker.launch(arrayOf("image/*")) }

    val scroll = rememberScrollState()
    val islandThreshold = with(LocalDensity.current) { 190.dp.toPx() }
    val scrolled by remember { derivedStateOf { scroll.value > islandThreshold } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .statusBarsPadding()
                .padding(horizontal = 18.dp),
        ) {
            Spacer(Modifier.height(60.dp))
            ProfileHero(
                settings = state.settings,
                avatar = avatar,
                onAvatar = { if (avatar != null) sheet = ProfileSheet.PHOTO else choosePhoto() },
                onEdit = { sheet = ProfileSheet.EDIT },
            )
            ProfileSection("Ejercicios estrella", KpknLineIcons.Star) {
                StarredExercisesModule(state.starredExercises, onOpenExerciseProgress)
            }
            ProfileSection("Progreso nutricional") { NutritionGoalModule(state.nutritionGoal) }
            ProfileSection("Progreso", KpknLineIcons.Layers) {
                ProfileProgressModule(onOpenMuscleVolume, onOpenExerciseProgress, state.progressExercises)
            }
            ProfileSection("Técnica en vídeo", KpknLineIcons.Camera) {
                ProfileTechniqueCard(state.technique, onOpenTechnique, onOpenTechniqueAnalysis)
            }
            ProfileSection("Tu archivo") { ProfileArchiveTiles(onOpenAlbums, onOpenHabits) }
            // El archivo de competiciones conserva su componente (su rediseño interno es de la propuesta Registros v1).
            Box(Modifier.padding(top = 30.dp).fillMaxWidth().tourAnchor(TourAnchors.PROFILE_COMPETICION)) {
                ProfileCompetitionsArchive(
                    onOpenCompetition = onOpenCompetition,
                    onCreateCompetition = onCreateCompetition,
                )
            }
            Spacer(Modifier.height(48.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }

        // Atrás y Ajustes flotan en vidrio hasta que aparece la isla con el nombre.
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 12.dp, end = 12.dp, top = 6.dp)
                .alpha(if (scrolled) 0f else 1f),
        ) {
            ProfileGlassButton(KpknLineIcons.ChevronLeft, "Volver", onBack, enabled = !scrolled)
            Spacer(Modifier.weight(1f))
            ProfileGlassButton(
                Icons.Outlined.Settings, "Ajustes", onNavigateToSettings, enabled = !scrolled,
                modifier = Modifier.tourAnchor(TourAnchors.PROFILE_AJUSTES),
            )
        }
        KpknTopIsland(
            visible = scrolled && sheet == null,
            title = profileDisplayName(state.settings) ?: "Perfil",
            subtitle = "Perfil",
            leading = { KpknIconButton(KpknLineIcons.ChevronLeft, "Volver", onBack) },
            actions = { KpknIconButton(Icons.Outlined.Settings, "Ajustes", onNavigateToSettings) },
        )
    }

    when (sheet) {
        ProfileSheet.PHOTO -> ProfilePhotoSheet(
            onChoose = choosePhoto,
            onRemove = { viewModel.removePhoto(context) },
            onDismiss = { sheet = null },
        )
        ProfileSheet.EDIT -> ProfileEditSheet(
            settings = state.settings,
            onSave = viewModel::saveProfile,
            onDismiss = { sheet = null },
        )
        null -> Unit
    }
}

/** Nombre a mostrar, o null si aún no hay (el valor por defecto «Usuario» no cuenta). */
internal fun profileDisplayName(settings: Settings): String? =
    settings.username.trim().takeUnless { it.isBlank() || it == "Usuario" }
