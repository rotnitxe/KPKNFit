// components/home/SessionTodayCard.tsx
// Cockpit-style session card for Home Hub

import React from 'react';
import { Program, Session, WorkoutLog } from '../../types';
import { PlayIcon, CheckCircleIcon, PencilIcon, SettingsIcon, RefreshCwIcon, LinkIcon } from '../icons';
import { CaupolicanIcon } from '../CaupolicanIcon';

export interface TodaySessionItem {
    session: Session;
    program: Program;
    location: { macroIndex: number; mesoIndex: number; weekId: string };
    isCompleted: boolean;
    log?: WorkoutLog;
}

interface SessionTodayCardProps {
    programName: string;
    programId: string;
    todaySessions: TodaySessionItem[];
    onStartWorkout: (session: Session, program: Program, _?: unknown, ctx?: { macroIndex: number; mesoIndex: number; weekId: string }) => void;
    onEditSession: (programId: string, macroIndex: number, mesoIndex: number, weekId: string, sessionId: string) => void;
    onViewProgram: (programId: string) => void;
    onOpenStartWorkoutModal: () => void;
    onShareLog?: (log: WorkoutLog) => void;
}

export const SessionTodayCard: React.FC<SessionTodayCardProps> = ({
    programName,
    programId,
    todaySessions,
    onStartWorkout,
    onEditSession,
    onViewProgram,
    onOpenStartWorkoutModal,
    onShareLog,
}) => {
    const hasSessions = todaySessions.length > 0;
    const firstSession = todaySessions[0];

    return (
        <div className="bg-[#0a0a0a] border border-white/10 rounded-2xl overflow-hidden flex flex-col min-h-[200px]">
            {/* Header - Cockpit style */}
            <div className="px-5 py-3 border-b border-white/5 flex justify-between items-center">
                <span className="text-[9px] font-black text-zinc-500 uppercase tracking-[0.25em]">
                    {programName}
                </span>
                <span className="text-[9px] font-mono text-zinc-600">
                    {hasSessions ? 'DÍA DE ACCIÓN' : 'DESCANSO'}
                </span>
            </div>

            {/* Content */}
            <div className="flex-1 p-5 flex flex-col">
                {hasSessions ? (
                    <div className="space-y-4">
                        {todaySessions.map((ts, idx) => (
                            <div
                                key={idx}
                                className={`rounded-xl border p-5 transition-all ${
                                    ts.isCompleted
                                        ? 'bg-emerald-950/30 border-emerald-500/30'
                                        : 'bg-white/5 border-white/10 hover:border-white/20'
                                }`}
                            >
                                {ts.isCompleted && (
                                    <div className="flex flex-wrap items-center gap-2 mb-3">
                                        <div className="inline-flex items-center gap-2 px-2.5 py-1 bg-emerald-500/20 text-emerald-400 text-[8px] font-black uppercase tracking-widest rounded-full border border-emerald-500/30">
                                            <CheckCircleIcon size={10} /> Completada
                                        </div>
                                        {ts.log && onShareLog && (
                                            <button
                                                onClick={() => onShareLog(ts.log!)}
                                                className="inline-flex items-center gap-1.5 px-2.5 py-1 bg-white/10 text-white/90 text-[8px] font-black uppercase tracking-widest rounded-full border border-white/20 hover:bg-white/20 transition-colors"
                                            >
                                                <LinkIcon size={10} /> Compartir
                                            </button>
                                        )}
                                    </div>
                                )}
                                <h3 className={`font-black uppercase tracking-tight text-xl mb-4 ${ts.isCompleted ? 'text-zinc-500' : 'text-white'}`}>
                                    {ts.session.name}
                                </h3>
                                {!ts.isCompleted && (
                                    <button
                                        onClick={() => onStartWorkout(ts.session, ts.program, undefined, ts.location)}
                                        className="w-full py-3.5 bg-white text-black font-black uppercase text-[10px] tracking-[0.2em] rounded-xl hover:bg-zinc-200 active:scale-[0.98] transition-all flex justify-center items-center gap-2"
                                    >
                                        <PlayIcon size={14} fill="currentColor" /> Iniciar
                                    </button>
                                )}
                            </div>
                        ))}

                        {/* Quick actions */}
                        <div className="flex gap-2 pt-2">
                            {firstSession && (
                                <button
                                    onClick={() => onEditSession(
                                        firstSession.program.id,
                                        firstSession.location.macroIndex,
                                        firstSession.location.mesoIndex,
                                        firstSession.location.weekId,
                                        firstSession.session.id
                                    )}
                                    className="flex-1 py-2.5 rounded-lg border border-white/10 bg-black/50 text-[9px] font-black text-zinc-400 uppercase tracking-widest hover:bg-white/10 hover:text-white transition-colors flex items-center justify-center gap-1.5"
                                >
                                    <PencilIcon size={12} /> Editar
                                </button>
                            )}
                            {firstSession && (
                                <button
                                    onClick={() => onViewProgram(firstSession.program.id)}
                                    className="flex-1 py-2.5 rounded-lg border border-white/10 bg-black/50 text-[9px] font-black text-zinc-400 uppercase tracking-widest hover:bg-white/10 hover:text-white transition-colors flex items-center justify-center gap-1.5"
                                >
                                    <SettingsIcon size={12} /> Programa
                                </button>
                            )}
                            <button
                                onClick={onOpenStartWorkoutModal}
                                className="flex-1 py-2.5 rounded-lg border border-white/10 bg-black/50 text-[9px] font-black text-zinc-400 uppercase tracking-widest hover:bg-white/10 hover:text-white transition-colors flex items-center justify-center gap-1.5"
                            >
                                <RefreshCwIcon size={12} /> Cambiar
                            </button>
                        </div>
                    </div>
                ) : (
                    <div className="flex-1 flex flex-col items-center justify-center py-8 text-center">
                        <CaupolicanIcon size={56} color="#333" />
                        <p className="text-sm font-bold text-zinc-400 mt-4">Recuperación Activa</p>
                        <p className="text-[9px] text-zinc-600 font-black uppercase tracking-widest mt-1">
                            El músculo crece cuando descansas.
                        </p>
                        <div className="flex gap-2 mt-6">
                            <button
                                onClick={() => onViewProgram(programId)}
                                className="py-2 px-4 rounded-lg border border-white/10 text-[9px] font-black text-zinc-500 uppercase tracking-widest hover:text-white transition-colors"
                            >
                                Ver Programa
                            </button>
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
};
