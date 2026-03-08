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
        <div className="space-y-6 p-4 rounded-3xl">
            <div className="flex items-center justify-between">
                <h3 className="text-title-sm font-black text-[var(--md-sys-color-on-surface)] uppercase tracking-[0.2em] flex items-center gap-2">
                    <CalendarIcon size={16} className="text-[var(--md-sys-color-primary)]" /> Eventos
                </h3>
                <button
                    onClick={() => openForm()}
                    className="flex items-center gap-2 px-4 py-2 rounded-xl bg-[var(--md-sys-color-primary-container)] text-[var(--md-sys-color-on-primary-container)] text-label-small font-black uppercase tracking-widest hover:brightness-110 transition-all shadow-sm"
                >
                    <PlusIcon size={14} /> Nuevo Evento
                </button>
            </div>

            {/* Timeline */}
            {!isCyclic && events.length > 0 && totalWeeks > 0 && (
                <div className="bg-[var(--md-sys-color-surface-container-low)] border border-[var(--md-sys-color-outline-variant)] rounded-2xl p-6 shadow-sm">
                    <div className="flex items-center justify-between mb-4">
                        <span className="text-label-small font-black text-[var(--md-sys-color-on-surface-variant)] uppercase tracking-widest">Cronograma de Eventos</span>
                        <span className="text-label-small text-[var(--md-sys-color-on-surface)] font-bold uppercase">{totalWeeks} Semanas</span>
                    </div>
                    <div className="relative w-full h-3 bg-[var(--md-sys-color-surface-container-high)] rounded-full border border-[var(--md-sys-color-outline-variant)] px-1 overflow-visible">
                        {events.map((e, i) => {
                            const pos = Math.min(100, ((e.calculatedWeek + 1) / totalWeeks) * 100);
                            return (
                                <div
                                    key={i}
                                    style={{ left: `${pos}%` }}
                                    className="absolute top-1/2 -translate-y-1/2 w-4 h-4 rounded-full bg-[var(--md-sys-color-primary)] border-2 border-[var(--md-sys-color-surface)] shadow-[0_0_10px_rgba(var(--md-sys-color-primary-rgb),0.4)] -translate-x-1/2 cursor-pointer hover:scale-125 transition-all z-10"
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
                <div className="grid grid-cols-1 gap-2">
                    {events.map((ev, idx) => (
                        <div key={ev.id || idx} className="flex items-center gap-4 bg-[var(--md-sys-color-surface-container-low)] border border-[var(--md-sys-color-outline-variant)] rounded-2xl p-4 group cursor-pointer hover:border-[var(--md-sys-color-primary)]/30 hover:shadow-md transition-all" onClick={() => openForm(ev)}>
                            <div className="w-10 h-10 rounded-full bg-[var(--md-sys-color-primary-container)] flex items-center justify-center text-[var(--md-sys-color-on-primary-container)] shrink-0 shadow-inner">
                                <CalendarIcon size={18} />
                            </div>
                            <div className="flex-1 min-w-0">
                                <span className="text-label-large font-black text-[var(--md-sys-color-on-surface)] uppercase tracking-tight block truncate mb-1">{ev.title}</span>
                                <span className="text-[9px] font-bold text-[var(--md-sys-color-on-surface-variant)] uppercase tracking-widest">
                                    {isCyclic ? `FRECUENCIA: CADA ${ev.repeatEveryXCycles} CICLOS` : `PLANIFICADO PARA: SEMANA ${ev.calculatedWeek + 1}`}
                                </span>
                            </div>
                            <button
                                onClick={e => { e.stopPropagation(); handleDelete(ev.id || ''); }}
                                className="w-8 h-8 rounded-lg flex items-center justify-center text-[var(--md-sys-color-outline-variant)] hover:text-[var(--md-sys-color-error)] hover:bg-[var(--md-sys-color-error-container)]/30 transition-all"
                            >
                                <TrashIcon size={16} />
                            </button>
                        </div>
                    ))}
                </div>
            ) : (
                <div className="text-center py-12 border-2 border-dashed border-[var(--md-sys-color-outline-variant)] rounded-3xl bg-[var(--md-sys-color-surface-container-lowest)]">
                    <div className="w-16 h-16 bg-[var(--md-sys-color-surface-container-high)] rounded-full flex items-center justify-center mx-auto mb-4 text-[var(--md-sys-color-outline-variant)] shadow-sm border border-[var(--md-sys-color-outline-variant)]">
                        <CalendarIcon size={24} />
                    </div>
                    <p className="text-label-small text-[var(--md-sys-color-on-surface-variant)] font-black uppercase tracking-widest">Sin eventos planificados</p>
                </div>
            )}

            {/* Form modal */}
            {showForm && (
                <div className="bg-[var(--md-sys-color-surface)] border border-[var(--md-sys-color-outline-variant)] rounded-3xl p-6 space-y-4 shadow-2xl animate-in fade-in slide-in-from-bottom-4 duration-300 ring-4 ring-[var(--md-sys-color-primary)]/5">
                    <div className="flex items-center justify-between">
                        <h4 className="text-title-small font-black text-[var(--md-sys-color-on-surface)] uppercase tracking-widest">{editingId ? 'Editar' : 'configurar nuevo'} Evento</h4>
                        <button onClick={() => setShowForm(false)} className="w-8 h-8 flex items-center justify-center rounded-full text-[var(--md-sys-color-on-surface-variant)] hover:bg-[var(--md-sys-color-surface-variant)] transition-all"><XIcon size={18} /></button>
                    </div>

                    <div className="space-y-4">
                        <div className="space-y-1.5">
                            <label className="text-label-small font-black text-[var(--md-sys-color-on-surface-variant)] uppercase tracking-widest px-1">Título del Evento</label>
                            <input
                                type="text"
                                value={formData.title}
                                onChange={e => setFormData({ ...formData, title: e.target.value })}
                                placeholder="Ej: Toma de marcas 1RM"
                                className="w-full bg-[var(--md-sys-color-surface-container-high)] border border-[var(--md-sys-color-outline-variant)] rounded-xl px-4 py-3 text-body-medium font-black text-[var(--md-sys-color-on-surface)] uppercase placeholder-[var(--md-sys-color-outline-variant)] focus:ring-2 focus:ring-[var(--md-sys-color-primary)]/20 focus:border-[var(--md-sys-color-primary)] outline-none transition-all"
                            />
                        </div>

                        <div className="bg-[var(--md-sys-color-surface-container-low)] rounded-2xl p-4 border border-[var(--md-sys-color-outline-variant)]">
                            {isCyclic ? (
                                <div className="flex items-center justify-between">
                                    <span className="text-label-small font-black text-[var(--md-sys-color-on-surface-variant)] uppercase tracking-widest">Repetir cada</span>
                                    <div className="flex items-center gap-3">
                                        <input
                                            type="number" min={1}
                                            value={formData.repeatEveryXCycles}
                                            onChange={e => setFormData({ ...formData, repeatEveryXCycles: parseInt(e.target.value) || 1 })}
                                            className="w-20 bg-[var(--md-sys-color-surface)] border border-[var(--md-sys-color-outline-variant)] rounded-xl px-3 py-2 text-label-large text-[var(--md-sys-color-on-surface)] text-center font-black focus:ring-2 focus:ring-[var(--md-sys-color-primary)]/20 focus:border-[var(--md-sys-color-primary)] outline-none transition-all shadow-sm"
                                        />
                                        <span className="text-label-small font-black text-[var(--md-sys-color-on-surface-variant)] uppercase">Ciclos</span>
                                    </div>
                                </div>
                            ) : (
                                <div className="flex items-center justify-between">
                                    <span className="text-label-small font-black text-[var(--md-sys-color-on-surface-variant)] uppercase tracking-widest">Programar en</span>
                                    <div className="flex items-center gap-3">
                                        <span className="text-label-small font-black text-[var(--md-sys-color-on-surface-variant)] uppercase">Semana</span>
                                        <input
                                            type="number" min={1}
                                            value={formData.calculatedWeek + 1}
                                            onChange={e => setFormData({ ...formData, calculatedWeek: (parseInt(e.target.value) || 1) - 1 })}
                                            className="w-20 bg-[var(--md-sys-color-surface)] border border-[var(--md-sys-color-outline-variant)] rounded-xl px-3 py-2 text-label-large text-[var(--md-sys-color-on-surface)] text-center font-black focus:ring-2 focus:ring-[var(--md-sys-color-primary)]/20 focus:border-[var(--md-sys-color-primary)] outline-none transition-all shadow-sm"
                                        />
                                    </div>
                                </div>
                            )}
                        </div>
                    </div>

                    <div className="flex gap-3 pt-2">
                        {editingId && (
                            <button
                                onClick={() => { handleDelete(editingId); setShowForm(false); }}
                                className="px-5 py-3 text-[var(--md-sys-color-error)] hover:bg-[var(--md-sys-color-error-container)]/30 rounded-xl text-label-small font-black uppercase tracking-widest transition-all"
                            >
                                Eliminar
                            </button>
                        )}
                        <button
                            onClick={handleSave}
                            disabled={!formData.title.trim()}
                            className="flex-1 py-3 bg-[var(--md-sys-color-on-surface)] text-[var(--md-sys-color-surface)] font-black text-label-small uppercase tracking-[0.2em] rounded-xl hover:shadow-xl hover:scale-[1.02] transition-all active:scale-95 disabled:opacity-20 shadow-md"
                        >
                            Guardar Cambios
                        </button>
                    </div>
                </div>
            )}
        </div>
    );
};

export default EventsSection;
