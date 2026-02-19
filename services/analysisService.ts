
// services/analysisService.ts
import { Program, DetailedMuscleVolumeAnalysis, ExerciseMuscleInfo, Settings, Session, MuscleHierarchy, Exercise, WorkoutLog, ExerciseSet, CompletedSet, ProgramWeek, NutritionLog, BodyProgressLog } from '../types';
import { getWeekId, calculateFFMI, calculateBrzycki1RM } from '../utils/calculations';
import { isSetEffective, calculateCompletedSessionStress } from './fatigueService';
import { MUSCLE_ROLE_MULTIPLIERS } from './volumeCalculator';

const createChildToParentMap = (hierarchy: MuscleHierarchy): Map<string, string> => {
    const map = new Map<string, string>();
    if (!hierarchy || !hierarchy.bodyPartHierarchy) return map;

    for (const bodyPart in hierarchy.bodyPartHierarchy) {
        const subgroups = hierarchy.bodyPartHierarchy[bodyPart as keyof typeof hierarchy.bodyPartHierarchy];
        
        for (const item of subgroups) {
            if (typeof item === 'object' && item !== null) {
                const subgroupName = Object.keys(item)[0];
                const children = Object.values(item)[0];
                children.forEach(child => map.set(child, subgroupName));
            }
        }
    }
    return map;
};

