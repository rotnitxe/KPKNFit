// components/home/WeekPreviewSection.tsx
// Vista Lun-Dom expandible inline con sesiones/descanso

import React, { useState, useMemo } from 'react';
import { Program, ProgramWeek, Session } from '../../types';
import { ChevronDownIcon, ChevronUpIcon } from '../icons';

const DAY_NAMES = ['Dom', 'Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb'];

interface WeekPreviewSectionProps {
    program: Program | null;
    activeProgramState: { currentWeekId?: string; currentMacrocycleIndex?: number } | null;
    onNavigateProgram: () => void;
}

export const WeekPreviewSection: React.FC<WeekPreviewSectionProps> = ({
    program,
    activeProgramState,
    onNavigateProgram,
}) => {
    const [expanded, setExpanded] = useState(false);

    const weekData = useMemo(() => {
        if (!program || !activeProgramState?.currentWeekId) return null;
        const mIdx = activeProgramState.currentMacrocycleIndex ?? 0;
        const macro = program.macrocycles?.[mIdx];
        if (!macro) return null;

        let activeWeek: ProgramWeek | null = null;
        for (const block of macro.blocks || []) {
            for (const meso of block.mesocycles || []) {
                const w = meso.weeks?.find(we => we.id === activeProgramState.currentWeekId);
                if (w) {
                    activeWeek = w;
                    break;
                }
            }
            if (activeWeek) break;
        }

        if (!activeWeek?.sessions) return null;

        const sessionsByDay: Record<number, Session[]> = {};
        for (let d = 0; d <= 6; d++) sessionsByDay[d] = [];
        for (const s of activeWeek.sessions) {
            const day = s.dayOfWeek ?? 0;
            if (!sessionsByDay[day]) sessionsByDay[day] = [];
            sessionsByDay[day].push(s);
        }

        return { sessionsByDay, weekId: activeWeek.id };
    }, [program, activeProgramState]);

    if (!program) return null;

    const days = [0, 1, 2, 3, 4, 5, 6];
    const hasSessions = weekData && Object.values(weekData.sessionsByDay).some(arr => arr.length > 0);

    return (
        <div className="bg-[#0a0a0a] border border-white/10 rounded-xl overflow-hidden">
            <button
                onClick={() => setExpanded(!expanded)}
                className="w-full px-4 py-3 flex items-center justify-between hover:bg-white/[0.02] transition-colors"
            >
                <span className="text-[9px] font-black text-zinc-500 uppercase tracking-[0.2em]">
                    Esta semana
                </span>
                {expanded ? (
                    <ChevronUpIcon size={16} className="text-zinc-500" />
                ) : (
                    <ChevronDownIcon size={16} className="text-zinc-500" />
                )}
            </button>

            {expanded && (
                <div className="px-4 pb-4 animate-fade-in">
                    {hasSessions ? (
                        <div className="grid grid-cols-7 gap-1">
                            {days.map(day => {
                                const sess = weekData?.sessionsByDay[day] ?? [];
                                const isRest = sess.length === 0;
                                return (
                                    <div
                                        key={day}
                                        className={`flex flex-col items-center py-2 px-1 rounded-lg ${
                                            isRest ? 'bg-zinc-900/50' : 'bg-amber-950/20 border border-amber-500/20'
                                        }`}
                                    >
                                        <span className="text-[8px] font-mono text-zinc-500">
                                            {DAY_NAMES[day]}
                                        </span>
                                        {isRest ? (
                                            <span className="text-[8px] text-zinc-600 mt-0.5">—</span>
                                        ) : (
                                            <span className="text-[8px] font-bold text-amber-400 mt-0.5 truncate w-full text-center" title={sess[0]?.name}>
                                                {sess[0]?.name?.slice(0, 6) || 'Sesión'}
                                            </span>
                                        )}
                                    </div>
                                );
                            })}
                        </div>
                    ) : (
                        <p className="text-[9px] text-zinc-500 text-center py-4">
                            Sin sesiones en esta semana
                        </p>
                    )}
                    <button
                        onClick={onNavigateProgram}
                        className="w-full mt-3 py-2 rounded-lg border border-white/10 text-[9px] font-black text-zinc-400 uppercase tracking-widest hover:text-white hover:border-white/20 transition-colors"
                    >
                        Ver programa
                    </button>
                </div>
            )}
        </div>
    );
};
