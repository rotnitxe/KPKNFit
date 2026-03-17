// apps/mobile/src/services/analysisService.ts
import type { 
    WorkoutLog, 
    ExerciseCatalogEntry, 
    MuscleHierarchy,
    DetailedMuscleVolumeAnalysis,
    ProgramWeek
} from '../types/workout';
import { getMuscleDisplayId } from '../utils/canonicalMuscles';
import { buildExerciseIndex, findExerciseWithFallback } from '../utils/exerciseIndex';
import { isSetEffective } from './fatigueService';

const MUSCLE_ROLE_MULTIPLIERS: Record<string, number> = { 
    primary: 1.0, 
    secondary: 0.5, 
    stabilizer: 0, 
    neutralizer: 0 
};

const createChildToParentMap = (hierarchy: MuscleHierarchy): Map<string, string> => {
    const map = new Map<string, string>();
    if (!hierarchy || !hierarchy.bodyPartHierarchy) return map;

    for (const bodyPart in hierarchy.bodyPartHierarchy) {
        const subgroups = (hierarchy.bodyPartHierarchy as any)[bodyPart];
        
        for (const item of subgroups) {
            if (typeof item === 'object' && item !== null) {
                const subgroupName = Object.keys(item)[0];
                const children = Object.values(item)[0];
                if (Array.isArray(children) && subgroupName) {
                    children.forEach(child => map.set(child, subgroupName));
                }
            }
        }
    }
    return map;
};

