
import { 
    AugeAdaptiveCacheBase, 
    DailyWellnessLog, 
    MuscleRecoveryStatus,
    HYPERTROPHY_ROLE_MULTIPLIERS
} from '@kpkn/shared-types';
import { 
    calculatePersonalizedBatteryTanks, 
    calculateSetBatteryDrain, 
    getDynamicAugeMetrics,
    isSetEffective,
    getEffectiveRPE
} from './fatigue';

// --- CONSTANTES & CONFIGURACIÓN ---

const RECOVERY_PROFILES: Record<string, number> = {
    'fast': 24,   // Recuperación rápida
    'medium': 48, // Estándar
    'slow': 72,   // Grandes grupos/daño alto
    'heavy': 96   // Sistémico/Axial
};

const MUSCLE_PROFILE_MAP: Record<string, string> = {
    'Bíceps': 'fast', 'Tríceps': 'fast', 'Deltoides': 'fast', 'Deltoides Anterior': 'fast', 'Deltoides Lateral': 'fast', 'Deltoides Posterior': 'fast',
    'Pantorrillas': 'fast', 'Abdomen': 'fast', 'Antebrazo': 'fast',
    'Pectorales': 'medium', 'Dorsales': 'medium', 'Hombros': 'medium', 'Trapecio': 'medium',
    'Cuádriceps': 'slow', 'Glúteos': 'slow', 'Aductores': 'medium',
    'Isquiosurales': 'heavy', 'Erectores Espinales': 'heavy', 'Core': 'medium', 'Cuello': 'medium'
};

const clamp = (val: number, min: number, max: number) => Math.min(max, Math.max(min, val));

const safeExp = (val: number): number => {
    const res = Math.exp(val);
    return isNaN(res) || !isFinite(res) ? 0 : res;
};

const transformFatigueToPercent = (value: number, scale: number): number => {
    if (!Number.isFinite(value)) return 0;
    if (scale <= 0) return 0;
    const normalized = Math.max(0, value);
    const percent = 100 * (1 - safeExp(-normalized / scale));
    return clamp(percent, 0, 100);
};

const normalizeLookupKey = (value: string | undefined | null): string => {
    return (value || '')
        .normalize('NFD')
        .replace(/[\u0300-\u036f]/g, '')
        .toLowerCase()
        .trim();
};

/**
 * --- SISTEMA AUGE v3.0: MOTOR DE RECUPERACIÓN ---
 * Versión extraída para cross-platform (dependency inversion).
 * Los datos de cache adaptativo se inyectan.
 */