export const calculateAverageVolumeForWeeks = (
    weeks: ProgramWeek[],
    exerciseList: ExerciseMuscleInfo[],
    muscleHierarchy: MuscleHierarchy,
    mode: 'simple' | 'complex' = 'complex'
): DetailedMuscleVolumeAnalysis[] => {
    if (weeks.length === 0) return [];

    const allMuscleTotals: Record<string, { totalVol: number, direct: Map<string, number>, indirect: Map<string, {sets: number, act: number}>, freqDirect: number, freqIndirect: number }> = {};
    const childToParentMap = createChildToParentMap(muscleHierarchy);
    
    // Función segura para obtener la familia del músculo
    const getDisplayGroup = (muscle: string) => childToParentMap.get(muscle) || muscle;

    weeks.forEach(week => {
        week.sessions.forEach(session => {
            const exercises = (session.parts && session.parts.length > 0) ? session.parts.flatMap(p => p.exercises) : session.exercises;
            
            // Mapa de impacto de Frecuencia para ESTA sesión
            const sessionFreqImpact = new Map<string, { direct: number, indirect: number }>();

            exercises.forEach(exercise => {
                // FALLBACK DE SEGURIDAD: Intentar buscar por ID, luego por nombre.
                const exerciseData = exerciseList.find(e => e.id === exercise.exerciseDbId) 
                                  || exerciseList.find(e => e.name.toLowerCase() === exercise.name.toLowerCase());
                
                // Si el ejercicio fue borrado de la DB, lo ignoramos para no crashear
                if (!exerciseData || !exerciseData.involvedMuscles) return;

                // Contar series válidas
                const effectiveSets = exercise.sets.filter(isSetEffective).length;
                if (effectiveSets === 0) return;

                // --- NUEVA LÓGICA DE FRECUENCIA DIRECTA/INDIRECTA ---
                const isDirectEffective = (s: any) => {
                    if (!isSetEffective(s)) return false;
                    const rpe = s.rpe || s.completedRPE || s.targetRPE;
                    const rir = s.rir ?? s.completedRIR ?? s.targetRIR;
                    
                    if (s.isFailure || s.intensityMode === 'failure' || s.isAmrap || s.performanceMode === 'failed') return true;
                    if (rpe !== undefined && rpe >= 6) return true;
                    if (rir !== undefined && rir <= 4) return true;
                    
                    // Si no hay datos de intensidad registrados, pero es una serie válida de trabajo, asumimos esfuerzo suficiente
                    if (rpe === undefined && rir === undefined) return true;
                    return false;
                };
                const hasDirectEffectiveSets = exercise.sets.some(isDirectEffective);

                // BLINDAJE ANTI-DUPLICACIÓN: Guardamos el rol más alto por grupo muscular
                const highestRolePerGroup = new Map<string, { maxMultiplier: number, bestRole: string }>();

                exerciseData.involvedMuscles.forEach(m => {
                    const group = getDisplayGroup(m.muscle);
                    
                    // 1. Lógica de Frecuencia Intra-Sesión
                    const currentFreq = sessionFreqImpact.get(group) || { direct: 0, indirect: 0 };
                    if ((m.role === 'primary' || m.role === 'secondary') && hasDirectEffectiveSets) {
                        const impactVal = m.role === 'primary' ? 1.0 : 0.5;
                        currentFreq.direct = Math.max(currentFreq.direct, impactVal);
                    } else if (m.role === 'stabilizer' || m.role === 'neutralizer') {
                        currentFreq.indirect = 1.0;
                    }
                    sessionFreqImpact.set(group, currentFreq);

                    // 2. Lógica de Volumen Original
                    if (mode === 'simple' && m.role !== 'primary') return;
                    
                    const multiplier = mode === 'simple' ? 1.0 : (MUSCLE_ROLE_MULTIPLIERS[m.role] || 0.5);
                    
                    const existing = highestRolePerGroup.get(group);
                    if (!existing || existing.maxMultiplier < multiplier) {
                        highestRolePerGroup.set(group, {
                            maxMultiplier: multiplier,
                            bestRole: m.role
                        });
                    }
                });

                // APLICAR EL VOLUMEN MÁXIMO DEL EJERCICIO
                highestRolePerGroup.forEach((data, groupName) => {
                    if (!allMuscleTotals[groupName]) {
                        allMuscleTotals[groupName] = { totalVol: 0, direct: new Map(), indirect: new Map(), freqDirect: 0, freqIndirect: 0 };
                    }

                    allMuscleTotals[groupName].totalVol += effectiveSets * data.maxMultiplier;

                    if (data.bestRole === 'primary') {
                        allMuscleTotals[groupName].direct.set(exercise.name, (allMuscleTotals[groupName].direct.get(exercise.name) || 0) + effectiveSets);
                    } else if (mode === 'complex') {
                        const existing = allMuscleTotals[groupName].indirect.get(exercise.name);
                        const percentageEquivalent = data.maxMultiplier * 100; 
                        
                        if (!existing || existing.act < percentageEquivalent) {
                            allMuscleTotals[groupName].indirect.set(exercise.name, { sets: (existing?.sets || 0) + effectiveSets, act: percentageEquivalent });
                        }
                    }
                });
            });

            // Consolidar la Frecuencia Diaria (Días/Veces por Semana) en el Gran Total Semanal
            sessionFreqImpact.forEach((impact, groupName) => {
                if (!allMuscleTotals[groupName]) {
                    allMuscleTotals[groupName] = { totalVol: 0, direct: new Map(), indirect: new Map(), freqDirect: 0, freqIndirect: 0 };
                }
                
                // Sumamos la frecuencia directa (Días que se entrenó Primario/Secundario en la semana)
                allMuscleTotals[groupName].freqDirect += impact.direct;
                
                // ANTI-DUPLICACIÓN DE DÍAS: Solo sumamos este día como "Frecuencia Indirecta" 
                // si el músculo NO recibió estímulo directo en esta misma sesión.
                if (impact.direct === 0) {
                    allMuscleTotals[groupName].freqIndirect += impact.indirect;
                }
            });
        });
    });

    return Object.entries(allMuscleTotals).map(([muscleGroup, data]) => ({
        muscleGroup,
        displayVolume: Math.round((data.totalVol / weeks.length) * 10) / 10,
        totalSets: Math.round(data.totalVol / weeks.length),
        // Exponemos la nueva Frecuencia Directa. Además se inyecta la Indirecta en el objeto para uso analítico del sistema.
        frequency: Math.round((data.freqDirect / weeks.length) * 10) / 10,
        indirectFrequency: Math.round((data.freqIndirect / weeks.length) * 10) / 10,
        avgRestDays: null,
        directExercises: Array.from(data.direct.entries()).map(([name, sets]) => ({ name, sets: Math.round((sets / weeks.length) * 10) / 10 })),
        indirectExercises: Array.from(data.indirect.entries()).map(([name, info]) => ({ name, sets: Math.round((info.sets / weeks.length) * 10) / 10, activationPercentage: info.act })),
        avgIFI: null,
        recoveryStatus: 'N/A' as const,
    })).filter(v => v.displayVolume > 0 || (v as any).indirectFrequency > 0).sort((a, b) => b.displayVolume - a.displayVolume);
};

