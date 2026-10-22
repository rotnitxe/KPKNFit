package com.example.kpkn.screens.workout

import kotlinx.serialization.Serializable
import r4.jvm.CompileOnlySerializer
import r4.jvm.unavailable

/*
 * Compile-only replacements for the five UI snapshot type slots in
 * data/models/WorkoutLog.kt (OngoingWorkoutState).
 * Their real definitions are in WorkoutSessionContracts.kt/WorkoutUiModels.kt.
 * Those files pull voice/Compose state into the JVM closure and are excluded.
 * Included AUGE tests neither construct nor serialize these types. Constructors
 * AND serializers record an error and throw, so their schema is never simulated.
 */
@Serializable(with = WorkoutSetDraftJvmSerializer::class)
class WorkoutSetDraft { init { unavailable("WorkoutSetDraft constructor") } }
object WorkoutSetDraftJvmSerializer : CompileOnlySerializer<WorkoutSetDraft>("WorkoutSetDraft")

@Serializable(with = PreparationReportJvmSerializer::class)
class PreparationReport { init { unavailable("PreparationReport constructor") } }
object PreparationReportJvmSerializer : CompileOnlySerializer<PreparationReport>("PreparationReport")

@Serializable(with = WorkoutRestModalStateJvmSerializer::class)
class WorkoutRestModalState { init { unavailable("WorkoutRestModalState constructor") } }
object WorkoutRestModalStateJvmSerializer : CompileOnlySerializer<WorkoutRestModalState>("WorkoutRestModalState")

@Serializable(with = FinishResumeSnapshotJvmSerializer::class)
class FinishResumeSnapshot { init { unavailable("FinishResumeSnapshot constructor") } }
object FinishResumeSnapshotJvmSerializer : CompileOnlySerializer<FinishResumeSnapshot>("FinishResumeSnapshot")

@Serializable(with = GodModeUndoSnapshotJvmSerializer::class)
class GodModeUndoSnapshot { init { unavailable("GodModeUndoSnapshot constructor") } }
object GodModeUndoSnapshotJvmSerializer : CompileOnlySerializer<GodModeUndoSnapshot>("GodModeUndoSnapshot")
