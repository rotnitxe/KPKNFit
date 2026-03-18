import type { CoreReminderSettings } from '@kpkn/shared-types';
import type {
  WeightUnit,
  HapticIntensity,
  OneRMFormula,
  IntensityLevel,
  CalorieGoalConfig,
  AthleteProfile,
  AthleteProfileScore,
  ExperienceClassification,
  TrainingPhase,
  IntensityTier,
  VolumeRecommendation,
  VolumeRecSnapshot,
  VolumeCalibrationEntry,
  CoverStyle,
  SessionBackground
} from './workout';

export type TrainingProfile = 'Aesthetics' | 'Powerlifting' | 'Powerbuilding';
export type PreferredIntensity = 'RIR_High' | 'Failure';
export type VolumeSystem = 'israetel' | 'kpnk' | 'manual';

export interface AlgorithmSettings {
  augeEnableSleepTracking?: boolean;
  augeEnableNutritionTracking?: boolean;
  augeEnableWellbeingTracking?: boolean;
  oneRMDecayRate?: number;
  failureFatigueFactor?: number;
  legVolumeMultiplier?: number;
  torsoVolumeMultiplier?: number;
  synergistFactor?: number;
}

export interface BatteryCalibration {
  cnsDelta?: number;
  muscularDelta?: number;
  spinalDelta?: number;
  muscleDeltas?: Record<string, number>;
  lastCalibrated?: string;
}

export interface AthleteScore {
  technicalScore?: 1 | 2 | 3;
  consistencyScore?: 1 | 2 | 3;
  strengthScore?: 1 | 2 | 3;
  mobilityScore?: 1 | 2 | 3;
  trainingStyle?: 'Bodybuilder' | 'Powerbuilder' | 'Powerlifter';
  totalScore?: number;
  profileLevel?: 'Beginner' | 'Advanced';
}

export interface Settings extends CoreReminderSettings {
  hasSeenWelcome: boolean;
  hasSeenHomeTour: boolean;
  hasSeenProgramEditorTour: boolean;
  hasSeenSessionEditorTour: boolean;
  hasSeenKPKNTour: boolean;
  hasSeenNutritionWizard?: boolean;
  nutritionWizardVersion?: number;
  hasDismissedNutritionSetup?: boolean;
  hasSeenGeneralWizard?: boolean;
  hasPrecalibratedBattery?: boolean;
  precalibrationDismissed?: boolean;
  hasSeenMuscleFatigueTip?: boolean;

  // Perfil
  username?: string;
  profilePicture?: string;
  age?: number;
  athleteType: 'enthusiast' | 'powerlifter' | 'bodybuilder' | 'powerbuilder' | 'zercher_lifter' | 'hybrid' | 'weightlifter' | 'parapowerlifter' | 'calisthenics';
  powerliftingDeadliftStyle?: 'conventional' | 'sumo';
  gymName?: string;

  // Algoritmo KPKN: Perfilamiento
  trainingProfile?: 'Aesthetics' | 'Powerlifting' | 'Powerbuilding'; // Define qué motor lógico usar
  preferredIntensity?: 'RIR_High' | 'Failure'; // Define el factor de ajuste de volumen (Módulo 4.1)
  athleteScore?: AthleteScore; // Guardamos aquí el resultado del test
  /** Sistema de recomendación de volumen: israetel, kpnk (KPKN Personalizado), manual */
  volumeSystem?: 'israetel' | 'kpnk' | 'manual';

  // General & Entrenamiento
  soundsEnabled: boolean;
  weightUnit: WeightUnit;
  intensityMetric: 'rpe' | 'rir';
  barbellWeight: number;
  showTimeSaverPrompt: boolean;
  restTimerAutoStart: boolean;
  restTimerDefaultSeconds: number;
  /** Vista compacta en tabla de sets (40px en lugar de 48px) */
  sessionCompactView?: boolean;
  /** Auto-avance Kg → Reps al pulsar Siguiente en teclado numérico */
  sessionAutoAdvanceFields?: boolean;
  showPRsInWorkout: boolean;
  readinessCheckEnabled: boolean;
  workoutLoggerMode: 'pro' | 'simple' | null;
  oneRMFormula: OneRMFormula;

