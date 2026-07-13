// components/nutrition/NutritionSetupModal.tsx
// Sheet desde abajo — estilo Tú

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
        <>
            <div
                className="fixed inset-0 z-[200] bg-black/80 backdrop-blur-sm animate-fade-in"
                onClick={onConfigurarDespues}
                aria-hidden="true"
            />
            <div className="fixed left-0 right-0 bottom-0 z-[201] bg-[#FEF7FF] border-t border-[#E6E0E9] rounded-none shadow-2xl animate-slide-up pb-[max(1rem, env(safe-area-inset-bottom))]">
                <div className="w-10 h-1 rounded-full bg-white/20 mx-auto mt-3 mb-4" aria-hidden />
                <div className="px-6 pb-6">
                    <h2 className="text-lg font-black text-white uppercase tracking-tight mb-2">
                        Ajusta tu plan de alimentación
                    </h2>
                    <p className="text-sm text-[#49454F] mb-6">
                        Configura calorías y macros según tu objetivo, datos corporales y actividad.
                    </p>
                    <div className="flex flex-col gap-3">
                        <Button
                            onClick={onConfigurarAhora}
                            className="w-full !py-3 !bg-emerald-500 hover:!bg-emerald-400 !border-emerald-500/50"
                        >
                            Configurar ahora
                        </Button>
                        <button
                            onClick={onConfigurarDespues}
                            className="w-full py-3 rounded-none border border-[#E6E0E9] text-[#49454F] hover:text-white hover:border-white/20 transition-colors text-sm font-bold"
                        >
                            Configurar después
                        </button>
                    </div>
                </div>
            </div>
        </>
    );
};
