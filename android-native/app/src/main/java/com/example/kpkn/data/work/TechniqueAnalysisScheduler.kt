package com.example.kpkn.data.work

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import androidx.work.workDataOf
import com.example.kpkn.data.media.technique.AnalysisMode
import com.example.kpkn.data.media.technique.CaptureMetadataFile
import com.example.kpkn.data.media.technique.TechniqueSidecars
import com.example.kpkn.data.models.VideoAnalysisStatus
import com.example.kpkn.data.repository.ReconcileResult
import com.example.kpkn.data.repository.VideoAnalysisRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import java.io.File
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.ConcurrentHashMap

/**
 * La cola del análisis de técnica en vídeo (§9.4): encola, cancela y reconcilia los [TechniqueAnalysisWorker].
 *
 * Un trabajo único por vídeo (`technique-analysis:<id>`), así que nunca hay dos análisis del mismo medio a la vez:
 * - **FULL** (el vídeo recién grabado o importado, «Reanalizar», elegir perfil): con `userInitiated`, urgente
 *   (`setExpedited(RUN_AS_NON_EXPEDITED_WORK_REQUEST)`: si no queda cuota se ejecuta como un trabajo normal). Política KEEP: si ya
 *   hay un FULL pendiente o en curso no se toca nada. Un RECOMPUTE o un PLATE_ONLY pendiente no lo deja sin efecto: un FULL los
 *   contiene, así que se cancelan antes de encolarlo. Sin `userInitiated` (por lotes) no es urgente y pide batería no baja.
 * - **RECOMPUTE** (cambió el motor o el ejercicio): con batería no baja; REPLACE.
 * - **PLATE_ONLY** (el usuario fijó el disco): REPLACE.
 *
 * Antes de encolar, la fila pasa a QUEUED (la interfaz solo lee Room). Con un fallo transitorio el Worker se reintenta con
 * espera exponencial desde [BACKOFF_SECONDS] s.
 *
 * `explicit` separa lo que pidió el usuario de lo automático. Lo AUTOMÁTICO (el que encola `WorkoutMediaRepository` al guardar
 * un vídeo y la reconciliación) se descarta con el ajuste «Analizar la técnica de los vídeos» apagado; lo EXPLÍCITO (lo que
 * encola `VideoAnalysisRepository`) corre siempre. No es lo mismo que `userInitiated`, que solo decide si el trabajo es urgente.
 */
object TechniqueAnalysisScheduler {
    const val UNIQUE_PREFIX = "technique-analysis:"

    /** Etiqueta de todos los trabajos de análisis. */
    const val TAG_ALL = "technique-analysis"

    /** Prefijo de la etiqueta con el id del medio (`technique-analysis-media:<id>`): con ella se sabe de quién es un trabajo. */
    const val TAG_MEDIA_PREFIX = "technique-analysis-media:"

    /** Prefijo de la etiqueta con el modo (`technique-analysis-mode:FULL`): con ella se sabe qué hace un trabajo pendiente. */
    const val TAG_MODE_PREFIX = "technique-analysis-mode:"

    /** Prefijo de la etiqueta con el id de una solicitud manual durable. */
    const val TAG_MANUAL_REQUEST_PREFIX = "technique-analysis-manual-request:"

    /** Prefijo de la etiqueta con la generación durable de cualquier trabajo, automático o manual. */
    const val TAG_GENERATION_PREFIX = "technique-analysis-generation:"

    /** Espera del primer reintento tras un fallo transitorio; WorkManager la dobla en cada intento. */
    const val BACKOFF_SECONDS = 30L

    private const val TAG = "TechniqueScheduler"

    private val reconcileLock = Mutex()
    private val enqueueLocks = ConcurrentHashMap<String, Mutex>()
    private val backgroundReconcileStarted = AtomicBoolean(false)
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    internal var beforeEnqueueCommitForTests: suspend (String, String) -> Unit = { _, _ -> }

    internal fun resetEnqueueHooksForTests() {
        beforeEnqueueCommitForTests = { _, _ -> }
    }

    /** Nombre del trabajo único del análisis de [mediaId]. */
    fun uniqueName(mediaId: String): String = UNIQUE_PREFIX + mediaId

