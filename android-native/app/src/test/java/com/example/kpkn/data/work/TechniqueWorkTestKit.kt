package com.example.kpkn.data.work

import android.content.Context
import android.util.Log
import androidx.work.Configuration
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toEntity
import com.example.kpkn.data.media.WorkoutMediaStore
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.TechniqueVideoSettings
import com.example.kpkn.data.models.UserVitals
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.models.WorkoutMediaKind
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Lo común de las pruebas de la cola y del Worker del análisis de técnica: un WorkManager de pruebas cuyo trabajo de análisis
 * se queda esperando (así el estado del trabajo no depende de una carrera) y medios de ejemplo sobre Room en memoria.
 */
internal object TechniqueWorkTestKit {
    @Volatile
    private var gate = CompletableDeferred<Unit>()

    /** Los datos de entrada de cada trabajo de análisis que el WorkManager de pruebas llegó a empezar, en orden. */
    val started = CopyOnWriteArrayList<Data>()

    /**
     * El trabajo de análisis de la cola de pruebas: anota sus datos de entrada, no hace nada y espera a [release]; al
     * cancelarlo, se cancela.
     */
    class HoldingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            started += inputData
            running.incrementAndGet()
            try {
                gate.await()
            } finally {
                running.decrementAndGet()
            }
            return Result.success()
        }
    }

    private val running = AtomicInteger()

    /** Espera a que hayan empezado [count] trabajos y devuelve sus datos de entrada. */
    suspend fun awaitStarted(count: Int): List<Data> {
        withTimeout(10_000L) {
            while (started.size < count) delay(20L)
        }
        return started.toList()
    }

    /** Inicia el WorkManager de pruebas: todo `TechniqueAnalysisWorker` encolado se sustituye por un [HoldingWorker]. */
    fun initWorkManager(context: Context) {
        gate = CompletableDeferred()
        started.clear()
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker? =
                if (workerClassName == TechniqueAnalysisWorker::class.java.name) HoldingWorker(appContext, workerParameters) else null
        }
        val configuration = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setWorkerFactory(factory)
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, configuration)
    }

    /** Deja terminar los trabajos que esperan. */
    fun release() {
        gate.complete(Unit)
    }

    fun tearDownWorkManager() {
        release()
        // Los trabajos en marcha terminan antes de cerrar la base de WorkManager: así no la reabren a medias.
        val deadline = System.nanoTime() + 2_000_000_000L
        while (running.get() > 0 && System.nanoTime() < deadline) Thread.sleep(10L)
        Thread.sleep(50L)
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    /** Un vídeo de una sentadilla del catálogo, de la serie 2 de la sesión «S», con 80 kg. */
    fun videoMedia(
        id: String,
        filePath: String,
        createdAtMs: Long = 1_780_000_000_000L,
        sessionKey: String? = "S",
        exerciseId: String? = "squat",
        catalogDefinitionId: String? = "back_squat",
        catalogConfigurationId: String? = "back_squat__barbell",
        exerciseName: String? = "Sentadilla trasera",
        weightKg: Double? = 80.0,
        durationMs: Long? = 12_000L,
    ) = WorkoutMedia(
        id = id,
        kind = WorkoutMediaKind.VIDEO,
        filePath = filePath,
        createdAtMs = createdAtMs,
        sessionKey = sessionKey,
        exerciseId = exerciseId,
        canonicalExerciseId = null,
        exerciseName = exerciseName,
        setIndex = 2,
        weightKg = weightKg,
        reps = 8,
        durationMs = durationMs,
        catalogDefinitionId = catalogDefinitionId,
        catalogConfigurationId = catalogConfigurationId,
    )

    /** Guarda en [db] un vídeo de [store] (con un archivo de verdad: el Worker comprueba que existe) y lo devuelve. */
    suspend fun insertVideo(
        db: KpknDatabase,
        store: WorkoutMediaStore,
        id: String,
        withFile: Boolean = true,
        configure: (WorkoutMedia) -> WorkoutMedia = { it },
    ): WorkoutMedia {
        val createdAtMs = 1_780_000_000_000L
        val file = store.destinationFile(id, WorkoutMediaKind.VIDEO, createdAtMs)
        if (withFile) file.writeBytes(ByteArray(64) { it.toByte() })
        val media = configure(videoMedia(id, file.absolutePath, createdAtMs))
        db.workoutMediaDao().upsert(media.toEntity())
        return media
    }

    /** Guarda los ajustes con el análisis activo o apagado, [heightCm] de estatura y [plateMm] de disco. */
    suspend fun putSettings(
        db: KpknDatabase,
        analyzeVideos: Boolean = true,
        heightCm: Double? = null,
        plateMm: Int = 450,
    ) {
        val settings = Settings(
            userVitals = UserVitals(height = heightCm),
            techniqueVideo = TechniqueVideoSettings(analyzeVideos = analyzeVideos, plateDiameterMm = plateMm),
        )
        db.settingsDao().upsert(settings.toEntity())
    }

    fun mediaFile(media: WorkoutMedia): File = File(media.filePath)
}
