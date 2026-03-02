// components/home/HomeHeroSection.tsx
// Hero contextual: saludo + badge fecha + CTA según contexto

import React from 'react';
import { Program, WorkoutLog } from '../../types';
import { CheckCircleIcon, LinkIcon } from '../icons';

export type HeroContext =
    | { type: 'no_program' }
    | { type: 'has_program'; sessionToday: boolean; trainedToday: boolean; program: Program }
    | { type: 'rest_day'; program: Program }
    | { type: 'no_session'; program: Program };

interface HomeHeroSectionProps {
    context: HeroContext;
    firstVisitToday: boolean;
    todayLog?: WorkoutLog;
    onNavigateProgram: () => void;
    onNavigateProgramEditor: () => void;
    onShareLog?: (log: WorkoutLog) => void;
}

const getGreeting = (): string => {
    const h = new Date().getHours();
    if (h < 12) return 'Buenos días';
    if (h < 19) return 'Buenas tardes';
    return 'Buenas noches';
};

export const HomeHeroSection: React.FC<HomeHeroSectionProps> = ({
    context,
    firstVisitToday,
    todayLog,
    onNavigateProgram,
    onNavigateProgramEditor,
    onShareLog,
}) => {
    const greeting = getGreeting();
    const dateStr = new Date().toLocaleDateString('es-ES', { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' });

    return (
        <div className="animate-fade-in">
            <div className="flex flex-col gap-3 mb-4">
                <div className="flex items-center justify-between gap-2">
                    <h1 className={`font-black text-white tracking-tight ${firstVisitToday ? 'text-2xl' : 'text-xl'}`}>
                        {greeting}
                    </h1>
                    <div className="inline-flex items-center gap-2 px-2.5 py-1 rounded-full bg-white/5 border border-white/10 shrink-0">
                        <span className="text-[9px] font-mono text-zinc-400">
                            {dateStr}
                        </span>
                    </div>
                </div>

                {context.type === 'no_program' && (
                    <p className="text-sm text-zinc-500">
                        Crea tu programa para comenzar a entrenar.
                    </p>
                )}

                {context.type === 'has_program' && context.sessionToday && context.trainedToday && todayLog && (
                    <div className="flex items-center gap-2 py-2">
                        <div className="inline-flex items-center gap-2 px-3 py-1.5 bg-emerald-950/40 border border-emerald-500/30 rounded-lg">
                            <CheckCircleIcon size={16} className="text-emerald-400" />
                            <span className="text-sm font-bold text-emerald-400">Entrenamiento completado</span>
                        </div>
                        {onShareLog && (
                            <button
                                onClick={() => onShareLog(todayLog)}
                                className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-white/10 border border-white/10 rounded-lg text-zinc-400 hover:text-white hover:border-white/20 transition-colors text-xs font-bold"
                            >
                                <LinkIcon size={14} /> Compartir
                            </button>
                        )}
                    </div>
                )}

                {context.type === 'has_program' && context.sessionToday && !context.trainedToday && (
                    <p className="text-sm text-zinc-500">
                        Sesión programada para hoy.
                    </p>
                )}

                {context.type === 'rest_day' && (
                    <p className="text-sm text-zinc-500">
                        Día de descanso.
                    </p>
                )}

                {context.type === 'no_session' && (
                    <p className="text-sm text-zinc-500">
                        Sin sesión programada hoy.
                    </p>
                )}
            </div>
        </div>
    );
};
