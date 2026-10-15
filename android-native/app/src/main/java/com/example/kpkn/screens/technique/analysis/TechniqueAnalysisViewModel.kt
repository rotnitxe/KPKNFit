package com.example.kpkn.screens.technique.analysis

import android.app.Application
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.media.technique.AnalysisMode
import com.example.kpkn.data.media.technique.TechniqueSidecars
import com.example.kpkn.data.media.video.VideoFrameMath
import com.example.kpkn.data.media.video.VideoInfo
import com.example.kpkn.data.media.video.VideoInfoProbe
import com.example.kpkn.data.models.VideoAnalysisStatus
import com.example.kpkn.data.models.VideoAnalysisSummary
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.models.WorkoutMediaKind
import com.example.kpkn.data.models.techniqueExerciseKey
import com.example.kpkn.data.repository.CaptureOverrides
import com.example.kpkn.data.repository.VideoAnalysisRepository
import com.example.kpkn.data.repository.WorkoutMediaRepository
import com.example.kpkn.domain.biomechanics.bar.BarTrack
import com.example.kpkn.domain.biomechanics.bar.BarTrackCodec
import com.example.kpkn.domain.biomechanics.bar.PlateSeed
import com.example.kpkn.domain.biomechanics.metrics.TechniqueAnalysisEngine
import com.example.kpkn.domain.biomechanics.pose.PoseTrackCodec
import com.example.kpkn.domain.biomechanics.pose.PoseTrackV2
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileResolver
import com.example.kpkn.domain.biomechanics.profiles.ProfileResolution
import com.example.kpkn.screens.technique.player.TechniqueOverlayData
import java.io.File
import java.io.IOException
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Un fotograma del vídeo en el instante [atUs], del tamaño del fotograma de análisis ([width] × [height]): así las coordenadas del
 * disco (`PlateSeed`) son las del propio bitmap. Lo usan la miniatura del módulo del disco y el diálogo para marcarlo.
 */
class PlateFrame(val bitmap: Bitmap, val atUs: Long, val width: Int, val height: Int)