export const calculateSessionVolume = (
    session: Session, 
    exerciseList: ExerciseMuscleInfo[], 
    muscleHierarchy: MuscleHierarchy,
    mode: 'simple' | 'complex' = 'complex'
): DetailedMuscleVolumeAnalysis[] => {
    // Reuse the logic by treating the session as a 1-week program
    const tempWeek: ProgramWeek = { id: 'temp', name: 'temp', sessions: [session] };
    return calculateAverageVolumeForWeeks([tempWeek], exerciseList, muscleHierarchy, mode);
};

export const findPlannedWeek = (programs: Program[], history: WorkoutLog[], settings: Settings): ProgramWeek | null => {
    const today = new Date();
    const todayIndex = today.getDay(); // 0 = Sunday

    let plannedWeek: ProgramWeek | null = null;
    
    for (const program of programs) {
        for (const macro of program.macrocycles) {
            for (const block of (macro.blocks || [])) {
                for (const meso of block.mesocycles) {
                    for (const week of meso.weeks) {
                        if (week.sessions.some(s => s.dayOfWeek === todayIndex)) {
                            plannedWeek = week;
                            break;
                        }
                    }
                    if (plannedWeek) break;
                }
                if (plannedWeek) break;
            }
            if (plannedWeek) break;
        }
    }
    return plannedWeek;
};

export const calculateDetailedVolumeForAllPrograms = async (
  programs: Program[],
  settings: Settings,
  exerciseList: ExerciseMuscleInfo[],
  muscleHierarchy: MuscleHierarchy
): Promise<DetailedMuscleVolumeAnalysis[]> => {
  const program = programs[0];
  if (!program) return [];
  const allWeeks = program.macrocycles.flatMap(m => (m.blocks || []).flatMap(b => b.mesocycles.flatMap(meso => meso.weeks)));
  return calculateAverageVolumeForWeeks(allWeeks, exerciseList, muscleHierarchy, 'complex');
};

export const calculateACWR = (
  history: WorkoutLog[],
  settings: Settings,
  exerciseList: ExerciseMuscleInfo[]
): { acwr: number; interpretation: string; color: string } => {
  if (history.length < 7) {
    return { acwr: 0, interpretation: 'Datos insuficientes', color: 'text-slate-400' };
  }

  const today = new Date();
  const stressByDay: { [date: string]: number } = {};

  history.forEach(log => {
    const dateStr = new Date(log.date).toISOString().split('T')[0];
    const stress = log.sessionStressScore ?? calculateCompletedSessionStress(log.completedExercises, exerciseList);
    stressByDay[dateStr] = (stressByDay[dateStr] || 0) + stress;
  });

  const getDailyStress = (date: Date): number => {
    return stressByDay[date.toISOString().split('T')[0]] || 0;
  };

  let acuteLoad = 0;
  for (let i = 0; i < 7; i++) {
    const date = new Date(today);
    date.setDate(today.getDate() - i);
    acuteLoad += getDailyStress(date);
  }

  const weeklyLoads: number[] = [];
  for (let week = 0; week < 4; week++) {
    let weeklyLoad = 0;
    for (let day = 0; day < 7; day++) {
      const date = new Date(today);
      date.setDate(today.getDate() - (week * 7) - day);
      weeklyLoad += getDailyStress(date);
    }
    weeklyLoads.push(weeklyLoad);
  }
  
  const chronicLoad = weeklyLoads.reduce((sum, load) => sum + load, 0) / 4;
  if (chronicLoad < 10) return { acwr: 0, interpretation: 'Carga baja', color: 'text-sky-400' };

  const acwr = acuteLoad / chronicLoad;
  let interpretation = 'Zona Segura';
  let color = 'text-green-400';

  if (acwr < 0.8) { interpretation = 'Sub-entrenando'; color = 'text-sky-400'; }
  else if (acwr > 1.3 && acwr <= 1.5) { interpretation = 'Zona de Riesgo'; color = 'text-yellow-400'; }
  else if (acwr > 1.5) { interpretation = 'Alto Riesgo'; color = 'text-red-400'; }

  return { acwr: parseFloat(acwr.toFixed(2)), interpretation, color };
};

