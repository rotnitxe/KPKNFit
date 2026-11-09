package com.example.kpkn.screens.programs.importer

import com.example.kpkn.data.importer.ImportedEditorDocument
import com.example.kpkn.data.importer.ImportedEditorDraftPersistence
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramSchedulePlan
import com.example.kpkn.data.models.Session
import com.example.kpkn.domain.training.importer.ImportedProgramDraft
import com.example.kpkn.domain.training.importer.ImportedProgramBuilder
import com.example.kpkn.domain.training.importer.ImportedProgramSourceMap
import com.example.kpkn.domain.training.importer.ImportSourceEvidence
import com.example.kpkn.domain.training.importer.ImportWizardPage
import com.example.kpkn.domain.training.importer.ImportWizardSheet
import com.example.kpkn.screens.sessioneditor.ImportedProgramEditorStore
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Borrador durable de importación; el Program opcional solo existe en borradores de versiones anteriores. */
internal class ImportedEditorDraftSession private constructor(
    initial: ImportedEditorDocument,
    private val persistence: ImportedEditorDraftPersistence,
) {
    private val mutex = Mutex()
    private var closed = false
    private val _document = MutableStateFlow(initial)
    val document: StateFlow<ImportedEditorDocument> = _document.asStateFlow()

    private var programStoreValue: ImportedProgramEditorStore? = initial.program?.let(::createProgramStore)
    val programStore: ImportedProgramEditorStore
        get() = checkNotNull(programStoreValue) { "Este borrador aún no tiene un programa canónico." }

    private fun createProgramStore(initialProgram: Program) = ImportedProgramEditorStore(initialProgram) { program ->
        mutex.withLock {
            if (closed) return@withLock false
            val before = _document.value
            val (source, resolutions) = sourceWithEditorSelections(
                before.source, before.resolutions, before.sourceMap, program,
            )
            val next = before.copy(
                revision = before.revision + 1,
                program = program,
                title = program.name,
                weekdays = program.schedulePlan?.trainingDays.orEmpty(),
                source = source,
                resolutions = resolutions,
            )
            if (persistence.write(next, before.revision).isFailure) return@withLock false
            _document.value = next
            true
        }
    }

    /** Saves review edits before the full editor becomes canonical. */
    suspend fun updateReview(
        source: ImportedProgramDraft,
        resolutions: Map<String, String?>,
        title: String,
        weekdays: Set<Int>,
        wizardPage: ImportWizardPage? = null,
        wizardSheet: ImportWizardSheet? = null,
        sourceEvidence: ImportSourceEvidence? = null,
    ): Boolean = mutex.withLock {
        if (closed) return@withLock false
        val before = _document.value
        if (before.editingStarted) return@withLock false
        val normalizedTitle = title.ifBlank { ImportedProgramBuilder.DEFAULT_TITLE }
        val retainedLegacyProgram = before.program?.copy(name = normalizedTitle)
            ?.withImportedWeekdays(weekdays, explicitImportedSessionIds(source, before.sourceMap))
        val next = before.copy(
            revision = before.revision + 1,
            source = source,
            resolutions = resolutions,
            title = title,
            weekdays = weekdays,
            wizardPage = wizardPage ?: before.wizardPage,
            wizardSheet = wizardSheet,
            sourceEvidence = sourceEvidence ?: before.sourceEvidence,
            program = retainedLegacyProgram,
        )
        if (next == before.copy(revision = next.revision)) return@withLock true
        if (persistence.write(next, before.revision).isFailure) return@withLock false
        _document.value = next
        true
    }

    /** Persists navigation and source-evidence changes without rewriting the canonical program. */
    suspend fun updateWorkflow(
        wizardPage: ImportWizardPage,
        wizardSheet: ImportWizardSheet?,
        sourceEvidence: ImportSourceEvidence,
    ): Boolean = mutex.withLock {
        if (closed) return@withLock false
        val before = _document.value
        val next = before.copy(
            revision = before.revision + 1,
            wizardPage = wizardPage,
            wizardSheet = wizardSheet,
            sourceEvidence = sourceEvidence,
        )
        if (next.wizardPage == before.wizardPage && next.wizardSheet == before.wizardSheet && next.sourceEvidence == before.sourceEvidence) {
            return@withLock true
        }
        if (persistence.write(next, before.revision).isFailure) return@withLock false
        _document.value = next
        true
    }

    /** Keeps the weekly schedule and its source-day assignments in the same durable revision. */
    suspend fun updateImportedWeekdays(assignments: Map<String, Int?>): Boolean {
        val store = programStoreValue ?: return mutex.withLock {
            if (closed) return@withLock false
            val before = _document.value
            val updatedSource = before.source.copy(weeks = before.source.weeks.map { week ->
                week.copy(days = week.days.map { day ->
                    val assigned = assignments[day.id]?.takeIf { it in 1..7 }
                    if (day.isRestDay || day.id !in assignments) day else day.copy(dayOfWeek = assigned)
                })
            })
            val weekdays = updatedSource.weeks.flatMap { it.days }
                .filterNot { it.isRestDay }
                .mapNotNull { it.dayOfWeek?.takeIf { day -> day in 1..7 } }
                .toSet()
            val next = before.copy(revision = before.revision + 1, source = updatedSource, weekdays = weekdays)
            if (persistence.write(next, before.revision).isFailure) return@withLock false
            _document.value = next
            true
        }
        return store.mutateThrough(
            transform = { program ->
                val bySessionId = _document.value.sourceMap.sessionIdsByDayId.mapNotNull { (dayId, sessionId) ->
                    if (dayId in assignments) sessionId to assignments[dayId]?.takeIf { it in 1..7 } else null
                }.toMap()
                program.copy(macrocycles = program.macrocycles.map { macro ->
                    macro.copy(blocks = macro.blocks.map { block ->
                        block.copy(mesocycles = block.mesocycles.map { meso ->
                            meso.copy(weeks = meso.weeks.map { week ->
                                week.copy(sessions = week.sessions.map { session ->
                                    if (session.id !in bySessionId) session else session.copy(
                                        dayOfWeek = bySessionId[session.id],
                                        assignedDays = listOfNotNull(bySessionId[session.id]),
                                    )
                                })
                            })
                        })
                    })
                })
            },
            forcePersist = true,
        ) { program ->
            mutex.withLock {
                if (closed) return@withLock false
                val before = _document.value
                val updatedSource = before.source.copy(weeks = before.source.weeks.map { week ->
                    week.copy(days = week.days.map { day ->
                        val assigned = assignments[day.id]?.takeIf { it in 1..7 }
                        if (day.isRestDay || day.id !in assignments) day else day.copy(dayOfWeek = assigned)
                    })
                })
                val weekdays = updatedSource.weeks.flatMap { it.days }
                    .filterNot { it.isRestDay }
                    .mapNotNull { it.dayOfWeek?.takeIf { day -> day in 1..7 } }
                    .toSet()
                val programWeekdays = weekdays
                val nextProgram = program.copy(
                    startDay = programWeekdays.minOrNull(),
                    weekDays = programWeekdays.size.takeIf { it > 0 },
                    schedulePlan = if (programWeekdays.isEmpty()) null else {
                        ProgramSchedulePlan(
                            weekStartDay = programWeekdays.minOrNull() ?: 1,
                            trainingDays = programWeekdays,
                        )
                    },
                )
                val next = before.copy(
                    revision = before.revision + 1,
                    source = updatedSource,
                    weekdays = weekdays,
                    program = nextProgram,
                )
                if (persistence.write(next, before.revision).isFailure) return@withLock false
                _document.value = next
                true
            }
        }
    }

    /** Solo marca un aviso existente; no transforma ni descarta el texto original. */
    suspend fun reviewWarning(id: String): Boolean = mutex.withLock {
        if (closed) return@withLock false
        val before = _document.value
        if (before.source.warnings.none { it.id == id }) return@withLock false
        if (id in before.source.reviewedWarnings) return@withLock true
        withContext(NonCancellable) {
            val next = before.copy(
                revision = before.revision + 1,
                source = before.source.copy(reviewedWarnings = before.source.reviewedWarnings + id),
            )
            if (persistence.write(next, before.revision).isFailure) return@withContext false
            _document.value = next
            true
        }
    }

    /** Marks a session reviewed only after the shared editor has successfully saved and closed. */
    suspend fun markSessionReviewed(sessionId: String): Boolean = mutex.withLock {
        if (closed) return@withLock false
        val before = _document.value
        val exists = before.program?.macrocycles.orEmpty().any { macro ->
            macro.blocks.any { block ->
                block.mesocycles.any { meso ->
                    meso.weeks.any { week -> week.sessions.any { it.id == sessionId } }
                }
            }
        }
        if (!exists) return@withLock false
        if (sessionId in before.reviewedSessionIds) return@withLock true
        withContext(NonCancellable) {
            val next = before.copy(
                revision = before.revision + 1,
                reviewedSessionIds = before.reviewedSessionIds + sessionId,
            )
            if (persistence.write(next, before.revision).isFailure) return@withContext false
            _document.value = next
            true
        }
    }

    /** Persists the completed library commit before attempting draft-file cleanup. */
    suspend fun markSaved(programId: String): Boolean = withContext(NonCancellable) {
        mutex.withLock {
            if (closed) return@withLock false
            val before = _document.value
            if (before.savedProgramId == programId) return@withLock true
            val next = before.copy(revision = before.revision + 1, savedProgramId = programId)
            if (persistence.write(next, before.revision).isFailure) return@withLock false
            _document.value = next
            true
        }
    }

    /** Retires an older session after a successful compare-and-replace without deleting its replacement. */
    suspend fun retire() = withContext(NonCancellable) {
        programStoreValue?.closeForMutations()
        mutex.withLock { closed = true }
    }

    /** Evita escrituras tardías antes de eliminar exactamente la revisión que se está descartando. */
    suspend fun discard(): Result<Boolean> = withContext(NonCancellable) {
        programStoreValue?.closeForMutations()
        mutex.withLock {
            closed = true
            val current = _document.value
            persistence.delete(current.id, current.revision)
        }
    }

    private fun sourceWithEditorSelections(
        source: ImportedProgramDraft,
        resolutions: Map<String, String?>,
        sourceMap: ImportedProgramSourceMap,
        program: Program,
    ): Pair<ImportedProgramDraft, Map<String, String?>> {
        val exercisesById = program.macrocycles.flatMap { macro ->
            macro.blocks.flatMap { block ->
                block.mesocycles.flatMap { meso ->
                    meso.weeks.flatMap { week -> week.sessions.flatMap(Session::allExercises) }
                }
            }
        }.associateBy(Exercise::id)
        val updated = source.copy(weeks = source.weeks.map { week ->
            week.copy(days = week.days.map { day ->
                day.copy(exercises = day.exercises.map exercise@ { imported ->
                    val programId = sourceMap.programExerciseIdsBySourceExerciseId[imported.id] ?: return@exercise imported
                    val exercise = exercisesById[programId] ?: return@exercise imported
                    imported.copy(catalogSelectionId = catalogIdentity(exercise))
                })
            })
        })
        val nextResolutions = resolutions.toMutableMap()
        val grouped = updated.weeks.flatMap { week -> week.days.flatMap { it.exercises } }.groupBy { it.rawName }
        grouped.forEach { (name, occurrences) ->
            val mapped = occurrences.mapNotNull { imported ->
                val programId = sourceMap.programExerciseIdsBySourceExerciseId[imported.id] ?: return@mapNotNull null
                val exercise = exercisesById[programId] ?: return@mapNotNull null
                catalogIdentity(exercise)
            }
            val mappedCount = occurrences.count { imported ->
                sourceMap.programExerciseIdsBySourceExerciseId[imported.id]?.let(exercisesById::containsKey) == true
            }
            if (mappedCount == occurrences.size && mapped.size == occurrences.size && mapped.distinct().size == 1) {
                nextResolutions[name] = mapped.single()
            } else if (mappedCount == occurrences.size) {
                if (!resolutions.containsKey(name) || resolutions[name] != null || mapped.size == occurrences.size) {
                    nextResolutions.remove(name)
                } else {
                    // Keep an explicit «propio» decision when SessionEditor preserves rows without catalog identity.
                    nextResolutions[name] = null
                }
            }
        }
        return updated to nextResolutions
    }

    companion object {
        suspend fun create(persistence: ImportedEditorDraftPersistence, initial: ImportedEditorDocument): Result<ImportedEditorDraftSession> =
            persistence.write(initial, null).map { ImportedEditorDraftSession(initial, persistence) }

        suspend fun replace(
            persistence: ImportedEditorDraftPersistence,
            initial: ImportedEditorDocument,
            expectedId: String,
            expectedRevision: Long,
        ): Result<ImportedEditorDraftSession> =
            persistence.replace(initial, expectedId, expectedRevision).map { ImportedEditorDraftSession(initial, persistence) }

        suspend fun restore(persistence: ImportedEditorDraftPersistence): Result<ImportedEditorDraftSession?> =
            persistence.read().map { it?.let { document -> ImportedEditorDraftSession(document, persistence) } }
    }
}
