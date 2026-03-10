import React, { useState, useMemo, useEffect, useRef } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Program, ProgramWeek, Session } from '../../types';
import { XIcon, PlusIcon, TrashIcon, EditIcon, CalendarIcon, ChevronDownIcon, ChevronUpIcon, SaveIcon, AlertTriangleIcon } from '../icons';

interface MacrocycleEditorProps {
    program: Program;
    isOpen: boolean;
    onClose: () => void;
    onUpdateProgram: (program: Program) => void;
    addToast: (message: string, type: 'success' | 'error' | 'info') => void;
}

interface CyclicEvent {
    id: string;
    title: string;
    type: '1rm_test' | 'deload' | 'competition' | 'custom';
    repeatEveryXCycles: number;
    durationType: 'day' | 'week';
    dayOfWeek?: number; // 0-6, solo si durationType es 'day'
    durationWeeks?: number; // solo si durationType es 'week'
}

interface KeyDate {
    id: string;
    title: string;
    type: 'competition' | 'exam_week' | 'vacation' | 'custom';
    targetDate: string;
    durationType: 'day' | 'week';
    weekIndex?: number; // índice de semana donde cae
    dayOfWeek?: number; // 0-6, solo si durationType es 'day'
}

const MacrocycleEditor: React.FC<MacrocycleEditorProps> = ({
    program,
    isOpen,
    onClose,
    onUpdateProgram,
    addToast,
}) => {
    // Estado para programas simples (cíclicos)
    const [cyclicEvents, setCyclicEvents] = useState<CyclicEvent[]>(program.events?.filter((e: any) => e.repeatEveryXCycles) || []);

    // Estado para programas avanzados
    const [keyDates, setKeyDates] = useState<KeyDate[]>(program.events?.filter((e: any) => !e.repeatEveryXCycles) || []);

    // Estado para estructura
    const [expandedBlocks, setExpandedBlocks] = useState<Set<string>>(new Set(['0']));
    const [showAdvancedWarning, setShowAdvancedWarning] = useState(false);
    const [pendingBlockAdd, setPendingBlockAdd] = useState(false);

    // Estado para header transformable
    const [lastScrollY, setLastScrollY] = useState(0);
    const [isHeaderCollapsed, setIsHeaderCollapsed] = useState(false);
    const contentRef = useRef<HTMLDivElement>(null);

    // Detectar scroll para transformar header
    useEffect(() => {
        const content = contentRef.current;
        if (!content) return;

        const handleScroll = () => {
            const currentScrollY = content.scrollTop;
            // Colapsar cuando scroll > 100px, expandir cuando está cerca del top
            if (currentScrollY > 100 && currentScrollY > lastScrollY) {
                setIsHeaderCollapsed(true);
            } else if (currentScrollY < 50) {
                setIsHeaderCollapsed(false);
            }
            setLastScrollY(currentScrollY);
        };

        content.addEventListener('scroll', handleScroll);
        return () => content.removeEventListener('scroll', handleScroll);
    }, [lastScrollY]);

    // Detectar si es programa simple
    const isSimple = program.structure === 'simple' || (!program.structure && program.macrocycles.length === 1 && (program.macrocycles[0].blocks || []).length <= 1);

    // Calcular total de semanas
    const totalWeeks = useMemo(() => {
        return program.macrocycles.reduce((acc, m) => 
            acc + (m.blocks || []).reduce((ba, b) => 
                ba + b.mesocycles.reduce((ma, me) => ma + me.weeks.length, 0), 0
            ), 0
        );
    }, [program]);

    // Calcular ciclo para programas simples
    const cycleLength = totalWeeks;

    // Resetear estado cuando cambia el programa
    useEffect(() => {
        setCyclicEvents(program.events?.filter((e: any) => e.repeatEveryXCycles) || []);
        setKeyDates(program.events?.filter((e: any) => !e.repeatEveryXCycles) || []);
    }, [program]);

    // ─── Handlers para programas simples ───
    const addCyclicEvent = () => {
        const newEvent: CyclicEvent = {
            id: crypto.randomUUID(),
            title: 'Nuevo Evento',
            type: 'custom',
            repeatEveryXCycles: 1,
            durationType: 'day',
            dayOfWeek: 1,
        };
        setCyclicEvents([...cyclicEvents, newEvent]);
    };

    const updateCyclicEvent = (id: string, updates: Partial<CyclicEvent>) => {
        setCyclicEvents(cyclicEvents.map(e => e.id === id ? { ...e, ...updates } : e));
    };

    const deleteCyclicEvent = (id: string) => {
        setCyclicEvents(cyclicEvents.filter(e => e.id !== id));
    };

    // ─── Handlers para programas avanzados ───
    const addKeyDate = () => {
        const newKeyDate: KeyDate = {
            id: crypto.randomUUID(),
            title: 'Nueva Fecha Clave',
            type: 'custom',
            targetDate: new Date().toISOString().split('T')[0],
            durationType: 'week',
            weekIndex: totalWeeks - 1,
        };
        setKeyDates([...keyDates, newKeyDate]);
    };

    const updateKeyDate = (id: string, updates: Partial<KeyDate>) => {
        setKeyDates(keyDates.map(d => d.id === id ? { ...d, ...updates } : d));
    };

    const deleteKeyDate = (id: string) => {
        setKeyDates(keyDates.filter(d => d.id !== id));
    };

    // ─── Handlers para estructura ───
    const toggleBlock = (blockKey: string) => {
        const newExpanded = new Set(expandedBlocks);
        if (newExpanded.has(blockKey)) {
            newExpanded.delete(blockKey);
        } else {
            newExpanded.add(blockKey);
        }
        setExpandedBlocks(newExpanded);
    };

    const addBlock = () => {
        if (isSimple) {
            // Mostrar advertencia antes de convertir a avanzado
            setShowAdvancedWarning(true);
            setPendingBlockAdd(true);
            return;
        }
        
        const updated = JSON.parse(JSON.stringify(program));
        const lastMacro = updated.macrocycles[updated.macrocycles.length - 1];
        lastMacro.blocks.push({
            id: crypto.randomUUID(),
            name: `Bloque ${lastMacro.blocks.length + 1}`,
            mesocycles: [{
                id: crypto.randomUUID(),
                name: 'Fase Inicial',
                goal: 'Acumulación',
                weeks: [{ id: crypto.randomUUID(), name: 'Semana 1', sessions: [] }]
            }]
        });
        onUpdateProgram(updated);
        addToast('Bloque agregado', 'success');
    };

    const deleteBlock = (macroIndex: number, blockIndex: number) => {
        if (program.macrocycles.length === 1 && program.macrocycles[0].blocks.length <= 2) {
            addToast('Debe haber al menos 1 bloque', 'error');
            return;
        }
        
        const updated = JSON.parse(JSON.stringify(program));
        updated.macrocycles[macroIndex].blocks.splice(blockIndex, 1);
        onUpdateProgram(updated);
        addToast('Bloque eliminado', 'success');
    };

    const addWeekToBlock = (macroIndex: number, blockIndex: number, mesoIndex: number) => {
        const updated = JSON.parse(JSON.stringify(program));
        const newWeek: ProgramWeek = {
            id: crypto.randomUUID(),
            name: `Semana ${updated.macrocycles[macroIndex].blocks[blockIndex].mesocycles[mesoIndex].weeks.length + 1}`,
            sessions: []
        };
        updated.macrocycles[macroIndex].blocks[blockIndex].mesocycles[mesoIndex].weeks.push(newWeek);
        onUpdateProgram(updated);
        addToast('Semana agregada', 'success');
    };

    const deleteWeek = (macroIndex: number, blockIndex: number, mesoIndex: number, weekIndex: number) => {
        const updated = JSON.parse(JSON.stringify(program));
        const meso = updated.macrocycles[macroIndex].blocks[blockIndex].mesocycles[mesoIndex];
        if (meso.weeks.length <= 1) {
            addToast('Cada mesociclo debe tener al menos 1 semana', 'error');
            return;
        }
        meso.weeks.splice(weekIndex, 1);
        onUpdateProgram(updated);
        addToast('Semana eliminada', 'success');
    };

    // ─── Confirmar conversión a avanzado ───
    const confirmConvertToAdvanced = () => {
        const updated = JSON.parse(JSON.stringify(program));
        updated.structure = 'complex';
        updated.events = []; // Eliminar eventos cíclicos
        
        // Convertir estructura
        updated.macrocycles[0].name = 'Macrociclo Principal';
        updated.macrocycles[0].blocks[0].name = 'Bloque 1';
        
        // Agregar segundo bloque
        updated.macrocycles[0].blocks.push({
            id: crypto.randomUUID(),
            name: 'Bloque 2',
            mesocycles: [{
                id: crypto.randomUUID(),
                name: 'Fase Inicial',
                goal: 'Acumulación',
                weeks: [{ id: crypto.randomUUID(), name: 'Semana 1', sessions: [] }]
            }]
        });
        
        onUpdateProgram(updated);
        setShowAdvancedWarning(false);
        setPendingBlockAdd(false);
        addToast('Programa convertido a avanzado', 'success');
    };

    // ─── Guardar eventos/fechas ───
    const handleSave = () => {
        const updated = JSON.parse(JSON.stringify(program));
        
        if (isSimple) {
            // Guardar eventos cíclicos
            updated.events = cyclicEvents.map(e => ({
                id: e.id,
                title: e.title,
                type: e.type,
                repeatEveryXCycles: e.repeatEveryXCycles,
                durationType: e.durationType,
                dayOfWeek: e.durationType === 'day' ? e.dayOfWeek : undefined,
                durationWeeks: e.durationType === 'week' ? e.durationWeeks : undefined,
            }));
        } else {
            // Guardar fechas clave
            updated.events = keyDates.map(d => ({
                id: d.id,
                title: d.title,
                type: d.type,
                targetDate: d.targetDate,
                durationType: d.durationType,
                weekIndex: d.weekIndex,
                dayOfWeek: d.durationType === 'day' ? d.dayOfWeek : undefined,
            }));
        }
        
        onUpdateProgram(updated);
        addToast('Cambios guardados', 'success');
        onClose();
    };

    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 z-[200] bg-black/40 backdrop-blur-sm flex items-end sm:items-center justify-center p-0 sm:p-4" onClick={onClose}>
            <motion.div
                initial={{ y: '100%' }}
                animate={{ y: 0 }}
                exit={{ y: '100%' }}
                transition={{ type: 'spring', damping: 25, stiffness: 300 }}
                className="bg-[#FEF7FF] w-full max-w-2xl max-h-[90vh] flex flex-col rounded-t-[2rem] sm:rounded-[2.5rem] shadow-2xl overflow-hidden"
                onClick={e => e.stopPropagation()}
            >
                {/* Header Transformable */}
                <motion.div
                    animate={{
                        height: isHeaderCollapsed ? 48 : 80,
                        padding: isHeaderCollapsed ? '0 16px' : '20px 24px',
                    }}
                    transition={{ type: 'spring', stiffness: 400, damping: 30 }}
                    className={`
                        flex items-center justify-between border-b border-[#ECE6F0] bg-white/95 backdrop-blur-xl z-10
                        ${isHeaderCollapsed ? 'shadow-lg' : 'shadow-sm'}
                    `}
                    style={{ minHeight: isHeaderCollapsed ? 48 : 80 }}
                >
                    {/* Contenido del header - se desvanece cuando está colapsado */}
                    <AnimatePresence>
                        {!isHeaderCollapsed && (
                            <motion.div
                                initial={{ opacity: 0 }}
                                animate={{ opacity: 1 }}
                                exit={{ opacity: 0 }}
                                transition={{ duration: 0.15 }}
                                className="flex items-center gap-3"
                            >
                                <div className={`w-8 h-8 rounded-xl flex items-center justify-center ${isSimple ? 'bg-cyan-100' : 'bg-purple-100'}`}>
                                    <CalendarIcon className={isSimple ? 'text-cyan-600' : 'text-purple-600'} size={18} />
                                </div>
                                <div>
                                    <h2 className="text-sm font-black text-zinc-900 uppercase tracking-tight">
                                        Editor de Macrociclo
                                    </h2>
                                    <p className="text-[9px] text-zinc-500 uppercase tracking-widest -mt-0.5">
                                        {isSimple ? 'Programa Simple (Cíclico)' : 'Programa Avanzado'}
                                    </p>
                                </div>
                            </motion.div>
                        )}
                    </AnimatePresence>

                    {/* Botón cerrar - siempre visible */}
                    <button
                        onClick={onClose}
                        className={`
                            rounded-full flex items-center justify-center transition-all
                            ${isHeaderCollapsed 
                                ? 'w-8 h-8 bg-zinc-100 hover:bg-zinc-200' 
                                : 'w-10 h-10 bg-zinc-100 hover:bg-zinc-200'
                            }
                        `}
                    >
                        <XIcon size={isHeaderCollapsed ? 16 : 18} />
                    </button>
                </motion.div>

                {/* Floating Pill - Indicador de contexto (solo cuando header colapsado) */}
                <AnimatePresence>
                    {isHeaderCollapsed && (
                        <motion.div
                            initial={{ opacity: 0, y: -10 }}
                            animate={{ opacity: 1, y: 0 }}
                            exit={{ opacity: 0, y: -10 }}
                            transition={{ duration: 0.2 }}
                            className="absolute top-[52px] left-1/2 -translate-x-1/2 z-10"
                            style={{ pointerEvents: 'none' }}
                        >
                            <div className="flex items-center gap-3 px-4 py-2 bg-white/90 backdrop-blur-xl border border-[#ECE6F0] rounded-full shadow-lg">
                                <div className="flex items-center gap-2">
                                    <div className={`w-2 h-2 rounded-full ${isSimple ? 'bg-cyan-500' : 'bg-purple-500'}`} />
                                    <span className="text-[8px] font-black uppercase tracking-[0.15em] text-zinc-600">
                                        {isSimple ? 'Simple' : 'Avanzado'}
                                    </span>
                                </div>
                                <div className="w-px h-3 bg-zinc-200" />
                                <span className="text-[8px] font-bold text-zinc-500">
                                    {totalWeeks} semanas
                                </span>
                            </div>
                        </motion.div>
                    )}
                </AnimatePresence>

                {/* Contenido scrollable */}
                <div ref={contentRef} className="flex-1 overflow-y-auto custom-scrollbar p-6 space-y-6">
                    
                    {/* ═══ SECCIÓN 1: ESTRUCTURA ═══ */}
                    <section>
                        <div className="flex items-center justify-between mb-3">
                            <h3 className="text-[10px] font-black uppercase tracking-[0.25em] text-zinc-500">Estructura</h3>
                            {!isSimple && (
                                <button
                                    onClick={addBlock}
                                    className="flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-purple-100 text-purple-700 text-[8px] font-black uppercase tracking-[0.15em] hover:bg-purple-200 transition-colors"
                                >
                                    <PlusIcon size={12} />
                                    Agregar Bloque
                                </button>
                            )}
                        </div>

                        <div className="space-y-2">
                            {program.macrocycles.map((macro, macroIdx) =>
                                (macro.blocks || []).map((block, blockIdx) => {
                                    const blockKey = `${macroIdx}-${blockIdx}`;
                                    const isExpanded = expandedBlocks.has(blockKey);
                                    const blockWeeks = block.mesocycles.reduce((acc, m) => acc + m.weeks.length, 0);

                                    return (
                                        <div key={block.id || blockKey} className="bg-white rounded-2xl border border-[#ECE6F0] overflow-hidden">
                                            {/* Header del bloque */}
                                            <div className="px-4 py-3 flex items-center justify-between bg-[#FAFAFA]">
                                                <button
                                                    onClick={() => toggleBlock(blockKey)}
                                                    className="flex items-center gap-3 flex-1"
                                                >
                                                    <div className={`w-8 h-8 rounded-xl flex items-center justify-center ${isExpanded ? 'bg-purple-500 text-white' : 'bg-zinc-200 text-zinc-500'}`}>
                                                        <CalendarIcon size={16} />
                                                    </div>
                                                    <div className="text-left">
                                                        <div className="text-sm font-bold text-zinc-900">{block.name || `Bloque ${blockIdx + 1}`}</div>
                                                        <div className="text-[9px] text-zinc-500">{blockWeeks} semanas • {block.mesocycles.length} mesociclos</div>
                                                    </div>
                                                </button>
                                                <div className="flex items-center gap-2">
                                                    {isExpanded ? <ChevronUpIcon size={16} className="text-zinc-400" /> : <ChevronDownIcon size={16} className="text-zinc-400" />}
                                                    {!isSimple && blockIdx > 0 && (
                                                        <button
                                                            onClick={() => deleteBlock(macroIdx, blockIdx)}
                                                            className="w-8 h-8 rounded-full bg-red-50 text-red-500 flex items-center justify-center hover:bg-red-100"
                                                        >
                                                            <TrashIcon size={14} />
                                                        </button>
                                                    )}
                                                </div>
                                            </div>

                                            {/* Contenido expandido */}
                                            <AnimatePresence>
                                                {isExpanded && (
                                                    <motion.div
                                                        initial={{ height: 0 }}
                                                        animate={{ height: 'auto' }}
                                                        exit={{ height: 0 }}
                                                        className="overflow-hidden border-t border-[#ECE6F0]"
                                                    >
                                                        <div className="p-4 space-y-4">
                                                            {block.mesocycles.map((meso, mesoIdx) => (
                                                                <div key={meso.id || mesoIdx}>
                                                                    <div className="flex items-center gap-2 mb-2">
                                                                        <div className="w-1 h-3 rounded-full bg-purple-400" />
                                                                        <span className="text-[9px] font-black uppercase tracking-[0.2em] text-purple-600">
                                                                            {meso.goal || `Mesociclo ${mesoIdx + 1}`}
                                                                        </span>
                                                                    </div>

                                                                    {/* Semanas */}
                                                                    <div className="grid grid-cols-4 gap-2">
                                                                        {meso.weeks.map((week, weekIdx) => (
                                                                            <div key={week.id} className="relative group">
                                                                                <div className="aspect-square rounded-xl bg-[#F5F3F8] border border-[#ECE6F0] flex flex-col items-center justify-center gap-0.5">
                                                                                    <span className="text-[10px] font-black text-zinc-700">S{weekIdx + 1}</span>
                                                                                    <span className="text-[7px] text-zinc-400">{week.sessions.length} sesiones</span>
                                                                                </div>
                                                                                {!isSimple && (
                                                                                    <button
                                                                                        onClick={() => deleteWeek(macroIdx, blockIdx, mesoIdx, weekIdx)}
                                                                                        className="absolute -top-1 -right-1 w-5 h-5 rounded-full bg-red-500 text-white flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity"
                                                                                    >
                                                                                        <XIcon size={10} />
                                                                                    </button>
                                                                                )}
                                                                            </div>
                                                                        ))}
                                                                        
                                                                        {/* Botón agregar semana */}
                                                                        {!isSimple && (
                                                                            <button
                                                                                onClick={() => addWeekToBlock(macroIdx, blockIdx, mesoIdx)}
                                                                                className="aspect-square rounded-xl border-2 border-dashed border-zinc-300 flex items-center justify-center text-zinc-400 hover:border-purple-400 hover:text-purple-500 transition-colors"
                                                                            >
                                                                                <PlusIcon size={16} />
                                                                            </button>
                                                                        )}
                                                                    </div>
                                                                </div>
                                                            ))}
                                                        </div>
                                                    </motion.div>
                                                )}
                                            </AnimatePresence>
                                        </div>
                                    );
                                })
                            )}
                        </div>
                    </section>

                    {/* ═══ SECCIÓN: ROAD TO (solo avanzados) ═══ */}
                    {!isSimple && keyDates.length > 0 && (
                        <section>
                            <h3 className="text-[10px] font-black uppercase tracking-[0.25em] text-zinc-500 mb-3">
                                Road To
                            </h3>
                            
                            <div className="space-y-4">
                                {keyDates.map((date, idx) => {
                                    const targetDate = new Date(date.targetDate);
                                    const today = new Date();
                                    const daysUntil = Math.ceil((targetDate.getTime() - today.getTime()) / (1000 * 60 * 60 * 24));
                                    const totalDays = daysUntil > 0 ? daysUntil : 1;
                                    const progressPct = daysUntil > 0 ? Math.max(0, Math.min(100, ((totalDays - daysUntil) / totalDays) * 100)) : 100;
                                    
                                    return (
                                        <div key={date.id} className="bg-gradient-to-br from-purple-50 to-white rounded-2xl border border-purple-100 p-4">
                                            <div className="flex items-center justify-between mb-3">
                                                <div className="flex items-center gap-2">
                                                    <div className={`w-2 h-2 rounded-full ${daysUntil < 7 ? 'bg-red-500 animate-pulse' : 'bg-purple-500'}`} />
                                                    <span className="text-sm font-bold text-zinc-900">{date.title}</span>
                                                </div>
                                                <span className={`text-[9px] font-black uppercase tracking-[0.15em] ${daysUntil < 7 ? 'text-red-600' : 'text-zinc-500'}`}>
                                                    {daysUntil > 0 ? `Faltan ${daysUntil} días` : daysUntil === 0 ? '¡Es hoy!' : `Pasó hace ${Math.abs(daysUntil)} días`}
                                                </span>
                                            </div>
                                            
                                            {/* Barra de progreso */}
                                            <div className="relative h-3 bg-zinc-100 rounded-full overflow-hidden">
                                                <motion.div
                                                    initial={{ width: 0 }}
                                                    animate={{ width: `${progressPct}%` }}
                                                    transition={{ duration: 0.5 }}
                                                    className="absolute inset-y-0 left-0 bg-gradient-to-r from-purple-400 to-purple-600 rounded-full"
                                                />
                                                
                                                {/* Marcador de fecha objetivo */}
                                                <div className="absolute inset-y-0 right-0 w-0.5 bg-zinc-400" />
                                            </div>
                                            
                                            {/* Info adicional */}
                                            <div className="flex items-center justify-between mt-2">
                                                <span className="text-[8px] text-zinc-400 font-bold uppercase tracking-wider">
                                                    {date.type === 'competition' ? '🏆 Competición' : date.type === 'exam_week' ? '📚 Exámenes' : date.type === 'vacation' ? '🏖️ Vacaciones' : '📌 Fecha clave'}
                                                </span>
                                                <span className="text-[8px] text-zinc-400 font-bold">
                                                    {targetDate.toLocaleDateString('es-ES', { day: 'numeric', month: 'short', year: 'numeric' })}
                                                </span>
                                            </div>
                                        </div>
                                    );
                                })}
                            </div>
                        </section>
                    )}

                    {/* ═══ SECCIÓN 2: EVENTOS / FECHAS CLAVE ═══ */}
                    <section>
                        <div className="flex items-center justify-between mb-3">
                            <h3 className="text-[10px] font-black uppercase tracking-[0.25em] text-zinc-500">
                                {isSimple ? 'Eventos Cíclicos' : 'Fechas Clave'}
                            </h3>
                            <button
                                onClick={isSimple ? addCyclicEvent : addKeyDate}
                                className="flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-cyan-100 text-cyan-700 text-[8px] font-black uppercase tracking-[0.15em] hover:bg-cyan-200 transition-colors"
                            >
                                <PlusIcon size={12} />
                                {isSimple ? 'Agregar Evento' : 'Agregar Fecha'}
                            </button>
                        </div>

                        {isSimple ? (
                            /* ─── Eventos Cíclicos ─── */
                            <div className="space-y-2">
                                {cyclicEvents.length === 0 && (
                                    <div className="py-6 text-center border-2 border-dashed border-[#ECE6F0] rounded-2xl">
                                        <p className="text-[10px] text-zinc-400 font-bold uppercase tracking-wider">Sin eventos cíclicos</p>
                                    </div>
                                )}
                                {cyclicEvents.map(event => (
                                    <div key={event.id} className="bg-white rounded-2xl border border-[#ECE6F0] p-4 space-y-3">
                                        <div className="flex items-center justify-between">
                                            <input
                                                type="text"
                                                value={event.title}
                                                onChange={(e) => updateCyclicEvent(event.id, { title: e.target.value })}
                                                className="flex-1 text-sm font-bold text-zinc-900 bg-transparent border-none outline-none"
                                                placeholder="Nombre del evento"
                                            />
                                            <button
                                                onClick={() => deleteCyclicEvent(event.id)}
                                                className="w-8 h-8 rounded-full bg-red-50 text-red-500 flex items-center justify-center hover:bg-red-100"
                                            >
                                                <TrashIcon size={14} />
                                            </button>
                                        </div>

                                        <div className="grid grid-cols-2 gap-3">
                                            <div>
                                                <label className="text-[8px] font-black uppercase tracking-[0.15em] text-zinc-400 block mb-1.5">Repetir cada</label>
                                                <div className="flex items-center gap-2">
                                                    <input
                                                        type="number"
                                                        min="1"
                                                        value={event.repeatEveryXCycles}
                                                        onChange={(e) => updateCyclicEvent(event.id, { repeatEveryXCycles: parseInt(e.target.value) || 1 })}
                                                        className="w-20 h-10 rounded-xl bg-[#F5F3F8] border border-[#ECE6F0] text-center text-sm font-bold outline-none focus:border-purple-400"
                                                    />
                                                    <span className="text-[9px] font-bold text-zinc-500">ciclos</span>
                                                </div>
                                            </div>

                                            <div>
                                                <label className="text-[8px] font-black uppercase tracking-[0.15em] text-zinc-400 block mb-1.5">Duración</label>
                                                <select
                                                    value={event.durationType}
                                                    onChange={(e) => updateCyclicEvent(event.id, { durationType: e.target.value as 'day' | 'week' })}
                                                    className="w-full h-10 rounded-xl bg-[#F5F3F8] border border-[#ECE6F0] text-sm font-bold outline-none focus:border-purple-400"
                                                >
                                                    <option value="day">1 Día</option>
                                                    <option value="week">1 Semana</option>
                                                </select>
                                            </div>
                                        </div>

                                        {event.durationType === 'day' && (
                                            <div>
                                                <label className="text-[8px] font-black uppercase tracking-[0.15em] text-zinc-400 block mb-1.5">Día de la semana</label>
                                                <select
                                                    value={event.dayOfWeek || 1}
                                                    onChange={(e) => updateCyclicEvent(event.id, { dayOfWeek: parseInt(e.target.value) })}
                                                    className="w-full h-10 rounded-xl bg-[#F5F3F8] border border-[#ECE6F0] text-sm font-bold outline-none focus:border-purple-400"
                                                >
                                                    <option value={0}>Domingo</option>
                                                    <option value={1}>Lunes</option>
                                                    <option value={2}>Martes</option>
                                                    <option value={3}>Miércoles</option>
                                                    <option value={4}>Jueves</option>
                                                    <option value={5}>Viernes</option>
                                                    <option value={6}>Sábado</option>
                                                </select>
                                            </div>
                                        )}

                                        <div className="pt-2 border-t border-[#ECE6F0]">
                                            <p className="text-[8px] text-zinc-400">
                                                📅 Aparecerá cada {event.repeatEveryXCycles} ciclo{event.repeatEveryXCycles > 1 ? 's' : ''} ({cycleLength * event.repeatEveryXCycles} semanas)
                                            </p>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        ) : (
                            /* ─── Fechas Clave ─── */
                            <div className="space-y-2">
                                {keyDates.length === 0 && (
                                    <div className="py-6 text-center border-2 border-dashed border-[#ECE6F0] rounded-2xl">
                                        <p className="text-[10px] text-zinc-400 font-bold uppercase tracking-wider">Sin fechas clave</p>
                                    </div>
                                )}
                                {keyDates.map(date => (
                                    <div key={date.id} className="bg-white rounded-2xl border border-[#ECE6F0] p-4 space-y-3">
                                        <div className="flex items-center justify-between">
                                            <input
                                                type="text"
                                                value={date.title}
                                                onChange={(e) => updateKeyDate(date.id, { title: e.target.value })}
                                                className="flex-1 text-sm font-bold text-zinc-900 bg-transparent border-none outline-none"
                                                placeholder="Nombre de la fecha clave"
                                            />
                                            <button
                                                onClick={() => deleteKeyDate(date.id)}
                                                className="w-8 h-8 rounded-full bg-red-50 text-red-500 flex items-center justify-center hover:bg-red-100"
                                            >
                                                <TrashIcon size={14} />
                                            </button>
                                        </div>

                                        <div className="grid grid-cols-2 gap-3">
                                            <div>
                                                <label className="text-[8px] font-black uppercase tracking-[0.15em] text-zinc-400 block mb-1.5">Fecha objetivo</label>
                                                <input
                                                    type="date"
                                                    value={date.targetDate}
                                                    onChange={(e) => updateKeyDate(date.id, { targetDate: e.target.value })}
                                                    className="w-full h-10 rounded-xl bg-[#F5F3F8] border border-[#ECE6F0] text-sm font-bold outline-none focus:border-purple-400"
                                                />
                                            </div>

                                            <div>
                                                <label className="text-[8px] font-black uppercase tracking-[0.15em] text-zinc-400 block mb-1.5">Tipo</label>
                                                <select
                                                    value={date.type}
                                                    onChange={(e) => updateKeyDate(date.id, { type: e.target.value as any })}
                                                    className="w-full h-10 rounded-xl bg-[#F5F3F8] border border-[#ECE6F0] text-sm font-bold outline-none focus:border-purple-400"
                                                >
                                                    <option value="competition">Competición</option>
                                                    <option value="exam_week">Semana de Exámenes</option>
                                                    <option value="vacation">Vacaciones</option>
                                                    <option value="custom">Personalizado</option>
                                                </select>
                                            </div>
                                        </div>

                                        <div>
                                            <label className="text-[8px] font-black uppercase tracking-[0.15em] text-zinc-400 block mb-1.5">Semana estimada</label>
                                            <input
                                                type="number"
                                                min="0"
                                                max={totalWeeks - 1}
                                                value={date.weekIndex || 0}
                                                onChange={(e) => updateKeyDate(date.id, { weekIndex: parseInt(e.target.value) || 0 })}
                                                className="w-full h-10 rounded-xl bg-[#F5F3F8] border border-[#ECE6F0] text-sm font-bold outline-none focus:border-purple-400"
                                                placeholder="Número de semana (0-indexed)"
                                            />
                                        </div>
                                    </div>
                                ))}
                            </div>
                        )}
                    </section>
                </div>

                {/* Footer con acciones */}
                <div className="px-6 py-4 border-t border-[#ECE6F0] bg-white flex items-center justify-between">
                    <div className="text-[9px] text-zinc-400">
                        {totalWeeks} semanas totales • {cycleLength} semanas por ciclo
                    </div>
                    <div className="flex items-center gap-2">
                        <button
                            onClick={onClose}
                            className="px-5 py-2.5 rounded-full text-[9px] font-black uppercase tracking-[0.15em] text-zinc-500 hover:bg-zinc-100 transition-colors"
                        >
                            Cancelar
                        </button>
                        <button
                            onClick={handleSave}
                            className="px-5 py-2.5 rounded-full bg-gradient-to-r from-purple-500 to-purple-600 text-white text-[9px] font-black uppercase tracking-[0.15em] hover:opacity-90 transition-all shadow-lg flex items-center gap-2"
                        >
                            <SaveIcon size={14} />
                            Guardar
                        </button>
                    </div>
                </div>
            </motion.div>

            {/* ═══ ADVERTENCIA: Conversión a Avanzado ═══ */}
            <AnimatePresence>
                {showAdvancedWarning && (
                    <div className="fixed inset-0 z-[250] bg-black/40 backdrop-blur-sm flex items-center justify-center p-4" onClick={() => setShowAdvancedWarning(false)}>
                        <motion.div
                            initial={{ scale: 0.9, opacity: 0 }}
                            animate={{ scale: 1, opacity: 1 }}
                            exit={{ scale: 0.9, opacity: 0 }}
                            className="bg-white w-full max-w-md rounded-[2.5rem] p-8 shadow-2xl relative"
                            onClick={e => e.stopPropagation()}
                        >
                            <div className="w-16 h-16 rounded-full bg-amber-100 flex items-center justify-center mb-6 mx-auto">
                                <AlertTriangleIcon className="text-amber-600" size={32} />
                            </div>
                            
                            <h3 className="text-lg font-black text-zinc-900 uppercase tracking-tight text-center mb-2">
                                ¿Convertir a Programa Avanzado?
                            </h3>
                            
                            <p className="text-[10px] text-zinc-500 uppercase tracking-widest text-center mb-6">
                                Esta acción no se puede deshacer
                            </p>

                            <div className="bg-[#F5F3F8] rounded-2xl p-5 mb-6 space-y-3">
                                <div className="flex items-start gap-3">
                                    <div className="w-5 h-5 rounded-full bg-red-100 text-red-500 flex items-center justify-center flex-shrink-0 mt-0.5">
                                        <XIcon size={12} />
                                    </div>
                                    <p className="text-[9px] text-zinc-600 font-bold leading-relaxed">
                                        Se eliminarán todos los <span className="font-black">eventos cíclicos</span> configurados
                                    </p>
                                </div>
                                <div className="flex items-start gap-3">
                                    <div className="w-5 h-5 rounded-full bg-emerald-100 text-emerald-500 flex items-center justify-center flex-shrink-0 mt-0.5">
                                        <PlusIcon size={12} />
                                    </div>
                                    <p className="text-[9px] text-zinc-600 font-bold leading-relaxed">
                                        Podrás usar <span className="font-black">fechas clave</span> y la barra "Road to"
                                    </p>
                                </div>
                                <div className="flex items-start gap-3">
                                    <div className="w-5 h-5 rounded-full bg-purple-100 text-purple-500 flex items-center justify-center flex-shrink-0 mt-0.5">
                                        <CalendarIcon size={12} />
                                    </div>
                                    <p className="text-[9px] text-zinc-600 font-bold leading-relaxed">
                                        El programa tendrá <span className="font-black">múltiples bloques</span> con inicio y fin
                                    </p>
                                </div>
                            </div>

                            <div className="flex items-center gap-3">
                                <button
                                    onClick={() => { setShowAdvancedWarning(false); setPendingBlockAdd(false); }}
                                    className="flex-1 py-3.5 rounded-full border-2 border-zinc-200 text-zinc-600 text-[9px] font-black uppercase tracking-[0.15em] hover:bg-zinc-50 transition-colors"
                                >
                                    Cancelar
                                </button>
                                <button
                                    onClick={confirmConvertToAdvanced}
                                    className="flex-1 py-3.5 rounded-full bg-gradient-to-r from-purple-500 to-purple-600 text-white text-[9px] font-black uppercase tracking-[0.15em] hover:opacity-90 transition-all shadow-lg"
                                >
                                    Convertir
                                </button>
                            </div>
                        </motion.div>
                    </div>
                )}
            </AnimatePresence>
        </div>
    );
};

export default MacrocycleEditor;
