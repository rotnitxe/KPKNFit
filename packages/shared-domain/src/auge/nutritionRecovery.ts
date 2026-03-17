
import type { NutritionRecoveryInput, NutritionRecoveryResult } from '@kpkn/shared-types';

/** RDA aproximados (mg o µg) para micronutrientes clave - valores diarios de referencia */
export const MICRONUTRIENT_RDA: Record<string, { amount: number; unit: string; label: string }> = {
    'Hierro': { amount: 18, unit: 'mg', label: 'Hierro' },
    'Calcio': { amount: 1000, unit: 'mg', label: 'Calcio' },
    'Vitamina D': { amount: 20, unit: 'µg', label: 'Vitamina D' },
    'Magnesio': { amount: 400, unit: 'mg', label: 'Magnesio' },
    'Zinc': { amount: 11, unit: 'mg', label: 'Zinc' },
    'Vitamina B12': { amount: 2.4, unit: 'µg', label: 'Vitamina B12' },
    'Vitamina C': { amount: 90, unit: 'mg', label: 'Vitamina C' },
    'Potasio': { amount: 2600, unit: 'mg', label: 'Potasio' },
    'Sodio': { amount: 2300, unit: 'mg', label: 'Sodio' },
};

/** Alias para matching de micronutrientes (nombre usuario -> clave RDA) */
const MICRONAME_ALIASES: Record<string, string> = {
    'hierro': 'Hierro', 'iron': 'Hierro', 'fe': 'Hierro',
    'calcio': 'Calcio', 'calcium': 'Calcio', 'ca': 'Calcio',
    'vitamina d': 'Vitamina D', 'vitamin d': 'Vitamina D', 'vit d': 'Vitamina D',
    'magnesio': 'Magnesio', 'magnesium': 'Magnesio',
    'zinc': 'Zinc', 'zn': 'Zinc',
    'vitamina b12': 'Vitamina B12', 'vitamin b12': 'Vitamina B12', 'b12': 'Vitamina B12', 'cobalamina': 'Vitamina B12',
    'vitamina c': 'Vitamina C', 'vitamin c': 'Vitamina C', 'vit c': 'Vitamina C', 'acido ascorbico': 'Vitamina C',
    'potasio': 'Potasio', 'potassium': 'Potasio',
    'sodio': 'Sodio', 'sodium': 'Sodio',
};

function resolveToRdaKey(name: string): string | null {
    if (MICRONUTRIENT_RDA[name as keyof typeof MICRONUTRIENT_RDA]) return name;
    const n = name.toLowerCase().trim().normalize('NFD').replace(/\p{Diacritic}/gu, '').replace(/\s+/g, ' ');
    const direct = MICRONAME_ALIASES[n];
    if (direct) return direct;
    const found = Object.entries(MICRONAME_ALIASES).find(([k]) => n.includes(k) || k.includes(n));
    return found ? found[1] : null;
}

/**
 * Calcula el multiplicador de recuperación basado en nutrición.
 */
