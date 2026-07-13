import type {
    BodyProgressEntry,
    NutritionCalculationSnapshot,
    NutritionProjection,
    NutritionRiskFlag,
    NutritionTrendStatus,
} from '../types/workout';
import type { NutritionPlan, CalorieGoalConfig } from '../types/nutrition';
import type { Settings } from '../types/settings';
import { calculateBMR, calculateDailyCalorieGoal } from '../utils/calorieFormulas';
import type { CalorieSettings } from '../utils/calorieFormulas';

const DAY_MS = 24 * 60 * 60 * 1000;

const clamp = (value: number, min: number, max: number): number => Math.min(max, Math.max(min, value));

const generateId = (): string => Math.random().toString(36).substring(2, 11);

export interface EngineRiskInput {
    settings: Settings;
    calorieTarget: number;
    goalMetric: 'weight' | 'bodyFat' | 'muscleMass';
    goalValue: number;
    weeklyChangeKg: number;
}

export const estimateBodyFatFromAnthropometrics = (params: {
    weightKg: number;
    heightCm: number;
    age: number;
    gender: Settings['userVitals']['gender'];
}): number => {
    const { weightKg, heightCm, age, gender } = params;
    const heightM = heightCm / 100;
    const bmi = weightKg / (heightM * heightM);
    const maleFactor = gender === 'female' || gender === 'transfemale' ? 0 : 1;
    const estimate = 1.2 * bmi + 0.23 * age - 10.8 * maleFactor - 5.4;
    return Math.round(clamp(estimate, 5, 55) * 10) / 10;
};

export const estimateMuscleMassFromBodyFat = (params: {
    bodyFatPct: number;
    gender: Settings['userVitals']['gender'];
}): number => {
    const { bodyFatPct, gender } = params;
    const isFemale = gender === 'female' || gender === 'transfemale';
    const baseline = isFemale ? 36 : 44;
    const estimate = baseline - (bodyFatPct - 18) * 0.35;
    return Math.round(clamp(estimate, isFemale ? 24 : 28, isFemale ? 48 : 58) * 10) / 10;
};

const getGoalMetricFromPlan = (plan: NutritionPlan): 'weight' | 'bodyFat' | 'muscleMass' => {
    return plan.primaryGoal?.metric ?? plan.goalType;
};

const getGoalValueFromPlan = (plan: NutritionPlan): number => {
    return plan.primaryGoal?.value ?? plan.goalValue;
};

const metricValueFromLog = (
    metric: 'weight' | 'bodyFat' | 'muscleMass',
    log: BodyProgressEntry,
    settings: Settings
): number | null => {
    if (metric === 'weight') return log.weight ?? settings.userVitals?.weight ?? null;
    if (metric === 'bodyFat') return log.bodyFatPercentage ?? settings.userVitals?.bodyFatPercentage ?? null;
    return log.muscleMassPercentage ?? settings.userVitals?.muscleMassPercentage ?? null;
};

const metricQualityFromLog = (
    metric: 'weight' | 'bodyFat' | 'muscleMass',
    log: BodyProgressEntry
): number => {
    if (metric === 'bodyFat') {
        if (log.bodyFatQuality === 'measured') return 1;
        if (log.bodyFatQuality === 'estimated') return 0.75;
        return 0.9;
    }

    if (metric === 'muscleMass') {
        if (log.muscleMassQuality === 'measured') return 1;
        if (log.muscleMassQuality === 'estimated') return 0.75;
        return 0.9;
    }

    return 1;
};

const computeLinearProjection = (points: Array<{ x: number; y: number }>, goal: number): { etaDate: string | null; weeklyDelta: number | null } => {
    if (points.length < 2) return { etaDate: null, weeklyDelta: null };

    const n = points.length;
    const sumX = points.reduce((s, p) => s + p.x, 0);
    const sumY = points.reduce((s, p) => s + p.y, 0);
    const sumXY = points.reduce((s, p) => s + p.x * p.y, 0);
    const sumX2 = points.reduce((s, p) => s + p.x * p.x, 0);
    const denom = n * sumX2 - sumX * sumX;

    if (Math.abs(denom) < 1e-10) return { etaDate: null, weeklyDelta: null };

    const slopePerDay = (n * sumXY - sumX * sumY) / denom;
    const weeklyDelta = slopePerDay * 7;

    const current = points[points.length - 1];
    const diff = goal - current.y;
    if (Math.abs(slopePerDay) < 1e-10) return { etaDate: null, weeklyDelta };

    const headingToGoal = diff > 0 ? slopePerDay > 0 : slopePerDay < 0;
    if (!headingToGoal) return { etaDate: null, weeklyDelta };

    const daysToGoal = diff / slopePerDay;
    if (!Number.isFinite(daysToGoal)) return { etaDate: null, weeklyDelta };

    const eta = new Date((current.x + daysToGoal) * DAY_MS);
    return { etaDate: eta.toISOString().slice(0, 10), weeklyDelta };
};

