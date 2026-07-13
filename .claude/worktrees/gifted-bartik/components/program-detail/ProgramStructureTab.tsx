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
    onUpdateProgram
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
        <div className="flex flex-col gap-5 p-4 bg-[#FEF7FF] pb-[max(120px,calc(100px+env(safe-area-inset-bottom)))]">
            {DAYS_OF_WEEK.map((day) => {
                const daySessions = currentWeekSessions.filter(s => s.dayOfWeek === day.id);

                return (
                    <div
                        key={day.id}
                        onDragOver={handleSessionDragOver}
                        onDrop={(e) => handleSessionDrop(e, day.id)}
                        className="bg-white rounded-[28px] p-5 shadow-[0_1px_3px_rgba(0,0,0,0.06),0_4px_12px_rgba(0,0,0,0.04)] border border-[#ECE6F0]/50 transition-all duration-300"
                    >
                        {/* Day Header */}
                        <div className="flex items-center justify-between mb-5">
                            <h3 className="text-sm font-black uppercase tracking-[0.2em] text-[#1D1B20]/60">
                                {day.name}
                            </h3>
                            <button
                                onClick={onAddSession}
                                className="w-10 h-10 rounded-full flex items-center justify-center bg-[#ECE6F0] text-[#1D1B20] hover:bg-[#D3E3FD] transition-colors"
                            >
                                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><path d="M5 12h14" /><path d="M12 5v14" /></svg>
                            </button>
                        </div>

                        {/* Sessions List */}
                        <div className="flex flex-col gap-3">
                            {daySessions.map(session => (
                                <div
                                    key={session.id}
                                    draggable
                                    onDragStart={(e) => handleSessionDragStart(e, session.id)}
                                    className={`
                                        flex items-center justify-between p-4 rounded-2xl border border-[#ECE6F0] bg-[#FEF7FF]/50 transition-all duration-300
                                        ${draggedSessionId === session.id ? 'opacity-30 scale-95 border-blue-500' : 'opacity-100 hover:border-[#D3E3FD] hover:bg-white'}
                                        cursor-grab active:cursor-grabbing
                                    `}
                                >
                                    <div className="flex items-center gap-4">
                                        <div className="w-10 h-10 rounded-xl flex items-center justify-center bg-white shadow-sm border border-[#ECE6F0] text-[#004A77]">
                                            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="m6.5 6.5 11 11" /><path d="m21 21-1-1" /><path d="m3 3 1 1" /><path d="m18 22 4-4" /><path d="m2 6 4-4" /><path d="m3 10 7-7" /><path d="m14 21 7-7" /></svg>
                                        </div>
                                        <div className="flex flex-col">
                                            <h4 className="text-sm font-black text-[#1D1B20]">{session.name || "Sesión"}</h4>
                                            <div className="flex items-center gap-1.5 opacity-50 mt-0.5">
                                                <div className="w-1 h-1 rounded-full bg-current" />
                                                <p className="text-[10px] font-bold uppercase tracking-wider">{session.exercises?.length || 0} Ejercicios</p>
                                            </div>
                                        </div>
                                    </div>
                                    <div className="flex items-center gap-1">
                                        <button
                                            onClick={(e) => { e.stopPropagation(); onEditSession && onEditSession(session.id); }}
                                            className="w-10 h-10 rounded-full flex items-center justify-center text-[#1D1B20]/40 hover:text-blue-600 hover:bg-blue-50 transition-all"
                                        >
                                            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z" /><path d="m15 5 4 4" /></svg>
                                        </button>
                                        <button
                                            onClick={(e) => { e.stopPropagation(); setSessionToDelete(session.id); }}
                                            className="w-10 h-10 rounded-full flex items-center justify-center text-[#1D1B20]/40 hover:text-red-500 hover:bg-red-50 transition-all"
                                        >
                                            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M3 6h18" /><path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6" /><path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2" /><line x1="10" x2="10" y1="11" y2="17" /><line x1="14" x2="14" y1="11" y2="17" /></svg>
                                        </button>
                                    </div>
                                </div>
                            ))}
                            {daySessions.length === 0 && (
                                <div className="py-6 text-center border-2 border-dashed border-[#ECE6F0] rounded-2xl flex flex-col items-center gap-2">
                                    <p className="text-[10px] font-bold uppercase tracking-widest text-[#1D1B20]/30 italic">Descanso o sin programar</p>
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