export const calculateWeeklyTonnageComparison = (
  history: WorkoutLog[],
  settings: Settings
): { current: number; previous: number } => {
  const today = new Date();
  const currentWeekId = getWeekId(today, settings.startWeekOn);
  const [yearStr, monthStr, dayStr] = currentWeekId.split('-');
  const currentWeekStartDate = new Date(Date.UTC(parseInt(yearStr), parseInt(monthStr) - 1, parseInt(dayStr)));
  const prevWeekDate = new Date(currentWeekStartDate);
  prevWeekDate.setUTCDate(prevWeekDate.getUTCDate() - 7);
  const previousWeekId = getWeekId(prevWeekDate, settings.startWeekOn);

  let current = 0;
  let previous = 0;

  history.forEach(log => {
    const logDate = new Date(log.date);
    const logWeekId = getWeekId(new Date(Date.UTC(logDate.getUTCFullYear(), logDate.getUTCMonth(), logDate.getUTCDate())), settings.startWeekOn);
    const volume = log.completedExercises.reduce((total, ex) =>
      total + ex.sets.reduce((setTotal, s) => {
        const weight = s.weight || 0;
        const reps = s.completedReps || 0;
        const duration = s.completedDuration || 0;
        if (duration > 0) return setTotal + (duration * (weight > 0 ? weight : 1));
        return setTotal + ((weight + (ex.useBodyweight ? (settings.userVitals.weight || 0) : 0)) * reps);
      }, 0), 0);
    
    if (logWeekId === currentWeekId) current += volume;
    else if (logWeekId === previousWeekId) previous += volume;
  });

  return { current: Math.round(current), previous: Math.round(previous) };
};

export const calculateWeeklyEffectiveVolumeByMuscleGroup = (
  programs: Program[],
  history: WorkoutLog[],
  settings: Settings,
  exerciseList: ExerciseMuscleInfo[],
  muscleHierarchy: MuscleHierarchy
): { muscleGroup: string; completed: number; planned: number }[] => {
    const today = new Date();
    const currentWeekId = getWeekId(today, settings.startWeekOn);
    const thisWeekLogs = history.filter(log => getWeekId(new Date(log.date), settings.startWeekOn) === currentWeekId);
    const plannedWeek = findPlannedWeek(programs, history, settings);

    const completedVolume: Record<string, number> = {};
    thisWeekLogs.forEach(log => {
        log.completedExercises.forEach(ex => {
            const exInfo = exerciseList.find(e => e.id === ex.exerciseDbId || e.name.toLowerCase() === ex.exerciseName.toLowerCase());
            if (exInfo) {
                const primaryMuscle = exInfo.involvedMuscles.find(m => m.role === 'primary')?.muscle;
                if (primaryMuscle) {
                    const effectiveSets = ex.sets.filter(isSetEffective).length;
                    completedVolume[primaryMuscle] = (completedVolume[primaryMuscle] || 0) + effectiveSets;
                }
            }
        });
    });

    const plannedVolume: Record<string, number> = {};
    if (plannedWeek) {
        const exercises = plannedWeek.sessions.flatMap(s => (s.parts && s.parts.length > 0) ? s.parts.flatMap(p => p.exercises) : s.exercises);
        exercises.forEach(ex => {
             const exInfo = exerciseList.find(e => e.id === ex.exerciseDbId || e.name.toLowerCase() === ex.name.toLowerCase());
             if (exInfo) {
                const primaryMuscle = exInfo.involvedMuscles.find(m => m.role === 'primary')?.muscle;
                if (primaryMuscle) {
                    const effectiveSets = ex.sets.filter(isSetEffective).length;
                    plannedVolume[primaryMuscle] = (plannedVolume[primaryMuscle] || 0) + effectiveSets;
                }
            }
        });
    }

    const allMuscles = new Set([...Object.keys(completedVolume), ...Object.keys(plannedVolume)]);
    return Array.from(allMuscles).map(muscle => ({
        muscleGroup: muscle,
        completed: completedVolume[muscle] || 0,
        planned: plannedVolume[muscle] || 0
    })).sort((a, b) => (b.completed + b.planned) - (a.completed + a.planned));
};

