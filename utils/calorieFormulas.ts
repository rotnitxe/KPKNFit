// utils/calorieFormulas.ts
// Fórmulas para calcular calorías diarias según objetivo del atleta

import type { CalorieGoalConfig, Settings } from '../types';

const ACTIVITY_FACTORS: Record<number, number> = {
    1: 1.2,   // sedentario
    2: 1.375, // ligero
    3: 1.55,  // moderado
    4: 1.725, // activo
    5: 1.9,   // muy activo
};

/**
 * Mifflin-St Jeor (más precisa que Harris-Benedict)
 * TMB = (10 * weight) + (6.25 * height) - (5 * age) + s
 * s = +5 hombre, -161 mujer
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
 * Hombre: 88.362 + (13.397 * weight) + (4.799 * height) - (5.677 * age)
 * Mujer: 447.593 + (9.247 * weight) + (3.098 * height) - (4.330 * age)
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
 * Katch-McArdle (requiere % grasa corporal, mejor para atletas)
 * LBM = weight * (1 - bodyFat/100)
 * TMB = 370 + (21.6 * LBM)
 */
export function katchMcArdle(weightKg: number, bodyFatPercent: number): number {
    const lbm = weightKg * (1 - bodyFatPercent / 100);
    return 370 + 21.6 * lbm;
}

/**
 * Calcula TMB según la fórmula seleccionada
 */
export function calculateBMR(
    settings: Settings,
    config?: CalorieGoalConfig | null
): number | null {
    const { userVitals } = settings;
    const weight = userVitals.weight;
    const height = userVitals.height;
    const age = userVitals.age;
    const gender = (userVitals.gender || 'male') as 'male' | 'female';
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

/**
 * Mapea activityLevel de settings a índice numérico
 */
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

/**
 * Calcula calorías diarias objetivo
 * TMB * factor actividad * ajuste objetivo * healthMultiplier
 */
export function calculateDailyCalorieGoal(
    settings: Settings,
    config?: CalorieGoalConfig | null
): number {
    const bmr = calculateBMR(settings, config);
    if (bmr == null) return settings.dailyCalorieGoal ?? 2500;

    const activityIdx = config?.activityLevel ?? getActivityIndex(settings.userVitals.activityLevel);
    const factor = ACTIVITY_FACTORS[activityIdx] ?? 1.55;
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
