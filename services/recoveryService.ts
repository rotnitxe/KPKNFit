
import { WorkoutLog, ExerciseMuscleInfo, MuscleHierarchy, SleepLog, PostSessionFeedback, PendingQuestionnaire, DailyWellbeingLog, Settings, WaterLog, NutritionLog } from '../types';
import { computeNutritionRecoveryMultiplier } from './nutritionRecoveryService';
import { calculateSetStress, isSetEffective, calculatePersonalizedBatteryTanks, calculateSetBatteryDrain } from './fatigueService';
import { 
    calculateMuscleRecovery as calculateMuscleRecoveryShared,
    computeAugeReadiness as computeAugeReadinessShared,
    AugeEngineConfig
} from '@kpkn/shared-domain';
import { buildExerciseIndex, findExercise, findExerciseWithFallback, ExerciseIndex } from '../utils/exerciseIndex';

import { inferInvolvedMuscles } from '../data/inferMusclesFromName';
import { getLocalDateString } from '../utils/dateUtils';
import { calculateArticularBatteries } from './tendonRecoveryService';
import { getCachedAdaptiveData, type AugeAdaptiveCache } from './augeAdaptiveService';
import { getMuscleDisplayId, matchesMuscleTarget, normalizeCanonicalMuscle } from '../utils/canonicalMuscles';

// --- CONSTANTES & CONFIGURACIÓN ---

const RECOVERY_PROFILES: Record<string, number> = {
    'fast': 24,   // Recuperación rápida (Bíceps, Hombro Lateral, Gemelo)
    'medium': 48, // Estándar (Pecho, Espalda Alta)
    'slow': 72,   // Grandes grupos/daño alto (Cuádriceps, Glúteos)
    'heavy': 96   // Sistémico/Axial (Erectores, Isquiosurales)
};

const MUSCLE_PROFILE_MAP: Record<string, string> = {
    'Bíceps': 'fast', 'Tríceps': 'fast', 'Deltoides': 'fast', 'Deltoides Anterior': 'fast', 'Deltoides Lateral': 'fast', 'Deltoides Posterior': 'fast',
    'Pantorrillas': 'fast', 'Abdomen': 'fast', 'Antebrazo': 'fast',
    'Pectorales': 'medium', 'Dorsales': 'medium', 'Hombros': 'medium', 'Trapecio': 'medium',
    'Cuádriceps': 'slow', 'Glúteos': 'slow', 'Aductores': 'medium',
    'Isquiosurales': 'heavy', 'Erectores Espinales': 'heavy', 'Core': 'medium', 'Cuello': 'medium'
};

const ATHLETE_CAPACITY_FLOORS: Record<string, number> = {
    'enthusiast': 500,
    'hybrid': 650,
    'calisthenics': 600,
    'bodybuilder': 1000,
    'powerbuilder': 1100,
    'powerlifter': 1200,
    'weightlifter': 1000,
    'parapowerlifter': 1100
};

export const RECOVERY_FACTORS = {
    OPTIMAL_SLEEP: { label: 'Sueño Óptimo', factor: 1.2, icon: '😴', desc: 'Recuperación acelerada por descanso profundo.' },
    CALORIC_SURPLUS: { label: 'Superávit', factor: 1.1, icon: '🍲', desc: 'Exceso de energía para la síntesis de tejido.' },
    CALORIC_DEFICIT: { label: 'Déficit', factor: 0.85, icon: '📉', desc: 'Recursos limitados para la reparación muscular.' },
    STRESS_HIGH: { label: 'Estrés Alto', factor: 0.8, icon: '🤯', desc: 'El cortisol elevado inhibe la recuperación.' }
};

// --- HELPER FUNCTIONS (SANITIZATION & UTILS) ---

const clamp = (val: number, min: number, max: number) => Math.min(max, Math.max(min, val));

// Asegura que la función exponencial no devuelva NaN o Infinity
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

const shouldCountFatigueSet = (set: any): boolean => {
    return !!set && !(set.type === 'warmup' || set.isIneffective) && isSetEffective(set);
};

const getAdaptiveRecoveryHours = (muscleName: string, adaptiveCache?: any): number | null => {
    const cache = adaptiveCache || getCachedAdaptiveData();
    const normalizedTarget = normalizeLookupKey(muscleName);
    const canonicalTarget = normalizeLookupKey(normalizeCanonicalMuscle(muscleName).muscle);

    for (const [key, value] of Object.entries(cache.personalizedRecoveryHours || {})) {
        const normalizedKey = normalizeLookupKey(key);
        if (normalizedKey === normalizedTarget || normalizedKey === canonicalTarget) {
            return typeof value === 'number' && value > 0 ? value : null;
        }
    }

    return null;
};

const getAdaptiveSystemBiasCorrection = (system: 'cns' | 'muscular' | 'spinal', adaptiveCache?: any): number => {
    const cache = adaptiveCache || getCachedAdaptiveData();
    const value = cache.selfImprovement?.suggested_adjustments?.[`${system}_bias_correction`];
    return typeof value === 'number' ? value : 0;
};