/**
 * El ViewModel de «Análisis de técnica» (VT7b). Combina el medio (`WorkoutMediaRepository`), la fila de análisis (`VideoAnalysisRepository`,
 * que es lo único que lee la interfaz del estado) y, en IO, los sidecars: `<id>.analysis.json` (el informe, fuente de verdad) y la traza de
 * pose y del disco, de los que sale la capa del reproductor. Todo eso llega ya redactado en [uiState].
 *
 * - **Estados:** sin fila → «sin analizar»; QUEUED y RUNNING → analizando (el avance va de 0 a 100 y nunca baja); UNSUPPORTED,
 *   NEEDS_PROFILE y FAILED, con su motivo; DONE → los resultados. Si el informe no se puede leer, «sin informe».
 * - **RECOMPUTE perezoso:** si la fila DONE que se abre tiene una `engineVersion` menor que la del motor, se pide un RECOMPUTE UNA vez por
 *   apertura y, sin pasar por las cifras viejas, el estado dice «Actualizando el análisis…» hasta que termina.
 * - **Acciones:** [reanalyze], [analyze], [chooseProfile], [confirmPlate], [savePlateSeed] y [requestPlateFrame]. Todas son peticiones
 *   explícitas del usuario: corren aunque el ajuste «Analizar la técnica de los vídeos» esté apagado (D31). Si una no se puede guardar,
 *   [TechniqueAnalysisUiState.notice] lo dice.
 * - **La repetición elegida no vive aquí:** es de `TechniquePlayerState.selectedRep` (la gráfica, los chips y «Ver» pasan por él).
 *
 * Los dispatchers y el reloj se pueden inyectar para las pruebas.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TechniqueAnalysisViewModel @JvmOverloads constructor(
    application: Application,
    private val mediaId: String,
    private val analysis: VideoAnalysisRepository = VideoAnalysisRepository.init(application),
    private val mediaRepository: WorkoutMediaRepository = WorkoutMediaRepository.init(application),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) : AndroidViewModel(application) {

    private sealed interface MediaLoad {
        data object Loading : MediaLoad
        data object Missing : MediaLoad
        data class Found(val media: WorkoutMedia) : MediaLoad
    }

    /** Lo que se lee del análisis terminado, listo para pintar. */
    private class DoneContent(
        val overlay: TechniqueOverlayData,
        val results: AnalysisResultsUi,
        val profileId: AnalysisProfileId,
    )

    private class CachedDone(
        val updatedAtMs: Long,
        val engineVersion: Int,
        val overrides: CaptureOverrides,
        val content: DoneContent,
    )

    private val media = MutableStateFlow<MediaLoad>(MediaLoad.Loading)
    private val notice = MutableStateFlow<String?>(null)
    private val _plateFrame = MutableStateFlow<PlateFrame?>(null)

    /** El fotograma del disco (a petición con [requestPlateFrame]); `null` hasta que se carga. */
    val plateFrame: StateFlow<PlateFrame?> = _plateFrame.asStateFlow()

    // El avance más alto visto en el análisis en curso: la barra nunca retrocede (un reintento vuelve a empezar de 0 en Room).
    private val progressFloor = AtomicInteger(0)

    // RECOMPUTE perezoso: se pide una sola vez por apertura y, mientras la petición no ha dejado la fila en QUEUED, la pantalla ya dice
    // «Actualizando…» (así no enseña un instante las cifras viejas).
    private val recomputeAsked = AtomicBoolean(false)

    @Volatile
    private var recomputeInFlight = false

    // La fila DONE vieja que provocó la petición: otra DONE distinta (la del recálculo, o cualquier otra) ya se enseña.
    @Volatile
    private var recomputeStaleStamp = -1L

    // Para volver a armar el estado sin que cambie la fila (cuando termina la petición de RECOMPUTE).
    private val refresh = MutableStateFlow(0)

    @Volatile
    private var cachedDone: CachedDone? = null

    private val assembled: Flow<TechniqueAnalysisUiState> = combine(
        media,
        analysis.observeByMedia(mediaId).distinctUntilChanged(),
        refresh,
    ) { loaded, row, _ -> loaded to row }
        .mapLatest { (loaded, row) -> assemble(loaded, row) }

    val uiState: StateFlow<TechniqueAnalysisUiState> = combine(assembled, notice) { state, message ->
        if (message == null) state else state.copy(notice = message)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TechniqueAnalysisUiState.Loading)

    init {
        viewModelScope.launch {
            val item = try {
                withContext(ioDispatcher) { mediaRepository.getById(mediaId) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            media.value = if (item != null && item.kind == WorkoutMediaKind.VIDEO) MediaLoad.Found(item) else MediaLoad.Missing
        }
    }

    // ── Acciones ──────────────────────────────────────────────────────────────────────────────────────────────────────────────

    /** «Reanalizar»: vuelve a decodificar el vídeo y a estimar la pose con la versión actual. */
    fun reanalyze() {
        launchAction("No se pudo pedir el análisis. Inténtalo de nuevo.") { analysis.reanalyze(mediaId) }
    }

    /** «Analizar»: el análisis completo de un vídeo que no tenía. */
    fun analyze() {
        launchAction("No se pudo pedir el análisis. Inténtalo de nuevo.") { analysis.requestAnalysis(mediaId, AnalysisMode.FULL) }
    }

    /** «Analizar» con el movimiento que el usuario eligió (`setUserProfileOverride`). */
    fun chooseProfile(profileId: AnalysisProfileId) {
        launchAction("No se pudo guardar el movimiento. Inténtalo de nuevo.") { analysis.setUserProfileOverride(mediaId, profileId) }
    }

    /**
     * «Confirmar» el disco que halló el detector, con su diámetro en mm: lo da por bueno (`userConfirmed`) y encola PLATE_ONLY. No hace
     * nada si el vídeo no tiene un disco hallado que confirmar.
     */
    fun confirmPlate(diameterMm: Int) {
        val seed = uiState.value.results?.plate?.circle ?: return
        savePlateSeed(seed.copy(userConfirmed = true), diameterMm)
    }

    /** Guarda el disco que el usuario marcó (en píxeles del fotograma de análisis) y su diámetro en mm, y encola PLATE_ONLY. */
    fun savePlateSeed(seed: PlateSeed, diameterMm: Int) {
        launchAction("No se pudo guardar el disco. Inténtalo de nuevo.") { analysis.setPlateSeed(mediaId, seed, diameterMm) }
    }

    /** Cierra el aviso de una acción que no se pudo guardar. */
    fun dismissNotice() {
        notice.value = null
    }

    /** Carga el fotograma del disco (el del disco hallado, o el del fondo de la 1.ª repetición) si todavía no está. */
    fun requestPlateFrame() {
        val state = uiState.value
        val plate = state.results?.plate ?: return
        val path = state.videoPath ?: return
        val current = _plateFrame.value
        if (current != null && current.atUs == plate.frameUs) return
        viewModelScope.launch {
            val frame = withContext(ioDispatcher) { loadFrame(File(path), plate.frameUs, plate.frameWidth, plate.frameHeight) }
            if (frame != null) _plateFrame.value = frame
        }
    }

    private fun launchAction(failure: String, block: suspend () -> Boolean) {
        viewModelScope.launch {
            val done = try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
            if (!done) notice.value = failure
        }
    }

    // ── RECOMPUTE perezoso ────────────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Si es la primera vez en esta apertura, pide el RECOMPUTE de una fila DONE con un motor viejo. `true` si lo pidió ahora. La
     * pantalla sigue en «Actualizando…» hasta que la fila deja de ser esa DONE (la petición la pone en QUEUED antes de volver) o la
     * petición falla.
     */
    private fun requestRecomputeOnce(stale: VideoAnalysisSummary): Boolean {
        if (!recomputeAsked.compareAndSet(false, true)) return false
        recomputeStaleStamp = stale.updatedAtMs
        recomputeInFlight = true
        viewModelScope.launch {
            val queued = try {
                analysis.requestAnalysis(mediaId, AnalysisMode.RECOMPUTE)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
            if (!queued) {
                // Sin RECOMPUTE se siguen enseñando las cifras que hay.
                recomputeInFlight = false
                refresh.value += 1
            }
        }
        return true
    }

    // ── Del medio y la fila al estado de la pantalla ──────────────────────────────────────────────────────────────────────────

    private suspend fun assemble(loaded: MediaLoad, row: VideoAnalysisSummary?): TechniqueAnalysisUiState {
        val item = when (loaded) {
            MediaLoad.Loading -> return TechniqueAnalysisUiState.Loading
            MediaLoad.Missing -> return TechniqueAnalysisUiState.MediaMissing
            is MediaLoad.Found -> loaded.media
        }
        val overrides = analysis.captureOverrides(item)
        val videoPath = withContext(ioDispatcher) { item.filePath.takeIf { File(it).isFile } }
        val base = TechniqueAnalysisUiState(
            phase = AnalysisPhase.READY,
            exerciseName = row?.exerciseName?.takeIf { it.isNotBlank() }
                ?: item.exerciseName?.takeIf { it.isNotBlank() }
                ?: "Ejercicio sin nombre",
            setInfo = setInfoOf(item, nowMs(), ZoneId.systemDefault()),
            videoPath = videoPath,
            exerciseKey = row?.exerciseKey ?: item.techniqueExerciseKey(),
            profileId = row?.profileId,
            // Un ejercicio propio (sin ids del catálogo) o un perfil que ya salió de una elección del usuario.
            canChooseMovement = (item.catalogDefinitionId.isNullOrBlank() && item.catalogConfigurationId.isNullOrBlank()) ||
                overrides.userProfileOverride != null,
        )
        if (row == null) {
            progressFloor.set(0)
            return base.copy(status = AnalysisStatusUi.NotAnalyzed)
        }
        // La fila ya no es la DONE vieja: el recálculo pedido está en marcha (o terminó) y manda lo que diga la fila.
        if (row.status != VideoAnalysisStatus.DONE) recomputeInFlight = false
        return when (row.status) {
            VideoAnalysisStatus.QUEUED, VideoAnalysisStatus.RUNNING -> {
                val pct = progressFloor.accumulateAndGet(clampPercent(row.progressPct)) { a, b -> max(a, b) }
                base.copy(
                    status = AnalysisStatusUi.Analyzing(
                        progressPct = pct,
                        updating = row.engineVersion < TechniqueAnalysisEngine.ENGINE_VERSION,
                        queued = row.status == VideoAnalysisStatus.QUEUED,
                    ),
                )
            }
            VideoAnalysisStatus.UNSUPPORTED -> {
                progressFloor.set(0)
                base.copy(status = AnalysisStatusUi.Unsupported(unsupportedText(row.errorCode)))
            }
            VideoAnalysisStatus.NEEDS_PROFILE -> {
                progressFloor.set(0)
                base.copy(status = AnalysisStatusUi.NeedsProfile(row.profileId))
            }
            VideoAnalysisStatus.FAILED -> {
                progressFloor.set(0)
                base.copy(status = AnalysisStatusUi.Failed(failureMessage(row.errorCode)))
            }
            VideoAnalysisStatus.DONE -> {
                // Un motor viejo: se recalcula (una vez por apertura) y, mientras tanto, no se enseñan las cifras viejas.
                if (row.engineVersion < TechniqueAnalysisEngine.ENGINE_VERSION) {
                    val waiting = requestRecomputeOnce(row) || (recomputeInFlight && row.updatedAtMs == recomputeStaleStamp)
                    if (waiting) return base.copy(status = AnalysisStatusUi.Analyzing(progressPct = 0, updating = true, queued = true))
                }
                progressFloor.set(0)
                val content = loadDone(item, row, overrides)
                if (content == null) {
                    base.copy(status = AnalysisStatusUi.ReportMissing)
                } else {
                    base.copy(
                        status = AnalysisStatusUi.Done,
                        overlay = content.overlay,
                        videoAspect = content.overlay.videoAspect,
                        results = content.results,
                        profileId = content.profileId,
                    )
                }
            }
        }
    }

    /**
     * Lee `<id>.analysis.json` (el informe) y las trazas de pose y del disco, y arma la capa y los módulos. `null` si el informe no
     * se puede leer. Lo ya leído se reutiliza mientras la fila y lo que decidió el usuario sigan igual.
     */
    private suspend fun loadDone(item: WorkoutMedia, row: VideoAnalysisSummary, overrides: CaptureOverrides): DoneContent? {
        cachedDone?.let { cached ->
            if (cached.updatedAtMs == row.updatedAtMs && cached.engineVersion == row.engineVersion && cached.overrides == overrides) {
                return cached.content
            }
        }
        val video = File(item.filePath)
        val sidecar = withContext(ioDispatcher) { TechniqueSidecars.readAnalysis(TechniqueSidecars.analysisFile(video)) } ?: return null
        val track: PoseTrackV2? = withContext(ioDispatcher) { readBytes(TechniqueSidecars.poseFile(video))?.let { PoseTrackCodec.decode(it) } }
        val bar: BarTrack? = withContext(ioDispatcher) { readBytes(TechniqueSidecars.barFile(video))?.let { BarTrackCodec.decode(it) } }
        val info: VideoInfo? = withContext(ioDispatcher) { probe(video) }
        val defaultDiameterMm = analysis.readSettings().techniqueVideo.plateDiameterMm
        val report = sidecar.report
        val content = withContext(computeDispatcher) {
            val overlay = TechniqueOverlayData.from(track, bar, report, info?.displayWidth ?: 0, info?.displayHeight ?: 0)
            val frame = analysisFrameSize(bar, track, info)
            val usesPlate = (AnalysisProfileResolver.resolve(sidecar.exercise) as? ProfileResolution.Supported)?.profile?.usesPlate == true
            DoneContent(
                overlay = overlay,
                results = buildResults(
                    report = report,
                    marks = overlay.reps,
                    bar = bar,
                    overrides = overrides,
                    usesPlate = usesPlate,
                    defaultDiameterMm = defaultDiameterMm,
                    frameWidth = frame.first,
                    frameHeight = frame.second,
                ),
                profileId = report.profileId,
            )
        }
        cachedDone = CachedDone(row.updatedAtMs, row.engineVersion, overrides, content)
        return content
    }

    /** Los bytes de un sidecar, o `null` si no existe o no se puede leer (un sidecar ilegible solo significa «sin dato»). */
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
        // Sin las bibliotecas nativas del códec (pruebas en la JVM) no hay tamaño del vídeo: se usa el de las trazas.
        null
    }

    /**
     * El tamaño del fotograma de análisis (en el que están las coordenadas del disco): el de la traza del disco o de la pose, o el que
     * saldría de decodificar el vídeo con el lado largo a 720. (0, 0) si no se sabe.
     */
    private fun analysisFrameSize(bar: BarTrack?, track: PoseTrackV2?, info: VideoInfo?): Pair<Int, Int> {
        if (bar != null && bar.frameWidth > 0 && bar.frameHeight > 0) return bar.frameWidth to bar.frameHeight
        if (track != null && track.frameWidth > 0 && track.frameHeight > 0) return track.frameWidth to track.frameHeight
        if (info != null && info.displayWidth > 0 && info.displayHeight > 0) {
            val size = VideoFrameMath.outputSize(info.displayWidth, info.displayHeight, ANALYSIS_LONG_EDGE)
            return size.width to size.height
        }
        return 0 to 0
    }

    /** El fotograma de [file] en [atUs], llevado al tamaño del fotograma de análisis (o, sin él, con el lado largo a 720). */
    private fun loadFrame(file: File, atUs: Long, frameWidth: Int, frameHeight: Int): PlateFrame? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val full = retriever.getFrameAtTime(atUs, MediaMetadataRetriever.OPTION_CLOSEST) ?: return null
            val width: Int
            val height: Int
            if (frameWidth > 0 && frameHeight > 0) {
                width = frameWidth
                height = frameHeight
            } else {
                val size = VideoFrameMath.outputSize(full.width, full.height, ANALYSIS_LONG_EDGE)
                width = size.width
                height = size.height
            }
            val scaled = if (full.width == width && full.height == height) full else Bitmap.createScaledBitmap(full, width, height, true)
            if (scaled !== full) full.recycle()
            PlateFrame(scaled, atUs, width, height)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } catch (_: LinkageError) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
                // Nada que hacer: el retriever ya no sirve.
            }
        }
    }

    private companion object {
        /** Cuánto espera el flujo de estado, sin nadie mirándolo, antes de soltar la fila de Room. */
        const val STOP_TIMEOUT_MS = 5_000L

        /** Lado largo del fotograma de análisis (`targetLongEdge` de la decodificación). */
        const val ANALYSIS_LONG_EDGE = 720
    }
}
