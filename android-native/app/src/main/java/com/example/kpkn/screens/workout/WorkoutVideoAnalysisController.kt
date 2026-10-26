package com.example.kpkn.screens.workout

import com.example.kpkn.domain.exercises.resolvedCanonicalExerciseId

import com.example.kpkn.data.db.VideoAnalysisSessionRow
import com.example.kpkn.data.media.technique.PartialSummary
import com.example.kpkn.data.media.WorkoutMediaSides
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.data.models.VideoAnalysisStatus
import com.example.kpkn.data.models.VideoAnalysisSummary
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.repository.VideoAnalysisRepository
import com.example.kpkn.data.repository.WorkoutMediaRepository
import com.example.kpkn.domain.biomechanics.metrics.CountConfidence
import com.example.kpkn.domain.biomechanics.metrics.PartialZone
import com.example.kpkn.domain.biomechanics.profiles.UnsupportedReasons
import com.example.kpkn.screens.workout.design.VideoThumbBadge
import com.example.kpkn.screens.workout.design.videoThumbBadgeFor
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal data class SetVideoAnalysisUi(val mediaId: String, val state: SetVideoAnalysisState)

internal sealed interface SetVideoAnalysisState {
    data class Analyzing(val progress: Int) : SetVideoAnalysisState
    /**
     * [partials] es el número de parciales que se OFRECE: con la confianza propia de las parciales ([partialConfidence]) BAJA
     * es 0 y nada se aplica. [partialZone] es la zona común de las parciales, si la hay (solo se nombra con ALTA).
     */
    data class Suggest(
        val reps: Int,
        val partials: Int,
        val failed: Int,
        val confidence: CountConfidence,
        val romPercent: Int? = null,
        val partialConfidence: CountConfidence = confidence,
        val partialZone: PartialZone? = null,
    ) : SetVideoAnalysisState

    /**
     * [partials] es el número de parciales que se OFRECE: con la confianza propia de las parciales ([partialConfidence]) BAJA
     * es [loggedPartials], las que ya había, y «Corregir» no las toca.
     */
    data class Mismatch(
        val logged: Int,
        val counted: Int,
        val partials: Int,
        val failed: Int,
        val confidence: CountConfidence,
        val loggedPartials: Int = 0,
        val partialConfidence: CountConfidence = confidence,
        val partialZone: PartialZone? = null,
    ) : SetVideoAnalysisState {
        val countsDiffer: Boolean get() = logged != counted || loggedPartials != partials
    }
    data class Agrees(val counted: Int) : SetVideoAnalysisState
    data object Inconclusive : SetVideoAnalysisState
    data class Unsupported(val reasonText: String) : SetVideoAnalysisState
    data class Failed(val errorCode: String?) : SetVideoAnalysisState
}

/** Petición explícita de la interfaz: el repositorio encola FULL aunque el ajuste automático esté apagado. */
internal class WorkoutVideoAnalysisRetry(
    private val scope: CoroutineScope,
    private val repository: () -> VideoAnalysisRepository = { VideoAnalysisRepository.getInstance() },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    fun retry(mediaId: String): Job = scope.launch(ioDispatcher) {
        repository().reanalyze(mediaId)
    }
}

