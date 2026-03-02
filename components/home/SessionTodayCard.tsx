// components/home/SessionTodayCard.tsx
// Cockpit-style session card for Home Hub

import React from 'react';
import { Program, Session, WorkoutLog } from '../../types';
import { PlayIcon, CheckCircleIcon, PencilIcon, SettingsIcon, RefreshCwIcon, LinkIcon, PauseIcon } from '../icons';
import { CaupolicanIcon } from '../CaupolicanIcon';
import { getSessionDayLabel } from '../../utils/sessionDayLabel';
import { useAppState } from '../../contexts/AppContext';
import { SessionReadinessBlock } from './SessionReadinessBlock';

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
    ongoingWorkout?: { session: Session; programId: string; isPaused?: boolean } | null;
    onStartWorkout: (session: Session, program: Program, _?: unknown, ctx?: { macroIndex: number; mesoIndex: number; weekId: string }) => void;
    onResumeWorkout?: () => void;
    onEditSession: (programId: string, macroIndex: number, mesoIndex: number, weekId: string, sessionId: string) => void;
    onViewProgram: (programId: string) => void;
    onOpenStartWorkoutModal: () => void;
    onShareLog?: (log: WorkoutLog) => void;
    /** Variante "continuation": híbrido tarjeta/continuación visual, con demarcación clara vs hero */
    variant?: 'card' | 'continuation';
}

