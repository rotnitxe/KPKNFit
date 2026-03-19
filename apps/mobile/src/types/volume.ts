// apps/mobile/src/types/volume.ts
import type { Mesocycle } from './workout';

// Re-export VolumeRecommendation (KPKN per-muscle type) from workout
export type {
  AthleteProfileScore,
  VolumeRecommendation,
  VolumeRecSnapshot,
  VolumeCalibrationEntry,
} from './workout';

// Separate type for weekly volume calculation (used by volumeCalculator)
export interface WeeklyVolumeRecommendation {
  minSets: number;
  maxSets: number;
  optimalSets: number;
  type: 'sets' | 'lifts';
  reasoning: string;
}

export interface PostSessionFeedback {
  date: string;
  feedback: Record<string, { doms: number; strengthCapacity: number }>;
}

export interface MuscleVolumeThresholds {
  min: number;
  optimal: number;
  max: number;
  source: 'program' | 'israetel' | 'kpnk';
  rangeLabel: string;
}