// Mapa de normalización para agrupar variantes anatómicas
const MUSCLE_CATEGORY_MAP: Record<string, string[]> = {
    'Pectorales': ['pectoral', 'pecho'],
    'Dorsales': ['dorsal', 'dorsales', 'redondo mayor', 'espalda alta', 'lats', 'romboides'],
    'Deltoides': ['deltoides', 'hombro', 'delts'],
    'Deltoides Anterior': ['deltoides anterior', 'deltoide anterior', 'anterior'],
    'Deltoides Lateral': ['deltoides lateral', 'deltoide lateral', 'lateral', 'medio'],
    'Deltoides Posterior': ['deltoides posterior', 'deltoide posterior', 'posterior'],
    'Bíceps': ['bíceps', 'biceps', 'braquial', 'braquiorradial', 'antebrazo'],
    'Tríceps': ['tríceps', 'triceps'],
    'Cuádriceps': ['cuádriceps', 'cuadriceps', 'recto femoral', 'vasto', 'quads'],
    'Isquiosurales': ['isquiosurales', 'isquiotibiales', 'bíceps femoral', 'semitendinoso', 'semimembranoso', 'femoral', 'hamstrings'],
    'Glúteos': ['glúteo', 'gluteo', 'glutes'],
    'Pantorrillas': ['pantorrilla', 'gemelo', 'gastrocnemio', 'sóleo', 'soleo', 'calves'],
    'Abdomen': ['abdomen', 'abdominal', 'oblicuo', 'recto abdominal', 'core', 'transverso', 'abs', 'flexores de cadera', 'iliopsoas'],
    'Trapecio': ['trapecio'],
    'Erectores Espinales': ['erector', 'espinal', 'lumbar', 'espalda baja', 'cuadrado lumbar', 'lower back'],
    'Cuello': ['cuello', 'cervical', 'neck']
};

const isMuscleInGroup = (specificMuscle: string, targetCategory: string, specificEmphasis?: string): boolean => {
    if (!specificMuscle || !targetCategory) return false;
    if (matchesMuscleTarget(specificMuscle, targetCategory, specificEmphasis)) return true;

    const specificDisplay = getMuscleDisplayId(specificMuscle, specificEmphasis);
    return normalizeLookupKey(specificDisplay) === normalizeLookupKey(targetCategory);
};

// --- 1. CAPACIDAD DE TRABAJO DINÁMICA CON SUELO DE SEGURIDAD ---
// Calcula cuánto volumen tolera el usuario. Si el historial está vacío o es errático,
// usa el tipo de atleta para asegurar un suelo mínimo lógico.
const calculateUserWorkCapacity = (history: WorkoutLog[], muscleName: string, exerciseList: ExerciseMuscleInfo[], settings: Settings, idx?: ExerciseIndex): number => {
    const now = Date.now();
    const fourWeeksAgo = now - (28 * 24 * 60 * 60 * 1000);

    const recentLogs = history.filter(log => new Date(log.date).getTime() > fourWeeksAgo);

    const baseFloor = ATHLETE_CAPACITY_FLOORS[settings.athleteType || 'enthusiast'] || 500;

    if (recentLogs.length === 0) return baseFloor;

    const index = idx || buildExerciseIndex(exerciseList);
    let totalStress = 0;

    recentLogs.forEach(log => {
        log.completedExercises.forEach(ex => {
            const info = findExerciseWithFallback(index, ex.exerciseDbId, ex.exerciseName);
            if (!info) return;

            // Verificar si el músculo participó en el ejercicio
            const involvement = info.involvedMuscles.find(m => isMuscleInGroup(m.muscle, muscleName, m.emphasis));
            if (involvement) {
                // Sumar estrés ponderado por participación
                const stress = ex.sets.reduce((acc, s) => {
                    if (!shouldCountFatigueSet(s)) return acc;
                    return acc + calculateSetStress(s, info, (ex as any).restTime || 90);
                }, 0);
                totalStress += (stress * (involvement.activation ?? 1));
            }
        });
    });

    // Promedio semanal de estrés para este músculo
    const weeklyAvg = totalStress / 4;

    // La capacidad es el volumen semanal promedio + buffer de supercompensación (1.8x)
    // Pero NUNCA menor que el suelo base del atleta.
    const calculatedCapacity = weeklyAvg * 1.8;

    return clamp(Math.max(calculatedCapacity, baseFloor), 500, 3500);
};

// --- CORE: CÁLCULO DE BATERÍA MUSCULAR ---

