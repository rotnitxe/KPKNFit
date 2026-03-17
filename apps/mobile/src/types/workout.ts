/**
 * Mobile-side type definitions for the workout / program / exercise / body domains.
 *
 * These mirror the essential shape of the PWA root `types.ts` definitions.
 * They are intentionally lightweight — only the fields that mobile code
 * actually reads today are required; everything else is optional.
 * The data arrives via migration from the PWA's IndexedDB/Capacitor storage.
 */

import type { CanonicalMuscle, MuscleRole } from '@kpkn/shared-types';

// ---------------------------------------------------------------------------
// Program periodization tree
// ---------------------------------------------------------------------------

export interface ExerciseSet {
  id: string;
  targetReps?: number;
  targetDuration?: number;
  targetRPE?: number;
  targetRIR?: number;
  intensityMode?: 'rpe' | 'rir' | 'failure' | 'amrap' | 'load' | 'solo_rm';
  targetPercentageRM?: number;
  weight?: number;
  advancedTechnique?: string;
  completedReps?: number;
  completedDuration?: number;
  completedRPE?: number;
  completedRIR?: number;
  isFailure?: boolean;
  isAmrap?: boolean;
  isCalibrator?: boolean;
  performanceMode?: 'target' | 'failure' | 'failed';
  dropSets?: unknown[];
  restPauses?: unknown[];
}

export interface Exercise {
  id: string;
  name: string;
  exerciseDbId?: string;
  exerciseId?: string;
  sets: ExerciseSet[];
  warmupSets?: unknown[];
  restTime?: number;
  trainingMode?: 'reps' | 'time' | 'percent' | 'custom';
  supersetId?: string;
  notes?: string;
  isFavorite?: boolean;
  reference1RM?: number;
  isUnilateral?: boolean;
  damageProfile?: 'stretch' | 'squeeze' | 'normal';
}

export interface Session {
  id: string;
  name: string;
  description?: string;
  focus?: string;
  exercises: Exercise[];
  warmup?: unknown[];
  parts?: SessionPart[];
  dayOfWeek?: number;
  assignedDays?: number[];
}

export interface SessionPart {
  id: string;
  name: string;
  exercises: Exercise[];
  color?: string;
}

export interface ProgramWeek {
  id: string;
  name: string;
  sessions: Session[];
  variant?: 'A' | 'B' | 'C' | 'D';
}

export interface Mesocycle {
  id: string;
  name: string;
  goal: 'Acumulación' | 'Intensificación' | 'Realización' | 'Descarga' | 'Custom';
  customGoal?: string;
  weeks: ProgramWeek[];
}

export interface Block {
  id: string;
  name: string;
  mesocycles: Mesocycle[];
}

export interface ProgramEvent {
  id: string;
  title: string;
  type: string;
  calculatedWeek: number;
  date: string;
  repeatEveryXCycles?: boolean;
}

export interface Macrocycle {
  id: string;
  name: string;
  blocks?: Block[];
}

export interface Program {
  id: string;
  name: string;
  description?: string;
  mode: 'powerlifting' | 'hypertrophy' | 'powerbuilding';
  structure: 'simple' | 'complex';
  macrocycles: Macrocycle[];
  author?: string;
  tags?: string[];
  exerciseGoals?: Record<string, number>;
  volumeSystem?: 'israetel' | 'manual' | 'kpnk';
  volumeAlertsEnabled?: boolean;
  autoVolumeEnabled?: boolean;
  isDraft?: boolean;
  coverImage?: string;
  startDay?: number;
  selectedSplitId?: string;
  athleteProfile?: AthleteProfileScore;
  events?: ProgramEvent[];
}

export interface ActiveProgramState {
  programId: string;
  status: 'active' | 'paused' | 'completed';
  startDate: string;
  firstSessionDate?: string;
  queuedProgramId?: string;
  currentMacrocycleIndex: number;
  currentBlockIndex: number;
  currentMesocycleIndex: number;
  currentWeekId: string;
}

// ---------------------------------------------------------------------------
// Exercise catalog
// ---------------------------------------------------------------------------

export interface InvolvedMuscle {
  muscle: CanonicalMuscle | string;
  role: MuscleRole;
  activation?: number;
  emphasis?: string;
}

export type ExerciseCatalogEntry = ExerciseMuscleInfo;

export interface ExerciseMuscleInfo {
  id: string;
  name: string;
  alias?: string;
  description: string;
  involvedMuscles: InvolvedMuscle[];
  category: string;
  type: 'Básico' | 'Accesorio' | 'Aislamiento';
  tier?: 'T1' | 'T2' | 'T3';
  equipment: string;
  force: string;
  isCustom?: boolean;
  bodyPart?: 'upper' | 'lower' | 'full';
  isFavorite?: boolean;
  variantOf?: string;
  efc?: number;
  ssc?: number;
  cnc?: number;
  ttc?: number;
}

export interface ExercisePlaylist {
  id: string;
  name: string;
  exerciseIds: string[];
}

export interface MuscleGroupInfo {
  id: string;
  name: string;
  description: string;
  importance: { movement: string; health: string };
  volumeRecommendations: { mev: string; mav: string; mrv: string };
  coverImage?: string;
  recommendedExercises?: string[];
  favoriteExerciseId?: string;
}

export type MuscleSubGroup = string | { [key: string]: string[] };

export interface MuscleHierarchy {
  bodyPartHierarchy: Record<string, MuscleSubGroup[]>;
  specialCategories: Record<string, string[]>;
  muscleToBodyPart: Record<string, string>;
}

