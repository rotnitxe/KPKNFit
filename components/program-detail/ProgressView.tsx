import React, { useMemo } from 'react';
import { motion } from 'framer-motion';
import { Program } from '../../types';
import { useAppContext } from '../../contexts/AppContext';
import { TargetIcon, TrendingUpIcon, StarIcon } from '../icons';

interface ProgressViewProps {
    program: Program;
    history: any[];
    settings: any;
}

interface ExerciseProgress {
    exerciseId: string;
    exerciseName: string;
    isStar: boolean;
    targetLoad?: number;
    bestPR: number;
    estimated1RM: number;
    logs: any[];
    trend: 'up' | 'down' | 'stable';
}

const ProgressView: React.FC<ProgressViewProps> = ({
    program,
    history,
    settings,
}) => {
    const { exerciseList } = useAppContext();

    // Obtener ejercicios estrella del programa
    const starExercises = useMemo(() => {
        const starIds = new Set<string>();
        
        // Buscar ejercicios marcados como estrella en las sesiones
        program.macrocycles.forEach(macro => {
            (macro.blocks || []).forEach(block => {
                block.mesocycles.forEach(meso => {
                    meso.weeks.forEach(week => {
                        week.sessions.forEach(session => {
                            session.exercises?.forEach((ex: any) => {
                                if (ex.isStar || ex.priority === 'high') {
                                    starIds.add(ex.exerciseId);
                                }
                            });
                        });
                    });
                });
            });
        });
        
        return starIds;
    }, [program]);

    // Calcular progreso por ejercicio
    const exerciseProgress = useMemo<ExerciseProgress[]>(() => {
        const progressMap = new Map<string, ExerciseProgress>();
        
        // Procesar historial
        history.forEach((log) => {
            if (log.programId !== program.id) return;
            
            log.exercises?.forEach((logEx: any) => {
                const exId = logEx.exerciseId;
                const exName = logEx.name || exerciseList.find((e: any) => e.id === exId)?.name || 'Ejercicio';
                
                if (!progressMap.has(exId)) {
                    progressMap.set(exId, {
                        exerciseId: exId,
                        exerciseName: exName,
                        isStar: starExercises.has(exId),
                        targetLoad: logEx.targetLoad,
                        bestPR: 0,
                        estimated1RM: 0,
                        logs: [],
                        trend: 'stable',
                    });
                }
                
                const progress = progressMap.get(exId)!;
                progress.logs.push(log);
                
                // Actualizar mejor PR
                const setPR = Math.max(...(logEx.sets?.map((s: any) => s.load * s.reps) || [0]));
                if (setPR > progress.bestPR) {
                    progress.bestPR = setPR;
                }
                
                // Calcular 1RM estimado (fórmula de Epley)
                const maxLoad = Math.max(...(logEx.sets?.map((s: any) => s.load) || [0]));
                const maxReps = logEx.sets?.find((s: any) => s.load === maxLoad)?.reps || 1;
                const estimated1RM = Math.round(maxLoad * (1 + maxReps / 30));
                
                if (estimated1RM > progress.estimated1RM) {
                    progress.estimated1RM = estimated1RM;
                }
            });
        });
        
        // Determinar tendencia
        progressMap.forEach((progress) => {
            if (progress.logs.length < 2) {
                progress.trend = 'stable';
                return;
            }
            
            const sortedLogs = [...progress.logs].sort((a, b) => 
                new Date(b.date).getTime() - new Date(a.date).getTime()
            );
            
            const recent1RM = sortedLogs[0]?.exercises?.[0]?.sets?.reduce((max: number, s: any) => 
                Math.max(max, s.load * (1 + s.reps / 30)), 0
            ) || 0;
            
            const previous1RM = sortedLogs[1]?.exercises?.[0]?.sets?.reduce((max: number, s: any) => 
                Math.max(max, s.load * (1 + s.reps / 30)), 0
            ) || 0;
            
            if (recent1RM > previous1RM * 1.05) {
                progress.trend = 'up';
            } else if (recent1RM < previous1RM * 0.95) {
                progress.trend = 'down';
            } else {
                progress.trend = 'stable';
            }
        });
        
        return Array.from(progressMap.values())
            .sort((a, b) => (b.isStar ? 1 : 0) - (a.isStar ? 1 : 0))
            .sort((a, b) => b.estimated1RM - a.estimated1RM);
    }, [history, program.id, starExercises, exerciseList]);

    // Filtrar solo ejercicios estrella o con progreso
    const displayedProgress = useMemo(() => {
        const stars = exerciseProgress.filter(e => e.isStar);
        const withProgress = exerciseProgress.filter(e => !e.isStar && e.logs.length > 0);
        return [...stars, ...withProgress].slice(0, 10);
    }, [exerciseProgress]);

    return (
        <div className="pb-6">
            {/* Resumen de progreso */}
            <div className="px-4 mb-4">
                <div className="bg-gradient-to-br from-emerald-50 to-white rounded-3xl border border-emerald-100 p-4 shadow-sm">
                    <div className="flex items-center justify-between mb-3">
                        <h3 className="text-sm font-black uppercase tracking-[0.2em] text-emerald-700">
                            Progreso General
                        </h3>
                        <div className="flex items-center gap-1 text-emerald-600">
                            <TrendingUpIcon size={16} />
                            <span className="text-[10px] font-black">
                                {exerciseProgress.filter(e => e.trend === 'up').length} mejorando
                            </span>
                        </div>
                    </div>
                    
                    <div className="grid grid-cols-3 gap-3">
                        <div className="text-center">
                            <div className="text-2xl font-black text-emerald-600">
                                {exerciseProgress.filter(e => e.isStar).length}
                            </div>
                            <div className="text-[8px] uppercase tracking-[0.15em] text-emerald-600/70 mt-0.5">
                                Ejercicios Estrella
                            </div>
                        </div>
                        <div className="text-center">
                            <div className="text-2xl font-black text-zinc-700">
                                {exerciseProgress.filter(e => e.trend === 'up').length}
                            </div>
                            <div className="text-[8px] uppercase tracking-[0.15em] text-zinc-400 mt-0.5">
                                En Progreso
                            </div>
                        </div>
                        <div className="text-center">
                            <div className="text-2xl font-black text-zinc-700">
                                {exerciseProgress.filter(e => e.trend === 'stable').length}
                            </div>
                            <div className="text-[8px] uppercase tracking-[0.15em] text-zinc-400 mt-0.5">
                                Estables
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            {/* Lista de ejercicios con progreso */}
            <div className="px-4 space-y-2">
                <h4 className="text-[10px] font-black uppercase tracking-[0.25em] text-zinc-400 mb-2">
                    Ejercicios Principales
                </h4>
                
                {displayedProgress.map((exercise, idx) => {
                    const progressToTarget = exercise.targetLoad 
                        ? Math.min((exercise.estimated1RM / exercise.targetLoad) * 100, 100)
                        : 0;
                    
                    return (
                        <motion.div
                            key={exercise.exerciseId}
                            initial={{ opacity: 0, y: 10 }}
                            animate={{ opacity: 1, y: 0 }}
                            transition={{ delay: idx * 0.05 }}
                            className={`
                                bg-white rounded-2xl border p-4 shadow-sm
                                ${exercise.isStar ? 'border-amber-200 bg-amber-50/20' : 'border-zinc-200'}
                            `}
                        >
                            {/* Header del ejercicio */}
                            <div className="flex items-start justify-between mb-3">
                                <div className="flex items-center gap-2">
                                    {exercise.isStar && (
                                        <StarIcon size={16} className="text-amber-500 fill-amber-500" />
                                    )}
                                    <div>
                                        <h5 className="text-sm font-bold text-zinc-900">
                                            {exercise.exerciseName}
                                        </h5>
                                        <div className="flex items-center gap-2 mt-0.5">
                                            <span className={`
                                                text-[8px] uppercase tracking-[0.15em] font-black
                                                ${exercise.trend === 'up' ? 'text-emerald-600' : 
                                                  exercise.trend === 'down' ? 'text-red-500' : 'text-zinc-400'}
                                            `}>
                                                {exercise.trend === 'up' ? '↑ Mejorando' : 
                                                 exercise.trend === 'down' ? '↓ Retrocediendo' : '→ Estable'}
                                            </span>
                                        </div>
                                    </div>
                                </div>
                                
                                <div className="text-right">
                                    <div className="text-lg font-black text-zinc-900">
                                        {exercise.estimated1RM} kg
                                    </div>
                                    <div className="text-[8px] uppercase tracking-[0.15em] text-zinc-400">
                                        1RM Est.
                                    </div>
                                </div>
                            </div>
                            
                            {/* Road to target */}
                            {exercise.targetLoad && (
                                <div className="mb-3">
                                    <div className="flex items-center justify-between mb-1">
                                        <span className="text-[8px] uppercase tracking-[0.2em] text-zinc-500 flex items-center gap-1">
                                            <TargetIcon size={10} />
                                            Road to {exercise.targetLoad} kg
                                        </span>
                                        <span className="text-[8px] font-black text-emerald-600">
                                            {Math.round(progressToTarget)}%
                                        </span>
                                    </div>
                                    <div className="h-2 bg-zinc-100 rounded-full overflow-hidden">
                                        <motion.div
                                            initial={{ width: 0 }}
                                            animate={{ width: `${progressToTarget}%` }}
                                            transition={{ duration: 0.5, delay: idx * 0.05 }}
                                            className="h-full bg-gradient-to-r from-emerald-400 to-emerald-500 rounded-full"
                                        />
                                    </div>
                                </div>
                            )}
                            
                            {/* Stats */}
                            <div className="grid grid-cols-3 gap-2 pt-3 border-t border-zinc-100">
                                <div>
                                    <div className="text-[8px] uppercase tracking-[0.15em] text-zinc-400">Mejor PR</div>
                                    <div className="text-sm font-black text-zinc-700">{exercise.bestPR} kg·reps</div>
                                </div>
                                <div>
                                    <div className="text-[8px] uppercase tracking-[0.15em] text-zinc-400">1RM Real</div>
                                    <div className="text-sm font-black text-zinc-700">
                                        {exercise.logs.flatMap((l: any) => l.exercises || [])
                                            .flatMap((e: any) => e.sets || [])
                                            .filter((s: any) => s.reps === 1)
                                            .reduce((max: number, s: any) => Math.max(max, s.load), 0) || '-'}
                                    </div>
                                </div>
                                <div>
                                    <div className="text-[8px] uppercase tracking-[0.15em] text-zinc-400">Sesiones</div>
                                    <div className="text-sm font-black text-zinc-700">{exercise.logs.length}</div>
                                </div>
                            </div>
                        </motion.div>
                    );
                })}
                
                {displayedProgress.length === 0 && (
                    <div className="text-center py-8">
                        <p className="text-sm text-zinc-500">Completa sesiones para ver tu progreso</p>
                    </div>
                )}
            </div>
        </div>
    );
};

export default ProgressView;