export const calculateMuscleBattery = (
    muscleName: string,
    history: WorkoutLog[],
    exerciseList: ExerciseMuscleInfo[],
    sleepLogs: SleepLog[],
    settings: Settings,
    muscleHierarchy: MuscleHierarchy,
    postSessionFeedback: PostSessionFeedback[] = [],
    waterLogs: WaterLog[] = [],
    dailyWellbeingLogs: DailyWellbeingLog[] = [],
    nutritionLogs: NutritionLog[] = [],
    adaptiveCache?: any
) => {
    const todayStr = getLocalDateString();
    const wellbeingLog = dailyWellbeingLogs.find(l => l.date === todayStr) || dailyWellbeingLogs[dailyWellbeingLogs.length - 1];

    // Calculamos el multiplicador de nutrición como antes
    let nutritionMultiplier = 1.0;
    if (settings.algorithmSettings?.augeEnableNutritionTracking !== false) {
        const nutritionResult = computeNutritionRecoveryMultiplier({
            nutritionLogs,
            settings,
            stressLevel: wellbeingLog?.stressLevel ?? 3,
            hoursWindow: 48,
        });
        nutritionMultiplier = nutritionResult.recoveryTimeMultiplier;
    } else if (settings.calorieGoalObjective === 'deficit') {
        nutritionMultiplier = 1.25;
    }

    const exIndex = buildExerciseIndex(exerciseList);
    const effectiveAdaptiveCache = adaptiveCache || getCachedAdaptiveData();

    // Inyectamos el suelo de capacidad en los settings para que el shared-domain lo use
    const enrichedSettings = {
        ...settings,
        athleteScore: {
            ...settings.athleteScore,
            baseCapacity: calculateUserWorkCapacity(history, muscleName, exerciseList, settings, exIndex)
        }
    };

    const sharedWellness = wellbeingLog ? {
        date: wellbeingLog.date,
        sleepHours: (Array.isArray(sleepLogs) && sleepLogs.length > 0) ? (sleepLogs[0].duration || 7.5) : 7.5,
        sleepQuality: 3, // No mapeado directamente
        nutritionStatus: settings.calorieGoalObjective as any || 'maintenance',
        stressLevel: wellbeingLog.stressLevel,
        hydration: 'good' as any,
        doms: wellbeingLog.doms
    } : undefined;

    const result = calculateMuscleRecoveryShared(
        muscleName,
        history,
        effectiveAdaptiveCache,
        sharedWellness,
        enrichedSettings,
        (ex, mName) => {
            const info = findExerciseWithFallback(exIndex, ex.exerciseDbId, ex.exerciseName);
            if (!info) return null;
            const involvement = info.involvedMuscles.find(m => isMuscleInGroup(m.muscle, mName, m.emphasis));
            if (!involvement) return null;
            return { role: involvement.role, activation: involvement.activation ?? 1.0 };
        },
        () => nutritionMultiplier
    );

    // Mapeo de vuelta al formato PWA legacy para no romper la UI
    return {
        recoveryScore: result.recoveryScore,
        effectiveSets: result.effectiveSets,
        hoursSinceLastSession: result.hoursSinceLastSession,
        estimatedHoursToRecovery: result.estimatedHoursToRecovery,
        status: result.status === 'fresh' ? 'optimal' : result.status // legacy enum: optimal | recovering | exhausted
    };
};


// --- CORE: CÁLCULO DE SNC (ROBUSTO Y HUMANO) ---

export const calculateSystemicFatigue = (history: WorkoutLog[], sleepLogs: SleepLog[], dailyWellbeingLogs: DailyWellbeingLog[], exerciseList: ExerciseMuscleInfo[], settings?: Settings) => {
    const now = Date.now();
    const exIndex = buildExerciseIndex(exerciseList);
    const tanks = calculatePersonalizedBatteryTanks(settings || {});
    const last7DaysLogs = history.filter(l => (now - new Date(l.date).getTime()) < 7 * 24 * 3600 * 1000);

    let cnsLoad = 0;

    last7DaysLogs.forEach(log => {
        const daysAgo = (now - new Date(log.date).getTime()) / (24 * 3600 * 1000);
        const recencyMultiplier = Math.max(0.1, Math.exp(-0.4 * daysAgo));

        let sessionCNS = 0;
        const muscleVolumeMap: Record<string, number> = {};
        log.completedExercises.forEach(ex => {
            const info = findExerciseWithFallback(exIndex, ex.exerciseDbId, ex.exerciseName);
            const primaryEntry = info?.involvedMuscles?.find(m => m.role === 'primary');
            const primaryMuscle = primaryEntry
                ? getMuscleDisplayId(primaryEntry.muscle, primaryEntry.emphasis)
                : 'Core';
            let accumulatedSets = muscleVolumeMap[primaryMuscle] || 0;

            ex.sets.forEach(s => {
                if (!shouldCountFatigueSet(s)) return;
                accumulatedSets += 1;
                const drain = calculateSetBatteryDrain(s, info, tanks, accumulatedSets, (ex as any).restTime || 90);
                // SISTEMA AUGE: Convertimos la escala CNC (1-5) en un ratio multiplicador (0.2 a 1.0)
                sessionCNS += drain.cnsDrainPct;

                // Cargas supra-máximas (>90% 1RM) aumentan la fuga del CNC drásticamente
            });
            muscleVolumeMap[primaryMuscle] = accumulatedSets;
        });

        // Duración: >75 min empieza a liberar cortisol exponencialmente
        if ((log.duration || 0) > 75 * 60) sessionCNS *= 1.08;
        if ((log.duration || 0) > 90 * 60) sessionCNS *= 1.15;

        cnsLoad += sessionCNS * recencyMultiplier;
    });

    const normalizedGymFatigue = clamp(cnsLoad, 0, 100);

    let sleepPenalty = 0;

    // Sistema AUGE: Solo evaluamos penalización de SNC por sueño si el usuario lo tiene activado
    if (settings?.algorithmSettings?.augeEnableSleepTracking !== false) {
        const sleepArray = Array.isArray(sleepLogs) ? sleepLogs : [];
        const sortedSleep = [...sleepArray].sort((a, b) => new Date(b.endTime).getTime() - new Date(a.endTime).getTime()).slice(0, 3);

        // Si no hay datos, asumimos 7.5h neutrales
        const wSleep = sortedSleep.length > 0
            ? ((sortedSleep[0]?.duration || 7.5) * 0.5) + ((sortedSleep[1]?.duration || 7.5) * 0.3) + ((sortedSleep[2]?.duration || 7.5) * 0.2)
            : 7.5;

        // Curva sigmoide invertida aproximada para penalización y compensación (PCE)
        if (wSleep < 4.5) sleepPenalty = 40;       // Crítico
        else if (wSleep < 5.5) sleepPenalty = 25;  // Malo
        else if (wSleep < 6.5) sleepPenalty = 15;  // Subóptimo
        else if (wSleep >= 8.5) sleepPenalty = -15; // PCE: Sleep Banking limpia fatiga del SNC
        else if (wSleep > 7.5) sleepPenalty = -5;  // Bonus estándar
    }

    // 3. Carga Cognitiva y Estrés Vital (Background Load) - BLINDADO
    const todayStr = getLocalDateString();
    const wellbeingArray = Array.isArray(dailyWellbeingLogs) ? dailyWellbeingLogs : [];
    const wellbeing = wellbeingArray.find(l => l.date === todayStr) || wellbeingArray[wellbeingArray.length - 1];

    let lifeStressPenalty = 0;
    if (wellbeing) {
        // Estrés reportado (1-5) actúa como multiplicador de drenaje
        if (wellbeing.stressLevel >= 4) lifeStressPenalty += 15;
        else if (wellbeing.stressLevel === 3) lifeStressPenalty += 5;

        // Carga laboral/estudio
        if (wellbeing.workIntensity === 'high' || wellbeing.studyIntensity === 'high') {
            lifeStressPenalty += 10;
        }
    }

    // 4. Fusión Final
    const totalFatigue = normalizedGymFatigue + sleepPenalty + lifeStressPenalty - getAdaptiveSystemBiasCorrection('cns');

    // Invertir para mostrar "Batería" (Energía restante)
    const cnsBattery = clamp(100 - totalFatigue, 0, 100);

    // Helper interno para agrupar factores de vida en la UI
    const calcLifeScore = (sPen: number, lPen: number) => Math.max(0, sPen + lPen);

    return {
        total: Math.round(cnsBattery),
        gym: Math.round(normalizedGymFatigue),
        life: Math.round(calcLifeScore(sleepPenalty, lifeStressPenalty))
    };
};