export const calculateFFMIProgress = (bodyProgress: BodyProgressLog[], settings: Settings): { current: number | null; initial: number | null; trend: 'up' | 'down' | 'stable' } => {
  if (!settings.userVitals.height) return { current: null, initial: null, trend: 'stable' };
  const validLogs = bodyProgress.filter(log => log.weight && log.bodyFatPercentage).sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime());
  if (validLogs.length === 0) {
      const { height, weight, bodyFatPercentage } = settings.userVitals;
      if (height && weight && bodyFatPercentage) {
          const ffmi = calculateFFMI(height, weight, bodyFatPercentage);
          return { current: ffmi ? parseFloat(ffmi.normalizedFfmi) : null, initial: null, trend: 'stable' };
      }
      return { current: null, initial: null, trend: 'stable' };
  }
  const initialFFMI = calculateFFMI(settings.userVitals.height, validLogs[0].weight!, validLogs[0].bodyFatPercentage!);
  const currentFFMI = calculateFFMI(settings.userVitals.height, validLogs[validLogs.length - 1].weight!, validLogs[validLogs.length - 1].bodyFatPercentage!);
  const initial = initialFFMI ? parseFloat(initialFFMI.normalizedFfmi) : null;
  const current = currentFFMI ? parseFloat(currentFFMI.normalizedFfmi) : null;
  let trend: 'up' | 'down' | 'stable' = 'stable';
  if (current && initial && current > initial) trend = 'up';
  if (current && initial && current < initial) trend = 'down';
  return { current, initial, trend };
};

export const calculateDailyNutritionSummary = (nutritionLogs: NutritionLog[], settings: Settings): { consumed: { calories: number; protein: number; carbs: number; fats: number }; goals: { calories: number; protein: number; carbs: number; fats: number }; } => {
  const todayStr = new Date().toISOString().split('T')[0];
  const todaysLogs = nutritionLogs.filter(log => log.date && log.date.startsWith(todayStr));
  const consumed = todaysLogs.reduce((acc, log) => {
      (log.foods || []).forEach(food => {
        acc.calories += food.calories || 0;
        acc.protein += food.protein || 0;
        acc.carbs += food.carbs || 0;
        acc.fats += food.fats || 0;
      });
      return acc;
  }, { calories: 0, protein: 0, carbs: 0, fats: 0 });
  return { consumed, goals: { calories: settings.dailyCalorieGoal || 0, protein: settings.dailyProteinGoal || 0, carbs: settings.dailyCarbGoal || 0, fats: settings.dailyFatGoal || 0 } };
};

export const calculateHistoricalFatigueData = (history: WorkoutLog[], settings: Settings, exerciseList: ExerciseMuscleInfo[]): any[] => {
  return [];
};

