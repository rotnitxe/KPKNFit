import React, { useState } from 'react';
import { Program, ProgramWeek } from '../../types';
import {
    CalendarIcon, PlusIcon, TrashIcon, XIcon,
    ActivityIcon, ChevronDownIcon,
} from '../icons';
import { getAbsoluteWeekIndex, checkWeekHasEvent } from '../../utils/programHelpers';

interface StructureCardProps {
    program: Program;
    isSimple: boolean;
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
    program, isSimple, onUpdateProgram, onEditWeek,
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
        <div className="bg-white/80 backdrop-blur-[40px] border border-white/60 rounded-[32px] overflow-hidden transition-all duration-300 shadow-[0_20px_60px_rgba(0,0,0,0.06)] relative group">
            {/* Background Accent */}
            <div className="absolute top-0 right-0 w-48 h-48 bg-blue-500/[0.03] blur-[80px] rounded-full -mr-24 -mt-24 pointer-events-none" />

            <button onClick={onToggleCollapse} className="w-full flex items-center justify-between p-6 text-left relative z-10">
                <div className="flex items-center gap-4">
                    <div className="w-12 h-12 rounded-[20px] bg-blue-50 flex items-center justify-center shadow-sm">
                        <ActivityIcon size={22} className="text-blue-600" />
                    </div>
                    <div>
                        <h3 className="text-[14px] font-black text-black uppercase tracking-tight">Estructura</h3>
                        <p className="text-[10px] text-zinc-500 font-black uppercase tracking-widest mt-0.5">
                            {isSimple ? 'Programa Simple' : `${program.macrocycles.length} Macrociclo${program.macrocycles.length > 1 ? 's' : ''}`}
                        </p>
                    </div>
                </div>
                <div className="w-8 h-8 rounded-full bg-black/5 flex items-center justify-center text-zinc-400 group-hover:bg-black/10 group-hover:text-black transition-all">
                    <ChevronDownIcon size={16} className={`transition-transform duration-300 ${collapsed ? '' : 'rotate-180'}`} />
                </div>
            </button>

