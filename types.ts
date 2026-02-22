
// types.ts
import { z } from 'zod';
import React from 'react';

export type WeightUnit = 'kg' | 'lbs';
export type HapticIntensity = 'soft' | 'medium' | 'heavy';
export type OneRMFormula = 'brzycki' | 'epley' | 'lander';

export interface CalorieGoalConfig {
    formula: 'mifflin' | 'harris' | 'katch';
    activityLevel: number;
    goal: 'lose' | 'maintain' | 'gain';
    weeklyChangeKg?: number;
    healthMultiplier?: number;
}

// --- NUEVO: Tipos para el Algoritmo KPKN (Informe v3) ---
export interface AthleteProfileScore {
  technicalScore: 1 | 2 | 3;
  consistencyScore: 1 | 2 | 3;
  strengthScore: 1 | 2 | 3;
  mobilityScore: 1 | 2 | 3;
  // --- TÉRMINOS EXACTOS SOLICITADOS ---
  trainingStyle: 'Bodybuilder' | 'Powerbuilder' | 'Powerlifter';
  // ------------------------------------
  totalScore: number;
  profileLevel: 'Beginner' | 'Advanced';
}

export type View = 
  | 'home' 
  | 'programs' 
  | 'program-detail' 
  | 'program-editor' 
  | 'session-editor' 
  | 'workout' 
  | 'progress' 
  | 'settings' 
  | 'coach' 
  | 'log-hub' 
  | 'achievements' 
  | 'log-workout' 
  | 'kpkn' 
  | 'ai-art-studio' 
  | 'body-lab' 
  | 'mobility-lab' 
  | 'training-purpose' 
  | 'exercise-database' 
  | 'food-database' 
  | 'smart-meal-planner' 
  | 'exercise-detail' 
  | 'muscle-group-detail' 
  | 'body-part-detail' 
  | 'muscle-category' 
  | 'chain-detail' 
  | 'hall-of-fame' 
  | 'joint-detail'
  | 'tendon-detail'
  | 'movement-pattern-detail'
  | 'wiki-home'
  | 'nutrition' 
  | 'food-detail' 
  | 'session-detail'
  | 'tasks'
  | 'social-feed'
  | 'athlete-profile'
  | 'recovery'
  | 'sleep';

// --- LÓGICA DE VOLUMEN V3 (INFORME TÉCNICO) ---

// Módulo 1: La Matriz de Input
export interface AthleteProfile {
    technicalScore: 1 | 2 | 3;      // 1: Inestable, 2: Estable, 3: Depurada
    consistencyScore: 1 | 2 | 3;    // 1: Irregular, 2: Regular, 3: Constante
    strengthStandard: 1 | 2 | 3;    // 1: Novato, 2: Intermedio, 3: Elite
    recoveryCapacity: -1 | 0 | 1;   // -1: Mala, 0: Normal, 1: Buena
}

// Clasificación resultante del algoritmo
export type ExperienceClassification = 'beginner' | 'intermediate' | 'advanced';

// Módulo 2: Factores de Modulación
export type TrainingPhase = 
    | 'accumulation'    // F_Fase = 1.0 (Hipertrofia Base)
    | 'transformation'  // F_Fase = 0.75 (Fuerza/Hibrido)
    | 'realization';    // F_Fase = 0.50 (Peaking/Taper)

export type IntensityTier = 
    | 'failure'   // RPE 10 (F_Int = 0.6)
    | 'rpe_8_9'   // RPE 8-9 (F_Int = 1.0)
    | 'rpe_6_7';  // RPE 6-7 (F_Int = 1.2)

// Estructura para guardar el cálculo final por músculo
export interface VolumeRecommendation {
    muscleGroup: string;
    minEffectiveVolume: number; // MEV Personalizado
    maxAdaptiveVolume: number;  // MAV Personalizado (El Target)
    maxRecoverableVolume: number; // MRV Personalizado
    frequencyCap: number;       // Límite de sesiones por semana para este músculo
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

export interface Settings {
  hasSeenWelcome: boolean;
  hasSeenHomeTour: boolean;
  hasSeenProgramEditorTour: boolean;
  hasSeenSessionEditorTour: boolean;
  hasSeenKPKNTour: boolean;
  
  // Perfil
  username?: string;
  profilePicture?: string;
  athleteType: 'enthusiast' | 'powerlifter' | 'bodybuilder' | 'powerbuilder' | 'zercher_lifter' | 'hybrid' | 'weightlifter' | 'parapowerlifter' | 'calisthenics';
  powerliftingDeadliftStyle?: 'conventional' | 'sumo';
  gymName?: string;

  // Algoritmo KPKN: Perfilamiento
  trainingProfile?: 'Aesthetics' | 'Powerlifting' | 'Powerbuilding'; // Define qué motor lógico usar
  preferredIntensity?: 'RIR_High' | 'Failure'; // Define el factor de ajuste de volumen (Módulo 4.1)
  athleteScore?: AthleteProfileScore; // Guardamos aquí el resultado del test

  // General & Entrenamiento
  soundsEnabled: boolean;
  weightUnit: WeightUnit;
  intensityMetric: 'rpe' | 'rir';
  barbellWeight: number;
  showTimeSaverPrompt: boolean;
  restTimerAutoStart: boolean;
  restTimerDefaultSeconds: number;
  showPRsInWorkout: boolean;
  readinessCheckEnabled: boolean;
  workoutLoggerMode: 'pro' | 'simple';
  oneRMFormula: OneRMFormula;
  
  // Custom Tab Bar
  enabledTabs?: View[];

  // IA & APIs
  apiProvider: 'gemini' | 'gpt' | 'deepseek';
  fallbackEnabled: boolean;
  apiKeys: {
    gemini?: string;
    gpt?: string;
    deepseek?: string;
    usda?: string;
  };
  aiTemperature: number; // 0.0 a 1.0 (Creatividad)
  aiMaxTokens: number;
  aiVoice: string;
  // Added missing googleClientId for Google Drive synchronization
  googleClientId?: string;

  // Algoritmos Prime
  algorithmSettings: {
    oneRMDecayRate: number;
    failureFatigueFactor: number;
    legVolumeMultiplier: number;
    torsoVolumeMultiplier: number;
    synergistFactor: number; // Cuánto cuenta una serie secundaria (0.1 a 0.5)
    
    // --- NUEVO: Configuración de Precisión AUGE ---
    augeEnableNutritionTracking: boolean;
    augeEnableSleepTracking: boolean;
  };

