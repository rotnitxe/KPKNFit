package com.example.kpkn.screens.technique.trends

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.media.technique.TechniqueSidecars
import com.example.kpkn.data.models.VideoAnalysisSummary
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.repository.VideoAnalysisRepository
import com.example.kpkn.data.repository.WorkoutMediaRepository
import com.example.kpkn.domain.biomechanics.metrics.DepthClass
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlin.math.sqrt

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

    // Leer y calcular solo cuando cambian las filas; seleccionar un vídeo no vuelve a abrir archivos.
    private val baseState = repository.observeTrend(exerciseKey)
        .mapLatest { summaries -> assemble(summaries) }
        .flowOn(ioDispatcher)
        .catch { error ->
            if (error is CancellationException) throw error
            emit(TechniqueTrendsUiState(exerciseKey = exerciseKey, isLoading = false, hasError = true))
        }

    val uiState: StateFlow<TechniqueTrendsUiState> = combine(
        baseState,
        isCompareMode,
        selectedMediaIds,
    ) { base, compareMode, selectedIds ->
        val existingIds = base.videos.mapTo(HashSet()) { it.mediaId }
        val validSelection = selectedIds.intersect(existingIds)
        base.copy(
            isCompareMode = compareMode,
            selectedMediaIds = validSelection,
            videos = base.videos.map { it.copy(isSelectedForCompare = it.mediaId in validSelection) },
        )
    }
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
    ): TechniqueTrendsUiState {
        if (summaries.isEmpty()) {
            return TechniqueTrendsUiState(
                exerciseKey = exerciseKey,
                exerciseName = exerciseKey,
                isLoading = false,
                isEmpty = true,
            )
        }

        val now = nowMs()
        val zone = ZoneId.systemDefault()
        val exerciseName = summaries.lastOrNull { !it.exerciseName.isNullOrBlank() }?.exerciseName ?: exerciseKey
        val mediaById = HashMap<String, WorkoutMedia?>()
        for (summary in summaries) {
            coroutineContext.ensureActive()
            mediaById[summary.mediaId] = mediaRepository.getById(summary.mediaId)
        }

        // 1. Módulo «Velocidad por carga»
        val loadVelocityState = buildLoadVelocityState(summaries, now)

        // 2. Módulo «Pérdida de velocidad» (D29: si todas son null, no se pinta)
        val lossSummaries = summaries.filter { it.velocityLossPct != null && it.velocityLossPct.isFinite() && it.velocityLossPct >= 0.0 }
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
            val items = buildRomSessions(romSummaries, mediaById, now, zone)
            val depthCaption = calculateDepthCaption(summaries)
            RomTrendUiState(items = items, depthCaption = depthCaption)
        }

        // 4. Módulo «TUT por serie»
        val tutSummaries = summaries.filter { it.tutMs != null && it.tutMs >= 0L }
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
            coroutineContext.ensureActive()
            val media = mediaById[summary.mediaId]
            val thumbFile = media?.thumbPath?.let { File(it) }?.takeIf { it.isFile }
                ?: media?.filePath?.let { File(it) }?.takeIf { it.isFile }
            val loadAndReps = formatLoadAndReps(summary, media)
            val detectedReps = summary.repCount?.let { "≈ $it reps detectadas" }

            AnalyzedVideoItemUi(
                mediaId = summary.mediaId,
                dateLabel = AnalysisFormat.shortDate(summary.recordedAtMs, now, zone),
                loadAndReps = loadAndReps,
                detectedReps = detectedReps,
                confidence = summary.confidence,
                thumbFile = thumbFile,
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
        )
    }

    private suspend fun buildLoadVelocityState(
        summaries: List<VideoAnalysisSummary>,
        nowMs: Long,
    ): LoadVelocityUiState? {
        // Misma configurationKey (la de la última serie realizada)
        val targetConfigKey = summaries.last().configurationKey?.takeIf { it.isNotBlank() } ?: return null
        val configSummaries = summaries.filter { it.configurationKey == targetConfigKey }

        // Últimas 8 semanas (56 días)
        val eightWeeksMs = 56L * 86_400_000L
        val recent = configSummaries.filter { nowMs - it.recordedAtMs in 0L..eightWeeksMs }

        // Puntos con carga y MCV
        val withLoads = recent.filter {
            it.loadKg != null && it.loadKg.isFinite() && it.loadKg > 0.0 &&
                it.mcvBest != null && it.mcvBest.isFinite() && it.mcvBest > 0.0
        }
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
                    LoadVelocityResult.NotEnough("no_mvt"),
                    "Este ejercicio permite comparar la velocidad medida, pero todavía no estimar el 1RM",
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
        // La MVT solo procede del ejercicio real guardado; un perfil sin MVT no admite 1RM.
        val sidecar = summary.reportPath?.let { File(it) }?.takeIf { it.isFile }?.let {
            TechniqueSidecars.readAnalysis(it)
        } ?: return null to null
        if (sidecar.exercise.configurationId != summary.configurationKey) return null to null
        val resolved = AnalysisProfileResolver.resolve(sidecar.exercise) as? ProfileResolution.Supported
        return resolved?.profile?.mvtMps?.takeIf { it.isFinite() && it > 0.0 } to resolved?.profile?.id
    }

    private suspend fun calculateDepthCaption(summaries: List<VideoAnalysisSummary>): String? {
        var totalDepthReps = 0
        var belowReps = 0
        var isSquat = false

        for (summary in summaries) {
            coroutineContext.ensureActive()
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

    private fun formatLoadAndReps(summary: VideoAnalysisSummary, media: WorkoutMedia?): String? {
        val load = (media?.weightKg ?: summary.loadKg)?.takeIf { it.isFinite() && it > 0.0 }
        val reps = media?.reps?.takeIf { it > 0 }
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

    /** Media y DE combinadas sobre las FULL de la sesión; importaciones sin sesión conservan su propio punto. */
    private fun buildRomSessions(
        summaries: List<VideoAnalysisSummary>,
        media: Map<String, WorkoutMedia?>,
        now: Long,
        zone: ZoneId,
    ): List<RomTrendItemUi> {
        fun sessionKey(summary: VideoAnalysisSummary): String? {
            if (summary.repCount == null || summary.repCount <= 0) return null
            val item = media[summary.mediaId]
            return item?.workoutLogId?.takeIf { it.isNotBlank() }?.let { "log:$it" }
                ?: item?.sessionKey?.takeIf { it.isNotBlank() }?.let { "session:$it" }
        }
        fun sd(summary: VideoAnalysisSummary): Double? = summary.romCvPct
            ?.takeIf { it.isFinite() && it >= 0.0 }?.let { summary.romMeanDeg!! * it / 100.0 }
        return summaries.groupBy { sessionKey(it) ?: "media:${it.mediaId}" }.values.map { group ->
            val time = group.maxOf { it.recordedAtMs }
            val n = group.sumOf { it.repCount?.takeIf { count -> count > 0 } ?: 1 }
            val mean = group.sumOf { it.romMeanDeg!! * (it.repCount?.takeIf { count -> count > 0 } ?: 1) } / n
            val combinedSd = if (group.size == 1) sd(group.single())
            else if (n <= 1 || group.any { (it.repCount ?: 0) > 1 && sd(it) == null }) null
            else {
                val sumSquares = group.sumOf {
                    val count = it.repCount!!
                    val offset = it.romMeanDeg!! - mean
                    (count - 1) * (sd(it) ?: 0.0).let { value -> value * value } + count * offset * offset
                }
                sqrt(sumSquares / (n - 1))
            }
            RomTrendItemUi(AnalysisFormat.shortDate(time, now, zone), mean, combinedSd, time, sessionKey(group.first()) != null)
        }.sortedBy { it.timestampMs }
    }
}
