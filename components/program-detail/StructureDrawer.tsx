import React, { useState } from 'react';
import { Program, ProgramWeek } from '../../types';
import { XIcon, PlusIcon, ChevronDownIcon, CalendarIcon, AlertTriangleIcon } from '../icons';
import { getAbsoluteWeekIndex, checkWeekHasEvent } from '../../utils/programHelpers';

interface StructureDrawerProps {
    isOpen: boolean;
    onClose: () => void;
    program: Program;
    isCyclic: boolean;
    selectedBlockId: string | null;
    selectedWeekId: string | null;
    onSelectBlock: (id: string) => void;
    onSelectWeek: (id: string) => void;
    onUpdateProgram?: (program: Program) => void;
    onEditWeek: (info: {
        macroIndex: number; blockIndex: number; mesoIndex: number;
        weekIndex: number; week: ProgramWeek; isSimple: boolean;
    }) => void;
    onShowAdvancedTransition: () => void;
    onShowSimpleTransition: () => void;
    onOpenEventModal: (data?: any) => void;
    /** Cuando true, se renderiza inline dentro del tab de entrenamiento en lugar de drawer lateral */
    inline?: boolean;
}

const GOAL_OPTIONS = ['Acumulación', 'Intensificación', 'Realización', 'Descarga', 'Custom'];
const BLOCK_COLORS = ['#3B82F6', '#A855F7', '#EAB308', '#10B981', '#F43F5E', '#06B6D4'];