export const calculatePowerliftingMetrics = (history: WorkoutLog[], settings: Settings, exerciseList: ExerciseMuscleInfo[]): { weeklyVolume: { squat: { totalReps: number; effectiveSets: number }; bench: { totalReps: number; effectiveSets: number }; deadlift: { totalReps: number; effectiveSets: number }; }; balanceRatios: { ohpToBench: number; squatToDeadlift: number; }; } | null => {
  if (history.length === 0) return null;
  const today = new Date();
  const currentWeekId = getWeekId(today, settings.startWeekOn);
  const thisWeekLogs = history.filter(log => getWeekId(new Date(log.date), settings.startWeekOn) === currentWeekId);
  const weeklyVolume = { squat: { totalReps: 0, effectiveSets: 0 }, bench: { totalReps: 0, effectiveSets: 0 }, deadlift: { totalReps: 0, effectiveSets: 0 } };
  const max1RMs = { squat: 0, bench: 0, deadlift: 0, ohp: 0 };
  const liftKeywords = { squat: ['sentadilla', 'squat'], bench: ['press de banca', 'bench press'], deadlift: ['peso muerto', 'deadlift'], ohp: ['press militar', 'overhead press', 'ohp'] };

  history.forEach(log => {
    log.completedExercises.forEach(ex => {
      const name = ex.exerciseName.toLowerCase();
      ex.sets.forEach(set => {
        if (set.weight && set.completedReps) {
          const e1rm = calculateBrzycki1RM(set.weight, set.completedReps);
          if (liftKeywords.squat.some(kw => name.includes(kw))) max1RMs.squat = Math.max(max1RMs.squat, e1rm);
          if (liftKeywords.bench.some(kw => name.includes(kw))) max1RMs.bench = Math.max(max1RMs.bench, e1rm);
          if (liftKeywords.deadlift.some(kw => name.includes(kw))) max1RMs.deadlift = Math.max(max1RMs.deadlift, e1rm);
          if (liftKeywords.ohp.some(kw => name.includes(kw))) max1RMs.ohp = Math.max(max1RMs.ohp, e1rm);
        }
      });
    });
  });

  thisWeekLogs.forEach(log => {
    log.completedExercises.forEach(ex => {
      const name = ex.exerciseName.toLowerCase();
      const isSquat = liftKeywords.squat.some(kw => name.includes(kw));
      const isBench = liftKeywords.bench.some(kw => name.includes(kw));
      const isDeadlift = liftKeywords.deadlift.some(kw => name.includes(kw));
      if (isSquat || isBench || isDeadlift) {
        let liftType: 'squat' | 'bench' | 'deadlift' | null = null;
        if (isSquat) liftType = 'squat'; else if (isBench) liftType = 'bench'; else if (isDeadlift) liftType = 'deadlift';
        if (liftType) {
          ex.sets.forEach(set => {
            weeklyVolume[liftType].totalReps += set.completedReps || 0;
            if (isSetEffective(set)) weeklyVolume[liftType].effectiveSets++;
          });
        }
      }
    });
  });
  return { weeklyVolume, balanceRatios: { ohpToBench: max1RMs.bench > 0 ? max1RMs.ohp / max1RMs.bench : 0, squatToDeadlift: max1RMs.deadlift > 0 ? max1RMs.squat / max1RMs.deadlift : 0 } };
};

export const calculateEffectiveWeeklyVolume = (history: WorkoutLog[], exerciseList: ExerciseMuscleInfo[], muscleHierarchy: MuscleHierarchy, settings: Settings): DetailedMuscleVolumeAnalysis[] => {
  return [];
};

export const calculateProgramAdherence = (program: Program, history: WorkoutLog[], settings: Settings, exerciseList: ExerciseMuscleInfo[]): { adherencePercentage: number; completedSessions: number; plannedSessions: number; sessionsWithVolumeVariance: number; } | null => {
  const programHistory = history.filter(log => log.programId === program.id);
  const completedSessionIds = new Set(programHistory.map(log => log.sessionId));
  const allSessions = program.macrocycles.flatMap(m => (m.blocks || []).flatMap(b => b.mesocycles.flatMap(meso => meso.weeks.flatMap(w => w.sessions))));
  if (allSessions.length === 0) return { adherencePercentage: 100, completedSessions: 0, plannedSessions: 0, sessionsWithVolumeVariance: 0 };
  const allSessionIds = new Set(allSessions.map(s => s.id));
  const completedUniqueSessionsCount = Array.from(allSessionIds).filter(id => completedSessionIds.has(id)).length;
  const adherencePercentage = (completedUniqueSessionsCount / allSessionIds.size) * 100;
  return { adherencePercentage, completedSessions: completedUniqueSessionsCount, plannedSessions: allSessionIds.size, sessionsWithVolumeVariance: 0 };
};

export const calculateIFI = (exercise: Exercise, exerciseList: ExerciseMuscleInfo[]): number | null => {
    return null;
};