// --- SISTEMA AUGE: SEMÁFORO DIARIO DE RECUPERACIÓN (READINESS) ---

export const calculateDailyReadiness = (
    sleepLogs: SleepLog[],
    dailyWellbeingLogs: DailyWellbeingLog[],
    settings: Settings,
    cnsBattery: number, // Batería sistémica/SNC actual (0-100)
    adaptiveCache?: AugeAdaptiveCache
) => {
    const todayStr = getLocalDateString();
    const wellbeingArray = Array.isArray(dailyWellbeingLogs) ? dailyWellbeingLogs : [];
    const recentWellbeing = wellbeingArray.find(l => l.date === todayStr) || wellbeingArray[wellbeingArray.length - 1];

    // Evaluador de Sueño
    const sleepArray = Array.isArray(sleepLogs) ? sleepLogs : [];
    const lastSleep = [...sleepArray].sort((a, b) => new Date(b.endTime).getTime() - new Date(a.endTime).getTime())[0];
    const sleepHours = lastSleep?.duration || 7.5;

    const effectiveAdaptiveCache = adaptiveCache ?? getCachedAdaptiveData();

    const config: AugeEngineConfig = {
        settings,
        adaptiveCache: effectiveAdaptiveCache,
        wellbeing: recentWellbeing ? {
            date: recentWellbeing.date,
            sleepHours,
            sleepQuality: 3,
            nutritionStatus: settings.calorieGoalObjective as any || 'maintenance',
            stressLevel: recentWellbeing.stressLevel,
            hydration: 'good' as any,
            doms: recentWellbeing.doms
        } : undefined,
        history: [], // No requerido para el cálculo actual de readiness en shared
        cnsBattery
    };

    return computeAugeReadinessShared(config);
};

// --- UTILIDADES ---

export const learnRecoveryRate = (currentMultiplier: number, calculatedScore: number, manualFeel: number): number => {
    // Ajuste conservador basado en la discrepancia
    const diff = manualFeel - calculatedScore;
    const adjustment = diff * 0.005;
    return clamp(currentMultiplier + adjustment, 0.5, 2.0);
};

export const checkPendingSurveys = (history: WorkoutLog[], feedbacks: PostSessionFeedback[]): PendingQuestionnaire[] => {
    const now = Date.now();
    return history.filter(log => {
        const timeSince = now - new Date(log.date).getTime();
        // Solo mostrar encuestas para sesiones entre 2h y 48h de antigüedad que no tengan feedback
        return timeSince > 2 * 3600 * 1000 && timeSince < 48 * 3600 * 1000 && !feedbacks.some(f => f.logId === log.id);
    }).slice(0, 1).map(log => ({
        logId: log.id,
        sessionName: log.sessionName,
        muscleGroups: [], // Se llenará en el componente UI
        scheduledTime: new Date(log.date).getTime() + 24 * 3600 * 1000
    }));
};

