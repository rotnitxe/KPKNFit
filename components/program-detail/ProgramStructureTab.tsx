import React, { useState, useCallback } from 'react';
import { Program, Session } from '../../types';

interface ProgramStructureTabProps {
    program: Program;
    history: any[];
    onEditSession?: (sessionId: string, intermediateWeek?: any) => void;
    onAddSession?: () => void;
    onDeleteSession?: (sessionId: string, programId: string, macroIndex: number, mesoIndex: number, weekId: string) => void;
    currentWeekId?: string;
    onUpdateProgram?: (program: Program) => void;
    onEditMacrocycle?: () => void;
    onChangeSplit?: () => void;
}

const DAYS_OF_WEEK = [
    { id: 1, name: 'Lunes' },
    { id: 2, name: 'Martes' },
    { id: 3, name: 'Miércoles' },
    { id: 4, name: 'Jueves' },
    { id: 5, name: 'Viernes' },
    { id: 6, name: 'Sábado' },
    { id: 0, name: 'Domingo' }
];

export const ProgramStructureTab: React.FC<ProgramStructureTabProps> = ({
    program,
    onEditSession,
    onAddSession,
    onDeleteSession,
    currentWeekId,
    onUpdateProgram,
    onEditMacrocycle,
    onChangeSplit
}) => {
    // We'll flatten the structure to just find the current week's sessions for now
    const currentWeekSessions = React.useMemo(() => {
        let sessions: Session[] = [];
        let foundWeekId = currentWeekId;

        program.macrocycles.forEach((macro, mIdx) => {
            (macro.blocks || []).forEach((block, bIdx) => {
                block.mesocycles.forEach((meso, meIdx) => {
                    meso.weeks.forEach((week) => {
                        // If no week selected, pick the first one
                        if (!foundWeekId) foundWeekId = week.id;
                        if (week.id === foundWeekId) {
                            sessions = week.sessions;
                        }
                    });
                });
            });
        });
        return sessions;
    }, [program, currentWeekId]);

    // Local state for drag and drop
    const [draggedSessionId, setDraggedSessionId] = useState<string | null>(null);
    const [sessionToDelete, setSessionToDelete] = useState<string | null>(null);

    const handleSessionDragStart = (e: React.DragEvent, id: string) => {
        setDraggedSessionId(id);
        if (e.dataTransfer) {
            e.dataTransfer.effectAllowed = 'move';
            e.dataTransfer.setData('text/plain', id);
        }
    };

    const handleSessionDragOver = (e: React.DragEvent) => {
        e.preventDefault();
        e.dataTransfer.dropEffect = 'move';
    };

    const handleSessionDrop = (e: React.DragEvent, targetDayId: number) => {
        e.preventDefault();
        if (!draggedSessionId) return;

        // Move session to new day
        if (onUpdateProgram) {
            const updatedProgram = JSON.parse(JSON.stringify(program));
            let found = false;
            updatedProgram.macrocycles.forEach((macro: any) => {
                macro.blocks.forEach((block: any) => {
                    block.mesocycles.forEach((meso: any) => {
                        meso.weeks.forEach((week: any) => {
                            const session = week.sessions.find((s: any) => s.id === draggedSessionId);
                            if (session) {
                                session.dayOfWeek = targetDayId;
                                found = true;
                            }
                        });
                    });
                });
            });
            if (found) {
                onUpdateProgram(updatedProgram);
            }
        }
        setDraggedSessionId(null);
    };

    return (
        <div className="flex flex-col gap-6 p-4 bg-[#FEF7FF] pb-[max(120px,calc(100px+env(safe-area-inset-bottom)))]">
            {/* ── Structure Toolbox (Horizontal Layout) ── */}
            <div className="flex flex-col gap-3 px-2">
                <div className="flex items-center gap-3">
                    <div className="h-[1px] flex-1 bg-black/[0.05]" />
                    <span className="text-[10px] font-black uppercase tracking-[0.2em] text-zinc-400">Herramientas</span>
                    <div className="h-[1px] flex-1 bg-black/[0.05]" />
                </div>
                <div className="flex gap-3">
                    <button
                        onClick={onEditMacrocycle}
                        className="flex-1 h-14 rounded-[20px] bg-gradient-to-br from-white/80 to-white/40 backdrop-blur-xl border border-white/60 shadow-[0_8px_16px_rgba(0,0,0,0.03)] flex flex-col justify-center items-center gap-0.5 text-[9px] font-black uppercase tracking-[0.15em] text-zinc-600 hover:text-black active:scale-95 transition-all"
                    >
                        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" className="opacity-60"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" /><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" /></svg>
                        Estructura
                    </button>
                    <button
                        onClick={onChangeSplit}
                        className="flex-1 h-14 rounded-[20px] bg-gradient-to-br from-zinc-800 to-black border border-zinc-700 shadow-[0_8px_16px_rgba(0,0,0,0.15)] flex flex-col justify-center items-center gap-0.5 text-[9px] font-black uppercase tracking-[0.15em] text-white/80 hover:text-white active:scale-95 transition-all"
                    >
                        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" className="opacity-80"><path d="m16 16 3-8 3 8c-.87.65-1.92 1-3 1s-2.13-.35-3-1Z" /><path d="m2 16 3-8 3 8c-.87.65-1.92 1-3 1s-2.13-.35-3-1Z" /><path d="M7 21h10" /><path d="M12 3v18" /><path d="M3 7h2c2 0 5-1 7-2 2 1 5 2 7 2h2" /></svg>
                        Split
                    </button>
                </div>
            </div>

            {DAYS_OF_WEEK.map((day) => {
                const daySessions = currentWeekSessions.filter(s => s.dayOfWeek === day.id);

                return (
                    <div
                        key={day.id}
                        onDragOver={handleSessionDragOver}
                        onDrop={(e) => handleSessionDrop(e, day.id)}
                        className="group"
                    >
                        {/* Day Header */}
                        <div className="flex items-center justify-between px-2 mb-2">
                            <h3 className="text-[11px] font-black uppercase tracking-[0.25em] text-[#1D1B20]/40">
                                {day.name}
                            </h3>
                            <button
                                onClick={onAddSession}
                                className="w-6 h-6 rounded-full flex items-center justify-center bg-zinc-100 text-zinc-400 hover:bg-blue-50 hover:text-blue-500 transition-all opacity-0 group-hover:opacity-100"
                            >
                                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round"><path d="M12 5v14M5 12h14" /></svg>
                            </button>
                        </div>

                        {/* Sessions List */}
                        <div className="flex flex-col gap-2.5">
                            {daySessions.map(session => (
                                <div
                                    key={session.id}
                                    draggable
                                    onDragStart={(e) => handleSessionDragStart(e, session.id)}
                                    className={`
                                                relative overflow-hidden rounded-[24px] p-4 shadow-sm border transition-all duration-300
                                                ${draggedSessionId === session.id ? 'opacity-30 scale-95 border-blue-500' : 'opacity-100 hover:scale-[1.01] hover:shadow-md'}
                                                ${session.exercises?.length ? 'bg-gradient-to-br from-white to-zinc-50 border-white/80' : 'bg-white/40 backdrop-blur-md border-black/[0.02]'}
                                                cursor-grab active:cursor-grabbing
                                            `}
                                >
                                    {/* Subtle Gradient Glow for Sessions with Exercises */}
                                    {session.exercises?.length > 0 && (
                                        <div className="absolute inset-0 bg-gradient-to-br from-blue-500/[0.02] to-transparent pointer-events-none" />
                                    )}

                                    <div className="flex items-center justify-between relative z-10">
                                        <div className="flex items-center gap-3">
                                            {/* Icon with Dynamic Background */}
                                            <div className={`w-10 h-10 rounded-xl flex items-center justify-center shadow-sm border transition-colors ${session.exercises?.length ? 'bg-gradient-to-br from-blue-500 to-blue-600 text-white border-blue-400' : 'bg-zinc-100 text-zinc-400 border-zinc-200'}`}>
                                                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><path d="m6.5 6.5 11 11" /><path d="m21 21-1-1" /><path d="m3 3 1 1" /><path d="m18 22 4-4" /><path d="m2 6 4-4" /><path d="m3 10 7-7" /><path d="m14 21 7-7" /></svg>
                                            </div>
                                            <div className="flex flex-col">
                                                <h4 className="text-sm font-black text-[#1D1B20] tracking-tight">{session.name || "Sesión"}</h4>
                                                <div className="flex items-center gap-2 mt-0.5">
                                                    <span className={`px-2 py-0.5 rounded-full text-[8.5px] font-black uppercase tracking-wider ${session.exercises?.length ? 'bg-blue-100/50 text-blue-700' : 'bg-zinc-100 text-zinc-500'}`}>
                                                        {session.exercises?.length || 0} Ejercicios
                                                    </span>
                                                </div>
                                            </div>
                                        </div>

                                        <div className="flex items-center gap-1">
                                            <button
                                                onClick={(e) => { e.stopPropagation(); onEditSession && onEditSession(session.id); }}
                                                className="w-8 h-8 rounded-full flex items-center justify-center text-zinc-400 hover:text-blue-600 hover:bg-blue-50 transition-all active:scale-90"
                                            >
                                                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z" /><path d="m15 5 4 4" /></svg>
                                            </button>
                                            <button
                                                onClick={(e) => { e.stopPropagation(); setSessionToDelete(session.id); }}
                                                className="w-8 h-8 rounded-full flex items-center justify-center text-zinc-400 hover:text-red-500 hover:bg-red-50 transition-all active:scale-90"
                                            >
                                                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><path d="M3 6h18" /><path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6" /><path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2" /><line x1="10" x2="10" y1="11" y2="17" /><line x1="14" x2="14" y1="11" y2="17" /></svg>
                                            </button>
                                        </div>
                                    </div>
                                </div>
                            ))}
                            {daySessions.length === 0 && (
                                <div
                                    onClick={onAddSession}
                                    className="py-4 rounded-[20px] border-2 border-dashed border-zinc-200/50 bg-white/30 flex flex-col items-center justify-center cursor-pointer hover:bg-white/50 hover:border-zinc-300 transition-all"
                                >
                                    <p className="text-[9px] font-black uppercase tracking-[0.2em] text-zinc-400">Descanso</p>
                                </div>
                            )}
                        </div>
                    </div>
                );
            })}

            {/* M3 Delete Confirmation Dialog */}
            {sessionToDelete && (
                <div className="fixed inset-0 z-[300] bg-[var(--md-sys-color-scrim)]/50 backdrop-blur-sm flex items-center justify-center p-4">
                    <div className="bg-[var(--md-sys-color-surface)] border border-[var(--md-sys-color-outline-variant)] w-full max-w-sm rounded-[28px] p-6 shadow-2xl relative">
                        <div className="flex flex-col gap-4">
                            <div className="flex flex-col gap-4">
                                <div className="w-12 h-12 rounded-full flex items-center justify-center bg-[var(--md-sys-color-error-container)] text-[var(--md-sys-color-on-error-container)] mx-auto">
                                    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M3 6h18" /><path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6" /><path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2" /><line x1="10" x2="10" y1="11" y2="17" /><line x1="14" x2="14" y1="11" y2="17" /></svg>
                                </div>
                                <div className="text-center">
                                    <h2 className="text-headline-sm font-bold text-[var(--md-sys-color-on-surface)] mb-2">¿Eliminar sesión?</h2>
                                    <p className="text-body-md text-[var(--md-sys-color-on-surface-variant)]">Esta acción no se puede deshacer. Los datos guardados de esta sesión se perderán.</p>
                                </div>
                            </div>
                            <div className="flex items-center justify-end gap-2 mt-2">
                                <button
                                    onClick={() => setSessionToDelete(null)}
                                    className="px-6 py-2.5 rounded-full text-sm font-medium transition-colors text-[var(--md-sys-color-primary)] hover:bg-[var(--md-sys-color-primary)]/10"
                                >
                                    Cancelar
                                </button>
                                <button
                                    onClick={() => {
                                        if (sessionToDelete && onDeleteSession) {
                                            onDeleteSession(sessionToDelete, program.id, 0, 0, currentWeekId || '');
                                        }
                                        setSessionToDelete(null);
                                    }}
                                    className="px-6 py-2.5 rounded-full text-sm font-medium transition-colors bg-[var(--md-sys-color-error)] text-[var(--md-sys-color-on-error)] hover:brightness-110"
                                >
                                    Eliminar
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};

export default ProgramStructureTab;