  // Custom Tab Bar
  enabledTabs?: ('auth' | 'home' | 'athlete-id' | 'my-rings' | 'programs' | 'program-detail' | 'program-editor' | 'session-editor' | 'workout' | 'progress' | 'settings' | 'coach' | 'log-hub' | 'achievements' | 'log-workout' | 'kpkn' | 'ai-art-studio' | 'body-lab' | 'mobility-lab' | 'training-purpose' | 'exercise-database' | 'food-database' | 'smart-meal-planner' | 'exercise-detail' | 'muscle-group-detail' | 'body-part-detail' | 'muscle-category' | 'chain-detail' | 'joint-detail' | 'tendon-detail' | 'movement-pattern-detail' | 'wiki-home' | 'wikilab-biomechanics' | 'nutrition' | 'food-detail' | 'session-detail' | 'tasks' | 'social-feed' | 'athlete-profile' | 'recovery' | 'sleep' | 'program-metric-volume' | 'program-metric-strength' | 'program-metric-density' | 'program-metric-frequency' | 'program-metric-banister' | 'program-metric-recovery' | 'program-metric-adherence' | 'program-metric-rpe' | 'body-progress' | 'home-card-page')[];
  soundPack?: 'classic' | 'minimal' | 'none';
  tabBarStyle?: 'default' | 'compact' | 'icons-only';

  // IA & APIs
  apiProvider: 'gemini' | 'gpt' | 'deepseek';
  fallbackEnabled: boolean;
  apiKeys: {
    gemini?: string;
    gpt?: string;
    deepseek?: string;
    usda?: string;
  };
  googleClientId?: string;
  feedSettings?: {
    background: string;
    cardColor: string;
  };
  nutritionDescriptionMode?: 'auto' | 'rules' | 'local-ai' | 'deterministic' | 'assisted';
  nutritionResolutionMode?: 'deterministic' | 'assisted';
  nutritionUseOnlineApis?: boolean;
  nutritionUseLocalAI?: boolean;
  nutritionLocalModel?: string;
  aiTemperature: number; // 0.0 a 1.0 (Creatividad)
  aiMaxTokens: number;
  aiVoice: string;

  // Algoritmos Prime
  algorithmSettings?: AlgorithmSettings;

  // UI / UX / Estética
  appTheme: 'default' | 'deep-black' | 'volt' | 'dark' | 'light';
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

  // Nutrición & Biometría
  userVitals: {
    age?: number;
    weight?: number;
    height?: number;
    gender?: 'male' | 'female' | 'transmale' | 'transfemale' | 'other';
    activityLevel?: 'sedentary' | 'light' | 'moderate' | 'active' | 'very_active';
    bodyFatPercentage?: number;
    muscleMassPercentage?: number;
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
  metabolicConditions?: string[];
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
  reminderTime: string | null;
  mealRemindersEnabled: boolean;
  breakfastReminderTime: string;
  lunchReminderTime: string;
  dinnerReminderTime: string;
  missedWorkoutReminderEnabled: boolean;
  missedWorkoutReminderTime: string;
  augeBatteryReminderEnabled: boolean;
  augeBatteryReminderThreshold: number;
  augeBatteryReminderTime: string;
  eventRemindersEnabled: boolean;
  autoSyncEnabled: boolean;
  appBackground?: SessionBackground;
  homeWidgetOrder?: string[];

  // --- LÍMITES CALIBRADOS DEL ATLETA (KPKN ENGINE) ---
  volumeLimits?: Record<string, { maxSession: number; max: number; min?: number }>;

  /** Fecha última recalibración de volumen (YYYY-MM-DD) */
  volumeLastRecalibrationDate?: string;
  /** Historial de recalibraciones KPKN */
  volumeCalibrationHistory?: VolumeCalibrationEntry[];

  // --- AUTO-CALIBRACIÓN CIBERNÉTICA (Deltas del Usuario) ---
  batteryCalibration?: BatteryCalibration;
}

export type SettingsStatus = 'idle' | 'ready';
export type ReminderPreset = 'light' | 'full';

export interface MobileSettingsSummary extends Settings {
  // Mobile specific summary flags or extra fields
}
