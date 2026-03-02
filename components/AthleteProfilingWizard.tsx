import React, { useState } from 'react';
import { AthleteProfileScore } from '../types';
import { ChevronRightIcon, XIcon, ArrowLeftIcon, ActivityIcon, ZapIcon, MoveIcon, DumbbellIcon, TargetIcon } from './icons'; 

interface AthleteProfilingWizardProps {
    onComplete: (score: AthleteProfileScore) => void;
    onCancel: () => void;
    /** Modo inline: se renderiza en el flujo del documento (acordeón), sin overlay */
    inline?: boolean;
}

// Aseguramos que los IDs coincidan exactamente con las llaves de options
type QuestionStep = 'preference' | 'technique' | 'consistency' | 'strength' | 'mobility';

const AthleteProfilingWizard: React.FC<AthleteProfilingWizardProps> = ({ onComplete, onCancel, inline = false }) => {
    const [step, setStep] = useState<number>(0);
    const [scores, setScores] = useState({
        preference: 'Bodybuilder' as 'Bodybuilder' | 'Powerbuilder' | 'Powerlifter',
        technique: 0 as 1 | 2 | 3,
        consistency: 0 as 1 | 2 | 3,
        strength: 0 as 1 | 2 | 3,
        mobility: 0 as 1 | 2 | 3,
    });

    // 1. DEFINICIÓN DE PASOS (títulos amigables)
    const steps: { id: QuestionStep; title: string; description: string; icon: any }[] = [
        { id: 'preference', title: '¿Cuál es tu enfoque?', description: 'Define cómo quieres entrenar: estética, fuerza o ambos.', icon: <TargetIcon size={24} /> },
        { id: 'technique', title: '¿Cómo ejecutas los ejercicios?', description: 'Tu capacidad para mantener buena forma bajo carga.', icon: <DumbbellIcon size={24} /> },
        { id: 'consistency', title: '¿Qué tan regular entrenas?', description: 'Tu constancia en los últimos meses.', icon: <ActivityIcon size={24} /> },
        { id: 'strength', title: '¿Cómo está tu fuerza?', description: 'Referencia a cuánto mueves respecto a tu peso.', icon: <ZapIcon size={24} /> },
        { id: 'mobility', title: '¿Cómo está tu movilidad?', description: 'Rango de movimiento y molestias articulares.', icon: <MoveIcon size={24} /> },
    ];

    // 2. OPCIONES (Deben coincidir con los IDs de arriba)
    const options: Record<string, { value: any; label: string; detail: string }[]> = {
        preference: [
            { 
                value: 'Bodybuilder', 
                label: 'Músculo y estética', 
                detail: 'Quiero ganar tamaño muscular y verme mejor.' 
            },
            { 
                value: 'Powerbuilder', 
                label: 'Fuerza + Músculo', 
                detail: 'Quiero ser más fuerte y también ganar músculo.' 
            },
            { 
                value: 'Powerlifter', 
                label: 'Solo fuerza', 
                detail: 'Lo que importa es subir mis números (peso muerto, sentadilla, banca).' 
            },
        ],
        technique: [
            { 
                value: 1, 
                label: 'Estoy aprendiendo', 
                detail: 'Aún corrigiendo la técnica en varios ejercicios.' 
            },
            { 
                value: 2, 
                label: 'Técnica sólida', 
                detail: 'Ejecuto bien la mayoría de los ejercicios.' 
            },
            { 
                value: 3, 
                label: 'Dominio total', 
                detail: 'Mantengo buena forma incluso con peso máximo.' 
            },
        ],
        consistency: [
            { 
                value: 1, 
                label: 'Principiante', 
                detail: 'Empiezo de cero o acabo de retomar.' 
            },
            { 
                value: 2, 
                label: 'Intermedio', 
                detail: 'Entreno 2-3 veces por semana sin faltar mucho.' 
            },
            { 
                value: 3, 
                label: 'Muy constante', 
                detail: 'Entreno 4 o más días por semana, es parte de mi rutina.' 
            },
        ],
        strength: [
            { 
                value: 1, 
                label: 'Estoy empezando', 
                detail: 'Aún construyo mi fuerza base en los ejercicios principales.' 
            },
            { 
                value: 2, 
                label: 'Muevo mi peso bien', 
                detail: 'Puedo hacer sentadilla, peso muerto, etc. con mi propio peso o más.' 
            },
            { 
                value: 3, 
                label: 'Muevo cargas altas', 
                detail: 'Muevo 1.5x o más mi peso en los ejercicios compuestos.' 
            },
        ],
        mobility: [
            { 
                value: 1, 
                label: 'Rigidez o molestias', 
                detail: 'Algunos ejercicios me cuestan por falta de movilidad.' 
            },
            { 
                value: 2, 
                label: 'Sin limitaciones', 
                detail: 'Hago todos los ejercicios con buen rango de movimiento.' 
            },
            { 
                value: 3, 
                label: 'Muy flexible', 
                detail: 'Tengo buena movilidad incluso en posiciones profundas.' 
            },
        ],
    };

    const handleOptionSelect = (value: any) => {
        const currentStepId = steps[step].id;
        const newScores = { ...scores, [currentStepId]: value };
        setScores(newScores);

        if (step < steps.length - 1) {
            setStep(step + 1);
        } else {
            finishWizard(newScores);
        }
    };

    const finishWizard = (finalScores: typeof scores) => {
        const total = finalScores.technique + finalScores.consistency + finalScores.strength + finalScores.mobility;
        
        const result: AthleteProfileScore = {
            trainingStyle: finalScores.preference, 
            technicalScore: finalScores.technique,
            consistencyScore: finalScores.consistency,
            strengthScore: finalScores.strength,
            mobilityScore: finalScores.mobility,
            totalScore: total,
            profileLevel: total >= 8 ? 'Advanced' : 'Beginner'
        };
        
        onComplete(result);
    };

    const currentStepData = steps[step];
    // --- SOLUCIÓN DEL ERROR: Fallback a array vacío si options[...] es undefined ---
    const currentOptions = options[currentStepData?.id] || [];

    // Si por alguna razón currentStepData no existe, no renderizamos nada (Safety check)
    if (!currentStepData) return null;

    return (
        <div className={`flex flex-col font-mono animate-in fade-in duration-200 ${inline ? 'rounded-lg border border-white/10 bg-[#0a0a0a] overflow-hidden' : 'fixed inset-0 z-[300] bg-[#050505] safe-area-root'}`}>
            {/* Header */}
            <div className={`px-4 bg-[#050505] border-b border-white/10 ${inline ? 'py-2' : 'pt-12 pb-6'}`}>
                <div className={`flex justify-between items-center ${inline ? 'mb-2' : 'mb-8'}`}>
                    {step > 0 ? (
                        <button onClick={() => setStep(step - 1)} aria-label="Atrás" className="p-1.5 -ml-1.5 text-slate-400 hover:text-white transition-colors rounded">
                            <ArrowLeftIcon size={16} />
                        </button>
                    ) : (
                        <button onClick={onCancel} aria-label="Cerrar" className="p-1.5 -ml-1.5 text-slate-400 hover:text-white transition-colors rounded">
                            <XIcon size={16} />
                        </button>
                    )}
                    <div className="flex gap-1">
                        {steps.map((_, i) => (
                            <div key={i} className={`h-0.5 rounded-full transition-all ${i <= step ? 'w-4 bg-white/80' : 'w-1.5 bg-white/10'}`} />
                        ))}
                    </div>
                </div>

                <div className={`space-y-1 max-w-lg mx-auto w-full ${inline ? '' : 'space-y-3'}`}>
                    <div className="flex items-center gap-2 mb-0.5">
                        <span className="text-white/80 p-1.5 bg-white/5 rounded border border-white/10">{React.cloneElement(currentStepData.icon, { size: inline ? 14 : 24 })}</span>
                        <span className="text-[9px] font-bold uppercase tracking-widest text-slate-500">Paso {step + 1}/{steps.length}</span>
                    </div>
                    <h2 id="athlete-step-title" className={`font-bold text-white uppercase tracking-tight leading-tight ${inline ? 'text-sm' : 'text-2xl'}`}>{currentStepData.title}</h2>
                    <p className={`text-slate-400 leading-snug ${inline ? 'text-[11px]' : 'text-sm'}`}>{currentStepData.description}</p>
                </div>
            </div>

            {/* Body */}
            <main role="region" aria-labelledby="athlete-step-title" className={`flex-1 overflow-y-auto bg-[#050505] custom-scrollbar ${inline ? 'p-3 max-h-[36vh]' : 'p-6 pb-8'}`}>
                <div className={`flex flex-col max-w-lg mx-auto ${inline ? 'gap-1.5' : 'gap-3'}`}>
                    {currentOptions.map((opt) => (
                        <button
                            key={opt.value}
                            onClick={() => handleOptionSelect(opt.value)}
                            className={`group relative w-full text-left rounded-lg border border-white/10 bg-white/5 hover:bg-white/10 hover:border-white/20 transition-all active:scale-[0.99] focus:outline-none focus:ring-1 focus:ring-white/30 ${inline ? 'p-2.5' : 'p-5'}`}
                        >
                            <div className="flex justify-between items-center mb-0.5">
                                <span className={`font-bold uppercase tracking-tight text-white ${inline ? 'text-xs' : 'text-base'}`}>
                                    {opt.label}
                                </span>
                                {typeof opt.value === 'number' && (
                                    <div className="flex gap-0.5">
                                        {Array.from({ length: 3 }).map((_, i) => (
                                            <div key={i} className={`w-1 h-2 rounded-full ${i < opt.value ? 'bg-white/60' : 'bg-white/10'}`} />
                                        ))}
                                    </div>
                                )}
                            </div>
                            <p className={`text-slate-500 leading-snug group-hover:text-slate-300 transition-colors ${inline ? 'text-[10px] pr-5' : 'text-xs pr-6'}`}>
                                {opt.detail}
                            </p>
                            {!inline && (
                                <div className="absolute right-5 top-1/2 -translate-y-1/2 opacity-0 group-hover:opacity-100 transition-all transform translate-x-2 group-hover:translate-x-0">
                                    <ChevronRightIcon size={18} className="text-white/60" />
                                </div>
                            )}
                        </button>
                    ))}
                </div>
            </main>

            {!inline && (
                <div className="wizard-safe-footer p-4 bg-[#050505] border-t border-white/10 text-center">
                    <p className="text-[9px] text-slate-600 uppercase tracking-widest font-mono">KPKN • Calibración</p>
                </div>
            )}
        </div>
    );
};

export default AthleteProfilingWizard;