  // UI / UX / Estética
  appTheme: 'default' | 'deep-black' | 'volt';
  themePrimaryColor: string;
  enableGlassmorphism: boolean;
  enableAnimations: boolean;
  enableGlowEffects: boolean;
  enableZenMode: boolean;
  enableParallax: boolean;
  themeCardBorderRadius: number;
  themeBlurAmount: number;
  themeGlowIntensity: number;
  fontSizeScale: number; // 0.8 a 1.2
  hapticFeedbackEnabled: boolean;
  hapticIntensity: HapticIntensity;
  // Added missing feedSettings for the feed view customization
  feedSettings?: {
    background: string;
    cardColor: string;
  };

  // Nutrición & Biometría
  userVitals: {
    age?: number;
    weight?: number;
    height?: number;
    gender?: 'male' | 'female' | 'transmale' | 'transfemale' | 'other';
    activityLevel?: 'sedentary' | 'light' | 'moderate' | 'active' | 'very_active';
    bodyFatPercentage?: number;
    targetWeight?: number;
    targetDate?: string;
    targetStartWeight?: number;
    targetStartDate?: string;
    wingspan?: number;
    torsoLength?: number;
    femurLength?: number;
    tibiaLength?: number;
    humerusLength?: number;
    forearmLength?: number;
    somatotype?: { endomorph: number; mesomorph: number; ectomorph: number };
    bodyFatDistribution?: 'android' | 'gynoid' | 'even';
    jointHealthNotes?: { joint: string; note: string }[];
    // Nuevos campos para calibración de fatiga
    workHours?: number;
    studyHours?: number;
    workIntensity?: IntensityLevel;
    studyIntensity?: IntensityLevel;
  };
  calorieGoalObjective: 'deficit' | 'maintenance' | 'surplus';
  dailyCalorieGoal?: number;
  dailyProteinGoal?: number;
  dailyCarbGoal?: number;
  dailyFatGoal?: number;
  calorieGoalConfig?: CalorieGoalConfig;
  waterIntakeGoal_L?: number;
  dietaryPreference?: 'omnivore' | 'vegetarian' | 'vegan' | 'keto';
  micronutrientFocus?: string[];
  muscleRecoveryMultipliers?: Record<string, number>;

  // Sueño
  smartSleepEnabled: boolean;
  sleepTargetHours: number;
  workDays: number[]; // 0=Domingo, 1=Lunes, etc.
  wakeTimeWork: string; // HH:mm format
  wakeTimeOff: string; // HH:mm format

  // Otros
  startWeekOn: number; // 0=Dom, 1=Lun
  remindersEnabled: boolean;
  reminderTime?: string;
  autoSyncEnabled: boolean;
  appBackground?: SessionBackground;
  homeWidgetOrder?: string[];
  
  // --- LÍMITES CALIBRADOS DEL ATLETA (KPKN ENGINE) ---
  volumeLimits?: Record<string, { maxSession: number; max: number; min?: number }>;
  
  // --- AUTO-CALIBRACIÓN CIBERNÉTICA (Deltas del Usuario) ---
  batteryCalibration?: {
      cnsDelta: number;
      muscularDelta: number;
      spinalDelta: number;
      lastCalibrated: string;
  };
}

export interface Program {
    id: string;
    name: string;
    description?: string;
    coverImage?: string;
    mode: 'powerlifting' | 'hypertrophy' | 'powerbuilding';
    structure: 'simple' | 'complex';
    macrocycles: Macrocycle[];
    author?: string;
    isPublic?: boolean;
    tags?: string[];
    background?: any; 
    events?: { 
        id?: string; 
        title: string; 
        type: string; 
        date: string; 
        endDate?: string; 
        calculatedWeek: number; 
        createMacrocycle?: boolean;
        repeatEveryXCycles?: number; // Nueva propiedad para eventos cíclicos (Programas Simples)
        sessions?: Session[]; // Soporte para sesiones exclusivas del evento
        rules?: { avoidDaysOfWeek?: number[]; avoidEndOfMonth?: boolean; }; // NUEVO: Motor de reglas condicionales
    }[];
    exerciseGoals?: Record<string, number>;
    
    // --- CONFIGURACIÓN TÉCNICA Y MOTOR LÓGICO ---
    athleteProfile?: AthleteProfile; 
    trainingPhase?: TrainingPhase;
    intensityTier?: IntensityTier;
    volumeRecommendations?: VolumeRecommendation[]; 
    
