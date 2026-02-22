// components/nutrition/NutritionSetupModal.tsx
// Modal de aviso para ajustar plan de alimentación (primera visita)

import React from 'react';
import Button from '../ui/Button';

interface NutritionSetupModalProps {
    isOpen: boolean;
    onConfigurarAhora: () => void;
    onConfigurarDespues: () => void;
}

export const NutritionSetupModal: React.FC<NutritionSetupModalProps> = ({
    isOpen,
    onConfigurarAhora,
    onConfigurarDespues,
}) => {
    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 z-[200] flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm">
            <div className="bg-[#0a0a0a] border border-orange-500/30 rounded-2xl p-6 max-w-sm w-full shadow-xl">
                <h2 className="text-lg font-black text-white uppercase tracking-tight mb-2">
                    Ajusta tu plan de alimentación
                </h2>
                <p className="text-sm text-zinc-400 mb-6">
                    Configura calorías y macros según tu objetivo, datos corporales y actividad.
                </p>
                <div className="flex flex-col gap-3">
                    <Button onClick={onConfigurarAhora} className="w-full !py-3">
                        Configurar ahora
                    </Button>
                    <button
                        onClick={onConfigurarDespues}
                        className="w-full py-3 rounded-xl border border-white/10 text-zinc-400 hover:text-white hover:border-white/20 transition-colors text-sm font-bold"
                    >
                        Configurar después
                    </button>
                </div>
            </div>
        </div>
    );
};
