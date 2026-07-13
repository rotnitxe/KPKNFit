
// AUGE Core Types

export type MuscleRole = 'primary' | 'secondary' | 'stabilizer' | 'neutralizer';

export type CanonicalMuscle =
    | 'Pectorales' | 'Dorsales' | 'Trapecio' | 'Deltoides'
    | 'Tríceps' | 'Bíceps' | 'Antebrazo' | 'Abdomen'
    | 'Cuádriceps' | 'Isquiosurales' | 'Glúteos' | 'Aductores' | 'Pantorrillas'
    | 'Core' | 'Erectores Espinales' | 'Cuello';

/** Coeficiente de Participación Sinérgica (AUGE) */
export interface InvolvesMuscle {
    muscle: CanonicalMuscle | string;
    role: MuscleRole;
    activation?: number; // K_rol
    emphasis?: string;
}

/** Perfil de estrés del ejercicio */
export interface AugeExerciseMetrics {
    efc?: number; // Costo Metabólico/Fatiga Local (1-5)
    ssc?: number; // Costo Estructural/Espinal (0-2.0)
    cnc?: number; // Costo Neural Central (1-5)
    ttc?: number; // Estrés tendinoso (0-2.0)
}

/** Estado de recuperación de un músculo */
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

export type ArticularBatteryId = 'shoulder' | 'elbow' | 'knee' | 'hip' | 'ankle' | 'cervical';

export interface ArticularBatteryState {
  recoveryScore: number;
  estimatedHoursToRecovery: number;
  status: 'optimal' | 'recovering' | 'exhausted';
  accumulatedStress: number;
}

// Tipos base para evitar dependencias circulares con el root
export interface WorkoutLog {
  id: string;
  date: string;
  completedExercises: any[];
  [key: string]: any;
}

/** Ejercicio base (subset portable — root types.ts tiene la versión completa con ~70 campos) */
export interface ExerciseMuscleInfoBase {
  id: string;
  name: string;
  involvedMuscles: any[];
  [key: string]: any;
}

/** @deprecated Use ExerciseMuscleInfoBase for new shared code */
export type ExerciseMuscleInfo = ExerciseMuscleInfoBase;

export interface Settings {
  [key: string]: any;
}

/** Grupo muscular base (subset portable — root types.ts tiene la versión completa) */
export interface MuscleGroupInfoBase {
  id: string;
  name: string;
  relatedJoints?: string[];
  relatedTendons?: string[];
  [key: string]: any;
}

/** @deprecated Use MuscleGroupInfoBase for new shared code */
export type MuscleGroupInfo = MuscleGroupInfoBase;

export interface StructuralReadinessBreakdown {
  muscleId: string;
  muscleLabel: string;
  muscleBattery: number;
  articularBattery: number;
  combinedBattery: number;
  limitingBattery: number;
  relatedArticularIds: ArticularBatteryId[];
  relatedArticularLabels: string[];
}

export interface TendonImbalanceAlert {
  type: 'warning' | 'danger';
  muscleLabel: string;
  articularLabel: string;
  muscleBattery: number;
  articularBattery: number;
  gap: number;
  message: string;
}

export interface TendonCompensationSuggestion {
  type: 'biomechanical' | 'nutrition';
  title: string;
  message: string;
}

/** Cache adaptativo portable (AUGE v2.0) — subset compartido entre PWA y RN */
export interface AugeAdaptiveCacheBase {
    cnsDelta: number;
    muscularDelta: number;
    spinalDelta: number;
    muscleDeltas?: Record<string, number>;
    lastCalibrated: string;
    gpCurve?: any; // Datos para la curva de fatiga Gaussiana
    personalizedRecoveryHours?: Record<string, number>;
}

/** @deprecated Use AugeAdaptiveCacheBase for new code. Alias kept for backward compatibility. */
export type AugeAdaptiveCache = AugeAdaptiveCacheBase;

/** Wellness diario (Impacto en AUGE) */
export interface DailyWellnessLog {
    date: string; // YYYY-MM-DD
    sleepHours: number;
    sleepQuality: number; // 1-5
    nutritionStatus: 'deficit' | 'maintenance' | 'surplus';
    stressLevel: number; // 1-5
    hydration: 'good' | 'poor';
    doms?: number; // 1-5
}

/** Semáforo de Recuperación AUGE */
export interface AugeReadinessVerdict {
    status: 'green' | 'yellow' | 'red';
    stressMultiplier: number;
    cnsBattery: number;
    diagnostics: string[];
    recommendation: string;
}

/**
 * Multiplicadores de rol muscular para conteo de HIPERTROFIA.
 * Miden el estímulo mecánico real que recibe un músculo según su rol.
 * Estabilizadores/Neutralizadores = 0 porque no hacen ROM significativo.
 */
export const HYPERTROPHY_ROLE_MULTIPLIERS: Record<MuscleRole, number> = {
    primary: 1.0,
    secondary: 0.5,
    stabilizer: 0.0,
    neutralizer: 0.0,
};

/**
 * Multiplicadores de rol muscular para conteo de FATIGA.
 * Miden el coste sistémico (drenaje de batería) que genera un músculo.
 * Los estabilizadores SÍ drenan porque hacen esfuerzo isométrico/compresión.
 */
export const FATIGUE_ROLE_MULTIPLIERS: Record<MuscleRole, number> = {
    primary: 1.0,
    secondary: 0.6,
    stabilizer: 0.3,
    neutralizer: 0.15,
};

/**
 * Pesos de rol para DISPLAY UI (peso visual relativo en listas de ejercicios).
 * Incluye estabilizadores con peso visual reducido para mostrar su participación.
 */
export const DISPLAY_ROLE_WEIGHTS: Record<MuscleRole, number> = {
    primary: 1.0,
    secondary: 0.5,
    stabilizer: 0.4,
    neutralizer: 0.2,
};
