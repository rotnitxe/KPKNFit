// components/home/SessionTodayCard.tsx
// Material 3 — Tarjeta de sesión siguiendo diseño Figma:
//   · Área gris con shapes abstractas (ilustración placeholder)
//   · Nombre de sesión + nombre de programa
//   · Play button circular negro a la derecha

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

    const dayLabel = hasSessions && firstSession
        ? getSessionDayLabel(firstSession.session, exerciseList ?? [])
        : 'DESCANSO';

    if (!hasSessions) {
        return (
            <div className="flex flex-col items-center py-8">
                <CaupolicanIcon size={48} color="#9E9E9E" />
                <p className="text-[14px] font-semibold text-[#49454F] mt-3">Día de Descanso</p>
                <p className="text-[11px] text-[#79747E] mt-1">El músculo crece cuando descansas</p>
                <button
                    onClick={() => onViewProgram(programId)}
                    className="mt-4 px-5 py-2 rounded-full text-[11px] font-bold text-[#1C1B1F] bg-[#E8E0DE] hover:bg-[#D9D1CE] transition-colors"
                >
                    Ver Programa
                </button>
            </div>
        );
    }

    return (
        <div className="space-y-3">
            {todaySessions.map((ts, idx) => {
                const isOngoing = ongoingWorkout && ongoingWorkout.programId === ts.program.id && ongoingWorkout.session.id === ts.session.id;
                const isPaused = isOngoing && ongoingWorkout?.isPaused;

                return (
                    <div key={idx} className="flex flex-col">
                        {/* ── Imagen/Ilustración abstracta (Figma: grey rounded area) ── */}
                        <div className="w-full h-[140px] bg-[#E8E0DE] rounded-[24px] flex items-center justify-center overflow-hidden relative">
                            {/* Abstract shapes como en el Figma */}
                            <svg width="120" height="80" viewBox="0 0 120 80" fill="none" className="opacity-40">
                                <path d="M60 10l20 35-20 35-20-35z" fill="#B0A8A4" />
                                <circle cx="40" cy="55" r="18" fill="#A09892" />
                                <rect x="65" y="40" width="30" height="30" rx="6" fill="#A09892" />
                            </svg>
                            {/* Status badges */}
                            {ts.isCompleted && (
                                <div className="absolute top-3 left-3 flex items-center gap-1.5 px-2.5 py-1 bg-emerald-500/90 text-white text-[9px] font-bold uppercase tracking-wider rounded-full">
                                    <CheckCircleIcon size={10} /> Completada
                                </div>
                            )}
                            {isOngoing && !ts.isCompleted && (
                                <div className={`absolute top-3 left-3 flex items-center gap-1.5 px-2.5 py-1 text-white text-[9px] font-bold uppercase tracking-wider rounded-full ${isPaused ? 'bg-amber-500/90' : 'bg-sky-500/90'}`}>
                                    {isPaused ? <PauseIcon size={10} /> : <PlayIcon size={10} />}
                                    {isPaused ? 'Pausada' : 'En curso'}
                                </div>
                            )}
                        </div>

                        {/* ── Session info + Play Button ── */}
                        <div className="flex items-center justify-between mt-2.5 px-0.5">
                            <div className="flex-1 min-w-0">
                                <h3 className="text-[16px] font-bold text-[#1C1B1F] truncate">
                                    {ts.session.name}
                                </h3>
                                <p className="text-[13px] text-[#79747E]">{programName}</p>
                            </div>
                            {!ts.isCompleted && (
                                isOngoing && onResumeWorkout ? (
                                    <button
                                        onClick={onResumeWorkout}
                                        className="w-12 h-12 flex items-center justify-center rounded-full bg-[#1C1B1F] text-white shadow-[0_2px_8px_rgba(0,0,0,0.15)] hover:bg-[#333] active:scale-95 transition-all flex-shrink-0 ml-3"
                                    >
                                        <PlayIcon size={20} fill="currentColor" />
                                    </button>
                                ) : (
                                    <button
                                        onClick={() => onStartWorkout(ts.session, ts.program, undefined, ts.location)}
                                        className="w-12 h-12 flex items-center justify-center rounded-full bg-[#1C1B1F] text-white shadow-[0_2px_8px_rgba(0,0,0,0.15)] hover:bg-[#333] active:scale-95 transition-all flex-shrink-0 ml-3"
                                    >
                                        <PlayIcon size={20} fill="currentColor" />
                                    </button>
                                )
                            )}
                            {ts.isCompleted && ts.log && onShareLog && (
                                <button
                                    onClick={() => onShareLog(ts.log!)}
                                    className="w-10 h-10 flex items-center justify-center rounded-full bg-[#E8E0DE] text-[#49454F] hover:bg-[#D9D1CE] active:scale-95 transition-all flex-shrink-0 ml-3"
                                >
                                    <LinkIcon size={16} />
                                </button>
                            )}
                        </div>

                        {/* ── Readiness (if not completed) ── */}
                        {!ts.isCompleted && (
                            <div className="mt-3">
                                <SessionReadinessBlock session={ts.session} compact />
                            </div>
                        )}
                    </div>
                );
            })}

            {/* Quick actions */}
            <div className="flex gap-2 pt-1">
                {firstSession && (
                    <button
                        onClick={() => onEditSession(firstSession.program.id, firstSession.location.macroIndex, firstSession.location.mesoIndex, firstSession.location.weekId, firstSession.session.id)}
                        className="flex-1 py-2.5 rounded-full text-[10px] font-bold text-[#79747E] hover:text-[#1C1B1F] hover:bg-[#E8E0DE] transition-colors flex items-center justify-center gap-1.5"
                    >
                        <PencilIcon size={12} /> Editar
                    </button>
                )}
                {firstSession && (
                    <button
                        onClick={() => onViewProgram(firstSession.program.id)}
                        className="flex-1 py-2.5 rounded-full text-[10px] font-bold text-[#79747E] hover:text-[#1C1B1F] hover:bg-[#E8E0DE] transition-colors flex items-center justify-center gap-1.5"
                    >
                        <SettingsIcon size={12} /> Programa
                    </button>
                )}
                <button
                    onClick={onOpenStartWorkoutModal}
                    className="flex-1 py-2.5 rounded-full text-[10px] font-bold text-[#79747E] hover:text-[#1C1B1F] hover:bg-[#E8E0DE] transition-colors flex items-center justify-center gap-1.5"
                >
                    <RefreshCwIcon size={12} /> Cambiar
                </button>
            </div>
        </div>
    );
};