    /**
     * Pone la fila de [mediaId] en QUEUED y encola su análisis. `false` si el medio ya no existe o no es un vídeo.
     * Con [AnalysisMode.FULL] y un análisis ya pendiente o en curso no hace nada (política KEEP) y devuelve `true`.
     * [imported] avisa al Worker de que el vídeo viene de la galería (se analiza en primer plano, igual que uno largo).
     * [explicit] dice que lo pidió el usuario: corre aunque el ajuste «Analizar la técnica de los vídeos» esté apagado; el
     * automático (el valor por defecto) se descarta con el ajuste apagado.
     */
    suspend fun enqueue(
        context: Context,
        mediaId: String,
        mode: AnalysisMode = AnalysisMode.FULL,
        userInitiated: Boolean = true,
        imported: Boolean = false,
        explicit: Boolean = false,
        manualRequestId: String? = null,
        repository: VideoAnalysisRepository = VideoAnalysisRepository.init(context),
    ): Boolean = enqueueLocks.computeIfAbsent(mediaId) { Mutex() }.withLock {
        val app = context.applicationContext
        if (repository.videoOf(mediaId) == null) return false
        val workManager = WorkManager.getInstance(app)
        val name = uniqueName(mediaId)
        val currentGeneration = if (manualRequestId == null) repository.currentAnalysisGeneration(mediaId) else null
        val pending = unfinishedWork(workManager, name)
        if (manualRequestId == null && currentGeneration?.manualRequestPending == true) {
            // A durable user request owns this media until it is fulfilled; an automatic capture/reconcile must not replace it.
            return pending.any { TAG_GENERATION_PREFIX + currentGeneration.id in it.tags }
        }
        val representedGenerationId = manualRequestId ?: currentGeneration?.id
        if (pending.isNotEmpty()) {
            val sameMode = pending.all { TAG_MODE_PREFIX + mode.name in it.tags }
            val generationAlreadyRepresented = representedGenerationId != null && pending.any {
                TAG_GENERATION_PREFIX + representedGenerationId in it.tags
            }
            if (sameMode && generationAlreadyRepresented && (manualRequestId != null || mode == AnalysisMode.FULL)) {
                return true
            }
        }
        val generationId = manualRequestId ?: repository.beginAnalysisGeneration(mediaId, mode) ?: return false
        beforeEnqueueCommitForTests(mediaId, generationId)
        repository.withCurrentAnalysisGeneration(mediaId, generationId) {
            // Keep cancellation, the Room transition, and WorkManager's unique enqueue in one generation fence.
            // A newer manual tap may replace the durable token while generation creation is otherwise in flight.
            if (pending.isNotEmpty()) {
                workManager.cancelUniqueWork(name).await()
            }
            if (!repository.markState(mediaId, VideoAnalysisStatus.QUEUED)) return@withCurrentAnalysisGeneration false
            workManager.enqueueUniqueWork(
                name,
                policyFor(mode),
                requestFor(mediaId, mode, userInitiated, imported, explicit, manualRequestId, generationId),
            ).await()
            true
        } ?: false
    }

