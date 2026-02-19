import React, { useState } from 'react';
import { AthleteProfileScore } from '../types';
import { ChevronRightIcon, XIcon, ArrowLeftIcon, ActivityIcon, ZapIcon, MoveIcon, DumbbellIcon, TargetIcon } from './icons'; 

interface AthleteProfilingWizardProps {
    onComplete: (score: AthleteProfileScore) => void;
    onCancel: () => void;
}

// Aseguramos que los IDs coincidan exactamente con las llaves de options
type QuestionStep = 'preference' | 'technique' | 'consistency' | 'strength' | 'mobility';

const AthleteProfilingWizard: React.FC<AthleteProfilingWizardProps> = ({ onComplete, onCancel }) => {
    const [step, setStep] = useState<number>(0);
    const [scores, setScores] = useState({
        preference: 'Bodybuilder' as 'Bodybuilder' | 'Powerbuilder' | 'Powerlifter',
        technique: 0 as 1 | 2 | 3,
        consistency: 0 as 1 | 2 | 3,
        strength: 0 as 1 | 2 | 3,
        mobility: 0 as 1 | 2 | 3,
    });

    // 1. DEFINICIÓN DE PASOS
    const steps: { id: QuestionStep; title: string; description: string; icon: any }[] = [
        { id: 'preference', title: 'Enfoque de Entrenamiento', description: 'Definirá la lógica de volumen e intensidad del programa.', icon: <TargetIcon size={24} /> },
        { id: 'technique', title: 'Dominio Técnico', description: 'Tu capacidad para mantener la ejecución bajo carga.', icon: <DumbbellIcon size={24} /> },
        { id: 'consistency', title: 'Historial Reciente', description: 'Tu regularidad entrenando en los últimos 6 meses.', icon: <ActivityIcon size={24} /> },
        { id: 'strength', title: 'Nivel de Fuerza', description: 'Referencia relativa a tu peso corporal (Ratio BW).', icon: <ZapIcon size={24} /> },
        { id: 'mobility', title: 'Salud Articular', description: 'Rango de movimiento y ausencia de molestias.', icon: <MoveIcon size={24} /> },
    ];

    // 2. OPCIONES (Deben coincidir con los IDs de arriba)
    const options: Record<string, { value: any; label: string; detail: string }[]> = {
        preference: [
            { 
                value: 'Bodybuilder', 
                label: 'Bodybuilder', 
                detail: 'Prioridad: Hipertrofia y Estética. Buscamos maximizar el desarrollo muscular visual.' 
            },
            { 
                value: 'Powerbuilder', 
                label: 'Powerbuilder', 
                detail: 'Híbrido: Fuerza + Hipertrofia. Base pesada en compuestos y accesorios estéticos.' 
            },
            { 
                value: 'Powerlifter', 
                label: 'Powerlifter', 
                detail: 'Prioridad: Rendimiento (1RM). Todo gira en torno a subir tus números en SBD.' 
            },
        ],
        technique: [
            { 
                value: 1, 
                label: 'En Desarrollo', 
                detail: 'Aún estoy aprendiendo los patrones de movimiento o corrigiendo errores básicos.' 
            },
            { 
                value: 2, 
                label: 'Consolidada', 
                detail: 'Ejecución segura y estable en la mayoría de los ejercicios.' 
            },
            { 
                value: 3, 
                label: 'Avanzada', 
                detail: 'Control motor total, incluso bajo fatiga o cargas máximas.' 
            },
        ],
        consistency: [
            { 
                value: 1, 
                label: 'Reinicio / Inicio', 
                detail: 'Retomando el entrenamiento o empezando desde cero.' 
            },
            { 
                value: 2, 
                label: 'Constante', 
                detail: 'Entreno regularmente (2-3 veces/semana) sin interrupciones mayores.' 
            },
            { 
                value: 3, 
                label: 'Disciplina Atlética', 
                detail: 'Entreno 4+ veces por semana como parte innegociable de mi rutina.' 
            },
        ],
        strength: [
            { 
                value: 1, 
                label: 'Base (< 1x Peso Corporal)', 
                detail: 'Aún construyendo mi fuerza base en los levantamientos principales.' 
            },
            { 
                value: 2, 
                label: 'Intermedio (1x - 1.5x Peso Corporal)', 
                detail: 'Puedo mover mi propio peso corporal en ejercicios como Sentadilla.' 
            },
            { 
                value: 3, 
                label: 'Avanzado (> 1.5x Peso Corporal)', 
                detail: 'Muevo cargas significativas (1.5x a 2x mi peso) con buena forma.' 
            },
        ],
        mobility: [
            { 
                value: 1, 
                label: 'Restringida', 
                detail: 'Noto rigidez o molestias que limitan algunos ejercicios.' 
            },
            { 
                value: 2, 
                label: 'Funcional', 
                detail: 'Rango de movimiento completo sin dolor en ejercicios estándar.' 
            },
            { 
                value: 3, 
                label: 'Óptima', 
                detail: 'Gran flexibilidad y control en rangos profundos.' 
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
        <div className="fixed inset-0 z-[300] bg-black flex flex-col font-sans animate-in fade-in duration-200">
            {/* Header: Diseño Limpio y Tech */}
            <div className="px-6 pt-12 pb-6 bg-black border-b border-white/10">
                <div className="flex justify-between items-center mb-8">
                    {step > 0 ? (
                        <button onClick={() => setStep(step - 1)} className="p-2 -ml-2 text-gray-500 hover:text-white transition-colors">
                            <ArrowLeftIcon size={20} />
                        </button>
                    ) : (
                        <button onClick={onCancel} className="p-2 -ml-2 text-gray-500 hover:text-white transition-colors">
                            <XIcon size={20} />
                        </button>
                    )}
                    {/* Barra de Progreso Sutil */}
                    <div className="flex gap-1.5">
                        {steps.map((_, i) => (
                            <div key={i} className={`h-1 rounded-full transition-all duration-300 ${i <= step ? 'w-8 bg-white' : 'w-2 bg-white/10'}`} />
                        ))}
                    </div>
                </div>
                
                <div className="space-y-3 max-w-lg mx-auto w-full">
                    <div className="flex items-center gap-3 mb-2">
                         <span className="text-white/80 p-2 bg-white/5 rounded-lg border border-white/5">{currentStepData.icon}</span>
                         <span className="text-[10px] font-black uppercase tracking-widest text-gray-500">Paso {step + 1}/{steps.length}</span>
                    </div>
                    <h2 className="text-3xl font-black text-white uppercase tracking-tight leading-none">{currentStepData.title}</h2>
                    <p className="text-sm text-gray-400 font-medium leading-relaxed">{currentStepData.description}</p>
                </div>
            </div>

            {/* Body */}
            <div className="flex-1 p-6 overflow-y-auto bg-[#050505] custom-scrollbar">
                <div className="flex flex-col gap-3 max-w-lg mx-auto">
                    {/* El .map ahora es seguro gracias al fallback '|| []' */}
                    {currentOptions.map((opt) => (
                        <button
                            key={opt.value}
                            onClick={() => handleOptionSelect(opt.value)}
                            className="group relative w-full text-left p-5 rounded-2xl border border-white/10 bg-[#111] hover:bg-white hover:border-white transition-all duration-200 active:scale-[0.98]"
                        >
                            <div className="flex justify-between items-center mb-1.5">
                                <span className="text-base font-black uppercase tracking-tight text-white group-hover:text-black transition-colors">
                                    {opt.label}
                                </span>
                                {typeof opt.value === 'number' && (
                                    <div className="flex gap-0.5">
                                        {Array.from({ length: 3 }).map((_, i) => (
                                            <div key={i} className={`w-1 h-3 rounded-full ${i < opt.value ? 'bg-white/40 group-hover:bg-black/40' : 'bg-white/5 group-hover:bg-black/5'}`} />
                                        ))}
                                    </div>
                                )}
                            </div>
                            <p className="text-xs text-gray-500 font-medium leading-relaxed group-hover:text-gray-600 transition-colors pr-6">
                                {opt.detail}
                            </p>
                            <div className="absolute right-5 top-1/2 -translate-y-1/2 opacity-0 group-hover:opacity-100 transition-all transform translate-x-2 group-hover:translate-x-0">
                                <ChevronRightIcon size={18} className="text-black" />
                            </div>
                        </button>
                    ))}
                </div>
            </div>
            
            <div className="p-4 bg-black border-t border-white/5 text-center">
                 <p className="text-[9px] text-gray-600 uppercase tracking-widest font-mono">KPKN Algorithm • Calibración</p>
            </div>
        </div>
    );
};

export default AthleteProfilingWizard;