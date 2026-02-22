// components/onboarding/WelcomeWizard.tsx
// Welcome Wizard: 6+ slides, solo 1 vez. Estética NERD con fondo vertical animado.

import React, { useState, useCallback } from 'react';
import { AnimatedSvgBackground } from './AnimatedSvgBackground';
import {
    ZapIcon,
    BrainIcon,
    DumbbellIcon,
    TargetIcon,
    UtensilsIcon,
    ChevronRightIcon,
    ChevronLeftIcon,
    ActivityIcon,
} from '../icons';

const TOTAL_SLIDES = 6;

interface WelcomeWizardProps {
    onComplete: () => void;
}

const SLIDES: { id: string; title: string; icon: React.ReactNode; content: React.ReactNode }[] = [
    {
        id: 'welcome',
        title: 'Bienvenido a KPKN',
        icon: <ZapIcon size={40} className="text-amber-400" />,
        content: (
            <div className="space-y-4 text-center">
                <p className="text-zinc-300 text-sm font-medium leading-relaxed">
                    La app más completa para entrenar con inteligencia. Programa, trackea y optimiza tu rendimiento.
                </p>
                <p className="text-[10px] font-mono text-zinc-500 uppercase tracking-[0.3em]">
                    Primera impresión
                </p>
            </div>
        ),
    },
    {
        id: 'battery',
        title: 'Sistema de Batería (AUGE)',
        icon: <BrainIcon size={40} className="text-cyan-400" />,
        content: (
            <div className="space-y-4 text-left">
                <p className="text-zinc-300 text-sm font-medium leading-relaxed">
                    Tu cuerpo tiene <span className="text-white font-bold">3 baterías</span> que KPKN modela:
                </p>
                <ul className="space-y-2 text-xs text-zinc-400 font-mono">
                    <li><span className="text-cyan-400">SNC</span> — coordinación y fuerza máxima</li>
                    <li><span className="text-amber-400">Músculos</span> — fatiga por grupo muscular</li>
                    <li><span className="text-emerald-400">Columna</span> — carga axial</li>
                </ul>
                <p className="text-zinc-300 text-sm leading-relaxed">
                    Cada entrenamiento las drena; el sueño y la nutrición las recargan. KPKN te muestra en tiempo real cuánto tienes disponible para entrenar duro sin sobreentrenarte.
                </p>
            </div>
        ),
    },
    {
        id: 'programs',
        title: 'Programas y Sesiones',
        icon: <DumbbellIcon size={40} className="text-sky-400" />,
        content: (
            <div className="space-y-4 text-left">
                <p className="text-zinc-300 text-sm font-medium leading-relaxed">
                    <span className="text-white font-bold">Programas simples:</span> lineal u ondulante (A/B). <span className="text-white font-bold">Avanzados:</span> bloques, mesociclos, periodización profesional.
                </p>
                <p className="text-zinc-300 text-sm leading-relaxed">
                    Crea eventos y fechas clave según corresponda. La creación de programas y sesiones más completa disponible en una app.
                </p>
            </div>
        ),
    },
    {
        id: 'sessions',
        title: 'Sesiones Avanzadas',
        icon: <ActivityIcon size={40} className="text-orange-400" />,
        content: (
            <div className="space-y-4 text-left">
                <p className="text-zinc-300 text-sm font-medium leading-relaxed">
                    Trabaja con <span className="text-white font-bold">1RM</span> y cálculos automáticos, <span className="text-white font-bold">RPE/RIR</span>, <span className="text-white font-bold">AMRAP</span>, <span className="text-white font-bold">biseries</span> y circuitos.
                </p>
                <p className="text-zinc-300 text-sm leading-relaxed">
                    Múltiples opciones avanzadas para cada serie. Todo el control que necesitas.
                </p>
            </div>
        ),
    },
    {
        id: 'food',
        title: 'Trackeo de Alimentos',
        icon: <UtensilsIcon size={40} className="text-emerald-400" />,
        content: (
            <div className="space-y-4 text-left">
                <p className="text-zinc-300 text-sm font-medium leading-relaxed">
                    Sistema de descripción por <span className="text-white font-bold">etiquetas</span>. Escribe como hablas:
                </p>
                <p className="text-xs font-mono text-zinc-400 bg-white/5 rounded-lg p-3 border border-white/10">
                    &quot;200g arroz con pollo&quot;, &quot;2 huevos y tostadas&quot;
                </p>
                <p className="text-zinc-300 text-sm leading-relaxed">
                    Usa <span className="text-emerald-400 font-mono">y</span> para sumar alimentos, <span className="text-emerald-400 font-mono">con</span> para acompañamientos. Escribe cantidades (200g, 1 taza) y porciones (grande, mediano) cuando lo sepas.
                </p>
            </div>
        ),
    },
    {
        id: 'calories',
        title: 'Calorías Objetivo',
        icon: <TargetIcon size={40} className="text-amber-400" />,
        content: (
            <div className="space-y-4 text-left">
                <p className="text-zinc-300 text-sm font-medium leading-relaxed">
                    Establece tus <span className="text-white font-bold">calorías objetivo</span> y macros. Déficit, mantención o superávit con fórmulas TMB/TDEE.
                </p>
                <p className="text-zinc-300 text-sm leading-relaxed">
                    Conecta nutrición con la batería muscular para un cálculo de recuperación más preciso.
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

    return (
        <div className="fixed inset-0 z-[9999] flex flex-col bg-black/70 overflow-hidden">
            <AnimatedSvgBackground
                src="/fondo-welcome.svg"
                variant="vertical"
                animation="scroll"
                opacity={0.3}
            />

            <div className="relative z-10 flex-1 flex flex-col min-h-0">
                {/* Contenido del slide */}
                <div className="flex-1 overflow-y-auto custom-scrollbar flex flex-col items-center justify-center px-6 py-12 pb-24">
                    <div className="w-full max-w-md mx-auto">
                        <div className="flex justify-center mb-6">
                            <div className="w-20 h-20 rounded-2xl bg-black/20 border border-white/10 flex items-center justify-center backdrop-blur-md">
                                {current.icon}
                            </div>
                        </div>
                        <h2 className="text-xl font-black text-white uppercase tracking-tight text-center mb-6 font-mono">
                            {current.title}
                        </h2>
                        <div className="bg-black/25 backdrop-blur-md rounded-2xl border border-white/10 p-6">
                            {current.content}
                        </div>
                    </div>
                </div>

                {/* Navegación */}
                <div className="shrink-0 px-6 pb-6 pt-4 border-t border-white/5 bg-black/20 backdrop-blur-sm">
                    <div className="flex items-center justify-between gap-4 max-w-md mx-auto">
                        <button
                            onClick={handlePrev}
                            disabled={slide === 0}
                            className={`p-3 rounded-xl border transition-all font-mono text-[10px] font-black uppercase tracking-widest ${
                                slide === 0
                                    ? 'border-white/5 text-zinc-600 cursor-not-allowed'
                                    : 'border-white/20 text-zinc-400 hover:text-white hover:border-white/30'
                            }`}
                        >
                            <ChevronLeftIcon size={18} className="inline-block mr-1" />
                            Atrás
                        </button>

                        <div className="flex gap-1.5">
                            {Array.from({ length: TOTAL_SLIDES }).map((_, i) => (
                                <button
                                    key={i}
                                    onClick={() => setSlide(i)}
                                    className={`w-2 h-2 rounded-full transition-all ${
                                        i === slide ? 'bg-amber-500 w-6' : 'bg-white/20 hover:bg-white/30'
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
                    className="absolute bottom-6 right-6 z-20 w-12 h-12 rounded-full border border-amber-500/50 bg-amber-500/20 text-amber-400 hover:bg-amber-500/30 flex items-center justify-center shadow-lg transition-all"
                >
                    <ChevronRightIcon size={22} />
                </button>
            </div>
        </div>
    );
};
