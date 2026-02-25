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
            onClick: () => setIsNutritionLogModalOpen(true),
            color: 'orange',
        },
    ];

    const colorClasses: Record<string, { border: string; icon: string }> = {
        sky: { border: 'border-sky-500/30 hover:border-sky-400/50', icon: 'text-sky-400' },
        indigo: { border: 'border-indigo-500/30 hover:border-indigo-400/50', icon: 'text-indigo-400' },
        orange: { border: 'border-cyber-copper/30 hover:border-cyber-copper/50', icon: 'text-cyber-copper' },
    };

    return (
        <div className="bg-[#0a0a0a] border border-white/10 rounded-2xl overflow-hidden">
            <div className="px-5 py-3 border-b border-white/5 flex justify-between items-center">
                <span className="text-[9px] font-black text-zinc-500 uppercase tracking-[0.25em]">
                    Quick Log
                </span>
            </div>
            <div className="p-4 grid grid-cols-3 gap-3">
                {tiles.map(({ id, label, icon: Icon, completed, onClick, color }) => {
                    const cc = colorClasses[color];
                    return (
                        <button
                            key={id}
                            onClick={onClick}
                            className={`flex flex-col items-center justify-center gap-2 p-4 rounded-xl border transition-all cursor-pointer bg-white/[0.02] ${cc.border}`}
                        >
                            <div className={`relative ${cc.icon}`}>
                                <Icon size={24} />
                                {completed && (
                                    <span className="absolute -top-1 -right-1 text-emerald-500">
                                        <CheckCircleIcon size={12} />
                                    </span>
                                )}
                            </div>
                            <span className="text-[9px] font-black text-zinc-400 uppercase tracking-widest">
                                {label}
                            </span>
                            <span className={`text-[8px] font-mono ${completed ? 'text-emerald-500' : 'text-zinc-600'}`}>
                                {completed ? 'OK' : '—'}
                            </span>
                        </button>
                    );
                })}
            </div>
        </div>
    );
};
