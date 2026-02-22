// components/onboarding/GeneralOnboardingWizard.tsx
// Wizard general primera vez: nutrición + programa + pre-calibración (flujo flexible). Estética NERD.

import React, { useState } from 'react';
import { useAppContext } from '../../contexts/AppContext';
import { NutritionWizard } from '../nutrition/NutritionWizard';
import { BatteryPrecalibrationStep } from './BatteryPrecalibrationStep';
import { AnimatedSvgBackground } from './AnimatedSvgBackground';
import { UtensilsIcon, DumbbellIcon, ZapIcon, ChevronRightIcon, XIcon } from '../icons';

type Phase = 'choice' | 'nutrition' | 'program' | 'precalibration';

interface GeneralOnboardingWizardProps {
    onComplete: () => void;
}

export const GeneralOnboardingWizard: React.FC<GeneralOnboardingWizardProps> = ({ onComplete }) => {
    const { navigateTo, setSettings } = useAppContext();
    const [phase, setPhase] = useState<Phase>('choice');

    const handleNutritionComplete = () => {
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
            <div className="fixed inset-0 z-[9998] flex flex-col bg-black overflow-hidden">
                <AnimatedSvgBackground
                    src="/fondo-wizards.svg"
                    variant="horizontal"
                    animation="zoom"
                    opacity={0.28}
                />
                <div className="relative z-10 flex-1 flex items-center justify-center p-6 min-h-0">
                    <div className="w-full max-w-md">
                        <div className="bg-black/60 backdrop-blur-xl border border-white/10 rounded-2xl overflow-hidden shadow-2xl">
                            <div className="p-8">
                                <div className="w-16 h-16 rounded-2xl bg-amber-500/20 border border-amber-500/30 flex items-center justify-center mx-auto mb-6">
                                    <ZapIcon size={32} className="text-amber-400" />
                                </div>
                                <h1 className="text-xl font-black text-white uppercase tracking-tight text-center mb-1 font-mono">
                                    Configura tu plan
                                </h1>
                                <p className="text-[10px] text-zinc-500 font-mono uppercase tracking-widest text-center mb-8">
                                    Elige por dónde empezar. Puedes completar el otro después.
                                </p>

                                <div className="space-y-4">
                                    <button
                                        onClick={() => setPhase('nutrition')}
                                        className="w-full p-5 rounded-xl border border-white/10 bg-white/5 hover:bg-white/10 hover:border-orange-500/30 transition-all text-left flex items-center gap-4 group"
                                    >
                                        <div className="w-12 h-12 rounded-xl bg-orange-500/20 border border-orange-500/20 flex items-center justify-center group-hover:bg-orange-500/30">
                                            <UtensilsIcon size={24} className="text-orange-400" />
                                        </div>
                                        <div className="flex-1 text-left min-w-0">
                                            <span className="font-bold text-white block font-mono text-sm">Empezar con Nutrición</span>
                                            <span className="text-[10px] text-zinc-500 font-mono">Datos biométricos y plan alimenticio</span>
                                        </div>
                                        <ChevronRightIcon size={20} className="text-zinc-500 shrink-0" />
                                    </button>

                                    <button
                                        onClick={handleProgramClick}
                                        className="w-full p-5 rounded-xl border border-white/10 bg-white/5 hover:bg-white/10 hover:border-sky-500/30 transition-all text-left flex items-center gap-4 group"
                                    >
                                        <div className="w-12 h-12 rounded-xl bg-sky-500/20 border border-sky-500/20 flex items-center justify-center group-hover:bg-sky-500/30">
                                            <DumbbellIcon size={24} className="text-sky-400" />
                                        </div>
                                        <div className="flex-1 text-left min-w-0">
                                            <span className="font-bold text-white block font-mono text-sm">Empezar con Programa</span>
                                            <span className="text-[10px] text-zinc-500 font-mono">Crear tu programa de entrenamiento</span>
                                        </div>
                                        <ChevronRightIcon size={20} className="text-zinc-500 shrink-0" />
                                    </button>
                                </div>

                                <button
                                    onClick={() => {
                                        setSettings({ hasSeenGeneralWizard: true, precalibrationDismissed: true });
                                        onComplete();
                                    }}
                                    className="mt-6 w-full py-2.5 text-[10px] font-mono text-zinc-500 hover:text-white transition-colors uppercase tracking-widest"
                                >
                                    Omitir por ahora
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        );
    }

    if (phase === 'nutrition') {
        return (
            <div className="fixed inset-0 z-[9998] flex flex-col bg-black overflow-hidden">
                <AnimatedSvgBackground src="/fondo-wizards.svg" variant="horizontal" animation="zoom" opacity={0.28} />
                <div className="relative z-10 flex flex-col flex-1 min-h-0">
                    <div className="flex justify-between items-center p-4 border-b border-white/10 shrink-0">
                        <h2 className="text-[10px] font-black text-white uppercase tracking-widest font-mono">Nutrición</h2>
                        <div className="flex gap-2">
                            <button
                                onClick={handleNutritionLater}
                                className="px-4 py-2 rounded-xl text-[10px] font-black uppercase bg-white/5 border border-white/10 text-zinc-400 hover:text-white font-mono"
                            >
                                Completar después
                            </button>
                            <button
                                onClick={() => {
                                    setSettings({ hasSeenGeneralWizard: true, hasDismissedNutritionSetup: true });
                                    onComplete();
                                }}
                                className="p-2 rounded-xl border border-white/10 text-zinc-500 hover:text-white"
                            >
                                <XIcon size={18} />
                            </button>
                        </div>
                    </div>
                    <div className="flex-1 overflow-y-auto">
                        <NutritionWizard onComplete={handleNutritionComplete} />
                    </div>
                </div>
            </div>
        );
    }

    if (phase === 'precalibration') {
        return (
            <div className="fixed inset-0 z-[9998] flex flex-col bg-black overflow-hidden">
                <AnimatedSvgBackground src="/fondo-wizards.svg" variant="horizontal" animation="zoom" opacity={0.28} />
                <div className="relative z-10 flex flex-col flex-1 min-h-0">
                    <div className="flex justify-between items-center p-4 border-b border-white/10 shrink-0">
                        <h2 className="text-[10px] font-black text-white uppercase tracking-widest font-mono">Pre-calibrar batería</h2>
                        <button
                            onClick={handlePrecalibrationSkip}
                            className="px-4 py-2 rounded-xl text-[10px] font-black uppercase bg-white/5 border border-white/10 text-zinc-400 hover:text-white font-mono"
                        >
                            Completar después
                        </button>
                    </div>
                    <div className="flex-1 overflow-y-auto">
                        <BatteryPrecalibrationStep onComplete={handlePrecalibrationComplete} onSkip={handlePrecalibrationSkip} />
                    </div>
                </div>
            </div>
        );
    }

    return null;
};
