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
import com.example.kpkn.domain.biomechanics.metrics.MetricKey
import com.example.kpkn.domain.biomechanics.metrics.SetTechniqueReport
import com.example.kpkn.domain.biomechanics.metrics.VelocityUnit
import com.example.kpkn.domain.biomechanics.pose.PoseTrackCodec
import com.example.kpkn.domain.biomechanics.pose.PoseTrackV2
import com.example.kpkn.screens.technique.analysis.AnalysisFormat
import com.example.kpkn.screens.technique.compare.design.BarPathPoint
import com.example.kpkn.screens.technique.player.TechniqueOverlayData
import java.io.File
import java.io.IOException
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt
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

                val isCalibrated = (reportA?.velocityUnit == VelocityUnit.MPS || MetricKey.PATH_ABSOLUTE in (reportA?.available ?: emptySet())) &&
                    (reportB?.velocityUnit == VelocityUnit.MPS || MetricKey.PATH_ABSOLUTE in (reportB?.available ?: emptySet()))

                val pathPointsA = withContext(computeDispatcher) { extractPath(barA, reportA, isCalibrated) }
                val pathPointsB = withContext(computeDispatcher) { extractPath(barB, reportB, isCalibrated) }

                val now = nowMs()
                val zone = ZoneId.systemDefault()
                val labelA = formatMediaLabel(mediaA, now, zone)
                val labelB = formatMediaLabel(mediaB, now, zone)

                val table = withContext(computeDispatcher) {
                    buildDifferenceTable(reportA, reportB, mediaA, mediaB)
                }

                _uiState.value = TechniqueCompareUiState(
                    isLoading = false,
                    hasError = false,
                    labelA = labelA,
                    labelB = labelB,
                    fileA = fileA.takeIf { it.isFile },
                    fileB = fileB.takeIf { it.isFile },
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

    private fun extractPath(bar: BarTrack?, report: SetTechniqueReport?, isCalibrated: Boolean): List<BarPathPoint> {
        val samples = bar?.samples ?: return emptyList()
        if (samples.isEmpty()) return emptyList()

        val x0 = samples.first().cx
        val y0 = samples.first().cy

        // Factor de escala: si calibrado, convertir a cm utilizando el diámetro conocido
        val scale = if (isCalibrated && bar.plateDiameterMm != null && bar.plateDiameterMm > 0) {
            val validR = samples.map { it.r.toDouble() }.filter { it > 0.0 }
            val avgR = if (validR.isNotEmpty()) validR.average() else 1.0
            (bar.plateDiameterMm.toDouble() / 10.0) / (2.0 * avgR) // cm por píxel
        } else {
            val romPx = report?.romMeanDeg ?: 100.0
            100.0 / maxOf(10.0, romPx)
        }

        return samples.map { s ->
            val dx = ((s.cx - x0) * scale).toFloat()
            val dy = ((s.cy - y0) * scale).toFloat()
            BarPathPoint(dx, dy)
        }
    }

    private fun buildDifferenceTable(
        reportA: SetTechniqueReport?,
        reportB: SetTechniqueReport?,
        mediaA: WorkoutMedia,
        mediaB: WorkoutMedia,
    ): List<CompareMetricRowUi> {
        val rows = ArrayList<CompareMetricRowUi>(5)

        // 1. Repeticiones
        val repsA = reportA?.repCount ?: mediaA.reps
        val repsB = reportB?.repCount ?: mediaB.reps
        if (repsA != null && repsB != null) {
            val diff = repsB - repsA
            val diffStr = if (diff > 0) "+$diff" else "$diff"
            rows.add(CompareMetricRowUi("Repeticiones", repsA.toString(), repsB.toString(), diffStr))
        }

        // 2. TUT (Tiempo bajo tensión)
        val tutA = reportA?.tutMs?.let { it / 1000.0 } ?: mediaA.durationMs?.let { it / 1000.0 }
        val tutB = reportB?.tutMs?.let { it / 1000.0 } ?: mediaB.durationMs?.let { it / 1000.0 }
        if (tutA != null && tutB != null) {
            val diff = tutB - tutA
            val sign = if (diff > 0.0) "+" else ""
            rows.add(
                CompareMetricRowUi(
                    "TUT",
                    "${AnalysisFormat.decimal(tutA, 1)} s",
                    "${AnalysisFormat.decimal(tutB, 1)} s",
                    "$sign${AnalysisFormat.decimal(diff, 1)} s",
                ),
            )
        }

        // 3. ROM
        val romA = reportA?.romMeanDeg
        val romB = reportB?.romMeanDeg
        if (romA != null && romB != null) {
            val diff = (romB - romA).roundToInt()
            val sign = if (diff > 0) "+" else ""
            rows.add(
                CompareMetricRowUi(
                    "ROM",
                    "${romA.roundToInt()}°",
                    "${romB.roundToInt()}°",
                    "$sign$diff°",
                ),
            )
        }

        // 4. Pérdida de velocidad
        val lossA = reportA?.velocityLossPct
        val lossB = reportB?.velocityLossPct
        if (lossA != null && lossB != null) {
            val diff = (lossB - lossA).roundToInt()
            val sign = if (diff > 0) "+" else ""
            rows.add(
                CompareMetricRowUi(
                    "Pérdida de velocidad",
                    "${lossA.roundToInt()} %",
                    "${lossB.roundToInt()} %",
                    "$sign$diff %",
                ),
            )
        }

        // 5. MCV (Velocidad concéntrica media)
        val mcvA = reportA?.mcvBest
        val mcvB = reportB?.mcvBest
        if (mcvA != null && mcvB != null) {
            val isMps = reportA?.velocityUnit == VelocityUnit.MPS && reportB?.velocityUnit == VelocityUnit.MPS
            val unit = if (isMps) "m/s" else "%"
            val diff = mcvB - mcvA
            val sign = if (diff > 0.0) "+" else ""
            rows.add(
                CompareMetricRowUi(
                    "MCV (mejor)",
                    "≈ ${AnalysisFormat.decimal(mcvA, 2)} $unit",
                    "≈ ${AnalysisFormat.decimal(mcvB, 2)} $unit",
                    "$sign${AnalysisFormat.decimal(diff, 2)} $unit",
                ),
            )
        }

        return rows
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
