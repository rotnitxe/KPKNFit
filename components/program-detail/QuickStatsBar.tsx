import React, { useMemo } from 'react';
import { Program } from '../../types';
import { CalendarIcon, ActivityIcon, DumbbellIcon, ClockIcon } from '../icons';

interface QuickStatsBarProps {
    program: Program;
    history: any[];
    currentWeekIndex: number;
    totalWeeks: number;
}

const QuickStatsBar: React.FC<QuickStatsBarProps> = ({
    program, history, currentWeekIndex, totalWeeks,
}) => {
    const stats = useMemo(() => {
        const programLogs = history.filter((log: any) => log.programId === program.id);

        const thisWeekLogs = programLogs.slice(0, 7);
        const sessionsThisWeek = thisWeekLogs.length;

        const totalSessions = program.macrocycles.reduce((acc, macro) => {
            return acc + (macro.blocks || []).reduce((bAcc, block) => {
                return bAcc + block.mesocycles.reduce((mAcc, meso) => {
                    return mAcc + meso.weeks.reduce((wAcc, w) => wAcc + (w.sessions?.length || 0), 0);
                }, 0);
            }, 0);
        }, 0);

        const adherence = totalSessions > 0
            ? Math.min(100, Math.round((programLogs.length / totalSessions) * 100))
            : 0;

        const avgVolume = programLogs.length > 0
            ? Math.round(programLogs.reduce((acc: number, log: any) => {
                const sets = (log.exercises || []).reduce((s: number, ex: any) => s + (ex.sets?.length || 0), 0);
                return acc + sets;
            }, 0) / programLogs.length)
            : 0;

        const trainingDays = new Set(programLogs.map((l: any) => l.date?.split('T')[0])).size;

        return { sessionsThisWeek, adherence, avgVolume, trainingDays };
    }, [program, history]);

    const items = [
        { label: 'Semana', value: `${currentWeekIndex + 1}/${totalWeeks || '∞'}`, icon: CalendarIcon },
        { label: 'Esta Sem.', value: `${stats.sessionsThisWeek}`, icon: DumbbellIcon },
        { label: 'Adherencia', value: `${stats.adherence}%`, icon: ActivityIcon },
        { label: 'Días', value: `${stats.trainingDays}`, icon: ClockIcon },
    ];

    return (
        <div className="grid grid-cols-4 gap-2 px-4 py-3">
            {items.map(item => (
                <div key={item.label} className="bg-zinc-900/50 border border-white/5 rounded-xl p-3 text-center">
                    <item.icon size={14} className="text-zinc-500 mx-auto mb-1" />
                    <div className="text-sm font-black text-white leading-none">{item.value}</div>
                    <div className="text-[8px] font-bold text-zinc-600 uppercase tracking-widest mt-1">{item.label}</div>
                </div>
            ))}
        </div>
    );
};

export default QuickStatsBar;
