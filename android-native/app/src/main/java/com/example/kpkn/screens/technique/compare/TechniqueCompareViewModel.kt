package com.example.kpkn.screens.technique.compare

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.media.technique.TechniqueSidecars
import com.example.kpkn.data.media.video.VideoInfo
import com.example.kpkn.data.media.video.VideoInfoProbe
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.repository.VideoAnalysisRepository
import com.example.kpkn.data.repository.WorkoutMediaRepository
import com.example.kpkn.domain.biomechanics.bar.BarTrack
import com.example.kpkn.domain.biomechanics.bar.BarTrackCodec
import com.example.kpkn.domain.biomechanics.metrics.SetTechniqueReport
import com.example.kpkn.domain.biomechanics.pose.PoseTrackCodec
import com.example.kpkn.domain.biomechanics.pose.PoseTrackV2
import com.example.kpkn.screens.technique.analysis.AnalysisFormat
import com.example.kpkn.screens.technique.compare.design.BarPathPoint
import com.example.kpkn.screens.technique.player.TechniqueOverlayData
import java.io.File
import java.io.IOException
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Fila de la tabla de diferencias entre el vídeo A y el vídeo B. */
@Immutable
data class CompareMetricRowUi(
    val label: String,
    val valueA: String,
    val valueB: String,
    val diff: String,
)

/** Estado de la pantalla «Comparar dos vídeos» (VT9a). */
@Immutable
data class TechniqueCompareUiState(
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
    val labelA: String = "",
    val labelB: String = "",
    val fileA: File? = null,
    val fileB: File? = null,
    val overlayA: TechniqueOverlayData? = null,
    val overlayB: TechniqueOverlayData? = null,
    val pathA: List<BarPathPoint> = emptyList(),
    val pathB: List<BarPathPoint> = emptyList(),
    val isCalibrated: Boolean = false,
    val metricsTable: List<CompareMetricRowUi> = emptyList(),
)

/**
 * ViewModel de «Comparar dos vídeos» (VT9a).
 *
 * Carga en [ioDispatcher] los medios y sidecars de [mediaIdA] y [mediaIdB], construye los overlays
 * para ambos reproductores, calcula las trayectorias relativas superpuestas y la tabla de diferencias.
 */
class TechniqueCompareViewModel @JvmOverloads constructor(
    application: Application,
    private val mediaIdA: String,
    private val mediaIdB: String,
    private val analysis: VideoAnalysisRepository = VideoAnalysisRepository.init(application),
    private val mediaRepository: WorkoutMediaRepository = WorkoutMediaRepository.init(application),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(TechniqueCompareUiState(isLoading = true))
    val uiState: StateFlow<TechniqueCompareUiState> = _uiState.asStateFlow()

    init {
        loadComparison()
    }

    private fun loadComparison() {
        viewModelScope.launch {
            _uiState.value = TechniqueCompareUiState(isLoading = true)

            try {
                val mediaA = withContext(ioDispatcher) { mediaRepository.getById(mediaIdA) }
                val mediaB = withContext(ioDispatcher) { mediaRepository.getById(mediaIdB) }

                if (mediaA == null || mediaB == null) {
                    _uiState.value = TechniqueCompareUiState(isLoading = false, hasError = true)
                    return@launch
                }

                val fileA = File(mediaA.filePath)
                val fileB = File(mediaB.filePath)

                val sidecarA = withContext(ioDispatcher) {
                    TechniqueSidecars.readAnalysis(TechniqueSidecars.analysisFile(fileA))
                }
                val sidecarB = withContext(ioDispatcher) {
                    TechniqueSidecars.readAnalysis(TechniqueSidecars.analysisFile(fileB))
                }

                val trackA = withContext(ioDispatcher) {
                    readBytes(TechniqueSidecars.poseFile(fileA))?.let { PoseTrackCodec.decode(it) }
                }
                val trackB = withContext(ioDispatcher) {
                    readBytes(TechniqueSidecars.poseFile(fileB))?.let { PoseTrackCodec.decode(it) }
                }

                val barA = withContext(ioDispatcher) {
                    readBytes(TechniqueSidecars.barFile(fileA))?.let { BarTrackCodec.decode(it) }
                }
                val barB = withContext(ioDispatcher) {
                    readBytes(TechniqueSidecars.barFile(fileB))?.let { BarTrackCodec.decode(it) }
                }

                val infoA = withContext(ioDispatcher) { probe(fileA) }
                val infoB = withContext(ioDispatcher) { probe(fileB) }

                val reportA = sidecarA?.report
                val reportB = sidecarB?.report

                val overlayA = withContext(computeDispatcher) {
                    TechniqueOverlayData.from(trackA, barA, reportA, infoA?.displayWidth ?: 0, infoA?.displayHeight ?: 0)
                }
                val overlayB = withContext(computeDispatcher) {
                    TechniqueOverlayData.from(trackB, barB, reportB, infoB?.displayWidth ?: 0, infoB?.displayHeight ?: 0)
                }

                val isCalibrated = TechniqueComparisonData.calibratedScale(reportA) != null &&
                    TechniqueComparisonData.calibratedScale(reportB) != null

                val pathPointsA = withContext(computeDispatcher) { TechniqueComparisonData.extractPath(barA, reportA, isCalibrated) }
                val pathPointsB = withContext(computeDispatcher) { TechniqueComparisonData.extractPath(barB, reportB, isCalibrated) }

                val now = nowMs()
                val zone = ZoneId.systemDefault()
                val labelA = formatMediaLabel(mediaA, now, zone)
                val labelB = formatMediaLabel(mediaB, now, zone)

                val table = withContext(computeDispatcher) {
                    TechniqueComparisonData.differenceTable(reportA, reportB)
                }

                _uiState.value = TechniqueCompareUiState(
                    isLoading = false,
                    hasError = false,
                    labelA = labelA,
                    labelB = labelB,
                    fileA = withContext(ioDispatcher) { fileA.takeIf { it.isFile } },
                    fileB = withContext(ioDispatcher) { fileB.takeIf { it.isFile } },
                    overlayA = overlayA,
                    overlayB = overlayB,
                    pathA = pathPointsA,
                    pathB = pathPointsB,
                    isCalibrated = isCalibrated,
                    metricsTable = table,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.value = TechniqueCompareUiState(isLoading = false, hasError = true)
            }
        }
    }

    private fun formatMediaLabel(media: WorkoutMedia, nowMs: Long, zone: ZoneId): String {
        val date = AnalysisFormat.shortDate(media.createdAtMs, nowMs, zone)
        val set = media.setIndex?.let { "Serie $it" }
        val load = media.weightKg?.takeIf { it > 0.0 }
        val reps = media.reps?.takeIf { it > 0 }
        val loadReps = when {
            load != null && reps != null -> "${AnalysisFormat.kg(load)} × $reps"
            load != null -> AnalysisFormat.kg(load)
            reps != null -> "$reps reps"
            else -> null
        }
        val prefix = listOfNotNull(set, loadReps).joinToString(" · ").takeIf { it.isNotEmpty() }
        return if (prefix != null) "$prefix · $date" else date
    }

    private fun readBytes(file: File): ByteArray? {
        if (!file.isFile) return null
        return try {
            file.readBytes()
        } catch (_: IOException) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    private fun probe(file: File): VideoInfo? = try {
        VideoInfoProbe.probe(file)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    } catch (_: LinkageError) {
        null
    }
}
