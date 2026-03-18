// apps/mobile/src/types/volume.ts
import type { Mesocycle } from './workout';
import type { AthleteProfileScore, VolumeRecommendation, VolumeRecSnapshot, VolumeCalibrationEntry } from '@kpkn/shared-types';

// Re-export from shared types to ensure consistency
export { AthleteProfileScore, VolumeRecommendation, VolumeRecSnapshot, VolumeCalibrationEntry };

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
