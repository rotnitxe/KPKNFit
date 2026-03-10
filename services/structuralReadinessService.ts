import { ARTICULAR_BATTERIES, type ArticularBatteryId } from '../data/articularBatteryConfig';
import { getMuscleDisplayId } from '../utils/canonicalMuscles';
import { MUSCLE_TO_ARTICULAR_BATTERIES } from './tendonRecoveryService';

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

const roundBattery = (value: number): number => Math.round(Math.min(100, Math.max(0, value)));

const getDisplayLabel = (muscleId: string): string => {
  return getMuscleDisplayId(muscleId) || muscleId;
};

export const getRelatedArticularBatteryIds = (muscleId: string): ArticularBatteryId[] => {
  const displayId = getDisplayLabel(muscleId);
  return [...new Set(MUSCLE_TO_ARTICULAR_BATTERIES[displayId] ?? MUSCLE_TO_ARTICULAR_BATTERIES[muscleId] ?? [])];
};

export const getStructuralReadinessForMuscle = (
  muscleId: string,
  muscleBattery: number,
  articularBatteries?: Record<ArticularBatteryId, { recoveryScore: number }>
): StructuralReadinessBreakdown => {
  const muscleLabel = getDisplayLabel(muscleId);
  const relatedArticularIds = getRelatedArticularBatteryIds(muscleId);
  const relatedArticularLabels = relatedArticularIds.map((id) => {
    return ARTICULAR_BATTERIES.find((battery) => battery.id === id)?.shortLabel ?? id;
  });
  const articularScores = relatedArticularIds.map((id) => articularBatteries?.[id]?.recoveryScore ?? 100);
  const articularBattery = articularScores.length > 0
    ? articularScores.reduce((sum, score) => sum + score, 0) / articularScores.length
    : muscleBattery;
  const combinedBattery = articularScores.length > 0
    ? (muscleBattery + articularBattery) / 2
    : muscleBattery;

  return {
    muscleId,
    muscleLabel,
    muscleBattery: roundBattery(muscleBattery),
    articularBattery: roundBattery(articularBattery),
    combinedBattery: roundBattery(combinedBattery),
    limitingBattery: roundBattery(Math.min(muscleBattery, articularBattery)),
    relatedArticularIds,
    relatedArticularLabels,
  };
};

export const getStructuralReadinessForMuscles = (
  perMuscle: Record<string, number>,
  articularBatteries?: Record<ArticularBatteryId, { recoveryScore: number }>,
  muscleIds?: string[]
): StructuralReadinessBreakdown[] => {
  const ids = muscleIds && muscleIds.length > 0 ? muscleIds : Object.keys(perMuscle);
  return ids.map((muscleId) => getStructuralReadinessForMuscle(muscleId, perMuscle[muscleId] ?? 100, articularBatteries));
};
