package com.example.kpkn.data.repository

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.VideoAnalysisExerciseRow
import com.example.kpkn.data.db.VideoAnalysisLoadPoint
import com.example.kpkn.data.db.VideoAnalysisSessionRow
import com.example.kpkn.data.db.toSettings
import com.example.kpkn.data.db.toWorkoutMedia
import com.example.kpkn.data.media.WorkoutMediaCaptureJournal
import com.example.kpkn.data.media.WorkoutMediaStore
import com.example.kpkn.data.media.technique.AnalysisMode
import com.example.kpkn.data.media.technique.CaptureFileLock
import com.example.kpkn.data.media.technique.TechniqueSidecars
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.VideoAnalysisStatus
import com.example.kpkn.data.models.VideoAnalysisSummary
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.models.WorkoutMediaKind
import com.example.kpkn.data.models.techniqueConfigurationKey
import com.example.kpkn.data.models.techniqueExerciseKey
import com.example.kpkn.data.models.techniqueSetKey
import com.example.kpkn.data.models.toEntity
import com.example.kpkn.data.models.toSummary
import com.example.kpkn.data.work.TechniqueAnalysisScheduler
import com.example.kpkn.domain.biomechanics.bar.PlateSeed
import com.example.kpkn.domain.biomechanics.metrics.SetTechniqueReport
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.domain.biomechanics.profiles.ExerciseRef
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.io.IOException

/**
 * Lo que dejó [VideoAnalysisRepository.reconcile] (y, con la cola, `TechniqueAnalysisScheduler.reconcile`).
 *
 * @property rebuiltRows filas `video_analysis` rehechas desde su `<id>.analysis.json` (p. ej. tras restaurar una copia, o una
 *   fila QUEUED o RUNNING sin trabajo vivo con el análisis automático apagado y un informe en su sidecar).
 * @property removedRows filas que apuntaban a un medio que ya no existe (o que no es un vídeo), y las QUEUED o RUNNING sin
 *   trabajo vivo con el análisis automático apagado y sin informe en su sidecar.
 * @property removedFiles sidecars huérfanos borrados: de un medio que ya no existe.
 * @property requeued filas que decían QUEUED o RUNNING sin ningún trabajo vivo y se volvieron a encolar (solo con el análisis
 *   automático encendido).
 */
data class ReconcileResult(
    val rebuiltRows: Int = 0,
    val removedRows: Int = 0,
    val removedFiles: Int = 0,
    val requeued: Int = 0,
)

/**
 * El análisis de técnica en vídeo visto desde la interfaz y desde la cola (§9.5): lee Room, pide análisis y guarda lo que el
 * usuario decide (disco de calibración, perfil de un ejercicio propio).
 *
 * - **Lo que lee la interfaz es SOLO Room** ([observeByMedia], [observeBySessionKey], [observeExerciseSummaries], [observeTrend],
 *   [observeConfigurationPoints]). La FUENTE DE VERDAD de un análisis terminado es `<id>.analysis.json`; la fila se rehace
 *   desde ahí en [reconcile].
 * - **Orden obligatorio al terminar bien:** primero `<id>.analysis.json` y DESPUÉS la fila ([completeDone]). `workout_media`
 *   no se escribe aquí nunca (y jamás con REPLACE: dispararía el `ON DELETE CASCADE` de `video_analysis`).
 * - **Lo que decide el usuario** va en `<id>.capture.json`, junto a los metadatos de la captura, sin tocar las claves que no
 *   conoce (ver [CaptureOverridesFile]): `userProfileOverride`, `plateSeed` y `plateDiameterMm`. Se eligió el sidecar de
 *   captura y no un `<id>.plate.json` aparte para que `TechniqueSidecars.deleteAll` y la limpieza de huérfanos ya lo cubran.
 *
 * Singleton de proceso al estilo de [WorkoutMediaRepository] (`init` / `getInstance`); [forDatabase] da una instancia sobre
 * otra base (pruebas y `WorkoutMediaRepository`).
 */
