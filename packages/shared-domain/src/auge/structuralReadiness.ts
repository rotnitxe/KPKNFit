
import type { ArticularBatteryId, StructuralReadinessBreakdown } from '@kpkn/shared-types';

const roundBattery = (value: number): number => Math.round(Math.min(100, Math.max(0, value)));

export const getStructuralReadinessForMuscle = (
  muscleId: string,
  muscleBattery: number,
  articularBatteries: Record<ArticularBatteryId, { recoveryScore: number }> | undefined,
  dependencies: {
    getDisplayLabel: (id: string) => string;
    getRelatedArticularBatteryIds: (id: string) => ArticularBatteryId[];
    getArticularLabel: (id: ArticularBatteryId) => string;
  }
): StructuralReadinessBreakdown => {
  const muscleLabel = dependencies.getDisplayLabel(muscleId);
  const relatedArticularIds = dependencies.getRelatedArticularBatteryIds(muscleId);
  const relatedArticularLabels = relatedArticularIds.map((id) => dependencies.getArticularLabel(id));
  
  const articularScores = relatedArticularIds.map((id) => articularBatteries?.[id]?.recoveryScore ?? 100);
  const articularBattery = articularScores.length > 0
    ? articularScores.reduce((sum, score) => sum + score, 0) / articularScores.length
    : muscleBattery;
  const combinedBattery = (muscleBattery + articularBattery) / 2;

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
  articularBatteries: Record<ArticularBatteryId, { recoveryScore: number }> | undefined,
  dependencies: {
    getDisplayLabel: (id: string) => string;
    getRelatedArticularBatteryIds: (id: string) => ArticularBatteryId[];
    getArticularLabel: (id: ArticularBatteryId) => string;
  },
  muscleIds?: string[]
): StructuralReadinessBreakdown[] => {
  const ids = muscleIds && muscleIds.length > 0 ? muscleIds : Object.keys(perMuscle);
  return ids.map((muscleId) => 
    getStructuralReadinessForMuscle(muscleId, perMuscle[muscleId] ?? 100, articularBatteries, dependencies)
  );
};
