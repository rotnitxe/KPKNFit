// components/home/Star1RMGoalsWidget.tsx
// Records 1RM y progreso hacia meta en ejercicios estrella

import React, { useMemo } from 'react';
import { useAppState } from '../../contexts/AppContext';
import { calculateBrzycki1RM } from '../../utils/calculations';
import { StarIcon } from '../icons';

export const Star1RMGoalsWidget: React.FC<{
    onNavigate?: () => void;
}> = ({ onNavigate }) => {
    const { programs, history, activeProgramState } = useAppState();
    const activeProgram = programs.find(p => p.id === activeProgramState?.programId);

    const { starList, goalMap } = useMemo(() => {
        const list: string[] = [];
        const goals = new Map<string, number>();
        if (!activeProgram) return { starList: [], goalMap: goals };

        activeProgram.macrocycles?.forEach(m =>
            m.blocks?.forEach(b =>
                b.mesocycles?.forEach(me =>
                    me.weeks?.forEach(w =>
                        w.sessions?.forEach(s => {
                            const exs = (s.parts && s.parts.length > 0)
                                ? s.parts.flatMap((p: any) => p.exercises ?? [])
                                : (s.exercises ?? []);
                            exs.forEach((ex: any) => {
                                if (ex.isStarTarget && ex.name && !list.includes(ex.name)) {
                                    list.push(ex.name);
                                    if (ex.goal1RM != null && ex.goal1RM > 0) {
                                        goals.set(ex.name, ex.goal1RM);
                                    }
                                }
                            });
                        })
                    )
                )
            )
        );
        return { starList: list, goalMap: goals };
    }, [activeProgram]);

    const data = useMemo(() => {
        const programLogs = (history || []).filter(log => log.programId === activeProgram?.id);
        return starList.map(name => {
            let lastE1rm = 0;
            programLogs.forEach((log: any) => {
                (log.completedExercises || []).forEach((ex: any) => {
                    if ((ex.exerciseName || ex.name) !== name) return;
                    (ex.sets || []).forEach((s: any) => {
                        const w = s.weight ?? 0;
                        const r = s.completedReps ?? s.reps ?? 0;
                        if (w > 0 && r > 0) {
                            const e1rm = calculateBrzycki1RM(w, r);
                            if (e1rm > lastE1rm) lastE1rm = e1rm;
                        }
                    });
                });
            });
            const goal = goalMap.get(name);
            return { name, lastE1rm: lastE1rm > 0 ? lastE1rm : undefined, goal1RM: goal };
        });
    }, [starList, goalMap, history, activeProgram?.id]);

    if (starList.length === 0) {
        return (
            <div className="w-full bg-[#0a0a0a] border border-white/10 rounded-2xl p-4">
                <div className="flex justify-between items-center mb-3">
                    <span className="text-[9px] font-black text-zinc-500 uppercase tracking-[0.2em] flex items-center gap-2">
                        <StarIcon size={10} filled className="text-amber-400" /> 1RM Estrella
                    </span>
                </div>
                <p className="text-[10px] text-zinc-500 font-mono">Sin ejercicios estrella en el programa activo</p>
            </div>
        );
    }

    return (
        <div className="w-full bg-[#0a0a0a] border border-white/10 rounded-2xl overflow-hidden">
            <div className="flex justify-between items-center p-4 pb-2">
                <span className="text-[9px] font-black text-zinc-500 uppercase tracking-[0.2em] flex items-center gap-2">
                    <StarIcon size={10} filled className="text-amber-400" /> 1RM Estrella
                </span>
            </div>
            <div className="max-h-[200px] overflow-y-auto custom-scrollbar px-4 pb-4">
                <div className="space-y-2">
                    {data.map(({ name, lastE1rm, goal1RM }) => {
                        const progress = goal1RM != null && goal1RM > 0 && lastE1rm != null
                            ? Math.min(100, (lastE1rm / goal1RM) * 100)
                            : null;
                        return (
                            <div key={name} className="py-2 px-3 rounded-lg bg-black/30 border border-white/5 space-y-1.5">
                                <div className="flex items-center justify-between gap-2">
                                    <span className="text-[10px] font-bold text-white truncate flex-1">{name}</span>
                                    <span className="text-xs font-black text-cyan-400 shrink-0">
                                        {lastE1rm != null ? `${Math.round(lastE1rm)} kg` : '—'}
                                    </span>
                                    {goal1RM != null && (
                                        <span className="text-[8px] text-amber-400 shrink-0">meta {goal1RM}</span>
                                    )}
                                </div>
                                {progress != null && (
                                    <div className="h-1.5 bg-white/5 rounded-full overflow-hidden">
                                        <div
                                            className="h-full rounded-full transition-all"
                                            style={{
                                                width: `${progress}%`,
                                                backgroundColor: progress >= 100 ? '#10b981' : progress >= 70 ? '#06b6d4' : '#f59e0b',
                                            }}
                                        />
                                    </div>
                                )}
                            </div>
                        );
                    })}
                </div>
            </div>
        </div>
    );
};
