// services/auge.ts
// ============================================================================
// AUGE v3.0 — SINGLE SOURCE OF TRUTH
// ============================================================================
// Este archivo es el ÚNICO punto de entrada para TODO cálculo de fatiga,
// estrés, recuperación, baterías y clasificación de esfuerzo en la app.
//
// REGLA: Ningún componente, hook o servicio debe calcular fatiga por su cuenta.
//        Todo pasa por aquí.
// ============================================================================

// ─── RE-EXPORTS: Motor de Fatiga (fatigueService) ───────────────────────────

export {
    getDynamicAugeMetrics,
    getEffectiveRPE,
    calculatePersonalizedBatteryTanks,
    calculateSetBatteryDrain,
    calculatePredictedSessionDrain,
    calculateSetStress,
    calculateSpinalScore,
    calculateExerciseFatigueScale,
    isSetEffective,
    calculateCompletedSessionStress,
    calculateCompletedSessionDrainBreakdown,
    normalizeToTenScale,
    WEEKLY_CNS_FATIGUE_REFERENCE,
} from './fatigueService';

// ─── RE-EXPORTS: Motor de Recuperación (recoveryService) ────────────────────

export {
    calculateMuscleBattery,
    calculateSystemicFatigue,
    calculateDailyReadiness,
    calculateGlobalBatteries,
    getPerMuscleBatteries,
    getSpinalDrainByExercise,
    applyPrecalibrationToBattery,
    applyPrecalibrationReadinessOnly,
    PRECALIBRATION_INTENSITY_TO_RPE,
    ACCORDION_MUSCLES,
    learnRecoveryRate,
    checkPendingSurveys,
    calculateSleepRecommendations,
    RECOVERY_FACTORS,
    type BatteryAuditLog,
    type SpinalDrainEntry,
    type PrecalibrationExerciseInput,
    type PrecalibrationReadinessInput,
} from './recoveryService';

// ─── CONSTANTES CENTRALIZADAS ───────────────────────────────────────────────

/**
 * Multiplicadores de rol muscular para conteo de HIPERTROFIA.
 * Miden el estímulo mecánico real que recibe un músculo según su rol.
 * Estabilizadores/Neutralizadores = 0 porque no hacen ROM significativo.
 */
export const HYPERTROPHY_ROLE_MULTIPLIERS: Record<string, number> = {
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
export const FATIGUE_ROLE_MULTIPLIERS: Record<string, number> = {
    primary: 1.0,
    secondary: 0.6,
    stabilizer: 0.3,
    neutralizer: 0.15,
};

/**
 * Pesos de rol para DISPLAY UI (peso visual relativo en listas de ejercicios).
 * Incluye estabilizadores con peso visual reducido para mostrar su participación.
 */
export const DISPLAY_ROLE_WEIGHTS: Record<string, number> = {
    primary: 1.0,
    secondary: 0.5,
    stabilizer: 0.4,
    neutralizer: 0.2,
};

// ─── FUNCIONES DERIVADAS ────────────────────────────────────────────────────

import { getEffectiveRPE as _getEffectiveRPE } from './fatigueService';

/**
 * Multiplicador de volumen efectivo para hipertrofia basado en la intensidad.
 * Usa getEffectiveRPE de AUGE como fuente, eliminando la reimplementación inline.
 *
 * RPE ≥10 → 1.2 (fallo amplifica estímulo)
 * RPE 8-9 → 1.0 (zona óptima estándar)
 * RPE <8  → 0.6 (bombeo/calentamiento, cuenta parcial)
 */
export const getEffectiveVolumeMultiplier = (set: any): number => {
    const rpe = _getEffectiveRPE(set);
    if (rpe >= 10) return 1.2;
    if (rpe >= 8) return 1.0;
    return 0.6;
};

/**
 * Clasificación semántica del estrés de sesión.
 * Fuente única para todos los componentes que muestran etiquetas de estrés.
 */
export const classifyStressLevel = (score: number): { label: string; color: string; barColor: string } => {
    if (score < 40) return { label: 'Bajo', color: 'text-sky-400', barColor: 'bg-sky-500' };
    if (score < 80) return { label: 'Óptimo', color: 'text-green-400', barColor: 'bg-green-500' };
    if (score < 120) return { label: 'Alto', color: 'text-orange-400', barColor: 'bg-orange-500' };
    return { label: 'Excesivo', color: 'text-red-500', barColor: 'bg-red-500' };
};

/**
 * Clasificación semántica del ACWR (Acute:Chronic Workload Ratio).
 * Fuente única para analysisService y cualquier componente que interprete ACWR.
 */
export const classifyACWR = (acwr: number): { interpretation: string; color: string } => {
    if (acwr < 0.8) return { interpretation: 'Sub-entrenando', color: 'text-sky-400' };
    if (acwr > 1.5) return { interpretation: 'Alto Riesgo', color: 'text-red-400' };
    if (acwr > 1.3) return { interpretation: 'Zona de Riesgo', color: 'text-yellow-400' };
    return { interpretation: 'Zona Segura', color: 'text-green-400' };
};
