// components/onboarding/WelcomeWizard.tsx
// Welcome Wizard: 2-3 slides, solo 1 vez. Estética Cyber/NERD. Sin fondos animados.

import React, { useState, useCallback } from 'react';
import {
    ZapIcon,
    DumbbellIcon,
    UtensilsIcon,
    ChevronRightIcon,
    CheckIcon,
} from '../icons';

const TOTAL_SLIDES = 3;

interface WelcomeWizardProps {
    onComplete: () => void;
}

const SLIDES: { id: string; title: string; icon: React.ReactNode; content: React.ReactNode }[] = [
    {
        id: 'welcome',
        title: 'Bienvenido a KPKN',
        icon: <ZapIcon size={40} className="text-cyber-cyan" />,
        content: (
            <div className="space-y-4 text-center">
                <p className="text-[#1D1B20] text-sm font-medium leading-relaxed">
                    La app más completa para entrenar con inteligencia. Programa, trackea y optimiza tu rendimiento.
                </p>
                <p className="text-[10px]  text-[#49454F] uppercase tracking-[0.3em]">
                    Primera impresión
                </p>
            </div>
        ),
    },
    {
        id: 'capabilities',
        title: 'Todo en uno',
        icon: <DumbbellIcon size={40} className="text-cyber-cyan" />,
        content: (
            <div className="space-y-4 text-left">
                <p className="text-[#1D1B20] text-sm font-medium leading-relaxed">
                    <span className="text-[#1D1B20] font-bold">Programas:</span> estructura, bloques, mesociclos. <span className="text-[#1D1B20] font-bold">Sesiones:</span> 1RM, RPE/RIR, AMRAP, biseries.
                </p>
                <p className="text-[#1D1B20] text-sm leading-relaxed">
                    Batería muscular en tiempo real. Nutrición conectada. Todo el control que necesitas.
                </p>
            </div>
        ),
    },
    {
        id: 'ready',
        title: 'Empezar',
        icon: <UtensilsIcon size={40} className="text-cyber-cyan" />,
        content: (
            <div className="space-y-4 text-center">
                <p className="text-[#1D1B20] text-sm font-medium leading-relaxed">
                    Configura tu programa y plan nutricional. Puedes omitir y hacerlo después.
                </p>
                <p className="text-[10px]  text-cyber-cyan/80 uppercase tracking-widest">
                    Listo para entrenar
                </p>
            </div>
        ),
    },
];

export const WelcomeWizard: React.FC<WelcomeWizardProps> = ({ onComplete }) => {
    const [slide, setSlide] = useState(0);
    const isLast = slide === TOTAL_SLIDES - 1;

    const handleNext = useCallback(() => {
        if (isLast) {
            onComplete();
        } else {
            setSlide((s) => Math.min(s + 1, TOTAL_SLIDES - 1));
        }
    }, [isLast, onComplete]);

    const handlePrev = useCallback(() => {
        setSlide((s) => Math.max(s - 1, 0));
    }, []);

    const current = SLIDES[slide];
    const fabBottom = 'max(1.5rem, env(safe-area-inset-bottom))';

    return (
        <div className="fixed inset-0 z-[9999] flex flex-col bg-[#050505] overflow-hidden safe-area-root">
            <div className="relative z-10 flex-1 flex flex-col min-h-0">
                {/* Contenido del slide */}
                <div className="flex-1 overflow-y-auto custom-scrollbar flex flex-col items-center justify-center px-6 py-12 pb-24">
                    <div className="w-full max-w-md mx-auto">
                        <div className="flex justify-center mb-6">
                            <div className="w-20 h-20 rounded-2xl bg-white/5 border border-cyber-cyan/20 flex items-center justify-center">
                                {current.icon}
                            </div>
                        </div>
                        <h2 className="text-xl font-black text-[#1D1B20] uppercase tracking-tight text-center mb-6 ">
                            {current.title}
                        </h2>
                        <div className="bg-black/60 rounded-2xl border border-[#E6E0E9] p-6">
                            {current.content}
                        </div>
                    </div>
                </div>

                {/* Navegación inferior */}
                <div className="shrink-0 px-6 wizard-safe-footer pt-4 border-t border-[#E6E0E9]">
                    <div className="flex items-center justify-between gap-4 max-w-md mx-auto">
                        <button
                            onClick={handlePrev}
                            disabled={slide === 0}
                            aria-label="Atrás"
                            className={`p-3 rounded-xl border transition-all  text-[10px] font-black uppercase tracking-widest focus:outline-none focus:ring-2 focus:ring-cyber-cyan focus:ring-offset-2 focus:ring-offset-[#0a0a0a] ${
                                slide === 0
                                    ? 'border-[#E6E0E9] text-zinc-600 cursor-not-allowed'
                                    : 'border-white/20 text-[#49454F] hover:text-[#1D1B20] hover:border-cyber-cyan/30'
                            }`}
                        >
                            <ChevronRightIcon size={18} className="inline-block mr-1 rotate-180" />
                            Atrás
                        </button>

                        <div className="flex gap-1.5">
                            {Array.from({ length: TOTAL_SLIDES }).map((_, i) => (
                                <button
                                    key={i}
                                    onClick={() => setSlide(i)}
                                    className={`w-2 h-2 rounded-full transition-all focus:outline-none focus:ring-2 focus:ring-cyber-cyan focus:ring-offset-2 focus:ring-offset-[#0a0a0a] ${
                                        i === slide ? 'bg-cyber-cyan w-6' : 'bg-white/20 hover:bg-white/30'
                                    }`}
                                    aria-label={`Slide ${i + 1}`}
                                />
                            ))}
                        </div>

                        <div className="w-[72px]" aria-hidden />
                    </div>
                </div>

                {/* FAB Siguiente/Empezar */}
                <button
                    onClick={handleNext}
                    aria-label={isLast ? 'Empezar' : 'Siguiente'}
                    className="absolute z-20 w-12 h-12 rounded-full border border-cyber-cyan/50 bg-cyber-cyan/20 text-cyber-cyan hover:bg-cyber-cyan/30 flex items-center justify-center shadow-lg transition-all right-6 focus:outline-none focus:ring-2 focus:ring-cyber-cyan focus:ring-offset-2 focus:ring-offset-[#0a0a0a]"
                    style={{ bottom: fabBottom }}
                >
                    {isLast ? (
                        <CheckIcon size={22} strokeWidth={2.5} />
                    ) : (
                        <ChevronRightIcon size={22} />
                    )}
                </button>
            </div>
        </div>
    );
};
