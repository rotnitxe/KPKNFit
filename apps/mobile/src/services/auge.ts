// apps/mobile/src/services/auge.ts
// AUGE v3.0 — SINGLE SOURCE OF TRUTH — Ported from PWA

export {
  getDynamicAugeMetrics,
  getEffectiveRPE,
  calculatePersonalizedBatteryTanks,
  calculateSetBatteryDrain,
  calculatePredictedSessionDrain,
  isSetEffective,
  calculateCompletedSessionStress,
  calculateCompletedSessionDrainBreakdown,
  calculateExerciseFatigueScale,
  calculateSetStress,
  calculateSpinalScore,
  normalizeToTenScale,
  WEEKLY_CNS_FATIGUE_REFERENCE,
} from './fatigueService';

export {
  calculateMuscleBattery,
  calculateGlobalBatteries,
  calculateSystemicFatigue,
  calculateDailyReadiness,
  getPerMuscleBatteries,
  getSpinalDrainByExercise,
  applyPrecalibrationToBattery,
  applyPrecalibrationReadinessOnly,
  learnRecoveryRate,
  checkPendingSurveys,
  calculateSleepRecommendations,
  RECOVERY_FACTORS,
  ACCORDION_MUSCLES,
  PRECALIBRATION_INTENSITY_TO_RPE,
  type BatteryAuditLog,
  type SpinalDrainEntry,
  type PrecalibrationExerciseInput,
  type PrecalibrationReadinessInput,
} from './recoveryService';

export {
  calculateTTC,
  calculateSetTendonDrain,
  getTendonDrainInput,
} from './ttcService';

export {
  getArticularBatteriesForExercise,
  ARTICULAR_BATTERIES,
  type ArticularBatteryId,
} from '../data/articularBatteryConfig';

export {
  calculateArticularBatteries,
  MUSCLE_TO_ARTICULAR_BATTERIES,
  type ArticularBatteryState,
} from './tendonRecoveryService';

export {
  getTendonImbalanceAlerts,
  getTendonCompensationSuggestions,
  type TendonImbalanceAlert,
  type TendonCompensationSuggestion,
} from './tendonAlertsService';

export {
  getRelatedArticularBatteryIds,
  getStructuralReadinessForMuscle,
  getStructuralReadinessForMuscles,
  type StructuralReadinessBreakdown,
} from './structuralReadinessService';

export {
  HYPERTROPHY_ROLE_MULTIPLIERS,
  FATIGUE_ROLE_MULTIPLIERS,
  DISPLAY_ROLE_WEIGHTS,
} from '@kpkn/shared-types';

import { getEffectiveRPE as _getEffectiveRPE } from './fatigueService';

export const getEffectiveVolumeMultiplier = (set: unknown): number => {
  const rpe = _getEffectiveRPE(set as any);
  if (rpe >= 10) return 1.2;
  if (rpe >= 8) return 1.0;
  return 0.6;
};

export const classifyStressLevel = (score: number): { label: string; color: string; barColor: string } => {
  if (score < 40) return { label: 'Bajo', color: '#38BDF8', barColor: '#0EA5E9' };
  if (score < 80) return { label: 'Óptimo', color: '#00FF9D', barColor: '#00FF9D' };
  if (score < 120) return { label: 'Alto', color: '#FFD600', barColor: '#FFD600' };
  return { label: 'Excesivo', color: '#FF2E43', barColor: '#FF2E43' };
};

export const classifyACWR = (acwr: number): { interpretation: string; color: string } => {
  if (acwr < 0.8) return { interpretation: 'Sub-entrenando', color: '#38BDF8' };
  if (acwr > 1.5) return { interpretation: 'Alto Riesgo', color: '#FF2E43' };
  if (acwr > 1.3) return { interpretation: 'Zona de Riesgo', color: '#FFD600' };
  return { interpretation: 'Zona Segura', color: '#00FF9D' };
};
