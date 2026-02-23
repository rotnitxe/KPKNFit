// components/home/SetupChecklistCard.tsx
// Tarjeta "Completa tu setup" con checkboxes para programa y/o plan de alimentación

import React from 'react';
import { DumbbellIcon, UtensilsIcon, ChevronRightIcon } from '../icons';

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
        <div className="w-full bg-black/60 backdrop-blur-sm border border-amber-500/20 rounded-2xl p-5">
            <h3 className="text-[10px] font-black text-amber-500/90 uppercase tracking-[0.2em] font-mono mb-4">
                Completa tu setup
            </h3>
            <div className="space-y-3">
                {!hasProgram && (
                    <button
                        onClick={onProgramPress}
                        className="w-full flex items-center gap-4 p-4 rounded-xl border border-white/10 bg-white/5 hover:bg-white/10 hover:border-sky-500/30 transition-all text-left group"
                    >
                        <div className="w-10 h-10 rounded-xl bg-sky-500/20 flex items-center justify-center shrink-0">
                            <DumbbellIcon size={20} className="text-sky-400" />
                        </div>
                        <div className="flex-1 min-w-0">
                            <span className="font-bold text-white block font-mono text-sm">Crear programa de entrenamiento</span>
                            <span className="text-[9px] text-zinc-500 font-mono">Diseña tu plan de entrenamiento</span>
                        </div>
                        <ChevronRightIcon size={18} className="text-zinc-500 group-hover:text-white shrink-0" />
                    </button>
                )}
                {!hasNutrition && (
                    <button
                        onClick={onNutritionPress}
                        className="w-full flex items-center gap-4 p-4 rounded-xl border border-white/10 bg-white/5 hover:bg-white/10 hover:border-cyber-copper/30 transition-all text-left group"
                    >
                        <div className="w-10 h-10 rounded-xl bg-cyber-copper/20 flex items-center justify-center shrink-0">
                            <UtensilsIcon size={20} className="text-cyber-copper" />
                        </div>
                        <div className="flex-1 min-w-0">
                            <span className="font-bold text-white block font-mono text-sm">Configurar plan de alimentación</span>
                            <span className="text-[9px] text-zinc-500 font-mono">Calorías, macros y objetivos</span>
                        </div>
                        <ChevronRightIcon size={18} className="text-zinc-500 group-hover:text-white shrink-0" />
                    </button>
                )}
            </div>
        </div>
    );
};
