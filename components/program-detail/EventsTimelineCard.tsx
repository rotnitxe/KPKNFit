import React, { useState } from 'react';
import { Program } from '../../types';
import { CalendarIcon, PlusIcon, TrashIcon, ActivityIcon, ChevronDownIcon } from '../icons';

interface EventsTimelineCardProps {
    program: Program;
    isCyclic: boolean;
    onUpdateProgram?: (program: Program) => void;
    onOpenEventModal: (eventData?: { id: string; title: string; repeatEveryXCycles: number; calculatedWeek: number; type: string }) => void;
    collapsed?: boolean;
    onToggleCollapse?: () => void;
}

const EventsTimelineCard: React.FC<EventsTimelineCardProps> = ({
    program, isCyclic, onUpdateProgram, onOpenEventModal,
    collapsed = false, onToggleCollapse,
}) => {
    const events = program.events || [];
    const totalWeeks = program.macrocycles[0]?.blocks?.flatMap(b => b.mesocycles?.flatMap(m => m.weeks || []) || [])?.length || 1;

    return (
        <div className="bg-zinc-900/50 border border-white/5 rounded-2xl overflow-hidden transition-all duration-300">
            <button onClick={onToggleCollapse} className="w-full flex items-center justify-between p-4 text-left">
                <div className="flex items-center gap-3">
                    <div className="w-8 h-8 rounded-lg bg-yellow-500/10 border border-yellow-500/20 flex items-center justify-center">
                        <CalendarIcon size={16} className="text-yellow-400" />
                    </div>
                    <div>
                        <h3 className="text-xs font-black text-white uppercase tracking-widest">Eventos</h3>
                        <p className="text-[9px] text-zinc-500 font-bold">
                            {events.length} {events.length === 1 ? 'evento' : 'eventos'}
                        </p>
                    </div>
                </div>
                <ChevronDownIcon size={16} className={`text-zinc-500 transition-transform duration-300 ${collapsed ? '' : 'rotate-180'}`} />
            </button>

            {!collapsed && (
                <div className="px-4 pb-4 space-y-4 animate-fade-in">
                    {/* Linear timeline */}
                    {!isCyclic && events.length > 0 && (
                        <div className="relative w-full h-1.5 bg-zinc-900 rounded-full border border-white/5">
                            {events.map((e, idx) => {
                                const pos = Math.min(100, ((e.calculatedWeek + 1) / Math.max(1, totalWeeks)) * 100);
                                return (
                                    <div
                                        key={`marker-${idx}`}
                                        style={{ left: `${pos}%` }}
                                        className="absolute top-1/2 -translate-y-1/2 w-3 h-3 rounded-full bg-yellow-400 shadow-[0_0_10px_rgba(250,204,21,0.6)] -translate-x-1/2 cursor-pointer hover:scale-150 transition-transform"
                                        onClick={() => onOpenEventModal({
                                            id: e.id || '', title: e.title,
                                            repeatEveryXCycles: e.repeatEveryXCycles || 1,
                                            calculatedWeek: e.calculatedWeek || 0,
                                            type: e.type || '1rm_test',
                                        })}
                                    />
                                );
                            })}
                        </div>
                    )}

                    {/* Cyclic diagram */}
                    {isCyclic && events.length > 0 && (
                        <div className="relative w-full flex justify-center py-4">
                            <div className="relative w-40 h-40 flex items-center justify-center">
                                <svg className="absolute inset-0 w-full h-full text-zinc-800" viewBox="0 0 100 100">
                                    <defs>
                                        <marker id="arrowHeadEvents" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
                                            <path d="M 0 0 L 10 5 L 0 10 z" className="fill-zinc-700" />
                                        </marker>
                                    </defs>
                                    <path d="M 50 5 A 45 45 0 0 1 95 50" fill="none" stroke="currentColor" strokeWidth="2" markerEnd="url(#arrowHeadEvents)" strokeLinecap="round" />
                                    <path d="M 95 50 A 45 45 0 0 1 50 95" fill="none" stroke="currentColor" strokeWidth="2" markerEnd="url(#arrowHeadEvents)" strokeLinecap="round" />
                                    <path d="M 50 95 A 45 45 0 0 1 5 50" fill="none" stroke="currentColor" strokeWidth="2" markerEnd="url(#arrowHeadEvents)" strokeLinecap="round" />
                                    <path d="M 5 50 A 45 45 0 0 1 50 5" fill="none" stroke="currentColor" strokeWidth="2" markerEnd="url(#arrowHeadEvents)" strokeLinecap="round" />
                                </svg>
                                <div className="absolute inset-0 flex items-center justify-center flex-col">
                                    <ActivityIcon size={16} className="text-zinc-700 mb-0.5" />
                                    <span className="text-[7px] font-black uppercase tracking-widest text-zinc-600 text-center">Bucle</span>
                                </div>
                                {events.map((ev, i) => {
                                    const angle = (i * (360 / events.length) - 90) * (Math.PI / 180);
                                    const radius = 65;
                                    const x = Math.cos(angle) * radius;
                                    const y = Math.sin(angle) * radius;
                                    return (
                                        <div
                                            key={ev.id || i}
                                            onClick={() => onOpenEventModal({
                                                id: ev.id || '', title: ev.title,
                                                repeatEveryXCycles: ev.repeatEveryXCycles || 1,
                                                calculatedWeek: ev.calculatedWeek || 0,
                                                type: ev.type || '1rm_test',
                                            })}
                                            className="absolute z-10 flex flex-col items-center cursor-pointer group"
                                            style={{ transform: `translate(${x}px, ${y}px)` }}
                                        >
                                            <div className="w-10 h-10 rounded-full bg-zinc-950 border border-yellow-500/50 flex items-center justify-center shadow-[0_0_10px_rgba(250,204,21,0.2)] group-hover:scale-110 transition-all">
                                                <div className="text-center">
                                                    <span className="block text-[7px] text-zinc-500 font-black uppercase leading-none">Cada</span>
                                                    <span className="block text-[10px] font-black text-yellow-400 leading-none mt-0.5">{ev.repeatEveryXCycles}</span>
                                                </div>
                                            </div>
                                            <div className="absolute top-11 bg-black px-2 py-1 rounded-md border border-white/10 min-w-max pointer-events-none">
                                                <span className="text-[8px] font-black text-white uppercase">{ev.title}</span>
                                            </div>
                                        </div>
                                    );
                                })}
                            </div>
                        </div>
                    )}

                    {/* Event cards */}
                    {events.length > 0 ? (
                        <div className="flex gap-2 overflow-x-auto no-scrollbar pb-1">
                            {events.map((e, idx) => (
                                <div key={idx} className="relative shrink-0 w-40 bg-zinc-950 border border-white/10 rounded-xl p-3 flex flex-col group hover:border-white/30 transition-colors">
                                    <button
                                        onClick={ev => {
                                            ev.stopPropagation();
                                            if (window.confirm(`¿Eliminar: ${e.title}?`)) {
                                                const updated = JSON.parse(JSON.stringify(program));
                                                updated.events = updated.events.filter((evnt: any) => evnt.id !== e.id);
                                                onUpdateProgram?.(updated);
                                            }
                                        }}
                                        className="absolute top-2 right-2 text-zinc-600 hover:text-red-500 transition-colors opacity-0 group-hover:opacity-100 p-0.5 bg-black/50 rounded-full"
                                    >
                                        <TrashIcon size={10} />
                                    </button>
                                    <span className="text-[7px] text-zinc-500 font-black uppercase tracking-widest mb-0.5">
                                        {isCyclic ? `Cada ${e.repeatEveryXCycles} ciclos` : 'Fecha Clave'}
                                    </span>
                                    <span className="text-[11px] font-bold text-white truncate pr-5">{e.title}</span>
                                    {!isCyclic && (
                                        <span className="text-[8px] text-zinc-400 mt-1 font-bold bg-white/5 self-start px-1.5 py-0.5 rounded">
                                            Semana {e.calculatedWeek + 1}
                                        </span>
                                    )}
                                </div>
                            ))}
                        </div>
                    ) : (
                        <div className="w-full text-center py-6 border border-dashed border-white/10 rounded-xl bg-black/20">
                            <p className="text-[9px] text-zinc-500 uppercase font-bold tracking-widest">Sin eventos programados.</p>
                        </div>
                    )}

                    <button
                        onClick={() => onOpenEventModal({ id: '', title: '', repeatEveryXCycles: isCyclic ? 4 : 1, calculatedWeek: 0, type: '1rm_test' })}
                        className="w-full py-2.5 bg-zinc-950 border border-dashed border-yellow-500/20 rounded-xl text-[9px] font-black uppercase tracking-widest text-yellow-600 hover:text-yellow-400 hover:border-yellow-500/40 transition-all flex items-center justify-center gap-1.5"
                    >
                        <PlusIcon size={12} /> Nuevo {isCyclic ? 'Evento' : 'Hito'}
                    </button>
                </div>
            )}
        </div>
    );
};

export default EventsTimelineCard;