export const calculateMuscleRecovery = (
    muscleName: string,
    history: any[], // WorkoutLog[]
    adaptiveCache: AugeAdaptiveCacheBase,
    wellbeing: DailyWellnessLog | undefined,
    settings: any,
    // Callbacks para lógica que requiere dependencias externas pesadas o DB
    getInvolvement: (exercise: any, muscle: string) => { role: string, activation: number } | null,
    getRecoveryMultiplier: () => number = () => 1.0
): MuscleRecoveryStatus => {
    const now = Date.now();
    const tanks = calculatePersonalizedBatteryTanks(settings);

    // 1. Determinar Perfil de Recuperación
    let profileKey = 'medium';
    const normalizedMuscle = normalizeLookupKey(muscleName);
    for (const [key, val] of Object.entries(MUSCLE_PROFILE_MAP)) {
        if (normalizeLookupKey(key) === normalizedMuscle) {
            profileKey = val;
            break;
        }
    }
    
    // Cache adaptativo
    let adaptiveHours = adaptiveCache.personalizedRecoveryHours?.[muscleName] || null;
    const baseRecoveryTime = clamp(adaptiveHours ?? RECOVERY_PROFILES[profileKey], 18, 144);

    // 2. Factores de Recuperación (Wellness)
    let recoveryTimeMultiplier = getRecoveryMultiplier(); // Nutrición, etc.

    if (wellbeing) {
        if (wellbeing.stressLevel >= 4) recoveryTimeMultiplier *= 1.4;
        // Penalti por falta de sueño (AUGE Legacy)
        if (wellbeing.sleepHours < 6) recoveryTimeMultiplier *= 1.5;
        else if (wellbeing.sleepHours < 7) recoveryTimeMultiplier *= 1.2;
        else if (wellbeing.sleepHours >= 8.5) recoveryTimeMultiplier *= 0.8;
        else if (wellbeing.sleepHours >= 7.5) recoveryTimeMultiplier *= 0.9;
    }

    const age = settings.userVitals?.age || 25;
    if (age > 35) recoveryTimeMultiplier *= (1 + (age - 35) * 0.01);
    
    const gender = settings.userVitals?.gender || 'male';
    if (gender === 'female' || gender === 'transfemale') recoveryTimeMultiplier *= 0.85;

    const realRecoveryTime = baseRecoveryTime * Math.max(0.5, recoveryTimeMultiplier);

    // 3. Acumulación de Fatiga
    let accumulatedFatigue = 0;
    let effectiveSetsCount = 0;
    let lastSessionDate = 0;

    const relevantHistory = history.filter(log => (now - new Date(log.date).getTime()) < 10 * 24 * 3600 * 1000);

    relevantHistory.forEach(log => {
        const logTime = new Date(log.date).getTime();
        const hoursSince = Math.max(0, (now - logTime) / 3600000);
        let sessionMuscleStress = 0;

        log.completedExercises.forEach((ex: any) => {
            const involvement = getInvolvement(ex, muscleName);
            if (!involvement) return;

            const rawStress = ex.sets.reduce((acc: number, s: any) => {
                if (s.type === 'warmup' || s.isIneffective || !isSetEffective(s)) return acc;
                // info se asume disponible vía closure o el objeto ex ya tiene los metrics
                const metrics = ex.augeMetrics || getDynamicAugeMetrics(undefined, ex.exerciseName);
                const drain = calculateSetBatteryDrain(s, metrics, tanks, 0, ex.restTime || 90);
                return acc + drain.muscularDrainPct;
            }, 0);

            let roleMult = 0.1;
            if (involvement.role === 'primary') roleMult = 1.0;
            else if (involvement.role === 'secondary') roleMult = 0.5;
            else if (involvement.role === 'stabilizer') roleMult = 0.15;

            sessionMuscleStress += (rawStress * roleMult * (involvement.activation || 1.0));
            
            if (hoursSince <= 168 && (involvement.role === 'primary' || (involvement.role === 'secondary' && involvement.activation > 0.6))) {
                effectiveSetsCount += ex.sets.filter((s: any) => s.type !== 'warmup' && !s.isIneffective && isSetEffective(s)).length;
            }
        });

        if (sessionMuscleStress > 0) {
            const k = 2.9957 / Math.max(1, realRecoveryTime);
            accumulatedFatigue += sessionMuscleStress * safeExp(-k * hoursSince);
            if (logTime > lastSessionDate) lastSessionDate = logTime;
        }
    });

    // 4. Batería Final
    const capacity = settings?.athleteScore?.baseCapacity || 500;
    const rawFatiguePercent = (accumulatedFatigue / capacity) * 100;
    const fatiguePenalty = transformFatigueToPercent(rawFatiguePercent, 30);
    let batteryPercentage = clamp(100 - fatiguePenalty, 0, 100);

    // 4.1 Garantía de Frescura (AUGE v3.2)
    if (accumulatedFatigue <= 0.1 && (!wellbeing?.doms || wellbeing.doms <= 2)) {
        batteryPercentage = 100;
    }

    // 5. Override por DOMS y Molestias
    let domsCap = 100;
    if (wellbeing && wellbeing.doms && wellbeing.doms > 2) {
        if (wellbeing.doms === 5) domsCap = 20;
        else if (wellbeing.doms === 4) domsCap = 50;
        else if (wellbeing.doms === 3) domsCap = 85;
    }
    batteryPercentage = Math.min(batteryPercentage, domsCap);

    // Calibración
    const myDelta = adaptiveCache.muscleDeltas?.[muscleName] ?? 0;
    batteryPercentage = clamp(batteryPercentage + myDelta, 0, 100);

    // 6. Estimación de tiempo para volver al 90%
    let hoursToRecovery = 0;
    const targetPercentage = 90;
    if (batteryPercentage < targetPercentage && accumulatedFatigue > 0) {
        const k = 2.9957 / realRecoveryTime;
        const targetFatigue = (100 - targetPercentage) * capacity / 100;
        if (accumulatedFatigue > targetFatigue) {
            hoursToRecovery = -Math.log(targetFatigue / accumulatedFatigue) / k;
        }
    }

    // Estado
    let status: 'fresh' | 'optimal' | 'recovering' | 'exhausted' = 'optimal';
    if (batteryPercentage < 40) status = 'exhausted';
    else if (batteryPercentage < 85) status = 'recovering';
    else if (batteryPercentage >= 95) status = 'fresh';

    return {
        muscleId: muscleName,
        muscleName,
        recoveryScore: Math.round(batteryPercentage),
        hoursSinceLastSession: lastSessionDate > 0 ? Math.round((now - lastSessionDate) / 3600000) : -1,
        status,
        impactingFactors: [],
        effectiveSets: effectiveSetsCount,
        estimatedHoursToRecovery: Math.round(Math.max(0, hoursToRecovery))
    };
};
