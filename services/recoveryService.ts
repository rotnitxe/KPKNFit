
import { WorkoutLog, ExerciseMuscleInfo, MuscleHierarchy, SleepLog, PostSessionFeedback, PendingQuestionnaire, DailyWellbeingLog, Settings, WaterLog } from '../types';
import { calculateSetStress } from './fatigueService';

// --- CONSTANTES & CONFIGURACIÓN ---

const RECOVERY_PROFILES: Record<string, number> = {
    'fast': 24,   // Recuperación rápida (Bíceps, Hombro Lateral, Gemelo)
    'medium': 48, // Estándar (Pecho, Espalda Alta)
    'slow': 72,   // Grandes grupos/daño alto (Cuádriceps, Glúteos)
    'heavy': 96   // Sistémico/Axial (Erectores, Isquiosurales)
};

const MUSCLE_PROFILE_MAP: Record<string, string> = {
    'bíceps': 'fast', 'tríceps': 'fast', 'deltoides': 'fast', 'pantorrillas': 'fast', 'abdomen': 'fast', 'antebrazo': 'fast',
    'pectorales': 'medium', 'dorsales': 'medium', 'hombros': 'medium', 'trapecio': 'medium',
    'cuádriceps': 'slow', 'glúteos': 'slow', 'aductores': 'medium',
    'isquiosurales': 'heavy', 'espalda baja': 'heavy', 'erectores espinales': 'heavy', 'core': 'medium'
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

// Mapa de normalización para agrupar variantes anatómicas
const MUSCLE_CATEGORY_MAP: Record<string, string[]> = {
    'pectorales': ['pectoral', 'pecho'],
    'dorsales': ['dorsal', 'redondo mayor', 'espalda alta', 'lats'],
    'deltoides': ['deltoides', 'hombro', 'delts'],
    'bíceps': ['bíceps', 'biceps', 'braquial', 'braquiorradial', 'antebrazo'],
    'tríceps': ['tríceps', 'triceps'],
    'cuádriceps': ['cuádriceps', 'cuadriceps', 'recto femoral', 'vasto', 'quads'],
    'isquiosurales': ['isquiosurales', 'isquiotibiales', 'bíceps femoral', 'semitendinoso', 'semimembranoso', 'femoral', 'hamstrings'],
    'glúteos': ['glúteo', 'gluteo', 'glutes'],
    'pantorrillas': ['pantorrilla', 'gemelo', 'gastrocnemio', 'sóleo', 'soleo', 'calves'],
    'abdomen': ['abdomen', 'abdominal', 'oblicuo', 'recto abdominal', 'core', 'transverso', 'abs'],
    'espalda baja': ['erector', 'espinal', 'lumbar', 'espalda baja', 'cuadrado lumbar', 'lower back']
};

const isMuscleInGroup = (specificMuscle: string, targetCategory: string): boolean => {
    const specific = specificMuscle.toLowerCase();
    const target = targetCategory.toLowerCase();
    if (specific === target) return true;
    const keywords = MUSCLE_CATEGORY_MAP[target];
    if (keywords) {
        return keywords.some(k => specific.includes(k));
    }
    return specific.includes(target) || target.includes(specific);
};

// --- 1. CAPACIDAD DE TRABAJO DINÁMICA CON SUELO DE SEGURIDAD ---
// Calcula cuánto volumen tolera el usuario. Si el historial está vacío o es errático,
// usa el tipo de atleta para asegurar un suelo mínimo lógico.
const calculateUserWorkCapacity = (history: WorkoutLog[], muscleName: string, exerciseList: ExerciseMuscleInfo[], settings: Settings): number => {
    const now = Date.now();
    const fourWeeksAgo = now - (28 * 24 * 60 * 60 * 1000);
    
    // Filtrar logs de las últimas 4 semanas
    const recentLogs = history.filter(log => new Date(log.date).getTime() > fourWeeksAgo);
    
    // Obtener suelo base según tipo de atleta (Safety Floor)
    const baseFloor = ATHLETE_CAPACITY_FLOORS[settings.athleteType || 'enthusiast'] || 500;

    if (recentLogs.length === 0) return baseFloor;

    let totalStress = 0;
    
    recentLogs.forEach(log => {
        log.completedExercises.forEach(ex => {
            const info = exerciseList.find(e => e.id === ex.exerciseDbId || e.name === ex.exerciseName);
            if (!info) return;

            // Verificar si el músculo participó en el ejercicio
            const involvement = info.involvedMuscles.find(m => isMuscleInGroup(m.muscle, muscleName));
            if (involvement) {
                // Sumar estrés ponderado por participación
                const stress = ex.sets.reduce((acc, s) => acc + calculateSetStress(s, info, 90), 0);
                totalStress += (stress * involvement.activation);
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
    dailyWellbeingLogs: DailyWellbeingLog[] = []
) => {
    const now = Date.now();
    
    // 1. Obtener Capacidad Dinámica (Tu tanque de gasolina personal)
    const muscleCapacity = calculateUserWorkCapacity(history, muscleName, exerciseList, settings);

    // 2. Determinar Perfil de Recuperación (Half-Life diferenciado)
    let profileKey = 'medium';
    for (const [key, val] of Object.entries(MUSCLE_PROFILE_MAP)) {
        if (isMuscleInGroup(key, muscleName)) {
            profileKey = val;
            break;
        }
    }
    const baseRecoveryTime = RECOVERY_PROFILES[profileKey];

    // 3. SISTEMA AUGE: Ajuste por Factores de Estilo de Vida (Wellness)
    // Nota: En AUGE, el multiplicador AUMENTA el tiempo de recuperación (penalización)
    let recoveryTimeMultiplier = 1.0;
    
    // Extraer el log de bienestar más reciente
    const todayStr = new Date().toISOString().split('T')[0];
    const recentWellbeing = dailyWellbeingLogs.find(l => l.date === todayStr) || dailyWellbeingLogs[dailyWellbeingLogs.length - 1];

    // --- INTEGRACIÓN NUTRICIÓN DINÁMICA AUGE ---
    // Solo aplicamos el modificador si el usuario tiene el tracking activado
    if (settings.algorithmSettings?.augeEnableNutritionTracking !== false) {
        const fortyEightHoursAgo = now - (48 * 3600000);
        const recentNutrition = nutritionLogs.filter(n => new Date(n.date).getTime() > fortyEightHoursAgo);
        
        let actualNutritionStatus = settings.calorieGoalObjective; // Fallback
        if (recentNutrition.length > 0) {
            const avgCalories = recentNutrition.reduce((acc, curr) => acc + (curr.calories || 0), 0) / 2;
            if (settings.dailyCalorieGoal) {
                if (avgCalories < settings.dailyCalorieGoal * 0.9) actualNutritionStatus = 'deficit';
                else if (avgCalories > settings.dailyCalorieGoal * 1.1) actualNutritionStatus = 'surplus';
                else actualNutritionStatus = 'maintenance';
            }
        }

        if (actualNutritionStatus === 'deficit') recoveryTimeMultiplier *= 1.35; // Penalización AUGE
        else if (actualNutritionStatus === 'surplus') recoveryTimeMultiplier *= 0.85; 
    }
    
    // Factor 2: Estrés (Alto estrés = +40% de tiempo según AUGE)
    if (recentWellbeing && recentWellbeing.stressLevel >= 4) {
        recoveryTimeMultiplier *= 1.4;
    }

    // Factor 3: Sueño (El "Borrador de Fatiga" Neural)
    // Solo aplicamos el modificador de sueño si el usuario lo permite
    let wSleep = 7.5; // Base neutral por defecto
    
    if (settings.algorithmSettings?.augeEnableSleepTracking !== false) {
        const safeSleepLogs = sleepLogs || [];
        const sleepArray = Array.isArray(sleepLogs) ? sleepLogs : [];
        const sortedSleep = [...sleepArray].sort((a, b) => new Date(b.endTime).getTime() - new Date(a.endTime).getTime()).slice(0, 3);
        
        if (sortedSleep.length > 0) {
            wSleep = ((sortedSleep[0]?.duration || 7) * 0.5) + ((sortedSleep[1]?.duration || 7) * 0.3) + ((sortedSleep[2]?.duration || 7) * 0.2);
        }

        if (wSleep < 6) recoveryTimeMultiplier *= 1.5;
        else if (wSleep < 7) recoveryTimeMultiplier *= 1.2;
        else if (wSleep >= 8.5) recoveryTimeMultiplier *= 0.8; // PCE: Sleep Banking (Recompensa masiva)
        else if (wSleep >= 7.5) recoveryTimeMultiplier *= 0.9; // Sueño óptimo estándar
    }

    // --- 3.1 FACTORES BIOLÓGICOS (EDAD Y GÉNERO) ---
    const age = settings.userVitals?.age || 25;
    if (age > 35) {
        const agePenalty = (age - 35) * 0.01; // +1% de tiempo por cada año sobre 35
        recoveryTimeMultiplier *= (1 + agePenalty);
    }
    const gender = settings.userVitals?.gender || 'male';
    if (gender === 'female' || gender === 'transfemale') {
        recoveryTimeMultiplier *= 0.85; // Sistema neuromuscular femenino recupera ~15% más rápido
    }

    // TimeToRecover = Base * Multiplier (Si el factor es 1.5 y 1.4, se dispara exponencialmente)
    const realRecoveryTime = baseRecoveryTime * Math.max(0.5, recoveryTimeMultiplier);

    // 4. Cálculo de Fatiga Acumulada (Cascada y Sinergistas)
    let accumulatedFatigue = 0;
    let lastSessionDate = 0;
    let effectiveSetsCount = 0;

    const relevantHistory = history.filter(log => (now - new Date(log.date).getTime()) < 10 * 24 * 3600 * 1000);

    relevantHistory.forEach(log => {
        const logTime = new Date(log.date).getTime();
        const hoursSince = Math.max(0, (now - logTime) / 3600000); // Sanitización
        let sessionMuscleStress = 0;

        log.completedExercises.forEach(ex => {
            const info = exerciseList.find(e => e.id === ex.exerciseDbId || e.name === ex.exerciseName);
            if (!info) return;

            // --- LÓGICA DE CASCADA (SINERGISTAS) ---
            const muscleInvolvement = info.involvedMuscles.find(m => isMuscleInGroup(m.muscle, muscleName));

            if (muscleInvolvement) {
                const rawStress = ex.sets.reduce((acc, s) => acc + calculateSetStress(s, info, 90), 0);
                
                let roleMultiplier = 0.0;
                switch (muscleInvolvement.role) {
                    case 'primary': roleMultiplier = 1.0; break;
                    case 'secondary': roleMultiplier = 0.5; break;
                    case 'stabilizer': roleMultiplier = 0.15; break;
                    default: roleMultiplier = 0.1;
                }

                const activationFactor = muscleInvolvement.activation || 1.0; 
                sessionMuscleStress += (rawStress * roleMultiplier * activationFactor);

                if (hoursSince <= 168 && (muscleInvolvement.role === 'primary' || (muscleInvolvement.role === 'secondary' && activationFactor > 0.6))) {
                    effectiveSetsCount += ex.sets.length;
                }
            }
        });

        if (sessionMuscleStress > 0) {
            // Decaimiento Exponencial
            const k = 2.9957 / Math.max(1, realRecoveryTime); 
            const remainingStress = sessionMuscleStress * safeExp(-k * hoursSince);
            
            accumulatedFatigue += remainingStress;
            if (logTime > lastSessionDate) lastSessionDate = logTime;
        }
    });

    // Calcular batería base matemática
    let batteryPercentage = 100 - (accumulatedFatigue / muscleCapacity * 100);
    batteryPercentage = clamp(batteryPercentage, 0, 100);

    // --- 5. CARGA DE FONDO (BACKGROUND LOAD) ---
    // Si la vida diaria es exigente, el "100%" nunca es realmente 100%.
    // Usamos el último log de bienestar o los settings generales.
    const wellbeingArray = Array.isArray(dailyWellbeingLogs) ? dailyWellbeingLogs : [];
    const contextLog = wellbeingArray.find(l => l.date === todayStr) || wellbeingArray[wellbeingArray.length - 1];
    
    let backgroundCap = 100;
    const workIntensity = contextLog?.workIntensity || settings.userVitals?.workIntensity || 'light';
    const stressLevel = contextLog?.stressLevel || 3;

    if (workIntensity === 'high') backgroundCap -= 10; // Trabajo físico reduce tope
    else if (workIntensity === 'moderate') backgroundCap -= 5;
    
    if (stressLevel >= 4) backgroundCap -= 10; // Alto cortisol reduce tope

    // Aplicar el techo de carga de fondo
    batteryPercentage = Math.min(batteryPercentage, backgroundCap);


    // --- 6. OVERRIDE POR DOMS Y MOLESTIAS (READINESS + POST-ENTRENO) ---
    let domsCap = 100;

    // A. Check del Readiness (Wellbeing) - Impacto Inmediato
    if (recentWellbeing && recentWellbeing.doms > 1) {
        // Mapeo simple: si hay DOMS general alto, afecta a todos los músculos
        const wellbeingDoms = recentWellbeing.doms;
        if (wellbeingDoms === 5) domsCap = Math.min(domsCap, 15);
        else if (wellbeingDoms === 4) domsCap = Math.min(domsCap, 40);
        else if (wellbeingDoms === 3) domsCap = Math.min(domsCap, 70);
    }

    // B. Check de Molestias registradas en logs (Discomforts Array)
    const recentLogsWithDiscomfort = history.filter(l => 
        (now - new Date(l.date).getTime()) < 48 * 3600000 && l.discomforts && l.discomforts.length > 0
    );

    recentLogsWithDiscomfort.forEach(log => {
        const isRelated = log.discomforts?.some(d => isMuscleInGroup(d, muscleName));
        if (isRelated) domsCap = Math.min(domsCap, 50); // Cap de seguridad por molestia activa
    });

    // C. Feedback Detallado (Existente)
    const recentFeedback = postSessionFeedback
        .filter(f => (now - new Date(f.date).getTime()) < 72 * 3600 * 1000)
        .sort((a,b) => new Date(b.date).getTime() - new Date(a.date).getTime())[0];

    if (recentFeedback?.feedback) {
        const feedbackEntry = Object.entries(recentFeedback.feedback).find(([k]) => isMuscleInGroup(k, muscleName));
        if (feedbackEntry) {
            const [_, data] = feedbackEntry;
            const hoursSinceFeedback = (now - new Date(recentFeedback.date).getTime()) / 3600000;
            
            // "Hard Caps" basados en dolor reportado (1-5)
            // Se asume que el dolor disminuye con el tiempo, liberando el cap gradualmente.
            let domsCap = 100;
            
            if (data.doms === 5) { // Extremo
                // Bloqueado en 10% primeras 24h, sube lento
                domsCap = 10 + (hoursSinceFeedback * 1.5); 
            } else if (data.doms === 4) { // Fuerte
                // Bloqueado en 40%
                domsCap = 40 + (hoursSinceFeedback * 2.0);
            } else if (data.doms === 3) { // Moderado
                domsCap = 70 + (hoursSinceFeedback * 2.5);
            }

            // La batería no puede superar el límite impuesto por el dolor real
            batteryPercentage = Math.min(batteryPercentage, domsCap);
        }
    }

    // Clamp final por seguridad
    batteryPercentage = clamp(batteryPercentage, 0, 100);

    // Definir estado cualitativo
    let status: 'optimal' | 'recovering' | 'exhausted' = 'optimal';
    if (batteryPercentage < 40) status = 'exhausted';
    else if (batteryPercentage < 85) status = 'recovering';

    // Estimación de tiempo para volver al 90% (o al backgroundCap si es menor)
    let hoursToRecovery = 0;
    const targetPercentage = Math.min(90, backgroundCap);
    
    if (batteryPercentage < targetPercentage && accumulatedFatigue > 0) {
        const k = 2.9957 / realRecoveryTime;
        // targetStress es la fatiga restante permitida para estar al targetPercentage
        // battery = 100 - (fatigue / capacity * 100)
        // fatigue = (100 - battery) * capacity / 100
        const targetFatigue = (100 - targetPercentage) * muscleCapacity / 100;
        
        if (accumulatedFatigue > targetFatigue) {
            // t = -ln(target/current) / k
            hoursToRecovery = -Math.log(targetFatigue / accumulatedFatigue) / k;
        }
    }

    return {
        recoveryScore: Math.round(batteryPercentage),
        effectiveSets: effectiveSetsCount,
        hoursSinceLastSession: lastSessionDate > 0 ? Math.round((now - lastSessionDate) / 3600000) : -1,
        estimatedHoursToRecovery: Math.round(Math.max(0, hoursToRecovery)),
        status
    };
};

// --- CORE: CÁLCULO DE SNC (ROBUSTO Y HUMANO) ---

export const calculateSystemicFatigue = (history: WorkoutLog[], sleepLogs: SleepLog[], dailyWellbeingLogs: DailyWellbeingLog[], settings?: Settings) => {
    const now = Date.now();
    const last7DaysLogs = history.filter(l => (now - new Date(l.date).getTime()) < 7 * 24 * 3600 * 1000);
    
    // 1. Carga de Entrenamiento (SNC Focus & Axial)
    let cnsLoad = 0;
    
    last7DaysLogs.forEach(log => {
        const daysAgo = (now - new Date(log.date).getTime()) / (24 * 3600 * 1000);
        // Factor de recencia suavizado (Exponencial inversa)
        const recencyMultiplier = Math.max(0.1, Math.exp(-0.4 * daysAgo)); 

        let sessionCNS = 0;
        log.completedExercises.forEach(ex => {
            const info = exerciseList.find(e => e.id === ex.exerciseDbId || e.name === ex.exerciseName);
            const { cnc } = getDynamicAugeMetrics(info, ex.exerciseName);
            
            ex.sets.forEach(s => {
                const stress = calculateSetStress(s, info, 90);
                // SISTEMA AUGE: Convertimos la escala CNC (1-5) en un ratio multiplicador (0.2 a 1.0)
                const sncRatio = cnc / 5.0; 
                
                // Cargas supra-máximas (>90% 1RM) aumentan la fuga del CNC drásticamente
                let loadMultiplier = 1.0;
                if (info?.calculated1RM && s.weight) {
                    if ((s.weight / info.calculated1RM) >= 0.90) loadMultiplier = 1.3;
                }
                
                sessionCNS += (stress * sncRatio * loadMultiplier);
            });
        });

        // Duración: >75 min empieza a liberar cortisol exponencialmente
        if ((log.duration || 0) > 75 * 60) sessionCNS *= 1.15;
        if ((log.duration || 0) > 90 * 60) sessionCNS *= 1.25;

        cnsLoad += sessionCNS * recencyMultiplier;
    });

    // Normalizar carga (0-100). Referencia: 4000 pts = Fatiga total teórica semanal para un avanzado
    const normalizedGymFatigue = clamp((cnsLoad / 4000) * 100, 0, 100);

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
    const todayStr = new Date().toISOString().split('T')[0];
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
    const totalFatigue = normalizedGymFatigue + sleepPenalty + lifeStressPenalty;
    
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
    cnsBattery: number // Batería sistémica/SNC actual (0-100)
) => {
    let recoveryTimeMultiplier = 1.0;
    const diagnostics: string[] = [];
    
    const todayStr = new Date().toISOString().split('T')[0];
    const wellbeingArray = Array.isArray(dailyWellbeingLogs) ? dailyWellbeingLogs : [];
    const recentWellbeing = wellbeingArray.find(l => l.date === todayStr) || wellbeingArray[wellbeingArray.length - 1];

    // Evaluador de Sueño
    const sleepArray = Array.isArray(sleepLogs) ? sleepLogs : [];
    const lastSleep = [...sleepArray].sort((a, b) => new Date(b.endTime).getTime() - new Date(a.endTime).getTime())[0];
    const sleepHours = lastSleep?.duration || 7.5;

    if (sleepHours < 6) {
        recoveryTimeMultiplier *= 1.5;
        diagnostics.push("Falta de sueño detectada (<6h). Tu recarga está severamente frenada hoy.");
    }

    // Evaluador de Estrés
    if (recentWellbeing && recentWellbeing.stressLevel >= 4) {
        recoveryTimeMultiplier *= 1.4;
        diagnostics.push("Tus niveles altos de estrés están liberando cortisol, bloqueando la recuperación del sistema nervioso.");
    }

    // Evaluador de Nutrición
    if (settings.calorieGoalObjective === 'deficit') {
        recoveryTimeMultiplier *= 1.3;
        diagnostics.push("Al estar en déficit calórico, tienes recursos limitados para reparar tejido dañado.");
    }

    // Determinación del Semáforo (Traffic Light)
    let status: 'green' | 'yellow' | 'red' = 'green';
    let recommendation = "Estás en condiciones óptimas. Tienes luz verde para buscar récords personales o tirar pesado.";

    // Lógica de castigo combinado: Batería baja + Mal estilo de vida = Riesgo inminente
    if (cnsBattery < 40 || recoveryTimeMultiplier >= 1.8) {
        status = 'red';
        recommendation = "Tu sistema nervioso no está listo. Tu falta de sueño/estrés está frenando tu recarga. Considera descanso total o una sesión muy ligera de movilidad.";
    } else if (cnsBattery < 70 || recoveryTimeMultiplier >= 1.3) {
        status = 'yellow';
        recommendation = "Tienes fatiga residual o factores externos en contra. Cambia el trabajo pesado por técnica, o reduce tu volumen planificado al 50%.";
    }

    if (diagnostics.length === 0) {
        diagnostics.push("Tus hábitos de las últimas 24 hrs fueron excelentes. La síntesis de recuperación está a tope.");
    }

    return {
        status, // 'green', 'yellow', 'red'
        stressMultiplier: parseFloat(recoveryTimeMultiplier.toFixed(2)),
        cnsBattery,
        diagnostics,
        recommendation
    };
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