class VideoAnalysisRepository internal constructor(
    context: Context,
    private val db: KpknDatabase = KpknDatabase.getInstance(context),
    store: WorkoutMediaStore? = null,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) {
    private val appContext = context.applicationContext
    private val injectedStore = store
    private val store: WorkoutMediaStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        injectedStore ?: WorkoutMediaStore(appContext.filesDir)
    }

    // --- Lo que lee la interfaz (solo Room) ---

    /** El análisis de un vídeo, o `null` si no tiene (se actualiza con el avance). */
    fun observeByMedia(mediaId: String): Flow<VideoAnalysisSummary?> =
        db.videoAnalysisDao().observeByMedia(mediaId).map { row -> row?.toSummary() }

    /** Los vídeos más recientes con su estado, también mientras se analizan. */
    fun observeRecent(limit: Int): Flow<List<VideoAnalysisSummary>> =
        db.videoAnalysisDao().observeRecent(limit.coerceAtLeast(0)).map { rows -> rows.map { it.toSummary() } }

    /** Los vídeos de una sesión que tienen análisis (cualquier estado), del más antiguo al más reciente. */
    fun observeBySessionKey(sessionKey: String): Flow<List<VideoAnalysisSessionRow>> =
        db.videoAnalysisDao().observeBySessionKey(sessionKey)

    /** Los ejercicios con algún análisis terminado, del más reciente al más antiguo (el hub). */
    fun observeExerciseSummaries(): Flow<List<VideoAnalysisExerciseRow>> =
        db.videoAnalysisDao().observeExerciseSummaries()

    /** Los análisis terminados (DONE) de un ejercicio, por fecha de grabación. */
    fun observeTrend(exerciseKey: String): Flow<List<VideoAnalysisSummary>> =
        db.videoAnalysisDao().observeTrend(exerciseKey).map { rows -> rows.map { it.toSummary() } }

    /** Los puntos carga-velocidad de una configuración del catálogo, por fecha. */
    fun observeConfigurationPoints(configurationKey: String): Flow<List<VideoAnalysisLoadPoint>> =
        db.videoAnalysisDao().observeConfigurationPoints(configurationKey)

    suspend fun getByMedia(mediaId: String): VideoAnalysisSummary? =
        db.videoAnalysisDao().getByMedia(mediaId)?.toSummary()

    // --- Lo que pide la interfaz ---

    /**
     * Encola un análisis que pide el usuario (la fila pasa a QUEUED). `false` si el medio ya no existe o no es un vídeo.
     * Corre aunque el ajuste «Analizar la técnica de los vídeos» esté apagado: ese ajuste solo gobierna el análisis automático
     * al guardar un vídeo, no lo que el usuario pide (también [reanalyze], [setPlateSeed] y [setUserProfileOverride]).
     */
    suspend fun requestAnalysis(mediaId: String, mode: AnalysisMode = AnalysisMode.FULL): Boolean =
        TechniqueAnalysisScheduler.enqueue(appContext, mediaId, mode, userInitiated = true, explicit = true, repository = this)

    /** «Reanalizar»: vuelve a decodificar el vídeo y a estimar la pose con la versión actual. */
    suspend fun reanalyze(mediaId: String): Boolean = requestAnalysis(mediaId, AnalysisMode.FULL)

    /**
     * Guarda el disco que el usuario fijó en la pantalla de calibrar ([seed] en píxeles del fotograma de análisis y
     * [diameterMm] su diámetro real) en `<id>.capture.json` y encola PLATE_ONLY. `false` si el medio no existe, el disco no es
     * válido (coordenadas no finitas, radio ≤ 0 o un diámetro fuera de 100–700 mm) o no se pudo guardar.
     */
    suspend fun setPlateSeed(mediaId: String, seed: PlateSeed, diameterMm: Int): Boolean {
        if (!seed.isUsable() || diameterMm !in MIN_PLATE_MM..MAX_PLATE_MM) return false
        val media = videoOf(mediaId) ?: return false
        if (!saveOverrides(media) { it.copy(plateSeed = seed, plateDiameterMm = diameterMm) }) return false
        return requestAnalysis(mediaId, AnalysisMode.PLATE_ONLY)
    }

    /**
     * El perfil que el usuario eligió para un ejercicio propio ([profileId]; `null` lo quita): se guarda en
     * `<id>.capture.json` (`userProfileOverride`, que gana a todo en el resolver) y se encola FULL.
     */
    suspend fun setUserProfileOverride(mediaId: String, profileId: AnalysisProfileId?): Boolean {
        val media = videoOf(mediaId) ?: return false
        if (!saveOverrides(media) { it.copy(userProfileOverride = profileId) }) return false
        return requestAnalysis(mediaId, AnalysisMode.FULL)
    }

    // --- Lo que usan la cola y el Worker (mismo módulo) ---

    /** El medio, si existe y es un VÍDEO. */
    internal suspend fun videoOf(mediaId: String): WorkoutMedia? =
        db.workoutMediaDao().getById(mediaId)?.toWorkoutMedia()?.takeIf { it.kind == WorkoutMediaKind.VIDEO }

    /** Los ajustes guardados, o los de fábrica si aún no hay fila o está ilegible. */
    internal suspend fun readSettings(): Settings {
        val stored = try {
            db.settingsDao().get()?.toSettings()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "No se pudieron leer los ajustes; se usan los de fábrica.", error)
            null
        }
        return stored ?: Settings()
    }

    /** Lo que el usuario fijó para este vídeo en `<id>.capture.json` (perfil elegido, disco y su diámetro). */
    internal suspend fun captureOverrides(media: WorkoutMedia): CaptureOverrides =
        withContext(Dispatchers.IO) { CaptureOverridesFile.read(TechniqueSidecars.captureFile(File(media.filePath))) }

    private suspend fun saveOverrides(media: WorkoutMedia, change: (CaptureOverrides) -> CaptureOverrides): Boolean =
        withContext(Dispatchers.IO) {
            try {
                CaptureOverridesFile.update(TechniqueSidecars.captureFile(File(media.filePath)), change)
                true
            } catch (error: IOException) {
                Log.w(TAG, "No se pudo guardar la elección del usuario de ${media.id}.", error)
                false
            }
        }

    /**
     * Pone la fila de [mediaId] en [status] con ese avance. Si la fila ya existe conserva sus medidas mientras se vuelve a
     * analizar (QUEUED, RUNNING, FAILED: un reanálisis no las pierde de golpe) y refresca lo que sale del medio (ejercicio,
     * carga, fecha); en UNSUPPORTED y NEEDS_PROFILE las pierde, porque el informe viejo ya no vale. Si no existe, la crea. En
     * NEEDS_PROFILE, [profileGuess] queda en `profileId` como sugerencia. `false` si el medio ya no existe (no se escribe nada).
     */
    internal suspend fun markState(
        mediaId: String,
        status: VideoAnalysisStatus,
        progressPct: Int = 0,
        errorCode: String? = null,
        profileGuess: AnalysisProfileId? = null,
    ): Boolean = db.withTransaction {
        val media = db.workoutMediaDao().getById(mediaId)?.toWorkoutMedia() ?: return@withTransaction false
        val dao = db.videoAnalysisDao()
        val now = nowMs()
        val fresh = VideoAnalysisSummary.pending(
            media = media,
            status = status,
            exerciseKey = media.techniqueExerciseKey(),
            configurationKey = media.techniqueConfigurationKey(),
            loadKg = media.techniqueLoadKg(),
            nowMs = now,
            progressPct = progressPct,
            errorCode = errorCode,
        )
        val previous = dao.getByMedia(mediaId)?.toSummary()
        val next = when {
            previous == null -> fresh
            // Sin análisis posible (otro ejercicio, o falta elegir el perfil): el informe viejo ya no vale, ni sus medidas.
            status == VideoAnalysisStatus.UNSUPPORTED || status == VideoAnalysisStatus.NEEDS_PROFILE -> fresh.copy(
                poseVersion = previous.poseVersion,
                poseEngine = previous.poseEngine,
                createdAtMs = previous.createdAtMs,
            )
            else -> previous.copy(
                status = status,
                progressPct = fresh.progressPct,
                errorCode = errorCode,
                exerciseKey = fresh.exerciseKey,
                configurationKey = fresh.configurationKey,
                exerciseName = fresh.exerciseName,
                setKey = fresh.setKey,
                loadKg = fresh.loadKg,
                recordedAtMs = fresh.recordedAtMs,
                updatedAtMs = now,
            )
        }
        dao.upsert(
            (if (status == VideoAnalysisStatus.NEEDS_PROFILE) next.copy(profileId = profileGuess) else next).toEntity(),
        )
        true
    }

    /** El avance de un análisis en curso: solo estado y avance, sin releer el medio. Sin fila, la crea. */
    internal suspend fun writeProgress(mediaId: String, progressPct: Int) {
        val pct = progressPct.coerceIn(0, 100)
        val changed = db.videoAnalysisDao().updateProgress(mediaId, VideoAnalysisStatus.RUNNING.name, pct, nowMs())
        if (changed == 0) markState(mediaId, VideoAnalysisStatus.RUNNING, pct)
    }

    /**
     * Cierra un análisis bien hecho: escribe `<id>.analysis.json` (la fuente de verdad) y SOLO DESPUÉS, en una transacción que
     * antes comprueba que el medio sigue ahí, deja la fila en DONE. Con [AnalysisMode.FULL] la traza de pose es nueva; con los
     * otros modos se conservan la versión y el motor de pose que ya tenía la fila.
     *
     * Devuelve `false` si el medio desapareció durante el análisis (se borran entonces sus sidecars). Un fallo al escribir el
     * sidecar lanza [IOException] y no toca la fila.
     */
    internal suspend fun completeDone(
        media: WorkoutMedia,
        report: SetTechniqueReport,
        exercise: ExerciseRef,
        loadKg: Double?,
        mode: AnalysisMode,
        poseVersion: Int? = null,
        poseEngine: String? = null,
    ): Boolean {
        val video = File(media.filePath)
        val file = TechniqueSidecars.analysisFile(video)
        val analyzedAtMs = nowMs()
        withContext(Dispatchers.IO) {
            TechniqueSidecars.writeAnalysis(file, report, exercise, loadKg, media.techniqueSetKey(), analyzedAtMs)
        }
        val stored = upsertDoneRow(
            mediaId = media.id,
            report = report,
            loadKg = loadKg,
            reportPath = file.absolutePath,
            createdAtFallbackMs = analyzedAtMs,
            keepPose = mode != AnalysisMode.FULL,
            poseVersion = poseVersion,
            poseEngine = poseEngine,
        )
        if (!stored) withContext(Dispatchers.IO) { TechniqueSidecars.deleteAll(video, store::isManagedPath) }
        return stored
    }

    /** Borra el informe de un vídeo (el ejercicio dejó de ser analizable): ya no vale para esta asignación. */
    internal suspend fun deleteReport(media: WorkoutMedia) {
        withContext(Dispatchers.IO) {
            val file = TechniqueSidecars.analysisFile(File(media.filePath))
            if (store.isManagedPath(file)) file.delete()
        }
    }

    /**
     * Deja la fila de [mediaId] como estaría sin un análisis que ya no va a correr (el automático que el ajuste apagado
     * descarta): si el vídeo tiene `<id>.analysis.json`, la fila se rehace desde él como en [reconcile] (DONE, con su resumen y su
     * `engineVersion`); si no, se borra. Solo toca `video_analysis`, nunca `workout_media`. Devuelve `true` si la rehízo y `false`
     * si la borró (o no había nada que borrar).
     */
    internal suspend fun restoreOrDrop(mediaId: String): Boolean {
        val media = videoOf(mediaId)
        val restored = media != null && rebuildFromSidecar(media)
        if (!restored) db.videoAnalysisDao().delete(mediaId)
        return restored
    }

    private suspend fun upsertDoneRow(
        mediaId: String,
        report: SetTechniqueReport,
        loadKg: Double?,
        reportPath: String,
        createdAtFallbackMs: Long,
        keepPose: Boolean,
        poseVersion: Int? = null,
        poseEngine: String? = null,
    ): Boolean = db.withTransaction {
        val media = db.workoutMediaDao().getById(mediaId)?.toWorkoutMedia() ?: return@withTransaction false
        val dao = db.videoAnalysisDao()
        val previous = dao.getByMedia(mediaId)
        val summary = report.toSummary(
            media = media,
            exerciseKey = media.techniqueExerciseKey(),
            configurationKey = media.techniqueConfigurationKey(),
            loadKg = loadKg,
            reportPath = reportPath,
            nowMs = nowMs(),
            // La versión real de la traza de pose la fijará VT4c (POSE_VERSION); hasta entonces vale la de siempre.
            poseVersion = if (keepPose) (previous?.poseVersion ?: VideoAnalysisSummary.DEFAULT_POSE_VERSION)
            else (poseVersion ?: VideoAnalysisSummary.DEFAULT_POSE_VERSION),
            poseEngine = if (keepPose) previous?.poseEngine else poseEngine,
            createdAtMs = previous?.createdAtMs ?: createdAtFallbackMs,
        )
        dao.upsert(summary.toEntity())
        true
    }

    private suspend fun rebuildFromSidecar(media: WorkoutMedia): Boolean {
        val file = TechniqueSidecars.analysisFile(File(media.filePath))
        val sidecar = withContext(Dispatchers.IO) { TechniqueSidecars.readAnalysis(file) } ?: return false
        return upsertDoneRow(
            mediaId = media.id,
            report = sidecar.report,
            loadKg = sidecar.loadKg,
            reportPath = file.absolutePath,
            createdAtFallbackMs = sidecar.analyzedAtMs,
            keepPose = true,
        )
    }

    /** Los vídeos con una fila QUEUED o RUNNING (un análisis pedido y aún sin terminar). */
    internal suspend fun activeIds(): List<String> {
        val dao = db.videoAnalysisDao()
        return dao.allIds().filter { id ->
            val status = dao.getByMedia(id)?.status
            status == VideoAnalysisStatus.QUEUED.name || status == VideoAnalysisStatus.RUNNING.name
        }
    }

    /**
     * Pone la caché de acuerdo con los archivos (§3.9): `video_analysis` es una caché de `<id>.analysis.json`, y una
     * restauración de copias la deja vacía (`DELETE FROM workout_media` y la cascada).
     *
     * 1. Borra las filas de un medio que ya no existe (o que no es un vídeo).
     * 2. Rehace desde su sidecar la fila de cada vídeo que tiene `<id>.analysis.json` y ninguna fila.
     * 3. Borra los sidecars huérfanos (de un medio que ya no existe). Nunca toca uno de un vídeo que está ingresando: lo salva el
     *    diario de capturas y un margen de [orphanGraceMs] sobre la fecha del archivo.
     *
     * Las filas con un `engineVersion` anterior al actual NO se tocan aquí: su RECOMPUTE se lanza al abrir el análisis.
     */
    suspend fun reconcile(orphanGraceMs: Long = ORPHAN_GRACE_MS): ReconcileResult {
        val media = db.workoutMediaDao().getAll()
        val videos = media.filter { it.kind == WorkoutMediaKind.VIDEO.name }
        val videoIds = videos.mapTo(HashSet()) { it.id }
        val dao = db.videoAnalysisDao()
        val rowIds = dao.allIds()

        var removedRows = 0
        for (id in rowIds) {
            if (id !in videoIds) removedRows += dao.delete(id)
        }

        val withRow = rowIds.toHashSet()
        var rebuilt = 0
        for (entity in videos) {
            if (entity.id in withRow) continue
            try {
                if (rebuildFromSidecar(entity.toWorkoutMedia())) rebuilt++
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "No se pudo rehacer la fila de ${entity.id} desde su sidecar.", error)
            }
        }

        val removedFiles = sweepOrphanSidecars(media.mapTo(HashSet()) { it.id }, orphanGraceMs)
        return ReconcileResult(rebuiltRows = rebuilt, removedRows = removedRows, removedFiles = removedFiles)
    }

    /**
     * Borra, en las carpetas de meses del almacén, los sidecars de la técnica cuyo medio no existe. Se salta los de una captura
     * que el diario aún no ingresó y los modificados hace menos de [graceMs]; si el diario está ilegible no borra nada.
     */
    private suspend fun sweepOrphanSidecars(knownMediaIds: Set<String>, graceMs: Long): Int = withContext(Dispatchers.IO) {
        val pending = try {
            WorkoutMediaCaptureJournal(appContext.filesDir).readAll().mapTo(HashSet()) { it.media.id }
        } catch (error: IOException) {
            Log.w(TAG, "Diario de capturas ilegible: no se limpian sidecars huérfanos esta vez.", error)
            return@withContext 0
        }
        val cutoff = nowMs() - graceMs
        var removed = 0
        val months = store.root().listFiles { file -> file.isDirectory && MONTH_DIR.matches(file.name) }.orEmpty()
        for (month in months) {
            for (file in month.listFiles().orEmpty()) {
                if (!file.isFile) continue
                val id = TechniqueSidecars.sidecarMediaId(file) ?: continue
                if (id in knownMediaIds || id in pending) continue
                if (file.lastModified() > cutoff) continue
                if (file.delete()) removed++
            }
        }
        removed
    }

    companion object {
        /** Un sidecar huérfano más reciente que esto no se borra: puede ser de una captura que aún se está ingresando. */
        const val ORPHAN_GRACE_MS = 24L * 60L * 60L * 1000L

        /** Diámetro del disco que el usuario puede fijar: de un disco pequeño (100 mm) a uno enorme (700 mm). */
        const val MIN_PLATE_MM = 100
        const val MAX_PLATE_MM = 700

        private const val TAG = "VideoAnalysisRepo"
        private val MONTH_DIR = Regex("\\d{4}-\\d{2}")

        @Volatile
        private var INSTANCE: VideoAnalysisRepository? = null

        fun init(context: Context): VideoAnalysisRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: VideoAnalysisRepository(context.applicationContext).also { INSTANCE = it }
            }

        /** Una instancia sobre [db] (y, si se da, [store]) que no es la del proceso. */
        fun forDatabase(context: Context, db: KpknDatabase, store: WorkoutMediaStore? = null): VideoAnalysisRepository =
            VideoAnalysisRepository(context.applicationContext, db, store)

        fun getInstance(): VideoAnalysisRepository =
            INSTANCE ?: error("VideoAnalysisRepository not initialized — call init(context) first.")

        fun closeInstance() {
            INSTANCE = null
        }

        /** Pruebas: pone (o quita, con `null`) la instancia del proceso que usa el Worker. */
        internal fun installForTests(repository: VideoAnalysisRepository?) {
            INSTANCE = repository
        }
    }
}

