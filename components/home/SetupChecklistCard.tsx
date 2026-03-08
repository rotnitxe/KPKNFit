// components/home/SetupChecklistCard.tsx
// Banner discreto "Completa tu configuración" (versión sutil según plan)

import React from 'react';
import { DumbbellIcon, UtensilsIcon } from '../icons';

interface SetupChecklistCardProps {
    hasProgram: boolean;
    hasNutrition: boolean;
    onProgramPress: () => void;
    onNutritionPress: () => void;
}

export const SetupChecklistCard: React.FC<SetupChecklistCardProps> = ({
    hasProgram,
    hasNutrition,
    onProgramPress,
    onNutritionPress,
}) => {
    if (hasProgram && hasNutrition) return null;

    return (
        <div className="w-full flex items-center gap-3 px-4 py-3 rounded-xl border border-[#E6E0E9] bg-white/[0.02]">
            <span className="text-[9px] font-bold text-[#49454F] uppercase tracking-widest flex-1">
                Completa tu configuración
            </span>
            <div className="flex items-center gap-1">
                {!hasProgram && (
                    <button onClick={onProgramPress} className="inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg border border-[#E6E0E9] hover:bg-white/5 hover:border-zinc-500/30 text-[8px] font-black text-[#49454F] uppercase tracking-widest transition-colors">
                        <DumbbellIcon size={10} /> Programa
                    </button>
                )}
                {!hasNutrition && (
                    <button onClick={onNutritionPress} className="inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg border border-[#E6E0E9] hover:bg-white/5 hover:border-zinc-500/30 text-[8px] font-black text-[#49454F] uppercase tracking-widest transition-colors">
                        <UtensilsIcon size={10} /> Nutrición
                    </button>
                )}
            </div>
        </div>
    );
};
