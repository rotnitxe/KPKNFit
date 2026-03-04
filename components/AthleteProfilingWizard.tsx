import React, { useState } from 'react';
import { AthleteProfileScore } from '../types';
import { ArrowLeftIcon, ActivityIcon, ZapIcon, MoveIcon, DumbbellIcon, TargetIcon } from './icons';

interface AthleteProfilingWizardProps {
    onComplete: (score: AthleteProfileScore) => void;
    onCancel: () => void;
    /** Modo inline: se renderiza en acordeón, sin overlay */
    inline?: boolean;
    /** Modo integrado: dentro del wizard de bienvenida, mismo layout y estética Tú */
    embedded?: boolean;
}

// Aseguramos que los IDs coincidan exactamente con las llaves de options
type QuestionStep = 'preference' | 'technique' | 'consistency' | 'strength' | 'mobility';

const AthleteProfilingWizard: React.FC<AthleteProfilingWizardProps> = ({ onComplete, onCancel, inline = false, embedded = false }) => {
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
        { id: 'mobility', title: '¿Cómo está tu movilidad?', description: 'Cómo te sientes con el rango de movimiento de tus articulaciones.', icon: <MoveIcon size={24} /> },
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
    const currentOptions = options[currentStepData?.id] || [];
    if (!currentStepData) return null;

    const content = (
        <div className="animate-in fade-in slide-in-from-bottom-4 duration-300">
            <h2 className="text-xl font-black text-[var(--md-sys-color-on-surface)] mb-2 uppercase tracking-tight">{currentStepData.title}</h2>
            <p className="text-body-sm text-[var(--md-sys-color-on-surface-variant)] mb-8 opacity-60 leading-relaxed">{currentStepData.description}</p>
            <div className="flex flex-col gap-4">
                {currentOptions.map((opt) => (
                    <button
                        key={opt.value}
                        onClick={() => handleOptionSelect(opt.value)}
                        className="group w-full text-left bg-[var(--md-sys-color-surface-container-high)] border border-[var(--md-sys-color-outline-variant)]/30 rounded-[1.5rem] px-5 py-5 transition-all hover:scale-[1.02] hover:bg-[var(--md-sys-color-surface-container-highest)] hover:border-[var(--md-sys-color-primary)] active:scale-98 shadow-sm"
                    >
                        <div className="flex justify-between items-center mb-2">
                            <span className="text-title-sm font-black text-[var(--md-sys-color-on-surface)] uppercase tracking-wide">{opt.label}</span>
                            {typeof opt.value === 'number' && (
                                <div className="flex gap-1.5">
                                    {Array.from({ length: 3 }).map((_, i) => (
                                        <div key={i} className={`w-2 h-2 rounded-full ${i < opt.value ? 'bg-[var(--md-sys-color-primary)]' : 'bg-[var(--md-sys-color-surface-variant)]'}`} />
                                    ))}
                                </div>
                            )}
                        </div>
                        <p className="text-[var(--md-sys-color-on-surface-variant)] text-label-sm leading-snug opacity-60">{opt.detail}</p>
                    </button>
                ))}
            </div>
        </div>
    );

    if (embedded) {
        return (
            <div className="flex flex-col min-h-0 flex-1">
                <div className="flex justify-end px-6 pt-6 shrink-0">
                    <button onClick={onCancel} className="text-label-sm font-black text-[var(--md-sys-color-on-surface-variant)] uppercase tracking-widest opacity-40 hover:opacity-100 py-2">Omitir</button>
                </div>
                <div className="flex-1 overflow-y-auto px-6 py-6 custom-scrollbar">
                    <div className="flex gap-2 justify-center mb-8">
                        {steps.map((_, i) => (
                            <span key={i} className={`h-1.5 rounded-full transition-all duration-300 ${i <= step ? 'w-8 bg-[var(--md-sys-color-primary)]' : 'w-2 bg-[var(--md-sys-color-surface-variant)]'}`} />
                        ))}
                    </div>
                    {content}
                </div>
            </div>
        );
    }

    if (inline) {
        return (
            <div className="rounded-[2.5rem] border border-[var(--md-sys-color-outline-variant)]/50 bg-[var(--md-sys-color-surface-container-low)] overflow-hidden shadow-2xl scale-in-center">
                <div className="p-8">
                    <div className="flex gap-1.5 justify-center mb-6">
                        {steps.map((_, i) => (
                            <span key={i} className={`h-1 rounded-full transition-all duration-300 ${i <= step ? 'w-6 bg-[var(--md-sys-color-primary)]' : 'w-1.5 bg-[var(--md-sys-color-surface-variant)]'}`} />
                        ))}
                    </div>
                    {content}
                </div>
            </div>
        );
    }

    return (
        <div className="fixed inset-0 z-[300] flex flex-col bg-[var(--md-sys-color-background)] safe-area-root">
            <div className="flex-1 overflow-y-auto px-6 py-8 custom-scrollbar">
                <div className="flex justify-between items-center mb-8">
                    {step > 0 ? (
                        <button onClick={() => setStep(step - 1)} className="text-label-sm font-black text-[var(--md-sys-color-primary)] uppercase tracking-widest py-2 flex items-center gap-2 hover:translate-x-[-4px] transition-transform">
                            <ArrowLeftIcon size={18} />
                            Atrás
                        </button>
                    ) : (
                        <button onClick={onCancel} className="text-label-sm font-black text-[var(--md-sys-color-on-surface-variant)] uppercase tracking-widest opacity-40 hover:opacity-100 py-2">Cerrar</button>
                    )}
                    <div className="flex gap-2">
                        {steps.map((_, i) => (
                            <span key={i} className={`h-1.5 rounded-full transition-all duration-300 ${i <= step ? 'w-10 bg-[var(--md-sys-color-primary)]' : 'w-2 bg-[var(--md-sys-color-surface-variant)]'}`} />
                        ))}
                    </div>
                </div>
                {content}
            </div>
        </div>
    );
};

export default AthleteProfilingWizard;