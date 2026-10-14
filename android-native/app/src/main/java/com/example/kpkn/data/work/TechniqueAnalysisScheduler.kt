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
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

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

    /** Espera del primer reintento tras un fallo transitorio; WorkManager la dobla en cada intento. */
    const val BACKOFF_SECONDS = 30L

    private const val TAG = "TechniqueScheduler"

    private val reconcileLock = Mutex()
    private val backgroundReconcileStarted = AtomicBoolean(false)
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
        repository: VideoAnalysisRepository = VideoAnalysisRepository.init(context),
    ): Boolean {
        val app = context.applicationContext
        if (repository.videoOf(mediaId) == null) return false
        val workManager = WorkManager.getInstance(app)
        val name = uniqueName(mediaId)
        if (mode == AnalysisMode.FULL) {
            val pending = unfinishedWork(workManager, name)
            if (pending.isNotEmpty()) {
                // KEEP: ya hay un FULL pendiente o en curso, no se toca nada (ni la fila).
                if (pending.all { TAG_MODE_PREFIX + AnalysisMode.FULL.name in it.tags }) return true
                // Un RECOMPUTE o un PLATE_ONLY pendiente no puede dejar sin efecto a un FULL, que ya lo contiene.
                workManager.cancelUniqueWork(name).await()
            }
        }
        if (!repository.markState(mediaId, VideoAnalysisStatus.QUEUED)) return false
        workManager.enqueueUniqueWork(name, policyFor(mode), requestFor(mediaId, mode, userInitiated, imported, explicit)).await()
        return true
    }

    /** Cancela el análisis de [mediaId], esté pendiente o en curso (al borrar el medio). No toca la fila. */
    fun cancel(context: Context, mediaId: String) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(uniqueName(mediaId))
    }

    /**
     * Pone la caché de análisis de acuerdo con los archivos y la cola ([VideoAnalysisRepository.reconcile]) y vuelve a
     * encolar las filas que decían QUEUED o RUNNING sin ningún trabajo vivo (el trabajo se perdió: una copia restaurada, un
     * borrado de datos de WorkManager). Las filas con un `engineVersion` anterior al actual no se tocan: su RECOMPUTE se lanza
     * al abrir el análisis. Dos llamadas a la vez se reparten el trabajo: la segunda no hace nada.
     */
    suspend fun reconcile(
        context: Context,
        repository: VideoAnalysisRepository = VideoAnalysisRepository.init(context),
    ): ReconcileResult {
        if (!reconcileLock.tryLock()) return ReconcileResult()
        try {
            val base = repository.reconcile()
            return base.copy(requeued = requeueStuckRows(context.applicationContext, repository))
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
    ): OneTimeWorkRequest {
        val builder = OneTimeWorkRequestBuilder<TechniqueAnalysisWorker>()
            .setInputData(
                workDataOf(
                    TechniqueAnalysisWorker.KEY_MEDIA_ID to mediaId,
                    TechniqueAnalysisWorker.KEY_MODE to mode.name,
                    TechniqueAnalysisWorker.KEY_USER_INITIATED to userInitiated,
                    TechniqueAnalysisWorker.KEY_IMPORTED to imported,
                    TechniqueAnalysisWorker.KEY_EXPLICIT to explicit,
                ),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .addTag(TAG_ALL)
            .addTag(TAG_MEDIA_PREFIX + mediaId)
            .addTag(TAG_MODE_PREFIX + mode.name)
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

    private suspend fun requeueStuckRows(app: Context, repository: VideoAnalysisRepository): Int {
        val workManager = try {
            WorkManager.getInstance(app)
        } catch (error: IllegalStateException) {
            Log.w(TAG, "WorkManager no está listo: no se vuelven a encolar los análisis pendientes.", error)
            return 0
        }
        val alive = workManager.getWorkInfosByTagFlow(TAG_ALL).first()
            .filter { !it.state.isFinished }
            .flatMapTo(HashSet()) { info ->
                info.tags.filter { it.startsWith(TAG_MEDIA_PREFIX) }.map { it.removePrefix(TAG_MEDIA_PREFIX) }
            }
        var requeued = 0
        for (id in repository.activeIds()) {
            if (id in alive) continue
            try {
                if (enqueue(app, id, AnalysisMode.FULL, userInitiated = false, explicit = false, repository = repository)) requeued++
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "No se pudo volver a encolar el análisis de $id.", error)
            }
        }
        return requeued
    }

    private fun isMainProcess(app: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.P || Application.getProcessName() == app.packageName
}
