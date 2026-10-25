package com.example.kpkn.domain.training.block

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.training.*
import java.time.LocalDate
import java.util.UUID
import kotlin.math.ceil

/** Nueva copia de trabajo: el programa terminado conserva íntegros su historial y sus recetas efectivas. */
object ContinuationProgramFactory {
    fun candidateId(program: Program, choice: ContinuationChoice): String? {
        val pending = program.pendingContinuation ?: return null
        val identity = "${program.id}:${pending.programRunId}:${pending.cycleNumber}:${choice.name}"
        return "continuation_${UUID.nameUUIDFromBytes(identity.toByteArray(Charsets.UTF_8))}"
    }

    fun sourceDecisionId(program: Program): String? = program.pendingContinuation?.let {
        "continuation-source:${program.id}:${it.programRunId}:${it.cycleNumber}"
    }

    fun build(program: Program, choice: ContinuationChoice, metadata: ExerciseCompositionMetadataProvider,
              today: LocalDate, nowMs: Long): Program? {
        val pending = program.pendingContinuation ?: return null
        val run = program.runState ?: return null
        if (run.status != ProgramRunStatus.COMPLETED || run.runId != pending.programRunId ||
            choice == ContinuationChoice.PICK_FROM_CATALOG) return null
        val id = candidateId(program,choice) ?: return null
        val maintenance = choice == ContinuationChoice.MAINTENANCE_WEEK
        val sourceWeeks = ProgramHierarchyIndex(program).orderedWeeks().map { it.week }
        val maintenanceSource = sourceWeeks.lastOrNull { it.executionKind == WeekExecutionKind.TRAINING && it.sessions.isNotEmpty() }
        if (maintenance && maintenanceSource == null) return null
        var counter = 0
        val ids = object : IdProvider { override fun newId() = "$id-${++counter}" }
        val seed = program.copy(id = id,name = if (maintenance) "Mantenimiento · ${program.name}" else program.name,
            isDraft = false,isPublic = false,macrocycles = emptyList(),runState = null,
            structure = if (maintenance) ProgramStructure.SIMPLE else program.structure,
            simpleProgramKind = if (maintenance) SimpleProgramKind.CYCLIC else program.simpleProgramKind,
            sourceRecipe = if (maintenance) null else program.sourceRecipe,
            sourceProtocolId = if (maintenance) null else program.sourceProtocolId,
            planProvenance = if (maintenance) null else program.planProvenance,
            effectiveWeekRecipes = emptyList(),exerciseLoadReferences = emptyList(),manualSessionOverrides = emptyList(),
            blockSplitSelections = emptyMap(),weekSplitSelections = emptyMap(),
            nativeProgressionProposals = emptyList(),nativeProgressionAudit = emptyList(),
            weeklyCheckins = emptyList(),pendingWeeklyCheckin = null,blockReviews = emptyList(),
            pendingBlockReview = null,pendingContinuation = null,resolvedPlanningRangeProposalIds = emptyList(),
            skippedSessionKeys = emptyList(),pendingMissedSessions = null,
            loops = emptyList(),loopState = null,loopOccurrences = emptyList(),events = emptyList(),keyDates = emptyList(),
            calendarization = null,calendarBreaks = emptyList(),pausedCyclicSnapshot = null,
            optionalSessionConfirmations = emptyList(),hybridOccurrenceSchedules = emptyList(),hybridAuthoredSources = emptyList(),
            timelineStartDate = today.toString(),schedulePlan = ProgramSchedulePlan(
                anchorDate = today.toString(),weekStartDay = program.startDay,mode = ScheduleMode.FLOATING),
            programDecisions = listOf(ProgramDecision(sourceDecisionId(program)!!,nowMs,"CONTINUATION",
                if (maintenance) "Semana de mantenimiento preparada" else "Nueva copia de la receta preparada",
                "Origen: ${program.name} (${program.id}), ejecución ${run.runId}. La copia se crea por elección explícita.")))
        val built = when {
            maintenance -> {
                val sessions = SplitApplicationEngine.copySessionsWithNewIds(maintenanceSource!!.sessions).map(::maintenanceSession)
                seed.copy(macrocycles = listOf(Macrocycle(ids.newId(),"Mantenimiento",listOf(Block(ids.newId(),"Mantenimiento",
                    goal = BlockGoal.CUSTOM,mesocycles = listOf(Mesocycle(ids.newId(),"Mantenimiento",weeks = listOf(
                        ProgramWeek(ids.newId(),"Semana de mantenimiento",sessions = sessions)))))))))
            }
            seed.sourceRecipe != null -> PlanMaterializer.materialize(seed,seed.sourceRecipe,metadata,ids,strict = false)
            else -> seed.copy(macrocycles = program.macrocycles.map { macro -> macro.copy(id = ids.newId(),blocks = macro.blocks.map { block ->
                block.copy(id = ids.newId(),mesocycles = block.mesocycles.map { meso -> meso.copy(id = ids.newId(),weeks = meso.weeks.map { week ->
                    week.copy(id = ids.newId(),startDate = null,endDate = null,trainingDayDates = emptyMap(),
                        sessions = SplitApplicationEngine.copySessionsWithNewIds(week.sessions).map(::withoutCompetition),
                        hybridProposal = null,hybridAppliedSchedule = null)
                }) })
            }) })
        }
        val first = ProgramHierarchyIndex(built).orderedWeeks().firstOrNull() ?: return null
        return built.copy(runState = ProgramRunState("run_$id",cycleNumber = 1,weekId = first.week.id,
            weekInstanceId = if (built.requiresNativeWeekInstances() || built.isSimpleProgram && !built.isSimpleLinearProgram)
                ProgramProgressEngine.instanceIdFor(1,first.week.id) else first.week.id,
            macrocycleId = first.macrocycleId,blockId = first.blockId,mesocycleId = first.mesocycleId,
            status = ProgramRunStatus.ACTIVE))
    }

