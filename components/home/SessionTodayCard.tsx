// components/home/SessionTodayCard.tsx
// Material 3 — Figma-exact session card
// ALWAYS renders full layout (image + name + play) even without active sessions

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

    // ─── No sessions: ALWAYS show full Figma layout with placeholder ────────
    if (!hasSessions) {
        return (
            <div className="self-stretch inline-flex flex-col justify-start items-start">
                {/* Figma: image area h-28, rounded-3xl */}
                <div className="self-stretch h-28 px-4 py-2 inline-flex justify-start items-start gap-2 overflow-hidden">
                    <div className="flex-1 self-stretch bg-blend-luminosity rounded-3xl inline-flex flex-col justify-center items-center gap-1"
                        style={{ backgroundColor: 'var(--md-sys-color-surface-container-high, #E8E0DE)' }}>
                        <CaupolicanIcon size={32} className="text-[var(--md-sys-color-on-surface-variant)] opacity-40" />
                    </div>
                </div>
                {/* Session name + program name + play button */}
                <div className="self-stretch px-4 inline-flex justify-start items-start gap-2">
                    <div className="flex-1 flex justify-start items-start">
                        <div className="flex-1 inline-flex flex-col justify-start items-start">
                            <div className="self-stretch h-6 text-[var(--md-sys-color-on-surface)] text-base font-normal font-['Roboto'] leading-6 tracking-wide">
                                Día de descanso
                            </div>
                            <div className="self-stretch text-[var(--md-sys-color-on-surface-variant)] text-sm font-normal font-['Roboto'] leading-5 tracking-tight line-clamp-2">
                                Configura un programa para ver tu sesión
                            </div>
                        </div>
                    </div>
                    {/* Play button area (disabled) */}
                    <div className="w-20 flex justify-start items-start">
                        <button onClick={() => onViewProgram(programId)}
                            className="w-10 h-10 rounded-full bg-[var(--md-sys-color-surface-container-high,#E8E0DE)] flex items-center justify-center">
                            <PlayIcon size={16} className="text-[var(--md-sys-color-on-surface-variant)]" />
                        </button>
                    </div>
                </div>
            </div>
        );
    }

    // ─── Has sessions: render each ──────────────────────────────────────────
    return (
        <div className="space-y-3">
            {todaySessions.map((ts, idx) => {
                const isOngoing = ongoingWorkout && ongoingWorkout.programId === ts.program.id && ongoingWorkout.session.id === ts.session.id;
                const isPaused = isOngoing && ongoingWorkout?.isPaused;

                return (
                    <div key={idx} className="self-stretch inline-flex flex-col justify-start items-start">
                        {/* ── Figma: image area h-28 px-4 py-2, rounded-3xl ── */}
                        <div className="self-stretch h-28 px-4 py-2 inline-flex justify-start items-start gap-2 overflow-hidden">
                            <div className="flex-1 self-stretch bg-blend-luminosity rounded-3xl inline-flex flex-col justify-center items-center gap-1 relative"
                                style={{ backgroundColor: 'var(--md-sys-color-surface-container-high, #E8E0DE)' }}>
                                {/* Abstract shapes */}
                                <svg width="120" height="80" viewBox="0 0 120 80" fill="none" className="opacity-30">
                                    <path d="M60 10l20 35-20 35-20-35z" fill="#B0A8A4" />
                                    <circle cx="40" cy="55" r="18" fill="#A09892" />
                                    <rect x="65" y="40" width="30" height="30" rx="6" fill="#A09892" />
                                </svg>
                                {/* Status badges */}
                                {ts.isCompleted && (
                                    <div className="absolute top-3 left-3 flex items-center gap-1.5 px-2.5 py-1 bg-emerald-500/90 text-white text-xs font-medium uppercase tracking-wider rounded-full font-['Roboto']">
                                        <CheckCircleIcon size={10} /> Completada
                                    </div>
                                )}
                                {isOngoing && !ts.isCompleted && (
                                    <div className={`absolute top-3 left-3 flex items-center gap-1.5 px-2.5 py-1 text-white text-xs font-medium uppercase tracking-wider rounded-full font-['Roboto'] ${isPaused ? 'bg-amber-500/90' : 'bg-sky-500/90'}`}>
                                        {isPaused ? <PauseIcon size={10} /> : <PlayIcon size={10} />}
                                        {isPaused ? 'Pausada' : 'En curso'}
                                    </div>
                                )}
                            </div>
                        </div>

                        {/* ── Figma: session info + play button ── */}
                        <div className="self-stretch px-4 inline-flex justify-start items-start gap-2">
                            <div className="flex-1 flex justify-start items-start">
                                <div className="flex-1 inline-flex flex-col justify-start items-start">
                                    <div className="self-stretch h-6 text-[var(--md-sys-color-on-surface)] text-base font-normal font-['Roboto'] leading-6 tracking-wide truncate">
                                        {ts.session.name}
                                    </div>
                                    <div className="self-stretch text-[var(--md-sys-color-on-surface-variant)] text-sm font-normal font-['Roboto'] leading-5 tracking-tight line-clamp-2">
                                        {programName}
                                    </div>
                                </div>
                            </div>
                            {/* Play button */}
                            <div className="w-20 flex justify-start items-start">
                                {!ts.isCompleted && (
                                    isOngoing && onResumeWorkout ? (
                                        <button onClick={onResumeWorkout}
                                            className="w-10 h-10 rounded-full bg-[var(--md-sys-color-primary)] text-[var(--md-sys-color-on-primary)] flex items-center justify-center shadow-[0_2px_8px_rgba(0,0,0,0.15)] active:scale-95 transition-all">
                                            <PlayIcon size={20} fill="currentColor" />
                                        </button>
                                    ) : (
                                        <button onClick={() => onStartWorkout(ts.session, ts.program, undefined, ts.location)}
                                            className="w-10 h-10 rounded-full bg-[var(--md-sys-color-on-surface)] text-[var(--md-sys-color-surface)] flex items-center justify-center shadow-[0_2px_8px_rgba(0,0,0,0.15)] active:scale-95 transition-all">
                                            <PlayIcon size={20} fill="currentColor" />
                                        </button>
                                    )
                                )}
                                {ts.isCompleted && ts.log && onShareLog && (
                                    <button onClick={() => onShareLog(ts.log!)}
                                        className="w-10 h-10 rounded-full bg-[var(--md-sys-color-surface-container-high,#E8E0DE)] text-[var(--md-sys-color-on-surface-variant)] flex items-center justify-center active:scale-95 transition-all">
                                        <LinkIcon size={16} />
                                    </button>
                                )}
                            </div>
                        </div>

                        {/* ── Readiness block ── */}
                        {!ts.isCompleted && (
                            <div className="self-stretch px-4 mt-2">
                                <SessionReadinessBlock session={ts.session} compact />
                            </div>
                        )}
                    </div>
                );
            })}

            {/* Quick actions - Figma style */}
            <div className="flex gap-2 px-4 pt-1">
                {firstSession && (
                    <button
                        onClick={() => onEditSession(firstSession.program.id, firstSession.location.macroIndex, firstSession.location.mesoIndex, firstSession.location.weekId, firstSession.session.id)}
                        className="flex-1 py-2.5 rounded-full text-xs font-medium text-[var(--md-sys-color-on-surface-variant)] hover:bg-[var(--md-sys-color-surface-container-high,#E8E0DE)] transition-colors flex items-center justify-center gap-1.5 font-['Roboto'] tracking-wide"
                    >
                        <PencilIcon size={12} /> Editar
                    </button>
                )}
                {firstSession && (
                    <button
                        onClick={() => onViewProgram(firstSession.program.id)}
                        className="flex-1 py-2.5 rounded-full text-xs font-medium text-[var(--md-sys-color-on-surface-variant)] hover:bg-[var(--md-sys-color-surface-container-high,#E8E0DE)] transition-colors flex items-center justify-center gap-1.5 font-['Roboto'] tracking-wide"
                    >
                        <SettingsIcon size={12} /> Programa
                    </button>
                )}
                <button
                    onClick={onOpenStartWorkoutModal}
                    className="flex-1 py-2.5 rounded-full text-xs font-medium text-[var(--md-sys-color-on-surface-variant)] hover:bg-[var(--md-sys-color-surface-container-high,#E8E0DE)] transition-colors flex items-center justify-center gap-1.5 font-['Roboto'] tracking-wide"
                >
                    <RefreshCwIcon size={12} /> Cambiar
                </button>
            </div>
        </div>
    );
};
