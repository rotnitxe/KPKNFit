// components/onboarding/GeneralOnboardingWizard.tsx
// Wizard general primera vez: nutrición + programa + pre-calibración (flujo flexible)

import React, { useState } from 'react';
import { useAppContext } from '../../contexts/AppContext';
import { NutritionWizard } from '../nutrition/NutritionWizard';
import { BatteryPrecalibrationStep } from './BatteryPrecalibrationStep';
import { UtensilsIcon, DumbbellIcon, ZapIcon, ChevronRightIcon, XIcon } from '../icons';

type Phase = 'choice' | 'nutrition' | 'program' | 'precalibration';

interface GeneralOnboardingWizardProps {
    onComplete: () => void;
}

export const GeneralOnboardingWizard: React.FC<GeneralOnboardingWizardProps> = ({ onComplete }) => {
    const { navigateTo, setSettings } = useAppContext();
    const [phase, setPhase] = useState<Phase>('choice');
    const [completedNutrition, setCompletedNutrition] = useState(false);
    const [completedProgram, setCompletedProgram] = useState(false);

    const handleNutritionComplete = () => {
        setCompletedNutrition(true);
        setSettings({ hasSeenNutritionWizard: true });
        setPhase('precalibration');
    };

    const handleNutritionLater = () => {
        setSettings({ hasDismissedNutritionSetup: true });
        setPhase('precalibration');
    };

    const handleProgramClick = () => {
        setPhase('program');
        setSettings({ hasSeenGeneralWizard: true });
        onComplete();
        navigateTo('program-editor');
    };

    const handlePrecalibrationComplete = () => {
        setSettings({ hasSeenGeneralWizard: true });
        onComplete();
    };

    const handlePrecalibrationSkip = () => {
        setSettings({ hasSeenGeneralWizard: true, precalibrationDismissed: true });
        onComplete();
    };

    if (phase === 'choice') {
        return (
            <div className="fixed inset-0 z-[9998] flex items-center justify-center p-4 bg-black/95">
                <div className="w-full max-w-md bg-[#0a0a0a] border border-white/10 rounded-2xl overflow-hidden">
                    <div className="p-8 text-center">
                        <div className="w-16 h-16 bg-amber-500/20 rounded-full flex items-center justify-center mx-auto mb-6">
                            <ZapIcon size={32} className="text-amber-400" />
                        </div>
                        <h1 className="text-2xl font-black text-white uppercase tracking-tight mb-2">Configura tu plan</h1>
                        <p className="text-[11px] text-zinc-400 mb-8">Elige por dónde empezar. Puedes completar el otro después.</p>

                        <div className="space-y-4">
                            <button
                                onClick={() => setPhase('nutrition')}
                                className="w-full p-5 rounded-xl border border-white/10 bg-white/5 hover:bg-white/10 transition-colors text-left flex items-center gap-4 group"
                            >
                                <div className="w-12 h-12 rounded-xl bg-orange-500/20 flex items-center justify-center group-hover:bg-orange-500/30">
                                    <UtensilsIcon size={24} className="text-orange-400" />
                                </div>
                                <div className="flex-1 text-left">
                                    <span className="font-bold text-white block">Empezar con Nutrición</span>
                                    <span className="text-[10px] text-zinc-500">Datos biométricos y plan alimenticio</span>
                                </div>
                                <ChevronRightIcon size={20} className="text-zinc-500" />
                            </button>

                            <button
                                onClick={handleProgramClick}
                                className="w-full p-5 rounded-xl border border-white/10 bg-white/5 hover:bg-white/10 transition-colors text-left flex items-center gap-4 group"
                            >
                                <div className="w-12 h-12 rounded-xl bg-sky-500/20 flex items-center justify-center group-hover:bg-sky-500/30">
                                    <DumbbellIcon size={24} className="text-sky-400" />
                                </div>
                                <div className="flex-1 text-left">
                                    <span className="font-bold text-white block">Empezar con Programa</span>
                                    <span className="text-[10px] text-zinc-500">Crear tu programa de entrenamiento</span>
                                </div>
                                <ChevronRightIcon size={20} className="text-zinc-500" />
                            </button>
                        </div>

                        <button
                            onClick={() => {
                                setSettings({ hasSeenGeneralWizard: true, precalibrationDismissed: true });
                                onComplete();
                            }}
                            className="mt-6 text-[10px] text-zinc-500 hover:text-white transition-colors"
                        >
                            Omitir por ahora
                        </button>
                    </div>
                </div>
            </div>
        );
    }

    if (phase === 'nutrition') {
        return (
            <div className="fixed inset-0 z-[9998] flex flex-col bg-black">
                <div className="flex justify-between items-center p-4 border-b border-white/10 shrink-0">
                    <h2 className="text-sm font-black text-white uppercase">Nutrición</h2>
                    <div className="flex gap-2">
                        <button
                            onClick={handleNutritionLater}
                            className="px-4 py-2 rounded-lg text-[10px] font-black uppercase bg-white/5 text-zinc-400 hover:text-white"
                        >
                            Completar después
                        </button>
                        <button
                            onClick={() => {
                                setSettings({ hasSeenGeneralWizard: true, hasDismissedNutritionSetup: true });
                                onComplete();
                            }}
                            className="p-2 rounded-lg text-zinc-500 hover:text-white"
                        >
                            <XIcon size={18} />
                        </button>
                    </div>
                </div>
                <div className="flex-1 overflow-y-auto">
                    <NutritionWizard onComplete={handleNutritionComplete} />
                </div>
            </div>
        );
    }

    if (phase === 'precalibration') {
        return (
            <div className="fixed inset-0 z-[9998] flex flex-col bg-black">
                <div className="flex justify-between items-center p-4 border-b border-white/10 shrink-0">
                    <h2 className="text-sm font-black text-white uppercase">Pre-calibrar batería</h2>
                    <button
                        onClick={handlePrecalibrationSkip}
                        className="px-4 py-2 rounded-lg text-[10px] font-black uppercase bg-white/5 text-zinc-400 hover:text-white"
                    >
                        Completar después
                    </button>
                </div>
                <div className="flex-1 overflow-y-auto">
                    <BatteryPrecalibrationStep onComplete={handlePrecalibrationComplete} onSkip={handlePrecalibrationSkip} />
                </div>
            </div>
        );
    }

    return null;
};
