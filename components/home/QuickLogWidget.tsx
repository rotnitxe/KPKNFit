// components/home/QuickLogWidget.tsx
// Quick Log: Peso, Sueño, Nutrición - tiles con estado completado/pendiente

import React, { useMemo } from 'react';
import { useAppState, useAppDispatch } from '../../contexts/AppContext';
import { BodyIcon, BedIcon, UtensilsIcon, CheckCircleIcon } from '../icons';
import { getLocalDateString } from '../../utils/dateUtils';

export const QuickLogWidget: React.FC = () => {
    const { bodyProgress, sleepLogs, nutritionLogs } = useAppState();
    const { setIsBodyLogModalOpen, setIsNutritionLogModalOpen, navigateTo } = useAppDispatch();

    const todayStr = getLocalDateString();

    const hasWeightToday = useMemo(() =>
        bodyProgress.some(l => l.date && l.date.startsWith(todayStr) && l.weight != null),
    [bodyProgress, todayStr]);

    const hasSleepToday = useMemo(() =>
        (sleepLogs || []).some(l => l.date && l.date.startsWith(todayStr)),
    [sleepLogs, todayStr]);

    const hasNutritionToday = useMemo(() =>
        (nutritionLogs || []).some(l => l.date && l.date.startsWith(todayStr)),
    [nutritionLogs, todayStr]);

    const tiles = [
        {
            id: 'peso',
            label: 'Peso',
            icon: BodyIcon,
            completed: hasWeightToday,
            onClick: () => setIsBodyLogModalOpen(true),
            color: 'sky',
        },
        {
            id: 'sueño',
            label: 'Sueño',
            icon: BedIcon,
            completed: hasSleepToday,
            onClick: () => navigateTo('sleep'),
            color: 'indigo',
        },
        {
            id: 'nutrición',
            label: 'Nutrición',
            icon: UtensilsIcon,
            completed: hasNutritionToday,
            onClick: () => { navigateTo('nutrition'); setIsNutritionLogModalOpen(true); },
            color: 'emerald',
        },
    ];

    const colorClasses: Record<string, { border: string; icon: string }> = {
        sky: { border: 'border-sky-500/30 hover:border-sky-400/50', icon: 'text-sky-400' },
        indigo: { border: 'border-indigo-500/30 hover:border-indigo-400/50', icon: 'text-indigo-400' },
        emerald: { border: 'border-emerald-500/30 hover:border-emerald-400/50', icon: 'text-emerald-400' },
    };

    const allEmpty = !hasWeightToday && !hasSleepToday && !hasNutritionToday;

    return (
        <div className="bg-[#FEF7FF] border border-[#E6E0E9] rounded-none overflow-hidden">
            <div className="px-5 py-3 border-b border-[#E6E0E9] flex justify-between items-center">
                <span className="text-[9px] font-black text-[#49454F] uppercase tracking-[0.25em]">
                    Quick Log
                </span>
            </div>
            {allEmpty ? (
                <div className="p-4">
                    <div className="flex items-center justify-between gap-3 py-2 px-3 rounded-lg bg-white/[0.02] border border-[#E6E0E9] min-h-[44px]">
                        <span className="text-[9px] text-[#49454F] font-bold uppercase">Añade datos</span>
                        <div className="flex items-center gap-2">
                            <button onClick={() => setIsBodyLogModalOpen(true)} className="text-[8px] font-black text-[#49454F] uppercase tracking-widest hover:text-white">Peso</button>
                            <button onClick={() => navigateTo('sleep')} className="text-[8px] font-black text-[#49454F] uppercase tracking-widest hover:text-white">Sueño</button>
                            <button onClick={() => { navigateTo('nutrition'); setIsNutritionLogModalOpen(true); }} className="text-[8px] font-black text-[#49454F] uppercase tracking-widest hover:text-white">Nutrición</button>
                        </div>
                    </div>
                </div>
            ) : (
            <div className="p-4 grid grid-cols-3 gap-3">
                {tiles.map(({ id, label, icon: Icon, completed, onClick, color }) => {
                    const cc = colorClasses[color];
                    return (
                        <button
                            key={id}
                            onClick={onClick}
                            className={`flex flex-col items-center justify-center gap-2 p-4 rounded-none border transition-all cursor-pointer bg-white/[0.02] ${cc.border}`}
                        >
                            <div className={`relative ${cc.icon}`}>
                                <Icon size={24} />
                                {completed && (
                                    <span className="absolute -top-1 -right-1 text-emerald-500">
                                        <CheckCircleIcon size={12} />
                                    </span>
                                )}
                            </div>
                            <span className="text-[9px] font-black text-[#49454F] uppercase tracking-widest">
                                {label}
                            </span>
                            <span className={`text-[8px] font-mono ${completed ? 'text-emerald-500' : 'text-zinc-600'}`}>
                                {completed ? 'OK' : '—'}
                            </span>
                        </button>
                    );
                })}
            </div>
            )}
        </div>
    );
};