// ---------------------------------------------------------------------------
// Body / progress
// ---------------------------------------------------------------------------

export interface BodyProgressEntry {
  id: string;
  date: string;
  weight?: number;
  bodyFatPercentage?: number;
  muscleMassPercentage?: number;
  bodyFatQuality?: 'measured' | 'estimated';
  muscleMassQuality?: 'measured' | 'estimated';
  measurements?: Record<string, number>;
  photos?: string[];
  aiInsight?: string;
}

export interface BodyLabAnalysis {
  profileTitle: string;
  profileSummary: string;
  strongPoints: { muscle: string; reason: string }[];
  weakPoints: { muscle: string; reason: string }[];
  recoveryAnalysis: { score: number; summary: string };
  frequencyAnalysis: { preferredType: string; summary: string };
  recommendations: { title: string; description: string }[];
}

export interface BiomechanicalData {
  height: number;
  wingspan: number;
  torsoLength: number;
  femurLength: number;
  tibiaLength: number;
  humerusLength: number;
  forearmLength: number;
}

export interface BiomechanicalAnalysis {
  apeIndex: { value: number; interpretation: string };
  advantages: { title: string; explanation: string }[];
  challenges: { title: string; explanation: string }[];
  exerciseSpecificRecommendations: { exerciseName: string; recommendation: string }[];
}

// ---------------------------------------------------------------------------
// Workout Logging & Session State (1:1 Parity)
// ---------------------------------------------------------------------------

export type SetTypeLabel = 'W' | 'T' | 'F' | 'D';

export interface OngoingWorkoutState {
    programId: string;
    session: Session;
    isPaused?: boolean;
    startTime: number;
    activeExerciseId: string | null;
    activeSetId: string | null;
    setTypeOverrides?: Record<string, SetTypeLabel>;
    completedSets: Record<string, OngoingSetData | { left: OngoingSetData | null, right: OngoingSetData | null }>;
    dynamicWeights: Record<string, { consolidated?: number, technical?: number }>;
    sessionAdjusted1RMs?: Record<string, number>;
    selectedBrands?: Record<string, string>;
    lastPR?: { exerciseName: string, weight: number, reps: number, e1RM: number } | null;
}


export interface OngoingSetData {
    weight: number;
    reps?: number;
    rpe?: number;
    rir?: number;
    isFailure?: boolean;
    isIneffective?: boolean;
    duration?: number;
    machineBrand?: string;
    isPartial?: boolean;
    partialReps?: number;
    dropSets?: DropSetData[];
    restPauses?: RestPauseData[];
    isAmrap?: boolean;
    isCalibrator?: boolean;
    amrapReps?: number;
    isChangeOfPlans?: boolean;
    performanceMode?: 'target' | 'failure' | 'failed';
}


export interface DropSetData {
    weight: number;
    reps: number;
}

export interface RestPauseData {
    restTime: number;
    reps: number;
}

export interface SetInputState {
    reps: string;
    weight: string;
    rpe: string;
    rir: string;
    isFailure?: boolean;
    isIneffective?: boolean;
    isPartial?: boolean;
    duration?: string;
    advancedTechnique?: string;
    partialReps: string;
}

export interface UnilateralSetInputs {
    left: SetInputState;
    right: SetInputState;
}

export interface AthleteProfileScore {
    trainingStyle: 'Bodybuilder' | 'Powerbuilder' | 'Powerlifter';
    technicalScore: number;
    consistencyScore: number;
    strengthScore: number;
    mobilityScore: number;
    totalScore: number;
    profileLevel: 'Beginner' | 'Advanced';
}

export type SplitTag = 'Recomendado por KPKN' | 'Alta Frecuencia' | 'Baja Frecuencia' | 'Balanceado' | 'Alto Volumen' | 'Alta Tolerancia' | 'Personalizado' | 'Powerlifting';

export interface SplitTemplate {
    id: string;
    name: string;
    description: string;
    tags: SplitTag[];
    pattern: string[];
    difficulty: 'Principiante' | 'Intermedio' | 'Avanzado';
    pros: string[];
    cons: string[];
}

export interface ProgramTemplateOption {
    id: string;
    name: string;
    description: string;
    type: 'simple' | 'complex';
    weeks: number;
    iconType: 'trending' | 'barchart' | 'star';
    blockNames?: string[];
    defaultBlockDurations?: number[];
}

export interface DetailedMuscleVolumeAnalysis {
    muscleGroup: string;
    displayVolume: number;
    totalSets: number;
    frequency: number;
    indirectFrequency: number;
    avgRestDays: number | null;
    directExercises: { name: string; sets: number }[];
    indirectExercises: { name: string; sets: number; activationPercentage: number }[];
    avgIFI: number | null;
    recoveryStatus: 'N/A' | 'Recovered' | 'UnderRecovered';
}

export interface WorkoutLog {
    id: string;
    programId: string;
    programName: string;
    sessionId: string;
    sessionName: string;
    date: string;
    duration?: number;
    completedExercises: CompletedExercise[];
    notes?: string;
    discomforts?: string[];
    fatigueLevel: number;
    mentalClarity: number;
    gymName?: string;
    focus?: number;
    pump?: number;
    sessionDifficulty?: number;
    muscleBatteries?: Record<string, number>;
}

export interface CompletedExercise {
    exerciseId: string;
    exerciseDbId?: string;
    exerciseName: string;
    sets: CompletedSet[];
    machineBrand?: string;
}

export interface CompletedSet extends ExerciseSet {
    weight: number;
    side?: 'left' | 'right';
}
