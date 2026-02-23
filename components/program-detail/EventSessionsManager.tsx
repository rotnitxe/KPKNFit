import React, { useState } from 'react';
import { Program, Session } from '../../types';
import { PlusIcon, TrashIcon, CalendarIcon } from '../icons';

const EVENT_SESSION_TYPES = [
    { id: '1rm_test', label: 'Test 1RM' },
    { id: 'powerlifting_comp', label: 'Competición Powerlifting' },
    { id: 'bodybuilding_comp', label: 'Competición Culturismo' },
    { id: 'physical_test', label: 'Prueba Física' },
];

interface EventSessionsManagerProps {
    program: Program;
    isCyclic: boolean;
    onUpdateProgram: (program: Program) => void;
}

const EventSessionsManager: React.FC<EventSessionsManagerProps> = ({ program, isCyclic, onUpdateProgram }) => {
    const [expandedEventId, setExpandedEventId] = useState<string | null>(null);

    const events = program.events || [];

    const update = (fn: (p: Program) => void) => {
        const clone = JSON.parse(JSON.stringify(program));
        fn(clone);
        onUpdateProgram(clone);
    };

    const addSessionToEvent = (eventId: string) => {
        const type = EVENT_SESSION_TYPES[0].id;
        const newSession: Session = {
            id: crypto.randomUUID(),
            name: 'Nueva sesión especial',
            description: JSON.stringify({ type }),
            exercises: [],
            warmup: [],
        };
        update(p => {
            const ev = p.events?.find(e => (e.id || '') === eventId);
            if (ev) {
                ev.sessions = ev.sessions || [];
                ev.sessions.push(newSession);
            }
        });
    };

    const removeSessionFromEvent = (eventId: string, sessionId: string) => {
        if (!window.confirm('¿Eliminar esta sesión del evento?')) return;
        update(p => {
            const ev = p.events?.find(e => (e.id || '') === eventId);
            if (ev?.sessions) ev.sessions = ev.sessions.filter(s => s.id !== sessionId);
        });
    };

    if (events.length === 0) {
        return (
            <div className="rounded-xl border border-dashed border-white/10 bg-white/[0.02] p-6 text-center">
                <CalendarIcon size={24} className="text-[#48484A] mx-auto mb-2" />
                <p className="text-xs text-[#8E8E93] font-bold">Sin eventos programados</p>
                <p className="text-[10px] text-[#48484A] mt-1">Crea eventos en la sección de Estructura para asignar sesiones especiales.</p>
            </div>
        );
    }

    return (
        <div className="space-y-3">
            <h3 className="text-xs font-bold text-[#8E8E93] uppercase tracking-wide">Sesiones por evento</h3>
            <p className="text-[10px] text-[#48484A]">Asigna sesiones especiales (tests, competiciones) a cada evento o fecha clave.</p>
            {events.map((ev: any) => {
                const evId = ev.id || `ev-${ev.title}-${ev.calculatedWeek}`;
                const sessions = ev.sessions || [];
                const isExpanded = expandedEventId === evId;
                return (
                    <div key={evId} className="rounded-xl border border-white/5 bg-[#111] overflow-hidden">
                        <button
                            onClick={() => setExpandedEventId(isExpanded ? null : evId)}
                            className="w-full flex items-center justify-between px-4 py-3 text-left hover:bg-white/5 transition-colors"
                        >
                            <div className="flex items-center gap-2">
                                <div className="w-2 h-2 rounded-full bg-[#00F0FF] shrink-0" />
                                <span className="text-sm font-bold text-white truncate max-w-[180px]">{ev.title}</span>
                                <span className="text-[10px] text-[#48484A]">
                                    {isCyclic ? `Cada ${ev.repeatEveryXCycles} ciclos` : `Semana ${(ev.calculatedWeek || 0) + 1}`}
                                </span>
                            </div>
                            <span className="text-[10px] text-[#48484A]">{sessions.length} sesión(es)</span>
                        </button>
                        {isExpanded && (
                            <div className="border-t border-white/5 px-4 py-3 space-y-2">
                                {sessions.map(s => (
                                    <div key={s.id} className="flex items-center gap-2 px-3 py-2 rounded-lg bg-white/5">
                                        <span className="text-xs font-bold text-white flex-1 truncate">{s.name}</span>
                                        <button
                                            onClick={() => removeSessionFromEvent(evId, s.id)}
                                            className="p-1.5 text-[#48484A] hover:text-red-400 transition-colors"
                                        >
                                            <TrashIcon size={12} />
                                        </button>
                                    </div>
                                ))}
                                <button
                                    onClick={() => addSessionToEvent(evId)}
                                    className="w-full flex items-center justify-center gap-2 py-2 rounded-lg border border-dashed border-[#00F0FF]/30 text-[#00F0FF] text-xs font-bold hover:bg-[#00F0FF]/10 transition-colors"
                                >
                                    <PlusIcon size={12} /> Añadir sesión especial
                                </button>
                            </div>
                        )}
                    </div>
                );
            })}
        </div>
    );
};

export default EventSessionsManager;
