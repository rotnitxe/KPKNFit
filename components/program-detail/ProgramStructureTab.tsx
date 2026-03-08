import React, { useState, useCallback } from 'react';
import { Program, Session } from '../../types';

interface ProgramStructureTabProps {
    program: Program;
    history: any[];
    onEditSession?: (sessionId: string, intermediateWeek?: any) => void;
    onAddSession?: () => void;
    onDeleteSession?: (sessionId: string, programId: string, macroIndex: number, mesoIndex: number, weekId: string) => void;
    currentWeekId?: string;
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
    currentWeekId
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
    const [daysOrder, setDaysOrder] = useState(DAYS_OF_WEEK);
    const [draggedDayId, setDraggedDayId] = useState<number | null>(null);
    const [sessionToDelete, setSessionToDelete] = useState<string | null>(null);

    const handleDragStart = (e: React.DragEvent, id: number) => {
        setDraggedDayId(id);
        // Required for Firefox
        if (e.dataTransfer) {
            e.dataTransfer.effectAllowed = 'move';
            e.dataTransfer.setData('text/plain', id.toString());
        }
    };

    const handleDragOver = (e: React.DragEvent) => {
        e.preventDefault(); // allow drop
    };

    const handleDrop = (e: React.DragEvent, targetId: number) => {
        e.preventDefault();
        if (draggedDayId === null || draggedDayId === targetId) return;

        setDaysOrder(prev => {
            const dragIndex = prev.findIndex(d => d.id === draggedDayId);
            const dropIndex = prev.findIndex(d => d.id === targetId);
            const newOrder = [...prev];
            const [draggedItem] = newOrder.splice(dragIndex, 1);
            newOrder.splice(dropIndex, 0, draggedItem);
            return newOrder;
        });
        setDraggedDayId(null);
    };

    return (
        <div className="flex flex-col gap-4 p-4">
            {daysOrder.map((day) => {
                const daySessions = currentWeekSessions.filter(s => s.dayOfWeek === day.id);

                return (
                    <div
                        key={day.id}
                        draggable
                        onDragStart={(e) => handleDragStart(e, day.id)}
                        onDragOver={handleDragOver}
                        onDrop={(e) => handleDrop(e, day.id)}
                        className={`
                            rounded-xl p-4 transition-all duration-200
                            ${draggedDayId === day.id ? 'opacity-50 scale-[0.98]' : 'opacity-100'}
                        `}
                        style={{ backgroundColor: 'var(--md-sys-color-surface-container)' }}
                    >
                        {/* Day Header */}
                        <div className="flex items-center justify-between mb-4">
                            <div className="flex items-center gap-3">
                                {/* Drag Handle Icon */}
                                <div className="cursor-grab active:cursor-grabbing text-[var(--md-sys-color-on-surface-variant)] opacity-50 hover:opacity-100">
                                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><circle cx="9" cy="12" r="1" /><circle cx="9" cy="5" r="1" /><circle cx="9" cy="19" r="1" /><circle cx="15" cy="12" r="1" /><circle cx="15" cy="5" r="1" /><circle cx="15" cy="19" r="1" /></svg>
                                </div>
                                <h3 className="text-title-md font-bold text-[var(--md-sys-color-on-surface)]">
                                    {day.name}
                                </h3>
                            </div>
                            <button
                                onClick={onAddSession}
                                className="flex items-center gap-2 px-3 py-1.5 rounded-lg text-sm font-medium transition-colors bg-[var(--md-sys-color-secondary-container)] text-[var(--md-sys-color-on-secondary-container)] hover:brightness-110"
                            >
                                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M5 12h14" /><path d="M12 5v14" /></svg>
                                Nuevo
                            </button>
                        </div>

                        {/* Sessions List */}
                        {daySessions.length === 0 ? (
                            <div className="py-4 text-center border-2 border-dashed border-[var(--md-sys-color-outline-variant)] rounded-lg">
                                <p className="text-body-sm text-[var(--md-sys-color-on-surface-variant)]">Sin sesiones para este día</p>
                            </div>
                        ) : (
                            <div className="flex flex-col gap-2">
                                {daySessions.map(session => (
                                    <div
                                        key={session.id}
                                        className="flex items-center justify-between p-3 rounded-lg border border-[var(--md-sys-color-outline-variant)] bg-[var(--md-sys-color-surface)]"
                                    >
                                        <div className="flex items-center gap-3">
                                            <div className="w-10 h-10 rounded-full flex items-center justify-center bg-[var(--md-sys-color-secondary-container)] text-[var(--md-sys-color-on-secondary-container)]">
                                                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="m6.5 6.5 11 11" /><path d="m21 21-1-1" /><path d="m3 3 1 1" /><path d="m18 22 4-4" /><path d="m2 6 4-4" /><path d="m3 10 7-7" /><path d="m14 21 7-7" /></svg>
                                            </div>
                                            <div>
                                                <h4 className="text-body-lg font-bold text-[var(--md-sys-color-on-surface)]">{session.name || "Sesión"}</h4>
                                                <p className="text-body-sm text-[var(--md-sys-color-on-surface-variant)]">{session.exercises?.length || 0} ejercicios</p>
                                            </div>
                                        </div>
                                        <div className="flex items-center gap-1">
                                            <button
                                                onClick={() => onEditSession && onEditSession(session.id)}
                                                className="w-8 h-8 rounded-full flex items-center justify-center text-[var(--md-sys-color-on-surface-variant)] hover:bg-[var(--md-sys-color-surface-variant)]"
                                            >
                                                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z" /><path d="m15 5 4 4" /></svg>
                                            </button>
                                            <button
                                                onClick={() => setSessionToDelete(session.id)}
                                                className="w-8 h-8 rounded-full flex items-center justify-center text-[var(--md-sys-color-error)] hover:bg-[var(--md-sys-color-error-container)]"
                                            >
                                                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M3 6h18" /><path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6" /><path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2" /><line x1="10" x2="10" y1="11" y2="17" /><line x1="14" x2="14" y1="11" y2="17" /></svg>
                                            </button>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        )}
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
