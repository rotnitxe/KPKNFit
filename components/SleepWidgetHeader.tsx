// components/SleepWidgetHeader.tsx
import React from 'react';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { BedIcon, SunIcon } from './icons';
import { hapticImpact, ImpactStyle } from '../services/hapticsService';

export const SleepWidgetHeader: React.FC = () => {
    const { sleepStartTime } = useAppState();
    const { handleLogSleep } = useAppDispatch();

    const isSleeping = sleepStartTime !== null;

    const toggleSleep = () => {
        handleLogSleep(isSleeping ? 'end' : 'start');
        hapticImpact(ImpactStyle.Light);
    };

    const Icon = isSleeping ? SunIcon : BedIcon;
    const label = isSleeping ? 'Despertar' : 'Dormir';

    return (
        <button
            onClick={toggleSleep}
            className="flex items-center gap-2 text-xs font-semibold px-2 py-1 rounded-full transition-colors bg-slate-800/60 text-slate-300 hover:bg-slate-700/80"
            aria-label={label}
        >
            <Icon size={16} className={isSleeping ? 'text-yellow-400' : 'text-sky-400'} />
        </button>
    );
};