export const calculateSleepRecommendations = (
    settings: Settings,
    todayContext: DailyWellbeingLog | undefined,
    todayWorkout: WorkoutLog | undefined,
    exerciseList: ExerciseMuscleInfo[] = []
) => {
    const baseTarget = settings.sleepTargetHours || 8;
    let extraTime = 0;
    const reasons: string[] = [];

    // Fatiga aguda del entreno de hoy
    if (todayWorkout) {
        const stress = todayWorkout.sessionStressScore || calculateSetStress({} as any, undefined, 0); // Placeholder safe call
        // Estimación aproximada si no hay score pre-calculado
        const volume = todayWorkout.completedExercises.length * 3;

        if (volume > 15 || stress > 200) {
            extraTime += 0.75;
            reasons.push("Alta Carga Neural");
        } else if (volume > 10) {
            extraTime += 0.5;
            reasons.push("Volumen Moderado");
        }
    }

    if (todayContext) {
        if (todayContext.workIntensity === 'high' || todayContext.studyIntensity === 'high') {
            extraTime += 0.5;
            reasons.push("Desgaste Cognitivo");
        }
        if (todayContext.stressLevel > 3) {
            extraTime += 0.5;
            reasons.push("Estrés Elevado");
        }
    }

    const recommendedDuration = Math.min(10.5, baseTarget + extraTime);
    if (reasons.length === 0) reasons.push("Mantenimiento");

    // Cálculo de horarios
    const now = new Date();
    const tomorrow = new Date(now);
    tomorrow.setDate(now.getDate() + 1);
    const tomorrowDayIndex = tomorrow.getDay();

    const isTomorrowWorkDay = settings.workDays?.includes(tomorrowDayIndex) ?? false;
    const wakeTime = isTomorrowWorkDay ? (settings.wakeTimeWork || '07:00') : (settings.wakeTimeOff || '09:00');

    const [wakeHours, wakeMinutes] = wakeTime.split(':').map(Number);
    const wakeDate = new Date();
    wakeDate.setHours(wakeHours, wakeMinutes, 0, 0);
    if (wakeDate.getTime() < now.getTime()) {
        wakeDate.setDate(wakeDate.getDate() + 1);
    }

    const bedTimeDate = new Date(wakeDate.getTime() - (recommendedDuration * 60 * 60 * 1000));
    const bedTime = bedTimeDate.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: false });

    return {
        duration: parseFloat(recommendedDuration.toFixed(1)),
        wakeTime,
        bedTime,
        reasons,
        isWorkDayTomorrow: isTomorrowWorkDay
    };
};

export interface BatteryAuditLog { icon: string; label: string; val: number | string; type: 'workout' | 'penalty' | 'bonus' | 'info'; }

/**
 * --- KPKN ENGINE 3.0: BATERÍAS GLOBALES Y AUDITORÍA ---
 * Calcula el estado de los 3 sistemas cruzando entrenamientos con hábitos de vida.
 */