    /** Cancela el análisis de [mediaId], esté pendiente o en curso (al borrar el medio). No toca la fila. */
    fun cancel(context: Context, mediaId: String) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(uniqueName(mediaId))
    }

    /**
     * Pone la caché de análisis de acuerdo con los archivos y la cola ([VideoAnalysisRepository.reconcile]) y vuelve a
     * encolar las filas que decían QUEUED o RUNNING sin ningún trabajo vivo (el trabajo se perdió: una copia restaurada, un
     * borrado de datos de WorkManager). Con el análisis automático apagado no se reencola nada: a esas filas se les hace lo que
     * haría el Worker al descartar un automático ([VideoAnalysisRepository.restoreOrDrop]: DONE desde su sidecar, o borrarlas).
     * Las filas con un `engineVersion` anterior al actual no se tocan: su RECOMPUTE se lanza al abrir el análisis. Dos llamadas
     * a la vez se reparten el trabajo: la segunda no hace nada.
     */
    suspend fun reconcile(
        context: Context,
        repository: VideoAnalysisRepository = VideoAnalysisRepository.init(context),
    ): ReconcileResult {
        if (!reconcileLock.tryLock()) return ReconcileResult()
        try {
            val base = repository.reconcile()
            val stuck = settleStuckRows(context.applicationContext, repository)
            return base.copy(
                rebuiltRows = base.rebuiltRows + stuck.rebuiltRows,
                removedRows = base.removedRows + stuck.removedRows,
                requeued = stuck.requeued,
            )
        } finally {
            reconcileLock.unlock()
        }
    }

    /**
     * [reconcile] sin bloquear a quien llama: en un ámbito propio del proceso y solo en el proceso principal (el `:voice` no
     * toca los medios). Corre UNA vez por proceso: `MainActivity` se recrea (al girar la pantalla, al cambiar el idioma…) y
     * `AppBootstrap` también la llama, y las demás llamadas no hacen nada. Un fallo se anota y no cae a nadie. Devuelve el
     * trabajo, o `null` si este no es el proceso principal o ya se lanzó en este proceso.
     */
    fun reconcileInBackground(context: Context): Job? {
        val app = context.applicationContext
        if (!isMainProcess(app)) return null
        if (!backgroundReconcileStarted.compareAndSet(false, true)) return null
        return backgroundScope.launch {
            try {
                reconcile(app)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "No se pudo reconciliar el análisis de técnica.", error)
            }
        }
    }

    /** Pruebas: el proceso vuelve a poder lanzar [reconcileInBackground]. */
    internal fun resetBackgroundReconcileForTests() {
        backgroundReconcileStarted.set(false)
    }

    internal fun policyFor(mode: AnalysisMode): ExistingWorkPolicy =
        if (mode == AnalysisMode.FULL) ExistingWorkPolicy.KEEP else ExistingWorkPolicy.REPLACE

    internal fun requestFor(
        mediaId: String,
        mode: AnalysisMode,
        userInitiated: Boolean,
        imported: Boolean = false,
        explicit: Boolean = false,
        manualRequestId: String? = null,
        analysisGenerationId: String? = manualRequestId,
    ): OneTimeWorkRequest {
        val builder = OneTimeWorkRequestBuilder<TechniqueAnalysisWorker>()
            .setInputData(
                workDataOf(
                    TechniqueAnalysisWorker.KEY_MEDIA_ID to mediaId,
                    TechniqueAnalysisWorker.KEY_MODE to mode.name,
                    TechniqueAnalysisWorker.KEY_USER_INITIATED to userInitiated,
                    TechniqueAnalysisWorker.KEY_IMPORTED to imported,
                    TechniqueAnalysisWorker.KEY_EXPLICIT to explicit,
                    TechniqueAnalysisWorker.KEY_MANUAL_REQUEST_ID to manualRequestId,
                    TechniqueAnalysisWorker.KEY_ANALYSIS_GENERATION_ID to analysisGenerationId,
                ),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .addTag(TAG_ALL)
            .addTag(TAG_MEDIA_PREFIX + mediaId)
            .addTag(TAG_MODE_PREFIX + mode.name)
        manualRequestId?.takeIf(String::isNotBlank)?.let { builder.addTag(TAG_MANUAL_REQUEST_PREFIX + it) }
        analysisGenerationId?.takeIf(String::isNotBlank)?.let { builder.addTag(TAG_GENERATION_PREFIX + it) }
        // Un trabajo urgente solo admite restricciones de red y almacenamiento: la batería va en los demás.
        val batteryNotLow = Constraints.Builder().setRequiresBatteryNotLow(true).build()
        when (mode) {
            AnalysisMode.FULL ->
                if (userInitiated) builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                else builder.setConstraints(batteryNotLow)
            AnalysisMode.RECOMPUTE -> builder.setConstraints(batteryNotLow)
            AnalysisMode.PLATE_ONLY -> Unit
        }
        return builder.build()
    }

    private suspend fun unfinishedWork(workManager: WorkManager, uniqueName: String): List<WorkInfo> =
        workManager.getWorkInfosForUniqueWorkFlow(uniqueName).first().filter { !it.state.isFinished }

    /**
     * Las filas QUEUED o RUNNING sin ningún trabajo vivo en WorkManager: con el análisis automático encendido se vuelven a
     * encolar (como automáticas); con él apagado el Worker descartaría ese automático, así que aquí se les aplica ya lo mismo
     * ([VideoAnalysisRepository.restoreOrDrop]: DONE desde su sidecar, o se borran). Sin WorkManager listo no se puede saber qué
     * trabajos viven y no se toca nada.
     *
     * Caso que se acepta: una petición EXPLÍCITA (la pidió el usuario) que se cortó por la muerte del proceso y que, con el
     * ajuste apagado, se reconcilia aquí como si fuera automática, se pierde. El usuario puede volver a pedirla.
     */
    private suspend fun settleStuckRows(app: Context, repository: VideoAnalysisRepository): ReconcileResult {
        val workManager = try {
            WorkManager.getInstance(app)
        } catch (error: IllegalStateException) {
            Log.w(TAG, "WorkManager no está listo: no se vuelven a encolar los análisis pendientes.", error)
            return ReconcileResult()
        }
        val aliveWorks = workManager.getWorkInfosByTagFlow(TAG_ALL).first()
            .filter { !it.state.isFinished }
        val aliveByMedia = HashMap<String, MutableList<WorkInfo>>()
        for (info in aliveWorks) {
            for (tag in info.tags) {
                if (tag.startsWith(TAG_MEDIA_PREFIX)) {
                    val mediaId = tag.removePrefix(TAG_MEDIA_PREFIX)
                    aliveByMedia.getOrPut(mediaId) { mutableListOf() }.add(info)
                }
            }
        }
        val automaticOn = repository.readSettings().techniqueVideo.analyzeVideos
        var requeued = 0
        var restored = 0
        var dropped = 0

        val pendingRequests = repository.allPendingManualAnalysisRequests()
        val allIds = (repository.activeIds() + pendingRequests.keys).distinct()

        for (id in allIds) {
            try {
                val media = repository.videoOf(id)
                if (media == null) {
                    pendingRequests[id]?.let { repository.clearManualAnalysisRequest(id, it.id) }
                    continue
                }
                val manualRequest = repository.pendingManualAnalysisRequest(id)
                val aliveForMedia = aliveByMedia[id].orEmpty()

                if (manualRequest != null) {
                    val matchingWork = aliveForMedia.firstOrNull { info ->
                        val hasMode = (TAG_MODE_PREFIX + manualRequest.mode.name) in info.tags
                        val hasRequestId = (TAG_MANUAL_REQUEST_PREFIX + manualRequest.id) in info.tags
                        val hasGeneration = (TAG_GENERATION_PREFIX + manualRequest.id) in info.tags
                        hasMode && hasRequestId && hasGeneration
                    }
                    if (matchingWork != null) {
                        continue
                    }
                    if (aliveForMedia.isNotEmpty()) {
                        workManager.cancelUniqueWork(uniqueName(id)).await()
                    }
                    if (enqueue(
                            app,
                            id,
                            manualRequest.mode,
                            userInitiated = true,
                            explicit = true,
                            manualRequestId = manualRequest.id,
                            repository = repository,
                        )
                    ) {
                        requeued++
                    }
                } else {
                    val currentGeneration = repository.currentAnalysisGeneration(id)
                    val represented = currentGeneration?.let { generation ->
                        aliveForMedia.any { info ->
                            TAG_MODE_PREFIX + generation.mode.name in info.tags &&
                                TAG_GENERATION_PREFIX + generation.id in info.tags
                        }
                    } == true
                    if (represented) continue
                    if (aliveForMedia.isNotEmpty()) workManager.cancelUniqueWork(uniqueName(id)).await()
                    val takeChoice = withContext(Dispatchers.IO) {
                        runCatching {
                            CaptureMetadataFile.read(TechniqueSidecars.captureFile(File(media.filePath)))
                                .analysisRequestedForCapture
                        }.getOrNull()
                    }
                    val shouldQueue = takeChoice ?: automaticOn
                    if (shouldQueue) {
                        if (enqueue(
                                app,
                                id,
                                AnalysisMode.FULL,
                                userInitiated = takeChoice == true,
                                explicit = takeChoice == true,
                                manualRequestId = null,
                                repository = repository,
                            )
                        ) {
                            requeued++
                        }
                    } else if (repository.restoreOrDrop(id)) {
                        restored++
                    } else {
                        dropped++
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "No se pudo resolver la fila pendiente de $id.", error)
            }
        }
        return ReconcileResult(rebuiltRows = restored, removedRows = dropped, requeued = requeued)
    }

    private fun isMainProcess(app: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.P || Application.getProcessName() == app.packageName
}
