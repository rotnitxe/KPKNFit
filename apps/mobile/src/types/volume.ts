// apps/mobile/src/types/volume.ts
import type { Mesocycle } from './workout';

export interface AthleteProfileScore {
    totalScore: number;
    profileLevel: 'Beginner' | 'Intermediate' | 'Advanced';
    lastUpdated: string;
}

export interface VolumeRecommendation {
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