export const SessionTodayCard: React.FC<SessionTodayCardProps> = ({
    programName,
    programId,
    todaySessions,
    ongoingWorkout,
    onStartWorkout,
    onResumeWorkout,
    onEditSession,
    onViewProgram,
    onOpenStartWorkoutModal,
    onShareLog,
    variant = 'card',
}) => {
    const { exerciseList } = useAppState();
    const hasSessions = todaySessions.length > 0;
    const firstSession = todaySessions[0];
    const hasPendingSession = hasSessions && todaySessions.some(ts => !ts.isCompleted && !(ongoingWorkout && ongoingWorkout.programId === ts.program.id && ongoingWorkout.session.id === ts.session.id));

    const dayLabel = hasSessions && firstSession
        ? getSessionDayLabel(firstSession.session, exerciseList ?? [])
        : 'DESCANSO';

    const isContinuation = variant === 'continuation';

    return (
        <div
            className={`flex flex-col ${
                isContinuation
                    ? 'bg-transparent'
                    : 'min-h-[180px] overflow-hidden bg-[#0a0a0a] border border-white/10 rounded-2xl'
            }`}
        >
            {/* Header — demarcación: HERO = baterías, aquí = sesión */}
            <div className={`flex justify-between items-center flex-wrap gap-2 ${isContinuation ? 'px-0 py-2' : 'px-5 py-3 border-b border-white/5'}`}>
                <div className="flex items-center gap-2">
                    {isContinuation && (
                        <span className="text-[9px] font-black text-zinc-500 uppercase tracking-[0.2em]">
                            Sesión de hoy
                        </span>
                    )}
                    <span className="text-[9px] font-black text-zinc-500 uppercase tracking-[0.25em]">
                        {programName}
                    </span>
                </div>
                <div className="flex items-center gap-2">
                    {hasPendingSession && (
                        <span className="text-[8px] font-mono text-amber-400 bg-amber-950/30 border border-amber-500/20 rounded px-2 py-0.5">
                            Sesión pendiente
                        </span>
                    )}
                    <span className="text-[9px] font-mono text-zinc-600">
                        {dayLabel}
                    </span>
                </div>
            </div>

            {/* Content */}
            <div className={`flex-1 flex flex-col ${isContinuation ? 'py-0' : 'p-5'}`}>
                {hasSessions ? (
                    <div className={isContinuation ? 'space-y-3' : 'space-y-4'}>
                        {todaySessions.map((ts, idx) => {
                            const isOngoing = ongoingWorkout && ongoingWorkout.programId === ts.program.id && ongoingWorkout.session.id === ts.session.id;
                            const isPaused = isOngoing && ongoingWorkout?.isPaused;
                            const sessionBlockClass = isContinuation
                                ? 'py-4 transition-all ' + (ts.isCompleted ? 'text-zinc-500' : '')
                                : `rounded-xl border p-5 transition-all ${ts.isCompleted ? 'bg-emerald-950/30 border-emerald-500/30' : isOngoing ? 'bg-amber-950/30 border-amber-500/30' : 'bg-white/5 border-white/10 hover:border-white/20'}`;
                            return (
                            <div key={idx} className={sessionBlockClass}>
                                {!ts.isCompleted && (
                                    <SessionReadinessBlock session={ts.session} compact={isContinuation} />
                                )}
                                {ts.isCompleted && (
                                    <div className="flex flex-wrap items-center gap-2 mb-2">
                                        <div className="inline-flex items-center gap-2 px-2.5 py-1 bg-emerald-500/20 text-emerald-400 text-[8px] font-black uppercase tracking-widest rounded-full border border-emerald-500/30">
                                            <CheckCircleIcon size={10} /> Completada
                                        </div>
                                        {ts.log && onShareLog && (
                                            <button onClick={() => onShareLog(ts.log!)} className="inline-flex items-center gap-1.5 px-2.5 py-1 bg-white/10 text-white/90 text-[8px] font-black uppercase tracking-widest rounded-full border border-white/20 hover:bg-white/20 transition-colors">
                                                <LinkIcon size={10} /> Compartir
                                            </button>
                                        )}
                                    </div>
                                )}
                                {isOngoing && !ts.isCompleted && (
                                    <div className="flex flex-wrap items-center gap-2 mb-2">
                                        <div className={`inline-flex items-center gap-2 px-2.5 py-1 text-[8px] font-black uppercase tracking-widest rounded-full border ${isPaused ? 'bg-amber-500/20 text-amber-400 border-amber-500/30' : 'bg-cyan-500/20 text-cyan-400 border-cyan-500/30'}`}>
                                            {isPaused ? <PauseIcon size={10} /> : <PlayIcon size={10} />}
                                            {isPaused ? 'Pausada' : 'En curso'}
                                        </div>
                                    </div>
                                )}
                                <h3 className={`font-black uppercase tracking-tight ${isContinuation ? 'text-lg mb-3' : 'text-xl mb-4'} ${ts.isCompleted ? 'text-zinc-500' : 'text-white'}`}>
                                    {ts.session.name}
                                </h3>
                                {!ts.isCompleted && (
                                    isOngoing && onResumeWorkout ? (
                                        <button
                                            onClick={onResumeWorkout}
                                            className={`w-full bg-cyan-500 text-white font-black uppercase text-[10px] tracking-[0.2em] rounded-xl hover:bg-cyan-400 active:scale-[0.98] transition-all flex justify-center items-center gap-2 border border-cyan-400/30 ${isContinuation ? 'py-3' : 'py-3.5'}`}
                                        >
                                            <PlayIcon size={14} fill="currentColor" /> Reanudar
                                        </button>
                                    ) : (
                                        <button
                                            onClick={() => onStartWorkout(ts.session, ts.program, undefined, ts.location)}
                                            className={`w-full bg-white text-black font-black uppercase text-[10px] tracking-[0.2em] rounded-xl hover:bg-zinc-200 active:scale-[0.98] transition-all flex justify-center items-center gap-2 ${isContinuation ? 'py-3' : 'py-3.5'}`}
                                        >
                                            <PlayIcon size={14} fill="currentColor" /> Iniciar
                                        </button>
                                    )
                                )}
                            </div>
                        );})}

                        {/* Quick actions */}
                        <div className={`flex gap-2 ${isContinuation ? 'pt-3' : 'pt-2'}`}>
                            {firstSession && (
                                <button
                                    onClick={() => onEditSession(firstSession.program.id, firstSession.location.macroIndex, firstSession.location.mesoIndex, firstSession.location.weekId, firstSession.session.id)}
                                    className={`flex-1 py-2.5 rounded-lg text-[9px] font-black uppercase tracking-widest transition-colors flex items-center justify-center gap-1.5 ${isContinuation ? 'text-zinc-500 hover:text-white' : 'text-zinc-400 border border-white/10 bg-black/50 hover:bg-white/10 hover:text-white'}`}
                                >
                                    <PencilIcon size={12} /> Editar
                                </button>
                            )}
                            {firstSession && (
                                <button
                                    onClick={() => onViewProgram(firstSession.program.id)}
                                    className={`flex-1 py-2.5 rounded-lg text-[9px] font-black uppercase tracking-widest transition-colors flex items-center justify-center gap-1.5 ${isContinuation ? 'text-zinc-500 hover:text-white' : 'text-zinc-400 border border-white/10 bg-black/50 hover:bg-white/10 hover:text-white'}`}
                                >
                                    <SettingsIcon size={12} /> Programa
                                </button>
                            )}
                            <button
                                onClick={onOpenStartWorkoutModal}
                                className={`flex-1 py-2.5 rounded-lg text-[9px] font-black uppercase tracking-widest transition-colors flex items-center justify-center gap-1.5 ${isContinuation ? 'text-zinc-500 hover:text-white' : 'text-zinc-400 border border-white/10 bg-black/50 hover:bg-white/10 hover:text-white'}`}
                            >
                                <RefreshCwIcon size={12} /> Cambiar
                            </button>
                        </div>
                    </div>
                ) : (
                    <div className={`flex flex-col items-center justify-center text-center ${isContinuation ? 'py-6' : 'flex-1 py-8'}`}>
                        <CaupolicanIcon size={isContinuation ? 44 : 56} color="#333" />
                        <p className={`font-bold text-zinc-400 ${isContinuation ? 'text-xs mt-3' : 'text-sm mt-4'}`}>Recuperación Activa</p>
                        <p className="text-[9px] text-zinc-600 font-black uppercase tracking-widest mt-1">
                            El músculo crece cuando descansas.
                        </p>
                        <div className="flex gap-2 mt-4">
                            <button
                                onClick={() => onViewProgram(programId)}
                                className={`py-2 px-4 rounded-lg text-[9px] font-black text-zinc-500 uppercase tracking-widest hover:text-white transition-colors ${isContinuation ? '' : 'border border-white/10'}`}
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
