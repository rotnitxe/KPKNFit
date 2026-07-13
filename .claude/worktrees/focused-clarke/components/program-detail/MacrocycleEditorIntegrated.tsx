import React, { useState, useMemo } from 'react';
import { Program, ProgramWeek, ProgramMesocycle, ProgramBlock } from '../../types';
import { PlusIcon, TrashIcon, EditIcon, ChevronDownIcon, ChevronUpIcon, CalendarIcon, ActivityIcon, TargetIcon, XIcon, CheckIcon, SaveIcon } from '../icons';
import { motion, AnimatePresence } from 'framer-motion';

interface MacrocycleEditorProps {
    program: Program;
    onUpdateProgram: (program: Program) => void;
    addToast: (msg: string, type?: 'success' | 'danger' | 'achievement' | 'suggestion', title?: string, duration?: number, why?: string) => void;
}

interface EditingBlock {
    type: 'add' | 'edit';
    blockIndex?: number;
    data?: ProgramBlock;
}

interface EditingMesocycle {
    type: 'add' | 'edit';
    blockIndex: number;
    mesoIndex?: number;
    data?: ProgramMesocycle;
}

interface EditingWeek {
    type: 'add' | 'edit';
    blockIndex: number;
    mesoIndex: number;
    weekIndex?: number;
    data?: ProgramWeek;
}

const MESOCYCLE_GOALS = [
    { value: 'Acumulación', label: 'Acumulación' },
    { value: 'Intensificación', label: 'Intensificación' },
    { value: 'Realización', label: 'Realización' },
    { value: 'Transición', label: 'Transición' },
    { value: 'Hipertrofia', label: 'Hipertrofia' },
    { value: 'Fuerza', label: 'Fuerza' },
    { value: 'Potencia', label: 'Potencia' },
    { value: 'Custom', label: 'Personalizado' },
];