export const calculateGlobalBatteries = (
    history: WorkoutLog[],
    sleepLogs: SleepLog[],
    dailyWellbeingLogs: DailyWellbeingLog[],
    nutritionLogs: NutritionLog[],
    settings: Settings,
    exerciseList: ExerciseMuscleInfo[],
    adaptiveCache?: AugeAdaptiveCache
) => {
    const now = Date.now();
    const tanks = calculatePersonalizedBatteryTanks(settings);

    // Vida Media de la Fatiga (Horas para recuperar el 50%)
    let cnsHalfLife = 28;    // SNC recupera moderadamente rápido si hay sueño
    let muscHalfLife = 40;   // Músculo requiere síntesis proteica
    let spinalHalfLife = 72; // Tejido conectivo y fascia tardan muchísimo

    const auditLogs = { cns: [] as BatteryAuditLog[], muscular: [] as BatteryAuditLog[], spinal: [] as BatteryAuditLog[] };
    const todayStr = getLocalDateString();
    const wellbeingArray = Array.isArray(dailyWellbeingLogs) ? dailyWellbeingLogs : [];
    const recentWellbeing = wellbeingArray.find(l => l.date === todayStr) || wellbeingArray[wellbeingArray.length - 1];
    const effectiveAdaptiveCache = adaptiveCache ?? getCachedAdaptiveData();
    const adaptiveRecoverySamples = Object.values(effectiveAdaptiveCache.personalizedRecoveryHours || {}).filter((value): value is number => typeof value === 'number' && value > 0);

    if (adaptiveRecoverySamples.length > 0) {
        const adaptiveMeanRecovery = adaptiveRecoverySamples.reduce((acc, value) => acc + value, 0) / adaptiveRecoverySamples.length;
        muscHalfLife = clamp((muscHalfLife * 0.6) + (adaptiveMeanRecovery * 0.4), 24, 96);
        auditLogs.muscular.push({ icon: 'ðŸ§ª', label: 'Curva Bayesiana Personal', val: Math.round(muscHalfLife), type: 'info' });
    }

    // 1. MODULADOR DE NUTRICIÓN (Afecta la recarga Muscular)
    if (settings?.algorithmSettings?.augeEnableNutritionTracking !== false) {
        const nutritionResult = computeNutritionRecoveryMultiplier({
            nutritionLogs,
            settings,
            stressLevel: recentWellbeing?.stressLevel ?? 3,
            hoursWindow: 48,
        });
        const nutMult = nutritionResult.recoveryTimeMultiplier;
        muscHalfLife *= nutMult;
        if (nutritionResult.status === 'deficit') {
            auditLogs.muscular.push({ icon: '📉', label: 'Déficit Calórico (Recarga Lenta)', val: '', type: 'info' });
        } else if (nutritionResult.status === 'surplus') {
            auditLogs.muscular.push({ icon: '🚀', label: 'Superávit Calórico (Recarga Acelerada)', val: '', type: 'info' });
        } else if (nutritionResult.factors.some((f: string) => f.includes('Proteína'))) {
            auditLogs.muscular.push({ icon: '🥩', label: 'Proteína subóptima', val: '', type: 'info' });
        }
    } else if (settings?.calorieGoalObjective === 'deficit') {
        // Régimen especial: usuario reportó déficit pero no conecta nutrición
        muscHalfLife *= 1.25;
        auditLogs.muscular.push({ icon: '📉', label: 'Régimen Déficit (Recarga más lenta)', val: '', type: 'info' });
    }

    // 2. MODULADOR DE SUEÑO Y ESTRÉS (Afecta Capacidad SNC)
    let cnsPenalty = 0;
    if (recentWellbeing && recentWellbeing.stressLevel >= 4) {
        cnsPenalty += 12;
        auditLogs.cns.push({ icon: '🤯', label: 'Alto Estrés Reportado', val: -12, type: 'penalty' });
    }

    if (settings?.algorithmSettings?.augeEnableSleepTracking !== false) {
        const sleepArray = Array.isArray(sleepLogs) ? sleepLogs : [];
        const sortedSleep = [...sleepArray].sort((a, b) => new Date(b.endTime).getTime() - new Date(a.endTime).getTime()).slice(0, 3);
        let wSleep = 7.5;
        if (sortedSleep.length > 0) wSleep = ((sortedSleep[0]?.duration || 7.5) * 0.5) + ((sortedSleep[1]?.duration || 7.5) * 0.3) + ((sortedSleep[2]?.duration || 7.5) * 0.2);

        if (wSleep < 6) {
            cnsPenalty += 18;
            auditLogs.cns.push({ icon: '🥱', label: 'Deuda de Sueño Crítica', val: -18, type: 'penalty' });
        } else if (wSleep >= 8.5) {
            cnsPenalty -= 10;
            auditLogs.cns.push({ icon: '🛌', label: 'Sueño Profundo (Bonus)', val: '+10', type: 'bonus' });
        }
    }

    // 3. ACUMULACIÓN DE ENTRENAMIENTO (Drenaje Directo)
    const exIndex = buildExerciseIndex(exerciseList);
    let cnsFatigue = 0, muscFatigue = 0, spinalFatigue = 0;
    const sevenDaysAgo = now - (7 * 24 * 3600 * 1000);
    const recentLogs = history.filter(l => new Date(l.date).getTime() > sevenDaysAgo);

    recentLogs.forEach(log => {
        let logCns = 0, logMusc = 0, logSpinal = 0;
        const hoursAgo = (now - new Date(log.date).getTime()) / 3600000;
        const muscleVolumeMap: Record<string, number> = {};

        log.completedExercises.forEach(ex => {
            const info = findExerciseWithFallback(exIndex, ex.exerciseDbId, ex.exerciseName);
            const primaryEntry = info?.involvedMuscles?.find(m => m.role === 'primary');
            const primaryMuscle = primaryEntry
                ? getMuscleDisplayId(primaryEntry.muscle, primaryEntry.emphasis)
                : 'Core';
            let accumulatedSets = muscleVolumeMap[primaryMuscle] || 0;
            ex.sets.forEach((s) => {
                if (!shouldCountFatigueSet(s)) return;
                accumulatedSets += 1;
                const drain = calculateSetBatteryDrain(s, info, tanks, accumulatedSets, (ex as any).restTime || 90);
                logCns += drain.cnsDrainPct;
                logMusc += drain.muscularDrainPct;
                logSpinal += drain.spinalDrainPct;
            });
            muscleVolumeMap[primaryMuscle] = accumulatedSets;
        });

        // Aplicamos la tasa de recarga (Decaimiento Exponencial) basado en cuánto tiempo pasó
        const cnsDecay = logCns * Math.exp(-(Math.LN2 / cnsHalfLife) * hoursAgo);
        const muscDecay = logMusc * Math.exp(-(Math.LN2 / muscHalfLife) * hoursAgo);
        const spinalDecay = logSpinal * Math.exp(-(Math.LN2 / spinalHalfLife) * hoursAgo);

        cnsFatigue += cnsDecay;
        muscFatigue += muscDecay;
        spinalFatigue += spinalDecay;

        // Registrar en Auditoría solo si la sesión aún tiene un impacto > 3%
        if (hoursAgo < 72) {
            if (cnsDecay > 3) auditLogs.cns.push({ icon: '🏋️', label: `Sesión: ${log.sessionName}`, val: -Math.round(cnsDecay), type: 'workout' });
            if (muscDecay > 3) auditLogs.muscular.push({ icon: '🏋️', label: `Sesión: ${log.sessionName}`, val: -Math.round(muscDecay), type: 'workout' });
            if (spinalDecay > 3) auditLogs.spinal.push({ icon: '🏋️', label: `Sesión: ${log.sessionName}`, val: -Math.round(spinalDecay), type: 'workout' });
        }
    });

    // 4. AUTO-CALIBRACIÓN DEL USUARIO (Manual Override)
    const calib = settings.batteryCalibration || { cnsDelta: 0, muscularDelta: 0, spinalDelta: 0, lastCalibrated: '' };
    let cnsDelta = calib.cnsDelta || 0;
    let muscDelta = calib.muscularDelta || 0;
    let spinalDelta = calib.spinalDelta || 0;

    // Los overrides desaparecen solos después de 3 días (72h)
    if (calib.lastCalibrated) {
        const calibHours = (now - new Date(calib.lastCalibrated).getTime()) / 3600000;
        const calibDecay = Math.max(0, 1 - (calibHours / 72));
        cnsDelta *= calibDecay;
        muscDelta *= calibDecay;
        spinalDelta *= calibDecay;
    }

    cnsDelta += getAdaptiveSystemBiasCorrection('cns');
    muscDelta += getAdaptiveSystemBiasCorrection('muscular');
    spinalDelta += getAdaptiveSystemBiasCorrection('spinal');

    if (Math.abs(cnsDelta) > 1) auditLogs.cns.push({ icon: '🧠', label: 'Auto-Calibración (Tú)', val: Math.round(cnsDelta) > 0 ? `+${Math.round(cnsDelta)}` : Math.round(cnsDelta), type: cnsDelta > 0 ? 'bonus' : 'penalty' });
    if (Math.abs(muscDelta) > 1) auditLogs.muscular.push({ icon: '🧠', label: 'Auto-Calibración (Tú)', val: Math.round(muscDelta) > 0 ? `+${Math.round(muscDelta)}` : Math.round(muscDelta), type: muscDelta > 0 ? 'bonus' : 'penalty' });
    if (Math.abs(spinalDelta) > 1) auditLogs.spinal.push({ icon: '🧠', label: 'Auto-Calibración (Tú)', val: Math.round(spinalDelta) > 0 ? `+${Math.round(spinalDelta)}` : Math.round(spinalDelta), type: spinalDelta > 0 ? 'bonus' : 'penalty' });

    // 5. CÁLCULO FINAL (100% - Daño Acumulado)
    const cnsFatiguePct = transformFatigueToPercent(cnsFatigue, 65);
    const muscFatiguePct = transformFatigueToPercent(muscFatigue, 80);
    const spinalFatiguePct = transformFatigueToPercent(spinalFatigue, 110);

    const finalCns = clamp(100 - cnsFatiguePct - cnsPenalty + cnsDelta, 0, 100);
    // La batería muscular global en el motor v3.1 ahora es un "tanque sistémico" pero 
    // en la UI se promediará con los músculos para coherencia.
    const finalMusc = clamp(100 - muscFatiguePct + muscDelta, 0, 100);
    const finalSpinal = clamp(100 - spinalFatiguePct + spinalDelta, 0, 100);

    // 6. VEREDICTO EXPERTO AUGE
    let verdict = "Todos tus sistemas están óptimos. Es un buen día para buscar récords personales (PRs).";
    if (finalCns < 30) verdict = "Tu sistema nervioso está frito. NO intentes 1RMs hoy. Prioriza máquinas y reduce el RPE.";
    else if (finalSpinal < 35) verdict = "Tu columna y tejido axial están sobrecargados. Evita el Peso Muerto o Sentadillas Libres hoy.";
    else if (finalMusc < 30) verdict = "Alta fatiga muscular residual. Asegúrate de comer suficiente proteína y haz rutinas de bombeo.";
    else if (cnsPenalty > 10) verdict = "Tu falta de sueño/estrés está limitando tu potencial hoy. Autorregula tu peso y no vayas al fallo.";

    // 7. BATERÍAS ARTICULARES (tendones y articulaciones)
    const articularBatteries = calculateArticularBatteries(history, exerciseList, [], settings);
    const articularScores = Object.values(articularBatteries).map(state => state.recoveryScore);

    const articularAverage = articularScores.length
        ? Math.round(articularScores.reduce((sum, score) => sum + score, 0) / articularScores.length)
        : 100;
    const muscleArticularBlend = Math.round((Math.round(finalMusc) + articularAverage) / 2);

    return {
        cns: Math.round(finalCns),
        muscular: Math.round(finalMusc),
        spinal: Math.round(finalSpinal),
        auditLogs,
        verdict,
        articularBatteries,
        articularAverage,
        muscleArticularBlend,
    };
};