            {!collapsed && (
                <div className="px-6 pb-6 space-y-6 animate-fade-in relative z-10">
                    {(program.macrocycles || []).map((macro, macroIndex) => {
                        const macroWeeks = (macro.blocks || []).flatMap(b => b.mesocycles.flatMap(me => me.weeks));

                        return (
                            <div key={macro.id} className="space-y-4">
                                <div className="flex justify-between items-center bg-black/[0.02] p-4 rounded-2xl border border-black/5">
                                    <div className="flex items-center gap-3 flex-1">
                                        <span className="bg-black text-white text-[9px] font-black px-2.5 py-1 rounded-md uppercase tracking-wider shadow-sm">M{macroIndex + 1}</span>
                                        <input
                                            className="bg-transparent border-none p-0 text-[13px] font-black text-black uppercase tracking-tight focus:ring-0 w-full"
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
                                    <span className="text-[10px] text-zinc-500 font-black uppercase tracking-widest bg-white px-3 py-1.5 rounded-full border border-black/5 shadow-sm">{macroWeeks.length} sem</span>
                                </div>

                                {/* Actions */}
                                <div className="flex gap-3 flex-wrap pl-2">
                                    {isSimple ? (
                                        <button
                                            onClick={onShowAdvancedTransition}
                                            className="py-2.5 px-4 bg-white/60 backdrop-blur-md border border-white/60 rounded-xl text-[9px] font-black uppercase tracking-widest text-blue-600 hover:bg-blue-50 transition-all flex items-center gap-2 shadow-sm"
                                        >
                                            <PlusIcon size={12} /> Añadir Bloque
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
                                                className="py-2.5 px-4 bg-white/60 backdrop-blur-md border border-white/60 rounded-xl text-[9px] font-black uppercase tracking-widest text-blue-600 hover:bg-blue-50 transition-all flex items-center gap-2 shadow-sm"
                                            >
                                                <PlusIcon size={12} /> Bloque
                                            </button>
                                            {macroIndex === 0 && (
                                                <button
                                                    onClick={onShowSimpleTransition}
                                                    className="py-2.5 px-4 bg-black text-white rounded-xl text-[9px] font-black uppercase tracking-widest hover:brightness-110 transition-all flex items-center gap-2 shadow-[0_4px_12px_rgba(0,0,0,0.1)]"
                                                >
                                                    <ActivityIcon size={12} /> Simplificar
                                                </button>
                                            )}
                                        </>
                                    )}
                                </div>

                                {/* Blocks tree */}
                                <div className="pl-6 border-l-2 border-black/[0.05] space-y-4 relative">
                                    {(macro.blocks || []).map((block, blockIndex) => {
                                        const isExpanded = expandedBlocks.has(block.id);
                                        return (
                                            <div key={block.id} className="relative group">
                                                <div className="absolute -left-[26px] top-6 w-3 h-3 rounded-full border-2 border-white bg-blue-100 group-hover:bg-blue-500 transition-colors shadow-sm" />
                                                <div className={`bg-white rounded-2xl overflow-hidden transition-all duration-300 border ${isExpanded ? 'border-blue-100 shadow-md ring-2 ring-blue-50' : 'border-black/[0.05] shadow-sm hover:border-black/10'}`}>
                                                    {/* Block header */}
                                                    <div
                                                        className="p-4 flex items-center justify-between cursor-pointer"
                                                        onClick={() => toggleBlock(block.id)}
                                                    >
                                                        <div className="flex items-center gap-3 flex-1">
                                                            <span className="bg-blue-50 text-blue-600 text-[8px] font-black px-2 py-1 rounded-md uppercase tracking-widest border border-blue-100">B{blockIndex + 1}</span>
                                                            <input
                                                                className="bg-transparent border-none p-0 text-[12px] font-black text-black uppercase tracking-tight focus:ring-0 flex-1"
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
                                                        <div className="flex items-center gap-3">
                                                            <span className="text-[9px] text-zinc-500 font-black uppercase tracking-widest">
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
                                                                className="w-8 h-8 rounded-full flex items-center justify-center text-zinc-400 hover:bg-red-50 hover:text-red-500 transition-colors"
                                                            >
                                                                <TrashIcon size={14} />
                                                            </button>
                                                            <div className={`w-8 h-8 rounded-full flex items-center justify-center bg-black/5 text-zinc-500 transition-transform duration-300 ${isExpanded ? 'rotate-180 bg-blue-50 text-blue-600' : ''}`}>
                                                                <ChevronDownIcon size={14} />
                                                            </div>
                                                        </div>
                                                    </div>

                                                    {/* Block content */}
                                                    {isExpanded && (
                                                        <div className="p-4 pt-0 space-y-4 animate-fade-in border-t border-black/[0.03] mt-2">
                                                            {(block.mesocycles || []).map((meso, mesoIndex) => (
                                                                <div key={meso.id} className="bg-black/[0.02] p-4 rounded-xl border border-black/5 space-y-3 relative overflow-hidden">
                                                                    <div className="absolute top-0 right-0 w-24 h-24 bg-blue-500/[0.02] blur-2xl rounded-full -mr-12 -mt-12" />
                                                                    <div className="flex items-center justify-between relative z-10">
                                                                        <div className="flex items-center gap-3 flex-1">
                                                                            <div className="w-2 h-2 rounded-full bg-blue-500 shadow-[0_0_8px_rgba(59,130,246,0.5)]" />
                                                                            <input
                                                                                className="bg-transparent border-none p-0 text-[11px] font-black text-black uppercase tracking-tight flex-1 focus:ring-0"
                                                                                defaultValue={isSimple ? 'Semanas Cíclicas' : meso.name}
                                                                                disabled={isSimple}
                                                                                onBlur={e => {
                                                                                    if (!isSimple && e.target.value !== meso.name) {
                                                                                        const updated = JSON.parse(JSON.stringify(program));
                                                                                        updated.macrocycles[macroIndex].blocks[blockIndex].mesocycles[mesoIndex].name = e.target.value;
                                                                                        onUpdateProgram?.(updated);
                                                                                    }
                                                                                }}
                                                                            />
                                                                        </div>
                                                                        <div className="flex items-center gap-2">
                                                                            {!isSimple && (
                                                                                <select
                                                                                    className="bg-white text-[8px] font-black text-blue-600 uppercase tracking-widest border border-black/5 rounded-lg px-2 py-1 shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500/20 appearance-none"
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
                                                                                className="w-7 h-7 bg-white border border-black/5 rounded-lg flex items-center justify-center text-zinc-500 hover:text-blue-600 hover:border-blue-200 transition-colors shadow-sm"
                                                                            >
                                                                                <PlusIcon size={12} />
                                                                            </button>
                                                                        </div>
                                                                    </div>

                                                                    {isSimple && (
                                                                        <p className="text-[9px] font-black uppercase tracking-widest text-zinc-400 opacity-60">
                                                                            Las semanas se repiten cíclicamente.
                                                                        </p>
                                                                    )}

                                                                    <div className="flex gap-2 overflow-x-auto custom-scrollbar pb-2 relative z-10 -mx-2 px-2">
                                                                        {(meso.weeks || []).map((week, weekIndex) => {
                                                                            const weekPattern = Array(7).fill('Descanso');
                                                                            week.sessions.forEach(s => {
                                                                                if (s.dayOfWeek !== undefined && s.dayOfWeek >= 0 && s.dayOfWeek < 7) weekPattern[s.dayOfWeek] = s.name;
                                                                            });
                                                                            const absIdx = getAbsoluteWeekIndex(program, block.id, week.id);
                                                                            const hasEvent = checkWeekHasEvent(program, absIdx);

                                                                            return (
                                                                                <div key={week.id} className="shrink-0 w-32 relative group/week">
                                                                                    <div
                                                                                        onClick={() => onEditWeek({ macroIndex, blockIndex, mesoIndex, weekIndex, week, isSimple: isSimple })}
                                                                                        className={`w-full bg-white border ${hasEvent ? 'border-amber-400 ring-2 ring-amber-400/20' : 'border-black/5'} rounded-xl p-3 flex flex-col gap-2.5 hover:border-blue-400 hover:shadow-md transition-all cursor-pointer shadow-sm`}
                                                                                    >
                                                                                        <div className="flex items-center justify-between">
                                                                                            {hasEvent && <div className="w-2 h-2 bg-amber-400 rounded-full animate-pulse shadow-[0_0_8px_rgba(251,191,36,0.6)]" />}
                                                                                            <input
                                                                                                className={`bg-transparent border-none p-0 text-[9px] font-black uppercase tracking-widest w-full focus:ring-0 ${hasEvent ? 'text-amber-600' : 'text-zinc-600'}`}
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
                                                                                        <div className="flex gap-[2px] h-1.5">
                                                                                            {weekPattern.map((d, dIdx) => (
                                                                                                <div key={dIdx} className={`flex-1 rounded-[2px] ${d.toLowerCase() === 'descanso' ? 'bg-black/5' : hasEvent ? 'bg-amber-400' : 'bg-blue-500'}`} />
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
                                                                                        className="absolute -top-2 -right-2 bg-red-500 text-white rounded-full w-5 h-5 flex items-center justify-center opacity-0 group-hover/week:opacity-100 transition-opacity z-20 shadow-md hover:bg-red-600 hover:scale-110 active:scale-90"
                                                                                    >
                                                                                        <XIcon size={12} />
                                                                                    </button>
                                                                                </div>
                                                                            );
                                                                        })}
                                                                    </div>
                                                                </div>
                                                            ))}

                                                            {!isSimple && (
                                                                <button
                                                                    onClick={() => {
                                                                        const updated = JSON.parse(JSON.stringify(program));
                                                                        updated.macrocycles[macroIndex].blocks[blockIndex].mesocycles.push({
                                                                            id: crypto.randomUUID(), name: 'Nuevo Mesociclo', goal: 'Acumulación', weeks: [],
                                                                        });
                                                                        onUpdateProgram?.(updated);
                                                                    }}
                                                                    className="w-full py-3 bg-white/50 backdrop-blur-sm border-2 border-dashed border-black/[0.05] rounded-xl text-[9px] font-black uppercase tracking-widest text-zinc-400 hover:text-black hover:border-black/20 hover:bg-white transition-all flex items-center justify-center gap-2"
                                                                >
                                                                    <PlusIcon size={14} /> Mesociclo
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
