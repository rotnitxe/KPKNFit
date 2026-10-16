package com.example.kpkn.screens.technique.trends

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.media.technique.TechniqueSidecars
import com.example.kpkn.data.models.VideoAnalysisSummary
import com.example.kpkn.data.repository.VideoAnalysisRepository
import com.example.kpkn.data.repository.WorkoutMediaRepository
import com.example.kpkn.domain.biomechanics.metrics.DepthClass
import com.example.kpkn.domain.biomechanics.profiles.ExerciseRef
import com.example.kpkn.domain.biomechanics.metrics.RepKind
import com.example.kpkn.domain.biomechanics.metrics.VelocityUnit
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileResolver
import com.example.kpkn.domain.biomechanics.profiles.ProfileResolution
import com.example.kpkn.domain.biomechanics.vbt.LoadVelocityPoint
import com.example.kpkn.domain.biomechanics.vbt.LoadVelocityProfile
import com.example.kpkn.domain.biomechanics.vbt.LoadVelocityResult
import com.example.kpkn.screens.technique.analysis.AnalysisFormat
import java.io.File
import java.time.ZoneId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel de la pantalla «Tendencias por ejercicio» (VT9a).
 *
 * Lee Room en [ioDispatcher] mediante [VideoAnalysisRepository] y [WorkoutMediaRepository],
 * calcula la dispersión y regresión de carga-velocidad, y prepara los estados de las 4 gráficas
 * y la lista de vídeos para comparar.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TechniqueTrendsViewModel @JvmOverloads constructor(
    application: Application,
    private val exerciseKey: String,
    private val repository: VideoAnalysisRepository = VideoAnalysisRepository.init(application),
    private val mediaRepository: WorkoutMediaRepository = WorkoutMediaRepository.init(application),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) : AndroidViewModel(application) {

    private val isCompareMode = MutableStateFlow(false)
    private val selectedMediaIds = MutableStateFlow<Set<String>>(emptySet())

    val uiState: StateFlow<TechniqueTrendsUiState> = combine(
        repository.observeTrend(exerciseKey),
        isCompareMode,
        selectedMediaIds,
    ) { summaries, compareMode, selectedIds ->
        assemble(summaries, compareMode, selectedIds)
    }
        .flowOn(ioDispatcher)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = TechniqueTrendsUiState(exerciseKey = exerciseKey, isLoading = true),
        )

    fun toggleCompareMode() {
        val next = !isCompareMode.value
        isCompareMode.value = next
        if (!next) {
            selectedMediaIds.value = emptySet()
        }
    }

    fun toggleVideoSelection(mediaId: String) {
        val current = selectedMediaIds.value.toMutableSet()
        if (mediaId in current) {
            current.remove(mediaId)
        } else {
            if (current.size >= 2) {
                // Si ya hay 2 seleccionados, reemplazamos el último
                val list = current.toList()
                current.clear()
                current.add(list.first())
                current.add(mediaId)
            } else {
                current.add(mediaId)
            }
        }
        selectedMediaIds.value = current
    }

    fun clearSelection() {
        selectedMediaIds.value = emptySet()
    }

    // ── Construcción del estado ─────────────────────────────────────────────────────────────────

    private suspend fun assemble(
        summaries: List<VideoAnalysisSummary>,
        compareMode: Boolean,
        selectedIds: Set<String>,
    ): TechniqueTrendsUiState {
        if (summaries.isEmpty()) {
            return TechniqueTrendsUiState(
                exerciseKey = exerciseKey,
                exerciseName = exerciseKey,
                isLoading = false,
                isEmpty = true,
                isCompareMode = compareMode,
                selectedMediaIds = selectedIds,
            )
        }

        val now = nowMs()
        val zone = ZoneId.systemDefault()
        val latest = summaries.last()
        val exerciseName = summaries.lastOrNull { !it.exerciseName.isNullOrBlank() }?.exerciseName ?: exerciseKey

        // 1. Módulo «Velocidad por carga»
        val loadVelocityState = buildLoadVelocityState(summaries, now)

        // 2. Módulo «Pérdida de velocidad» (D29: si todas son null, no se pinta)
        val lossSummaries = summaries.filter { it.velocityLossPct != null && it.velocityLossPct.isFinite() }
        val velocityLossState = if (lossSummaries.isEmpty()) {
            null
        } else {
            val items = lossSummaries.map {
                VelocityLossItemUi(
                    mediaId = it.mediaId,
                    dateLabel = AnalysisFormat.shortDate(it.recordedAtMs, now, zone),
                    lossPct = it.velocityLossPct!!,
                )
            }
            VelocityLossUiState(
                items = items,
                meanLossPct = items.map { it.lossPct }.average(),
            )
        }

        // 3. Módulo «ROM y profundidad»
        val romSummaries = summaries.filter { it.romMeanDeg != null && it.romMeanDeg.isFinite() }
        val romTrendState = if (romSummaries.isEmpty()) {
            null
        } else {
            val items = romSummaries.map {
                val cv = it.romCvPct?.takeIf { pct -> pct.isFinite() && pct >= 0.0 }
                val sd = if (cv != null) it.romMeanDeg!! * (cv / 100.0) else null
                RomTrendItemUi(
                    dateLabel = AnalysisFormat.shortDate(it.recordedAtMs, now, zone),
                    romMeanDeg = it.romMeanDeg!!,
                    romSdDeg = sd,
                    timestampMs = it.recordedAtMs,
                )
            }
            val depthCaption = calculateDepthCaption(summaries)
            RomTrendUiState(items = items, depthCaption = depthCaption)
        }

        // 4. Módulo «TUT por serie»
        val tutSummaries = summaries.filter { it.tutMs != null && it.tutMs > 0L }
        val tutState = if (tutSummaries.isEmpty()) {
            null
        } else {
            val items = tutSummaries.map {
                TutItemUi(
                    mediaId = it.mediaId,
                    dateLabel = AnalysisFormat.shortDate(it.recordedAtMs, now, zone),
                    tutSeconds = (it.tutMs!!.toDouble() / 1000.0),
                )
            }
            TutUiState(
                items = items,
                maxSeconds = items.maxOf { it.tutSeconds },
            )
        }

        // 5. Lista de vídeos (más recientes primero)
        val videoItems = summaries.asReversed().map { summary ->
            val media = runCatching { mediaRepository.getById(summary.mediaId) }.getOrNull()
            val thumbFile = media?.thumbPath?.let { File(it) }?.takeIf { it.isFile }
                ?: media?.filePath?.let { File(it) }?.takeIf { it.isFile }
            val loadAndReps = formatLoadAndReps(summary)
            val detectedReps = "${summary.repCount ?: 0} reps"

            AnalyzedVideoItemUi(
                mediaId = summary.mediaId,
                dateLabel = AnalysisFormat.shortDate(summary.recordedAtMs, now, zone),
                loadAndReps = loadAndReps,
                detectedReps = detectedReps,
                confidence = summary.confidence,
                thumbFile = thumbFile,
                isSelectedForCompare = summary.mediaId in selectedIds,
            )
        }

        return TechniqueTrendsUiState(
            exerciseName = exerciseName,
            exerciseKey = exerciseKey,
            isLoading = false,
            isEmpty = false,
            loadVelocity = loadVelocityState,
            velocityLoss = velocityLossState,
            romTrend = romTrendState,
            tut = tutState,
            videos = videoItems,
            isCompareMode = compareMode,
            selectedMediaIds = selectedIds,
        )
    }

    private suspend fun buildLoadVelocityState(
        summaries: List<VideoAnalysisSummary>,
        nowMs: Long,
    ): LoadVelocityUiState? {
        // Misma configurationKey (la de la última serie realizada)
        val targetConfigKey = summaries.lastOrNull { !it.configurationKey.isNullOrBlank() }?.configurationKey
            ?: summaries.last().configurationKey

        val configSummaries = if (targetConfigKey != null) {
            summaries.filter { it.configurationKey == targetConfigKey }
        } else {
            summaries
        }

        // Últimas 8 semanas (56 días)
        val eightWeeksMs = 56L * 86_400_000L
        val recent = configSummaries.filter { nowMs - it.recordedAtMs <= eightWeeksMs }

        // Puntos con carga y MCV
        val withLoads = recent.filter { it.loadKg != null && it.loadKg > 0.0 && it.mcvBest != null && it.mcvBest > 0.0 }
        if (withLoads.isEmpty()) return null

        val hasMps = withLoads.any { it.velocityUnit == VelocityUnit.MPS }
        val mpsPoints = withLoads.filter { it.velocityUnit == VelocityUnit.MPS }

        val lvPoints = mpsPoints.map {
            LoadVelocityPoint(
                loadKg = it.loadKg!!,
                mcvMps = it.mcvBest!!,
                recordedAtMs = it.recordedAtMs,
            )
        }

        // Resolución del perfil y MVT
        val (mvtMps, profileId) = resolveProfileAndMvt(recent.last())

        val (result, reasonText) = when {
            !hasMps -> {
                Pair(
                    LoadVelocityResult.NotEnough("few_loads"),
                    "Calibra el disco en tus vídeos para ver la velocidad real",
                )
            }
            mvtMps == null -> {
                Pair(
                    LoadVelocityResult.NotEnough("implausible"),
                    translateNotEnough("implausible"),
                )
            }
            else -> {
                val heaviestMax = profileId?.let { LoadVelocityProfile.heaviestMaxMps(it) }
                val res = LoadVelocityProfile.estimate(lvPoints, mvtMps, heaviestMax)
                val text = when (res) {
                    is LoadVelocityResult.NotEnough -> translateNotEnough(res.reason)
                    is LoadVelocityResult.Estimate -> null
                }
                Pair(res, text)
            }
        }

        return LoadVelocityUiState(
            points = lvPoints,
            result = result,
            mvtMps = mvtMps,
            hasMpsPoints = hasMps,
            reasonText = reasonText,
        )
    }

    private fun resolveProfileAndMvt(summary: VideoAnalysisSummary): Pair<Double?, AnalysisProfileId?> {
        // Intentar leer el sidecar del disco
        val sidecar = summary.reportPath?.let { File(it) }?.takeIf { it.isFile }?.let {
            TechniqueSidecars.readAnalysis(it)
        }
        val canonicalDef = when (summary.exerciseKey) {
            "back_squat" -> "high_bar_back_squat"
            "deadlift" -> "conventional_deadlift"
            "bench_press" -> "bench_press"
            "ohp" -> "military_press"
            "barbell_row" -> "seal_row"
            else -> summary.exerciseKey
        }
        val canonicalPattern = when {
            summary.exerciseKey.contains("squat") -> "knee_dominant"
            summary.exerciseKey.contains("bench") -> "horizontal_push"
            summary.exerciseKey.contains("deadlift") -> "deadlift"
            summary.exerciseKey.contains("press") || summary.exerciseKey.contains("ohp") -> "vertical_push"
            summary.exerciseKey.contains("row") -> "horizontal_pull"
            else -> null
        }
        val equipment = if (summary.configurationKey?.contains("barbell") != false) "barbell" else summary.configurationKey
        val exerciseRef = sidecar?.exercise ?: ExerciseRef(
            definitionId = canonicalDef,
            configurationId = summary.configurationKey,
            movementPatternId = canonicalPattern,
            equipmentId = equipment,
            laterality = null,
            name = summary.exerciseName.orEmpty(),
            isCustom = false,
        )

        val resolved = AnalysisProfileResolver.resolve(exerciseRef) as? ProfileResolution.Supported
        val mvt = resolved?.profile?.mvtMps
            ?: when {
                summary.exerciseKey.contains("squat") || summary.profileId == AnalysisProfileId.SQUAT -> 0.30
                summary.exerciseKey.contains("bench") || summary.profileId == AnalysisProfileId.BENCH -> 0.17
                summary.exerciseKey.contains("deadlift") || summary.profileId == AnalysisProfileId.DEADLIFT -> 0.15
                summary.exerciseKey.contains("press") || summary.exerciseKey.contains("ohp") || summary.profileId == AnalysisProfileId.OHP -> 0.19
                summary.exerciseKey.contains("row") || summary.profileId == AnalysisProfileId.ROW -> 0.50
                else -> null
            }
        val profileId = resolved?.profile?.id ?: summary.profileId
            ?: when {
                summary.exerciseKey.contains("squat") -> AnalysisProfileId.SQUAT
                summary.exerciseKey.contains("bench") -> AnalysisProfileId.BENCH
                summary.exerciseKey.contains("deadlift") -> AnalysisProfileId.DEADLIFT
                summary.exerciseKey.contains("press") || summary.exerciseKey.contains("ohp") -> AnalysisProfileId.OHP
                summary.exerciseKey.contains("row") -> AnalysisProfileId.ROW
                else -> null
            }

        return Pair(mvt, profileId)
    }

    private fun calculateDepthCaption(summaries: List<VideoAnalysisSummary>): String? {
        var totalDepthReps = 0
        var belowReps = 0
        var isSquat = false

        for (summary in summaries) {
            if (summary.profileId == AnalysisProfileId.SQUAT || summary.exerciseKey.contains("squat")) isSquat = true
            val sidecar = summary.reportPath?.let { File(it) }?.takeIf { it.isFile }?.let {
                TechniqueSidecars.readAnalysis(it)
            }
            if (sidecar != null) {
                if (sidecar.report.profileId == AnalysisProfileId.SQUAT) isSquat = true
                for (rep in sidecar.report.reps) {
                    if (rep.kind == RepKind.FULL && rep.depthClass != null) {
                        totalDepthReps++
                        if (rep.depthClass == DepthClass.BELOW) {
                            belowReps++
                        }
                    }
                }
            }
        }

        if (!isSquat || totalDepthReps == 0) return null
        return "Bajo el paralelo en ≈ $belowReps de $totalDepthReps repeticiones"
    }

    private fun formatLoadAndReps(summary: VideoAnalysisSummary): String? {
        val load = summary.loadKg?.takeIf { it > 0.0 }
        val reps = summary.repCount?.takeIf { it > 0 }
        return when {
            load != null && reps != null -> "${AnalysisFormat.kg(load)} × $reps"
            load != null -> AnalysisFormat.kg(load)
            reps != null -> "$reps reps"
            else -> null
        }
    }

    companion object {
        fun translateNotEnough(reason: String): String = when (reason) {
            "few_loads" -> "Graba series con al menos 3 cargas distintas"
            "narrow_range" -> "Las cargas están muy juntas: amplía el rango"
            "low_velocity_spread" -> "La velocidad apenas cambia entre cargas"
            "heaviest_too_fast" -> "Falta una serie pesada (más lenta)"
            "positive_slope" -> "Los datos no siguen la tendencia esperada"
            "low_r2" -> "Los datos están muy dispersos"
            "implausible" -> "La estimación no es creíble con estos datos"
            "wide_interval" -> "Hace falta más precisión: graba más series"
            else -> "Faltan datos"
        }
    }
}
