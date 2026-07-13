// services/volumeCalibrationService.ts
import {
    Program,
    PostSessionFeedback,
    Settings,
    VolumeCalibrationEntry,
    VolumeRecSnapshot,
} from '../types';
import {
    calculateVolumeAdjustment,
    normalizeMuscleGroup,
} from './volumeCalculator';

const BACKEND_URL = (import.meta as any)?.env?.VITE_BACKEND_URL || 'http://localhost:8000';

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

/**
 * Obtiene el historial de feedback normalizado para un músculo.
 * Mapea claves de feedback (ej. "deltoides-anterior") a muscleGroup canónico ("Deltoides Anterior").
 */
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
                feedback: merged as any,
            });
        }
    }

    return result;
}

/**
 * Aplica factor de ajuste respetando ±15% y redondeo a enteros.
 */
function applyFactor(
    value: number,
    factor: number
): number {
    const clamped = Math.max(MIN_ADJUSTMENT_FACTOR, Math.min(MAX_ADJUSTMENT_FACTOR, factor));
    return Math.max(1, Math.round(value * clamped));
}

export interface GetSuggestedVolumeAdjustmentsOptions {
    history?: any[];
    postSessionFeedback: PostSessionFeedback[];
    program: Program;
    settings?: Settings;
    isOnline?: boolean;
}

/**
 * Llama al backend Python para recalibración adaptativa (EMA, regresión).
 * Fallback silencioso: devuelve null en error.
 */
export async function fetchAdaptiveRecalibrationFromBackend(payload: {
    volumeRecommendations: { muscleGroup: string; minEffectiveVolume: number; maxAdaptiveVolume: number; maxRecoverableVolume: number; frequencyCap: number }[];
    postSessionFeedback: PostSessionFeedback[];
    settings?: Settings | null;
}): Promise<{ suggestions: VolumeSuggestion[]; confidence: number } | null> {
    try {
        const res = await fetch(`${BACKEND_URL}/api/volume/adaptive-recalibrate`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                volumeRecommendations: payload.volumeRecommendations,
                postSessionFeedback: payload.postSessionFeedback,
                settings: payload.settings ? { athleteScore: payload.settings.athleteScore } : undefined,
            }),
        });
        if (!res.ok) return null;
        const data = await res.json();
        const raw = (data.suggestions || []).map((s: any) => ({
            muscle: s.muscle,
            currentRec: { ...s.currentRec, frequencyCap: s.currentRec?.frequencyCap ?? 4 },
            suggestedRec: { ...s.suggestedRec, frequencyCap: s.suggestedRec?.frequencyCap ?? 4 },
            factor: s.factor ?? 1,
            reason: s.reason ?? '',
            status: s.status ?? 'optimal',
        }));
        return { suggestions: raw as VolumeSuggestion[], confidence: data.confidence ?? 0 };
    } catch {
        return null;
    }
}

/**
 * Obtiene sugerencias de ajuste de volumen basadas en feedback post-sesión.
 * Si isOnline y backend disponible → usa Python. Si no → usa lógica TS (offline).
 */
export async function getSuggestedVolumeAdjustments(
    options: GetSuggestedVolumeAdjustmentsOptions
): Promise<VolumeSuggestion[]> {
    const { postSessionFeedback, program, isOnline } = options;
    const recs = program?.volumeRecommendations;
    if (!recs?.length) return [];

    if (isOnline) {
        const backend = await fetchAdaptiveRecalibrationFromBackend({
            volumeRecommendations: recs,
            postSessionFeedback: postSessionFeedback || [],
            settings: options.settings,
        });
        if (backend?.suggestions?.length) return backend.suggestions;
    }

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

/**
 * Aplica las sugerencias de volumen al programa y actualiza historial.
 */
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

/**
 * Indica si conviene sugerir recalibración.
 * - Sin fecha previa: true si hay ≥4 semanas de feedback.
 * - Con fecha: true si han pasado ≥90 días.
 */
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
