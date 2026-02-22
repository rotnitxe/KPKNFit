import React, { useState } from 'react';
import { Program, ProgramWeek } from '../../types';
import {
    CalendarIcon, PlusIcon, TrashIcon, XIcon,
    ActivityIcon, ChevronDownIcon,
} from '../icons';
import { getAbsoluteWeekIndex, checkWeekHasEvent } from '../../utils/programHelpers';

interface StructureCardProps {
    program: Program;
    isCyclic: boolean;
    onUpdateProgram?: (program: Program) => void;
    onEditWeek: (info: {
        macroIndex: number;
        blockIndex: number;
        mesoIndex: number;
        weekIndex: number;
        week: ProgramWeek;
        isSimple: boolean;
    }) => void;
    onShowAdvancedTransition: () => void;
    onShowSimpleTransition: () => void;
    collapsed?: boolean;
    onToggleCollapse?: () => void;
}

const GOAL_OPTIONS = ['Acumulación', 'Intensificación', 'Realización', 'Descarga', 'Custom'];

const StructureCard: React.FC<StructureCardProps> = ({
    program, isCyclic, onUpdateProgram, onEditWeek,
    onShowAdvancedTransition, onShowSimpleTransition,
    collapsed = false, onToggleCollapse,
}) => {
    const [expandedBlocks, setExpandedBlocks] = useState<Set<string>>(new Set());

    const toggleBlock = (blockId: string) => {
        setExpandedBlocks(prev => {
            const next = new Set(prev);
            next.has(blockId) ? next.delete(blockId) : next.add(blockId);
            return next;
        });
    };

    return (
        <div className="bg-zinc-900/50 border border-white/5 rounded-2xl overflow-hidden transition-all duration-300">
            <button onClick={onToggleCollapse} className="w-full flex items-center justify-between p-4 text-left">
                <div className="flex items-center gap-3">
                    <div className="w-8 h-8 rounded-lg bg-purple-500/10 border border-purple-500/20 flex items-center justify-center">
                        <ActivityIcon size={16} className="text-purple-400" />
                    </div>
                    <div>
                        <h3 className="text-xs font-black text-white uppercase tracking-widest">Estructura</h3>
                        <p className="text-[9px] text-zinc-500 font-bold">
                            {isCyclic ? 'Programa Cíclico' : `${program.macrocycles.length} Macrociclo${program.macrocycles.length > 1 ? 's' : ''}`}
                        </p>
                    </div>
                </div>
                <ChevronDownIcon size={16} className={`text-zinc-500 transition-transform duration-300 ${collapsed ? '' : 'rotate-180'}`} />
            </button>

            {!collapsed && (
                <div className="px-4 pb-4 space-y-4 animate-fade-in">
                    {(program.macrocycles || []).map((macro, macroIndex) => {
                        const macroWeeks = (macro.blocks || []).flatMap(b => b.mesocycles.flatMap(me => me.weeks));

                        return (
                            <div key={macro.id} className="space-y-3">
                                <div className="flex justify-between items-center bg-zinc-950 p-3 rounded-xl border border-white/10">
                                    <div className="flex items-center gap-2 flex-1">
                                        <span className="bg-white/10 text-white text-[8px] font-black px-2 py-0.5 rounded uppercase tracking-wider">M{macroIndex + 1}</span>
                                        <input
                                            className="bg-transparent border-none p-0 text-sm font-black text-white uppercase tracking-tight focus:ring-0 w-full"
                                            defaultValue={macro.name}
                                            onBlur={e => {
                                                if (e.target.value !== macro.name) {
                                                    const updated = JSON.parse(JSON.stringify(program));
                                                    updated.macrocycles[macroIndex].name = e.target.value || `Macrociclo ${macroIndex + 1}`;
                                                    onUpdateProgram?.(updated);
                                                }
                                            }}
                                        />
                                    </div>
                                    <span className="text-[9px] text-zinc-500 font-bold">{macroWeeks.length}sem</span>
                                </div>

                                {/* Actions */}
                                <div className="flex gap-2 flex-wrap pl-4">
                                    {isCyclic ? (
                                        <button
                                            onClick={onShowAdvancedTransition}
                                            className="py-2 px-3 bg-zinc-900 border border-dashed border-white/20 rounded-lg text-[8px] font-black uppercase tracking-widest text-zinc-400 hover:text-white hover:border-white transition-all flex items-center gap-1.5"
                                        >
                                            <PlusIcon size={10} /> Añadir Bloque
                                        </button>
                                    ) : (
                                        <>
                                            <button
                                                onClick={() => {
                                                    const updated = JSON.parse(JSON.stringify(program));
                                                    updated.macrocycles[macroIndex].blocks.push({
                                                        id: crypto.randomUUID(), name: 'Nuevo Bloque',
                                                        mesocycles: [{ id: crypto.randomUUID(), name: 'Fase Inicial', goal: 'Acumulación', weeks: [] }],
                                                    });
                                                    onUpdateProgram?.(updated);
                                                }}
                                                className="py-2 px-3 bg-zinc-900 border border-dashed border-white/20 rounded-lg text-[8px] font-black uppercase tracking-widest text-zinc-400 hover:text-white hover:border-white transition-all flex items-center gap-1.5"
                                            >
                                                <PlusIcon size={10} /> Bloque
                                            </button>
                                            {macroIndex === 0 && (
                                                <button
                                                    onClick={onShowSimpleTransition}
                                                    className="py-2 px-3 bg-black border border-white/10 rounded-lg text-[8px] font-black uppercase tracking-widest text-zinc-500 hover:text-white transition-all flex items-center gap-1.5"
                                                >
                                                    <ActivityIcon size={10} /> Simplificar
                                                </button>
                                            )}
                                        </>
                                    )}
                                </div>

                                {/* Blocks tree */}
                                <div className="pl-4 border-l-2 border-white/10 space-y-3">
                                    {(macro.blocks || []).map((block, blockIndex) => {
                                        const isExpanded = expandedBlocks.has(block.id);
                                        return (
                                            <div key={block.id} className="relative group">
                                                <div className="absolute -left-[13px] top-4 w-2.5 h-2.5 rounded-full border-2 border-white/30 bg-zinc-950 group-hover:border-blue-500 transition-colors" />
                                                <div className="bg-zinc-950 border border-white/10 rounded-xl overflow-hidden hover:border-white/20 transition-all">
                                                    {/* Block header */}
                                                    <div
                                                        className="p-3 flex items-center justify-between cursor-pointer"
                                                        onClick={() => toggleBlock(block.id)}
                                                    >
                                                        <div className="flex items-center gap-2 flex-1">
                                                            <span className="bg-white/10 text-white text-[7px] font-black px-1.5 py-0.5 rounded uppercase tracking-wider">B{blockIndex + 1}</span>
                                                            <input
                                                                className="bg-transparent border-none p-0 text-xs font-black text-white uppercase tracking-tight focus:ring-0 flex-1"
                                                                defaultValue={block.name}
                                                                onClick={e => e.stopPropagation()}
                                                                onBlur={e => {
                                                                    if (e.target.value !== block.name) {
                                                                        const updated = JSON.parse(JSON.stringify(program));
                                                                        updated.macrocycles[macroIndex].blocks[blockIndex].name = e.target.value || `Bloque ${blockIndex + 1}`;
                                                                        onUpdateProgram?.(updated);
                                                                    }
                                                                }}
                                                            />
                                                        </div>
                                                        <div className="flex items-center gap-2">
                                                            <span className="text-[8px] text-zinc-500 font-bold">
                                                                {block.mesocycles.reduce((a, m) => a + m.weeks.length, 0)}sem
                                                            </span>
                                                            <button
                                                                onClick={e => {
                                                                    e.stopPropagation();
                                                                    if (window.confirm('¿Eliminar este bloque?')) {
                                                                        const updated = JSON.parse(JSON.stringify(program));
                                                                        updated.macrocycles[macroIndex].blocks.splice(blockIndex, 1);
                                                                        onUpdateProgram?.(updated);
                                                                    }
                                                                }}
                                                                className="p-1 text-zinc-700 hover:text-red-500 transition-colors"
                                                            >
                                                                <TrashIcon size={10} />
                                                            </button>
                                                            <ChevronDownIcon size={14} className={`text-zinc-500 transition-transform ${isExpanded ? 'rotate-180' : ''}`} />
                                                        </div>
                                                    </div>

                                                    {/* Block content */}
                                                    {isExpanded && (
                                                        <div className="p-3 pt-0 space-y-3 animate-fade-in">
                                                            {(block.mesocycles || []).map((meso, mesoIndex) => (
                                                                <div key={meso.id} className="bg-black/40 p-2.5 rounded-lg border border-white/5 space-y-2">
                                                                    <div className="flex items-center justify-between">
                                                                        <div className="flex items-center gap-2 flex-1">
                                                                            <div className="w-1.5 h-1.5 rounded-full bg-blue-500" />
                                                                            <input
                                                                                className="bg-transparent border-none p-0 text-[10px] font-black text-white uppercase flex-1 focus:ring-0"
                                                                                defaultValue={isCyclic ? 'Semanas Cíclicas' : meso.name}
                                                                                disabled={isCyclic}
                                                                                onBlur={e => {
                                                                                    if (!isCyclic && e.target.value !== meso.name) {
                                                                                        const updated = JSON.parse(JSON.stringify(program));
                                                                                        updated.macrocycles[macroIndex].blocks[blockIndex].mesocycles[mesoIndex].name = e.target.value;
                                                                                        onUpdateProgram?.(updated);
                                                                                    }
                                                                                }}
                                                                            />
                                                                        </div>
                                                                        <div className="flex items-center gap-1.5">
                                                                            {!isCyclic && (
                                                                                <select
                                                                                    className="bg-black text-[7px] text-zinc-400 border border-white/10 rounded px-1.5 py-0.5 uppercase font-bold"
                                                                                    value={meso.goal}
                                                                                    onChange={e => {
                                                                                        const updated = JSON.parse(JSON.stringify(program));
                                                                                        updated.macrocycles[macroIndex].blocks[blockIndex].mesocycles[mesoIndex].goal = e.target.value;
                                                                                        onUpdateProgram?.(updated);
                                                                                    }}
                                                                                >
                                                                                    {GOAL_OPTIONS.map(g => <option key={g} value={g}>{g}</option>)}
                                                                                </select>
                                                                            )}
                                                                            <button
                                                                                onClick={() => {
                                                                                    const updated = JSON.parse(JSON.stringify(program));
                                                                                    const m = updated.macrocycles[macroIndex].blocks[blockIndex].mesocycles[mesoIndex];
                                                                                    if (!m.weeks) m.weeks = [];
                                                                                    m.weeks.push({ id: crypto.randomUUID(), name: `Semana ${m.weeks.length + 1}`, sessions: [] });
                                                                                    onUpdateProgram?.(updated);
                                                                                }}
                                                                                className="p-1 bg-zinc-900 border border-white/10 rounded flex items-center justify-center text-zinc-400 hover:text-white transition-colors"
                                                                            >
                                                                                <PlusIcon size={10} />
                                                                            </button>
                                                                        </div>
                                                                    </div>

                                                                    {isCyclic && (
                                                                        <p className="text-[8px] text-zinc-500 italic px-1">
                                                                            Las semanas se repiten cíclicamente.
                                                                        </p>
                                                                    )}

                                                                    <div className="flex gap-1.5 overflow-x-auto no-scrollbar pb-1">
                                                                        {(meso.weeks || []).map((week, weekIndex) => {
                                                                            const weekPattern = Array(7).fill('Descanso');
                                                                            week.sessions.forEach(s => {
                                                                                if (s.dayOfWeek !== undefined && s.dayOfWeek >= 0 && s.dayOfWeek < 7) weekPattern[s.dayOfWeek] = s.name;
                                                                            });
                                                                            const absIdx = getAbsoluteWeekIndex(program, block.id, week.id);
                                                                            const hasEvent = checkWeekHasEvent(program, absIdx);

                                                                            return (
                                                                                <div key={week.id} className="shrink-0 w-28 relative group/week">
                                                                                    <div
                                                                                        onClick={() => onEditWeek({ macroIndex, blockIndex, mesoIndex, weekIndex, week, isSimple: isCyclic })}
                                                                                        className={`w-full bg-zinc-950 border ${hasEvent ? 'border-yellow-500/50' : 'border-white/5'} rounded-lg p-2 flex flex-col gap-1.5 hover:border-blue-500 transition-all cursor-pointer`}
                                                                                    >
                                                                                        <div className="flex items-center justify-between">
                                                                                            {hasEvent && <div className="w-1.5 h-1.5 bg-yellow-400 rounded-full animate-pulse" />}
                                                                                            <input
                                                                                                className={`bg-transparent border-none p-0 text-[8px] font-black uppercase w-full focus:ring-0 ${hasEvent ? 'text-yellow-400' : 'text-zinc-400'}`}
                                                                                                defaultValue={week.name}
                                                                                                onClick={e => e.stopPropagation()}
                                                                                                onBlur={e => {
                                                                                                    if (e.target.value !== week.name) {
                                                                                                        const updated = JSON.parse(JSON.stringify(program));
                                                                                                        updated.macrocycles[macroIndex].blocks[blockIndex].mesocycles[mesoIndex].weeks[weekIndex].name = e.target.value;
                                                                                                        onUpdateProgram?.(updated);
                                                                                                    }
                                                                                                }}
                                                                                            />
                                                                                        </div>
                                                                                        <div className="flex gap-[1px] h-1">
                                                                                            {weekPattern.map((d, dIdx) => (
                                                                                                <div key={dIdx} className={`flex-1 rounded-[1px] ${d.toLowerCase() === 'descanso' ? 'bg-white/5' : hasEvent ? 'bg-yellow-400' : 'bg-white'}`} />
                                                                                            ))}
                                                                                        </div>
                                                                                    </div>
                                                                                    <button
                                                                                        onClick={e => {
                                                                                            e.stopPropagation();
                                                                                            if (window.confirm('¿Borrar semana?')) {
                                                                                                const updated = JSON.parse(JSON.stringify(program));
                                                                                                updated.macrocycles[macroIndex].blocks[blockIndex].mesocycles[mesoIndex].weeks.splice(weekIndex, 1);
                                                                                                onUpdateProgram?.(updated);
                                                                                            }
                                                                                        }}
                                                                                        className="absolute -top-1 -right-1 bg-red-500 text-white rounded-full p-0.5 opacity-0 group-hover/week:opacity-100 transition-opacity z-20"
                                                                                    >
                                                                                        <XIcon size={7} />
                                                                                    </button>
                                                                                </div>
                                                                            );
                                                                        })}
                                                                    </div>
                                                                </div>
                                                            ))}

                                                            {!isCyclic && (
                                                                <button
                                                                    onClick={() => {
                                                                        const updated = JSON.parse(JSON.stringify(program));
                                                                        updated.macrocycles[macroIndex].blocks[blockIndex].mesocycles.push({
                                                                            id: crypto.randomUUID(), name: 'Nuevo Mesociclo', goal: 'Acumulación', weeks: [],
                                                                        });
                                                                        onUpdateProgram?.(updated);
                                                                    }}
                                                                    className="w-full py-2 bg-zinc-900/50 border border-dashed border-white/10 rounded-lg text-[8px] font-black uppercase text-zinc-500 hover:text-white hover:border-white/30 transition-all flex items-center justify-center gap-1.5"
                                                                >
                                                                    <PlusIcon size={10} /> Mesociclo
                                                                </button>
                                                            )}
                                                        </div>
                                                    )}
                                                </div>
                                            </div>
                                        );
                                    })}
                                </div>
                            </div>
                        );
                    })}
                </div>
            )}
        </div>
    );
};

export default StructureCard;