// --- BATERÍA POR MÚSCULO (para acordeón Home) ---
/** Lista de músculos para el acordeón. Sin porciones excepto deltoides (anterior, lateral, posterior). */
export const ACCORDION_MUSCLES: { id: string; label: string; isDeltoidPortion?: boolean }[] = [
    { id: 'Bíceps', label: 'Bíceps' },
    { id: 'Tríceps', label: 'Tríceps' },
    { id: 'Pectorales', label: 'Pectorales' },
    { id: 'Dorsales', label: 'Dorsales' },
    { id: 'Deltoides Anterior', label: 'Deltoides Anterior', isDeltoidPortion: true },
    { id: 'Deltoides Lateral', label: 'Deltoides Lateral', isDeltoidPortion: true },
    { id: 'Deltoides Posterior', label: 'Deltoides Posterior', isDeltoidPortion: true },
    { id: 'Cuádriceps', label: 'Cuádriceps' },
    { id: 'Glúteos', label: 'Glúteos' },
    { id: 'Isquiosurales', label: 'Isquiosurales' },
    { id: 'Pantorrillas', label: 'Pantorrillas' },
    { id: 'Abdomen', label: 'Abdomen' },
    { id: 'Antebrazo', label: 'Antebrazo' },
    { id: 'Trapecio', label: 'Trapecio' },
    { id: 'Core', label: 'Core' },
    { id: 'Erectores Espinales', label: 'Erectores Espinales' },
    { id: 'Aductores', label: 'Aductores' },
    { id: 'Cuello', label: 'Cuello' },
];

export const getPerMuscleBatteries = (
    history: WorkoutLog[],
    exerciseList: ExerciseMuscleInfo[],
    sleepLogs: SleepLog[],
    settings: Settings,
    muscleHierarchy: MuscleHierarchy,
    postSessionFeedback: PostSessionFeedback[] = [],
    waterLogs: WaterLog[] = [],
    dailyWellbeingLogs: DailyWellbeingLog[] = [],
    nutritionLogs: NutritionLog[] = []
): Record<string, number> => {
    const result: Record<string, number> = {};
    for (const m of ACCORDION_MUSCLES) {
        const battery = calculateMuscleBattery(
            m.id,
            history,
            exerciseList,
            sleepLogs,
            settings,
            muscleHierarchy,
            postSessionFeedback,
            waterLogs,
            dailyWellbeingLogs,
            nutritionLogs
        );
        result[m.id] = battery.recoveryScore;
    }
    return result;
};

