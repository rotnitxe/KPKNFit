// apps/mobile/src/services/volumeCalibrationService.ts
// Motor de Calibración de Volumen — Ported from PWA

import type {
    Program,
    VolumeCalibrationEntry,
    VolumeRecSnapshot,
} from '../types/workout';
import type { PostSessionFeedback } from '../types/volume';
import type { Settings } from '../types/settings';
import { calculateVolumeAdjustment, normalizeMuscleGroup } from './volumeCalculator';

const MAX_ADJUSTMENT_FACTOR = 1.15;
const MIN_ADJUSTMENT_FACTOR = 0.85;
const RECALIBRATION_DAYS = 90;
const MIN_FEEDBACK_WEEKS = 4;

export interface VolumeSuggestion {
    muscle: string;
    currentRec: VolumeRecSnapshot & { frequencyCap: number };
    suggestedRec: VolumeRecSnapshot & { frequencyCap: number };
    factor: number;
    reason: string;
    status: 'recovery_debt' | 'optimal' | 'undertraining';
}

const MUSCLE_ALIASES: Record<string, string[]> = {
    Abdomen: ['Abdominales'],
    Pantorrillas: ['Gemelos'],
};

function getNormalizedFeedbackHistoryForMuscle(
    muscleGroup: string,
    feedbackHistory: PostSessionFeedback[]
): PostSessionFeedback[] {
    const accept = new Set([muscleGroup, ...(MUSCLE_ALIASES[muscleGroup] || [])]);
    const result: PostSessionFeedback[] = [];

    for (const log of feedbackHistory) {
        if (!log.feedback) continue;

        const merged: Record<string, { doms: number; strengthCapacity: number }> = {};
        for (const [key, val] of Object.entries(log.feedback)) {
            const normalized = normalizeMuscleGroup(key);
            if (accept.has(normalized)) {
                const mg = muscleGroup;
                if (!merged[mg]) {
                    merged[mg] = { doms: val.doms, strengthCapacity: val.strengthCapacity };
                } else {
                    merged[mg] = {
                        doms: (merged[mg].doms + val.doms) / 2,
                        strengthCapacity: (merged[mg].strengthCapacity + val.strengthCapacity) / 2,
                    };
                }
            }
        }

        if (Object.keys(merged).length > 0) {
            result.push({
                ...log,
                feedback: merged,
            });
        }
    }

    return result;
}

function applyFactor(value: number, factor: number): number {
    const clamped = Math.max(MIN_ADJUSTMENT_FACTOR, Math.min(MAX_ADJUSTMENT_FACTOR, factor));
    return Math.max(1, Math.round(value * clamped));
}

export interface GetSuggestedVolumeAdjustmentsOptions {
    history?: unknown[];
    postSessionFeedback: PostSessionFeedback[];
    program: Program;
    settings?: Settings;
}

export function getSuggestedVolumeAdjustments(
    options: GetSuggestedVolumeAdjustmentsOptions
): VolumeSuggestion[] {
    const { postSessionFeedback, program } = options;
    const recs = program?.volumeRecommendations;
    if (!recs?.length) return [];

    const suggestions: VolumeSuggestion[] = [];

    for (const rec of recs) {
        const muscleHistory = getNormalizedFeedbackHistoryForMuscle(rec.muscleGroup, postSessionFeedback || []);
        const { factor, suggestion, status } = calculateVolumeAdjustment(rec.muscleGroup, muscleHistory);

        if (status === 'optimal' && Math.abs(factor - 1) < 0.01) continue;

        const prev: VolumeRecSnapshot & { frequencyCap: number } = {
            minEffectiveVolume: rec.minEffectiveVolume,
            maxAdaptiveVolume: rec.maxAdaptiveVolume,
            maxRecoverableVolume: rec.maxRecoverableVolume,
            frequencyCap: rec.frequencyCap,
        };

        const suggestedRec: VolumeRecSnapshot & { frequencyCap: number } = {
            minEffectiveVolume: applyFactor(rec.minEffectiveVolume, factor),
            maxAdaptiveVolume: applyFactor(rec.maxAdaptiveVolume, factor),
            maxRecoverableVolume: applyFactor(rec.maxRecoverableVolume, factor),
            frequencyCap: rec.frequencyCap,
        };

        suggestions.push({
            muscle: rec.muscleGroup,
            currentRec: prev,
            suggestedRec,
            factor,
            reason: suggestion || (status === 'recovery_debt' ? 'Recuperación lenta' : 'Sub-entrenamiento'),
            status,
        });
    }

    return suggestions;
}

export interface ApplyVolumeAdjustmentsParams {
    program: Program;
    suggestions: VolumeSuggestion[];
    source: 'auto' | 'manual';
    onUpdateProgram: (p: Program) => void;
    onUpdateSettings?: (partial: Partial<Settings>) => void;
    settings?: Settings;
}

export function applyVolumeAdjustments(params: ApplyVolumeAdjustmentsParams): void {
    const {
        program,
        suggestions,
        source,
        onUpdateProgram,
        onUpdateSettings,
        settings,
    } = params;

    if (suggestions.length === 0) return;

    const today = new Date().toISOString().slice(0, 10);
    const newRecs = [...(program.volumeRecommendations || [])];
    const changes: VolumeCalibrationEntry['changes'] = [];

    for (const s of suggestions) {
        const idx = newRecs.findIndex(r => r.muscleGroup === s.muscle);
        if (idx < 0) continue;

        const prev: VolumeRecSnapshot = {
            minEffectiveVolume: s.currentRec.minEffectiveVolume,
            maxAdaptiveVolume: s.currentRec.maxAdaptiveVolume,
            maxRecoverableVolume: s.currentRec.maxRecoverableVolume,
        };
        const next: VolumeRecSnapshot = {
            minEffectiveVolume: s.suggestedRec.minEffectiveVolume,
            maxAdaptiveVolume: s.suggestedRec.maxAdaptiveVolume,
            maxRecoverableVolume: s.suggestedRec.maxRecoverableVolume,
        };

        newRecs[idx] = {
            ...newRecs[idx],
            minEffectiveVolume: next.minEffectiveVolume,
            maxAdaptiveVolume: next.maxAdaptiveVolume,
            maxRecoverableVolume: next.maxRecoverableVolume,
        };
        changes.push({ muscle: s.muscle, prev, next, reason: s.reason });
    }

    onUpdateProgram({
        ...program,
        volumeRecommendations: newRecs,
    });

    const entry: VolumeCalibrationEntry = { date: today, source, changes };
    const newHistory = [...(settings?.volumeCalibrationHistory || []), entry];

    if (onUpdateSettings) {
        onUpdateSettings({
            volumeLastRecalibrationDate: today,
            volumeCalibrationHistory: newHistory,
        });
    }
}

export function shouldSuggestRecalibration(
    settings: Settings | null | undefined,
    postSessionFeedback: PostSessionFeedback[] | null | undefined
): boolean {
    const feedback = postSessionFeedback || [];
    const lastDate = settings?.volumeLastRecalibrationDate;

    if (!lastDate) {
        if (feedback.length === 0) return false;
        const sorted = [...feedback].sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime());
        const oldest = sorted[sorted.length - 1];
        const daysSinceOldest = (Date.now() - new Date(oldest.date).getTime()) / (24 * 60 * 60 * 1000);
        return daysSinceOldest >= MIN_FEEDBACK_WEEKS * 7;
    }

    const daysSince = (Date.now() - new Date(lastDate).getTime()) / (24 * 60 * 60 * 1000);
    return daysSince >= RECALIBRATION_DAYS;
}
