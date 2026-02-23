import React, { useState } from 'react';
import { Program, ProgramWeek } from '../../types';
import { PlusIcon, ChevronDownIcon, CalendarIcon, AlertTriangleIcon } from '../icons';
import EventSessionsManager from './EventSessionsManager';
import { getAbsoluteWeekIndex, checkWeekHasEvent } from '../../utils/programHelpers';

const GOAL_OPTIONS = ['Acumulación', 'Intensificación', 'Realización', 'Descarga', 'Custom'];
const BLOCK_COLORS = ['#3B82F6', '#A855F7', '#EAB308', '#10B981', '#F43F5E', '#06B6D4'];

interface StructureTabViewProps {
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
}

const StructureTabView: React.FC<StructureTabViewProps> = ({
    program, isCyclic, selectedBlockId, selectedWeekId,
    onSelectBlock, onSelectWeek, onUpdateProgram, onEditWeek,
    onShowAdvancedTransition, onShowSimpleTransition, onOpenEventModal,
}) => {
    const [expandedNodes, setExpandedNodes] = useState<Set<string>>(new Set(['macro-0', 'block-0-0']));
    const [showAddBlockConfirm, setShowAddBlockConfirm] = useState(false);
    const [pendingAddBlockMacroIdx, setPendingAddBlockMacroIdx] = useState<number | null>(null);

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
    const blockCount = program.macrocycles.reduce((acc, m) => acc + (m.blocks || []).length, 0);
    const showSimpleWarning = isCyclic && blockCount === 1;

    const handleAddBlockClick = (macroIdx: number) => {
        if (isCyclic && blockCount === 1) {
            setPendingAddBlockMacroIdx(macroIdx);
            setShowAddBlockConfirm(true);
        } else {
            doAddBlock(macroIdx);
        }
    };

    const doAddBlock = (macroIdx: number) => {
        const macro = program.macrocycles[macroIdx];
        if (!macro?.blocks) return;
        update(p => {
            const m = p.macrocycles[macroIdx];
            if (m?.blocks) m.blocks.push({
                id: crypto.randomUUID(), name: 'Nuevo Bloque',
                mesocycles: [{ id: crypto.randomUUID(), name: 'Fase Inicial', goal: 'Acumulación' as any, weeks: [{ id: crypto.randomUUID(), name: 'Semana 1', sessions: [] }] }],
            });
        });
        setShowAddBlockConfirm(false);
        setPendingAddBlockMacroIdx(null);
    };

    return (
        <div className="flex-1 flex flex-col min-h-0 overflow-hidden bg-[#0a0a0a]">
            {/* Simple→Advanced warning banner */}
            {showSimpleWarning && (
                <div className="shrink-0 mx-4 mt-4 p-3 rounded-xl bg-amber-500/10 border border-amber-500/30 flex items-start gap-2">
                    <AlertTriangleIcon size={18} className="text-amber-400 shrink-0 mt-0.5" />
                    <div>
                        <p className="text-xs font-bold text-amber-200">Programa simple</p>
                        <p className="text-[11px] text-amber-200/90 mt-0.5">Si añades un bloque, el programa pasará a avanzado y los eventos cíclicos se perderán.</p>
                    </div>
                </div>
            )}

            {/* Progress bar - bloques y fechas clave */}
            <div className="shrink-0 px-4 py-4 border-b border-white/5">
                <h3 className="text-[10px] font-black text-[#8E8E93] uppercase tracking-widest mb-3">Progreso del programa</h3>
                {totalWeeks > 0 && !isCyclic && (
                    <>
                        <div className="flex gap-0.5 h-3 rounded-full overflow-hidden">
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
                        <div className="flex justify-between mt-2">
                            <span className="text-[10px] text-[#48484A] font-bold">{totalWeeks} semanas</span>
                            <span className="text-[10px] text-[#48484A] font-bold">{events.length} eventos</span>
                        </div>
                        {events.length > 0 && (
                            <div className="flex flex-wrap gap-1.5 mt-2">
                                {events.map((e: any, i: number) => (
                                    <button
                                        key={e.id || i}
                                        onClick={() => onOpenEventModal(e)}
                                        className="text-[9px] font-bold px-2 py-1 rounded-md bg-[#00F0FF]/10 border border-[#00F0FF]/20 text-[#00F0FF] hover:bg-[#00F0FF]/20"
                                    >
                                        {e.title} · S{(e.calculatedWeek || 0) + 1}
                                    </button>
                                ))}
                            </div>
                        )}
                    </>
                )}
                {isCyclic && (
                    <div className="text-[11px] text-[#8E8E93] font-bold">
                        Programa cíclico · {totalWeeks} semanas · {events.length} eventos
                    </div>
                )}
            </div>

            {/* Tree - scrollable */}
            <div className="flex-1 min-h-0 overflow-y-auto px-4 py-4 pb-[max(80px,env(safe-area-inset-bottom,0px))] custom-scrollbar">
                <div className="space-y-2">
                    {program.macrocycles.map((macro, macroIdx) => {
                        const macroKey = `macro-${macroIdx}`;
                        const macroExpanded = expandedNodes.has(macroKey);
                        return (
                            <div key={macro.id} className="bg-[#111] border border-white/5 rounded-xl overflow-hidden">
                                <button onClick={() => toggleNode(macroKey)} className="w-full flex items-center gap-3 px-4 py-3 text-left hover:bg-white/5 transition-colors">
                                    <ChevronDownIcon size={14} className={`text-[#48484A] transition-transform ${macroExpanded ? 'rotate-0' : '-rotate-90'}`} />
                                    <span className="text-sm font-bold text-white flex-1">{macro.name}</span>
                                </button>

                                {macroExpanded && (
                                    <div className="border-t border-white/5 px-4 pb-4 space-y-2">
                                        {(macro.blocks || []).map((block, blockIdx) => {
                                            const blockKey = `block-${macroIdx}-${blockIdx}`;
                                            const blockExpanded = expandedNodes.has(blockKey);
                                            const colorIdx = (macroIdx * 10 + blockIdx) % BLOCK_COLORS.length;
                                            const isSelected = block.id === selectedBlockId;
                                            return (
                                                <div key={block.id} className="rounded-lg border border-white/5 overflow-hidden">
                                                    <button
                                                        onClick={() => { toggleNode(blockKey); onSelectBlock(block.id); }}
                                                        className={`w-full flex items-center gap-3 px-3 py-2.5 text-left transition-colors ${isSelected ? 'bg-[#00F0FF]/10' : 'hover:bg-white/5'}`}
                                                    >
                                                        <div className="w-3 h-3 rounded-full shrink-0" style={{ backgroundColor: BLOCK_COLORS[colorIdx] }} />
                                                        <ChevronDownIcon size={12} className={`text-[#48484A] transition-transform ${blockExpanded ? 'rotate-0' : '-rotate-90'}`} />
                                                        <span className="text-xs font-bold text-[#8E8E93] flex-1">{block.name}</span>
                                                        <span className="text-[10px] text-[#48484A]">{block.mesocycles.reduce((a, m) => a + m.weeks.length, 0)} sem</span>
                                                    </button>

                                                    {blockExpanded && (
                                                        <div className="border-t border-white/5 pl-6 pr-3 py-2 space-y-1">
                                                            {block.mesocycles.map((meso, mesoIdx) => (
                                                                <div key={meso.id}>
                                                                    <div className="flex items-center gap-2 py-1.5">
                                                                        <span className="text-[11px] font-bold text-[#48484A] flex-1">{meso.name}</span>
                                                                        <select
                                                                            value={meso.goal || 'Custom'}
                                                                            onChange={e => update(p => { const t = p.macrocycles[macroIdx]?.blocks?.[blockIdx]?.mesocycles?.[mesoIdx]; if (t) t.goal = e.target.value as any; })}
                                                                            className="bg-transparent text-[10px] text-[#00F0FF] font-bold border-none p-0 focus:ring-0 cursor-pointer"
                                                                        >
                                                                            {GOAL_OPTIONS.map(g => <option key={g} value={g} className="bg-black text-white">{g}</option>)}
                                                                        </select>
                                                                    </div>
                                                                    {meso.weeks.map((week, weekIdx) => {
                                                                        const absIdx = getAbsoluteWeekIndex(program, block.id, week.id);
                                                                        const hasEvent = checkWeekHasEvent(program, absIdx);
                                                                        const isWeekSelected = week.id === selectedWeekId;
                                                                        return (
                                                                            <button
                                                                                key={week.id}
                                                                                onClick={() => { onSelectBlock(block.id); onSelectWeek(week.id); }}
                                                                                className={`w-full flex items-center gap-2 px-3 py-2 rounded-lg text-left transition-all ${
                                                                                    isWeekSelected ? 'bg-[#00F0FF]/10 text-[#00F0FF]' : 'hover:bg-white/5 text-[#8E8E93]'
                                                                                }`}
                                                                            >
                                                                                <span className="text-[11px] font-bold flex-1">{week.name}</span>
                                                                                <span className="text-[10px] text-[#48484A]">{week.sessions.length} ses</span>
                                                                                {hasEvent && <CalendarIcon size={12} className="text-[#00F0FF] shrink-0" />}
                                                                            </button>
                                                                        );
                                                                    })}
                                                                    <button
                                                                        onClick={() => update(p => {
                                                                            const target = p.macrocycles[macroIdx]?.blocks?.[blockIdx]?.mesocycles?.[mesoIdx];
                                                                            if (target) target.weeks.push({ id: crypto.randomUUID(), name: `Semana ${target.weeks.length + 1}`, sessions: [] });
                                                                        })}
                                                                        className="w-full flex items-center gap-2 px-3 py-1.5 text-[10px] text-[#48484A] hover:text-[#00F0FF] transition-colors"
                                                                    >
                                                                        <PlusIcon size={12} /> Semana
                                                                    </button>
                                                                </div>
                                                            ))}
                                                            <button
                                                                onClick={() => update(p => {
                                                                    const blk = p.macrocycles[macroIdx]?.blocks?.[blockIdx]; if (!blk) return; blk.mesocycles.push({
                                                                        id: crypto.randomUUID(), name: 'Nueva Fase', goal: 'Custom' as any,
                                                                        weeks: [{ id: crypto.randomUUID(), name: 'Semana 1', sessions: [] }],
                                                                    });
                                                                })}
                                                                className="w-full flex items-center gap-2 px-3 py-1.5 text-[10px] text-[#48484A] hover:text-[#00F0FF] transition-colors"
                                                            >
                                                                <PlusIcon size={12} /> Mesociclo
                                                            </button>
                                                        </div>
                                                    )}
                                                </div>
                                            );
                                        })}
                                        {!isCyclic && (
                                            <button
                                                onClick={() => handleAddBlockClick(macroIdx)}
                                                className="w-full flex items-center gap-2 px-3 py-2 rounded-lg text-[11px] text-[#48484A] hover:text-[#00F0FF] hover:bg-white/5 transition-colors border border-dashed border-white/10"
                                            >
                                                <PlusIcon size={14} /> Añadir bloque
                                            </button>
                                        )}
                                    </div>
                                )}
                            </div>
                        );
                    })}
                </div>
                {onUpdateProgram && (
                    <div className="mt-6 pt-4 border-t border-white/5">
                        <EventSessionsManager program={program} isCyclic={isCyclic} onUpdateProgram={onUpdateProgram} />
                    </div>
                )}
            </div>

            {/* Events + Transition - fixed footer */}
            <div className="shrink-0 border-t border-white/5 px-4 py-4 space-y-3">
                <div className="flex items-center justify-between">
                    <span className="text-xs font-bold text-[#8E8E93] uppercase tracking-wide">Eventos</span>
                    <button onClick={() => onOpenEventModal()} className="text-xs text-[#00F0FF] font-bold hover:underline">
                        + Nuevo evento
                    </button>
                </div>
                {events.length > 0 ? (
                    <div className="flex flex-wrap gap-2">
                        {events.map((ev: any, i: number) => (
                            <button
                                key={ev.id || i}
                                onClick={() => onOpenEventModal(ev)}
                                className="flex items-center gap-2 px-3 py-2 rounded-lg bg-white/5 hover:bg-white/10 transition-colors"
                            >
                                <div className="w-2 h-2 rounded-full bg-[#00F0FF] shrink-0" />
                                <span className="text-xs font-bold text-white truncate max-w-[120px]">{ev.title}</span>
                                <span className="text-[10px] text-[#48484A]">
                                    {isCyclic ? `c/${ev.repeatEveryXCycles}` : `S${(ev.calculatedWeek || 0) + 1}`}
                                </span>
                            </button>
                        ))}
                    </div>
                ) : (
                    <p className="text-[11px] text-[#48484A] text-center py-2">Sin eventos programados</p>
                )}
                <div className="flex gap-2">
                    {isCyclic ? (
                        <button onClick={onShowAdvancedTransition} className="flex-1 py-2.5 rounded-lg bg-white/5 text-xs font-bold text-[#8E8E93] hover:text-white hover:bg-white/10 transition-colors">
                            Convertir a Avanzado
                        </button>
                    ) : (
                        <button onClick={onShowSimpleTransition} className="flex-1 py-2.5 rounded-lg bg-white/5 text-xs font-bold text-[#8E8E93] hover:text-white hover:bg-white/10 transition-colors">
                            Simplificar Programa
                        </button>
                    )}
                </div>
            </div>

            {/* Add block confirmation modal */}
            {showAddBlockConfirm && (
                <>
                    <div className="fixed inset-0 z-[200] bg-black/60" onClick={() => { setShowAddBlockConfirm(false); setPendingAddBlockMacroIdx(null); }} />
                    <div className="fixed inset-0 z-[201] flex items-center justify-center p-4">
                        <div className="bg-[#1a1a1a] border border-white/10 rounded-2xl p-5 max-w-sm w-full shadow-xl" onClick={e => e.stopPropagation()}>
                            <div className="flex items-start gap-3 mb-4">
                                <AlertTriangleIcon size={24} className="text-amber-400 shrink-0" />
                                <div>
                                    <h3 className="text-sm font-bold text-white">Convertir a programa avanzado</h3>
                                    <p className="text-xs text-[#8E8E93] mt-1">Al añadir un bloque, el programa se convierte en avanzado y los eventos cíclicos se perderán. ¿Continuar?</p>
                                </div>
                            </div>
                            <div className="flex gap-2">
                                <button
                                    onClick={() => { setShowAddBlockConfirm(false); setPendingAddBlockMacroIdx(null); }}
                                    className="flex-1 py-2.5 rounded-lg bg-white/5 text-xs font-bold text-[#8E8E93] hover:text-white transition-colors"
                                >
                                    Cancelar
                                </button>
                                <button
                                    onClick={() => pendingAddBlockMacroIdx !== null && doAddBlock(pendingAddBlockMacroIdx)}
                                    className="flex-1 py-2.5 rounded-lg bg-[#00F0FF] text-xs font-bold text-white hover:bg-[#00F0FF]/90 transition-colors"
                                >
                                    Continuar
                                </button>
                            </div>
                        </div>
                    </div>
                </>
            )}
        </div>
    );
};

export default StructureTabView;