export function computeNutritionRecoveryMultiplier(input: NutritionRecoveryInput): NutritionRecoveryResult {
    const {
        nutritionLogs,
        settings,
        stressLevel = 3,
        hoursWindow = 48,
    } = input;

    const factors: string[] = [];
    let multiplier = 1.0;

    const now = Date.now();
    const windowStart = now - hoursWindow * 3600000;
    const recentLogs = nutritionLogs.filter(n => new Date(n.date).getTime() > windowStart);

    const calorieGoal = settings.dailyCalorieGoal;
    const proteinGoal = settings.dailyProteinGoal || 150;

    if (recentLogs.length === 0) {
        const fallback = settings.calorieGoalObjective || 'maintenance';
        if (fallback === 'deficit') {
            multiplier = 1.25;
            factors.push('Sin datos recientes; asumiendo déficit según objetivo.');
        } else if (fallback === 'surplus') {
            multiplier = 0.95;
            factors.push('Sin datos recientes; asumiendo superávit según objetivo.');
        }
        return {
            recoveryTimeMultiplier: multiplier,
            status: fallback as 'deficit' | 'maintenance' | 'surplus',
            factors,
        };
    }

    let totalCal = 0, totalProtein = 0;
    recentLogs.forEach(log => {
        (log.foods || []).forEach((f: any) => {
            totalCal += f.calories || 0;
            totalProtein += f.protein || 0;
        });
    });
    const daysInWindow = Math.max(1, hoursWindow / 24);
    const avgCalories = totalCal / daysInWindow;
    const avgProtein = totalProtein / daysInWindow;

    const calRatio = calorieGoal && calorieGoal > 0 ? avgCalories / calorieGoal : 1;
    const proteinRatio = proteinGoal > 0 ? avgProtein / proteinGoal : 1;

    let status: 'deficit' | 'maintenance' | 'surplus' = 'maintenance';

    if (calRatio < 0.9) {
        status = 'deficit';
        const deficitSeverity = 1 - calRatio;
        multiplier = 1 + deficitSeverity * 1.2;
        factors.push(`Déficit calórico (~${Math.round((1 - calRatio) * 100)}%). Recursos limitados para reparación.`);
        if (proteinRatio < 0.7) {
            multiplier *= 1.1;
            factors.push('Proteína insuficiente agrava el déficit.');
        }
    }
    else if (calRatio <= 1.1) {
        status = 'maintenance';
        if (proteinRatio < 0.8) {
            multiplier = 1.05;
            factors.push('Proteína por debajo del objetivo; ligera penalización.');
        } else {
            factors.push('Mantenimiento calórico. Recuperación estándar.');
        }
    }
    else {
        status = 'surplus';
        const surplusPct = (calRatio - 1) * 100;
        if (proteinRatio < 0.6) {
            multiplier = 1.05;
            factors.push('Superávit sin suficiente proteína. La síntesis muscular está limitada.');
        } else if (proteinRatio < 0.8) {
            const baseBenefit = surplusPct < 15 ? 0.92 : surplusPct < 25 ? 0.88 : 0.92;
            multiplier = baseBenefit;
            factors.push(`Superávit moderado (~${Math.round(surplusPct)}%) con proteína subóptima. Beneficio limitado.`);
        } else {
            if (surplusPct < 8) {
                multiplier = 0.96;
                factors.push(`Superávit ligero (~${Math.round(surplusPct)}%). Pequeña mejora en recuperación.`);
            } else if (surplusPct < 18) {
                multiplier = 0.86;
                factors.push(`Superávit óptimo (~${Math.round(surplusPct)}%). Recuperación acelerada.`);
            } else if (surplusPct < 30) {
                multiplier = 0.90;
                factors.push(`Superávit alto (~${Math.round(surplusPct)}%). Beneficio decreciente.`);
            } else {
                multiplier = 0.96;
                factors.push(`Superávit muy alto (~${Math.round(surplusPct)}%). Rendimientos decrecientes; no acelera más.`);
            }
        }
        if (stressLevel >= 4 && multiplier < 1) {
            multiplier = Math.min(1, multiplier + 0.06);
            factors.push('Estrés elevado reduce parte del beneficio nutricional.');
        }
    }

    return {
        recoveryTimeMultiplier: Math.max(0.6, Math.min(1.6, multiplier)),
        status,
        factors,
    };
}

/**
 * Detecta micronutrientes por debajo del 70% del RDA en el consumo del día.
 */
export function getMicronutrientDeficiencies(
    micronutrients: { name: string; amount: number; unit: string }[]
): { name: string; amount: number; unit: string; rda: number; pct: number }[] {
    const deficiencies: { name: string; amount: number; unit: string; rda: number; pct: number }[] = [];

    micronutrients.forEach(m => {
        const rdaKey = resolveToRdaKey(m.name);
        if (!rdaKey) return;
        const rda = MICRONUTRIENT_RDA[rdaKey];
        if (!rda || rda.amount <= 0) return;
        let amount = m.amount;
        const mUnit = (m.unit || '').toLowerCase().replace('µg', 'mcg').replace('ug', 'mcg');
        const rUnit = (rda.unit || '').toLowerCase().replace('µg', 'mcg').replace('ug', 'mcg');
        if (mUnit.includes('mcg') && rUnit.includes('mg')) amount = m.amount / 1000;
        else if (mUnit.includes('mg') && rUnit.includes('mcg')) amount = m.amount * 1000;
        else if (mUnit.includes('g') && !mUnit.includes('mg') && rUnit.includes('mg')) amount = m.amount * 1000;
        const pct = (amount / rda.amount) * 100;
        if (pct < 70) {
            deficiencies.push({
                name: rda.label || rdaKey,
                amount: m.amount,
                unit: m.unit,
                rda: rda.amount,
                pct: Math.round(pct),
            });
        }
    });

    return deficiencies.sort((a, b) => a.pct - b.pct);
}