const StructureDrawer: React.FC<StructureDrawerProps> = ({
    isOpen, onClose, program, isCyclic, selectedBlockId, selectedWeekId,
    onSelectBlock, onSelectWeek, onUpdateProgram, onEditWeek,
    onShowAdvancedTransition, onShowSimpleTransition, onOpenEventModal,
    inline = false,
}) => {
    const [expandedNodes, setExpandedNodes] = useState<Set<string>>(new Set(['macro-0', 'block-0-0']));
    const [showAddBlockConfirm, setShowAddBlockConfirm] = useState(false);
    const [pendingAddBlockMacroIdx, setPendingAddBlockMacroIdx] = useState<number | null>(null);

    const blockCount = program.macrocycles.reduce((acc, m) => acc + (m.blocks || []).length, 0);
    const showSimpleWarning = isCyclic && blockCount === 1;

    const toggleNode = (id: string) => {
        setExpandedNodes(prev => {
            const next = new Set(prev);
            next.has(id) ? next.delete(id) : next.add(id);
            return next;
        });
    };

    const update = (fn: (p: Program) => void) => {
        if (!onUpdateProgram) return;
        const clone = JSON.parse(JSON.stringify(program));
        fn(clone);
        onUpdateProgram(clone);
    };

    const totalWeeks = program.macrocycles.reduce((acc, m) =>
        acc + (m.blocks || []).reduce((ba, b) =>
            ba + b.mesocycles.reduce((ma, me) => ma + me.weeks.length, 0), 0), 0);

    const events = program.events || [];

    if (!isOpen) return null;

    const wrapperClassName = inline
        ? 'flex flex-col flex-1 min-h-0 bg-[#0a0a0a] rounded-2xl border border-white/10 overflow-hidden'
        : 'fixed top-0 left-0 bottom-0 z-[102] w-[280px] sm:w-[300px] bg-[#0a0a0a] border-r border-white/10 flex flex-col animate-slide-right';

    return (
        <>
            {!inline && <div className="fixed inset-0 z-[101] bg-black/50" onClick={onClose} />}

            <div className={wrapperClassName}>
                {/* Header */}
                <div data-testid="structure-drawer" className="px-4 py-3 border-b border-white/10 flex items-center justify-between shrink-0">
                    <span className="text-[10px] font-black text-white uppercase tracking-widest" aria-label="Estructura del programa">Estructura</span>
                    <button onClick={onClose} className="w-7 h-7 rounded-lg bg-white/5 border border-white/10 flex items-center justify-center text-zinc-500 hover:text-white transition-colors">
                        <XIcon size={14} />
                    </button>
                </div>

                {/* Simple→Advanced warning */}
                {showSimpleWarning && (
                    <div className="px-3 py-2 border-b border-white/10 flex items-start gap-2">
                        <AlertTriangleIcon size={14} className="text-amber-400 shrink-0 mt-0.5" />
                        <p className="text-[9px] text-amber-200/90">Si añades un bloque, el programa pasará a avanzado y los eventos se perderán.</p>
                    </div>
                )}

                {/* Mini timeline */}
                {totalWeeks > 0 && !isCyclic && (
                    <div className="px-4 py-2 border-b border-white/10">
                        <div className="flex gap-0.5 h-2 rounded-full overflow-hidden">
                            {program.macrocycles.flatMap((m, mi) =>
                                (m.blocks || []).map((block, bi) => {
                                    const bWeeks = block.mesocycles.reduce((a, me) => a + me.weeks.length, 0);
                                    const pct = totalWeeks > 0 ? (bWeeks / totalWeeks) * 100 : 0;
                                    const colorIdx = (mi * 10 + bi) % BLOCK_COLORS.length;
                                    return (
                                        <div
                                            key={block.id}
                                            className="h-full rounded-sm cursor-pointer hover:brightness-125 transition-all"
                                            style={{ width: `${pct}%`, backgroundColor: BLOCK_COLORS[colorIdx] }}
                                            onClick={() => onSelectBlock(block.id)}
                                            title={`${block.name} (${bWeeks} sem)`}
                                        />
                                    );
                                })
                            )}
                        </div>
                        <div className="flex justify-between mt-1">
                            <span className="text-[9px] text-zinc-500 font-bold uppercase tracking-widest">{totalWeeks} semanas</span>
                            <span className="text-[9px] text-zinc-500 font-bold uppercase tracking-widest">{events.length} eventos</span>
                        </div>
                    </div>
                )}

                {/* Tree */}
                <div className="flex-1 overflow-y-auto custom-scrollbar px-3 py-3 space-y-1">
                    {program.macrocycles.map((macro, macroIdx) => {
                        const macroKey = `macro-${macroIdx}`;
                        const macroExpanded = expandedNodes.has(macroKey);
                        return (
                            <div key={macro.id}>
                                {/* Macrocycle */}
                                <button onClick={() => toggleNode(macroKey)} className="w-full flex items-center gap-2 px-2 py-1.5 rounded-lg text-left hover:bg-white/5 transition-colors">
                                    <ChevronDownIcon size={12} className={`text-zinc-500 transition-transform ${macroExpanded ? 'rotate-0' : '-rotate-90'}`} />
                                    <span className="text-[11px] font-bold text-white flex-1 truncate">{macro.name}</span>
                                </button>

                                {macroExpanded && (
                                    <div className="ml-3 border-l border-white/10 pl-2 space-y-0.5">
                                        {(macro.blocks || []).map((block, blockIdx) => {
                                            const blockKey = `block-${macroIdx}-${blockIdx}`;
                                            const blockExpanded = expandedNodes.has(blockKey);
                                            const colorIdx = (macroIdx * 10 + blockIdx) % BLOCK_COLORS.length;
                                            const isSelected = block.id === selectedBlockId;
                                            return (
                                                <div key={block.id}>
                                                    <button
                                                        onClick={() => { toggleNode(blockKey); onSelectBlock(block.id); }}
                                                        className={`w-full flex items-center gap-2 px-2 py-1.5 rounded-lg text-left transition-colors ${isSelected ? 'bg-white/5' : 'hover:bg-white/5'}`}
                                                    >
                                                        <div className="w-2 h-2 rounded-full shrink-0" style={{ backgroundColor: BLOCK_COLORS[colorIdx] }} />
                                                        <ChevronDownIcon size={10} className={`text-zinc-500 transition-transform ${blockExpanded ? 'rotate-0' : '-rotate-90'}`} />
                                                        <span className="text-[11px] font-bold text-zinc-500 flex-1 truncate">{block.name}</span>
                                                        <span className="text-[9px] text-zinc-500">{block.mesocycles.reduce((a, m) => a + m.weeks.length, 0)}s</span>
                                                    </button>

                                                    {blockExpanded && (
                                                        <div className="ml-5 border-l border-white/10 pl-2 space-y-0.5">
                                                            {block.mesocycles.map((meso, mesoIdx) => (
                                                                <div key={meso.id}>
                                                                    {/* Mesocycle */}
                                                                    <div className="flex items-center gap-1 px-2 py-1">
                                                                        <span className="text-[10px] font-bold text-zinc-500 flex-1 truncate">{meso.name}</span>
                                                                        <select
                                                                            value={meso.goal || 'Custom'}
                                                                            onChange={e => update(p => { const t = p.macrocycles[macroIdx]?.blocks?.[blockIdx]?.mesocycles?.[mesoIdx]; if (t) t.goal = e.target.value as any; })}
                                                                            className="bg-transparent text-[9px] text-cyan-400 font-bold border-none p-0 focus:ring-0 cursor-pointer"
                                                                        >
                                                                            {GOAL_OPTIONS.map(g => <option key={g} value={g} className="bg-black text-white">{g}</option>)}
                                                                        </select>
                                                                    </div>
                                                                    {/* Weeks */}
                                                                    {meso.weeks.map((week, weekIdx) => {
                                                                        const absIdx = getAbsoluteWeekIndex(program, block.id, week.id);
                                                                        const hasEvent = checkWeekHasEvent(program, absIdx);
                                                                        const isWeekSelected = week.id === selectedWeekId;
                                                                        return (
                                                                            <button
                                                                                key={week.id}
                                                                                onClick={() => { onSelectBlock(block.id); onSelectWeek(week.id); }}
                                                                                className={`w-full flex items-center gap-2 px-3 py-1.5 rounded-md text-left transition-all ${
                                                                                    isWeekSelected ? 'bg-cyan-500/10 text-cyan-400' : 'hover:bg-white/5 text-zinc-500'
                                                                                }`}
                                                                            >
                                                                                <span className="text-[10px] font-bold flex-1">{week.name}</span>
                                                                                <span className="text-[9px] text-zinc-500">{week.sessions.length}s</span>
                                                                                {hasEvent && <CalendarIcon size={10} className="text-cyan-400 shrink-0" />}
                                                                            </button>
                                                                        );
                                                                    })}
                                                                    {/* Add week */}
                                                                    <button
                                                                        onClick={() => update(p => {
                                                                            const target = p.macrocycles[macroIdx]?.blocks?.[blockIdx]?.mesocycles?.[mesoIdx];
                                                                            if (target) target.weeks.push({ id: crypto.randomUUID(), name: `Semana ${target.weeks.length + 1}`, sessions: [] });
                                                                        })}
                                                                        className="w-full flex items-center gap-1.5 px-3 py-1 text-[9px] text-zinc-500 hover:text-cyan-400 transition-colors"
                                                                    >
                                                                        <PlusIcon size={10} /> Semana
                                                                    </button>
                                                                </div>
                                                            ))}
                                                            {/* Add mesocycle */}
                                                            <button
                                                                onClick={() => update(p => {
                                                                    const blk = p.macrocycles[macroIdx]?.blocks?.[blockIdx]; if (!blk) return; blk.mesocycles.push({
                                                                        id: crypto.randomUUID(), name: 'Nueva Fase', goal: 'Custom' as any,
                                                                        weeks: [{ id: crypto.randomUUID(), name: 'Semana 1', sessions: [] }],
                                                                    });
                                                                })}
                                                                className="w-full flex items-center gap-1.5 px-2 py-1 text-[9px] text-zinc-500 hover:text-cyan-400 transition-colors"
                                                            >
                                                                <PlusIcon size={10} /> Mesociclo
                                                            </button>
                                                        </div>
                                                    )}
                                                </div>
                                            );
                                        })}
                                        {/* Add block */}
                                        {!isCyclic && (
                                            <button
                                                onClick={() => {
                                                    if (isCyclic && blockCount === 1) {
                                                        setPendingAddBlockMacroIdx(macroIdx);
                                                        setShowAddBlockConfirm(true);
                                                    } else {
                                                        update(p => {
                                                            const macro = p.macrocycles[macroIdx]; if (!macro?.blocks) return; macro.blocks.push({
                                                                id: crypto.randomUUID(), name: 'Nuevo Bloque',
                                                                mesocycles: [{ id: crypto.randomUUID(), name: 'Fase Inicial', goal: 'Acumulación' as any, weeks: [{ id: crypto.randomUUID(), name: 'Semana 1', sessions: [] }] }],
                                                            });
                                                        });
                                                    }
                                                }}
                                                className="w-full flex items-center gap-1.5 px-2 py-1.5 text-[9px] text-zinc-500 hover:text-cyan-400 transition-colors"
                                            >
                                                <PlusIcon size={10} /> Bloque
                                            </button>
                                        )}
                                    </div>
                                )}
                            </div>
                        );
                    })}
                </div>

                {/* Events section */}
                <div className="border-t border-white/10 px-3 py-3 shrink-0">
                    <div className="flex items-center justify-between mb-2">
                        <span className="text-[10px] font-black text-zinc-500 uppercase tracking-widest">Eventos</span>
                        <button onClick={() => onOpenEventModal()} className="text-[10px] text-cyan-400 font-bold hover:underline">
                            + Nuevo
                        </button>
                    </div>
                    {events.length > 0 ? (
                        <div className="space-y-1 max-h-[120px] overflow-y-auto custom-scrollbar">
                            {events.map((ev: any, i: number) => (
                                <div key={ev.id || i} className="flex items-center gap-2 px-2 py-1.5 rounded-lg hover:bg-white/5 cursor-pointer transition-colors" onClick={() => onOpenEventModal(ev)}>
                                    <div className="w-1.5 h-1.5 rounded-full bg-cyan-400 shrink-0" />
                                    <span className="text-[10px] font-bold text-white flex-1 truncate">{ev.title}</span>
                                    <span className="text-[9px] text-zinc-500">
                                        {isCyclic ? `c/${ev.repeatEveryXCycles}` : `S${(ev.calculatedWeek || 0) + 1}`}
                                    </span>
                                </div>
                            ))}
                        </div>
                    ) : (
                        <p className="text-[10px] text-zinc-500 text-center py-2">Sin eventos</p>
                    )}
                </div>

                {/* Transition buttons */}
                <div className="border-t border-white/10 px-3 py-3 shrink-0">
                    {isCyclic ? (
                        <button onClick={onShowAdvancedTransition} className="w-full py-2 rounded-lg bg-white/5 border border-white/10 text-[10px] font-bold text-zinc-500 hover:text-white hover:bg-white/10 transition-colors">
                            Convertir a Avanzado
                        </button>
                    ) : (
                        <button onClick={onShowSimpleTransition} className="w-full py-2 rounded-lg bg-white/5 border border-white/10 text-[10px] font-bold text-zinc-500 hover:text-white hover:bg-white/10 transition-colors">
                            Simplificar Programa
                        </button>
                    )}
                </div>
            </div>

            {/* Add block confirmation modal */}
            {showAddBlockConfirm && (
                <>
                    <div className="fixed inset-0 z-[103] bg-black/60" onClick={() => { setShowAddBlockConfirm(false); setPendingAddBlockMacroIdx(null); }} />
                    <div className="fixed inset-0 z-[104] flex items-center justify-center p-4">
                        <div className="bg-[#1a1a1a] border border-white/10 rounded-2xl p-5 max-w-sm w-full shadow-xl" onClick={e => e.stopPropagation()}>
                            <div className="flex items-start gap-3 mb-4">
                                <AlertTriangleIcon size={24} className="text-amber-400 shrink-0" />
                                <div>
                                    <h3 className="text-sm font-bold text-white">Convertir a programa avanzado</h3>
                                    <p className="text-xs text-zinc-500 mt-1">Al añadir un bloque, el programa se convierte en avanzado y los eventos cíclicos se perderán. ¿Continuar?</p>
                                </div>
                            </div>
                            <div className="flex gap-2">
                                <button
                                    onClick={() => { setShowAddBlockConfirm(false); setPendingAddBlockMacroIdx(null); }}
                                    className="flex-1 py-2.5 rounded-lg bg-white/5 border border-white/10 text-xs font-bold text-zinc-500 hover:text-white transition-colors"
                                >
                                    Cancelar
                                </button>
                                <button
                                    onClick={() => {
                                        if (pendingAddBlockMacroIdx !== null) {
                                            update(p => {
                                                const macro = p.macrocycles[pendingAddBlockMacroIdx]; if (!macro?.blocks) return; macro.blocks.push({
                                                    id: crypto.randomUUID(), name: 'Nuevo Bloque',
                                                    mesocycles: [{ id: crypto.randomUUID(), name: 'Fase Inicial', goal: 'Acumulación' as any, weeks: [{ id: crypto.randomUUID(), name: 'Semana 1', sessions: [] }] }],
                                                });
                                            });
                                            setShowAddBlockConfirm(false);
                                            setPendingAddBlockMacroIdx(null);
                                        }
                                    }}
                                    className="flex-1 py-2.5 rounded-lg bg-cyan-500/30 border border-cyan-500/50 text-xs font-bold text-cyan-400 hover:bg-cyan-500/40 transition-colors"
                                >
                                    Continuar
                                </button>
                            </div>
                        </div>
                    </div>
                </>
            )}
        </>
    );
};

export default StructureDrawer;
