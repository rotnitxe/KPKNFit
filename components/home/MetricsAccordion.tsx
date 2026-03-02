// components/home/MetricsAccordion.tsx
// Acordeón "Ver más métricas" con widgets secundarios

import React, { useState } from 'react';
import { ChevronDownIcon, ChevronUpIcon } from '../icons';
import {
    TopExercisesWidget,
    VolumeByMuscleWidget,
    RelativeStrengthWidget,
    Star1RMGoalsWidget,
    KeyDatesWidget,
    StreakWidget,
    QuickLogWidget,
} from './index';

interface MetricsAccordionProps {
    onNavigateToExercise: (name: string) => void;
    onNavigateProgram: () => void;
}

export const MetricsAccordion: React.FC<MetricsAccordionProps> = ({
    onNavigateToExercise,
    onNavigateProgram,
}) => {
    const [expanded, setExpanded] = useState(false);

    return (
        <div className="bg-[#0a0a0a] border border-white/10 rounded-xl overflow-hidden">
            <button
                onClick={() => setExpanded(!expanded)}
                className="w-full px-4 py-3 flex items-center justify-between hover:bg-white/[0.02] transition-colors"
            >
                <span className="text-[9px] font-black text-zinc-500 uppercase tracking-[0.2em]">
                    Ver más métricas
                </span>
                {expanded ? (
                    <ChevronUpIcon size={16} className="text-zinc-500" />
                ) : (
                    <ChevronDownIcon size={16} className="text-zinc-500" />
                )}
            </button>

            {expanded && (
                <div className="px-4 pb-4 space-y-4 border-t border-white/5 pt-4 animate-fade-in">
                    <TopExercisesWidget onNavigateToExercise={onNavigateToExercise} />
                    <VolumeByMuscleWidget onNavigate={onNavigateProgram} />
                    <RelativeStrengthWidget onNavigate={onNavigateProgram} />
                    <Star1RMGoalsWidget onNavigate={onNavigateProgram} />
                    <KeyDatesWidget onNavigate={onNavigateProgram} />
                    <StreakWidget />
                    <QuickLogWidget />
                </div>
            )}
        </div>
    );
};