    private fun withoutCompetition(session: Session): Session = session.copy(isMeetDay = false,isCompetitionSession = false,
        competitionRecordId = null,competitionKeyDateId = null,competitionDetails = null,competitionSportType = null,
        competitionRecordMode = null,meetResults = null,meetBodyweight = null,trainingBackup = null,
        sessionB = session.sessionB?.let(::withoutCompetition),sessionC = session.sessionC?.let(::withoutCompetition),
        sessionD = session.sessionD?.let(::withoutCompetition))

    private fun maintenanceSession(session: Session): Session {
        fun soften(exercise: Exercise): Exercise {
            if (exercise.isCardio) return exercise
            val working = exercise.sets.filterNot { it.isIneffective }
            return exercise.copy(sets = working.take(ceil(working.size / 2.0).toInt()).map { set ->
                set.copy(targetRIR = 4,targetRPE = null,intensityMode = IntensityMode.RIR,targetPercentageRM = null,
                    weight = null,loadBasis = null,purpose = SetPurpose.NONE,isFailure = false,isTopSet = false,
                    plannedTargetV2 = null,loadModeV2 = null,unitModeV2 = null,leftTarget = null,rightTarget = null,
                    amrapMinimumReps = null,calibrationScope = null,completedReps = null,completedRIR = null,completedRPE = null)
            },warmupSets = emptyList(),nativeProgressionManaged = false,apreBlock = null,loadReference = null)
        }
        val clean = withoutCompetition(session)
        return clean.copy(exercises = clean.exercises.map(::soften),parts = clean.parts.map { part -> part.copy(exercises = part.exercises.map(::soften)) },
            sessionB = clean.sessionB?.let(::maintenanceSession),sessionC = clean.sessionC?.let(::maintenanceSession),
            sessionD = clean.sessionD?.let(::maintenanceSession))
    }
}