export const MacrocycleEditor: React.FC<MacrocycleEditorProps> = ({
    program,
    onUpdateProgram,
    addToast,
}) => {
    const [expandedBlocks, setExpandedBlocks] = useState<Set<string>>(new Set(['0']));
    const [editingBlock, setEditingBlock] = useState<EditingBlock | null>(null);
    const [editingMesocycle, setEditingMesocycle] = useState<EditingMesocycle | null>(null);
    const [editingWeek, setEditingWeek] = useState<EditingWeek | null>(null);
    const [showEventModal, setShowEventModal] = useState(false);
    const [eventData, setEventData] = useState({ id: '', title: '', week: 0, type: '1rm_test' });
    const [showSimpleWarning, setShowSimpleWarning] = useState(false);
    const [pendingDeleteBlockIndex, setPendingDeleteBlockIndex] = useState<number | null>(null);

    const isSimple = program.structure === 'simple' || (!program.structure && program.macrocycles.length === 1 && (program.macrocycles[0].blocks || []).length <= 1);

    const toggleBlock = (blockKey: string) => {
        const newExpanded = new Set(expandedBlocks);
        if (newExpanded.has(blockKey)) {
            newExpanded.delete(blockKey);
        } else {
            newExpanded.add(blockKey);
        }
        setExpandedBlocks(newExpanded);
    };

    // Calcular total de semanas y estadísticas
    const totalStats = useMemo(() => {
        let totalWeeks = 0;
        let totalSessions = 0;
        let totalMesocycles = 0;
        let totalBlocks = 0;

        program.macrocycles.forEach(macro => {
            (macro.blocks || []).forEach(block => {
                totalBlocks++;
                block.mesocycles.forEach(meso => {
                    totalMesocycles++;
                    totalWeeks += meso.weeks.length;
                    totalSessions += meso.weeks.reduce((acc, w) => acc + w.sessions.length, 0);
                });
            });
        });

        return { totalWeeks, totalSessions, totalMesocycles, totalBlocks };
    }, [program]);

    // Handlers para bloques
    const handleAddBlock = () => {
        setEditingBlock({
            type: 'add',
            data: {
                id: crypto.randomUUID(),
                name: 'Nuevo Bloque',
                mesocycles: [{
                    id: crypto.randomUUID(),
                    name: 'Fase Inicial',
                    goal: 'Acumulación',
                    weeks: [{ id: crypto.randomUUID(), name: 'Semana 1', sessions: [] }]
                }]
            }
        });
    };

    const handleSaveBlock = (blockData: ProgramBlock) => {
        const updated = JSON.parse(JSON.stringify(program));
        if (editingBlock?.type === 'add') {
            updated.macrocycles[0].blocks.push(blockData);
        } else if (editingBlock?.type === 'edit' && editingBlock.blockIndex !== undefined) {
            updated.macrocycles[0].blocks[editingBlock.blockIndex] = blockData;
        }
        onUpdateProgram(updated);
        setEditingBlock(null);
        addToast('Bloque guardado', 'success');
    };

    const handleDeleteBlock = (blockIndex: number) => {
        const blocks = program.macrocycles[0]?.blocks || [];
        if (blocks.length <= 1) {
            addToast('Debe haber al menos un bloque', 'danger');
            return;
        }
        // If deleting would leave only 1 block → warn about conversion to simple
        if (blocks.length === 2 && program.structure === 'complex') {
            setPendingDeleteBlockIndex(blockIndex);
            setShowSimpleWarning(true);
            return;
        }
        const updated = JSON.parse(JSON.stringify(program));
        updated.macrocycles[0].blocks.splice(blockIndex, 1);
        onUpdateProgram(updated);
        addToast('Bloque eliminado', 'success');
    };

    const confirmConvertToSimple = () => {
        if (pendingDeleteBlockIndex === null) return;
        const updated = JSON.parse(JSON.stringify(program));
        updated.macrocycles[0].blocks.splice(pendingDeleteBlockIndex, 1);
        updated.structure = 'simple';
        // Clear key dates (not relevant for simple programs)
        updated.events = (updated.events || []).filter((e: any) => e.repeatEveryXCycles);
        onUpdateProgram(updated);
        setShowSimpleWarning(false);
        setPendingDeleteBlockIndex(null);
        addToast('Programa convertido a Simple (Cíclico)', 'success');
    };

    // Handlers para mesociclos
    const handleAddMesocycle = (blockIndex: number) => {
        setEditingMesocycle({
            type: 'add',
            blockIndex,
            data: {
                id: crypto.randomUUID(),
                name: 'Nuevo Mesociclo',
                goal: 'Acumulación',
                weeks: [{ id: crypto.randomUUID(), name: 'Semana 1', sessions: [] }]
            }
        });
    };

    const handleSaveMesocycle = (mesoData: ProgramMesocycle) => {
        const updated = JSON.parse(JSON.stringify(program));
        if (editingMesocycle?.type === 'add') {
            updated.macrocycles[0].blocks[editingMesocycle.blockIndex].mesocycles.push(mesoData);
        } else if (editingMesocycle?.type === 'edit' && editingMesocycle.mesoIndex !== undefined) {
            updated.macrocycles[0].blocks[editingMesocycle.blockIndex].mesocycles[editingMesocycle.mesoIndex] = mesoData;
        }
        onUpdateProgram(updated);
        setEditingMesocycle(null);
        addToast('Mesociclo guardado', 'success');
    };

    const handleDeleteMesocycle = (blockIndex: number, mesoIndex: number) => {
        const updated = JSON.parse(JSON.stringify(program));
        updated.macrocycles[0].blocks[blockIndex].mesocycles.splice(mesoIndex, 1);
        onUpdateProgram(updated);
        addToast('Mesociclo eliminado', 'success');
    };

    // Handlers para semanas
    const handleAddWeek = (blockIndex: number, mesoIndex: number) => {
        setEditingWeek({
            type: 'add',
            blockIndex,
            mesoIndex,
            data: { id: crypto.randomUUID(), name: `Semana ${((program.macrocycles[0]?.blocks?.[blockIndex]?.mesocycles[mesoIndex]?.weeks?.length ?? 0) + 1)}`, sessions: [] }
        });
    };

    const handleSaveWeek = (weekData: ProgramWeek) => {
        const updated = JSON.parse(JSON.stringify(program));
        if (editingWeek?.type === 'add') {
            updated.macrocycles[0].blocks[editingWeek.blockIndex].mesocycles[editingWeek.mesoIndex].weeks.push(weekData);
        }
        onUpdateProgram(updated);
        setEditingWeek(null);
        addToast('Semana guardada', 'success');
    };

    const handleDeleteWeek = (blockIndex: number, mesoIndex: number, weekIndex: number) => {
        const updated = JSON.parse(JSON.stringify(program));
        const weeks = updated.macrocycles[0].blocks[blockIndex].mesocycles[mesoIndex].weeks;
        if (weeks.length <= 1) {
            addToast('Debe haber al menos una semana', 'danger');
            return;
        }
        weeks.splice(weekIndex, 1);
        onUpdateProgram(updated);
        addToast('Semana eliminada', 'success');
    };

    // Handlers para eventos
    const handleSaveEvent = () => {
        if (!eventData.title.trim()) {
            addToast('Nombre del evento requerido', 'danger');
            return;
        }
        const updated = JSON.parse(JSON.stringify(program));
        if (!updated.events) updated.events = [];
        
        if (eventData.id) {
            const idx = updated.events.findIndex((e: any) => e.id === eventData.id);
            if (idx !== -1) updated.events[idx] = { ...eventData };
        } else {
            updated.events.push({
                id: crypto.randomUUID(),
                title: eventData.title,
                type: eventData.type,
                calculatedWeek: eventData.week,
                date: new Date().toISOString()
            });
        }
        onUpdateProgram(updated);
        setShowEventModal(false);
        addToast('Evento guardado', 'success');
    };

    const handleDeleteEvent = (eventId: string) => {
        const updated = JSON.parse(JSON.stringify(program));
        updated.events = updated.events.filter((e: any) => e.id !== eventId);
        onUpdateProgram(updated);
        setShowEventModal(false);
        addToast('Evento eliminado', 'success');
    };

    return (
        <div className="pb-6">
            {/* Stats y Road To Bar */}
            <div className="px-4 mb-4 space-y-3">
                {/* Stats resumen */}
                <div className="grid grid-cols-4 gap-2">
                    <div className="bg-white rounded-2xl border border-zinc-200 p-3 text-center shadow-sm">
                        <div className="text-xl font-black text-zinc-900">{totalStats.totalBlocks}</div>
                        <div className="text-[7px] uppercase tracking-[0.2em] text-zinc-400 mt-0.5">Bloques</div>
                    </div>
                    <div className="bg-white rounded-2xl border border-zinc-200 p-3 text-center shadow-sm">
                        <div className="text-xl font-black text-zinc-900">{totalStats.totalMesocycles}</div>
                        <div className="text-[7px] uppercase tracking-[0.2em] text-zinc-400 mt-0.5">Mesociclos</div>
                    </div>
                    <div className="bg-white rounded-2xl border border-zinc-200 p-3 text-center shadow-sm">
                        <div className="text-xl font-black text-zinc-900">{totalStats.totalWeeks}</div>
                        <div className="text-[7px] uppercase tracking-[0.2em] text-zinc-400 mt-0.5">Semanas</div>
                    </div>
                    <div className="bg-white rounded-2xl border border-zinc-200 p-3 text-center shadow-sm">
                        <div className="text-xl font-black text-zinc-900">{totalStats.totalSessions}</div>
                        <div className="text-[7px] uppercase tracking-[0.2em] text-zinc-400 mt-0.5">Sesiones</div>
                    </div>
                </div>

                {/* ROAD TO Bar - Fechas clave y eventos */}
                <div className="bg-gradient-to-r from-purple-500 to-purple-600 rounded-2xl p-4 text-white shadow-lg">
                    <div className="flex items-center justify-between mb-3">
                        <div className="flex items-center gap-2">
                            <TargetIcon size={18} className="text-white/90" />
                            <span className="text-[10px] font-black uppercase tracking-[0.2em]">Road To</span>
                        </div>
                        <button
                            onClick={() => {
                                setEventData({ id: '', title: '', week: 0, type: '1rm_test' });
                                setShowEventModal(true);
                            }}
                            className="text-[9px] font-black uppercase tracking-[0.15em] bg-white/20 hover:bg-white/30 px-3 py-1.5 rounded-full transition-all flex items-center gap-1"
                        >
                            <PlusIcon size={12} />
                            Evento
                        </button>
                    </div>
                    
                    {/* Timeline de eventos */}
                    <div className="flex items-center gap-2 overflow-x-auto custom-scrollbar">
                        {program.events && program.events.length > 0 ? (
                            program.events.map((event: any, idx: number) => (
                                <div
                                    key={event.id}
                                    className="flex-shrink-0 bg-white/20 backdrop-blur-sm rounded-xl px-3 py-2 flex items-center gap-2"
                                >
                                    <CalendarIcon size={12} className="text-white/80" />
                                    <div>
                                        <div className="text-[9px] font-black uppercase tracking-[0.1em]">{event.title}</div>
                                        <div className="text-[8px] text-white/60">Semana {event.calculatedWeek + 1}</div>
                                    </div>
                                </div>
                            ))
                        ) : (
                            <div className="text-[10px] text-white/70 italic">Sin eventos programados</div>
                        )}
                    </div>
                </div>
            </div>

            {/* Lista de bloques con herramientas integradas */}
            <div className="px-4 space-y-4">
                {/* Header con botón de añadir bloque */}
                <div className="flex items-center justify-between">
                    <h3 className="text-[11px] font-black uppercase tracking-[0.2em] text-zinc-600">Estructura del Programa</h3>
                    <button
                        onClick={handleAddBlock}
                        className="flex items-center gap-1.5 bg-purple-500 hover:bg-purple-600 text-white px-3 py-2 rounded-xl text-[9px] font-black uppercase tracking-[0.15em] transition-all shadow-md"
                    >
                        <PlusIcon size={14} />
                        Bloque
                    </button>
                </div>

                {/* Check si no hay bloques */}
                {!program.macrocycles[0]?.blocks || program.macrocycles[0].blocks.length === 0 ? (
                    <div className="text-center py-12">
                        <div className="w-20 h-20 mx-auto bg-gradient-to-br from-purple-100 to-pink-100 rounded-full flex items-center justify-center mb-4">
                            <CalendarIcon size={40} className="text-purple-600" />
                        </div>
                        <h4 className="text-sm font-black text-zinc-900 uppercase tracking-widest mb-2">Sin bloques</h4>
                        <p className="text-[10px] text-zinc-500 uppercase tracking-widest mb-4">Este programa no tiene bloques aún</p>
                        <button
                            onClick={handleAddBlock}
                            className="px-6 py-3 rounded-xl bg-gradient-to-r from-purple-500 to-purple-600 text-white text-[9px] font-black uppercase tracking-[0.15em] hover:opacity-90 transition-all shadow-lg"
                        >
                            Añadir Primer Bloque
                        </button>
                    </div>
                ) : null}

                {/* Bloques */}
                {(program.macrocycles[0]?.blocks || []).map((block, blockIdx) => {
                    const blockKey = `${blockIdx}`;
                    const isExpanded = expandedBlocks.has(blockKey);
                    const blockWeeks = block.mesocycles.reduce((acc, m) => acc + m.weeks.length, 0);

                    return (
                        <div key={block.id || blockIdx} className="bg-white rounded-2xl border border-zinc-200 overflow-hidden shadow-sm">
                            {/* Header del bloque */}
                            <div className="px-4 py-3 flex items-center justify-between border-b border-zinc-100">
                                <div className="flex items-center gap-3 flex-1">
                                    <button
                                        onClick={() => toggleBlock(blockKey)}
                                        className="w-8 h-8 rounded-xl flex items-center justify-center flex-shrink-0 bg-purple-100 text-purple-600 hover:bg-purple-200 transition-colors"
                                    >
                                        {isExpanded ? <ChevronUpIcon size={16} /> : <ChevronDownIcon size={16} />}
                                    </button>
                                    <div className="flex-1 min-w-0">
                                        <div className="text-sm font-bold text-zinc-900 truncate">{block.name || `Bloque ${blockIdx + 1}`}</div>
                                        <div className="text-[10px] text-zinc-500">{blockWeeks} semanas • {block.mesocycles.length} mesociclos</div>
                                    </div>
                                </div>
                                <div className="flex items-center gap-2">
                                    <button
                                        onClick={() => setEditingBlock({ type: 'edit', blockIndex: blockIdx, data: block })}
                                        className="w-9 h-9 rounded-xl bg-zinc-100 hover:bg-zinc-200 transition-colors flex items-center justify-center"
                                    >
                                        <EditIcon size={16} className="text-zinc-600" />
                                    </button>
                                    <button
                                        onClick={() => handleDeleteBlock(blockIdx)}
                                        className="w-9 h-9 rounded-xl bg-red-50 hover:bg-red-100 transition-colors flex items-center justify-center"
                                    >
                                        <TrashIcon size={16} className="text-red-500" />
                                    </button>
                                </div>
                            </div>

                            {/* Contenido expandido */}
                            <AnimatePresence>
                                {isExpanded && (
                                    <motion.div
                                        initial={{ height: 0, opacity: 0 }}
                                        animate={{ height: 'auto', opacity: 1 }}
                                        exit={{ height: 0, opacity: 0 }}
                                        transition={{ duration: 0.2 }}
                                        className="overflow-hidden"
                                    >
                                        <div className="px-4 pb-4 pt-3 space-y-4">
                                            {/* Mesociclos */}
                                            {block.mesocycles.map((meso, mesoIdx) => (
                                                <div key={meso.id || mesoIdx} className="bg-zinc-50 rounded-xl p-3 border border-zinc-200/60">
                                                    {/* Header del mesociclo */}
                                                    <div className="flex items-center justify-between mb-3">
                                                        <div className="flex items-center gap-2">
                                                            <div className="w-0.5 h-4 rounded-full bg-purple-500" />
                                                            <div>
                                                                <div className="text-[10px] font-black uppercase tracking-[0.15em] text-purple-600">{meso.goal}</div>
                                                                <div className="text-[9px] text-zinc-500">{meso.weeks.length} semanas</div>
                                                            </div>
                                                        </div>
                                                        <div className="flex items-center gap-2">
                                                            <button
                                                                onClick={() => setEditingMesocycle({ type: 'edit', blockIndex: blockIdx, mesoIndex: mesoIdx, data: meso })}
                                                                className="w-8 h-8 rounded-xl bg-white hover:bg-zinc-100 border border-zinc-200 transition-colors flex items-center justify-center"
                                                            >
                                                                <EditIcon size={14} className="text-zinc-600" />
                                                            </button>
                                                            <button
                                                                onClick={() => handleDeleteMesocycle(blockIdx, mesoIdx)}
                                                                className="w-8 h-8 rounded-xl bg-red-50 hover:bg-red-100 border border-red-100 transition-colors flex items-center justify-center"
                                                            >
                                                                <TrashIcon size={14} className="text-red-500" />
                                                            </button>
                                                        </div>
                                                    </div>

                                                    {/* Grid de semanas */}
                                                    <div className="grid grid-cols-4 gap-1.5 mb-3">
                                                        {meso.weeks.map((week, weekIdx) => (
                                                            <div
                                                                key={week.id}
                                                                className="aspect-square rounded-xl bg-white border border-zinc-200 flex flex-col items-center justify-center gap-0.5 group relative"
                                                            >
                                                                <span className="text-[10px] font-black text-zinc-700">S{weekIdx + 1}</span>
                                                                <span className="text-[7px] text-zinc-400">{week.sessions.length} ses</span>
                                                                {/* Hover actions */}
                                                                <div className="absolute inset-0 bg-black/50 rounded-xl opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center gap-1">
                                                                    <button
                                                                        onClick={() => setEditingWeek({ type: 'edit', blockIndex: blockIdx, mesoIndex: mesoIdx, weekIndex: weekIdx, data: week })}
                                                                        className="w-6 h-6 rounded-lg bg-white/20 hover:bg-white/30 flex items-center justify-center"
                                                                    >
                                                                        <EditIcon size={12} className="text-white" />
                                                                    </button>
                                                                    <button
                                                                        onClick={() => handleDeleteWeek(blockIdx, mesoIdx, weekIdx)}
                                                                        className="w-6 h-6 rounded-lg bg-red-500/80 hover:bg-red-500 flex items-center justify-center"
                                                                    >
                                                                        <TrashIcon size={12} className="text-white" />
                                                                    </button>
                                                                </div>
                                                            </div>
                                                        ))}
                                                    </div>

                                                    {/* Botón añadir semana */}
                                                    <button
                                                        onClick={() => handleAddWeek(blockIdx, mesoIdx)}
                                                        className="w-full py-2 rounded-xl border-2 border-dashed border-zinc-300 hover:border-purple-400 hover:bg-purple-50 transition-all flex items-center justify-center gap-2 text-[9px] font-black uppercase tracking-[0.15em] text-zinc-500 hover:text-purple-600"
                                                    >
                                                        <PlusIcon size={14} />
                                                        Semana
                                                    </button>
                                                </div>
                                            ))}

                                            {/* Botón añadir mesociclo */}
                                            <button
                                                onClick={() => handleAddMesocycle(blockIdx)}
                                                className="w-full py-3 rounded-xl border-2 border-dashed border-zinc-300 hover:border-purple-400 hover:bg-purple-50 transition-all flex items-center justify-center gap-2 text-[9px] font-black uppercase tracking-[0.15em] text-zinc-500 hover:text-purple-600"
                                            >
                                                <PlusIcon size={16} />
                                                Mesociclo
                                            </button>
                                        </div>
                                    </motion.div>
                                )}
                            </AnimatePresence>
                        </div>
                    );
                })}
            </div>

            {/* Modal para editar/bloque/mesociclo/semana */}
            <BlockMesocycleWeekModal
                editingBlock={editingBlock}
                editingMesocycle={editingMesocycle}
                editingWeek={editingWeek}
                onClose={() => {
                    setEditingBlock(null);
                    setEditingMesocycle(null);
                    setEditingWeek(null);
                }}
                onSaveBlock={handleSaveBlock}
                onSaveMesocycle={handleSaveMesocycle}
                onSaveWeek={handleSaveWeek}
            />

            {/* Modal de eventos */}
            {showEventModal && (
                <div className="fixed inset-0 z-[200] bg-black/20 backdrop-blur-sm flex items-center justify-center p-4" onClick={() => setShowEventModal(false)}>
                    <div className="bg-white rounded-[2rem] w-full max-w-sm p-6 shadow-2xl relative" onClick={e => e.stopPropagation()}>
                        <button onClick={() => setShowEventModal(false)} className="absolute top-4 right-4 text-zinc-400 hover:text-zinc-600">
                            <XIcon size={20} />
                        </button>
                        <h3 className="text-sm font-black text-zinc-900 uppercase tracking-widest mb-4 flex items-center gap-2">
                            <CalendarIcon size={18} className="text-purple-500" />
                            {eventData.id ? 'Editar Evento' : 'Nuevo Evento'}
                        </h3>
                        <div className="space-y-4">
                            <div>
                                <label className="text-[10px] text-zinc-500 font-black uppercase tracking-widest block mb-2">Nombre</label>
                                <input
                                    type="text"
                                    value={eventData.title}
                                    onChange={e => setEventData({ ...eventData, title: e.target.value })}
                                    placeholder="Ej: Prueba 1RM"
                                    className="w-full bg-zinc-50 border border-zinc-200 rounded-xl px-4 py-3 text-sm font-bold focus:ring-2 focus:ring-purple-500/30 outline-none"
                                />
                            </div>
                            <div>
                                <label className="text-[10px] text-zinc-500 font-black uppercase tracking-widest block mb-2">Semana</label>
                                <input
                                    type="number"
                                    value={eventData.week + 1}
                                    onChange={e => setEventData({ ...eventData, week: (parseInt(e.target.value) || 1) - 1 })}
                                    className="w-full bg-zinc-50 border border-zinc-200 rounded-xl px-4 py-3 text-sm font-bold focus:ring-2 focus:ring-purple-500/30 outline-none"
                                />
                            </div>
                            <div className="flex gap-3 pt-4">
                                {eventData.id && (
                                    <button
                                        onClick={() => handleDeleteEvent(eventData.id)}
                                        className="w-14 h-14 bg-red-50 border border-red-100 text-red-500 rounded-xl flex items-center justify-center"
                                    >
                                        <TrashIcon size={18} />
                                    </button>
                                )}
                                <button
                                    onClick={handleSaveEvent}
                                    className="flex-1 bg-purple-500 text-white py-4 rounded-xl text-[9px] font-black uppercase tracking-[0.15em] hover:bg-purple-600 transition-all shadow-lg"
                                >
                                    Guardar
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            )}

            {/* Warning Modal: Conversión Avanzado → Simple */}
            {showSimpleWarning && (
                <div className="fixed inset-0 z-[400] bg-black/40 backdrop-blur-sm flex items-center justify-center p-4">
                    <motion.div
                        initial={{ scale: 0.95, opacity: 0 }}
                        animate={{ scale: 1, opacity: 1 }}
                        className="bg-white rounded-[2rem] w-full max-w-sm p-6 shadow-2xl"
                    >
                        <div className="w-16 h-16 mx-auto bg-cyan-50 rounded-2xl flex items-center justify-center mb-4">
                            <span className="text-3xl">🔄</span>
                        </div>
                        <h3 className="text-sm font-black text-zinc-900 uppercase tracking-widest text-center mb-2">
                            Convertir a Programa Simple
                        </h3>
                        <p className="text-[10px] text-zinc-600 leading-relaxed text-center mb-4">
                            Al eliminar este bloque quedarás con 1 solo bloque, lo que convierte tu programa en <strong>Simple (Cíclico)</strong>.
                        </p>
                        <div className="bg-amber-50 border border-amber-200 rounded-xl p-3 mb-4 space-y-1.5">
                            <p className="text-[9px] text-amber-700 font-bold">⚠️ Se eliminarán todas las Fechas Clave</p>
                            <p className="text-[9px] text-emerald-700 font-bold">✅ Podrás usar Loops (loops)</p>
                            <p className="text-[9px] text-emerald-700 font-bold">✅ Tu programa se repetirá cíclicamente</p>
                        </div>
                        <div className="flex gap-2">
                            <button
                                onClick={() => { setShowSimpleWarning(false); setPendingDeleteBlockIndex(null); }}
                                className="flex-1 py-3 rounded-xl bg-zinc-100 text-zinc-600 text-[10px] font-black uppercase tracking-widest"
                            >
                                Cancelar
                            </button>
                            <button
                                onClick={confirmConvertToSimple}
                                className="flex-1 py-3 rounded-xl bg-cyan-600 text-white text-[10px] font-black uppercase tracking-widest hover:bg-cyan-700 transition-colors"
                            >
                                Convertir
                            </button>
                        </div>
                    </motion.div>
                </div>
            )}
        </div>
    );
};

