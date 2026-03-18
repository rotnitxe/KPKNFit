// apps/mobile/src/services/structuralReadinessService.ts
// Readiness estructural (músculos + baterías articulares) — Ported from PWA

import { ARTICULAR_BATTERIES } from '../data/articularBatteryConfig';
import { getMuscleDisplayId } from '../utils/canonicalMuscles';
import { MUSCLE_TO_ARTICULAR_BATTERIES } from './tendonRecoveryService';
import {
  getStructuralReadinessForMuscle as getSharedMuscle,
  getStructuralReadinessForMuscles as getSharedMuscles
} from '@kpkn/shared-domain';
import type { ArticularBatteryId, StructuralReadinessBreakdown } from '@kpkn/shared-types';

export type { StructuralReadinessBreakdown };

const getDisplayLabel = (muscleId: string): string => {
  return getMuscleDisplayId(muscleId) || muscleId;
};

export const getRelatedArticularBatteryIds = (muscleId: string): ArticularBatteryId[] => {
  const displayId = getDisplayLabel(muscleId);
  return [...new Set(MUSCLE_TO_ARTICULAR_BATTERIES[displayId] ?? MUSCLE_TO_ARTICULAR_BATTERIES[muscleId] ?? [])] as ArticularBatteryId[];
};

const getArticularLabel = (id: ArticularBatteryId): string => {
  return ARTICULAR_BATTERIES.find((battery) => battery.id === id)?.shortLabel ?? id;
};

const sharedDeps = {
  getDisplayLabel,
  getRelatedArticularBatteryIds,
  getArticularLabel
};

export const getStructuralReadinessForMuscle = (
  muscleId: string,
  muscleBattery: number,
  articularBatteries?: Record<ArticularBatteryId, { recoveryScore: number }>
): StructuralReadinessBreakdown => {
  return getSharedMuscle(muscleId, muscleBattery, articularBatteries, sharedDeps);
};

export const getStructuralReadinessForMuscles = (
  perMuscle: Record<string, number>,
  articularBatteries?: Record<ArticularBatteryId, { recoveryScore: number }>,
  muscleIds?: string[]
): StructuralReadinessBreakdown[] => {
  return getSharedMuscles(perMuscle, articularBatteries, sharedDeps, muscleIds);
};