    // --- PREFERENCIAS AVANZADAS DEL PROGRAMA ---
    autoVolumeEnabled?: boolean;
    manualVolumeTargets?: Record<string, number>;
    progressAlertsEnabled?: boolean;
    alerts?: {
        deload?: boolean;
        prs?: boolean;
    };
    carpeDiemEnabled?: boolean;
    startDay?: number;
    selectedSplitId?: string;
    isDraft?: boolean;
    lastSavedStep?: number;
    draftData?: {
        selectedSplitId?: string;
        detailedSessions?: Record<number, Session>;
        wizardEvents?: { id?: string, title: string, type: string, date: string, endDate?: string, calculatedWeek: number, createMacrocycle?: boolean, repeatEveryXCycles?: number }[];
        blockSplits?: Record<number, string>;   // <-- AHORA GUARDA EL ID DEL SPLIT
        splitMode?: 'global' | 'per_block';
        startDay?: number;
        cycleDuration?: number;
    };
}

export interface Macrocycle {
  id: string;
  name: string;
  blocks?: Block[];
}

export interface Block {
  id: string;
  name: string;
  mesocycles: Mesocycle[];
}

export interface Mesocycle {
  id: string;
  name: string;
  goal: 'Acumulación' | 'Intensificación' | 'Realización' | 'Descarga' | 'Custom';
  customGoal?: string;
  weeks: ProgramWeek[];
}

export interface ProgramWeek {
  id: string;
  name: string;
  sessions: Session[];
  variant?: 'A' | 'B' | 'C' | 'D';
}

export interface Session {
  id: string;
  name: string;
  description?: string;
  exercises: Exercise[];
  warmup?: WarmupExercise[];
  parts?: SessionPart[];
  background?: SessionBackground;
  coverStyle?: CoverStyle;
  dayOfWeek?: number;
  scheduleLabel?: string; 
  assignedDays?: number[]; // Transient property for multi-day assignment in Editor
  sessionB?: Session;
  sessionC?: Session;
  sessionD?: Session;
  isMeetDay?: boolean;
  meetBodyweight?: number;
  meetResults?: { placement?: string; total?: number; dots?: number; awards?: string[] };
}

export interface SessionPart {
  id: string;
  name: string;
  exercises: Exercise[];
  color?: string;
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

export interface Exercise {
  id: string;
  name: string;
  exerciseDbId?: string;
  exerciseId?: string; 
  sets: ExerciseSet[];
  warmupSets?: WarmupSetDefinition[];
  restTime?: number;
  isFavorite?: boolean;
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
  prFor1RM?: { weight: number, reps: number };
  brandEquivalencies?: BrandEquivalency[];
  isUnilateral?: boolean;
  isCalibratorAmrap?: boolean;
  goal1RM?: number;
  calculated1RM?: number;
  damageProfile?: 'stretch' | 'squeeze' | 'normal'; 
  isCompetitionLift?: boolean;
}

export interface ExerciseSet {
  id: string;
  targetReps?: number;
  targetDuration?: number;
  targetRPE?: number;
  targetRIR?: number;
  intensityMode?: 'rpe' | 'rir' | 'failure' | 'amrap' | 'load'; 
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

// NUEVO: Definimos los roles exactos (AUGE)
export type MuscleRole = 'primary' | 'secondary' | 'stabilizer' | 'neutralizer';

// NUEVO: Modelo de Datos para el Wellness Diario (AUGE)
export interface DailyWellnessLog {
    date: string; // YYYY-MM-DD
    sleepHours: number;
    sleepQuality: number; // 1-5
    nutritionStatus: 'deficit' | 'maintenance' | 'surplus';
    stressLevel: number; // 1-5 (1=Alto Estrés, 5=Relajado)
    hydration: 'good' | 'poor';
}

export interface ExerciseMuscleInfo {
    id: string;
    name: string;
    alias?: string;
    description: string;
    // AQUÍ ESTÁ EL CAMBIO CLAVE:
    involvedMuscles: {
      muscle: string;
      role: MuscleRole;
      activation?: number; // K_rol: Coeficiente de Participación Sinérgica (AUGE)
    }[];
    subMuscleGroup?: string;
    category: 'Fuerza' | 'Hipertrofia' | 'Resistencia' | 'Potencia' | 'Movilidad' | 'Pliometría' | 'Estabilidad' | 'Calistenia';
    type: 'Básico' | 'Accesorio' | 'Aislamiento';
    tier?: 'T1' | 'T2' | 'T3'; // Módulo 2.3: T1 (Neural x1.5), T2 (Metabólico x1.0)
    equipment: 'Barra' | 'Mancuerna' | 'Máquina' | 'Peso Corporal' | 'Banda' | 'Kettlebell' | 'Polea' | 'Otro';
    force: 'Empuje' | 'Tirón' | 'Bisagra' | 'Sentadilla' | 'Rotación' | 'Anti-Rotación' | 'Flexión' | 'Extensión' | 'Anti-Flexión' | 'Anti-Extensión' | 'Salto' | 'Otro';
    isCustom?: boolean;
    bodyPart?: 'upper' | 'lower' | 'full';
    chain?: 'anterior' | 'posterior' | 'full';
    isFavorite?: boolean;
    variantOf?: string;
    sfr?: { score: number; justification: string; };
    setupTime?: number;
    // --- SISTEMA AUGE v2.0 ---
    efc?: number; // Costo Metabólico/Fatiga Local (1-5)
    ssc?: number; // Costo Estructural/Espinal (0-2.0) - Reemplaza a axialLoadFactor
    cnc?: number; // Costo Neural Central (1-5)
    axialLoadFactor?: number; // Legacy support
    // -------------------------
    postureFactor?: number;   // Multiplicador de postura (ej. Barra Baja = 1.2)
    technicalDifficulty?: number;
    injuryRisk?: { level: number; details: string; };
    transferability?: number;
    recommendedMobility?: string[];
    isHallOfFame?: boolean;
    sportsRelevance?: string[];
    baseIFI?: number;
    resistanceProfile?: {
        curve?: string;
        peakTensionPoint?: string;
        description?: string;
    };
    commonMistakes?: { mistake: string; correction: string; }[];
    progressions?: { name: string; description: string; }[];
    regressions?: { name: string; description: string; }[];
    anatomicalConsiderations?: { trait: string; advice: string; }[];
    periodizationNotes?: { phase: string; suitability: number; notes: string; }[];
    primeStars?: { score: number; justification: string; };
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
}

export interface BrandEquivalency {
    brand: string;
    pr?: { weight: number, reps: number, e1rm: number };
}

export interface OngoingWorkoutState {
    readinessData?: any; // <-- SISTEMA AUGE
    programId: string;
    session: Session;
    startTime: number;
    activeExerciseId: string | null;
    activeSetId: string | null;
    activeMode: 'A' | 'B' | 'C' | 'D';
    completedSets: Record<string, OngoingSetData | { left: OngoingSetData | null, right: OngoingSetData | null }>;
    dynamicWeights: Record<string, { consolidated?: number, technical?: number }>;
    exerciseFeedback: Record<string, any>;
    unilateralImbalances: Record<string, any>;
    readiness?: any;
    isCarpeDiem?: boolean;
    macroIndex?: number;
    mesoIndex?: number;
    weekId?: string;
    unilateralSetInputs?: Record<string, UnilateralSetInputs | SetInputState>;
    selectedBrands?: Record<string, string>;
    exerciseHeartRates?: Record<string, { initial?: number, peak?: number }>;
    topSetAmrapState?: { status: string };
    consolidatedWeights?: Record<string, number>; 
    isLowEnergyMental?: boolean;
}

export interface OngoingSetData {
    weight: number;
    reps?: number;
    rpe?: number;
    rir?: number;
    spinalScore?: number; // Carga Espinal calculada dinámicamente para esta serie
    isFailure?: boolean;
    isIneffective?: boolean;
    duration?: number;
    machineBrand?: string;
    isPartial?: boolean;
    performanceMode?: 'target' | 'failure' | 'failed';
    partialReps?: number;
    dropSets?: DropSetData[];
    restPauses?: RestPauseData[];
    discomforts?: string[];
    discomfortNotes?: string;
    isAmrap?: boolean;
    amrapReps?: number;
    isChangeOfPlans?: boolean;
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
    notes?: string;
    advancedTechnique?: string;
    dropSets?: DropSetData[];
    restPauses?: RestPauseData[];
    performanceMode?: 'target' | 'failure' | 'failed';
    partialReps: string;
    technicalQuality?: string;
    discomfortLevel?: string;
    discomfortNotes?: string;
    tempo?: string;
    judgingLights?: [boolean | null, boolean | null, boolean | null];
    attemptResult?: 'good' | 'no-lift' | 'pending';
}

export interface UnilateralSetInputs {
    left: SetInputState;
    right: SetInputState;
}

export interface WorkoutLog {
    readinessData?: any; // <-- SISTEMA AUGE
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
    photoUri?: string;
    sessionVariant?: 'A' | 'B' | 'C' | 'D';
    planDeviations?: PlanDeviation[];
    readiness?: any;
    focus?: number;
    pump?: number;
    environmentTags?: string[];
    sessionDifficulty?: number;
    planAdherenceTags?: string[];
    sessionStressScore?: number;
    postTitle?: string;
    postSummary?: string;
    postPhotos?: string[];
    isCustomPost?: boolean;
    photo?: string;
    caloriesBurned?: number;
}

export interface CompletedExercise {
    exerciseId: string;
    exerciseDbId?: string;
    exerciseName: string;
    sets: CompletedSet[];
    totalSpinalScore?: number; // Sumatoria total de carga espinal del ejercicio
    initialHeartRate?: number;
    peakHeartRate?: number;
    useBodyweight?: boolean;
    technicalQuality?: number;
    jointLoad?: number;
    perceivedFatigue?: number;
    machineBrand?: string;
}

export interface CompletedSet extends ExerciseSet {
    weight: number;
    side?: 'left' | 'right';
    spinalScore?: number; // Carga espinal histórica guardada
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

export interface BodyProgressLog {
    id: string;
    date: string;
    weight?: number;
    bodyFatPercentage?: number;
    photos?: string[];
    measurements?: Record<string, number>;
    aiInsight?: string;
}

export type PortionPreset = 'small' | 'medium' | 'large' | 'extra';

export const PORTION_MULTIPLIERS: Record<PortionPreset, number> = {
    small: 0.6,
    medium: 1,
    large: 1.5,
    extra: 2,
};

export type PortionUnit = 'g' | 'oz' | 'preset' | 'reference';
export type PortionReference = 'palm' | 'fist' | 'tablespoon' | 'cup' | 'handful';

export interface PortionInput {
    type: PortionUnit;
    value: number;
    reference?: PortionReference;
}

export type CookingMethod = 'crudo' | 'cocido' | 'plancha' | 'horno' | 'frito' | 'empanizado_frito';

export interface ParsedMealItem {
    tag: string;
    quantity: number;
    amountGrams?: number;
    cookingMethod?: CookingMethod;
    portion?: PortionPreset | PortionInput;
}

export interface ParsedMealDescription {
    items: ParsedMealItem[];
    rawDescription: string;
}

export interface LoggedFood {
    id: string;
    foodName: string;
    amount: number;
    unit: string;
    calories: number;
    protein: number;
    carbs: number;
    fats: number;
    pantryItemId?: string;
    tags?: string[];
    fatBreakdown?: { saturated: number; monounsaturated: number; polyunsaturated: number; trans: number };
    micronutrients?: { name: string; amount: number; unit: string }[];
    portionPreset?: PortionPreset;
    portionInput?: PortionInput;
    cookingMethod?: CookingMethod;
    quantity?: number;
}

export interface NutritionLog {
    id: string;
    date: string;
    mealType: 'breakfast' | 'lunch' | 'dinner' | 'snack';
    foods: LoggedFood[];
    notes?: string;
    description?: string; 
    status?: 'planned' | 'consumed';
    calories?: number;
    protein?: number;
    carbs?: number;
    fats?: number;
}

export interface FoodItem {
    id: string;
    name: string;
    brand?: string;
    servingSize: number;
    servingUnit?: string; 
    unit: string;
    calories: number;
    protein: number;
    carbs: number;
    fats: number;
    isCustom?: boolean;
    image?: string;
    fatBreakdown?: { saturated: number; monounsaturated: number; polyunsaturated: number; trans: number };
    carbBreakdown?: { fiber: number; sugar: number };
    proteinQuality?: { completeness: string; details: string };
    micronutrients?: { name: string; amount: number; unit: string }[];
    aiNotes?: string;
}

export interface PantryItem {
    id: string;
    name: string;
    calories: number;
    protein: number;
    carbs: number;
    fats: number;
    currentQuantity: number;
    unit: string;
}

export interface Task {
    id: string;
    title: string;
    description?: string;
    completed: boolean;
    completedDate?: string;
    generatedBy?: 'user' | 'ai';
}

export interface Achievement {
    id: string;
    name: string;
    description: string;
    icon: string;
    category: string;
    check: (context: any) => boolean;
}

export interface AchievementUnlock {
    achievementId: string;
    date: string;
}

export interface ToastData {
    id: number;
    message: string;
    type: 'success' | 'achievement' | 'suggestion' | 'danger';
    title?: string;
    duration?: number;
}

// Wiki/Lab: Lesión común con enfoque en entrenamiento
export interface CommonInjury {
    name: string;
    description: string;
    riskExercises?: string[];
    contraindications?: string[];
    returnProgressions?: string[];
}

// Wiki/Lab: Articulación
export interface JointInfo {
    id: string;
    name: string;
    description: string;
    type: 'hinge' | 'ball-socket' | 'pivot' | 'gliding' | 'saddle' | 'condyloid';
    bodyPart: 'upper' | 'lower' | 'spine';
    musclesCrossing: string[];
    tendonsRelated: string[];
    movementPatterns: string[];
    commonInjuries: CommonInjury[];
}

// Wiki/Lab: Tendón
export interface TendonInfo {
    id: string;
    name: string;
    description: string;
    muscleId: string;
    jointId?: string;
    commonInjuries: CommonInjury[];
}

// Wiki/Lab: Patrón de movimiento
export interface MovementPatternInfo {
    id: string;
    name: string;
    description: string;
    forceTypes: string[];
    chainTypes: string[];
    primaryMuscles: string[];
    primaryJoints: string[];
    exampleExercises: string[];
}

export interface MuscleGroupInfo {
    id: string;
    name: string;
    description: string;
    importance: { movement: string; health: string };
    volumeRecommendations: { mev: string; mav: string; mrv: string };
    coverImage?: string;
    coverStyle?: CoverStyle;
    recommendedExercises?: string[];
    favoriteExerciseId?: string;
    // Wiki/Lab: campos expandidos
    origin?: string;
    insertion?: string;
    mechanicalFunctions?: string[];
    relatedJoints?: string[];
    relatedTendons?: string[];
    commonInjuries?: CommonInjury[];
    movementPatterns?: string[];
}

export interface MuscleHierarchy {
    bodyPartHierarchy: Record<string, MuscleSubGroup[]>;
    specialCategories: Record<string, string[]>;
    muscleToBodyPart: Record<string, string>;
}

export type MuscleSubGroup = string | { [key: string]: string[] };

export interface PlanDeviation {
    type: string;
    description: string;
}

export interface ChatMessage {
    role: 'user' | 'model';
    parts: { text: string }[];
}

export interface GenerateContentResponse {
    text: string;
}

export interface CoachInsight {
    title: string;
    findings: string;
    suggestions: string[];
    alertLevel: 'info' | 'warning' | 'danger';
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

export interface CarpeDiemPlan {
    coachMessage: string;
    modifiedSessions: Session[];
}

export interface AINutritionPlan {
    meals: {
        name: string;
        description: string;
        macros: { calories: number; protein: number; carbs: number; fats: number };
        foods: string[];
    }[];
}

export interface AIPantryMealPlan {
    meals: {
        mealName: string;
        foods: { name: string; grams: number }[];
        totalMacros: { calories: number; protein: number; carbs: number; fats: number };
    }[];
    shoppingList: string[];
}

export interface ActiveProgramState {
    programId: string;
    status: 'active' | 'paused' | 'completed';
    startDate: string;
    firstSessionDate?: string; // NUEVO: Fecha en la que realmente se hizo la primera sesión
    queuedProgramId?: string;  // NUEVO: ID del programa en cola para auto-transición
    currentMacrocycleIndex: number;
    currentBlockIndex: number;
    currentMesocycleIndex: number;
    currentWeekId: string;
}

export interface SleepLog {
    id: string;
    startTime: string;
    endTime: string;
    duration: number;
    date: string;
    isAuto?: boolean;
}

export type IntensityLevel = 'light' | 'moderate' | 'high';

export interface DailyWellbeingLog {
    id: string;
    date: string;
    sleepQuality: number;
    stressLevel: number;
    doms: number;
    motivation: number;
    readiness?: number; // 1-10, manual for rest days
    workHours?: number; 
    workIntensity?: IntensityLevel; 
    studyHours?: number; 
    studyIntensity?: IntensityLevel; 
    moodState?: 'happy' | 'neutral' | 'sad' | 'anxious' | 'energetic'; 
    isDepressiveEpisode?: boolean; 
    notes?: string;
}

export interface PostSessionFeedback {
    logId: string;
    date: string;
    cnsRecovery: number;
    feedback: Record<string, {
        doms: number;
        jointPain: boolean;
        strengthCapacity: number;
        notes: string;
    }>;
}

export interface PendingQuestionnaire {
    logId: string;
    sessionName: string;
    muscleGroups: string[];
    scheduledTime: number;
}

export interface RecommendationTrigger {
    id: string;
    type: string;
    date: string;
    value: number;
    context: string;
    actionTaken: boolean;
    exerciseDbId?: string;
    exerciseName?: string;
}

export interface ExercisePlaylist {
    id: string;
    name: string;
    exerciseIds: string[];
}

export interface CustomExerciseModalData {
    exercise?: ExerciseMuscleInfo;
    preFilledName?: string;
}

export interface TabBarActions {
    onLogPress: () => void;
    onFinishWorkoutPress: () => void;
    onTimeSaverPress: () => void;
    onModifyPress: () => void;
    onTimersPress: () => void;
    onCancelWorkoutPress: () => void;
    onPauseWorkoutPress: () => void;
    onSaveSessionPress: () => void;
    onAddExercisePress: () => void;
    onCancelEditPress: () => void;
    onSaveProgramPress: () => void;
    onSaveLoggedWorkoutPress: () => void;
    onAddCustomExercisePress: () => void;
    onCoachPress: () => void;
    onEditExercisePress: () => void;
    onAnalyzeTechniquePress: () => void;
    onAnalyzePosturePress: () => void;
    onAddToPlaylistPress: () => void;
    onAddToSessionPress: () => void;
    onCreatePostPress: () => void;
    onCustomizeFeedPress: () => void;
}

export interface LocalSnapshot {
    id: string;
    name: string;
    date: string;
    data: any;
}

export interface UseGoogleDriveReturn {
  isSupported: boolean;
  isSignedIn: boolean;
  isAuthLoading: boolean;
  isSyncing: boolean;
  isLoading: boolean;
  user: GoogleUserProfile | null;
  lastSyncDate: string | null;
  signIn: () => void;
  signOut: () => void;
  syncToDrive: () => Promise<void>;
  loadFromDrive: () => Promise<void>;
}

export interface GoogleUserProfile {
    name: string;
    email: string;
    picture: string;
}

export type Terminology = string;

export interface MuscleVolumeAnalysis {
    muscleGroup: string;
    displayVolume: number;
    totalSets: number;
    directExercises: { name: string, sets: number }[];
    indirectExercises: { name: string, sets: number, activationPercentage: number }[];
    frequency: number;
    avgRestDays: number | null;
    avgIFI: number | null;
    recoveryStatus: 'N/A';
    assessment?: string;
}

export interface DetailedMuscleVolumeAnalysis extends MuscleVolumeAnalysis {}

export interface SfrData {
    exercise: string;
    score: number;
}

export interface ProgramProgressInsight {
    summary: string;
    positiveCorrelations: string[];
    improvementAreas: string[];
}

export interface ImprovementSuggestion {
    category: 'Progression' | 'Volume' | 'Intensity' | 'Recovery';
    title: string;
    suggestion: string;
}

export interface PerformanceAnalysis {
    score: number;
    summary: string;
    positivePoints: string[];
    negativePoints: string[];
}

export interface MuscleGroupAnalysis {
    assessment: 'Optimo' | 'Sobrecargado' | 'Subentrenado';
    summary: string;
    recommendations: string[];
}

export interface MobilityExercise {
    name: string;
    duration: number;
    instruction: string;
}

export interface MuscleRecoveryStatus {
    muscleId: string;
    muscleName: string;
    recoveryScore: number;
    hoursSinceLastSession: number;
    status: 'fresh' | 'optimal' | 'recovering' | 'exhausted';
    impactingFactors: string[];
    effectiveSets: number;
    estimatedHoursToRecovery: number;
}

export interface WaterLog {
    id: string;
    date: string;
    amountMl: number;
}

export interface AppContextState {
    view: View;
    historyStack: { view: View; data?: any }[];
    programs: Program[];
    history: WorkoutLog[];
    skippedLogs: SkippedWorkoutLog[];
    settings: Settings;
    bodyProgress: BodyProgressLog[];
    nutritionLogs: NutritionLog[];
    waterLogs: WaterLog[];
    pantryItems: PantryItem[];
    tasks: Task[];
    exercisePlaylists: ExercisePlaylist[];
    muscleGroupData: MuscleGroupInfo[];
    muscleHierarchy: MuscleHierarchy;
    jointDatabase: JointInfo[];
    tendonDatabase: TendonInfo[];
    movementPatternDatabase: MovementPatternInfo[];
    exerciseList: ExerciseMuscleInfo[];
    foodDatabase: FoodItem[];
    unlockedAchievements: AchievementUnlock[];
    isOnline: boolean;
    isAppLoading: boolean;
    installPromptEvent: any;
    drive: UseGoogleDriveReturn;
    toasts: ToastData[];
    bodyLabAnalysis: BodyLabAnalysis | null;
    biomechanicalData: BiomechanicalData | null;
    biomechanicalAnalysis: BiomechanicalAnalysis | null;
    syncQueue: WorkoutLog[];
    aiNutritionPlan: AINutritionPlan | null;
    activeProgramId: string | null;
    editingProgramId: string | null;
    editingSessionInfo: { programId: string; macroIndex: number; mesoIndex: number; weekId: string; sessionId?: string; dayOfWeek?: number; } | null;
    activeSession: Session | null;
    loggingSessionInfo: { programId: string; sessionId: string } | null;
    viewingSessionInfo: { programId: string; sessionId: string; } | null;
    viewingExerciseId: string | null;
    viewingFoodId: string | null;
    viewingMuscleGroupId: string | null;
    viewingJointId: string | null;
    viewingTendonId: string | null;
    viewingMovementPatternId: string | null;
    viewingBodyPartId: string | null;
    viewingChainId: string | null;
    viewingMuscleCategoryName: string | null;
    exerciseToAddId: string | null;
    foodItemToAdd_to_pantry: FoodItem | null;
    ongoingWorkout: OngoingWorkoutState | null;
    editingCustomExerciseData: CustomExerciseModalData | null;
    editingFoodData: { food?: FoodItem, preFilledName?: string } | null;
    pendingWorkoutForReadinessCheck: { session: Session; program: Program; weekVariant?: 'A' | 'B' | 'C' | 'D', location?: { macroIndex: number, mesoIndex: number, weekId: string } } | null;
    editingWorkoutSessionInfo: { session: Session; programId: string; macroIndex: number; mesoIndex: number; weekId: string; } | null;
    editingCategoryInfo: { name: string, type: 'bodyPart' | 'special' } | null;
    pendingNavigation: any | null;
    saveSessionTrigger: number;
    addExerciseTrigger: number;
    saveProgramTrigger: number;
    saveLoggedWorkoutTrigger: number;
    modifyWorkoutTrigger: number;
    searchQuery: string;
    activeSubTabs: Record<string, string>;
    currentBackgroundOverride: SessionBackground | undefined;
    restTimer: { duration: number; remaining: number; key: number; exerciseName: string; endTime: number; } | null;
    isDirty: boolean;
    kpknAction: any;
    pendingCoachBriefing: string | null;
    pendingWorkoutAfterBriefing: { session: Session; program: Program; weekVariant?: 'A' | 'B' | 'C' | 'D', location?: { macroIndex: number, mesoIndex: number, weekId: string } } | null;
    sleepLogs: SleepLog[];
    sleepStartTime: number | null;
    isGlobalVoiceActive: boolean;
    recommendationTriggers: RecommendationTrigger[];
    isMenuOpen: boolean;
    activeProgramState: ActiveProgramState | null;
    onExerciseCreated: ((exercise: ExerciseMuscleInfo) => void) | null;
    pendingQuestionnaires: PendingQuestionnaire[];
    postSessionFeedback: PostSessionFeedback[];
    dailyWellbeingLogs: DailyWellbeingLog[]; 
    isBodyLogModalOpen: boolean;
    isNutritionLogModalOpen: boolean;
    isMeasurementsModalOpen: boolean;
    isStartWorkoutModalOpen: boolean;
    isCustomExerciseEditorOpen: boolean;
    isFoodEditorOpen: boolean;
    isFinishModalOpen: boolean;
    isTimeSaverModalOpen: boolean;
    isTimersModalOpen: boolean;
    isReadinessModalOpen: boolean;
    isAddToPlaylistSheetOpen: boolean;
    isWorkoutEditorOpen: boolean;
    isMuscleListEditorOpen: boolean;
    isLiveCoachActive: boolean;
    isLogActionSheetOpen: boolean;
    isWorkoutExitModalOpen: boolean;
    isAddPantryItemModalOpen: boolean;
    isSpecialSessionModalOpen: boolean;
    specialSessionData: any | null;
}

export interface AppContextDispatch {
    setPrograms: React.Dispatch<React.SetStateAction<Program[]>>;
    setHistory: React.Dispatch<React.SetStateAction<WorkoutLog[]>>;
    setSkippedLogs: React.Dispatch<React.SetStateAction<SkippedWorkoutLog[]>>;
    setSettings: (settings: Partial<Settings>) => void;
    setBodyProgress: React.Dispatch<React.SetStateAction<BodyProgressLog[]>>;
    setNutritionLogs: React.Dispatch<React.SetStateAction<NutritionLog[]>>;
    setWaterLogs: React.Dispatch<React.SetStateAction<WaterLog[]>>;
    handleLogWater: (amountMl: number) => void;
    setPantryItems: React.Dispatch<React.SetStateAction<PantryItem[]>>;
    addOrUpdatePantryItem: (item: PantryItem) => void;
    setTasks: React.Dispatch<React.SetStateAction<Task[]>>;
    addTask: (task: Omit<Task, 'id' | 'completed' | 'generatedBy'>) => void;
    addAITasks: (tasks: Omit<Task, 'id' | 'completed'>[]) => void;
    toggleTask: (taskId: string) => void;
    deleteTask: (taskId: string) => void;
    setExercisePlaylists: React.Dispatch<React.SetStateAction<ExercisePlaylist[]>>;
    addOrUpdatePlaylist: (playlist: ExercisePlaylist) => void;
    deletePlaylist: (playlistId: string) => void;
    setMuscleGroupData: React.Dispatch<React.SetStateAction<MuscleGroupInfo[]>>;
    updateMuscleGroupInfo: (id: string, data: Partial<MuscleGroupInfo>) => void;
    setMuscleHierarchy: React.Dispatch<React.SetStateAction<MuscleHierarchy>>;
    renameMuscleCategory: (oldName: string, newName: string) => void;
    renameMuscleGroup: (oldName: string, newName: string) => void;
    updateCategoryMuscles: (categoryName: string, newMuscles: any, type: 'bodyPart' | 'special') => void;
    setBodyLabAnalysis: (analysis: BodyLabAnalysis | null) => void;
    setBiomechanicalData: (data: BiomechanicalData) => void;
    setBiomechanicalAnalysis: React.Dispatch<React.SetStateAction<BiomechanicalAnalysis | null>>;
    setAiNutritionPlan: React.Dispatch<React.SetStateAction<AINutritionPlan | null>>;
    setActiveProgramState: React.Dispatch<React.SetStateAction<ActiveProgramState | null>>;
    setOnExerciseCreated: React.Dispatch<React.SetStateAction<((exercise: ExerciseMuscleInfo) => void) | null>>;
    setInstallPromptEvent: React.Dispatch<any>;
    setIsBodyLogModalOpen: React.Dispatch<React.SetStateAction<boolean>>;
    setIsNutritionLogModalOpen: React.Dispatch<React.SetStateAction<boolean>>;
    setIsMeasurementsModalOpen: React.Dispatch<React.SetStateAction<boolean>>;
    setIsStartWorkoutModalOpen: React.Dispatch<React.SetStateAction<boolean>>;
    setIsFinishModalOpen: React.Dispatch<React.SetStateAction<boolean>>;
    setIsTimeSaverModalOpen: React.Dispatch<React.SetStateAction<boolean>>;
    setIsTimersModalOpen: React.Dispatch<React.SetStateAction<boolean>>;
    setIsReadinessModalOpen: React.Dispatch<React.SetStateAction<boolean>>;
    setIsAddToPlaylistSheetOpen: React.Dispatch<React.SetStateAction<boolean>>;
    setIsLiveCoachActive: React.Dispatch<React.SetStateAction<boolean>>;
    setIsLogActionSheetOpen: React.Dispatch<React.SetStateAction<boolean>>;
    openCustomExerciseEditor: (data?: CustomExerciseModalData) => void;
    closeCustomExerciseEditor: () => void;
    openFoodEditor: (data?: { food?: FoodItem; preFilledName?: string }) => void;
    closeFoodEditor: () => void;
    openAddPantryItemModal: (foodItem: FoodItem) => void;
    closeAddPantryItemModal: () => void;
    openMuscleListEditor: (categoryName: string, type: 'bodyPart' | 'special') => void;
    closeMuscleListEditor: () => void;
    setIsWorkoutExitModalOpen: React.Dispatch<React.SetStateAction<boolean>>;
    setPendingNavigation: React.Dispatch<React.SetStateAction<any | null>>;
    setExerciseToAddId: React.Dispatch<React.SetStateAction<string | null>>;
    setPendingWorkoutForReadinessCheck: React.Dispatch<React.SetStateAction<{ session: Session; program: Program; weekVariant?: 'A' | 'B' | 'C' | 'D', location?: { macroIndex: number, mesoIndex: number, weekId: string } } | null>>;
    setSaveSessionTrigger: React.Dispatch<React.SetStateAction<number>>;
    setAddExerciseTrigger: React.Dispatch<React.SetStateAction<number>>;
    setSaveProgramTrigger: React.Dispatch<React.SetStateAction<number>>;
    setSaveLoggedWorkoutTrigger: React.Dispatch<React.SetStateAction<number>>;
    setModifyWorkoutTrigger: React.Dispatch<React.SetStateAction<number>>;
    setSearchQuery: React.Dispatch<React.SetStateAction<string>>;
    setActiveSubTabs: React.Dispatch<React.SetStateAction<Record<string, string>>>;
    setCurrentBackgroundOverride: React.Dispatch<React.SetStateAction<SessionBackground | undefined>>;
    setOngoingWorkout: React.Dispatch<React.SetStateAction<OngoingWorkoutState | null>>;
    navigateTo: (view: View, data?: any, options?: { replace?: boolean }) => void;
    handleBack: () => void;
    addToast: (message: string, type?: ToastData['type'], title?: string, duration?: number) => void;
    removeToast: (id: number) => void;
    setPendingCoachBriefing: React.Dispatch<React.SetStateAction<string | null>>;
    setPendingWorkoutAfterBriefing: React.Dispatch<React.SetStateAction<{ session: Session; program: Program; weekVariant?: 'A' | 'B' | 'C' | 'D', location?: { macroIndex: number, mesoIndex: number, weekId: string } } | null>>;
    handleCreateProgram: () => void;
    handleEditProgram: (programId: string) => void;
    handleSelectProgram: (program: Program) => void;
    handleSaveProgram: (program: Program) => void;
    handleUpdateProgram: (program: Program) => void;
    handleDeleteProgram: (programId: string) => void;
    handleAddSession: (programId: string, macroIndex: number, mesoIndex: number, weekId: string, dayOfWeek?: number) => void;
    handleEditSession: (programId: string, macroIndex: number, mesoIndex: number, weekId: string, sessionId: string) => void;
    handleSaveSession: (session: Session | Session[], programId: string, macroIndex: number, mesoIndex: number, weekId: string) => void;
    handleUpdateSessionInProgram: (session: Session, programId: string, macroIndex: number, mesoIndex: number, weekId: string) => void;
    handleDeleteSession: (sessionId: string, programId: string, macroIndex: number, mesoIndex: number, weekId: string) => void;
    handleChangeSplit: (programId: string, splitPattern: string[], splitId: string, scope: 'week' | 'block' | 'program', preserveExercises: boolean, startDay: number, targetBlockId?: string, targetWeekId?: string) => void;
    handleCopySessionsToMeso: (programId: string, macroIndex: number, mesoIndex: number) => void;
    handleStartProgram: (programId: string) => void;
    handlePauseProgram: () => void;
    handleFinishProgram: () => void;
    handleRestartProgram: () => void;
    handleStartWorkout: (session: Session, program: Program, weekVariant?: 'A' | 'B' | 'C' | 'D', location?: { macroIndex: number, mesoIndex: number, weekId: string }, isLowEnergyMental?: boolean) => void;
    handleResumeWorkout: () => void;
    handleContinueFromReadiness: (data: any) => void;
    handleContinueWorkoutAfterBriefing: () => void;
    onCancelWorkout: () => void;
    handlePauseWorkout: () => void;
    handleFinishWorkout: (
        completedExercises: CompletedExercise[], 
        duration: number, 
        notes?: string, 
        discomforts?: string[], 
        fatigue?: number, 
        clarity?: number, 
        logDate?: string, 
        photoUri?: string, 
        planDeviations?: PlanDeviation[],
        focus?: number,
        pump?: number,
        environmentTags?: string[],
        sessionDifficulty?: number,
        planAdherenceTags?: string[]
    ) => void;
    handleLogWorkout: (programId: string, sessionId: string) => void;
    handleSaveLoggedWorkout: (log: WorkoutLog) => void;
    handleSkipWorkout: (session: Session, program: Program, reason: SkippedWorkoutLog['reason'], notes?: string) => void;
    handleSaveBodyLog: (log: BodyProgressLog) => void;
    handleSaveNutritionLog: (log: NutritionLog) => void;
    addOrUpdateFoodItem: (food: FoodItem) => void;
    handleUpdateExerciseInProgram: (programId: string, sessionId: string, exerciseId: string, updatedExercise: Exercise) => void;
    handleUpdateProgressionWeights: (exerciseId: string, consolidated?: number, technical?: number) => void;
    handleUpdateExercise1RM: (exerciseDbId: string | undefined, exerciseName: string, weight: number, reps: number, testDate?: string, machineBrand?: string) => void;
    handleUpdateExerciseBrandPR: (exerciseDbId: string, brand: string, pr: { weight: number, reps: number, e1rm: number }) => void;
    handleUpdateExerciseRepDebt: (exerciseDbId: string, debtUpdate: Record<string, number>) => void;
    handleStartRest: (duration: number, exerciseName: string) => void;
    handleAdjustRestTimer: (amountInSeconds: number) => void;
    handleSkipRestTimer: () => void;
    handleLogPress: () => void;
    addOrUpdateCustomExercise: (exerciseInfo: ExerciseMuscleInfo) => void;
    batchAddExercises: (exercises: ExerciseMuscleInfo[]) => void;
    createAndAddExerciseToDB: (exerciseName: string) => Promise<ExerciseMuscleInfo | null>;
    setExerciseList: React.Dispatch<React.SetStateAction<ExerciseMuscleInfo[]>>;
    exportExerciseDatabase: () => void;
    importExerciseDatabase: (jsonString: string) => void;
    setIsDirty: React.Dispatch<React.SetStateAction<boolean>>;
    handleModifyWorkout: () => void;
    handleSaveModifiedWorkout: (session: Session) => void;
    setIsWorkoutEditorOpen: React.Dispatch<React.SetStateAction<boolean>>;
    setKpknaction: React.Dispatch<any>;
    handleLogSleep: (action: 'start' | 'end') => void;
    setSleepLogs: React.Dispatch<React.SetStateAction<SleepLog[]>>;
    handleSavePostSessionFeedback: (feedback: PostSessionFeedback) => void;
    setIsGlobalVoiceActive: React.Dispatch<React.SetStateAction<boolean>>;
    addRecommendationTrigger: (trigger: Omit<RecommendationTrigger, 'id' | 'date' | 'actionTaken'>) => void;
    markRecommendationAsTaken: (id: string) => void;
    setIsMenuOpen: React.Dispatch<React.SetStateAction<boolean>>;
    handleLogDailyWellbeing: (data: Omit<DailyWellbeingLog, 'id'>) => void; 
    setIsSpecialSessionModalOpen: React.Dispatch<React.SetStateAction<boolean>>;
    setSpecialSessionData: React.Dispatch<React.SetStateAction<any | null>>;
}

export interface SocialProfile {
    uid: string;
    username: string;
    athleteType: string;
    stats: { totalWorkouts: number; ipfPoints: number; followerCount: number; followingCount: number };
    badges: string[];
}

export interface SocialComment {
    id: string;
    authorId: string;
    authorName: string;
    text: string;
    date: string;
}

export interface SocialPost {
    id: string;
    authorId: string;
    authorName: string;
    date: string;
    type: 'pr_alert' | 'workout_summary' | 'achievement';
    content: {
        text?: string;
        workoutData?: {
            sessionName: string;
            volume: number;
            duration: number;
            highlightExercise?: string;
            highlightWeight?: number;
        };
    };
    likes: string[];
    comments: SocialComment[];
}

// Zod Schema for WorkoutLog validation
export const WorkoutLogSchema = z.object({
  id: z.string(),
  programId: z.string(),
  programName: z.string(),
  sessionId: z.string(),
  sessionName: z.string(),
  date: z.string(),
  duration: z.number().optional(),
  completedExercises: z.array(z.any()), // Simplified for now
  notes: z.string().optional(),
  discomforts: z.array(z.string()).optional(),
  fatigueLevel: z.number(),
  mentalClarity: z.number(),
  gymName: z.string().optional(),
  photoUri: z.string().optional(),
  sessionVariant: z.enum(['A','B','C','D']).optional(),
  planDeviations: z.array(z.any()).optional(),
  readiness: z.any().optional(),
  focus: z.number().optional(),
  pump: z.number().optional(),
  environmentTags: z.array(z.string()).optional(),
  sessionDifficulty: z.number().optional(),
  planAdherenceTags: z.array(z.string()).optional(),
  sessionStressScore: z.number().optional(),
  postTitle: z.string().optional(),
  postSummary: z.string().optional(),
  postPhotos: z.array(z.string()).optional(),
  isCustomPost: z.boolean().optional(),
  photo: z.string().optional(),
  caloriesBurned: z.number().optional(),
});
