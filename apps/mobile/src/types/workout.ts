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
// Settings & Config Types
// ---------------------------------------------------------------------------

export type WeightUnit = 'kg' | 'lbs';
export type HapticIntensity = 'soft' | 'medium' | 'heavy';
export type OneRMFormula = 'brzycki' | 'epley' | 'lander';
export type IntensityLevel = 'light' | 'moderate' | 'high';

export interface CalorieGoalConfig {
  formula: 'mifflin' | 'harris' | 'katch';
  activityLevel: number;
  goal: 'lose' | 'maintain' | 'gain';
  weeklyChangeKg?: number;
  healthMultiplier?: number;
  customActivityFactor?: number;
  activityDaysPerWeek?: number;
  activityHoursPerDay?: number;
}

export type NutritionGoalMetric = 'weight' | 'bodyFat' | 'muscleMass';
export type NutritionRiskSeverity = 'info' | 'warning' | 'danger';
export type NutritionTrendStatus = 'on_track' | 'behind' | 'ahead' | 'unknown';

export interface NutritionGoal {
  metric: NutritionGoalMetric;
  value: number;
  label: string;
  unit: string;
  priority: 'primary' | 'secondary';
}

export interface NutritionRiskFlag {
  id: string;
  code: string;
  severity: NutritionRiskSeverity;
  message: string;
  hardStop?: boolean;
}

export interface NutritionCalculationSnapshot {
  formula: CalorieGoalConfig['formula'];
  activityFactor: number;
  bmr: number | null;
  tdee: number | null;
  calorieTarget: number;
  generatedAt: string;
}

export interface NutritionProjection {
  etaDate: string | null;
  trendStatus: NutritionTrendStatus;
  weeklyDelta: number | null;
  confidence: number;
}

// ---------------------------------------------------------------------------
// Periodización & Perfil de Atleta
// ---------------------------------------------------------------------------

export interface AthleteProfile {
  technicalScore: 1 | 2 | 3;
  consistencyScore: 1 | 2 | 3;
  strengthStandard: 1 | 2 | 3;
  recoveryCapacity: -1 | 0 | 1;
}

export type ExperienceClassification = 'beginner' | 'intermediate' | 'advanced';

export type TrainingPhase =
  | 'accumulation'
  | 'transformation'
  | 'realization';

export type IntensityTier =
  | 'failure'
  | 'rpe_8_9'
  | 'rpe_6_7';

export interface VolumeRecommendation {
  muscleGroup: string;
  minEffectiveVolume: number;
  maxAdaptiveVolume: number;
  maxRecoverableVolume: number;
  frequencyCap: number;
}

export interface VolumeRecSnapshot {
  minEffectiveVolume: number;
  maxAdaptiveVolume: number;
  maxRecoverableVolume: number;
}

export interface VolumeCalibrationEntry {
  date: string;
  source: 'auto' | 'manual';
  changes: { muscle: string; prev: VolumeRecSnapshot; next: VolumeRecSnapshot; reason: string }[];
}

export interface CoverStyle {
  filters?: {
    contrast: number;
    saturation: number;
    brightness: number;
    grayscale: number;
    sepia: number;
    vignette: number;
  };
  enableMotion?: boolean;
  labelPosition?: 'bottom-left' | 'center' | 'bottom-center';
}

export interface SessionBackground {
  type: 'color' | 'image';
  value: string;
  style?: {
    blur?: number;
    brightness?: number;
  };
}

// ---------------------------------------------------------------------------
// Loops & Eventos de Programa
// ---------------------------------------------------------------------------

export type LoopType = '1rm_test' | 'deload' | 'competition' | 'custom';

export interface Loop {
  id: string;
  title: string;
  type: LoopType;
  repeatEveryXLoops: number;
  durationType: 'day' | 'week';
  dayOfWeek?: number;
  durationWeeks?: number;
  priority?: number;
  sessions?: Session[];
  color?: string;
}

export interface LoopActivation {
  loopId: string;
  cycle: number;
  status: 'scheduled' | 'active' | 'completed' | 'postponed' | 'cancelled';
  postponedTo?: number;
}

export interface LoopTemplate {
  id: string;
  name: string;
  emoji: string;
  description: string;
  tags: string[];
  weeks: number;
  splitId?: string;
  loops: Loop[];
  weekConfigs?: { weekIndex: number; focus?: string; intensityModifier?: number }[];
}

export type KeyDateType = 'competition' | 'exam_week' | 'vacation' | 'custom';

export interface KeyDate {
  id: string;
  title: string;
  type: KeyDateType;
  targetDate: string;
  durationType: 'day' | 'week';
  durationDays?: number;
  weekIndex?: number;
  dayOfWeek?: number;
  sessions?: Session[];
  truncateWeek?: boolean;
}

export type ProtocolId = 'gzcl' | '531' | 'juggernaut' | 'westside' | 'rts' | 'custom';

export interface Protocol {
  id: string;
  protocolId: ProtocolId;
  name: string;
  emoji: string;
  description: string;
  author?: string;
  tags: string[];
  sessionCategories: string[];
  blocks: {
    name: string;
    weeks: number;
    goal: Mesocycle['goal'];
    intensityRange?: [number, number];
    volumeModifier?: number;
  }[];
  defaultSplit?: string;
}

