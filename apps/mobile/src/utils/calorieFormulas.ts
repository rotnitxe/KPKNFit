// apps/mobile/src/utils/calorieFormulas.ts
// Fórmulas para calcular calorías diarias según objetivo del atleta - Ported from PWA
import type { Program } from '../types/workout';

// Note: Settings and CalorieGoalConfig types are expected to be available in the mobile context
// We define the necessary subset here or import them if available.
// Based on PWA types.ts

export interface CalorieGoalConfig {
  formula: 'mifflin' | 'harris' | 'katch';
  activityLevel: number;
  goal: 'lose' | 'maintain' | 'gain';
  weeklyChangeKg?: number;
  healthMultiplier?: number;
  customActivityFactor?: number;
  activityDaysPerWeek?: number;
  activityHoursPerDay?: number;
}

// Minimal Settings interface for calorie calculations
export interface CalorieSettings {
  userVitals?: {
    age?: number;
    weight?: number;
    height?: number;
    gender?: 'male' | 'female' | 'transmale' | 'transfemale' | 'other';
    activityLevel?: 'sedentary' | 'light' | 'moderate' | 'active' | 'very_active';
    bodyFatPercentage?: number;
  };
  dailyCalorieGoal?: number;
  calorieGoalObjective?: 'deficit' | 'maintenance' | 'surplus';
  calorieGoalConfig?: CalorieGoalConfig;
}

const ACTIVITY_FACTORS: Record<number, number> = {
    1: 1.2,   // sedentario
    2: 1.375, // ligero
    3: 1.55,  // moderado
    4: 1.725, // activo
    5: 1.9,   // muy activo
};

/**
 * Mifflin-St Jeor (más precisa que Harris-Benedict)
 */
export function mifflinStJeor(
    weightKg: number,
    heightCm: number,
    age: number,
    gender: 'male' | 'female'
): number {
    const s = gender === 'male' ? 5 : -161;
    return 10 * weightKg + 6.25 * heightCm - 5 * age + s;
}

/**
 * Harris-Benedict (clásica)
 */
export function harrisBenedict(
    weightKg: number,
    heightCm: number,
    age: number,
    gender: 'male' | 'female'
): number {
    if (gender === 'male') {
        return 88.362 + 13.397 * weightKg + 4.799 * heightCm - 5.677 * age;
    }
    return 447.593 + 9.247 * weightKg + 3.098 * heightCm - 4.33 * age;
}

/**
 * Katch-McArdle (requiere % grasa corporal)
 */
export function katchMcArdle(weightKg: number, bodyFatPercent: number): number {
    const lbm = weightKg * (1 - bodyFatPercent / 100);
    return 370 + 21.6 * lbm;
}

function getMifflinGender(g: string | undefined): 'male' | 'female' {
    if (g === 'female' || g === 'transfemale') return 'female';
    return 'male';
}

/**
 * Calcula TMB según la fórmula seleccionada
 */
export function calculateBMR(
    settings: CalorieSettings,
    config?: CalorieGoalConfig | null
): number | null {
    const userVitals = settings.userVitals;
    if (!userVitals) return null;
    const weight = userVitals.weight;
    const height = userVitals.height;
    const age = userVitals.age;
    const gender = getMifflinGender(userVitals.gender);
    const bodyFat = userVitals.bodyFatPercentage ?? 15;

    if (!weight || !height || !age) return null;

    const formula = config?.formula || 'mifflin';

    switch (formula) {
        case 'harris':
            return harrisBenedict(weight, height, age, gender);
        case 'katch':
            return katchMcArdle(weight, bodyFat);
        case 'mifflin':
        default:
            return mifflinStJeor(weight, height, age, gender);
    }
}

function getActivityIndex(level?: string): number {
    const map: Record<string, number> = {
        sedentary: 1,
        light: 2,
        moderate: 3,
        active: 4,
        very_active: 5,
    };
    return map[level || 'moderate'] ?? 3;
}

function getActivityFactor(config?: CalorieGoalConfig | null): number {
    if (config?.customActivityFactor != null && config.customActivityFactor >= 1 && config.customActivityFactor <= 2) {
        return config.customActivityFactor;
    }
    const days = config?.activityDaysPerWeek ?? 3;
    const hours = config?.activityHoursPerDay ?? 1;
    const derived = 1.2 + (Math.min(7, Math.max(0, days)) / 7) * 0.4 + (Math.min(24, Math.max(0, hours)) / 12) * 0.3;
    if (config?.activityDaysPerWeek != null || config?.activityHoursPerDay != null) {
        return derived;
    }
    const activityIdx = config?.activityLevel ?? 3;
    return ACTIVITY_FACTORS[activityIdx] ?? 1.55;
}

/**
 * Calcula calorías diarias objetivo
 */
export function calculateDailyCalorieGoal(
    settings: CalorieSettings,
    config?: CalorieGoalConfig | null
): number {
    if (settings.dailyCalorieGoal != null && settings.dailyCalorieGoal > 0) {
        return settings.dailyCalorieGoal;
    }

    const bmr = calculateBMR(settings, config);
    if (bmr == null) return 0;

    const effectiveConfig: CalorieGoalConfig | undefined = config ?? (settings.calorieGoalConfig ? { ...settings.calorieGoalConfig, activityLevel: settings.calorieGoalConfig.activityLevel ?? getActivityIndex(settings.userVitals?.activityLevel) } : undefined);
    const factor = getActivityFactor(effectiveConfig);
    let tdee = bmr * factor;

    const goal = config?.goal ?? (settings.calorieGoalObjective === 'deficit' ? 'lose' : settings.calorieGoalObjective === 'surplus' ? 'gain' : 'maintain');
    const weeklyChange = config?.weeklyChangeKg ?? 0.5;

    if (goal === 'lose') {
        tdee -= (weeklyChange * 7700) / 7;
    } else if (goal === 'gain') {
        tdee += (weeklyChange * 7700) / 7;
    }

    const healthMult = config?.healthMultiplier ?? 1;
    return Math.round(tdee * healthMult);
}

export function getWeeklyKcalForBodyFatTrend(
    weightKg: number,
    trendPctPerWeek: number
): number {
    const kgFatPerWeek = (trendPctPerWeek / 100) * weightKg;
    return kgFatPerWeek * 7700;
}

export function calculateCaloriesForBodyFatTrend(
    tdee: number,
    weightKg: number,
    trendPctPerWeek: number
): number {
    const weeklyKcal = getWeeklyKcalForBodyFatTrend(weightKg, trendPctPerWeek);
    return Math.round(tdee + weeklyKcal / 7);
}

export function getBMRAndTDEE(
    settings: CalorieSettings,
    config?: CalorieGoalConfig | null
): { bmr: number | null; tdee: number | null } {
    const bmr = calculateBMR(settings, config);
    if (bmr == null) return { bmr: null, tdee: null };
    const effectiveConfig = config ?? (settings.calorieGoalConfig ? { ...settings.calorieGoalConfig } : undefined);
    const factor = getActivityFactor(effectiveConfig);
    let tdee = bmr * factor;
    const goal = config?.goal ?? (settings.calorieGoalObjective === 'deficit' ? 'lose' : settings.calorieGoalObjective === 'surplus' ? 'gain' : 'maintain');
    const weeklyChange = config?.weeklyChangeKg ?? 0.5;
    if (goal === 'lose') tdee -= (weeklyChange * 7700) / 7;
    else if (goal === 'gain') tdee += (weeklyChange * 7700) / 7;
    const healthMult = config?.healthMultiplier ?? 1;
    return { bmr, tdee: Math.round(tdee * healthMult) };
}