export const buildNutritionProjection = (params: {
    plan: NutritionPlan;
    bodyProgress: BodyProgressEntry[];
    settings: Settings;
}): NutritionProjection => {
    const { plan, bodyProgress, settings } = params;
    const metric = getGoalMetricFromPlan(plan);
    const goal = getGoalValueFromPlan(plan);

    const points = [...bodyProgress]
        .sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime())
        .map((log) => {
            const y = metricValueFromLog(metric, log, settings);
            const x = new Date(log.date).getTime() / DAY_MS;
            const quality = metricQualityFromLog(metric, log);
            return y == null || Number.isNaN(y) ? null : { x, y, quality };
        })
        .filter((p): p is { x: number; y: number; quality: number } => p != null);

    const projection = computeLinearProjection(
        points.map((point) => ({ x: point.x, y: point.y })),
        goal
    );

    let trendStatus: NutritionTrendStatus = 'unknown';
    if (!projection.etaDate && points.length >= 2) {
        trendStatus = 'behind';
    } else if (projection.etaDate && plan.estimatedEndDate) {
        const etaDays = new Date(projection.etaDate).getTime() / DAY_MS;
        const planDays = new Date(plan.estimatedEndDate).getTime() / DAY_MS;
        const diff = etaDays - planDays;
        if (diff > 7) trendStatus = 'behind';
        else if (diff < -7) trendStatus = 'ahead';
        else trendStatus = 'on_track';
    } else if (projection.etaDate) {
        trendStatus = 'on_track';
    }

    const densityConfidence = clamp(points.length / 10, 0.2, 1);
    const qualityConfidence = points.length
        ? points.reduce((sum, point) => sum + point.quality, 0) / points.length
        : 0.6;
    const confidence = clamp(densityConfidence * qualityConfidence, 0.2, 1);

    return {
        etaDate: projection.etaDate ?? plan.estimatedEndDate ?? null,
        trendStatus,
        weeklyDelta: projection.weeklyDelta,
        confidence: Math.round(confidence * 100) / 100,
    };
};

export const buildNutritionRiskFlags = (input: EngineRiskInput): NutritionRiskFlag[] => {
    const { settings, calorieTarget, goalMetric, goalValue, weeklyChangeKg } = input;
    const flags: NutritionRiskFlag[] = [];
    const isFemale = settings.userVitals?.gender === 'female' || settings.userVitals?.gender === 'transfemale';

    const minSoft = isFemale ? 1200 : 1500;
    const minHard = isFemale ? 1000 : 1200;

    if (calorieTarget < minHard) {
        flags.push({
            id: generateId(),
            code: 'calories_extreme_low',
            severity: 'danger',
            hardStop: true,
            message: `Objetivo calórico extremadamente bajo (< ${minHard} kcal/día). Ajusta el plan antes de guardar.`,
        });
    } else if (calorieTarget < minSoft) {
        flags.push({
            id: generateId(),
            code: 'calories_low',
            severity: 'warning',
            message: `Objetivo calórico bajo para tu perfil (${calorieTarget} kcal/día). Revisa adherencia y recuperación.`,
        });
    }

    if (weeklyChangeKg > 1.8) {
        flags.push({
            id: generateId(),
            code: 'pace_extreme',
            severity: 'danger',
            hardStop: true,
            message: 'Ritmo de cambio semanal extremo (> 1.8 kg/sem). Este objetivo no es seguro.',
        });
    } else if (weeklyChangeKg > 1.2) {
        flags.push({
            id: generateId(),
            code: 'pace_aggressive',
            severity: 'warning',
            message: 'Ritmo de cambio agresivo (> 1.2 kg/sem). Riesgo de pérdida muscular o ganancia excesiva de grasa.',
        });
    }

    if (goalMetric === 'bodyFat' && (goalValue < 5 || goalValue > 45)) {
        flags.push({
            id: generateId(),
            code: 'bodyfat_unhealthy',
            severity: goalValue < 4 ? 'danger' : 'warning',
            hardStop: goalValue < 4,
            message: 'El % de grasa objetivo está fuera de rango saludable para la mayoría de usuarios.',
        });
    }

    if (goalMetric === 'muscleMass' && (goalValue < 20 || goalValue > 60)) {
        flags.push({
            id: generateId(),
            code: 'muscle_unrealistic',
            severity: 'warning',
            message: 'El % de músculo objetivo parece poco realista. Revisa tu meta y horizonte temporal.',
        });
    }

    if (goalMetric === 'weight') {
        const hCm = settings.userVitals?.height;
        if (hCm && hCm > 0) {
            const bmi = goalValue / ((hCm / 100) * (hCm / 100));
            if (bmi < 17 || bmi > 33) {
                flags.push({
                    id: generateId(),
                    code: 'goal_bmi_extreme',
                    severity: bmi < 16.5 || bmi > 35 ? 'danger' : 'warning',
                    hardStop: bmi < 16.5 || bmi > 35,
                    message: `La meta de peso implica IMC ${bmi.toFixed(1)}, fuera de un rango recomendado.`,
                });
            }
        }
    }

    return flags;
};

export const buildCalculationSnapshot = (params: {
    settings: Settings;
    calorieGoalConfig: CalorieGoalConfig;
}): NutritionCalculationSnapshot => {
    const { settings, calorieGoalConfig } = params;
    const calorieSettings: CalorieSettings = {
        userVitals: settings.userVitals,
        dailyCalorieGoal: settings.dailyCalorieGoal,
        calorieGoalObjective: settings.calorieGoalObjective,
        calorieGoalConfig: settings.calorieGoalConfig as any,
    };
    const bmr = calculateBMR(calorieSettings, calorieGoalConfig);

    const levelMap: Record<number, number> = {
        1: 1.2,
        2: 1.375,
        3: 1.55,
        4: 1.725,
        5: 1.9,
    };

    const activityFactor = calorieGoalConfig.customActivityFactor
        ?? levelMap[calorieGoalConfig.activityLevel]
        ?? 1.55;

    const calorieTarget = calculateDailyCalorieGoal(calorieSettings, calorieGoalConfig);

    const tdee = bmr == null ? null : Math.round(bmr * activityFactor);

    return {
        formula: calorieGoalConfig.formula,
        activityFactor,
        bmr,
        tdee,
        calorieTarget,
        generatedAt: new Date().toISOString(),
    };
};
