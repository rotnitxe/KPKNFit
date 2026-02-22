import React, { useState } from 'react';
import { Program } from '../../types';
import { CalendarIcon, PlusIcon, TrashIcon, XIcon } from '../icons';

interface EventsSectionProps {
    program: Program;
    onUpdateProgram: (program: Program) => void;
}

const EventsSection: React.FC<EventsSectionProps> = ({ program, onUpdateProgram }) => {
    const [showForm, setShowForm] = useState(false);
    const [editingId, setEditingId] = useState<string | null>(null);
    const [formData, setFormData] = useState({ title: '', type: '1rm_test', repeatEveryXCycles: 4, calculatedWeek: 0 });

    const isCyclic = program.structure === 'simple' || (program.macrocycles.length === 1 && (program.macrocycles[0].blocks || []).length <= 1);
    const events = program.events || [];
    const totalWeeks = program.macrocycles.reduce((a, m) => a + (m.blocks || []).reduce((b, bl) => b + bl.mesocycles.reduce((c, me) => c + me.weeks.length, 0), 0), 0);

    const openForm = (ev?: any) => {
        if (ev) {
            setEditingId(ev.id);
            setFormData({ title: ev.title, type: ev.type || '1rm_test', repeatEveryXCycles: ev.repeatEveryXCycles || 4, calculatedWeek: ev.calculatedWeek || 0 });
        } else {
            setEditingId(null);
            setFormData({ title: '', type: '1rm_test', repeatEveryXCycles: 4, calculatedWeek: 0 });
        }
        setShowForm(true);
    };

    const handleSave = () => {
        if (!formData.title.trim()) return;
        const updated = JSON.parse(JSON.stringify(program));
        if (!updated.events) updated.events = [];
        const payload = {
            id: editingId || crypto.randomUUID(),
            title: formData.title, type: formData.type,
            date: new Date().toISOString(),
            calculatedWeek: isCyclic ? 0 : formData.calculatedWeek,
            repeatEveryXCycles: isCyclic ? formData.repeatEveryXCycles : undefined,
        };
        if (editingId) {
            const idx = updated.events.findIndex((e: any) => e.id === editingId);
            if (idx !== -1) updated.events[idx] = payload;
        } else {
            updated.events.push(payload);
        }
        onUpdateProgram(updated);
        setShowForm(false);
    };

    const handleDelete = (id: string) => {
        if (!window.confirm('¿Eliminar evento?')) return;
        const updated = JSON.parse(JSON.stringify(program));
        updated.events = updated.events.filter((e: any) => e.id !== id);
        onUpdateProgram(updated);
    };

    return (
        <div className="space-y-4">
            <div className="flex items-center justify-between">
                <h3 className="text-xs font-black text-white uppercase tracking-widest flex items-center gap-2">
                    <CalendarIcon size={14} className="text-zinc-400" /> Eventos
                </h3>
                <button
                    onClick={() => openForm()}
                    className="flex items-center gap-1 px-3 py-1.5 rounded-lg bg-zinc-900 border border-white/10 text-[8px] font-black text-zinc-400 uppercase tracking-widest hover:text-white transition-colors"
                >
                    <PlusIcon size={10} /> Nuevo
                </button>
            </div>

            {/* Timeline */}
            {!isCyclic && events.length > 0 && totalWeeks > 0 && (
                <div className="bg-zinc-950 border border-white/5 rounded-xl p-3">
                    <div className="relative w-full h-2 bg-zinc-900 rounded-full">
                        {events.map((e, i) => {
                            const pos = Math.min(100, ((e.calculatedWeek + 1) / totalWeeks) * 100);
                            return (
                                <div
                                    key={i}
                                    style={{ left: `${pos}%` }}
                                    className="absolute top-1/2 -translate-y-1/2 w-3 h-3 rounded-full bg-yellow-400 -translate-x-1/2 cursor-pointer hover:scale-150 transition-transform"
                                    onClick={() => openForm(e)}
                                    title={`${e.title} - S${e.calculatedWeek + 1}`}
                                />
                            );
                        })}
                    </div>
                </div>
            )}

            {/* Event list */}
            {events.length > 0 ? (
                <div className="space-y-2">
                    {events.map((ev, idx) => (
                        <div key={ev.id || idx} className="flex items-center gap-3 bg-zinc-950 border border-white/5 rounded-xl p-3 group cursor-pointer hover:border-white/15 transition-colors" onClick={() => openForm(ev)}>
                            <div className="w-2 h-2 bg-yellow-400 rounded-full shrink-0" />
                            <div className="flex-1 min-w-0">
                                <span className="text-[11px] font-bold text-white block truncate">{ev.title}</span>
                                <span className="text-[9px] text-zinc-500">
                                    {isCyclic ? `Cada ${ev.repeatEveryXCycles} ciclos` : `Semana ${ev.calculatedWeek + 1}`}
                                </span>
                            </div>
                            <button
                                onClick={e => { e.stopPropagation(); handleDelete(ev.id || ''); }}
                                className="p-1 text-zinc-700 hover:text-red-500 opacity-0 group-hover:opacity-100 transition-all"
                            >
                                <TrashIcon size={12} />
                            </button>
                        </div>
                    ))}
                </div>
            ) : (
                <div className="text-center py-8 border border-dashed border-white/10 rounded-xl">
                    <CalendarIcon size={20} className="text-zinc-700 mx-auto mb-1" />
                    <p className="text-[9px] text-zinc-500 font-bold">Sin eventos</p>
                </div>
            )}

            {/* Form modal */}
            {showForm && (
                <div className="bg-zinc-950 border border-white/10 rounded-2xl p-4 space-y-3 animate-fade-in">
                    <div className="flex items-center justify-between">
                        <h4 className="text-[10px] font-black text-white uppercase tracking-widest">{editingId ? 'Editar' : 'Nuevo'} Evento</h4>
                        <button onClick={() => setShowForm(false)} className="p-1 text-zinc-500 hover:text-white"><XIcon size={14} /></button>
                    </div>
                    <input
                        type="text"
                        value={formData.title}
                        onChange={e => setFormData({ ...formData, title: e.target.value })}
                        placeholder="Nombre del evento"
                        className="w-full bg-black border border-white/10 rounded-lg px-3 py-2 text-xs font-bold text-white placeholder-zinc-600 focus:ring-1 focus:ring-white/30"
                    />
                    {isCyclic ? (
                        <div className="flex items-center gap-2">
                            <span className="text-[9px] text-zinc-500 font-bold">Cada</span>
                            <input
                                type="number" min={1}
                                value={formData.repeatEveryXCycles}
                                onChange={e => setFormData({ ...formData, repeatEveryXCycles: parseInt(e.target.value) || 1 })}
                                className="w-16 bg-black border border-white/10 rounded-lg px-2 py-1.5 text-xs text-white text-center font-bold focus:ring-1 focus:ring-white/30"
                            />
                            <span className="text-[9px] text-zinc-500 font-bold">ciclos</span>
                        </div>
                    ) : (
                        <div className="flex items-center gap-2">
                            <span className="text-[9px] text-zinc-500 font-bold">Semana</span>
                            <input
                                type="number" min={1}
                                value={formData.calculatedWeek + 1}
                                onChange={e => setFormData({ ...formData, calculatedWeek: (parseInt(e.target.value) || 1) - 1 })}
                                className="w-16 bg-black border border-white/10 rounded-lg px-2 py-1.5 text-xs text-white text-center font-bold focus:ring-1 focus:ring-white/30"
                            />
                        </div>
                    )}
                    <div className="flex gap-2">
                        {editingId && (
                            <button onClick={() => { handleDelete(editingId); setShowForm(false); }} className="px-3 py-2 bg-red-500/10 border border-red-500/30 text-red-400 rounded-lg text-[9px] font-bold">
                                Eliminar
                            </button>
                        )}
                        <button onClick={handleSave} disabled={!formData.title.trim()} className="flex-1 py-2 bg-white text-black font-black text-[9px] uppercase tracking-widest rounded-lg hover:bg-zinc-200 disabled:opacity-30">
                            Guardar
                        </button>
                    </div>
                </div>
            )}
        </div>
    );
};

export default EventsSection;