/** Los kg de la serie del medio, o `null` sin carga (peso corporal, cero o un dato que no es un número). */
internal fun WorkoutMedia.techniqueLoadKg(): Double? = weightKg?.takeIf { it.isFinite() && it > 0.0 }

/** Un disco que se puede seguir: coordenadas finitas, radio positivo y un instante del vídeo. */
private fun PlateSeed.isUsable(): Boolean = cx.isFinite() && cy.isFinite() && r.isFinite() && r > 0f && tUs >= 0L

/**
 * Lo que el usuario fijó para un vídeo y se guarda en `<id>.capture.json`: el perfil de análisis de un ejercicio propio
 * ([userProfileOverride]), el disco de calibración ([plateSeed], en píxeles del fotograma de análisis) y su diámetro real
 * ([plateDiameterMm]). `null` = sin dato.
 */
internal data class CaptureOverrides(
    val userProfileOverride: AnalysisProfileId? = null,
    val plateSeed: PlateSeed? = null,
    val plateDiameterMm: Int? = null,
)

/**
 * Lee y escribe esas tres claves de `<id>.capture.json` SIN tocar las demás (la gravedad, los fps y la lente que escribe el
 * grabador, o lo que añada una versión posterior): se parte del JSON que hay y solo cambian `userProfileOverride`,
 * `plateSeed` y `plateDiameterMm`. El archivo puede no existir (un vídeo de la galería no trae metadatos de captura): entonces
 * se crea solo con ellas, así que quien lea `<id>.capture.json` debe tratar TODOS los demás campos como opcionales.
 *
 * ```
 * {"userProfileOverride":"SQUAT","plateSeed":{"tUs":1500000,"cx":512.5,"cy":300.0,"r":42.0,"userConfirmed":true},"plateDiameterMm":450}
 * ```
 *
 * E/S bloqueante: llamar desde `Dispatchers.IO`. La escritura es atómica ([TechniqueSidecars.writeAtomically]) y el
 * leer-cambiar-escribir va en una sección crítica de proceso.
 */
