// components/home/TopExercisesWidget.tsx
// Top 5 ejercicios más frecuentes con mini-cards

import React, { useMemo } from 'react';
import { useAppState } from '../../contexts/AppContext';
import { DumbbellIcon, ChevronRightIcon, TrendingUpIcon } from '../icons';

export const TopExercisesWidget: React.FC<{
    onNavigateToExercise: (exerciseName: string) => void;
}> = ({ onNavigateToExercise }) => {
    const { history } = useAppState();

    const topExercises = useMemo(() => {
        const count: Record<string, { count: number; lastDate: string; maxWeight: number }> = {};
        const thirtyDaysAgo = Date.now() - 30 * 24 * 60 * 60 * 1000;

        history
            .filter(log => new Date(log.date).getTime() > thirtyDaysAgo)
            .forEach(log => {
                log.completedExercises.forEach(ex => {
                    const name = ex.exerciseName || 'Ejercicio';
                    if (!count[name]) count[name] = { count: 0, lastDate: log.date, maxWeight: 0 };
                    count[name].count += 1;
                    if (log.date > count[name].lastDate) count[name].lastDate = log.date;
                    ex.sets?.forEach(s => {
                        const w = s.weight || 0;
                        if (w > count[name].maxWeight) count[name].maxWeight = w;
                    });
                });
            });

        return Object.entries(count)
            .sort((a, b) => b[1].count - a[1].count)
            .slice(0, 5)
            .map(([name, data]) => ({ name, ...data }));
    }, [history]);

    if (topExercises.length === 0) {
        return (
            <div className="w-full bg-[#0a0a0a] border border-white/10 rounded-2xl p-4">
                <div className="flex justify-between items-center mb-3">
                    <span className="text-[9px] font-black text-zinc-500 uppercase tracking-[0.2em] flex items-center gap-2">
                        <TrendingUpIcon size={10} className="text-blue-400" /> Top Ejercicios
                    </span>
                </div>
                <p className="text-[10px] text-zinc-500 font-mono">Sin datos en últimos 30 días</p>
            </div>
        );
    }

    return (
        <div className="w-full bg-[#0a0a0a] border border-white/10 rounded-2xl p-4">
            <div className="flex justify-between items-center mb-3">
                <span className="text-[9px] font-black text-zinc-500 uppercase tracking-[0.2em] flex items-center gap-2">
                    <TrendingUpIcon size={10} className="text-blue-400" /> Top Ejercicios
                </span>
                <ChevronRightIcon size={14} className="text-zinc-500" />
            </div>
            <div className="space-y-2">
                {topExercises.map((ex, i) => (
                    <button
                        key={ex.name}
                        onClick={() => onNavigateToExercise(ex.name)}
                        className="w-full flex items-center justify-between py-2 px-3 rounded-lg bg-black/30 border border-white/5 hover:border-white/15 transition-colors text-left group"
                    >
                        <div className="flex items-center gap-2 min-w-0">
                            <span className="text-[9px] font-mono text-zinc-500 w-4">{i + 1}</span>
                            <span className="text-[10px] font-bold text-white truncate">{ex.name}</span>
                        </div>
                        <div className="flex items-center gap-3 shrink-0">
                            <span className="text-[9px] font-mono text-zinc-500">{ex.count} ses.</span>
                            {ex.maxWeight > 0 && (
                                <span className="text-[9px] font-mono text-emerald-400">{ex.maxWeight} kg</span>
                            )}
                        </div>
                    </button>
                ))}
            </div>
        </div>
    );
};
