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
            <div className="self-stretch inline-flex flex-col justify-start items-start w-full">
                <div onClick={onOpenStartWorkoutModal} className="self-stretch w-full h-28 px-4 py-2 inline-flex justify-start items-start gap-2 overflow-hidden cursor-pointer">
                    <div className="flex-1 self-stretch bg-[#ECE6F0] rounded-[24px] inline-flex flex-col justify-center items-center gap-1">
                        <CaupolicanIcon size={32} className="text-[#49454F] opacity-40" />
                    </div>
                </div>
                <div onClick={onOpenStartWorkoutModal} className="self-stretch w-full px-4 py-2 inline-flex justify-between items-center cursor-pointer">
                    <div className="flex-1 inline-flex flex-col justify-start items-start">
                        <div className="self-stretch text-[#1D1B20] text-base font-normal font-['Roboto'] leading-6 tracking-wide">
                            Día de descanso
                        </div>
                        <div className="self-stretch text-[#49454F] text-sm font-normal font-['Roboto'] leading-5 tracking-tight line-clamp-2">
                            Configura un programa
                        </div>
                    </div>
                    <div className="w-12 h-12 flex justify-center items-center">
                        <div className="w-10 h-10 rounded-[100px] inline-flex flex-col justify-center items-center overflow-hidden bg-[#ECE6F0]">
                            <PlayIcon size={16} className="text-[#49454F] ml-1" />
                        </div>
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
                    <div key={idx} className="self-stretch inline-flex flex-col justify-start items-start w-full">
                        <div className="self-stretch w-full h-28 px-4 py-2 inline-flex justify-start items-start gap-2 overflow-hidden">
                            <div className="flex-1 self-stretch bg-[#ECE6F0] rounded-[24px] inline-flex flex-col justify-center items-center gap-1 relative">
                                <CaupolicanIcon size={32} className="text-[#49454F] opacity-40" />
                                {ts.isCompleted && (
                                    <div className="absolute top-3 left-3 flex items-center gap-1.5 px-2.5 py-1 bg-emerald-500/90 text-white text-xs font-medium uppercase tracking-wider rounded-full font-['Roboto']">
                                        <CheckCircleIcon size={10} /> Completada
                                    </div>
                                )}
                                {isOngoing && !ts.isCompleted && (
                                    <div className={`absolute top-3 left-3 flex items-center gap-1.5 px-2.5 py-1 text-white text-xs font-medium uppercase tracking-wider rounded-full font-['Roboto'] ${isPaused ? 'bg-amber-500/90' : 'bg-[#1D1B20]'}`}>
                                        {isPaused ? <PauseIcon size={10} /> : <PlayIcon size={10} />}
                                        {isPaused ? 'Pausada' : 'En curso'}
                                    </div>
                                )}
                            </div>
                        </div>

                        <div className="self-stretch w-full px-4 py-2 inline-flex justify-between items-center">
                            <div className="flex-1 flex flex-col justify-start items-start pr-2">
                                <div className="self-stretch text-[#1D1B20] text-base font-normal font-['Roboto'] leading-6 tracking-wide truncate">
                                    {ts.session.name}
                                </div>
                                <div className="self-stretch text-[#49454F] text-sm font-normal font-['Roboto'] leading-5 tracking-tight line-clamp-2">
                                    {programName}
                                </div>
                            </div>
                            <div className="w-12 h-12 flex justify-center items-center shrink-0">
                                {!ts.isCompleted && (
                                    isOngoing && onResumeWorkout ? (
                                        <button onClick={onResumeWorkout} className="w-10 h-10 rounded-[100px] flex items-center justify-center bg-[#1D1B20] text-white overflow-hidden transition-all active:scale-95">
                                            <PlayIcon size={16} className="ml-1" fill="currentColor" />
                                        </button>
                                    ) : (
                                        <button onClick={() => onStartWorkout(ts.session, ts.program, undefined, ts.location)} className="w-10 h-10 rounded-[100px] flex items-center justify-center bg-[#ECE6F0] text-[#1D1B20] border-2 border-[#1D1B20] overflow-hidden transition-all active:scale-95">
                                            <div className="w-0 h-0 border-t-[6px] border-t-transparent border-l-[10px] border-l-[#1D1B20] border-b-[6px] border-b-transparent ml-1" />
                                        </button>
                                    )
                                )}
                                {ts.isCompleted && ts.log && onShareLog && (
                                    <button onClick={() => onShareLog(ts.log!)} className="w-10 h-10 rounded-[100px] flex items-center justify-center bg-[#ECE6F0] text-[#49454F] overflow-hidden transition-all active:scale-95">
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