export interface MicroProgramRule {
  id: string;
  scope: 'session' | 'day' | 'week';
  activateEveryXLoops: number;
  durationLoops: number;
  targetSessionIds?: string[];
  targetDayOfWeek?: number;
  action: 'swap_main' | 'activate_secondary' | 'deactivate';
}

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
  isIneffective?: boolean;
  isPartial?: boolean;
  partialReps?: number;
  isAmrap?: boolean;
  isCalibrator?: boolean;
  machineBrand?: string;
  isChangeOfPlans?: boolean;
  dropSets?: DropSetData[];
  restPauses?: RestPauseData[];
  performanceMode?: 'target' | 'failure' | 'failed';
  technicalWeight?: number;
  consolidatedWeight?: number;
  attemptResult?: 'good' | 'no-lift' | 'pending';
  judgingLights?: [boolean | null, boolean | null, boolean | null];
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
  customUnit?: string;
  reference1RM?: number;
  targetSessionGoal?: string;
  isStarTarget?: boolean;
  trackHeartRate?: boolean;
  setupDetails?: {
    seatPosition?: string;
    pinPosition?: string;
    equipmentNotes?: string;
  };
  supersetId?: string;
  variantName?: string;
  prFor1RM?: { weight: number; reps: number };
  consolidatedWeight?: { weightKg: number; reps: number };
  brandEquivalencies?: BrandEquivalency[];
  isUnilateral?: boolean;
  isCalibratorAmrap?: boolean;
  goal1RM?: number;
  calculated1RM?: number;
  damageProfile?: 'stretch' | 'squeeze' | 'normal';
  isCompetitionLift?: boolean;
  isFavorite?: boolean;
  notes?: string;
}

export interface Session {
  id: string;
  name: string;
  description?: string;
  focus?: string;
  exercises: Exercise[];
  warmup?: WarmupExercise[];
  parts?: SessionPart[];
  background?: SessionBackground;
  coverStyle?: CoverStyle;
  dayOfWeek?: number;
  scheduleLabel?: string;
  assignedDays?: number[];
  sessionB?: Session;
  sessionC?: Session;
  sessionD?: Session;
  isMeetDay?: boolean;
  isMainSession?: boolean;
  microProgram?: {
    enabled: boolean;
    everyXCycles: number;
    isMainInCycle: boolean;
    rules?: MicroProgramRule[];
  };
  meetBodyweight?: number;
  meetResults?: {
    placement?: string;
    total?: number;
    dots?: number;
    awards?: string[];
  };
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
  events?: Array<{
    id?: string;
    title: string;
    type?: string;
  }>;
  isLoopWeek?: boolean;
  loopId?: string;
}

export interface WarmupExercise {
  id: string;
  name: string;
  description?: string;
  category?: string;
  duration?: number;
  sets?: number;
  reps?: string;
}

export interface WarmupSetDefinition {
  id: string;
  percentageOfWorkingWeight: number;
  targetReps: number;
  matchRPE?: number;
}

export interface BrandEquivalency {
  brand: string;
  pr?: { weight: number; reps: number; e1rm: number };
}

export interface SkippedWorkoutLog {
  id: string;
  date: string;
  programId: string;
  sessionId: string;
  sessionName: string;
  programName: string;
  reason: 'sick' | 'vacation' | 'gym_closed' | 'other' | 'skip';
  notes?: string;
}

export interface Mesocycle {
  id: string;
  name: string;
  goal: 'Acumulación' | 'Intensificación' | 'Realización' | 'Descarga' | 'Custom';
  customGoal?: string;
  weeks: ProgramWeek[];
}
export type ProgramMesocycle = Mesocycle;

export interface Block {
  id: string;
  name: string;
  mesocycles: Mesocycle[];
}
export type ProgramBlock = Block;

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
  volumeRecommendations?: VolumeRecommendation[];
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
  subMuscleGroup?: string;
  category: string;
  type: 'Básico' | 'Accesorio' | 'Aislamiento';
  tier?: 'T1' | 'T2' | 'T3';
  equipment: string;
  force: string;
  isCustom?: boolean;
  bodyPart?: 'upper' | 'lower' | 'full';
  chain?: 'anterior' | 'posterior' | 'full';
  isFavorite?: boolean;
  variantOf?: string;
  sfr?: { score: number; justification: string };
  setupTime?: number;
  averageRestSeconds?: number;
  coreInvolvement?: 'high' | 'medium' | 'low';
  bracingRecommended?: boolean;
  strapsRecommended?: boolean;
  bodybuildingScore?: number;
  functionalTransfer?: string;
  efc?: number;
  ssc?: number;
  cnc?: number;
  ttc?: number;
  axialLoadFactor?: number;
  postureFactor?: number;
  technicalDifficulty?: number;
  injuryRisk?: { level: number; details: string };
  transferability?: number;
  recommendedMobility?: string[];
  resistanceProfile?: {
    curve?: string;
    peakTensionPoint?: string;
    description?: string;
  };
  commonMistakes?: { mistake: string; correction: string }[];
  progressions?: { name: string; description: string }[];
  regressions?: { name: string; description: string }[];
  anatomicalConsiderations?: { trait: string; advice: string }[];
  periodizationNotes?: { phase: string; suitability: number; notes: string }[];
  primeStars?: { score: number; justification: string };
  communityOpinion?: string[];
  aiCoachAnalysis?: { summary: string; pros: string[]; cons: string[] };
  images?: string[];
  videos?: string[];
  userRating?: number;
  setupDetails?: {
    seatPosition?: string;
    pinPosition?: string;
    equipmentNotes?: string;
  };
  brandEquivalencies?: BrandEquivalency[];
  repDebtHistory?: Record<string, number>;
  damageProfile?: 'stretch' | 'squeeze' | 'normal';
  calculated1RM?: number;
  last1RMTestDate?: string;
  setupCues?: string[];
  executionCues?: string[];
  isHallOfFame?: boolean;
  sportsRelevance?: string[];
  baseIFI?: number;
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
