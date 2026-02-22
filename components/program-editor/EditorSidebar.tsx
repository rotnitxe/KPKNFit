import React, { useState } from 'react';
import { Program } from '../../types';
import { ChevronDownIcon, PlusIcon, CalendarIcon, ActivityIcon, DumbbellIcon } from '../icons';

interface EditorSidebarProps {
    program: Program;
    activeSectionId: string | null;
    onNavigateToSection: (sectionId: string) => void;
    onAddBlock?: (macroIndex: number) => void;
    onAddWeek?: (macroIndex: number, blockIndex: number, mesoIndex: number) => void;
    collapsed?: boolean;
    onToggleCollapsed?: () => void;
}

const EditorSidebar: React.FC<EditorSidebarProps> = ({
    program, activeSectionId, onNavigateToSection,
    onAddBlock, onAddWeek, collapsed = false, onToggleCollapsed,
}) => {
    const [expandedNodes, setExpandedNodes] = useState<Set<string>>(new Set(['macro-0']));

    const toggleNode = (id: string) => {
        setExpandedNodes(prev => {
            const next = new Set(prev);
            next.has(id) ? next.delete(id) : next.add(id);
            return next;
        });
    };

    const sections = [
        { id: 'details', label: 'Detalles', icon: <DumbbellIcon size={12} /> },
        { id: 'structure', label: 'Estructura', icon: <ActivityIcon size={12} /> },
        { id: 'goals', label: 'Metas', icon: <ActivityIcon size={12} /> },
        { id: 'events', label: 'Eventos', icon: <CalendarIcon size={12} /> },
        { id: 'volume', label: 'Volumen', icon: <ActivityIcon size={12} /> },
        { id: 'export', label: 'Exportar', icon: <ActivityIcon size={12} /> },
    ];

    if (collapsed) {
        return (
            <div className="w-12 bg-zinc-950 border-r border-white/5 flex flex-col items-center py-4 gap-3 shrink-0">
                <button onClick={onToggleCollapsed} className="p-1.5 rounded-lg text-zinc-500 hover:text-white transition-colors">
                    <ChevronDownIcon size={14} className="rotate-[-90deg]" />
                </button>
                {sections.map(s => (
                    <button
                        key={s.id}
                        onClick={() => onNavigateToSection(s.id)}
                        className={`p-2 rounded-lg transition-colors ${
                            activeSectionId === s.id ? 'bg-white/10 text-white' : 'text-zinc-600 hover:text-white'
                        }`}
                        title={s.label}
                    >
                        {s.icon}
                    </button>
                ))}
            </div>
        );
    }

    return (
        <div className="w-56 bg-zinc-950 border-r border-white/5 flex flex-col shrink-0 overflow-hidden">
            {/* Header */}
            <div className="flex items-center justify-between px-3 py-3 border-b border-white/5">
                <span className="text-[9px] font-black text-zinc-400 uppercase tracking-widest">Navegación</span>
                <button onClick={onToggleCollapsed} className="p-1 text-zinc-500 hover:text-white transition-colors">
                    <ChevronDownIcon size={12} className="rotate-90" />
                </button>
            </div>

            {/* Section links */}
            <div className="p-2 space-y-0.5 border-b border-white/5">
                {sections.map(s => (
                    <button
                        key={s.id}
                        onClick={() => onNavigateToSection(s.id)}
                        className={`w-full flex items-center gap-2 px-2.5 py-2 rounded-lg text-[10px] font-bold transition-colors ${
                            activeSectionId === s.id
                                ? 'bg-white/5 text-white'
                                : 'text-zinc-500 hover:text-white hover:bg-white/5'
                        }`}
                    >
                        {s.icon}
                        {s.label}
                    </button>
                ))}
            </div>

            {/* Program tree */}
            <div className="flex-1 overflow-y-auto custom-scrollbar p-2 space-y-1">
                <span className="text-[8px] font-black text-zinc-600 uppercase tracking-widest px-2 block mb-2">Estructura</span>
                {program.macrocycles.map((macro, macroIdx) => {
                    const macroId = `macro-${macroIdx}`;
                    const isMacroExpanded = expandedNodes.has(macroId);
                    return (
                        <div key={macro.id}>
                            <button
                                onClick={() => toggleNode(macroId)}
                                className="w-full flex items-center gap-1.5 px-2 py-1.5 rounded-md text-[9px] font-bold text-zinc-400 hover:text-white hover:bg-white/5 transition-colors"
                            >
                                <ChevronDownIcon size={10} className={`transition-transform ${isMacroExpanded ? 'rotate-0' : '-rotate-90'}`} />
                                <span className="truncate">{macro.name || `Macro ${macroIdx + 1}`}</span>
                            </button>
                            {isMacroExpanded && (
                                <div className="pl-4 space-y-0.5 mt-0.5">
                                    {(macro.blocks || []).map((block, blockIdx) => {
                                        const blockId = `block-${macroIdx}-${blockIdx}`;
                                        const isBlockExpanded = expandedNodes.has(blockId);
                                        return (
                                            <div key={block.id}>
                                                <button
                                                    onClick={() => toggleNode(blockId)}
                                                    className="w-full flex items-center gap-1.5 px-2 py-1 rounded-md text-[9px] font-bold text-zinc-500 hover:text-white hover:bg-white/5 transition-colors"
                                                >
                                                    <ChevronDownIcon size={9} className={`transition-transform ${isBlockExpanded ? 'rotate-0' : '-rotate-90'}`} />
                                                    <span className="truncate">{block.name || `Bloque ${blockIdx + 1}`}</span>
                                                </button>
                                                {isBlockExpanded && (
                                                    <div className="pl-4 space-y-0.5 mt-0.5">
                                                        {block.mesocycles.map((meso, mesoIdx) => (
                                                            <div key={meso.id} className="pl-2">
                                                                <div className="text-[8px] font-bold text-zinc-600 px-2 py-0.5 flex items-center gap-1">
                                                                    <div className="w-1 h-1 rounded-full bg-blue-500" />
                                                                    {meso.name} ({meso.weeks.length}sem)
                                                                </div>
                                                                {onAddWeek && (
                                                                    <button
                                                                        onClick={() => onAddWeek(macroIdx, blockIdx, mesoIdx)}
                                                                        className="ml-2 text-[7px] text-zinc-600 hover:text-white flex items-center gap-1 py-0.5"
                                                                    >
                                                                        <PlusIcon size={8} /> Semana
                                                                    </button>
                                                                )}
                                                            </div>
                                                        ))}
                                                    </div>
                                                )}
                                            </div>
                                        );
                                    })}
                                    {onAddBlock && (
                                        <button
                                            onClick={() => onAddBlock(macroIdx)}
                                            className="w-full flex items-center gap-1.5 px-2 py-1 text-[8px] font-bold text-zinc-600 hover:text-white transition-colors"
                                        >
                                            <PlusIcon size={9} /> Bloque
                                        </button>
                                    )}
                                </div>
                            )}
                        </div>
                    );
                })}
            </div>
        </div>
    );
};

export default EditorSidebar;
