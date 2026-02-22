import React, { useState } from 'react';
import { PlusIcon, TrashIcon, CalendarIcon, XIcon } from '../icons';

interface WizardEvent {
    title: string;
    type: string;
    date: string;
    endDate?: string;
    calculatedWeek: number;
    createMacrocycle?: boolean;
    repeatEveryXCycles?: number;
}

interface CalendarStepProps {
    events: WizardEvent[];
    onAddEvent: (event: WizardEvent) => void;
    onRemoveEvent: (index: number) => void;
    isCyclic: boolean;
    blockNames?: string[];
    blockDurations?: number[];
    onChangeBlockDuration?: (index: number, duration: number) => void;
}

const CalendarStep: React.FC<CalendarStepProps> = ({
    events, onAddEvent, onRemoveEvent, isCyclic,
    blockNames, blockDurations, onChangeBlockDuration,
}) => {
    const [showForm, setShowForm] = useState(false);
    const [formData, setFormData] = useState({
        title: '',
        type: '1rm_test',
        repeatEveryXCycles: 4,
        calculatedWeek: 0,
    });

    const handleAdd = () => {
        if (!formData.title.trim()) return;
        onAddEvent({
            title: formData.title,
            type: formData.type,
            date: new Date().toISOString(),
            calculatedWeek: isCyclic ? 0 : formData.calculatedWeek,
            repeatEveryXCycles: isCyclic ? formData.repeatEveryXCycles : undefined,
        });
        setFormData({ title: '', type: '1rm_test', repeatEveryXCycles: 4, calculatedWeek: 0 });
        setShowForm(false);
    };

    const totalWeeks = blockDurations ? blockDurations.reduce((a, b) => a + b, 0) : 0;

    return (
        <div className="max-w-lg mx-auto py-6 px-4 space-y-6">
            <div className="text-center">
                <h3 className="text-sm font-black text-white uppercase tracking-widest">Calendario y Eventos</h3>
                <p className="text-[10px] text-zinc-500 mt-1">Configura hitos y duración de bloques</p>
            </div>

            {/* Block durations (complex only) */}
            {!isCyclic && blockNames && blockDurations && onChangeBlockDuration && (
                <div className="bg-zinc-950 border border-white/5 rounded-2xl p-4 space-y-3">
                    <h4 className="text-[9px] font-black text-zinc-400 uppercase tracking-widest">Duración de Bloques</h4>
                    <div className="flex gap-1 h-3 rounded-full overflow-hidden mb-3">
                        {blockNames.map((_, i) => {
                            const colors = ['bg-blue-500', 'bg-purple-500', 'bg-yellow-500', 'bg-emerald-500', 'bg-rose-500'];
                            const width = totalWeeks > 0 ? (blockDurations[i] / totalWeeks) * 100 : 0;
                            return <div key={i} className={`${colors[i % colors.length]} rounded-full`} style={{ width: `${width}%` }} />;
                        })}
                    </div>
                    {blockNames.map((name, i) => (
                        <div key={i} className="flex items-center justify-between">
                            <span className="text-[10px] font-bold text-zinc-300">{name}</span>
                            <div className="flex items-center gap-2">
                                <button
                                    onClick={() => onChangeBlockDuration(i, Math.max(1, blockDurations[i] - 1))}
                                    className="w-6 h-6 rounded bg-zinc-800 border border-white/10 text-zinc-400 hover:text-white flex items-center justify-center text-xs font-bold"
                                >
                                    -
                                </button>
                                <span className="text-xs font-black text-white w-8 text-center">{blockDurations[i]}</span>
                                <button
                                    onClick={() => onChangeBlockDuration(i, blockDurations[i] + 1)}
                                    className="w-6 h-6 rounded bg-zinc-800 border border-white/10 text-zinc-400 hover:text-white flex items-center justify-center text-xs font-bold"
                                >
                                    +
                                </button>
                                <span className="text-[9px] text-zinc-500">sem</span>
                            </div>
                        </div>
                    ))}
                    <div className="text-right">
                        <span className="text-[9px] text-zinc-500 font-bold">Total: {totalWeeks} semanas</span>
                    </div>
                </div>
            )}

            {/* Events list */}
            <div className="space-y-2">
                <div className="flex items-center justify-between">
                    <h4 className="text-[9px] font-black text-zinc-400 uppercase tracking-widest">Eventos Programados</h4>
                    <button
                        onClick={() => setShowForm(!showForm)}
                        className="flex items-center gap-1 px-3 py-1.5 rounded-lg bg-zinc-900 border border-white/10 text-[9px] font-black text-zinc-400 uppercase tracking-widest hover:text-white transition-colors"
                    >
                        <PlusIcon size={12} /> Nuevo
                    </button>
                </div>

                {events.length > 0 ? (
                    events.map((ev, idx) => (
                        <div key={idx} className="flex items-center gap-3 bg-zinc-950 border border-white/5 rounded-xl p-3 group">
                            <div className="w-2 h-2 bg-yellow-400 rounded-full shrink-0" />
                            <div className="flex-1 min-w-0">
                                <span className="text-[11px] font-bold text-white block truncate">{ev.title}</span>
                                <span className="text-[9px] text-zinc-500">
                                    {isCyclic ? `Cada ${ev.repeatEveryXCycles} ciclos` : `Semana ${ev.calculatedWeek + 1}`}
                                </span>
                            </div>
                            <button
                                onClick={() => onRemoveEvent(idx)}
                                className="p-1 text-zinc-700 hover:text-red-500 transition-colors opacity-0 group-hover:opacity-100"
                            >
                                <TrashIcon size={12} />
                            </button>
                        </div>
                    ))
                ) : (
                    <div className="text-center py-6 border border-dashed border-white/10 rounded-xl bg-black/20">
                        <CalendarIcon size={20} className="text-zinc-700 mx-auto mb-1" />
                        <p className="text-[9px] text-zinc-500 font-bold">Sin eventos</p>
                    </div>
                )}
            </div>

            {/* Add event form */}
            {showForm && (
                <div className="bg-zinc-950 border border-white/10 rounded-2xl p-4 space-y-3 animate-fade-in">
                    <div className="flex items-center justify-between">
                        <h4 className="text-[10px] font-black text-white uppercase tracking-widest">Nuevo Evento</h4>
                        <button onClick={() => setShowForm(false)} className="p-1 text-zinc-500 hover:text-white">
                            <XIcon size={14} />
                        </button>
                    </div>
                    <div>
                        <label className="text-[8px] font-black text-zinc-500 uppercase tracking-widest block mb-1">Nombre</label>
                        <input
                            type="text"
                            value={formData.title}
                            onChange={e => setFormData({ ...formData, title: e.target.value })}
                            placeholder="Ej: Test 1RM"
                            className="w-full bg-black border border-white/10 rounded-lg px-3 py-2 text-xs font-bold text-white placeholder-zinc-600 focus:ring-1 focus:ring-white/30"
                        />
                    </div>
                    {isCyclic ? (
                        <div>
                            <label className="text-[8px] font-black text-zinc-500 uppercase tracking-widest block mb-1">Cada cuántos ciclos</label>
                            <input
                                type="number"
                                min={1}
                                value={formData.repeatEveryXCycles}
                                onChange={e => setFormData({ ...formData, repeatEveryXCycles: parseInt(e.target.value) || 1 })}
                                className="w-24 bg-black border border-white/10 rounded-lg px-3 py-2 text-xs font-bold text-white text-center focus:ring-1 focus:ring-white/30"
                            />
                        </div>
                    ) : (
                        <div>
                            <label className="text-[8px] font-black text-zinc-500 uppercase tracking-widest block mb-1">Semana</label>
                            <input
                                type="number"
                                min={1}
                                value={formData.calculatedWeek + 1}
                                onChange={e => setFormData({ ...formData, calculatedWeek: (parseInt(e.target.value) || 1) - 1 })}
                                className="w-24 bg-black border border-white/10 rounded-lg px-3 py-2 text-xs font-bold text-white text-center focus:ring-1 focus:ring-white/30"
                            />
                        </div>
                    )}
                    <button
                        onClick={handleAdd}
                        disabled={!formData.title.trim()}
                        className="w-full py-2.5 bg-white text-black font-black text-[10px] uppercase tracking-widest rounded-xl hover:bg-zinc-200 transition-colors disabled:opacity-30 disabled:cursor-not-allowed"
                    >
                        Agregar Evento
                    </button>
                </div>
            )}
        </div>
    );
};

export default CalendarStep;