internal object CaptureOverridesFile {
    const val KEY_PROFILE = "userProfileOverride"
    const val KEY_SEED = "plateSeed"
    const val KEY_DIAMETER = "plateDiameterMm"

    private val json = Json { ignoreUnknownKeys = true }
    private val empty = JsonObject(emptyMap())

    fun read(file: File): CaptureOverrides = synchronized(CaptureFileLock) { parse(readObject(file)) }

    /** Aplica [change] a lo que hay y lo escribe; lanza [IOException] si no se puede guardar. */
    fun update(file: File, change: (CaptureOverrides) -> CaptureOverrides) {
        synchronized(CaptureFileLock) {
            val current = readObject(file)
            val next = change(parse(current))
            val merged = LinkedHashMap<String, JsonElement>(current)
            merged.setOrRemove(KEY_PROFILE, next.userProfileOverride?.let { JsonPrimitive(it.name) })
            merged.setOrRemove(KEY_SEED, next.plateSeed?.let { seedToJson(it) })
            merged.setOrRemove(KEY_DIAMETER, next.plateDiameterMm?.let { JsonPrimitive(it) })
            TechniqueSidecars.writeAtomically(file, JsonObject(merged).toString().toByteArray(Charsets.UTF_8))
        }
    }

    private fun MutableMap<String, JsonElement>.setOrRemove(key: String, value: JsonElement?) {
        if (value == null) remove(key) else put(key, value)
    }

