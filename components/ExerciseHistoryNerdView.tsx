// components/ExerciseHistoryNerdView.tsx
// Vista NERD ampliada del historial de un ejercicio

import React, { useMemo } from 'react';
import { WorkoutLog, Settings } from '../types';
import Modal from './ui/Modal';
import { BarChartIcon, ActivityIcon } from './icons';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';

interface ExerciseHistoryNerdViewProps {
    exerciseName: string;
    history: WorkoutLog[];
    settings: Settings;
    onClose: () => void;
}

export const ExerciseHistoryNerdView: React.FC<ExerciseHistoryNerdViewProps> = ({
    exerciseName,
    history,
    settings,
    onClose,
}) => {
    const exerciseHistory = useMemo(() => {
        const searchName = (exerciseName || '').trim().toLowerCase();
        return history
            .map(log => {
                const completedEx = log.completedExercises.find(ce => {
                    const ceName = (ce.exerciseName || '').trim().toLowerCase();
                    if (ceName && searchName && ceName === searchName) return true;
                    if (ce.exerciseId && String(ce.exerciseId) === exerciseName) return true;
                    return false;
                });
                if (completedEx) {
                    return {
                        date: log.date,
                        sessionName: log.sessionName,
                        programId: log.programId,
                        sets: completedEx.sets,
                    };
                }
                return null;
            })
            .filter((log): log is NonNullable<typeof log> => log !== null)
            .sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime());
    }, [history, exerciseName]);

    const chartData = useMemo(() => {
        return [...exerciseHistory]
            .reverse()
            .map(log => {
                const maxWeight = Math.max(...(log.sets.map(s => s.weight || 0)));
                const totalVolume = log.sets.reduce((acc, s) => acc + (s.weight || 0) * (s.completedReps || s.targetReps || 0), 0);
                return {
                    date: new Date(log.date).toLocaleDateString('es-ES', { day: '2-digit', month: 'short' }),
                    'Peso Máx': maxWeight > 0 ? maxWeight : null,
                    'Vol': totalVolume > 0 ? Math.round(totalVolume) : null,
                };
            })
            .filter(d => d['Peso Máx'] !== null || d['Vol'] !== null);
    }, [exerciseHistory]);

    const totalSets = useMemo(() => exerciseHistory.reduce((acc, log) => acc + log.sets.length, 0), [exerciseHistory]);
    const totalVolume = useMemo(() => {
        return exerciseHistory.reduce((acc, log) => {
            return acc + log.sets.reduce((s, set) => s + (set.weight || 0) * (set.completedReps || set.targetReps || 0), 0);
        }, 0);
    }, [exerciseHistory]);

    return (
        <Modal isOpen={true} onClose={onClose} title={`${exerciseName}`} useCustomContent>
            <div className="p-4 space-y-4 max-h-[80vh] overflow-y-auto custom-scrollbar bg-[#0a0a0a]">
                <div className="grid grid-cols-3 gap-2">
                    <div className="bg-black/50 border border-white/5 rounded-lg p-3 text-center">
                        <span className="text-[9px] font-black text-zinc-500 uppercase tracking-widest block">Sesiones</span>
                        <span className="text-lg font-black font-mono text-white">{exerciseHistory.length}</span>
                    </div>
                    <div className="bg-black/50 border border-white/5 rounded-lg p-3 text-center">
                        <span className="text-[9px] font-black text-zinc-500 uppercase tracking-widest block">Series</span>
                        <span className="text-lg font-black font-mono text-white">{totalSets}</span>
                    </div>
                    <div className="bg-black/50 border border-white/5 rounded-lg p-3 text-center">
                        <span className="text-[9px] font-black text-zinc-500 uppercase tracking-widest block">Vol total</span>
                        <span className="text-lg font-black font-mono text-emerald-400">{Math.round(totalVolume)}</span>
                    </div>
                </div>

                {chartData.length > 1 ? (
                    <div className="bg-black/30 p-3 rounded-xl border border-white/5">
                        <h4 className="text-[9px] font-black text-zinc-500 uppercase tracking-widest mb-3 flex items-center gap-2">
                            <BarChartIcon size={12} /> Progresión
                        </h4>
                        <div className="h-[180px] w-full">
                            <ResponsiveContainer width="100%" height="100%">
                                <LineChart data={chartData}>
                                    <CartesianGrid strokeDasharray="3 3" stroke="#333" vertical={false} />
                                    <XAxis dataKey="date" stroke="#666" tick={{ fill: '#666', fontSize: 9 }} tickLine={false} axisLine={false} />
                                    <YAxis stroke="#666" tick={{ fill: '#666', fontSize: 9 }} tickLine={false} axisLine={false} unit={settings.weightUnit} domain={['dataMin - 5', 'auto']} />
                                    <Tooltip 
                                        contentStyle={{ backgroundColor: '#111', border: '1px solid #333', borderRadius: '8px', fontSize: '11px' }} 
                                        itemStyle={{ color: '#fff' }}
                                    />
                                    <Line type="monotone" dataKey="Peso Máx" stroke="#3b82f6" strokeWidth={2} dot={{ r: 3 }} activeDot={{ r: 5 }} />
                                </LineChart>
                            </ResponsiveContainer>
                        </div>
                    </div>
                ) : (
                    <div className="text-center text-zinc-500 text-[10px] p-6 bg-black/20 rounded-xl border border-white/5 border-dashed font-mono">
                        {exerciseHistory.length < 2 ? 'Necesitas más sesiones para ver la gráfica.' : 'Sin datos de peso.'}
                    </div>
                )}

                <div>
                    <h4 className="text-[9px] font-black text-zinc-500 uppercase tracking-widest mb-2 flex items-center gap-2">
                        <ActivityIcon size={12} /> Historial
                    </h4>
                    <div className="space-y-2">
                        {exerciseHistory.slice(0, 10).map((log, index) => (
                            <div key={index} className="bg-black/30 border border-white/5 p-3 rounded-lg">
                                <div className="flex justify-between items-center mb-2">
                                    <span className="text-[10px] font-mono text-zinc-400">
                                        {new Date(log.date).toLocaleDateString('es-ES', { weekday: 'short', day: 'numeric', month: 'short' })}
                                    </span>
                                    <span className="text-[9px] text-zinc-500 font-mono">{log.sets.length} sets</span>
                                </div>
                                <div className="space-y-1">
                                    {log.sets.map((set, setIndex) => {
                                        if (set.completedReps === undefined && !set.weight) return null;
                                        return (
                                            <div key={setIndex} className="flex justify-between text-[10px] font-mono">
                                                <span className="text-zinc-500 w-4">{setIndex + 1}</span>
                                                <span className="text-white">
                                                    {set.weight || '—'}{settings.weightUnit} x {set.completedReps ?? set.targetReps ?? '—'}
                                                </span>
                                                {set.completedRPE && (
                                                    <span className="text-zinc-500">RPE {set.completedRPE}</span>
                                                )}
                                            </div>
                                        );
                                    })}
                                </div>
                            </div>
                        ))}
                    </div>
                    {exerciseHistory.length > 10 && (
                        <p className="text-[9px] text-zinc-600 font-mono mt-2">+{exerciseHistory.length - 10} sesiones más</p>
                    )}
                </div>
            </div>
        </Modal>
    );
};
