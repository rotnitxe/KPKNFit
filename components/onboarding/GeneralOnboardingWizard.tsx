// components/onboarding/GeneralOnboardingWizard.tsx
// Flujo: Programa (primero) → Nutrición → Precalibración. Estética Cyber/NERD.

import React, { useState } from 'react';
import { useAppContext } from '../../contexts/AppContext';
import { NutritionWizard } from '../nutrition/NutritionWizard';
import { BatteryPrecalibrationStep } from './BatteryPrecalibrationStep';
import { UtensilsIcon, DumbbellIcon, ZapIcon, ChevronRightIcon, XIcon } from '../icons';
import type { Program } from '../../types';
import ProgramEditor from '../ProgramEditor';

type Phase = 'choice' | 'program' | 'nutrition-choice' | 'nutrition' | 'precalibration';

interface GeneralOnboardingWizardProps {
    onComplete: () => void;
}

export const GeneralOnboardingWizard: React.FC<GeneralOnboardingWizardProps> = ({ onComplete }) => {
    const { setSettings, setPrograms, addToast, isOnline } = useAppContext();
    const [phase, setPhase] = useState<Phase>('choice');

    const handleNutritionComplete = () => {
        setSettings({ hasSeenNutritionWizard: true });
        setPhase('precalibration');
    };

    const handleNutritionLater = () => {
        setSettings({ hasDismissedNutritionSetup: true });
        setPhase('precalibration');
    };

    const handleProgramComplete = (program: Program) => {
        setPrograms((prev) => {
            const index = prev.findIndex((p) => p.id === program.id);
            if (index > -1) {
                const updated = [...prev];
                updated[index] = program;
                return updated;
            }
            return [...prev, program];
        });
        addToast('Programa guardado.', 'success');
        setPhase('nutrition-choice');
    };

    const handleProgramCancel = () => {
        setPhase('nutrition-choice');
    };

    const handlePrecalibrationComplete = () => {
        setSettings({ hasSeenGeneralWizard: true });
        onComplete();
    };

    const handlePrecalibrationSkip = () => {
        setSettings({ hasSeenGeneralWizard: true, precalibrationDismissed: true });
        onComplete();
    };

    const handleSkipAll = () => {
        setSettings({ hasSeenGeneralWizard: true, precalibrationDismissed: true });
        onComplete();
    };

    if (phase === 'program') {
        return (
            <div className="fixed inset-0 z-[9998] bg-[#050505] overflow-hidden safe-area-root">
                <ProgramEditor
                    onSave={handleProgramComplete}
                    onCancel={handleProgramCancel}
                    existingProgram={null}
                    isOnline={isOnline}
                    saveTrigger={0}
                />
            </div>
        );
    }

    if (phase === 'choice') {
        return (
            <div className="fixed inset-0 z-[9998] flex flex-col bg-[#050505] overflow-hidden safe-area-root">
                <div className="relative z-10 flex-1 flex items-center justify-center p-6 min-h-0">
                    <div className="w-full max-w-md">
                        <div className="bg-black/80 border border-white/10 rounded-2xl overflow-hidden">
                            <div className="p-8">
                                <div className="w-16 h-16 rounded-2xl bg-cyber-cyan/20 border border-cyber-cyan/30 flex items-center justify-center mx-auto mb-6">
                                    <ZapIcon size={32} className="text-cyber-cyan" />
                                </div>
                                <h1 className="text-xl font-black text-white uppercase tracking-tight text-center mb-1 font-mono">
                                    Configura tu plan
                                </h1>
                                <p className="text-[10px] text-zinc-500 font-mono uppercase tracking-widest text-center mb-8">
                                    Empieza creando tu programa. Nutrición después.
                                </p>

                                <div className="space-y-4">
                                    <button
                                        onClick={() => setPhase('program')}
                                        className="w-full p-5 rounded-xl border border-white/10 bg-white/5 hover:bg-white/10 hover:border-cyber-cyan/30 transition-all text-left flex items-center gap-4 group"
                                    >
                                        <div className="w-12 h-12 rounded-xl bg-cyber-cyan/20 border border-cyber-cyan/20 flex items-center justify-center group-hover:bg-cyber-cyan/30">
                                            <DumbbellIcon size={24} className="text-cyber-cyan" />
                                        </div>
                                        <div className="flex-1 text-left min-w-0">
                                            <span className="font-bold text-white block font-mono text-sm">Empezar con Programa</span>
                                            <span className="text-[10px] text-zinc-500 font-mono">Crear tu programa de entrenamiento</span>
                                        </div>
                                        <ChevronRightIcon size={20} className="text-zinc-500 shrink-0" />
                                    </button>
                                </div>

                                <button
                                    onClick={handleSkipAll}
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

    if (phase === 'nutrition-choice') {
        return (
            <div className="fixed inset-0 z-[9998] flex flex-col bg-[#050505] overflow-hidden safe-area-root">
                <div className="relative z-10 flex-1 flex items-center justify-center p-6 min-h-0">
                    <div className="w-full max-w-md">
                        <div className="bg-black/80 border border-white/10 rounded-2xl overflow-hidden">
                            <div className="p-8">
                                <div className="w-16 h-16 rounded-2xl bg-cyber-cyan/20 border border-cyber-cyan/30 flex items-center justify-center mx-auto mb-6">
                                    <UtensilsIcon size={32} className="text-cyber-cyan" />
                                </div>
                                <h1 className="text-xl font-black text-white uppercase tracking-tight text-center mb-1 font-mono">
                                    ¿Configurar Nutrición?
                                </h1>
                                <p className="text-[10px] text-zinc-500 font-mono uppercase tracking-widest text-center mb-8">
                                    Calorías, macros y objetivos. Opcional.
                                </p>

                                <div className="space-y-4">
                                    <button
                                        onClick={() => setPhase('nutrition')}
                                        className="w-full p-5 rounded-xl border border-white/10 bg-white/5 hover:bg-white/10 hover:border-cyber-cyan/30 transition-all text-left flex items-center gap-4 group"
                                    >
                                        <div className="w-12 h-12 rounded-xl bg-cyber-cyan/20 border border-cyber-cyan/20 flex items-center justify-center group-hover:bg-cyber-cyan/30">
                                            <UtensilsIcon size={24} className="text-cyber-cyan" />
                                        </div>
                                        <div className="flex-1 text-left min-w-0">
                                            <span className="font-bold text-white block font-mono text-sm">Empezar Nutrición</span>
                                            <span className="text-[10px] text-zinc-500 font-mono">Datos biométricos y plan</span>
                                        </div>
                                        <ChevronRightIcon size={20} className="text-zinc-500 shrink-0" />
                                    </button>
                                </div>

                                <button
                                    onClick={() => setPhase('precalibration')}
                                    className="mt-6 w-full py-2.5 text-[10px] font-mono text-zinc-500 hover:text-white transition-colors uppercase tracking-widest"
                                >
                                    Completar después
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
            <div className="fixed inset-0 z-[9998] flex flex-col bg-[#050505] overflow-hidden safe-area-root">
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
            <div className="fixed inset-0 z-[9998] flex flex-col bg-[#050505] overflow-hidden safe-area-root">
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