/**
 * Solo observa y propone. «Usar» modifica el borrador local; corregir lo registrado debe pasar por la puerta Room→UI.
 * La selección/completado no repite E/S: el detalle de las filas se prepara únicamente al emitir Room.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class WorkoutVideoAnalysisController(
    scope: CoroutineScope,
    sessionKeys: Flow<String?>,
    completedSets: Flow<Map<String, CompletedSet>>,
    exercises: Flow<List<Exercise>>,
    private val repository: VideoAnalysisRepository,
    private val mediaRepository: WorkoutMediaRepository,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    executionBindings: Flow<Map<String, com.example.kpkn.data.models.WorkoutSetExecutionRef>> = flowOf(emptyMap()),
    private val executionMatches: (com.example.kpkn.data.models.WorkoutSetExecutionRef) -> Boolean = { true },
    private val legacyAssociationsValid: (String) -> Boolean = { true },
) {
    private data class Entry(val row: VideoAnalysisSessionRow, val summary: VideoAnalysisSummary?, val partials: PartialSummary? = null, val sourceRef: com.example.kpkn.data.models.WorkoutSetExecutionRef? = null, val configurationId: String? = null)
    private data class Snapshot(val entries: List<Entry>, val romReferences: Map<String, Double>)

    private val snapshots = sessionKeys.distinctUntilChanged().flatMapLatest { key ->
        if (key.isNullOrBlank()) flowOf(emptyList()) else repository.observeBySessionKey(key)
    }.mapLatest { rows ->
        val entries = rows.map { row ->
            coroutineContext.ensureActive()
            val summary = repository.getByMedia(row.mediaId)
            // La confianza propia de las parciales y su zona no están en Room: salen del sidecar, y solo si hay parciales.
            val partials = if (row.status == VideoAnalysisStatus.DONE.name && (row.partialReps ?: 0) > 0) {
                summary?.reportPath?.let { repository.partialSummaryAt(it) }
            } else null
            Entry(row, summary, partials, mediaRepository.executionRefFor(row.mediaId), mediaRepository.getById(row.mediaId)?.catalogConfigurationId)
        }
        val references = HashMap<String, Double>()
        for (key in entries.mapNotNull { it.summary?.exerciseKey }.distinct()) {
            val measured = ArrayList<Pair<WorkoutMedia, Double>>()
            val trendMedia = ArrayList<WorkoutMedia>()
            for (summary in repository.observeTrend(key).first()) {
                coroutineContext.ensureActive()
                val media = mediaRepository.getById(summary.mediaId) ?: continue
                trendMedia += media
                val rom = summary.romMeanDeg?.takeIf { it.isFinite() && it > 0.0 } ?: continue
                if (summary.confidence == null || summary.confidence == CountConfidence.BAJA) continue
                measured += media to rom
            }
            // Los identificadores de ejecución y log son alias: unirlos evita contar dos veces
            // una sesión, incluso si hay varios sessionKey asociados al mismo log.
            val aliases = HashMap<String, String>()
            fun root(key: String): String {
                val parent = aliases[key] ?: return key
                return if (parent == key) key else root(parent).also { aliases[key] = it }
            }
            fun sessionAlias(media: WorkoutMedia) = media.sessionKey?.takeIf(String::isNotBlank)?.let { "session:$it" }
            fun logAlias(media: WorkoutMedia) = media.workoutLogId?.takeIf(String::isNotBlank)?.let { "log:$it" }
            for (media in trendMedia) {
                val session = sessionAlias(media) ?: continue
                val log = logAlias(media) ?: continue
                aliases[root(log)] = root(session)
            }
            val sessions = HashMap<String, Double>()
            for ((media, rom) in measured) {
                val session = (sessionAlias(media) ?: logAlias(media))?.let(::root) ?: continue
                sessions[session] = maxOf(sessions[session] ?: rom, rom)
            }
            if (sessions.size >= 2) references[key] = sessions.values.max()
        }
        Snapshot(entries, references)
    }.flowOn(ioDispatcher).shareIn(scope, SharingStarted.WhileSubscribed(5_000L), replay = 1)

    val videoAnalysisBySetKey: StateFlow<Map<String, SetVideoAnalysisUi>> = combine(snapshots, completedSets, exercises, executionBindings) { snapshot, completed, currentExercises, bindings ->
        val byExercise = currentExercises.associateBy { it.id }
        snapshot.entries.mapNotNull { entry ->
            val row = entry.row
            val exercise = byExercise[row.exerciseId] ?: return@mapNotNull null
            val ref = entry.sourceRef ?: bindings[row.mediaId]
            val index = if (ref != null) {
                if (!executionMatches(ref) || ref.exerciseOccurrenceId != (exercise.occurrenceId ?: exercise.id) ||
                    ref.configurationId != executionConfigurationId(exercise)) return@mapNotNull null
                exercise.sets.indexOfFirst { it.id == ref.setId }.takeIf { it >= 0 } ?: return@mapNotNull null
            } else {
                if (!legacyAssociationsValid(exercise.id) ||
                    (entry.configurationId == null || entry.configurationId != exercise.catalogConfigurationId)) return@mapNotNull null
                row.setIndex?.takeIf { it in exercise.sets.indices } ?: return@mapNotNull null
            }
            val side = WorkoutMediaSides.normalize(ref?.side ?: row.side, exercise)
            val key = workoutSetKey(exercise.id, index, side)
            key to entry
        }.groupBy({ it.first }, { it.second }).mapValues { (key, entries) ->
            val chronological = entries.sortedWith(compareBy<Entry> { it.row.createdAtMs }.thenBy { it.row.mediaId })
            val chosen = chronological.lastOrNull { it.row.status == VideoAnalysisStatus.DONE.name }
                ?: chronological.lastOrNull { it.row.status == VideoAnalysisStatus.RUNNING.name || it.row.status == VideoAnalysisStatus.QUEUED.name }
                ?: chronological.last()
            val exercise = byExercise.getValue(chosen.row.exerciseId!!)
            SetVideoAnalysisUi(chosen.row.mediaId, stateFor(chosen, exercise, completed[key], snapshot.romReferences, parseCompletedSetKey(key)?.setIdx ?: 0))
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000L), emptyMap())

    private fun stateFor(entry: Entry, exercise: Exercise, logged: CompletedSet?, references: Map<String, Double>, resolvedIndex: Int): SetVideoAnalysisState {
        if (logged?.skipped == true) return SetVideoAnalysisState.Unsupported("Esta serie está omitida")
        val row = entry.row
        val plannedUnit = exercise.sets.getOrNull(resolvedIndex)?.unitModeV2 ?: when (exercise.trainingMode) {
            TrainingMode.TIME -> UnitModeV2.TIME
            TrainingMode.DISTANCE -> UnitModeV2.DISTANCE
            TrainingMode.CUSTOM, TrainingMode.SOLO_RPE -> UnitModeV2.CUSTOM
            else -> UnitModeV2.REPS
        }
        // El registro es histórico: un cambio del ejercicio no cambia la unidad ya guardada.
        val unit = if (logged == null) plannedUnit else logged.recordedPayloadV3?.unitMode ?: when {
            logged.timeSeconds != null -> UnitModeV2.TIME
            logged.distanceKm != null -> UnitModeV2.DISTANCE
            else -> UnitModeV2.REPS
        }
        if (unit != UnitModeV2.REPS) {
            return SetVideoAnalysisState.Unsupported(if (unit == UnitModeV2.TIME) "Esta serie se registra por tiempo" else "Esta serie se registra en otra unidad")
        }
        if (row.status == VideoAnalysisStatus.RUNNING.name || row.status == VideoAnalysisStatus.QUEUED.name) {
            return SetVideoAnalysisState.Analyzing(row.progressPct.coerceIn(0, 100))
        }
        if (row.status == VideoAnalysisStatus.UNSUPPORTED.name) {
            val reason = entry.summary?.errorCode
            return SetVideoAnalysisState.Unsupported(reason?.let(UnsupportedReasons::textOf) ?: "Este ejercicio todavía no admite análisis de técnica")
        }
        if (row.status == VideoAnalysisStatus.FAILED.name) {
            return SetVideoAnalysisState.Failed(entry.summary?.errorCode)
        }
        if (row.status != VideoAnalysisStatus.DONE.name) return SetVideoAnalysisState.Inconclusive
        val confidence = CountConfidence.entries.firstOrNull { it.name == row.confidence }
        if (confidence == null || confidence == CountConfidence.BAJA) return SetVideoAnalysisState.Inconclusive
        val count = row.repCount?.takeIf { it >= 0 } ?: return SetVideoAnalysisState.Inconclusive
        val detected = row.partialReps?.takeIf { it >= 0 } ?: return SetVideoAnalysisState.Inconclusive
        val failed = row.failedReps?.takeIf { it >= 0 } ?: return SetVideoAnalysisState.Inconclusive
        // M4: el número de parciales tiene su propia confianza (sin ella, un informe de antes del motor 9, la de las repeticiones).
        // Con BAJA no se ofrecen: no entran en la propuesta y, al corregir, se dejan las que ya había.
        val partialConfidence = entry.partials?.confidence ?: confidence
        val partialZone = entry.partials?.zone
        val offered = if (partialConfidence == CountConfidence.BAJA) 0 else detected
        if (count == 0 && offered == 0 && failed == 0) return SetVideoAnalysisState.Inconclusive
        if (logged != null) {
            val partials = if (partialConfidence == CountConfidence.BAJA) logged.partialReps ?: 0 else detected
            val sameCounts = logged.reps == count && (logged.partialReps ?: 0) == partials
            if (sameCounts && confidence == CountConfidence.ALTA && (failed == 0 || logged.isFailure)) return SetVideoAnalysisState.Agrees(count)
            return SetVideoAnalysisState.Mismatch(
                logged.reps, count, partials, failed, confidence, logged.partialReps ?: 0, partialConfidence, partialZone,
            )
        }
        val reference = entry.summary?.exerciseKey?.let { references[it] }
        val romPercent = if (exercise.trackRom && reference != null) entry.summary?.romMeanDeg
            ?.takeIf { it.isFinite() && it > 0.0 }?.let { (it / reference * 20.0).roundToInt() * 5 } else null
        return SetVideoAnalysisState.Suggest(count, offered, failed, confidence, romPercent, partialConfidence, partialZone)
    }

    val videoThumbBadgeByMediaId: StateFlow<Map<String, VideoThumbBadge>> = snapshots.mapLatest { snapshot ->
        buildMap {
            for (entry in snapshot.entries) {
                val badge = videoThumbBadgeFor(entry.row)
                if (badge != null) {
                    put(entry.row.mediaId, badge)
                }
            }
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000L), emptyMap())
}
