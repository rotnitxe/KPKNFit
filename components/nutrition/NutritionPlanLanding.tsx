// components/nutrition/NutritionPlanLanding.tsx
// Landing previa al wizard: Hero + Cómo funciona + Beneficios + CTA

import React from 'react';
import Button from '../ui/Button';

interface NutritionPlanLandingProps {
    onStartWizard: () => void;
}

const HOW_IT_WORKS = [
    { step: 1, title: 'Objetivo y datos', desc: 'Define si buscas definición, mantención o superávit. Conecta con AUGE y añade tus datos corporales.' },
    { step: 2, title: 'Actividad y preferencias', desc: 'Nivel de actividad, condiciones metabólicas y preferencia dietética (omnívoro, vegetariano, vegano).' },
    { step: 3, title: 'Desglose y macros', desc: 'Revisa TMB, TDEE, ajusta macros y guarda. Todo editable después en el editor de plan.' },
];

const BENEFITS = [
    'Calorías ajustadas a tu objetivo',
    'Macros personalizados (P/C/G)',
    'Conectado con la batería AUGE',
    'Editor de plan para cambios futuros',
];

export const NutritionPlanLanding: React.FC<NutritionPlanLandingProps> = ({ onStartWizard }) => {
    return (
        <div className="min-h-screen bg-[#050505] flex flex-col px-4 py-8 pb-24">
            <div className="max-w-md mx-auto space-y-10">
                {/* Hero */}
                <div className="text-center space-y-3">
                    <h1 className="text-3xl font-black uppercase tracking-tighter text-white">
                        Crea tu plan de alimentación
                    </h1>
                    <p className="text-slate-400 text-sm">
                        Configura tus objetivos nutricionales en 3 pasos. Calorías y macros adaptados a ti.
                    </p>
                </div>

                {/* Cómo funciona */}
                <div>
                    <h2 className="text-xs font-black text-cyan-500 uppercase tracking-widest mb-4">Cómo funciona</h2>
                    <div className="space-y-4">
                        {HOW_IT_WORKS.map(({ step, title, desc }) => (
                            <div key={step} className="flex gap-4 p-4 rounded-xl bg-slate-900/50 border border-white/5">
                                <span className="flex-shrink-0 w-8 h-8 rounded-full bg-cyan-500/20 text-cyan-400 flex items-center justify-center text-sm font-black">
                                    {step}
                                </span>
                                <div>
                                    <h3 className="font-bold text-white text-sm">{title}</h3>
                                    <p className="text-slate-400 text-xs mt-0.5">{desc}</p>
                                </div>
                            </div>
                        ))}
                    </div>
                </div>

                {/* Beneficios */}
                <div>
                    <h2 className="text-xs font-black text-cyan-500 uppercase tracking-widest mb-4">Beneficios</h2>
                    <ul className="space-y-2">
                        {BENEFITS.map((b, i) => (
                            <li key={i} className="flex items-center gap-3 text-slate-300 text-sm">
                                <span className="w-1.5 h-1.5 rounded-full bg-cyan-500 flex-shrink-0" />
                                {b}
                            </li>
                        ))}
                    </ul>
                </div>

                {/* CTA */}
                <Button onClick={onStartWizard} className="w-full !py-4 text-base font-black">
                    Crear mi plan
                </Button>
            </div>
        </div>
    );
};