    private fun readObject(file: File): JsonObject {
        if (!file.isFile) return empty
        val text = try {
            file.readText(Charsets.UTF_8)
        } catch (_: IOException) {
            return empty
        }
        if (text.isBlank()) return empty
        return try {
            json.parseToJsonElement(text) as? JsonObject ?: empty
        } catch (_: IllegalArgumentException) {
            // SerializationException hereda de IllegalArgumentException: un sidecar ilegible solo significa «sin dato».
            empty
        }
    }

    private fun parse(obj: JsonObject): CaptureOverrides = CaptureOverrides(
        userProfileOverride = (obj[KEY_PROFILE] as? JsonPrimitive)?.contentOrNull
            ?.let { name -> AnalysisProfileId.entries.firstOrNull { it.name == name } },
        plateSeed = (obj[KEY_SEED] as? JsonObject)?.let(::seedFromJson),
        plateDiameterMm = (obj[KEY_DIAMETER] as? JsonPrimitive)?.intOrNull,
    )

    private fun seedToJson(seed: PlateSeed): JsonObject = JsonObject(
        linkedMapOf(
            "tUs" to JsonPrimitive(seed.tUs),
            "cx" to JsonPrimitive(seed.cx),
            "cy" to JsonPrimitive(seed.cy),
            "r" to JsonPrimitive(seed.r),
            "userConfirmed" to JsonPrimitive(seed.userConfirmed),
        ),
    )

    private fun seedFromJson(obj: JsonObject): PlateSeed? {
        val tUs = (obj["tUs"] as? JsonPrimitive)?.longOrNull ?: return null
        val cx = (obj["cx"] as? JsonPrimitive)?.floatOrNull ?: return null
        val cy = (obj["cy"] as? JsonPrimitive)?.floatOrNull ?: return null
        val r = (obj["r"] as? JsonPrimitive)?.floatOrNull ?: return null
        val confirmed = (obj["userConfirmed"] as? JsonPrimitive)?.booleanOrNull ?: false
        if (!cx.isFinite() || !cy.isFinite() || !r.isFinite() || r <= 0f) return null
        return PlateSeed(tUs, cx, cy, r, confirmed)
    }
}
