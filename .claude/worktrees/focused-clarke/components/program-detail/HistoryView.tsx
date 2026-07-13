import React, { useMemo, useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Program } from '../../types';
import { useAppContext } from '../../contexts/AppContext';
import { ChevronDownIcon, ChevronUpIcon, SearchIcon, DumbbellIcon, CalendarIcon, TrendingUpIcon } from '../icons';

interface HistoryViewProps {
    program: Program;
    history: any[];
}

interface ExerciseHistory {
    exerciseId: string;
    exerciseName: string;
    totalSessions: number;
    totalSets: number;
    totalReps: number;
    maxLoad: number;
    avgLoad: number;
    firstDate: string;
    lastDate: string;
    logs: any[];
}

const HistoryView: React.FC<HistoryViewProps> = ({ program, history }) => {
    const { exerciseList } = useAppContext();
    const [searchQuery, setSearchQuery] = useState('');
    const [expandedExercise, setExpandedExercise] = useState<string | null>(null);
    const [sortBy, setSortBy] = useState<'name' | 'sessions' | 'load'>('sessions');

    // Procesar historial por ejercicio
    const exerciseHistory = useMemo<ExerciseHistory[]>(() => {
        const historyMap = new Map<string, ExerciseHistory>();
        
        const programLogs = history.filter((log) => log.programId === program.id);
        
        programLogs.forEach((log) => {
            log.exercises?.forEach((logEx: any) => {
                const exId = logEx.exerciseId;
                const exName = logEx.name || exerciseList.find((e: any) => e.id === exId)?.name || 'Ejercicio';
                
                if (!historyMap.has(exId)) {
                    historyMap.set(exId, {
                        exerciseId: exId,
                        exerciseName: exName,
                        totalSessions: 0,
                        totalSets: 0,
                        totalReps: 0,
                        maxLoad: 0,
                        avgLoad: 0,
                        firstDate: log.date,
                        lastDate: log.date,
                        logs: [],
                    });
                }
                
                const hist = historyMap.get(exId)!;
                hist.logs.push(log);
                hist.totalSessions++;
                
                const sets = logEx.sets || [];
                hist.totalSets += sets.length;
                hist.totalReps += sets.reduce((acc: number, s: any) => acc + (s.reps || 0), 0);
                
                const maxSetLoad = Math.max(...sets.map((s: any) => s.load || 0), 0);
                if (maxSetLoad > hist.maxLoad) {
                    hist.maxLoad = maxSetLoad;
                }
                
                // Fechas
                if (new Date(log.date) < new Date(hist.firstDate)) {
                    hist.firstDate = log.date;
                }
                if (new Date(log.date) > new Date(hist.lastDate)) {
                    hist.lastDate = log.date;
                }
            });
        });
        
        // Calcular promedio de carga
        historyMap.forEach((hist) => {
            const allLoads = hist.logs.flatMap((l: any) => 
                l.exercises?.find((e: any) => e.exerciseId === hist.exerciseId)?.sets?.map((s: any) => s.load) || []
            );
            hist.avgLoad = allLoads.length > 0 
                ? Math.round(allLoads.reduce((a, b) => a + b, 0) / allLoads.length)
                : 0;
        });
        
        return Array.from(historyMap.values());
    }, [history, program.id, exerciseList]);

    // Filtrar y ordenar
    const filteredHistory = useMemo(() => {
        let filtered = exerciseHistory;
        
        if (searchQuery) {
            filtered = filtered.filter(ex => 
                ex.exerciseName.toLowerCase().includes(searchQuery.toLowerCase())
            );
        }
        
        return filtered.sort((a, b) => {
            switch (sortBy) {
                case 'name':
                    return a.exerciseName.localeCompare(b.exerciseName);
                case 'load':
                    return b.maxLoad - a.maxLoad;
                case 'sessions':
                default:
                    return b.totalSessions - a.totalSessions;
            }
        });
    }, [exerciseHistory, searchQuery, sortBy]);

    // Stats generales
    const totalStats = useMemo(() => {
        return {
            totalSessions: exerciseHistory.reduce((acc, ex) => acc + ex.totalSessions, 0),
            totalSets: exerciseHistory.reduce((acc, ex) => acc + ex.totalSets, 0),
            totalReps: exerciseHistory.reduce((acc, ex) => acc + ex.totalReps, 0),
            uniqueExercises: exerciseHistory.length,
        };
    }, [exerciseHistory]);

    const toggleExpand = (exId: string) => {
        setExpandedExercise(expandedExercise === exId ? null : exId);
    };

    return (
        <div className="pb-6">
            {/* Stats generales */}
            <div className="px-4 mb-4">
                <div className="bg-gradient-to-br from-blue-50 to-white rounded-3xl border border-blue-100 p-4 shadow-sm">
                    <h3 className="text-sm font-black uppercase tracking-[0.2em] text-blue-700 mb-3 text-center">
                        Historial Completo
                    </h3>
                    
                    <div className="grid grid-cols-4 gap-2">
                        <div className="text-center">
                            <div className="text-lg font-black text-blue-600">{totalStats.uniqueExercises}</div>
                            <div className="text-[7px] uppercase tracking-[0.15em] text-blue-600/70">Ejercicios</div>
                        </div>
                        <div className="text-center">
                            <div className="text-lg font-black text-blue-600">{totalStats.totalSessions}</div>
                            <div className="text-[7px] uppercase tracking-[0.15em] text-blue-600/70">Sesiones</div>
                        </div>
                        <div className="text-center">
                            <div className="text-lg font-black text-blue-600">{totalStats.totalSets}</div>
                            <div className="text-[7px] uppercase tracking-[0.15em] text-blue-600/70">Sets</div>
                        </div>
                        <div className="text-center">
                            <div className="text-lg font-black text-blue-600">{totalStats.totalReps}</div>
                            <div className="text-[7px] uppercase tracking-[0.15em] text-blue-600/70">Reps</div>
                        </div>
                    </div>
                </div>
            </div>

            {/* Buscador y filtro */}
            <div className="px-4 mb-3 flex items-center gap-2">
                <div className="flex-1 relative">
                    <SearchIcon size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-zinc-400" />
                    <input
                        type="text"
                        value={searchQuery}
                        onChange={(e) => setSearchQuery(e.target.value)}
                        placeholder="Buscar ejercicio..."
                        className="w-full pl-10 pr-4 py-2.5 rounded-xl bg-white border border-zinc-200 text-sm font-medium text-zinc-900 placeholder-zinc-400 focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500"
                    />
                </div>
                <select
                    value={sortBy}
                    onChange={(e) => setSortBy(e.target.value as any)}
                    className="px-3 py-2.5 rounded-xl bg-white border border-zinc-200 text-[9px] font-black uppercase tracking-[0.15em] text-zinc-600 focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500"
                >
                    <option value="sessions">Más sesiones</option>
                    <option value="load">Más carga</option>
                    <option value="name">Nombre</option>
                </select>
            </div>

            {/* Lista de ejercicios */}
            <div className="px-4 space-y-2">
                {filteredHistory.map((exercise, idx) => {
                    const isExpanded = expandedExercise === exercise.exerciseId;
                    
                    return (
                        <motion.div
                            key={exercise.exerciseId}
                            initial={{ opacity: 0, y: 10 }}
                            animate={{ opacity: 1, y: 0 }}
                            transition={{ delay: idx * 0.03 }}
                            className="bg-white rounded-2xl border border-zinc-200 overflow-hidden shadow-sm"
                        >
                            {/* Header del ejercicio */}
                            <button
                                onClick={() => toggleExpand(exercise.exerciseId)}
                                className="w-full px-4 py-3 flex items-center justify-between hover:bg-zinc-50 transition-colors"
                            >
                                <div className="flex items-center gap-3">
                                    <div className="w-10 h-10 rounded-xl bg-blue-50 text-blue-600 flex items-center justify-center">
                                        <DumbbellIcon size={20} />
                                    </div>
                                    <div className="text-left">
                                        <h5 className="text-sm font-bold text-zinc-900">
                                            {exercise.exerciseName}
                                        </h5>
                                        <div className="flex items-center gap-2 mt-0.5">
                                            <span className="text-[9px] text-zinc-500">
                                                {exercise.totalSessions} sesiones
                                            </span>
                                            <span className="text-[9px] text-zinc-400">•</span>
                                            <span className="text-[9px] text-zinc-500">
                                                Máx {exercise.maxLoad} kg
                                            </span>
                                        </div>
                                    </div>
                                </div>
                                {isExpanded ? (
                                    <ChevronUpIcon size={20} className="text-zinc-400" />
                                ) : (
                                    <ChevronDownIcon size={20} className="text-zinc-400" />
                                )}
                            </button>

                            {/* Contenido expandido */}
                            <AnimatePresence>
                                {isExpanded && (
                                    <motion.div
                                        initial={{ height: 0, opacity: 0 }}
                                        animate={{ height: 'auto', opacity: 1 }}
                                        exit={{ height: 0, opacity: 0 }}
                                        transition={{ duration: 0.2 }}
                                        className="overflow-hidden"
                                    >
                                        <div className="px-4 pb-4 pt-2 border-t border-zinc-100">
                                            {/* Stats detalladas */}
                                            <div className="grid grid-cols-3 gap-2 mb-3">
                                                <div className="bg-zinc-50 rounded-xl p-2 text-center">
                                                    <div className="text-[8px] uppercase tracking-[0.15em] text-zinc-400">Sets</div>
                                                    <div className="text-sm font-black text-zinc-700">{exercise.totalSets}</div>
                                                </div>
                                                <div className="bg-zinc-50 rounded-xl p-2 text-center">
                                                    <div className="text-[8px] uppercase tracking-[0.15em] text-zinc-400">Reps</div>
                                                    <div className="text-sm font-black text-zinc-700">{exercise.totalReps}</div>
                                                </div>
                                                <div className="bg-zinc-50 rounded-xl p-2 text-center">
                                                    <div className="text-[8px] uppercase tracking-[0.15em] text-zinc-400">Promedio</div>
                                                    <div className="text-sm font-black text-zinc-700">{exercise.avgLoad} kg</div>
                                                </div>
                                            </div>

                                            {/* Timeline de sesiones */}
                                            <div className="space-y-2">
                                                <div className="flex items-center gap-2 mb-2">
                                                    <CalendarIcon size={14} className="text-zinc-400" />
                                                    <span className="text-[9px] font-black uppercase tracking-[0.2em] text-zinc-500">
                                                        {new Date(exercise.firstDate).toLocaleDateString()} - {new Date(exercise.lastDate).toLocaleDateString()}
                                                    </span>
                                                </div>

                                                {/* Últimas sesiones */}
                                                {exercise.logs.slice(0, 5).map((log, logIdx) => {
                                                    const logEx = log.exercises?.find((e: any) => e.exerciseId === exercise.exerciseId);
                                                    if (!logEx) return null;
                                                    
                                                    const bestSet = logEx.sets?.reduce((max: any, s: any) => 
                                                        (s.load * s.reps) > (max?.load * max?.reps || 0) ? s : max
                                                    , null);
                                                    
                                                    return (
                                                        <div
                                                            key={log.id || logIdx}
                                                            className="flex items-center justify-between py-2 px-3 rounded-xl bg-zinc-50"
                                                        >
                                                            <div>
                                                                <div className="text-[9px] text-zinc-500">
                                                                    {new Date(log.date).toLocaleDateString()}
                                                                </div>
                                                                <div className="text-[10px] font-medium text-zinc-600">
                                                                    {log.sessionName || 'Sesión'}
                                                                </div>
                                                            </div>
                                                            {bestSet && (
                                                                <div className="text-right">
                                                                    <div className="text-sm font-black text-zinc-900">
                                                                        {bestSet.load} kg × {bestSet.reps}
                                                                    </div>
                                                                    <div className="text-[8px] text-zinc-400">
                                                                        {bestSet.load * bestSet.reps} kg·reps
                                                                    </div>
                                                                </div>
                                                            )}
                                                        </div>
                                                    );
                                                })}
                                            </div>
                                        </div>
                                    </motion.div>
                                )}
                            </AnimatePresence>
                        </motion.div>
                    );
                })}

                {filteredHistory.length === 0 && (
                    <div className="text-center py-8">
                        <DumbbellIcon size={48} className="mx-auto text-zinc-300 mb-3" />
                        <p className="text-sm text-zinc-500">No hay historial registrado</p>
                        <p className="text-[11px] text-zinc-400 mt-1">Completa sesiones para ver el historial</p>
                    </div>
                )}
            </div>
        </div>
    );
};

export default HistoryView;