// Modal compartido para editar bloque/mesociclo/semana
const BlockMesocycleWeekModal: React.FC<{
    editingBlock: EditingBlock | null;
    editingMesocycle: EditingMesocycle | null;
    editingWeek: EditingWeek | null;
    onClose: () => void;
    onSaveBlock: (data: ProgramBlock) => void;
    onSaveMesocycle: (data: ProgramMesocycle) => void;
    onSaveWeek: (data: ProgramWeek) => void;
}> = ({ editingBlock, editingMesocycle, editingWeek, onClose, onSaveBlock, onSaveMesocycle, onSaveWeek }) => {
    const [blockData, setBlockData] = useState<ProgramBlock | null>(null);
    const [mesoData, setMesoData] = useState<ProgramMesocycle | null>(null);
    const [weekData, setWeekData] = useState<ProgramWeek | null>(null);

    // ✅ ACTUALIZAR estados cuando cambian las props
    React.useEffect(() => {
        if (editingBlock?.data) setBlockData(editingBlock.data);
        else setBlockData(null);
    }, [editingBlock]);

    React.useEffect(() => {
        if (editingMesocycle?.data) setMesoData(editingMesocycle.data);
        else setMesoData(null);
    }, [editingMesocycle]);

    React.useEffect(() => {
        if (editingWeek?.data) setWeekData(editingWeek.data);
        else setWeekData(null);
    }, [editingWeek]);

    // ✅ DEBUG: Log cuando se abre el modal
    React.useEffect(() => {
        console.log('Modal abierto:', { editingBlock, editingMesocycle, editingWeek });
    }, [editingBlock, editingMesocycle, editingWeek]);

    if (!editingBlock && !editingMesocycle && !editingWeek) {
        console.log('Modal no se renderiza - todo es null');
        return null;
    }

    const handleSave = () => {
        if (editingBlock && blockData) onSaveBlock(blockData);
        if (editingMesocycle && mesoData) onSaveMesocycle(mesoData);
        if (editingWeek && weekData) onSaveWeek(weekData);
    };

    return (
        <div className="fixed inset-0 z-[200] bg-black/20 backdrop-blur-sm flex items-center justify-center p-4" onClick={onClose}>
            <div className="bg-white rounded-[2rem] w-full max-w-sm p-6 shadow-2xl relative" onClick={e => e.stopPropagation()}>
                <button onClick={onClose} className="absolute top-4 right-4 text-zinc-400 hover:text-zinc-600">
                    <XIcon size={20} />
                </button>
                
                {editingBlock && blockData && (
                    <div>
                        <h3 className="text-sm font-black text-zinc-900 uppercase tracking-widest mb-4">
                            {editingBlock.type === 'add' ? 'Nuevo Bloque' : 'Editar Bloque'}
                        </h3>
                        <div className="space-y-4">
                            <div>
                                <label className="text-[10px] text-zinc-500 font-black uppercase tracking-widest block mb-2">Nombre</label>
                                <input
                                    type="text"
                                    value={blockData.name}
                                    onChange={e => setBlockData({ ...blockData, name: e.target.value })}
                                    className="w-full bg-zinc-50 border border-zinc-200 rounded-xl px-4 py-3 text-sm font-bold focus:ring-2 focus:ring-purple-500/30 outline-none"
                                />
                            </div>
                        </div>
                    </div>
                )}

                {editingMesocycle && mesoData && (
                    <div>
                        <h3 className="text-sm font-black text-zinc-900 uppercase tracking-widest mb-4">
                            {editingMesocycle.type === 'add' ? 'Nuevo Mesociclo' : 'Editar Mesociclo'}
                        </h3>
                        <div className="space-y-4">
                            <div>
                                <label className="text-[10px] text-zinc-500 font-black uppercase tracking-widest block mb-2">Nombre</label>
                                <input
                                    type="text"
                                    value={mesoData.name}
                                    onChange={e => setMesoData({ ...mesoData, name: e.target.value })}
                                    className="w-full bg-zinc-50 border border-zinc-200 rounded-xl px-4 py-3 text-sm font-bold focus:ring-2 focus:ring-purple-500/30 outline-none"
                                />
                            </div>
                            <div>
                                <label className="text-[10px] text-zinc-500 font-black uppercase tracking-widest block mb-2">Objetivo</label>
                                <select
                                    value={mesoData.goal}
                                    onChange={e => setMesoData({ ...mesoData, goal: e.target.value as ProgramMesocycle['goal'] })}
                                    className="w-full bg-zinc-50 border border-zinc-200 rounded-xl px-4 py-3 text-sm font-bold focus:ring-2 focus:ring-purple-500/30 outline-none"
                                >
                                    {MESOCYCLE_GOALS.map(g => (
                                        <option key={g.value} value={g.value}>{g.label}</option>
                                    ))}
                                </select>
                            </div>
                        </div>
                    </div>
                )}

                {editingWeek && weekData && (
                    <div>
                        <h3 className="text-sm font-black text-zinc-900 uppercase tracking-widest mb-4">
                            {editingWeek.type === 'add' ? 'Nueva Semana' : 'Editar Semana'}
                        </h3>
                        <div className="space-y-4">
                            <div>
                                <label className="text-[10px] text-zinc-500 font-black uppercase tracking-widest block mb-2">Nombre</label>
                                <input
                                    type="text"
                                    value={weekData.name}
                                    onChange={e => setWeekData({ ...weekData, name: e.target.value })}
                                    className="w-full bg-zinc-50 border border-zinc-200 rounded-xl px-4 py-3 text-sm font-bold focus:ring-2 focus:ring-purple-500/30 outline-none"
                                />
                            </div>
                        </div>
                    </div>
                )}

                <div className="flex gap-3 mt-6 pt-4 border-t border-zinc-100">
                    <button
                        onClick={onClose}
                        className="flex-1 py-3 rounded-xl border-2 border-zinc-200 text-[9px] font-black uppercase tracking-[0.15em] text-zinc-600 hover:bg-zinc-50 transition-all"
                    >
                        Cancelar
                    </button>
                    <button
                        onClick={handleSave}
                        className="flex-1 py-3 rounded-xl bg-purple-500 text-white text-[9px] font-black uppercase tracking-[0.15em] hover:bg-purple-600 transition-all shadow-lg flex items-center justify-center gap-2"
                    >
                        <SaveIcon size={14} />
                        Guardar
                    </button>
                </div>
            </div>
        </div>
    );
};