export const calculateAverageVolumeForWeeks = (
    weeks: ProgramWeek[],
    exerciseList: ExerciseCatalogEntry[],
    muscleHierarchy: MuscleHierarchy | null,
    mode: 'simple' | 'complex' = 'complex'
): DetailedMuscleVolumeAnalysis[] => {
    if (weeks.length === 0) return [];

    const exIndex = buildExerciseIndex(exerciseList);
    const allMuscleTotals: Record<string, { totalVol: number, direct: Map<string, number>, indirect: Map<string, {sets: number, act: number}>, freqDirect: number, freqIndirect: number }> = {};
    const childToParentMap = muscleHierarchy ? createChildToParentMap(muscleHierarchy) : new Map<string, string>();
    
    const getDisplayGroup = (muscle: string) => getMuscleDisplayId(childToParentMap.get(muscle) || muscle) || 'Core';

    weeks.forEach(week => {
        week.sessions.forEach(session => {
            const exercises = (session.parts && session.parts.length > 0) ? session.parts.flatMap(p => p.exercises) : session.exercises;
            
            const sessionFreqImpact = new Map<string, { direct: number, indirect: number }>();

            exercises?.forEach(exercise => {
                const exerciseData = findExerciseWithFallback(exIndex, exercise.exerciseDbId || (exercise as any).exerciseId, exercise.name);
                
                if (!exerciseData || !exerciseData.involvedMuscles) return;

                const effectiveSets = exercise.sets?.filter(isSetEffective).length || 0;
                if (effectiveSets === 0) return;

                const isDirectEffective = (s: any) => {
                    if (!isSetEffective(s)) return false;
                    const rpe = s.rpe || s.completedRPE || s.targetRPE;
                    const rir = s.rir ?? s.completedRIR ?? s.targetRIR;
                    
                    if (s.isFailure || s.intensityMode === 'failure' || s.isAmrap || s.performanceMode === 'failed') return true;
                    if (rpe !== undefined && rpe >= 6) return true;
                    if (rir !== undefined && rir <= 4) return true;
                    
                    if (rpe === undefined && rir === undefined) return true;
                    return false;
                };
                const hasDirectEffectiveSets = exercise.sets?.some(isDirectEffective);

                const highestRolePerGroup = new Map<string, { maxMultiplier: number, bestRole: string }>();

                exerciseData.involvedMuscles.forEach(m => {
                    if (!m || !m.muscle) return;
                    const group = getDisplayGroup(m.muscle.toString());
                    const role = m.role ?? 'primary';
                    
                    const currentFreq = sessionFreqImpact.get(group) || { direct: 0, indirect: 0 };
                    if ((role === 'primary' || role === 'secondary') && hasDirectEffectiveSets) {
                        const impactVal = MUSCLE_ROLE_MULTIPLIERS[role] ?? 0.5;
                        currentFreq.direct = Math.max(currentFreq.direct, impactVal);
                    } else if (role === 'stabilizer' || role === 'neutralizer') {
                        currentFreq.indirect = 1.0;
                    }
                    sessionFreqImpact.set(group, currentFreq);

                    if (mode === 'simple' && role !== 'primary') return;
                    
                    const multiplier = mode === 'simple' ? 1.0 : (MUSCLE_ROLE_MULTIPLIERS[role] ?? 0.5);
                    
                    const existing = highestRolePerGroup.get(group);
                    if (!existing || existing.maxMultiplier < multiplier) {
                        highestRolePerGroup.set(group, {
                            maxMultiplier: multiplier,
                            bestRole: role
                        });
                    }
                });

                highestRolePerGroup.forEach((data, groupName) => {
                    if (!allMuscleTotals[groupName]) {
                        allMuscleTotals[groupName] = { totalVol: 0, direct: new Map(), indirect: new Map(), freqDirect: 0, freqIndirect: 0 };
                    }

                    allMuscleTotals[groupName].totalVol += effectiveSets * data.maxMultiplier;

                    if (data.bestRole === 'primary') {
                        allMuscleTotals[groupName].direct.set(exercise.name ?? '', (allMuscleTotals[groupName].direct.get(exercise.name ?? '') || 0) + effectiveSets);
                    } else if (mode === 'complex') {
                        const existing = allMuscleTotals[groupName].indirect.get(exercise.name ?? '');
                        const percentageEquivalent = data.maxMultiplier * 100; 
                        
                        if (!existing || existing.act < percentageEquivalent) {
                            allMuscleTotals[groupName].indirect.set(exercise.name ?? '', { sets: (existing?.sets || 0) + effectiveSets, act: percentageEquivalent });
                        }
                    }
                });
            });

            sessionFreqImpact.forEach((impact, groupName) => {
                if (!allMuscleTotals[groupName]) {
                    allMuscleTotals[groupName] = { totalVol: 0, direct: new Map(), indirect: new Map(), freqDirect: 0, freqIndirect: 0 };
                }
                
                allMuscleTotals[groupName].freqDirect += impact.direct;
                
                if (impact.direct === 0) {
                    allMuscleTotals[groupName].freqIndirect += impact.indirect;
                }
            });
        });
    });

    return Object.entries(allMuscleTotals)
        .filter(([muscleGroup]) => muscleGroup !== 'General')
        .map(([muscleGroup, data]) => ({
            muscleGroup,
            displayVolume: Math.round((data.totalVol / weeks.length) * 10) / 10,
            totalSets: Math.round(data.totalVol / weeks.length),
            frequency: Math.round((data.freqDirect / weeks.length) * 10) / 10,
            indirectFrequency: Math.round((data.freqIndirect / weeks.length) * 10) / 10,
            avgRestDays: null,
            directExercises: Array.from(data.direct.entries()).map(([name, sets]) => ({ name, sets: Math.round((sets / weeks.length) * 10) / 10 })),
            indirectExercises: Array.from(data.indirect.entries()).map(([name, info]) => ({ name, sets: Math.round((info.sets / weeks.length) * 10) / 10, activationPercentage: info.act })),
            avgIFI: null,
            recoveryStatus: 'N/A' as const,
        })).filter(v => v.displayVolume > 0 || (v as any).indirectFrequency > 0).sort((a, b) => b.displayVolume - a.displayVolume);
};

export const calculateLast7DaysMuscleVolume = (
    history: WorkoutLog[],
    exerciseList: ExerciseCatalogEntry[],
    muscleHierarchy: MuscleHierarchy | null
): DetailedMuscleVolumeAnalysis[] => {
    const sevenDaysAgo = new Date();
    sevenDaysAgo.setDate(sevenDaysAgo.getDate() - 7);
    
    // Convert history logs to a pseudo-week sessions format
    const recentLogs = history.filter(log => new Date(log.date) >= sevenDaysAgo);
    
    const pseudoSessions: any[] = recentLogs.map(log => ({
        id: log.id,
        name: log.sessionName,
        exercises: log.completedExercises.map(ce => ({
            exerciseDbId: ce.exerciseDbId,
            name: ce.exerciseName,
            sets: ce.sets
        }))
    }));

    const pseudoWeek: ProgramWeek = {
        id: 'last-7-days',
        name: 'Last 7 Days',
        sessions: pseudoSessions as any
    };

    return calculateAverageVolumeForWeeks([pseudoWeek], exerciseList, muscleHierarchy, 'complex');
};
