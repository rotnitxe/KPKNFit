// Pure classification and scaling utilities for the AUGE engine.
// Platform-agnostic: no Tailwind, no StyleSheet, no DOM.
// Both PWA and RN import from here; each platform applies its own color format.

const clamp = (val: number, min: number, max: number): number =>
  Math.min(max, Math.max(min, val));

/**
 * Normaliza un puntaje Verro (0-100) a escala 1-10.
 * Fuente única para fatigaService en PWA y móvil.
 */
export const normalizeToTenScale = (verroScore: number): number => {
  return Math.min(10, Math.max(1, parseFloat(((verroScore / 10) * 10).toFixed(1))));
};

/**
 * Multiplicador de volumen efectivo según RPE del set.
 * RPE >= 10 → 1.2x, RPE >= 8 → 1.0x, else → 0.6x.
 */
export const getEffectiveVolumeMultiplier = (
  rpe: number,
): number => {
  if (rpe >= 10) return 1.2;
  if (rpe >= 8) return 1.0;
  return 0.6;
};

/**
 * Etiqueta semántica de ACWR (Acute:Chronic Workload Ratio).
 * Retorna zona + interpretación. Los colores hex se aplican por plataforma.
 *
 * Zonas:
 *  < 0.8  → 'under'    (Sub-entrenando)
 *  0.8-1.3 → 'safe'    (Zona Segura)
 *  1.3-1.5 → 'caution' (Zona de Riesgo)
 *  > 1.5  → 'danger'   (Alto Riesgo)
 */
export type AcwrZone = 'under' | 'safe' | 'caution' | 'danger';

export const classifyAcwrZone = (acwr: number): AcwrZone => {
  if (acwr < 0.8) return 'under';
  if (acwr > 1.5) return 'danger';
  if (acwr > 1.3) return 'caution';
  return 'safe';
};

export const ACWR_ZONE_LABELS: Record<AcwrZone, string> = {
  under: 'Sub-entrenando',
  safe: 'Zona Segura',
  caution: 'Zona de Riesgo',
  danger: 'Alto Riesgo',
};

export const ACWR_ZONE_COLORS: Record<AcwrZone, string> = {
  under: '#38BDF8',
  safe: '#00FF9D',
  caution: '#FFD600',
  danger: '#FF2E43',
};

/**
 * Etiqueta semántica de estrés de sesión.
 *
 * Niveles:
 *  < 40  → 'low'      (Bajo)
 *  40-80 → 'optimal'  (Óptimo)
 *  80-120 → 'high'    (Alto)
 *  > 120 → 'excessive' (Excesivo)
 */
export type StressLevel = 'low' | 'optimal' | 'high' | 'excessive';

export const classifyStressZone = (score: number): StressLevel => {
  if (score < 40) return 'low';
  if (score < 80) return 'optimal';
  if (score < 120) return 'high';
  return 'excessive';
};

export const STRESS_ZONE_LABELS: Record<StressLevel, string> = {
  low: 'Bajo',
  optimal: 'Óptimo',
  high: 'Alto',
  excessive: 'Excesivo',
};

export const STRESS_ZONE_COLORS: Record<StressLevel, string> = {
  low: '#38BDF8',
  optimal: '#00FF9D',
  high: '#FFD600',
  excessive: '#FF2E43',
};

/**
 * Ajusta la tasa de aprendizaje de recuperación.
 * Pure math: diff entre sensación manual y score calculado.
 */
export const learnRecoveryRate = (
  currentMultiplier: number,
  calculatedScore: number,
  manualFeel: number,
): number => {
  const diff = manualFeel - calculatedScore;
  const adjustment = diff * 0.005;
  return clamp(currentMultiplier + adjustment, 0.5, 2.0);
};

// ─── Thin fatigue wrappers ─────────────────────────────────────────────────
// These are identical in PWA and RN. Moved here to eliminate duplication.
// They depend on calculatePersonalizedBatteryTanks and calculateSetBatteryDrain
// which are already in ./fatigue.

import { calculatePersonalizedBatteryTanks, calculateSetBatteryDrain } from './fatigue';

/**
 * Estrés muscular de un set individual.
 * Wrapper sobre calculateSetBatteryDrain → muscularDrainPct.
 */
export const calculateSetStress = (set: any, info: any | undefined, restTime: number = 90): number => {
  const defaultTanks = calculatePersonalizedBatteryTanks({});
  const drain = calculateSetBatteryDrain(set, info, defaultTanks, 0, restTime);
  return drain.muscularDrainPct;
};

/**
 * Puntaje espinal de un set individual.
 * Wrapper sobre calculateSetBatteryDrain → spinalDrainPct.
 */
export const calculateSpinalScore = (set: any, info: any | undefined): number => {
  const defaultTanks = calculatePersonalizedBatteryTanks({});
  const drain = calculateSetBatteryDrain(set, info, defaultTanks, 0, 90);
  return drain.spinalDrainPct;
};

/**
 * Escala de fatiga de un ejercicio (1-10).
 * Suma CNS + 50% muscular de cada set, normaliza.
 */
export const calculateExerciseFatigueScale = (exercise: any, info: any | undefined): number => {
  if (!exercise.sets?.length) return 0;
  const defaultTanks = calculatePersonalizedBatteryTanks({});
  let totalDrain = 0;
  exercise.sets.forEach((s: any, idx: number) => {
    const drain = calculateSetBatteryDrain(s, info, defaultTanks, idx, exercise.restTime || 90);
    totalDrain += drain.cnsDrainPct + (drain.muscularDrainPct * 0.5);
  });
  return Math.min(10, Math.round(totalDrain / 2));
};