// --- DRENAJE ESPINAL POR EJERCICIO (para COLUMNA tabs) ---
export interface SpinalDrainEntry {
    exerciseName: string;
    totalSpinalDrain: number;
}

/** Intensidad pre-calibración → RPE para simular drenaje */
export const PRECALIBRATION_INTENSITY_TO_RPE: Record<string, number> = {
    LIGERO: 6,
    MEDIO: 7,
    ALTO: 8,
    MUY_ALTO: 9,
    EXTREMO: 10,
};

export interface PrecalibrationExerciseInput {
    exerciseDbId?: string;
    exerciseName: string;
    intensity: 'LIGERO' | 'MEDIO' | 'ALTO' | 'MUY_ALTO' | 'EXTREMO';
}

export interface PrecalibrationReadinessInput {
    sleepQuality: number;
    stressLevel: number;
    doms: number;
    motivation: number;
}

/** Aplica pre-calibración: simula drenaje de ejercicios recientes + readiness y devuelve deltas para batteryCalibration */
export const applyPrecalibrationToBattery = (
    exercises: PrecalibrationExerciseInput[],
    readiness: PrecalibrationReadinessInput,
    exerciseList: ExerciseMuscleInfo[],
    settings: Settings
): { cnsDelta: number; muscularDelta: number; spinalDelta: number } => {
    const tanks = calculatePersonalizedBatteryTanks(settings);
    const exIndex = buildExerciseIndex(exerciseList);

    let totalCns = 0;
    let totalMusc = 0;
    let totalSpinal = 0;

    exercises.forEach((ex, exIdx) => {
        const info = findExerciseWithFallback(exIndex, ex.exerciseDbId, ex.exerciseName);
        const rpe = PRECALIBRATION_INTENSITY_TO_RPE[ex.intensity] ?? 8;
        const reps = (info?.type === 'Básico' ? 5 : 8);
        const virtualSet = { completedRPE: rpe, completedReps: reps, targetReps: reps, weight: 0 };
        const drain = calculateSetBatteryDrain(virtualSet, info, tanks, exIdx, 90);
        totalCns += drain.cnsDrainPct;
        totalMusc += drain.muscularDrainPct;
        totalSpinal += drain.spinalDrainPct;
    });

    const readinessAvg = (readiness.sleepQuality + (6 - readiness.stressLevel) + (6 - readiness.doms) + readiness.motivation) / 4;
    const readinessFactor = readinessAvg / 5;
    const bonusFromReadiness = (readinessFactor - 0.6) * 15;
    const cnsBonus = Math.round(bonusFromReadiness);
    const muscBonus = Math.round(bonusFromReadiness * 0.5);

    return {
        cnsDelta: Math.round(-totalCns + cnsBonus),
        muscularDelta: Math.round(-totalMusc + muscBonus),
        spinalDelta: Math.round(-totalSpinal),
    };
};

/** Pre-calibración solo con readiness (usuario no entrenó antes) */
export const applyPrecalibrationReadinessOnly = (
    readiness: PrecalibrationReadinessInput
): { cnsDelta: number; muscularDelta: number; spinalDelta: number } => {
    const readinessAvg = (readiness.sleepQuality + (6 - readiness.stressLevel) + (6 - readiness.doms) + readiness.motivation) / 4;
    const factor = (readinessAvg - 3) * 5;
    return {
        cnsDelta: Math.round(factor),
        muscularDelta: Math.round(factor * 0.5),
        spinalDelta: 0,
    };
};

export const getSpinalDrainByExercise = (
    history: WorkoutLog[],
    exerciseList: ExerciseMuscleInfo[],
    days: number | null,
    settings?: Settings
): SpinalDrainEntry[] => {
    const now = Date.now();
    const tanks = calculatePersonalizedBatteryTanks(settings || {});
    const exIndex = buildExerciseIndex(exerciseList);

    const cutoff = days === null
        ? 0
        : now - (days * 24 * 60 * 60 * 1000);

    const relevantLogs = history.filter(log =>
        days === null || new Date(log.date).getTime() > cutoff
    );

    const byExercise = new Map<string, number>();

    relevantLogs.forEach(log => {
        const hoursAgo = (now - new Date(log.date).getTime()) / 3600000;
        const spinalHalfLife = 72;
        const decay = Math.exp(-(Math.LN2 / spinalHalfLife) * hoursAgo);

        log.completedExercises.forEach(ex => {
            const info = findExerciseWithFallback(exIndex, ex.exerciseDbId, ex.exerciseName);
            const name = ex.exerciseName || info?.name || 'Ejercicio';
            let total = 0;
            ex.sets.forEach((s, idx) => {
                const drain = calculateSetBatteryDrain(s, info, tanks, idx, 90);
                total += drain.spinalDrainPct * decay;
            });
            if (total > 0) {
                const prev = byExercise.get(name) || 0;
                byExercise.set(name, prev + total);
            }
        });
    });

    return Array.from(byExercise.entries())
        .map(([exerciseName, totalSpinalDrain]) => ({ exerciseName, totalSpinalDrain }))
        .sort((a, b) => b.totalSpinalDrain - a.totalSpinalDrain);
};
