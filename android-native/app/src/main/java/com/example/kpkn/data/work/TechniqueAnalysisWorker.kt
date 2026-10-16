package com.example.kpkn.data.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.kpkn.R
import com.example.kpkn.data.media.technique.AnalysisMode
import com.example.kpkn.data.media.technique.TechniqueAnalysisOutcome
import com.example.kpkn.data.media.technique.TechniqueAnalysisRequest
import com.example.kpkn.data.media.technique.TechniqueAnalyzerProvider
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.VideoAnalysisStatus
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.repository.VideoAnalysisRepository
import com.example.kpkn.data.repository.techniqueLoadKg
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.domain.biomechanics.profiles.ExerciseRef
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException

/**
 * Analiza la técnica del vídeo de una serie en segundo plano (§9.4). Entrada: `mediaId`, `mode` ([AnalysisMode]), y de forma
 * opcional `userInitiated`, `imported` y `explicit` (ver [TechniqueAnalysisScheduler]). La interfaz solo lee Room: el Worker
 * deja allí el estado y el avance de cada paso.
 *
 * Secuencia:
 * 1. Si el medio ya no existe → `Result.success()`. Lo mismo si el trabajo es AUTOMÁTICO (el que se encola al guardar un vídeo
 *    o al reconciliar: `explicit` falso) y el ajuste «Analizar la técnica de los vídeos» está apagado: se descarta, pero antes
 *    la fila vuelve a como estaría sin este trabajo (DONE rehecha desde su `<id>.analysis.json` si lo hay; si no, se borra),
 *    para que la serie no se quede en «Analizando…». Lo que pide el usuario (`explicit`: analizar, reanalizar, fijar el disco
 *    o el perfil) corre siempre, con el ajuste apagado también: el ajuste solo gobierna el análisis automático.
 * 2. Toma [AnalysisGate.mutex]: un análisis cada vez. Fila en RUNNING.
 * 3. [AnalysisGate.awaitClear]: espera a que la cámara no grabe y el móvil no esté en estado térmico SEVERE.
 * 4. Arma la [TechniqueAnalysisRequest] y llama al analizador ([TechniqueAnalyzerProvider]).
 * 5. Según el resultado: Done → `<id>.analysis.json` y DESPUÉS la fila DONE; Unsupported → UNSUPPORTED; NeedsProfile →
 *    NEEDS_PROFILE; Failed transitorio → `Result.retry()` (espera exponencial desde 30 s) hasta el 3.er intento; si no, FAILED
 *    con su `errorCode`.
 *
 * El avance va a Room como mucho una vez por segundo y solo si sube 5 puntos ([ProgressThrottle]). Los vídeos de más de 45 s o
 * importados se promocionan a primer plano ([getForegroundInfo]; `setForeground` dentro de un `try`: en Android 12+ el sistema
 * puede negarlo desde segundo plano y el análisis sigue sin aviso).
 */
class TechniqueAnalysisWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val mediaId = inputData.getString(KEY_MEDIA_ID)?.takeIf { it.isNotBlank() } ?: return Result.failure()
        val mode = AnalysisMode.entries.firstOrNull { it.name == inputData.getString(KEY_MODE) } ?: AnalysisMode.FULL
        val repository = VideoAnalysisRepository.init(applicationContext)
        return try {
            // Sin análisis que hacer: se descarta ANTES de esperar el turno.
            if (discardIfUnwanted(repository, mediaId)) return Result.success()
            AnalysisGate.mutex.withLock { runLocked(repository, mediaId, mode) }
        } catch (cancelled: CancellationException) {
            // Cancelado por el sistema (se reintentará) o al borrar el medio: la fila queda como estaba.
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "El Worker de análisis de $mediaId falló por un error inesperado.", error)
            runCatching { repository.markState(mediaId, VideoAnalysisStatus.FAILED, 0, errorCode = ERROR_WORKER) }
            Result.failure(workDataOf(KEY_ERROR_CODE to ERROR_WORKER))
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfoFor(0)

    private suspend fun runLocked(repository: VideoAnalysisRepository, mediaId: String, mode: AnalysisMode): Result {
        // Los ajustes y el medio pudieron cambiar mientras se esperaba el turno.
        if (discardIfUnwanted(repository, mediaId)) return Result.success()
        if (!repository.markState(mediaId, VideoAnalysisStatus.RUNNING, 0)) return Result.success()
        AnalysisGate.awaitClear(applicationContext)
        // Ya en marcha, el análisis sigue aunque el ajuste se apague mientras se espera a la cámara.
        val (media, settings) = load(repository, mediaId) ?: return Result.success()

        val video = File(media.filePath)
        if (!video.isFile) {
            return failed(repository, media.id, TechniqueAnalysisOutcome.Failed(ERROR_VIDEO_MISSING, transient = false), 0)
        }
        val overrides = repository.captureOverrides(media)
        val request = TechniqueAnalysisRequest(
            mediaId = media.id,
            videoFile = video,
            mode = mode,
            exercise = exerciseRefOf(media, overrides.userProfileOverride),
            loadKg = media.techniqueLoadKg(),
            plateDiameterMm = overrides.plateDiameterMm ?: settings.techniqueVideo.plateDiameterMm,
            plateSeed = overrides.plateSeed,
            userHeightCm = settings.userVitals.height?.takeIf { it.isFinite() && it > 0.0 },
            // Los nombres de SetTechniqueV2 de la serie no viajan con el medio: VT4c/VT8 los añadirán si hacen falta.
            setHints = emptySet(),
        )

        val inForeground = promoteToForeground(media)
        val throttle = ProgressThrottle()
        var written = 0
        val outcome = try {
            TechniqueAnalyzerProvider.get(applicationContext).analyze(request) { raw ->
                val percent = throttle.accept(raw) ?: return@analyze
                written = percent
                try {
                    repository.writeProgress(media.id, percent)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    // El avance es una cortesía: una escritura que falla nunca tumba un análisis que va bien.
                    Log.w(TAG, "No se pudo escribir el avance de ${media.id}.", error)
                }
                if (inForeground) updateForeground(percent)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: IOException) {
            TechniqueAnalysisOutcome.Failed(ERROR_IO, transient = true)
        } catch (error: OutOfMemoryError) {
            TechniqueAnalysisOutcome.Failed(ERROR_OUT_OF_MEMORY, transient = false)
        } catch (error: Throwable) {
            Log.w(TAG, "El analizador lanzó una excepción en ${media.id}.", error)
            TechniqueAnalysisOutcome.Failed(ERROR_ANALYZER_PREFIX + error.javaClass.simpleName, transient = false)
        }

        return when (outcome) {
            is TechniqueAnalysisOutcome.Done -> {
                try {
                    // false = el medio se borró durante el análisis: no queda nada que guardar.
                    repository.completeDone(
                        media = media,
                        report = outcome.report,
                        exercise = outcome.exercise ?: request.exercise,
                        loadKg = request.loadKg,
                        mode = mode,
                        poseVersion = if (mode == AnalysisMode.FULL) outcome.poseVersion else null,
                        poseEngine = if (mode == AnalysisMode.FULL) outcome.poseEngine else null,
                    )
                    Result.success()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    // Un disco lleno o una base ocupada suelen pasar: se reintenta (el sidecar, si llegó a escribirse, sigue ahí).
                    Log.w(TAG, "No se pudo guardar el informe de ${media.id}.", error)
                    val code = if (error is IOException) ERROR_SIDECAR_WRITE else ERROR_STORE_WRITE
                    failed(repository, media.id, TechniqueAnalysisOutcome.Failed(code, transient = true), written)
                }
            }
            is TechniqueAnalysisOutcome.Unsupported -> {
                repository.deleteReport(media)
                repository.markState(media.id, VideoAnalysisStatus.UNSUPPORTED, 0, errorCode = outcome.reason)
                Result.success()
            }
            is TechniqueAnalysisOutcome.NeedsProfile -> {
                repository.deleteReport(media)
                repository.markState(media.id, VideoAnalysisStatus.NEEDS_PROFILE, 0, profileGuess = outcome.guess)
                Result.success()
            }
            is TechniqueAnalysisOutcome.Failed -> failed(repository, media.id, outcome, written)
        }
    }

    /** El medio y los ajustes, o `null` si el medio ya no existe (o no es un vídeo). */
    private suspend fun load(repository: VideoAnalysisRepository, mediaId: String): Pair<WorkoutMedia, Settings>? {
        val media = repository.videoOf(mediaId) ?: return null
        return media to repository.readSettings()
    }

    /**
     * `true` si el trabajo termina aquí, sin analizar: el medio ya no existe, o el trabajo es automático y el ajuste «Analizar la
     * técnica de los vídeos» está apagado. En este último caso la fila, que el encolado dejó en QUEUED, vuelve antes a como
     * estaría sin este trabajo ([VideoAnalysisRepository.restoreOrDrop]: DONE desde su sidecar, o borrada). Un trabajo que pidió
     * el usuario ([KEY_EXPLICIT]) nunca se descarta por el ajuste.
     */
    private suspend fun discardIfUnwanted(repository: VideoAnalysisRepository, mediaId: String): Boolean {
        val (_, settings) = load(repository, mediaId) ?: return true
        if (inputData.getBoolean(KEY_EXPLICIT, false) || settings.techniqueVideo.analyzeVideos) return false
        repository.restoreOrDrop(mediaId)
        return true
    }

    /** Un fallo transitorio se reintenta hasta el 3.er intento; si no, FAILED con su código. */
    private suspend fun failed(
        repository: VideoAnalysisRepository,
        mediaId: String,
        outcome: TechniqueAnalysisOutcome.Failed,
        progressPct: Int,
    ): Result {
        if (outcome.transient && runAttemptCount < MAX_ATTEMPTS - 1) {
            repository.markState(mediaId, VideoAnalysisStatus.QUEUED, 0)
            return Result.retry()
        }
        repository.markState(mediaId, VideoAnalysisStatus.FAILED, progressPct, errorCode = outcome.errorCode)
        return Result.failure(workDataOf(KEY_ERROR_CODE to outcome.errorCode))
    }

    // --- Primer plano ---

    /** Vídeos de más de 45 s o importados: pasan a primer plano para que el sistema no corte el análisis. */
    private suspend fun promoteToForeground(media: WorkoutMedia): Boolean {
        val long = (media.durationMs ?: 0L) > FOREGROUND_MIN_DURATION_MS
        val imported = inputData.getBoolean(KEY_IMPORTED, false) || media.sessionKey.isNullOrBlank()
        if (!long && !imported) return false
        return try {
            setForeground(foregroundInfoFor(0))
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "No se pudo pasar a primer plano; el análisis sigue sin aviso.", error)
            false
        }
    }

    private suspend fun updateForeground(percent: Int) {
        try {
            setForeground(foregroundInfoFor(percent))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // El aviso es una cortesía: si el sistema lo niega, el análisis sigue.
        }
    }

    /** El aviso «Analizando la técnica del vídeo» con su avance (0 = barra indeterminada). */
    internal fun foregroundInfoFor(percent: Int): ForegroundInfo {
        ensureChannel()
        val context = applicationContext
        val pct = percent.coerceIn(0, 100)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_kpkn)
            .setContentTitle(context.getString(R.string.notif_technique_title))
            .setProgress(100, pct, pct == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setLocalOnly(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        if (pct > 0) builder.setContentText(context.getString(R.string.notif_technique_progress, pct))
        openAppIntent(context)?.let { builder.setContentIntent(it) }
        val notification = builder.build()
        val type = foregroundServiceType(Build.VERSION.SDK_INT)
        return if (type == null) {
            ForegroundInfo(NOTIFICATION_ID, notification)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification, type)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.notif_channel_technique_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = applicationContext.getString(R.string.notif_channel_technique_desc)
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            },
        )
    }

    private fun openAppIntent(context: Context): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        return PendingIntent.getActivity(
            context,
            REQUEST_CODE_OPEN,
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        const val KEY_MEDIA_ID = "mediaId"
        const val KEY_MODE = "mode"
        const val KEY_USER_INITIATED = "userInitiated"
        const val KEY_IMPORTED = "imported"

        /**
         * El análisis lo pidió el usuario (analizar, reanalizar, fijar el disco o el perfil): corre aunque el ajuste «Analizar la
         * técnica de los vídeos» esté apagado, que solo gobierna el automático (el que se encola al guardar y al reconciliar).
         * No es `userInitiated`, que decide si el trabajo es urgente.
         */
        const val KEY_EXPLICIT = "explicit"

        /** Dato de salida de un trabajo que acabó en FAILED. */
        const val KEY_ERROR_CODE = "errorCode"

        /** Intentos antes de dar un fallo transitorio por definitivo. */
        const val MAX_ATTEMPTS = 3

        /** Por encima de esta duración el vídeo se analiza en primer plano. */
        const val FOREGROUND_MIN_DURATION_MS = 45_000L

        const val CHANNEL_ID = "technique_analysis"
        const val NOTIFICATION_ID = 42061

        const val ERROR_VIDEO_MISSING = "video_missing"
        const val ERROR_IO = "io_error"
        const val ERROR_OUT_OF_MEMORY = "out_of_memory"
        const val ERROR_SIDECAR_WRITE = "sidecar_write_failed"
        const val ERROR_STORE_WRITE = "store_write_failed"
        const val ERROR_WORKER = "worker_error"
        const val ERROR_ANALYZER_PREFIX = "analyzer_error:"

        private const val TAG = "TechniqueWorker"
        private const val REQUEST_CODE_OPEN = 507

        /**
         * Tipo del servicio en primer plano: MEDIA_PROCESSING desde la API 35, DATA_SYNC de la 29 a la 34, y ninguno antes de
         * la 29 (que no distingue tipos).
         */
        internal fun foregroundServiceType(sdkInt: Int): Int? = when {
            sdkInt >= 35 -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
            sdkInt >= 29 -> ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            else -> null
        }

        /**
         * El `ExerciseRef` con lo que hay en el medio: los ids del catálogo, el nombre y el perfil elegido por el usuario. Sin ids
         * del catálogo el ejercicio es propio. VT4c lo completa (patrón, material, lateralidad) con su resolvedor de catálogo.
         */
        internal fun exerciseRefOf(media: WorkoutMedia, userProfileOverride: AnalysisProfileId?): ExerciseRef {
            val definitionId = media.catalogDefinitionId?.takeIf { it.isNotBlank() }
            val configurationId = media.catalogConfigurationId?.takeIf { it.isNotBlank() }
            return ExerciseRef(
                definitionId = definitionId,
                configurationId = configurationId,
                movementPatternId = null,
                equipmentId = null,
                laterality = null,
                name = media.exerciseName.orEmpty(),
                isCustom = definitionId == null && configurationId == null,
                userProfileOverride = userProfileOverride,
                guessedMovementPatternId = null,
            )
        }
    }
}

/**
 * Limita lo que el avance de un análisis escribe en Room: como mucho una vez por [minIntervalMs] y solo si sube [minStepPct]
 * puntos o más sobre lo último escrito. Nunca retrocede y nunca llega a 100 (el 100 es el estado DONE).
 */
internal class ProgressThrottle(
    private val minIntervalMs: Long = MIN_INTERVAL_MS,
    private val minStepPct: Int = MIN_STEP_PCT,
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private var lastPct = 0
    private var lastAtMs = 0L
    private var wrote = false

    /** El porcentaje que hay que escribir ahora para [percent], o `null` si todavía no toca. */
    fun accept(percent: Int): Int? {
        val pct = percent.coerceIn(0, MAX_RUNNING_PCT)
        if (pct - lastPct < minStepPct) return null
        val now = nowMs()
        if (wrote && now - lastAtMs < minIntervalMs) return null
        lastPct = pct
        lastAtMs = now
        wrote = true
        return pct
    }

    companion object {
        const val MIN_INTERVAL_MS = 1_000L
        const val MIN_STEP_PCT = 5
        const val MAX_RUNNING_PCT = 99
    }
}
