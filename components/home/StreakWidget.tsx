// components/home/StreakWidget.tsx
// Streak: consecutive days + weekly adherence

import React, { useMemo } from 'react';
import { useAppState } from '../../contexts/AppContext';
import { FlameIcon } from '../icons';
import { getWeekId } from '../../utils/calculations';

export const StreakWidget: React.FC = () => {
    const { history, activeProgramState, programs, settings } = useAppState();

    const { streak, weeklyAdherence } = useMemo(() => {
        const completedDates = new Set(
            history.map(log => log.date.split('T')[0])
        );

        let streakCount = 0;
        const today = new Date();
        const todayStr = today.toISOString().split('T')[0];

        for (let i = 0; i < 365; i++) {
            const d = new Date(today);
            d.setDate(d.getDate() - i);
            const dateStr = d.toISOString().split('T')[0];
            if (completedDates.has(dateStr)) {
                streakCount++;
            } else {
                break;
            }
        }

        const startWeekOn = settings?.startWeekOn ?? 1;
        const currentWeekId = getWeekId(today, startWeekOn);
        let planned = 0;
        let completed = 0;

        if (activeProgramState && programs.length > 0) {
            const prog = programs.find(p => p.id === activeProgramState.programId);
            if (prog) {
                let found = false;
                for (const macro of prog.macrocycles || []) {
                    for (const block of macro.blocks || []) {
                        for (const meso of block.mesocycles || []) {
                            const week = meso.weeks?.find(w => w.id === activeProgramState.currentWeekId);
                            if (week) {
                                planned = (week.sessions || []).length;
                                completed = history.filter(
                                    log => log.programId === prog.id && getWeekId(new Date(log.date), startWeekOn) === currentWeekId
                                ).length;
                                found = true;
                                break;
                            }
                        }
                        if (found) break;
                    }
                    if (found) break;
                }
            }
        }
        if (planned === 0) {
            completed = history.filter(log => getWeekId(new Date(log.date), startWeekOn) === currentWeekId).length;
            planned = Math.max(completed, 4);
        }

        const adherence = planned > 0 ? Math.round((completed / planned) * 100) : 0;

        return { streak: streakCount, weeklyAdherence: Math.min(100, adherence) };
    }, [history, activeProgramState, programs, settings?.startWeekOn]);

    return (
        <div className="bg-[#0a0a0a] border border-white/10 rounded-2xl overflow-hidden">
            <div className="px-5 py-3 border-b border-white/5 flex justify-between items-center">
                <span className="text-[9px] font-black text-zinc-500 uppercase tracking-[0.25em] flex items-center gap-1.5">
                    <FlameIcon size={10} className="text-amber-400" /> Racha
                </span>
            </div>
            <div className="p-5 flex flex-col items-center">
                <div className="flex items-baseline gap-1">
                    <span className="text-4xl font-black font-mono text-white">{streak}</span>
                    <span className="text-[10px] text-zinc-500 font-black uppercase">días</span>
                </div>
                <p className="text-[8px] text-zinc-500 font-bold uppercase mt-1">consecutivos</p>
                <div className="mt-4 pt-4 border-t border-white/5 w-full">
                    <div className="flex justify-between text-[9px] font-black uppercase">
                        <span className="text-zinc-500">Semana</span>
                        <span className={weeklyAdherence >= 80 ? 'text-emerald-400' : weeklyAdherence >= 50 ? 'text-amber-400' : 'text-zinc-400'}>
                            {weeklyAdherence}%
                        </span>
                    </div>
                    <div className="mt-1.5 h-1.5 bg-zinc-900 rounded-full overflow-hidden">
                        <div
                            className="h-full bg-white/80 rounded-full transition-all duration-500"
                            style={{ width: `${weeklyAdherence}%` }}
                        />
                    </div>
                </div>
            </div>
        </div>
    );
};
