import React, { useMemo } from 'react';
import { WorkoutLog, Settings } from '../../../types';

interface GhostSetInfoProps {
    exerciseId: string;
    setIndex: number;
    history: WorkoutLog[];
    settings: Settings;
}

export const GhostSetInfo: React.FC<GhostSetInfoProps> = ({ exerciseId, setIndex, history, settings }) => {
    const lastLog = useMemo(() => {
        for (let i = history.length - 1; i >= 0; i--) {
            const log = history[i];
            const completedEx = log.completedExercises.find(ex => ex.exerciseDbId === exerciseId || ex.exerciseId === exerciseId);
            if (completedEx && completedEx.sets[setIndex]) {
                if (completedEx.sets[setIndex].completedReps || completedEx.sets[setIndex].weight) {
                    return { date: log.date, set: completedEx.sets[setIndex] };
                }
            }
        }
        return null;
    }, [history, exerciseId, setIndex]);

    if (!lastLog) return null;
    const { date, set } = lastLog;
    const dateStr = new Date(date).toLocaleDateString('es-ES', { month: 'short', day: 'numeric' });

    return (
        <div className="text-[10px] text-black/40 flex items-center justify-center gap-2 mb-4 py-2 border-b border-black/5 font-black uppercase tracking-widest bg-black/[0.02] rounded-lg">
            <span>{dateStr}: {set.weight}{settings.weightUnit} x {set.completedReps}{set.completedRPE && <span className="ml-1 opacity-60">@{set.completedRPE}</span>}</span>
        </div>
    );
};
